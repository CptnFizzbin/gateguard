import { Action } from "../action";
import { Subject, SubjectDef } from "../subject";
import { Condition } from "../conditions";

export interface Rule<
  TActions extends readonly Action[] = readonly Action[],
  TSubjects extends readonly Subject[] = readonly Subject[]
> {
  action: TActions[number] | string;
  subject: TSubjects[number] | SubjectDef | string;
  conditions?: Condition;
  inverted: boolean;
}

export interface PolicyDefinition<
  TActions extends readonly Action[] = readonly Action[],
  TSubjects extends readonly Subject[] = readonly Subject[]
> {
  version: number;
  rules: {
    allow: Array<[TActions[number] | string, TSubjects[number] | SubjectDef | string, Condition?]>;
    deny: Array<[TActions[number] | string, TSubjects[number] | SubjectDef | string, Condition?]>;
  };
}
