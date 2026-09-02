import * as semver from "semver";
import { Action } from "../action";
import { Subject, SubjectDef, SubjectRef } from "../subject";
import { Condition, ConditionResolver, CustomConditionChecker } from "../conditions";
import { BUILTIN_OPERATORS } from "../conditions/ConditionResolver";
import { PolicyError, PolicyLoadException, PolicyVersionException } from "../errors";
import type { PolicyDefinition, RuleTuple } from "./PolicyDefinition";
import { DISABLED, effectiveAnyAction, effectiveAnySubject, subjectNameOf } from "./wildcards";

/** The highest version this implementation supports natively - SPEC_V1-0-0.md §2. PATCH never affects compatibility. */
const SUPPORTED_VERSION = "1.0.0";
const SUPPORTED_MAJOR = semver.major(SUPPORTED_VERSION);
const SUPPORTED_MINOR = semver.minor(SUPPORTED_VERSION);
/** Same MAJOR as SUPPORTED_VERSION, MINOR no higher - PATCH is irrelevant either way (§2). */
const COMPATIBLE_RANGE = `>=${SUPPORTED_MAJOR}.0.0 <${SUPPORTED_MAJOR}.${SUPPORTED_MINOR + 1}.0`;

/**
 * Recursively collects every non-built-in, `$`-prefixed operator name used
 * anywhere in a Conditions tree - used to enforce `meta.customOperators`
 * coverage at construction time (§3.2.3, EC-13).
 */
function collectCustomOperators(condition: Condition | undefined, out: Set<string>): void {
  if (condition === undefined || condition === null || typeof condition !== "object") return;

  for (const [key, value] of Object.entries(condition as Record<string, any>)) {
    if (key.startsWith("$")) {
      if (!BUILTIN_OPERATORS.has(key)) out.add(key);
      if (key === "$or" || key === "$and") {
        if (Array.isArray(value)) value.forEach((c: Condition) => collectCustomOperators(c, out));
      } else if (key === "$not") {
        collectCustomOperators(value, out);
      } else if (key === "$field" && Array.isArray(value) && value.length === 2) {
        collectCustomOperators(value[1], out);
      }
    } else {
      collectCustomOperators(value, out);
    }
  }
}

export class Policy<
  TActions extends Action = Action,
  TSubjects extends Subject = Subject
