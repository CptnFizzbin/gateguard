import type { SubjectDef, SubjectRef } from "./SubjectDef";
import { Subject } from "./Subject";

export function createSubject<TSubject>(name: string): SubjectDef<TSubject> {
  return {
    [Symbol.for("SubjectBrand")]: undefined as any,
    __name: name,
    wrap(obj: TSubject): SubjectRef<TSubject> {
      return {
        [Symbol.for("SubjectBrand")]: undefined as any,
        __name: name,
        value: obj,
      };
    },
  } as SubjectDef<TSubject>;
}
