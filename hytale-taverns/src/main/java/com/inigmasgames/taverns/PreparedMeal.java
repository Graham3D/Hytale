package com.inigmasgames.taverns;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.Optional;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonString;
import org.bson.BsonValue;

/** Server-authoritative freshness metadata carried by a prepared serving ItemStack. */
final class PreparedMeal {
    static final long FRESHNESS_MILLIS = 60_000L;
    static final String BASE_FOOD_ID_KEY = "taverns.baseFoodId";
    static final String PREPARED_AT_KEY = "taverns.preparedAt";
    static final String EXPIRES_AT_KEY = "taverns.expiresAt";

    private PreparedMeal() {
    }

    static ItemStack stamp(
            ItemStack output,
            PreparedFoodRegistry.Definition definition,
            long preparedAt) {
        BsonDocument metadata = output.getMetadata() == null
                ? new BsonDocument()
                : output.getMetadata().clone();
        stampMetadata(metadata, definition, preparedAt);
        return new ItemStack(output.getItemId(), 1, metadata);
    }

    static Optional<Details> inspect(ItemStack stack, PreparedFoodRegistry registry) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return inspect(stack.getItemId(), stack.getMetadata(), registry);
    }

    static Optional<Details> inspect(
            String itemId,
            BsonDocument metadata,
            PreparedFoodRegistry registry) {
        Optional<PreparedFoodRegistry.Definition> found =
                registry.byPreparedFoodId(itemId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        String baseFoodId = stringValue(metadata, BASE_FOOD_ID_KEY);
        Long preparedAt = longValue(metadata, PREPARED_AT_KEY);
        Long expiresAt = longValue(metadata, EXPIRES_AT_KEY);
        boolean metadataValid = found.get().baseFoodId().equals(baseFoodId)
                && preparedAt != null
                && expiresAt != null
                && expiresAt >= preparedAt;
        return Optional.of(new Details(
                found.get().baseFoodId(),
                found.get().preparedFoodId(),
                preparedAt == null ? 0L : preparedAt,
                expiresAt == null ? 0L : expiresAt,
                metadataValid));
    }

    static BsonDocument createMetadata(
            PreparedFoodRegistry.Definition definition,
            long preparedAt) {
        BsonDocument metadata = new BsonDocument();
        stampMetadata(metadata, definition, preparedAt);
        return metadata;
    }

    private static void stampMetadata(
            BsonDocument metadata,
            PreparedFoodRegistry.Definition definition,
            long preparedAt) {
        metadata.put(BASE_FOOD_ID_KEY, new BsonString(definition.baseFoodId()));
        metadata.put(PREPARED_AT_KEY, new BsonInt64(preparedAt));
        metadata.put(EXPIRES_AT_KEY, new BsonInt64(preparedAt + FRESHNESS_MILLIS));
    }

    static boolean matchesBase(
            ItemStack stack,
            String expectedBaseFoodId,
            PreparedFoodRegistry registry) {
        return inspect(stack, registry)
                .filter(Details::metadataValid)
                .map(details -> details.baseFoodId().equals(expectedBaseFoodId))
                .orElse(false);
    }

    private static String stringValue(BsonDocument metadata, String key) {
        if (metadata == null) {
            return null;
        }
        BsonValue value = metadata.get(key);
        return value != null && value.isString() ? value.asString().getValue() : null;
    }

    private static Long longValue(BsonDocument metadata, String key) {
        if (metadata == null) {
            return null;
        }
        BsonValue value = metadata.get(key);
        if (value == null) {
            return null;
        }
        if (value.isInt64()) {
            return value.asInt64().getValue();
        }
        return value.isInt32() ? (long) value.asInt32().getValue() : null;
    }

    record Details(
            String baseFoodId,
            String preparedFoodId,
            long preparedAt,
            long expiresAt,
            boolean metadataValid) {
        boolean isFresh(long now) {
            return metadataValid && now >= preparedAt && now < expiresAt;
        }
    }
}
