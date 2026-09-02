import type { NumberCondition } from "./numberConditions";
import type { StringCondition } from "./stringConditions";
import type { GroupCondition } from "./groupConditions";
import type { LogicCondition } from "./logicConditions";
import type { ExplicitFieldCondition } from "./fieldConditions";

export type Condition =
  | string
  | number
  | boolean
  | null
  | NumberCondition
  | StringCondition
  | GroupCondition
  | LogicCondition
  | ExplicitFieldCondition
  | { [key: string]: Condition };

export type CustomConditionChecker = {
  [key: string]: (subject: any, value: any) => boolean;
};
