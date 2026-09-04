# Open Questions — v1 Spec

Items raised during spec review that needed a decision before being folded
back into `SPEC_V1-0-0.md` (or explicitly rejected). Resolved items are kept
here as a record of the decision and where it landed in the spec.

## 1. RESOLVED — Should `$ne` treat a missing field as a match (`true`), same as explicit `null`?

Previously, §7.3 made a **missing field** evaluate the entire field-condition
to `false` regardless of the nested operator, with `{status: {$ne: "archived"}}`
against a subject with no `status` key given as an explicit example of `false`.
That was in tension with §7.4.2's own requirement that `$ne` **MUST** be the
exact negation of `$eq` for the same subject/value pair — since `$eq` on a
missing field is `false`, `$ne` should have been `true` by that contract.

**Decision:** `$ne` is the exact negation of `$eq`, including on a missing
field — a missing field genuinely isn't equal to anything, so `$ne` on it
evaluates to `true`. Every other operator (`$eq` itself, `$gt`, `$in`, `$has`,
`$substr`, ...) keeps the blanket `false`, since only `$ne` is specified as an
exact negation of another operator. Landed in §7.3 and §7.4.2.

**Follow-up worth a look:** `$not` (§7.4.9) has the same "MUST be the exact
negation of evaluating `Condition`" contract as `$ne`, so the same tension
technically applies to it too — e.g. does `{ status: { $not: { $eq: "archived" } } }`
on a missing `status` also become `true`, by the same reasoning? This
decision was scoped to `$ne` specifically (the case actually raised); `$not`
was not addressed and still follows the general missing-field-is-`false` rule
as written. Flag if that should be revisited.

## 2. RESOLVED — Should `meta.operators` catalog-completeness enforcement be a MUST or a SHOULD-with-opt-out?

Previously, §3.2.3/EC-13 made it an unconditional MUST: whenever `meta.operators`
is declared, construction had to throw if any rule's condition referenced a
custom `$op` not listed in the catalog — same enforcement tier as
`meta.actions`/`meta.subjects` (EC-8).

**Decision:** split into two different obligations, at two different
strengths:

- **Registration check** (EC-15) — for every name listed in `meta.operators`,
  an operator implementation **MUST** actually be registered on the
  `Policy`/`ConditionResolver` instance, checked unconditionally at
  construction. Unchanged — this was already a MUST and stays one.
- **Deep condition-tree validation** (EC-13) — walking every rule's
  `Conditions` to confirm every custom `$op` referenced anywhere is listed in
  `meta.operators` — is now a **SHOULD**, not a MUST. An implementation MAY
  skip this walk (or expose skipping it as an explicit opt-out) since it can
  be costly over many/deeply-nested conditions. Skipping it has no effect on
  evaluation-time behavior: an uncataloged `$op` a rule actually reaches
  still resolves to `false` per §7.4.12 either way.

Landed in §3.2.3, §7.4.12, and EC-13.
