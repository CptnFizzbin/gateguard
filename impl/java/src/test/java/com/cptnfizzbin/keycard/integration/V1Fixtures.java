package com.cptnfizzbin.keycard.integration;

import com.cptnfizzbin.keycard.policy.PolicyDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared loading helpers for the v1 conformance fixtures under
 * test/fixtures/v1 (see the README there) - the shared, spec-derived
 * fixtures every implementation MUST read, per SPEC_V1-0-0.md §6. Not a
 * test class itself - see V1ConformanceFixtureTest.
 *
 * KeyCard itself never reads or writes policy.yaml text; parsing it into a
 * plain PolicyDefinition (via SnakeYaml, a test-only dependency) is this
 * test suite's job, mirroring what an application would do.
 *
 * impl/java has not been migrated to the v1 rules/meta schema yet (see
 * KNOWN_ISSUES.md): its PolicyDefinition is still the pre-v1
 * allowRules/denyRules shape, driven by an "allow AND NOT deny" check
 * rather than v1's reverse-scan last-rule-wins, and its wildcard token is
 * hardcoded to "*" rather than reading meta.anyAction/meta.anySubject.
 * {@link #toLegacyDefinition} is a best-effort adapter that reshapes a
 * parsed v1 suite into that pre-v1 shape so this suite can still exercise
 * the current implementation - it does not attempt to emulate
 * last-rule-wins, wildcard tokens, or meta catalogs. Cases that depend on
 * that v1-only behavior are therefore EXPECTED TO FAIL against the current
 * implementation; that gap is what this suite exists to make visible, not
 * a defect in the fixtures. Once impl/java adopts the v1 schema natively,
 * this adapter should be replaced with passing the parsed definition
 * straight through.
 *
 * File discovery, YAML parsing, the per-case shape, and can()-resolution
 * are shared with every other compliance suite via {@link ComplianceFixtures}.
 */
final class V1Fixtures {
    private V1Fixtures() {}

    static final Path FIXTURES_DIR = Paths.get("../../test/fixtures/v1");

    /** All `*.yaml` fixture files under test/fixtures/v1, sorted by name. */
    static List<Path> discoverFixtureFiles() throws IOException {
        return ComplianceFixtures.discoverYamlFiles(FIXTURES_DIR, p -> true);
    }

    /** One `---`-separated `{ version, name, rules, cases }` document from a fixture file. */
    record Suite(String version, String name, PolicyDefinition legacyDefinition, List<ComplianceFixtures.TestCase> cases) {}

    @SuppressWarnings("unchecked")
    static List<Suite> loadSuites(Path yamlFile) throws IOException {
        String content = Files.readString(yamlFile);
        List<Suite> suites = new ArrayList<>();

        for (Object rawDoc : ComplianceFixtures.YAML.loadAll(content)) {
            Map<String, Object> raw = (Map<String, Object>) rawDoc;
            String version = String.valueOf(raw.get("version"));
            String name = (String) raw.get("name");

            List<PolicyDefinition.Rule> allow = new ArrayList<>();
            List<PolicyDefinition.Rule> deny = new ArrayList<>();
            for (Object o : (List<?>) raw.get("rules")) {
                List<?> tuple = (List<?>) o;
                String effect = String.valueOf(tuple.get(0));
                String action = String.valueOf(tuple.get(1));
                String subjectName = String.valueOf(tuple.get(2));
                Map<String, Object> conditions = tuple.size() > 3 ? (Map<String, Object>) tuple.get(3) : null;
                PolicyDefinition.Rule rule = new PolicyDefinition.Rule(action, subjectName, conditions);
                ("allow".equals(effect) ? allow : deny).add(rule);
            }
            PolicyDefinition legacyDefinition = new PolicyDefinition(1, name, null, allow, deny);

            List<ComplianceFixtures.TestCase> cases = new ArrayList<>();
            for (Map<String, Object> rc : (List<Map<String, Object>>) raw.get("cases")) {
                String action = (String) rc.get("action");
                String subject = (String) rc.get("subject");
                Object expected = rc.get("expected");
                String caseName = rc.get("name") != null
                    ? (String) rc.get("name")
                    : action + " / " + subject + " -> " + expected;
                cases.add(new ComplianceFixtures.TestCase(
                    caseName,
                    action,
                    subject,
                    (Map<String, Object>) rc.get("subjectData"),
                    "allow".equals(expected)
                ));
            }

            suites.add(new Suite(version, name, legacyDefinition, cases));
        }

        return suites;
    }
}
