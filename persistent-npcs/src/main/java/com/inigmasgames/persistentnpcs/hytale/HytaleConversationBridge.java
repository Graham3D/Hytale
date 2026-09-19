package com.inigmasgames.persistentnpcs.hytale;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.inigmasgames.persistentnpcs.conversation.ConversationOutcome;
import com.inigmasgames.persistentnpcs.conversation.ConversationInvocation;
import com.inigmasgames.persistentnpcs.conversation.ConversationLifecycleObserver;
import com.inigmasgames.persistentnpcs.conversation.ConversationService;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.ConversationSessionManager;
import com.inigmasgames.persistentnpcs.conversation.MinimalWorldContext;
import com.inigmasgames.persistentnpcs.llm.LlmProviderException;
import com.inigmasgames.persistentnpcs.llm.LlmProviderStatus;
import com.inigmasgames.persistentnpcs.llm.LlmSetupRequiredException;
import com.inigmasgames.persistentnpcs.llm.LlmTimeoutException;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.social.NpcSocialAttentionService;
import com.inigmasgames.persistentnpcs.voice.NpcVoiceService;
import com.inigmasgames.persistentnpcs.voice.HytaleSpatialVoiceAdapter;
import com.inigmasgames.persistentnpcs.voice.SpeechPhraseChunker;
import com.inigmasgames.persistentnpcs.voice.EligibleNpcListener;
import com.inigmasgames.persistentnpcs.voice.PlayerUtteranceAudienceService;
import com.inigmasgames.persistentnpcs.orbis.CancellationReason;
import com.inigmasgames.persistentnpcs.orbis.BranchCognitionSnapshot;
import com.inigmasgames.persistentnpcs.orbis.OrbisCognitionGateway;
import com.inigmasgames.persistentnpcs.orbis.OrbisRuntime;
import com.inigmasgames.persistentnpcs.orbis.TurnIngressSource;
import com.inigmasgames.persistentnpcs.orbis.CanonicalSpeechChunk;
import com.inigmasgames.persistentnpcs.llm.PinnedLlmProvider;
import com.inigmasgames.persistentnpcs.voice.SpeechProjection;
import com.inigmasgames.persistentnpcs.voice.TranscribedPlayerUtterance;
import com.inigmasgames.persistentnpcs.voice.VoiceInteractionTraceStore;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;
import org.joml.Vector3d;

public final class HytaleConversationBridge implements OrbisCognitionGateway {
    private static final String CURIOSITY_CUE = "NPC_INITIATED_CURIOSITY: "
            + "The player has entered conversational proximity. Ask at most one concise, "
            + "grounded question only if current authoritative context provides a genuinely "
            + "interesting topic. Otherwise offer one brief natural greeting.";
    private final Supplier<NpcProfile> profile;
    private final NpcProfileRegistry profileRegistry;
    private final ConversationSessionManager sessions;
    private final ConversationService conversations;
    private final HytaleNpcAdapter npcAdapter;
    private final int maximumMessageCharacters;
    private final Consumer<Throwable> errorLog;
    private final NpcSocialAttentionService attention;
    private final NpcVoiceService voice;
    private final HytaleSpatialVoiceAdapter spatialVoice;
    private final PlayerUtteranceAudienceService utteranceAudience;
    private final VoiceInteractionTraceStore voiceTraces;
    private final double npcSpeechMaxRadius;
    private volatile OrbisRuntime orbisRuntime;
    private volatile BooleanSupplier conversationalReady = () -> true;
    private volatile Supplier<String> startupStatus = () -> "ORBIS READY";

    public HytaleConversationBridge(
            Supplier<NpcProfile> profile,
            ConversationSessionManager sessions,
            ConversationService conversations,
            HytaleNpcAdapter npcAdapter,
            int maximumMessageCharacters,
            Consumer<Throwable> errorLog) {
        this(profile, sessions, conversations, npcAdapter,
                maximumMessageCharacters, errorLog, null, null, null);
    }

    public HytaleConversationBridge(
            Supplier<NpcProfile> profile,
            ConversationSessionManager sessions,
            ConversationService conversations,
            HytaleNpcAdapter npcAdapter,
            int maximumMessageCharacters,
            Consumer<Throwable> errorLog,
            NpcSocialAttentionService attention) {
        this(profile, sessions, conversations, npcAdapter, maximumMessageCharacters,
                errorLog, attention, null, null);
    }

