package com.cptnfizzbin.keycard.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PolicyDefinition)) return false;
        PolicyDefinition other = (PolicyDefinition) o;
        return version == other.version
            && java.util.Objects.equals(name, other.name)
            && java.util.Objects.equals(description, other.description)
            && allowRules.equals(other.allowRules)
            && denyRules.equals(other.denyRules);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(version, name, description, allowRules, denyRules);
    }

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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Rule)) return false;
            Rule other = (Rule) o;
            return java.util.Objects.equals(action, other.action)
                && java.util.Objects.equals(subjectName, other.subjectName)
                && java.util.Objects.equals(conditions, other.conditions);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(action, subjectName, conditions);
        }
    }
}
