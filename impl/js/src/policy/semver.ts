/** A parsed MAJOR.MINOR.PATCH SemVer string, per SPEC_V1-0-0.md §2. */
export interface SemVer {
  major: number;
  minor: number;
  patch: number;
}

/**
 * Parses a `version` string. `PATCH` (and `MINOR`) may be omitted -
 * `"1"` and `"1.0"` are valid shorthand for `"1.0.0"` (SPEC_V1-0-0.md
 * §2.1) - and default to `0`.
 */
export function parseSemVer(raw: string): SemVer {
  const match = /^(\d+)(?:\.(\d+))?(?:\.(\d+))?$/.exec(String(raw).trim());
  if (!match) {
    throw new RangeError(`"${raw}" is not a valid SemVer version string.`);
  }
  const [, major, minor, patch] = match;
  return {
    major: Number(major),
    minor: minor !== undefined ? Number(minor) : 0,
    patch: patch !== undefined ? Number(patch) : 0,
  };
}
