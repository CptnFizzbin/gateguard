import { Condition, CustomConditionChecker } from "./Condition";

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
  console.error(`${DIAGNOSTIC_PREFIX} ${operator}: ${message}`);
}

/** §7.4.1: value equality for primitives - not reference/identity equality, and (per `===`) never treats NaN as equal to itself. */
function valuesEqual(a: unknown, b: unknown): boolean {
  return a === b;
}

interface SubstrPattern {
  anchorStart: boolean;
  anchorEnd: boolean;
  /** Literal segments, in order; consecutive segments are separated by a required run of one-or-more characters (a `*`/`**` in the source pattern). */
  segments: string[];
}

/**
 * §7.4.6: parses a `$substr` pattern into anchors plus the literal
 * segments between its wildcards. Returns `null` when the pattern is
 * structurally invalid (an unescaped `^` anywhere but the first
 * character, or an unescaped `$` anywhere but the last).
 */
function parseSubstrPattern(raw: string): SubstrPattern | null {
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
  return { anchorStart, anchorEnd, segments };
}

/** §7.4.6: matches `subject` against an already-parsed `$substr` pattern. */
function matchesSubstrPattern(subject: string, pattern: SubstrPattern): boolean {
  const n = subject.length;
  const lastIdx = pattern.segments.length - 1;
  let cursor = 0;

  for (let idx = 0; idx <= lastIdx; idx++) {
    const seg = pattern.segments[idx];
    const isFirst = idx === 0;
    const isLast = idx === lastIdx;
    const minStart = isFirst ? 0 : cursor + 1; // a wildcard requires 1+ characters between segments
    const anchoredStart = isFirst && pattern.anchorStart;
    const anchoredEnd = isLast && pattern.anchorEnd;

    if (anchoredStart && anchoredEnd) {
      if (subject.length !== seg.length || subject !== seg) return false;
      cursor = n;
      continue;
    }

    if (anchoredStart) {
      if (!subject.startsWith(seg)) return false;
      cursor = seg.length;
      continue;
    }

    if (anchoredEnd) {
      const start = n - seg.length;
      if (start < minStart || subject.slice(start) !== seg) return false;
      cursor = n;
      continue;
    }

    const found = subject.indexOf(seg, minStart);
    if (found === -1) return false;
    cursor = found + seg.length;
  }

  return true;
}

/** Every `$`-prefixed key this resolver understands natively - anything else starting with `$` is a custom operator lookup (§7.4.12). */
const BUILTIN_OPERATORS = new Set([
  "$eq", "$ne", "$gt", "$gte", "$lt", "$lte",
  "$in", "$has", "$substr", "$or", "$and", "$not", "$field",
]);

export { BUILTIN_OPERATORS };

export class ConditionResolver {
  private customCheckers: CustomConditionChecker;
  private declaredCustomOperators: Set<string>;

  /**
   * @param customCheckers runtime checkers for custom `$op` operators (§7.4.12).
   * @param declaredCustomOperators the policy's `meta.customOperators` catalog, if any -
   *   used only to distinguish EC-13 (uncataloged, no diagnostic) from EC-15
   *   (cataloged but never registered, diagnostic required).
   */
  constructor(customCheckers?: CustomConditionChecker, declaredCustomOperators?: Iterable<string>) {
    this.customCheckers = customCheckers ?? {};
    this.declaredCustomOperators = new Set(declaredCustomOperators ?? []);
  }

  evaluate(subject: any, condition: Condition): boolean {
    if (
      typeof condition === "string" ||
      typeof condition === "number" ||
      typeof condition === "boolean" ||
      condition === null
    ) {
      // §7.2: bare-value shorthand for $eq.
      return valuesEqual(subject, condition);
    }

    if (typeof condition !== "object") {
      return false;
    }

    // §7.5: every key MUST be evaluated and ANDed together - no key may
    // "consume" the whole object or cause sibling keys to be ignored.
    for (const [key, value] of Object.entries(condition as Record<string, any>)) {
      if (!this.evaluateKey(subject, key, value)) {
        return false;
      }
    }

    return true;
  }

