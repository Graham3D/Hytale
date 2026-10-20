package com.inigmasgames.hytalerpg.combat.power;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Versioned fallback registry for vanilla items lacking explicit RPG tags. */
public final class ItemPowerRegistry {
    private final int schemaVersion;
    private final String registryId;
    private final Map<String, ItemPowerDescriptor> items;

    public ItemPowerRegistry(int schemaVersion, String registryId, List<ItemPowerDescriptor> descriptors) {
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported item power registry schema: " + schemaVersion);
        this.schemaVersion = schemaVersion; this.registryId = registryId;
        Map<String, ItemPowerDescriptor> indexed = new HashMap<>();
        for (ItemPowerDescriptor descriptor : descriptors) {
            if (indexed.put(descriptor.itemId(), descriptor) != null)
                throw new IllegalArgumentException("Duplicate item power entry: " + descriptor.itemId());
        }
        this.items = Map.copyOf(indexed);
    }
    public static ItemPowerRegistry loadCanonical() {
        try (var stream = ItemPowerRegistry.class.getResourceAsStream("/rpg/balance/item-power-registry-v1.json")) {
            if (stream == null) throw new IllegalStateException("Missing item-power-registry-v1.json");
            RegistryData data = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), RegistryData.class);
            return new ItemPowerRegistry(data.schemaVersion, data.registryId, data.items == null ? List.of() : data.items);
        } catch (RuntimeException error) { throw error; }
        catch (Exception error) { throw new IllegalStateException("Cannot load item power registry", error); }
    }
    public Optional<ItemPowerDescriptor> find(String itemId) { return Optional.ofNullable(items.get(itemId)); }
    public int schemaVersion() { return schemaVersion; }
    public String registryId() { return registryId; }
    private static final class RegistryData { int schemaVersion; String registryId; List<ItemPowerDescriptor> items; }
}
