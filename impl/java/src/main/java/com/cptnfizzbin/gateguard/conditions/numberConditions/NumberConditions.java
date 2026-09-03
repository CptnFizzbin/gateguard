package com.cptnfizzbin.gateguard.conditions.numberConditions;

public final class NumberConditions {
    private NumberConditions() {}

    public static boolean gt(Number subject, Number expected) {
        if (subject == null || expected == null) return false;
        return subject.doubleValue() > expected.doubleValue();
    }

    public static boolean gte(Number subject, Number expected) {
        if (subject == null || expected == null) return false;
        return subject.doubleValue() >= expected.doubleValue();
    }

    public static boolean lt(Number subject, Number expected) {
        if (subject == null || expected == null) return false;
        return subject.doubleValue() < expected.doubleValue();
    }

    public static boolean lte(Number subject, Number expected) {
        if (subject == null || expected == null) return false;
        return subject.doubleValue() <= expected.doubleValue();
    }
}
