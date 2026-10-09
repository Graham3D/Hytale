package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.inigmasgames.hytalerpg.gear.GearNativeItems;
import com.inigmasgames.hytalerpg.gear.GearTooltip;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** View-only rendering. Inventory owns hover identity and lifetime. */
final class ManagedGearTooltip {
    static final int WIDTH = 400;
    private static final int WORKSPACE_WIDTH = 1164;
    private static final int WORKSPACE_HEIGHT = 900;
    private static final String ROOT = "#ManagedGearTooltip";

    record Rect(int x, int y, int width, int height) { }
    record Point(int x, int y) { }
    record Model(String name, String rarity, String band, String classification,
                 String damage, List<String> base, List<String> affixes,
                 List<GearTooltip.Line> requirements, String durability, String flavor, int height) { }

    static Rect bagRect(SpatialLayout.Entry entry) {
        // The same authored workspace offsets and pitch used by #Bag and its
        // item visual. The entry supplies the complete multi-cell bounds.
        final int gridLeft = 12;
        final int gridTop = 48 + 12 + 430 + 16 + 38 + 12;
        return new Rect(gridLeft + entry.position().x() * InventoryGridGeometry.PITCH,
                gridTop + entry.position().y() * InventoryGridGeometry.PITCH,
                entry.size().width() * InventoryGridGeometry.PITCH - InventoryGridGeometry.SPACING,
                entry.size().height() * InventoryGridGeometry.PITCH - InventoryGridGeometry.SPACING);
    }

    static Model model(GearNativeItems.TooltipView view) {
        var lines = view.lines();
        String classification = lines.size() > 1 ? lines.get(1).text() : "";
        String damage = "", flavor = "";
        var base = new ArrayList<String>();
        var affixes = new ArrayList<String>();
        var requirements = new ArrayList<GearTooltip.Line>();
        int section = 0;
        for (int i = 2; i < lines.size(); i++) {
            var line = lines.get(i);
            if (line.style() == GearTooltip.Style.DIVIDER) { section++; continue; }
            if (line.style() == GearTooltip.Style.FLAVOR) { flavor = line.text(); continue; }
            if (line.style() == GearTooltip.Style.DAMAGE) { damage = line.text(); continue; }
            if (line.style() == GearTooltip.Style.HEADING) continue;
            if (line.style() == GearTooltip.Style.AFFIX) affixes.add(line.text());
            else if (line.text().startsWith("Requires ") || line.style() == GearTooltip.Style.ERROR)
                requirements.add(line);
            else if (section >= 1) base.add(line.text());
        }
        String durability = view.maxDurability() > 0
                ? "Durability: " + number(view.durability()) + "/" + number(view.maxDurability()) : "";
        int height = height(damage, base, affixes, requirements, flavor);
        String band = view.gear().rarity().presentation ==
                com.inigmasgames.hytalerpg.gear.GearRarityPresentation.NORMAL
                ? "Common" : view.gear().rarity().presentation.label;
        return new Model(view.gear().displayName(), view.gear().rarity().label, band,
                classification, damage, List.copyOf(base), List.copyOf(affixes),
                List.copyOf(requirements), durability, flavor, height);
    }

    static Model nativeModel(String name, String rarity, String band, String classification,
                             List<String> description, double durability, double maxDurability) {
        var body = description.stream().filter(line -> !line.isBlank()).toList();
        String wear = maxDurability > 0 ? "Durability: " + number(durability) + "/" + number(maxDurability) : "";
        return new Model(name, rarity, band, classification, "", body, List.of(), List.of(), wear,
                "", height("", body, List.of(), List.of(), ""));
    }

    private static int height(String damage, List<String> base, List<String> affixes,
                              List<GearTooltip.Line> requirements, String flavor) {
        int baseHeight = (damage.isEmpty() ? 0 : 56) + rowsHeight(base);
        int affixHeight = rowsHeight(affixes);
        int lowerHeight = Math.max(24, rowsHeight(requirements.stream().map(GearTooltip.Line::text).toList()));
        return 44 + 70 + 14 + baseHeight + (baseHeight > 0 ? 14 : 0)
                + affixHeight + (affixHeight > 0 ? 14 : 0) + lowerHeight
                + (flavor.isBlank() ? 0 : 14 + rowHeight(flavor));
    }

