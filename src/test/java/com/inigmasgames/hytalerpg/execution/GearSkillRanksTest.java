package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GearSkillRanksTest {
    private static GearInstance item(String id,double value,int level,GearRarity rarity){
        var catalog=GearCatalog.load();var a=catalog.affix(id);
        var base=catalog.base(id.equals("WA-129")||id.equals("WA-132")?"gm.sword_adamantite.h":
                level<51?"gm.staff_prismatic.n":level<60?"gm.staff_prismatic.nm":
                        level<77?"gm.staff_healing.h":"gm.staff_prismatic.h");
        var tier=GearAffixTiers.compile(a).getFirst();
        var roll=new GearInstance.AffixRoll(id,a.side(),a.exclusionGroup(),tier.tier(),value,
                new GearRequirements.Gate(tier.requiredLevel(),Map.of(a.requirementAttribute(base),
                        a.attributeFloor(0,tier.minimumItemLevel()))),a.name(),a.name(),id.equals("WA-122")?"wind_cutter":null);
        var rolls=new ArrayList<GearInstance.AffixRoll>();rolls.add(roll);
        if(rarity==GearRarity.RARE){var filler=catalog.affix("WA-009");var ft=GearAffixTiers.compile(filler).getLast();rolls.add(new GearInstance.AffixRoll(
                filler.id(),filler.side(),filler.exclusionGroup(),ft.tier(),15,
                new GearRequirements.Gate(ft.requiredLevel(),Map.of(filler.requirementAttribute(base),
                        filler.attributeFloor(5-ft.tier(),ft.minimumItemLevel()))),filler.name(),filler.name()));}
        return GearInstance.authoredQa(base,UUID.randomUUID(),level,1000,
                rarity,rolls,BigDecimal.ZERO);
    }
    @Test void explicitSchoolSelectorAffectsOnlyMatchingLearnedExecutionProfile(){
        var profiles=Stage04SkillProfiles.loadCanonical(com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical());
        var wind=profiles.require("wind_cutter");var fire=profiles.require("fire_bolt");
        // A controlled single-roll snapshot proves this consumer; the native equip gate is separate.
        var gear=new GearEffectSnapshot(List.of(item("WA-123",1,95,GearRarity.MAGIC)));
        // Magic is ineligible for a family skiller even though the frozen item can be decoded.
        assertEquals(0,GearSkillRanks.bonus(gear,wind));
        assertEquals(0,GearSkillRanks.bonus(gear,fire));
        assertEquals(0,GearSkillRanks.bonus(GearEffectSnapshot.EMPTY,wind));
        var eligible=new GearEffectSnapshot(List.of(item("WA-123",1,95,GearRarity.RARE)));
        assertEquals(1,GearSkillRanks.bonus(eligible,wind));
        assertEquals(0,GearSkillRanks.bonus(eligible,fire));
        assertEquals(6,EffectiveSkillLevel.resolveBase(5,GearSkillRanks.bonus(eligible,wind)));
        var named=new GearEffectSnapshot(List.of(item("WA-122",1,95,GearRarity.MAGIC)));
        assertEquals(1,GearSkillRanks.bonus(named,wind));
        assertEquals(0,GearSkillRanks.bonus(named,fire));
    }
    @Test void everyFamilySelectorHasAnAuthoredPositiveAndExcludedProfile(){
        var profiles=Stage04SkillProfiles.loadCanonical(com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical());
        var cases=Map.ofEntries(
                Map.entry("WA-123",List.of("wind_cutter","fire_bolt")),
                Map.entry("WA-124",List.of("frost_bolt","fire_bolt")),
                Map.entry("WA-125",List.of("fire_bolt","frost_bolt")),
                Map.entry("WA-126",List.of("stone_bolt","fire_bolt")),
                Map.entry("WA-127",List.of("lightning_arrow","fire_bolt")),
                Map.entry("WA-128",List.of("void_bolt","fire_bolt")),
                Map.entry("WA-129",List.of("quick_slash","fire_bolt")),
                Map.entry("WA-130",List.of("minor_heal","life_drain")),
                Map.entry("WA-131",List.of("wolf_summon","dominate")),
                Map.entry("WA-132",List.of("quick_slash","fire_bolt")),
                Map.entry("WA-133",List.of("fire_bolt","quick_slash")));
        for(var entry:cases.entrySet()){
            var eligible=new GearEffectSnapshot(List.of(item(entry.getKey(),1,95,GearRarity.RARE)));
            assertEquals(1,GearSkillRanks.bonus(eligible,profiles.require(entry.getValue().get(0))),entry.getKey());
            assertEquals(0,GearSkillRanks.bonus(eligible,profiles.require(entry.getValue().get(1))),entry.getKey());
            assertEquals(0,GearSkillRanks.bonus(GearEffectSnapshot.EMPTY,profiles.require(entry.getValue().get(0))),entry.getKey());
        }
        var water=new GearEffectSnapshot(List.of(item("WA-124",1,95,GearRarity.RARE)));
        assertEquals(1,GearSkillRanks.bonus(water,profiles.require("avalanche")));
        var martial=new GearEffectSnapshot(List.of(item("WA-129",1,95,GearRarity.RARE)));
        assertEquals(1,GearSkillRanks.bonus(martial,profiles.require("lightning_arrow")));
        assertEquals(0,GearSkillRanks.bonus(martial,profiles.require("stone_bolt")));
    }
    @Test void rankRarityAndItemLevelBoundariesCannotGrantIllegally(){
        var profiles=Stage04SkillProfiles.loadCanonical(com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical());
        var heal=profiles.require("minor_heal");var wind=profiles.require("wind_cutter");
        assertEquals(0,GearSkillRanks.bonus(new GearEffectSnapshot(List.of(item("WA-121",1,95,GearRarity.RARE))),heal));
        assertEquals(0,GearSkillRanks.bonus(new GearEffectSnapshot(List.of(item("WA-121",1,77,GearRarity.VERY_RARE))),heal));
        assertEquals(1,GearSkillRanks.bonus(new GearEffectSnapshot(List.of(item("WA-121",1,78,GearRarity.VERY_RARE))),heal));
        assertEquals(0,GearSkillRanks.bonus(new GearEffectSnapshot(List.of(item("WA-121",2,92,GearRarity.VERY_RARE))),heal));
        assertEquals(2,GearSkillRanks.bonus(new GearEffectSnapshot(List.of(item("WA-121",2,92,GearRarity.LEGENDARY))),heal));
        assertEquals(0,GearSkillRanks.bonus(new GearEffectSnapshot(List.of(item("WA-123",1,54,GearRarity.RARE))),wind));
        assertEquals(1,GearSkillRanks.bonus(new GearEffectSnapshot(List.of(item("WA-123",1,55,GearRarity.RARE))),wind));
        assertEquals(0,GearSkillRanks.bonus(new GearEffectSnapshot(List.of(item("WA-123",2,79,GearRarity.RARE))),wind));
        assertEquals(2,GearSkillRanks.bonus(new GearEffectSnapshot(List.of(item("WA-123",2,80,GearRarity.RARE))),wind));
        assertEquals(0,GearSkillRanks.bonus(new GearEffectSnapshot(List.of(item("WA-123",3,93,GearRarity.RARE))),wind));
        assertEquals(3,GearSkillRanks.bonus(new GearEffectSnapshot(List.of(item("WA-123",3,94,GearRarity.RARE))),wind));
        assertEquals(0,GearSkillRanks.bonus(new GearEffectSnapshot(List.of(item("WA-123",3,94,GearRarity.LEGENDARY))),wind));
        assertEquals(0,GearSkillRanks.bonus(new GearEffectSnapshot(List.of(item("WA-122",1,34,GearRarity.MAGIC))),wind));
        assertEquals(1,GearSkillRanks.bonus(new GearEffectSnapshot(List.of(item("WA-122",1,35,GearRarity.MAGIC))),wind));
        assertEquals(0,GearSkillRanks.bonus(new GearEffectSnapshot(List.of(item("WA-122",2,59,GearRarity.RARE))),wind));
        assertEquals(2,GearSkillRanks.bonus(new GearEffectSnapshot(List.of(item("WA-122",2,60,GearRarity.RARE))),wind));
        assertEquals(0,GearSkillRanks.bonus(new GearEffectSnapshot(List.of(item("WA-122",4,94,GearRarity.LEGENDARY))),wind));
        assertEquals(4,GearSkillRanks.bonus(new GearEffectSnapshot(List.of(item("WA-122",4,94,GearRarity.RARE))),wind));
    }
}
