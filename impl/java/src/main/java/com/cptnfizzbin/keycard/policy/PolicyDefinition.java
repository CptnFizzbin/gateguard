package com.cptnfizzbin.keycard.policy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The PolicyDefinition document shape - SPEC_V1-0-0.md §3. */
@Data
public final class PolicyDefinition {
    /** Required SemVer string, e.g. "1.0.0" - see SPEC_V1-0-0.md §2. */
    private final String version;
    /** Informational only - plays no role in evaluation. */
    private final String name;
    /** Informational only - plays no role in evaluation. */
    private final String description;
    private final Meta meta;
    private final List<Rule> rules;

    public PolicyDefinition(String version, List<Rule> rules) {
        this(version, null, null, null, rules);
    }

    // Hand-written: null-defaults and defensively copies rules, which a
    // generated @AllArgsConstructor wouldn't do.
    public PolicyDefinition(String version, String name, String description, Meta meta, List<Rule> rules) {
        this.version = version;
        this.name = name;
        this.description = description;
        this.meta = meta;
        this.rules = new ArrayList<>(rules != null ? rules : List.of());
    }

    // Hand-written: defensively copies, unlike a generated @Getter.
    public List<Rule> getRules() {
        return List.copyOf(rules);
    }

    /** `[Effect, Action, Subject, Conditions?]` - SPEC_V1-0-0.md §3.3. Ordered; declaration order is significant (§6). */
    @Data
    @AllArgsConstructor
    public static final class Rule {
        /** MUST be "allow" or "deny" - anything else is a malformed rule tuple (EC-10). */
        private final String effect;
        private final String action;
        private final String subjectName;
        /** Nullable - a rule with no conditions is unconditional. */
        private final Map<String, Object> conditions;
    }

    /**
     * SPEC_V1-0-0.md §3.2: the optional `meta` object, grouping six
     * independent, all-optional fields. Constructed via {@link #builder()}
     * since `anyAction`/`anySubject` are each tri-state (unset - the
     * "_ANY_" default; an explicit string; or explicit `null`, disabling
     * that wildcard position) and a plain nullable field can't distinguish
     * "unset" from "explicitly null".
     */
    @Getter
    @ToString
    @EqualsAndHashCode
    public static final class Meta {
        private final boolean anyActionDeclared;
        private final String anyAction;
        private final boolean anySubjectDeclared;
        private final String anySubject;
        private final List<String> actions;
        private final List<String> subjects;
        private final List<String> customOperators;
        private final Object application;

        private Meta(
            boolean anyActionDeclared, String anyAction,
            boolean anySubjectDeclared, String anySubject,
            List<String> actions, List<String> subjects, List<String> customOperators, Object application
        ) {
            this.anyActionDeclared = anyActionDeclared;
            this.anyAction = anyAction;
            this.anySubjectDeclared = anySubjectDeclared;
            this.anySubject = anySubject;
            this.actions = actions != null ? List.copyOf(actions) : null;
            this.subjects = subjects != null ? List.copyOf(subjects) : null;
            this.customOperators = customOperators != null ? List.copyOf(customOperators) : null;
            this.application = application;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private boolean anyActionDeclared = false;
            private String anyAction;
            private boolean anySubjectDeclared = false;
            private String anySubject;
            private List<String> actions;
            private List<String> subjects;
            private List<String> customOperators;
            private Object application;

            /** §3.2.1: declares the action wildcard token explicitly - pass {@code null} to disable it entirely. */
            public Builder anyAction(String value) {
                this.anyActionDeclared = true;
                this.anyAction = value;
                return this;
            }

            /** §3.2.1: declares the subject wildcard token explicitly - pass {@code null} to disable it entirely. */
            public Builder anySubject(String value) {
                this.anySubjectDeclared = true;
                this.anySubject = value;
                return this;
            }

            public Builder actions(List<String> value) {
                this.actions = value;
                return this;
            }

            public Builder subjects(List<String> value) {
                this.subjects = value;
                return this;
            }

            public Builder customOperators(List<String> value) {
                this.customOperators = value;
                return this;
            }

            public Builder application(Object value) {
                this.application = value;
                return this;
            }

            public Meta build() {
                return new Meta(
                    anyActionDeclared, anyAction, anySubjectDeclared, anySubject,
                    actions, subjects, customOperators, application
                );
            }
        }
    }
}
