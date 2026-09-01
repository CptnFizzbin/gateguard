import type { Condition } from "../Condition";

// Logic operators
export interface OrCondition {
  $or: Condition[];
}

export interface AndCondition {
  $and: Condition[];
}

export interface NotCondition {
  $not: Condition;
}

export type LogicCondition = OrCondition | AndCondition | NotCondition;
