package com.cptnfizzbin.keycard.conditions;

import java.util.LinkedHashMap;
import java.util.Map;

/** Assembles a name -&gt; implementation registry from a list of {@link Operator}s. */
final class OperatorMap {
    private OperatorMap() {}

    static Map<String, Operator.Impl> of(Operator... operators) {
        Map<String, Operator.Impl> map = new LinkedHashMap<>();
        for (Operator operator : operators) {
            map.put(operator.name(), operator.impl());
        }
        return Map.copyOf(map);
    }
}
