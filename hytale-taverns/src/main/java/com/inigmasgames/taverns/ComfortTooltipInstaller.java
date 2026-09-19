package com.inigmasgames.taverns;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTranslationProperties;
import com.hypixel.hytale.server.core.Message;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Adds registry-backed Comfort values to Hytale's native item hover descriptions. */
final class ComfortTooltipInstaller {
    private final Field itemTranslations;
    private final Field nameArguments;
    private final Field descriptionArguments;
    private final Map<Item, ItemTranslationProperties> originals = new IdentityHashMap<>();

    ComfortTooltipInstaller() {
        try {
            itemTranslations = Item.class.getDeclaredField("translationProperties");
            nameArguments = ItemTranslationProperties.class.getDeclaredField("nameArguments");
            descriptionArguments = ItemTranslationProperties.class.getDeclaredField(
                    "descriptionArguments");
            itemTranslations.setAccessible(true);
            nameArguments.setAccessible(true);
            descriptionArguments.setAccessible(true);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Could not access native item translation properties", exception);
        }
    }

    synchronized int install(ComfortRegistry registry) {
        try {
            restoreOriginals();

            int installed = 0;
            for (ComfortDefinition definition : registry.definitions()) {
                if (definition.comfort() <= 0) {
                    continue;
                }
                Item item = Item.getAssetMap().getAsset(definition.assetId());
                if (item == null) {
                    continue;
                }
                ItemTranslationProperties current = item.getTranslationProperties();
                String nameKey = item.getTranslationKey();
                if (nameKey == null || nameKey.isBlank()) {
                    continue;
                }

                ItemTranslationProperties replacement = buildTranslationProperties(
                        nameKey, current, definition);
                originals.put(item, current);
                itemTranslations.set(item, replacement);
                item.invalidatePacketCache();
                installed++;
            }
            return installed;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Could not attach Comfort values to native item tooltips", exception);
        }
    }

    synchronized void restore() {
        try {
            restoreOriginals();
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(
                    "Could not restore native item translation properties", exception);
        }
    }

    private void restoreOriginals() throws IllegalAccessException {
        for (Map.Entry<Item, ItemTranslationProperties> entry : originals.entrySet()) {
            Item item = entry.getKey();
            itemTranslations.set(item, entry.getValue());
            item.invalidatePacketCache();
        }
        originals.clear();
    }

    ItemTranslationProperties buildTranslationProperties(
            String nameKey,
            ItemTranslationProperties current,
            ComfortDefinition definition) throws IllegalAccessException {
        boolean hasVanillaDescription = current != null
                && current.getDescription() != null
                && !current.getDescription().isBlank();
        ItemTranslationProperties replacement = new ItemTranslationProperties(
                nameKey,
                hasVanillaDescription
                        ? descriptionWithVanillaKey(definition)
                        : descriptionKey(definition));

        if (current != null && current.getNameArguments() != null
                && !current.getNameArguments().isEmpty()) {
            nameArguments.set(
                    replacement,
                    new LinkedHashMap<>(current.getNameArguments()));
        }
        if (hasVanillaDescription) {
            Message vanilla = Message.translation(current.getDescription());
            if (current.getDescriptionArguments() != null) {
                for (Map.Entry<String, Message> argument :
                        current.getDescriptionArguments().entrySet()) {
                    vanilla.param(argument.getKey(), argument.getValue());
                }
            }
            descriptionArguments.set(
                    replacement,
                    Map.of("vanillaDescription", vanilla));
        }
        return replacement;
    }

    static String descriptionKey(ComfortDefinition definition) {
        return "server.taverns.item.comfort."
                + definition.category().tooltipToken()
                + "." + definition.comfort();
    }

    static String descriptionWithVanillaKey(ComfortDefinition definition) {
        return "server.taverns.item.comfort_with_description."
                + definition.category().tooltipToken()
                + "." + definition.comfort();
    }
}
