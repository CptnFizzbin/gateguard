package com.cptnfizzbin.gateguard.conditions;

/**
 * Passed to every {@link Operator}'s {@code resolve} call - built-in and
 * custom alike - so it can recurse into the condition language exactly the
 * way $and/$or/$not do (SPEC_V1-0-0.md §7.4.12). This is what gives a
 * custom, host-application-supplied operator the same recursive power a
 * built-in one has, rather than being limited to a flat
 * {@code (subject, value) -> boolean} check.
 */
public interface OperatorContext {
    /** Evaluates `condition` against `subject`, exactly as {@code ConditionResolver.evaluate} would. */
    boolean resolveSubcondition(Object subject, Object condition);
}
