package com.inigmasgames.hytalerpg.execution.support;

import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.combat.resource.RpgResourceService;
import java.util.UUID;
import java.util.Objects;

/** Converts only confirmed native Mana loss into post-shield Health protection. */
public final class ManaBeforeLifeStage {
    private ManaBeforeLifeStage() {}
    public record Resolution(float healthDamage, double manaSpent, double intended, boolean uncertain) {}

    public static Resolution apply(float incoming, double equipmentPercent, UUID actor,
                                   RpgResourceService owner, NativeResourcePort nativeResources) {
        if (!Float.isFinite(incoming) || incoming < 0 || !Double.isFinite(equipmentPercent)
                || equipmentPercent < 0 || actor == null) throw new IllegalArgumentException("Invalid Mana Before Life hit");
        Objects.requireNonNull(owner);Objects.requireNonNull(nativeResources);
        // Keep native before/write/readback indivisible with other resource-owner debits.
        synchronized(owner){return applyLocked(incoming,equipmentPercent,actor,owner,nativeResources);}
    }
    private static Resolution applyLocked(float incoming,double equipmentPercent,UUID actor,
                                          RpgResourceService owner,NativeResourcePort nativeResources){
        double intended = incoming * Math.min(0.6, equipmentPercent);
        if (intended <= 0) return new Resolution(incoming, 0, 0, false);
        double before;
        try { before = nativeResources.current(ResourceType.MANA); }
        catch (RuntimeException unavailable) { return new Resolution(incoming, 0, intended, true); }
        double reported = 0;
        boolean uncertain = false;
        try { reported = owner.drainSpendableMana(actor, intended, nativeResources, () -> true); }
        catch (RuntimeException failure) { uncertain = true; }
        double after;
        try { after = nativeResources.current(ResourceType.MANA); }
        catch (RuntimeException unavailable) {
            // drainSpendableMana returns only after its own native readback and
            // allowance check. A later diagnostic read failure cannot erase
            // that already confirmed debit; a thrown drain remains uncertain.
            double confirmed=!uncertain&&Double.isFinite(reported)&&reported>=0&&reported<=intended
                    ?reported:0;
            return new Resolution(CycloneBarrierLedger.nativeRemainder(incoming-confirmed),
                    confirmed,intended,true);
        }
        // The native readback is the authority even when a write threw after it committed.
        double confirmed = Double.isFinite(before) && Double.isFinite(after)
                ? Math.max(0, Math.min(intended, before - after)) : 0;
        if (!uncertain && Math.abs(confirmed - reported) > 1e-3) uncertain = true;
        float remainder = CycloneBarrierLedger.nativeRemainder(incoming - confirmed);
        return new Resolution(remainder, confirmed, intended, uncertain);
    }
}
