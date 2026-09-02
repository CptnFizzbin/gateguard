package com.cptnfizzbin.keycard.conditions;

/**
 * A runtime checker for a custom, non-built-in {@code $}-prefixed
 * condition operator (SPEC_V1-0-0.md §7.4.12). Registered on a {@code
 * Policy}/{@code ConditionResolver} at construction time; a
 * {@code PolicyDefinition} can declare an operator's name in {@code
 * meta.customOperators} but cannot carry its behavior - that's supplied
 * separately by the host application via this interface.
 */
@FunctionalInterface
public interface ConditionChecker {
    boolean check(Object subject, Object value);
}
