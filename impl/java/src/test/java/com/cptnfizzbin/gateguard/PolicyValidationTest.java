package com.cptnfizzbin.gateguard;

import org.junit.Test;

import com.cptnfizzbin.gateguard.action.ActionFactory;
import com.cptnfizzbin.gateguard.conditions.Operator;
import com.cptnfizzbin.gateguard.policy.Policy;
import com.cptnfizzbin.gateguard.policy.PolicyDefinition;
import com.cptnfizzbin.gateguard.errors.PolicyLoadException;
import com.cptnfizzbin.gateguard.errors.PolicyVersionException;
import com.cptnfizzbin.gateguard.subject.SubjectFactory;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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
        PolicyDefinition.Meta meta = PolicyDefinition.Meta.builder().operators(List.of("$hasRole")).build();

        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition("1.0.0", null, null, meta, List.of(
                new PolicyDefinition.Rule("allow", "Read", "Article", Map.of("$isAdmin", true))
            ))));
    }

    // --- Issue 3: operator registry collisions (SPEC_V1-0-0.md §3.2.3, EC-16) ---

    @Test
    public void throwsPolicyLoadExceptionWhenACustomOperatorCollidesWithABuiltin() {
        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition("1.0.0", List.of()),
                List.of(Operator.of("$eq", (s, v, ctx) -> true))));
    }

    @Test
    public void throwsPolicyLoadExceptionWhenTwoCustomOperatorsCollideWithEachOther() {
        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition("1.0.0", List.of()), List.of(
                Operator.of("$hasRole", (s, v, ctx) -> true),
                Operator.of("$hasRole", (s, v, ctx) -> false)
            )));
    }

    // --- Issue 4: meta.operators promotes "cataloged but never registered" to a construction-time throw (EC-15) ---

    @Test
    public void throwsPolicyLoadExceptionWhenMetaOperatorsDeclaresANameNothingIsRegisteredFor() {
        PolicyDefinition.Meta meta = PolicyDefinition.Meta.builder().operators(List.of("$hasRole")).build();

        // Unlike EC-13 above, this throws even though no rule references
        // $hasRole at all - meta.operators' registration requirement is
        // checked in full at construction time, not merely for names rules
        // actually use.
        assertThrows(PolicyLoadException.class, () ->
            Policy.from(new PolicyDefinition("1.0.0", null, null, meta, List.of())));
    }

    @Test
    public void metaOperatorsIsSatisfiedByABuiltinName() {
        PolicyDefinition.Meta meta = PolicyDefinition.Meta.builder().operators(List.of("$eq")).build();

        Policy.from(new PolicyDefinition("1.0.0", null, null, meta, List.of())); // should not throw
    }

    @Test
    public void metaOperatorsIsSatisfiedByARegisteredCustomOperator() {
        PolicyDefinition.Meta meta = PolicyDefinition.Meta.builder().operators(List.of("$hasRole")).build();

        Policy.from(
            new PolicyDefinition("1.0.0", null, null, meta, List.of()),
            List.of(Operator.of("$hasRole", (s, v, ctx) -> true))
        ); // should not throw
    }

    // --- Issue 5: meta.anyAction/meta.anySubject four-way dispatch (SPEC_V1-0-0.md §3.2.1) ---

    @Test
    public void falseDisablesTheActionWildcardJustLikeNull() {
        PolicyDefinition.Meta meta = PolicyDefinition.Meta.builder().anyAction(false).build();

        Policy policy = Policy.from(new PolicyDefinition("1.0.0", null, null, meta, List.of(
            new PolicyDefinition.Rule("allow", "_ANY_", "Article", null)
        )));

        // With the wildcard disabled, "_ANY_" is just an ordinary, literal
        // action name - it does not match "Read".
        assertTrue(policy.cannot(ActionFactory.create("Read"), SubjectFactory.create("Article")));
        assertTrue(policy.can(ActionFactory.create("_ANY_"), SubjectFactory.create("Article")));
    }

    @Test
    public void throwsPolicyLoadExceptionWhenAnyActionIsDeclaredTrue() {
        assertThrows(PolicyLoadException.class, () ->
            PolicyDefinition.Meta.builder().anyAction(true));
    }

    @Test
    public void throwsPolicyLoadExceptionWhenAnyActionIsDeclaredANonBooleanNonStringValue() {
        assertThrows(PolicyLoadException.class, () ->
            PolicyDefinition.Meta.builder().anyAction(42));
    }
}
