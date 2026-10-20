package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.progress.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GearBindingReconciliationTest {
    final GearCatalog catalog=GearCatalog.load();
    final GearBindings bindings=new GearBindings();
    final GearDropGenerator generator=new GearDropGenerator(catalog,bindings,GearDropGenerator.STAGE_TWO_CANDIDATES);
    EnemyRewardRegistry.LootSource source(DifficultyId era,int level){return new EnemyRewardRegistry.LootSource(
            "audit/"+era+"/"+level,new UUID(0,1),new UUID(0,2),era,level,"audit",ProgressionMath.Rank.BOSS,ProgressionMath.Rarity.ORDINARY,"test");}
    @Test void everyBlockedIdIsExcludedAtEveryLevelAndEraWithoutFallback(){
        var reached=new HashSet<String>();
        for(var era:DifficultyId.values())for(int level=1;level<=99;level++){
            var source=source(era,level);
            for(var base:generator.eligibleBases(source,Set.of())){
                assertTrue(bindings.require(base.id()).mapped(),base.id());assertEquals(era,base.era());
                reached.add(base.id());
            }
        }
        for(var binding:bindings.all()){
            var base=catalog.base(binding.baseId());
            if(!binding.mapped()){
                assertFalse(reached.contains(base.id()),base.id());
                assertNull(binding.managedItemId(),base.id());
                var source=source(base.era(),base.sourceWindow()==null?base.requiredLevel():base.sourceWindow().getFirst());
                assertTrue(generator.eligibleBases(source,Set.of(base.family())).isEmpty(),base.id());
                assertThrows(IllegalStateException.class,()->generator.generate(source,100,"force-boss",Set.of(base.family())),base.id());
            }else if(base.worldDropCandidate())assertTrue(reached.contains(base.id()),base.id());
        }
    }
    @Test void countsDoNotRelabelAdapterWorkAsNativeImpossibility(){
        assertEquals(447,bindings.all().size());
        assertEquals(318,bindings.all().stream().filter(GearBindings.Binding::mapped).count());
        assertEquals(114,bindings.all().stream().filter(b->b.resolutionClass().equals("MISSING_ADAPTER")).count());
        assertEquals(15,bindings.all().stream().filter(b->b.resolutionClass().equals("TRUE_NATIVE_CAPABILITY_BLOCKER")).count());
        assertTrue(bindings.all().stream().noneMatch(b->b.resolutionClass().equals("RESOLVABLE_AUDIT")));
        for(var binding:bindings.all())if(binding.mapped())assertEquals("VALIDATED_NATIVE_REUSE",binding.resolutionClass());
    }
    @Test void reuseRetainsAuthoredIdentityEraAndUtilityExclusion(){
        for(String era:List.of("n","nm","h")){
            for(String family:List.of("crossbow_scout","crossbow_standard","crossbow_repeater"))
                assertEquals("Weapon_Crossbow_Iron",bindings.require("gm."+family+"."+era).nativeItemId());
            var shovel=bindings.require("gm.tool.shovel.adamantite."+era);
            assertTrue(shovel.mapped());assertEquals("Tool_Shovel_Iron",shovel.nativeItemId());
            assertFalse(catalog.base(shovel.baseId()).worldDropCandidate());
        }
        assertNotEquals(bindings.require("gm.crossbow_scout.n").managedItemId(),bindings.require("gm.crossbow_scout.h").managedItemId());
    }
}
