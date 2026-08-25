declare const SubjectBrand: unique symbol;

export type Subject<T = unknown> = {
  readonly [SubjectBrand]: T;
  readonly __name: string;
};
