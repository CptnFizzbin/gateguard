import type { Condition } from "../Condition";

/**
 * §7.4.11: explicit field access, required when a subject field's name
 * itself starts with "$" (the bare-key form `{ $type: Condition }` would
 * otherwise be read as the `$type` operator).
 */
export interface ExplicitFieldCondition {
  $field: [string, Condition];
}
