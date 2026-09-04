import {JsonValue} from "../../lib/json";
import {PolicyTypeMismatchError} from "../../errors/PolicyTypeMismatchError";
import {getLogger} from "../../lib/logger";

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
          // §7.1: "type issues are diagnosed, not silenced" - call
          // getLogger() fresh rather than caching it at module load, so
          // a consumer's setLogger() (almost always called after this
          // module has already been imported) still takes effect.
          getLogger().warn(e.message)
          return false
        }

        throw e
      }
    }
  }
}
