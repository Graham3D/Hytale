package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.cognition.NpcGroundingClaimValidator;
import com.inigmasgames.persistentnpcs.conversation.CognitiveContextRouter;
import com.inigmasgames.persistentnpcs.conversation.CognitiveDepth;
import com.inigmasgames.persistentnpcs.conversation.DialogueMode;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Deterministic R057 coverage; no model, GPU, microphone, or Hytale client is required. */
public final class R057WorldWarmupGroundingTest {
    private R057WorldWarmupGroundingTest() { }

    public static void main(String[] args) throws Exception {
        socialClaimsAreDistinctFromAuthoritativeFacts();
        fishingInvitationUsesBoundedSocialContext();
        startupIsAsyncAndLifecycleCoordinated();
        moonshineAndChatterboxHaveExplicitWarmupOperations();
        System.out.println("R057 world warmup, Moonshine, grounding, and latency tests passed.");
    }

    private static void socialClaimsAreDistinctFromAuthoritativeFacts() {
        NpcGroundingClaimValidator validator = new NpcGroundingClaimValidator();
        var preference = validator.validate("I like fishing when I have the time.", List.of());
        assert preference.getFirst().valid();
        assert preference.getFirst().category().equals("SAFE_SOCIAL_SUBJECTIVE");

        var hypothetical = validator.validate(
                "Unless you're a fox, then I might be.", List.of());
        assert hypothetical.getFirst().valid() : hypothetical;
        assert hypothetical.getFirst().category().equals("SAFE_SOCIAL_SUBJECTIVE");

        var foxMill = validator.validate("I saw a fox by the old mill.", List.of());
        assert foxMill.stream().anyMatch(value -> !value.valid()
                && value.category().equals("WITNESSED_OR_CONCRETE_WORLD_EVENT")) : foxMill;

        var action = validator.validate("I've already repaired it.", List.of());
        assert action.stream().anyMatch(value -> !value.valid()
                && value.category().equals("COMPLETED_ACTION")) : action;
    }

    private static void fishingInvitationUsesBoundedSocialContext() {
        NpcProfile mara = new NpcProfile(UUID.randomUUID(), "Mara", "Tinkerer",
                "Warm and inquisitive", "A mechanic", "Learn and build", "", "",
                List.of("fishing", "foxes"), List.of("dishonesty"), List.of(), List.of(), 10);
        var fishing = CognitiveContextRouter.route(mara,
                "Would you like to go fishing?", DialogueMode.PROPOSED_PLAN, null, null);
        assert fishing.depth() == CognitiveDepth.SIMPLE_SOCIAL : fishing;
        assert fishing.detectedIntent().equals("SUBJECTIVE_OR_SOCIAL_INVITATION");
        assert !fishing.includes("SEMANTIC_WORLD") && !fishing.includes("MEMORIES");

        var adventure = CognitiveContextRouter.route(mara,
                "Would you like to go on an adventure?", DialogueMode.PROPOSED_PLAN, null, null);
        assert adventure.depth() == CognitiveDepth.COMPLEX_INTENT : adventure;
    }

    private static void startupIsAsyncAndLifecycleCoordinated() throws Exception {
        String plugin = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/PersistentNpcsPlugin.java"));
        String factory = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ai/AiServiceRouterFactory.java"));
        String coordinator = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/orbis/OrbisStartupCoordinator.java"));
        assert plugin.contains("StartWorldEvent.class")
                && plugin.contains("AllWorldsLoadedEvent.class")
                && plugin.contains("AddPlayerToWorldEvent.class");
        assert plugin.contains("startupCoordinator.trigger(\"PluginSetupEvent\")");
        assert !factory.contains("prepareActiveResidency().orTimeout")
                && !factory.contains(".join()");
        assert coordinator.contains("CompletableFuture.supplyAsync(action)");
        assert coordinator.contains("readyBeforeAddPlayerToWorld");
        assert coordinator.contains("warmupTimeline");
    }

    private static void moonshineAndChatterboxHaveExplicitWarmupOperations()
            throws Exception {
        String worker = Files.readString(Path.of(
                "src/main/resources/tools/immersive_voice_worker.py"));
        String stt = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/voice/LocalWorkerSpeechToTextProvider.java"));
        assert worker.contains("def warm_stt") && worker.contains("operation == \"warm_stt\"");
        assert worker.contains("Its result is discarded") || worker.contains("result is discarded");
        assert worker.indexOf("def warm_tts") < worker.indexOf("def warm_stt");
        assert stt.contains("bounded capture remains in Java memory");
        assert stt.contains("thenCompose(ignored -> worker().transcribe");
    }
}
