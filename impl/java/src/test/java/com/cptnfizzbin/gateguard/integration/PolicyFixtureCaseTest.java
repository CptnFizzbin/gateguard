package com.cptnfizzbin.gateguard.integration;

import com.cptnfizzbin.gateguard.policy.Policy;
import com.cptnfizzbin.gateguard.policy.PolicyDefinition;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Metaprogrammed: every test case in every `policy-*.test.yaml` companion
 * under test/fixtures/policies is discovered at test-run time (via
 * {@link Parameterized}) and becomes its own case below. Adding a new
 * fixture pair, or a new case to an existing companion file, adds coverage
 * automatically - no new test code required.
 */
@RunWith(Parameterized.class)
public class PolicyFixtureCaseTest {

    @Parameters(name = "{0}: {1}")
    public static Collection<Object[]> cases() throws IOException {
        List<Object[]> params = new ArrayList<>();
        for (Path policyFile : PolicyFixtures.discoverPolicyFiles()) {
            Path testFile = PolicyFixtures.testFileFor(policyFile);
            if (!Files.exists(testFile)) {
                continue;
            }

            PolicyDefinition policyDef = PolicyFixtures.loadPolicyDefinition(policyFile);
            for (ComplianceFixtures.TestCase testCase : PolicyFixtures.loadTestCases(testFile)) {
                params.add(new Object[] { policyFile.getFileName().toString(), testCase.name(), policyDef, testCase });
            }
        }
        return params;
    }

    private final PolicyDefinition policyDef;
    private final ComplianceFixtures.TestCase testCase;

    public PolicyFixtureCaseTest(
        String policyName, String caseName, PolicyDefinition policyDef, ComplianceFixtures.TestCase testCase
    ) {
        this.policyDef = policyDef;
        this.testCase = testCase;
    }

    @Test
    public void resolvesExpectedResult() {
        Policy policy = Policy.from(policyDef);

        assertEquals(testCase.name(), testCase.expected(), ComplianceFixtures.resolve(policy, testCase));
    }
}
