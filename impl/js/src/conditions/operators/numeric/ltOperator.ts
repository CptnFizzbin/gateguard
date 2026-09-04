import {createOperator} from "../operator";
import {numericCompare} from "./numericCompare";

/** §7.4.3: `$lt` - numeric less-than. */
export const LtOperator = createOperator("$lt", (subject, value) => numericCompare(subject, value, (a, b) => a < b))
