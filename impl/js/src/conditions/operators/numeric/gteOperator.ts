import {createOperator} from "../operator";
import {numericCompare} from "./numericCompare";

/** §7.4.3: `$gte` - numeric greater-than-or-equal. */
export const GteOperator = createOperator("$gte", (subject, value) => numericCompare(subject, value, (a, b) => a >= b))
