import { Action } from "../action";
import { Subject, SubjectDef, SubjectRef } from "../subject";
import { Condition, ConditionResolver, CustomConditionChecker } from "../conditions";
import { PolicyError } from "../errors";
import type { PolicyDefinition } from "../policy/PolicyDefinition";

export class Policy<
  TActions extends readonly Action[] = readonly Action[],
  TSubjects extends readonly Subject[] = readonly Subject[]
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
   * Builds a Policy from an already-parsed PolicyDefinition. KeyCard itself
   * never reads or writes policy.yaml text - an application (or a test, via
   * a YAML library of its own choosing) parses the file into a plain
   * PolicyDefinition object and hands it to KeyCard.
   */
  static from<
    TActions extends readonly Action[] = readonly Action[],
    TSubjects extends readonly Subject[] = readonly Subject[]
  >(definition: PolicyDefinition<TActions, TSubjects>, customConditions?: CustomConditionChecker): Policy<TActions, TSubjects> {
    return new Policy(definition, customConditions);
  }

  /** Alias of `from`. */
  static fromDto<
    TActions extends readonly Action[] = readonly Action[],
    TSubjects extends readonly Subject[] = readonly Subject[]
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

  can(action: TActions[number], subject: TSubjects[number] | SubjectDef | SubjectRef | string): boolean {
    if (!this.checkPermission(action, subject, false)) {
      return false;
    }
    // A matching deny rule overrides a matching allow rule.
    return !this.checkPermission(action, subject, true);
  }

  cannot(action: TActions[number], subject: TSubjects[number] | SubjectDef | SubjectRef | string): boolean {
    return !this.can(action, subject);
  }

  require(action: TActions[number], subject: TSubjects[number] | SubjectDef | SubjectRef | string): void {
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

  private checkPermission(
    action: TActions[number],
    subject: TSubjects[number] | SubjectDef | SubjectRef | string,
    inverted: boolean
  ): boolean {
    const subjectName = this.getSubjectName(subject);
    const rules = inverted ? this.definition.rules.deny : this.definition.rules.allow;

    for (const [ruleAction, ruleSubject, ruleCondition] of rules) {
      if (this.matchesAction(action, ruleAction) && this.matchesSubject(subjectName, ruleSubject)) {
        if (!ruleCondition || this.resolver.evaluate(this.getSubjectValue(subject), ruleCondition)) {
          return true;
        }
      }
    }

    return false;
  }

  private getSubjectName(subject: TSubjects[number] | SubjectDef | SubjectRef | string): string {
    if (typeof subject === "string") {
      return subject;
    }
    return subject.__name;
  }

  private getSubjectValue(subject: TSubjects[number] | SubjectDef | SubjectRef | string): any {
    if (typeof subject === "string") {
      return subject;
    }
    if ("value" in subject && subject.value !== undefined) {
      return subject.value;
    }
    return subject;
  }

  private matchesAction(action: TActions[number], ruleAction: TActions[number] | string): boolean {
    return action === ruleAction || ruleAction === "*";
  }

  private matchesSubject(
    subjectName: string,
    ruleSubject: TSubjects[number] | SubjectDef | string
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
