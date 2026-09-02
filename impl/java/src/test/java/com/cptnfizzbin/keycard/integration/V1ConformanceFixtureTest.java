package com.cptnfizzbin.keycard.integration;

import com.cptnfizzbin.keycard.policy.Policy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Metaprogrammed: every case in every suite in every fixture file under
 * test/fixtures/v1 is discovered at test-run time (via {@link Parameterized})
 * and becomes its own case below. Dropping a new suite, or a new case into
 * an existing suite, adds coverage automatically - no new test code
 * required. See {@link V1Fixtures} for the fixture format and the adapter
 * this suite uses to run v1 fixtures against the current, pre-v1
 * implementation.
 */
@RunWith(Parameterized.class)
public class V1ConformanceFixtureTest {

    @Parameters(name = "{0} > {1} > {2}")
    public static Collection<Object[]> cases() throws IOException {
        List<Object[]> params = new ArrayList<>();
        for (Path fixtureFile : V1Fixtures.discoverFixtureFiles()) {
            for (V1Fixtures.Suite suite : V1Fixtures.loadSuites(fixtureFile)) {
                for (V1Fixtures.TestCase testCase : suite.cases()) {
                    params.add(new Object[] {
                        fixtureFile.getFileName().toString(), suite.name(), testCase.name(), suite, testCase
                    });
                }
            }
        }
        return params;
    }

    private final V1Fixtures.Suite suite;
    private final V1Fixtures.TestCase testCase;

    public V1ConformanceFixtureTest(
        String fixtureName, String suiteName, String caseName, V1Fixtures.Suite suite, V1Fixtures.TestCase testCase
    ) {
        this.suite = suite;
        this.testCase = testCase;
    }

    @Test
    public void resolvesExpectedResult() {
        Policy policy = Policy.from(suite.legacyDefinition());

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
