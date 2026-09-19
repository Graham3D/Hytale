package com.inigmasgames.persistentnpcs.epistemic;

import java.util.List;
import java.util.Set;

/** Permanent E0 corpus derived from real trace failures, not a sentence patch table. */
public final class EpistemicConversationCorpus {
    private EpistemicConversationCorpus() { }

    public static List<Fixture> fixtures() {
        return List.of(
            fixture("A_IDENTITY_RECALL", "What's my name?",
                    List.of("PLAYER_TESTIMONY:name=Graham exists in memory"),
                    DialogueAct.IDENTITY_QUERY, Answerability.KNOWN,
                    "WRONG_OR_INVENTED_IDENTITY", EvidenceSourceKind.PLAYER_TESTIMONY,
                    "Mara_2026-08-30_20-50-58.jsonl/R067"),
            fixture("B_UNSUPPORTED_OBJECT_PROPERTY", "Is this lantern flickering?",
                    List.of("DIRECT_OBSERVATION:player holds lantern", "NO flame/property state"),
                    DialogueAct.FACT_QUERY, Answerability.UNKNOWN,
                    "UNSUPPORTED_OBJECT_PROPERTY", EvidenceSourceKind.DIRECT_OBSERVATION,
                    "Mara_2026-08-30_20-50-58.jsonl/R067"),
            fixture("C_SOCIAL_AUTOBIOGRAPHY", "Do you have any friends?",
                    List.of("NO authored/learned friend relationship"), DialogueAct.FACT_QUERY,
                    Answerability.UNKNOWN, "INVENTED_RELATIONSHIP_OR_AUTOBIOGRAPHY",
                    EvidenceSourceKind.AUTHORED_CANON, "R035-R067 social regressions"),
            fixture("D_CURRENT_PERCEPTION", "What do you see around us?",
                    List.of("current SemanticWorldModel snapshot"), DialogueAct.PERCEPTION_QUERY,
                    Answerability.NEEDS_CURRENT_PERCEPTION, "UNOBSERVED_SCENE_FACT",
                    EvidenceSourceKind.DIRECT_OBSERVATION, "R030/R067"),
            fixture("E_CURRENT_ACTIVITY", "Where are you going?",
                    List.of("current task/plan/AgentOperation"), DialogueAct.SELF_STATE_QUERY,
                    Answerability.KNOWN, "INVENTED_CURRENT_ACTIVITY",
                    EvidenceSourceKind.SELF_STATE, "R029-R031/R067"),
            fixture("F_ACTION_FOLLOW", "Can you follow me?",
                    List.of("FOLLOW capability and authoritative action result required"),
                    DialogueAct.ACTION_REQUEST, Answerability.NEEDS_ACTION,
                    "UNCOMMITTED_ACTION_PROMISE", EvidenceSourceKind.ACTION_RESULT,
                    "R029/R060"),
            fixture("G_CLARIFICATION", "What did you mean by that?",
                    List.of("prior delivered canonical proposition/topic"),
                    DialogueAct.CLARIFICATION_REQUEST, Answerability.NEEDS_CLARIFICATION,
                    "UNBOUND_CLARIFICATION_INVENTION", EvidenceSourceKind.EPISODIC_MEMORY,
                    "R058-R060"),
            fixture("H_UNKNOWN_PROPERTY", "Is the lantern hot?",
                    List.of("lantern entity only; temperature unavailable"), DialogueAct.FACT_QUERY,
                    Answerability.UNKNOWN, "FABRICATED_UNKNOWN_PROPERTY",
                    EvidenceSourceKind.DIRECT_OBSERVATION, "R067"),
            fixture("I_MALFORMED_STT", "I want you to tell me what's in my",
                    List.of("incomplete authoritative Moonshine transcript"),
                    DialogueAct.UNRESOLVED, Answerability.NEEDS_CLARIFICATION,
                    "GUESSED_MISSING_UTTERANCE", EvidenceSourceKind.PLAYER_TESTIMONY,
                    "Mara_2026-08-30_20-50-58.jsonl/R067"));
    }

