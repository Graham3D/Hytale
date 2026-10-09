package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage;
import java.util.Map;
import java.util.Set;

/** A single-use per-Damage request, not a global pending-hit map. */
public final class HytaleConditionalDamage {
    private HytaleConditionalDamage(){}
    private static final MetaKey<ConditionalDamage> PENDING=Damage.META_REGISTRY.registerMetaObject(ignored->null,false,"InigmasGames:RpgConditionalGather",null);
    private static final MetaKey<Double> VICTIM_FACTOR=Damage.META_REGISTRY.registerMetaObject(ignored->1d,false,"InigmasGames:RpgVictimCoefficient",null);
    private static final MetaKey<Boolean> GEAR_APPLIED=Damage.META_REGISTRY.registerMetaObject(ignored->false,false,"InigmasGames:GearConditionalApplied",com.hypixel.hytale.codec.Codec.BOOLEAN);
    private static final MetaKey<Double> PRIOR_INCREASED=Damage.META_REGISTRY.registerMetaObject(ignored->0d,false,"InigmasGames:PriorConditionalIncreased",null);
    public static double victimFactor(Damage damage){var value=damage.getIfPresentMetaObject(VICTIM_FACTOR);return value==null?1:value;}
    public static boolean requiresFacing(Damage damage){var value=damage.getIfPresentMetaObject(PENDING);return value!=null&&value.victimCoefficient()==com.inigmasgames.hytalerpg.combat.damage.VictimCoefficient.BACKSTAB;}
    public static void attach(Damage damage,ConditionalDamage request){
        if(request!=null&&request.active())damage.putMetaObject(PENDING,request);
    }
    public static boolean pending(Damage damage){return damage.getIfPresentMetaObject(PENDING)!=null;}
    public static Map<String,Object> gather(Damage damage,double health,double maximum,Set<String> statuses){
        return gather(damage,health,maximum,statuses,null,null);
    }
    public static Map<String,Object> gather(Damage damage,double health,double maximum,Set<String> statuses,
            com.inigmasgames.hytalerpg.execution.math.Vec3 targetForward,com.inigmasgames.hytalerpg.execution.math.Vec3 casterMinusTarget){
        var request=damage.getIfPresentMetaObject(PENDING);if(request==null)return Map.of();
        damage.putMetaObject(PENDING,null); // Consume before any mutation: duplicate invocation cannot amplify.
        var metadata=HytaleDamageAdapter.metadata(damage);
        if(metadata==null||metadata.origin()==HytaleDamageMetadata.Origin.REDIRECTED||damage.isCancelled())return Map.of("conditionalGate","INELIGIBLE_OR_CANCELLED");
        // Installed native mitigation/outgoing-effect scaling is in Filter, after this system.
        // Refuse an unexpected earlier writer rather than overwrite or guess its arithmetic.
        if(Float.compare(damage.getAmount(),(float)request.expectedAmount())!=0){
            damage.setCancelled(true);return Map.of("conditionalGate","NATIVE_GATHER_AMOUNT_CHANGED");
        }
        double victimFactor;
        try { victimFactor=request.victimCoefficient().factor(health,maximum,targetForward,casterMinusTarget); }
        catch(IllegalArgumentException unavailable){damage.setCancelled(true);return Map.of("conditionalGate",unavailable.getMessage());}
        double increased=request.increased(health,maximum,statuses),amount=request.amount(increased)*victimFactor;
        if(!Double.isFinite(amount)||amount>Float.MAX_VALUE){damage.setCancelled(true);return Map.of("conditionalGate","CONDITIONAL_DAMAGE_OVERFLOW");}
        damage.setAmount((float)amount);
        damage.putMetaObject(PRIOR_INCREASED,increased);
        damage.putMetaObject(VICTIM_FACTOR,victimFactor);
        damage.putMetaObject(HytaleDamageAdapter.RPG_METADATA,HytaleDamageAdapter.metadataJson(new HytaleDamageMetadata(metadata.actorId(),metadata.rootCastId(),metadata.skillInstanceId(),metadata.correlationId(),amount,
                Double.isFinite(health)?health:metadata.targetHealthBefore(),metadata.effectInstanceId(),metadata.canProc(),metadata.origin(),metadata.monsterAffix())));
        return Map.of("conditionalGate","RESOLVED","targetConditionalIncreased",increased,"targetHealthAtGather",Double.isFinite(health)?health:"UNAVAILABLE",
                "targetMaxHealthAtGather",Double.isFinite(maximum)?maximum:"UNAVAILABLE","targetControlAtGather",statuses,"conditionalPreMitigation",amount,
                "victimCoefficientRule",request.victimCoefficient().name(),"victimCoefficientFactor",victimFactor);
    }
    /** Native managed contact conditions: one additive Increased bucket at Gather. */
    public static double gearGather(Damage damage,com.inigmasgames.hytalerpg.gear.GearHitConditions.Context facts){
        var source=HytaleDamageAdapter.gearHit(damage);
        if(source==null||damage.isCancelled()||Boolean.TRUE.equals(damage.getIfPresentMetaObject(GEAR_APPLIED))
                ||!com.inigmasgames.hytalerpg.gear.GearHitConditions.present(source.hit()))return 0;
        damage.putMetaObject(GEAR_APPLIED,true);
        double conditional=com.inigmasgames.hytalerpg.gear.GearHitConditions.increased(source.hit(),facts);
        if(conditional==0)return 0;
        var prior=damage.getIfPresentMetaObject(PRIOR_INCREASED);
        double existing=source.hit().increased(source.channel())+(prior==null?0:prior);
        double amount=damage.getAmount()*(1+existing+conditional)/(1+existing);
        if(!Double.isFinite(amount)||amount<0||amount>Float.MAX_VALUE){damage.setCancelled(true);return 0;}
        damage.setAmount((float)amount);
        var old=HytaleDamageAdapter.metadata(damage);
        if(old!=null)damage.putMetaObject(HytaleDamageAdapter.RPG_METADATA,HytaleDamageAdapter.metadataJson(
                new HytaleDamageMetadata(old.actorId(),old.rootCastId(),old.skillInstanceId(),old.correlationId(),
                        amount,old.targetHealthBefore(),old.effectInstanceId(),old.canProc(),old.origin())));
        return conditional;
    }
    /** Reads canonical status, rank and transform owners for native managed contacts. */
    public static final class GearGather extends com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem {
        private static final java.util.Map<String,com.inigmasgames.hytalerpg.progress.ProgressionMath.Rank> RANKS=ranks();
        private static java.util.Map<String,com.inigmasgames.hytalerpg.progress.ProgressionMath.Rank> ranks(){
            var registry=com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry.load();
            var index=new java.util.HashMap<String,com.inigmasgames.hytalerpg.progress.ProgressionMath.Rank>();
            for(var role:registry.roles())index.put(role.roleId(),role.rank());
            for(var alias:registry.aliases()){
                var rank=index.get(alias.canonicalRoleId());if(rank!=null)index.put(alias.roleId(),rank);
            }
            return java.util.Map.copyOf(index);
        }
        private final com.inigmasgames.hytalerpg.combat.status.StatusService statuses;
        /** Periodic source authority and normal Health capacity are supplied by their owning systems. */
        public interface PeriodicFacts { Set<String> active(java.util.UUID victim); }
        public interface NormalMaximum { double value(java.util.UUID victim,
                com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap stats); }
        private final PeriodicFacts periodic;
        private final NormalMaximum normalMaximum;
        public GearGather(com.inigmasgames.hytalerpg.combat.status.StatusService statuses,
                PeriodicFacts periodic,NormalMaximum normalMaximum){
            this.statuses=java.util.Objects.requireNonNull(statuses);
            this.periodic=java.util.Objects.requireNonNull(periodic);
            this.normalMaximum=java.util.Objects.requireNonNull(normalMaximum);
        }
        @Override public com.hypixel.hytale.component.query.Query<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> getQuery(){
            return com.hypixel.hytale.component.query.Query.any();
        }
        @Override public com.hypixel.hytale.component.SystemGroup<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> getGroup(){
            return com.hypixel.hytale.server.core.modules.entity.damage.DamageModule.get().getGatherDamageGroup();
        }
        @Override public java.util.Set<com.hypixel.hytale.component.dependency.Dependency<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>> getDependencies(){
            return java.util.Set.of(
                    new com.hypixel.hytale.component.dependency.SystemDependency<>(com.hypixel.hytale.component.dependency.Order.AFTER,HytaleDamageAdapter.ManagedGearGather.class),
                    new com.hypixel.hytale.component.dependency.SystemDependency<>(com.hypixel.hytale.component.dependency.Order.AFTER,HytaleDamageLifecycleSystems.Gather.class));
        }
        @Override public void handle(int index,com.hypixel.hytale.component.ArchetypeChunk<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> chunk,
                com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store,
                com.hypixel.hytale.component.CommandBuffer<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> buffer,Damage damage){
            var source=HytaleDamageAdapter.gearHit(damage);
            if(source==null||!com.inigmasgames.hytalerpg.gear.GearHitConditions.present(source.hit()))return;
            var identity=chunk.getComponent(index,com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
            var stats=chunk.getComponent(index,com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType());
            var hp=stats==null?null:stats.get(com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getHealth());
            var transform=chunk.getComponent(index,com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
            var at=transform==null?null:transform.getPosition();
            var target=at==null?null:new com.inigmasgames.hytalerpg.execution.math.Vec3(at.x,at.y,at.z);
            var direction=transform==null?null:transform.getRotation().transform(new org.joml.Vector3d(0,0,1));
            var forward=direction==null?null:new com.inigmasgames.hytalerpg.execution.math.Vec3(direction.x,direction.y,direction.z);
            var active=new java.util.HashSet<String>();
            if(identity!=null){
                for(var type:statuses.inspect(identity.getUuid()).active().keySet())
                    if(type!=com.inigmasgames.hytalerpg.combat.status.RpgStatusType.BURN
                            &&type!=com.inigmasgames.hytalerpg.combat.status.RpgStatusType.POISON
                            &&type!=com.inigmasgames.hytalerpg.combat.status.RpgStatusType.BLEED)active.add(type.name());
                active.addAll(periodic.active(identity.getUuid()));
            }
            var effects=chunk.getComponent(index,com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent.getComponentType());
            var stun=com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect.getAssetMap().getAsset("Stun");
            if(effects!=null&&stun!=null&&effects.hasEffect(stun))active.add("STUN");
            var npc=chunk.getComponent(index,com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType());
            var rank=npc==null?null:RANKS.get(npc.getRoleName());
            boolean elite=rank!=null&&rank.ordinal()>=com.inigmasgames.hytalerpg.progress.ProgressionMath.Rank.ELITE.ordinal();
            try {
                double normal=com.inigmasgames.hytalerpg.gear.GearHitConditions.needsNormalMaximum(source.hit())
                        &&identity!=null&&stats!=null?normalMaximum.value(identity.getUuid(),stats):Double.NaN;
                gearGather(damage,new com.inigmasgames.hytalerpg.gear.GearHitConditions.Context(
                    source.hit().origin(),target,forward,hp==null?Double.NaN:hp.get(),
                    normal,active,elite));
            }
            catch(IllegalArgumentException unavailable){damage.setCancelled(true);}
        }
    }
}
