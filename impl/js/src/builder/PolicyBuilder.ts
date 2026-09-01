import { Action } from "../action";
import { Subject, SubjectDef } from "../subject";
import { Condition } from "../conditions";
import type { Rule, PolicyDefinition } from "../policy/PolicyDefinition";

export class PolicyBuilder<
  TActions extends readonly Action[] = readonly Action[],
  TSubjects extends readonly Subject[] = readonly Subject[]
> {
  private rules: Rule<TActions, TSubjects>[] = [];

  allow<T extends TActions[number]>(
    action: T,
    subject: TSubjects[number] | SubjectDef | string,
    conditions?: Condition
  ): this {
    this.rules.push({
      action,
      subject,
      conditions,
      inverted: false,
    });
    return this;
  }

  deny<T extends TActions[number]>(
    action: T,
    subject: TSubjects[number] | SubjectDef | string,
    conditions?: Condition
  ): this {
    this.rules.push({
      action,
      subject,
      conditions,
      inverted: true,
    });
    return this;
  }

  build() {
    throw new Error("Not yet implemented");
  }

  buildDef(): PolicyDefinition<TActions, TSubjects> {
    const allow: Array<[TActions[number] | string, TSubjects[number] | SubjectDef | string, Condition?]> = [];
    const deny: Array<[TActions[number] | string, TSubjects[number] | SubjectDef | string, Condition?]> = [];

    for (const rule of this.rules) {
      const tuple: [TActions[number] | string, TSubjects[number] | SubjectDef | string, Condition?] = rule.conditions
        ? [rule.action, rule.subject, rule.conditions]
        : [rule.action, rule.subject];

      if (rule.inverted) {
        deny.push(tuple);
      } else {
        allow.push(tuple);
      }
    }

    return {
      version: 1,
      rules: { allow, deny },
    };
  }
}
