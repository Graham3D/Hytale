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
        ItemGridSlot[] slots = new ItemGridSlot[container.getCapacity()];
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            slots[slot] = ItemStack.isEmpty(stack)
                    ? new ItemGridSlot() : new ItemGridSlot(stack);
            slots[slot].setActivatable(true);
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
        for (int slot = 0; slot < encodedSlots.size(); slot++) {
            BsonDocument encoded = encodedSlots.get(slot).asDocument();
            encoded.put("InventorySlotIndex", new BsonInt32(slot));
            encoded.put("IsActivatable", BsonBoolean.TRUE);
            encoded.put("IsItemIncompatible", BsonBoolean.FALSE);
        }
        command.data = data.toJson();
    }
}
