package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.gear.GearAffixRuntime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GearRuntimeIntegrationTest {
    @Test void gearBonusesReachCommittedSkillAndDoNotMutatePlayerAllocationsOrBaseRank(){
        var plain=new Stage09SupportRuntimeTest.Harness("minor_heal");assertTrue(plain.cast().committed());
        var gear=new Stage09SupportRuntimeTest.Harness("minor_heal"){
            @Override public GearAffixRuntime.Effects gearEffects(){return new GearAffixRuntime.Effects(Map.of(RpgAttribute.WIS,20),0,0,0,.25,.30,2);}
            @Override public int itemGrantedSkillLevels(String skill,Equipment equipment){return gearEffects().allSkillRanks();}
        };
        var before=gear.bundle.service().getPresentationView(gear.actor).state();int rank=gear.bundle.service().baseSkillRank(gear.actor,"minor_heal");
        assertTrue(gear.cast().committed());
        assertEquals(rank+2,gear.context.effectiveSkillLevel());
        assertEquals(plain.context.snapshot().derivedStats().raw(RpgAttribute.WIS)+20,gear.context.snapshot().derivedStats().raw(RpgAttribute.WIS));
        assertTrue(gear.health>plain.health);assertTrue(gear.context.snapshot().cooldownSeconds()<plain.context.snapshot().cooldownSeconds());
        var after=gear.bundle.service().getPresentationView(gear.actor).state();
        assertEquals(before.attributes,after.attributes);assertEquals(before.learnedSkills,after.learnedSkills);
        assertEquals(rank,gear.bundle.service().baseSkillRank(gear.actor,"minor_heal"));assertEquals(before.gearEconomy,after.gearEconomy);
    }
}
