package com.cptnfizzbin.keycard;

import org.junit.Test;

import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;
import com.cptnfizzbin.keycard.errors.PolicyLoadException;
import com.cptnfizzbin.keycard.errors.PolicyVersionException;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertThrows;

/** Construction-time validation required by SPEC_V1-0-0.md but not covered by the allow/deny-outcome-only v1 conformance suite (see test/fixtures/v1/README.md's Scope section). */
public class PolicyValidationTest {

    @Test
    public void throwsPolicyVersionExceptionForAnUnsupportedMajorVersion() {
        assertThrows(PolicyVersionException.class, () ->
            Policy.from(new PolicyDefinition("2.0.0", List.of())));
    }

    @Test
    public void throwsPolicyVersionExceptionForAMinorNewerThanWhatsSupported() {
        assertThrows(PolicyVersionException.class, () ->
            Policy.from(new PolicyDefinition("1.99.0", List.of())));
    }

    @Test
    public void ignoresPatchWhenDecidingCompatibility() {
        Policy.from(new PolicyDefinition("1.0.99", List.of())); // should not throw
    }

    @Test
    public void throwsPolicyLoadExceptionForAMalformedRuleTuple() {
        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition("1.0.0", List.of(
                new PolicyDefinition.Rule("maybe", "Read", "Article", null)
            ))));
    }

    @Test
    public void throwsPolicyLoadExceptionForARuleWildcardedOnBothSidesCarryingACondition() {
        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition("1.0.0", List.of(
                new PolicyDefinition.Rule("allow", "_ANY_", "_ANY_", Map.of("owner_id", 1))
            ))));
    }

    @Test
    public void throwsPolicyLoadExceptionWhenARulesActionIsntCoveredByADeclaredCatalog() {
        PolicyDefinition.Meta meta = PolicyDefinition.Meta.builder().actions(List.of("Read")).build();

        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition("1.0.0", null, null, meta, List.of(
                new PolicyDefinition.Rule("allow", "Write", "Article", null)
            ))));
    }

    @Test
    public void throwsPolicyLoadExceptionWhenARuleUsesACustomOperatorOutsideADeclaredCatalog() {
        PolicyDefinition.Meta meta = PolicyDefinition.Meta.builder().customOperators(List.of("$hasRole")).build();

        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition("1.0.0", null, null, meta, List.of(
                new PolicyDefinition.Rule("allow", "Read", "Article", Map.of("$isAdmin", true))
            ))));
    }
}
