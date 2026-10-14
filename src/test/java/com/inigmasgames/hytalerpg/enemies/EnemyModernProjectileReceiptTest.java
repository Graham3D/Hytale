package com.inigmasgames.hytalerpg.enemies;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.projectile.component.ImpactModifiers;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsConfig;
import com.hypixel.hytale.server.core.modules.projectile.component.Projectile;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.*;
import com.inigmasgames.hytalerpg.combat.damage.WeaponDamageExecution;
import com.inigmasgames.hytalerpg.combat.hytale.NativeDamageLeafReceipts;
import com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyProjectileDamage;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Native damage packets and ECS codec boundaries, with no simulated server or mock receipt owner. */
class EnemyModernProjectileReceiptTest {
    private static HytaleAssetStore<String,DamageCause,IndexedLookupTableAssetMap<String,DamageCause>> causes;
    @BeforeAll static void nativeCauses() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        class MapOfCauses extends IndexedLookupTableAssetMap<String,DamageCause>{
            MapOfCauses(){super(DamageCause[]::new);}
            void seed(){putAll("ModernProjectileFixture",DamageCause.CODEC,
                    Map.of("Poison",new DamageCause("Poison"),"Physical",new DamageCause("Physical")),Map.of(),Map.of());}
        }
        var map=new MapOfCauses();map.seed();
        var builder=HytaleAssetStore.builder(DamageCause.class,(IndexedLookupTableAssetMap<String,DamageCause>)map)
                .setPath("Entity/Damage").setCodec(DamageCause.CODEC).setKeyFunction(DamageCause::getId)
                .setReplaceOnRemove(DamageCause::new);
        causes=new HytaleAssetStore<>(builder){final EventBus events=new EventBus(false);
            @Override protected EventBus getEventBus(){return events;}};
        AssetRegistry.register(causes);NativeFixtureAssetCache.clear(DamageCause.class);
    }
    @AfterAll static void removeNativeCauses(){AssetRegistry.unregister(causes);NativeFixtureAssetCache.clear(DamageCause.class);}
    private static EnemyModernProjectileReceipt.State state(String cause,float lower,float upper){
        var world=UUID.randomUUID();var actor=UUID.randomUUID();
        var identity=new WeaponDamageExecution.Identity(world,actor,"accepted-root","native-root","projectile");
        var offense=new EnemyOffenseSnapshot(identity,3,"native-0.7.1","channels","balance",1,
                Map.of(cause,10d),10,Map.of(cause,12d),null,0);
        return new EnemyModernProjectileReceipt.State(UUID.randomUUID(),UUID.randomUUID(),
                "native-config","native-hit","native-miss",cause,lower,upper,offense,1,0);
    }
    @Test void exactHitAndMissClaimCannotReplayNativeDamageOrEffects(){
        var turret=new EnemyModernProjectileReceipt(state("Physical",8,8));
        assertThrows(IllegalStateException.class,()->turret.claimDamage("native-hit"));
        assertTrue(turret.beginImpact("native-hit"));
        assertFalse(turret.beginImpact("native-hit"));
        assertTrue(turret.claimDamage("native-hit"));
        assertFalse(turret.claimDamage("native-hit"));
        var copied=turret.clone();
        assertFalse(copied.beginImpact("native-miss"));
        assertFalse(copied.claimDamage("native-hit"));
        var seeker=new EnemyModernProjectileReceipt(state("Poison",9,11));
        assertTrue(seeker.beginImpact("native-miss"));
        assertThrows(IllegalStateException.class,()->seeker.claimDamage("native-hit"));
        assertFalse(seeker.beginImpact("native-hit"));
        assertThrows(IllegalStateException.class,()->seeker.beginImpact("foreign-root"));
    }
    @Test void decodedOrphanCannotResumeAfterNativeNonserializedProjectileRestart(){
        var launched=new EnemyModernProjectileReceipt(state("Poison",9,11));
        var decoded=EnemyModernProjectileReceipt.CODEC.decode(
                EnemyModernProjectileReceipt.CODEC.encode(launched,new ExtraInfo()),new ExtraInfo());
        assertEquals(launched.state(),decoded.state());
        assertThrows(IllegalStateException.class,()->decoded.beginImpact("native-hit"));
        assertThrows(IllegalStateException.class,()->decoded.claimDamage("native-hit"));
        assertThrows(IllegalStateException.class,()->decoded.clone().beginImpact("native-miss"));
        var original=launched.state();
        assertThrows(IllegalStateException.class,()->original.require(UUID.randomUUID(),original.sourceEntity(),
                original.offense().identity().worldId(),3,"native-0.7.1","native-config"));
        assertThrows(IllegalStateException.class,()->original.require(original.projectile(),UUID.randomUUID(),
                original.offense().identity().worldId(),3,"native-0.7.1","native-config"));
    }
    @Test void nativeScalarIsReplacedOnceAndNonneutralModifiersAreRejected(){
        var seeker=state("Poison",9,11);var poison=new DamageCause("Poison");
        var packet=new Damage(Damage.NULL_SOURCE,poison,10);
        packet.putMetaObject(Damage.INTERACTION_TYPE,InteractionType.ProjectileHit);
        assertEquals(12,NativeEnemyProjectileDamage.requireOriginalModernPacket(seeker,packet));
        assertEquals(10,packet.getAmount());
        NativeDamageLeafReceipts.observeProjectile(packet,ignored->{},seeker.offense(),seeker.projectile());
        assertTrue(NativeDamageLeafReceipts.sourceFactorsResolved(packet));
        assertThrows(IllegalStateException.class,()->NativeDamageLeafReceipts.observeProjectile(packet,ignored->{}));
        var changedCause=new Damage(Damage.NULL_SOURCE,new DamageCause("Physical"),10);
        changedCause.putMetaObject(Damage.INTERACTION_TYPE,InteractionType.ProjectileHit);
        assertThrows(IllegalStateException.class,()->NativeEnemyProjectileDamage.requireOriginalModernPacket(seeker,changedCause));
        var changedAmount=new Damage(Damage.NULL_SOURCE,poison,13);
        changedAmount.putMetaObject(Damage.INTERACTION_TYPE,InteractionType.ProjectileHit);
        assertThrows(IllegalStateException.class,()->NativeEnemyProjectileDamage.requireOriginalModernPacket(seeker,changedAmount));
        var doubled=new Damage(Damage.NULL_SOURCE,poison,10);
        doubled.putMetaObject(Damage.INTERACTION_TYPE,InteractionType.ProjectileHit);
        doubled.putMetaObject(ImpactModifiers.DAMAGE_MODIFIERS,new ImpactModifiers(Map.of("damage",2f),new String[0]));
        assertThrows(IllegalStateException.class,()->NativeEnemyProjectileDamage.requireOriginalModernPacket(seeker,doubled));
    }
    @Test void nativeEcsPacketRequiresExactProjectileAndPhysicsCreator() throws Exception {
        var registry=new ComponentRegistry<EntityStore>();
        var entityInstance=EntityModule.class.getDeclaredField("instance");entityInstance.setAccessible(true);
        var projectileInstance=ProjectileModule.class.getDeclaredField("instance");projectileInstance.setAccessible(true);
        var priorEntity=entityInstance.get(null);var priorProjectile=projectileInstance.get(null);
        try{
            var unsafeClass=Class.forName("sun.misc.Unsafe");var singleton=unsafeClass.getDeclaredField("theUnsafe");
            singleton.setAccessible(true);var unsafe=singleton.get(null);
            var allocate=unsafeClass.getMethod("allocateInstance",Class.class);
            var entityModule=allocate.invoke(unsafe,EntityModule.class);
            var projectileModule=allocate.invoke(unsafe,ProjectileModule.class);
            var uuid=registry.registerComponent(UUIDComponent.class,()->new UUIDComponent(UUID.randomUUID()));
            var marker=registry.registerComponent(Projectile.class,()->Projectile.INSTANCE);
            var physicsType=registry.registerComponent(StandardPhysicsProvider.class,()->
                    new StandardPhysicsProvider(new BoundingBox(),UUID.randomUUID(),StandardPhysicsConfig.DEFAULT,
                            new org.joml.Vector3d(1,0,0),false));
            var uuidField=EntityModule.class.getDeclaredField("uuidComponentType");uuidField.setAccessible(true);
            uuidField.set(entityModule,uuid);entityInstance.set(null,entityModule);
            for(var entry:Map.of("projectileComponentType",marker,"standardPhysicsProviderComponentType",physicsType).entrySet()){
                var field=ProjectileModule.class.getDeclaredField(entry.getKey());field.setAccessible(true);
                field.set(projectileModule,entry.getValue());
            }
            projectileInstance.set(null,projectileModule);
            var store=registry.addStore(null,new EnemyNativeOutgoingTest.Resources());
            var shooterId=UUID.randomUUID();var projectileId=UUID.randomUUID();
            var shooterHolder=registry.newHolder();shooterHolder.addComponent(uuid,new UUIDComponent(shooterId));
            var shooter=store.addEntity(shooterHolder,AddReason.SPAWN);
            var impostorHolder=registry.newHolder();impostorHolder.addComponent(uuid,new UUIDComponent(UUID.randomUUID()));
            var impostor=store.addEntity(impostorHolder,AddReason.SPAWN);
            // The installed NPC spawn route supplies null generatedUUID. Attach identity
            // to the original native projectile after its entity has been created.
            var projectileHolder=registry.newHolder();
            projectileHolder.addComponent(marker,Projectile.INSTANCE);
            projectileHolder.addComponent(physicsType,new StandardPhysicsProvider(new BoundingBox(),shooterId,
                    StandardPhysicsConfig.DEFAULT,new org.joml.Vector3d(1,0,0),false));
            var projectile=store.addEntity(projectileHolder,AddReason.SPAWN);
            assertNull(store.getComponent(projectile,uuid));
            store.putComponent(projectile,uuid,new UUIDComponent(projectileId));
            assertEquals(projectileId,store.getComponent(projectile,uuid).getUuid());
            var receipt=state("Physical",8,8);
            receipt=new EnemyModernProjectileReceipt.State(projectileId,shooterId,receipt.configId(),receipt.hitRoot(),
                    receipt.missRoot(),receipt.nativeCause(),receipt.nativeMinimum(),receipt.nativeMaximum(),
                    receipt.offense(),receipt.forwardX(),receipt.forwardZ());
            var exact=new Damage(new Damage.ProjectileSource(shooter,projectile),new DamageCause("Physical"),8);
            NativeEnemyProjectileDamage.requireModernSource(store,exact,shooter,projectile,receipt);
            var wrongShooter=new Damage(new Damage.ProjectileSource(impostor,projectile),new DamageCause("Physical"),8);
            var saved=receipt;
            assertThrows(IllegalStateException.class,()->NativeEnemyProjectileDamage.requireModernSource(
                    store,wrongShooter,shooter,projectile,saved));
            assertThrows(IllegalStateException.class,()->NativeEnemyProjectileDamage.requireModernSource(
                    store,exact,impostor,projectile,saved));
            var wrongProjectile=new Damage(new Damage.ProjectileSource(shooter,impostor),new DamageCause("Physical"),8);
            assertThrows(IllegalStateException.class,()->NativeEnemyProjectileDamage.requireModernSource(
                    store,wrongProjectile,shooter,projectile,saved));
            store.putComponent(projectile,physicsType,new StandardPhysicsProvider(new BoundingBox(),UUID.randomUUID(),
                    StandardPhysicsConfig.DEFAULT,new org.joml.Vector3d(1,0,0),false));
            assertThrows(IllegalStateException.class,()->NativeEnemyProjectileDamage.requireModernSource(
                    store,exact,shooter,projectile,saved));
            store.removeComponent(projectile,marker);
            assertThrows(IllegalStateException.class,()->NativeEnemyProjectileDamage.requireModernSource(
                    store,exact,shooter,projectile,saved));
        }finally{try{registry.shutdown();}finally{entityInstance.set(null,priorEntity);projectileInstance.set(null,priorProjectile);}}
    }
}
