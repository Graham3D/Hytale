package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.inigmasgames.hytalerpg.gear.GearNativeItems;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Packaged templates and object-codec anchors, matching the client-tested CanvasUI path. */
final class InventoryProbeBag {
    private static final int PITCH = InventoryGridGeometry.PITCH;
    // Bounded visual test only. The native grid/cursor and inventory owner are unchanged.
    private static final Set<String> POSE_BATCH = Set.of(
            "Armor_Leather_Light_Chest", "Armor_Leather_Light_Hands",
            "Armor_Leather_Light_Head", "Armor_Leather_Light_Legs",
            "Weapon_Sword_Iron", "Weapon_Daggers_Iron", "Weapon_Shortbow_Iron",
            "Weapon_Shortbow_Copper", "Weapon_Crossbow_Iron", "Weapon_Battleaxe_Iron",
            "Weapon_Mace_Iron", "Weapon_Longsword_Iron", "Weapon_Spear_Iron",
            "Weapon_Shield_Iron", "Weapon_Staff_Iron", "Weapon_Wand_Wood",
            "Weapon_Spellbook_Fire", "Weapon_Assault_Rifle",
            "Weapon_Gun_Blunderbuss", "Weapon_Bomb_Fire");
    private final Map<String, String> selectors = new HashMap<>();
    private int childIndex;

    void reset(UICommandBuilder commands) {
        selectors.clear(); childIndex = 0;
        commands.clear("#Bag");
        for (int y = 0; y < InventoryGridGeometry.ROWS; y++)
            for (int x = 0; x < InventoryGridGeometry.COLUMNS; x++) {
            String selector = append(commands, "RpgInventoryProbeCell.ui");
            commands.setObject(selector + ".Anchor", anchor(x * PITCH, y * PITCH,
                    InventoryGridGeometry.SLOT, InventoryGridGeometry.SLOT));
        }
    }

    String nativeGrid(UICommandBuilder commands, int sectionId) {
        commands.append("#Bag", "InventoryNativeWorkspace/Section" + sectionId + ".ui");
        childIndex++;
        // The appended document's root is the named ItemGrid itself. Looking for
        // that same name *under* #Bag[72] misses it and disconnects the client.
        return "#WorkspaceNativeGrid";
    }

    void item(UICommandBuilder commands, SpatialLayout.Entry entry, String itemId, int quantity) {
        item(commands, entry, itemId, quantity, null);
    }

    void item(UICommandBuilder commands, SpatialLayout.Entry entry, ItemStack stack) {
        item(commands, entry, stack.getItemId(), stack.getQuantity(), stack);
    }

    private void item(UICommandBuilder commands, SpatialLayout.Entry entry,
                      String itemId, int quantity, ItemStack stack) {
        String selector = append(commands, "RpgInventoryProbeItem.ui");
        selectors.put(entry.id(), selector);
        move(commands, entry);
        int width = entry.size().width() * PITCH - InventoryGridGeometry.SPACING;
        int height = entry.size().height() * PITCH - InventoryGridGeometry.SPACING;
        // ItemIcon scales to its anchor. Keep that anchor square so a square
        // source canvas cannot be stretched to a tall spatial footprint.
        // The rarity and selection layers still cover the entire footprint.
        int iconSide = Math.min(width, height) - 16;
        commands.setObject(selector + " #Icon.Anchor", anchor((width - iconSide) / 2,
                (height - iconSide) / 2, iconSide, iconSide));
        commands.set(selector + " #Icon.ItemId", itemId);
        String nativeId = GearNativeItems.nativeId(itemId);
        if (POSE_BATCH.contains(nativeId)) {
            commands.setObject(selector + " #PoseArt.Background", new PatchStyle()
                    .setTexturePath(Value.of("Icons/RPG/PoseBatch/" + nativeId + ".png")));
            commands.set(selector + " #PoseArt.Visible", true);
            commands.set(selector + " #Icon.Visible", false);
        }
        commands.set(selector + " #Quantity.Visible", quantity > 1);
        if (quantity > 1) commands.set(selector + " #Quantity.Text", Integer.toString(quantity));
        if (stack != null) {
            String color = qualityColor(stack);
            commands.setObject(selector + ".Background", new PatchStyle().setColor(Value.of(color + "cc")));
            commands.setObject(selector + " #RarityFill.Background",
                    new PatchStyle().setColor(Value.of(color + "26")));
            String rarityArt = rarityArt(stack, entry.size());
            if (rarityArt != null) {
                commands.setObject(selector + " #RarityArt.Background", new PatchStyle()
                        .setTexturePath(Value.of(rarityArt)).setColor(Value.of("#0049a2")));
                commands.set(selector + " #RarityArt.Visible", true);
            }
        }
    }

    private static String rarityArt(ItemStack stack, SpatialLayout.Size size) {
        try {
            var quality = ItemQuality.getAssetMap().getAsset(stack.getQualityIndex());
            return quality == null ? null : rarityArtForQuality(quality.getId(), size);
        } catch (RuntimeException unavailable) { return null; }
    }

    static String rarityArtForQuality(String qualityId, SpatialLayout.Size size) {
        if (!"RPG_Gear_Rare".equals(qualityId)) return null; // Hywind's Magic quality asset.
        if (size.width() == 1 && size.height() == 1) return "Icons/RPG/GridRarity-1x1.png";
        if (size.width() == 2 && size.height() == 2) return "Icons/RPG/GridRarity-2x2.png";
        return null; // Never stretch an authored tile across an unauthored footprint.
    }

    static String qualityColor(ItemStack stack) {
        try {
            var quality = ItemQuality.getAssetMap().getAsset(stack.getQualityIndex());
            if (quality != null && quality.getTextColor() != null) {
                var value = quality.getTextColor();
                return String.format(java.util.Locale.ROOT, "#%02x%02x%02x",
                        Byte.toUnsignedInt(value.red), Byte.toUnsignedInt(value.green), Byte.toUnsignedInt(value.blue));
            }
        } catch (RuntimeException unavailable) { /* Neutral frame until qualities are loaded. */ }
        return "#58728a";
    }

    String hit(UICommandBuilder commands, int x, int y) {
        String selector = append(commands, "RpgInventoryProbeHit.ui");
        commands.setObject(selector + ".Anchor", anchor(x * PITCH, y * PITCH,
                InventoryGridGeometry.SLOT, InventoryGridGeometry.SLOT));
        return selector;
    }

    void move(UICommandBuilder commands, SpatialLayout.Entry entry) {
        commands.setObject(selectors.get(entry.id()) + ".Anchor", anchor(entry.position().x() * PITCH,
                entry.position().y() * PITCH,
                entry.size().width() * PITCH - InventoryGridGeometry.SPACING,
                entry.size().height() * PITCH - InventoryGridGeometry.SPACING));
    }

    void dim(UICommandBuilder commands, String id, boolean dimmed) {
        commands.set(selectors.get(id) + " #Dim.Visible", dimmed);
    }

    void select(UICommandBuilder commands, String id, boolean selected) {
        String selector = selectors.get(id);
        if (selector != null) commands.set(selector + " #Selected.Visible", selected);
    }

    private String append(UICommandBuilder commands, String document) {
        commands.append("#Bag", document);
        return "#Bag[" + childIndex++ + "]";
    }

    private static Anchor anchor(int left, int top, int width, int height) {
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(left)); anchor.setTop(Value.of(top));
        anchor.setWidth(Value.of(width)); anchor.setHeight(Value.of(height));
        return anchor;
    }
}
