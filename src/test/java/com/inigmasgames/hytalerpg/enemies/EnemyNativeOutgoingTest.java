package com.inigmasgames.hytalerpg.enemies;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.projectile.config.Projectile;
import com.hypixel.hytale.server.core.entity.effect.*;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.execution.hytale.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real native source-effect calculation and ECS component lookup, without a World/server or plugin startup. */
class EnemyNativeOutgoingTest {
    static final DamageCause PHYSICAL=new DamageCause("Physical"),PROJECTILE=new DamageCause("Projectile"),ELEMENTAL=new DamageCause("Elemental"),
            FIRE=new DamageCause("Fire","Elemental",false,false,false);
    static final class Causes extends IndexedLookupTableAssetMap<String,DamageCause>{
        Causes(){super(DamageCause[]::new);}
        void seed(){putAll("OutgoingFixture",DamageCause.CODEC,Map.of("Physical",PHYSICAL,"Projectile",PROJECTILE,"Elemental",ELEMENTAL,"Fire",FIRE),Map.of(),Map.of());}
    }
    static final class Effect extends EntityEffect {
        Effect(String id,Map<DamageCause,ResistanceModifier[]> outgoing,Map<DamageCause,ResistanceModifier[]> incoming){
            super(id);outgoingDamageValues=outgoing;damageResistanceValues=incoming;
        }
    }
    static final class Effects extends IndexedLookupTableAssetMap<String,EntityEffect>{
        Effects(){super(EntityEffect[]::new);}
        void seed(){
            var physical=new ResistanceModifier[]{new ResistanceModifier(ResistanceModifier.ResistanceCalculationType.FLAT,10),
                    new ResistanceModifier(ResistanceModifier.ResistanceCalculationType.PERCENT,.3f)};
            var elemental=new ResistanceModifier[]{new ResistanceModifier(ResistanceModifier.ResistanceCalculationType.PERCENT,.2f)};
            var vulnerability=new ResistanceModifier[]{new ResistanceModifier(ResistanceModifier.ResistanceCalculationType.PERCENT,-.15f)};
            putAll("OutgoingFixture",EntityEffect.CODEC,Map.of("Fixture",new Effect("Fixture",Map.of(PHYSICAL,physical,ELEMENTAL,elemental),
                    Map.of(PHYSICAL,vulnerability,FIRE,elemental))),Map.of(),Map.of());
        }
    }
    static HytaleAssetStore<String,DamageCause,IndexedLookupTableAssetMap<String,DamageCause>> causes;
    static HytaleAssetStore<String,EntityEffect,IndexedLookupTableAssetMap<String,EntityEffect>> effects;
    @BeforeAll static void assets() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});var cm=new Causes();cm.seed();var em=new Effects();em.seed();
        var cb=HytaleAssetStore.builder(DamageCause.class,(IndexedLookupTableAssetMap<String,DamageCause>)cm).setPath("Entity/Damage")
                .setCodec(DamageCause.CODEC).setKeyFunction(DamageCause::getId).setReplaceOnRemove(DamageCause::new);
        causes=new HytaleAssetStore<>(cb){final EventBus events=new EventBus(false);@Override protected EventBus getEventBus(){return events;}};
        var eb=HytaleAssetStore.builder(EntityEffect.class,(IndexedLookupTableAssetMap<String,EntityEffect>)em).setPath("Entity/Effects")
                .setCodec(EntityEffect.CODEC).setKeyFunction(EntityEffect::getId).setReplaceOnRemove(EntityEffect::new);
        effects=new HytaleAssetStore<>(eb){final EventBus events=new EventBus(false);@Override protected EventBus getEventBus(){return events;}};
        AssetRegistry.register(causes);AssetRegistry.register(effects);NativeFixtureAssetCache.clear(DamageCause.class);
    }
    @AfterAll static void cleanup(){if(effects!=null)AssetRegistry.unregister(effects);if(causes!=null)AssetRegistry.unregister(causes);NativeFixtureAssetCache.clear(DamageCause.class);}
    static final class Resources implements IResourceStorage {
        @Override public <T extends Resource<E>,E> CompletableFuture<T> load(Store<E> store,ComponentRegistry.Data<E> data,ResourceType<E,T> type){return CompletableFuture.completedFuture(data.createResource(type));}
        @Override public <T extends Resource<E>,E> CompletableFuture<Void> save(Store<E> store,ComponentRegistry.Data<E> data,ResourceType<E,T> type,T resource){return CompletableFuture.completedFuture(null);}
        @Override public <T extends Resource<E>,E> CompletableFuture<Void> remove(Store<E> store,ComponentRegistry.Data<E> data,ResourceType<E,T> type){return CompletableFuture.completedFuture(null);}
    }
    @Test void capturesNativeFlatPercentAndInheritedSourceEffectsWithoutMutatingSourceOrSubmittingDamage() throws Exception {
        var registry=new ComponentRegistry<EntityStore>();
        var instance=EntityModule.class.getDeclaredField("instance");instance.setAccessible(true);var previous=instance.get(null);
        try{
            var unsafeType=Class.forName("sun.misc.Unsafe");var uf=unsafeType.getDeclaredField("theUnsafe");uf.setAccessible(true);
            var module=unsafeType.getMethod("allocateInstance",Class.class).invoke(uf.get(null),EntityModule.class);
            var type=registry.registerComponent(EffectControllerComponent.class,EffectControllerComponent::new);
            var field=EntityModule.class.getDeclaredField("effectControllerComponentType");field.setAccessible(true);field.set(module,type);instance.set(null,module);
            var store=registry.addStore(null,new Resources());var holder=registry.newHolder();
            var controller=new EffectControllerComponent();int effectIndex=EntityEffect.getAssetMap().getIndex("Fixture");
            controller.getActiveEffects().put(effectIndex,new ActiveEntityEffect("Fixture",effectIndex,true,false));holder.addComponent(type,controller);
            var actor=store.addEntity(holder,AddReason.SPAWN);var adapter=new NativeEnemyOutgoingEffects();
            var input=Map.of(PHYSICAL,100f,FIRE,50f);var snapshot=adapter.snapshot(store,actor,input);
            assertEquals((double)((100f+10)*1.3f),snapshot.get("Physical"));assertEquals((double)(50f*1.2f),snapshot.get("Fire"));
            assertEquals(Map.of(PHYSICAL,100f,FIRE,50f),input);assertEquals(1,controller.getActiveEffects().size());
            var nativePacket=new Damage(new Damage.EntitySource(actor),PHYSICAL,100);
            new DamageSystems.ScaleOutgoingDamageFromEntityEffects().handle(0,null,store,null,nativePacket);
            assertEquals(snapshot.get("Physical"),nativePacket.getAmount());
            // Ordinary packets still delegate to that same native implementation.
            var ordinary=new Damage(new Damage.EntitySource(actor),PHYSICAL,100);adapter.handle(0,null,store,null,ordinary);
            assertEquals(nativePacket.getAmount(),ordinary.getAmount());
            var armor=DamageSystems.ArmorDamageReduction.getResistanceModifiers(null,
                    com.hypixel.hytale.server.core.inventory.container.EmptyItemContainer.INSTANCE,false,null);
            var all=DamageSystems.ArmorDamageReduction.getResistanceModifiers(null,
                    com.hypixel.hytale.server.core.inventory.container.EmptyItemContainer.INSTANCE,false,controller);
            NativeEnemyArmor.projectPhysical(PHYSICAL,all,armor,EnemyNativeDefenseTest.stone(.25,0));
            assertEquals(95,NativeEnemyArmor.applyNative(100,PHYSICAL,all),.00001); // .20 managed + native Rend -.15.
            actionSnapshot(store,actor,controller,adapter);
        }finally{try{registry.shutdown();}finally{instance.set(null,previous);}}
    }
    static void actionSnapshot(Store<EntityStore> store,Ref<EntityStore> actor,EffectControllerComponent controller,
            NativeEnemyOutgoingEffects outgoing) throws Exception {
        var leaf=NativeEnemyDamageInteraction.CODEC.decode(org.bson.BsonDocument.parse("""
                {"RunTime":0.25,"DamageCalculator":{"Type":"Absolute","BaseDamage":{"Physical":100,"Fire":50},"RandomPercentageModifier":0}}
                """),new com.hypixel.hytale.codec.ExtraInfo());
        var id=com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.class.getDeclaredField("id");id.setAccessible(true);id.set(leaf,"FixtureLeaf");
        class Interactions extends IndexedLookupTableAssetMap<String,com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction>{
            Interactions(){super(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction[]::new);}
            void seed(){putAll("Fixture",com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC,Map.of(leaf.getId(),leaf),Map.of(),Map.of());}
            void seedLaunch(com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.LaunchProjectileInteraction launch){
                putAll("Fixture",com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC,
                        Map.of(launch.getId(),launch),Map.of(),Map.of());}
        }
        var map=new Interactions();map.seed();
        var builder=HytaleAssetStore.builder(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.class,
                (IndexedLookupTableAssetMap<String,com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction>)map)
                .setPath("Item/Interactions").setCodec(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC)
                .setKeyFunction(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction::getId)
                .setReplaceOnRemove(ignored->{throw new AssertionError("Fixture assets cannot be removed during an action");});
        var interactions=new HytaleAssetStore<>(builder){final EventBus bus=new EventBus(false);@Override protected EventBus getEventBus(){return bus;}};
        AssetRegistry.register(interactions);NativeFixtureAssetCache.clear(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.class);
        try{
            var context=com.hypixel.hytale.server.core.entity.InteractionContext.withoutEntity();
            for(String fieldName:List.of("runningForEntity","owningEntity")){
                var field=context.getClass().getDeclaredField(fieldName);field.setAccessible(true);field.set(context,actor);
            }
            var root=new com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction("FixtureRoot",leaf.getId());root.build();
            var type=com.hypixel.hytale.protocol.InteractionType.Primary;
            var chain=new com.hypixel.hytale.server.core.entity.InteractionChain(type,context,new com.hypixel.hytale.protocol.InteractionChainData(),root,null,false);
            var fixture=new EnemyAffixSnapshotTest();
            var descriptor=fixture.actor(false,List.of(fixture.affix(EnemyAffixRegistry.Operator.EXTRA_STRONG),fixture.affix(EnemyAffixRegistry.Operator.FIRE_ENCHANTED)),List.of(),null);
            var providers=EnemyAffixSnapshot.resolve(descriptor,fixture.balance,fixture.pack(),0,true,true);
            var binding=new NativeEnemyAction.Binding(descriptor.nativeBindingRevision(),root,type,List.of(new NativeEnemyAction.Strike("hit-0","native-fixture",1,leaf)));
            var certified=NativeEnemyAction.Binding.certifySingle(descriptor.nativeBindingRevision(),root,type,context,
                    "hit-0","native-fixture",EnemyStatusEffects.Source.noNativeStatus(0));
            assertSame(root,certified.root());assertSame(leaf,certified.strikes().getFirst().leaf());
            var active=new java.util.concurrent.atomic.AtomicBoolean(true);
            var action=NativeEnemyAction.capture(store,actor,chain,binding,descriptor,providers,"replay-root-1","birth-seed",2,1,0,outgoing,active::get);
            var snapshot=action.snapshot(leaf);
            assertEquals(((double)((100f+10)*1.3f)+(double)(50f*1.2f))*2*providers.rarityDirectFactor(),snapshot.sourcePower(),.00001);
            controller.getActiveEffects().clear();
            assertSame(snapshot,action.snapshot(leaf)); // Later source mutations cannot change any original victim's vector.
            assertEquals("replay-root-1",snapshot.identity().rootId());assertEquals("hit-0",snapshot.identity().authoredTickId());
            var repeated=new com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction("Repeated",leaf.getId(),leaf.getId());
            var repeatedBinding=new NativeEnemyAction.Binding(descriptor.nativeBindingRevision(),repeated,type,binding.strikes());
            assertThrows(IllegalStateException.class,()->repeatedBinding.validate(context));
            assertThrows(IllegalStateException.class,()->NativeEnemyAction.Binding.certifySingle(descriptor.nativeBindingRevision(),
                    repeated,type,context,"hit-0","native-fixture",EnemyStatusEffects.Source.noNativeStatus(0)));
            var missing=new com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction("Empty");
            assertThrows(IllegalStateException.class,()->new NativeEnemyAction.Binding(descriptor.nativeBindingRevision(),missing,type,binding.strikes()).validate(context));
            projectileAcceptedRoot(store,actor,context,outgoing,map::seedLaunch,descriptor.nativeBindingRevision());
            active.set(false);
            assertThrows(IllegalStateException.class,()->NativeEnemyAction.capture(store,actor,chain,binding,descriptor,providers,"replay-root-2","birth-seed",2,1,0,outgoing,active::get));
        }finally{AssetRegistry.unregister(interactions);NativeFixtureAssetCache.clear(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.class);}
    }
    static void projectileAcceptedRoot(Store<EntityStore> store,Ref<EntityStore> actor,
            com.hypixel.hytale.server.core.entity.InteractionContext context,NativeEnemyOutgoingEffects outgoing,
            java.util.function.Consumer<com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.LaunchProjectileInteraction> addLaunch,
            String revision)throws Exception{
        class Projectiles extends DefaultAssetMap<String,Projectile>{
            void seed(Projectile arrow){putAll("Fixture",Projectile.CODEC,Map.of(arrow.getId(),arrow),Map.of(),Map.of());}
        }
        var ctor=Projectile.class.getDeclaredConstructor();ctor.setAccessible(true);var arrow=ctor.newInstance();
        var projectileId=Projectile.class.getDeclaredField("id");projectileId.setAccessible(true);projectileId.set(arrow,"Skeleton_Scout_Arrow");
        var damage=Projectile.class.getDeclaredField("damage");damage.setAccessible(true);damage.setInt(arrow,13);
        var projectiles=new Projectiles();projectiles.seed(arrow);
        var builder=HytaleAssetStore.builder(Projectile.class,(DefaultAssetMap<String,Projectile>)projectiles)
                .setPath("Projectiles").setCodec(Projectile.CODEC).setKeyFunction(Projectile::getId)
                .setReplaceOnRemove(ignored->{throw new AssertionError("Fixture projectile removed");});
        var assetStore=new HytaleAssetStore<>(builder){final EventBus events=new EventBus(false);
            @Override protected EventBus getEventBus(){return events;}};
        var launch=new com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.LaunchProjectileInteraction();
        var interactionId=com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.class.getDeclaredField("id");
        interactionId.setAccessible(true);interactionId.set(launch,"ScoutFixtureLaunch");
        var launchAsset=launch.getClass().getDeclaredField("projectileId");launchAsset.setAccessible(true);launchAsset.set(launch,arrow.getId());
        // Same fixture asset store as the native melee path; only the original launch operation is added.
        addLaunch.accept(launch);
        var priorCause=DamageCause.PROJECTILE;DamageCause.PROJECTILE=PROJECTILE;
        try{
            AssetRegistry.register(assetStore);
            try{
            var root=new com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction("ScoutFixtureRoot",launch.getId());root.build();
            var type=com.hypixel.hytale.protocol.InteractionType.Primary;
            var binding=NativeEnemyAction.Binding.certifyProjectileSingle(revision,root,type,context,
                    "arrow-0","native-scout-arrow",EnemyStatusEffects.Source.noNativeStatus(0));
            assertEquals(1,binding.projectileStrikes().size());assertTrue(binding.strikes().isEmpty());
            var fixture=new EnemyAffixSnapshotTest();
            var descriptor=fixture.actor(false,List.of(fixture.affix(EnemyAffixRegistry.Operator.EXTRA_STRONG),
                    fixture.affix(EnemyAffixRegistry.Operator.KNOCKBACK)),List.of(),null);
            var providers=EnemyAffixSnapshot.resolve(descriptor,fixture.balance,fixture.pack(),0,true,true);
            var chain=new com.hypixel.hytale.server.core.entity.InteractionChain(type,context,
                    new com.hypixel.hytale.protocol.InteractionChainData(),root,null,false);chain.setChainId(91);
            var accepted=NativeEnemyAction.capture(store,actor,chain,binding,descriptor,providers,
                    "accepted-scout-root-1","birth-seed",1,1,0,outgoing,()->true);
            var projectile=UUID.randomUUID();
            var identity=new com.inigmasgames.hytale.patch.NativeProjectileReceiptHook.Context(descriptor.worldId(),
                    descriptor.entityId(),null,projectile,91,List.of(),root.getId(),root.getId(),chain.getOperationIndex(),0);
            var matched=accepted.projectileLaunch(identity).orElseThrow();
            assertEquals(arrow.getId(),matched.nativeProjectileAsset());assertEquals(13,matched.nativeBaseDamage());
            assertEquals("accepted-scout-root-1",matched.offense().identity().rootId());
            assertTrue(accepted.projectileLaunch(new com.inigmasgames.hytale.patch.NativeProjectileReceiptHook.Context(
                    descriptor.worldId(),descriptor.entityId(),null,UUID.randomUUID(),92,List.of(),root.getId(),root.getId(),
                    chain.getOperationIndex(),0)).isEmpty());
            assertThrows(IllegalStateException.class,()->accepted.projectileLaunch(new com.inigmasgames.hytale.patch.NativeProjectileReceiptHook.Context(
                    descriptor.worldId(),descriptor.entityId(),null,projectile,91,List.of(),root.getId(),root.getId(),
                    chain.getOperationIndex(),1)));
            }finally{AssetRegistry.unregister(assetStore);}
        }finally{DamageCause.PROJECTILE=priorCause;}
    }
    @Test void outgoingAdapterSharesTheProvenReplaceAndRestoreLifecycle() throws Exception {
        try(var fixture=new EnemyNativeDefenseTest.RegistryFixture()){
            var original=new DamageSystems.ScaleOutgoingDamageFromEntityEffects();fixture.registry.registerSystem(original);
            var adapter=new NativeEnemyOutgoingEffects();try(var lease=adapter.install(fixture.registry)){
                assertFalse(fixture.registry.hasSystemClass(original.getClass()));assertTrue(fixture.registry.hasSystemClass(adapter.getClass()));
            }
            assertTrue(fixture.registry.hasSystemClass(original.getClass()));assertFalse(fixture.registry.hasSystemClass(adapter.getClass()));
        }
    }
}
