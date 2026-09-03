package com.inigmasgames.persistentnpcs.scene;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** Eligibility gate and turn arbiter for exact text-to-text NPC speech delivery. */
public final class NpcSpeechRouter {
    private final int maximumTurns;
    private final int cooldownSeconds;
    private final Function<UUID, NpcHearingSnapshot> hearing;
    private final BiConsumer<UUID, NpcSpeechEvent> exactTextReceiver;
    private final Consumer<NpcSpeechEvent> nearbyPlayerTts;
    private final NpcSpeechAttention attention;
    private final Consumer<String> diagnostics;
    private final Map<UUID, ConversationState> conversations = new ConcurrentHashMap<>();
    private final Map<String, Instant> pairCooldowns = new ConcurrentHashMap<>();

    public NpcSpeechRouter(
            int maximumTurns,
            int cooldownSeconds,
            Function<UUID, NpcHearingSnapshot> hearing,
            BiConsumer<UUID, NpcSpeechEvent> exactTextReceiver,
            Consumer<NpcSpeechEvent> nearbyPlayerTts,
            NpcSpeechAttention attention,
            Consumer<String> diagnostics) {
        this.maximumTurns = Math.max(1, maximumTurns);
        this.cooldownSeconds = Math.max(1, cooldownSeconds);
        this.hearing = hearing;
        this.exactTextReceiver = exactTextReceiver;
        this.nearbyPlayerTts = nearbyPlayerTts == null ? ignored -> { } : nearbyPlayerTts;
        this.attention = attention == null ? NpcSpeechAttention.noOp() : attention;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
    }

    public synchronized DeliveryResult route(NpcSpeechEvent untrusted) {
        NpcSpeechEvent event;
        try {
            event = untrusted.normalized();
        } catch (RuntimeException failure) {
            return reject("INVALID_EVENT", failure.getMessage());
        }
        UUID listenerId = event.targetNpcId();
        if (listenerId == null) {
            return reject("NO_DIRECT_TARGET",
                    "Ambient audience delivery requires explicit per-listener routing");
        }
        if (!event.addresses(listenerId) || listenerId.equals(event.speakerNpcId())) {
            return reject("TARGET_MISMATCH", "Speech target is not eligible");
        }
        Instant cooldown = pairCooldowns.get(pair(event.speakerNpcId(), listenerId));
        if (cooldown != null && event.timestamp().isBefore(cooldown)) {
            return reject("PAIR_COOLDOWN", "NPC pair is on conversation cooldown");
        }
        NpcHearingSnapshot snapshot = hearing == null ? null : hearing.apply(listenerId);
        if (snapshot == null || snapshot.listenerNpcId() == null
                || !listenerId.equals(snapshot.listenerNpcId())) {
            return reject("LISTENER_UNAVAILABLE", "Listener state is not loaded");
        }
        snapshot = snapshot.normalized();
        if (!snapshot.state().canHearNpcSpeech() || !snapshot.state().interruptible()) {
            return reject("HIGH_PRIORITY_STATE",
                    "Listener state " + snapshot.state() + " cannot be interrupted");
        }
        if (snapshot.activeConversationId() != null
                && !snapshot.activeConversationId().equals(event.conversationId())) {
            return reject("COMPETING_CONVERSATION", "Listener is already in another conversation");
        }
        double distance = event.location().distanceTo(snapshot.location());
        if (!Double.isFinite(distance) || distance > event.audibilityMeters()) {
            return reject("OUT_OF_RANGE", "Listener is outside speech range");
        }
        if (!snapshot.lineOfSight()) {
            return reject("NO_LINE_OF_SIGHT", "Available hearing rules require line of sight");
        }
        ConversationState state = conversations.computeIfAbsent(event.conversationId(),
                ignored -> new ConversationState(event.speakerNpcId(), listenerId));
        if (state.turns >= maximumTurns) {
            finishConversation(event.conversationId(), event.timestamp(), false);
            return reject("TURN_LIMIT", "NPC conversation reached its bounded turn limit");
        }
        if (event.speakerNpcId().equals(state.lastSpeaker)) {
            return reject("TURN_COLLISION", "The same NPC cannot take two consecutive turns");
        }
        if (!attention.beginListening(listenerId, event.speakerNpcId(), event.conversationId())) {
            return reject("ATTENTION_REJECTED", "Listener could not pause its current activity");
        }
        state.participants.add(event.speakerNpcId());
        state.participants.add(listenerId);
        state.lastSpeaker = event.speakerNpcId();
        state.turns++;
        nearbyPlayerTts.accept(event);
        exactTextReceiver.accept(listenerId, event);
        diagnostics.accept("NPC_SPEECH_DELIVERED conversationId=" + event.conversationId()
                + " turn=" + state.turns + "/" + maximumTurns + " speaker="
                + event.speakerNpcId() + " listener=" + listenerId + " distance="
                + "%.2f".formatted(distance) + " directText=true stt=false topic="
                + event.topic());
        return new DeliveryResult(true, "DELIVERED", "Exact text delivered", state.turns);
    }

    public synchronized void finishConversation(
            UUID conversationId, Instant now, boolean interrupted) {
        ConversationState state = conversations.remove(conversationId);
        if (state == null) return;
        Instant resolved = now == null ? Instant.now() : now;
        pairCooldowns.put(pair(state.first, state.second),
                resolved.plusSeconds(cooldownSeconds));
        state.participants.forEach(participant ->
                attention.finishListening(participant, conversationId, interrupted));
        diagnostics.accept("NPC_SPEECH_CONVERSATION_END conversationId=" + conversationId
                + " turns=" + state.turns + " interrupted=" + interrupted);
    }

    private DeliveryResult reject(String code, String reason) {
        diagnostics.accept("NPC_SPEECH_REJECTED code=" + code + " reason=" + reason);
        return new DeliveryResult(false, code, reason, 0);
    }

    private static String pair(UUID first, UUID second) {
        return first.compareTo(second) < 0 ? first + ":" + second : second + ":" + first;
    }

    public record DeliveryResult(boolean delivered, String code, String reason, int turnNumber) { }

    private static final class ConversationState {
        private final UUID first;
        private final UUID second;
        private final Set<UUID> participants = new LinkedHashSet<>();
        private UUID lastSpeaker;
        private int turns;

        private ConversationState(UUID first, UUID second) {
            this.first = first;
            this.second = second;
        }
    }
}
