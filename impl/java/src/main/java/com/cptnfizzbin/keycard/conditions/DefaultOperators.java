package com.cptnfizzbin.keycard.conditions;

/**
 * Every operator {@link ConditionResolver} understands natively
 * (SPEC_V1-0-0.md §7.4.1-§7.4.11), as named, reusable {@link Operator}
 * constants - assembled into a registry via {@link OperatorMap#of}.
 */
final class DefaultOperators {
    private DefaultOperators() {}

    static final Operator EQ = new Operator("$eq", (r, s, v) -> StringConditions.eq(s, v));
    static final Operator NE = new Operator("$ne", (r, s, v) -> StringConditions.ne(s, v));
    static final Operator GT = new Operator("$gt", (r, s, v) -> r.numericCompare("$gt", s, v, (a, b) -> a > b));
    static final Operator GTE = new Operator("$gte", (r, s, v) -> r.numericCompare("$gte", s, v, (a, b) -> a >= b));
    static final Operator LT = new Operator("$lt", (r, s, v) -> r.numericCompare("$lt", s, v, (a, b) -> a < b));
    static final Operator LTE = new Operator("$lte", (r, s, v) -> r.numericCompare("$lte", s, v, (a, b) -> a <= b));
    static final Operator IN = new Operator("$in", (r, s, v) -> r.inCheck(s, v));
    static final Operator HAS = new Operator("$has", (r, s, v) -> r.hasCheck(s, v));
    static final Operator SUBSTR = new Operator("$substr", (r, s, v) -> r.substrCheck(s, v));
    static final Operator OR = new Operator("$or", (r, s, v) -> r.orCheck(s, v));
    static final Operator AND = new Operator("$and", (r, s, v) -> r.andCheck(s, v));
    static final Operator NOT = new Operator("$not", (r, s, v) -> !r.evaluate(s, v));
    static final Operator FIELD = new Operator("$field", (r, s, v) -> r.fieldOpCheck(s, v));
}
