package com.cptnfizzbin.keycard.builder;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.subject.SubjectDef;
import com.cptnfizzbin.keycard.errors.PolicyArgumentException;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;
import com.cptnfizzbin.keycard.policy.Wildcards;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PolicyBuilder {
    /** The v1 SemVer this builder implements - stamped onto every buildDef() output, per SPEC_V1-0-0.md §2. Single-sourced from {@link Policy#SUPPORTED_VERSION} so the two can never drift apart. */
    public static final String BUILDER_VERSION = Policy.SUPPORTED_VERSION.toString();

    private final List<PolicyDefinition.Rule> rules = new ArrayList<>();
    private final PolicyDefinition.Meta meta;

    public PolicyBuilder() {
        this(null);
    }

    public PolicyBuilder(PolicyDefinition.Meta meta) {
        this.meta = meta;
    }

    public <T> PolicyBuilder allow(Action<?> action, SubjectDef<T> subject) {
        return allow(action, subject, null);
    }

    public <T> PolicyBuilder allow(Action<?> action, SubjectDef<T> subject, Map<String, Object> conditions) {
        return addRule("allow", action, subject, conditions);
    }

    public <T> PolicyBuilder deny(Action<?> action, SubjectDef<T> subject) {
        return deny(action, subject, null);
    }

    public <T> PolicyBuilder deny(Action<?> action, SubjectDef<T> subject, Map<String, Object> conditions) {
        return addRule("deny", action, subject, conditions);
    }

    public Policy build() {
        return new Policy(buildDef());
    }

    public PolicyDefinition buildDef() {
        return new PolicyDefinition(BUILDER_VERSION, null, null, meta, rules);
    }

    private <T> PolicyBuilder addRule(String effect, Action<?> action, SubjectDef<T> subject, Map<String, Object> conditions) {
        if (conditions != null) {
            // SPEC_V1-0-0.md §6 property 5, EC-6: a rule wildcarded on both
            // the action and the subject MUST NOT carry a Conditions element
            // - the builder MUST catch this immediately, rather than waiting
            // for eventual construction (Policy.from) to catch it.
            String anyAction = Wildcards.effectiveAnyAction(meta);
            String anySubject = Wildcards.effectiveAnySubject(meta);
            boolean actionIsWildcard = anyAction != null && action.getName().equals(anyAction);
            boolean subjectIsWildcard = anySubject != null && subject.getName().equals(anySubject);
            if (actionIsWildcard && subjectIsWildcard) {
                throw new PolicyArgumentException("rules with any action and any subject cannot be conditional");
            }
        }

        rules.add(new PolicyDefinition.Rule(effect, action.getName(), subject.getName(), conditions));
        return this;
    }
}
