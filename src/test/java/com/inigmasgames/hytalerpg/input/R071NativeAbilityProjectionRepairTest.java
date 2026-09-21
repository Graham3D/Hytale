package com.inigmasgames.hytalerpg.input;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for Reset/rejoin/re-equip stale native HUD projections. */
class R071NativeAbilityProjectionRepairTest {
    private static final short SLOT = NativeAbilityProjectionService.ABILITY2_PRIMARY_INDEX;
    private static final String SPARK = "RPG_Ability_Charged_Bolt";
    private static final String FOREIGN = "Weapon_Sword_Iron";
    private static HytaleAssetStore<String, Item, DefaultAssetMap<String, Item>> store;

    @BeforeAll static void nativeItemFixture() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        Map<String, Item> items = Map.of(SPARK, new Item(SPARK), FOREIGN, new Item(FOREIGN));
        var builder = HytaleAssetStore.builder(Item.class, new DefaultAssetMap<String, Item>(items))
                .setPath("Item/Items").setCodec(Item.CODEC).setKeyFunction(Item::getId);
        store = new HytaleAssetStore<>(builder) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        AssetRegistry.register(store);
    }

    @AfterAll static void releaseFixture() { if (store != null) AssetRegistry.unregister(store); }

    @Test void authoritativeProjectionCanClearAndReinstallOwnedAbilityStack() {
        var container = new InventoryComponent.AbilitySlots(
                InventoryComponent.DEFAULT_ABILITIES_CAPACITY).getInventory();
        var spark = new ItemStack(SPARK);

        assertTrue(NativeAbilityProjectionService.writeOwnedProjection(container, SLOT, SPARK, spark));
        assertEquals(SPARK, container.getItemStack(SLOT).getItemId());

        assertTrue(NativeAbilityProjectionService.clearOwnedProjection(container, SLOT));
        assertTrue(ItemStack.isEmpty(container.getItemStack(SLOT)));

        assertTrue(NativeAbilityProjectionService.writeOwnedProjection(
                container, SLOT, SPARK, new ItemStack(SPARK)));
        assertEquals(SPARK, container.getItemStack(SLOT).getItemId());
    }

    @Test void authoritativeProjectionNeverClearsOrReplacesForeignNativeRune() {
        var container = new InventoryComponent.AbilitySlots(
                InventoryComponent.DEFAULT_ABILITIES_CAPACITY).getInventory();
        var foreign = new ItemStack(FOREIGN);
        assertTrue(container.setItemStackForSlot(SLOT, foreign, false).succeeded());

        assertFalse(NativeAbilityProjectionService.clearOwnedProjection(container, SLOT));
        assertFalse(NativeAbilityProjectionService.writeOwnedProjection(
                container, SLOT, SPARK, new ItemStack(SPARK)));
        assertEquals(FOREIGN, container.getItemStack(SLOT).getItemId());
    }
}
