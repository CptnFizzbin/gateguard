package com.cptnfizzbin.keycard.conditions;

import com.cptnfizzbin.keycard.conditions.numberConditions.NumberConditions;
import com.cptnfizzbin.keycard.conditions.stringConditions.StringConditions;
import com.cptnfizzbin.keycard.conditions.groupConditions.GroupConditions;
import com.cptnfizzbin.keycard.conditions.logicConditions.LogicConditions;

import java.util.Collection;
import java.util.Map;

public final class ConditionResolver {

    public boolean evaluate(Object subject, Object condition) {
        if (condition == null) {
            return true;
        }

        if (!(condition instanceof Map)) {
            return subject != null && subject.equals(condition);
        }

        Map<String, Object> condMap = (Map<String, Object>) condition;

        for (Map.Entry<String, Object> entry : condMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            switch (key) {
                case "$eq":
                    if (!StringConditions.eq(subject, value)) return false;
                    break;
                case "$ne":
                    if (!StringConditions.ne(subject, value)) return false;
                    break;
                case "$gt":
                    if (!(subject instanceof Number) || !(value instanceof Number)) return false;
                    if (!NumberConditions.gt((Number) subject, (Number) value)) return false;
                    break;
                case "$gte":
                    if (!(subject instanceof Number) || !(value instanceof Number)) return false;
                    if (!NumberConditions.gte((Number) subject, (Number) value)) return false;
                    break;
                case "$lt":
                    if (!(subject instanceof Number) || !(value instanceof Number)) return false;
                    if (!NumberConditions.lt((Number) subject, (Number) value)) return false;
                    break;
                case "$lte":
                    if (!(subject instanceof Number) || !(value instanceof Number)) return false;
                    if (!NumberConditions.lte((Number) subject, (Number) value)) return false;
                    break;
                case "$in":
                    if (!GroupConditions.in(subject, value)) return false;
                    break;
                case "$has":
                    if (!GroupConditions.has(subject, value)) return false;
                    break;
                case "$rgx":
                    if (!StringConditions.rgx(subject, value)) return false;
                    break;
                case "$or":
                    if (!LogicConditions.or(this, subject, value)) return false;
                    break;
                case "$and":
                    if (!LogicConditions.and(this, subject, value)) return false;
                    break;
                case "$not":
                    if (!LogicConditions.not(this, subject, value)) return false;
                    break;
                default:
                    if (key.startsWith("$")) {
                        return false;
                    }
                    if (subject instanceof Map) {
                        Object subjectValue = ((Map<?, ?>) subject).get(key);
                        if (!evaluate(subjectValue, value)) return false;
                    } else {
                        try {
                            java.lang.reflect.Field field = subject.getClass().getDeclaredField(key);
                            field.setAccessible(true);
                            Object subjectValue = field.get(subject);
                            if (!evaluate(subjectValue, value)) return false;
                        } catch (NoSuchFieldException | IllegalAccessException e) {
                            return false;
                        }
                    }
            }
        }
        return true;
    }
}
