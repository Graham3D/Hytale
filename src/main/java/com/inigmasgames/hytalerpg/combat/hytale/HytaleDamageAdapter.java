package com.inigmasgames.hytalerpg.combat.hytale;

import com.google.gson.Gson;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.damage.DamageCalculationService;
import com.inigmasgames.hytalerpg.gear.GearCombatEffects;

/** Only bridge allowed to narrow the kernel's double result and submit it to Hytale Damage. */
public final class HytaleDamageAdapter {
    static final Gson GSON = new Gson();
    public static final MetaKey<String> RPG_METADATA = Damage.META_REGISTRY.registerMetaObject(
            ignored -> "", false, "InigmasGames:HytaleRPGDamage", Codec.STRING);
    // Installed MetaRegistry allows null codec when persistence=false. This context never goes on the wire/save.
    private static final MetaKey<com.inigmasgames.hytalerpg.execution.SkillExecutionContext> EXECUTION_CONTEXT=Damage.META_REGISTRY.registerMetaObject(
            ignored->null,false,"InigmasGames:RpgMeaningfulRoot",null);
    /** Nonpersistent source evidence supplied by an owning execution, never inferred from Damage.amount. */
    public record WeaponComponent(com.inigmasgames.hytalerpg.combat.damage.WeaponFireDecision decision,String componentId) {
        public WeaponComponent { java.util.Objects.requireNonNull(decision).execution().component(componentId); }
    }
    private static final MetaKey<WeaponComponent> WEAPON_COMPONENT=Damage.META_REGISTRY.registerMetaObject(
            ignored->null,false,"InigmasGames:RpgWeaponSourceComponent",null);
    /** One channel event from an immutable native managed hit; all channel events share contactId. */
    public record GearHitSource(GearCombatEffects.Hit hit,String contactId,GearCombatEffects.Channel channel) {}
    private static final MetaKey<GearHitSource> GEAR_HIT=Damage.META_REGISTRY.registerMetaObject(
            ignored->null,false,"InigmasGames:ManagedGearHit",null);
    public record CriticalParts(double ordinary,double critical){
        public CriticalParts {if(!Double.isFinite(ordinary)||!Double.isFinite(critical)||ordinary<0||critical<ordinary)
            throw new IllegalArgumentException("INVALID_CRITICAL_PARTS");}
    }
    private static final MetaKey<CriticalParts> CRITICAL_PARTS=Damage.META_REGISTRY.registerMetaObject(
            ignored->null,false,"InigmasGames:FrozenCriticalParts",null);
    public static GearHitSource gearHit(Damage damage){return damage.getIfPresentMetaObject(GEAR_HIT);}
    /** Skill owners attach these when they know the precrit and critical components. */
    public static void attachCriticalParts(Damage damage,CriticalParts parts){
        if(damage.getIfPresentMetaObject(CRITICAL_PARTS)!=null)throw new IllegalArgumentException("DUPLICATE_CRITICAL_PARTS");
        damage.putMetaObject(CRITICAL_PARTS,java.util.Objects.requireNonNull(parts));
    }
    /** Environmental/native and periodic events are outside item elemental resistance. */
    public static GearCombatEffects.Channel eligibleResistanceChannel(Damage damage){
        var meta=metadata(damage);
        if(meta==null||meta.origin()==HytaleDamageMetadata.Origin.PERIODIC
                ||meta.origin()==HytaleDamageMetadata.Origin.REDIRECTED||damage.getCause()==null
                ||damage.getCause().doesBypassResistances())return null;
        var channel=GearCombatEffects.nativeChannel(damage.getCause().getId());
        return channel==GearCombatEffects.Channel.PHYSICAL?null:channel;
    }
    public static void attachGearHit(Damage damage,GearHitSource source){
        if(source==null||gearHit(damage)!=null)throw new IllegalArgumentException("INVALID_GEAR_HIT_ATTACH");
        damage.putMetaObject(GEAR_HIT,source);
    }
    /** Installed ArmorDamageReduction applies this factor before our Filter. Read its public map once. */
    public static double nativeResistance(com.hypixel.hytale.server.core.universe.world.World world,
            com.hypixel.hytale.server.core.inventory.container.ItemContainer armor,
            boolean penalties,com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent effects,
            DamageCause cause){
        var modifiers=DamageSystems.ArmorDamageReduction.getResistanceModifiers(world,armor,penalties,effects);
        double remaining=1;
        var current=modifiers.get(cause);
        int depth=0;
        while(current!=null){
            if(++depth>16)throw new IllegalStateException("NATIVE_RESISTANCE_PARENT_CYCLE");
            remaining*=Math.max(0,1-current.multiplierModifier);
            current=current.inheritedParentId==null?null:modifiers.get(current.inheritedParentId);
        }
        return Math.max(0,1-remaining);
    }
    public static double combinedResistanceFactor(double nativeResistance,double gearResistance,double penetration){
        if(!Double.isFinite(nativeResistance)||nativeResistance<0||nativeResistance>=1
                ||!Double.isFinite(gearResistance)||gearResistance<0||!Double.isFinite(penetration)||penetration<0)
            throw new IllegalArgumentException("INVALID_COMBINED_RESISTANCE");
        double total=Math.min(.75,nativeResistance+gearResistance);
        return (1-Math.max(0,total-penetration))/(1-nativeResistance);
    }
    /** Execute before HytaleDamageLifecycleSystems.Gather; native leaf creates the Damage events. */
    public static final class ManagedGearGather extends com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem {
        @Override public com.hypixel.hytale.component.query.Query<EntityStore> getQuery(){return com.hypixel.hytale.component.query.Query.any();}
        @Override public com.hypixel.hytale.component.SystemGroup<EntityStore> getGroup(){
            return com.hypixel.hytale.server.core.modules.entity.damage.DamageModule.get().getGatherDamageGroup();
        }
        @Override public java.util.Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies(){
            return java.util.Set.of(new com.hypixel.hytale.component.dependency.SystemDependency<>(
                    com.hypixel.hytale.component.dependency.Order.BEFORE,HytaleDamageLifecycleSystems.Gather.class));
        }
        @Override public void handle(int index,com.hypixel.hytale.component.ArchetypeChunk<EntityStore> chunk,
                com.hypixel.hytale.component.Store<EntityStore> store,
                com.hypixel.hytale.component.CommandBuffer<EntityStore> buffer,Damage damage){
            if(!(damage.getSource() instanceof Damage.EntitySource source)||source.getRef()==null
                    ||damage.getCause()==null)return;
            var actor=source.getRef();
            var player=buffer.getComponent(actor,com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
            if(player==null)return;
            var hit=com.inigmasgames.hytalerpg.gear.ManagedGearDamageInteraction.claim(damage,player.getUuid());
            if(hit==null)hit=com.inigmasgames.hytalerpg.gear.ManagedCarrierDamageInteraction.claim(damage,player.getUuid());
            if(hit==null)return;
            if(damage.isCancelled()||metadata(damage)!=null)return;
            var channel=GearCombatEffects.nativeChannel(damage.getCause().getId());
            if(channel==null||hit.amount(channel)<=0)return;
            var victim=chunk.getComponent(index,com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
            if(victim==null)return;
            String contact=hit.rootId()+"/"+victim.getUuid();
            attachGearHit(damage,new GearHitSource(hit,contact,channel));
            var stats=chunk.getComponent(index,EntityStatMap.getComponentType());
            var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
            if(hp==null)return;
            boolean child=com.inigmasgames.hytalerpg.gear.ManagedGearDamageInteraction.noProcChild(hit);
            var meta=new HytaleDamageMetadata(player.getUuid(),hit.rootId(),"managed-gear",contact,
                    damage.getAmount(),hp.get(),contact,!child,child?HytaleDamageMetadata.Origin.TRIGGERED:HytaleDamageMetadata.Origin.DIRECT);
            damage.putMetaObject(RPG_METADATA,metadataJson(meta));
        }
    }
    /** Live valid-equipment resistance and frozen critical excess, after native mitigation and before shields. */
    public static final class GearResistanceFilter extends com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem {
        private static final MetaKey<Boolean> APPLIED=Damage.META_REGISTRY.registerMetaObject(
                ignored->false,false,"InigmasGames:GearResistanceApplied",Codec.BOOLEAN);
        @Override public com.hypixel.hytale.component.query.Query<EntityStore> getQuery(){
            return com.hypixel.hytale.component.query.Query.any();
        }
        @Override public java.util.Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies(){
            return java.util.Set.of(
                    new com.hypixel.hytale.component.dependency.SystemGroupDependency<>(
                            com.hypixel.hytale.component.dependency.Order.AFTER,
                            com.hypixel.hytale.server.core.modules.entity.damage.DamageModule.get().getFilterDamageGroup()),
                    new com.hypixel.hytale.component.dependency.SystemDependency<>(
                            com.hypixel.hytale.component.dependency.Order.BEFORE,
                            com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.Shield.class),
                    new com.hypixel.hytale.component.dependency.SystemDependency<>(
                            com.hypixel.hytale.component.dependency.Order.BEFORE,
                            com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems.ApplyDamage.class));
        }
        @Override public void handle(int index,com.hypixel.hytale.component.ArchetypeChunk<EntityStore> chunk,
                com.hypixel.hytale.component.Store<EntityStore> store,
                com.hypixel.hytale.component.CommandBuffer<EntityStore> buffer,Damage damage){
            if(damage.isCancelled()||damage.getAmount()<=0||Boolean.TRUE.equals(damage.getIfPresentMetaObject(APPLIED)))return;
            var meta=metadata(damage);if(meta==null||meta.origin()==HytaleDamageMetadata.Origin.PERIODIC
                    ||meta.origin()==HytaleDamageMetadata.Origin.REDIRECTED)return;
            var channel=eligibleResistanceChannel(damage);
            var hit=gearHit(damage);
            CriticalParts parts=damage.getIfPresentMetaObject(CRITICAL_PARTS);
            if(parts==null&&hit!=null&&hit.hit().critical()){
                double total=hit.hit().amount(hit.channel());
                parts=new CriticalParts(total/hit.hit().criticalMultiplier(),total);
            }
            if(channel==null&&parts==null)return;
            var target=chunk.getReferenceTo(index);
            boolean player=buffer.getComponent(target,
                    com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType())!=null;
            // The summon guard already owns native incoming resistance. Only qualified
            // critical parts use the shared recipient snapshot for a native actor.
            if(!player&&parts==null)return;
            var defense=com.inigmasgames.hytalerpg.gear.GearNativeItems.recipientEffects(target,buffer);
            if(!player&&defense.empty())return;
            applyResolved(damage,defense,player,()->{
                var armor=buffer.getComponent(target,com.hypixel.hytale.server.core.inventory.InventoryComponent.Armor.getComponentType());
                var inventory=armor==null?com.hypixel.hytale.server.core.inventory.container.EmptyItemContainer.INSTANCE:armor.getInventory();
                var effects=chunk.getComponent(index,com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent.getComponentType());
                return nativeResistance(store.getExternalData().getWorld(),inventory,
                        com.hypixel.hytale.server.core.entity.ItemUtils.canApplyItemStackPenalties(target,buffer),effects,damage.getCause());
            });
        }
        /** Final write of the live recipient filter, also usable with isolated native Damage components. */
        public static void applyResolved(Damage damage,com.inigmasgames.hytalerpg.gear.GearEffectSnapshot defense,
                                         boolean player,java.util.function.DoubleSupplier nativeResistance) {
            if(damage.isCancelled()||damage.getAmount()<=0||Boolean.TRUE.equals(damage.getIfPresentMetaObject(APPLIED)))return;
            var meta=metadata(damage);if(meta==null||meta.origin()==HytaleDamageMetadata.Origin.PERIODIC
                    ||meta.origin()==HytaleDamageMetadata.Origin.REDIRECTED)return;
            var channel=eligibleResistanceChannel(damage);
            var hit=gearHit(damage);
            CriticalParts parts=damage.getIfPresentMetaObject(CRITICAL_PARTS);
            if(parts==null&&hit!=null&&hit.hit().critical()) {
                double total=hit.hit().amount(hit.channel());
                parts=new CriticalParts(total/hit.hit().criticalMultiplier(),total);
            }
            if(channel==null&&parts==null||!player&&(parts==null||defense.empty()))return;
            double amount=damage.getAmount();
            if(parts!=null&&parts.critical()>0)amount*=com.inigmasgames.hytalerpg.gear.GearDefenseEffects
                    .criticalAmount(defense,parts.ordinary(),parts.critical())/parts.critical();
            if(player&&channel!=null){
                double penetration=hit==null?0:hit.hit().penetration(channel);
                double gearR=defense.percent("WA-078")+defense.percent(switch(channel){
                            case WIND->"WA-072";case WATER->"WA-073";case FIRE->"WA-074";
                            case EARTH->"WA-075";case LIGHTNING->"WA-076";case VOID->"WA-077";
                            case PHYSICAL->throw new AssertionError(channel);
                        });
                if(gearR>0||penetration>0){
                    double nativeR=nativeResistance.getAsDouble();
                    if(nativeR<1)amount*=combinedResistanceFactor(nativeR,gearR,penetration);
                }
            }
            if(!Double.isFinite(amount)||amount<0||amount>Float.MAX_VALUE){damage.setCancelled(true);return;}
            damage.setAmount((float)amount);damage.putMetaObject(APPLIED,true);
        }
    }
    public static WeaponComponent weaponComponent(Damage damage){return damage.getIfPresentMetaObject(WEAPON_COMPONENT);}
    public static void attachWeaponComponent(Damage damage,HytaleDamageMetadata metadata,WeaponComponent witness){
        var identity=witness.decision().execution().identity();
        if(!identity.actorId().equals(metadata.actorId())||!identity.rootId().equals(metadata.rootCastId()))
            throw new IllegalArgumentException("FOREIGN_WEAPON_EXECUTION_COMPONENT");
        boolean sourceFire=witness.decision().execution().component(witness.componentId()).channel().equals("FIRE");
        boolean nativeFire=damage.getDamageCauseIndex()==DamageCause.getAssetMap().getIndex("Fire");
        if(sourceFire!=nativeFire)throw new IllegalArgumentException("WEAPON_COMPONENT_NATIVE_FIRE_CAUSE_MISMATCH");
        damage.putMetaObject(WEAPON_COMPONENT,witness);
    }
    public static com.inigmasgames.hytalerpg.execution.SkillExecutionContext executionContext(Damage damage){return damage.getIfPresentMetaObject(EXECUTION_CONTEXT);}
    public static void attachExecutionContext(Damage damage,HytaleDamageMetadata metadata,com.inigmasgames.hytalerpg.execution.SkillExecutionContext context){
        if(context==null)return;
        String rootCorrelation=context.request().correlationId();
        if(!context.request().actorId().equals(metadata.actorId())||!context.rootCastId().equals(metadata.rootCastId())
                ||!context.skillInstanceId().equals(metadata.skillInstanceId())
                ||!(rootCorrelation.equals(metadata.correlationId())
                    ||metadata.correlationId().startsWith(rootCorrelation+"/")))
            throw new IllegalArgumentException("FOREIGN_NATIVE_DAMAGE_CONTEXT");
        damage.putMetaObject(EXECUTION_CONTEXT,context);
    }

    public void apply(Ref<EntityStore> target, ComponentAccessor<EntityStore> accessor,
                      Ref<EntityStore> source, DamageCause cause,
                      HytaleDamageMetadata metadata, DamageCalculationService.Result calculation) {
        applyObserved(target, accessor, source, cause, metadata, calculation);
    }

    /** Same native dispatch with an observation result; cancellation must also gate secondary statuses. */
    public NativeResult applyObserved(Ref<EntityStore> target, ComponentAccessor<EntityStore> accessor,
                      Ref<EntityStore> source, DamageCause cause,
                      HytaleDamageMetadata metadata, DamageCalculationService.Result calculation) {
        return applyResolved(target,accessor,source,cause,metadata,calculation.preMitigationDamage(),null,null,null,null,
                calculation.critical()?new CriticalParts(calculation.preCritDamage(),calculation.preMitigationDamage()):null);
    }
    public NativeResult applyObserved(Ref<EntityStore> target,ComponentAccessor<EntityStore> accessor,Ref<EntityStore> source,DamageCause cause,
            HytaleDamageMetadata metadata,DamageCalculationService.Result calculation,com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage conditional){
        return applyResolved(target,accessor,source,cause,metadata,calculation.preMitigationDamage(),conditional,null,null,null,
                calculation.critical()?new CriticalParts(calculation.preCritDamage(),calculation.preMitigationDamage()):null);
    }
    /** Already-resolved secondary amount (e.g. a post-mitigation split), not another offensive scaling pass. */
    public NativeResult applyObserved(Ref<EntityStore> target,ComponentAccessor<EntityStore> accessor,Ref<EntityStore> source,DamageCause cause,
            HytaleDamageMetadata metadata,DamageCalculationService.Result calculation,com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage conditional,
            com.inigmasgames.hytalerpg.execution.SkillExecutionContext context){
        return applyResolved(target,accessor,source,cause,metadata,calculation.preMitigationDamage(),conditional,context,null,null,
                calculation.critical()?new CriticalParts(calculation.preCritDamage(),calculation.preMitigationDamage()):null);
    }
    public NativeResult applyResolved(Ref<EntityStore> target,ComponentAccessor<EntityStore> accessor,
                      Ref<EntityStore> source,DamageCause cause,HytaleDamageMetadata metadata,double amount){
        return applyResolved(target,accessor,source,cause,metadata,amount,null);
    }
    public NativeResult applyResolved(Ref<EntityStore> target,ComponentAccessor<EntityStore> accessor,
                      Ref<EntityStore> source,DamageCause cause,HytaleDamageMetadata metadata,double amount,
                      com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage conditional){
        return applyResolved(target,accessor,source,cause,metadata,amount,conditional,null);
    }
    public NativeResult applyResolved(Ref<EntityStore> target,ComponentAccessor<EntityStore> accessor,
                      Ref<EntityStore> source,DamageCause cause,HytaleDamageMetadata metadata,double amount,
                      com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage conditional,
                      com.inigmasgames.hytalerpg.execution.SkillExecutionContext context){
        return applyResolved(target,accessor,source,cause,metadata,amount,conditional,context,null);
    }
    /** Complete owner preflight must precede this call. Existing scalar callers are unchanged.
     * Native source producers are not registered until their resolved-source witness is verified. */
    public NativeResult applyWeaponComponent(Ref<EntityStore> target,ComponentAccessor<EntityStore> accessor,
            Ref<EntityStore> source,DamageCause cause,HytaleDamageMetadata metadata,double victimAmount,
            com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage conditional,
            com.inigmasgames.hytalerpg.execution.SkillExecutionContext context,WeaponComponent witness){
        double amount=witness.decision().directAmount(witness.componentId(),victimAmount);
        return applyResolved(target,accessor,source,cause,metadata,amount,conditional,context,witness);
    }
    /** Explicit skill handoff: the skill owner commits this hit once and submits one native channel at a time. */
    public NativeResult applyGearResolved(Ref<EntityStore> target,ComponentAccessor<EntityStore> accessor,
            Ref<EntityStore> source,DamageCause cause,HytaleDamageMetadata metadata,
            GearCombatEffects.Hit hit,GearCombatEffects.Channel channel,
            com.inigmasgames.hytalerpg.execution.SkillExecutionContext context){
        return applyGearResolved(target,accessor,source,cause,metadata,hit,channel,null,context);
    }
    public NativeResult applyGearResolved(Ref<EntityStore> target,ComponentAccessor<EntityStore> accessor,
            Ref<EntityStore> source,DamageCause cause,HytaleDamageMetadata metadata,
            GearCombatEffects.Hit hit,GearCombatEffects.Channel channel,
            com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage conditional,
            com.inigmasgames.hytalerpg.execution.SkillExecutionContext context){
        return applyGearResolved(target,accessor,source,cause,metadata,hit,channel,conditional,context,null);
    }
    /** Retain the authored source component witness for Quick Slash's Fire sustain owner. */
    public NativeResult applyGearResolved(Ref<EntityStore> target,ComponentAccessor<EntityStore> accessor,
            Ref<EntityStore> source,DamageCause cause,HytaleDamageMetadata metadata,
            GearCombatEffects.Hit hit,GearCombatEffects.Channel channel,
            com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage conditional,
            com.inigmasgames.hytalerpg.execution.SkillExecutionContext context,WeaponComponent witness){
        if(cause==null||!metadata.rootCastId().equals(hit.rootId())
                ||GearCombatEffects.nativeChannel(cause.getId())!=channel||hit.amount(channel)<=0)
            throw new IllegalArgumentException("FOREIGN_GEAR_CHANNEL");
        double amount=witness==null?hit.amount(channel):witness.decision().directAmount(witness.componentId(),hit.amount(channel));
        var gearSource=new GearHitSource(hit,metadata.correlationId(),channel);
        return applyResolved(target,accessor,source,cause,metadata,amount,conditional,context,witness,gearSource);
    }
    /** Exact native Damage construction used by the dispatch path, exposed for deterministic adapter checks. */
    public static Damage prepareGearDamage(Ref<EntityStore> source,DamageCause cause,HytaleDamageMetadata metadata,
            GearCombatEffects.Hit hit,GearCombatEffects.Channel channel,
            com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage conditional,
            com.inigmasgames.hytalerpg.execution.SkillExecutionContext context){
        return prepareGearDamage(source,cause,metadata,hit,channel,conditional,context,null);
    }
    public static Damage prepareGearDamage(Ref<EntityStore> source,DamageCause cause,HytaleDamageMetadata metadata,
            GearCombatEffects.Hit hit,GearCombatEffects.Channel channel,
            com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage conditional,
            com.inigmasgames.hytalerpg.execution.SkillExecutionContext context,WeaponComponent witness){
        if(cause==null||metadata==null||hit==null||!metadata.rootCastId().equals(hit.rootId())
                ||GearCombatEffects.nativeChannel(cause.getId())!=channel||hit.amount(channel)<=0)
            throw new IllegalArgumentException("FOREIGN_GEAR_CHANNEL");
        return newDamage(source,cause,metadata,hit.amount(channel),conditional,context,witness,
                new GearHitSource(hit,metadata.correlationId(),channel));
    }
    private NativeResult applyResolved(Ref<EntityStore> target,ComponentAccessor<EntityStore> accessor,
                      Ref<EntityStore> source,DamageCause cause,HytaleDamageMetadata metadata,double amount,
                      com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage conditional,
                      com.inigmasgames.hytalerpg.execution.SkillExecutionContext context,WeaponComponent witness){
        return applyResolved(target,accessor,source,cause,metadata,amount,conditional,context,witness,null);
    }
    private NativeResult applyResolved(Ref<EntityStore> target,ComponentAccessor<EntityStore> accessor,
                      Ref<EntityStore> source,DamageCause cause,HytaleDamageMetadata metadata,double amount,
                      com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage conditional,
                      com.inigmasgames.hytalerpg.execution.SkillExecutionContext context,WeaponComponent witness,GearHitSource gear){
        return applyResolved(target,accessor,source,cause,metadata,amount,conditional,context,witness,gear,null);
    }
    private NativeResult applyResolved(Ref<EntityStore> target,ComponentAccessor<EntityStore> accessor,
                      Ref<EntityStore> source,DamageCause cause,HytaleDamageMetadata metadata,double amount,
                      com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage conditional,
                      com.inigmasgames.hytalerpg.execution.SkillExecutionContext context,WeaponComponent witness,
                      GearHitSource gear,CriticalParts critical){
        if(!Double.isFinite(amount)||amount<0||amount>Float.MAX_VALUE)throw new IllegalArgumentException("Invalid native damage amount");
        if(cause==null)throw new IllegalArgumentException("NATIVE_DAMAGE_CAUSE_MISSING");
        // Resolve former generic Arcane by the actual authored producer, before native cause filtering.
        // Secondary/copy callers without that witness retain the cause already supplied by their owner.
        String mappedCause=com.inigmasgames.hytalerpg.combat.damage.DamageChannels.canonical().nativeCause(
                cause.getId(),context==null?null:context.profile().skillId());
        if(!mappedCause.equals(cause.getId())){
            cause=DamageCause.getAssetMap().getAsset(mappedCause);
            if(cause==null)throw new IllegalStateException("CANONICAL_NATIVE_DAMAGE_CAUSE_MISSING:"+mappedCause);
        }
        EntityStatMap targetStats = accessor.getComponent(target, EntityStatMap.getComponentType());
        double before = targetStats == null || targetStats.get(DefaultEntityStatTypes.getHealth()) == null
                ? Double.NaN : targetStats.get(DefaultEntityStatTypes.getHealth()).get();
        HytaleDamageMetadata complete = new HytaleDamageMetadata(metadata.actorId(), metadata.rootCastId(),
                metadata.skillInstanceId(), metadata.correlationId(), amount, before,
                metadata.effectInstanceId(),metadata.canProc(),metadata.origin(),metadata.monsterAffix());
        Damage damage=newDamage(source,cause,complete,amount,conditional,context,witness,gear);
        if(critical!=null)attachCriticalParts(damage,critical);
        DamageSystems.executeDamage(target, accessor, damage);
        double after = targetStats == null || targetStats.get(DefaultEntityStatTypes.getHealth()) == null
                ? Double.NaN : targetStats.get(DefaultEntityStatTypes.getHealth()).get();
        return new NativeResult(damage.isCancelled(), damage.getAmount(), before, after,metadata(damage).preMitigationDamage(),HytaleConditionalDamage.victimFactor(damage));
    }
    private static Damage newDamage(Ref<EntityStore> source,DamageCause cause,HytaleDamageMetadata metadata,double amount,
            com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage conditional,
            com.inigmasgames.hytalerpg.execution.SkillExecutionContext context,WeaponComponent witness,GearHitSource gear){
        if(!Double.isFinite(amount)||amount<0||amount>Float.MAX_VALUE||cause==null)
            throw new IllegalArgumentException("Invalid native damage amount or cause");
        Damage damage=new Damage(source==null?Damage.NULL_SOURCE:new Damage.EntitySource(source),cause,(float)amount);
        damage.putMetaObject(RPG_METADATA,metadataJson(metadata));
        attachExecutionContext(damage,metadata,context);
        if(witness!=null)attachWeaponComponent(damage,metadata,witness);
        if(gear!=null)attachGearHit(damage,gear);
        HytaleConditionalDamage.attach(damage,conditional);
        return damage;
    }
    public record NativeResult(boolean cancelled, double nativeAmount, double healthBefore, double healthAfter,double preMitigationAmount,double victimCoefficientFactor) {
        public NativeResult(boolean cancelled,double nativeAmount,double healthBefore,double healthAfter,double preMitigationAmount){this(cancelled,nativeAmount,healthBefore,healthAfter,preMitigationAmount,1);}
        public NativeResult(boolean cancelled,double nativeAmount,double healthBefore,double healthAfter){this(cancelled,nativeAmount,healthBefore,healthAfter,nativeAmount);}
    }
    /** Missing native health remains unknown; never emit invalid NaN JSON or invent zero HP. */
    static String metadataJson(HytaleDamageMetadata value) {
        if (!Double.isFinite(value.preMitigationDamage()) || value.preMitigationDamage() < 0)
            throw new IllegalArgumentException("INVALID_PRE_MITIGATION_DAMAGE");
        boolean known = Double.isFinite(value.targetHealthBefore());
        var tree = GSON.toJsonTree(known ? value : withHealthBefore(value, 0)).getAsJsonObject();
        if (!known) tree.add("targetHealthBefore", com.google.gson.JsonNull.INSTANCE);
        return GSON.toJson(tree);
    }
    private static HytaleDamageMetadata withHealthBefore(HytaleDamageMetadata value, double health) {
        return new HytaleDamageMetadata(value.actorId(), value.rootCastId(), value.skillInstanceId(),
                value.correlationId(), value.preMitigationDamage(), health, value.effectInstanceId(),
                value.canProc(), value.origin());
    }
    public static HytaleDamageMetadata metadata(Damage damage) {
        String json = damage.getIfPresentMetaObject(RPG_METADATA);
        if (json == null || json.isBlank()) return null;
        var tree = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        var value = GSON.fromJson(tree, HytaleDamageMetadata.class);
        if (tree.has("targetHealthBefore") && !tree.get("targetHealthBefore").isJsonNull()) return value;
        return withHealthBefore(value, Double.NaN);
    }
}
