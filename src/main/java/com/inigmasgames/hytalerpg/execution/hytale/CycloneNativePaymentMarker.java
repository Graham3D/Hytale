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
    public enum Kind { NONE, PLAIN, REFLECTIVE }
    private CycloneNativePaymentMarker(){}

    public static void mark(EntityStatMap stats,UUID generation,boolean reflectiveWard){
        mark(stats,generation,reflectiveWard,old->false);
    }

    /** Retain payment evidence for earlier generations with a live Shared Aegis child. */
    public static void mark(EntityStatMap stats,UUID generation,boolean reflectiveWard,
            java.util.function.Predicate<UUID> keepPriorGeneration){
        int mana=DefaultEntityStatTypes.getMana();
        var value=stats.get(mana);
        if(value==null)throw new IllegalStateException("CYCLONE_NATIVE_MANA_MISSING");
        float before=value.get(),maximum=value.getMax();
        if(!Float.isFinite(before))throw new IllegalStateException("CYCLONE_NATIVE_MANA_INVALID");
        if(value.getModifiers()!=null)for(String old:new ArrayList<>(value.getModifiers().keySet())){
            if(!old.startsWith(PREFIX))continue;
            UUID prior=markerGeneration(old);
            if(prior==null||prior.equals(generation)||!keepPriorGeneration.test(prior))
                stats.removeModifier(mana,old);
        }
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
        return kind(stats,generation)==Kind.PLAIN;
    }

    public static Kind kind(EntityStatMap stats,UUID generation){
        if(stats==null||generation==null)return Kind.NONE;
        int mana=DefaultEntityStatTypes.getMana();
        var value=stats.get(mana);
        if(value==null)return Kind.NONE;
        boolean plain=valid(value.getModifier(key(generation,false)));
        boolean reflective=valid(value.getModifier(key(generation,true)));
        if(plain==reflective)return Kind.NONE;
        return reflective?Kind.REFLECTIVE:Kind.PLAIN;
    }
    private static boolean valid(Modifier modifier){
        return modifier instanceof StaticModifier staticModifier
                &&modifier.getTarget()==Modifier.ModifierTarget.MAX
                &&staticModifier.getCalculationType()==StaticModifier.CalculationType.ADDITIVE
                &&staticModifier.getAmount()==0;
    }

    /** Recover only from the loaded native stat record. Balance comparisons and
     * the journal's in-memory payment receipt are not a persistence proof. */
    public static CycloneBarrierLedger.State recover(CycloneBarrierLedger ledger,
            CycloneBarrierLedger.Lease lease,EntityStatMap stats){
        return recover(ledger,lease,stats,null);
    }

    /** Reflective recovery also requires a valid frozen behavior journal for the
     * exact paid generation. Both authorities are checked before publication. */
    public static CycloneBarrierLedger.State recover(CycloneBarrierLedger ledger,
            CycloneBarrierLedger.Lease lease,EntityStatMap stats,
            com.inigmasgames.hytalerpg.execution.support.CycloneReflectionJournal reflections){
        var state=ledger.state(lease);
        if(!ledger.paymentRecoveryRequired(lease))return state;
        var health=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
        var marker=kind(stats,state.generation());
        if(state.phase()==CycloneBarrierLedger.Phase.ACTIVE&&health!=null&&health.get()>0
                &&(marker==Kind.PLAIN||marker==Kind.REFLECTIVE&&reflections!=null&&reflections.load(state)!=null))
            return ledger.reconcileNativePayment(lease,state.generation());
        return ledger.invalidateUncertainPayment(lease);
    }

    private static String key(UUID generation,boolean reflectiveWard){
        return PREFIX+generation+(reflectiveWard?".reflective":".plain");
    }
    private static UUID markerGeneration(String key){
        String suffix=key.endsWith(".reflective")?".reflective":key.endsWith(".plain")?".plain":null;
        if(suffix==null)return null;
        try{return UUID.fromString(key.substring(PREFIX.length(),key.length()-suffix.length()));}
        catch(IllegalArgumentException invalid){return null;}
    }
}
