package com.cptnfizzbin.keycard.subject;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class SubjectRef<T> implements Subject<T> {
    private final String name;
    private final T value;
}
