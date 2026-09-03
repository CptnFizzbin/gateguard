package com.cptnfizzbin.gateguard.errors;

import lombok.experimental.StandardException;

/**
 * Thrown by {@code Policy.from(...)} at construction time when a
 * PolicyDefinition's {@code version} is incompatible with what this
 * implementation supports - a different MAJOR, or a MINOR higher than
 * what's understood within a supported MAJOR (SPEC_V1-0-0.md §2, EC-11).
 * {@code PATCH} never affects this decision.
 */
@StandardException
public class PolicyVersionException extends RuntimeException {
}
