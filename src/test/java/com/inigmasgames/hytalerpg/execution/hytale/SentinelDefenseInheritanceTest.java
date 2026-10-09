package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.summon.*;
import com.inigmasgames.hytalerpg.gear.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SentinelDefenseInheritanceTest {
    private static GearInstance shield(String... ids){
        var catalog=GearCatalog.load();var base=catalog.base("gm.shield_copper.n");
        var rolls=new ArrayList<GearInstance.AffixRoll>();
        for(var id:ids){var affix=catalog.affix(id);rolls.add(new GearInstance.AffixRoll(id,affix.side(),
                affix.exclusionGroup(),1,id.equals("WA-069")?10:20,new GearRequirements.Gate(1,Map.of()),
                affix.name(),affix.name()));}
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.RARE,rolls,BigDecimal.ZERO);
    }
    private static SummonRegistry.Lease lease(GearInstance item){
        var binding=new IronSentinelBinding(3,UUID.randomUUID(),UUID.randomUUID(),"event",item,
                IronSentinelBinding.State.RESTORING,40,UUID.randomUUID(),Vec3.ZERO,1,0,25,1,2,List.of());
        return new SummonRegistry().restoreIronSentinel(binding,10);
    }
    @Test void shieldLocalAndGlobalDefenseReachNativeFilterViewOnce(){
        var plain=lease(shield());var affixed=lease(shield("WA-069","WA-070","WA-071"));
        var control=HytaleSummonSystem.sentinelDefenseView(plain);
        var actual=HytaleSummonSystem.sentinelDefenseView(affixed);
        double k=100+10*25;
        double chassis=k*affixed.sentinelStats().finalProtection()/(1-affixed.sentinelStats().finalProtection());
        double plainShield=plain.boundItem().intrinsicStats().get("shieldDefense");
        double shield=(affixed.boundItem().intrinsicStats().get("shieldDefense")+10)*1.2;
        assertEquals(chassis+plainShield,control.totalRating(),1e-6);
        assertEquals((chassis+shield)*1.2,actual.totalRating(),1e-6);
        assertEquals(actual.managedProtection(),HytaleSummonSystem.incomingResistance(affixed,"Physical"),1e-9);
        assertEquals(actual.managedProtection(),HytaleSummonSystem.incomingResistance(affixed,"Projectile"),1e-9);
        assertEquals(0,HytaleSummonSystem.incomingResistance(affixed,"Fire"),1e-9);
    }
}
