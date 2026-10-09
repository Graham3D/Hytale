package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;

/** Supplies real item details to Hytale's built-in ItemGrid tooltip and cursor. */
final class NativeItemGrid {
    private NativeItemGrid() { }

    static void writeItem(UICommandBuilder commands, String selector, ItemStack stack) {
        var slot = ItemStack.isEmpty(stack) ? new ItemGridSlot() : new ItemGridSlot(stack);
        slot.setActivatable(true);
        writeSlots(commands, selector, new ItemGridSlot[]{slot});
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
            // U7P5 ItemGridSlot still delegates to the server persistence codec,
            // which now writes QualityOverride. ClientItemStack still accepts
            // Quality, not QualityOverride (confirmed by the client crash log).
            // Resolve inherited quality too: the client needs the visible asset
            // quality rather than the persistence codec's optional override.
            var quality = stack.remove("QualityOverride");
            if (quality == null) {
                var asset = Item.getAssetMap().getAsset(stack.getString("Id").getValue());
                quality = new BsonInt32((asset == null ? Item.UNKNOWN : asset).getQualityIndex());
            }
            stack.put("Quality", quality);
            // ItemStack.CODEC writes the server's arbitrary BSON metadata.
            // U7P5's ClientItemMetadata is a typed client structure; passing
            // the server document here disconnects the client. The native
            // ItemGridSlot has separate Name and Description strings for
            // display. Project only those fields and keep the original stack
            // and its private gear metadata unchanged.
            var metadata = stack.remove("Metadata");
            if (metadata != null && metadata.isDocument()) {
                var itemDisplay = metadata.asDocument().get("ItemDisplay");
                if (itemDisplay != null && itemDisplay.isDocument()) {
                    var display = itemDisplay.asDocument();
                    var name = displayText(display.get("Name"));
                    var description = displayText(display.get("Description"));
                    if (!name.isEmpty()) slot.put("Name", new BsonString(name));
                    if (!description.isEmpty()) slot.put("Description", new BsonString(description));
                }
            }
        }
        command.data = data.toJson();
    }

    private static String displayText(BsonValue value) {
        if (value == null) return "";
        if (value.isString()) return value.asString().getValue();
        if (!value.isDocument()) return "";
        var message = value.asDocument();
        var result = new StringBuilder();
        var raw = message.get("RawText");
        if (raw != null && raw.isString()) result.append(raw.asString().getValue());
        var children = message.get("Children");
        if (children != null && children.isArray())
            for (var child : children.asArray()) result.append(displayText(child));
        return result.toString();
    }
}
