# Known Issues

This file tracks where the JS (`impl/js`) and Java (`impl/java`)
implementations currently diverge from [`SPEC_V1-0-0.md`](SPEC_V1-0-0.md),
the normative v1 policy specification. It exists to guide follow-up work —
it is not part of the spec itself, and fixing an implementation should
shrink this list, not the spec document. Section references below (§N)
point into `SPEC_V1-0-0.md`.

Both `impl/java` and `impl/js` now speak the v1 `rules`/`meta` schema
natively, evaluate via the §6 reverse-scan last-rule-wins algorithm, and
implement the construction-time validation this revision introduces
(malformed rule tuples, `version` incompatibility, the EC-6 wildcarded-
and-conditional check, `meta.actions`/`meta.subjects`/`meta.operators`
catalog coverage). Two gaps remain, both `impl/js`-only:

## `meta.operators` registration hardening (§3.2.3)

- **No construction-time EC-15 check.** When `meta.operators` declares a
  name that's never actually registered (built-in or custom) on the
  `Policy`/`ConditionResolver` instance, the spec requires this to be
  caught in full at construction time, regardless of whether any rule
  actually reaches that operator during evaluation. `impl/js`'s
  `ConditionResolver` doesn't do this yet - `impl/java`'s
  `ConditionResolver.assertAllRegistered(Collection<String>)`, called
  from `Policy`'s constructor right after building the resolver, is a
  model for the shape this should take.
- **No operator-registry collision check (EC-16).** `impl/js`'s
  `ConditionResolver` constructor merges the built-in `DefaultOperators`
  with the caller-supplied `Operator[]` via `Map.set`, so a custom
  operator sharing a `$name` with a built-in (or with another custom
  operator) silently overwrites it instead of throwing. `impl/java`'s
  `ConditionResolver.buildRegistry` is a model here too: it throws a
  `PolicyLoadException` immediately on any such collision, built-in-vs-
  custom or custom-vs-custom, rather than allowing a silent override.
