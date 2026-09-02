import * as fs from "fs";
import * as path from "path";
import * as YAML from "yaml";
import { describe, test, expect } from "vitest";
import { Policy, PolicyDefinition } from "../../src";

/**
 * Reads the v1 conformance suite under test/fixtures/v1 (see the README
 * there) - the shared, spec-derived fixtures every implementation MUST
 * read, per SPEC_V1-0-0.md §6. Each `*.yaml` file is a sequence of
 * `---`-separated documents; each document is one self-contained
 * `{ name, rules, cases }` test suite in the v1 `PolicyDefinition` shape.
 *
 * `impl/js` has not been migrated to the v1 `rules`/`meta` schema yet (see
 * KNOWN_ISSUES.md): its `PolicyDefinition` is still the pre-v1
 * `{ allow: [...], deny: [...] }` shape, driven by an "allow AND NOT deny"
 * check rather than v1's reverse-scan last-rule-wins, and its wildcard
 * token is hardcoded to `"*"` rather than reading `meta.anyAction`/
 * `meta.anySubject`. `toLegacyDefinition` below is a best-effort adapter
 * that reshapes a v1 definition into that pre-v1 shape so this suite can
 * still exercise the current implementation - it does not attempt to
 * emulate last-rule-wins, wildcard tokens, or `meta` catalogs. Cases that
 * depend on that v1-only behavior are therefore EXPECTED TO FAIL against
 * the current implementation; that gap is what this suite exists to make
 * visible, not a defect in the fixtures. Once `impl/js` adopts the v1
 * schema natively, this adapter should be replaced with passing the parsed
 * definition straight through.
 */

const FIXTURES_DIR = path.join(__dirname, "../../../../test/fixtures/v1");

type V1Rule = [string, string, string, Record<string, unknown>?];

interface V1Case {
  name?: string;
  action: string;
  subject: string;
  subjectData?: Record<string, unknown>;
  expected: "allow" | "deny";
}

interface V1Suite {
  version: string;
  name: string;
  description?: string;
  meta?: Record<string, unknown>;
  rules: V1Rule[];
  cases: V1Case[];
}

interface FixtureFile {
  fileName: string;
  filePath: string;
}

function discoverFixtureFiles(): FixtureFile[] {
  return fs
    .readdirSync(FIXTURES_DIR)
    .filter((f) => f.endsWith(".yaml"))
    .sort()
    .map((fileName) => ({ fileName, filePath: path.join(FIXTURES_DIR, fileName) }));
}

function loadSuites(filePath: string): V1Suite[] {
  const raw = fs.readFileSync(filePath, "utf-8");
  return YAML.parseAllDocuments(raw).map((doc) => doc.toJSON() as V1Suite);
}

/**
 * Reshapes a v1 `{ rules: [[effect, action, subject, conditions?], ...] }`
 * definition into the pre-v1 `{ rules: { allow: [...], deny: [...] } }`
 * shape `impl/js` currently expects, preserving each bucket's relative
 * order (cross-bucket ordering - the part last-rule-wins actually needs -
 * can't be preserved by this split, which is exactly the gap this suite is
 * meant to surface).
 */
function toLegacyDefinition(suite: V1Suite): PolicyDefinition {
  const allow: PolicyDefinition["rules"]["allow"] = [];
  const deny: PolicyDefinition["rules"]["deny"] = [];

  for (const [effect, action, subject, conditions] of suite.rules) {
    const tuple: [string, string, Record<string, unknown>?] = conditions
      ? [action, subject, conditions]
      : [action, subject];
    (effect === "allow" ? allow : deny).push(tuple);
  }

  return {
    version: 1,
    name: suite.name,
    description: suite.description,
    rules: { allow, deny },
  };
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
    const policy = Policy.from(toLegacyDefinition(suite));
    const cases = suite.cases.map((c) => ({
      ...c,
      name: c.name ?? `${c.action} / ${c.subject} -> ${c.expected}`,
    }));

    test.each(cases)("$name", (testCase) => {
      const subjectArg = testCase.subjectData
        ? { ...testCase.subjectData, __name: testCase.subject }
        : testCase.subject;

      expect(policy.can(testCase.action, subjectArg)).toBe(testCase.expected === "allow");
    });
  });
});
