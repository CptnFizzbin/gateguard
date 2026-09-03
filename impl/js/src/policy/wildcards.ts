import type { Meta } from "./PolicyDefinition";

/**
 * Returned by `effectiveAnyAction`/`effectiveAnySubject` when a policy
 * explicitly disables that wildcard position (`meta.anyAction`/
 * `meta.anySubject: null`). No ordinary string can ever equal this
 * sentinel, so the wildcard branch of `matchesAction`/`matchesSubject`
 * never succeeds for that position (SPEC_V1-0-0.md §3.2.1, §4, §5, §6).
 */
export const DISABLED: unique symbol = Symbol("keycard:wildcard-disabled");

const DEFAULT_WILDCARD = "_ANY_";

/** meta.anyAction: absent -> "_ANY_" default; explicit string -> that string; explicit null -> DISABLED. */
export function effectiveAnyAction(meta?: Meta<any, any>): string | typeof DISABLED {
  if (!meta || meta.anyAction === undefined) return DEFAULT_WILDCARD;
  if (meta.anyAction === null) return DISABLED;
  return meta.anyAction;
}

/** meta.anySubject: absent -> "_ANY_" default; explicit string -> that string; explicit null -> DISABLED. */
export function effectiveAnySubject(meta?: Meta<any, any>): string | typeof DISABLED {
  if (!meta || meta.anySubject === undefined) return DEFAULT_WILDCARD;
  if (meta.anySubject === null) return DISABLED;
  return meta.anySubject;
}

/**
 * Extracts the string name from any of the shapes a Subject/rule-Subject
 * position can take: a bare string, or an object carrying `__name`
 * (a `SubjectDef`/`SubjectRef`-shaped value). Throws for anything else -
 * callers that need to treat "not a valid subject" as a recoverable
 * condition (e.g. malformed-rule-tuple validation) should catch this.
 */
export function subjectNameOf(subject: unknown): string {
  if (typeof subject === "string") return subject;
  if (subject && typeof subject === "object" && typeof (subject as any).__name === "string") {
    return (subject as any).__name;
  }
  throw new TypeError(`Not a valid subject: ${JSON.stringify(subject)}`);
}
