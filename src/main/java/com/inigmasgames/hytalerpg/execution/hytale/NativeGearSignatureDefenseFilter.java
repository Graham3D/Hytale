package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.hytale.*;
import com.inigmasgames.hytalerpg.gear.*;
import java.util.*;

/** Ordered native Filter contribution for WA-138 and WA-140. The Defense owner
 * supplies its current pre-break rating and K; this class never guesses armor. */
public final class NativeGearSignatureDefenseFilter extends DamageEventSystem {
    public record Rating(double unbroken,double k){
        public Rating {if(!Double.isFinite(unbroken)||unbroken<0||!Double.isFinite(k)||k<=0)
            throw new IllegalArgumentException("INVALID_DEFENSE_RATING");}
    }
    public interface DefenseView {Optional<Rating> rating(Ref<EntityStore> target,CommandBuffer<EntityStore> buffer);}
    public static double physicalDefenseFactor(Rating rating,double remainingRatingFraction){
        if(!Double.isFinite(remainingRatingFraction)||remainingRatingFraction<0||remainingRatingFraction>1)
            throw new IllegalArgumentException("INVALID_BREAK_FRACTION");
        double ordinary=Math.clamp(rating.unbroken()/(rating.k()+rating.unbroken()),0,.60);
        double brokenRating=rating.unbroken()*remainingRatingFraction;
        double broken=Math.clamp(brokenRating/(rating.k()+brokenRating),0,.60);
        return (1-broken)/(1-ordinary);
    }
    /** The same amount mutation used by the native Filter; never submits child Damage. */
    public static void applyFactor(Damage damage,double factor){
        if(!Double.isFinite(factor)||factor<0)throw new IllegalArgumentException("INVALID_DEFENSE_FACTOR");
        if(damage.isCancelled()||damage.getAmount()<=0||factor==1)return;
        double amount=damage.getAmount()*factor;
        if(!Double.isFinite(amount)||amount<0||amount>Float.MAX_VALUE){damage.setCancelled(true);return;}
        damage.setAmount((float)amount);
    }
    private final GearSignatureProcRuntime runtime;
    private final DefenseView defense;
    public NativeGearSignatureDefenseFilter(GearSignatureProcRuntime runtime,DefenseView defense){
        this.runtime=Objects.requireNonNull(runtime);this.defense=Objects.requireNonNull(defense);
    }
    @Override public Query<EntityStore> getQuery(){return UUIDComponent.getComponentType();}
    @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
            new SystemGroupDependency<>(Order.AFTER,DamageModule.get().getFilterDamageGroup()),
            new SystemDependency<>(Order.BEFORE,HytaleDamageLifecycleSystems.BeforeAbsorption.class),
            new SystemDependency<>(Order.BEFORE,SupportDamageSystems.Shield.class),
            new SystemDependency<>(Order.BEFORE,DamageSystems.ApplyDamage.class));}
    @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                                 CommandBuffer<EntityStore> buffer,Damage damage){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(
                store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.DAMAGE)){
        if(damage.isCancelled()||damage.getAmount()<=0||damage.getCause()==null)return;
        var target=chunk.getReferenceTo(index);
        var source=damage.getSource() instanceof Damage.EntitySource entity?entity.getRef():null;
        if(source==null||!source.isValid()||source.equals(target))return; // environmental exclusion
        var meta=HytaleDamageAdapter.metadata(damage);
        boolean direct=meta!=null?meta.origin()==HytaleDamageMetadata.Origin.DIRECT
                ||meta.origin()==HytaleDamageMetadata.Origin.TRIGGERED
                ||meta.origin()==HytaleDamageMetadata.Origin.REFLECTED:
                damage.getIfPresentMetaObject(Damage.INTERACTION_TYPE)!=null||damage.getSource() instanceof Damage.ProjectileSource;
        if(!direct)return;
        var identity=chunk.getComponent(index,UUIDComponent.getComponentType());
        if(identity==null)return;
        UUID targetId=identity.getUuid();double now=System.nanoTime()/1e9;
        double factor=runtime.incomingHitFactor(targetId,now,true,false);
        var channel=GearCombatEffects.nativeChannel(damage.getCause().getId());
        if(channel==GearCombatEffects.Channel.PHYSICAL){
            double breakFactor=runtime.armorRatingFactor(targetId,now);
            if(breakFactor<1){
                var rating=defense.rating(target,buffer).orElse(null);
                if(rating!=null&&rating.unbroken()>0){
                    factor*=physicalDefenseFactor(rating,breakFactor);
                }
            }
        }
        applyFactor(damage,factor);
        }
    }
}
