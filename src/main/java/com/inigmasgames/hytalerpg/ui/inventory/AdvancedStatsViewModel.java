package com.inigmasgames.hytalerpg.ui.inventory;

import com.inigmasgames.hytalerpg.combat.attribute.DerivedStats;
import com.inigmasgames.hytalerpg.gear.GearAffixRuntime;
import com.inigmasgames.hytalerpg.gear.HytaleGearEquipment;
import com.inigmasgames.hytalerpg.gear.GearCombatEffects;
import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot;
import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.Operator;
import com.inigmasgames.hytalerpg.execution.GearResourceModifiers;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
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
        for (var channel : GearCombatEffects.Channel.values()) if(channel!=GearCombatEffects.Channel.PHYSICAL) {
            String title=channel.name().charAt(0)+channel.name().substring(1).toLowerCase(Locale.ROOT);
            double nativeValue=defense.directPercent(GearCombatEffects.nativeCause(channel)).orElse(0)/100.0;
            double value=GearCombatEffects.resistance(equipped.snapshot(),channel,nativeValue);
            model.add("resist"+title,"DEFENSE",title+" Resistance",percent(value),
                    "RPG combat resistance; capped at 75%. Environmental hazards are excluded."
                    +"\nOther sources: "+percent(nativeValue)
                    +"\nValid gear: "+percent(equipped.snapshot().percent("WA-"+String.format(Locale.ROOT,"%03d",71+channel.ordinal()))
                        +equipped.snapshot().percent("WA-078")));
        }
        model.add("health", "RESOURCES", "Maximum Health", number(sheet.health().maximum()), "Current native resource maximum");
        model.add("mana", "RESOURCES", "Maximum Mana", number(sheet.mana().maximum()), "Current native resource maximum");
        model.add("stamina", "RESOURCES", "Maximum Stamina", number(sheet.stamina().maximum()), "Current native resource maximum");
        model.add("find", "UTILITY & LOOT", "Magic Find", percent(magicFind.total()),
                "Final: " + percent(magicFind.total()) + "\nLuck: +" + percent(magicFind.luck())
                        + (magicFind.equippedGear() == 0 ? "" : "\nEquipped Gear: +" + percent(magicFind.equippedGear()))
                        + "\nIncreases the relative chance for higher-rarity eligible equipment drops. Does not increase item quantity or item level.");
        model.add("learn", "UTILITY & LOOT", "Learn Chance", percent(d.learnRate()), "Final derived learning chance");
        model.addGearModifiers(equipped.snapshot());
        return model;
    }

    /** Only actor-wide terms are shown here. Item-local and conditional terms need an action source. */
    private void addGearModifiers(GearEffectSnapshot gear) {
        var channelBuckets=GearCombatEffects.attack(gear,null,"advanced-stats/global",0,1,
                false,false,0,null,0,1,false);
        for (var channel : GearCombatEffects.Channel.values()) if (channel != GearCombatEffects.Channel.PHYSICAL) {
            String name=channel.name().charAt(0)+channel.name().substring(1).toLowerCase(Locale.ROOT);
            addPercentIfPresent("damage"+name,"ELEMENTAL DAMAGE",name+" Damage Increase",
                    channelBuckets.increased(channel),
                    "Global Increased bucket for eligible "+name+" hit damage; other action modifiers apply at execution.");
            addPercentIfPresent("penetration"+name,"ELEMENTAL DAMAGE",name+" Penetration",
                    channelBuckets.penetration(channel),"Applies to RPG "+name+" resistance for this hit.");
        }
        addOperatorPercent(gear,Operator.SPELL_DAMAGE,"spellDamage","OFFENSE","Spell Damage Increase","Tagged spell damage only");
        addOperatorPercent(gear,Operator.PHYSICAL_DAMAGE_GLOBAL,"physicalDamage","OFFENSE","Physical Damage Increase","RPG Physical hit damage only");
        addOperatorPercent(gear,Operator.DOT_DAMAGE,"dotDamage","STATUS","Damage over Time Increase","Eligible damaging status ticks");
        addOperatorPercent(gear,Operator.DAMAGING_STATUS_DURATION,"statusDuration","STATUS","Damaging Status Duration","New eligible damaging status packages");
        addOperatorPercent(gear,Operator.STATUS_PENETRATION,"statusPenetration","STATUS","Status Penetration","Eligible status admission; target resistance still applies");
        addOperatorPercent(gear,Operator.STATUS_RESISTANCE,"statusResistance","STATUS","Status Resistance","Incoming eligible RPG status admission");
        addOperatorPercent(gear,Operator.MANA_REGEN,"manaRegen","RESOURCES","Mana Regeneration Increase","Current resource regeneration rate, not a flat Mana gain");
        addOperatorPercent(gear,Operator.STAMINA_REGEN,"staminaRegen","RESOURCES","Stamina Regeneration Increase","Current resource regeneration rate, not a flat Stamina gain");
        addPercentIfPresent("manaCost","RESOURCES","Mana Cost Reduction",
                GearResourceModifiers.reduction(gear,ResourceType.MANA,false,false),"Eligible finite Mana skill payments");
        addPercentIfPresent("staminaCost","RESOURCES","Stamina Cost Reduction",
                GearResourceModifiers.reduction(gear,ResourceType.STAMINA,false,false),"Eligible Stamina skill payments");
        addPercentIfPresent("channelCost","RESOURCES","Channel Mana Cost Reduction",
                GearResourceModifiers.reduction(gear,ResourceType.MANA,true,false),"Eligible paid channel ticks; includes ordinary Mana cost reduction");
        addPercentIfPresent("summonCost","SUMMONS","Summon Mana Cost Reduction",
                GearResourceModifiers.reduction(gear,ResourceType.MANA,false,true),"Eligible finite summon payments; includes ordinary Mana cost reduction");
        addNumberIfPresent("healingPower","SUPPORT","Healing Power",gear.total(Operator.HEALING_POWER),"Flat power before eligible healing scaling");
        addOperatorPercent(gear,Operator.HEALING_DONE,"healingDone","SUPPORT","Outgoing Healing Increase","Eligible scalable healing only");
        addOperatorPercent(gear,Operator.HEALING_RECEIVED,"healingReceived","SUPPORT","Healing Received Increase","Eligible incoming healing before maximum Health clipping");
        addOperatorPercent(gear,Operator.BARRIER_STRENGTH,"barrierStrength","SUPPORT","Barrier Strength Increase","Eligible barriers; existing current barrier is not refilled");
        addOperatorPercent(gear,Operator.FINITE_SUPPORT_DURATION,"supportDuration","SUPPORT","Finite Support Duration","Eligible finite friendly effects; sustained auras excluded");
        addOperatorPercent(gear,Operator.TETHER_REACH,"tetherReach","SUPPORT","Tether Reach Increase","Eligible connection primary reach only");
        addOperatorPercent(gear,Operator.CHANNEL_RAMP,"channelRamp","SUPPORT","Channel Ramp per Paid Second","Eligible channel payload; capped by the execution owner");
        addOperatorPercent(gear,Operator.MINION_DAMAGE,"minionDamage","SUMMONS","Minion Damage Increase","Eligible combat summons; captured at creation");
        addOperatorPercent(gear,Operator.MINION_HEALTH,"minionHealth","SUMMONS","Minion Maximum Health Increase","Eligible combat summons; does not heal existing summons");
        addOperatorPercent(gear,Operator.MINION_DEFENSE,"minionDefense","SUMMONS","Minion Defense Increase","Eligible combat summons");
        addOperatorPercent(gear,Operator.MINION_RESISTANCE,"minionResistance","SUMMONS","Minion Resistance Increase","Eligible combat summons; subject to resistance caps");
        addOperatorPercent(gear,Operator.MINION_ATTACK_SPEED,"minionAttackSpeed","SUMMONS","Minion Attack Speed Increase","Eligible combat summons; attack period uses the summon owner");
        addOperatorPercent(gear,Operator.MINION_MOVEMENT,"minionMovement","SUMMONS","Minion Movement Speed Increase","Eligible combat summons; pathing and leash still apply");
        addOperatorPercent(gear,Operator.MINION_DURATION,"minionDuration","SUMMONS","Minion Duration Increase","Finite eligible summons only");
        addOperatorPercent(gear,Operator.GOLD_FIND,"currencyQuantity","UTILITY & LOOT","Currency Quantity Increase","Eligible newly credited currency rewards only");
        double pickup=gear.total(Operator.PICKUP_RADIUS);
        if (pickup != 0) add("pickupReachBonus","UTILITY & LOOT","Material Pickup Reach Bonus",
                String.format(Locale.ROOT,"+%.2f m",pickup),
                "Valid equipped gear. Metres added to the normal reach for eligible protected materials");
    }

    private void addOperatorPercent(GearEffectSnapshot gear,Operator operator,String id,String category,
                                    String label,String scope) {
        addPercentIfPresent(id,category,label,gear.percent(operator),scope);
    }
    private void addPercentIfPresent(String id,String category,String label,double value,String scope) {
        if (value != 0) add(id,category,label,signedPercent(value),"Valid equipped gear. "+scope);
    }
    private void addNumberIfPresent(String id,String category,String label,double value,String scope) {
        if (value != 0) add(id,category,label,"+"+number(value),"Valid equipped gear. "+scope);
    }

    private void add(String id, String category, String label, String value, String sources) {
        rows.add(new Row(new Descriptor(id, category, label, sources), value, sources));
    }
    List<Row> rows() { return List.copyOf(rows); }
    void trace(java.util.UUID player,CharacterSheetViewModel sheet,GearAffixRuntime.Effects equipped,
               NativeArmorDefenseView defense,HytaleGearEquipment.MagicFindBreakdown magicFind){
        if(!com.inigmasgames.hytalerpg.gear.GearQaTrace.active(player))return;
        var d=sheet.derivedStats();
        var numbers=new java.util.LinkedHashMap<String,Double>();
        numbers.put("critical",d.criticalChance());numbers.put("criticalDamage",d.criticalMultiplier());
        numbers.put("cooldown",d.cooldownRecovery());numbers.put("find",magicFind.total());
        numbers.put("health",sheet.health().maximum());numbers.put("mana",sheet.mana().maximum());
        numbers.put("stamina",sheet.stamina().maximum());
        if(equipped.castRate()!=0)numbers.put("cast",equipped.castRate());
        for(var channel:GearCombatEffects.Channel.values())if(channel!=GearCombatEffects.Channel.PHYSICAL){
            String title=channel.name().charAt(0)+channel.name().substring(1).toLowerCase(Locale.ROOT);
            numbers.put("resist"+title,GearCombatEffects.resistance(equipped.snapshot(),channel,
                    defense.directPercent(GearCombatEffects.nativeCause(channel)).orElse(0)/100.0));
        }
        var contributions=new java.util.HashMap<String,Double>();
        contributions.put("health",equipped.health());contributions.put("mana",equipped.mana());
        contributions.put("stamina",equipped.stamina());contributions.put("cooldown",equipped.cooldownRecovery());
        contributions.put("find",magicFind.equippedGear());contributions.put("cast",equipped.castRate());
        var expected=new java.util.HashMap<String,String>();
        for(var entry:numbers.entrySet())expected.put(entry.getKey(),switch(entry.getKey()){
            case "health","mana","stamina"->number(entry.getValue());
            case "cast"->signedPercent(entry.getValue());
            default->percent(entry.getValue());
        });
        var projected=new java.util.HashMap<String,String>();
        for(var row:rows)projected.put(row.descriptor().id(),row.value());
        com.inigmasgames.hytalerpg.gear.GearQaTrace.advanced(player,numbers,projected,contributions,expected);
    }
    String sources(String id) {
        return rows.stream().filter(row -> row.descriptor().id().equals(id))
                .map(row -> row.descriptor().label() + "  " + row.value() + "\n" + row.sources())
                .findFirst().orElse("");
    }
    private static String percent(double fraction) { return String.format(Locale.ROOT, "%.1f%%", fraction * 100); }
    private static String signedPercent(double fraction) { return String.format(Locale.ROOT, "%+.1f%%", fraction * 100); }
    private static String numericPercent(double value) { return String.format(Locale.ROOT, "%.1f%%", value); }
    private static String number(double value) { return Math.abs(value-Math.rint(value)) < .0001
            ? Long.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value); }
}
