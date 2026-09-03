import {createOperator} from "../operator";

/** §7.4.2: `$ne` - the exact negation of `$eq` (eqOperator.ts) for the same subject/value pair. No notion of a type mismatch, same as `$eq`. */
export const NeOperator = createOperator("$ne", (subject, value) => subject !== value)
