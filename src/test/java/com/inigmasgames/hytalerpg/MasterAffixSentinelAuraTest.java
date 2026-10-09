package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.hytale.NativeSentinelItemAuras;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.summon.*;
import com.inigmasgames.hytalerpg.execution.support.SupportWorldPort;
import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

final class MasterAffixSentinelAuraTest {
    private static final GearCatalog CATALOG=GearCatalog.load();
    static Stream<org.junit.jupiter.params.provider.Arguments> auraIds(){return Stream.of(
            org.junit.jupiter.params.provider.Arguments.of("WA-148","emanatism"),
            org.junit.jupiter.params.provider.Arguments.of("WA-149","thorns_aura"),
            org.junit.jupiter.params.provider.Arguments.of("WA-150","pedanticism"));}

    @ParameterizedTest(name="boundItemAuraActivatesAndChangesRecipient[{0}]")
    @MethodSource("auraIds")
    void boundItemAuraActivatesAndChangesRecipient(String id,String skill){
        var h=new Stage09SupportRuntimeTest.Harness(skill);
        var item=item(id);var binding=new IronSentinelBinding(3,UUID.randomUUID(),h.actor,"event",item,
                IronSentinelBinding.State.RESTORING,40,h.world,Vec3.ZERO,1,0,25,1,2,List.of());
        var registry=new SummonRegistry();var lease=registry.restoreIronSentinel(binding,10);
        UUID npc=UUID.randomUUID(),recipient=UUID.randomUUID();
        assertTrue(registry.activate(lease,npc,10));
        var context=NativeSentinelItemAuras.boundContext(h.execution,h.kernel,lease,Vec3.ZERO,skill);
        assertEquals(npc,context.request().actorId());
        assertEquals(item.identity(),context.gearSourceItemId());
        assertEquals(lease.boundEffects(),context.gearSnapshot());
        var port=new Recipients(npc,recipient);
        assertEquals(0,read(h,id,recipient,0));
        assertEquals("ACTIVE",h.runtime.activateItemAura(context,item.identity(),true,0,port));
        assertEquals(Set.of(new com.inigmasgames.hytalerpg.execution.support.SupportRuntime.ItemAuraSource(
                item.identity(),skill)),h.runtime.itemAuras(npc));
        assertTrue(read(h,id,recipient,0)>0,id+" recipient must receive the canonical field");
        assertEquals(0,read(h,id,UUID.randomUUID(),0),id+" unrelated actor");
        h.runtime.tick(npc,.1,true,port); // initialize the support clock
        port.members.remove(recipient);
        h.runtime.tick(npc,.2,true,port);
        assertEquals(0,read(h,id,recipient,.2),id+" recipient left the field");
        port.members.add(recipient);
        h.runtime.tick(npc,.3,true,port);
        assertTrue(read(h,id,recipient,.3)>0,id+" recipient returned");
        h.runtime.endItemAura(npc,item.identity(),skill,"SOURCE_ENDED",port);
        assertEquals(0,read(h,id,recipient,.3),id+" teardown");
        assertTrue(h.runtime.itemAuras(npc).isEmpty());
        assertEquals(0,port.resourceCalls,id+" free NPC field must not bill player Mana");
    }

    private static double read(Stage09SupportRuntimeTest.Harness h,String id,UUID recipient,double now){
        return switch(id){
            case "WA-148"->h.runtime.manaRegenerationIncreased(recipient,now);
            case "WA-149"->h.runtime.thorns(h.world,recipient,now).map(e->e.magnitude()).orElse(0d);
            case "WA-150"->h.runtime.cooldownRecoveryIncreased(recipient,now);
            default->throw new AssertionError(id);
        };
    }
    private static GearInstance item(String id){
        var base=CATALOG.base("gm.staff_prismatic.h");var affix=CATALOG.affix(id);
        var roll=new GearInstance.AffixRoll(id,affix.side(),affix.exclusionGroup(),1,1,
                new GearRequirements.Gate(1,Map.of()),"Bound aura",affix.name());
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.MAGIC,List.of(roll),BigDecimal.ZERO);
    }
    private static final class Recipients implements SupportWorldPort {
        final List<UUID> members=new ArrayList<>();int resourceCalls;
        Recipients(UUID npc,UUID recipient){members.add(npc);members.add(recipient);}
        public NativeResourcePort resources(){resourceCalls++;throw new AssertionError("NPC item aura billed resources");}
        public String valid(SkillExecutionContext context){return "PASS";}
        public List<UUID> allies(SkillExecutionContext context,double radius){return List.copyOf(members);}
        public List<UUID> enemies(SkillExecutionContext context,double radius){return List.of();}
        public double heal(SkillExecutionContext context,UUID target,double requested){throw new AssertionError("Aura healed");}
        public void present(SkillExecutionContext context,double radius,double duration){}
        public void trace(SkillExecutionContext context,String event,Map<String,?> details){}
    }
}
