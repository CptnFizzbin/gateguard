// String matching conditions
export interface EqCondition<T = any> {
  $eq: T;
}

export interface RegexCondition {
  $rgx: string | RegExp;
}

export type StringCondition = EqCondition | RegexCondition;
