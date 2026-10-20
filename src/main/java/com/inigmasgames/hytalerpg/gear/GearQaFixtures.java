package com.inigmasgames.hytalerpg.gear;

import java.math.BigDecimal;
import java.util.*;

/** Fixed, visibly QA-authored affix sets for definition/requirements/presentation testing, never drop generation. */
public final class GearQaFixtures {
    private GearQaFixtures() {}
    public static GearInstance create(GearCatalog catalog,GearCatalog.Base base,UUID identity,int level,int intrinsic,GearRarity rarity) {
        var ids=new ArrayList<String>();
        boolean armor=base.category()==GearCatalog.Category.ARMOR;
        switch(rarity) {
            case COMMON,NORMAL -> {}
            case UNCOMMON,MAGIC -> ids.add(armor?"GA-159":"WA-001");
            case RARE -> { ids.add(armor?"GA-159":"WA-001");ids.add(armor?"GA-160":"WA-002");ids.add("WA-151"); }
            case VERY_RARE -> ids.addAll(armor?List.of("GA-159","GA-160","WA-085","WA-151"):List.of("WA-001","WA-002","WA-085","WA-151"));
            case LEGENDARY -> ids.addAll(armor?List.of("GA-159","GA-160","WA-091","WA-085","WA-154","WA-151"):List.of("WA-001","WA-002","WA-091","WA-085","WA-151","WA-154"));
        }
        if(base.category()==GearCatalog.Category.TOOL && !ids.isEmpty()) throw new IllegalArgumentException("Utility tools are Common only");
        var rolls=new ArrayList<GearInstance.AffixRoll>();BigDecimal reduction=BigDecimal.ZERO;
        for(var id:ids) {
            var affix=catalog.affix(id);var tier=GearAffixTiers.compile(affix).getFirst();
            if(level<tier.minimumItemLevel() || !GearAffixTiers.rarityAllows(affix,tier,rarity))
                throw new IllegalArgumentException("QA fixture affix is not eligible at this source level: "+id);
            double value=tier.low();
            if(armor) {
                double factor=id.equals("GA-159")?switch(base.slot()){case HEAD->.20/.36;case HANDS->.16/.36;case LEGS->.28/.36;default->1;}:
                        id.equals("WA-091")?(base.slot()==GearCatalog.Slot.CHEST || base.slot()==GearCatalog.Slot.LEGS?.50:.35):affix.operator().equals("ATTRIBUTE")?.5:1;
                // Slot scaling precedes quantization; use the authored unquantized lower endpoint.
                value=BigDecimal.valueOf(affix.topRange().getFirst()).multiply(new BigDecimal("0.40")).multiply(BigDecimal.valueOf(factor))
                        .divide(BigDecimal.valueOf(tier.grid()),0,java.math.RoundingMode.CEILING).multiply(BigDecimal.valueOf(tier.grid())).doubleValue();
            }
            var gate=new GearRequirements.Gate(tier.requiredLevel(),Map.of(affix.requirementAttribute(base),affix.attributeFloor(0,0)));
            String description=affix.effectContract().split(" Require:")[0].replaceAll("\\bV\\b",Double.toString(value));
            rolls.add(new GearInstance.AffixRoll(id,affix.side(),affix.exclusionGroup(),tier.tier(),value,gate,description,affix.name()));
            if(id.equals("WA-154")) reduction=BigDecimal.valueOf(value).movePointLeft(2);
        }
        return GearInstance.authoredQa(base,identity,level,intrinsic,rarity,rolls,reduction);
    }
}
