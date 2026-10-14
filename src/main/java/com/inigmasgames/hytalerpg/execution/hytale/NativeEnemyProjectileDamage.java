package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.projectile.component.ImpactModifiers;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.inigmasgames.hytale.patch.NativeDamageReceiptHook;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.hytale.NativeDamageLeafReceipts;
import com.inigmasgames.hytalerpg.enemies.*;
import java.util.*;

/** Original native projectile packet only; observes its post-Apply Health through the shared receipt owner. */
public final class NativeEnemyProjectileDamage extends DamageEventSystem {
    private final HytaleDifficultyCombat combat;
    private final NativeEnemyActions actions;
    private final EnemyNativeBindings bindings;
    public NativeEnemyProjectileDamage(HytaleDifficultyCombat combat,NativeEnemyActions actions,EnemyNativeBindings bindings){
        this.combat=Objects.requireNonNull(combat);this.actions=Objects.requireNonNull(actions);
        this.bindings=Objects.requireNonNull(bindings);
    }
    @Override public Query<EntityStore> getQuery(){return Query.any();}
    @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getGatherDamageGroup();}
    @Override public java.util.Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies(){
        return java.util.Set.of(new com.hypixel.hytale.component.dependency.SystemDependency<>(
                com.hypixel.hytale.component.dependency.Order.BEFORE,
                com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageLifecycleSystems.Gather.class));}
    @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
            CommandBuffer<EntityStore> buffer,Damage damage){
            try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.DAMAGE)){
        if(!(damage.getSource() instanceof Damage.ProjectileSource source))return;
        var projectile=source.getProjectile();
        boolean holderAvailable=projectile!=null&&projectile.isValid()&&projectile.getStore()==store;
        var modernType=EnemyModernProjectileReceipt.getComponentType();
        var modern=!holderAvailable||modernType==null?null:buffer.getComponent(projectile,modernType);
        if(modern!=null){handleModern(index,chunk,store,buffer,damage,source,projectile,modern);return;}
        var receiptType=EnemyProjectileReceipt.getComponentType();
        // A missing holder or component registration is also a missing receipt for an ME source.
        // Ordinary native projectiles still take their original route.
        var saved=!holderAvailable||receiptType==null?null:buffer.getComponent(projectile,receiptType);
        if(saved==null){
            // A published projectile actor must not fall back to native unsnapshotted damage after reload
            // or lost holder metadata. Ordinary native shooters never enter this gate.
            var shooter=source.getRef();
            var nativeProjectile=holderAvailable?buffer.getComponent(projectile,ProjectileComponent.getComponentType()):null;
            UUID creator=nativeProjectile==null?null:nativeProjectile.getCreatorUuid();
            boolean publishedRef=shooter!=null&&shooter.isValid()&&shooter.getStore()==store
                    &&buffer.getComponent(shooter,EnemyActorIdentity.getComponentType())!=null;
            var shooterId=publishedRef?buffer.getComponent(shooter,UUIDComponent.getComponentType()):null;
            var world=store.getExternalData().getWorld().getWorldConfig().getUuid();
            if(publishedRef&&shooterId==null){damage.setCancelled(true);actions.quarantineWorld(world);
                throw new IllegalStateException("ENEMY_PROJECTILE_SOURCE_ID_MISSING");}
            if(publishedRef&&creator!=null&&!creator.equals(shooterId.getUuid())){
                damage.setCancelled(true);actions.quarantineWorld(world);
                throw new IllegalStateException("ENEMY_PROJECTILE_CREATOR_SOURCE_MISMATCH");
            }
            var actor=creator==null?java.util.Optional.<HytaleDifficultyCombat.EnemyState>empty():combat.enemyState(world,creator);
            if(actor.isEmpty()&&publishedRef)actor=combat.enemyState(world,shooterId.getUuid());
            if(actor.isEmpty()&&publishedRef){damage.setCancelled(true);actions.quarantineWorld(world);
                throw new IllegalStateException("ENEMY_PROJECTILE_ACTOR_STATE_MISSING");
            }
            if(actor.isPresent())try{
                if(bindings.requireActorRole(actor.get().descriptor()).requiresProjectileReceipt()
                        ||actions.requiresModernReceipt(shooter))
                    throw new IllegalStateException("ENEMY_PROJECTILE_RECEIPT_MISSING");
            }catch(RuntimeException uncertified){
                damage.setCancelled(true);actions.quarantineWorld(world);throw uncertified;
            }
            return;
        }
        try{
            if(damage.isCancelled()||!store.isInThread())
                throw new IllegalStateException("ENEMY_PROJECTILE_NATIVE_PACKET_CHANGED");
            var sourceRef=source.getRef();var victim=chunk.getReferenceTo(index);
            if(sourceRef==null||!sourceRef.isValid()||sourceRef.getStore()!=store||victim==sourceRef)
                throw new IllegalStateException("ENEMY_PROJECTILE_SOURCE_UNAVAILABLE");
            var projectileId=buffer.getComponent(projectile,UUIDComponent.getComponentType());
            var sourceId=buffer.getComponent(sourceRef,UUIDComponent.getComponentType());
            var victimId=chunk.getComponent(index,UUIDComponent.getComponentType());
            var nativeProjectile=buffer.getComponent(projectile,ProjectileComponent.getComponentType());
            var actorIdentity=buffer.getComponent(sourceRef,EnemyActorIdentity.getComponentType());
            if(projectileId==null||sourceId==null||victimId==null||nativeProjectile==null
                    ||nativeProjectile.getProjectile()==null||actorIdentity==null)
                throw new IllegalStateException("ENEMY_PROJECTILE_NATIVE_IDENTITY_MISSING");
            var world=store.getExternalData().getWorld().getWorldConfig().getUuid();
            var descriptor=combat.enemyState(world,sourceId.getUuid()).orElseThrow(
                    ()->new IllegalStateException("ENEMY_PROJECTILE_SOURCE_NOT_PUBLISHED")).descriptor();
            actorIdentity.state().require(descriptor);
            var receipt=saved.state();
            receipt.require(projectileId.getUuid(),sourceId.getUuid(),world,descriptor.encounterGeneration(),
                    descriptor.nativeBindingRevision(),nativeProjectile.getProjectileAssetName());
            float amount=requireOriginalPacket(receipt,nativeProjectile,damage);
            NativeDamageLeafReceipts.observeProjectile(damage,result->{
                try{actions.deliveredProjectile(store,buffer,sourceRef,victim,receipt,result);}
                catch(RuntimeException uncertain){actions.quarantineWorld(world);throw uncertain;}
            },receipt.offense(),projectileId.getUuid());
            damage.setAmount(amount);
        }catch(RuntimeException unsupported){
            damage.setCancelled(true); // Never admit an unsnapshotted ME projectile into native Health.
            actions.quarantineWorld(store.getExternalData().getWorld().getWorldConfig().getUuid());
            throw unsupported;
        }
    
            }}

    private void handleModern(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
            CommandBuffer<EntityStore> buffer,Damage damage,Damage.ProjectileSource source,
            Ref<EntityStore> projectile,EnemyModernProjectileReceipt saved){
        var world=store.getExternalData().getWorld().getWorldConfig().getUuid();
        try{
            if(damage.isCancelled()||!store.isInThread())
                throw new IllegalStateException("ENEMY_MODERN_PROJECTILE_NATIVE_PACKET_CHANGED");
            var sourceRef=source.getRef();var victim=chunk.getReferenceTo(index);
            if(sourceRef==null||!sourceRef.isValid()||sourceRef.getStore()!=store||victim==sourceRef)
                throw new IllegalStateException("ENEMY_MODERN_PROJECTILE_SOURCE_UNAVAILABLE");
            var projectileId=buffer.getComponent(projectile,UUIDComponent.getComponentType());
            var sourceId=buffer.getComponent(sourceRef,UUIDComponent.getComponentType());
            var victimId=chunk.getComponent(index,UUIDComponent.getComponentType());
            var nativeMarker=buffer.getComponent(projectile,
                    com.hypixel.hytale.server.core.modules.projectile.component.Projectile.getComponentType());
            var physics=buffer.getComponent(projectile,StandardPhysicsProvider.getComponentType());
            var actorIdentity=buffer.getComponent(sourceRef,EnemyActorIdentity.getComponentType());
            if(projectileId==null||sourceId==null||victimId==null||nativeMarker==null||physics==null||actorIdentity==null
                    ||!sourceId.getUuid().equals(physics.getCreatorUuid()))
                throw new IllegalStateException("ENEMY_MODERN_PROJECTILE_NATIVE_IDENTITY_MISSING");
            requireModernSource(buffer,damage,sourceRef,projectile,saved.state());
            var descriptor=combat.enemyState(world,sourceId.getUuid()).orElseThrow(
                    ()->new IllegalStateException("ENEMY_MODERN_PROJECTILE_SOURCE_NOT_PUBLISHED")).descriptor();
            actorIdentity.state().require(descriptor);
            var receipt=saved.state();
            receipt.require(projectileId.getUuid(),sourceId.getUuid(),world,descriptor.encounterGeneration(),
                    descriptor.nativeBindingRevision(),receipt.configId());
            String token=NativeEnemyActions.modernReceiptToken(projectileId.getUuid(),victimId.getUuid(),receipt.nativeCause());
            if(!token.equals(NativeDamageReceiptHook.receipt(damage)))
                throw new IllegalStateException("ENEMY_MODERN_PROJECTILE_IMPACT_TOKEN_MISSING");
            var amount=requireOriginalModernPacket(receipt,damage);
            if(!saved.claimDamage(receipt.hitRoot())){
                damage.setCancelled(true);return; // Native duplicate cannot enter Health or produce another receipt.
            }
            NativeDamageLeafReceipts.observeProjectile(damage,result->{
                try{actions.deliveredModernProjectile(store,buffer,sourceRef,victim,receipt,result);}
                catch(RuntimeException uncertain){actions.quarantineWorld(world);throw uncertain;}
            },receipt.offense(),projectileId.getUuid());
            damage.setAmount(amount);
        }catch(RuntimeException unsupported){
            damage.setCancelled(true);actions.quarantineWorld(world);throw unsupported;
        }
    }

    /** Check the actual native packet's shooter and projectile against the ECS owner and receipt. */
    public static void requireModernSource(ComponentAccessor<EntityStore> accessor,Damage damage,
            Ref<EntityStore> shooter,Ref<EntityStore> projectile,EnemyModernProjectileReceipt.State receipt){
        Objects.requireNonNull(accessor);Objects.requireNonNull(receipt);
        if(!(damage.getSource() instanceof Damage.ProjectileSource nativeSource)
                ||shooter==null||projectile==null||!shooter.isValid()||!projectile.isValid()
                ||nativeSource.getRef()!=shooter||nativeSource.getProjectile()!=projectile
                ||shooter.getStore()!=projectile.getStore())
            throw new IllegalStateException("ENEMY_MODERN_PROJECTILE_NATIVE_SOURCE_CHANGED");
        var shooterId=accessor.getComponent(shooter,UUIDComponent.getComponentType());
        var projectileId=accessor.getComponent(projectile,UUIDComponent.getComponentType());
        var marker=accessor.getComponent(projectile,
                com.hypixel.hytale.server.core.modules.projectile.component.Projectile.getComponentType());
        var physics=accessor.getComponent(projectile,StandardPhysicsProvider.getComponentType());
        if(shooterId==null||projectileId==null||marker==null||physics==null
                ||!receipt.sourceEntity().equals(shooterId.getUuid())
                ||!receipt.projectile().equals(projectileId.getUuid())
                ||!receipt.sourceEntity().equals(physics.getCreatorUuid()))
            throw new IllegalStateException("ENEMY_MODERN_PROJECTILE_NATIVE_OWNER_CHANGED");
    }

    /** The native calculator still runs once; only its certified original scalar is replaced. */
    public static float requireOriginalModernPacket(EnemyModernProjectileReceipt.State receipt,Damage damage){
        Objects.requireNonNull(receipt);Objects.requireNonNull(damage);
        if(damage.getCause()==null||!receipt.nativeCause().equals(damage.getCause().getId())
                ||damage.getIfPresentMetaObject(Damage.INTERACTION_TYPE)!=com.hypixel.hytale.protocol.InteractionType.ProjectileHit)
            throw new IllegalStateException("ENEMY_MODERN_PROJECTILE_NATIVE_CAUSE_CHANGED");
        var modifiers=damage.getIfPresentMetaObject(ImpactModifiers.DAMAGE_MODIFIERS);
        NativeEnemyActions.requireNeutralModifiers(modifiers);
        float original=damage.getAmount();
        if(!Float.isFinite(original)||original<receipt.nativeMinimum()-1e-4f
                ||original>receipt.nativeMaximum()+1e-4f)
            throw new IllegalStateException("ENEMY_MODERN_PROJECTILE_NATIVE_DAMAGE_CHANGED");
        var vector=receipt.offense().affixedVector();
        if(!vector.keySet().equals(Set.of(receipt.nativeCause())))
            throw new IllegalStateException("ENEMY_MODERN_PROJECTILE_MULTI_CAUSE_UNSUPPORTED");
        double amount=vector.get(receipt.nativeCause());
        if(!Double.isFinite(amount)||amount<=0||amount>Float.MAX_VALUE)
            throw new IllegalStateException("ENEMY_MODERN_PROJECTILE_FROZEN_AMOUNT_INVALID");
        return (float)amount;
    }

    /** Certify Hytale's one original impact packet before changing its scalar amount. */
    public static float requireOriginalPacket(EnemyProjectileReceipt.State receipt,ProjectileComponent nativeProjectile,Damage damage){
        Objects.requireNonNull(receipt);Objects.requireNonNull(nativeProjectile);Objects.requireNonNull(damage);
        if(damage.getCause()!=DamageCause.PROJECTILE)
            throw new IllegalStateException("ENEMY_PROJECTILE_NATIVE_CAUSE_CHANGED");
        if(!receipt.sourceEntity().equals(nativeProjectile.getCreatorUuid()))
            throw new IllegalStateException("ENEMY_PROJECTILE_NATIVE_CREATOR_CHANGED");
        if(nativeProjectile.getProjectile()==null||nativeProjectile.getProjectile().getDamage()!=receipt.nativeBaseDamage()
                ||Float.compare(damage.getAmount(),(float)receipt.nativeBaseDamage())!=0)
            throw new IllegalStateException("ENEMY_PROJECTILE_NATIVE_DAMAGE_CHANGED");
        var vector=receipt.offense().affixedVector();
        // Legacy Hytale arrow impact owns exactly one scalar packet. Multi-cause routing is a separate gate.
        if(!vector.keySet().equals(Set.of("Projectile")))
            throw new IllegalStateException("ENEMY_PROJECTILE_MULTI_CAUSE_ROUTE_UNSUPPORTED");
        double amount=vector.get("Projectile");
        if(!Double.isFinite(amount)||amount<=0||amount>Float.MAX_VALUE)
            throw new IllegalStateException("ENEMY_PROJECTILE_FROZEN_AMOUNT_INVALID");
        return (float)amount;
    }
}
