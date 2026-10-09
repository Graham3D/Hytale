package com.inigmasgames.hytalerpg.enemies;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.entity.*;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.execution.hytale.EnemyStaging;
import com.inigmasgames.hytalerpg.execution.hytale.NativeEnemySpawnGroups;
import com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyActorRecovery;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyNativeStagingTest {
    @Test void holderStagingPreservesIndependentNativeFlagsAndItsOwnershipSurvivesCodecReload() throws Exception{
        var registry=new ComponentRegistry<EntityStore>();
        var instance=EntityModule.class.getDeclaredField("instance");instance.setAccessible(true);var previous=instance.get(null);
        try{
            var unsafeType=Class.forName("sun.misc.Unsafe");var field=unsafeType.getDeclaredField("theUnsafe");field.setAccessible(true);
            var module=unsafeType.getMethod("allocateInstance",Class.class).invoke(field.get(null),EntityModule.class);
            var frozen=registry.registerComponent(Frozen.class,Frozen::get);
            var immune=registry.registerComponent(Invulnerable.class,()->Invulnerable.INSTANCE);
            var intangible=registry.registerComponent(Intangible.class,()->Intangible.INSTANCE);
            var uuid=registry.registerComponent(UUIDComponent.class,()->new UUIDComponent(UUID.randomUUID()));
            for(var binding:Map.of("frozenComponentType",frozen,"invulnerableComponentType",immune,
                    "intangibleComponentType",intangible,"uuidComponentType",uuid).entrySet()){
                var slot=EntityModule.class.getDeclaredField(binding.getKey());slot.setAccessible(true);slot.set(module,binding.getValue());
            }
            instance.set(null,module);
            var marker=registry.registerComponent(EnemyStaging.class,EnemyStaging::new);EnemyStaging.bind(marker);
            var identityType=registry.registerComponent(EnemyActorIdentity.class,EnemyActorIdentity::new);EnemyActorIdentity.bind(identityType);
            var world=UUID.randomUUID();var encounter=UUID.randomUUID();
            for(int existing=0;existing<8;existing++){
                var holder=registry.newHolder();var actor=UUID.randomUUID();holder.addComponent(uuid,new UUIDComponent(actor));
                if((existing&1)!=0)holder.addComponent(frozen,Frozen.get());
                if((existing&2)!=0)holder.addComponent(immune,Invulnerable.INSTANCE);
                if((existing&4)!=0)holder.addComponent(intangible,Intangible.INSTANCE);
                var state=EnemyStaging.prepare(holder,world,encounter,7);assertEquals(7^existing,state.addedFlags());
                assertFalse(state.additional());
                assertNotNull(holder.getComponent(frozen));assertNotNull(holder.getComponent(immune));assertNotNull(holder.getComponent(intangible));
                assertEquals(state,EnemyStaging.prepare(holder,world,encounter,7));
                var encoded=EnemyStaging.CODEC.encode(holder.getComponent(marker),new ExtraInfo());
                var decoded=EnemyStaging.CODEC.decode(encoded,new ExtraInfo());assertEquals(state,decoded.state());assertEquals(state,decoded.clone().state());
                assertThrows(IllegalStateException.class,()->EnemyStaging.prepare(holder,world,encounter,8));
                holder.removeComponent(frozen);
                assertEquals(state,EnemyStaging.prepare(holder,world,encounter,7));
                assertNotNull(holder.getComponent(frozen));
            }
            var npcType=registry.registerComponent(NPCEntity.class,()->{throw new AssertionError("Fixture supplies native NPC explicitly");});
            var nativeTypes=EntityModule.class.getDeclaredField("classToComponentType");nativeTypes.setAccessible(true);nativeTypes.set(module,Map.of(NPCEntity.class,npcType));
            var store=registry.addStore(null,new EnemyNativeOutgoingTest.Resources());
            java.util.function.IntFunction<Holder<EntityStore>> member=configuration->{
                try{
                    // Only native identity/provenance accessors are needed, not an initialized NPC role/server.
                    var npc=(NPCEntity)unsafeType.getMethod("allocateInstance",Class.class).invoke(field.get(null),NPCEntity.class);
                    npc.setRoleName("Trork_Warrior");
                    for(var value:Map.of("environmentIndex",3,"spawnConfigurationIndex",configuration,"spawnRoleIndex",4).entrySet()){
                        var slot=NPCEntity.class.getDeclaredField(value.getKey());slot.setAccessible(true);slot.setInt(npc,value.getValue());
                    }
                    var holder=registry.newHolder();holder.addComponent(uuid,new UUIDComponent(UUID.randomUUID()));holder.addComponent(npcType,npc);return holder;
                }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
            };
            var groups=new ArrayList<NativeEnemySpawnGroups.Group>();var reservations=new java.util.concurrent.atomic.AtomicInteger();
            var owner=new NativeEnemySpawnGroups.Owner(){
                public boolean eligible(NativeEnemySpawnGroups.Job job){return true;}
                public Optional<NativeEnemySpawnGroups.Reservation> reserve(NativeEnemySpawnGroups.Job job,UUID first,String nativeRole){
                    assertEquals("Trork_Warrior",nativeRole);
                    reservations.incrementAndGet();return Optional.of(new NativeEnemySpawnGroups.Reservation(world,first,1));
                }
                public void captured(Store<EntityStore> actual,NativeEnemySpawnGroups.Group group){assertSame(store,actual);groups.add(group);}
            };
            var capture=new NativeEnemySpawnGroups.Capture();var job=new NativeEnemySpawnGroups.Job(world,42,4,"Trork_Warrior",3,5,2);
            var first=member.apply(5);var second=member.apply(5);var unrelated=member.apply(Integer.MIN_VALUE);
            // Native FlockSpawnTypes may select another concrete role while
            // retaining the same world-job spawnRoleIndex and configuration.
            second.getComponent(npcType).setRoleName("Trork_Fighter");
            var reloaded=member.apply(5);var reloadedId=reloaded.getComponent(uuid).getUuid();
            var savedIdentity=new EnemyActorIdentity.State(world,encounter,UUID.randomUUID(),reloadedId,reloadedId,7,"Trork_Warrior");
            reloaded.addComponent(identityType,new EnemyActorIdentity(savedIdentity));
            var restored=NativeEnemyActorRecovery.restore(reloaded,world);
            assertEquals(reloadedId,restored.entity());assertNotNull(reloaded.getComponent(marker));
            assertNotNull(reloaded.getComponent(frozen));assertNotNull(reloaded.getComponent(immune));
            assertNotNull(reloaded.getComponent(intangible));
            assertThrows(IllegalStateException.class,()->NativeEnemyActorRecovery.restore(reloaded,UUID.randomUUID()));
            NativeEnemySpawnGroups.capture(job,owner,store,()->{
                capture.onEntityAdd(unrelated,AddReason.SPAWN,store);capture.onEntityAdd(first,AddReason.SPAWN,store);
                capture.onEntityAdd(first,AddReason.SPAWN,store);capture.onEntityAdd(second,AddReason.SPAWN,store);
            });
            assertEquals(1,reservations.get());assertEquals(2,groups.getFirst().members().size());assertFalse(groups.getFirst().nativeFailed());
            assertEquals("Trork_Fighter",groups.getFirst().members().get(1).nativeRole());
            var additional=new ArrayList<NativeEnemySpawnGroups.Group>();
            var third=member.apply(5);var fourth=member.apply(5);
            var extensionJob=new NativeEnemySpawnGroups.Job(world,42,4,"Trork_Warrior",3,5,2);
            NativeEnemySpawnGroups.captureAdditional(extensionJob,groups.getFirst().reservation(),store,()->{
                capture.onEntityAdd(third,AddReason.SPAWN,store);capture.onEntityAdd(fourth,AddReason.SPAWN,store);
            },additional::add);
            assertEquals(1,reservations.get());assertEquals(2,additional.getFirst().members().size());
            assertTrue(additional.getFirst().members().stream().allMatch(value->value.staging().additional()));
            assertEquals(groups.getFirst().reservation(),additional.getFirst().reservation());
            assertFalse(additional.getFirst().nativeFailed());
            var partial=member.apply(5);
            assertThrows(IllegalStateException.class,()->NativeEnemySpawnGroups.captureAdditional(extensionJob,
                    groups.getFirst().reservation(),store,()->{
                        capture.onEntityAdd(partial,AddReason.SPAWN,store);throw new IllegalStateException("native partial failure");
                    },additional::add));
            assertTrue(additional.getLast().nativeFailed());assertEquals(1,additional.getLast().members().size());
            assertTrue(additional.getLast().members().getFirst().staging().additional());
            assertNull(unrelated.getComponent(marker));
            var outside=member.apply(5);capture.onEntityAdd(outside,AddReason.SPAWN,store);assertNull(outside.getComponent(marker));
            var failed=member.apply(5);
            assertThrows(IllegalStateException.class,()->NativeEnemySpawnGroups.capture(job,owner,store,()->{
                capture.onEntityAdd(failed,AddReason.SPAWN,store);throw new IllegalStateException("native spawn failure");
            }));
            assertTrue(groups.getLast().nativeFailed());assertEquals(1,groups.getLast().members().size());
            var afterFailure=member.apply(5);capture.onEntityAdd(afterFailure,AddReason.SPAWN,store);assertNull(afterFailure.getComponent(marker));
            var projection=new com.inigmasgames.hytalerpg.execution.hytale.HytaleDifficultyCombat(record->{});
            var stagedRef=store.addEntity(first,AddReason.SPAWN);
            assertTrue(projection.blocksIncoming(store,stagedRef));
            // A source-less packet needs no mapped channel to be rejected before any native stat write.
            var packet=new com.hypixel.hytale.server.core.modules.entity.damage.Damage(
                    com.hypixel.hytale.server.core.modules.entity.damage.Damage.NULL_SOURCE,0,999999);
            var observed=new java.util.concurrent.atomic.AtomicInteger();
            store.forEachEntityParallel(marker,(i,chunk,buffer)->{
                observed.incrementAndGet();projection.new IncomingProtection().handle(i,chunk,store,buffer,packet);
            });
            assertEquals(1,observed.get());
            assertTrue(packet.isCancelled());assertEquals(999999,packet.getAmount());
        }finally{registry.shutdown();instance.set(null,previous);}
    }
    @Test void invalidPersistedStagingCannotMintAGroup(){
        assertThrows(NullPointerException.class,()->new EnemyStaging.State(null,UUID.randomUUID(),UUID.randomUUID(),0,0));
        assertThrows(IllegalArgumentException.class,()->new EnemyStaging.State(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),-1,0));
        assertTrue(new EnemyStaging.State(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),0,8).additional());
        assertThrows(IllegalArgumentException.class,()->new EnemyStaging.State(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),0,16));
    }
}
