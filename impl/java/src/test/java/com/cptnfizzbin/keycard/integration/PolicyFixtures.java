package com.cptnfizzbin.keycard.integration;

import com.cptnfizzbin.keycard.policy.PolicyDefinition;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared loading helpers for the fixture-driven integration tests. Not a
 * test class itself - see PolicyFixtureReadTest and PolicyFixtureCaseTest.
 *
 * KeyCard itself never reads or writes policy.yaml text; parsing it into a
 * plain PolicyDefinition (via SnakeYaml, a test-only dependency) is this
 * test suite's job, mirroring what an application would do.
 */
final class PolicyFixtures {
    private PolicyFixtures() {}

    static final Path FIXTURES_DIR = Paths.get("../../test/fixtures/policies");

    private static final Yaml YAML = new Yaml();

    /** All `policy-*.yaml` fixtures (excluding their `.test.yaml` companions), sorted by name. */
    static List<Path> discoverPolicyFiles() throws IOException {
        try (var stream = Files.list(FIXTURES_DIR)) {
            return stream
                .filter(p -> p.getFileName().toString().endsWith(".yaml"))
                .filter(p -> !p.getFileName().toString().endsWith(".test.yaml"))
                .sorted()
                .collect(Collectors.toList());
        }
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
        Map<String, Object> raw = YAML.load(content);

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

    /** One entry from a `policy-*.test.yaml` companion's `tests:` list. */
    record TestCase(String name, String action, String subject, Map<String, Object> subjectData, boolean expected) {}

    @SuppressWarnings("unchecked")
    static List<TestCase> loadTestCases(Path testYamlFile) throws IOException {
        String content = Files.readString(testYamlFile);
        Map<String, Object> raw = YAML.load(content);
        List<Map<String, Object>> rawCases = (List<Map<String, Object>>) raw.get("tests");

        List<TestCase> cases = new ArrayList<>();
        for (Map<String, Object> rc : rawCases) {
            cases.add(new TestCase(
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
