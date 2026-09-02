import { Action } from "../action";
import { Subject, SubjectDef } from "../subject";
import { Condition } from "../conditions";
import type { Rule, RuleTuple, Meta, Effect, PolicyDefinition } from "../policy/PolicyDefinition";
import { Policy } from "../policy/Policy";
import { PolicyArgumentError } from "../errors";
import { DISABLED, effectiveAnyAction, effectiveAnySubject, subjectNameOf } from "../policy/wildcards";

/** The v1 SemVer this builder implements - stamped onto every `buildDef()` output, per SPEC_V1-0-0.md §2. */
export const BUILDER_VERSION = "1.0.0";

export class PolicyBuilder<
  TActions extends Action = Action,
  TSubjects extends Subject = Subject
> {
  private rules: Rule<TActions, TSubjects>[] = [];
  private meta: Meta<TActions, TSubjects>;

  constructor(meta: Meta<TActions, TSubjects> = {}) {
    this.meta = meta;
  }

  allow<T extends TActions>(
    action: T,
    subject: TSubjects | SubjectDef | string,
    conditions?: Condition
  ): this {
    return this.addRule("allow", action, subject, conditions);
  }

  deny<T extends TActions>(
    action: T,
    subject: TSubjects | SubjectDef | string,
    conditions?: Condition
  ): this {
    return this.addRule("deny", action, subject, conditions);
  }

  build(): Policy<TActions, TSubjects> {
    return new Policy(this.buildDef());
  }

  buildDef(): PolicyDefinition<TActions, TSubjects> {
    const rules: RuleTuple<TActions, TSubjects>[] = this.rules.map((rule) =>
      rule.conditions !== undefined
        ? [rule.effect, rule.action, rule.subject, rule.conditions]
        : [rule.effect, rule.action, rule.subject]
    );

    return {
      version: BUILDER_VERSION,
      meta: Object.keys(this.meta).length > 0 ? this.meta : undefined,
      rules,
    };
  }

  private addRule(
    effect: Effect,
    action: TActions | string,
    subject: TSubjects | SubjectDef | string,
    conditions?: Condition
  ): this {
    if (conditions) {
      // SPEC_V1-0-0.md §6 property 5, EC-6: a rule wildcarded on both the
      // action and the subject MUST NOT carry a Conditions element - the
      // builder MUST catch this immediately, rather than waiting for
      // eventual construction (Policy.from) to catch it.
      const anyAction = effectiveAnyAction(this.meta);
      const anySubject = effectiveAnySubject(this.meta);
      const subjectName = subjectNameOf(subject);
      if (anyAction !== DISABLED && action === anyAction && anySubject !== DISABLED && subjectName === anySubject) {
        throw new PolicyArgumentError(
          `A rule wildcarded on both the action ("${anyAction}") and the subject ("${anySubject}") MUST NOT carry a Conditions element (SPEC_V1-0-0.md §6 property 5, EC-6).`
        );
      }
    }

    this.rules.push({ effect, action, subject, conditions });
    return this;
  }
}
