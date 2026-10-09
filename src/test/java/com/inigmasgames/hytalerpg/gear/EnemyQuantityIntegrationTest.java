package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.difficulty.*;
import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class EnemyQuantityIntegrationTest {
    @TempDir Path directory;
    private final UUID world=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
    private GearDropGenerator generator(){return new GearDropGenerator(GearCatalog.load(),new GearBindings(),GearAffixRuntime.ENABLED);}
    private EncounterContributions.DeathPlan plan(){
        UUID enemy=UUID.randomUUID();var combat=new EncounterProfileResolver.Resolved(world,enemy,DifficultyId.HELL,"fixture","fixture","role","biome",95,100,10,1,1,MonsterResistanceProfile.NONE,EncounterProfileResolver.Evidence.FIXTURE_ONLY);
        var context=EnemyRewardContext.canonical(EnemyRarity.SUPER_UNIQUE,EnemyRewardContext.Origin.AUTHORED_ENCOUNTER,false,4);
        var spawn=new EnemyRewardRegistry.Spawn(world,enemy,"role","role","biome",95,ProgressionMath.Rank.BOSS,ProgressionMath.Rarity.ORDINARY,"fixture",1000,null,combat,context);
        long xp=ProgressionMath.equalShare(spawn.rewardXp(95),2);
        return new EncounterContributions.DeathPlan(spawn,Vec3.ZERO,2000,List.of(new EncounterContributions.Share(a,xp,15,95,2),new EncounterContributions.Share(b,xp,15,95,2)));
    }
    private void claims(GearLootService loot,EncounterContributions.DeathPlan p){
        var policy=new GearClaims.Policy("party",1,GearClaims.Mode.ROUND_ROBIN,List.of(a,b),a,Set.of(),true,false);
        loot.contribute(world,p.spawn().enemy(),a,policy,1500);loot.contribute(world,p.spawn().enemy(),b,policy,1600);
    }
    @Test void bonusSlotsUseNormalGeneratorIndependentIdsAndFrozenRoundRobinSponsorsAfterReload(){
        var plan=plan();List<GearLootService.Loot> first;var generator=generator();
        try(var store=new FileEncounterStore(directory)){
            var loot=new GearLootService(store,generator,()->3000);claims(loot,plan);
            var base=loot.death(plan,Map.of(a,.25,b,2.));
            assertEquals(generator.generate(plan.spawn().lootSource().orElseThrow(),.25,plan.spawn().eventId(),Set.of()),base.result());
            var bonus=store.gearRead("loot-quantity",plan.spawn().eventId(),GearLootService.QuantityPlan.class).orElseThrow();
            first=store.gearRecords("loot",GearLootService.Loot.class).stream().sorted(Comparator.comparing(row->row.source().eventId())).toList();
            assertEquals(1+bonus.bonusSlots(),first.size());assertTrue(first.size()>=2&&first.size()<=3);
            assertEquals(first.size(),first.stream().map(row->row.result().item().identity()).distinct().count());
            assertEquals(b,first.get(1).allocation().sponsor());assertEquals(2.,first.get(1).magicFind());
            assertTrue(first.subList(1,first.size()).stream().allMatch(row->row.seed().startsWith("loot-profile/me-equipment/")&&row.result().item()!=null));
            assertEquals(first.size(),store.gearRead("cursors","party",GearLootService.Cursor.class).orElseThrow().next());
        }
        try(var store=new FileEncounterStore(directory)){
            var loot=new GearLootService(store,generator,()->9000);loot.death(plan,Map.of(a,99.,b,99.));
            assertEquals(first,store.gearRecords("loot",GearLootService.Loot.class).stream().sorted(Comparator.comparing(row->row.source().eventId())).toList());
            assertEquals(first.size(),store.gearRead("cursors","party",GearLootService.Cursor.class).orElseThrow().next());
        }
    }
    @Test void crashAfterQuantityPlanOrChildCursorDoesNotRerollOrDuplicateItems(){
        for(String fault:List.of("AFTER_loot-quantity","CHILD_CURSOR")){
            var plan=plan();Path folder=directory.resolve(fault);var generator=generator();
            try(var store=new FileEncounterStore(folder)){
                var loot=new GearLootService(store,generator,()->3000);claims(loot,plan);var cursors=new AtomicInteger();
                store.configureGearFault(boundary->{if(boundary.equals(fault)||fault.equals("CHILD_CURSOR")&&boundary.equals("AFTER_cursors")&&cursors.incrementAndGet()==3)throw new IllegalStateException("PROCESS_LOSS");});
                assertThrows(IllegalStateException.class,()->loot.death(plan,Map.of(a,.25,b,2.)));
            }
            try(var store=new FileEncounterStore(folder)){
                var loot=new GearLootService(store,generator,()->9000);loot.death(plan,Map.of(a,99.,b,99.));
                var rows=store.gearRecords("loot",GearLootService.Loot.class);
                var frozen=store.gearRead("loot-quantity",plan.spawn().eventId(),GearLootService.QuantityPlan.class).orElseThrow();
                assertEquals(1+frozen.bonusSlots(),rows.size());assertEquals(rows.size(),rows.stream().map(row->row.result().item().identity()).distinct().count());
                assertTrue(rows.stream().allMatch(row->row.magicFind()==(row.allocation().sponsor().equals(a)?.25:2.)));
                assertEquals(rows.size(),store.gearRead("cursors","party",GearLootService.Cursor.class).orElseThrow().next());
            }
        }
    }
    @Test void r200ProfilePicksRemainTheBaseOwnerAndMeAddsFrozenChildren(){
        var death=plan();List<GearLootService.Loot> first;
        try(var store=new FileEncounterStore(directory)){
            var loot=new GearLootService(store,generator(),()->3000);claims(loot,death);
            first=loot.deliverPicks(death);
            var picks=store.gearRead("loot-picks",death.spawn().eventId(),GearLootService.DeathPicks.class).orElseThrow();
            int base=(int)picks.decision().succeeded();
            var quantity=store.gearRead("loot-quantity",death.spawn().eventId(),GearLootService.QuantityPlan.class).orElseThrow();
            assertEquals(base,quantity.baseItems().size());
            assertEquals(base+quantity.bonusSlots(),first.size());
            assertEquals(first.size(),first.stream().map(row->row.result().item().identity()).distinct().count());
        }
        try(var store=new FileEncounterStore(directory)){
            var loot=new GearLootService(store,generator(),()->9000);
            assertEquals(first,loot.deliverPicks(death));
        }
    }
}
