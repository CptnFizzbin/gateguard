import {PolicyError} from "./PolicyError";

export interface TypeMismatchInfo {
  expected: string
  received: string
}

export class PolicyTypeMismatchError extends PolicyError {
  constructor(
    options:
      | { subject: TypeMismatchInfo, value?: never }
      | { subject?: never, value?: TypeMismatchInfo }
  ) {
    if (options.subject) {
      const {expected, received} = options.subject
      super(`Expected subject to be of type '${expected}', received '${received}' instead`)
    }

    if (options.value) {
      const {expected, received} = options.value
      super(`Expected value to be of type '${expected}', received '${received}' instead`)
    }
  }
}
