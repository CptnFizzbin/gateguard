package com.cptnfizzbin.keycard.conditions;

/**
 * A named, built-in condition-operator implementation - pairs a
 * `$`-prefixed operator name with the function that implements it, so the
 * two travel together as one reusable constant (see {@link
 * DefaultOperators}) instead of being assembled ad hoc at each call site.
 */
final class Operator {
    private final String name;
    private final Impl impl;

    Operator(String name, Impl impl) {
        this.name = name;
        this.impl = impl;
    }

    String name() {
        return name;
    }

    Impl impl() {
        return impl;
    }

    /** An operator's implementation - takes the resolving instance (for recursive evaluate() calls and diagnostics) plus the subject/operand. */
    @FunctionalInterface
    interface Impl {
        boolean check(ConditionResolver resolver, Object subject, Object value);
    }
}
