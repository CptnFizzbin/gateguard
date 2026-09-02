package com.cptnfizzbin.keycard.policy;

/**
 * Shared wildcard-token resolution for {@link Policy} and {@code
 * PolicyBuilder} - SPEC_V1-0-0.md §3.2.1, §4, §5, §6.
 */
public final class Wildcards {
    private Wildcards() {}

    private static final String DEFAULT_WILDCARD = "_ANY_";

    /**
     * meta.anyAction: absent -&gt; "_ANY_" default; explicit string -&gt; that
     * string; explicit null -&gt; disabled (represented here as a Java
     * {@code null} return - safe, since a real action name is always a
     * non-null String, so callers guard with {@code anyToken != null}
     * before comparing).
     */
    public static String effectiveAnyAction(PolicyDefinition.Meta meta) {
        if (meta == null || !meta.isAnyActionDeclared()) return DEFAULT_WILDCARD;
        return meta.getAnyAction();
    }

    /** meta.anySubject: symmetric with {@link #effectiveAnyAction} in every respect. */
    public static String effectiveAnySubject(PolicyDefinition.Meta meta) {
        if (meta == null || !meta.isAnySubjectDeclared()) return DEFAULT_WILDCARD;
        return meta.getAnySubject();
    }

    /** True when `value` matches `ruleValue` exactly, or `ruleValue` is the (non-disabled) wildcard token. */
    public static boolean matches(String value, String ruleValue, String anyToken) {
        return value.equals(ruleValue) || (anyToken != null && ruleValue.equals(anyToken));
    }
}
