import { Action } from "../action";
import { Subject, SubjectDef } from "../subject";
import { Condition } from "../conditions";

export interface Rule<
  TActions extends Action = Action,
  TSubjects extends Subject = Subject
> {
  action: TActions | string;
  subject: TSubjects | SubjectDef | string;
  conditions?: Condition;
  inverted: boolean;
}

export interface PolicyDefinition<
  TActions extends Action = Action,
  TSubjects extends Subject = Subject
> {
  version: number;
  name?: string;
  description?: string;
  rules: {
    allow: Array<[TActions | string, TSubjects | SubjectDef | string, Condition?]>;
    deny: Array<[TActions | string, TSubjects | SubjectDef | string, Condition?]>;
  };
}
