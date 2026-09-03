import { Action } from "../action";
import { Subject, SubjectDef, SubjectRef } from "../subject";
import { Condition, ConditionResolver, CustomConditionChecker } from "../conditions";
import { PolicyError } from "../errors";
import type { PolicyDefinition } from "../policy/PolicyDefinition";

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
    this.definition = definition;
    this.resolver = new ConditionResolver(customConditions);
  }

  /**
   * Builds a Policy from an already-parsed PolicyDefinition. GateGuard itself
   * never reads or writes policy.yaml text - an application (or a test, via
   * a YAML library of its own choosing) parses the file into a plain
   * PolicyDefinition object and hands it to GateGuard.
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

  append(definition: PolicyDefinition<TActions, TSubjects>): Policy<TActions, TSubjects> {
    const merged: PolicyDefinition<TActions, TSubjects> = {
      version: this.definition.version,
      rules: {
        allow: [...this.definition.rules.allow, ...definition.rules.allow],
        deny: [...this.definition.rules.deny, ...definition.rules.deny],
      },
    };
    return new Policy(merged, this.resolver["customCheckers"]);
  }

  /** True iff a rule allows this action/subject, and no rule denies it. */
  private checkPermission(
    action: TActions,
    subject: TSubjects | SubjectDef | SubjectRef | string
  ): boolean {
    return (
      this.matchesAnyRule(this.definition.rules.allow, action, subject) &&
      !this.matchesAnyRule(this.definition.rules.deny, action, subject)
    );
  }

  private matchesAnyRule(
    rules: Array<[TActions | string, TSubjects | SubjectDef | string, Condition?]>,
    action: TActions,
    subject: TSubjects | SubjectDef | SubjectRef | string
  ): boolean {
    const subjectName = this.getSubjectName(subject);

    for (const [ruleAction, ruleSubject, ruleCondition] of rules) {
      if (this.matchesAction(action, ruleAction) && this.matchesSubject(subjectName, ruleSubject)) {
        if (!ruleCondition || this.resolver.evaluate(this.getSubjectValue(subject), ruleCondition)) {
          return true;
        }
      }
    }

    return false;
  }

  private getSubjectName(subject: TSubjects | SubjectDef | SubjectRef | string): string {
    if (typeof subject === "string") {
      return subject;
    }
    return subject.__name;
  }

  private getSubjectValue(subject: TSubjects | SubjectDef | SubjectRef | string): any {
    if (typeof subject === "string") {
      return subject;
    }
    if ("value" in subject && subject.value !== undefined) {
      return subject.value;
    }
    return subject;
  }

  private matchesAction(action: TActions, ruleAction: TActions | string): boolean {
    return action === ruleAction || ruleAction === "*";
  }

  private matchesSubject(
    subjectName: string,
    ruleSubject: TSubjects | SubjectDef | string
  ): boolean {
    if (typeof ruleSubject === "string") {
      return subjectName === ruleSubject || ruleSubject === "*";
    }
    if ("__name" in ruleSubject) {
      return subjectName === ruleSubject.__name;
    }
    return false;
  }
}
