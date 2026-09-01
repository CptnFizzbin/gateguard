import type { Subject } from "./Subject";

/**
 * Infers the `readonly Subject[]` shape expected by `PolicyBuilder`/`Policy`
 * from a subjects map, so callers don't have to spell out
 * `typeof Subjects[keyof typeof Subjects]` by hand.
 *
 * @example
 * const Subjects = {
 *   Article: createSubject<{ id: number }>("Article"),
 * } as const;
 *
 * type AppSubjects = InferSubjects<typeof Subjects>;
 * new PolicyBuilder<AppActions, AppSubjects>()
 */
export type InferSubjects<T extends Record<string, Subject>> = readonly T[keyof T][];
