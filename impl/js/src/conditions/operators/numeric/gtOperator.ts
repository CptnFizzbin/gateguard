import {createOperator} from "../operator";
import {numericCompare} from "./numericCompare";

/** §7.4.3: `$gt` - numeric greater-than. */
export const GtOperator = createOperator("$gt", (subject, value) => numericCompare(subject, value, (a, b) => a > b))
