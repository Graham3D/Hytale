package com.inigmasgames.hytalerpg;

import com.google.gson.JsonParser;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.codecs.map.EnumMapCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.inigmasgames.hytalerpg.execution.hytale.NativeProjectileSpawnConfig;
import java.util.*;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Native codec -> native Interactions constructor contract; companion isolated three-mod test invokes the full native spawn API. */
class Stage13NativeProjectileConstructionTest {
    static Map<InteractionType,String> decodedFireBoltRoots()throws Exception{
        try(var r=new java.io.InputStreamReader(Objects.requireNonNull(Stage13NativeProjectileConstructionTest.class.getResourceAsStream("/Server/ProjectileConfigs/RPG/Projectile_Config_RPG_Fire_Bolt.json")))){
            var json=JsonParser.parseReader(r).getAsJsonObject().getAsJsonObject("Interactions");
            // Same native EnumMapCodec and immutable-map option used by ProjectileConfig.CODEC;
            // value decoder cannot affect the empty-map reproduction.
            return new EnumMapCodec<>(InteractionType.class,Codec.STRING).decode(org.bson.BsonDocument.parse(json.toString()),new ExtraInfo());
        }
    }
    @Test void installedCodecAndConstructorReproduceExactEmptyMapException()throws Exception{
        var roots=decodedFireBoltRoots();assertTrue(roots.isEmpty());assertFalse(roots instanceof EnumMap<?,?>);
        var error=assertThrows(IllegalArgumentException.class,()->new Interactions(roots));assertEquals("Specified map is empty",error.getMessage());
        assertTrue(Arrays.stream(error.getStackTrace()).anyMatch(f->f.getClassName().equals("java.util.EnumMap")));
    }
    @Test void compatibleViewKeepsZeroNativeRootsAndDoesNotMutateConfig()throws Exception{
        var roots=decodedFireBoltRoots();var source=new ProjectileConfig(){@Override public Map<InteractionType,String> getInteractions(){return roots;}};
        var compatible=NativeProjectileSpawnConfig.compatible(source);assertNotSame(source,compatible);
        assertTrue(new Interactions(compatible.getInteractions()).isEmpty());assertSame(roots,source.getInteractions());assertTrue(roots.isEmpty());
        compatible.getInteractions().put(InteractionType.ProjectileHit,"probe");assertTrue(compatible.getInteractions().isEmpty());
        assertSame(source.getPhysicsConfig(),compatible.getPhysicsConfig());assertSame(source.getSpawnOffset(),compatible.getSpawnOffset());
        assertSame(source.getSpawnRotationOffset(),compatible.getSpawnRotationOffset());assertEquals(source.getLaunchForce(),compatible.getLaunchForce());
    }
    @Test void typedEmptyAndNonemptyNativeConfigsDoNotNeedAdaptation(){
        var typed=new ProjectileConfig(){@Override public Map<InteractionType,String> getInteractions(){return new EnumMap<>(InteractionType.class);}};
        assertSame(typed,NativeProjectileSpawnConfig.compatible(typed));
        var populated=new ProjectileConfig(){@Override public Map<InteractionType,String> getInteractions(){return Map.of(InteractionType.ProjectileHit,"NativeRoot");}};
        assertSame(populated,NativeProjectileSpawnConfig.compatible(populated));assertEquals("NativeRoot",new Interactions(populated.getInteractions()).getInteractionId(InteractionType.ProjectileHit));
    }
    @Test void compatibilityViewExplicitlyDelegatesEntireInstalledPublicConfigContract()throws Exception{
        for(var m:ProjectileConfig.class.getDeclaredMethods())if(Modifier.isPublic(m.getModifiers())&&!Modifier.isStatic(m.getModifiers()))
            assertNotNull(NativeProjectileSpawnConfig.class.getDeclaredMethod(m.getName(),m.getParameterTypes()),m.toString());
    }
    @Test void spawnViewCapturesOnlyTheNativePhysicsPreparedHolder(){
        var holder=com.hypixel.hytale.server.core.universe.world.storage.EntityStore.REGISTRY.newHolder();
        var calls=new java.util.concurrent.atomic.AtomicInteger();var velocity=new org.joml.Vector3d(1,2,3);
        var physics=new com.hypixel.hytale.server.core.modules.projectile.config.PhysicsConfig(){
            public void apply(com.hypixel.hytale.component.Holder<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> actual,
                    com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> actor,org.joml.Vector3d v,
                    com.hypixel.hytale.component.ComponentAccessor<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> accessor,boolean predicted){
                assertSame(holder,actual);assertSame(velocity,v);assertNull(actor);assertNull(accessor);assertTrue(predicted);calls.incrementAndGet();
            }
            public com.hypixel.hytale.protocol.PhysicsConfig toPacket(){return null;}
            public double getGravity(){return 3.25;}
        };
        var view=NativeProjectileSpawnConfig.forSpawn(new ProjectileConfig(){@Override public com.hypixel.hytale.server.core.modules.projectile.config.PhysicsConfig getPhysicsConfig(){return physics;}});
        assertThrows(NullPointerException.class,view::preparedHolder);
        view.getPhysicsConfig().apply(holder,null,velocity,null,true);
        assertSame(holder,view.preparedHolder());assertEquals(1,calls.get());assertEquals(3.25,view.getPhysicsConfig().getGravity());assertNull(view.getPhysicsConfig().toPacket());
    }
    @Test void nativePhysicsFailureKeepsOriginalExceptionAndPreciseBoundary(){
        var failure=new IllegalArgumentException("native physics failure");
        var physics=new com.hypixel.hytale.server.core.modules.projectile.config.PhysicsConfig(){
            public void apply(com.hypixel.hytale.component.Holder<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> h,
                    com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> a,org.joml.Vector3d v,
                    com.hypixel.hytale.component.ComponentAccessor<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> c,boolean p){throw failure;}
            public com.hypixel.hytale.protocol.PhysicsConfig toPacket(){return null;}
        };
        var view=NativeProjectileSpawnConfig.forSpawn(new ProjectileConfig(){@Override public com.hypixel.hytale.server.core.modules.projectile.config.PhysicsConfig getPhysicsConfig(){return physics;}});
        assertSame(failure,assertThrows(IllegalArgumentException.class,()->view.getPhysicsConfig().apply(null,null,null,null,false)));
        assertEquals("PHYSICS_COMPONENTS",com.inigmasgames.hytalerpg.execution.hytale.ProjectileSpawnDiagnostics.describe(failure,Map.of()).get("spawnStage"));
        assertThrows(NullPointerException.class,view::preparedHolder);
    }
}
