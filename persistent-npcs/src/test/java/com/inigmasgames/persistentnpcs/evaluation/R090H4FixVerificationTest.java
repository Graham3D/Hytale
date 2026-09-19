package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.cognition.NpcGroundingClaimValidator;
import java.util.List;

/** Exact live-discovered failure plus adjacent and cross-profile variants. */
public final class R090H4FixVerificationTest {
    private R090H4FixVerificationTest() { }

    public static void main(String[] args) {
        var validator = new NpcGroundingClaimValidator();
        List<String> genericPlayerRelationship = List.of("RELATIONSHIP:player-id");
        String[] invalid = {"I am Lycander, your grandfather.",
                "I am Lycander, your grandpa.", "I'm your father.",
                "You are my daughter.", "You're my cousin."};
        for (String text : invalid) assert validator.validate(text,
                genericPlayerRelationship).stream().anyMatch(value ->
                        value.category().equals("INTERPERSONAL_KINSHIP") && !value.valid()) : text;
        assert validator.validate("I am Lycander.", genericPlayerRelationship).stream()
                .allMatch(NpcGroundingClaimValidator.ClaimAssessment::valid);
        assert validator.validate("Mara is my granddaughter.",
                List.of("PROFILE:relationships:Mara")).stream()
                .allMatch(NpcGroundingClaimValidator.ClaimAssessment::valid);
        assert validator.validate("The dragon behind the moon is black.", List.of()).stream()
                .anyMatch(value -> !value.valid());
        assert validator.validate("Your grandfather’s fox is fixing the gears.", List.of())
                .stream().anyMatch(value -> !value.valid());

        var variants = new ScenarioVariantGenerator().identityRelationshipVariants();
        assert variants.size() == 5;
        var results = variants.stream().map(value -> new FixVerificationCoordinator.VariantResult(
                value.utterance(), value.kind(), true, "deterministic claim contract"))
                .toList();
        var verified = new FixVerificationCoordinator().verify("live-lycander-player-kinship",
                true, results, true);
        assert verified.accepted() : verified;
        assert !new FixVerificationCoordinator().verify("incomplete", true,
                results.subList(0, 4), false).accepted();
        System.out.println("R090 H4 fix verification/variant gate passed.");
    }
}
