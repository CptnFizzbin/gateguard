package com.cptnfizzbin.keycard.subject;

public final class SubjectDef<T> implements Subject<T> {
    private final String name;
    private final Class<T> type;

    private SubjectDef(String name, Class<T> type) {
        this.name = name;
        this.type = type;
    }

    public static <T> SubjectDef<T> create(String name, Class<T> type) {
        return new SubjectDef<>(name, type);
    }

    @Override
    public String getName() {
        return name;
    }

    public Class<T> getType() {
        return type;
    }

    public SubjectRef<T> wrap(T obj) {
        return new SubjectRef<>(name, obj);
    }
}
