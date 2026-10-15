package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.IResourceStorage;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.ChangeStatInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.ChangeStatWithModifierInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.ClearEntityEffectInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytale.patch.NativeMutationHook;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeMutationInteractionsTest {
    private static final class Resources implements IResourceStorage {
        @Override public <T extends Resource<E>,E> java.util.concurrent.CompletableFuture<T> load(
                Store<E> store,ComponentRegistry.Data<E> data,ResourceType<E,T> type){
            return java.util.concurrent.CompletableFuture.completedFuture(data.createResource(type));}
        @Override public <T extends Resource<E>,E> java.util.concurrent.CompletableFuture<Void> save(
                Store<E> store,ComponentRegistry.Data<E> data,ResourceType<E,T> type,T resource){
            return java.util.concurrent.CompletableFuture.completedFuture(null);}
        @Override public <T extends Resource<E>,E> java.util.concurrent.CompletableFuture<Void> remove(
                Store<E> store,ComponentRegistry.Data<E> data,ResourceType<E,T> type){
            return java.util.concurrent.CompletableFuture.completedFuture(null);}
    }
    @Test void protectedNativeStatLeafDeniesBeforeAnyNativeWrite() throws Exception {
        var registry=new ComponentRegistry<EntityStore>();
        var unsafeType=Class.forName("sun.misc.Unsafe");
        var field=unsafeType.getDeclaredField("theUnsafe");field.setAccessible(true);
        var unsafe=unsafeType.getMethod("allocateInstance",Class.class);
        var world=(com.hypixel.hytale.server.core.universe.world.World)unsafe.invoke(field.get(null),
                com.hypixel.hytale.server.core.universe.world.World.class);
        var store=registry.addStore(new EntityStore(world),new Resources());
        try {
            var ref=store.addEntity(registry.newHolder(),AddReason.SPAWN);
            var context=InteractionContext.withoutEntity();
            for(var name:new String[]{"owningEntity","runningForEntity"}){
                var slot=InteractionContext.class.getDeclaredField(name);slot.setAccessible(true);slot.set(context,ref);
            }
            var decisions=new java.util.concurrent.atomic.AtomicInteger();
            try(var policy=NativeMutationHook.install(mutation->{decisions.incrementAndGet();return false;})){
                assertThrows(InteractionManager.ChainCancelledException.class,()->
                        new NativeMutationInteractions.GuardedStat().firstRun(InteractionType.Primary,context,null));
                assertThrows(InteractionManager.ChainCancelledException.class,()->
                        new NativeMutationInteractions.GuardedModifiedStat().firstRun(InteractionType.Primary,context,null));
                assertThrows(InteractionManager.ChainCancelledException.class,()->
                        new NativeMutationInteractions.GuardedClearEffect().firstRun(InteractionType.Primary,context,null));
                assertEquals(3,decisions.get());
                assertTrue(ref.isValid());
                assertEquals(1,store.getEntityCount());
            }
        } finally {registry.shutdown();}
    }
    @Test void exactNativeCodecsDecodeGuardedLeavesAndRestore() throws Exception {
        var codecs=Interaction.CODEC;
        boolean statAbsent=codecs.getClassFor("ChangeStat")==null;
        boolean modifiedAbsent=codecs.getClassFor("ChangeStatWithModifier")==null;
        boolean clearAbsent=codecs.getClassFor("ClearEntityEffect")==null;
        if(statAbsent)codecs.register("ChangeStat",ChangeStatInteraction.class,ChangeStatInteraction.CODEC);
        if(modifiedAbsent)codecs.register("ChangeStatWithModifier",ChangeStatWithModifierInteraction.class,
                ChangeStatWithModifierInteraction.CODEC);
        if(clearAbsent)codecs.register("ClearEntityEffect",ClearEntityEffectInteraction.class,
                ClearEntityEffectInteraction.CODEC);
        try {
            try(var installed=NativeMutationInteractions.install(mutation->false)) {
                assertTrue(NativeMutationHook.installed());
                assertEquals(NativeMutationInteractions.GuardedStat.class,codecs.getClassFor("ChangeStat"));
                assertEquals(NativeMutationInteractions.GuardedModifiedStat.class,
                        codecs.getClassFor("ChangeStatWithModifier"));
                assertEquals(NativeMutationInteractions.GuardedClearEffect.class,
                        codecs.getClassFor("ClearEntityEffect"));
                assertInstanceOf(NativeMutationInteractions.GuardedStat.class,
                        codecs.decode(BsonDocument.parse("{\"Type\":\"ChangeStat\"}"),new ExtraInfo()));
                assertInstanceOf(NativeMutationInteractions.GuardedClearEffect.class,
                        codecs.decode(BsonDocument.parse("{\"Type\":\"ClearEntityEffect\",\"EntityEffectId\":\"Poison\"}"),new ExtraInfo()));
                assertThrows(IllegalStateException.class,()->NativeMutationInteractions.install(mutation->true));
            }
            assertFalse(NativeMutationHook.installed());
            assertEquals(ChangeStatInteraction.class,codecs.getClassFor("ChangeStat"));
            assertEquals(ChangeStatWithModifierInteraction.class,codecs.getClassFor("ChangeStatWithModifier"));
            assertEquals(ClearEntityEffectInteraction.class,codecs.getClassFor("ClearEntityEffect"));
        } finally {
            if(statAbsent)codecs.remove(ChangeStatInteraction.class);
            if(modifiedAbsent)codecs.remove(ChangeStatWithModifierInteraction.class);
            if(clearAbsent)codecs.remove(ClearEntityEffectInteraction.class);
        }
    }
}
