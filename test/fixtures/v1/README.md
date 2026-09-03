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
  operators: [...]
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

Every fixture suite declares a SemVer `version`. Each compliance test suite
bakes in its own `COMPLIANT_VERSION` constant — the highest version its
adapter is actually written against
(`v1ConformanceFixtures.test.ts`'s `COMPLIANT_VERSION`,
`V1ConformanceFixtureTest`'s `COMPLIANT_VERSION`) — and a fixture whose
declared `version` exceeds it is skipped, not failed, mirroring the
compatibility rule in SPEC_V1-0-0.md §2 (same `MAJOR`, `MINOR` no higher
than what's supported; `PATCH` never matters). This is automatic: once
fixtures for a newer `MINOR` version are added, a compliance suite whose
adapter hasn't caught up yet skips them with no configuration required,
rather than failing on behavior it was never meant to support. Bump a
suite's `COMPLIANT_VERSION` only once its adapter has actually been updated
to handle whatever the newer version adds — not merely because such
fixtures now exist.

For a one-off run that deliberately narrows or widens that baked-in ceiling
without editing code:

- JS: set the `KEYCARD_FIXTURES_MAX_VERSION` env var, e.g.
  `KEYCARD_FIXTURES_MAX_VERSION=1.0.0 yarn test run`.
- Java: set the `keycard.fixtures.maxVersion` system property, e.g.
  `mvn test -Dkeycard.fixtures.maxVersion=1.0.0`.

Every suite here declares `"1.0.0"`, and both compliance suites currently
bake in `COMPLIANT_VERSION = "1.0.0"` too, so today this filtering is a
no-op in practice — it starts mattering the moment a fixture file declares
something newer than a given suite's baked-in ceiling.

## Scope

This format only expresses *evaluation* outcomes (`can` returning `allow`/
`deny`), so it covers §4 through §7 and the evaluation-facing edge cases
(EC-1 through EC-9, EC-12 through EC-14) of the edge-case catalogue in §8.
It does **not** cover the purely construction-time validation requirements
(malformed rule tuples — EC-10, `version` incompatibility — EC-11, a
both-sides-wildcarded rule carrying a `Conditions` element,
`meta.actions`/`meta.subjects`/`meta.operators` catalog-coverage enforcement
— EC-8/EC-13's `PolicyLoadException` half, a `meta.operators` entry with
nothing registered for it — EC-15, now a construction-time throw rather
than a runtime diagnostic, or a duplicate operator name across the
built-ins and whatever custom operators were supplied — EC-16), since those
are expected to *throw* at construction rather than resolve to an
`allow`/`deny` outcome. Those requirements should be covered separately,
e.g. by implementation-specific unit tests asserting the right exception
type.

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
- `09-custom-operators.yaml` — unregistered, uncataloged custom operators
  always evaluate to `false` (§7.4.12, EC-13). The cataloged-but-never-
  registered case (EC-15) is no longer expressible here now that it's a
  construction-time throw - see the file's own comment.
- `10-subject-shapes.yaml` — bare type vs. wrapped instance (EC-7, EC-9).
- `11-worked-example.yaml` — an end-to-end mirror of the spec's own Appendix
  policy.
