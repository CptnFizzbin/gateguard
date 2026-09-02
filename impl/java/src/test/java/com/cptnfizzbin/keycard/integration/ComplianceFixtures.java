package com.cptnfizzbin.keycard.integration;

import com.cptnfizzbin.keycard.policy.Policy;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Shared helpers for every compliance-fixture-driven integration suite -
 * {@link PolicyFixtures} (the pre-v1 fixtures under test/fixtures/policies)
 * and {@link V1Fixtures} (the spec-native fixtures under test/fixtures/v1)
 * today, and any future fixture set. Not a test class itself.
 *
 * Factors out the parts that don't depend on a fixture format's on-disk
 * shape: discovering `*.yaml` files, the `{ action, subject, subjectData?,
 * expected }` shape every format's individual cases boil down to once
 * parsed, resolving one such case against a {@link Policy}, and filtering
 * fixtures by the SemVer `version` they declare - so each format-specific
 * loader only has to own parsing its own document shape into that common
 * {@link TestCase}, not the discovery/resolution/filtering mechanics
 * around it.
 *
 * KeyCard itself never reads or writes policy.yaml text; parsing it into a
 * plain PolicyDefinition (via SnakeYaml, a test-only dependency) is this
 * test suite's job, mirroring what an application would do.
 */
final class ComplianceFixtures {
    private ComplianceFixtures() {}

    /** Shared SnakeYaml instance for every fixture loader - construction isn't free, and it's stateless/reusable. */
    static final Yaml YAML = new Yaml();

    /**
     * One `{ action, subject, subjectData?, expected }` case, common to
     * every compliance fixture format regardless of how its surrounding
     * document is shaped.
     */
    record TestCase(String name, String action, String subject, Map<String, Object> subjectData, boolean expected) {}

    /** `*.yaml` files directly under {@code dir} for which {@code filter} holds, sorted by path. */
    static List<Path> discoverYamlFiles(Path dir, Predicate<Path> filter) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream
                .filter(p -> p.getFileName().toString().endsWith(".yaml"))
                .filter(filter)
                .sorted()
                .collect(Collectors.toList());
        }
    }

    /**
     * Resolves one {@link TestCase} against a {@link Policy} the same way
     * every fixture-driven suite does: a bare subject-name check when
     * there's no instance data (a {@code SubjectDef}-style, EC-7/EC-9
     * check), or a `{ ...subjectData, __name: subject }` map (mirroring a
     * {@code SubjectRef}) when there is.
     */
    static boolean resolve(Policy policy, TestCase testCase) {
        if (testCase.subjectData() != null) {
            Map<String, Object> subjectMap = new HashMap<>(testCase.subjectData());
            subjectMap.put("__name", testCase.subject());
            return policy.can(testCase.action(), subjectMap);
        }
        return policy.can(testCase.action(), testCase.subject());
    }

    /** A parsed MAJOR.MINOR.PATCH SemVer string, per SPEC_V1-0-0.md §2. */
    record SemVer(int major, int minor, int patch) implements Comparable<SemVer> {
        static SemVer parse(String raw) {
            String[] parts = raw.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return new SemVer(major, minor, patch);
        }

        @Override
        public int compareTo(SemVer other) {
            if (major != other.major) return Integer.compare(major, other.major);
            if (minor != other.minor) return Integer.compare(minor, other.minor);
            return Integer.compare(patch, other.patch);
        }
    }

    /**
     * True when a fixture declaring {@code fixtureVersion} is compatible
     * with an implementation targeting {@code maxSupportedVersion}, per
     * SPEC_V1-0-0.md §2: the same MAJOR, and a MINOR no higher than what's
     * supported. PATCH never affects compatibility.
     */
    static boolean isCompatible(String fixtureVersion, String maxSupportedVersion) {
        SemVer fixture = SemVer.parse(fixtureVersion);
        SemVer max = SemVer.parse(maxSupportedVersion);
        return fixture.major() == max.major() && fixture.minor() <= max.minor();
    }

    /**
     * System property that overrides a suite's baked-in
     * {@code compliantVersion} for one run (e.g.
     * {@code mvn test -Dkeycard.fixtures.maxVersion=1.0.0}) - useful for
     * deliberately narrowing or widening the cap without editing code.
     * Unset (the common case) means "use whatever version the compliance
     * suite itself bakes in".
     */
    static final String MAX_VERSION_PROPERTY = "keycard.fixtures.maxVersion";

    /**
     * True when a fixture declaring {@code fixtureVersion} should run
     * against a compliance suite that bakes in {@code compliantVersion} as
     * the highest version its adapter is written against - see e.g.
     * {@code V1ConformanceFixtureTest.COMPLIANT_VERSION}. Every compliance
     * suite bakes in its own version rather than defaulting to "run
     * everything", so a suite whose adapter hasn't caught up to a newer
     * MINOR version's fixtures skips them automatically, with no external
     * configuration required; {@link #MAX_VERSION_PROPERTY} overrides that
     * baked-in default when set.
     */
    static boolean isIncluded(String fixtureVersion, String compliantVersion) {
        String override = System.getProperty(MAX_VERSION_PROPERTY);
        return isCompatible(fixtureVersion, override != null ? override : compliantVersion);
    }
}
