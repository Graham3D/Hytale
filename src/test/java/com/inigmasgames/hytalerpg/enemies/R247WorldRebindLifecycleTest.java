package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.progress.FileEncounterStore;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class R247WorldRebindLifecycleTest {
    @TempDir Path directory;

    /** Reflection lets the identical regression execute on R246 before the lifecycle method exists. */
    private static boolean pending(EnemyWorldAdmission admission,UUID world,Object lifetime) throws Exception {
        Method method=EnemyWorldAdmission.class.getMethod("initialRebindPending",UUID.class,Object.class);
        return (boolean)method.invoke(admission,world,lifetime);
    }

    private static EnemyBirthPlan distinct(EnemyBirthPlan template,UUID world,int index){
        var gson=new Gson();String json=gson.toJson(template);
        json=json.replace(template.world().toString(),world.toString());
        json=json.replace(template.encounter().toString(),UUID.nameUUIDFromBytes(("r247-pack-"+index).getBytes(StandardCharsets.UTF_8)).toString());
        for(int actor=0;actor<template.actors().size();actor++)
            json=json.replace(template.actors().get(actor).entityId().toString(),
                    UUID.nameUUIDFromBytes(("r247-actor-"+index+"-"+actor).getBytes(StandardCharsets.UTF_8)).toString());
        return gson.fromJson(json,EnemyBirthPlan.class);
    }

    @Test void publishedPackReactivationNeverRepeatsStartupInventoryAudit() throws Exception {
        var world=UUID.randomUUID();var template=new EnemyBirthPersistenceTest().plan();var inspections=new AtomicInteger();
        try(var store=new FileEncounterStore(directory)){
            var gate=new EnemyWorldAdmission(w->CompletableFuture.completedStage(store.recoverEnemyWorld(w)),
                    new EnemyPackCapacity(EnemyBalance.canonical()),true);
            gate.begin(world).toCompletableFuture().join();Object initial=gate.lifetime(world);
            assertTrue(pending(gate,world,initial));
            if(pending(gate,world,initial))inspections.incrementAndGet();
            gate.rebindComplete(world,List.of());assertTrue(gate.admits(world));
            for(int i=0;i<3;i++){
                var birth=distinct(template,world,i);var pack=birth.pack();
                assertTrue(gate.reserve(EnemyPackCapacity.Reservation.of(pack)).accepted());
                store.reserveEnemyBirth(birth);
                store.transitionEnemyPack(world,pack.packId(),EnemyPackRecord::staged);
                var published=store.transitionEnemyPack(world,pack.packId(),EnemyPackRecord::publish);
                gate.activatePublished(birth,published,birth.actors().stream().map(EnemyDescriptor::entityId).toList());
                assertTrue(gate.admits(world));
                if(i==0){
                    for(var actor:birth.actors())gate.memberRemoved(birth,actor.entityId(),"UNLOAD");
                    assertEquals(0,gate.activePackReservations(world));
                    var suspended=store.transitionEnemyPack(world,pack.packId(),EnemyPackRecord::suspend);
                    gate.activatePublished(birth,suspended,List.of(birth.actors().getFirst().entityId()));
                    assertEquals(1,gate.activePackReservations(world));
                    gate.activatePublished(birth,suspended,List.of(birth.actors().getLast().entityId()));
                    assertEquals(1,gate.activePackReservations(world));
                    // Production's recovered-actor publication callback must not inspect the
                    // live actor against the startup inventory that predates this birth.
                    if(pending(gate,world,initial))inspections.incrementAndGet();
                    assertEquals(1,inspections.get());
                    assertTrue(gate.admits(world));
                }
            }
            assertEquals(3,store.recoverEnemyWorld(world).births().size());
            assertEquals(3,gate.activePackReservations(world));
            assertEquals(1,inspections.get());
            assertFalse(pending(gate,world,initial));
            gate.worldUnload(world);
            gate.begin(world).toCompletableFuture().join();
            assertFalse(pending(gate,world,initial)); // obsolete callback/lifetime
            assertTrue(pending(gate,world,gate.lifetime(world))); // new world still requires review
        }
    }

    @Test void pendingInitialRecoveryRetriesButQuarantineNeverReopens() throws Exception {
        var world=UUID.randomUUID();var birth=new EnemyBirthPersistenceTest().plan();
        var gate=new EnemyWorldAdmission(w->CompletableFuture.completedStage(
                new FileEncounterStore.EnemyWorldInventory(List.of(birth),List.of(birth.pack()))),
                new EnemyPackCapacity(EnemyBalance.canonical()),true);
        gate.begin(birth.world()).toCompletableFuture().join();Object lifetime=gate.lifetime(birth.world());
        assertTrue(pending(gate,birth.world(),lifetime));
        assertThrows(IllegalStateException.class,()->gate.rebindComplete(birth.world(),List.of()));
        assertTrue(pending(gate,birth.world(),lifetime));
        gate.failClosed(birth.world(),"CORRUPT_IDENTITY",new IllegalStateException("identity"));
        assertFalse(pending(gate,birth.world(),lifetime));
        assertFalse(gate.admits(birth.world()));
    }
}
