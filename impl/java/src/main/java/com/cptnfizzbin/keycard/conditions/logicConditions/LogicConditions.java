package com.cptnfizzbin.keycard.conditions.logicConditions;

import com.cptnfizzbin.keycard.conditions.ConditionResolver;

import java.util.List;
import java.util.Map;

public final class LogicConditions {
    private LogicConditions() {}

    public static boolean or(ConditionResolver resolver, Object subject, Object conditions) {
        if (!(conditions instanceof List)) {
            return false;
        }
        List<?> condList = (List<?>) conditions;
        for (Object cond : condList) {
            if (resolver.evaluate(subject, cond)) {
                return true;
            }
        }
        return false;
    }

    public static boolean and(ConditionResolver resolver, Object subject, Object conditions) {
        if (!(conditions instanceof List)) {
            return false;
        }
        List<?> condList = (List<?>) conditions;
        for (Object cond : condList) {
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
