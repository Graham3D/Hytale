package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.combat.attribute.*;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.cooldown.RpgCooldownService;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.execution.EffectiveSkillLevel;
import com.inigmasgames.hytalerpg.progress.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class GearAffixRuntimeTest {
    final GearCatalog catalog=GearCatalog.load();
    GearInstance item(String base,int intrinsic,String... rolls){
        var affixes=new ArrayList<GearInstance.AffixRoll>();
        for(int i=0;i<rolls.length;i+=2){var a=catalog.affix(rolls[i]);affixes.add(new GearInstance.AffixRoll(a.id(),a.side(),a.exclusionGroup(),5,Double.parseDouble(rolls[i+1]),new GearRequirements.Gate(1,Map.of()),a.name(),a.name()));}
        var rarity=switch(affixes.size()){case 0->GearRarity.COMMON;case 1->GearRarity.UNCOMMON;case 2,3->GearRarity.RARE;default->GearRarity.VERY_RARE;};
        return GearInstance.authoredQa(catalog.base(base),UUID.randomUUID(),99,intrinsic,rarity,affixes,BigDecimal.ZERO);
    }
    @Test void localPowerAddsThenNormalizesThenMultipliesExactlyOnce(){
        var g=item("gm.sword_mithril.h",900,"WA-001","10","WA-002","25","WA-151","1");var r=GearAffixRuntime.physical(g);
        assertEquals(Math.round((g.intrinsicStats().get("physicalMin")+10)*1.25*10)/10d,r.minimum());
        assertEquals(Math.round((g.intrinsicStats().get("physicalMax")+10)*1.25*10)/10d,r.maximum());
        var floor=item("gm.sword_mithril.h",1000,"WA-157","1000");var f=GearAffixRuntime.physical(floor);
        assertEquals(f.minimum(),f.maximum());assertEquals(floor,GearInstance.fromJson(floor.toJson()));
    }
    @Test void armorAffixesDoNotMultiplyOtherSlotsOrPools(){
        var a=item("gm.plate_mithril.head.h",900,"GA-159","2","GA-160","50","WA-151","1");
        assertEquals((a.intrinsicStats().get("protectionPoints")+2)*1.5,GearAffixRuntime.protection(a));
        assertEquals(a.intrinsicStats().getOrDefault("health",0d),GearAffixRuntime.effects(List.of(a)).health());
    }
    @Test void attributeBonusesUseSharedDiminishingReturnsAndNeverSatisfySelfOrCycle(){
        var g=item("gm.sword_mithril.h",1000,"WA-085","12");assertEquals(Map.of(RpgAttribute.STR,12),GearAffixRuntime.attributes(g));
        var gate=new GearRequirements.Gate(1,Map.of(RpgAttribute.STR,20));
        var candidate=new GearRequirements.Equipped(g.identity(),gate,GearAffixRuntime.attributes(g));
        var other=new GearRequirements.Equipped(UUID.randomUUID(),gate,Map.of(RpgAttribute.STR,12));
        assertTrue(GearRequirements.resolve(99,Map.of(RpgAttribute.STR,10),List.of(candidate,other)).valid().isEmpty());
        var profile=CombatBalanceProfile.loadCanonical();var service=new DerivedStatService(profile,new EffectiveAttributeService(profile));
        var raw=Map.of(RpgAttribute.STR,149);var projected=GearAffixRuntime.effects(List.of(g)).derive(service,raw);
        assertEquals(service.derive(Map.of(RpgAttribute.STR,161)).maxHealth(),projected.maxHealth());
        assertEquals(149,raw.get(RpgAttribute.STR));
    }
    @Test void poolsAreCapacityAndCooldownUsesExistingCapAndWindupOnly(){
        var g=item("gm.sword_mithril.h",1000,"WA-091","100","WA-009","25","WA-012","100");
        var effects=GearAffixRuntime.effects(List.of(g));assertEquals(100,effects.health());assertEquals(1.6,effects.windup(2));assertEquals(0,effects.windup(0));
        var p=CombatBalanceProfile.loadCanonical();var derived=effects.derive(new DerivedStatService(p,new EffectiveAttributeService(p)),Map.of());
        assertEquals(.75,new RpgCooldownService(p,()->0).calculate(10,1,derived.cooldownRecovery(),null).appliedRecovery());
    }
    @Test void allSkillRanksModifyEffectiveOnlyAndUncalibratedLightFailsClosed(){
        var g=item("gm.sword_mithril.h",1000,"WA-121","2");int baseRank=20;
        assertEquals(22,EffectiveSkillLevel.resolveBase(baseRank,GearAffixRuntime.effects(List.of(g)).allSkillRanks()));assertEquals(20,baseRank);
        assertTrue(GearAffixRuntime.supported(item("gm.sword_mithril.h",1000,"WA-072","8")));
        assertFalse(GearAffixRuntime.supported(item("gm.sword_mithril.h",1000,"WA-155","3")));
        assertThrows(IllegalArgumentException.class,()->new GearDropGenerator(catalog,new GearBindings(),Set.of("WA-155")));
    }
    @Test void generatedItemsHaveWorkingOperatorsAcrossAllErasAndRankGates(){
        var generator=new GearDropGenerator(catalog,new GearBindings(),GearAffixRuntime.ENABLED);int ranks=0;
        for(var era:DifficultyId.values())for(int i=0;i<120;i++){
            int level=era==DifficultyId.NORMAL?1+i%40:era==DifficultyId.NIGHTMARE?40+i%20:60+i%40;
            var source=new EnemyRewardRegistry.LootSource("test/"+era+i,new UUID(0,1),new UUID(0,i),era,level,"test",ProgressionMath.Rank.BOSS,ProgressionMath.Rarity.ORDINARY,"fixture");
            var g=generator.generate(source,10,source.eventId(),Set.of()).item();assertTrue(GearAffixRuntime.supported(g));
            for(var a:g.affixes())if(a.familyId().equals("WA-121")){ranks++;assertTrue(level>=78);assertTrue(g.rarity()==GearRarity.VERY_RARE||g.rarity()==GearRarity.LEGENDARY);if(a.value()==2){assertTrue(level>=92);assertEquals(GearRarity.LEGENDARY,g.rarity());}}
        }
        // Gate tables are deterministic even when the finite matrix does not select the rare skiller.
        var tiers=GearAffixTiers.compile(catalog.affix("WA-121"));assertEquals(List.of(78,92),tiers.stream().map(GearAffixTiers.Tier::minimumItemLevel).toList());
    }
}
