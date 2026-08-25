package com.cptnfizzbin.keycard.conditions.groupConditions;

import java.util.Collection;

public final class GroupConditions {
    private GroupConditions() {}

    public static boolean in(Object subject, Object array) {
        if (!(array instanceof Collection)) {
            return false;
        }
        return ((Collection<?>) array).contains(subject);
    }

    public static boolean has(Object subject, Object value) {
        if (!(subject instanceof Collection)) {
            return false;
        }
        return ((Collection<?>) subject).contains(value);
    }
}
