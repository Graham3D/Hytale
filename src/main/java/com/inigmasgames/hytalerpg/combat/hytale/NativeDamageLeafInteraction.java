package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.combat.DamageCalculator;
import java.util.function.UnaryOperator;

/** Existing managed-gear native leaf scaffold, shared without changing its damage formula or delivery. */
public abstract class NativeDamageLeafInteraction extends DamageEntityInteraction {
    public interface Scope {void completed(Throwable failure);}
    public record Invocation(NativeDamageLeafInteraction leaf,InteractionContext context,Scope scope){}
    private static final ThreadLocal<Invocation> CURRENT=new ThreadLocal<>();
    private static final ThreadLocal<UnaryOperator<DamageCalculator>> ADAPTING=new ThreadLocal<>();
    public static Invocation currentInvocation(){return CURRENT.get();}
    protected Invocation invocation(InteractionContext context){return new Invocation(this,context,null);}
    protected final void bindCalculators(UnaryOperator<DamageCalculator> adapter){
        var previous=ADAPTING.get();ADAPTING.set(adapter);
        try{
            damageCalculator=adapter.apply(damageCalculator);
            if(angledDamage!=null)for(int i=0;i<angledDamage.length;i++)
                angledDamage[i]=BoundAngled.CODEC.decode(AngledDamage.CODEC.encode(angledDamage[i],new ExtraInfo()),new ExtraInfo());
            if(targetedDamage!=null){
                targetedDamage=new java.util.HashMap<>(targetedDamage);
                targetedDamage.replaceAll((key,value)->BoundTargeted.CODEC.decode(TargetedDamage.CODEC.encode(value,new ExtraInfo()),new ExtraInfo()));
            }
        }finally{if(previous==null)ADAPTING.remove();else ADAPTING.set(previous);}
    }
    private static DamageCalculator adapt(DamageCalculator calculator){return ADAPTING.get().apply(calculator);}
    public static final class BoundAngled extends AngledDamage {
        static final BuilderCodec<BoundAngled> CODEC=BuilderCodec.builder(BoundAngled.class,BoundAngled::new,AngledDamage.CODEC)
                .afterDecode(value->value.damageCalculator=adapt(value.damageCalculator)).build();
    }
    public static final class BoundTargeted extends TargetedDamage {
        static final BuilderCodec<BoundTargeted> CODEC=BuilderCodec.builder(BoundTargeted.class,BoundTargeted::new,TargetedDamage.CODEC)
                .afterDecode(value->value.damageCalculator=adapt(value.damageCalculator)).build();
    }
    @Override protected void tick0(boolean first,float dt,InteractionType type,InteractionContext context,CooldownHandler cooldowns){
        var previous=CURRENT.get();var call=invocation(context);CURRENT.set(call);Throwable failed=null;
        try{super.tick0(first,dt,type,context,cooldowns);}
        catch(RuntimeException|Error failure){failed=failure;throw failure;}
        finally{
            try{if(call.scope()!=null)call.scope().completed(failed);}
            catch(RuntimeException|Error completionFailure){
                if(failed==null)throw completionFailure;
                if(failed!=completionFailure)failed.addSuppressed(completionFailure);
            }
            finally{if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
        }
    }
}