> {
  private definition: PolicyDefinition<TActions, TSubjects>;
  private resolver: ConditionResolver;

  constructor(
    definition: PolicyDefinition<TActions, TSubjects>,
    customConditions?: CustomConditionChecker
  ) {
    Policy.validateVersion(definition.version);
    Policy.validateRules(definition);

    this.definition = definition;
    this.resolver = new ConditionResolver(customConditions, definition.meta?.customOperators);
  }

  /**
   * Builds a Policy from an already-parsed PolicyDefinition. KeyCard itself
   * never reads or writes policy.yaml text - an application (or a test, via
   * a YAML library of its own choosing) parses the file into a plain
   * PolicyDefinition object and hands it to KeyCard.
   */
  static from<
    TActions extends Action = Action,
    TSubjects extends Subject = Subject
  >(definition: PolicyDefinition<TActions, TSubjects>, customConditions?: CustomConditionChecker): Policy<TActions, TSubjects> {
    return new Policy(definition, customConditions);
  }

  /** Alias of `from`. */
  static fromDto<
    TActions extends Action = Action,
    TSubjects extends Subject = Subject
  >(definition: PolicyDefinition<TActions, TSubjects>, customConditions?: CustomConditionChecker): Policy<TActions, TSubjects> {
    return Policy.from(definition, customConditions);
  }

  def(): PolicyDefinition<TActions, TSubjects> {
    return this.toDefinition();
  }

  /** Returns the PolicyDefinition backing this policy. */
  toDefinition(): PolicyDefinition<TActions, TSubjects> {
    return this.definition;
  }

  /** Alias of `toDefinition`. */
  toDto(): PolicyDefinition<TActions, TSubjects> {
    return this.toDefinition();
  }

  can(action: TActions, subject: TSubjects | SubjectDef | SubjectRef | string): boolean {
    return this.checkPermission(action, subject);
  }

  cannot(action: TActions, subject: TSubjects | SubjectDef | SubjectRef | string): boolean {
    return !this.can(action, subject);
  }

  require(action: TActions, subject: TSubjects | SubjectDef | SubjectRef | string): void {
    if (!this.can(action, subject)) {
      throw new PolicyError(
        `Access denied: cannot ${action} on ${typeof subject === "string" ? subject : JSON.stringify(subject)}`
      );
    }
  }

  /**
   * SPEC_V1-0-0.md §6: reverse scan over `rules`, returning the effect of
   * the first (i.e. most-recently-declared) rule whose action, subject,
   * and (if present) conditions all match. There is no independent
   * "allow AND NOT deny" veto and no combination of multiple matching
   * rules: exactly one rule decides the outcome, or none does and the
   * result is default deny.
   */
  private checkPermission(
    action: TActions,
    subject: TSubjects | SubjectDef | SubjectRef | string
  ): boolean {
    const meta = this.definition.meta;
    const anyAction = effectiveAnyAction(meta);
    const anySubject = effectiveAnySubject(meta);
    const subjectName = this.getSubjectName(subject);
    const rules = this.definition.rules;

    for (let i = rules.length - 1; i >= 0; i--) {
      const [effect, ruleAction, ruleSubject, ruleConditions] = rules[i];

      if (!this.matchesAction(action, ruleAction, anyAction)) continue;
      if (!this.matchesSubject(subjectName, ruleSubject, anySubject)) continue;

      if (ruleConditions) {
        // A conditional rule can never be satisfied by a bare-type/no-instance
        // check - there's no instance data for the condition to inspect (EC-7).
        if (!this.hasInstance(subject)) continue;
        if (!this.resolver.evaluate(this.getSubjectValue(subject), ruleConditions)) continue;
        return effect === "allow";
      }

      return effect === "allow";
    }

    return false; // EC-1, EC-2: default deny.
  }

  private matchesAction(action: TActions, ruleAction: TActions | string, anyAction: string | typeof DISABLED): boolean {
    return action === ruleAction || (anyAction !== DISABLED && ruleAction === anyAction);
  }

  private matchesSubject(
    subjectName: string,
    ruleSubject: TSubjects | SubjectDef | string,
    anySubject: string | typeof DISABLED
  ): boolean {
    const ruleSubjectName = subjectNameOf(ruleSubject);
    return subjectName === ruleSubjectName || (anySubject !== DISABLED && ruleSubjectName === anySubject);
  }

  private getSubjectName(subject: TSubjects | SubjectDef | SubjectRef | string): string {
    return subjectNameOf(subject);
  }

  /**
   * True when `subject` carries an instance value a Conditions element can
   * be evaluated against (a `SubjectRef`, or a plain data object) - false
   * for a bare string or a `SubjectDef` type token, neither of which has
   * instance data (§5, EC-7, EC-9).
   */
  private hasInstance(subject: TSubjects | SubjectDef | SubjectRef | string): boolean {
    if (typeof subject === "string") return false;
    if (subject && typeof (subject as any).wrap === "function") return false; // a bare SubjectDef
    return true;
  }

  /**
   * The subject value conditions are evaluated against. EC-9: a bare
   * `SubjectDef` MUST NOT expose fields that make ordinary domain
   * conditions accidentally match - `hasInstance` above already keeps
   * every conditional rule from reaching this method for one, so this
   * path only ever runs for a `SubjectRef` or an already-flat instance
   * value.
   */
  private getSubjectValue(subject: TSubjects | SubjectDef | SubjectRef | string): any {
    if (typeof subject === "string") return subject;
    if ("value" in subject && (subject as any).value !== undefined) return (subject as any).value;
    return subject;
  }

  private static validateVersion(version: string): void {
    // §2.1: PATCH (and MINOR) may be omitted - "1"/"1.0" are valid
    // shorthand for "1.0.0" - so coerce before comparing rather than
    // requiring a strict three-component string.
    const coerced = semver.coerce(version);
    if (!coerced || !semver.satisfies(coerced, COMPATIBLE_RANGE)) {
      throw new PolicyVersionException(
        `Unsupported policy version "${version}": this implementation supports ${SUPPORTED_MAJOR}.0.0 through ${SUPPORTED_MAJOR}.${SUPPORTED_MINOR}.x (SPEC_V1-0-0.md §2).`
      );
    }
  }

  private static validateRules(definition: PolicyDefinition<any, any>): void {
    const meta = definition.meta;
    const anyAction = effectiveAnyAction(meta);
    const anySubject = effectiveAnySubject(meta);

    const actionsCatalog = meta?.actions ? new Set(meta.actions.map((a) => String(a))) : undefined;
    const subjectsCatalog = meta?.subjects ? new Set(meta.subjects.map((s) => subjectNameOf(s))) : undefined;
    const customOpCatalog = meta?.customOperators ? new Set(meta.customOperators) : undefined;

    for (const rule of definition.rules as RuleTuple<any, any>[]) {
      if (!Array.isArray(rule) || rule.length < 3) {
        throw new PolicyLoadException(
          `Malformed rule tuple (fewer than 3 elements): ${JSON.stringify(rule)} (SPEC_V1-0-0.md §3.3, EC-10).`
        );
      }

      const [effect, action, subject, conditions] = rule;

      if (effect !== "allow" && effect !== "deny") {
        throw new PolicyLoadException(
          `Malformed rule tuple: effect must be "allow" or "deny", got ${JSON.stringify(effect)} (SPEC_V1-0-0.md §3.3, EC-10).`
        );
      }
      if (typeof action !== "string") {
        throw new PolicyLoadException(
          `Malformed rule tuple: action must be a string, got ${JSON.stringify(action)} (SPEC_V1-0-0.md §3.3, EC-10).`
        );
      }

      let subjectName: string;
      try {
        subjectName = subjectNameOf(subject);
      } catch {
        throw new PolicyLoadException(
          `Malformed rule tuple: subject is not a valid subject, got ${JSON.stringify(subject)} (SPEC_V1-0-0.md §3.3, EC-10).`
        );
      }

      const isWildcardAction = anyAction !== DISABLED && action === anyAction;
      const isWildcardSubject = anySubject !== DISABLED && subjectName === anySubject;

      if (isWildcardAction && isWildcardSubject && conditions) {
        throw new PolicyLoadException(
          `Rule [${effect}, ${action}, ${subjectName}] is wildcarded on both the action and the subject but carries a Conditions element - this MUST be unconditional (SPEC_V1-0-0.md §6 property 5, EC-6).`
        );
      }

      if (actionsCatalog && !isWildcardAction && !actionsCatalog.has(action)) {
        throw new PolicyLoadException(
          `Rule action "${action}" is not covered by meta.actions (SPEC_V1-0-0.md §3.2.2, EC-8).`
        );
      }
      if (subjectsCatalog && !isWildcardSubject && !subjectsCatalog.has(subjectName)) {
        throw new PolicyLoadException(
          `Rule subject "${subjectName}" is not covered by meta.subjects (SPEC_V1-0-0.md §3.2.2, EC-8).`
        );
      }

      if (customOpCatalog && conditions) {
        const used = new Set<string>();
        collectCustomOperators(conditions, used);
        for (const op of used) {
          if (!customOpCatalog.has(op)) {
            throw new PolicyLoadException(
              `Rule uses custom operator "${op}" not covered by meta.customOperators (SPEC_V1-0-0.md §3.2.3, EC-13).`
            );
          }
        }
      }
    }
  }
}
