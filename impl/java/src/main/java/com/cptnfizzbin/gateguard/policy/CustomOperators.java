package com.cptnfizzbin.gateguard.policy;

import com.cptnfizzbin.gateguard.conditions.ConditionResolver;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recursively collects every non-built-in, {@code $}-prefixed operator
 * name used anywhere in a Conditions tree - used to enforce {@code
 * meta.operators} coverage at construction time (§3.2.3, EC-13).
 */
final class CustomOperators {
    private CustomOperators() {}

    static void collect(Object condition, Set<String> out) {
        if (!(condition instanceof Map)) return;

        Map<?, ?> map = (Map<?, ?>) condition;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();

            if (key.startsWith("$")) {
                if (!ConditionResolver.BUILTIN_OPERATORS.contains(key)) out.add(key);
                if (key.equals("$or") || key.equals("$and")) {
                    if (value instanceof List) {
                        for (Object c : (List<?>) value) collect(c, out);
                    }
                } else if (key.equals("$not")) {
                    collect(value, out);
                } else if (key.equals("$field") && value instanceof List && ((List<?>) value).size() == 2) {
                    collect(((List<?>) value).get(1), out);
                }
            } else {
                collect(value, out);
            }
        }
    }
}
