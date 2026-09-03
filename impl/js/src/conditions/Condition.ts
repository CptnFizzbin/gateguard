import {JsonValue} from "../lib/json";

export type Condition =
  | JsonValue
  | { [key: string]: Condition };

export type CustomConditionChecker = {
  [key: string]: (subject: any, value: any) => boolean;
};
