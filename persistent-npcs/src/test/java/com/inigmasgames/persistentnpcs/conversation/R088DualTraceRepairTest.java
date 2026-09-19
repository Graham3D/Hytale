package com.inigmasgames.persistentnpcs.conversation;

import com.inigmasgames.persistentnpcs.epistemic.*;
import com.inigmasgames.persistentnpcs.cognition.NpcCognitionService;
import java.nio.file.Files;
import java.nio.file.Path;

/** Exact Mara/Lycander connected-trace regressions for R088. */
public final class R088DualTraceRepairTest {
    public static void main(String[] args) throws Exception {
        route("Mara, actually, my name is Graham, not Grant.", "Mara",
                EpistemicQueryKind.CORRECTION, "NAME");
        route("Mara, what was my first name I told you>", "Mara",
                EpistemicQueryKind.IDENTITY_RECALL, "NAME");
        route("Mara, what do I hide?", "Mara",
                EpistemicQueryKind.EPISODIC_RECALL, "PAST_EVENT");
        route("Lycander, what item did I drop?", "Lycander",
                EpistemicQueryKind.EPISODIC_RECALL, "PAST_EVENT");
        route("Mara, what is that item on the ground spinning?", "Mara",
                EpistemicQueryKind.CURRENT_PERCEPTION, "VISIBLE");
        route("Mara, are they pointy?", "Mara",
                EpistemicQueryKind.OBJECTIVE_PROPERTY, "PROPERTY:POINTY");

        EpistemicContract delivery = contract(
                "Lycander, please tell Mara that the bridge is blocked.", "Lycander");
        assert delivery.dialogueFrame().act() == DialogueAct.ACTION_REQUEST : delivery;
        assert delivery.answerPlan().requestedAction().equals("DELIVER_MESSAGE") : delivery;
        assert delivery.dialogueFrame().targetKey().equals("NPC_NAME:MARA") : delivery;
        var requestedAction = NpcCognitionService.class.getDeclaredMethod(
                "requestedAction", String.class);
        requestedAction.setAccessible(true);
        assert requestedAction.invoke(null,
                "Lycander, please tell Mara that the bridge is blocked.")
                .equals("DELIVER_MESSAGE");

        EpistemicContract testimony = contract(
                "Mara, what did Lycander tell you just now?", "Mara");
        assert testimony.queryPlan().queryKind().equals(
                EpistemicQueryKind.RELATIONSHIP_FACT.name()) : testimony;
        assert testimony.dialogueFrame().predicateKey().equals("BELIEVES_ACTOR_KNOWS")
                : testimony;
        assert testimony.dialogueFrame().subjectKey().equals("NPC_NAME:LYCANDER") : testimony;

        String firewall = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/epistemic/EpistemicClaimFirewall.java"));
        assert firewall.contains("results = fallbackResults")
                : "safe repaired verdict still carries rejected draft claims";
        assert firewall.contains("KNOWN, PARTIALLY_KNOWN, INFERRED")
                : "partial recall cannot use deterministic evidence fallback";
        String retriever = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/epistemic/EpistemicEvidenceRetriever.java"));
        assert retriever.contains("looksInterrogative(value.statement())")
                : "legacy question-as-belief contamination remains authoritative";
        System.out.println("R088 dual-trace repair tests passed.");
    }

    private static void route(String utterance, String npc, EpistemicQueryKind kind,
            String predicate) {
        EpistemicContract value = contract(utterance, npc);
        assert value.mode() == EpistemicFeatureMode.AUTHORITATIVE : value.mode();
        assert value.queryPlan().queryKind().equals(kind.name()) : value;
        assert value.dialogueFrame().predicateKey().equals(predicate) : value;
    }

    private static EpistemicContract contract(String utterance, String npc) {
        String normalized = ConversationService.stripLeadingNpcVocative(utterance, npc);
        return EpistemicShadowAnalyzer.analyzeInitial(normalized, new ConversationWorkspace());
    }
}
