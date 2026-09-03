package com.cptnfizzbin.gateguard.subject;

public final class SubjectFactory {
    private SubjectFactory() {}

    public static <T> Subject<T> create(String name) {
        return Subject.create(name);
    }
}
