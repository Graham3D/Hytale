package com.inigmasgames.hytalerpg.execution.support;

import java.util.UUID;

/** Exact post-mitigation native damage bridge; only a forced receipt may reduce the float hit. */
public final class CycloneDamageStage {
    private CycloneDamageStage(){}
    public record Resolution(CycloneBarrierLedger.Debit debit,float nativeRemainder){}
    public static Resolution absorb(CycloneBarrierLedger ledger,CycloneBarrierLedger.Lease lease,
            UUID generation,String receipt,float filteredDamage){
        if(!Float.isFinite(filteredDamage)||filteredDamage<0)
            throw new IllegalArgumentException("INVALID_FILTERED_DAMAGE");
        var debit=ledger.debit(lease,generation,receipt,filteredDamage);
        float remaining=debit.absorbed()>0
                ?CycloneBarrierLedger.nativeRemainder(debit.damageRemaining()):filteredDamage;
        return new Resolution(debit,remaining);
    }
}
