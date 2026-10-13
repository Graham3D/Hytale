package com.inigmasgames.hytalerpg.enemies;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.inigmasgames.hytalerpg.difficulty.*;
import com.inigmasgames.hytalerpg.execution.hytale.EnemyStaging;
import com.inigmasgames.hytalerpg.execution.hytale.NativeEnemySpawnGroups;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import com.inigmasgames.hytalerpg.progress.FileEncounterStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the Stage 1 release world identity through the existing natural Elite planner and durable store. */
class ChapterINaturalElitePipelineTest {
    @TempDir Path root;
    private static final UUID RELEASE_DEFAULT=UUID.fromString("3d366b79-f367-419a-87b7-a8d53cde7ec0");
    private static final UUID RELEASE_ZONE=UUID.fromString("6e88444d-898b-4a9d-99a6-f5f19d0257a1");
    private static final String BIOME="Default/Zone1_Tier1/Forest_Birch";
    private static final String ROLE="Wolf_Black";

    private static UUID id(String value){return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));}

    private void nativeWorld(String name,UUID id) throws Exception {
        var path=root.resolve("universe/worlds").resolve(name);
        Files.createDirectories(path);
        Files.writeString(path.resolve("config.json"),new BsonDocument("UUID",Codec.UUID_BINARY.encode(id,new ExtraInfo())).toJson());
    }

    private DifficultyRuntime releaseWorlds() throws Exception {
        try(var source=Objects.requireNonNull(getClass().getResourceAsStream("/chapter1/copied-difficulty-worlds-v1.json"))){
            Files.copy(source,root.resolve("difficulty-worlds.json"));
        }
        nativeWorld("default",RELEASE_DEFAULT);
        nativeWorld("zone3_taiga1_world",RELEASE_ZONE);
        var runtime=new DifficultyRuntime(root.resolve("difficulty-worlds.json"),root.resolve("universe/worlds"));
        assertEquals(RELEASE_DEFAULT,runtime.worldLoaded(RELEASE_DEFAULT,"default").orElseThrow().worldId());
        assertEquals(RELEASE_ZONE,runtime.worldLoaded(RELEASE_ZONE,"zone3_taiga1_world").orElseThrow().worldId());
        return runtime;
    }

    private record Fixture(NativeEnemySpawnGroups.Group group,EnemyBirthPlanner.Request request,
                           List<EnemyNativeGroupPreparation.MemberSource> sources) {}

    private static EnemyNativeGroupPreparation.MemberSource source(EncounterProfileResolver resolver,
            EnemyRewardRegistry catalog,UUID actor) {
        var classification=resolver.classifyNatural(catalog,RELEASE_DEFAULT,actor,ROLE,BIOME,1,
                EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,1000);
        assertEquals(EncounterProfileResolver.Admission.READY,classification.reason());
        return new EnemyNativeGroupPreparation.MemberSource(classification.spawn().orElseThrow(),"Black Wolf");
    }

    private static Fixture fixture(int job,EncounterProfileResolver resolver,EnemyRewardRegistry catalog,
            EnemyNativeBindings bindings,EnemyBalance balance) {
        var encounter=id("chapter1/encounter/"+job);
        var actor=id("chapter1/wolf/"+job);
        var member=new NativeEnemySpawnGroups.Member(actor,ROLE,new EnemyStaging.State(RELEASE_DEFAULT,encounter,actor,1,7));
        var group=new NativeEnemySpawnGroups.Group(new NativeEnemySpawnGroups.Job(RELEASE_DEFAULT,job,1,ROLE,1,1,1),
                new NativeEnemySpawnGroups.Reservation(RELEASE_DEFAULT,encounter,1),List.of(member),false);
        var sources=List.of(source(resolver,catalog,actor));
        var request=EnemyNativeGroupPreparation.prepare(group,sources,bindings::role,new Vec3(0,70,0),
                balance,bindings.revision(),true).orElseThrow();
        return new Fixture(group,request,sources);
    }

    @Test void copiedReleaseIdentityAdmitsNormalChampionAndUniqueWithoutRerollOnRestart() throws Exception {
        var worlds=releaseWorlds();
        var catalog=EnemyRewardRegistry.load();
        var bindings=EnemyNativeBindings.load();
        var balance=EnemyBalance.canonical();
        var planner=new EnemyBirthPlanner(balance,EnemyAffixRegistry.canonical(),EnemyAffinityRegistry.canonical(),
                EnemyNamePools.canonical(),EnemyVisualVariants.canonical());
        assertTrue(bindings.role(ROLE).orElseThrow().productionPromotionEnabled());
        var results=new EnumMap<EnemyRarity,EnemyBirthPlan>(EnemyRarity.class);
        for(int job=0;job<20_000&&results.size()<3;job++) {
            var candidate=fixture(job,worlds.encounters(),catalog,bindings,balance);
            var rarity=planner.rarity(candidate.request().seed(),DifficultyId.NORMAL);
            if(results.containsKey(rarity))continue;
            var request=candidate.request();
            var allSources=new ArrayList<>(candidate.sources());
            if(rarity!=EnemyRarity.NORMAL) {
                var need=rarity==EnemyRarity.CHAMPION?planner.additionalChampionMembers(request):planner.additionalUniqueMembers(request);
                if(need.isEmpty())continue;
                if(need.getAsInt()>0) {
                    var extras=new ArrayList<NativeEnemySpawnGroups.Member>();
                    var extraSources=new ArrayList<EnemyNativeGroupPreparation.MemberSource>();
                    for(int i=0;i<need.getAsInt();i++) {
                        var actor=id("chapter1/wolf/"+job+"/extra/"+i);
                        extras.add(new NativeEnemySpawnGroups.Member(actor,ROLE,
                                new EnemyStaging.State(RELEASE_DEFAULT,candidate.group().reservation().encounter(),actor,1,7)));
                        extraSources.add(source(worlds.encounters(),catalog,actor));
                    }
                    var original=candidate.group();
                    var added=new NativeEnemySpawnGroups.Group(new NativeEnemySpawnGroups.Job(RELEASE_DEFAULT,job,1,ROLE,1,1,
                            extras.size()),original.reservation(),extras,false);
                    request=EnemyNativeGroupPreparation.completeAdditional(original,request,added,extraSources,
                            bindings::role,balance,bindings.revision()).orElseThrow();
                    allSources.addAll(extraSources);
                }
            }
            var result=planner.plan(request);
            if(result.rolledRarity()!=rarity||rarity!=EnemyRarity.NORMAL&&!result.promoted())continue;
            assertEquals(result,planner.plan(request));
            var birth=result.plan();
            assertEquals(RELEASE_DEFAULT,birth.world());
            if(rarity==EnemyRarity.NORMAL) {
                assertNull(birth.pack());
                assertTrue(birth.actors().stream().allMatch(actor->actor.ownAffixes().isEmpty()));
            } else {
                assertNotNull(birth.pack());
                assertEquals(allSources.size(),birth.actors().size());
                assertEquals(allSources.stream().map(s->s.spawn().enemy()).toList(),
                        birth.actors().stream().map(EnemyDescriptor::entityId).toList());
                assertFalse(birth.actors().getFirst().ownAffixes().isEmpty());
                var rootBirth=new EnemyBirthRoot(birth,candidate.sources().stream().map(s->s.spawn()).toList(),
                        allSources.stream().map(s->s.spawn()).toList());
                var records=root.resolve("encounters/"+rarity);
                try(var store=new FileEncounterStore(records)) {
                    assertEquals(birth,store.reserveEnemyBirthRoot(rootBirth));
                    assertEquals(birth,store.reserveEnemyBirthRoot(rootBirth));
                    store.transitionEnemyPack(RELEASE_DEFAULT,birth.pack().packId(),EnemyPackRecord::staged);
                    store.transitionEnemyPack(RELEASE_DEFAULT,birth.pack().packId(),EnemyPackRecord::publish);
                }
                try(var store=new FileEncounterStore(records)) {
                    assertEquals(rootBirth,store.enemyBirthRoot(RELEASE_DEFAULT,birth.encounter()).orElseThrow());
                    assertEquals(birth,store.recoverEnemyWorld(RELEASE_DEFAULT).births().getFirst());
                }
            }
            results.put(rarity,birth);
        }
        assertEquals(EnumSet.of(EnemyRarity.NORMAL,EnemyRarity.CHAMPION,EnemyRarity.UNIQUE),results.keySet());
        assertEquals(EnemyDescriptor.PackRole.LEADER,results.get(EnemyRarity.UNIQUE).actors().getFirst().packRole());
        assertTrue(results.get(EnemyRarity.UNIQUE).actors().stream().skip(1)
                .allMatch(actor->actor.packRole()==EnemyDescriptor.PackRole.MINION));
        assertTrue(results.get(EnemyRarity.CHAMPION).actors().stream()
                .allMatch(actor->actor.packRole()==EnemyDescriptor.PackRole.MEMBER));
    }
}
