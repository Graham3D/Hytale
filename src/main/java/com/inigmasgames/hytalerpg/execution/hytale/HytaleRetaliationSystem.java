package com.inigmasgames.hytalerpg.execution.hytale;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import java.util.*;

/** Observes actual native post-Apply loss, then queues only: never executes a cast inside Damage. */
public final class HytaleRetaliationSystem extends DamageEventSystem {
    private final HytaleSkillExecutionSystem skills;
    private static final com.hypixel.hytale.server.core.meta.MetaKey<Boolean> OBSERVED=Damage.META_REGISTRY.registerMetaObject(ignored->false,false,"InigmasGames:RetaliationObserved",Codec.BOOLEAN);
    public HytaleRetaliationSystem(HytaleSkillExecutionSystem skills){this.skills=skills;}
    @Override public Query<EntityStore> getQuery(){return Query.and(PlayerRef.getComponentType(),EntityStatMap.getComponentType());}
    @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getInspectDamageGroup();}
    @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.AFTER,DamageSystems.ApplyDamage.class),new SystemDependency<>(Order.BEFORE,SupportDamageSystems.Reflect.class));}
    @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.DAMAGE)){
        if(Boolean.TRUE.equals(damage.getIfPresentMetaObject(OBSERVED)))return;damage.putMetaObject(OBSERVED,true);
        var stats=chunk.getComponent(index,EntityStatMap.getComponentType());var hp=stats.get(DefaultEntityStatTypes.getHealth());
        if(hp==null||hp.get()<=hp.getMin()||damage.isCancelled())return;
        double before=SupportDamageSystems.observedHealthBefore(damage);if(!Double.isFinite(before)||before<=hp.get())return;
        var recipient=chunk.getReferenceTo(index);var metadata=HytaleDamageAdapter.metadata(damage);
        boolean hostile=damage.getSource() instanceof Damage.EntitySource source&&source.getRef()!=null&&source.getRef().isValid()
                &&!source.getRef().equals(recipient)&&HytaleAreaQueries.hostile(store,source.getRef(),recipient);
        skills.onRetaliationDamage(chunk.getComponent(index,PlayerRef.getComponentType()).getUuid(),"native-loss-"+UUID.randomUUID(),before,hp.get(),hp.getMax(),hostile,metadata!=null&&metadata.noRetaliation());

        }
    }
}
