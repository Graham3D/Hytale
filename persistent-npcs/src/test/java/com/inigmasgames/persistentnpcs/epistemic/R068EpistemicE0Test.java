package com.inigmasgames.persistentnpcs.epistemic;

import com.inigmasgames.persistentnpcs.conversation.CognitiveContextPlan;
import java.nio.file.Files;
import java.nio.file.Path;

public final class R068EpistemicE0Test {
    private R068EpistemicE0Test() { }

    public static void main(String[] args) throws Exception {
        contractsAreVersionedAndEcsIndependent();
        modesAreObservationalAndAuthoritativeIsGated();
        permanentCorpusReplaysThroughShadowClassifier();
        diagnosticsUseExistingTraceSink();
        System.out.println("R068 E0 epistemic contracts/corpus/shadow tests passed.");
    }

    private static void contractsAreVersionedAndEcsIndependent() {
        System.setProperty(EpistemicFeatureMode.PROPERTY, "SHADOW");
        EpistemicContract contract = EpistemicShadowAnalyzer.analyze(
                "What's my name?", new CognitiveContextPlan(null, "SIMPLE_SOCIAL_RESPONSE",
                        java.util.Set.of("PROFILE", "RECENT_CONVERSATION"),
                        java.util.Set.of("MEMORIES"), java.util.List.of()), null);
        assert contract != null;
        assert contract.schemaVersion() == 1;
        assert contract.dialogueFrame().schemaVersion() == 1;
        assert contract.queryPlan().schemaVersion() == 1;
        assert contract.diagnoses().contains("IDENTITY_RECALL_REQUIRED");
        assert contract.diagnoses().contains("EVIDENCE_NOT_REQUESTED");
        assert !contract.budget().allowAdditionalInference();
        assert contract.answerPlan().authorizedPropositions().isEmpty();
        assert contract.getClass().getRecordComponents().length == 12;
    }

    private static void modesAreObservationalAndAuthoritativeIsGated() {
        System.setProperty(EpistemicFeatureMode.PROPERTY, "OFF");
        assert EpistemicShadowAnalyzer.analyze("Hello", CognitiveContextPlan.full("x"), null)
                == null;
        System.setProperty(EpistemicFeatureMode.PROPERTY, "AUTHORITATIVE");
        EpistemicContract gated = EpistemicShadowAnalyzer.analyze(
                "Hello", CognitiveContextPlan.full("x"), null);
        assert gated.mode() == EpistemicFeatureMode.AUTHORITATIVE;
        assert gated.diagnoses().contains("E3_AUTHORITATIVE_FOREGROUND");
        System.setProperty(EpistemicFeatureMode.PROPERTY, "SHADOW");
    }

    private static void permanentCorpusReplaysThroughShadowClassifier() {
        assert EpistemicConversationCorpus.fixtures().size() == 9;
        for (EpistemicConversationCorpus.Fixture fixture
                : EpistemicConversationCorpus.fixtures()) {
            EpistemicContract contract = EpistemicShadowAnalyzer.analyze(
                    fixture.playerUtterance(), CognitiveContextPlan.full("CURRENT_PIPELINE"),
                    null);
            assert contract.dialogueFrame().act() == fixture.expectedDialogueAct()
                    : fixture.id() + " classified " + contract.dialogueFrame().act();
            assert !fixture.prohibitedHallucinationClasses().isEmpty();
            assert !fixture.sourceTraceRevision().isBlank();
        }
    }

    private static void diagnosticsUseExistingTraceSink() throws Exception {
        String audit = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/diagnostics/NpcTurnAuditLog.java"));
        for (String event : new String[] { "DIALOGUE_FRAME_BUILT",
                "EPISTEMIC_QUERY_PLANNED", "EVIDENCE_RETRIEVED",
                "ANSWERABILITY_CLASSIFIED", "ANSWER_PLAN_COMPILED" }) {
            assert audit.contains(event) : event;
        }
        String service = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/conversation/ConversationService.java"));
        assert service.contains("EpistemicShadowAnalyzer.analyze");
        assert service.indexOf("EpistemicShadowAnalyzer.analyze")
                < service.indexOf(".withTurnExecutionPlan(executionPlan)");
    }
}
