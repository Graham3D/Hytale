package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCalculatorSystems;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.combat.DamageCalculator;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.Objects;

/** Native contact leaf for focus, shield guard and durable bomb carriers. */
public final class ManagedCarrierDamageInteraction extends DamageEntityInteraction {
    public static final String TYPE="RPG_CarrierDamage";
    private String procSelector;
    private Double procCoefficient;
    public static final BuilderCodec<ManagedCarrierDamageInteraction> CODEC=BuilderCodec.builder(
            ManagedCarrierDamageInteraction.class,ManagedCarrierDamageInteraction::new,DamageEntityInteraction.CODEC)
            .append(new KeyedCodec<>(NativeGearAttackAcceptance.PROC_SELECTOR,Codec.STRING),(leaf,value)->leaf.procSelector=value,leaf->leaf.procSelector).add()
            .append(new KeyedCodec<>(NativeGearAttackAcceptance.PROC_COEFFICIENT,Codec.DOUBLE),(leaf,value)->leaf.procCoefficient=value,leaf->leaf.procCoefficient).add()
            .afterDecode(ManagedCarrierDamageInteraction::bind).build();
    java.util.Map<String,String> procVariables(java.util.Map<String,String> inherited) {
        if(procSelector==null&&procCoefficient==null)return inherited;
        if(procSelector==null||procCoefficient==null)throw new IllegalStateException("INCOMPLETE_NATIVE_PROC_METADATA");
        return java.util.Map.of(NativeGearAttackAcceptance.PROC_SELECTOR,procSelector,
                NativeGearAttackAcceptance.PROC_COEFFICIENT,Double.toString(procCoefficient));
    }
    private static final ThreadLocal<Call> CURRENT=new ThreadLocal<>();
    private static final java.util.concurrent.ConcurrentHashMap<java.util.UUID,java.util.ArrayDeque<Pending>> PENDING=new java.util.concurrent.ConcurrentHashMap<>();
    private record Call(ManagedCarrierDamageInteraction leaf,InteractionContext context,GearEffectSnapshot effects,String root) {}
    private static final class Pending {
        final GearCombatEffects.Hit hit;final java.util.Set<String> remaining;final InteractionType type;
        final long createdNanos=System.nanoTime();boolean started;
        Pending(GearCombatEffects.Hit hit,java.util.Set<String> causes,InteractionType type){
            this.hit=hit;this.remaining=new java.util.HashSet<>(causes);this.type=type;
        }
    }
    public static void forget(java.util.UUID actor){PENDING.remove(actor);}
    public static int pending(java.util.UUID actor){var queue=PENDING.get(actor);if(queue==null)return 0;
        synchronized(queue){return queue.size();}}
    private static void register(java.util.UUID actor,GearCombatEffects.Hit hit,java.util.Set<String> causes,InteractionType type){
        if(causes.isEmpty())return;
        var queue=PENDING.computeIfAbsent(actor,ignored->new java.util.ArrayDeque<>());
        synchronized(queue){
            long now=System.nanoTime();
            while(!queue.isEmpty()&&now-queue.peekFirst().createdNanos>60_000_000_000L)queue.removeFirst();
            if(queue.size()>=64)throw new IllegalStateException("CARRIER_HIT_LEDGER_FULL");
            queue.addLast(new Pending(hit,causes,type));
        }
    }
    /** Damage Gather callback for the existing gear envelope owner. */
    public static GearCombatEffects.Hit claim(Damage damage,java.util.UUID actor){
        var queue=PENDING.get(actor);if(queue==null||damage.getCause()==null)return null;
        synchronized(queue){
            var pending=queue.peekFirst();if(pending==null)return null;
            String cause=damage.getCause().getId();
            if(!pending.remaining.contains(cause)||damage.getIfPresentMetaObject(Damage.INTERACTION_TYPE)!=pending.type)return null;
            if(!pending.started){
                var witness=damage.getIfPresentMetaObject(DamageCalculatorSystems.DAMAGE_SEQUENCE);
                if(witness==null||!(witness.getDamageCalculator() instanceof Calculator))return null;
                pending.started=true;
            }
            pending.remaining.remove(cause);
            if(pending.remaining.isEmpty()){queue.removeFirst();if(queue.isEmpty())PENDING.remove(actor,queue);}
            return pending.hit;
        }
    }

