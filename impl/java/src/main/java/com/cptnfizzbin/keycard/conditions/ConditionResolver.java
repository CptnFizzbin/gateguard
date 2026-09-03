package com.cptnfizzbin.keycard.conditions;

import com.cptnfizzbin.keycard.errors.PolicyLoadException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Implements SPEC_V1-0-0.md §7: the condition language and its evaluation
 * semantics. Built-in and custom {@link Operator}s share one registry and
 * are dispatched identically (§7.4.12) - this class is just the dispatch
 * loop: it looks a `$`-prefixed key up in that registry and delegates, or
 * narrows into a bare field name.
 */
public final class ConditionResolver implements OperatorContext {

    /** Every `$`-prefixed name {@link DefaultOperators} supplies natively - the single source of truth for "is this name built-in". */
    public static final Set<String> BUILTIN_OPERATORS = names(DefaultOperators.ALL);

    private final Map<String, Operator> registry;

    public ConditionResolver() {
        this(null);
    }

    /**
     * @param operators custom operators to register alongside {@link DefaultOperators} (§7.4.12) -
     *   built-in and custom operators share this one collection-based
     *   entry point. Constructing this with a name collision (a custom
     *   operator sharing a `$name` with a built-in, or with another
     *   operator in `operators`) MUST throw a {@link PolicyLoadException}
     *   immediately - never a silent overwrite (SPEC_V1-0-0.md §3.2.3, EC-16).
     */
    public ConditionResolver(Collection<Operator> operators) {
        this.registry = buildRegistry(operators);
    }

    /**
     * Every operator name actually registered on this resolver - built-in
     * and custom alike. Used by {@code Policy} to enforce {@code
     * meta.operators} registration coverage at construction time (§3.2.3,
     * EC-15).
     */
    public Set<String> registeredOperatorNames() {
        return registry.keySet();
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

    @Override
    public boolean resolveSubcondition(Object subject, Object condition) {
        return evaluate(subject, condition);
    }

    /**
     * §7.4.12, §7.5: any key starting with "$" is an operator lookup, never
     * a field name - built-in and custom operators are both resolved the
     * same way, by name, against the same registry.
     */
    private boolean evaluateKey(Object subject, String key, Object value) {
        if (!key.startsWith("$")) {
            return fieldCheck(subject, key, value);
        }

        Operator operator = registry.get(key);
        if (operator == null) {
            // §7.4.12, EC-13: an operator with no checker registered (built-in
            // or custom) MUST evaluate to false - never a no-op true, and
            // never treated as a field name. Not itself a required §7.1
            // diagnostic - and, unlike before, a cataloged-but-unregistered
            // name (EC-15) can no longer even reach this branch: `Policy`
            // now enforces meta.operators registration at construction time,
            // so any name still unregistered here was never cataloged.
            return false;
        }

        return operator.resolve(subject, value, this);
    }

    /** §7.4.10, §7.3: a missing field (or a non-object subject) makes the whole field-condition false - absence, not a type issue. */
    private boolean fieldCheck(Object subject, String fieldName, Object condition) {
        return FieldAccess.check(subject, fieldName, condition, this);
    }

    private static Map<String, Operator> buildRegistry(Collection<Operator> custom) {
        Map<String, Operator> map = new LinkedHashMap<>();
        for (Operator op : DefaultOperators.ALL) {
            map.put(op.name(), op);
        }
        if (custom != null) {
            for (Operator op : custom) {
                if (map.containsKey(op.name())) {
                    throw new PolicyLoadException(
                        "Duplicate operator \"" + op.name() + "\": an operator with this name is already registered"
                            + " (built-in or custom) - operator names MUST be unique (SPEC_V1-0-0.md §3.2.3, EC-16)."
                    );
                }
                map.put(op.name(), op);
            }
        }
        return Map.copyOf(map);
    }

    private static Set<String> names(Collection<Operator> operators) {
        Set<String> names = new LinkedHashSet<>();
        for (Operator op : operators) {
            names.add(op.name());
        }
        return Set.copyOf(names);
    }
}
