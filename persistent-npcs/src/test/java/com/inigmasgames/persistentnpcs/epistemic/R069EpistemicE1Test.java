package com.inigmasgames.persistentnpcs.epistemic;

import com.inigmasgames.persistentnpcs.conversation.CognitiveContextPlan;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class R069EpistemicE1Test {
    private R069EpistemicE1Test() { }

    public static void main(String[] args) {
        System.setProperty(EpistemicFeatureMode.PROPERTY, "SHADOW");
        e0CorpusUnderstandsEveryFailureClass();
        paraphraseFamiliesConverge();
        clarificationBindsOnlyDeliveredSpeech();
        ambiguousPronounsStayAmbiguous();
        workspaceIsBoundedAndTransient();
        queryPlansRequestButNeverRetrieveEvidence();
        planningIsCheap();
        System.out.println("R069 E1 dialogue-state/query-planning tests passed.");
    }

    private static void e0CorpusUnderstandsEveryFailureClass() {
        ConversationWorkspace workspace = new ConversationWorkspace();
        workspace.observeDelivered("I meant that the bridge looked unsafe.", Instant.now());
        for (EpistemicConversationCorpus.Fixture fixture
                : EpistemicConversationCorpus.fixtures()) {
            EpistemicContract result = analyze(fixture.playerUtterance(), workspace);
            assert result.dialogueFrame().act() == fixture.expectedDialogueAct()
                    : fixture.id() + " -> " + result.dialogueFrame().act();
            assert !result.queryPlan().queryKind().equals("UNRESOLVED")
                    || fixture.id().equals("I_MALFORMED_STT") : fixture.id();
        }
    }

    private static void paraphraseFamiliesConverge() {
        for (EpistemicConversationCorpus.SemanticFixture fixture
                : EpistemicConversationCorpus.e1Fixtures()) {
            EpistemicContract result = analyze(fixture.playerUtterance(),
                    new ConversationWorkspace());
            assert result.queryPlan().queryKind().equals(fixture.expectedQueryKind())
                    : fixture.id() + " -> " + result.queryPlan().queryKind();
            if (!fixture.expectedPredicate().isBlank()) {
                assert result.dialogueFrame().predicateKey().equals(fixture.expectedPredicate())
                        : fixture.id() + " predicate=" + result.dialogueFrame().predicateKey();
            }
            if (!fixture.expectedAction().isBlank()) {
                assert result.queryPlan().requestedAction().equals(fixture.expectedAction());
            }
        }
    }

    private static void clarificationBindsOnlyDeliveredSpeech() {
        ConversationWorkspace workspace = new ConversationWorkspace();
        EpistemicContract unresolved = analyze("What did you mean by that?", workspace);
        assert unresolved.dialogueFrame().ambiguous();
        assert unresolved.dialogueFrame().priorPropositionBinding().isBlank();
        workspace.observeDelivered("The west bridge may be unsafe.", Instant.now());
        EpistemicContract bound = analyze("Can you explain what you said?", workspace);
        assert !bound.dialogueFrame().ambiguous();
        assert bound.dialogueFrame().priorPropositionBinding()
                .equals("The west bridge may be unsafe.");
        assert bound.queryPlan().requireConversationWorkspace();
        assert bound.queryPlan().sourceProposition().equals("The west bridge may be unsafe.");
    }

    private static void ambiguousPronounsStayAmbiguous() {
        EpistemicContract result = analyze("Where did I say I hid it?",
                new ConversationWorkspace());
        assert result.queryPlan().queryKind().equals("EPISODIC_RECALL");
        assert result.dialogueFrame().ambiguous();
        assert result.dialogueFrame().ambiguityReason().equals("UNRESOLVED_OBJECT_PRONOUN");
        assert result.queryPlan().ambiguous();
    }

    private static void workspaceIsBoundedAndTransient() {
        ConversationWorkspace workspace = new ConversationWorkspace();
        for (int index = 0; index < 20; index++) {
            DialogueFrame frame = new DialogueFrame(1, DialogueAct.FACT_QUERY,
                    ExpectedAnswerKind.FACT, "ENTITY:" + index, "VISIBLE", "", "",
                    List.of(), false, "", "", "", false, .9, List.of("test"));
            workspace.observePlayer(frame, "entity " + index, Instant.now());
            workspace.addCommitment("commitment " + index, Instant.now());
        }
        ConversationWorkspace.Snapshot snapshot = workspace.snapshot(Instant.now());
        assert snapshot.activeEntities().size() == 8;
        assert snapshot.commitments().size() == 4;
        assert !ConversationWorkspace.class.getDeclaredFields()[0].getType()
                .getName().contains("Path");
    }

    private static void queryPlansRequestButNeverRetrieveEvidence() {
        EpistemicContract identity = analyze("What's my name?", new ConversationWorkspace());
        assert identity.queryPlan().requireMemory();
        assert identity.queryPlan().evidenceCategories().contains("PLAYER_FACT");
        assert identity.evidence().supporting().isEmpty();
        assert identity.answerability() == Answerability.UNIMPLEMENTED;
        EpistemicContract held = analyze("What's in my hand?", new ConversationWorkspace());
        assert held.queryPlan().requireCurrentPerception();
        assert !held.queryPlan().allowMemorySubstitution();
        assert held.evidence().sufficiency() == EvidenceSufficiency.UNIMPLEMENTED;
    }

    private static void planningIsCheap() {
        ArrayList<Long> samples = new ArrayList<>();
        for (int index = 0; index < 2_000; index++) {
            EpistemicContract result = analyze(index % 2 == 0 ? "What's my name?"
                    : "Can you follow me?", new ConversationWorkspace());
            samples.add(result.planningMicros());
        }
        samples.sort(Comparator.naturalOrder());
        long p95 = samples.get((int) (samples.size() * .95));
        long maximum = samples.getLast();
        assert p95 < 5_000 : "p95Micros=" + p95;
        assert maximum < 40_000 : "maxMicros=" + maximum;
        System.out.println("R069 E1 planning p95Micros=" + p95 + " maxMicros=" + maximum);
    }

    private static EpistemicContract analyze(String text, ConversationWorkspace workspace) {
        return EpistemicShadowAnalyzer.analyzeWithWorkspace(text,
                CognitiveContextPlan.full("TEST"), workspace);
    }
}
