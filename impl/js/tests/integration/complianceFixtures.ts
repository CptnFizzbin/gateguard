import * as fs from "fs";
import * as path from "path";

/**
 * Shared helpers for every compliance-fixture-driven integration suite -
 * policyFixtures.test.ts (the pre-v1 fixtures under test/fixtures/policies)
 * and v1ConformanceFixtures.test.ts (the spec-native fixtures under
 * test/fixtures/v1) today, and any future fixture set.
 *
 * Factors out the parts that don't depend on a fixture format's on-disk
 * shape: discovering `*.yaml` files, the subject argument every format's
 * cases boil down to once parsed, and filtering fixtures by the SemVer
 * `version` they declare - so each format-specific test file only owns
 * parsing its own document shape, not the discovery/resolution/filtering
 * mechanics around it.
 */

/** The bit of a parsed case every fixture format shares, regardless of how its `expected` field is spelled. */
export interface ComplianceCase {
  subject: string;
  subjectData?: Record<string, unknown>;
}

/** `*.yaml` files directly under `dir` for which `filter` holds (default: all of them), sorted by name. */
export function listYamlFiles(dir: string, filter: (fileName: string) => boolean = () => true): string[] {
  return fs
    .readdirSync(dir)
    .filter((f) => f.endsWith(".yaml") && filter(f))
    .sort()
    .map((f) => path.join(dir, f));
}

/**
 * The subject argument every fixture-driven suite passes to `Policy.can`:
 * a bare subject name when there's no instance data (a `SubjectDef`-style,
 * EC-7/EC-9 check), or a `{ ...subjectData, __name: subject }` map
 * (mirroring a `SubjectRef`) when there is.
 */
export function subjectArgFor(testCase: ComplianceCase): unknown {
  return testCase.subjectData ? { ...testCase.subjectData, __name: testCase.subject } : testCase.subject;
}

/** A parsed MAJOR.MINOR.PATCH SemVer string, per SPEC_V1-0-0.md §2. */
export interface SemVer {
  major: number;
  minor: number;
  patch: number;
}

export function parseSemVer(raw: string): SemVer {
  const [major, minor, patch] = raw.split(".").map((part) => parseInt(part, 10));
  return { major, minor: minor ?? 0, patch: patch ?? 0 };
}

/**
 * True when a fixture declaring `fixtureVersion` is compatible with an
 * implementation targeting `maxSupportedVersion`, per SPEC_V1-0-0.md §2:
 * the same MAJOR, and a MINOR no higher than what's supported. PATCH never
 * affects compatibility.
 */
export function isCompatible(fixtureVersion: string, maxSupportedVersion: string): boolean {
  const fixture = parseSemVer(fixtureVersion);
  const max = parseSemVer(maxSupportedVersion);
  return fixture.major === max.major && fixture.minor <= max.minor;
}

/**
 * Env var used to cap which fixture versions a test run exercises (e.g.
 * `KEYCARD_FIXTURES_MAX_VERSION=1.0.0 yarn test run`) - useful once fixtures
 * for a newer MINOR version exist and an implementation that only targets
 * an older one shouldn't be expected to pass them yet. Unset means "no cap,
 * run every fixture regardless of the version it declares".
 */
export const MAX_VERSION_ENV_VAR = "KEYCARD_FIXTURES_MAX_VERSION";

/** True when a fixture declaring `fixtureVersion` should run, given any configured version cap. */
export function isIncluded(fixtureVersion: string): boolean {
  const max = process.env[MAX_VERSION_ENV_VAR];
  return !max || isCompatible(fixtureVersion, max);
}
