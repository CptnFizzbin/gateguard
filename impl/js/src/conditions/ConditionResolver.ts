import {Condition} from "./Condition";
import {Operator} from "./operators/operator";
import {DefaultOperators} from "./operators/defaultOperators";
import {hasField} from "./operators/field/fieldAccess";
import {JsonValue} from "../lib/json";
import {getLogger} from "../lib/logger";

/**
 * Implements SPEC_V1-0-0.md §7: the condition language and its
 * evaluation semantics. Every operator's own behavior lives in
 * `./operators/**` - this class is just the dispatch loop: it looks a
 * `$`-prefixed key up in its registry (built-ins plus whatever custom
 * `Operator`s the caller registered) and delegates, or narrows into a
 * bare field name.
 */
export class ConditionResolver {
  private operatorRegistry = new Map<string, Operator>

  constructor(operators: Operator[] = []) {
    const allOperators = [...DefaultOperators, ...operators]
    for (const operator of allOperators) {
      this.operatorRegistry.set(operator.name, operator)
    }
  }

  evaluate(subject: any, condition: Condition): boolean {
    if (
      typeof condition === "string" ||
      typeof condition === "number" ||
      typeof condition === "boolean" ||
      condition === null
    ) {
      condition = {$eq: condition}
    }

    if (typeof condition !== "object") {
      return false;
    }

    // §7.5: every key MUST be evaluated and ANDed together - no key may
    // "consume" the whole object or cause sibling keys to be ignored.
    for (const [key, value] of Object.entries(condition)) {
      if (!this.evaluateKey(subject, key, value)) {
        return false;
      }
    }

    return true;
  }

  /**
   * §7.4.12, §7.5: any key starting with "$" is an operator lookup, never
   * a field name - built-in and custom operators are both resolved the
   * same way, by name, against the same registry.
   */
  private evaluateKey(subject: unknown, key: string, value: JsonValue): boolean {
    if (!key.startsWith("$")) {
      return this.fieldCheck(subject, key, value);
    }

    const operator = this.operatorRegistry.get(key)
    if (!operator) {
      // §7.4.12: an operator with no checker registered (built-in or
      // custom) MUST evaluate to false - never a no-op true, and never
      // treated as a field name. Not itself a required §7.1 diagnostic
      // (an unrecognized name is a different mistake than a recognized
      // operator given the wrong operand type - EC-13), but logging
      // regardless makes a typo easier to notice, and covers the
      // declared-but-unregistered case (EC-15) where it IS required.
      getLogger().warn(`Operator '${key}' is not registered`);
      return false;
    }

    return operator.resolve(
      subject,
      value,
      {
        resolveSubcondition: (
          subject: unknown,
          condition: Condition
        ) => this.evaluate(subject, condition)
      }
    )
  }

  /** §7.4.10, §7.3: a missing field (or a non-object subject) makes the whole field-condition false - absence, not a type issue. */
  private fieldCheck(subject: unknown, fieldName: string, condition: Condition): boolean {
    return hasField(subject, fieldName) && this.evaluate(subject[fieldName], condition);
  }
}
