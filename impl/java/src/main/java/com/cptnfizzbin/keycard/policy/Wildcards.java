package com.cptnfizzbin.keycard.policy;

/**
 * Shared wildcard-token resolution for {@link Policy} and {@code
 * PolicyBuilder} - SPEC_V1-0-0.md §3.2.1, §4, §5, §6.
 */
public final class Wildcards {
    private Wildcards() {}

    private static final WildcardToken.Named DEFAULT_WILDCARD = new WildcardToken.Named("_ANY_");

    /**
     * meta.anyAction: absent (a {@code null} {@link PolicyDefinition.Meta#getAnyAction()})
     * -&gt; the "_ANY_" default; otherwise whatever {@link WildcardToken}
     * was declared ({@link WildcardToken.Disabled} or {@link WildcardToken.Named}).
     */
    public static WildcardToken effectiveAnyAction(PolicyDefinition.Meta meta) {
        if (meta == null || meta.getAnyAction() == null) return DEFAULT_WILDCARD;
        return meta.getAnyAction();
    }

    /** meta.anySubject: symmetric with {@link #effectiveAnyAction} in every respect. */
    public static WildcardToken effectiveAnySubject(PolicyDefinition.Meta meta) {
        if (meta == null || meta.getAnySubject() == null) return DEFAULT_WILDCARD;
        return meta.getAnySubject();
    }

    /** True when `value` matches `ruleValue` exactly, or `ruleValue` is the (non-disabled) wildcard token. */
    public static boolean matches(String value, String ruleValue, WildcardToken any) {
        if (value.equals(ruleValue)) return true;
        return any instanceof WildcardToken.Named named && ruleValue.equals(named.token());
    }
}
