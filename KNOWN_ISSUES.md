# Known Issues

This file tracks where the JS (`impl/js`) and Java (`impl/java`)
implementations currently diverge from [`SPEC_V1-0-0.md`](SPEC_V1-0-0.md),
the normative v1 policy specification. It exists to guide follow-up work —
it is not part of the spec itself, and fixing an implementation should
shrink this list, not the spec document. Section references below (§N)
point into `SPEC_V1-0-0.md`.

None of the items below describe current behavior as correct or as a
description of intended design — they are gaps to close. Neither
implementation, and none of the existing YAML fixtures under
`test/fixtures/policies/`, match `SPEC_V1-0-0.md` yet.

## Schema

- **`rules.allow`/`rules.deny` → a single ordered `rules` list, plus a new
  `meta` object** (§2). `impl/js/src/policy/PolicyDefinition.ts`'s
  `PolicyDefinition.rules` is currently `{ allow: [...], deny: [...] }`;
  `impl/java/.../policy/PolicyDefinition.java` holds the same split as
  `allowRules`/`denyRules`. Both need `rules` to become one ordered list of
  4-tuples (or equivalent) carrying an allow/deny effect per entry, and a
  new `meta` field (`anyAction`, `anySubject`, `actions`, `subjects`,
  `customOperators`). The JS `Rule` interface in that same file already
  has an `inverted: boolean` field per rule, and `PolicyBuilder.buildDef()`
  (`impl/js/src/builder/PolicyBuilder.ts`) already accumulates `this.rules`
  as one ordered array *before* splitting it into `allow`/`deny` at the
  end — the JS fix is largely to stop doing that last split. The Java
  `PolicyDefinition.Rule` type has no such flag yet and needs one added,
  alongside collapsing `allowRules`/`denyRules` into one ordered `rules`
  list.
- **`version` is a plain integer in both implementations, not a SemVer
  string** (§3). `impl/js/src/policy/PolicyDefinition.ts` and
  `impl/java/.../policy/PolicyDefinition.java` both type `version` as a
  number (`1`); it needs to become a string (`"1.0.0"`), with the
  MAJOR/MINOR/PATCH compatibility checks in §3 implemented at construction.
- **`append()` still exists in both implementations; v1 drops it** (§1).
  `Policy.append` in both `impl/js/src/policy/Policy.ts` and
  `impl/java/.../policy/Policy.java` should be removed (or clearly marked
  as a pre-v1 extension outside the spec) as part of adopting this
  revision, since the spec no longer defines its semantics.

## Algorithm

- **"allow AND NOT deny" → reverse-scan last-match-wins** (§6).
  `impl/js/src/policy/Policy.ts`'s `checkPermission`/`matchesAnyRule` and
  `impl/java/.../policy/Policy.java`'s `checkPermission`/`matchesAnyRule`
  both currently OR-match `allow` and separately OR-match `deny`, then
  combine with "allow and not deny," independent of any ordering. Both
  need to change to a single reverse scan over the unified `rules` list
  that returns on the first (i.e., most-recently-declared) match.
- **Wildcard token: hardcoded `"*"` → `meta.anyAction`/`meta.anySubject`,
  defaulting to `"_ANY_"`** (§4, §5, §6). Both `matchesAction`/
  `matchesSubject` implementations currently treat the literal string
  `"*"` as the wildcard, unconditionally. Both need to instead read the
  effective wildcard token from the `Policy`'s own `meta.anyAction`/
  `meta.anySubject`, defaulting to `"_ANY_"` when unset.
- **No enforcement that a wildcard rule is unconditional** (§6 property 5,
  EC-6). Neither implementation currently validates this at all — a rule
  like `[allow, "*", Article, { owner_id: 1 }]` is accepted silently
  today. Both need this check added at `Policy.from`/construction time
  (throwing `PolicyLoadException`), and the builder needs the same check
  at the `allow()`/`deny()` call site (throwing an argument-error
  exception).
