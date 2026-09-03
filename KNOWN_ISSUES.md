# Known Issues

This file tracks where the JS (`impl/js`) and Java (`impl/java`)
implementations currently diverge from [`SPEC_V1-0-0.md`](SPEC_V1-0-0.md),
the normative v1 policy specification. It exists to guide follow-up work —
it is not part of the spec itself, and fixing an implementation should
shrink this list, not the spec document. Section references below (§N)
point into `SPEC_V1-0-0.md`.

`impl/java` is now fully compliant with this revision. Everything below is
`impl/js`-only, until it's migrated in a companion PR (#12).

## Schema

- **`rules.allow`/`rules.deny` → a single ordered `rules` list, plus a new
  `meta` object** (§2). `impl/js/src/policy/PolicyDefinition.ts`'s
  `PolicyDefinition.rules` is currently `{ allow: [...], deny: [...] }`.
  It needs `rules` to become one ordered list of 4-tuples (or equivalent)
  carrying an allow/deny effect per entry, and a new `meta` field
  (`anyAction`, `anySubject`, `actions`, `subjects`, `operators`,
  `application`). The JS `Rule` interface in that same file already has
  an `inverted: boolean` field per rule, and `PolicyBuilder.buildDef()`
  (`impl/js/src/builder/PolicyBuilder.ts`) already accumulates
  `this.rules` as one ordered array *before* splitting it into
  `allow`/`deny` at the end — the fix is largely to stop doing that last
  split.
- **`version` is a plain integer, not a SemVer string** (§3).
  `impl/js/src/policy/PolicyDefinition.ts` types `version` as a number
  (`1`); it needs to become a string (`"1.0.0"`), with the
  MAJOR/MINOR/PATCH compatibility checks in §3 implemented at construction.
- **`append()` still exists; v1 drops it** (§1). `Policy.append` in
  `impl/js/src/policy/Policy.ts` should be removed (or clearly marked as
  a pre-v1 extension outside the spec) as part of adopting this revision,
  since the spec no longer defines its semantics.

## Algorithm

- **"allow AND NOT deny" → reverse-scan last-match-wins** (§6).
  `impl/js/src/policy/Policy.ts`'s `checkPermission`/`matchesAnyRule`
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
  just silently return `false`. Needs a console call added at each of the
  type-issue sites listed in §7.4.

## Construction-time validation (new in this revision)

None of the following exist yet — all are new `Policy.from(...)`/
`PolicyBuilder` requirements introduced by this revision, not existing
behavior that's merely wrong:

- **Malformed rule tuples **MUST** throw `PolicyLoadException`** (§2, EC-10).
- **Catalog coverage **MUST** throw `PolicyLoadException`** when
  `meta.actions`/`meta.subjects`/`meta.operators` is declared and
  some rule references a name outside it (§2, EC-8, EC-13). No concept of
  these catalogs exists yet.
- **`version` incompatibility **MUST** throw `PolicyVersionException`**
  (§3, EC-11). `Policy.from` doesn't check `definition.version` at all
  today.
- **A wildcard rule carrying a condition **MUST** throw** — see "No
  enforcement that a wildcard rule is unconditional" above.

## `meta.operators` and custom-operator registration

- **`meta.operators` doesn't exist yet, and neither does the
  registered-vs-cataloged coverage check it implies** (§7.4.12, EC-13,
  EC-15). JS already has a runtime custom-checker registration point
  (`CustomConditionChecker`, `impl/js/src/conditions/Condition.ts`, passed
  to `Policy.from`), so this gap is purely additive — carry
  `meta.operators` through `PolicyDefinition`, validate coverage at
  construction (per the section above), then add the EC-15 check: for every
  name `meta.operators` declares, an operator (built-in or custom) MUST
  already be registered on the instance, checked once at construction time -
  `Policy.from(...)` MUST throw a `PolicyLoadException` immediately if not,
  rather than deferring to a runtime-only diagnostic. `impl/java`'s
  `Collection<Operator>` mechanism (passed to both `PolicyBuilder` and
  `Policy.from`) is a model for this - built-in and custom operators share
  one registration entry point and one merged registry there, with a
  construction-time `PolicyLoadException` if two operators (custom-vs-
  built-in or custom-vs-custom) collide on the same `$name`; JS's own
  `Operator[]`/`ConditionResolver` shape is already close to this and needs
  the same registration-coverage and collision checks added.

## Condition operators

- **`$rgx` needs to be replaced with `$substr`** (§7.4.6). v1 drops regex
  matching entirely in favor of a small, non-regex pattern language
  (`^`, `$`, `*`, `**`, `\`) so that behavior is identical across host
  languages. `ConditionResolver` currently implements `$rgx` (backed by
  JS's native regex engine) and implements neither `$substr` nor its
  pattern semantics.
- **`$field` (long-form field access) doesn't exist** (§7.4.11, §7.5).
  It's now required behavior: any `$`-prefixed condition key **MUST** be
  treated as an operator, never a field name — testing a field whose name
  starts with `$` **MUST** go through `{ $field: [name, Condition] }`.
  `ConditionResolver` has no special handling for `$field` today; a
  `$field` key currently just falls through as an unrecognized operator.
- **`$gt`/`$gte`/`$lt`/`$lte` use native `>`/`<` instead of a numeric-only
  guard** (§7.4.3). `impl/js/src/conditions/ConditionResolver.ts` does a
  bare `>`/`<` comparison, which for non-number operands falls back to
  JS's coercion/lexicographic rules instead of failing closed. Needs a
  `typeof subject === "number" && typeof operand === "number"` guard added.
- **Mixing an operator key with sibling keys drops the siblings** (§7.5).
  `impl/js/src/conditions/ConditionResolver.ts` returns as soon as it
  matches one of the `if ("$xxx" in obj)` branches, so
  `{ $ne: null, status: "open" }` only ever evaluates `$ne` and silently
  ignores `status`. Needs restructuring to iterate every key in the
  condition object (as `impl/java`'s loop-over-every-key structure
  already does) rather than returning from inside a chain of single-key
  `if` checks.

## Fixtures

- **`test/fixtures/v1/` is a spec-native conformance suite (see the
  README there) that `impl/js` reads through a best-effort adapter that
  reshapes each fixture's v1 `rules`/`meta` into the pre-v1 `allow`/`deny`
  shape** (`v1ConformanceFixtures.test.ts`). Every case whose behavior
  depends on a gap tracked above currently fails through that adapter —
  that's expected, and is exactly the set of gaps this document already
  tracks, not a new one. Once `impl/js` adopts the v1 schema natively,
  replace its adapter with passing the parsed definition straight
  through, at which point these fixtures should start passing for real.
  (`impl/java` did exactly this migration; its
  `V1ConformanceFixtureTest`/`V1Fixtures.java` are a model for the shape
  this should take.)