    public HytaleConversationBridge(
            Supplier<NpcProfile> profile,
            ConversationSessionManager sessions,
            ConversationService conversations,
            HytaleNpcAdapter npcAdapter,
            int maximumMessageCharacters,
            Consumer<Throwable> errorLog,
            NpcSocialAttentionService attention,
            NpcVoiceService voice) {
        this(profile, sessions, conversations, npcAdapter, maximumMessageCharacters,
                errorLog, attention, voice, null);
    }

    public HytaleConversationBridge(
            Supplier<NpcProfile> profile,
            ConversationSessionManager sessions,
            ConversationService conversations,
            HytaleNpcAdapter npcAdapter,
            int maximumMessageCharacters,
            Consumer<Throwable> errorLog,
            NpcSocialAttentionService attention,
            NpcVoiceService voice,
            HytaleSpatialVoiceAdapter spatialVoice) {
        this.profile = profile;
        this.profileRegistry = null;
        this.sessions = sessions;
        this.conversations = conversations;
        this.npcAdapter = npcAdapter;
        this.maximumMessageCharacters = maximumMessageCharacters;
        this.errorLog = errorLog;
        this.attention = attention;
        this.voice = voice;
        this.spatialVoice = spatialVoice;
        this.utteranceAudience = null;
        this.voiceTraces = null;
        this.npcSpeechMaxRadius = 15.0;
    }

    public HytaleConversationBridge(
            NpcProfileRegistry profiles,
            ConversationSessionManager sessions,
            ConversationService conversations,
            HytaleNpcAdapter npcAdapter,
            int maximumMessageCharacters,
            Consumer<Throwable> errorLog,
            NpcSocialAttentionService attention,
            NpcVoiceService voice,
            HytaleSpatialVoiceAdapter spatialVoice) {
        this.profileRegistry = profiles;
        this.profile = profiles::defaultProfile;
        this.sessions = sessions;
        this.conversations = conversations;
        this.npcAdapter = npcAdapter;
        this.maximumMessageCharacters = maximumMessageCharacters;
        this.errorLog = errorLog;
        this.attention = attention;
        this.voice = voice;
        this.spatialVoice = spatialVoice;
        this.utteranceAudience = null;
        this.voiceTraces = null;
        this.npcSpeechMaxRadius = 15.0;
    }

    public HytaleConversationBridge(
            NpcProfileRegistry profiles,
            ConversationSessionManager sessions,
            ConversationService conversations,
            HytaleNpcAdapter npcAdapter,
            int maximumMessageCharacters,
            Consumer<Throwable> errorLog,
            NpcSocialAttentionService attention,
            NpcVoiceService voice,
            HytaleSpatialVoiceAdapter spatialVoice,
            PlayerUtteranceAudienceService utteranceAudience,
            VoiceInteractionTraceStore voiceTraces,
            double npcSpeechMaxRadius) {
        this.profileRegistry = profiles;
        this.profile = profiles::defaultProfile;
        this.sessions = sessions;
        this.conversations = conversations;
        this.npcAdapter = npcAdapter;
        this.maximumMessageCharacters = maximumMessageCharacters;
        this.errorLog = errorLog;
        this.attention = attention;
        this.voice = voice;
        this.spatialVoice = spatialVoice;
        this.utteranceAudience = utteranceAudience;
        this.voiceTraces = voiceTraces;
        this.npcSpeechMaxRadius = Math.max(1.0, npcSpeechMaxRadius);
    }

