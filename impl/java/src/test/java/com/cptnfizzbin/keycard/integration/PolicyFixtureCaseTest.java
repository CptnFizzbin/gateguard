package com.cptnfizzbin.keycard.integration;

import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            for (PolicyFixtures.TestCase testCase : PolicyFixtures.loadTestCases(testFile)) {
                params.add(new Object[] { policyFile.getFileName().toString(), testCase.name(), policyDef, testCase });
            }
        }
        return params;
    }

    private final PolicyDefinition policyDef;
    private final PolicyFixtures.TestCase testCase;

    public PolicyFixtureCaseTest(String policyName, String caseName, PolicyDefinition policyDef, PolicyFixtures.TestCase testCase) {
        this.policyDef = policyDef;
        this.testCase = testCase;
    }

    @Test
    public void resolvesExpectedResult() {
        Policy policy = Policy.from(policyDef);

        boolean actual;
        if (testCase.subjectData() != null) {
            Map<String, Object> subjectMap = new HashMap<>(testCase.subjectData());
            subjectMap.put("__name", testCase.subject());
            actual = policy.can(testCase.action(), subjectMap);
        } else {
            actual = policy.can(testCase.action(), testCase.subject());
        }

        assertEquals(testCase.name(), testCase.expected(), actual);
    }
}
