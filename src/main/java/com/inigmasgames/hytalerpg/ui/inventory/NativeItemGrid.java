package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import org.bson.BsonDocument;

/** Supplies real item details to Hytale's built-in ItemGrid tooltip and cursor. */
final class NativeItemGrid {
    private NativeItemGrid() { }

    static void writeItem(UICommandBuilder commands, String selector, ItemStack stack) {
        var slot = slot(stack);
        slot.setActivatable(true);
        writeSlots(commands, selector, new ItemGridSlot[]{slot});
    }

    static ItemGridSlot slot(ItemStack stack) {
        if (ItemStack.isEmpty(stack)) return new ItemGridSlot();
        var slot = new ItemGridSlot(stack);
        var display = stack.getFromMetadataOrNull(ItemDisplayMetadata.KEYED_CODEC);
        if (display != null) {
            var name = plain(display.getName());
            var description = plain(display.getDescription());
            if (!name.isBlank()) slot.setName(name);
            if (!description.isBlank()) slot.setDescription(description);
        }
        return slot;
    }

    private static String plain(Message message) {
        if (message == null) return "";
        var text = new StringBuilder();
        appendPlain(message, text);
        return text.toString();
    }

    private static void appendPlain(Message message, StringBuilder text) {
        if (message.getRawText() != null) text.append(message.getRawText());
        for (var child : message.getChildren()) appendPlain(child, text);
    }

    static void writeSlots(UICommandBuilder commands, String selector, ItemGridSlot[] slots) {
        int before = commands.getCommands().length;
        commands.set(selector + ".Slots", slots);
        var encodedCommands = commands.getCommands();
        if (encodedCommands.length != before + 1)
            throw new IllegalStateException("Native item grid slot encoding changed");
        var command = encodedCommands[encodedCommands.length - 1];
        var data = BsonDocument.parse(command.data);
        var encoded = data.getArray("0");
        if (encoded.size() != slots.length)
            throw new IllegalStateException("Native item grid cell count changed");
        for (var value : encoded) {
            var slot = value.asDocument();
            if (!slot.containsKey("ItemStack")) continue;
            var stack = slot.getDocument("ItemStack");
            // ItemStack.CODEC is a save schema. Connected client evidence rejects
            // both QualityOverride and save-format Metadata in ClientItemStack.
            // Project only the client-supported display fields; Name/Description
            // above carry authored tooltip text without sending managed item data.
            var client = new BsonDocument();
            for (var field : new String[]{"Id", "Quantity", "Durability", "MaxDurability", "Quality"})
                if (stack.containsKey(field)) client.put(field, stack.get(field));
            if (stack.containsKey("QualityOverride")) client.put("Quality", stack.get("QualityOverride"));
            slot.put("ItemStack", client);
        }
        command.data = data.toJson();
    }
}