  private evaluateKey(subject: any, key: string, value: any): boolean {
    switch (key) {
      case "$eq":
        return valuesEqual(subject, value);
      case "$ne":
        return !valuesEqual(subject, value);
      case "$gt":
        return this.numericCompare("$gt", subject, value, (a, b) => a > b);
      case "$gte":
        return this.numericCompare("$gte", subject, value, (a, b) => a >= b);
      case "$lt":
        return this.numericCompare("$lt", subject, value, (a, b) => a < b);
      case "$lte":
        return this.numericCompare("$lte", subject, value, (a, b) => a <= b);
      case "$in":
        return this.inCheck(subject, value);
      case "$has":
        return this.hasCheck(subject, value);
      case "$substr":
        return this.substrCheck(subject, value);
      case "$or":
        return this.orCheck(subject, value);
      case "$and":
        return this.andCheck(subject, value);
      case "$not":
        return !this.evaluate(subject, value);
      case "$field":
        return this.fieldOpCheck(subject, value);
      default:
        // §7.5: any key starting with "$" MUST be treated as an operator,
        // never a field name, whether or not it's recognized.
        if (key.startsWith("$")) {
          return this.customOperatorCheck(subject, key, value);
        }
        return this.fieldCheck(subject, key, value);
    }
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

  /** §7.4.6: $substr - a null/undefined subject is an ordinary non-match, not a type issue; an invalid pattern always is. */
  private substrCheck(subject: any, pattern: any): boolean {
    if (typeof pattern !== "string") {
      logTypeIssue("$substr", `expected a string pattern, got ${typeof pattern}`);
      return false;
    }
    const parsed = parseSubstrPattern(pattern);
    if (!parsed) {
      logTypeIssue("$substr", `malformed pattern: ${JSON.stringify(pattern)}`);
      return false;
    }
    if (subject === null || subject === undefined) {
      return false;
    }
    return matchesSubstrPattern(String(subject), parsed);
  }

  /** §7.4.7: $or - operand must be an array; `$or: []` is vacuously false. */
  private orCheck(subject: any, operand: any): boolean {
    if (!Array.isArray(operand)) {
      logTypeIssue("$or", `expected an array operand, got ${typeof operand}`);
      return false;
    }
    if (operand.length === 0) {
      logTypeIssue("$or", "empty $or is vacuously false - likely an authoring mistake");
      return false;
    }
    return operand.some((cond: Condition) => this.evaluate(subject, cond));
  }

  /** §7.4.8: $and - operand must be an array; `$and: []` is vacuously true. */
  private andCheck(subject: any, operand: any): boolean {
    if (!Array.isArray(operand)) {
      logTypeIssue("$and", `expected an array operand, got ${typeof operand}`);
      return false;
    }
    if (operand.length === 0) {
      logTypeIssue("$and", "empty $and is vacuously true - likely an authoring mistake");
      return true;
    }
    return operand.every((cond: Condition) => this.evaluate(subject, cond));
  }

  /** §7.4.11: $field - explicit field access, for a field whose name itself starts with "$". */
  private fieldOpCheck(subject: any, operand: any): boolean {
    if (!Array.isArray(operand) || operand.length !== 2 || typeof operand[0] !== "string") {
      logTypeIssue("$field", `expected a [name, Condition] tuple, got ${JSON.stringify(operand)}`);
      return false;
    }
    const [name, condition] = operand;
    return this.fieldCheck(subject, name, condition);
  }

  /** §7.4.10, §7.3: a missing field (or a non-object subject) makes the whole field-condition false - absence, not a type issue. */
  private fieldCheck(subject: any, fieldName: string, condition: Condition): boolean {
    if (subject === null || subject === undefined || typeof subject !== "object" || !(fieldName in subject)) {
      return false;
    }
    return this.evaluate(subject[fieldName], condition);
  }

  /** §7.4.12: a custom `$op` - delegates to a registered checker, or evaluates false (EC-13/EC-15). */
  private customOperatorCheck(subject: any, op: string, value: any): boolean {
    const checker = this.customCheckers[op];
    if (checker) {
      return checker(subject, value);
    }
    if (this.declaredCustomOperators.has(op)) {
      // EC-15: cataloged in meta.customOperators, but nothing was ever
      // registered for it at runtime - a worth-surfacing configuration bug,
      // unlike the general "uncataloged" case below.
      logTypeIssue(op, "declared in meta.customOperators but no checker is registered for it");
    }
    // EC-13: an unregistered, uncataloged operator is ordinary unmatched
    // vocabulary (a possible typo), not a type issue - no diagnostic.
    return false;
  }
}
