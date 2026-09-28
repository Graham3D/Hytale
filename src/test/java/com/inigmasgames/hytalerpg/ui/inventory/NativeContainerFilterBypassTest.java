package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import org.bson.BsonString;
import org.bson.BsonDocument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Detached native API check. It never swaps a player's actual storage. */
class NativeContainerFilterBypassTest {
    private static final String ID = "Rock_Stone";
    private static HytaleAssetStore<String, Item, DefaultAssetMap<String, Item>> fixture;

    private static final class FilteredContainer extends SimpleItemContainer {
        FilteredContainer() { super((short) 2); }
        @Override protected boolean cantAddToSlot(short slot, ItemStack stack, ItemStack existing) {
            return true;
        }
    }

    private static final class GuardedContainer extends SimpleItemContainer {
        GuardedContainer() { super((short) 2); }
        @Override protected ItemStack internal_setSlot(short slot, ItemStack stack) {
            if (!ItemStack.isEmpty(stack)) throw new IllegalStateException("SPATIAL_REJECT");
            return super.internal_setSlot(slot, stack);
        }
    }

    private static final class SilentGuardContainer extends SimpleItemContainer {
        SilentGuardContainer() { super((short) 2); }
        @Override protected ItemStack internal_setSlot(short slot, ItemStack stack) {
            return ItemStack.isEmpty(stack) ? super.internal_setSlot(slot, stack) : getItemStack(slot);
        }
    }

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

    @Test void lowLevelFilterFlagCanBypassAnOtherwiseRejectingContainer() {
        var bag = new FilteredContainer();
        var stack = new ItemStack(ID);
        assertFalse(bag.setItemStackForSlot((short) 0, stack, true).succeeded());
        assertNull(bag.getItemStack((short) 0));
        assertTrue(bag.setItemStackForSlot((short) 0, stack, false).succeeded());
        assertEquals(ID, bag.getItemStack((short) 0).getItemId());
    }

    @Test void lowLevelGuardRejectsDirectFilterFalseWrite() {
        var bag = new GuardedContainer();
        assertThrows(IllegalStateException.class,
                () -> bag.setItemStackForSlot((short) 0, new ItemStack(ID), false));
        assertNull(bag.getItemStack((short) 0));
    }

    @Test void throwingLowLevelGuardLosesSourceInNativeTransfer() {
        var source = new SimpleItemContainer((short) 2);
        assertTrue(source.setItemStackForSlot((short) 0, new ItemStack(ID), false).succeeded());
        var destination = new GuardedContainer();
        assertThrows(IllegalStateException.class,
                () -> source.moveItemStackFromSlotToSlot((short) 0, 1, destination, (short) 0, false));
        assertNull(source.getItemStack((short) 0), "Native transfer removes before destination guard runs");
        assertNull(destination.getItemStack((short) 0));
    }

    @Test void silentLowLevelGuardReportsSuccessWithoutActuallyReceivingStack() {
        var source = new SimpleItemContainer((short) 2);
        assertTrue(source.setItemStackForSlot((short) 0, new ItemStack(ID), false).succeeded());
        var destination = new SilentGuardContainer();
        var result = source.moveItemStackFromSlotToSlot((short) 0, 1, destination, (short) 0, false);
        assertTrue(result.succeeded(), "Native transfer reports the false success");
        assertNull(source.getItemStack((short) 0));
        assertNull(destination.getItemStack((short) 0));
    }

    @Test void visualAliasNeverSendsManagedGearMetadataToClientItemGrid() {
        var real = new ItemStack("RPG_Gear_shortbow_copper_n", 1)
                .withMetadata("RpgGearV1", new BsonString("frozen-instance"));
        var visual = NativeSpatialDragProbePage.projectionStack(real);
        assertEquals("Weapon_Shortbow_Copper", visual.getItemId());
        assertNull(visual.getMetadata());
        assertNotNull(real.getMetadata());
        var commands = new UICommandBuilder();
        commands.set("#NativeAliasGrid.Slots", new ItemGridSlot[]{new ItemGridSlot(visual)});
        var slot = BsonDocument.parse(commands.getCommands()[0].data).getArray("0").get(0).asDocument();
        assertFalse(slot.getDocument("ItemStack").containsKey("Metadata"));
        assertFalse(slot.toJson().contains("RpgGearV1"));
        assertFalse(slot.toJson().contains("ItemDisplay"));
    }
}
