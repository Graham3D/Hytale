package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.combat.DamageCalculator;
import com.inigmasgames.hytalerpg.combat.power.NativeItemPowerRegistry;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;

/** Extends the native damage leaf, retaining collision, protection, effects and one Damage submission. */
public final class ManagedGearDamageInteraction extends DamageEntityInteraction {
    public static final String TYPE="RPG_GearDamage";
    private static final GearBindings BINDINGS=new GearBindings();
    private static final NativeItemPowerRegistry POWERS=NativeItemPowerRegistry.loadProduction();
    private static final ThreadLocal<Invocation> CURRENT=new ThreadLocal<>();
    private record Invocation(ManagedGearDamageInteraction leaf,InteractionContext context) {}
    public static final BuilderCodec<ManagedGearDamageInteraction> CODEC=BuilderCodec.builder(
            ManagedGearDamageInteraction.class,ManagedGearDamageInteraction::new,DamageEntityInteraction.CODEC)
            .afterDecode(ManagedGearDamageInteraction::bind).build();
    private void bind() {
        if(damageCalculator==null) { if(id!=null) throw new IllegalArgumentException("Gear damage calculator missing");return; }
        damageCalculator=adapt(damageCalculator);
        if(angledDamage!=null) for(int i=0;i<angledDamage.length;i++)
            angledDamage[i]=GearAngled.CODEC.decode(AngledDamage.CODEC.encode(angledDamage[i],new ExtraInfo()),new ExtraInfo());
        if(targetedDamage!=null) {
            targetedDamage=new java.util.HashMap<>(targetedDamage);
            targetedDamage.replaceAll((key,value)->GearTargeted.CODEC.decode(TargetedDamage.CODEC.encode(value,new ExtraInfo()),new ExtraInfo()));
        }
    }
    private static DamageCalculator adapt(DamageCalculator original) {
        if(original==null) return null;
        var calculator=Calculator.CODEC.decode(DamageCalculator.CODEC.encode(original,new ExtraInfo()),new ExtraInfo());
        calculator.validate();return calculator;
    }
    public static final class GearAngled extends AngledDamage {
        static final BuilderCodec<GearAngled> CODEC=BuilderCodec.builder(GearAngled.class,GearAngled::new,AngledDamage.CODEC)
                .afterDecode(value->value.damageCalculator=adapt(value.damageCalculator)).build();
    }
    public static final class GearTargeted extends TargetedDamage {
        static final BuilderCodec<GearTargeted> CODEC=BuilderCodec.builder(GearTargeted.class,GearTargeted::new,TargetedDamage.CODEC)
                .afterDecode(value->value.damageCalculator=adapt(value.damageCalculator)).build();
    }
    @Override protected void tick0(boolean first,float dt,InteractionType type,InteractionContext context,CooldownHandler cooldowns) {
        var previous=CURRENT.get();CURRENT.set(new Invocation(this,context));
        try { super.tick0(first,dt,type,context,cooldowns); }
        finally { if(previous==null) CURRENT.remove(); else CURRENT.set(previous); }
    }
    @Override protected void simulateTick0(boolean first,float dt,InteractionType type,InteractionContext context,CooldownHandler cooldowns) {}
    public static final class Calculator extends DamageCalculator {
        static final BuilderCodec<Calculator> CODEC=BuilderCodec.builder(Calculator.class,Calculator::new,DamageCalculator.CODEC).build();
        void validate() {
            if(type!=Type.ABSOLUTE || baseDamageRaw==null || baseDamageRaw.size()!=1
                    || !(baseDamageRaw.containsKey("Physical") || baseDamageRaw.containsKey("Projectile"))
                    || baseDamageRaw.values().doubleStream().anyMatch(v->v<0)
                    || sequentialModifierStep!=0) throw new IllegalArgumentException("Unsupported native managed weapon calculator");
        }
        @Override public Object2FloatMap<DamageCause> calculateDamage(double runtime) {
            var call=CURRENT.get();if(call==null) throw new IllegalStateException("Gear calculation outside native strike");
            var launch=ManagedGearProjectile.snapshot(call.context());
            if(launch==null && call.context().getEntity()!=call.context().getOwningEntity())
                return new Object2FloatOpenHashMap<>(); // Never substitute the shooter's current item for a missing launch snapshot.
            if(launch==null && !GearNativeItems.canUse(call.context().getHeldItem(),call.context().getOwningEntity(),call.context().getCommandBuffer()))
                return new Object2FloatOpenHashMap<>();
            var gear=launch==null?GearNativeItems.read(call.context().getHeldItem()):launch.gear;
            if(gear==null) throw new IllegalStateException("Managed damage requires immutable gear identity");
            var binding=BINDINGS.require(gear.baseId());var baseline=POWERS.find(binding.nativeItemId()).orElseThrow();
            var range=GearAffixRuntime.physical(gear);double minimum=range.minimum(),maximum=range.maximum();
            String strike=gear.identity()+"/"+call.context().getChain().getChainId()+"/"+call.leaf().getId()+"/"+call.context().getOperationCounter();
            double power=launch==null?GearPower.sample(minimum,maximum,strike):launch.power;
            // Native action coefficients survive. Native RandomPercentageModifier is deliberately never sampled.
            String cause=baseDamageRaw.containsKey("Projectile")?"Projectile":"Physical";
            double coefficient=baseDamageRaw.getFloat(cause)/baseline.basePower();
            var result=new Object2FloatOpenHashMap<DamageCause>();
            result.put(DamageCause.getAssetMap().getAsset(cause),(float)(power*coefficient));return result;
        }
    }
}
