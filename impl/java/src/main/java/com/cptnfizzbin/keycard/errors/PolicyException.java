package com.cptnfizzbin.keycard.errors;

public class PolicyException extends RuntimeException {
    public PolicyException(String message) {
        super(message);
    }

    public PolicyException(String message, Throwable cause) {
        super(message, cause);
    }
}
