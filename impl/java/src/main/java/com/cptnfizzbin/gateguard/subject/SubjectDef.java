package com.cptnfizzbin.gateguard.subject;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class SubjectDef<T> implements Subject<T> {
    private final String name;
    private final Class<T> type;

    public static <T> SubjectDef<T> create(String name, Class<T> type) {
        return new SubjectDef<>(name, type);
    }

    public SubjectRef<T> wrap(T obj) {
        return new SubjectRef<>(name, obj);
    }
}