    private void bind(){
        if(damageCalculator==null){if(id!=null)throw new IllegalArgumentException("Carrier damage calculator missing");return;}
        damageCalculator=adapt(damageCalculator);
        if(angledDamage!=null)for(int i=0;i<angledDamage.length;i++)
            angledDamage[i]=CarrierAngled.CODEC.decode(AngledDamage.CODEC.encode(angledDamage[i],new ExtraInfo()),new ExtraInfo());
        if(targetedDamage!=null){targetedDamage=new java.util.HashMap<>(targetedDamage);
            targetedDamage.replaceAll((key,value)->CarrierTargeted.CODEC.decode(TargetedDamage.CODEC.encode(value,new ExtraInfo()),new ExtraInfo()));}
    }
    private static DamageCalculator adapt(DamageCalculator original){
        var result=Calculator.CODEC.decode(DamageCalculator.CODEC.encode(original,new ExtraInfo()),new ExtraInfo());
        result.validate();return result;
    }
    public static final class CarrierAngled extends AngledDamage {
        static final BuilderCodec<CarrierAngled> CODEC=BuilderCodec.builder(CarrierAngled.class,CarrierAngled::new,AngledDamage.CODEC)
                .afterDecode(value->value.damageCalculator=adapt(value.damageCalculator)).build();
    }
    public static final class CarrierTargeted extends TargetedDamage {
        static final BuilderCodec<CarrierTargeted> CODEC=BuilderCodec.builder(CarrierTargeted.class,CarrierTargeted::new,TargetedDamage.CODEC)
                .afterDecode(value->value.damageCalculator=adapt(value.damageCalculator)).build();
    }
    @Override protected void tick0(boolean first,float dt,InteractionType type,InteractionContext context,CooldownHandler cooldowns){
        var previous=CURRENT.get();
        var frozen=ManagedCarrierProjectile.snapshot(context);
        var effects=frozen==null?GearNativeItems.effects(context.getOwningEntity(),context.getCommandBuffer()).snapshot():frozen.effects();
        CURRENT.set(new Call(this,context,effects,context.getChain().getChainId()+"/"+getId()+"/"+context.getOperationCounter()));
        try{super.tick0(first,dt,type,context,cooldowns);}finally{if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
    }
    @Override protected void simulateTick0(boolean first,float dt,InteractionType type,InteractionContext context,CooldownHandler cooldowns){}

    /** This is the production calculator. Tests invoke it with the same frozen inputs as the native leaf. */
    public static GearCombatEffects.Hit resolve(GearEffectSnapshot valid,GearInstance item,String root,double coefficient,
                                                double launchedPower,boolean projectile){
        return resolve(valid,item,root,coefficient,launchedPower,projectile,null);
    }
    public static GearCombatEffects.Hit resolve(GearEffectSnapshot valid,GearInstance item,String root,double coefficient,
                                                double launchedPower,boolean projectile,Vec3 origin){
        return resolve(valid,item,root,coefficient,launchedPower,projectile,origin,0,1.5);
    }
    public static GearCombatEffects.Hit resolve(GearEffectSnapshot valid,GearInstance item,String root,double coefficient,
                                                double launchedPower,boolean projectile,Vec3 origin,double criticalChance,double criticalMultiplier){
        Objects.requireNonNull(valid);Objects.requireNonNull(item);Objects.requireNonNull(root);
        if(valid.forItem(item.identity()).empty())throw new IllegalArgumentException("Carrier is not valid equipped gear");
        if(!Double.isFinite(coefficient)||coefficient<0)throw new IllegalArgumentException("Invalid native coefficient");
        String family=item.baseId();
        boolean focus=family.startsWith("gm.staff_")||family.startsWith("gm.wand_")||family.startsWith("gm.book_");
        boolean bomb=family.startsWith("gm.bomb_");
        boolean shield=family.startsWith("gm.shield_");
        if(!focus&&!bomb&&!shield)throw new IllegalArgumentException("Unsupported focused carrier "+family);
        double power;
        if(focus){var magic=GearAffixRuntime.magic(item);if(magic==null)throw new IllegalArgumentException("Focus has no Magic Power");power=magic;}
        else if(bomb){var range=GearCombatEffects.physical(item);power=projectile?launchedPower:GearPower.sample(range.minimum(),range.maximum(),root);}
        else power=coefficient==0?0:1; // Stock guard shove is zero; primary unarmed swing keeps one native power.
        return GearCombatEffects.attack(valid,item.identity(),root,power,coefficient,!focus,focus,0,null,criticalChance,criticalMultiplier,true,origin);
    }
    public static final class Calculator extends DamageCalculator {
        static final BuilderCodec<Calculator> CODEC=BuilderCodec.builder(Calculator.class,Calculator::new,DamageCalculator.CODEC).build();
        void validate(){
            if(type!=Type.ABSOLUTE||baseDamageRaw==null||baseDamageRaw.size()!=1
                    ||!(baseDamageRaw.containsKey("Physical")||baseDamageRaw.containsKey("Projectile"))
                    ||baseDamageRaw.values().doubleStream().anyMatch(v->v<0)||sequentialModifierStep!=0)
                throw new IllegalArgumentException("Unsupported managed carrier calculator");
        }
        @Override public Object2FloatMap<DamageCause> calculateDamage(double ignored){
            var call=CURRENT.get();if(call==null)throw new IllegalStateException("Carrier calculation outside native contact");
            var context=call.context();var launch=ManagedCarrierProjectile.snapshot(context);
            if(launch==null&&context.getEntity()!=context.getOwningEntity())return new Object2FloatOpenHashMap<>();
            if(launch==null&&!GearNativeItems.canUse(context.getHeldItem(),context.getOwningEntity(),context.getCommandBuffer()))
                return new Object2FloatOpenHashMap<>();
            var item=launch==null?GearNativeItems.read(context.getHeldItem()):launch.gear();
            if(item==null)throw new IllegalStateException("Carrier identity missing");
            var acceptance=launch==null?NativeGearAcceptedContext.require(context,item):launch.acceptance();
            if(acceptance==null)return new Object2FloatOpenHashMap<>();
            String cause=baseDamageRaw.containsKey("Projectile")?"Projectile":"Physical";
            var transform=launch==null?context.getCommandBuffer().getComponent(context.getOwningEntity(),TransformComponent.getComponentType()):null;
            var at=transform==null?null:transform.getPosition();
            var origin=launch==null?acceptance.origin():launch.origin();
            var hit=resolve(acceptance.snapshot(),item,launch==null?call.root():launch.rootId(),baseDamageRaw.getFloat(cause),
                    launch==null?0:launch.power(),launch!=null,origin,acceptance.baselineCritChance(),acceptance.baselineCritMultiplier());
            var proc=call.leaf().procVariables(context.getInteractionVars());
            if(GearCombatEffects.needsNativeEnvelope(hit)){
                String selector=proc.get(NativeGearAttackAcceptance.PROC_SELECTOR);
                double authored=NativeGearAttackAcceptance.coefficient(selector,proc);
                double itemOnly=NativeGearAttackAcceptance.selectedAreaCoefficient(context,
                        acceptance.world(),acceptance.actor(),hit,authored);
                hit=NativeGearAttackAcceptance.commit(acceptance.world(),acceptance.actor(),hit,
                        itemOnly,launch==null,selector);
            }
            var result=new Object2FloatOpenHashMap<DamageCause>();
            for(var entry:hit.amounts().entrySet()){
                if(entry.getValue()<=0)continue;
                String id=entry.getKey()==GearCombatEffects.Channel.PHYSICAL?cause:GearCombatEffects.nativeCause(entry.getKey());
                var nativeCause=DamageCause.getAssetMap().getAsset(id);
                if(nativeCause==null)throw new IllegalStateException("Missing native channel "+id);
                result.put(nativeCause,entry.getValue().floatValue());
            }
            var player=context.getCommandBuffer().getComponent(context.getOwningEntity(),PlayerRef.getComponentType());
            if(player!=null&&!result.isEmpty()&&GearCombatEffects.needsNativeEnvelope(hit)){
                var causes=new java.util.HashSet<String>();for(var nativeCause:result.keySet())causes.add(nativeCause.getId());
                register(player.getUuid(),hit,causes,context.getChain().getType());
            }
            return result;
        }
    }
}
