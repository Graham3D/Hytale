package com.inigmasgames.persistentnpcs.stats;

import com.google.gson.JsonObject;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.protocol.EntityStatResetBehavior;
import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemArmor;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.ModifierTarget;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier.CalculationType;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipFile;

/** Installed-asset, native-stat and lifecycle wiring coverage for R163. */
public final class R163NpcEquipmentStatsSyncTest {
    private static final int HEALTH = 2;

    public static void main(String[] args) throws Exception {
        try {
            installedArmorContract();
            deterministicSources();
            nativeCurrentMaximumSemantics();
            lifecycleAndUiAuthorityWiring();
            System.out.println("R163 equipment stat sync PASS: installed armor metadata, deterministic source ownership, native max/current clamps, spawn/hydration/restart/container hooks, bounded markers and EntityStatMap-only Profile reads.");
        } catch (Throwable failure) {
            failure.printStackTrace(System.out);
            throw failure;
        }
    }

    private static void installedArmorContract() throws Exception {
        Path assets = Path.of(System.getenv("APPDATA"),
                "Hytale/install/release/package/game/latest/Assets.zip");
        try (ZipFile zip = new ZipFile(assets.toFile())) {
            JsonObject copper = read(zip,
                    "Server/Item/Items/Armor/Copper/Armor_Copper_Chest.json");
            var health = copper.getAsJsonObject("Armor")
                    .getAsJsonObject("StatModifiers").getAsJsonArray("Health")
                    .get(0).getAsJsonObject();
            assert health.get("Amount").getAsInt() == 9;
            assert health.get("CalculationType").getAsString().equals("Additive");
            JsonObject cinder = read(zip,
                    "Server/Item/Items/Armor/Cloth_Cindercloth/Armor_Cloth_Cindercloth_Hands.json");
            assert cinder.getAsJsonObject("Armor").getAsJsonObject("StatModifiers")
                    .getAsJsonArray("Mana").get(0).getAsJsonObject()
                    .get("Amount").getAsInt() == 16;
        }
    }

    private static JsonObject read(ZipFile zip, String path) throws Exception {
        try (var reader = new java.io.InputStreamReader(
                zip.getInputStream(zip.getEntry(path)), StandardCharsets.UTF_8)) {
            return JsonFiles.GSON.fromJson(reader, JsonObject.class);
        }
    }

    private static void deterministicSources() {
        var armor = new MutableArmorContainer();
        armor.put((short) 1, stack("Armor_Copper_Chest", ItemArmorSlot.Chest,
                HEALTH, 9));
        assert !ItemStack.isEmpty(armor.getItemStack((short) 1));
        assert armor.getItemStack((short) 1).getItem().getArmor() != null;
        assert armor.getItemStack((short) 1).getItem().getArmor().getStatModifiers().size() == 1
                : armor.getItemStack((short) 1).getItem().getArmor().getStatModifiers();
        Map<String, NpcEquipmentStatSynchronizer.SourceModifier> once =
                NpcEquipmentStatSynchronizer.describe(armor);
        Map<String, NpcEquipmentStatSynchronizer.SourceModifier> reopen =
                NpcEquipmentStatSynchronizer.describe(armor);
        assert once.size() == 1 && once.keySet().equals(reopen.keySet())
                : "once=" + once + " reopen=" + reopen;
        var source = once.values().iterator().next();
        assert source.sourceIdentity().equals("NPC_EQUIPMENT:CHEST:stat_2:modifier_0");
        assert source.slot() == 1 && source.statIndex() == HEALTH;
        assert source.nativeModifierKey().equals(CalculationType.ADDITIVE.createKey("Armor"));

        armor.put((short) 1, stack("Armor_Iron_Chest", ItemArmorSlot.Chest,
                HEALTH, 17));
        var replacement = NpcEquipmentStatSynchronizer.describe(armor);
        assert replacement.size() == 1 && replacement.containsKey(source.sourceIdentity());
        assert replacement.values().iterator().next().itemId().equals("Armor_Iron_Chest");
        armor.put((short) 0, stack("Armor_Copper_Head", ItemArmorSlot.Head,
                HEALTH, 5));
        assert NpcEquipmentStatSynchronizer.describe(armor).size() == 2;
        armor.put((short) 1, null);
        var removed = NpcEquipmentStatSynchronizer.describe(armor);
        assert removed.size() == 1 && !removed.containsKey(source.sourceIdentity());
    }

    private static Stack stack(String id, ItemArmorSlot slot, int statIndex, float amount) {
        var modifiers = new Int2ObjectOpenHashMap<StaticModifier[]>();
        modifiers.put(statIndex, new StaticModifier[] {
                new StaticModifier(ModifierTarget.MAX, CalculationType.ADDITIVE, amount) });
        ItemArmor fixtureArmor = new ItemArmor(slot, 0, modifiers, null);
        Item item = new Item(id) {
            @Override public ItemArmor getArmor() { return fixtureArmor; }
        };
        return new Stack(item);
    }

