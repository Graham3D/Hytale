package com.inigmasgames.hytalerpg.links;

import java.util.Set;

public record CompatibilityResult(boolean accepted, ValidationCode code, String message,
                                  Set<String> required, Set<String> actual) {
    public CompatibilityResult { required = Set.copyOf(required); actual = Set.copyOf(actual); }
    public static CompatibilityResult accepted(Set<String> actual) {
        return new CompatibilityResult(true, ValidationCode.ACCEPTED, "Compatible", Set.of(), actual);
    }
    public static CompatibilityResult rejected(ValidationCode code, String message, Set<String> required, Set<String> actual) {
        return new CompatibilityResult(false, code, message, required, actual);
    }
}
