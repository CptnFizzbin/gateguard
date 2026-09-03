import * as fs from "fs";
import * as path from "path";
import * as YAML from "yaml";
import { describe, test, expect } from "vitest";
import { Policy, PolicyDefinition } from "../../src";
import { listYamlFiles, subjectArgFor, isIncluded } from "./complianceFixtures";

/**
 * Reads the v1 conformance suite under test/fixtures/v1 (see the README
 * there) - the shared, spec-derived fixtures every implementation MUST
 * read, per SPEC_V1-0-0.md §6. Each `*.yaml` file is a sequence of
 * `---`-separated documents; each document is one self-contained
 * `{ name, rules, cases }` test suite in the v1 `PolicyDefinition` shape.
 *
 * impl/js now natively implements the v1 `rules`/`meta` schema (see
 * ../../src/policy/PolicyDefinition.ts), so each parsed suite is a
 * PolicyDefinition already and is handed straight to `Policy.from(...)` -
 * no adapter needed.
 *
 * Discovery, subject-argument construction, and version filtering are
 * shared with every other compliance suite via ./complianceFixtures - see
 * that module for the KEYCARD_FIXTURES_MAX_VERSION knob that overrides
 * COMPLIANT_VERSION below for a single run.
 */

const FIXTURES_DIR = path.join(__dirname, "../../../../test/fixtures/v1");

/**
 * The highest v1 SemVer this suite (and the `Policy` implementation it
 * exercises) is written against. Baked into the suite itself - rather
 * than left to whatever an external operators happens to be - so "which
 * version this runs compliant with" is a property of the code: bump it
 * only once the implementation has actually been updated to handle
 * whatever a newer MINOR version's fixtures add, not merely because such
 * fixtures exist.
 */
const COMPLIANT_VERSION = "1.0.0";

interface V1Case {
  name?: string;
  action: string;
  subject: string;
  subjectData?: Record<string, unknown>;
  expected: "allow" | "deny";
}

interface V1Suite extends PolicyDefinition {
  name: string;
  description?: string;
  cases: V1Case[];
}

interface FixtureFile {
  fileName: string;
  filePath: string;
}

function discoverFixtureFiles(): FixtureFile[] {
  return listYamlFiles(FIXTURES_DIR).map((filePath) => ({ fileName: path.basename(filePath), filePath }));
}

function loadSuites(filePath: string): V1Suite[] {
  const raw = fs.readFileSync(filePath, "utf-8");
  return YAML.parseAllDocuments(raw).map((doc) => doc.toJSON() as V1Suite);
}

const fixtureFiles = discoverFixtureFiles();

test("discovers at least one v1 conformance fixture file", () => {
  expect(fixtureFiles.length).toBeGreaterThan(0);
});

describe.each(fixtureFiles)("v1 conformance fixture: $fileName", ({ filePath }) => {
  const suites = loadSuites(filePath);

  test("every document in the file is a well-formed suite", () => {
    for (const suite of suites) {
      expect(typeof suite.name).toBe("string");
      expect(Array.isArray(suite.rules)).toBe(true);
      expect(Array.isArray(suite.cases)).toBe(true);
    }
  });

  describe.each(suites)("$name", (suite) => {
    // Skips (rather than silently omitting) a suite whose declared version
    // exceeds this suite's own baked-in COMPLIANT_VERSION - see
    // complianceFixtures.ts's isIncluded.
    describe.skipIf(!isIncluded(suite.version, COMPLIANT_VERSION))(`version ${suite.version}`, () => {
      const policy = Policy.from(suite);
      const cases = suite.cases.map((c) => ({
        ...c,
        name: c.name ?? `${c.action} / ${c.subject} -> ${c.expected}`,
      }));

      test.each(cases)("$name", (testCase) => {
        expect(policy.can(testCase.action, subjectArgFor(testCase))).toBe(testCase.expected === "allow");
      });
    });
  });
});
