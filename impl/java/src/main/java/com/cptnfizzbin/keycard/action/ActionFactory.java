package com.cptnfizzbin.keycard.action;

public final class ActionFactory {
    private ActionFactory() {}

    public static <T extends String> Action<T> create(T name) {
        return Action.create(name);
    }
}
