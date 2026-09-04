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
