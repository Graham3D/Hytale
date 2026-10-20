package com.inigmasgames.hytalerpg.combat.resource;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Transaction coordinator over native EntityStatMap values. */
public final class RpgResourceService {
    private final CombatBalanceProfile profile;
    private final ReservationService reservations;
    private final Map<UUID, PendingCost> pending = new HashMap<>();
    private final Set<String> recoveredRootAttacks = new HashSet<>();
    public RpgResourceService(CombatBalanceProfile profile, ReservationService reservations) {
        this.profile = profile; this.reservations = reservations;
    }

    public ResourceCost evaluate(ResourceCost declared, CompiledSkillPlan.KernelModifiers modifiers) {
        return declared.modified(modifiers == null ? 1.0 : modifiers.resourceCostMultiplier());
    }
    /** Pinned native Health storage is float. Reject a nonlethal double payment that cannot subtract natively. */
    public static float nativeHealthTarget(float current,double requested){
        if(!Float.isFinite(current)||!Double.isFinite(requested)||requested<1||requested<current&&(float)requested>=current)
            throw new IllegalStateException("Health payment is lethal or below native float precision");
        return (float)requested;
    }
    public static float nativeCreditTarget(float current,double amount,double cap){
        if(!Float.isFinite(current)||current<0||!Double.isFinite(amount)||amount<0||!Double.isFinite(cap)||cap<0)
            throw new IllegalArgumentException("Invalid bounded native credit");
        double target=Math.min(cap,(double)current+amount);float narrowed=(float)target;
        if(narrowed>target)narrowed=Math.nextDown(narrowed);
        return Math.max(current,narrowed); // A recovery never removes current resource after a cap change.
    }
    public double spendableMaximum(UUID actor,ResourceType type,NativeResourcePort resources){
        if(type!=ResourceType.MANA&&type!=ResourceType.STAMINA)throw new IllegalArgumentException("No leechable resource");
        return type==ResourceType.MANA?reservations.spendableMaximum(actor,resources.maximum(type)):resources.maximum(type);
    }
    public RootLeechBudget.Recovery recoverLeech(RootLeechBudget budget,RootLeechBudget.HitReceipt receipt,NativeResourcePort resources){
        try{
            double maximum=budget.resource()==ResourceType.NONE?0:spendableMaximum(budget.actor(),budget.resource(),resources);
            return budget.recover(receipt,maximum,resources);
        }catch(RuntimeException unavailable){return budget.unavailable(receipt);}
    }
    /** One final integer boundary, after ordinary factors, additive Attunement stacks and named Health conversion. */
    public ResourceCost evaluateActivation(ResourceCost declared,CompiledSkillPlan plan,int attunementStacks) {
        if(attunementStacks<0||attunementStacks>5||attunementStacks>0&&!plan.resources().attunement())throw new IllegalArgumentException("Invalid Attunement stack count");
        double factor=plan.kernelModifiers().resourceCostMultiplier()*(1-.03*attunementStacks);
        if(!plan.resources().lifeblood())return declared.modified(factor);
        if(declared.type()!=ResourceType.MANA&&declared.type()!=ResourceType.STAMINA||declared.amount()<=0)
            throw new IllegalArgumentException("Lifeblood requires a positive upfront Mana/Stamina cost");
        return new ResourceCost(ResourceType.HEALTH,Math.max(1,Math.ceil(1.5*declared.amount()*factor)));
    }
    /** Continuous upkeep retains fractional units; the integer upfront-cost rule does not apply. */
    public ResourceCost evaluateUpkeep(ResourceCost slice, CompiledSkillPlan.KernelModifiers modifiers) {
        if(slice.type()==ResourceType.HEALTH)throw new IllegalArgumentException("Health upkeep is forbidden");
        double multiplier = modifiers == null ? 1.0 : modifiers.resourceCostMultiplier();
        if (!Double.isFinite(multiplier) || multiplier < 0) throw new IllegalArgumentException("Invalid upkeep multiplier");
        return new ResourceCost(slice.type(), slice.amount() * multiplier);
    }
    public synchronized boolean canAfford(UUID actor, ResourceCost cost, NativeResourcePort resources) {
        if (cost.type() == ResourceType.NONE) return true;
        double held = pending.values().stream().filter(p -> p.actor.equals(actor) && p.cost.type() == cost.type() && !p.committed)
                .mapToDouble(p -> p.cost.amount()).sum();
        double current=resources.current(cost.type());
        if(!Double.isFinite(current))return false;
        return cost.type()==ResourceType.HEALTH?current-cost.amount()-held>=1:current + 1.0e-9 >= cost.amount() + held;
    }
    public synchronized CostToken reserveCost(UUID actor, ResourceCost cost, NativeResourcePort resources) {
        if (!canAfford(actor, cost, resources)) throw new IllegalStateException("Insufficient " + cost.type());
        CostToken token = new CostToken(UUID.randomUUID(), actor, cost);
        pending.put(token.tokenId(), new PendingCost(actor, cost, false));
        return token;
    }
    public synchronized boolean commitCost(CostToken token, NativeResourcePort resources) {
        PendingCost hold = require(token);
        if (hold.committed) return false;
        if (hold.cost.type() != ResourceType.NONE) {
            double current = resources.current(hold.cost.type());
            if (!Double.isFinite(current)||(hold.cost.type()==ResourceType.HEALTH?current-hold.cost.amount()<1:current + 1.0e-9 < hold.cost.amount()))
                throw new IllegalStateException("Native resource changed before commit");
            resources.setCurrent(hold.cost.type(), current - hold.cost.amount());
        }
        pending.put(token.tokenId(), new PendingCost(hold.actor, hold.cost, true));
        return true;
    }
    public synchronized boolean refundIfUncommitted(CostToken token) {
        PendingCost hold = require(token);
        if (hold.committed) return false;
        pending.remove(token.tokenId());
        return true;
    }
    /** Explicit activation-transaction rollback. Never used for gameplay cancellation after dispatch. */
    public synchronized boolean refundCommittedCost(CostToken token, NativeResourcePort resources) {
        PendingCost hold = require(token);
        if (!hold.committed) return false;
        if (hold.cost.type() != ResourceType.NONE)
            addCapped(hold.cost.type(), hold.cost.amount(), resources.maximum(hold.cost.type()), resources);
        pending.remove(token.tokenId());
        return true;
    }
    public synchronized void finish(CostToken token) { pending.remove(token.tokenId()); }

