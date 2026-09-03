package com.cptnfizzbin.gateguard.conditions;

import java.util.Map;

/**
 * §7.4.10, §7.4.11: bare-key and `$field` long-form field access - shared
 * by {@link ConditionResolver}'s non-`$`-prefixed dispatch and the `$field`
 * {@link Operator} in {@link DefaultOperators}, since both resolve to the
 * exact same "look up a named field on the subject, then recurse" behavior.
 */
final class FieldAccess {
    private FieldAccess() {}

    /** §7.4.10, §7.3: a missing field (or a non-object subject) makes the whole field-condition false - absence, not a type issue. */
    static boolean check(Object subject, String fieldName, Object condition, OperatorContext ctx) {
        if (subject instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) subject;
            if (!map.containsKey(fieldName)) return false;
            return ctx.resolveSubcondition(map.get(fieldName), condition);
        }
        if (subject == null) {
            return false;
        }
        try {
            java.lang.reflect.Field field = subject.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object subjectValue = field.get(subject);
            return ctx.resolveSubcondition(subjectValue, condition);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return false;
        }
    }
}