    @SuppressWarnings("deprecation")
    public void handleInteract(PlayerInteractEvent event) {
        InteractionType action = event.getActionType();
        if (action != InteractionType.Use && action != InteractionType.Secondary) {
            return;
        }
        if (!npcAdapter.isTestNpc(event.getTargetEntity())) {
            return;
        }
        PlayerRef playerRef = event.getPlayerRef().getStore().getComponent(
                event.getPlayerRef(), PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        event.setCancelled(true);
        NpcProfile target = profile.get();
        if (profileRegistry != null && event.getTargetEntity() != null) {
            if (event.getTargetRef() != null && event.getTargetRef().isValid()) {
                UUIDComponent uuid = event.getTargetRef().getStore().getComponent(
                        event.getTargetRef(), UUIDComponent.getComponentType());
                if (uuid != null) {
                    target = npcAdapter.profileIdForEntity(uuid.getUuid())
                            .flatMap(profileRegistry::byId).orElse(target);
                }
                PersistentDisplayName display = event.getTargetRef().getStore().getComponent(
                        event.getTargetRef(), PersistentDisplayName.getComponentType());
                if (display != null && display.getDisplayName() != null) {
                    target = profileRegistry.byName(display.getDisplayName().getRawText())
                            .orElse(target);
                }
            }
        }
        focus(playerRef, target);
    }

    public void handleChat(PlayerChatEvent event) {
        PlayerRef playerRef = event.getSender();
        Instant now = Instant.now();
        Optional<ConversationSession> active;
        if (attention == null) {
            active = sessions.active(playerRef.getUuid(), now);
        } else {
            Optional<UUID> focusedNpc = attention.focusedNpcFor(
                    playerRef, event.getContent());
            active = focusedNpc.isPresent()
                    ? sessions.active(playerRef.getUuid(), focusedNpc.get(), now)
                    : sessions.active(playerRef.getUuid(), now);
        }
        if (active.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        submitOrbis(playerRef, active.get(), event.getContent(),
                TurnIngressSource.NATIVE_TEXT_CHAT);
    }

    public ConversationSession focus(PlayerRef playerRef) {
        return focus(playerRef, profile.get());
    }

    public ConversationSession focus(PlayerRef playerRef, NpcProfile current) {
        ConversationSession session = sessions.focus(current.id(), playerRef.getUuid(), Instant.now());
        tell(playerRef, "You are now speaking privately with " + current.name()
                + ". Type in normal chat; walk away to leave the conversation.");
        return session;
    }

    public void submitFocused(PlayerRef playerRef, String message) {
        ConversationSession session = sessions.active(playerRef.getUuid(), Instant.now())
                .orElseGet(() -> focus(playerRef));
        submitOrbis(playerRef, session, message, TurnIngressSource.MANUAL_SUBMISSION);
    }

    /** Starts a bounded NPC-led turn without pretending the internal cue was player speech. */
    public void initiateCuriosity(PlayerRef playerRef, ConversationSession session) {
        if (playerRef == null || session == null || session.requestInFlight()) return;
        submitOrbis(playerRef, session, CURIOSITY_CUE,
                TurnIngressSource.NPC_INITIATED_INTERNAL);
    }

    /** Orbis audience adapter: exactly one world-thread resolution per final transcript. */
    public CompletableFuture<PlayerUtteranceAudienceService.Resolution> resolveVoiceAudience(
            TranscribedPlayerUtterance utterance) {
        CompletableFuture<PlayerUtteranceAudienceService.Resolution> result =
                new CompletableFuture<>();
        if (utterance == null || utterance.transcript().isBlank()) {
            result.completeExceptionally(new IllegalArgumentException("final transcript required"));
            return result;
        }
        PlayerRef playerRef = Universe.get().getPlayer(utterance.playerId());
        if (playerRef == null || !playerRef.isValid()
                || !java.util.Objects.equals(playerRef.getWorldUuid(), utterance.worldId())) {
            result.completeExceptionally(new IllegalStateException("voice player unavailable"));
            return result;
        }
        World world = Universe.get().getWorld(utterance.worldId());
        if (world == null) {
            result.completeExceptionally(new IllegalStateException("captured world unavailable"));
            return result;
        }
        world.execute(() -> {
            if (!playerRef.isValid()) {
                result.completeExceptionally(new IllegalStateException("voice player disconnected"));
                return;
            }
            if (!conversationalReady.getAsBoolean()) {
                String message = notReadyMessage();
                tell(playerRef, message);
                result.completeExceptionally(new com.inigmasgames.persistentnpcs.conversation
                        .ConversationBusyException(message));
                return;
            }
            if (utteranceAudience == null) {
                result.completeExceptionally(new IllegalStateException(
                        "Orbis audience adapter unavailable"));
                return;
            }
            try {
                PlayerUtteranceAudienceService.Resolution resolution =
                        utteranceAudience.resolve(utterance, world);
                result.complete(resolution);
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    public CompletableFuture<java.util.Map<String, String>> prefetchVoiceContext(
            UUID playerId, UUID worldId) {
        long started = System.nanoTime();
        ConversationSession session = sessions.active(playerId, Instant.now()).orElse(null);
        if (session == null) return CompletableFuture.completedFuture(java.util.Map.of(
                "status", "NO_ACTIVE_FOCUS", "prefetchMillis", "0"));
        NpcProfile current = profileRegistry.byId(session.npcId()).orElse(null);
        if (current == null) return CompletableFuture.completedFuture(java.util.Map.of(
                "status", "PROFILE_UNAVAILABLE", "prefetchMillis", "0"));
        conversations.prefetchStaticContext(session, current);
        return CompletableFuture.completedFuture(java.util.Map.of(
                "status", "READY",
                "npcId", current.id().toString(),
                "sections", "PROFILE,PERSONALITY,PLAYER_RELATIONSHIP,RECENT_CONVERSATION",
                "partialTranscriptAuthority", "NONE",
                "prefetchMillis", Long.toString(java.util.concurrent.TimeUnit.NANOSECONDS
                        .toMillis(System.nanoTime() - started))));
    }

    /**
     * Phase 2 adapter. It captures the remaining Hytale/session references on the
     * world thread, then invokes the existing cognition service with Orbis IDs and
     * the exact pinned provider. It never commits dialogue itself.
     */
    @Override public CompletableFuture<ConversationOutcome> begin(
            BranchCognitionSnapshot snapshot, PinnedLlmProvider pinned,
            ConversationLifecycleObserver observer) {
        CompletableFuture<ConversationOutcome> result = new CompletableFuture<>();
        if (snapshot == null || pinned == null || snapshot.canonicalTranscript().isBlank()) {
            result.completeExceptionally(new IllegalArgumentException(
                    "Complete Orbis cognition snapshot/provider required"));
            return result;
        }
        if (!conversationalReady.getAsBoolean()) {
            result.completeExceptionally(new com.inigmasgames.persistentnpcs.conversation
                    .ConversationBusyException(notReadyMessage()));
            return result;
        }
        PlayerRef playerRef = Universe.get().getPlayer(snapshot.playerStableId());
        World world = Universe.get().getWorld(snapshot.worldId());
        if (playerRef == null || !playerRef.isValid() || world == null) {
            result.completeExceptionally(new IllegalStateException(
                    "Orbis branch player/world unavailable"));
            return result;
        }
        world.execute(() -> {
            if (!playerRef.isValid() || snapshot.cancellation().isCancelled()) {
                result.completeExceptionally(new java.util.concurrent.CancellationException(
                        "Orbis branch cancelled before cognition"));
                return;
            }
            NpcProfile current = profileRegistry == null ? profile.get()
                    : profileRegistry.byId(snapshot.npcStableId()).orElse(null);
            if (current == null || !current.id().equals(snapshot.npcStableId())) {
                result.completeExceptionally(new IllegalStateException(
                        "Authored NPC identity unavailable for Orbis branch"));
                return;
            }
            ConversationSession session = sessions.focus(
                    current.id(), playerRef.getUuid(), Instant.now());
            session.setDeferredConversationContext(snapshot.deferredConversationContext());
            session.setPlayerUtteranceContext(new ConversationSession.PlayerUtteranceContext(
                    snapshot.utteranceId(), snapshot.speechIntent(), snapshot.rangeClass(),
                    snapshot.directAddress(), snapshot.distanceBand(),
                    snapshot.directionFromPlayer(), snapshot.projection(),
                    snapshot.endpointMillis(), snapshot.sttMillis(),
                    snapshot.audienceResolutionMillis()));
            boolean npcInitiated = snapshot.canonicalTranscript()
                    .startsWith("NPC_INITIATED_CURIOSITY:");
            if (!npcInitiated) {
                tell(playerRef, formatPlayerEcho(playerDisplayName(playerRef),
                        snapshot.canonicalTranscript()));
                tell(playerRef, current.name() + " is thinking...");
            }
            ConversationLifecycleObserver targetObserver = observer == null
                    ? ConversationLifecycleObserver.none() : observer;
            ConversationLifecycleObserver bridgeObserver = (stage, facts) -> {
                targetObserver.onStage(stage, facts);
            };
            ConversationInvocation invocation = new ConversationInvocation(
                    snapshot.responseId().value(), snapshot.providerRequestId().value(),
                    pinned.delegate(), () -> !snapshot.cancellation().isCancelled(),
                    bridgeObserver, snapshot.branchEpoch());
            try {
                conversations.converseForOrbis(session, current,
                        snapshot.canonicalTranscript(), worldContext(playerRef), invocation,
                        (delta, state) -> { }).whenComplete((outcome, failure) -> {
                            session.clearDeferredConversationContext();
                            if (failure == null) result.complete(outcome);
                            else result.completeExceptionally(failure);
                        });
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    /** Phase 2 compatibility overload; Phase 3 production always supplies chunks. */
    @Override public CompletableFuture<Void> commit(
            BranchCognitionSnapshot snapshot, ConversationOutcome outcome) {
        java.util.ArrayList<CanonicalSpeechChunk> chunks = new java.util.ArrayList<>();
        SpeechPhraseChunker exact = SpeechPhraseChunker.exact((index, phrase, state) ->
                chunks.add(new CanonicalSpeechChunk(
                        com.inigmasgames.persistentnpcs.orbis.SpeechChunkId.create(),
                        index, phrase, state)));
        exact.complete(outcome == null ? "" : outcome.dialogue(),
                outcome == null ? null : outcome.vocalState());
        return commit(snapshot, outcome, List.copyOf(chunks));
    }

    /** Displays the exact immutable chunks owned by the Orbis branch; no TTS is invoked here. */
    @Override public CompletableFuture<Void> commit(
            BranchCognitionSnapshot snapshot, ConversationOutcome outcome,
            java.util.List<CanonicalSpeechChunk> chunks) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        PlayerRef playerRef = snapshot == null ? null
                : Universe.get().getPlayer(snapshot.playerStableId());
        World world = snapshot == null ? null : Universe.get().getWorld(snapshot.worldId());
        if (snapshot == null || outcome == null || playerRef == null
                || !playerRef.isValid() || world == null) {
            result.completeExceptionally(new IllegalStateException(
                    "Canonical response recipient/world unavailable"));
            return result;
        }
        world.execute(() -> {
            try {
                if (snapshot.cancellation().isCancelled()) {
                    throw new java.util.concurrent.CancellationException(
                            "Cancelled branch cannot reach display/TTS");
                }
                NpcProfile current = profileRegistry == null ? profile.get()
                        : profileRegistry.byId(snapshot.npcStableId()).orElse(null);
                if (current == null) throw new IllegalStateException(
                        "Committed NPC profile unavailable");
                StringBuilder committed = new StringBuilder();
                for (CanonicalSpeechChunk chunk : chunks == null
                        ? java.util.List.<CanonicalSpeechChunk>of() : chunks) {
                    if (snapshot.cancellation().isCancelled()) {
                        throw new java.util.concurrent.CancellationException(
                                "Cancelled branch cannot display a canonical chunk");
                    }
                    String exact = com.inigmasgames.persistentnpcs.conversation
                            .SpokenTextSafetyValidator.requireSafe(chunk.text());
                    if (chunk.index() == 0) conversations.markFirstCanonicalSpeechChunk(
                            snapshot.responseId().value());
                    tell(playerRef, current.name() + ": " + exact);
                    if (!committed.isEmpty()) committed.append(' ');
                    committed.append(exact);
                }
                if (!committed.toString().equals(outcome.dialogue())) {
                    throw new IllegalStateException(
                            "Orbis canonical display chunks differ from NpcDecision text");
                }
                // Displayed text is generated/canonical, but player-known conversation
                // history is committed only from native playback completion callbacks.
                sessions.active(playerRef.getUuid(), Instant.now()).ifPresent(session ->
                        session.clearPlayerUtteranceContext(snapshot.utteranceId()));
                result.complete(null);
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    @Override public CompletableFuture<Void> commitPhrase(
            BranchCognitionSnapshot snapshot, CanonicalSpeechChunk chunk) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        PlayerRef playerRef = snapshot == null ? null
                : Universe.get().getPlayer(snapshot.playerStableId());
        World world = snapshot == null ? null : Universe.get().getWorld(snapshot.worldId());
        if (snapshot == null || chunk == null || playerRef == null || world == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Early canonical phrase recipient/world unavailable"));
        }
        world.execute(() -> {
            try {
                if (snapshot.cancellation().isCancelled()) throw new
                        java.util.concurrent.CancellationException("stale early phrase");
                NpcProfile current = profileRegistry.byId(snapshot.npcStableId()).orElse(null);
                if (current == null) throw new IllegalStateException("NPC profile unavailable");
                String exact = com.inigmasgames.persistentnpcs.conversation
                        .SpokenTextSafetyValidator.requireSafe(chunk.text());
                if (chunk.index() == 0) conversations.markFirstCanonicalSpeechChunk(
                        snapshot.responseId().value());
                tell(playerRef, current.name() + ": " + exact);
                result.complete(null);
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    @Override public CompletableFuture<Void> finalizeCommit(
            BranchCognitionSnapshot snapshot, ConversationOutcome outcome,
            java.util.List<CanonicalSpeechChunk> chunks, int alreadyCommittedCount) {
        if (alreadyCommittedCount <= 0) return commit(snapshot, outcome, chunks);
        String all = chunks.stream().map(CanonicalSpeechChunk::text)
                .collect(java.util.stream.Collectors.joining(" "));
        if (!all.equals(outcome.dialogue())) return CompletableFuture.failedFuture(
                new IllegalStateException("Canonical chunks differ from final decision"));
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (int index = alreadyCommittedCount; index < chunks.size(); index++) {
            CanonicalSpeechChunk chunk = chunks.get(index);
            chain = chain.thenCompose(ignored -> commitPhrase(snapshot, chunk));
        }
        return chain;
    }

    @Override public CompletableFuture<Void> deliveryCompleted(
            BranchCognitionSnapshot snapshot, ConversationOutcome outcome,
            com.inigmasgames.persistentnpcs.orbis.SpeechDeliveryReport delivery) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        PlayerRef playerRef = snapshot == null ? null
                : Universe.get().getPlayer(snapshot.playerStableId());
        World world = snapshot == null ? null : Universe.get().getWorld(snapshot.worldId());
        if (snapshot == null || outcome == null || delivery == null
                || playerRef == null || world == null) {
            result.completeExceptionally(new IllegalStateException(
                    "Delivery provenance recipient/world unavailable"));
            return result;
        }
        world.execute(() -> {
            try {
                NpcProfile current = profileRegistry == null ? profile.get()
                        : profileRegistry.byId(snapshot.npcStableId()).orElse(null);
                ConversationSession session = sessions.active(snapshot.playerStableId(),
                        snapshot.npcStableId(), Instant.now()).orElse(null);
                if (current != null && session != null) {
                    conversations.recordDeliveredConversation(session, current,
                            snapshot.canonicalTranscript(), snapshot.responseId().value(),
                            delivery.deliveredText(), outcome.dialogueMode(),
                            delivery.interrupted());
                    conversations.recordCommittedSpokenText(current.id(),
                            snapshot.responseId().value(), delivery.deliveredText());
                }
                result.complete(null);
            } catch (RuntimeException failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    @Override public void failed(BranchCognitionSnapshot snapshot,
            CancellationReason reason, Throwable failure) {
        if (snapshot == null) return;
        // Cancellation must release ConversationService ownership and abort its actual
        // provider transport before the next utterance is admitted. This is deliberately
        // outside world.execute: it is thread-safe and cannot block a Hytale thread.
        conversations.cancelForOrbis(snapshot.responseId().value(),
                snapshot.providerRequestId().value());
        PlayerRef playerRef = Universe.get().getPlayer(snapshot.playerStableId());
        World world = Universe.get().getWorld(snapshot.worldId());
        if (playerRef == null || world == null) return;
        world.execute(() -> {
            sessions.active(playerRef.getUuid(), Instant.now()).ifPresent(session ->
                    session.clearPlayerUtteranceContext(snapshot.utteranceId()));
            if (playerRef.isValid() && reason != CancellationReason.SUPERSEDED
                    && reason != CancellationReason.USER_BARGE_IN
                    && reason != CancellationReason.PLAYER_DISCONNECT
                    && reason != CancellationReason.NPC_DESPAWN) {
                tell(playerRef, gracefulFailure(snapshot.npcName(),
                        failure == null ? new IllegalStateException(reason.name()) : failure));
            }
        });
    }

    public void setOrbisRuntime(OrbisRuntime runtime) {
        this.orbisRuntime = runtime;
    }

    /** Read-only startup gate; callbacks never perform provider work on Hytale threads. */
    public void setConversationalReadiness(BooleanSupplier ready, Supplier<String> status) {
        conversationalReady = ready == null ? () -> true : ready;
        startupStatus = status == null ? () -> "ORBIS INITIALIZING" : status;
    }

    public void end(PlayerRef playerRef) {
        sessions.active(playerRef.getUuid(), Instant.now())
                .ifPresent(session -> conversations.endSession(session.sessionId()));
        sessions.end(playerRef.getUuid());
        tell(playerRef, "Conversation ended.");
    }

    public void disconnected(UUID playerId) {
        sessions.active(playerId, Instant.now())
                .ifPresent(session -> conversations.endSession(session.sessionId()));
        sessions.end(playerId);
        if (attention != null) {
            attention.disconnected(playerId);
        }
    }

    public boolean hasActiveSession(UUID playerId) {
        return sessions.active(playerId, Instant.now()).isPresent();
    }

    public void entityRemoved(UUID npcId) {
        sessions.endNpc(npcId);
        OrbisRuntime orbis = orbisRuntime;
        if (orbis != null) orbis.npcUnloaded(npcId);
        if (attention != null) {
            attention.entityRemoved(npcId);
        }
        if (spatialVoice != null) {
            spatialVoice.entityRemoved(npcId);
        }
    }

    public void entityRefreshed(UUID npcId, UUID retainedEntityId) {
        if (attention != null) {
            attention.entityRefreshed(npcId, retainedEntityId);
        }
    }

    public void reportStatus(PlayerRef playerRef) {
        UUID playerId = playerRef.getUuid();
        tell(playerRef, "Checking the local AI backend...");
        conversations.checkProviderStatus().whenComplete((providerStatus, failure) -> {
            if (failure != null) {
                Throwable cause = unwrap(failure);
                errorLog.accept(cause);
                tell(playerRef, "Local AI status check failed unexpectedly: "
                        + compact(cause.getMessage(), 240));
                return;
            }
            tell(playerRef, formatStatus(playerId, providerStatus));
        });
    }

    private String formatStatus(UUID playerId, LlmProviderStatus providerStatus) {
        String focus = sessions.active(playerId, Instant.now())
                .map(session -> "focused session=" + session.sessionId()
                        + (session.requestInFlight() ? " (request in flight)" : ""))
                .orElse("not focused");
        ConversationOutcome last = conversations.lastOutcome();
        String latency = last == null
                ? "no completed LLM request"
                : "last request started=" + last.llmLatency().requestStartedAt()
                        + ", TTFT=" + last.llmLatency().timeToFirstTokenMillis()
                        + "ms completion=" + last.llmLatency().completionMillis()
                        + "ms mode=" + (last.llmLatency().streaming()
                                ? "SSE streamed" : "non-streaming fallback")
                        + ", total=" + last.totalConversationMillis() + "ms";
        return "Local AI status\n"
                + "Endpoint: " + providerStatus.endpoint() + "\n"
                + "Model: " + providerStatus.model() + "\n"
                + "Configured: " + yesNo(providerStatus.configured()) + "\n"
                + "Reachable: " + yesNo(providerStatus.reachable()) + "\n"
                + "Streaming requested: " + yesNo(providerStatus.streamingEnabled()) + "\n"
                + "Connection/status: " + providerStatus.reason() + "\n"
                + "Voice: " + (spatialVoice == null ? "not initialized"
                        : spatialVoice.diagnosticStatus()) + "\n"
                + "Conversation: " + focus + "\n"
                + "Latency: " + latency;
    }

    private void submitOrbis(PlayerRef playerRef, ConversationSession session,
            String untrustedMessage, TurnIngressSource ingressSource) {
        String message = untrustedMessage == null ? "" : untrustedMessage;
        if (message.isBlank()) {
            tell(playerRef, "Say something to continue the conversation.");
            return;
        }
        if (message.length() > maximumMessageCharacters) {
            tell(playerRef, "That message is too long (maximum "
                    + maximumMessageCharacters + " characters).");
            return;
        }
        OrbisRuntime orbis = orbisRuntime;
        if (orbis == null || !conversationalReady.getAsBoolean()) {
            tell(playerRef, notReadyMessage());
            return;
        }
        Vector3d position = playerRef.getTransform().getPosition();
        orbis.submitText(playerRef.getUuid(), playerRef.getWorldUuid(),
                position.x, position.y, position.z, message, ingressSource);
    }

    private NpcProfile profileForSession(ConversationSession session) {
        if (profileRegistry == null || session == null) return profile.get();
        return profileRegistry.byId(session.npcId()).orElseGet(profileRegistry::defaultProfile);
    }

    private String notReadyMessage() {
        String status;
        try {
            status = startupStatus.get();
        } catch (RuntimeException ignored) {
            status = "ORBIS INITIALIZING";
        }
        String compactStatus = compact(status, 180);
        return "Immersive NPC conversation services are still warming. Please try again "
                + "shortly. " + compactStatus;
    }


    public static String formatPlayerEcho(String displayName, String exactMessage) {
        String name = displayName == null || displayName.isBlank()
                ? "Player" : displayName;
        return name + ": " + (exactMessage == null ? "" : exactMessage);
    }

    private static String playerDisplayName(PlayerRef playerRef) {
        // PlayerChatEvent is dispatched asynchronously in Update 6. Component access
        // through PlayerRef.getComponent(...) is forbidden there and emitted a severe
        // diagnostic during the traced turn. Username is immutable PlayerRef metadata and
        // is safe for the private echo on both chat and voice paths.
        String username = playerRef == null ? "" : playerRef.getUsername();
        return username == null || username.isBlank() ? "Player" : username;
    }

    private static MinimalWorldContext worldContext(PlayerRef playerRef) {
        World world = Universe.get().getWorld(playerRef.getWorldUuid());
        Vector3d position = playerRef.getTransform().getPosition();
        return new MinimalWorldContext(world == null ? "unknown" : world.getName(),
                (int) Math.round(position.x), (int) Math.round(position.y),
                (int) Math.round(position.z));
    }

    private static String gracefulFailure(String npcName, Throwable failure) {
        if (failure instanceof com.inigmasgames.persistentnpcs.conversation.ConversationBusyException) {
            return failure.getMessage();
        }
        if (failure instanceof com.inigmasgames.persistentnpcs.conversation.InvalidDialogueException) {
            return "The local model did not form a complete reply. Please try speaking to "
                    + npcName + " again.";
        }
        if (failure instanceof LlmSetupRequiredException) {
            return failure.getMessage() + " Check ImmersiveNPCs config.json and the server log. "
                    + npcName + " herself is available; only the local AI backend needs setup.";
        }
        if (failure instanceof LlmTimeoutException timeout) {
            return switch (timeout.phase()) {
                case RESPONSE_START -> "The local model did not begin responding before the "
                        + "response-start deadline. It may still be loading. "
                        + "Check the server log for the request ID.";
                case STREAM_IDLE -> "The local AI stream stopped producing SSE events before "
                        + npcName + " finished. Check the server log for the stream-idle reason.";
                case NON_STREAMING_COMPLETION -> "The local AI backend began a non-streaming "
                        + "request but did not complete it before its deadline. "
                        + "Check the server log for the request ID.";
            };
        }
        if (failure instanceof TimeoutException || failure instanceof HttpTimeoutException
                || failure instanceof HttpConnectTimeoutException) {
            return "The local AI backend timed out before " + npcName
                    + " could answer. Check its configured endpoint and model.";
        }
        if (failure instanceof ConnectException) {
            return "Local AI setup is required before " + npcName
                    + " can answer: no local OpenAI-compatible server is reachable. Start "
                    + "LM Studio or Ollama, then try speaking again. "
                    + npcName + " herself is not broken.";
        }
        if (failure instanceof LlmProviderException) {
            return npcName + " is available, but the local AI request failed: "
                    + compact(failure.getMessage(), 240) + ". Check the server log for details.";
        }
        return npcName + " cannot answer right now. The server remains responsive.";
    }

    private static String yesNo(boolean value) {
        return value ? "YES" : "NO";
    }

    private static String compact(String text, int maximum) {
        String normalized = text == null ? "unknown error"
                : text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maximum
                ? normalized : normalized.substring(0, maximum) + "...";
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static void tell(PlayerRef playerRef, String text) {
        if (!playerRef.isValid()) {
            return;
        }
        World world = Universe.get().getWorld(playerRef.getWorldUuid());
        if (world == null || !world.isAlive()) {
            return;
        }
        world.execute(() -> {
            if (playerRef.isValid()) {
                playerRef.sendMessage(Message.raw(text));
            }
        });
    }
}
