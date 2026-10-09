package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.difficulty.*;
import com.inigmasgames.hytalerpg.execution.hytale.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.*;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real production classifier -> captured roster -> planner -> durable root -> active lease.
 * Native actor allocation/placement is represented by captured native identities, not a running server. */
class R244NaturalBirthIntegrationTest {
    @TempDir Path directory;
    static UUID id(String value){return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));}
    @Test void frostMixedFlockAndSingletonBearPublishFromExactCustomBiomeWithoutQaFallback(){
        var balance=EnemyBalance.canonical();var bindings=EnemyNativeBindings.load();var registry=EnemyRewardRegistry.load();
        var worlds=new WorldDifficultyRegistry(directory.resolve("worlds.json"));var world=id("r244/world");
        worlds.register(new WorldDifficultyRegistry.Binding(world,"default",WorldDifficultyRegistry.Kind.CAMPAIGN,
                DifficultyId.NORMAL,"rpg.encounters.r031.pilot","shared",true));
        var resolver=AuthoredEncounterCatalog.load().resolver(worlds);
        String biome=CampaignBiomes.current().biomes().stream().filter(b->b.key().contains("Zone3")
                &&b.sources().stream().anyMatch(s->s.asset().contains("/Custom."))).findFirst().orElseThrow().key();
        var planner=new EnemyBirthPlanner(balance,EnemyAffixRegistry.canonical(),EnemyAffinityRegistry.canonical(),EnemyNamePools.canonical(),EnemyVisualVariants.canonical());
        for(var roleList:List.of(List.of("Skeleton_Frost_Fighter","Skeleton_Frost_Soldier"),List.of("Bear_Grizzly"))){
            String role=roleList.getFirst();var encounter=id("r244/"+role);
            var reservation=new NativeEnemySpawnGroups.Reservation(world,encounter,1);
            var members=new ArrayList<NativeEnemySpawnGroups.Member>();var sources=new ArrayList<EnemyNativeGroupPreparation.MemberSource>();
            for(int i=0;i<roleList.size();i++){
                var actor=id(role+"/"+i);String memberRole=roleList.get(i);
                members.add(new NativeEnemySpawnGroups.Member(actor,memberRole,new EnemyStaging.State(world,encounter,actor,1,7)));
                var classification=resolver.classifyNatural(registry,world,actor,memberRole,biome,12,EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,1000);
                assertEquals(EncounterProfileResolver.Admission.READY,classification.reason());
                sources.add(new EnemyNativeGroupPreparation.MemberSource(classification.spawn().orElseThrow(),memberRole.replace('_',' ')));
            }
            NativeEnemySpawnGroups.Group captured=null;EnemyBirthPlanner.Request request=null;boolean sawOrdinary=false;
            // Find controlled deterministic fixture inputs. Production never loops/rerolls a native job.
            for(int job=0;job<10000;job++){
                var group=new NativeEnemySpawnGroups.Group(new NativeEnemySpawnGroups.Job(world,job,8,role,12,9,members.size()),reservation,members,false);
                var candidate=EnemyNativeGroupPreparation.prepare(group,sources,bindings::role,new Vec3(-65,70,63),balance,bindings.revision(),true).orElseThrow();
                var rarity=planner.rarity(candidate.seed(),DifficultyId.NORMAL);
                if(rarity==EnemyRarity.NORMAL)sawOrdinary=true;
                if(sawOrdinary&&rarity==EnemyRarity.UNIQUE){captured=group;request=candidate;break;}
            }
            assertNotNull(request);var seed=request.seed();int needed=planner.additionalUniqueMembers(request).orElseThrow();
            var allSources=new ArrayList<>(sources);
            if(needed>0){
                var extras=new ArrayList<NativeEnemySpawnGroups.Member>();var extraSources=new ArrayList<EnemyNativeGroupPreparation.MemberSource>();
                for(int i=0;i<needed;i++){
                    var actor=id(role+"/extra/"+i);
                    extras.add(new NativeEnemySpawnGroups.Member(actor,role,new EnemyStaging.State(world,encounter,actor,1,7)));
                    var spawn=resolver.classifyNatural(registry,world,actor,role,biome,12,EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,1000).spawn().orElseThrow();
                    extraSources.add(new EnemyNativeGroupPreparation.MemberSource(spawn,role.replace('_',' ')));
                }
                var job=captured.job();var added=new NativeEnemySpawnGroups.Group(new NativeEnemySpawnGroups.Job(world,job.nativeJobId(),job.roleIndex(),role,12,9,needed),reservation,extras,false);
                request=EnemyNativeGroupPreparation.completeAdditional(captured,request,added,extraSources,bindings::role,balance,bindings.revision()).orElseThrow();
                allSources.addAll(extraSources);assertEquals(seed,request.seed());
            }
            var result=planner.plan(request);assertTrue(result.promoted(),result.fallbackReason());assertEquals(result,planner.plan(request));
            var birth=result.plan();assertEquals(allSources.stream().map(s->s.spawn().enemy()).toList(),birth.actors().stream().map(EnemyDescriptor::entityId).toList());
            var capacity=new EnemyPackCapacity(balance);var gate=new EnemyWorldAdmission(w->CompletableFuture.completedFuture(new FileEncounterStore.EnemyWorldInventory(List.of(),List.of())),capacity);
            gate.begin(world).toCompletableFuture().join();assertTrue(gate.reserve(EnemyPackCapacity.Reservation.of(birth.pack())).accepted());
            var root=new EnemyBirthRoot(birth,sources.stream().map(EnemyNativeGroupPreparation.MemberSource::spawn).toList());
            var path=directory.resolve(role);
            try(var store=new FileEncounterStore(path)){
                assertEquals(birth,store.reserveEnemyBirthRoot(root));assertEquals(birth,store.reserveEnemyBirthRoot(root));
                store.transitionEnemyPack(world,birth.pack().packId(),EnemyPackRecord::staged);
                var published=store.transitionEnemyPack(world,birth.pack().packId(),EnemyPackRecord::publish);
                var actors=birth.actors().stream().map(EnemyDescriptor::entityId).toList();gate.activatePublished(birth,published,actors);
                assertEquals(1,gate.activePackReservations(world));
                for(var actor:actors)gate.memberRemoved(birth,actor,"UNLOAD");assertEquals(0,gate.activePackReservations(world));
                gate.activatePublished(birth,published,actors);assertEquals(1,gate.activePackReservations(world));
                for(var actor:actors)assertTrue(store.death(world,actor).isEmpty(),"Publication/rebind must not invent reward receipts");
            }
            try(var store=new FileEncounterStore(path)){
                var recovered=store.recoverEnemyWorld(world);assertEquals(1,recovered.births().size());assertEquals(1,recovered.packs().size());
                assertEquals(root,store.enemyBirthRoot(world,encounter).orElseThrow());
            }
        }
    }
}
