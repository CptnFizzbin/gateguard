// Equality and substring-pattern conditions
export interface EqCondition<T = any> {
  $eq: T;
}

export interface NeCondition<T = any> {
  $ne: T;
}

/** §7.4.6: a small, non-regex substring pattern language - see SPEC_V1-0-0.md §7.4.6. */
export interface SubstrCondition {
  $substr: string;
}

export type StringCondition = EqCondition | NeCondition | SubstrCondition;
