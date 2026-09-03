import {JsonValue} from "../../lib/json";
import {PolicyTypeMismatchError} from "../../errors/PolicyTypeMismatchError";
import {getLogger} from "../../lib/logger";

const logger = getLogger()

export interface OperatorContext {
  resolveSubcondition: (subject: unknown, condition: JsonValue) => boolean
}

export interface Operator {
  name: `$${string}`
  resolve: (subject: unknown, value: JsonValue, ctx: OperatorContext) => boolean
}


export function createOperator(
  name: Operator['name'],
  resolver: Operator['resolve']
): Operator {
  return {
    name,
    resolve: (subject, value, ctx) => {
      try {
        return resolver(subject, value, ctx)
      } catch (e) {
        if (e instanceof PolicyTypeMismatchError) {
          logger.warn(`[Keycard] ${e.message}`)
          return false
        }

        throw e
      }
    }
  }
}
