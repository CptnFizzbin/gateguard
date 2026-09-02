# Known Issues

This file tracks where the JS (`impl/js`) and Java (`impl/java`)
implementations currently diverge from [`SPEC_V1-0-0.md`](SPEC_V1-0-0.md),
the normative v1 policy specification. It exists to guide follow-up work —
it is not part of the spec itself, and fixing an implementation should
shrink this list, not the spec document. Section references below (§N)
point into `SPEC_V1-0-0.md`.

## Status

`impl/js` now implements the v1 `rules`/`meta` `PolicyDefinition` schema
natively — `version` is a SemVer string, `rules` is a single ordered list
of `[effect, action, subject, conditions?]` tuples evaluated via
reverse-scan last-rule-wins, and `meta.anyAction`/`meta.anySubject`/
`meta.actions`/`meta.subjects`/`meta.customOperators`/`meta.application`
are all supported per §3. `test/fixtures/v1/` is read directly (no
adapter) by `v1ConformanceFixtures.test.ts`, and every case there passes.
`test/fixtures/policies/*.yaml` has been migrated to the v1 schema as
well (shared with `impl/java`'s equivalent suite).

`impl/java` has not been migrated to this revision yet — see the
"Java gaps" section below, which is what the pre-existing (pre-v1)
document used to track for both implementations, now scoped to Java only.

## One spec-wording note (not an implementation gap)

§3.2.3 ends with "During construction, implementations MUST throw a
PolicyLoadException if a behavior is not provided to the constructor,"
which read literally would mean *any* cataloged-but-unregistered custom
operator throws at `Policy.from(...)` time. That directly contradicts
EC-15, which requires the opposite — a cataloged-but-never-registered
operator MUST NOT throw; it resolves to `false` with a §7.1 console
diagnostic logged only when evaluation actually reaches it. EC-15 is also
the behavior `test/fixtures/v1/09-custom-operators.yaml`'s "a cataloged
but never-registered custom operator still evaluates false" case
requires (`expected: deny`, not a construction-time error). `impl/js`
(`Policy.ts`) follows EC-15 and the conformance suite here, and does not
throw for this case — treat §3.2.3's closing sentence as an error in the
spec prose rather than normative behavior to implement. (`impl/java`
should follow the same interpretation once it adopts `meta.customOperators`.)

## Java gaps

None of the following exist in `impl/java` yet — they mirror what used to
be tracked for both implementations before `impl/js` closed them:

### Schema

- **`rules.allow`/`rules.deny` → a single ordered `rules` list, plus a new
  `meta` object** (§2). `impl/java/.../policy/PolicyDefinition.java` holds
  the pre-v1 split as `allowRules`/`denyRules`. It needs `rules` to become
  one ordered list of 4-tuples (or equivalent) carrying an allow/deny
  effect per entry, and a new `meta` field (`anyAction`, `anySubject`,
  `actions`, `subjects`, `customOperators`, `application`).
- **`version` is a plain integer, not a SemVer string** (§3).
  `impl/java/.../policy/PolicyDefinition.java` types `version` as an
  `int`; it needs to become a `String` (`"1.0.0"`), with the
  MAJOR/MINOR/PATCH compatibility checks in §3 implemented at construction.
- **`append()` still exists; v1 drops it** (§1). `Policy.append` in
  `impl/java/.../policy/Policy.java` should be removed (or clearly marked
  as a pre-v1 extension outside the spec) as part of adopting this
  revision, since the spec no longer defines its semantics.

### Algorithm

- **"allow AND NOT deny" → reverse-scan last-match-wins** (§6).
  `impl/java/.../policy/Policy.java`'s `checkPermission`/`matchesAnyRule`
  currently OR-matches `allow` and separately OR-matches `deny`, then
  combines with "allow and not deny," independent of any ordering. It
  needs to change to a single reverse scan over the unified `rules` list
  that returns on the first (i.e., most-recently-declared) match.
- **Wildcard token: hardcoded `"*"` → `meta.anyAction`/`meta.anySubject`,
  defaulting to `"_ANY_"`** (§4, §5, §6). `matchesAction`/`matchesSubject`
  currently treat the literal string `"*"` as the wildcard,
  unconditionally. They need to instead read the effective wildcard token
  from the `Policy`'s own `meta.anyAction`/`meta.anySubject`, defaulting
  to `"_ANY_"` when unset.
- **No enforcement that a wildcard rule is unconditional** (§6 property 5,
  EC-6). Not validated at all today — a rule like
  `[allow, "*", Article, { owner_id: 1 }]` is accepted silently. Needs
  this check added at `Policy.from`/construction time (throwing
  `PolicyLoadException`), and the builder needs the same check at the
  `allow()`/`deny()` call site (throwing an argument-error exception).
- **No console diagnostics for type issues** (§7.1 and each operator's
  Requirements in §7.4). `ConditionResolver` logs nothing today — a `$gt`
  against a string, a non-array `$in` operand, or a malformed pattern all
  just silently return `false`. Needs a console/log call added at each of
  the type-issue sites listed in §7.4.

### Construction-time validation (new in this revision)

None of the following exist yet — all are new `Policy.from(...)`/
`PolicyBuilder` requirements introduced by this revision, not existing
behavior that's merely wrong:

- **Malformed rule tuples **MUST** throw `PolicyLoadException`** (§2, EC-10).
- **Catalog coverage **MUST** throw `PolicyLoadException`** when
  `meta.actions`/`meta.subjects`/`meta.customOperators` is declared and
  some rule references a name outside it (§2, EC-8, EC-13). No concept of
  these catalogs exists yet.
- **`version` incompatibility **MUST** throw `PolicyVersionException`**
  (§3, EC-11). The `Policy` constructor doesn't check `definition.version`
  at all today.
- **A wildcard rule carrying a condition **MUST** throw** — see "No
  enforcement that a wildcard rule is unconditional" above.

### `meta.customOperators` and custom-operator registration

- **`meta.customOperators` doesn't exist yet, and neither does the
  registered-vs-cataloged coverage check it implies** (§7.4.12, EC-13,
  EC-15). `impl/java/.../conditions/ConditionResolver.java` has no
  custom-checker registration mechanism at all yet — that's a
  prerequisite before `meta.customOperators` or EC-15 can mean anything
  on the Java side. (`impl/js`'s `CustomConditionChecker` map, passed to
  `Policy.from`, is a model for what this can look like.)

### Condition operators

- **`$rgx` needs to be replaced with `$substr`** (§7.4.6). v1 drops regex
  matching entirely in favor of a small, non-regex pattern language
  (`^`, `$`, `*`, `**`, `\`) so that behavior is identical across host
  languages. `ConditionResolver` currently implements `$rgx` (backed by
  Java's native regex engine) and implements neither `$substr` nor its
  pattern semantics.
- **`$field` (long-form field access) doesn't exist** (§7.4.11, §7.5).
  It's now required behavior: any `$`-prefixed condition key **MUST** be
  treated as an operator, never a field name — testing a field whose name
  starts with `$` **MUST** go through `{ $field: [name, Condition] }`.
  `ConditionResolver` has no special handling for `$field` today; a
  `$field` key currently just falls through as an unrecognized operator.
- **Missing field conflated with explicit `null`** (§7.3).
  `impl/java/.../conditions/ConditionResolver.java`'s top-of-method
  `if (condition == null) return true;` means a nested field condition
  whose *value* happens to be `null` (i.e. `{ field: null }`, or any
  operator wrapper — `{ field: { $ne: 5 } }` — recursing with a `null`
  condition) is mishandled at the object level, and separately, a
  `Map`-backed subject's *missing* key (`map.get(key)` returning `null`
  for an absent key) is indistinguishable from a *present* key whose value
  is `null`, unlike the reflection-based (POJO) subject path in the same
  class, which correctly treats a missing field as absence
  (`NoSuchFieldException` → `false`).
- **`Double.equals` treats NaN as equal to NaN under `$eq`/`$ne`/`$gt`-family
  comparisons** (§7.4.3). Implementations SHOULD ensure NaN never equals
  itself under these operators even where the host language's default
  equality would say otherwise; Java's boxed `Double.equals` currently
  lets that leak through unguarded.

### Fixtures

- **`test/fixtures/v1/` is a spec-native conformance suite (see the
  README there) that `impl/java` reads through a best-effort adapter
  that reshapes each fixture's v1 `rules`/`meta` into the pre-v1
  `allow`/`deny` shape** (`V1ConformanceFixtureTest`/`V1Fixtures.java`).
  Every case whose behavior depends on a gap tracked above currently
  fails through that adapter — that's expected, and is exactly the set
  of gaps this document already tracks, not a new one. Once `impl/java`
  adopts the v1 schema natively, replace its adapter with passing the
  parsed definition straight through, at which point these fixtures
  should start passing for real. (`impl/js` did exactly this migration;
  its `v1ConformanceFixtures.test.ts` is a model for the shape this
  should take.)

## `SubjectDef` evaluation (EC-9)

- The "subject value" used when evaluating conditions against a bare
  `SubjectDef` (no wrapped instance) is currently, in JS, the value
  returned by `Policy`'s `getSubjectValue` for anything that isn't a
  `SubjectRef` or a bare string — which for an actual `SubjectDef`
  correctly falls back to the `SubjectDef` token object itself. It
  happens not to expose any domain fields real conditions would check,
  so it satisfies EC-9's "MUST NOT accidentally match" requirement, via
  the explicit `hasInstance` check in `impl/js/src/policy/Policy.ts`
  (a bare `SubjectDef` — detected by its `wrap` function — is never
  treated as having an instance in the first place, so a conditional
  rule can't reach `getSubjectValue` for one at all). The same needs
  verifying once `impl/java` adopts the v1 schema and its own bare-type
  vs. wrapped-instance handling.
