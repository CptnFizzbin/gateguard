package com.cptnfizzbin.keycard.builder;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.subject.SubjectDef;
import com.cptnfizzbin.keycard.conditions.Operator;
import com.cptnfizzbin.keycard.errors.PolicyArgumentException;
import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;
import com.cptnfizzbin.keycard.policy.Wildcards;
import com.cptnfizzbin.keycard.policy.WildcardToken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class PolicyBuilder {
    /** The v1 SemVer this builder implements - stamped onto every buildDef() output, per SPEC_V1-0-0.md §2. Single-sourced from {@link Policy#SUPPORTED_VERSION} so the two can never drift apart. */
    public static final String BUILDER_VERSION = Policy.SUPPORTED_VERSION.toString();

    private final List<PolicyDefinition.Rule> rules = new ArrayList<>();
    private final PolicyDefinition.Meta meta;
    private final Collection<Operator> operators;

    public PolicyBuilder() {
        this(null, null);
    }

    public PolicyBuilder(PolicyDefinition.Meta meta) {
        this(meta, null);
    }

    public PolicyBuilder(Collection<Operator> operators) {
        this(null, operators);
    }

    /**
     * @param operators custom operators this builder's eventual {@link #build()}
     *   should register alongside the built-ins (SPEC_V1-0-0.md §7.4.12) -
     *   carried through to {@link Policy}'s own constructor, so a
     *   builder-produced definition can carry its custom operators through
     *   consistently rather than requiring them to be re-supplied at
     *   {@code Policy.from(...)} time.
     */
    public PolicyBuilder(PolicyDefinition.Meta meta, Collection<Operator> operators) {
        this.meta = meta;
        this.operators = operators;
    }

    // --- allow(...)/deny(...) -----------------------------------------------
    // Every combination of `action: String | Action<?>` and
    // `subject: String | SubjectDef<?>` is supported - a rule references a
    // subject *type*, never an instance, so unlike `Policy.can`/`cannot`/
    // `require` there's no SubjectRef-shaped overload here.

    public PolicyBuilder allow(String action, String subject) {
        return allow(action, subject, null);
    }

    public PolicyBuilder allow(String action, String subject, Map<String, Object> conditions) {
        return addRule("allow", action, subject, conditions);
    }

    public PolicyBuilder allow(String action, SubjectDef<?> subject) {
        return allow(action, subject, null);
    }

    public PolicyBuilder allow(String action, SubjectDef<?> subject, Map<String, Object> conditions) {
        return addRule("allow", action, subject.getName(), conditions);
    }

    public PolicyBuilder allow(Action<?> action, String subject) {
        return allow(action, subject, null);
    }

    public PolicyBuilder allow(Action<?> action, String subject, Map<String, Object> conditions) {
        return addRule("allow", action.getName(), subject, conditions);
    }

    public PolicyBuilder allow(Action<?> action, SubjectDef<?> subject) {
        return allow(action, subject, null);
    }

    public PolicyBuilder allow(Action<?> action, SubjectDef<?> subject, Map<String, Object> conditions) {
        return addRule("allow", action.getName(), subject.getName(), conditions);
    }

    public PolicyBuilder deny(String action, String subject) {
        return deny(action, subject, null);
    }

    public PolicyBuilder deny(String action, String subject, Map<String, Object> conditions) {
        return addRule("deny", action, subject, conditions);
    }

    public PolicyBuilder deny(String action, SubjectDef<?> subject) {
        return deny(action, subject, null);
    }

    public PolicyBuilder deny(String action, SubjectDef<?> subject, Map<String, Object> conditions) {
        return addRule("deny", action, subject.getName(), conditions);
    }

    public PolicyBuilder deny(Action<?> action, String subject) {
        return deny(action, subject, null);
    }

    public PolicyBuilder deny(Action<?> action, String subject, Map<String, Object> conditions) {
        return addRule("deny", action.getName(), subject, conditions);
    }

    public PolicyBuilder deny(Action<?> action, SubjectDef<?> subject) {
        return deny(action, subject, null);
    }

    public PolicyBuilder deny(Action<?> action, SubjectDef<?> subject, Map<String, Object> conditions) {
        return addRule("deny", action.getName(), subject.getName(), conditions);
    }

    public Policy build() {
        return new Policy(buildDef(), operators);
    }

    public PolicyDefinition buildDef() {
        return new PolicyDefinition(BUILDER_VERSION, null, null, meta, rules);
    }

    private PolicyBuilder addRule(String effect, String action, String subjectName, Map<String, Object> conditions) {
        if (conditions != null) {
            // SPEC_V1-0-0.md §6 property 5, EC-6: a rule wildcarded on both
            // the action and the subject MUST NOT carry a Conditions element
            // - the builder MUST catch this immediately, rather than waiting
            // for eventual construction (Policy.from) to catch it.
            WildcardToken anyAction = Wildcards.effectiveAnyAction(meta);
            WildcardToken anySubject = Wildcards.effectiveAnySubject(meta);
            boolean actionIsWildcard = anyAction instanceof WildcardToken.Named named && action.equals(named.token());
            boolean subjectIsWildcard = anySubject instanceof WildcardToken.Named named && subjectName.equals(named.token());
            if (actionIsWildcard && subjectIsWildcard) {
                throw new PolicyArgumentException("rules with any action and any subject cannot be conditional");
            }
        }

        rules.add(new PolicyDefinition.Rule(effect, action, subjectName, conditions));
        return this;
    }
}
