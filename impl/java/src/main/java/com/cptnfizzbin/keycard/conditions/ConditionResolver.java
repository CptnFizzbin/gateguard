package com.cptnfizzbin.keycard.conditions;

import com.cptnfizzbin.keycard.conditions.stringConditions.StringConditions;
import com.cptnfizzbin.keycard.conditions.groupConditions.GroupConditions;
import com.cptnfizzbin.keycard.conditions.logicConditions.LogicConditions;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Implements SPEC_V1-0-0.md §7: the condition language and its
 * evaluation semantics.
 */
public final class ConditionResolver {

    private static final String DIAGNOSTIC_PREFIX = "[KeyCard]";

    /** Every {@code $}-prefixed key this resolver understands natively - anything else starting with "$" is a custom operator lookup (§7.4.12). */
    public static final Set<String> BUILTIN_OPERATORS = Set.of(
        "$eq", "$ne", "$gt", "$gte", "$lt", "$lte",
        "$in", "$has", "$substr", "$or", "$and", "$not", "$field"
    );

    private final Map<String, CustomConditionChecker> customCheckers;
    private final Set<String> declaredCustomOperators;

    public ConditionResolver() {
        this(null, null);
    }

    public ConditionResolver(Map<String, CustomConditionChecker> customCheckers) {
        this(customCheckers, null);
    }

    /**
     * @param customCheckers runtime checkers for custom $op operators (§7.4.12).
     * @param declaredCustomOperators the policy's meta.customOperators catalog, if any -
     *   used only to distinguish EC-13 (uncataloged, no diagnostic) from EC-15
     *   (cataloged but never registered, diagnostic required).
     */
    public ConditionResolver(Map<String, CustomConditionChecker> customCheckers, Set<String> declaredCustomOperators) {
        this.customCheckers = customCheckers != null ? Map.copyOf(customCheckers) : Map.of();
        this.declaredCustomOperators = declaredCustomOperators != null ? Set.copyOf(declaredCustomOperators) : Set.of();
    }

