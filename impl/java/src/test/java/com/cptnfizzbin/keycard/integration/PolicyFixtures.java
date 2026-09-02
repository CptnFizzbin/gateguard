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
 * Shared loading helpers for the fixture-driven integration tests. Not a
 * test class itself - see PolicyFixtureReadTest and PolicyFixtureCaseTest.
 *
 * KeyCard itself never reads or writes policy.yaml text; parsing it into a
 * plain PolicyDefinition (via SnakeYaml, a test-only dependency) is this
 * test suite's job, mirroring what an application would do.
 *
 * File discovery, YAML parsing, and the per-case shape are shared with
 * every other compliance suite via {@link ComplianceFixtures}.
 */
final class PolicyFixtures {
    private PolicyFixtures() {}

    static final Path FIXTURES_DIR = Paths.get("../../test/fixtures/policies");

    /** All `policy-*.yaml` fixtures (excluding their `.test.yaml` companions), sorted by name. */
    static List<Path> discoverPolicyFiles() throws IOException {
        return ComplianceFixtures.discoverYamlFiles(FIXTURES_DIR, p -> !p.getFileName().toString().endsWith(".test.yaml"));
    }

    /** The `*.test.yaml` companion path for a given `*.yaml` policy fixture. */
    static Path testFileFor(Path policyFile) {
        String name = policyFile.getFileName().toString();
        String testName = name.substring(0, name.length() - ".yaml".length()) + ".test.yaml";
        return policyFile.resolveSibling(testName);
    }

    @SuppressWarnings("unchecked")
    static PolicyDefinition loadPolicyDefinition(Path yamlFile) throws IOException {
        String content = Files.readString(yamlFile);
        Map<String, Object> raw = ComplianceFixtures.YAML.load(content);

        int version = raw.get("version") != null ? ((Number) raw.get("version")).intValue() : 1;
        String name = (String) raw.get("name");
        String description = (String) raw.get("description");

        List<PolicyDefinition.Rule> allow = toRules((List<?>) raw.getOrDefault("allow", List.of()));
        List<PolicyDefinition.Rule> deny = toRules((List<?>) raw.getOrDefault("deny", List.of()));

        return new PolicyDefinition(version, name, description, allow, deny);
    }

    @SuppressWarnings("unchecked")
    private static List<PolicyDefinition.Rule> toRules(List<?> rawRules) {
        List<PolicyDefinition.Rule> rules = new ArrayList<>();
        for (Object o : rawRules) {
            List<?> tuple = (List<?>) o;
            String action = String.valueOf(tuple.get(0));
            String subjectName = String.valueOf(tuple.get(1));
            Map<String, Object> conditions = tuple.size() > 2 ? (Map<String, Object>) tuple.get(2) : null;
            rules.add(new PolicyDefinition.Rule(action, subjectName, conditions));
        }
        return rules;
    }

    @SuppressWarnings("unchecked")
    static List<ComplianceFixtures.TestCase> loadTestCases(Path testYamlFile) throws IOException {
        String content = Files.readString(testYamlFile);
        Map<String, Object> raw = ComplianceFixtures.YAML.load(content);
        List<Map<String, Object>> rawCases = (List<Map<String, Object>>) raw.get("tests");

        List<ComplianceFixtures.TestCase> cases = new ArrayList<>();
        for (Map<String, Object> rc : rawCases) {
            cases.add(new ComplianceFixtures.TestCase(
                (String) rc.get("name"),
                (String) rc.get("action"),
                (String) rc.get("subject"),
                (Map<String, Object>) rc.get("subjectData"),
                Boolean.TRUE.equals(rc.get("expected"))
            ));
        }
        return cases;
    }
}
