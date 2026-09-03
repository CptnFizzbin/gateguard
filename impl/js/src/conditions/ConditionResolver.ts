import {Condition} from "./Condition";
import {Operator} from "./operators/operator";
import {DefaultOperators} from "./operators/defaultOperators";
import {PolicyError} from "../errors";
import {JsonValue} from "../lib/json";
import {getLogger} from "../lib/logger";

const logger = getLogger()

/**
 * Implements SPEC_V1-0-0.md §7: the condition language and its
 * evaluation semantics.
 */

const DIAGNOSTIC_PREFIX = "[KeyCard]";

/**
 * §7.1: "Type issues are diagnosed, not silenced." Writes a human-readable,
 * error-level diagnostic identifying the operator and what went wrong -
 * used only for genuine type issues, never for an ordinary non-match
 * (a missing field, an unmatched action/subject, an uncataloged custom
 * operator).
 */
function logTypeIssue(operator: string, message: string): void {
  // eslint-disable-next-line no-console
  logger.error(`${DIAGNOSTIC_PREFIX} ${operator}: ${message}`);
}

/** §7.4.1: value equality for primitives - not reference/identity equality, and (per `===`) never treats NaN as equal to itself. */
function valuesEqual(a: unknown, b: unknown): boolean {
  return a === b;
}

function escapeRegExp(literal: string): string {
  return literal.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/**
 * §7.4.6: parses a `$substr` pattern by compiling it to a `RegExp` - the
 * spec explicitly permits this ("Implementations MAY implement $substr
 * however they like internally (including compiling it to the host
 * language's native regex engine, e.g. translating `*`/`**` to `.+` and
 * escaping literal segments)"). Literal segments are escaped verbatim, a
 * wildcard becomes `.+` (one-or-more; the `s` flag makes `.` match
 * newlines too, so it's truly "any character"), and the leading/trailing
 * anchors carry straight through as regex anchors. Returns `null` when
 * the pattern is structurally invalid (an unescaped `^` anywhere but the
 * first character, or an unescaped `$` anywhere but the last).
 */
function parseSubstrPattern(raw: string): RegExp | null {
  const n = raw.length;
  let i = 0;
  let anchorStart = false;

  if (raw[0] === "^") {
    anchorStart = true;
    i = 1;
  }

  const segments: string[] = [];
  let current = "";
  let anchorEnd = false;

  while (i < n) {
    const ch = raw[i];

    if (ch === "\\") {
      const next = raw[i + 1];
      if (next === "^" || next === "$" || next === "*" || next === "\\") {
        current += next;
        i += 2;
      } else {
        // A trailing "\" with nothing following, or "\" before a
        // non-special character, is a literal "\".
        current += "\\";
        i += 1;
      }
      continue;
    }

    if (ch === "*") {
      let j = i + 1;
      if (raw[j] === "*") j += 1; // "*" and "**" are match-equivalent.
      segments.push(current);
      current = "";
      i = j;
      continue;
    }

    if (ch === "^") {
      return null; // unescaped "^" not at the start
    }

    if (ch === "$") {
      if (i === n - 1) {
        anchorEnd = true;
        i += 1;
        continue;
      }
      return null; // unescaped "$" not at the end
    }

    current += ch;
    i += 1;
  }

  segments.push(current);

  let source = anchorStart ? "^" : "";
  segments.forEach((seg, idx) => {
    if (idx > 0) source += ".+";
    source += escapeRegExp(seg);
  });
  if (anchorEnd) source += "$";

  return new RegExp(source, "s");
}

/** A built-in operator's implementation - takes the resolving instance (for recursive `evaluate()` calls and diagnostics) plus the subject/operand. */
type BuiltinOperator = (resolver: ConditionResolver, subject: any, value: any) => boolean;

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
   * same way, by name, against their respective registries.
   */
  private evaluateKey(subject: unknown, key: string, value: JsonValue): boolean {
    if (!key.startsWith("$")) {
      return this.fieldCheck(subject, key, value);
    }

    const operator = this.operatorRegistry.get(key)
    if (!operator) throw new PolicyError(`Operator '${key}' not registered`)
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

  /** §7.4.3: $gt/$gte/$lt/$lte - numeric-only, IEEE-754 double semantics. */
  private numericCompare(
    op: string,
    subject: any,
    operand: any,
    cmp: (a: number, b: number) => boolean
  ): boolean {
    if (typeof subject !== "number" || typeof operand !== "number") {
      logTypeIssue(op, `expected the subject and operand to both be numbers, got ${typeof subject} and ${typeof operand}`);
      return false;
    }
    return cmp(subject, operand);
  }

  /** §7.4.4: $in - operand must be an array; containment uses $eq semantics per element. */
  private inCheck(subject: any, operand: any): boolean {
    if (!Array.isArray(operand)) {
      logTypeIssue("$in", `expected an array operand, got ${typeof operand}`);
      return false;
    }
    return operand.some((v) => valuesEqual(v, subject));
  }

  /** §7.4.5: $has - subject must be an array. */
  private hasCheck(subject: any, value: any): boolean {
    if (!Array.isArray(subject)) {
      logTypeIssue("$has", `expected an array subject, got ${typeof subject}`);
      return false;
    }
    return subject.some((v) => valuesEqual(v, value));
  }

  /** §7.4.10, §7.3: a missing field (or a non-object subject) makes the whole field-condition false - absence, not a type issue. */
  private fieldCheck(subject: unknown, fieldName: string, condition: Condition): boolean {
    return (
      subject !== null
      && typeof subject === "object"
      && fieldName in subject
      && this.evaluate((subject as { [fieldName]: unknown })[fieldName], condition)
    )
  }
}
