package com.inigmasgames.persistentnpcs.conversation;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import com.inigmasgames.persistentnpcs.voice.PlayerSpeechIntent;
import com.inigmasgames.persistentnpcs.voice.SpeechProjection;
import com.inigmasgames.persistentnpcs.voice.UtteranceRangeClass;
import com.inigmasgames.persistentnpcs.epistemic.ConversationWorkspace;

public final class ConversationSession {
    private static final int MAX_RECENT_TURNS = 8;
    private final UUID sessionId;
    private final UUID npcId;
    private final UUID playerId;
    private final Instant startedAt;
    /** The response that owns this session's provider slot. */
    private final AtomicReference<UUID> requestOwner = new AtomicReference<>();
    private final ArrayDeque<ConversationTurn> recentTurns = new ArrayDeque<>();
    private final LinkedHashMap<String, InvalidatedIntent> invalidatedIntents =
            new LinkedHashMap<>();
    private final ConversationWorkspace epistemicWorkspace = new ConversationWorkspace();
    private volatile Instant lastActivity;
    private PendingGuideOffer pendingGuideOffer;
    private PlayerUtteranceContext playerUtteranceContext;
    private String deferredConversationContext = "";

    public ConversationSession(UUID sessionId, UUID npcId, UUID playerId, Instant now) {
        this.sessionId = sessionId;
        this.npcId = npcId;
        this.playerId = playerId;
        this.startedAt = now;
        this.lastActivity = now;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public UUID npcId() {
        return npcId;
    }

    public UUID playerId() {
        return playerId;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant lastActivity() {
        return lastActivity;
    }

    public void touch(Instant now) {
        lastActivity = now;
    }

    public boolean beginRequest(UUID responseId) {
        java.util.Objects.requireNonNull(responseId, "responseId");
        return requestOwner.compareAndSet(null, responseId);
    }

    /** Releases only the matching response; a stale completion cannot clear a newer turn. */
    public boolean finishRequest(UUID responseId) {
        return responseId != null && requestOwner.compareAndSet(responseId, null);
    }

    public boolean requestInFlight() {
        return requestOwner.get() != null;
    }

    public UUID requestOwner() { return requestOwner.get(); }

    public synchronized void appendTurn(String playerMessage, String npcReply, Instant at) {
        appendTurn(playerMessage, npcReply, DialogueMode.ORDINARY_CONVERSATION, at);
    }

    public synchronized void appendTurn(
            String playerMessage, String npcReply, DialogueMode mode, Instant at) {
        recentTurns.addLast(new ConversationTurn(
                compact(playerMessage, 400), compact(npcReply, 600),
                mode == null ? DialogueMode.ORDINARY_CONVERSATION : mode, at));
        while (recentTurns.size() > MAX_RECENT_TURNS) {
            recentTurns.removeFirst();
        }
        epistemicWorkspace.observeDelivered(npcReply, at);
    }

    public synchronized List<ConversationTurn> recentTurns(int limit) {
        if (limit <= 0 || recentTurns.isEmpty()) {
            return List.of();
        }
        return recentTurns.stream().skip(Math.max(0, recentTurns.size() - limit)).toList();
    }

    public synchronized String recentConversationBlock(String npcName, int limit) {
        List<ConversationTurn> turns = recentTurns(limit);
        if (turns.isEmpty()) {
            return "None (new session).";
        }
        StringBuilder block = new StringBuilder();
        for (ConversationTurn turn : turns) {
            if (!block.isEmpty()) {
                block.append('\n');
            }
            if (turn.mode() == DialogueMode.NPC_INITIATED_CURIOSITY) {
                block.append(npcName).append(" (initiated): ");
            } else {
                block.append("Player: ").append(turn.playerMessage()).append('\n')
                        .append(npcName).append(": ");
            }
            if (turn.mode() == DialogueMode.FICTIONAL_STORY) {
                block.append("[FICTIONAL_STORY; not current world state] ");
            }
            block.append(turn.npcReply());
        }
        return block.toString();
    }

    public synchronized void invalidateIntent(String value, String reason, Instant at) {
        String normalized = normalizeIntent(value);
        if (normalized.isBlank()) {
            return;
        }
        invalidatedIntents.put(normalized,
                new InvalidatedIntent(normalized, reason == null ? "unavailable" : reason, at));
        while (invalidatedIntents.size() > 6) {
            invalidatedIntents.remove(invalidatedIntents.keySet().iterator().next());
        }
    }

    public synchronized void validateIntent(String value) {
        invalidatedIntents.remove(normalizeIntent(value));
    }

    public synchronized List<InvalidatedIntent> invalidatedIntents() {
        return List.copyOf(invalidatedIntents.values());
    }

    public synchronized String invalidatedIntentBlock() {
        if (invalidatedIntents.isEmpty()) {
            return "None.";
        }
        return invalidatedIntents.values().stream()
                .map(intent -> "- desiredThing=" + intent.value()
                        + ", status=UNAVAILABLE, constraint=" + intent.reason())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    public synchronized boolean isInvalidated(String value) {
        return invalidatedIntents.containsKey(normalizeIntent(value));
    }

    public synchronized void offerGuide(UUID targetId, String targetName, Instant expiresAt) {
        if (targetId == null || expiresAt == null) return;
        pendingGuideOffer = new PendingGuideOffer(targetId,
                compact(targetName, 80), expiresAt);
    }

    public synchronized PendingGuideOffer pendingGuideOffer() {
        if (pendingGuideOffer != null && !Instant.now().isBefore(
                pendingGuideOffer.expiresAt())) {
            pendingGuideOffer = null;
        }
        return pendingGuideOffer;
    }

    public synchronized void clearPendingGuideOffer() {
        pendingGuideOffer = null;
    }

    public synchronized void setPlayerUtteranceContext(PlayerUtteranceContext context) {
        playerUtteranceContext = context;
    }

    public synchronized PlayerUtteranceContext playerUtteranceContext() {
        return playerUtteranceContext;
    }

    public synchronized void clearPlayerUtteranceContext(UUID utteranceId) {
        if (playerUtteranceContext != null && java.util.Objects.equals(
                playerUtteranceContext.utteranceId(), utteranceId)) {
            playerUtteranceContext = null;
        }
    }

    public synchronized void setDeferredConversationContext(String value) {
        deferredConversationContext = compact(value, 1_500);
    }

    public synchronized String deferredConversationContext() {
        return deferredConversationContext;
    }

    public synchronized void clearDeferredConversationContext() {
        deferredConversationContext = "";
    }

    public ConversationWorkspace epistemicWorkspace() { return epistemicWorkspace; }

    private static String normalizeIntent(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 _-]", " ").replaceAll("\\s+", " ").strip();
        if (normalized.endsWith("s") && normalized.length() > 3) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String compact(String value, int maximum) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        return text.length() <= maximum ? text : text.substring(0, maximum) + "...";
    }

    public record ConversationTurn(
            String playerMessage, String npcReply, DialogueMode mode, Instant at) {
    }

    public record InvalidatedIntent(String value, String reason, Instant at) {
    }

    public record PendingGuideOffer(UUID targetId, String targetName, Instant expiresAt) { }

    /** Ephemeral response metadata; no raw coordinates or engine identity are persisted. */
    public record PlayerUtteranceContext(
            UUID utteranceId,
            PlayerSpeechIntent speechIntent,
            UtteranceRangeClass rangeClass,
            boolean directAddress,
            String distanceBand,
            String directionFromPlayer,
            SpeechProjection projection,
            long endpointMillis,
            long sttMillis,
            long audienceResolutionMillis) {
        public PlayerUtteranceContext(UUID utteranceId, PlayerSpeechIntent speechIntent,
                UtteranceRangeClass rangeClass, boolean directAddress, String distanceBand,
                String directionFromPlayer, SpeechProjection projection) {
            this(utteranceId, speechIntent, rangeClass, directAddress, distanceBand,
                    directionFromPlayer, projection, -1, -1, -1);
        }

        public PlayerUtteranceContext {
            speechIntent = speechIntent == null
                    ? PlayerSpeechIntent.CONVERSATION : speechIntent;
            rangeClass = rangeClass == null ? UtteranceRangeClass.ORDINARY : rangeClass;
            distanceBand = compact(distanceBand, 80);
            directionFromPlayer = compact(directionFromPlayer, 40);
            projection = projection == null ? SpeechProjection.NORMAL : projection;
            endpointMillis = Math.max(-1, endpointMillis);
            sttMillis = Math.max(-1, sttMillis);
            audienceResolutionMillis = Math.max(-1, audienceResolutionMillis);
        }

        public boolean remoteHail() {
            return rangeClass == UtteranceRangeClass.REMOTE_HAIL;
        }
    }
}
