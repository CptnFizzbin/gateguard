package com.cptnfizzbin.keycard.conditions;

import com.cptnfizzbin.keycard.conditions.stringConditions.StringConditions;
import com.cptnfizzbin.keycard.conditions.groupConditions.GroupConditions;
import com.cptnfizzbin.keycard.conditions.logicConditions.LogicConditions;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

/**
 * Implements SPEC_V1-0-0.md §7: the condition language and its
 * evaluation semantics.
 */
public final class ConditionResolver {

    private static final String DIAGNOSTIC_PREFIX = "[KeyCard]";

    /** A built-in operator's implementation - takes the resolving instance (for recursive evaluate() calls and diagnostics) plus the subject/operand. */
    @FunctionalInterface
    private interface BuiltinOperator {
        boolean check(ConditionResolver resolver, Object subject, Object value);
    }

    /**
     * Every operator this resolver understands natively (§7.4.1-§7.4.11),
     * keyed by its `$`-prefixed name - the single source of truth for both
     * dispatch and "is this name built-in" (no separately-maintained name
     * list to fall out of sync with it).
     */
    private static final Map<String, BuiltinOperator> BUILTIN_OPERATOR_IMPLS = Map.ofEntries(
        Map.entry("$eq", (r, s, v) -> StringConditions.eq(s, v)),
        Map.entry("$ne", (r, s, v) -> StringConditions.ne(s, v)),
        Map.entry("$gt", (r, s, v) -> r.numericCompare("$gt", s, v, (a, b) -> a > b)),
        Map.entry("$gte", (r, s, v) -> r.numericCompare("$gte", s, v, (a, b) -> a >= b)),
        Map.entry("$lt", (r, s, v) -> r.numericCompare("$lt", s, v, (a, b) -> a < b)),
        Map.entry("$lte", (r, s, v) -> r.numericCompare("$lte", s, v, (a, b) -> a <= b)),
        Map.entry("$in", (r, s, v) -> r.inCheck(s, v)),
        Map.entry("$has", (r, s, v) -> r.hasCheck(s, v)),
        Map.entry("$substr", (r, s, v) -> r.substrCheck(s, v)),
        Map.entry("$or", (r, s, v) -> r.orCheck(s, v)),
        Map.entry("$and", (r, s, v) -> r.andCheck(s, v)),
        Map.entry("$not", (r, s, v) -> !r.evaluate(s, v)),
        Map.entry("$field", (r, s, v) -> r.fieldOpCheck(s, v))
    );

    /** Every `$`-prefixed key this resolver understands natively - anything else starting with "$" is a custom operator lookup (§7.4.12). */
    public static final Set<String> BUILTIN_OPERATORS = BUILTIN_OPERATOR_IMPLS.keySet();

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

    /**
     * §7.4.12, §7.5: any key starting with "$" is an operator lookup, never
     * a field name - built-in and custom operators are both resolved the
     * same way, by name, against their respective registries.
     */
    private boolean evaluateKey(Object subject, String key, Object value) {
        if (!key.startsWith("$")) {
            return fieldCheck(subject, key, value);
        }

        BuiltinOperator builtin = BUILTIN_OPERATOR_IMPLS.get(key);
        if (builtin != null) {
            return builtin.check(this, subject, value);
        }

        return customOperatorCheck(subject, key, value);
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

    /**
     * §7.4.12: a custom $op - delegates to a registered checker, or
     * evaluates false (EC-13/EC-15).
     *
     * Note on §3.2.3 vs. EC-15: §3.2.3's closing sentence ("implementations
     * MUST throw a PolicyLoadException if a behavior is not provided to the
     * constructor") read literally would mean any cataloged-but-unregistered
     * operator throws at construction time - but EC-15 explicitly requires
     * the opposite (resolves to false, with a diagnostic, and MUST NOT
     * throw), and that's also what
     * test/fixtures/v1/09-custom-operators.yaml's "cataloged but
     * never-registered" case expects. This method follows EC-15 and the
     * conformance suite; treat §3.2.3's closing sentence as an error in the
     * spec prose rather than normative behavior to implement.
     */
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
     * §7.4.6: a small, non-regex substring pattern language, implemented by
     * compiling it to a Java regex - the spec explicitly permits this
     * ("Implementations MAY implement $substr however they like internally
     * (including compiling it to the host language's native regex engine,
     * e.g. translating `*`/`**` to `.+` and escaping literal segments)").
     * {@link #parse} returns {@code null} for a structurally invalid
     * pattern (an unescaped "^" anywhere but the first character, or an
     * unescaped "$" anywhere but the last).
     */
    private static final class SubstrPattern {
        private final Pattern compiled;

        private SubstrPattern(Pattern compiled) {
            this.compiled = compiled;
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

            // Translate to a regex: each literal segment quoted verbatim, a
            // wildcard becomes ".+" (one-or-more, DOTALL so it's truly "any
            // character"), and the leading/trailing anchors carry straight
            // through as regex anchors.
            StringBuilder regex = new StringBuilder();
            if (anchorStart) regex.append('^');
            for (int idx = 0; idx < segments.size(); idx++) {
                if (idx > 0) regex.append(".+");
                regex.append(Pattern.quote(segments.get(idx)));
            }
            if (anchorEnd) regex.append('$');

            return new SubstrPattern(Pattern.compile(regex.toString(), Pattern.DOTALL));
        }

        boolean matches(String subject) {
            return compiled.matcher(subject).find();
        }
    }
}
