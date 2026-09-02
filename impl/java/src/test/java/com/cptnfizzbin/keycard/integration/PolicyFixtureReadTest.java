package com.cptnfizzbin.keycard.integration;

import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Metaprogrammed: every `policy-*.yaml` fixture under test/fixtures/policies
 * is discovered at test-run time (via {@link Parameterized}) and becomes
 * its own case below. Dropping a new fixture file into that directory adds
 * coverage automatically - no new test code required.
 */
@RunWith(Parameterized.class)
public class PolicyFixtureReadTest {

    @Parameters(name = "{0}")
    public static Collection<Object[]> fixtures() throws IOException {
        List<Object[]> params = new ArrayList<>();
        for (Path policyFile : PolicyFixtures.discoverPolicyFiles()) {
            params.add(new Object[] { policyFile.getFileName().toString(), policyFile });
        }
        return params;
    }

    private final String policyName;
    private final Path policyPath;

    public PolicyFixtureReadTest(String policyName, Path policyPath) {
        this.policyName = policyName;
        this.policyPath = policyPath;
    }

    @Test
    public void successfullyReadsThePolicyYamlFile() throws IOException {
        PolicyDefinition def = PolicyFixtures.loadPolicyDefinition(policyPath);

        assertNotNull("version should parse to a non-null SemVer string", def.getVersion());
        assertNotNull("rules should parse to a list", def.getRules());
    }

    @Test
    public void policyFromDefinitionToDefinitionRoundTrips() throws IOException {
        PolicyDefinition policyDef = PolicyFixtures.loadPolicyDefinition(policyPath);
        Policy policy = Policy.from(policyDef, PolicyFixtures.checkersFor(policyName));

        assertEquals(policyDef, policy.toDefinition());
    }

    @Test
    public void fromDtoToDtoAliasesRoundTrip() throws IOException {
        PolicyDefinition policyDef = PolicyFixtures.loadPolicyDefinition(policyPath);

        assertEquals(policyDef, Policy.fromDto(policyDef, PolicyFixtures.checkersFor(policyName)).toDto());
    }
}
