package com.cptnfizzbin.keycard.builder;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.subject.SubjectDef;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PolicyBuilder {
    private final List<PolicyDefinition.Rule> allowRules = new ArrayList<>();
    private final List<PolicyDefinition.Rule> denyRules = new ArrayList<>();

    public <T> PolicyBuilder allow(Action<?> action, SubjectDef<T> subject) {
        return allow(action, subject, null);
    }

    public <T> PolicyBuilder allow(Action<?> action, SubjectDef<T> subject, Map<String, Object> conditions) {
        allowRules.add(new PolicyDefinition.Rule(action.getName(), subject.getName(), conditions));
        return this;
    }

    public <T> PolicyBuilder deny(Action<?> action, SubjectDef<T> subject) {
        return deny(action, subject, null);
    }

    public <T> PolicyBuilder deny(Action<?> action, SubjectDef<T> subject, Map<String, Object> conditions) {
        denyRules.add(new PolicyDefinition.Rule(action.getName(), subject.getName(), conditions));
        return this;
    }

    public Policy build() {
        return new Policy(buildDef());
    }

    public PolicyDefinition buildDef() {
        return new PolicyDefinition(1, allowRules, denyRules);
    }
}
