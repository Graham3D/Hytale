package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.hytale.EntityStatResourcePort;
import com.inigmasgames.hytalerpg.combat.hytale.GearAppliedHitRecovery;
import com.inigmasgames.hytalerpg.combat.hytale.GearCreditedDeathRecovery;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageLifecycleSystems;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata;
import com.inigmasgames.hytalerpg.combat.hytale.NativeGearRecoveryTick;
import com.inigmasgames.hytalerpg.combat.resource.GearRecoveryRuntime;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import java.util.Objects;
import java.util.UUID;

/** One production recovery owner shared by native receipts, skill roots, reward shares and ticks. */
public final class GearRecoveryBindings {
    private final GearRecoveryRuntime runtime;
    private final GearAppliedHitRecovery applied;
    private final GearCreditedDeathRecovery creditedDeath;
    private final NativeGearRecoveryTick tick;

    public GearRecoveryBindings(RpgCombatKernel kernel,HytaleSupportSystem support,
                                HytaleSummonSystem summons,HytaleBossBarTracker bosses){
        Objects.requireNonNull(kernel);Objects.requireNonNull(support);
        Objects.requireNonNull(summons);Objects.requireNonNull(bosses);
        runtime=new GearRecoveryRuntime();
        applied=new GearAppliedHitRecovery(runtime,new GearAppliedHitRecovery.Eligibility(){
            @Override public boolean hostile(Ref<EntityStore> source,Ref<EntityStore> target,CommandBuffer<EntityStore> buffer){
                if(target==null||!target.isValid()||source==null||!source.isValid())return false;
                Store<EntityStore> store=buffer.getStore();
                if(SupportNativeEffects.control(store,target,bosses).protectedEntity())return false;
                var projection=buffer.getComponent(source,SummonProjection.getComponentType());
                if(projection!=null){
                    var lease=summons.registry().find(projection.token).orElse(null);
                    if(lease==null||!lease.ironSentinel()||lease.entity()==null)return false;
                    var owner=store.getExternalData().getRefFromUUID(lease.owner());
                    return owner!=null&&owner.isValid()&&HytaleAreaQueries.hostile(store,target,owner);
                }
                return buffer.getComponent(source,com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType())!=null
                        &&HytaleAreaQueries.hostile(store,target,source);
            }
            @Override public double normalHealthMaximum(UUID actor,Ref<EntityStore> source,CommandBuffer<EntityStore> buffer){
                var id=buffer.getComponent(source,UUIDComponent.getComponentType());
                return id!=null&&actor.equals(id.getUuid())?GearRecoveryBindings.normalHealthMaximum(buffer.getStore(),source,
                        buffer.getComponent(source,EntityStatMap.getComponentType())):0;
            }
            @Override public double spendableManaMaximum(UUID actor,Ref<EntityStore> source,CommandBuffer<EntityStore> buffer){
                var id=buffer.getComponent(source,UUIDComponent.getComponentType());
                var stats=buffer.getComponent(source,EntityStatMap.getComponentType());
                return id!=null&&actor.equals(id.getUuid())&&stats!=null
                        ?kernel.resources().spendableMaximum(actor,ResourceType.MANA,new EntityStatResourcePort(stats)):0;
            }
            @Override public boolean sentinel(Ref<EntityStore> source,CommandBuffer<EntityStore> buffer){
                return buffer.getComponent(source,SummonProjection.getComponentType())!=null;
            }
            @Override public SentinelAttack sentinelAttack(Ref<EntityStore> source,HytaleDamageMetadata metadata,
                                                          HytaleDamageAdapter.GearHitSource gear,CommandBuffer<EntityStore> buffer){
                var marker=buffer.getComponent(source,SummonProjection.getComponentType());
                if(marker==null)return null;
                var lease=summons.registry().find(marker.token).orElse(null);
                UUID world=buffer.getStore().getExternalData().getWorld().getWorldConfig().getUuid();
                if(lease==null||!lease.ironSentinel()||summons.registry().owned(lease.owner(),world,metadata.actorId()).orElse(null)!=lease
                        ||System.nanoTime()/1e9>=lease.expires()||metadata.origin()!=HytaleDamageMetadata.Origin.DIRECT
                        ||metadata.effectInstanceId()==null||!lease.entity().equals(metadata.actorId())
                        ||!lease.boundItem().identity().equals(gear.hit().itemId())
                        ||!lease.boundEffects().revision().equals(gear.hit().snapshot().revision())
                        ||!lease.boundEffects().items().equals(gear.hit().snapshot().items())
                        ||!gear.hit().rootId().equals(metadata.rootCastId())
                        ||!gear.contactId().equals(metadata.correlationId()))return null;
                String prefix=lease.token()+"/attack/";
                if(!metadata.effectInstanceId().startsWith(prefix))return null;
                String ordinal=metadata.effectInstanceId().substring(prefix.length());
                if(ordinal.isEmpty()||ordinal.length()>9||!ordinal.chars().allMatch(Character::isDigit)
                        ||!metadata.rootCastId().equals(lease.rootCastId()+"/attack/"+ordinal)
                        ||!metadata.correlationId().equals(lease.correlationId()+"/attack/"+ordinal))return null;
                return new SentinelAttack(lease.entity(),lease.boundEffects());
            }
        });
        creditedDeath=new GearCreditedDeathRecovery(runtime);
        tick=new NativeGearRecoveryTick(runtime,applied,kernel.resources(),support::creditSelfRecovery,
                GearRecoveryBindings::normalHealthMaximum);
    }
    /** Register in the existing Application system after BeforeApplication. */
    public HytaleDamageLifecycleSystems.AppliedObserver observer(){return applied;}
    /** Register once in the entity store registry. */
    public NativeGearRecoveryTick tickSystem(){return tick;}
    public GearAppliedHitRecovery committedAttacks(){return applied;}
    public GearCreditedDeathRecovery creditedDeaths(){return creditedDeath;}
    public GearRecoveryRuntime runtime(){return runtime;}
    public void cancel(UUID actor){runtime.cancel(actor);applied.cancel(actor);}
    public void cancelWorld(UUID world){String id=world.toString();runtime.cancelWorld(id);applied.cancelWorld(id);}
    public Death deathSystem(){return new Death(this);}
    public Removal removalSystem(){return new Removal(this);}
    /** Drop unpaid credit at the native death boundary; paid windows remain claimed. */
    public static final class Death extends DeathSystems.OnDeathSystem {
        private final GearRecoveryBindings recovery;
        public Death(GearRecoveryBindings recovery){this.recovery=Objects.requireNonNull(recovery);}
        @Override public Query<EntityStore> getQuery(){return UUIDComponent.getComponentType();}
        @Override public void onComponentAdded(Ref<EntityStore> ref,DeathComponent death,Store<EntityStore> store,
                                               CommandBuffer<EntityStore> buffer){
            var id=buffer.getComponent(ref,UUIDComponent.getComponentType());
            if(id!=null)recovery.cancel(id.getUuid());
        }
    }
    /** Entity removal also covers a Sentinel lease ending without a death component. */
    public static final class Removal extends RefSystem<EntityStore> {
        private final GearRecoveryBindings recovery;
        public Removal(GearRecoveryBindings recovery){this.recovery=Objects.requireNonNull(recovery);}
        @Override public Query<EntityStore> getQuery(){return UUIDComponent.getComponentType();}
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,
                                            CommandBuffer<EntityStore> buffer){}
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,
                                             CommandBuffer<EntityStore> buffer){
            var id=store.getComponent(ref,UUIDComponent.getComponentType());
            if(id!=null)recovery.cancel(id.getUuid());
        }
    }
    public static double normalHealthMaximum(Store<EntityStore> store,Ref<EntityStore> actor,EntityStatMap stats){
        if(actor==null||!actor.isValid()||stats==null)return 0;
        return com.inigmasgames.hytalerpg.combat.hytale.NativeNormalHealthMaximum.value(stats);
    }
}
