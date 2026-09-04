import {JsonValue} from "../lib/json";

export type Condition =
  | JsonValue
  | { [key: string]: Condition };
