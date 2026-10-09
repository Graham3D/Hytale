package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem;
import com.inigmasgames.hytalerpg.gear.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SentinelQaSnapshotTest {
    @Test void snapshotsAllPackSourcesThroughCommittedLeaseWithoutMutatingIt(){
        var suite=new GearAffixQaSuite(GearCatalog.load());var owner=UUID.randomUUID();
        assertEquals("NO_ACTIVE_SENTINEL",SentinelQaSnapshot.resolved(null).get("status"));
        for(var fixture:suite.fixtures().stream().filter(f->f.group().equals("qa159")).toList()){
            var item=suite.create(fixture.fixtureId(),owner);
            var binding=new IronSentinelBinding(3,UUID.randomUUID(),owner,"qa159-snapshot",item,
                    IronSentinelBinding.State.RESTORING,40,UUID.randomUUID(),Vec3.ZERO,1,0,25,1,2,List.of());
            var registry=new SummonRegistry();var lease=registry.restoreIronSentinel(binding,10);
            var before=lease.boundEffects().revision();var row=SentinelQaSnapshot.resolved(lease);
            assertEquals(lease.maximumHealth(),row.get("maximumHealth"));
            assertEquals(HytaleSummonSystem.sentinelDefenseView(lease),row.get("defense"));
            assertEquals(1/lease.interval(),row.get("attacksPerSecond"));
            assertEquals(IronSentinelAffixes.criticalChance(item),row.get("criticalChance"));
            assertEquals(item.affixes().size(),((List<?>)row.get("provenance")).size());
            assertEquals(before,lease.boundEffects().revision());
            var json=new com.google.gson.Gson().toJson(row);
            assertTrue(json.contains(fixture.fixtureId()));
            for(var affix:item.affixes())assertTrue(json.contains(affix.familyId()));
        }
        assertEquals("OWNER_ONLY",Qa159Pack.disposition("WA-151"));
        assertEquals("INAPPLICABLE",Qa159Pack.disposition("WA-141"));
        assertEquals("INHERITED",Qa159Pack.disposition("WA-148"));
    }
}
