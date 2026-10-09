package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.codec.ExtraInfo;
import org.bson.BsonString;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NativeEquipmentWriteTest {
    private static final String ID = "Rock_Stone";
    private static HytaleAssetStore<String, Item, DefaultAssetMap<String, Item>> fixture;

    @BeforeAll static void setup() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var builder = HytaleAssetStore.builder(Item.class,
                new DefaultAssetMap<String, Item>(Map.of(ID, new Item(ID))))
                .setPath("Item/Items").setCodec(Item.CODEC).setKeyFunction(Item::getId);
        fixture = new HytaleAssetStore<>(builder) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        AssetRegistry.register(fixture);
    }

    @AfterAll static void teardown() { if (fixture != null) AssetRegistry.unregister(fixture); }

    @Test void validatedServerTransferCanEquipAndUnequipThroughNativeAddFilter() {
        var slot = new SimpleItemContainer((short) 1) {
            @Override protected boolean cantAddToSlot(short index, ItemStack stack, ItemStack old) {
                return true;
            }
        };
        var incoming = new ItemStack(ID);
        String frozen = ItemStack.CODEC.encode(incoming, new ExtraInfo()).asDocument().toJson();
        assertFalse(slot.setItemStackForSlot((short) 0, incoming).succeeded());

        HytaleGearLoot.writeNativeEquipment(slot, (short) 0, incoming, frozen);
        assertEquals(ID, slot.getItemStack((short) 0).getItemId());
        HytaleGearLoot.writeNativeEquipment(slot, (short) 0, ItemStack.EMPTY, null);
        assertNull(slot.getItemStack((short) 0));
    }

    @Test void rejectedLowLevelWriteDoesNotReportSuccessfulEquipment() {
        var slot = new SimpleItemContainer((short) 1) {
            @Override protected ItemStack internal_setSlot(short index, ItemStack stack) {
                return getItemStack(index);
            }
        };
        var incoming = new ItemStack(ID);
        String frozen = ItemStack.CODEC.encode(incoming, new ExtraInfo()).asDocument().toJson();
        assertThrows(IllegalStateException.class,
                () -> HytaleGearLoot.writeNativeEquipment(slot, (short) 0, incoming, frozen));
        assertNull(slot.getItemStack((short) 0));
    }

    @Test void frozenComparisonIgnoresOnlyPresentationWithoutDecodingItemAsset() {
        var selected = new ItemStack(ID).withMetadata("RpgGearV1", new BsonString("identity"))
                .withMetadata("ItemDisplay", new BsonString("before"));
        String frozen = ItemStack.CODEC.encode(selected, new ExtraInfo()).asDocument().toJson();
        var refreshed = selected.withMetadata("ItemDisplay", new BsonString("after"));
        assertTrue(HytaleGearLoot.sameFrozen(refreshed, frozen));
        assertFalse(HytaleGearLoot.sameFrozen(
                refreshed.withMetadata("RpgGearV1", new BsonString("different")), frozen));
    }
}
