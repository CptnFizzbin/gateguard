import type { SubjectDef, SubjectRef } from "./SubjectDef";

export function createSubject<TSubject>(name: string): SubjectDef<TSubject> {
  return {
    __name: name,
    wrap(obj: TSubject): SubjectRef<TSubject> {
      return {
        __name: name,
        value: obj,
      };
    },
  };
}
