// Group/array conditions
export interface InCondition<T = any> {
  $in: T[];
}

export interface HasCondition<T = any> {
  $has: T;
}

export type GroupCondition = InCondition | HasCondition;
