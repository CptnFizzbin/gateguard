package com.cptnfizzbin.keycard.action;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Action<T extends String> {
    private final T name;

    public static <T extends String> Action<T> create(T name) {
        return new Action<>(name);
    }

    public String getNameStr() {
        return name;
    }
}
