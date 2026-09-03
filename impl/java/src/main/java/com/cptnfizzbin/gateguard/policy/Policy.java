package com.cptnfizzbin.gateguard.policy;

import com.cptnfizzbin.gateguard.action.Action;
import com.cptnfizzbin.gateguard.subject.SubjectDef;
import com.cptnfizzbin.gateguard.subject.SubjectRef;
import com.cptnfizzbin.gateguard.conditions.ConditionResolver;
import com.cptnfizzbin.gateguard.conditions.Operator;
import com.cptnfizzbin.gateguard.errors.PolicyException;
import com.cptnfizzbin.gateguard.errors.PolicyLoadException;
import com.cptnfizzbin.gateguard.errors.PolicyVersionException;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Policy {
    /**
     * The highest version this implementation supports natively -
     * SPEC_V1-0-0.md §2. PATCH never affects compatibility. Also the
     * single source of truth for {@link com.cptnfizzbin.gateguard.builder.PolicyBuilder#BUILDER_VERSION},
     * so a builder can never stamp a version this same implementation
     * would then refuse to load.
     */
    public static final SemVer SUPPORTED_VERSION = SemVer.parse("1.0.0");

    private final PolicyDefinition definition;
    private final ConditionResolver resolver;

    public Policy(PolicyDefinition definition) {
        this(definition, (Collection<Operator>) null);
    }

    /**
     * @param operators custom operators to register alongside the
     *   built-ins (SPEC_V1-0-0.md §7.4.12) - a single collection-based
     *   entry point shared with {@code PolicyBuilder}, so a
     *   builder-produced definition can carry its operators through
     *   consistently.
     */
    public Policy(PolicyDefinition definition, Collection<Operator> operators) {
        validateVersion(definition.getVersion());

        this.definition = definition;
        this.resolver = new ConditionResolver(operators);
        validateOperatorsRegistered(definition, resolver);
        validateRules(definition);
    }

    /** Advanced escape hatch: supply an already-built {@link ConditionResolver} directly. */
    public Policy(PolicyDefinition definition, ConditionResolver resolver) {
        validateVersion(definition.getVersion());

        this.definition = definition;
        this.resolver = resolver != null ? resolver : new ConditionResolver();
        validateOperatorsRegistered(definition, this.resolver);
        validateRules(definition);
    }

    /**
     * Builds a Policy from an already-parsed PolicyDefinition. GateGuard itself
     * never reads or writes policy.yaml text - an application (or a test,
     * via a YAML library of its own choosing) parses the file into a plain
     * PolicyDefinition and hands it to GateGuard.
     */
    public static Policy from(PolicyDefinition definition) {
        return new Policy(definition);
    }

    public static Policy from(PolicyDefinition definition, Collection<Operator> operators) {
        return new Policy(definition, operators);
    }

    public static Policy from(PolicyDefinition definition, ConditionResolver resolver) {
        return new Policy(definition, resolver);
    }

    /** Alias of {@link #from(PolicyDefinition)}. */
    public static Policy fromDto(PolicyDefinition definition) {
        return from(definition);
    }

    /** Alias of {@link #from(PolicyDefinition, Collection)}. */
    public static Policy fromDto(PolicyDefinition definition, Collection<Operator> operators) {
        return from(definition, operators);
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

    // --- can(...) ---------------------------------------------------------
    // Every combination of `action: String | Action<?>` and
    // `subject: String | SubjectDef<?> | SubjectRef<?>` is supported, per
    // the unified action/subject parameter contract shared with `allow`/
    // `deny`/`cannot`/`require` - all six forward to `checkPermission`
    // below. `can(String, Object)` additionally covers a raw instance value
    // (e.g. a POJO or a Map) whose subject name is inferred from it.

    /** A bare-type-name check (no instance). */
    public boolean can(String action, String subject) {
        return checkPermission(action, subject, false, null);
    }

    /** An instance check - `subject` carries the instance value conditions are evaluated against. */
    public boolean can(String action, Object subject) {
        String subjectName = getSubjectNameFromObject(subject);
        return checkPermission(action, subjectName, true, subject);
    }

    /** A bare-type check (no instance) - EC-7/EC-9: a conditional rule can never match this. */
    public boolean can(String action, SubjectDef<?> subject) {
        return checkPermission(action, subject.getName(), false, null);
    }

    /** A wrapped-instance check - conditional rules are eligible. */
    public boolean can(String action, SubjectRef<?> subject) {
        return checkPermission(action, subject.getName(), true, subject.getValue());
    }

    public boolean can(Action<?> action, String subject) {
        return checkPermission(action.getName(), subject, false, null);
    }

    /** A bare-type check (no instance) - EC-7/EC-9: a conditional rule can never match this. */
    public boolean can(Action<?> action, SubjectDef<?> subject) {
        return checkPermission(action.getName(), subject.getName(), false, null);
    }

    /** A wrapped-instance check - conditional rules are eligible. */
    public boolean can(Action<?> action, SubjectRef<?> subject) {
        return checkPermission(action.getName(), subject.getName(), true, subject.getValue());
    }

    // --- cannot(...) --------------------------------------------------------

    public boolean cannot(String action, String subject) {
        return !can(action, subject);
    }

    public boolean cannot(String action, Object subject) {
        return !can(action, subject);
    }

    public boolean cannot(String action, SubjectDef<?> subject) {
        return !can(action, subject);
    }

    public boolean cannot(String action, SubjectRef<?> subject) {
        return !can(action, subject);
    }

    public boolean cannot(Action<?> action, String subject) {
        return !can(action, subject);
    }

    public boolean cannot(Action<?> action, SubjectDef<?> subject) {
        return !can(action, subject);
    }

    public boolean cannot(Action<?> action, SubjectRef<?> subject) {
        return !can(action, subject);
    }

    // --- require(...) --------------------------------------------------------

    public void require(String action, String subject) throws PolicyException {
        requireCan(can(action, subject), action, subject);
    }

    public void require(String action, Object subject) throws PolicyException {
        requireCan(can(action, subject), action, subject);
    }

    public void require(String action, SubjectDef<?> subject) throws PolicyException {
        requireCan(can(action, subject), action, subject.getName());
    }

    public void require(String action, SubjectRef<?> subject) throws PolicyException {
        requireCan(can(action, subject), action, subject.getName());
    }

    public void require(Action<?> action, String subject) throws PolicyException {
        requireCan(can(action, subject), action.getName(), subject);
    }

    public void require(Action<?> action, SubjectDef<?> subject) throws PolicyException {
        requireCan(can(action, subject), action.getName(), subject.getName());
    }

    public void require(Action<?> action, SubjectRef<?> subject) throws PolicyException {
        requireCan(can(action, subject), action.getName(), subject.getName());
    }

    private static void requireCan(boolean allowed, String action, Object subject) throws PolicyException {
        if (!allowed) {
            throw new PolicyException("Access denied: cannot " + action + " on " + subject);
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
        WildcardToken anyAction = Wildcards.effectiveAnyAction(meta);
        WildcardToken anySubject = Wildcards.effectiveAnySubject(meta);
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

    /**
     * §3.2.3, EC-15 (promoted): when {@code meta.operators} is declared,
     * every name it lists MUST already be registered on this Policy - built
     * -in or custom - checked once here at construction time, regardless of
     * whether any rule actually reaches that operator during evaluation.
     * This replaces the previous behavior of deferring an unregistered-but
     * -cataloged name to a runtime-only diagnostic.
     */
    private static void validateOperatorsRegistered(PolicyDefinition definition, ConditionResolver resolver) {
        PolicyDefinition.Meta meta = definition.getMeta();
        List<String> declared = meta != null ? meta.getOperators() : null;
        if (declared == null) return;

        Set<String> registered = resolver.registeredOperatorNames();
        for (String name : declared) {
            if (!registered.contains(name)) {
                throw new PolicyLoadException(
                    "meta.operators declares \"" + name + "\" but no operator with that name is registered"
                        + " (built-in or custom) (SPEC_V1-0-0.md §3.2.3, EC-15)."
                );
            }
        }
    }

    private static void validateRules(PolicyDefinition definition) {
        PolicyDefinition.Meta meta = definition.getMeta();
        WildcardToken anyAction = Wildcards.effectiveAnyAction(meta);
        WildcardToken anySubject = Wildcards.effectiveAnySubject(meta);

        Set<String> actionsCatalog = meta != null && meta.getActions() != null ? new HashSet<>(meta.getActions()) : null;
        Set<String> subjectsCatalog = meta != null && meta.getSubjects() != null ? new HashSet<>(meta.getSubjects()) : null;
        Set<String> operatorsCatalog = meta != null && meta.getOperators() != null ? new HashSet<>(meta.getOperators()) : null;

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

            boolean isWildcardAction = anyAction instanceof WildcardToken.Named named && action.equals(named.token());
            boolean isWildcardSubject = anySubject instanceof WildcardToken.Named named && subjectName.equals(named.token());

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

            if (operatorsCatalog != null && conditions != null) {
                Set<String> used = new HashSet<>();
                CustomOperators.collect(conditions, used);
                for (String op : used) {
                    if (!operatorsCatalog.contains(op)) {
                        throw new PolicyLoadException(
                            "Rule uses custom operator \"" + op + "\" not covered by meta.operators (SPEC_V1-0-0.md §3.2.3, EC-13)."
                        );
                    }
                }
            }
        }
    }
}
