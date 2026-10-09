package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.combat.DamageCalculator;
import com.inigmasgames.hytalerpg.combat.hytale.*;
import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytale.patch.NativeDamageReceiptHook;
import com.inigmasgames.hytalerpg.progress.RewardIntent;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import java.util.*;
import java.util.function.*;

/** Consumes an acceptance/launch snapshot; does not infer a snapshot from victim damage or sample at collision. */
public final class EnemyNativeStrikeScope implements NativeEnemyDamageInteraction.EnemyScope,NativeDamageLeafReceipts.Scope {
    private final EnemyOffenseSnapshot offense;
    private final UUID nativeActor;
    private final UUID victim;
    private final DamageCalculator expectedCalculator;
    private final BooleanSupplier currentBinding;
    private final Consumer<EnemyAppliedHit> accepted;
    private final Consumer<Throwable> rejected;
    private final Map<String,HytaleDamageAdapter.NativeResult> results=new TreeMap<>();
    private final Set<String> begun=new HashSet<>();
    private Damage.Source deliverySource;
    private boolean calculated,closed;
    public EnemyNativeStrikeScope(EnemyOffenseSnapshot offense,UUID nativeActor,UUID victim,DamageCalculator expectedCalculator,
            BooleanSupplier currentBinding,Consumer<EnemyAppliedHit> accepted,Consumer<Throwable> rejected){
        this.offense=Objects.requireNonNull(offense);this.nativeActor=Objects.requireNonNull(nativeActor);
        this.victim=Objects.requireNonNull(victim);
        this.expectedCalculator=Objects.requireNonNull(expectedCalculator);this.currentBinding=Objects.requireNonNull(currentBinding);
        this.accepted=Objects.requireNonNull(accepted);this.rejected=Objects.requireNonNull(rejected);
    }
    /** Exact original component identity from the accepted writer-owned action root. */
    public String originalReceipt(NativeDamageReceiptHook.Context context){
        if(closed||!calculated||!currentBinding.getAsBoolean())
            throw new IllegalStateException("ENEMY_ORIGINAL_RECEIPT_NOT_ACTIVE");
        return originalReceipt(offense,nativeActor,victim,context);
    }
    public static String originalReceipt(EnemyOffenseSnapshot offense,UUID nativeActor,UUID victim,
            NativeDamageReceiptHook.Context context){
        Objects.requireNonNull(offense);Objects.requireNonNull(nativeActor);Objects.requireNonNull(victim);
        Objects.requireNonNull(context);
        var identity=offense.identity();
        if(!identity.worldId().equals(context.world())||!nativeActor.equals(context.executor())
                ||!nativeActor.equals(context.owner())||!nativeActor.equals(context.source())
                ||!victim.equals(context.target())||!identity.executionId().equals(context.initialRoot())
                ||!offense.affixedVector().containsKey(context.cause())
                ||context.chainId()<0||context.operationIndex()<0||context.operationCounter()<0
                ||context.componentIndex()<0||context.componentIndex()>=offense.affixedVector().size())
            throw new IllegalStateException("ENEMY_ORIGINAL_RECEIPT_CONTEXT_CHANGED");
        // Native chain coordinates prove the active invocation above, but are process-local.
        // The receipt itself uses only the writer-owned root and stable strike/component/target.
        return RewardIntent.digest(new com.google.gson.Gson().toJson(List.of("me.original-hit",
                identity.worldId(),identity.actorId(),nativeActor,offense.generation(),identity.rootId(),
                identity.executionId(),identity.authoredTickId(),victim,context.componentIndex(),context.cause())));
    }
    @Override public Object2FloatMap<DamageCause> calculate(DamageCalculator calculator,double runtime,Supplier<Object2FloatMap<DamageCause>> original){
        if(closed||calculated||calculator!=expectedCalculator||!currentBinding.getAsBoolean())throw new IllegalStateException("INVALID_ENEMY_NATIVE_STRIKE_BINDING");
        // Only a certified no-post-calculator-conversion/source-scaling binding may install this scope.
        // Native impact conversion/attributes must already be resolved in the acceptance snapshot adapter.
        var vector=new Object2FloatOpenHashMap<DamageCause>();
        for(var entry:offense.affixedVector().entrySet()){
            var cause=DamageCause.getAssetMap().getAsset(entry.getKey());
            if(cause==null)throw new IllegalStateException("ENEMY_NATIVE_CAUSE_MISSING:"+entry.getKey());
            vector.put(cause,entry.getValue().floatValue());
        }
        calculated=true;return vector;
    }
    @Override public boolean sourceFactorsResolved(){return true;}
    @Override public Consumer<HytaleDamageAdapter.NativeResult> component(Damage damage){
        if(closed||!calculated||!currentBinding.getAsBoolean())throw new IllegalStateException("ENEMY_STRIKE_RECEIPT_BINDING");
        if(deliverySource==null){
            var sequence=damage.getIfPresentMetaObject(DamageCalculatorSystems.DAMAGE_SEQUENCE);
            if(sequence==null||sequence.getDamageCalculator()!=expectedCalculator)throw new IllegalStateException("ENEMY_NATIVE_CALCULATOR_WITNESS_MISSING");
            deliverySource=damage.getSource();
        }
        if(damage.getSource()!=deliverySource)throw new IllegalStateException("ENEMY_NATIVE_DELIVERY_SOURCE_CHANGED");
        String cause=damage.getCause().getId();
        if(!offense.affixedVector().containsKey(cause)||!begun.add(cause))throw new IllegalStateException("ENEMY_NATIVE_COMPONENT_MISMATCH");
        return result->{
            if(closed||results.putIfAbsent(cause,Objects.requireNonNull(result))!=null)throw new IllegalStateException("ENEMY_NATIVE_RECEIPT_REPLAY");
        };
    }
    @Override public void completed(Throwable failure){
        if(closed)return;closed=true;
        if(failure!=null){rejected.accept(failure);return;}
        // An early native rejection before its calculator is a miss, not a completed damage receipt.
        if(!calculated)return;
        if(!currentBinding.getAsBoolean()){rejected.accept(new IllegalStateException("ENEMY_STRIKE_GENERATION_CHANGED"));return;}
        EnemyAppliedHit receipt;
        try{receipt=EnemyAppliedHit.completed(offense,victim,results);}
        catch(RuntimeException invalid){rejected.accept(invalid);return;}
        accepted.accept(receipt);
    }
}
