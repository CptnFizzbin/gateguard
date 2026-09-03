import { Action } from "../action";
import { Subject } from "../subject";
import { Condition } from "../conditions";
import type { RuleTuple, Meta, Effect, PolicyDefinition } from "../policy/PolicyDefinition";
import { Policy } from "../policy/Policy";
import { PolicyArgumentError } from "../errors";
import { DISABLED, effectiveAnyAction, effectiveAnySubject } from "../policy/wildcards";

/** The v1 SemVer this builder implements - stamped onto every `buildDef()` output, per SPEC_V1-0-0.md §2. */
export const BUILDER_VERSION = "1.0.0";

export class PolicyBuilder<
  TActions extends Action = Action,
  TSubjects extends Subject = Subject
> {
  private rules: RuleTuple[] = [];
  private meta: Meta;

  constructor(meta: Meta = {}) {
    this.meta = meta;
  }

  allow<T extends TActions>(action: T, subject: TSubjects, conditions?: Condition): this {
    return this.addRule("allow", action, subject, conditions);
  }

  deny<T extends TActions>(action: T, subject: TSubjects, conditions?: Condition): this {
    return this.addRule("deny", action, subject, conditions);
  }

  build(): Policy<TActions, TSubjects> {
    return new Policy(this.buildDef());
  }

  buildDef(): PolicyDefinition {
    return {
      version: BUILDER_VERSION,
      meta: Object.keys(this.meta).length > 0 ? this.meta : undefined,
      rules: this.rules,
    };
  }

  private addRule(effect: Effect, action: TActions, subject: TSubjects, conditions?: Condition): this {
    if (conditions) {
      // SPEC_V1-0-0.md §6 property 5, EC-6: a rule wildcarded on both the
      // action and the subject MUST NOT carry a Conditions element - the
      // builder MUST catch this immediately, rather than waiting for
      // eventual construction (Policy.from) to catch it.
      const anyAction = effectiveAnyAction(this.meta);
      const anySubject = effectiveAnySubject(this.meta);
      if (
        anyAction !== DISABLED && action.name === anyAction &&
        anySubject !== DISABLED && subject.name === anySubject
      ) {
        throw new PolicyArgumentError(
          `A rule wildcarded on both the action ("${anyAction}") and the subject ("${anySubject}") MUST NOT carry a Conditions element (SPEC_V1-0-0.md §6 property 5, EC-6).`
        );
      }
    }

    const rule: RuleTuple = conditions !== undefined
      ? [effect, action.name, subject.name, conditions]
      : [effect, action.name, subject.name];
    this.rules.push(rule);
    return this;
  }
}
