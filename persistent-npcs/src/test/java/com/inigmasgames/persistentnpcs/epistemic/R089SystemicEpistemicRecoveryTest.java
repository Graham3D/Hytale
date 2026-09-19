package com.inigmasgames.persistentnpcs.epistemic;

import com.inigmasgames.persistentnpcs.conversation.CognitiveContextPlan;
import com.inigmasgames.persistentnpcs.conversation.CognitiveDepth;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Permanent regression for the connected Lycander EPI-001 containment failure. */
public final class R089SystemicEpistemicRecoveryTest {
    public static void main(String[] args) throws Exception {
        recallEvidenceCannotBeLaunderedAcrossPredicates();
        rejectedDraftReceivesValidatedAnswerPlanRecovery();
        disclosurePrefaceIsNotASecretFactQuery();
        authoritativeRouteOwnsCognitionContext();
        sentinelOnlyGuardsFinalValidatedCandidate();
        System.out.println("R089 systemic epistemic recovery tests passed.");
    }

    private static void recallEvidenceCannotBeLaunderedAcrossPredicates() {
        assert EpistemicEvidenceRetriever.episodicPredicate(
                "Player said: \"I have hidden a magical sword outside in the desert.\"",
                MemoryType.EPISODIC, "LOCATION_REPORT").equals("PAST_EVENT");
        assert EpistemicEvidenceRetriever.episodicPredicate(
                "Player said: \"I have four jars in my pocket.\"",
                MemoryType.EPISODIC, "PLAYER_REPORT").equals("POSSESSION");
        assert EpistemicEvidenceRetriever.episodicPredicateCompatible(
                "PAST_EVENT", "PAST_EVENT");
        assert !EpistemicEvidenceRetriever.episodicPredicateCompatible(
                "PAST_EVENT", "POSSESSION")
                : "possession was laundered into a hiding event";

        List<AtomicClaim> claims = new AtomicClaimExtractor().extract(
                "You hid the four jars in your pocket.");
        assert claims.size() == 1;
        assert claims.getFirst().subjectKey().equals("CURRENT_PLAYER");
        assert claims.getFirst().predicateKey().equals("PAST_EVENT");
    }

    private static void rejectedDraftReceivesValidatedAnswerPlanRecovery() {
        EvidenceRef hiddenSword = new EvidenceRef(EvidenceRef.SCHEMA_VERSION,
                "trace:hidden-sword", EvidenceSourceKind.PLAYER_TESTIMONY,
                EpistemicStatus.BELIEVED, .72, true,
                "Player said: \"I have hidden a magical sword outside in the desert.\"",
                "CURRENT_PLAYER", "PAST_EVENT",
                "hidden a magical sword outside in the desert", Instant.now(), "RECENT",
                "CURRENT_PLAYER", false, false, "", "HISTORICAL");
        EpistemicContract contract = recallContract(hiddenSword);
        EpistemicClaimFirewall.Result result = new EpistemicClaimFirewall().validate(
                "You hid the four jars in your pocket.", contract, false);
        assert result.valid() : result;
        assert result.repaired();
        assert result.dialogue().equals(
                "You told me you had hidden a magical sword outside in the desert.")
                : result.dialogue();
        assert result.claims().stream().allMatch(AtomicClaimResult::releasable);
        assert result.claims().stream().noneMatch(value ->
                value.claim().text().toLowerCase().contains("four jars"));
    }

    private static void disclosurePrefaceIsNotASecretFactQuery() {
        EpistemicContract preface = EpistemicShadowAnalyzer.analyzeInitial(
                "I have a secret to tell you.", new ConversationWorkspace());
        assert preface.queryPlan().queryKind().equals("GENERAL_SOCIAL") : preface;
        assert preface.dialogueFrame().expectedAnswer() == ExpectedAnswerKind.OPEN_RESPONSE;

        EpistemicContract query = EpistemicShadowAnalyzer.analyzeInitial(
                "Tell me the silver sword secret.", new ConversationWorkspace());
        assert query.queryPlan().queryKind().equals("RELATIONSHIP_FACT") : query;
        assert query.dialogueFrame().predicateKey().equals("SECRET");
    }

    private static void authoritativeRouteOwnsCognitionContext() {
        EpistemicContract recall = EpistemicShadowAnalyzer.analyzeInitial(
                "What did I hide and where did I hide it?", new ConversationWorkspace());
        CognitiveContextPlan legacy = new CognitiveContextPlan(CognitiveDepth.SIMPLE_SOCIAL,
                "SIMPLE_SOCIAL_RESPONSE", Set.of("PROFILE", "RECENT_CONVERSATION"),
                Set.of(), List.of());
        CognitiveContextPlan routed = EpistemicProductionRoute.context(recall, legacy);
        assert routed.depth() == CognitiveDepth.DIRECT_FACT : routed;
        assert routed.detectedIntent().equals("EPISTEMIC_EPISODIC_RECALL") : routed;
    }

    private static void sentinelOnlyGuardsFinalValidatedCandidate() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/conversation/ConversationService.java"));
        int validityCheck = source.indexOf("if (!epistemicValidation.valid())");
        int sentinel = source.indexOf("requireSentinel(profile, responseId,",
                validityCheck - 2_000);
        assert validityCheck >= 0 && sentinel > validityCheck
                : "known-invalid draft still reaches Sentinel before recovery ownership";
    }

    private static EpistemicContract recallContract(EvidenceRef evidence) {
        EpistemicContract base = EpistemicShadowAnalyzer.analyzeInitial(
                "What did I hide and where did I hide it?", new ConversationWorkspace());
        EvidencePacket packet = new EvidencePacket(EvidencePacket.SCHEMA_VERSION,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                base.queryPlan().queryKind(), "CURRENT_PLAYER", "PAST_EVENT",
                List.of(evidence), List.of(), List.of(), List.of(),
                EvidenceSufficiency.PARTIAL, List.of(), List.of(), 0, List.of(),
                List.of(EvidenceSourceKind.PLAYER_TESTIMONY), 24, 1, false);
        AnswerPlan answer = new AnswerPlan(AnswerPlan.SCHEMA_VERSION, "RECALL",
                List.of(evidence.compactProposition()), List.of(evidence),
                Answerability.PARTIALLY_KNOWN.name(), 2, 1, Set.of(), Set.of(),
                "E3_AUTHORITATIVE", "answer directly", List.of(), "", List.of());
        return new EpistemicContract(base.schemaVersion(), EpistemicFeatureMode.AUTHORITATIVE,
                base.dialogueFrame(), base.queryPlan(), packet,
                Answerability.PARTIALLY_KNOWN, answer, base.claimPolicy(), base.budget(),
                List.of("R089_CONNECTED_TRACE_REGRESSION"), base.planningMicros(),
                Instant.now());
    }
}
