package com.inigmasgames.hytalerpg.diagnostics;

import com.google.gson.JsonParser;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import com.inigmasgames.hytalerpg.combat.damage.DamageCalculationService;
import com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets;
import com.hypixel.hytale.event.EventBus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CombatDamageTraceServiceTest {
    @TempDir Path directory;
    private static HytaleAssetStore<String,DamageCause,IndexedLookupTableAssetMap<String,DamageCause>> assets;
    private static final class Causes extends IndexedLookupTableAssetMap<String,DamageCause>{
        Causes(){super(DamageCause[]::new);}
        void seed(){putAll("CombatTraceFixture",DamageCause.CODEC,Map.of("Physical",new DamageCause("Physical")),Map.of(),Map.of());}
    }
    @BeforeAll static void setup() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var map=new Causes();map.seed();
        var builder=HytaleAssetStore.builder(DamageCause.class,(IndexedLookupTableAssetMap<String,DamageCause>)map)
                .setPath("Entity/Damage").setCodec(DamageCause.CODEC).setKeyFunction(DamageCause::getId).setReplaceOnRemove(DamageCause::new);
        assets=new HytaleAssetStore<>(builder){final EventBus events=new EventBus(false);
            @Override protected EventBus getEventBus(){return events;}};
        AssetRegistry.register(assets);
    }
    @AfterAll static void cleanup(){if(assets!=null)AssetRegistry.unregister(assets);}
    private static Damage hit(float amount){return new Damage(Damage.NULL_SOURCE,DamageCause.getAssetMap().getAsset("Physical"),amount);}

    @Test void capturesIncomingAndOutgoingAsSeparateNativeHealthReceipts() throws Exception {
        var failures=new ArrayList<Throwable>();var messages=new ArrayList<String>();
        var player=UUID.randomUUID();var goblin=UUID.randomUUID();var world=UUID.randomUUID();
        Path file=directory.resolve("combat-trace.jsonl");
        try(var trace=new CombatDamageTraceService(file,failures::add,messages::add)){
            String session=trace.start(player,world);
            var incoming=hit(100);
            trace.begin(incoming,world,goblin,player,"Goblin_Scrapper",null,90d,90d);
            trace.stage(incoming,"Native armor/resistance","ARMOR",100,80,Map.of("native",true));
            incoming.setAmount(80);
            trace.accepted(incoming);
            trace.stage(incoming,"Cyclone Armor","BARRIER_ABSORB",80,20,Map.of("absorbed",60));
            incoming.setAmount(20);
            trace.beforeApply(incoming,90d);
            trace.applied(incoming,70d);
            var outgoing=hit(120);
            trace.begin(outgoing,world,player,goblin,null,"Goblin_Scrapper",50d,50d);
            trace.accepted(outgoing);trace.beforeApply(outgoing,50d);trace.applied(outgoing,0d);
            var results=trace.last(player,2);
            assertEquals(2,results.size());
            var first=results.get(0);var second=results.get(1);
            assertEquals(session,first.sessionId());assertEquals("INCOMING",first.direction());
            assertEquals(100,first.attempted());assertEquals(80,first.accepted());
            assertEquals(20,first.postAbsorption());assertEquals(60,first.absorbed());
            assertEquals(20,first.healthLost());assertEquals("MATCH",first.reconciliation());
            assertEquals("OUTGOING",second.direction());assertEquals(50,second.healthLost());
            assertEquals(70,second.overkill());assertEquals("MATCH",second.reconciliation());
            assertNotEquals(first.hitId(),second.hitId());assertEquals(2,trace.status(player).events());
            assertEquals(2,messages.size());
        }
        assertTrue(failures.isEmpty());
        var lines=Files.readAllLines(file);assertEquals(2,lines.size());
        var json=JsonParser.parseString(lines.get(0)).getAsJsonObject();
        assertEquals("INCOMING",json.get("direction").getAsString());
        assertEquals(20,json.get("healthLost").getAsDouble());
        assertEquals(5,json.getAsJsonArray("stages").size());
    }

    @Test void disabledAndUnrelatedWorldsHaveNoPerHitCaptureAndMismatchIsExplicit() throws Exception {
        var player=UUID.randomUUID();var enemy=UUID.randomUUID();var world=UUID.randomUUID();
        try(var trace=new CombatDamageTraceService(directory.resolve("combat-trace.jsonl"),e->{},s->{})){
            var disabled=hit(10);trace.begin(disabled,world,enemy,player,"Goblin_Scrapper",null,20d,20d);
            assertFalse(trace.tracked(disabled));
            trace.start(player,world);
            var foreign=hit(10);trace.begin(foreign,UUID.randomUUID(),enemy,player,"Goblin_Scrapper",null,20d,20d);
            assertFalse(trace.tracked(foreign));
            var bad=hit(10);trace.begin(bad,world,enemy,player,"Goblin_Scrapper",null,20d,20d);
            trace.accepted(bad);trace.beforeApply(bad,20d);trace.applied(bad,8d);
            assertEquals("MISMATCH",trace.last(player,1).getFirst().reconciliation());
            assertEquals(1,trace.status(player).reconciliationErrors());
            trace.stop(player);
            var stopped=hit(10);trace.begin(stopped,world,enemy,player,"Goblin_Scrapper",null,20d,20d);
            assertFalse(trace.tracked(stopped));
        }
    }

    @Test void npcProfileOutcomesAreBoundedToActiveWorldSession() throws Exception {
        var world=UUID.randomUUID();var player=UUID.randomUUID();var npc=UUID.randomUUID();
        Path path=directory.resolve("combat-trace.jsonl");
        try(var trace=new CombatDamageTraceService(path,e->{},s->{})){
            trace.start(player,world);
            trace.npcEvent(UUID.randomUUID(),npc,"NOT_APPLIED",Map.of("reason","NO_AUTHORED_PROFILE"));
            trace.npcEvent(world,npc,"NOT_APPLIED",Map.of("reason","NO_AUTHORED_PROFILE"));
            assertEquals(1,trace.status(player).events());
        }
        var lines=Files.readAllLines(path);assertEquals(1,lines.size());
        var row=JsonParser.parseString(lines.getFirst()).getAsJsonObject();
        assertEquals("NPC_PROFILE_NOT_APPLIED",row.get("eventType").getAsString());
        assertEquals("NO_AUTHORED_PROFILE",row.get("reason").getAsString());
    }
    @Test void onePlayerVersusPlayerPacketAppearsInBothPersonalSessionsWithoutDoubleApplying() throws Exception {
        var world=UUID.randomUUID();var attacker=UUID.randomUUID();var defender=UUID.randomUUID();
        try(var trace=new CombatDamageTraceService(directory.resolve("combat-trace.jsonl"),e->{},s->{})){
            trace.start(attacker,world);trace.start(defender,world);
            var damage=hit(12);
            trace.begin(damage,world,attacker,defender,null,null,20d,20d);
            trace.accepted(damage);trace.beforeApply(damage,20d);trace.applied(damage,8d);
            trace.applied(damage,8d); // A duplicate observer callback cannot mint another receipt.
            var outgoing=trace.last(attacker,1).getFirst();var incoming=trace.last(defender,1).getFirst();
            assertEquals("OUTGOING",outgoing.direction());assertEquals("INCOMING",incoming.direction());
            assertEquals(outgoing.rootId(),incoming.rootId());assertEquals(outgoing.contactId(),incoming.contactId());
            assertNotEquals(outgoing.hitId(),incoming.hitId());
            assertEquals(12,outgoing.healthLost());assertEquals(12,incoming.healthLost());
            assertEquals(1,trace.status(attacker).events());assertEquals(1,trace.status(defender).events());
        }
    }
    @Test void authoredSkillHitRetainsExactAttributeAndModifierBucketInputs() throws Exception {
        var world=UUID.randomUUID();var player=UUID.randomUUID();var enemy=UUID.randomUUID();
        try(var trace=new CombatDamageTraceService(directory.resolve("combat-trace.jsonl"),e->{},s->{})){
            trace.start(player,world);
            var damage=hit(62.208f);
            var buckets=new ModifierBuckets(java.util.List.of(.25),java.util.List.of(.05),
                    java.util.List.of(1.2),java.util.List.of(.1));
            var inputs=new HytaleDamageAdapter.OffenseInputs("INT",40,buckets,1.5,.18);
            var result=new DamageCalculationService.Result(20,1.6,32,48,1.296,62.208,false,62.208);
            HytaleDamageAdapter.attachOffenseEvidence(damage,result,inputs);
            trace.begin(damage,world,player,enemy,null,"Goblin_Scrapper",100d,100d);
            trace.accepted(damage);trace.beforeApply(damage,100d);trace.applied(damage,100d-damage.getAmount());
            var stages=trace.last(player,1).getFirst().stages();
            var request=stages.stream().filter(stage->stage.operation().equals("MODIFIER_INPUTS")).findFirst().orElseThrow();
            assertEquals("INT",request.details().get("scalingAttribute"));
            assertEquals(40d,request.details().get("effectiveAttribute"));
            assertEquals(java.util.List.of(.25),request.details().get("increased"));
            assertEquals(java.util.List.of(.05),request.details().get("reduced"));
            assertEquals(java.util.List.of(1.2),request.details().get("more"));
            assertEquals(java.util.List.of(.1),request.details().get("less"));
            assertEquals(1.5,request.details().get("coefficient"));
        }
    }
    @Test void cancelledAndFullyAbsorbedPacketsRemainVisibleWithoutInventedHealthLoss() throws Exception {
        var world=UUID.randomUUID();var player=UUID.randomUUID();var enemy=UUID.randomUUID();
        try(var trace=new CombatDamageTraceService(directory.resolve("combat-trace.jsonl"),e->{},s->{})){
            trace.start(player,world);
            var cancelled=hit(30);trace.begin(cancelled,world,enemy,player,"Goblin_Scrapper",null,80d,80d);
            cancelled.setCancelled(true);trace.accepted(cancelled);trace.beforeApply(cancelled,80d);trace.applied(cancelled,80d);
            var absorbed=hit(40);trace.begin(absorbed,world,enemy,player,"Goblin_Scrapper",null,80d,80d);
            trace.accepted(absorbed);trace.stage(absorbed,"Cyclone Armor","BARRIER_ABSORB",40,0,Map.of("absorbed",40));
            absorbed.setAmount(0);trace.beforeApply(absorbed,80d);trace.applied(absorbed,80d);
            var hits=trace.last(player,2);
            assertTrue(hits.get(0).cancelled());assertEquals(0,hits.get(0).healthLost());
            assertEquals("MATCH",hits.get(0).reconciliation());
            assertEquals(40,hits.get(1).absorbed());assertEquals(0,hits.get(1).healthLost());
            assertEquals("MATCH",hits.get(1).reconciliation());
        }
    }
    @Test void simultaneousPendingHitsRespectTheSessionEventCapacity() throws Exception {
        var world=UUID.randomUUID();var player=UUID.randomUUID();var enemy=UUID.randomUUID();
        try(var trace=new CombatDamageTraceService(directory.resolve("combat-trace.jsonl"),e->{},s->{})){
            trace.start(player,world);
            for(int i=0;i<CombatDamageTraceService.MAX_EVENTS;i++){
                var damage=hit(1);trace.begin(damage,world,enemy,player,"Goblin_Scrapper",null,50d,50d);
                assertTrue(trace.tracked(damage));
            }
            var overflow=hit(1);trace.begin(overflow,world,enemy,player,"Goblin_Scrapper",null,50d,50d);
            assertFalse(trace.tracked(overflow));
            assertFalse(trace.status(player).active());assertEquals(1,trace.status(player).dropped());
        }
    }
    @Test void repeatedStartKeepsTheActiveSessionAndItsPendingHitReceipts() throws Exception {
        var world=UUID.randomUUID();var player=UUID.randomUUID();var enemy=UUID.randomUUID();
        try(var trace=new CombatDamageTraceService(directory.resolve("combat-trace.jsonl"),e->{},s->{})){
            String session=trace.start(player,world);
            var pending=hit(8);trace.begin(pending,world,enemy,player,"Goblin_Scrapper",null,20d,20d);
            assertEquals(session,trace.start(player,world));
            trace.accepted(pending);trace.beforeApply(pending,20d);trace.applied(pending,12d);
            assertEquals(session,trace.last(player,1).getFirst().sessionId());
        }
    }
}
