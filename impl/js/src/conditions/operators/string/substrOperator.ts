import {createOperator} from "../operator";
import {PolicyTypeMismatchError} from "../../../errors/PolicyTypeMismatchError";

export const SubstrOperator = createOperator("$substr", (subject, pattern) => {
  if (subject === null || subject === undefined) return false

  const subjectStr = String(subject)

  if (typeof pattern !== "string") throw new PolicyTypeMismatchError({
    value: {
      expected: "string",
      received: typeof pattern
    }
  })

  let regexPattern = ""
  for (let i: number = 0; i < pattern.length; i++) {
    const char = pattern[i]
    const next = pattern[i + 1]

    switch (char) {
      case "\\":
        if (!next) break

        regexPattern += RegExp.escape(next)
        i++ // skip next

        break
      case "*":
        regexPattern += ".*"
        break
      case "$":
      case "^":
        regexPattern += char
        break
      default:
        regexPattern += RegExp.escape(char)
    }
  }

  return !!(new RegExp(regexPattern).exec(subjectStr))
})