    private static void nativeCurrentMaximumSemantics() throws Exception {
        EntityStatType health = new EntityStatType("Health", 100, 0, 100, true,
                null, null, null, EntityStatResetBehavior.MaxValue);
        var definitions = Map.of("Health", health);
        var assetStore = new FixtureStore.FixtureBuilder(definitions)
                .setCodec(EntityStatType.CODEC).setKeyFunction(EntityStatType::getId)
                .setPath("Entity/Stats").build();
        var storeField = EntityStatType.class.getDeclaredField("ASSET_STORE");
        storeField.setAccessible(true);
        storeField.set(null, assetStore);
        var index = DefaultEntityStatTypes.class.getDeclaredField("HEALTH");
        index.setAccessible(true);
        index.setInt(null, HEALTH);

        EntityStatMap map = new EntityStatMap();
        EntityStatValue[] values = new EntityStatValue[HEALTH + 1];
        values[HEALTH] = new EntityStatValue(HEALTH, health);
        var valuesField = EntityStatMap.class.getDeclaredField("values");
        valuesField.setAccessible(true);
        valuesField.set(map, values);
        String nativeKey = CalculationType.ADDITIVE.createKey("Armor");
        var plusTwenty = new StaticModifier(
                ModifierTarget.MAX, CalculationType.ADDITIVE, 20);
        map.putModifier(HEALTH, nativeKey, plusTwenty);
        assert map.get(HEALTH).get() == 100 && map.get(HEALTH).getMax() == 120;
        map.putModifier(HEALTH, nativeKey, plusTwenty);
        assert map.get(HEALTH).getMax() == 120 : "Native replacement key accumulated";
        map.setStatValue(HEALTH, 120);
        map.removeModifier(HEALTH, nativeKey);
        assert map.get(HEALTH).getMax() == 100 && map.get(HEALTH).get() == 100;
    }

    private static void lifecycleAndUiAuthorityWiring() throws Exception {
        String sync = Files.readString(Path.of("src/main/java/com/inigmasgames/persistentnpcs/stats/NpcEquipmentStatSynchronizer.java"));
        String bridge = Files.readString(Path.of("src/main/java/com/inigmasgames/persistentnpcs/stats/NpcStatRuntimeBridge.java"));
        String inventory = Files.readString(Path.of("src/main/java/com/inigmasgames/persistentnpcs/profile/NpcInventoryRepository.java"));
        String plugin = Files.readString(Path.of("src/main/java/com/inigmasgames/persistentnpcs/PersistentNpcsPlugin.java"));
        String profile = Files.readString(Path.of("src/main/java/com/inigmasgames/persistentnpcs/profile/NpcStatsSnapshotService.java"));
        assert sync.contains("recalculateEntityStatModifiers(ref, stats, store)");
        for (String marker : new String[] {"NPC_EQUIPMENT_STATS_SYNC_BEGIN",
                "NPC_EQUIPMENT_STAT_APPLIED", "NPC_EQUIPMENT_STAT_REMOVED",
                "NPC_EQUIPMENT_STATS_SYNC_COMPLETE"}) assert sync.contains(marker);
        assert bridge.contains("FRESH_SPAWN_HYDRATION")
                && bridge.contains("WORLD_RESTORE_HYDRATION");
        assert inventory.contains("\"NPC_SPAWN\"")
                && inventory.contains("\"PERSISTED_INVENTORY_HYDRATION\"")
                && inventory.contains("\"ARMOR_CONTAINER_CHANGE\"");
        assert plugin.contains("configureEquipmentStatsSync(npcStats::syncEquipment)");
        assert profile.contains("authority.npcRef(), EntityStatMap.getComponentType()")
                && profile.contains("value.get(), value.getMin(), value.getMax()");
        assert !profile.contains("getStatModifiers()")
                : "Profile UI/snapshot must not calculate armor-derived vitals";
    }

    private static final class Stack extends ItemStack {
        private final Item asset;
        Stack(Item asset) {
            super();
            this.asset = asset;
            this.itemId = asset.getId();
            this.quantity = 1;
        }
        @Override public Item getItem() { return asset; }
        @Override public ItemStack withQuantity(int value) {
            return value == 0 ? null : new Stack(asset);
        }
    }

    private static final class MutableArmorContainer extends SimpleItemContainer {
        private final ItemStack[] slots = new ItemStack[4];
        MutableArmorContainer() { super((short) 4); }
        void put(short slot, ItemStack stack) { slots[slot] = stack; }
        @Override public ItemStack getItemStack(short slot) { return slots[slot]; }
    }

    private static final class FixtureMap extends IndexedLookupTableAssetMap<String, EntityStatType> {
        private final Map<String, EntityStatType> definitions;
        FixtureMap(Map<String, EntityStatType> definitions) {
            super(EntityStatType[]::new);
            this.definitions = definitions;
        }
        @Override public EntityStatType getAsset(int index) {
            return index == HEALTH ? definitions.get("Health") : null;
        }
    }

    private static final class FixtureStore extends AssetStore<String, EntityStatType,
            IndexedLookupTableAssetMap<String, EntityStatType>> {
        private static final class FixtureBuilder extends Builder<String, EntityStatType,
                IndexedLookupTableAssetMap<String, EntityStatType>, FixtureBuilder> {
            FixtureBuilder(Map<String, EntityStatType> definitions) {
                super(String.class, EntityStatType.class, new FixtureMap(definitions));
                setReplaceOnRemove(id -> EntityStatType.UNKNOWN);
                setIsUnknown(EntityStatType::isUnknown);
            }
            @Override public FixtureStore build() { return new FixtureStore(this); }
        }
        FixtureStore(FixtureBuilder builder) { super(builder); }
        @Override protected com.hypixel.hytale.event.IEventBus getEventBus() { return null; }
        @Override public void addFileMonitor(String pack, Path path) { }
        @Override public void removeFileMonitor(Path path) { }
        @Override protected void handleRemoveOrUpdate(Set<String> keys,
                Map<String, EntityStatType> assets,
                com.hypixel.hytale.assetstore.AssetUpdateQuery query) { }
    }
}
