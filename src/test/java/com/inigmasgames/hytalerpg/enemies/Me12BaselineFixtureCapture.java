package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.gear.*;
import com.inigmasgames.hytalerpg.progress.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.nio.file.*;
import java.util.*;

/** Manual provenance tool only. Must run with the verified b0844f8a classes BEFORE this checkout on the classpath.
 * It refuses a v2-capable serializer. It never reads a player save or regenerates test resources automatically. */
public final class Me12BaselineFixtureCapture {
    public static void main(String[] args) throws Exception {
        if(Arrays.stream(EnemyDescriptor.class.getRecordComponents()).anyMatch(c->c.getName().equals("v2")))throw new IllegalStateException("BASELINE_V1_BYTECODE_REQUIRED");
        Path output=Path.of(args[0]);Files.createDirectories(output);var json=new GsonBuilder().disableHtmlEscaping().create();var result=new JsonArray();
        var fixture=new EnemyAffixSnapshotTest();var own=List.of(fixture.affix(EnemyAffixRegistry.Operator.EXTRA_STRONG),fixture.affix(EnemyAffixRegistry.Operator.EXTRA_FAST));
        var leader=fixture.actor(false,own,List.of(),null);
        var inherited=own.stream().map(a->EnemyDescriptor.inheritedInstance(fixture.registry.require(a.affixId()),a,fixture.leader,"me.inherit/"+a.affixId()).orElseThrow()).toList();
        add(result,json,leader);add(result,json,fixture.actor(true,List.of(),inherited,null));
        add(result,json,fixture.actor(false,own,List.of(),new EnemyDescriptor.TemplateBirth("fixture-authored",1,2.0,1.4)));
        var root=new EnemyBirthPersistenceTest().root();var source=root.originalSpawns().getFirst();
        try(var store=new FileEncounterStore(output.resolve("accepted-store"))){
            add(result,json,store.create(source));
            add(result,json,store.freeze(new EncounterContributions.DeathPlan(source,Vec3.ZERO,1001,List.of())));
            var player=UUID.fromString("12345678-1234-1234-1234-123456789012");
            var quantity=new GearLootService.QuantityPlan(List.of(UUID.fromString("22345678-1234-1234-1234-123456789012")),leader.immutableRewardContext(),1,
                    new GearClaims.Claim(GearClaims.Policy.solo(player),0,1000),List.of(player),Map.of(player,75.0),"baseline-quantity",1001);
            add(result,json,store.gearTransaction("loot-quantity",source.eventId(),GearLootService.QuantityPlan.class,before->quantity));
            var checkpoint=new RewardCheckpoint(0,1,0,0,Map.of(),RewardLedger.INITIAL);
            add(result,json,RewardIntent.create(player,new EarnedReward("baseline-reward",5,1,Map.of(),"fixture","","","fixture"),checkpoint));
        }
        Files.writeString(output.resolve("legacy-extra-wire.json"),json.toJson(result));
        var vectors=new JsonObject();var random=new GearRandom("master-enemies-v1.1/"+fixture.balance.revision()+"/ME12-baseline-golden");
        vectors.addProperty("rarity",random.stream("me.rarity").nextLong(1000));vectors.addProperty("roster",random.stream("me.minion-count").nextInt(2,6));
        vectors.addProperty("gear",new GearRandom("ME12-baseline-golden").stream("rarity").nextLong(1000000));
        Files.writeString(output.resolve("legacy-rng.json"),json.toJson(vectors));
        byte[] descriptor;try(var in=EnemyDescriptor.class.getResourceAsStream("EnemyDescriptor.class")){descriptor=in.readAllBytes();}
        Files.writeString(output.resolve("provenance.json"),"{\"sourceCommit\":\"b0844f8a013305cffa0b6a9c8912dad571027c3c\",\"descriptorClassSha256\":\""+HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(descriptor))+"\",\"producer\":\"actual verified baseline bytecode; synthetic offline encounters; no active-save input\"}");
    }
    private static void add(JsonArray result,Gson json,Object value){var entry=new JsonObject();entry.addProperty("type",value.getClass().getName());entry.add("payload",json.toJsonTree(value));entry.addProperty("checksum",RewardIntent.digest(json.toJson(value)));result.add(entry);}
}
