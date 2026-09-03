package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.action.NpcActionRegistry;
import com.inigmasgames.persistentnpcs.cognition.NpcCognitionService;
import com.inigmasgames.persistentnpcs.cognition.NpcSocialPerformance;
import com.inigmasgames.persistentnpcs.conversation.ConversationContextBuilder;
import com.inigmasgames.persistentnpcs.conversation.ConversationGroundingService;
import com.inigmasgames.persistentnpcs.conversation.ConversationService;
import com.inigmasgames.persistentnpcs.llm.ConversationRateLimiter;
import com.inigmasgames.persistentnpcs.llm.LlmProvider;
import com.inigmasgames.persistentnpcs.llm.PinnedLlmProvider;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionGateway;
import com.inigmasgames.persistentnpcs.perception.KnownNpcLocatorService;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.voice.SpeechToTextProvider;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Single composition seam for the authoritative Orbis turn graph.
 *
 * <p>Production and evaluation hosts replace boundary adapters only. Both execute the same
 * {@link OrbisTurnCoordinator}; this factory deliberately contains no routing, cognition,
 * validation, persistence, or provider behavior of its own.</p>
 */
public final class OrbisRuntimeFactory {
    private OrbisRuntimeFactory() { }

    public static OrbisTurnCoordinator create(Composition value) {
        if (value == null) throw new IllegalArgumentException("composition required");
        if (value.speechCoordinator() == null) {
            return new OrbisTurnCoordinator(value.stt(), value.audience(), value.cognition(),
                    value.eligiblePlayer(), value.pinnedProvider(), value.diagnostics(),
                    value.packetRunReleaseMillis(), value.maximumFrames(),
                    value.providerTimeoutMillis(), value.log());
        }
        return new OrbisTurnCoordinator(value.stt(), value.audience(), value.cognition(),
                value.speechCoordinator(), value.eligiblePlayer(), value.pinnedProvider(),
                value.resources(), value.diagnostics(), value.packetRunReleaseMillis(),
                value.maximumFrames(), value.providerTimeoutMillis(), value.log());
    }

    /**
     * Shared conversation-service composition used by both the plugin and headless evaluation.
     * Boundary adapters may differ, but constructor defaults and authoritative service wiring may
     * not silently drift between hosts.
     */
    public static ConversationService createConversation(ConversationComposition value) {
        if (value == null) throw new IllegalArgumentException(
                "conversation composition required");
        return new ConversationService(value.context(), value.provider(),
                value.relationships(), value.memories(), value.actions(), value.perception(),
                value.maximumDialogueCharacters(), value.log(), value.rateLimiter(),
                value.grounding(), value.cognition(), value.socialPerformance(),
                value.knownNpcLocator());
    }

    public record Composition(
            SpeechToTextProvider stt,
            OrbisAudienceGateway audience,
            OrbisCognitionGateway cognition,
            OrbisSpeechCoordinator speechCoordinator,
            Predicate<UUID> eligiblePlayer,
            Supplier<PinnedLlmProvider> pinnedProvider,
            OrbisResourceScheduler resources,
            OrbisDiagnostics diagnostics,
            long packetRunReleaseMillis,
            int maximumFrames,
            long providerTimeoutMillis,
            Consumer<String> log) {
        public Composition {
            if (audience == null || cognition == null || pinnedProvider == null) {
                throw new IllegalArgumentException(
                        "audience, cognition, and pinned provider are required");
            }
            eligiblePlayer = eligiblePlayer == null ? ignored -> true : eligiblePlayer;
            diagnostics = diagnostics == null ? new OrbisDiagnostics() : diagnostics;
            packetRunReleaseMillis = Math.max(80L, packetRunReleaseMillis);
            maximumFrames = Math.max(1, maximumFrames);
            providerTimeoutMillis = Math.max(50L, providerTimeoutMillis);
            log = log == null ? ignored -> { } : log;
        }
    }

    public record ConversationComposition(
            ConversationContextBuilder context,
            LlmProvider provider,
            RelationshipStore relationships,
            MemoryStore memories,
            NpcActionRegistry actions,
            NpcPerceptionGateway perception,
            int maximumDialogueCharacters,
            Consumer<String> log,
            ConversationRateLimiter rateLimiter,
            ConversationGroundingService grounding,
            NpcCognitionService cognition,
            NpcSocialPerformance socialPerformance,
            KnownNpcLocatorService knownNpcLocator) {
        public ConversationComposition {
            if (context == null || provider == null || relationships == null
                    || memories == null || actions == null || rateLimiter == null
                    || grounding == null || cognition == null) {
                throw new IllegalArgumentException(
                        "authoritative conversation services are required");
            }
            maximumDialogueCharacters = Math.max(80, maximumDialogueCharacters);
            log = log == null ? ignored -> { } : log;
            socialPerformance = socialPerformance == null
                    ? NpcSocialPerformance.unavailable() : socialPerformance;
        }
    }
}
