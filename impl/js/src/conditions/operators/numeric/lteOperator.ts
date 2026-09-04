import {createOperator} from "../operator";
import {numericCompare} from "./numericCompare";

/** §7.4.3: `$lte` - numeric less-than-or-equal. */
export const LteOperator = createOperator("$lte", (subject, value) => numericCompare(subject, value, (a, b) => a <= b))
