# Open Questions — v1 Spec

Items raised during spec review that need a decision before being folded
back into `SPEC_V1-0-0.md` (or explicitly rejected).

## 1. Should `$ne` treat a missing field as a match (`true`), same as explicit `null`?

Current spec (§7.3) requires a **missing field** to make the entire
field-condition evaluate to `false`, regardless of the nested operator —
e.g. `{status: {$ne: "archived"}}` against a subject with no `status` key
is `false`, not `true`. The spec gives an explicit rationale: "not
archived" requires a `status` that is present and isn't `"archived"`, not
the absence of a `status`.

By contrast, an **explicit `null`** value is compared normally: since
`$ne` is the exact negation of `$eq` (value equality), `null !== "archived"`
is `true`, so `{status: {$ne: "archived"}}` matches when `status` is
explicitly `null`.

Raised question: should `$ne` (and possibly other operators) be changed so
that a *missing* field is also treated as an automatic match/`true`,
collapsing the missing-vs-null distinction for `$ne` specifically? This
would be a behavioral change from the currently-documented (and
conformance-tested) semantics in §7.3, not just a clarification.

Needs a decision: keep current behavior, or amend §7.3/§7.4.2 to special-case
`$ne` (and decide whether that special-casing should extend to other
operators too).

## 2. Should `meta.operators` catalog-completeness enforcement be a MUST or a SHOULD-with-opt-out?

Current spec (§3.2.3, EC-13) makes this unconditional: when `meta.operators`
is declared, `Policy.from(...)` **MUST** throw a `PolicyLoadException` at
construction if any rule's condition uses a custom `$op` not listed in the
catalog — the same enforcement tier as `meta.actions`/`meta.subjects`
(EC-8). There's no opt-out; this is different from `$op`s referenced when
`meta.operators` isn't declared at all (or doesn't mention that name),
which is a separate, already-settled case (§7.4.12: evaluates to `false`
at runtime, no construction-time check applies since there's no catalog to
violate).

Raised expectation: this eager catalog-completeness walk should be a
**SHOULD**, not a MUST, with room for an implementation (or a developer,
explicitly) to skip it — e.g. when a policy has enough complex/nested
conditions that eagerly walking all of them at construction is costly, or
when a developer deliberately opts out for other reasons. This would
mirror the pattern §2 already uses for MINOR-version checking:
*"Implementations MAY provide an option to disable MINOR version
verification... but this MUST be an explicit opt-in; the default behavior
is the MINOR check above."* — i.e. the default stays strict, but a named,
explicit opt-out exists.

Needs a decision: keep the current unconditional MUST (uniform validation,
no escape hatch), or amend §3.2.3/EC-13 to add an explicit, default-on
opt-out mechanism the same shape as §2's MINOR-version-check opt-out.