    public double regenerate(UUID actor, ResourceType type, double seconds, NativeResourcePort resources) {
        if (type != ResourceType.MANA && type != ResourceType.STAMINA || seconds <= 0.0) return 0.0;
        double cap = type == ResourceType.MANA
                ? reservations.spendableMaximum(actor, resources.maximum(type)) : resources.maximum(type);
        return addCapped(type, resources.maximum(type) * profile.passiveRegenerationPerSecond * seconds, cap, resources);
    }
    public synchronized RecoveryResult recoverHostileWeaponHit(UUID actor, String rootAttackId, boolean charged,
                                                                NativeResourcePort resources) {
        if(actor==null||rootAttackId==null||rootAttackId.isBlank()||rootAttackId.length()>512)throw new IllegalArgumentException("Invalid diagnostic root identity");
        String dedup = actor + ":" + rootAttackId;
        // Retained command/test API, not the production hook. Fail closed at capacity; never evict and replay.
        if(recoveredRootAttacks.size()>=65536)return new RecoveryResult(false,0,0);
        if (!recoveredRootAttacks.add(dedup)) return new RecoveryResult(false, 0.0, 0.0);
        return recoverWeaponHit(actor,charged,resources);
    }
    public RecoveryResult recoverHostileWeaponHit(RootWeaponHit receipt,NativeResourcePort resources){
        if(!receipt.claimRecovery())return new RecoveryResult(false,0,0);
        return recoverWeaponHit(receipt.actor(),receipt.charged(),resources);
    }
    private RecoveryResult recoverWeaponHit(UUID actor,boolean charged,NativeResourcePort resources){
        double fraction = charged ? profile.chargedHostileHitRecovery : profile.normalHostileHitRecovery;
        double manaMaximum=resources.maximum(ResourceType.MANA),staminaMaximum=resources.maximum(ResourceType.STAMINA);
        if(!Double.isFinite(manaMaximum)||!Double.isFinite(staminaMaximum)||manaMaximum<=0||staminaMaximum<=0)
            throw new IllegalStateException("NATIVE_RECOVERY_MAXIMUM_UNAVAILABLE");
        double mana = resources.restoreResourceAtMost(ResourceType.MANA,manaMaximum*fraction,
                reservations.spendableMaximum(actor,manaMaximum));
        double stamina = resources.restoreResourceAtMost(ResourceType.STAMINA,staminaMaximum*fraction,staminaMaximum);
        return new RecoveryResult(true, mana, stamina);
    }
    public void restoreBed(UUID actor, NativeResourcePort resources) { restoreFull(actor, resources); }
    public void restoreHome(UUID actor, NativeResourcePort resources) { restoreFull(actor, resources); }
    private void restoreFull(UUID actor, NativeResourcePort resources) {
        resources.setCurrent(ResourceType.MANA, reservations.spendableMaximum(actor, resources.maximum(ResourceType.MANA)));
        resources.setCurrent(ResourceType.STAMINA, resources.maximum(ResourceType.STAMINA));
    }
    private static double addCapped(ResourceType type, double amount, double cap, NativeResourcePort resources) {
        double before = resources.current(type);
        double after = Math.min(cap, Math.max(0.0, before + amount));
        resources.setCurrent(type, after);
        return after - before;
    }
    private PendingCost require(CostToken token) {
        PendingCost hold = pending.get(token.tokenId());
        if (hold == null || !hold.actor.equals(token.actor()) || !hold.cost.equals(token.cost()))
            throw new IllegalArgumentException("Unknown or mismatched cost token");
        return hold;
    }
    private record PendingCost(UUID actor, ResourceCost cost, boolean committed) { }
    public record CostToken(UUID tokenId, UUID actor, ResourceCost cost) { }
    public record RecoveryResult(boolean applied, double manaRecovered, double staminaRecovered) { }
}
