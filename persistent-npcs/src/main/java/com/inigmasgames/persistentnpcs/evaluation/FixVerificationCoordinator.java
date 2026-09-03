package com.inigmasgames.persistentnpcs.evaluation;

import java.util.List;

/** Accepts a fix only when the source failure and all required neighbors pass. */
public final class FixVerificationCoordinator {
    public Verification verify(String failureId, boolean exactReplayPassed,
            List<VariantResult> variants, boolean crossProfilePassed) {
        List<VariantResult> required = List.copyOf(variants == null ? List.of() : variants);
        boolean allVariants = !required.isEmpty() && required.stream().allMatch(
                VariantResult::passed);
        boolean accepted = exactReplayPassed && allVariants && crossProfilePassed;
        return new Verification(failureId, exactReplayPassed, required,
                crossProfilePassed, accepted, accepted ? "VERIFIED" : "NOT_FIXED");
    }

    public record VariantResult(String id, ScenarioVariantGenerator.VariantKind kind,
            boolean passed, String diagnostic) { }
    public record Verification(String failureId, boolean exactReplayPassed,
            List<VariantResult> variants, boolean crossProfilePassed,
            boolean accepted, String status) { }
}
