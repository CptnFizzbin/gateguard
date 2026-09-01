import * as fs from "fs";
import * as path from "path";
import * as YAML from "yaml";
import { describe, test, expect } from "vitest";
import { Policy, PolicyDefinition } from "../../src";

/**
 * Metaprogrammed integration suite: every `*.yaml` fixture under
 * test/fixtures/policies (paired with its `*.test.yaml` companion) gets its
 * own generated describe/test block below. Dropping a new
 * `policy-XX.yaml` + `policy-XX.test.yaml` pair into that directory is
 * picked up automatically the next time this suite runs - no test code
 * needs to change.
 *
 * KeyCard itself never reads or writes policy.yaml text (that's an
 * application concern) - so YAML parsing here is done with the `yaml`
 * package (a devDependency of this test suite only) and handed to the
 * library as a plain PolicyDefinition via `Policy.from(...)`.
 */

const FIXTURES_DIR = path.join(__dirname, "../../../../test/fixtures/policies");

interface TestCase {
  name: string;
  action: string;
  subject: string;
  subjectData?: Record<string, unknown>;
  expected: boolean;
}

interface FixtureFile {
  policyName: string;
  policyPath: string;
  testPath: string;
}

function discoverFixtures(): FixtureFile[] {
  return fs
    .readdirSync(FIXTURES_DIR)
    .filter((f) => f.endsWith(".yaml") && !f.endsWith(".test.yaml"))
    .sort()
    .map((policyFile) => ({
      policyName: policyFile,
      policyPath: path.join(FIXTURES_DIR, policyFile),
      testPath: path.join(FIXTURES_DIR, policyFile.replace(/\.yaml$/, ".test.yaml")),
    }));
}

/** Parses a policy.yaml fixture's on-disk shape into a PolicyDefinition. */
function loadPolicyDef(rawYaml: string): PolicyDefinition {
  const parsed = YAML.parse(rawYaml) as {
    version: number;
    name?: string;
    description?: string;
    allow?: PolicyDefinition["rules"]["allow"];
    deny?: PolicyDefinition["rules"]["deny"];
  };

  return {
    version: parsed.version,
    name: parsed.name,
    description: parsed.description,
    rules: {
      allow: parsed.allow ?? [],
      deny: parsed.deny ?? [],
    },
  };
}

const fixtures = discoverFixtures();

// Sanity check on the discovery mechanism itself, so a misconfigured
// FIXTURES_DIR fails loudly instead of silently running zero tests.
test("discovers at least one policy fixture", () => {
  expect(fixtures.length).toBeGreaterThan(0);
});

describe.each(fixtures)("policy fixture: $policyName", ({ policyPath, testPath }) => {
  const rawYaml = fs.readFileSync(policyPath, "utf-8");

  test("successfully reads the policy.yaml file", () => {
    const policyDef = loadPolicyDef(rawYaml);

    expect(typeof policyDef.version).toBe("number");
    expect(Array.isArray(policyDef.rules.allow)).toBe(true);
    expect(Array.isArray(policyDef.rules.deny)).toBe(true);
  });

  test("Policy.from(definition).toDefinition() deeply equals the parsed definition", () => {
    const policyDef = loadPolicyDef(rawYaml);
    const policy = Policy.from(policyDef);

    expect(policy.toDefinition()).toEqual(policyDef);
  });

  test("fromDto/toDto aliases behave identically to from/toDefinition", () => {
    const policyDef = loadPolicyDef(rawYaml);

    expect(Policy.fromDto(policyDef).toDto()).toEqual(policyDef);
  });

  if (!fs.existsSync(testPath)) {
    test.skip(`no companion ${path.basename(testPath)} found`, () => {});
    return;
  }

  const { tests: cases } = YAML.parse(fs.readFileSync(testPath, "utf-8")) as { tests: TestCase[] };
  const policy = Policy.from(loadPolicyDef(rawYaml));

  test.each(cases)("resolves test case: $name", (testCase) => {
    const subjectArg = testCase.subjectData
      ? { ...testCase.subjectData, __name: testCase.subject }
      : testCase.subject;

    expect(policy.can(testCase.action, subjectArg)).toBe(testCase.expected);
  });
});
