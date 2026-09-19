package com.inigmasgames.persistentnpcs.epistemic;

import java.util.List;

public record AtomicClaimResult(AtomicClaim claim, ClaimSupportStatus status,
        List<String> evidenceIds, String reason) {
    public AtomicClaimResult {
        if (claim == null || status == null) throw new IllegalArgumentException("claim result required");
        evidenceIds = List.copyOf(evidenceIds == null ? List.of() : evidenceIds);
        reason = reason == null ? "" : reason.strip();
    }
    public boolean releasable() {
        return status == ClaimSupportStatus.SUPPORTED
                || status == ClaimSupportStatus.SUPPORTED_AS_INFERENCE
                || status == ClaimSupportStatus.SUBJECTIVE_ALLOWED
                || status == ClaimSupportStatus.HYPOTHETICAL_ALLOWED;
    }
}
