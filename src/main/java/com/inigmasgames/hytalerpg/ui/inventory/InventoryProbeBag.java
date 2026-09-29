package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.inigmasgames.hytalerpg.gear.GearNativeItems;
import com.inigmasgames.hytalerpg.gear.GearRarity;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Packaged templates and object-codec anchors, matching the client-tested CanvasUI path. */
final class InventoryProbeBag {
    private static final int PITCH = InventoryGridGeometry.PITCH;
    // Each prototype render is documented in
    // art/spatial-icon-prototype/pose-batch-output/manifest.json. Other items
    // continue to use their shipped Hytale ItemIcon; never mix an untracked PNG.
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
    }

    void cells(UICommandBuilder commands) {
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
        // Pose and rarity canvases were authored at 64 px per logical cell.
        // Fit them uniformly into the 74 px native slots without stretching.
        double scale = Math.min(width / (entry.size().width() * 64.0),
                height / (entry.size().height() * 64.0));
        int artWidth = (int) Math.floor(entry.size().width() * 64 * scale);
        int artHeight = (int) Math.floor(entry.size().height() * 64 * scale);
        Anchor artAnchor = anchor((width - artWidth) / 2, (height - artHeight) / 2,
                artWidth, artHeight);
        commands.setObject(selector + " #PoseArt.Anchor", artAnchor);
        commands.setObject(selector + " #RarityArt.Anchor", artAnchor);
        commands.setObject(selector + " #RarityGlow.Anchor", artAnchor);
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
            String color = rarityColor(stack);
            String rarityArt = color == null ? null : rarityArtForSize(entry.size());
            if (rarityArt != null) {
                commands.setObject(selector + " #RarityArt.Background", new PatchStyle()
                        .setTexturePath(Value.of(rarityArt)).setColor(Value.of(color + "88")));
                commands.set(selector + " #RarityArt.Visible", true);
                // CustomUI has tint/alpha but no exposed additive or screen blend.
                // A second, faint pass through the same unmodified footprint art
                // lifts its highlight and border without replacing native cells.
                commands.setObject(selector + " #RarityGlow.Background", new PatchStyle()
                        .setTexturePath(Value.of(rarityArt)).setColor(Value.of("#ffffff26")));
                commands.set(selector + " #RarityGlow.Visible", true);
            }
        }
    }

    private static String rarityColor(ItemStack stack) {
        try {
            var managed = GearNativeItems.read(stack);
            if (managed != null) return switch (managed.rarity()) {
                case COMMON, NORMAL -> null;
                case UNCOMMON, MAGIC -> "#1d4dff";
                case RARE, VERY_RARE -> "#a000ff";
                case LEGENDARY -> "#ff9100";
            };
        } catch (RuntimeException invalidManagedItem) { return null; }
        try {
            var quality = ItemQuality.getAssetMap().getAsset(stack.getQualityIndex());
            return quality == null ? null : rarityColorForQuality(quality.getId());
        } catch (RuntimeException unavailable) { return null; }
    }

    static String rarityArtForQuality(String qualityId, SpatialLayout.Size size) {
        return rarityColorForQuality(qualityId) == null ? null : rarityArtForSize(size);
    }

    static String rarityColorForQuality(String qualityId) {
        return switch (qualityId) {
            case "RPG_Gear_Rare" -> "#1d4dff";
            case "RPG_Gear_RandomRare", "RPG_Gear_Epic" -> "#a000ff";
            case "RPG_Gear_Set" -> "#51c534";
            case "RPG_Gear_Legendary", "RPG_Gear_Unique" -> "#ff9100";
            default -> null;
        };
    }

    static String rarityArtForSize(SpatialLayout.Size size) {
        String footprint = size.width() + "x" + size.height();
        if (!Set.of("1x1", "1x2", "1x3", "1x4", "2x2", "2x3", "2x4").contains(footprint)) return null;
        return "Icons/RPG/GridRarity-" + footprint + ".png";
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
