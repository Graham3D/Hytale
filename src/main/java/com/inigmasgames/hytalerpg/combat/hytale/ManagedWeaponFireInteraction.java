package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.combat.DamageCalculator;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import java.util.Objects;

/** AQ Fire source normalization. The shipped damage leaf still owns validation, collision,
 * all recipient modifiers, knockback, and Damage submission. Only an explicitly bound Fire
 * calculator output is routed. No filter callback, private reflection, or asset mutation at tick.
 * Instance configuration is fixed at asset decode; callback context is scoped to the native call. */
public final class ManagedWeaponFireInteraction extends DamageEntityInteraction {
    public static final String TYPE="RPG_ManagedWeaponFire";
    @FunctionalInterface public interface Router {
        float route(InteractionContext context, String leaf, String sourceAttribute,
                    float authoredBase, float variance);
    }
    private final Router router;
    private static final ThreadLocal<Invocation> CURRENT=new ThreadLocal<>();
    private record Invocation(ManagedWeaponFireInteraction leaf,InteractionContext context,java.util.List<Runnable> after){}
    static void afterNativeResolved(Runnable echo){
        var invocation=Objects.requireNonNull(CURRENT.get(),"MANAGED_FIRE_OUTSIDE_NATIVE_LEAF");
        invocation.after.add(echo);
    }
    public ManagedWeaponFireInteraction(Router router){this.router=Objects.requireNonNull(router);}
    public static BuilderCodec<ManagedWeaponFireInteraction> codec(Router router){
        return BuilderCodec.builder(ManagedWeaponFireInteraction.class,()->new ManagedWeaponFireInteraction(router),DamageEntityInteraction.CODEC)
                .afterDecode(ManagedWeaponFireInteraction::bind).build();
    }
    private void bind(){
        // Codec registration validates the unconfigured default instance before loading assets.
        if(damageCalculator==null){if(id!=null)throw new IllegalArgumentException("MANAGED_FIRE_CALCULATOR_MISSING");return;}
        // Conditional calculators are not silently collapsed into their default branch.
        if(angledDamage!=null&&angledDamage.length>0||targetedDamage!=null&&!targetedDamage.isEmpty())
            throw new IllegalArgumentException("MANAGED_FIRE_CONDITIONAL_CALCULATOR_UNSUPPORTED");
        var copy=FireCalculator.CODEC.decode(DamageCalculator.CODEC.encode(damageCalculator,new ExtraInfo()),new ExtraInfo());
        copy.validateDefinition();damageCalculator=copy;
    }
    @Override protected void tick0(boolean first,float dt,InteractionType type,InteractionContext context,CooldownHandler cooldowns){
        var previous=CURRENT.get();var invocation=new Invocation(this,context,new java.util.ArrayList<>());CURRENT.set(invocation);
        try{super.tick0(first,dt,type,context,cooldowns);for(var echo:invocation.after)echo.run();}
        finally{if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
    }
    @Override protected void simulateTick0(boolean first,float dt,InteractionType type,InteractionContext context,CooldownHandler cooldowns){
        // Native DamageEntity simulation uses tick0. Never generate, debit or dispatch on simulation.
    }
    public static final class FireCalculator extends DamageCalculator {
        static final BuilderCodec<FireCalculator> CODEC=BuilderCodec.builder(FireCalculator.class,FireCalculator::new,DamageCalculator.CODEC).build();
        private void validateDefinition(){
            if(type!=Type.ABSOLUTE||baseDamageRaw==null||!baseDamageRaw.containsKey("Fire")
                    ||baseDamageRaw.getFloat("Fire")<=0||!Float.isFinite(baseDamageRaw.getFloat("Fire"))
                    ||!Float.isFinite(randomPercentageModifier)||randomPercentageModifier<0||randomPercentageModifier>1
                    ||sequentialModifierStep!=0)
                throw new IllegalArgumentException("MANAGED_FIRE_SOURCE_DEFINITION_UNSUPPORTED");
        }
        @Override public Object2FloatMap<DamageCause> calculateDamage(double runtime){
            var invocation=CURRENT.get();
            if(invocation==null)throw new IllegalStateException("MANAGED_FIRE_OUTSIDE_NATIVE_LEAF");
            // Native remains the owner of unrelated channels. Its superseded Fire sample is
            // discarded, NEVER used as source or delivered in addition to canonical Fire.
            var values=super.calculateDamage(runtime);
            var fire=DamageCause.getAssetMap().getAsset("Fire");
            if(fire==null)throw new IllegalStateException("NATIVE_FIRE_CAUSE_MISSING");
            float routed=invocation.leaf.router.route(invocation.context,invocation.leaf.getId(),
                    invocation.leaf.damageAttribute,baseDamageRaw.getFloat("Fire"),randomPercentageModifier);
            if(Float.isNaN(routed))return values; // Explicit unsupported-owner route keeps vanilla combat.
            if(!Float.isFinite(routed)||routed<0)throw new IllegalStateException("MANAGED_FIRE_ROUTE_INVALID");
            values.put(fire,routed);return values;
        }
    }
}
