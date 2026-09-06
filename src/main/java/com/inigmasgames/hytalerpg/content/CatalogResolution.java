package com.inigmasgames.hytalerpg.content;

import java.util.List;
import java.util.Optional;

public record CatalogResolution<T>(Status status, T value, List<String> candidates, String message) {
    public enum Status { RESOLVED, NOT_FOUND, AMBIGUOUS }
    public static <T> CatalogResolution<T> resolved(T value) {
        return new CatalogResolution<>(Status.RESOLVED, value, List.of(), "");
    }
    public static <T> CatalogResolution<T> notFound(String message) {
        return new CatalogResolution<>(Status.NOT_FOUND, null, List.of(), message);
    }
    public static <T> CatalogResolution<T> ambiguous(List<String> candidates) {
        return new CatalogResolution<>(Status.AMBIGUOUS, null, List.copyOf(candidates),
                "Ambiguous content name; candidates: " + String.join(", ", candidates));
    }
    public Optional<T> resolvedValue() { return Optional.ofNullable(value); }
}
