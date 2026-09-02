package com.cptnfizzbin.keycard.conditions;

import java.util.List;

/**
 * Pure combining logic for $or/$and/$not (SPEC_V1-0-0.md §7.4.7-§7.4.9) -
 * type-checking the operand and the required §7.1 diagnostic on failure
 * (including the vacuous-empty-array case) is the caller's job
 * (ConditionResolver), so {@link #or}/{@link #and} assume an
 * already-validated {@link List}. An empty list naturally falls out
 * correct here with no special-casing: zero iterations of `or` never
 * finds a match (false), zero iterations of `and` never finds a
 * counterexample (true).
 */
public final class LogicConditions {
    private LogicConditions() {}

    public static boolean or(ConditionResolver resolver, Object subject, List<?> conditions) {
        for (Object cond : conditions) {
            if (resolver.evaluate(subject, cond)) {
                return true;
            }
        }
        return false;
    }

    public static boolean and(ConditionResolver resolver, Object subject, List<?> conditions) {
        for (Object cond : conditions) {
            if (!resolver.evaluate(subject, cond)) {
                return false;
            }
        }
        return true;
    }

    public static boolean not(ConditionResolver resolver, Object subject, Object condition) {
        return !resolver.evaluate(subject, condition);
    }
}