- **No console diagnostics for type issues, in either implementation**
  (§7.1 and each operator's Requirements in §7.4). Neither
  `ConditionResolver` implementation logs anything today — a `$gt`
  against a string, a non-array `$in` operand, or a malformed pattern all
  just silently return `false`. Both need a console/log call added at
  each of the type-issue sites listed in §7.4.

## Construction-time validation (new in this revision)

None of the following exist in either implementation yet — all are new
`Policy.from(...)`/`PolicyBuilder` requirements introduced by this
revision, not existing behavior that's merely wrong:

- **Malformed rule tuples **MUST** throw `PolicyLoadException`** (§2, EC-10).
  Currently unvalidated in both implementations.
- **Catalog coverage **MUST** throw `PolicyLoadException`** when
  `meta.actions`/`meta.subjects`/`meta.customOperators` is declared and
  some rule references a name outside it (§2, EC-8, EC-13). Neither
  implementation has any concept of these catalogs yet.
- **`version` incompatibility **MUST** throw `PolicyVersionException`**
  (§3, EC-11). Neither `Policy.from` (`impl/js/src/policy/Policy.ts`) nor
  the `Policy` constructor (`impl/java/.../policy/Policy.java`) checks
  `definition.version` at all today.
- **A wildcard rule carrying a condition **MUST** throw** — see "No
  enforcement that a wildcard rule is unconditional" above.

## `meta.customOperators` and custom-operator registration

- **`meta.customOperators` doesn't exist yet, and neither does the
  registered-vs-cataloged coverage check it implies** (§7.4.12, EC-13,
  EC-15). The two implementations start from different places here: JS
  already has a runtime custom-checker registration point
  (`CustomConditionChecker`, `impl/js/src/conditions/Condition.ts`, passed
  to `Policy.from`), so its gap is purely additive — carry
  `meta.customOperators` through `PolicyDefinition`, validate coverage at
  construction (per the section above), then add the EC-15 check (when
  evaluation hits a `$op` listed in `meta.customOperators` with no runtime
  checker registered for it, log the required diagnostic). Java's
  `impl/java/.../conditions/ConditionResolver.java` has no custom-checker
  registration mechanism at all yet — that's a prerequisite there before
  `meta.customOperators` or EC-15 can mean anything on the Java side.

## Condition operators

- **`$rgx` needs to be replaced with `$substr`** (§7.4.6). v1 drops regex
  matching entirely in favor of a small, non-regex pattern language
  (`^`, `$`, `*`, `**`, `\`) so that behavior is identical across host
  languages. Both `ConditionResolver` implementations currently implement
  `$rgx` (backed by each language's native regex engine) and implement
  neither `$substr` nor its pattern semantics — this is new operator work
  on both sides, not a fix to existing behavior. See §7.4.6 for the full
  semantics, including that `*` and `**` are match-equivalent (no
  capture/extraction feature exists to make greedy/lazy observable).
- **`$field` (long-form field access) doesn't exist in either
  implementation** (§7.4.11, §7.5). It's now required behavior: any
  `$`-prefixed condition key **MUST** be treated as an operator, never a field
  name — testing a field whose name starts with `$` **MUST** go through
  `{ $field: [name, Condition] }`. Neither `ConditionResolver`
  implementation has special handling for `$field` today; a `$field` key
  currently just falls through as an unrecognized operator.
- **`$gt`/`$gte`/`$lt`/`$lte` are numeric-only in Java, native `>`/`<` in
  JS** (§7.4.3). Java's `NumberConditions` already requires both operands
  to be `Number`; `impl/js/src/conditions/ConditionResolver.ts` does a
  bare `>`/`<` comparison, which for non-number operands falls back to
  JS's coercion/lexicographic rules instead of failing closed. The JS side
  needs a `typeof subject === "number" && typeof operand === "number"`
  guard added.
- **Missing field conflated with explicit `null`, in Java** (§7.3).
  `impl/java/.../conditions/ConditionResolver.java`'s top-of-method
  `if (condition == null) return true;` means a nested field condition
  whose *value* happens to be `null` (i.e. `{ field: null }`, or any
  operator wrapper — `{ field: { $ne: 5 } }` — recursing with a `null`
  condition) is mishandled at the object level, and separately, a
  `Map`-backed subject's *missing* key (`map.get(key)` returning `null`
  for an absent key) is indistinguishable from a *present* key whose value
  is `null`, unlike the reflection-based (POJO) subject path in the same
  class, which correctly treats a missing field as absence
  (`NoSuchFieldException` → `false`). The JS resolver
  (`impl/js/src/conditions/ConditionResolver.ts`) already matches this
  spec: it checks `key in subject` before descending.
- **Mixing an operator key with sibling keys drops the siblings, in JS**
  (§7.5). `impl/js/src/conditions/ConditionResolver.ts` returns as soon as
  it matches one of the `if ("$xxx" in obj)` branches, so
  `{ $ne: null, status: "open" }` only ever evaluates `$ne` and silently
  ignores `status`. Java's loop-over-every-key structure in
  `ConditionResolver.java` already matches the spec's AND-everything rule.
  The JS resolver needs to be restructured to iterate every key in the
  condition object (as Java already does) rather than returning from
  inside a chain of single-key `if` checks.

## Fixtures

- **Existing YAML fixtures use the old `allow:`/`deny:` shape and an
  integer `version`.** Every file under `test/fixtures/policies/*.yaml`
  predates this revision. They'll need rewriting to the new `rules`/`meta`
  shape with a SemVer `version` string as part of the implementation
  follow-up, not just a schema change in the two `Policy` classes.
- **`test/fixtures/v1/` is a new, spec-native conformance suite (see the
  README there) that both `impl/js` and `impl/java` now read**
  (`impl/js/tests/integration/v1ConformanceFixtures.test.ts`,
  `impl/java/.../integration/V1ConformanceFixtureTest.java`), through a
  best-effort adapter that reshapes each fixture's v1 `rules`/`meta` into
  the current pre-v1 `allow`/`deny` shape. Every case above whose behavior
  depends on a gap tracked elsewhere in this document (last-rule-wins
  ordering, the `_ANY_` wildcard token, `meta` catalogs, `$substr`,
  `$field`, the Java missing-field/`null` conflation, ...) currently fails
  through that adapter — that's expected, and is exactly the set of gaps
  this document already tracks, not a new one. Once an implementation
  adopts the v1 schema natively, replace its adapter with passing the
  parsed definition straight through, at which point these fixtures should
  start passing for real.

## `SubjectDef` evaluation (EC-9)

- The "subject value" used when evaluating conditions against a bare
  `SubjectDef` (no wrapped instance) is currently, in JS, the `SubjectDef`
  token object itself (`impl/js/src/policy/Policy.ts`'s
  `getSubjectValue`), which happens not to expose any domain fields real
  conditions would check, so it satisfies EC-9's "MUST NOT accidentally
  match" requirement today, but incidentally rather than by an explicit
  design decision documented anywhere in the implementation. Worth an
  explicit code comment referencing EC-9 once touched for other reasons.