    /**
     * §7.1: {@code evaluate} always returns a boolean and SHOULD NOT throw
     * for any well-formed condition, regardless of what the subject is.
     */
    public boolean evaluate(Object subject, Object condition) {
        if (condition == null || condition instanceof String || condition instanceof Number || condition instanceof Boolean) {
            // §7.2: bare-value shorthand for $eq (including explicit null - §7.3, not a wildcard).
            return StringConditions.eq(subject, condition);
        }

        if (!(condition instanceof Map)) {
            return false;
        }

        Map<?, ?> condMap = (Map<?, ?>) condition;
        // §7.5: every key MUST be evaluated and ANDed together - no key may
        // "consume" the whole object or cause sibling keys to be ignored.
        for (Map.Entry<?, ?> entry : condMap.entrySet()) {
            if (!evaluateKey(subject, String.valueOf(entry.getKey()), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateKey(Object subject, String key, Object value) {
        switch (key) {
            case "$eq":
                return StringConditions.eq(subject, value);
            case "$ne":
                return StringConditions.ne(subject, value);
            case "$gt":
                return numericCompare("$gt", subject, value, (a, b) -> a > b);
            case "$gte":
                return numericCompare("$gte", subject, value, (a, b) -> a >= b);
            case "$lt":
                return numericCompare("$lt", subject, value, (a, b) -> a < b);
            case "$lte":
                return numericCompare("$lte", subject, value, (a, b) -> a <= b);
            case "$in":
                return inCheck(subject, value);
            case "$has":
                return hasCheck(subject, value);
            case "$substr":
                return substrCheck(subject, value);
            case "$or":
                return orCheck(subject, value);
            case "$and":
                return andCheck(subject, value);
            case "$not":
                return LogicConditions.not(this, subject, value);
            case "$field":
                return fieldOpCheck(subject, value);
            default:
                // §7.5: any key starting with "$" MUST be treated as an
                // operator, never a field name, whether or not it's recognized.
                if (key.startsWith("$")) {
                    return customOperatorCheck(subject, key, value);
                }
                return fieldCheck(subject, key, value);
        }
    }

    /** §7.4.3: $gt/$gte/$lt/$lte - numeric-only, IEEE-754 double semantics. */
    private boolean numericCompare(String op, Object subject, Object operand, BiPredicate<Double, Double> cmp) {
        if (!(subject instanceof Number) || !(operand instanceof Number)) {
            logTypeIssue(op, "expected the subject and operand to both be numbers, got "
                + typeName(subject) + " and " + typeName(operand));
            return false;
        }
        double a = ((Number) subject).doubleValue();
        double b = ((Number) operand).doubleValue();
        return cmp.test(a, b);
    }

    /** §7.4.4: $in - operand must be a collection; containment uses $eq semantics per element. */
    private boolean inCheck(Object subject, Object operand) {
        if (!(operand instanceof List)) {
            logTypeIssue("$in", "expected an array operand, got " + typeName(operand));
            return false;
        }
        return GroupConditions.in(subject, (List<?>) operand);
    }

    /** §7.4.5: $has - subject must be a collection. */
    private boolean hasCheck(Object subject, Object value) {
        if (!(subject instanceof List)) {
            logTypeIssue("$has", "expected an array subject, got " + typeName(subject));
            return false;
        }
        return GroupConditions.has((List<?>) subject, value);
    }

    /** §7.4.6: $substr - a null subject is an ordinary non-match, not a type issue; an invalid pattern always is. */
    private boolean substrCheck(Object subject, Object pattern) {
        if (!(pattern instanceof String)) {
            logTypeIssue("$substr", "expected a string pattern, got " + typeName(pattern));
            return false;
        }
        SubstrPattern parsed = SubstrPattern.parse((String) pattern);
        if (parsed == null) {
            logTypeIssue("$substr", "malformed pattern: " + pattern);
            return false;
        }
        if (subject == null) {
            return false;
        }
        return parsed.matches(String.valueOf(subject));
    }

    /** §7.4.7: $or - operand must be an array; {@code $or: []} is vacuously false. */
    private boolean orCheck(Object subject, Object operand) {
        if (!(operand instanceof List)) {
            logTypeIssue("$or", "expected an array operand, got " + typeName(operand));
            return false;
        }
        List<?> list = (List<?>) operand;
        if (list.isEmpty()) {
            logTypeIssue("$or", "empty $or is vacuously false - likely an authoring mistake");
            return false;
        }
        return LogicConditions.or(this, subject, list);
    }

    /** §7.4.8: $and - operand must be an array; {@code $and: []} is vacuously true. */
    private boolean andCheck(Object subject, Object operand) {
        if (!(operand instanceof List)) {
            logTypeIssue("$and", "expected an array operand, got " + typeName(operand));
            return false;
        }
        List<?> list = (List<?>) operand;
        if (list.isEmpty()) {
            logTypeIssue("$and", "empty $and is vacuously true - likely an authoring mistake");
            return true;
        }
        return LogicConditions.and(this, subject, list);
    }

    /** §7.4.11: $field - explicit field access, for a field whose name itself starts with "$". */
    private boolean fieldOpCheck(Object subject, Object operand) {
        if (!(operand instanceof List) || ((List<?>) operand).size() != 2 || !(((List<?>) operand).get(0) instanceof String)) {
            logTypeIssue("$field", "expected a [name, Condition] tuple, got " + operand);
            return false;
        }
        List<?> tuple = (List<?>) operand;
        return fieldCheck(subject, (String) tuple.get(0), tuple.get(1));
    }

    /** §7.4.10, §7.3: a missing field (or a non-object subject) makes the whole field-condition false - absence, not a type issue. */
    private boolean fieldCheck(Object subject, String fieldName, Object condition) {
        if (subject instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) subject;
            if (!map.containsKey(fieldName)) return false;
            return evaluate(map.get(fieldName), condition);
        }
        if (subject == null) {
            return false;
        }
        try {
            java.lang.reflect.Field field = subject.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object subjectValue = field.get(subject);
            return evaluate(subjectValue, condition);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return false;
        }
    }

    /** §7.4.12: a custom $op - delegates to a registered checker, or evaluates false (EC-13/EC-15). */
    private boolean customOperatorCheck(Object subject, String op, Object value) {
        CustomConditionChecker checker = customCheckers.get(op);
        if (checker != null) {
            return checker.check(subject, value);
        }
        if (declaredCustomOperators.contains(op)) {
            // EC-15: cataloged in meta.customOperators, but nothing was ever
            // registered for it at runtime - a worth-surfacing configuration
            // bug, unlike the general "uncataloged" case below.
            logTypeIssue(op, "declared in meta.customOperators but no checker is registered for it");
        }
        // EC-13: an unregistered, uncataloged operator is ordinary unmatched
        // vocabulary (a possible typo), not a type issue - no diagnostic.
        return false;
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    /**
     * §7.1: "Type issues are diagnosed, not silenced." Writes a
     * human-readable, error-level diagnostic identifying the operator and
     * what went wrong - used only for genuine type issues, never for an
     * ordinary non-match (a missing field, an unmatched action/subject, an
     * uncataloged custom operator).
     */
    private static void logTypeIssue(String operator, String message) {
        System.err.println(DIAGNOSTIC_PREFIX + " " + operator + ": " + message);
    }

    /**
     * §7.4.6: a small, non-regex substring pattern language. Parses into
     * anchors plus the literal segments between its wildcards; {@link
     * #parse} returns {@code null} for a structurally invalid pattern (an
     * unescaped "^" anywhere but the first character, or an unescaped "$"
     * anywhere but the last).
     */
    private static final class SubstrPattern {
        private final boolean anchorStart;
        private final boolean anchorEnd;
        private final List<String> segments;

        private SubstrPattern(boolean anchorStart, boolean anchorEnd, List<String> segments) {
            this.anchorStart = anchorStart;
            this.anchorEnd = anchorEnd;
            this.segments = segments;
        }

        static SubstrPattern parse(String raw) {
            int n = raw.length();
            int i = 0;
            boolean anchorStart = false;

            if (n > 0 && raw.charAt(0) == '^') {
                anchorStart = true;
                i = 1;
            }

            List<String> segments = new java.util.ArrayList<>();
            StringBuilder current = new StringBuilder();
            boolean anchorEnd = false;

            while (i < n) {
                char ch = raw.charAt(i);

                if (ch == '\\') {
                    char next = i + 1 < n ? raw.charAt(i + 1) : '\0';
                    if (next == '^' || next == '$' || next == '*' || next == '\\') {
                        current.append(next);
                        i += 2;
                    } else {
                        // A trailing "\" with nothing following, or "\" before
                        // a non-special character, is a literal "\".
                        current.append('\\');
                        i += 1;
                    }
                    continue;
                }

                if (ch == '*') {
                    int j = i + 1;
                    if (j < n && raw.charAt(j) == '*') j += 1; // "*" and "**" are match-equivalent.
                    segments.add(current.toString());
                    current = new StringBuilder();
                    i = j;
                    continue;
                }

                if (ch == '^') {
                    return null; // unescaped "^" not at the start
                }

                if (ch == '$') {
                    if (i == n - 1) {
                        anchorEnd = true;
                        i += 1;
                        continue;
                    }
                    return null; // unescaped "$" not at the end
                }

                current.append(ch);
                i += 1;
            }

            segments.add(current.toString());
            return new SubstrPattern(anchorStart, anchorEnd, segments);
        }

        boolean matches(String subject) {
            int n = subject.length();
            int lastIdx = segments.size() - 1;
            int cursor = 0;

            for (int idx = 0; idx <= lastIdx; idx++) {
                String seg = segments.get(idx);
                boolean isFirst = idx == 0;
                boolean isLast = idx == lastIdx;
                int minStart = isFirst ? 0 : cursor + 1; // a wildcard requires 1+ characters between segments
                boolean anchoredStart = isFirst && anchorStart;
                boolean anchoredEnd = isLast && anchorEnd;

                if (anchoredStart && anchoredEnd) {
                    if (subject.length() != seg.length() || !subject.equals(seg)) return false;
                    cursor = n;
                    continue;
                }

                if (anchoredStart) {
                    if (!subject.startsWith(seg)) return false;
                    cursor = seg.length();
                    continue;
                }

                if (anchoredEnd) {
                    int start = n - seg.length();
                    if (start < minStart || !subject.substring(start).equals(seg)) return false;
                    cursor = n;
                    continue;
                }

                int found = subject.indexOf(seg, minStart);
                if (found == -1) return false;
                cursor = found + seg.length();
            }

            return true;
        }
    }
}
