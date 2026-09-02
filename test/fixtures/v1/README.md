# v1 conformance fixtures

The YAML files in this directory are a conformance test suite for
[`SPEC_V1-0-0.md`](../../../SPEC_V1-0-0.md) — the authoritative v1 policy
spec — as opposed to `test/fixtures/policies/`, whose fixtures predate that
spec and still use the old `allow:`/`deny:` document shape (see
`KNOWN_ISSUES.md`).

Every implementation **MUST** read these fixtures as part of its test suite
(`impl/js/tests/integration/v1ConformanceFixtures.test.ts` and
`impl/java/.../integration/V1ConformanceFixtureTest.java` do so today). As of
this writing, neither implementation has been migrated to the v1 `rules`/
`meta` schema yet (see `KNOWN_ISSUES.md`), so these fixtures are read through
a best-effort adapter that reshapes a v1 `PolicyDefinition` into whatever the
current, pre-v1 `Policy`/`PolicyDefinition` classes expect. Cases that depend
on v1-only behavior the current implementations don't have yet (last-rule-
wins ordering, the `_ANY_` wildcard token, `meta` catalogs, `$substr`,
`$field`, console diagnostics, ...) are **expected to fail** until the
implementations catch up — that's the point of adding this suite ahead of
the implementation work, not a bug in the fixtures. Once an implementation
adopts the v1 schema natively, its adapter should be simplified to pass the
parsed definition straight through instead of reshaping it.

Discovery, subject-argument construction, and per-`version` filtering are
factored into small, reusable utility modules shared by every compliance
suite — not just this one — rather than duplicated per fixture format:
`impl/js/tests/integration/complianceFixtures.ts` and
`impl/java/.../integration/ComplianceFixtures.java`. Each format-specific
loader (`policyFixtures.test.ts`/`v1ConformanceFixtures.test.ts` in JS,
`PolicyFixtures`/`V1Fixtures` in Java) only owns parsing its own document
shape into the shared `{ action, subject, subjectData?, expected }` case
shape those utilities work with.

## Format

Each `*.yaml` file is a sequence of one or more YAML documents (separated by
`---`), one per test suite:

```yaml
version: "1.0.0"          # required — a v1 PolicyDefinition, per SPEC_V1-0-0.md §3
name: name of the test suite
description: string       # optional
meta:                      # optional — see SPEC_V1-0-0.md §3.2
  anyAction: ...
  anySubject: ...
  actions: [...]
  subjects: [...]
  customOperators: [...]
rules:                     # list of [effect, action, subject, conditions?] tuples
  - [allow, Read, Article]
cases:
  - name: description of the case          # optional
    action: Read
    subject: Article
    subjectData:            # optional — omit for a bare/no-instance subject check
      owner_id: 1
    expected: allow                        # "allow" or "deny"
---
name: next suite
...
```

`subject` names the subject's type; `subjectData`, when present, is the
wrapped instance's value (a `SubjectRef`, per §5) and makes the check
conditional-rule-eligible. Omitting `subjectData` checks a bare type (no
instance — a `SubjectDef`/EC-9 style check): any rule carrying a `Conditions`
element cannot match such a check (EC-7).

## Filtering by version

Every suite declares a SemVer `version`. Once fixtures for a newer `MINOR`
version exist, a test run can cap which ones it exercises — useful for an
implementation that only targets an older `MINOR` and shouldn't be expected
to pass fixtures for a newer one it hasn't caught up to yet:

- JS: set the `KEYCARD_FIXTURES_MAX_VERSION` env var, e.g.
  `KEYCARD_FIXTURES_MAX_VERSION=1.0.0 yarn test run`.
- Java: set the `keycard.fixtures.maxVersion` system property, e.g.
  `mvn test -Dkeycard.fixtures.maxVersion=1.0.0`.

A suite whose `version` isn't covered (different `MAJOR`, or a higher
`MINOR` than the cap) is skipped, not failed — mirroring the compatibility
rule in SPEC_V1-0-0.md §2 (same `MAJOR`, `MINOR` no higher than what's
supported; `PATCH` never matters). Leaving the knob unset runs every fixture
regardless of the version it declares, which is the default today since
every suite here declares `"1.0.0"`.

## Scope

This format only expresses *evaluation* outcomes (`can` returning `allow`/
`deny`), so it covers §4 through §7 and the evaluation-facing edge cases
(EC-1 through EC-9, EC-12 through EC-15) of the edge-case catalogue in §8.
It does **not** cover the purely construction-time validation requirements
(malformed rule tuples — EC-10, `version` incompatibility — EC-11, a
both-sides-wildcarded rule carrying a `Conditions` element, or
`meta.actions`/`meta.subjects`/`meta.customOperators` catalog-coverage
enforcement — EC-8/EC-13's `PolicyLoadException` half), since those are
expected to *throw* at construction rather than resolve to an `allow`/`deny`
outcome. Those requirements should be covered separately, e.g. by
implementation-specific unit tests asserting the right exception type.

## Files

- `01-action-subject-matching.yaml` — exact action/subject matching, case
  sensitivity (EC-12), default deny (EC-1, EC-2).
- `02-rule-ordering.yaml` — last-rule-wins, blanket rules being overridden or
  overriding (EC-3, EC-4, EC-5).
- `03-wildcards.yaml` — `_ANY_` default, a custom wildcard token, and
  disabling the wildcard mechanism via `null` (§4, §5, §6 property 4, EC-6,
  EC-14).
- `04-conditions-fields.yaml` — bare-value shorthand (§7.2), missing field vs.
  explicit `null` (§7.3), nested field conditions and `$field` (§7.4.10,
  §7.4.11).
- `05-operators-comparison.yaml` — `$eq`, `$ne`, `$gt`/`$gte`/`$lt`/`$lte`
  (§7.4.1–§7.4.3).
- `06-operators-collections.yaml` — `$in`, `$has` (§7.4.4, §7.4.5).
- `07-operators-substr.yaml` — `$substr`'s pattern language (§7.4.6).
- `08-operators-logic.yaml` — `$or`, `$and`, `$not`, and multi-key AND
  (§7.4.7–§7.4.9, §7.5).
- `09-custom-operators.yaml` — unregistered custom operators always evaluate
  to `false` (§7.4.12, EC-13, EC-15).
- `10-subject-shapes.yaml` — bare type vs. wrapped instance (EC-7, EC-9).
- `11-worked-example.yaml` — an end-to-end mirror of the spec's own Appendix
  policy.
