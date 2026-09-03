package com.cptnfizzbin.gateguard.version;

/**
 * The v1 SemVer this implementation speaks (SPEC_V1-0-0.md §2) - the
 * single source of truth {@link com.cptnfizzbin.gateguard.policy.Policy}'s
 * {@code SUPPORTED_VERSION}, {@link
 * com.cptnfizzbin.gateguard.builder.PolicyBuilder}'s {@code
 * BUILDER_VERSION}, and the compliance-fixture test suites' baked-in
 * compliant version all read from, so the three can never drift apart.
 */
public final class GateGuardVersion {
    private GateGuardVersion() {}

    public static final String GATEGUARD_POLICY_VERSION = "1.0.0";
}
