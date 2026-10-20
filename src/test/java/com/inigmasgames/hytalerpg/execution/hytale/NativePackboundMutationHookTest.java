package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.ChangeStatInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.ChangeStatBaseInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.ChangeStatWithModifierInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.ClearEntityEffectInteraction;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.ValueType;
import com.hypixel.hytale.protocol.ChangeStatBehaviour;
import com.hypixel.hytale.protocol.InteractionType;
import com.inigmasgames.hytale.patch.NativeMutationHook;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePackboundMutationHookTest {
    private static final class CountingEffects extends EffectControllerComponent {
        int writes;
        @Override public void removeEffect(Ref<EntityStore> target,int effect,ComponentAccessor<EntityStore> accessor){writes++;}
        @Override public CountingEffects clone(){return this;}
    }
    private static final class EffectAssets extends IndexedLookupTableAssetMap<String,EntityEffect> {
        EffectAssets(){super(EntityEffect[]::new);}
        void seed(){putAll("PackboundFixture",EntityEffect.CODEC,Map.of("PackboundFixture",new EntityEffect("PackboundFixture")),Map.of(),Map.of());}
    }
    private static final class CountingStats extends EntityStatMap {
        int writes;
        @Override public void processStatChanges(Predictable predictable,it.unimi.dsi.fastutil.ints.Int2FloatMap values,
                ValueType type,ChangeStatBehaviour behavior){writes++;}
        @Override public CountingStats clone(){return this;}
    }
    private static final class Buffer extends CommandBuffer<EntityStore> {
        Buffer(Store<EntityStore> store){super(store);}
    }
    private static final class Resources implements IResourceStorage {
        @Override public <T extends Resource<E>,E> CompletableFuture<T> load(Store<E> store,ComponentRegistry.Data<E> data,ResourceType<E,T> type){
            return CompletableFuture.completedFuture(data.createResource(type));
        }
        @Override public <T extends Resource<E>,E> CompletableFuture<Void> save(Store<E> store,ComponentRegistry.Data<E> data,ResourceType<E,T> type,T resource){
            return CompletableFuture.completedFuture(null);
        }
        @Override public <T extends Resource<E>,E> CompletableFuture<Void> remove(Store<E> store,ComponentRegistry.Data<E> data,ResourceType<E,T> type){
            return CompletableFuture.completedFuture(null);
        }
    }
    @Test void patchedThreeNativeLeavesVerifyWithoutServerStartup()throws Exception{
        var loader=getClass().getClassLoader();
        for(var leaf:new String[]{"ClearEntityEffectInteraction","ChangeStatInteraction","ChangeStatWithModifierInteraction"}){
            var type=Class.forName("com.hypixel.hytale.server.core.modules.interaction.interaction.config.server."+leaf,
                    false,loader);
            assertNotNull(type.getDeclaredMethod("firstRun",com.hypixel.hytale.protocol.InteractionType.class,
                    InteractionContext.class,com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler.class));
        }
    }
    @Test void neutralHookPreservesDefaultAndFreezesThreeLeafDecisions()throws Exception{
        var context=InteractionContext.withoutEntity();
        var target=new Ref<EntityStore>(null);
        var values=new Int2FloatOpenHashMap();values.put(7,12.5f);
        assertTrue(NativeMutationHook.allowClear(context,target,"test-effect"));
        assertTrue(NativeMutationHook.allowStat(context,target,values,null,null));
        var seen=new AtomicReference<NativeMutationHook.Mutation>();
        try(var close=NativeMutationHook.install(mutation->{seen.set(mutation);return false;})){
            assertTrue(NativeMutationHook.installed());
            assertFalse(NativeMutationHook.allowClear(context,target,"test-effect"));
            assertEquals(NativeMutationHook.Kind.CLEAR_ENTITY_EFFECT,seen.get().kind());
            assertEquals("test-effect",seen.get().effectId());
            assertThrows(IllegalStateException.class,()->NativeMutationHook.install(mutation->true));
            assertFalse(NativeMutationHook.allowStat(context,target,values,null,null));
            var frozen=seen.get();assertEquals(NativeMutationHook.Kind.CHANGE_STAT,frozen.kind());
            assertEquals(12.5f,frozen.statValues().get(7));
            values.put(7,99f);assertEquals(12.5f,frozen.statValues().get(7));
            assertThrows(UnsupportedOperationException.class,()->frozen.statValues().put(8,1f));
            assertFalse(NativeMutationHook.allowStatWithModifier(context,target,values,null,null));
            assertEquals(NativeMutationHook.Kind.CHANGE_STAT_WITH_MODIFIER,seen.get().kind());
        }
        assertFalse(NativeMutationHook.installed());
        assertTrue(NativeMutationHook.allowStat(context,target,values,null,null));
    }
    @Test void bridgeKeepsSelfMutationsAndOrdinaryActorsButRejectsExternalStagedMutations()throws Exception{
        var registry=new ComponentRegistry<EntityStore>();
        var slot=EntityModule.class.getDeclaredField("instance");slot.setAccessible(true);var previous=slot.get(null);
        try{
            var unsafeType=Class.forName("sun.misc.Unsafe");var unsafeSlot=unsafeType.getDeclaredField("theUnsafe");
            unsafeSlot.setAccessible(true);
            var module=unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafeSlot.get(null),EntityModule.class);
            var uuidType=registry.registerComponent(UUIDComponent.class,()->new UUIDComponent(UUID.randomUUID()));
            var field=EntityModule.class.getDeclaredField("uuidComponentType");field.setAccessible(true);field.set(module,uuidType);
            slot.set(null,module);
            var stagingType=registry.registerComponent(EnemyStaging.class,EnemyStaging::new);EnemyStaging.bind(stagingType);
            var store=registry.addStore(null,new Resources());
            var targetHolder=registry.newHolder();var targetId=UUID.randomUUID();
            targetHolder.addComponent(uuidType,new UUIDComponent(targetId));
            targetHolder.addComponent(stagingType,new EnemyStaging(new EnemyStaging.State(
                    UUID.randomUUID(),UUID.randomUUID(),targetId,1,0)));
            var target=store.addEntity(targetHolder,AddReason.SPAWN);
            var sourceHolder=registry.newHolder();sourceHolder.addComponent(uuidType,new UUIDComponent(UUID.randomUUID()));
            var source=store.addEntity(sourceHolder,AddReason.SPAWN);
            var combat=new HytaleDifficultyCombat(record->{});
            var own=new NativeMutationHook.Mutation(NativeMutationHook.Kind.CLEAR_ENTITY_EFFECT,target,target,
                    "test-effect",Map.of(),null,null);
            var external=new NativeMutationHook.Mutation(NativeMutationHook.Kind.CLEAR_ENTITY_EFFECT,source,target,
                    "test-effect",Map.of(),null,null);
            assertTrue(PackboundNativeMutationBridge.allow(combat,own));
            assertFalse(PackboundNativeMutationBridge.allow(combat,external));
            store.removeComponent(target,stagingType);
            assertTrue(PackboundNativeMutationBridge.allow(combat,external));
        }finally{registry.shutdown();slot.set(null,previous);}
    }
    @Test void patchedNativeStatLeafCancelsBeforeWriterAndRestoresOrdinaryPath()throws Exception{
        var registry=new ComponentRegistry<EntityStore>();
        var moduleSlot=EntityStatsModule.class.getDeclaredField("instance");moduleSlot.setAccessible(true);
        var priorModule=moduleSlot.get(null);
        try{
            var unsafeType=Class.forName("sun.misc.Unsafe");var unsafeSlot=unsafeType.getDeclaredField("theUnsafe");
            unsafeSlot.setAccessible(true);var unsafe=unsafeSlot.get(null);
            var module=unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafe,EntityStatsModule.class);
            var type=registry.registerComponent(EntityStatMap.class,CountingStats::new);
            var typeSlot=EntityStatsModule.class.getDeclaredField("entityStatMapComponentType");
            typeSlot.setAccessible(true);typeSlot.set(module,type);moduleSlot.set(null,module);
            var entityModuleSlot=EntityModule.class.getDeclaredField("instance");entityModuleSlot.setAccessible(true);
            var priorEntityModule=entityModuleSlot.get(null);
            var entityModule=unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafe,EntityModule.class);
            var armorType=registry.registerComponent(InventoryComponent.Armor.class,InventoryComponent.Armor::new);
            var armorSlot=EntityModule.class.getDeclaredField("armorInventoryComponentType");
            armorSlot.setAccessible(true);armorSlot.set(entityModule,armorType);entityModuleSlot.set(null,entityModule);
            try{
            var store=registry.addStore(null,new Resources());var holder=registry.newHolder();
            var stats=new CountingStats();holder.addComponent(type,stats);
            var target=store.addEntity(holder,AddReason.SPAWN);
            var manager=(InteractionManager)unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafe,InteractionManager.class);
            var bufferSlot=InteractionManager.class.getDeclaredField("commandBuffer");bufferSlot.setAccessible(true);
            bufferSlot.set(manager,new Buffer(store));
            var contextCtor=InteractionContext.class.getDeclaredConstructor(InteractionManager.class,Ref.class,
                    int.class,com.hypixel.hytale.server.core.inventory.container.ItemContainer.class,byte.class,
                    com.hypixel.hytale.server.core.inventory.ItemStack.class);
            contextCtor.setAccessible(true);var context=contextCtor.newInstance(manager,target,0,null,(byte)0,null);
            var values=ChangeStatBaseInteraction.class.getDeclaredField("entityStats");values.setAccessible(true);
            var valueType=ChangeStatBaseInteraction.class.getDeclaredField("valueType");valueType.setAccessible(true);
            var behavior=ChangeStatBaseInteraction.class.getDeclaredField("changeStatBehaviour");behavior.setAccessible(true);
            for(var leaf:List.of(new ChangeStatInteraction(),new ChangeStatWithModifierInteraction())){
                values.set(leaf,new Int2FloatOpenHashMap());valueType.set(leaf,ValueType.Absolute);
                behavior.set(leaf,ChangeStatBehaviour.Add);
                var firstRun=leaf.getClass().getDeclaredMethod("firstRun",InteractionType.class,
                        InteractionContext.class,com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler.class);
                firstRun.setAccessible(true);int before=stats.writes;
                try(var close=NativeMutationHook.install(mutation->false)){
                    var denied=assertThrows(InvocationTargetException.class,()->firstRun.invoke(leaf,InteractionType.Primary,context,null));
                    assertInstanceOf(InteractionManager.ChainCancelledException.class,denied.getCause());
                    assertEquals(before,stats.writes);
                }
                firstRun.invoke(leaf,InteractionType.Primary,context,null);
                assertEquals(before+1,stats.writes);
            }
            }finally{entityModuleSlot.set(null,priorEntityModule);}
        }finally{registry.shutdown();moduleSlot.set(null,priorModule);}
    }
    @Test void patchedNativeEffectClearCancelsBeforeWriterAndRestoresOrdinaryPath()throws Exception{
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var map=new EffectAssets();map.seed();
        var builder=HytaleAssetStore.builder(EntityEffect.class,(IndexedLookupTableAssetMap<String,EntityEffect>)map)
                .setPath("Entity/Effects").setCodec(EntityEffect.CODEC).setKeyFunction(EntityEffect::getId)
                .setReplaceOnRemove(EntityEffect::new);
        var assets=new HytaleAssetStore<>(builder){final EventBus events=new EventBus(false);
            @Override protected EventBus getEventBus(){return events;}};
        AssetRegistry.register(assets);
        var registry=new ComponentRegistry<EntityStore>();
        var moduleSlot=EntityModule.class.getDeclaredField("instance");moduleSlot.setAccessible(true);
        var priorModule=moduleSlot.get(null);
        try{
            var unsafeType=Class.forName("sun.misc.Unsafe");var unsafeSlot=unsafeType.getDeclaredField("theUnsafe");
            unsafeSlot.setAccessible(true);var unsafe=unsafeSlot.get(null);
            var module=unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafe,EntityModule.class);
            var type=registry.registerComponent(EffectControllerComponent.class,CountingEffects::new);
            var typeSlot=EntityModule.class.getDeclaredField("effectControllerComponentType");
            typeSlot.setAccessible(true);typeSlot.set(module,type);moduleSlot.set(null,module);
            var store=registry.addStore(null,new Resources());var holder=registry.newHolder();
            var effects=new CountingEffects();holder.addComponent(type,effects);
            var target=store.addEntity(holder,AddReason.SPAWN);
            var manager=(InteractionManager)unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafe,InteractionManager.class);
            var bufferSlot=InteractionManager.class.getDeclaredField("commandBuffer");bufferSlot.setAccessible(true);
            bufferSlot.set(manager,new Buffer(store));
            var contextCtor=InteractionContext.class.getDeclaredConstructor(InteractionManager.class,Ref.class,
                    int.class,com.hypixel.hytale.server.core.inventory.container.ItemContainer.class,byte.class,
                    com.hypixel.hytale.server.core.inventory.ItemStack.class);
            contextCtor.setAccessible(true);var context=contextCtor.newInstance(manager,target,0,null,(byte)0,null);
            var leaf=new ClearEntityEffectInteraction();
            var effectId=ClearEntityEffectInteraction.class.getDeclaredField("entityEffectId");effectId.setAccessible(true);
            effectId.set(leaf,"PackboundFixture");
            var firstRun=ClearEntityEffectInteraction.class.getDeclaredMethod("firstRun",InteractionType.class,
                    InteractionContext.class,com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler.class);
            firstRun.setAccessible(true);
            try(var close=NativeMutationHook.install(mutation->false)){
                var denied=assertThrows(InvocationTargetException.class,()->firstRun.invoke(leaf,InteractionType.Primary,context,null));
                assertInstanceOf(InteractionManager.ChainCancelledException.class,denied.getCause());
                assertEquals(0,effects.writes);
            }
            firstRun.invoke(leaf,InteractionType.Primary,context,null);
            assertEquals(1,effects.writes);
        }finally{registry.shutdown();moduleSlot.set(null,priorModule);AssetRegistry.unregister(assets);}
    }
}
