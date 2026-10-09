package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.combat.DamageCalculator;
import com.inigmasgames.hytalerpg.combat.hytale.NativeDamageLeafInteraction;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import java.util.Objects;
import java.util.function.*;

/** Adapts an existing native NPC damage leaf. Native collision, actions, effects and submission remain native. */
public final class NativeEnemyDamageInteraction extends NativeDamageLeafInteraction {
    public static final String TYPE="RPG_EnemyDamage";
    public interface EnemyScope extends Scope {
        /** Original calculation is lazy so an already-frozen strike does not sample native randomness again. */
        Object2FloatMap<DamageCause> calculate(DamageCalculator calculator,double runtime,Supplier<Object2FloatMap<DamageCause>> original);
    }
    private static volatile Function<Invocation,EnemyScope> provider=call->null;
    public static void configure(Function<Invocation,EnemyScope> source){provider=Objects.requireNonNull(source);}
    public static final BuilderCodec<NativeEnemyDamageInteraction> CODEC=BuilderCodec.builder(
            NativeEnemyDamageInteraction.class,NativeEnemyDamageInteraction::new,DamageEntityInteraction.CODEC)
            .afterDecode(NativeEnemyDamageInteraction::bind).build();
    private void bind(){bindCalculators(NativeEnemyDamageInteraction::adapt);}
    /** Only unconditional, sequence-neutral native leaves use this acceptance adapter. */
    public Calculator acceptanceCalculator(){
        if(!(damageCalculator instanceof Calculator calculator)||(angledDamage!=null&&angledDamage.length!=0)
                ||(targetedDamage!=null&&!targetedDamage.isEmpty())||damageAttribute!=null
                ||calculator.getSequentialModifierStep()!=0)
            throw new IllegalStateException("ENEMY_ACCEPTANCE_CALCULATOR_NOT_CERTIFIED:"+getId());
        return calculator;
    }
    public Object2FloatMap<DamageCause> sampleAtAcceptance(){
        return acceptanceCalculator().original(getRunTime());
    }
    /** Actual inherited native owner; no comparison between its velocity and authored metres. */
    public boolean ownsNativeImpulse(){return damageEffects!=null&&damageEffects.getKnockback()!=null;}
    private static DamageCalculator adapt(DamageCalculator original){
        return original==null?null:Calculator.CODEC.decode(DamageCalculator.CODEC.encode(original,new ExtraInfo()),new ExtraInfo());
    }
    @Override protected Invocation invocation(InteractionContext context){
        var call=new Invocation(this,context,null);var scope=provider.apply(call);
        if(scope!=null){
            var impact=com.hypixel.hytale.server.core.modules.projectile.component.ImpactModifiers.resolve(context,context.getEntity(),context.getCommandBuffer());
            if(impact!=null&&(impact.getElementalCauseId()!=null||damageAttribute!=null&&impact.getAttribute(damageAttribute,1)!=1)){
                var failure=new IllegalStateException("ENEMY_POST_CALCULATOR_SOURCE_ROUTE_NOT_CERTIFIED");scope.completed(failure);throw failure;
            }
        }
        return new Invocation(this,context,scope);
    }
    public static final class Calculator extends DamageCalculator {
        static final BuilderCodec<Calculator> CODEC=BuilderCodec.builder(Calculator.class,Calculator::new,DamageCalculator.CODEC).build();
        private Object2FloatMap<DamageCause> original(double runtime){return super.calculateDamage(runtime);}
        @Override public Object2FloatMap<DamageCause> calculateDamage(double runtime){
            var call=currentInvocation();
            if(call==null||!(call.leaf() instanceof NativeEnemyDamageInteraction)||!(call.scope() instanceof EnemyScope scope))
                return super.calculateDamage(runtime);
            return scope.calculate(this,runtime,()->super.calculateDamage(runtime));
        }
    }
}
