import { Action } from "../action";
import { Subject, SubjectDef } from "../subject";
import { Condition } from "../conditions";

/** SPEC_V1-0-0.md §3.3: a rule's effect - allow it, or deny it. */
export type Effect = "allow" | "deny";

/**
 * `[Effect, Action, Subject, Conditions?]` - SPEC_V1-0-0.md §3.3. A
 * three-element tuple is an unconditional rule; `rules` is a single,
 * ordered list of these (not split by effect) - declaration order is
 * significant (§6).
 */
export type RuleTuple<
  TActions extends Action = Action,
  TSubjects extends Subject = Subject
> =
  | [Effect, TActions | string, TSubjects | SubjectDef | string]
  | [Effect, TActions | string, TSubjects | SubjectDef | string, Condition];

/** A `PolicyBuilder`-internal rule, before `buildDef()` flattens it into a `RuleTuple`. */
export interface Rule<
  TActions extends Action = Action,
  TSubjects extends Subject = Subject
> {
  effect: Effect;
  action: TActions | string;
  subject: TSubjects | SubjectDef | string;
  conditions?: Condition;
}

/** SPEC_V1-0-0.md §3.2: the optional `meta` object, grouping six independent, all-optional fields. */
export interface Meta<
  TActions extends Action = Action,
  TSubjects extends Subject = Subject
> {
  /**
   * The action wildcard token (§3.2.1, §4). Absent -> defaults to
   * `"_ANY_"`. Explicit `null` -> disables the action wildcard entirely
   * (no string, including `"_ANY_"`, has special meaning).
   */
  anyAction?: string | null;
  /** The subject wildcard token (§3.2.1, §5), symmetric with `anyAction` in every respect. */
  anySubject?: string | null;
  /** Declared action vocabulary; when present, enforced at construction (§3.2.2, EC-8). */
  actions?: Array<TActions | string>;
  /** Declared subject vocabulary; when present, enforced at construction (§3.2.2, EC-8). */
  subjects?: Array<TSubjects | SubjectDef | string>;
  /** Declared custom `$`-operator vocabulary; when present, enforced at construction (§3.2.3, EC-13). */
  customOperators?: string[];
  /** Opaque application data - never validated, enforced, or cross-checked (§3.2.4). */
  application?: unknown;
}

/** The `PolicyDefinition` document shape - SPEC_V1-0-0.md §3. */
export interface PolicyDefinition<
  TActions extends Action = Action,
  TSubjects extends Subject = Subject
> {
  /** Required SemVer string, e.g. `"1.0.0"` - see SPEC_V1-0-0.md §2. */
  version: string;
  /** Informational only - plays no role in evaluation. */
  name?: string;
  /** Informational only - plays no role in evaluation. */
  description?: string;
  meta?: Meta<TActions, TSubjects>;
  /** Ordered; declaration order is significant (§3.3, §6). MAY be empty. */
  rules: RuleTuple<TActions, TSubjects>[];
}
