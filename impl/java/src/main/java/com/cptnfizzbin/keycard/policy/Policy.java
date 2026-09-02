package com.cptnfizzbin.keycard.policy;

import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.subject.SubjectDef;
import com.cptnfizzbin.keycard.subject.SubjectRef;
import com.cptnfizzbin.keycard.conditions.ConditionResolver;
import com.cptnfizzbin.keycard.conditions.CustomConditionChecker;
import com.cptnfizzbin.keycard.errors.PolicyException;
import com.cptnfizzbin.keycard.errors.PolicyLoadException;
import com.cptnfizzbin.keycard.errors.PolicyVersionException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Policy {
    /** The highest version this implementation supports natively - SPEC_V1-0-0.md §2. PATCH never affects compatibility. */
    private static final SemVer SUPPORTED_VERSION = SemVer.parse("1.0.0");

    private final PolicyDefinition definition;
    private final ConditionResolver resolver;

    public Policy(PolicyDefinition definition) {
        this(definition, (Map<String, CustomConditionChecker>) null);
    }

    public Policy(PolicyDefinition definition, Map<String, CustomConditionChecker> customCheckers) {
        validateVersion(definition.getVersion());
        validateRules(definition);

        this.definition = definition;
        PolicyDefinition.Meta meta = definition.getMeta();
        Set<String> declaredCustomOperators = meta != null && meta.getCustomOperators() != null
            ? new HashSet<>(meta.getCustomOperators())
            : null;
        this.resolver = new ConditionResolver(customCheckers, declaredCustomOperators);
    }

    /** Advanced escape hatch: supply an already-built {@link ConditionResolver} directly. */
    public Policy(PolicyDefinition definition, ConditionResolver resolver) {
        validateVersion(definition.getVersion());
        validateRules(definition);

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

    public static Policy from(PolicyDefinition definition, Map<String, CustomConditionChecker> customCheckers) {
        return new Policy(definition, customCheckers);
    }

    public static Policy from(PolicyDefinition definition, ConditionResolver resolver) {
        return new Policy(definition, resolver);
    }

    /** Alias of {@link #from(PolicyDefinition)}. */
    public static Policy fromDto(PolicyDefinition definition) {
        return from(definition);
    }

    /** Alias of {@link #from(PolicyDefinition, Map)}. */
    public static Policy fromDto(PolicyDefinition definition, Map<String, CustomConditionChecker> customCheckers) {
        return from(definition, customCheckers);
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

    /** A bare-type check (no instance) - EC-7/EC-9: a conditional rule can never match this. */
    public <T> boolean can(Action<?> action, SubjectDef<T> subject) {
        return checkPermission(action.getName(), subject.getName(), false, null);
    }

    /** A wrapped-instance check - conditional rules are eligible. */
    public <T> boolean can(Action<?> action, SubjectRef<T> subject) {
        return checkPermission(action.getName(), subject.getName(), true, subject.getValue());
    }

    /** A bare-type-name check (no instance). */
    public boolean can(String action, String subject) {
        return checkPermission(action, subject, false, null);
    }

    /** An instance check - `subject` carries the instance value conditions are evaluated against. */
    public boolean can(String action, Object subject) {
        String subjectName = getSubjectNameFromObject(subject);
        return checkPermission(action, subjectName, true, subject);
    }

    public <T> boolean cannot(Action<?> action, SubjectDef<T> subject) {
        return !can(action, subject);
    }

    public <T> boolean cannot(Action<?> action, SubjectRef<T> subject) {
        return !can(action, subject);
    }

    public boolean cannot(String action, String subject) {
        return !can(action, subject);
    }

    public boolean cannot(String action, Object subject) {
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

    /**
     * SPEC_V1-0-0.md §6: reverse scan over `rules`, returning the effect of
     * the first (i.e. most-recently-declared) rule whose action, subject,
     * and (if present) conditions all match. There is no independent
     * "allow AND NOT deny" veto and no combination of multiple matching
     * rules: exactly one rule decides the outcome, or none does and the
     * result is default deny.
     */
    private boolean checkPermission(String action, String subjectName, boolean hasInstance, Object subjectValue) {
        PolicyDefinition.Meta meta = definition.getMeta();
        String anyAction = Wildcards.effectiveAnyAction(meta);
        String anySubject = Wildcards.effectiveAnySubject(meta);
        List<PolicyDefinition.Rule> rules = definition.getRules();

        for (int i = rules.size() - 1; i >= 0; i--) {
            PolicyDefinition.Rule rule = rules.get(i);

            if (!Wildcards.matches(action, rule.getAction(), anyAction)) continue;
            if (!Wildcards.matches(subjectName, rule.getSubjectName(), anySubject)) continue;

            Map<String, Object> conditions = rule.getConditions();
            if (conditions != null) {
                // A conditional rule can never be satisfied by a bare-type/no-instance
                // check - there's no instance data for the condition to inspect (EC-7).
                if (!hasInstance) continue;
                if (!resolver.evaluate(subjectValue, conditions)) continue;
                return "allow".equals(rule.getEffect());
            }

            return "allow".equals(rule.getEffect());
        }

        return false; // EC-1, EC-2: default deny.
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

    private static void validateVersion(String version) {
        SemVer parsed;
        try {
            parsed = SemVer.parse(version);
        } catch (RuntimeException e) {
            throw new PolicyVersionException("Invalid policy version \"" + version + "\": " + e.getMessage());
        }
        if (!parsed.isCompatibleWith(SUPPORTED_VERSION)) {
            throw new PolicyVersionException(
                "Unsupported policy version \"" + version + "\": this implementation supports up to "
                    + SUPPORTED_VERSION.major() + "." + SUPPORTED_VERSION.minor() + ".x (SPEC_V1-0-0.md §2)."
            );
        }
    }

    private static void validateRules(PolicyDefinition definition) {
        PolicyDefinition.Meta meta = definition.getMeta();
        String anyAction = Wildcards.effectiveAnyAction(meta);
        String anySubject = Wildcards.effectiveAnySubject(meta);

        Set<String> actionsCatalog = meta != null && meta.getActions() != null ? new HashSet<>(meta.getActions()) : null;
        Set<String> subjectsCatalog = meta != null && meta.getSubjects() != null ? new HashSet<>(meta.getSubjects()) : null;
        Set<String> customOpCatalog = meta != null && meta.getCustomOperators() != null ? new HashSet<>(meta.getCustomOperators()) : null;

        for (PolicyDefinition.Rule rule : definition.getRules()) {
            String effect = rule.getEffect();
            String action = rule.getAction();
            String subjectName = rule.getSubjectName();
            Map<String, Object> conditions = rule.getConditions();

            if (!"allow".equals(effect) && !"deny".equals(effect)) {
                throw new PolicyLoadException(
                    "Malformed rule tuple: effect must be \"allow\" or \"deny\", got " + effect
                        + " (SPEC_V1-0-0.md §3.3, EC-10)."
                );
            }
            if (action == null) {
                throw new PolicyLoadException("Malformed rule tuple: action is required (SPEC_V1-0-0.md §3.3, EC-10).");
            }
            if (subjectName == null) {
                throw new PolicyLoadException("Malformed rule tuple: subject is required (SPEC_V1-0-0.md §3.3, EC-10).");
            }

            boolean isWildcardAction = anyAction != null && action.equals(anyAction);
            boolean isWildcardSubject = anySubject != null && subjectName.equals(anySubject);

            if (isWildcardAction && isWildcardSubject && conditions != null) {
                throw new PolicyLoadException(
                    "Rule [" + effect + ", " + action + ", " + subjectName
                        + "] is wildcarded on both the action and the subject but carries a Conditions element"
                        + " - this MUST be unconditional (SPEC_V1-0-0.md §6 property 5, EC-6)."
                );
            }

            if (actionsCatalog != null && !isWildcardAction && !actionsCatalog.contains(action)) {
                throw new PolicyLoadException(
                    "Rule action \"" + action + "\" is not covered by meta.actions (SPEC_V1-0-0.md §3.2.2, EC-8)."
                );
            }
            if (subjectsCatalog != null && !isWildcardSubject && !subjectsCatalog.contains(subjectName)) {
                throw new PolicyLoadException(
                    "Rule subject \"" + subjectName + "\" is not covered by meta.subjects (SPEC_V1-0-0.md §3.2.2, EC-8)."
                );
            }

            if (customOpCatalog != null && conditions != null) {
                Set<String> used = new HashSet<>();
                CustomOperators.collect(conditions, used);
                for (String op : used) {
                    if (!customOpCatalog.contains(op)) {
                        throw new PolicyLoadException(
                            "Rule uses custom operator \"" + op + "\" not covered by meta.customOperators (SPEC_V1-0-0.md §3.2.3, EC-13)."
                        );
                    }
                }
            }
        }
    }
}
