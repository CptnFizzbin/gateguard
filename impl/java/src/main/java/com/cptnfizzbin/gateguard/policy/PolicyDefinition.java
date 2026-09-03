package com.cptnfizzbin.gateguard.policy;

import lombok.AllArgsConstructor;
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

    // Hand-written: null-defaults and defensively copies allowRules/denyRules,
    // which a generated @AllArgsConstructor wouldn't do.
    public PolicyDefinition(int version, String name, String description, List<Rule> allowRules, List<Rule> denyRules) {
        this.version = version;
        this.name = name;
        this.description = description;
        this.allowRules = new ArrayList<>(allowRules != null ? allowRules : List.of());
        this.denyRules = new ArrayList<>(denyRules != null ? denyRules : List.of());
    }

    // Hand-written: defensively copies, unlike a generated @Getter.
    public List<Rule> getAllowRules() {
        return List.copyOf(allowRules);
    }

    public List<Rule> getDenyRules() {
        return List.copyOf(denyRules);
    }

    @Data
    @AllArgsConstructor
    public static final class Rule {
        private final String action;
        private final String subjectName;
        private final Map<String, Object> conditions;
    }
}
