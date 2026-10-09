package com.inigmasgames.hytalerpg.execution.support;

import com.inigmasgames.hytalerpg.combat.status.StatusService;
import java.util.Objects;
import java.util.Optional;

/** WA-112 observer for the shared status owner's completed-cleanse event. */
public final class SupportCleanse {
    private SupportCleanse(){}
    public static Optional<FiniteSupportEffects.Effect> consume(FiniteSupportEffects finite,
            StatusService.CleanseReceipt receipt,double normalMaximumHealth){
        return Objects.requireNonNull(finite).cleanseResolved(receipt,normalMaximumHealth);
    }
}
