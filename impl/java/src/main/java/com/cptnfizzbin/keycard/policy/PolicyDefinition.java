package com.cptnfizzbin.keycard.policy;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public final class PolicyDefinition {
    private final int version;
    private final String name;
    private final String description;
    private final List<Rule> allowRules;
    private final List<Rule> denyRules;

    public PolicyDefinition(int version, List<Rule> allowRules, List<Rule> denyRules) {
        this(version, null, null, allowRules, denyRules);
    }

    public PolicyDefinition(int version, String name, String description, List<Rule> allowRules, List<Rule> denyRules) {
        this.version = version;
        this.name = name;
        this.description = description;
        this.allowRules = new ArrayList<>(allowRules != null ? allowRules : List.of());
        this.denyRules = new ArrayList<>(denyRules != null ? denyRules : List.of());
    }

    // getVersion/getName/getDescription come from @Data. getAllowRules/
    // getDenyRules stay hand-written below since they defensively copy -
    // @Data's plain getter would leak the mutable internal list.

    public List<Rule> getAllowRules() {
        return List.copyOf(allowRules);
    }

    public List<Rule> getDenyRules() {
        return List.copyOf(denyRules);
    }

    // Constructor and getters come entirely from @Data - no defensive
    // copying is needed here (conditions is handed to the resolver as-is
    // elsewhere too).
    @Data
    public static final class Rule {
        private final String action;
        private final String subjectName;
        private final Map<String, Object> conditions;
    }
}
