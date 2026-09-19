package com.inigmasgames.persistentnpcs.hytale;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.inigmasgames.persistentnpcs.conversation.ContentCatalog;
import com.inigmasgames.persistentnpcs.conversation.ContentValidationResult;
import com.inigmasgames.persistentnpcs.conversation.ContentValidationStatus;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bounded lookup against the loaded authoritative Hytale item asset map. */
public final class HytaleItemContentCatalog implements ContentCatalog {
    @Override
    public ContentValidationResult validate(
            String requestedThing, NpcPerceptionSnapshot perception) {
        String query = normalize(requestedThing);
        if (query.isBlank()) {
            return ContentValidationResult.unknown(requestedThing,
                    "no concrete item/category supplied");
        }
        if (perception != null && perception.focusedPlayerHeldItem() != null
                && matches(perception.focusedPlayerHeldItem().itemId(), query)) {
            return new ContentValidationResult(requestedThing, ContentValidationStatus.FOUND,
                    List.of(perception.focusedPlayerHeldItem().itemId()),
                    "matching item is present in the focused player's held ItemStack");
        }
        try {
            var assets = Item.getAssetMap().getAssetMap();
            if (assets.isEmpty()) {
                return ContentValidationResult.unknown(requestedThing,
                        "loaded Hytale item registry is empty");
            }
            ArrayList<String> matches = new ArrayList<>();
            for (Item item : assets.values()) {
                if (item != null && matches(item, query)) {
                    matches.add(item.getId());
                    if (matches.size() >= 5) {
                        break;
                    }
                }
            }
            if (!matches.isEmpty()) {
                return new ContentValidationResult(requestedThing,
                        ContentValidationStatus.FOUND, matches,
                        "matching content exists in the loaded Hytale item registry");
            }
            return new ContentValidationResult(requestedThing,
                    ContentValidationStatus.NOT_FOUND, List.of(),
                    "no matching item/category exists in the loaded Hytale item registry");
        } catch (RuntimeException | LinkageError failure) {
            return ContentValidationResult.unknown(requestedThing,
                    "item registry lookup failed: " + failure.getClass().getSimpleName());
        }
    }

    private static boolean matches(Item item, String query) {
        if (matches(item.getId(), query) || matches(item.getTranslationKey(), query)
                || matches(item.getSubCategory(), query)) {
            return true;
        }
        String[] categories = item.getCategories();
        return categories != null && java.util.Arrays.stream(categories)
                .anyMatch(category -> matches(category, query));
    }

    private static boolean matches(String candidate, String query) {
        String normalized = normalize(candidate);
        if (normalized.isBlank()) {
            return false;
        }
        if (normalized.equals(query) || normalized.contains(" " + query + " ")
                || normalized.startsWith(query + " ") || normalized.endsWith(" " + query)) {
            return true;
        }
        if (query.equals("drink")) {
            return normalized.contains("beverage") || normalized.contains(" drink");
        }
        return normalized.contains(query);
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ").replaceAll("\\s+", " ").strip();
        if (normalized.endsWith("s") && normalized.length() > 3) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
