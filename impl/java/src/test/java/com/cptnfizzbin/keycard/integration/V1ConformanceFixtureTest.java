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
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

/**
 * Metaprogrammed: every case in every suite in every fixture file under
 * test/fixtures/v1 is discovered at test-run time (via {@link Parameterized})
 * and becomes its own case below. Dropping a new suite, or a new case into
 * an existing suite, adds coverage automatically - no new test code
 * required. See {@link V1Fixtures} and {@link ComplianceFixtures} for the
 * fixture format and the adapter this suite uses to run v1 fixtures
 * against the current, pre-v1 implementation.
 *
 * A fixture whose declared `version` isn't covered by
 * {@link #COMPLIANT_VERSION} - this suite's own baked-in ceiling, per
 * SPEC_V1-0-0.md §2's compatibility rule - is skipped (not failed) via
 * {@link org.junit.Assume}; see {@link ComplianceFixtures#isIncluded} for
 * the mechanics and {@link ComplianceFixtures#MAX_VERSION_PROPERTY} for the
 * knob that overrides it for a single run.
 */
@RunWith(Parameterized.class)
public class V1ConformanceFixtureTest {

    /**
     * The highest v1 SemVer this suite's adapter (see {@link V1Fixtures})
     * is written against. Baked into the suite itself - rather than left to
     * whatever an external default happens to be - so "which version this
     * runs compliant with" is a property of the code: bump it only once the
     * adapter has actually been updated to handle whatever a newer MINOR
     * version's fixtures add, not merely because such fixtures exist.
     */
    private static final String COMPLIANT_VERSION = "1.0.0";

    @Parameters(name = "{0} > {1} > {2}")
    public static Collection<Object[]> cases() throws IOException {
        List<Object[]> params = new ArrayList<>();
        for (Path fixtureFile : V1Fixtures.discoverFixtureFiles()) {
            for (V1Fixtures.Suite suite : V1Fixtures.loadSuites(fixtureFile)) {
                for (ComplianceFixtures.TestCase testCase : suite.cases()) {
                    params.add(new Object[] {
                        fixtureFile.getFileName().toString(), suite.name(), testCase.name(), suite, testCase
                    });
                }
            }
        }
        return params;
    }

    private final V1Fixtures.Suite suite;
    private final ComplianceFixtures.TestCase testCase;

    public V1ConformanceFixtureTest(
        String fixtureName, String suiteName, String caseName, V1Fixtures.Suite suite, ComplianceFixtures.TestCase testCase
    ) {
        this.suite = suite;
        this.testCase = testCase;
    }

    @Test
    public void resolvesExpectedResult() {
        assumeTrue(
            "suite version " + suite.version() + " exceeds this suite's compliant version " + COMPLIANT_VERSION,
            ComplianceFixtures.isIncluded(suite.version(), COMPLIANT_VERSION)
        );

        Policy policy = Policy.from(suite.legacyDefinition());

        assertEquals(testCase.name(), testCase.expected(), ComplianceFixtures.resolve(policy, testCase));
    }
}
