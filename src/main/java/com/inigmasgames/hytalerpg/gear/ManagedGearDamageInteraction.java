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
public final class ManagedGearDamageInteraction extends com.inigmasgames.hytalerpg.combat.hytale.NativeDamageLeafInteraction {
    public static final String TYPE="RPG_GearDamage";
    private static final GearBindings BINDINGS=new GearBindings();
    private static final NativeItemPowerRegistry POWERS=NativeItemPowerRegistry.loadProduction();
    public static final BuilderCodec<ManagedGearDamageInteraction> CODEC=BuilderCodec.builder(
            ManagedGearDamageInteraction.class,ManagedGearDamageInteraction::new,DamageEntityInteraction.CODEC)
            .afterDecode(ManagedGearDamageInteraction::bind).build();
    private void bind() {
        if(damageCalculator==null) { if(id!=null) throw new IllegalArgumentException("Gear damage calculator missing");return; }
        bindCalculators(ManagedGearDamageInteraction::adapt);
    }
    private static DamageCalculator adapt(DamageCalculator original) {
        if(original==null) return null;
        var calculator=Calculator.CODEC.decode(DamageCalculator.CODEC.encode(original,new ExtraInfo()),new ExtraInfo());
        calculator.validate();return calculator;
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
            var call=currentInvocation();if(call==null||!(call.leaf() instanceof ManagedGearDamageInteraction)) throw new IllegalStateException("Gear calculation outside native strike");
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
