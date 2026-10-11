package com.inigmasgames.hytalerpg.execution.support;

import com.inigmasgames.hytalerpg.combat.cooldown.RpgCooldownService;
import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.combat.resource.RpgResourceService;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * R261-A transaction seam. The existing execution owner drives these stages on its
 * native/worker threads; this class never blocks on a cooldown future or holds a
 * native resource port. An uncertain activation write is NEVER compensated: the
 * durable active record might have landed, so refunding could create free protection.
 */
public final class CycloneActivationTransaction {
    public enum Result { PREPARED, DUPLICATE_ACTIVE, DUPLICATE_INACTIVE, PENDING, ACTIVE, ABORTED, RECOVERY_REQUIRED }
    public interface Compensation {
        /** Implementations must use the original receipt and be idempotent. */
        void refundCooldown();
        void refundMana();
    }
    private final CycloneBarrierLedger ledger;
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value="EI_EXPOSE_REP2",
            justification="The cast coordinator intentionally shares the single injected barrier authority.")
    public CycloneActivationTransaction(CycloneBarrierLedger ledger){this.ledger=Objects.requireNonNull(ledger);}

    public Result begin(CycloneBarrierLedger.Lease lease,UUID activation,UUID generation,double capacity,
                        BooleanSupplier eligible,BooleanSupplier spendable,BooleanSupplier cooldownReady){
        Objects.requireNonNull(eligible);Objects.requireNonNull(spendable);Objects.requireNonNull(cooldownReady);
        // Admission order is explicit: no pending durable record for a rejected cast.
        if(!eligible.getAsBoolean())throw new IllegalStateException("CYCLONE_INELIGIBLE");
        var current=ledger.state(lease);
        if(Objects.equals(current.activation(),activation)){
            return switch(current.phase()){
                case ACTIVE->Result.DUPLICATE_ACTIVE;
                case PENDING,RECOVERY_PENDING->Result.PENDING;
                default->Result.DUPLICATE_INACTIVE;
            };
        }
        if(ledger.activationSeen(lease,activation))return Result.DUPLICATE_INACTIVE;
        if(current.phase()!=CycloneBarrierLedger.Phase.NONE&&current.phase()!=CycloneBarrierLedger.Phase.DEPLETED)
            throw new IllegalStateException("CYCLONE_BARRIER_ALREADY_ACTIVE_OR_PENDING");
        if(!spendable.getAsBoolean())throw new IllegalStateException("CYCLONE_RESOURCE_UNAVAILABLE");
        if(!cooldownReady.getAsBoolean())throw new IllegalStateException("CYCLONE_COOLDOWN_UNAVAILABLE");
        ledger.begin(lease,activation,generation,capacity);
        return Result.PREPARED;
    }
    /** Call only after RpgResourceService confirms the native resource debit. */
    public void manaCommitted(CycloneBarrierLedger.Lease lease,UUID activation,String resourceReceipt,double paid){
        ledger.confirmMana(lease,activation,resourceReceipt,paid);
    }
    /** Owner-thread bridge to the existing native Mana authority. The caller retains the
     * cost token for compensation if the subsequent journal force fails. */
    public void commitNativeMana(CycloneBarrierLedger.Lease lease,UUID activation,
                                 RpgResourceService resources,RpgResourceService.CostToken token,
                                 NativeResourcePort port){
        Objects.requireNonNull(resources);Objects.requireNonNull(token);Objects.requireNonNull(port);
        if(!lease.player().equals(token.actor())||token.cost().type()!=ResourceType.MANA
                ||token.cost().amount()<=0||!Double.isFinite(token.cost().amount()))
            throw new IllegalArgumentException("CYCLONE_NATIVE_MANA_TOKEN_INVALID");
        double before=port.current(ResourceType.MANA);
        if(!resources.commitCost(token,port))throw new IllegalStateException("CYCLONE_NATIVE_MANA_ALREADY_COMMITTED");
        double after=port.current(ResourceType.MANA);
        if(!Double.isFinite(after)||before-after+1e-5<token.cost().amount())
            throw new IllegalStateException("CYCLONE_NATIVE_MANA_DEBIT_UNVERIFIED");
        ledger.confirmMana(lease,activation,token.tokenId().toString(),before-after);
    }
    /** Call only after RpgCooldownService confirms its durable spend. */
    public void cooldownCommitted(CycloneBarrierLedger.Lease lease,UUID activation,String cooldownReceipt){
        ledger.confirmCooldown(lease,activation,cooldownReceipt);
    }
    /** A Spend is accepted only after RpgCooldownService's persistence receipt completes. */
    public void commitNativeCooldown(CycloneBarrierLedger.Lease lease,UUID activation,
                                     RpgCooldownService cooldowns,RpgCooldownService.Spend spend){
        Objects.requireNonNull(cooldowns);Objects.requireNonNull(spend);
        if(!lease.player().equals(spend.actor())||!"spirit_shield".equals(spend.skill()))
            throw new IllegalArgumentException("CYCLONE_COOLDOWN_SPEND_INVALID");
        cooldowns.acceptSpend(spend);
        ledger.confirmCooldown(lease,activation,spend.token().toString());
    }
    /** Publish protection only after both confirmations are durably recorded. */
    public CycloneBarrierLedger.State publish(CycloneBarrierLedger.Lease lease,UUID activation){
        return ledger.activate(lease,activation);
    }
    public Result abort(CycloneBarrierLedger.Lease lease,UUID activation,
                        boolean manaCommitted,boolean cooldownCommitted,boolean activationWriteAttempted,
                        Compensation compensation){
        Objects.requireNonNull(compensation);
        var current=ledger.state(lease);
        if(current.phase()==CycloneBarrierLedger.Phase.ACTIVE)return Result.ACTIVE;
        if(current.phase()==CycloneBarrierLedger.Phase.DEPLETED)return Result.ABORTED;
        // A failed force() may still leave a complete ACTIVE record. Never refund here.
        if(activationWriteAttempted)return Result.RECOVERY_REQUIRED;
        boolean uncertain=false;
        if(cooldownCommitted)try{compensation.refundCooldown();}catch(RuntimeException failure){uncertain=true;}
        if(manaCommitted)try{compensation.refundMana();}catch(RuntimeException failure){uncertain=true;}
        try{ledger.abort(lease,activation,uncertain);}
        catch(RuntimeException failure){return Result.RECOVERY_REQUIRED;}
        return uncertain?Result.RECOVERY_REQUIRED:Result.ABORTED;
    }
}
