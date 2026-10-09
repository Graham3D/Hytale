package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Invoked in the test-only binding overlay loader by NativeCarrierStackQualificationTest. */
public final class NativeCarrierIsolatedRoundTrip {
    private NativeCarrierIsolatedRoundTrip() {}

    public static int run() {
        var catalog = GearCatalog.load();
        var bindings = new GearBindings();
        int roundTrips = 0;
        for (var binding : bindings.all()) {
            var baseId = binding.baseId();
            if (!baseId.matches("gm\\.(?:staff|wand|book|shield|bomb)_.+\\.(?:n|nm|h)")) continue;
            assertTrue(binding.mapped(), baseId);
            var base = catalog.base(baseId);
            var affixId = baseId.startsWith("gm.shield_") ? "WA-069"
                    : baseId.startsWith("gm.bomb_") ? "WA-001" : "WA-003";
            var definition = catalog.affix(affixId);
            var frozen = new GearInstance.AffixRoll(affixId, definition.side(),
                    definition.exclusionGroup(), 3, 7.5, new GearRequirements.Gate(1, Map.of()),
                    "offline frozen value", definition.name(), null);
            for (var rarity : List.of(GearRarity.COMMON, GearRarity.UNCOMMON, GearRarity.RARE,
                    GearRarity.VERY_RARE, GearRarity.LEGENDARY)) {
                var gear = new GearInstance(1, UUID.randomUUID(), GearCatalog.REVISION, baseId,
                        base.name(), base.category(), DifficultyId.HELL, 95, rarity, 947,
                        base.perfectStats(), new GearRequirements.Gate(base.requiredLevel(),
                        base.requiredAttributes()), List.of(frozen), "native-offline-qualification", true);
                String expectedId = binding.carrier(rarity);
                assertNotNull(Item.getAssetMap().getAsset(expectedId), expectedId);
                ItemStack created = GearNativeItems.create(gear, 95, Map.of());
                assertEquals(expectedId, created.getItemId());
                assertEquals(1, created.getQuantity());
                assertEquals(base.durability(), created.getMaxDurability());
                assertEquals(ItemQuality.getAssetMap().getIndex(rarity.qualityAsset()), created.getQualityIndex());
                assertEquals(gear, GearNativeItems.read(created));
                assertEquals(gear.identity(), GearNativeItems.read(created).identity());
                var worn = created.withDurability(Math.max(1, base.durability() - 17));
                assertEquals(Math.max(1, base.durability() - 17), worn.getDurability());
                assertEquals(gear, GearNativeItems.read(worn));
                var encoded = ItemStack.CODEC.encode(worn, new ExtraInfo());
                var restored = ItemStack.CODEC.decode(encoded, new ExtraInfo());
                assertEquals(worn.getItemId(), restored.getItemId());
                assertEquals(worn.getDurability(), restored.getDurability());
                assertEquals(worn.getMaxDurability(), restored.getMaxDurability());
                assertEquals(worn.getQualityIndex(), restored.getQualityIndex());
                assertEquals(worn.getMetadata(), restored.getMetadata());
                assertEquals(gear, GearNativeItems.read(restored));
                roundTrips++;
            }
        }
        assertEquals(450, roundTrips);
        return roundTrips;
    }
}
