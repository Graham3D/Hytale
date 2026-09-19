package com.inigmasgames.persistentnpcs.epistemic;

import com.inigmasgames.persistentnpcs.conversation.AdaptiveReasoningDecision;
import com.inigmasgames.persistentnpcs.conversation.AdaptiveReasoningPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Frozen regressions promoted only after the live Mara/Lycander behavior probe passed. */
public final class R091GateABehaviorHardeningTest {
    private R091GateABehaviorHardeningTest() { }

    public static void main(String[] args) {
        System.setProperty(EpistemicFeatureMode.PROPERTY, "AUTHORITATIVE");
        semanticFamiliesCoverAdjacentVariants();
        emptyHandRealizesObservedAbsence();
        ambiguousActionsClarifyWithoutActionAdmission();
        desireAndEmotionPreserveTypedSelfState();
        System.out.println("R091 Gate-A behavior hardening regressions passed.");
    }

    private static void semanticFamiliesCoverAdjacentVariants() {
        for (String value : List.of("What am I holding?", "Am I holding anything?",
                "What is in my hand?")) assert kind(value) == EpistemicQueryKind.CURRENT_PERCEPTION;
        for (String value : List.of("Put it there.", "Move that over here.", "Take this.",
                "Drop it there.")) {
            EpistemicContract contract = base(value);
            assert kind(value) == EpistemicQueryKind.ACTION_REQUEST : value;
            assert contract.dialogueFrame().ambiguous() : value;
        }
        for (String value : List.of("What do you want?", "What is your goal?",
                "What are you trying to do?")) {
            EpistemicContract contract = base(value);
            assert kind(value) == EpistemicQueryKind.SUBJECTIVE_PREFERENCE : value;
            assert contract.dialogueFrame().predicateKey().equals("DESIRE") : value;
        }
        for (String value : List.of("How do you feel?", "Are you okay?", "Are you happy?")) {
            EpistemicContract contract = base(value);
            assert kind(value) == EpistemicQueryKind.SUBJECTIVE_PREFERENCE : value;
            assert contract.dialogueFrame().predicateKey().equals("EMOTION") : value;
        }
        assert kind("What color is the dragon behind the moon?")
                == EpistemicQueryKind.OBJECTIVE_PROPERTY;
    }

    private static void emptyHandRealizesObservedAbsence() {
        EpistemicContract held = contract("What am I holding?", Answerability.KNOWN,
                List.of(evidence("held-none", EvidenceSourceKind.DIRECT_OBSERVATION,
                        "CURRENT_PLAYER", "HELD_ITEM", "NONE")));
        var result = new EpistemicClaimFirewall().validate(
                "I can't tell you that safely.", held, false);
        assert result.valid() && result.dialogue().equals("You're holding nothing.") : result;
        assert result.claims().stream().allMatch(value -> value.releasable()) : result;
    }

    private static void ambiguousActionsClarifyWithoutActionAdmission() {
        for (String value : List.of("Put it there.", "Move that over here.", "Drop it there.")) {
            EpistemicContract contract = contract(value, Answerability.AMBIGUOUS, List.of());
            AdaptiveReasoningDecision route = EpistemicProductionRoute.reasoning(contract,
                    new AdaptiveReasoningDecision(AdaptiveReasoningPolicy.DIRECT_ACTION,
                            List.of("LEGACY")));
            assert route.policy() == AdaptiveReasoningPolicy.GROUNDED_DIALOGUE : route;
            var result = new EpistemicClaimFirewall().validate("Put it there.", contract, false);
            assert result.valid() && result.dialogue().contains("Which object")
                    && result.dialogue().contains("where") : value + " -> " + result;
        }
        EpistemicContract take = contract("Take this.", Answerability.AMBIGUOUS, List.of());
        var result = new EpistemicClaimFirewall().validate("Take it.", take, false);
        assert result.valid() && result.dialogue().equals("Which object do you mean?") : result;
    }

    private static void desireAndEmotionPreserveTypedSelfState() {
        EpistemicContract desire = contract("What do you want?", Answerability.SUBJECTIVE,
                List.of(evidence("goal", EvidenceSourceKind.AUTHORED_CANON,
                        "CURRENT_NPC", "DESIRE", "protect Mara")));
        var desireResult = new EpistemicClaimFirewall().validate(
                "I'm not certain enough to answer that.", desire, false);
        assert desireResult.valid() && desireResult.dialogue().startsWith("I want ")
                : desireResult;

        EpistemicContract emotion = contract("How do you feel?", Answerability.SUBJECTIVE,
                List.of(evidence("emotion", EvidenceSourceKind.SELF_STATE,
                        "CURRENT_NPC", "EMOTION", "CALM")));
        var emotionResult = new EpistemicClaimFirewall().validate("I feel excited.", emotion,
                false);
        assert emotionResult.valid() && emotionResult.dialogue().equals("I feel calm.")
                : emotionResult;
    }

    private static EpistemicContract contract(String utterance, Answerability answerability,
            List<EvidenceRef> evidence) {
        EpistemicContract base = base(utterance);
        EvidencePacket packet = new EvidencePacket(EvidencePacket.SCHEMA_VERSION,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                base.queryPlan().queryKind(), base.dialogueFrame().subjectKey(),
                base.dialogueFrame().predicateKey(), evidence, List.of(), List.of(), List.of(),
                evidence.isEmpty() ? EvidenceSufficiency.NONE : EvidenceSufficiency.SUFFICIENT,
                List.of(), List.of(), 0, List.of(), evidence.stream()
                        .map(EvidenceRef::sourceKind).distinct().toList(), 64, 1, false);
        EpistemicAnswerPlanner.Result planned = EpistemicAnswerPlanner.compile(
                base.dialogueFrame(), base.queryPlan(), packet, answerability, true);
        return new EpistemicContract(base.schemaVersion(), EpistemicFeatureMode.AUTHORITATIVE,
                base.dialogueFrame(), base.queryPlan(), packet, answerability,
                planned.answerPlan(), planned.claimPolicy(), base.budget(), List.of("R091"),
                base.planningMicros(), Instant.now());
    }

    private static EpistemicContract base(String utterance) {
        return EpistemicShadowAnalyzer.analyzeInitial(utterance, new ConversationWorkspace());
    }

    private static EpistemicQueryKind kind(String utterance) {
        return EpistemicQueryKind.valueOf(base(utterance).queryPlan().queryKind());
    }

    private static EvidenceRef evidence(String id, EvidenceSourceKind source, String subject,
            String predicate, String object) {
        return new EvidenceRef(EvidenceRef.SCHEMA_VERSION, id, source, EpistemicStatus.KNOWN,
                1, true, subject + " " + predicate + " " + object, subject, predicate, object,
                Instant.now(), "CURRENT", "R091_TEST", true, true, "", "CURRENT");
    }
}
