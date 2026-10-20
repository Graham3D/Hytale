package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class IronSentinelRestoredLeaseTest {
    @Test void restoredLeaseRetainsOwnerBoundItemRankPowerAndOneAttackClock(){
        var base=GearCatalog.load().base("gm.sword_iron.n");
        var item=GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.COMMON,List.of(),BigDecimal.ZERO);
        UUID owner=UUID.randomUUID(),world=UUID.randomUUID(),instance=UUID.randomUUID();
        var binding=new IronSentinelBinding(2,instance,owner,"event",item,IronSentinelBinding.State.RESTORING,
                42,world,new Vec3(0,70,0),System.currentTimeMillis(),4,25,1.25,2);
        var registry=new SummonRegistry();var lease=registry.restoreIronSentinel(binding,10);
        assertEquals(owner,lease.owner());assertEquals(world,lease.world());assertEquals(item,lease.boundItem());
        assertEquals(25,lease.sentinelStats().effectiveLevel());
        assertEquals(IronSentinelStatProjection.project(25,item,2).finalMaxHealth()*1.25,lease.maximumHealth(),1e-9);
        assertEquals(1.25,lease.coefficient(),1e-9);
        assertThrows(IllegalStateException.class,()->registry.restoreIronSentinel(binding,10));
        assertTrue(registry.activate(lease,UUID.randomUUID(),10));
        assertEquals(0,registry.claimAttack(lease.token(),10));
        assertEquals(1,registry.claimAttack(lease.token(),12));
        assertEquals(0,registry.claimAttack(lease.token(),12));
        assertEquals(1,registry.iron(owner).stream().count());
    }
}
