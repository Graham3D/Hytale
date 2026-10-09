package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.CommittedTarget;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot;
import com.inigmasgames.hytalerpg.gear.GearInstance;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Test-only access to the existing support harness and its real SupportRuntime owner. */
public final class MasterAffixFinalAuraHelper {
    private MasterAffixFinalAuraHelper() { }

    public static void verify(String id,String skill,GearInstance item,GearEffectSnapshot accepted,
                              GearEffectSnapshot unrolled) {
        var h=new Stage09SupportRuntimeTest.Harness(skill) {
            @Override public List<UUID> enemies(com.inigmasgames.hytalerpg.execution.SkillExecutionContext c,double radius) {
                return List.of();
            }
            @Override public double itemAuraActivationCost(com.inigmasgames.hytalerpg.execution.SkillExecutionContext c) {
                return c.profile().resourceCost();
            }
            @Override public boolean payItemAuraActivation(com.inigmasgames.hytalerpg.execution.SkillExecutionContext c) {
                double cost=c.profile().resourceCost();
                if(mana<cost)return false;
                mana-=cost;
                return true;
            }
            @Override public boolean upkeep(com.inigmasgames.hytalerpg.execution.SkillExecutionContext c,double seconds,int quantum) {
                double cost=c.profile().support().upkeepPerSecond()*seconds;
                if(mana<cost)return false;
                mana-=cost;
                return true;
            }
        };
        var ally=UUID.randomUUID();var unrelated=UUID.randomUUID();
        h.members.add(ally);
        assertEquals(0,unrolled.value(id),1e-9);
        assertEquals(0,h.runtime.auraCount());
        assertEffect(id,h,ally,unrelated,false);
        var target=new CommittedTarget(h.world,Vec3.ZERO,Vec3.ZERO,Vec3.FORWARD,h.actor);
        var context=h.execution.itemAura(h.world,h.actor,item.identity(),skill,accepted,h,target);
        assertEquals("ACTIVE",h.runtime.activateItemAura(context,item.identity(),false,0,h));
        assertEquals(1,h.runtime.auraCount());
        assertEffect(id,h,ally,unrelated,true);
        h.runtime.endItemAura(h.actor,item.identity(),skill,"SOURCE_REMOVED",h);
        assertEquals(0,h.runtime.auraCount());
        assertEffect(id,h,ally,unrelated,false);
    }

    private static void assertEffect(String id,Stage09SupportRuntimeTest.Harness h,UUID ally,
                                     UUID unrelated,boolean active) {
        if(id.equals("WA-149")) {
            assertEquals(active,h.runtime.thorns(h.world,ally,0).isPresent());
            assertTrue(h.runtime.thorns(h.world,unrelated,0).isEmpty());
            if(active)assertTrue(h.runtime.thorns(h.world,ally,0).orElseThrow().magnitude()>0);
        } else {
            assertEquals(active?.15:0,h.runtime.cooldownRecoveryIncreased(ally,0),1e-9);
            assertEquals(0,h.runtime.cooldownRecoveryIncreased(unrelated,0),1e-9);
        }
    }
}
