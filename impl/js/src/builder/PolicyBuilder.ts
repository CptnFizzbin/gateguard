import { Action } from "../action";
import { Subject, SubjectDef } from "../subject";
import { Condition } from "../conditions";
import type { Rule, PolicyDefinition } from "../policy/PolicyDefinition";

export class PolicyBuilder<
  TActions extends Action = Action,
  TSubjects extends Subject = Subject
> {
  private rules: Rule<TActions, TSubjects>[] = [];

  allow<T extends TActions>(
    action: T,
    subject: TSubjects | SubjectDef | string,
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

  deny<T extends TActions>(
    action: T,
    subject: TSubjects | SubjectDef | string,
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
    const allow: Array<[TActions | string, TSubjects | SubjectDef | string, Condition?]> = [];
    const deny: Array<[TActions | string, TSubjects | SubjectDef | string, Condition?]> = [];

    for (const rule of this.rules) {
      const tuple: [TActions | string, TSubjects | SubjectDef | string, Condition?] = rule.conditions
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