    /** Center on the complete rendered footprint; vertical anchor is its top edge. */
    static Point place(Rect slot, int tooltipWidth, int tooltipHeight, int viewportWidth, int viewportHeight) {
        int gap = 10, edge = 8;
        int centered = slot.x() + (slot.width() - tooltipWidth) / 2;
        int x = Math.max(edge, Math.min(centered, viewportWidth - tooltipWidth - edge));
        int above = slot.y() - tooltipHeight - gap;
        int below = slot.y() + slot.height() + gap;
        int y = above >= edge ? above : below + tooltipHeight <= viewportHeight - edge
                ? below : Math.max(edge, Math.min(above, viewportHeight - tooltipHeight - edge));
        return new Point(x, y);
    }

    static void hide(UICommandBuilder commands) {
        commands.set("#ManagedTooltipHost " + ROOT + ".Visible", false);
        commands.set("#ComparisonTooltipHost " + ROOT + ".Visible", false);
    }

    static void show(UICommandBuilder commands, Model model, Rect hoveredSlot) {
        commands.set("#ComparisonTooltipHost " + ROOT + ".Visible", false);
        render(commands, model, hoveredSlot, "#ManagedTooltipHost", null);
    }

    static Point pairPosition(Rect hovered, int height) {
        return place(hovered, WIDTH * 2 + 12, height, WORKSPACE_WIDTH, WORKSPACE_HEIGHT);
    }

    static void compare(UICommandBuilder commands, Model hovered, Model equipped, Rect slot) {
        var position = pairPosition(slot, Math.max(hovered.height(), equipped.height()));
        render(commands, hovered, slot, "#ManagedTooltipHost", position);
        render(commands, equipped, slot, "#ComparisonTooltipHost", new Point(position.x() + WIDTH + 12, position.y()));
    }

