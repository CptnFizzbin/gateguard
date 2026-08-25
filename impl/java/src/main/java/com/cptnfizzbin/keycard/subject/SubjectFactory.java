package com.cptnfizzbin.keycard.subject;

public final class SubjectFactory {
    private SubjectFactory() {}

    public static <T> SubjectDef<T> create(String name, Class<T> type) {
        return SubjectDef.create(name, type);
    }
}
