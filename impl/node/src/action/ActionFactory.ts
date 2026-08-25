import { Action } from "./Action";

export function createAction<T extends string>(name: T): Action<T> {
  return name as Action<T>;
}
