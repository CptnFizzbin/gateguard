package com.cptnfizzbin.keycard.subject;

public final class SubjectRef<T> implements Subject<T> {
    private final String name;
    private final T value;

    public SubjectRef(String name, T value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public String getName() {
        return name;
    }

    public T getValue() {
        return value;
    }
}
