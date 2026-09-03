package com.cptnfizzbin.gateguard.conditions.stringConditions;

import java.util.regex.Pattern;

public final class StringConditions {
    private StringConditions() {}

    public static boolean eq(Object subject, Object expected) {
        if (subject == null || expected == null) {
            return subject == expected;
        }
        return subject.equals(expected);
    }

    public static boolean ne(Object subject, Object expected) {
        return !eq(subject, expected);
    }

    public static boolean rgx(Object subject, Object pattern) {
        if (subject == null || pattern == null) return false;
        String subjectStr = subject.toString();
        String patternStr = pattern.toString();
        try {
            return Pattern.matches(patternStr, subjectStr);
        } catch (Exception e) {
            return false;
        }
    }
}
