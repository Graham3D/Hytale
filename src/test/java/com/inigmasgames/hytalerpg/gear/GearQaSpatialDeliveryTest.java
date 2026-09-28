package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.inigmasgames.hytalerpg.ui.inventory.FootprintCatalog;
import com.inigmasgames.hytalerpg.ui.inventory.SpatialBagAggregate;
import org.bson.BsonString;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class GearQaSpatialDeliveryTest {
    private static final String[][] CASES = {
            {"shortbow", "magic", "normal"},
            {"shortbow", "magic", "nightmare"},
            {"shortbow", "magic", "hell"},
            {"sword", "magic", "normal"},
            {"helmet", "magic", "nightmare"},
    };
    private static HytaleAssetStore<String, Item, DefaultAssetMap<String, Item>> fixture;

    @BeforeAll static void setup() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var catalog = GearCatalog.load();
        var bindings = new GearBindings();
        var generator = new GearDropGenerator(catalog, bindings, GearAffixRuntime.ENABLED);
        Map<String, Item> items = new LinkedHashMap<>();
        for (String[] row : CASES) {
            var request = GearQaRequest.parse(row[0], row[1], row[2], null, "spatial-" + String.join("-", row));
            var gear = generator.generateQa(request);
            String id = bindings.require(gear.baseId()).carrier(gear.rarity());
            items.put(id, new Item(id));
        }
        var builder = HytaleAssetStore.builder(Item.class, new DefaultAssetMap<String, Item>(items))
                .setPath("Item/Items").setCodec(Item.CODEC).setKeyFunction(Item::getId);
        fixture = new HytaleAssetStore<>(builder) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        AssetRegistry.register(fixture);
    }

    @AfterAll static void teardown() { if (fixture != null) AssetRegistry.unregister(fixture); }

    @Test void allErasAndOtherFamiliesKeepFrozenPayloadThroughSpatialSave() {
        var gearCatalog = GearCatalog.load();
        var bindings = new GearBindings();
        var generator = new GearDropGenerator(gearCatalog, bindings, GearAffixRuntime.ENABLED);
        var footprints = FootprintCatalog.loadDefault();
        var seenLevels = new java.util.HashSet<Integer>();
        var seenStats = new java.util.HashSet<Map<String, Double>>();
        for (String[] row : CASES) {
            var request = GearQaRequest.parse(row[0], row[1], row[2], null, "spatial-" + String.join("-", row));
            var gear = generator.generateQa(request);
            assertEquals(request.era(), gear.sourceEra());
            assertEquals(GearRarity.MAGIC, gear.rarity());
            assertTrue(gearCatalog.base(gear.baseId()).eligible(request.era(), gear.itemLevel()));
            if (row[0].equals("shortbow")) {
                seenLevels.add(gear.itemLevel());
                seenStats.add(gear.intrinsicStats());
            }
            String carrier = bindings.require(gear.baseId()).carrier(gear.rarity());
            var stack = new ItemStack(carrier, 1).withQuality(12)
                    .withMetadata(GearNativeItems.KEY, new BsonString(gear.toJson()));
            assertEquals(gear, GearNativeItems.read(stack));
            assertNotNull(footprints.size(GearNativeItems.nativeId(carrier)));
            UUID owner = UUID.randomUUID();
            var before = new SpatialBagAggregate(owner, footprints.revision());
            var offered = before.offer(gear.identity(), before.revision(), stack, footprints);
            assertTrue(offered.accepted(), String.join("/", row));
            var restored = SpatialBagAggregate.fromBson(offered.bag().toBson(), footprints);
            var saved = restored.entry(gear.identity()).orElseThrow().payload();
            assertEquals(stack.getItemId(), saved.getItemId());
            assertEquals(stack.getQualityIndex(), saved.getQualityIndex());
            assertEquals(stack.getMetadata(), saved.getMetadata());
            assertEquals(gear, GearNativeItems.read(saved));
            assertEquals(SpatialBagAggregate.Outcome.REPLAY_MISMATCH,
                    restored.offer(gear.identity(), restored.revision(), stack, footprints).receipt().outcome());
        }
        assertEquals(3, seenLevels.size());
        assertEquals(3, seenStats.size());
    }
}
