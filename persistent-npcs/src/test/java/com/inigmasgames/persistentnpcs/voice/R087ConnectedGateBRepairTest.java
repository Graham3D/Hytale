package com.inigmasgames.persistentnpcs.voice;

import com.inigmasgames.persistentnpcs.conversation.ConversationSessionManager;
import com.inigmasgames.persistentnpcs.cognition.PlayerFactMemoryService;
import com.inigmasgames.persistentnpcs.cognition.PlayerInputKind;
import com.inigmasgames.persistentnpcs.epistemic.*;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Exact connected-trace fixtures for the bounded R087 Gate-B repair. */
public final class R087ConnectedGateBRepairTest {
    public static void main(String[] args) throws Exception {
        var root = Files.createTempDirectory("r087-gate-b-");
        var registry = new NpcProfileRegistry(new ProfileRepository(root));
        NpcProfile mara = profile("Mara"), lycander = profile("Lycander");
        registry.register(mara); registry.register(lycander);
        var sessions = new ConversationSessionManager(Duration.ofMinutes(5));
        var correction = new SttSemanticCorrector(registry, sessions);

        check(correction.correct(UUID.randomUUID(), "Nara, I had a rock by the stream today."),
                "Mara, I had a rock by the stream today.", true);
        check(correction.correct(UUID.randomUUID(), "Laura, what do you know?"),
                "Mara, what do you know?", true);
        check(correction.correct(UUID.randomUUID(), "Lie candor. The old bridge is blocked."),
                "Lycander. The old bridge is blocked.", true);
        var untouched = correction.correct(UUID.randomUUID(), "I had a rock by the stream.");
        assert !untouched.applied() && untouched.rawTranscript().equals(untouched.correctedTranscript())
                : "ordinary ambiguous words were globally rewritten";
        UUID player = UUID.randomUUID();
        sessions.focus(mara.id(), player, Instant.now()).appendTurn(
                "I found a silver key.", "That is an unusual key.", Instant.now());
        check(correction.correct(player, "I found the silver tea."),
                "I found the silver key.", true);
        check(correction.correct(player, "Mara. I hid a rock by the stream today."),
                "Mara. I hid a rock by the stream today.", false);
        check(correction.correct(player, "I use a regular one."),
                "I use a regular one.", false);
        check(correction.correct(player, "Mara, what is that item on the ground spinning?"),
                "Mara, what is that item on the ground spinning?", false);
        assert PlayerFactMemoryService.classify(
                "Mara, “What did I tell you I hid today, and where?”")
                == PlayerInputKind.QUESTION;
        assert PlayerFactMemoryService.classify("Mara, what was my first name I told you>")
                == PlayerInputKind.QUESTION;

        var audience = new PlayerUtteranceAudienceService(registry, null, sessions,
                null, null, new VoiceRuntimeConfig(false, "", "auto", "base.en", "cpu",
                        "int8", "AUTO", "TINY_STREAMING", 250, 24_000, true, false,
                        true, 8.0, 15.0, 30.0), ignored -> { });
        Set<UUID> direct = audience.resolveDirectTargets(
                "Lycander, I want you to tell Mara what I said about the bridge.",
                PlayerSpeechIntent.CONVERSATION);
        assert direct.equals(Set.of(lycander.id())) : direct;
        List<EligibleNpcListener> listeners = List.of(
                listener(mara, true), listener(lycander, true));
        List<EligibleNpcListener> owners = PlayerUtteranceAudienceService
                .selectResponseOwners(listeners, direct);
        assert owners.size() == 1 && owners.getFirst().npcId().equals(lycander.id());
        assert PlayerUtteranceAudienceService.selectResponseOwners(
                List.of(listener(mara, false)), direct).isEmpty()
                : "unavailable direct addressee transferred ownership";

        authoritative("What did I tell you that I hid today and can wear?",
                EpistemicQueryKind.EPISODIC_RECALL);
        authoritative("Actually, what name did I tell you before I corrected it?",
                EpistemicQueryKind.IDENTITY_RECALL);
        authoritative("Mara, what do you know about the old bridge?",
                EpistemicQueryKind.RELATIONSHIP_FACT);
        EpistemicContract correctionContract = EpistemicShadowAnalyzer.analyzeInitial(
                "Actually, my name is Graham, not Grant.", new ConversationWorkspace());
        assert correctionContract.mode() == EpistemicFeatureMode.AUTHORITATIVE;
        assert correctionContract.queryPlan().queryKind().equals(
                EpistemicQueryKind.CORRECTION.name());
        assert correctionContract.dialogueFrame().objectKey().equals("PERSON_NAME:GRAHAM");

        String retriever = Files.readString(java.nio.file.Path.of("src/main/java/com/"
                + "inigmasgames/persistentnpcs/epistemic/EpistemicEvidenceRetriever.java"));
        assert retriever.contains("NO_ACTOR_LOCAL_TOPIC_TESTIMONY_OR_FACT");
        assert retriever.contains("predicateKey().equals(\"RELATIONSHIP\")");
        assert !retriever.contains("!base.dialogueFrame().predicateKey().equals(\"SECRET\")")
                : "generic relationship evidence can still substitute for topic testimony";
        String router = Files.readString(java.nio.file.Path.of("src/main/java/com/"
                + "inigmasgames/persistentnpcs/ai/AiServiceRouter.java"));
        assert router.contains("remediationAttempts.get() < 1");
        assert router.contains("activateStartupSteadyStateProfile");
        System.out.println("R087 connected Gate-B repair tests passed.");
    }

    private static void authoritative(String input, EpistemicQueryKind expected) {
        EpistemicContract contract = EpistemicShadowAnalyzer.analyzeInitial(input,
                new ConversationWorkspace());
        assert contract.mode() == EpistemicFeatureMode.AUTHORITATIVE : contract.mode();
        assert contract.queryPlan().queryKind().equals(expected.name())
                : contract.queryPlan().queryKind();
    }

    private static void check(SttSemanticCorrector.Correction actual,
            String expected, boolean applied) {
        assert actual.correctedTranscript().equals(expected) : actual;
        assert actual.applied() == applied;
        assert actual.rawTranscript() != null && !actual.reason().isBlank();
    }

    private static EligibleNpcListener listener(NpcProfile profile, boolean direct) {
        return new EligibleNpcListener(profile.id(), profile.name(), 2, "nearby", "north",
                UtteranceRangeClass.ORDINARY, direct, false, direct ? 1_000 : 10);
    }

    private static NpcProfile profile(String name) {
        return new NpcProfile(UUID.randomUUID(), name, "villager", "grounded",
                name + " lives in the village.", "Live responsibly.", "", "",
                List.of(), List.of(), List.of(), List.of(), 0).validated();
    }
}