    private static void render(UICommandBuilder commands, Model model, Rect hoveredSlot, String host, Point override) {
        int firstCommand = commands.getCommands().length;
        // Coordinates come from the same spatial layout and slot pitch that place
        // the visible item art, in the centered workspace's own coordinate space.
        var position = override == null ? place(hoveredSlot, WIDTH, model.height(), WORKSPACE_WIDTH, WORKSPACE_HEIGHT) : override;
        var popupAnchor = new Anchor();
        // A right anchor follows the actual viewport edge if the game is narrower
        // than the authored 1164px workspace, without guessing a screen pixel X.
        if (position.x() > WORKSPACE_WIDTH / 2)
            popupAnchor.setRight(Value.of(WORKSPACE_WIDTH - position.x() - WIDTH));
        else popupAnchor.setLeft(Value.of(position.x()));
        popupAnchor.setTop(Value.of(position.y()));
        popupAnchor.setWidth(Value.of(WIDTH)); popupAnchor.setHeight(Value.of(model.height()));
        commands.setObject(ROOT + ".Anchor", popupAnchor);
        for (String band : List.of("Common", "Rare", "Epic", "Legendary")) {
            String title = "#Title" + band;
            boolean selected = band.equals(model.band());
            commands.set(title + ".Visible", selected);
            if (selected) {
                commands.set(title + " #Name.Text", model.name());
                commands.set(title + " #Rarity.Text", model.rarity());
            }
        }
        for (String band : List.of("Rare", "Epic"))
            commands.set("#Accent" + band + ".Visible", band.equals(model.band()));
        commands.set("#TooltipClassification.Text", model.classification());
        boolean hasBase = !model.damage().isBlank() || !model.base().isEmpty();
        commands.set("#TooltipDamageHeading.Visible", !model.damage().isBlank());
        commands.set("#TooltipDamageRange.Visible", !model.damage().isBlank());
        commands.set("#TooltipDamageRange.Text", model.damage());
        anchorHeight(commands, "#TooltipDamageHeading", model.damage().isBlank() ? 0 : 22);
        anchorHeight(commands, "#TooltipDamageRange", model.damage().isBlank() ? 0 : 34);
        appendRows(commands, "#TooltipBaseStats", model.base(), "RpgGearTooltipStat.ui");
        anchorHeight(commands, "#TooltipBaseStats", rowsHeight(model.base()));
        anchorHeight(commands, "#TooltipBase", (model.damage().isBlank() ? 0 : 56) + rowsHeight(model.base()));
        commands.set("#TooltipDividerBase.Visible", hasBase);
        anchorHeight(commands, "#TooltipDividerBase", hasBase ? 14 : 0);
        appendRows(commands, "#TooltipAffixes", model.affixes(), "RpgGearTooltipAffix.ui");
        anchorHeight(commands, "#TooltipAffixes", rowsHeight(model.affixes()));
        commands.set("#TooltipDividerAffix.Visible", !model.affixes().isEmpty());
        anchorHeight(commands, "#TooltipDividerAffix", model.affixes().isEmpty() ? 0 : 14);
        commands.clear("#TooltipRequirements");
        for (int i = 0; i < model.requirements().size(); i++) {
            var line = model.requirements().get(i);
            commands.append("#TooltipRequirements", line.style() == GearTooltip.Style.ERROR
                    ? "RpgGearTooltipRequirementFailed.ui" : "RpgGearTooltipRequirementMet.ui");
            String row = "#TooltipRequirements[" + i + "]";
            commands.set(row + " #Text.Text", line.text());
            anchorHeight(commands, row, rowHeight(line.text()));
        }
        int lowerHeight = Math.max(24, rowsHeight(model.requirements().stream().map(GearTooltip.Line::text).toList()));
        anchorWidthHeight(commands, "#TooltipRequirements", 230, lowerHeight);
        anchorHeight(commands, "#TooltipLower", lowerHeight);
        commands.set("#TooltipDurability.Text", model.durability());
        boolean flavored = !model.flavor().isBlank();
        commands.set("#TooltipFlavorSection.Visible", flavored);
        commands.set("#TooltipFlavor.Text", model.flavor());
        anchorHeight(commands, "#TooltipFlavor", flavored ? rowHeight(model.flavor()) : 0);
        anchorHeight(commands, "#TooltipFlavorSection", flavored ? 14 + rowHeight(model.flavor()) : 0);
        commands.set(ROOT + ".Visible", true);
        var all = commands.getCommands();
        for (int i = firstCommand; i < all.length; i++)
            all[i].selector = host + " " + all[i].selector;

    }

    private static void appendRows(UICommandBuilder commands, String parent, List<String> lines, String document) {
        commands.clear(parent);
        for (int i = 0; i < lines.size(); i++) {
            commands.append(parent, document);
            String row = parent + "[" + i + "]";
            commands.set(row + " #Text.Text", lines.get(i));
            anchorHeight(commands, row, rowHeight(lines.get(i)));
        }
    }
    private static int rowHeight(String text) { return 24 * Math.max(1, (text.length() + 43) / 44); }
    private static int rowsHeight(List<String> lines) { return lines.stream().mapToInt(ManagedGearTooltip::rowHeight).sum(); }
    private static String number(double value) {
        return Math.abs(value - Math.rint(value)) < 0.0001 ? Long.toString(Math.round(value))
                : String.format(Locale.ROOT, "%.1f", value);
    }
    private static void anchorHeight(UICommandBuilder commands, String selector, int height) {
        var anchor = new Anchor(); anchor.setHeight(Value.of(height));
        commands.setObject(selector + ".Anchor", anchor);
    }
    private static void anchorWidthHeight(UICommandBuilder commands, String selector, int width, int height) {
        var anchor = new Anchor(); anchor.setWidth(Value.of(width)); anchor.setHeight(Value.of(height));
        commands.setObject(selector + ".Anchor", anchor);
    }
}
