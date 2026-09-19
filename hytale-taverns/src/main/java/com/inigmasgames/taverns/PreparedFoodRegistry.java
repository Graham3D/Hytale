package com.inigmasgames.taverns;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Authoritative data-driven mapping between vanilla foods and Tavern serving items. */
final class PreparedFoodRegistry {
    private static final String RESOURCE = "/prepared_foods.json";

    private final List<Definition> definitions;
    private final Map<String, Definition> byBaseFoodId;
    private final Map<String, Definition> byPreparedFoodId;

    private PreparedFoodRegistry(List<Definition> definitions) {
        LinkedHashMap<String, Definition> bases = new LinkedHashMap<>();
        LinkedHashMap<String, Definition> prepared = new LinkedHashMap<>();
        for (Definition definition : definitions) {
            Objects.requireNonNull(definition.baseFoodId(), "baseFoodId");
            Objects.requireNonNull(definition.preparedFoodId(), "preparedFoodId");
            if (definition.baseFoodId().isBlank() || definition.preparedFoodId().isBlank()) {
                throw new IllegalArgumentException("Prepared food IDs cannot be blank");
            }
            if (bases.putIfAbsent(definition.baseFoodId(), definition) != null) {
                throw new IllegalArgumentException("Duplicate base food " + definition.baseFoodId());
            }
            if (prepared.putIfAbsent(definition.preparedFoodId(), definition) != null) {
                throw new IllegalArgumentException("Duplicate prepared food " + definition.preparedFoodId());
            }
        }
        this.definitions = List.copyOf(definitions);
        this.byBaseFoodId = Map.copyOf(bases);
        this.byPreparedFoodId = Map.copyOf(prepared);
    }

    static PreparedFoodRegistry loadDefault() {
        try (InputStream stream = PreparedFoodRegistry.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing " + RESOURCE);
            }
            RegistryFile file = new Gson().fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8), RegistryFile.class);
            if (file == null || file.foods == null || file.foods.isEmpty()) {
                throw new IllegalStateException("Prepared food registry is empty");
            }
            return new PreparedFoodRegistry(file.foods);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not load " + RESOURCE, exception);
        }
    }

    List<Definition> definitions() {
        return definitions;
    }

    Optional<Definition> byBaseFoodId(String itemId) {
        return Optional.ofNullable(byBaseFoodId.get(itemId));
    }

    Optional<Definition> byPreparedFoodId(String itemId) {
        return Optional.ofNullable(byPreparedFoodId.get(itemId));
    }

    record Definition(String baseFoodId, String preparedFoodId) { }

    private static final class RegistryFile {
        private List<Definition> foods;
    }
}
