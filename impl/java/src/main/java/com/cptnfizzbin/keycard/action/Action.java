package com.cptnfizzbin.keycard.action;

public final class Action<T extends String> {
    private final T name;

    private Action(T name) {
        this.name = name;
    }

    public static <T extends String> Action<T> create(T name) {
        return new Action<>(name);
    }

    public T getName() {
        return name;
    }

    public String getNameStr() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Action)) return false;
        Action<?> action = (Action<?>) o;
        return name.equals(action.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
