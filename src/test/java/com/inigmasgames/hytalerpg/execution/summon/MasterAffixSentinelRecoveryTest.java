package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.combat.resource.GearRecoveryRuntime;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

final class MasterAffixSentinelRecoveryTest {
    private static final GearCatalog CATALOG=GearCatalog.load();

    @ParameterizedTest(name="boundNpcAcceptedHitPaysOnlyNpcHealth[{0}]")
    @ValueSource(strings={"WA-094","WA-096"})
    void boundNpcAcceptedHitPaysOnlyNpcHealth(String id){
        var source=lease(item(id));var control=lease(item(null));
        UUID npc=source.entity(),victim=UUID.randomUUID();
        var actual=new GearRecoveryRuntime();var plain=new GearRecoveryRuntime();
        var denied=new GearRecoveryRuntime();
        var hit=new GearRecoveryRuntime.Receipt(npc,"world","root","contact",victim,
                100,80,true,true,false,false,false);
        var child=new GearRecoveryRuntime.Receipt(npc,"world","child","contact",victim,
                100,80,true,true,false,true,true);
        actual.onAttack(hit,source.boundEffects(),1000,0,0,true);
        plain.onAttack(hit,control.boundEffects(),1000,0,0,true);
        denied.onAttack(child,source.boundEffects(),1000,0,0,true);
        double expected=id.equals("WA-094")?3:4;
        assertEquals(expected,actual.pay(npc,"world",GearRecoveryRuntime.Pool.HIT_HEALTH,
                1000,0,(pool,requested,maximum)->requested).amount(),1e-9,id);
        assertEquals(0,plain.pay(npc,"world",GearRecoveryRuntime.Pool.HIT_HEALTH,
                1000,0,(pool,requested,maximum)->requested).amount(),1e-9,id+" no source");
        assertEquals(0,denied.pay(npc,"world",GearRecoveryRuntime.Pool.HIT_HEALTH,
                1000,0,(pool,requested,maximum)->requested).amount(),1e-9,id+" NoLeech child");
        assertEquals(0,actual.pay(npc,"world",GearRecoveryRuntime.Pool.HIT_MANA,
                1000,0,(pool,requested,maximum)->requested).amount(),1e-9,id+" NPC has no Mana payout");
        assertEquals(0,actual.pay(source.owner(),"world",GearRecoveryRuntime.Pool.HIT_HEALTH,
                1000,0,(pool,requested,maximum)->requested).amount(),1e-9,id+" owner receives no copy");
    }

    private static SummonRegistry.Lease lease(GearInstance item){
        IronSentinelAffixes.requireAdapted(item);
        var binding=new IronSentinelBinding(3,UUID.randomUUID(),UUID.randomUUID(),"event",item,
                IronSentinelBinding.State.RESTORING,40,UUID.randomUUID(),Vec3.ZERO,1,0,25,1,2,List.of());
        var registry=new SummonRegistry();var lease=registry.restoreIronSentinel(binding,10);
        assertTrue(registry.activate(lease,UUID.randomUUID(),10));
        return lease;
    }
    private static GearInstance item(String id){
        var base=CATALOG.base("gm.sword_iron.n");List<GearInstance.AffixRoll> rolls=List.of();
        if(id!=null){var affix=CATALOG.affix(id);
            rolls=List.of(new GearInstance.AffixRoll(id,affix.side(),affix.exclusionGroup(),1,
                    id.equals("WA-094")?3:20,new GearRequirements.Gate(1,Map.of()),
                    "Bound recovery",affix.name()));}
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.UNCOMMON,rolls,BigDecimal.ZERO);
    }
}
