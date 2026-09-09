package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.projectile.config.*;
import com.hypixel.hytale.server.core.asset.type.model.config.*;
import java.util.*;

/** 0.7.0-pre.1 Interactions(Map) cannot copy an empty unmodifiable EnumMap.
 * A spawn-local view preserves its enum key type without adding native gameplay roots,
 * editing the asset registry, changing the model/physics, or duplicating native allocation. */
public final class NativeProjectileSpawnConfig extends ProjectileConfig {
    private final ProjectileConfig source;
    private final boolean captureHolder;
    private com.hypixel.hytale.component.Holder<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> preparedHolder;
    private NativeProjectileSpawnConfig(ProjectileConfig source){this(source,false);}
    private NativeProjectileSpawnConfig(ProjectileConfig source,boolean captureHolder){this.source=Objects.requireNonNull(source);this.captureHolder=captureHolder;}
    /** Native spawn queues insertion. Configure its native-created holder, never read the pending Ref through Store. */
    public static NativeProjectileSpawnConfig forSpawn(ProjectileConfig source){return new NativeProjectileSpawnConfig(source,true);}
    public com.hypixel.hytale.component.Holder<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> preparedHolder(){
        return Objects.requireNonNull(preparedHolder,"NATIVE_PROJECTILE_HOLDER_NOT_PREPARED");
    }
    public static ProjectileConfig compatible(ProjectileConfig source){
        Objects.requireNonNull(source);
        return source.getInteractions()!=null&&source.getInteractions().isEmpty()
                &&!(source.getInteractions() instanceof EnumMap<?,?>)?new NativeProjectileSpawnConfig(source):source;
    }
    @Override public Map<InteractionType,String> getInteractions(){return source.getInteractions().isEmpty()?new EnumMap<>(InteractionType.class):source.getInteractions();}
    @Override public String getId(){return source.getId();}
    @Override public PhysicsConfig getPhysicsConfig(){
        if(!captureHolder)return source.getPhysicsConfig();
        return new PhysicsConfig(){
            @Override public void apply(com.hypixel.hytale.component.Holder<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> holder,
                    com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> actor,org.joml.Vector3d velocity,
                    com.hypixel.hytale.component.ComponentAccessor<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> accessor,boolean predicted){
                try{source.getPhysicsConfig().apply(holder,actor,velocity,accessor,predicted);preparedHolder=holder;}
                catch(RuntimeException error){ProjectileSpawnDiagnostics.mark(error,ProjectileSpawnDiagnostics.Stage.PHYSICS_COMPONENTS);throw error;}
            }
            @Override public double getGravity(){return source.getPhysicsConfig().getGravity();}
            @Override public com.hypixel.hytale.protocol.PhysicsConfig toPacket(){return source.getPhysicsConfig().toPacket();}
        };
    }
    @Override public ModelAsset getModelAsset(){return source.getModelAsset();}
    @Override public Model getModel(){return source.getModel();}
    @Override public Model createSpawnModel(Long seed){return source.createSpawnModel(seed);}
    @Override public Model createSpawnModel(Long seed,float scale){return source.createSpawnModel(seed,scale);}
    @Override public double getLaunchForce(){return source.getLaunchForce();}
    @Override public DirectionalHitBoxConfig getDirectionalHitBox(){return source.getDirectionalHitBox();}
    @Override public double getMuzzleVelocity(){return source.getMuzzleVelocity();}
    @Override public double getGravity(){return source.getGravity();}
    @Override public double getVerticalCenterShot(){return source.getVerticalCenterShot();}
    @Override public double getHorizontalCenterShot(){return source.getHorizontalCenterShot();}
    @Override public double getDepthShot(){return source.getDepthShot();}
    @Override public boolean isPitchAdjustShot(){return source.isPitchAdjustShot();}
    @Override public int getLaunchWorldSoundEventIndex(){return source.getLaunchWorldSoundEventIndex();}
    @Override public int getProjectileSoundEventIndex(){return source.getProjectileSoundEventIndex();}
    @Override public org.joml.Vector3f getSpawnOffset(){return source.getSpawnOffset();}
    @Override public boolean isRotateSpawnOffsetByPitch(){return source.isRotateSpawnOffsetByPitch();}
    @Override public boolean isRotateSpawnOffsetByYaw(){return source.isRotateSpawnOffsetByYaw();}
    @Override public com.hypixel.hytale.protocol.Direction getSpawnRotationOffset(){return source.getSpawnRotationOffset();}
    @Override public org.joml.Vector3d getCalculatedOffset(float pitch,float yaw){return source.getCalculatedOffset(pitch,yaw);}
    @Override public com.hypixel.hytale.protocol.ProjectileConfig toPacket(){return source.toPacket();}
}
