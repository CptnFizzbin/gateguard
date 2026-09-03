package com.cptnfizzbin.keycard.conditions;

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

    /**
     * Every operator this resolver understands natively (§7.4.1-§7.4.11),
     * keyed by its `$`-prefixed name - the single source of truth for both
     * dispatch and "is this name built-in" (no separately-maintained name
     * list to fall out of sync with it). Assembled from the named {@link
     * DefaultOperators} constants via {@link OperatorMap#of}, rather than
     * an ad hoc Map.ofEntries(...) that duplicates each name as both a map
     * key and a lambda's identity.
     */
    private static final Map<String, Operator.Impl> BUILTIN_OPERATOR_IMPLS = OperatorMap.of(
        DefaultOperators.EQ,
        DefaultOperators.NE,
        DefaultOperators.GT,
        DefaultOperators.GTE,
        DefaultOperators.LT,
        DefaultOperators.LTE,
        DefaultOperators.IN,
        DefaultOperators.HAS,
        DefaultOperators.SUBSTR,
        DefaultOperators.OR,
        DefaultOperators.AND,
        DefaultOperators.NOT,
        DefaultOperators.FIELD
    );

    /** Every `$`-prefixed key this resolver understands natively - anything else starting with "$" is a custom operator lookup (§7.4.12). */
    public static final Set<String> BUILTIN_OPERATORS = BUILTIN_OPERATOR_IMPLS.keySet();

    private final Map<String, ConditionChecker> checkers;
    private final Set<String> declaredCustomOperators;

    public ConditionResolver() {
        this(null, null);
    }

    public ConditionResolver(Map<String, ConditionChecker> checkers) {
        this(checkers, null);
    }

    /**
     * @param checkers runtime checkers for custom $op operators (§7.4.12).
     * @param declaredCustomOperators the policy's meta.customOperators catalog, if any -
     *   used only to distinguish EC-13 (uncataloged, no diagnostic) from EC-15
     *   (cataloged but never registered, diagnostic required).
     */
    public ConditionResolver(Map<String, ConditionChecker> checkers, Set<String> declaredCustomOperators) {
        this.checkers = checkers != null ? Map.copyOf(checkers) : Map.of();
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

        Operator.Impl builtin = BUILTIN_OPERATOR_IMPLS.get(key);
        if (builtin != null) {
            return builtin.check(this, subject, value);
        }

        return customOperatorCheck(subject, key, value);
    }

    /** §7.4.3: $gt/$gte/$lt/$lte - numeric-only, IEEE-754 double semantics. Package-private: called from {@link DefaultOperators}'s implementations too. */
    boolean numericCompare(String op, Object subject, Object operand, BiPredicate<Double, Double> cmp) {
        if (!(subject instanceof Number) || !(operand instanceof Number)) {
            logTypeIssue(op, "expected the subject and operand to both be numbers, got "
                + typeName(subject) + " and " + typeName(operand));
            return false;
        }
        double a = ((Number) subject).doubleValue();
        double b = ((Number) operand).doubleValue();
        return cmp.test(a, b);
    }

    /** §7.4.4: $in - operand must be a collection; containment uses $eq semantics per element. Package-private: called from {@link DefaultOperators}. */
    boolean inCheck(Object subject, Object operand) {
        if (!(operand instanceof List)) {
            logTypeIssue("$in", "expected an array operand, got " + typeName(operand));
            return false;
        }
        return GroupConditions.in(subject, (List<?>) operand);
    }

    /** §7.4.5: $has - subject must be a collection. Package-private: called from {@link DefaultOperators}. */
    boolean hasCheck(Object subject, Object value) {
        if (!(subject instanceof List)) {
            logTypeIssue("$has", "expected an array subject, got " + typeName(subject));
            return false;
        }
        return GroupConditions.has((List<?>) subject, value);
    }

    /** §7.4.6: $substr - a null subject is an ordinary non-match, not a type issue; an invalid pattern always is. Package-private: called from {@link DefaultOperators}. */
    boolean substrCheck(Object subject, Object pattern) {
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

    /** §7.4.7: $or - operand must be an array; {@code $or: []} is vacuously false. Package-private: called from {@link DefaultOperators}. */
    boolean orCheck(Object subject, Object operand) {
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

    /** §7.4.8: $and - operand must be an array; {@code $and: []} is vacuously true. Package-private: called from {@link DefaultOperators}. */
    boolean andCheck(Object subject, Object operand) {
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

    /** §7.4.11: $field - explicit field access, for a field whose name itself starts with "$". Package-private: called from {@link DefaultOperators}. */
    boolean fieldOpCheck(Object subject, Object operand) {
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
        ConditionChecker checker = checkers.get(op);
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
     * e.g. translating `*` to `.*` and escaping literal segments)").
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
            StringBuilder regex = new StringBuilder();
            int n = raw.length();

            for (int i = 0; i < n; i++) {
                char c = raw.charAt(i);

                switch (c) {
                    case '\\':
                        // "\\" escapes the very next character, whatever it
                        // is, to a literal - a trailing "\\" with nothing
                        // following it is simply ignored.
                        if (i + 1 >= n) break;
                        regex.append(Pattern.quote(String.valueOf(raw.charAt(i + 1))));
                        i++; // skip the escaped character
                        break;
                    case '*':
                        // Zero or more characters; a run of consecutive "*"
                        // is match-equivalent to a single one.
                        regex.append(".*");
                        break;
                    case '^':
                        // Only meaningful as the pattern's first character -
                        // anywhere else it's a structurally invalid pattern.
                        if (i != 0) return null;
                        regex.append('^');
                        break;
                    case '$':
                        // Only meaningful as the pattern's last character.
                        if (i != n - 1) return null;
                        regex.append('$');
                        break;
                    default:
                        regex.append(Pattern.quote(String.valueOf(c)));
                }
            }

            // DOTALL so "." (from ".*") truly means "any character".
            return new SubstrPattern(Pattern.compile(regex.toString(), Pattern.DOTALL));
        }

        boolean matches(String subject) {
            return compiled.matcher(subject).find();
        }
    }
}
