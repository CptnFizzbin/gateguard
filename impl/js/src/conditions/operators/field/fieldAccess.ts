/**
 * §7.4.10, §7.3: true when `subject` is a non-null object carrying
 * `fieldName` - a missing field (or a non-object subject) is absence, not
 * a type issue, so this stays a plain predicate rather than throwing.
 * Shared by the bare-key field path (`ConditionResolver.fieldCheck`) and
 * the explicit `$field` operator (§7.4.11), which narrow the same way.
 */
export function hasField(subject: unknown, fieldName: string): subject is Record<string, unknown> {
  return subject !== null && typeof subject === "object" && fieldName in subject;
}
