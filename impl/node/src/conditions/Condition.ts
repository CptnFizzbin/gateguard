import type { NumberCondition } from "./numberConditions";
import type { StringCondition } from "./stringConditions";
import type { GroupCondition } from "./groupConditions";
import type { LogicCondition } from "./logicConditions";

export type Condition = 
  | string
  | number
  | boolean
  | null
  | NumberCondition
  | StringCondition
  | GroupCondition
  | LogicCondition
  | { [key: string]: Condition };

export type CustomConditionChecker = {
  [key: string]: (subject: any, value: any) => boolean;
};
