import { Subject } from "./Subject";

export interface SubjectDef<TSubject = unknown> extends Subject<TSubject> {
  wrap(obj: TSubject): SubjectRef<TSubject>;
}

export interface SubjectRef<TSubject = unknown> extends Subject<TSubject> {
  readonly value: TSubject;
}
