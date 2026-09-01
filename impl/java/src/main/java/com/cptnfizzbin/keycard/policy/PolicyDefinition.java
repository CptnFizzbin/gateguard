package com.cptnfizzbin.keycard.policy;

import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode
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

    public int getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<Rule> getAllowRules() {
        return List.copyOf(allowRules);
    }

    public List<Rule> getDenyRules() {
        return List.copyOf(denyRules);
    }

    @EqualsAndHashCode
    public static final class Rule {
        private final String action;
        private final String subjectName;
        private final Map<String, Object> conditions;

        public Rule(String action, String subjectName, Map<String, Object> conditions) {
            this.action = action;
            this.subjectName = subjectName;
            this.conditions = conditions;
        }

        public String getAction() {
            return action;
        }

        public String getSubjectName() {
            return subjectName;
        }

        public Map<String, Object> getConditions() {
            return conditions;
        }
    }
}