    /** E1 semantic-family and ambiguity coverage; assertions target meaning, not phrasing. */
    public static List<SemanticFixture> e1Fixtures() {
        return List.of(
                semantic("IDENTITY_1", "Do you remember my name?", "IDENTITY_RECALL", "NAME", ""),
                semantic("IDENTITY_2", "What did I tell you my name was?", "IDENTITY_RECALL", "NAME", ""),
                semantic("PERCEPTION_HELD_1", "What am I holding?", "CURRENT_PERCEPTION", "HELD_ITEM", ""),
                semantic("PERCEPTION_HELD_2", "Can you see what I'm holding?", "CURRENT_PERCEPTION", "HELD_ITEM", ""),
                semantic("PERCEPTION_SCENE", "Describe our surroundings.", "CURRENT_PERCEPTION", "VISIBLE", ""),
                semantic("FOLLOW_1", "Follow me.", "ACTION_REQUEST", "ACTION", "FOLLOW_PLAYER"),
                semantic("FOLLOW_2", "Come with me.", "ACTION_REQUEST", "ACTION", "FOLLOW_PLAYER"),
                semantic("FOLLOW_3", "Could you follow me?", "ACTION_REQUEST", "ACTION", "FOLLOW_PLAYER"),
                semantic("RELATIONSHIP_NPC", "Do you know Garrick?", "RELATIONSHIP_FACT", "RELATIONSHIP", ""),
                semantic("RELATIONSHIP_PLAYER", "Are we friends?", "RELATIONSHIP_FACT", "RELATIONSHIP", ""),
                semantic("PREFERENCE", "Do you like apples?", "SUBJECTIVE_PREFERENCE", "LIKE", ""),
                semantic("ACTIVITY_CURRENT", "What are you doing?", "NPC_SELF_STATE", "CURRENT_TASK", ""),
                semantic("ACTIVITY_HISTORICAL", "What were you doing?", "EPISODIC_RECALL", "PAST_ACTIVITY", ""),
                semantic("CORRECTION", "No, my name is Graham.", "CORRECTION", "NAME", ""),
                semantic("PROPERTY_GENERIC", "Is my lantern hot?", "OBJECTIVE_PROPERTY", "PROPERTY:HOT", ""),
                semantic("SOCIAL", "How are you?", "GENERAL_SOCIAL", "WELL_BEING", ""),
                semantic("MALFORMED", "Tell me what's in my", "UNRESOLVED", "", ""));
    }

    private static SemanticFixture semantic(String id, String utterance, String queryKind,
            String predicate, String action) {
        return new SemanticFixture(1, id, utterance, queryKind, predicate, action);
    }

    private static Fixture fixture(String id, String utterance, List<String> evidence,
            DialogueAct dialogueAct, Answerability answerability, String prohibited,
            EvidenceSourceKind source, String trace) {
        return new Fixture(1, id, utterance, evidence, dialogueAct, answerability,
                Set.of(prohibited), source, trace);
    }

    public record Fixture(int schemaVersion, String id, String playerUtterance,
            List<String> authoritativeEvidence, DialogueAct expectedDialogueAct,
            Answerability expectedFutureAnswerability, Set<String> prohibitedHallucinationClasses,
            EvidenceSourceKind expectedFutureEvidenceSource, String sourceTraceRevision) {
        public Fixture {
            if (schemaVersion < 1 || id == null || id.isBlank() || expectedDialogueAct == null
                    || expectedFutureAnswerability == null || expectedFutureEvidenceSource == null) {
                throw new IllegalArgumentException("complete versioned corpus fixture required");
            }
            authoritativeEvidence = List.copyOf(authoritativeEvidence == null
                    ? List.of() : authoritativeEvidence);
            prohibitedHallucinationClasses = Set.copyOf(prohibitedHallucinationClasses == null
                    ? Set.of() : prohibitedHallucinationClasses);
        }
    }

    public record SemanticFixture(int schemaVersion, String id, String playerUtterance,
            String expectedQueryKind, String expectedPredicate, String expectedAction) {
        public SemanticFixture {
            if (schemaVersion < 1 || id == null || id.isBlank()
                    || expectedQueryKind == null || expectedQueryKind.isBlank()) {
                throw new IllegalArgumentException("complete E1 semantic fixture required");
            }
        }
    }
}
