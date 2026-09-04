import {createOperator} from "../operator";
import {PolicyTypeMismatchError} from "../../../errors/PolicyTypeMismatchError";

export const AndOperator = createOperator("$and", (subject, subConditions, { resolveSubcondition }) => {
  if (subject === null || subject === undefined) return false

  if (!Array.isArray(subConditions)) throw new PolicyTypeMismatchError({
    value: {
      expected: "array",
      received: typeof subConditions
    }
  })

  return subConditions.every((condition) => {
    return resolveSubcondition(subject, condition)
  })
})
