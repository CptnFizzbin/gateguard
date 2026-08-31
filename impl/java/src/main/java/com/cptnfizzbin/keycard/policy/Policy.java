package com.cptnfizzbin.keycard.policy;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.subject.SubjectDef;
import com.cptnfizzbin.keycard.subject.SubjectRef;
import com.cptnfizzbin.keycard.conditions.ConditionResolver;
import com.cptnfizzbin.keycard.errors.PolicyException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Policy {
    private final PolicyDefinition definition;
    private final ConditionResolver resolver;

    public Policy(PolicyDefinition definition) {
        this(definition, new ConditionResolver());
    }

    public Policy(PolicyDefinition definition, ConditionResolver resolver) {
        this.definition = definition;
        this.resolver = resolver != null ? resolver : new ConditionResolver();
    }

    /**
     * Builds a Policy from an already-parsed PolicyDefinition. KeyCard itself
     * never reads or writes policy.yaml text - an application (or a test,
     * via a YAML library of its own choosing) parses the file into a plain
     * PolicyDefinition and hands it to KeyCard.
     */
    public static Policy from(PolicyDefinition definition) {
        return new Policy(definition);
    }

    public static Policy from(PolicyDefinition definition, ConditionResolver resolver) {
        return new Policy(definition, resolver);
    }

    /** Alias of {@link #from(PolicyDefinition)}. */
    public static Policy fromDto(PolicyDefinition definition) {
        return from(definition);
    }

    /** Alias of {@link #from(PolicyDefinition, ConditionResolver)}. */
    public static Policy fromDto(PolicyDefinition definition, ConditionResolver resolver) {
        return from(definition, resolver);
    }

    public PolicyDefinition getDefinition() {
        return toDefinition();
    }

    /** Returns the PolicyDefinition backing this policy. */
    public PolicyDefinition toDefinition() {
        return definition;
    }

    /** Alias of {@link #toDefinition()}. */
    public PolicyDefinition toDto() {
        return toDefinition();
    }

    public <T> boolean can(Action<?> action, SubjectDef<T> subject) {
        return checkPermission(action, subject.getName(), null, false);
    }

    public <T> boolean can(Action<?> action, SubjectRef<T> subject) {
        return checkPermission(action, subject.getName(), subject.getValue(), false);
    }

    public boolean can(String action, String subject) {
        return checkPermissionString(action, subject, null, false);
    }

    public boolean can(String action, Object subject) {
        String subjectName = getSubjectNameFromObject(subject);
        return checkPermissionString(action, subjectName, subject, false);
    }

    public <T> boolean cannot(Action<?> action, SubjectDef<T> subject) {
        return !can(action, subject);
    }

    public <T> boolean cannot(Action<?> action, SubjectRef<T> subject) {
        return !can(action, subject);
    }

    public <T> void require(Action<?> action, SubjectDef<T> subject) throws PolicyException {
        if (!can(action, subject)) {
            throw new PolicyException("Access denied: cannot " + action.getName() + " on " + subject.getName());
        }
    }

    public <T> void require(Action<?> action, SubjectRef<T> subject) throws PolicyException {
        if (!can(action, subject)) {
            throw new PolicyException("Access denied: cannot " + action.getName() + " on " + subject.getName());
        }
    }

    public Policy append(PolicyDefinition other) {
        List<PolicyDefinition.Rule> combined = new ArrayList<>(definition.getAllowRules());
        combined.addAll(other.getAllowRules());
        List<PolicyDefinition.Rule> denyRules = new ArrayList<>(definition.getDenyRules());
        denyRules.addAll(other.getDenyRules());
        return new Policy(new PolicyDefinition(definition.getVersion(), combined, denyRules));
    }

    private boolean checkPermission(Action<?> action, String subjectName, Object subjectValue, boolean inverted) {
        List<PolicyDefinition.Rule> rules = inverted ? definition.getDenyRules() : definition.getAllowRules();
        
        for (PolicyDefinition.Rule rule : rules) {
            if (matchesAction(action, rule) && matchesSubject(subjectName, rule)) {
                if (rule.getConditions() == null || resolver.evaluate(subjectValue, rule.getConditions())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean checkPermissionString(String actionName, String subjectName, Object subjectValue, boolean inverted) {
        List<PolicyDefinition.Rule> rules = inverted ? definition.getDenyRules() : definition.getAllowRules();
        
        for (PolicyDefinition.Rule rule : rules) {
            if (matchesActionString(actionName, rule) && matchesSubject(subjectName, rule)) {
                if (rule.getConditions() == null || resolver.evaluate(subjectValue, rule.getConditions())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getSubjectNameFromObject(Object subject) {
        if (subject instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) subject;
            if (map.containsKey("__name")) {
                return (String) map.get("__name");
            }
        }
        return subject.getClass().getSimpleName();
    }

    private boolean matchesAction(Action<?> action, PolicyDefinition.Rule rule) {
        return action.getName().equals(rule.getAction()) || "*".equals(rule.getAction());
    }

    private boolean matchesActionString(String actionName, PolicyDefinition.Rule rule) {
        return actionName.equals(rule.getAction()) || "*".equals(rule.getAction());
    }

    private boolean matchesSubject(String subjectName, PolicyDefinition.Rule rule) {
        return subjectName.equals(rule.getSubjectName()) || "*".equals(rule.getSubjectName());
    }
}
