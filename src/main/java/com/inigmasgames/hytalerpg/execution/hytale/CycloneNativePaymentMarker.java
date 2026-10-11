package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.inigmasgames.hytalerpg.execution.support.CycloneBarrierLedger;
import java.util.ArrayList;
import java.util.UUID;

/**
 * A zero-effect marker in the same native EntityStatValue record as paid Mana.
 * It is installed only after the journal has forced ACTIVE. A native save made
 * before installation has no proof and must forfeit protection on reconnect.
 */
public final class CycloneNativePaymentMarker {
    private static final String PREFIX="hywind.cyclone.paid.";
    private CycloneNativePaymentMarker(){}

    public static void mark(EntityStatMap stats,UUID generation,boolean reflectiveWard){
        int mana=DefaultEntityStatTypes.getMana();
        var value=stats.get(mana);
        if(value==null)throw new IllegalStateException("CYCLONE_NATIVE_MANA_MISSING");
        float before=value.get(),maximum=value.getMax();
        if(!Float.isFinite(before))throw new IllegalStateException("CYCLONE_NATIVE_MANA_INVALID");
        if(value.getModifiers()!=null)for(String old:new ArrayList<>(value.getModifiers().keySet()))
            if(old.startsWith(PREFIX))stats.removeModifier(mana,old);
        String marker=key(generation,reflectiveWard);
        stats.putModifier(mana,marker,new StaticModifier(
                Modifier.ModifierTarget.MAX,StaticModifier.CalculationType.ADDITIVE,0));
        stats.update();
        if(stats.get(mana).get()!=before||stats.get(mana).getMax()!=maximum){
            stats.removeModifier(mana,marker);stats.update();
            throw new IllegalStateException("CYCLONE_PAYMENT_MARKER_CHANGED_MANA");
        }
    }

    public static boolean confirms(EntityStatMap stats,UUID generation){
        if(stats==null||generation==null)return false;
        int mana=DefaultEntityStatTypes.getMana();
        var value=stats.get(mana);
        if(value==null)return false;
        // A recovered Reflective Ward needs its original cast context for the
        // secondary native damage path; conservatively forfeit until that
        // context has its own durable representation.
        var modifier=value.getModifier(key(generation,false));
        return modifier instanceof StaticModifier staticModifier
                &&modifier.getTarget()==Modifier.ModifierTarget.MAX
                &&staticModifier.getCalculationType()==StaticModifier.CalculationType.ADDITIVE
                &&staticModifier.getAmount()==0;
    }

    /** Recover only from the loaded native stat record. Balance comparisons and
     * the journal's in-memory payment receipt are not a persistence proof. */
    public static CycloneBarrierLedger.State recover(CycloneBarrierLedger ledger,
            CycloneBarrierLedger.Lease lease,EntityStatMap stats){
        var state=ledger.state(lease);
        if(!ledger.paymentRecoveryRequired(lease))return state;
        var health=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
        if(state.phase()==CycloneBarrierLedger.Phase.ACTIVE&&health!=null&&health.get()>0
                &&confirms(stats,state.generation()))
            return ledger.reconcileNativePayment(lease,state.generation());
        return ledger.invalidateUncertainPayment(lease);
    }

    private static String key(UUID generation,boolean reflectiveWard){
        return PREFIX+generation+(reflectiveWard?".reflective":".plain");
    }
}
