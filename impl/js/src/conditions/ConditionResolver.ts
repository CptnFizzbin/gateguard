import { Condition, CustomConditionChecker } from "./Condition";

export class ConditionResolver {
  private customCheckers: CustomConditionChecker = {};

  constructor(customCheckers?: CustomConditionChecker) {
    if (customCheckers) {
      this.customCheckers = customCheckers;
    }
  }

  evaluate(subject: any, condition: Condition): boolean {
    if (typeof condition === "string" || typeof condition === "number" || typeof condition === "boolean" || condition === null) {
      return subject === condition;
    }

    if (!condition || typeof condition !== "object") {
      return false;
    }

    const obj = condition as Record<string, any>;

    if ("$eq" in obj) {
      return subject === obj.$eq;
    }

    if ("$ne" in obj) {
      return subject !== obj.$ne;
    }

    if ("$gt" in obj) {
      return subject > obj.$gt;
    }

    if ("$gte" in obj) {
      return subject >= obj.$gte;
    }

    if ("$lt" in obj) {
      return subject < obj.$lt;
    }

    if ("$lte" in obj) {
      return subject <= obj.$lte;
    }

    if ("$in" in obj && Array.isArray(obj.$in)) {
      return obj.$in.includes(subject);
    }

    if ("$has" in obj) {
      if (Array.isArray(subject)) {
        return subject.includes(obj.$has);
      }
      return false;
    }

    if ("$rgx" in obj) {
      const regex = typeof obj.$rgx === "string" ? new RegExp(obj.$rgx) : obj.$rgx;
      return regex.test(String(subject));
    }

    if ("$or" in obj && Array.isArray(obj.$or)) {
      return obj.$or.some((cond: Condition) => this.evaluate(subject, cond));
    }

    if ("$and" in obj && Array.isArray(obj.$and)) {
      return obj.$and.every((cond: Condition) => this.evaluate(subject, cond));
    }

    if ("$not" in obj) {
      return !this.evaluate(subject, obj.$not);
    }

    // Handle field conditions
    for (const [key, value] of Object.entries(obj)) {
      if (key.startsWith("$")) {
        if (this.customCheckers[key]) {
          if (!this.customCheckers[key](subject, value)) {
            return false;
          }
        } else {
          return false;
        }
      } else if (subject && typeof subject === "object" && key in subject) {
        if (!this.evaluate(subject[key], value)) {
          return false;
        }
      } else {
        return false;
      }
    }

    return true;
  }
}
