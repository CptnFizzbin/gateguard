// Actions
import {setLogger} from "./lib/logger";

export type {Action, InferActions} from "./action";
export {createAction} from "./action";

// Subjects
export type {Subject, InferSubjects} from "./subject";
export {createSubject} from "./subject";

// Conditions
export type {Condition, Operator, OperatorContext} from "./conditions";
export {ConditionResolver, createOperator} from "./conditions";

// Policy
export type {RuleTuple, Meta, Effect, PolicyDefinition} from "./policy";
export {Policy} from "./policy";

// PolicyBuilder
export {PolicyBuilder} from "./builder";

// Errors
export {PolicyError, PolicyLoadException, PolicyVersionException, PolicyArgumentError} from "./errors";

export const GateGuardConfig = {
  setLogger: setLogger
}
