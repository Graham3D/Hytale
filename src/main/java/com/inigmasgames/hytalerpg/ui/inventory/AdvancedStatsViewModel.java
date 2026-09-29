package com.inigmasgames.hytalerpg.ui.inventory;

import com.inigmasgames.hytalerpg.combat.attribute.DerivedStats;
import com.inigmasgames.hytalerpg.gear.GearAffixRuntime;
import com.inigmasgames.hytalerpg.gear.HytaleGearEquipment;
import com.inigmasgames.hytalerpg.ui.model.CharacterSheetViewModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

/** Player-facing descriptors over the same resolved values used by the compact character sheet. */
final class AdvancedStatsViewModel {
    record Descriptor(String id, String category, String label, String help) { }
    record Row(Descriptor descriptor, String value, String sources) { }
    private final List<Row> rows = new ArrayList<>();

    static AdvancedStatsViewModel resolve(CharacterSheetViewModel sheet,
                                          GearAffixRuntime.Effects equipped,
                                          NativeArmorDefenseView defense,
                                          HytaleGearEquipment.MagicFindBreakdown magicFind) {
        var model = new AdvancedStatsViewModel();
        DerivedStats d = sheet.derivedStats();
        model.add("heavy", "OFFENSE", "Heavy Damage", signedPercent(d.heavyDamageMultiplier() - 1), "Final derived damage modifier");
        model.add("light", "OFFENSE", "Light Damage", signedPercent(d.lightDamageMultiplier() - 1), "Final derived damage modifier");
        model.add("magic", "OFFENSE", "Magic Damage", signedPercent(d.magicDamageMultiplier() - 1), "Final derived damage modifier");
        model.add("healing", "OFFENSE", "Healing", signedPercent(d.healingMultiplier() - 1), "Final derived healing modifier");
        model.add("critical", "OFFENSE", "Critical Hit Chance", percent(d.criticalChance()), "Final derived critical chance");
        model.add("criticalDamage", "OFFENSE", "Critical Damage", percent(d.criticalMultiplier()), "Final derived critical multiplier");
        model.add("cooldown", "OFFENSE", "Skill Cooldown Recovery", percent(d.cooldownRecovery()), "Final accepted recovery rate, including valid equipped gear");
        if (equipped.castRate() != 0)
            model.add("cast", "OFFENSE", "Faster Cast Rate", signedPercent(equipped.castRate()), "Valid equipped gear");
        OptionalDouble physical = defense.directPercent("Physical");
        if (physical.isPresent()) model.add("defense", "DEFENSE", "Physical Protection", numericPercent(physical.getAsDouble()), "Native armor and active effects");
        for (String[] channel : new String[][]{{"Wind","Wind"}, {"Water","Ice"}, {"Fire","Fire"},
                {"Earth","Poison"}, {"Lightning","Lightning"}, {"Void","RPG_Void"}}) {
            var value = defense.directPercent(channel[1]);
            if (value.isPresent()) model.add("resist" + channel[0], "DEFENSE", channel[0] + " Resistance",
                    numericPercent(value.getAsDouble()),
                    "Native armor and active effects; no separate RPG resistance cap is applied by this owner");
        }
        model.add("health", "RESOURCES", "Maximum Health", number(sheet.health().maximum()), "Current native resource maximum");
        model.add("mana", "RESOURCES", "Maximum Mana", number(sheet.mana().maximum()), "Current native resource maximum");
        model.add("stamina", "RESOURCES", "Maximum Stamina", number(sheet.stamina().maximum()), "Current native resource maximum");
        model.add("find", "UTILITY & LOOT", "Magic Find", percent(magicFind.total()),
                "Final: " + percent(magicFind.total()) + "\nLuck: +" + percent(magicFind.luck())
                        + (magicFind.equippedGear() == 0 ? "" : "\nEquipped Gear: +" + percent(magicFind.equippedGear()))
                        + "\nIncreases the relative chance for higher-rarity eligible equipment drops. Does not increase item quantity or item level.");
        model.add("learn", "UTILITY & LOOT", "Learn Chance", percent(d.learnRate()), "Final derived learning chance");
        if (equipped.allSkillRanks() != 0)
            model.add("skillRanks", "SKILL MODIFIERS", "All Active Skills", signedNumber(equipped.allSkillRanks()), "Valid equipped gear");
        return model;
    }

    private void add(String id, String category, String label, String value, String sources) {
        rows.add(new Row(new Descriptor(id, category, label, sources), value, sources));
    }
    List<Row> rows() { return List.copyOf(rows); }
    String sources(String id) {
        return rows.stream().filter(row -> row.descriptor().id().equals(id))
                .map(row -> row.descriptor().label() + "  " + row.value() + "\n" + row.sources())
                .findFirst().orElse("");
    }
    private static String percent(double fraction) { return String.format(Locale.ROOT, "%.1f%%", fraction * 100); }
    private static String signedPercent(double fraction) { return String.format(Locale.ROOT, "%+.1f%%", fraction * 100); }
    private static String numericPercent(double value) { return String.format(Locale.ROOT, "%.1f%%", value); }
    private static String signedNumber(int value) { return String.format(Locale.ROOT, "%+d", value); }
    private static String number(double value) { return Math.abs(value-Math.rint(value)) < .0001
            ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value); }
}
