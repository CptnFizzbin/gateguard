export type Condition =
  | string
  | number
  | boolean
  | null
  | { [key: string]: Condition };

export type CustomConditionChecker = {
  [key: string]: (subject: any, value: any) => boolean;
};
