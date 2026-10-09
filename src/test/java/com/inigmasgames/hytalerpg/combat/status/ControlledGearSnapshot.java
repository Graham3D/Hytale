package com.inigmasgames.hytalerpg.combat.status;

import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Legal carrier, tier, side, gate and rarity fixture. Construction does not prove native equip validity. */
public final class ControlledGearSnapshot {
    private ControlledGearSnapshot() { }
    public static GearEffectSnapshot with(String... ids) {
        return on("gm.battleaxe_adamantite.h","M",ids);
    }
    public static GearEffectSnapshot focus(String... ids) {
        return on("gm.staff_flame.h","C",ids);
    }
    private static GearEffectSnapshot on(String baseId,String family,String... ids) {
        if(ids.length==0)return GearEffectSnapshot.EMPTY;
        var catalog=GearCatalog.load();var base=catalog.base(baseId);
        int prefixes=0;var rolls=new ArrayList<GearInstance.AffixRoll>();
        for(String id:ids) {
            var a=catalog.affix(id);
            if(!a.eligibility().equals("ALL") && !a.eligibility().contains(family))
                throw new IllegalArgumentException("ILLEGAL_CONTROLLED_CARRIER_"+id);
            var tier=GearAffixTiers.compile(a).getLast();
            double value=switch(id) {
                case "WA-013" -> 30;case "WA-065","WA-066","WA-067" -> 24;
                case "WA-056" -> 20;
                case "WA-068" -> 25;case "WA-079" -> 12;case "WA-064" -> 10;
                case "WA-080" -> 15;case "WA-081" -> 20;
                case "WA-094" -> 3;case "WA-095","WA-097","WA-098" -> 2;
                case "WA-096" -> 4;default -> tier.high();
            };
            if(value<tier.low()||value>tier.high())throw new IllegalArgumentException("ILLEGAL_CONTROLLED_ROLL_"+id);
            var gate=new GearRequirements.Gate(tier.requiredLevel(),Map.of(a.requirementAttribute(base),
                    a.attributeFloor(5-tier.tier(),tier.minimumItemLevel())));
            rolls.add(new GearInstance.AffixRoll(id,a.side(),a.exclusionGroup(),tier.tier(),value,gate,
                    "controlled affix",a.name()));
            if(a.side()==GearCatalog.Side.PREFIX)prefixes++;
        }
        int suffixes=ids.length-prefixes;
        GearRarity rarity=ids.length==1?GearRarity.MAGIC:ids.length<=3?GearRarity.RARE:
                ids.length<=5?GearRarity.VERY_RARE:GearRarity.LEGENDARY;
        if(!rarity.legalNewBudget(prefixes,suffixes))throw new IllegalArgumentException("ILLEGAL_CONTROLLED_BUDGET");
        var item=GearInstance.authoredQa(base,UUID.randomUUID(),family.equals("C")?90:95,1000,rarity,rolls,BigDecimal.ZERO);
        return new GearEffectSnapshot(List.of(item));
    }
}
