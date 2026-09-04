package com.inigmasgames.persistentnpcs.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import java.util.function.IntPredicate;

/** Exact presentation/event contract shared by Probe 11 and the NPC Profile. */
public final class CustomInventoryBridgeUi {
    public static final String DROP_MARKER = "CUSTOM_BRIDGE_DROP";

    private CustomInventoryBridgeUi() { }

    public static void bindDrop(UIEventBuilder events, String selector, int targetSection) {
        bindDrop(events, selector, targetSection, new EventData());
    }

    public static void bindDrop(UIEventBuilder events, String selector, int targetSection,
            EventData envelope) {
        EventData data = envelope
                .append("Marker", DROP_MARKER)
                .append("Event", "Dropped")
                .append("Section", Integer.toString(targetSection));
        events.addEventBinding(CustomUIEventBindingType.Dropped, selector, data, false);
    }

    /**
     * Replaces the complete fixed-capacity snapshot and supplies the slot identity
     * fields that the current Custom UI encoder omits.
     */
    public static void setNativeSlots(UICommandBuilder commands, String selector,
            ItemContainer container) {
        setNativeSlots(commands, selector, container, 0, container.getCapacity());
    }

    /** Encodes a bounded visual range while retaining the authoritative source indexes. */
    public static void setNativeSlots(UICommandBuilder commands, String selector,
            ItemContainer container, int firstSlot, int slotCount) {
        setNativeSlots(commands, selector, container, firstSlot, slotCount,
                ignored -> false);
    }

    /** Encodes a bounded range and marks authoritative compatibility verdicts. */
    public static void setNativeSlots(UICommandBuilder commands, String selector,
            ItemContainer container, int firstSlot, int slotCount,
            IntPredicate incompatibleSlot) {
        if (firstSlot < 0 || slotCount < 0
                || firstSlot + slotCount > container.getCapacity()) {
            throw new IllegalArgumentException("Invalid ItemGrid slot range.");
        }
        ItemGridSlot[] slots = new ItemGridSlot[slotCount];
        for (int visualSlot = 0; visualSlot < slotCount; visualSlot++) {
            short inventorySlot = (short) (firstSlot + visualSlot);
            ItemStack stack = container.getItemStack(inventorySlot);
            slots[visualSlot] = ItemStack.isEmpty(stack)
                    ? new ItemGridSlot() : new ItemGridSlot(stack);
            slots[visualSlot].setActivatable(true);
        }
        int commandCount = commands.getCommands().length;
        commands.set(selector, slots);
        var encodedCommands = commands.getCommands();
        if (encodedCommands.length != commandCount + 1) {
            throw new IllegalStateException("Unable to identify Custom UI slot command.");
        }
        var command = encodedCommands[encodedCommands.length - 1];
        BsonDocument data = BsonDocument.parse(command.data);
        BsonArray encodedSlots = data.getArray("0");
        if (encodedSlots.size() != slots.length) {
            throw new IllegalStateException("Custom UI slot encoding changed shape.");
        }
        for (int visualSlot = 0; visualSlot < encodedSlots.size(); visualSlot++) {
            BsonDocument encoded = encodedSlots.get(visualSlot).asDocument();
            encoded.put("InventorySlotIndex", new BsonInt32(firstSlot + visualSlot));
            encoded.put("IsActivatable", BsonBoolean.TRUE);
            encoded.put("IsItemIncompatible", BsonBoolean.valueOf(
                    incompatibleSlot.test(firstSlot + visualSlot)));
        }
        command.data = data.toJson();
    }
}
