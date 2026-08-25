// Number comparison conditions
export interface GtCondition<T = any> {
  $gt: T;
}

export interface GteCondition<T = any> {
  $gte: T;
}

export interface LtCondition<T = any> {
  $lt: T;
}

export interface LteCondition<T = any> {
  $lte: T;
}

export type NumberCondition = GtCondition | GteCondition | LtCondition | LteCondition;
