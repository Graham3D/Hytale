package com.inigmasgames.persistentnpcs.voice;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.persistentnpcs.cognition.GroundedIntent;
import com.inigmasgames.persistentnpcs.cognition.PlayerFactMemoryService;
import com.inigmasgames.persistentnpcs.conversation.ConversationSessionManager;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.hytale.NpcRuntimeRegistry;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import com.inigmasgames.persistentnpcs.cognition.SourcedBeliefStore;
import com.inigmasgames.persistentnpcs.diagnostics.NpcTurnAuditLog;
import com.inigmasgames.persistentnpcs.perception.KnownNpcLocatorService;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.relationship.RelationshipRecord;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.joml.Vector3d;

/**
 * Stateless Hytale/ECS audience algorithm invoked through the Orbis audience gateway.
 * It does not create turns, response IDs, or provider requests.
 */
public final class PlayerUtteranceAudienceService {
    private final NpcProfileRegistry profiles;
    private final NpcRuntimeRegistry runtimes;
    private final ConversationSessionManager sessions;
    private final RelationshipStore relationships;
    private final MemoryStore memories;
    private final PlayerFactMemoryService playerFacts;
    private final NpcTurnAuditLog turnAudit;
    private final double conversationListenRadius;
    private final double remoteHailRadius;
    private final Consumer<String> diagnostics;
    private final SttSemanticCorrector transcriptCorrector;

    public PlayerUtteranceAudienceService(NpcProfileRegistry profiles,
            NpcRuntimeRegistry runtimes, ConversationSessionManager sessions,
            RelationshipStore relationships, MemoryStore memories,
            VoiceRuntimeConfig config, Consumer<String> diagnostics) {
        this(profiles, runtimes, sessions, relationships, memories, null, null,
                config, diagnostics);
    }

    public PlayerUtteranceAudienceService(NpcProfileRegistry profiles,
            NpcRuntimeRegistry runtimes, ConversationSessionManager sessions,
            RelationshipStore relationships, MemoryStore memories,
            SourcedBeliefStore sourcedBeliefs, NpcTurnAuditLog turnAudit,
            VoiceRuntimeConfig config, Consumer<String> diagnostics) {
        this.profiles = profiles;
        this.runtimes = runtimes;
        this.sessions = sessions;
        this.relationships = relationships;
        this.memories = memories;
        this.playerFacts = new PlayerFactMemoryService(profiles, sourcedBeliefs, memories);
        this.turnAudit = turnAudit;
        this.conversationListenRadius = config.effectiveConversationListenRadius();
        this.remoteHailRadius = config.effectiveRemoteHailRadius();
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
        this.transcriptCorrector = new SttSemanticCorrector(profiles, sessions);
    }

    public SttSemanticCorrector.Correction correctTranscript(UUID playerId, String raw) {
        return transcriptCorrector.correct(playerId, raw);
    }

    /** Must be invoked on the captured world's owning thread. */
    public Resolution resolve(TranscribedPlayerUtterance input, World world) {
        long started = System.nanoTime();
        String text = normalize(input.transcript());
        PlayerSpeechIntent speechIntent = classify(text);
        Set<UUID> directTargets = resolveDirectTargets(text, speechIntent);
        List<EligibleNpcListener> listeners = new ArrayList<>();
        Store<EntityStore> store = world.getEntityStore().getStore();
        Vector3d playerPosition = new Vector3d(
                input.playerX(), input.playerY(), input.playerZ());
        Instant now = Instant.now();

        for (NpcProfile profile : profiles.profiles()) {
            NpcRuntimeRegistry.RuntimeNpc runtime = runtimes.forProfile(profile.id()).orElse(null);
            if (runtime == null || !java.util.Objects.equals(input.worldId(), runtime.worldId())) {
                continue;
            }
            Ref<EntityStore> ref = world.getEntityRef(runtime.entityId());
            TransformComponent transform = ref == null || !ref.isValid() ? null
                    : store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) continue;
            Vector3d delta = new Vector3d(transform.getPosition()).sub(playerPosition);
            double distance = delta.length();
            boolean direct = directTargets.contains(profile.id());
            UtteranceRangeClass rangeClass;
            if (distance <= conversationListenRadius) {
                rangeClass = UtteranceRangeClass.ORDINARY;
            } else if (direct && speechIntent != PlayerSpeechIntent.CONVERSATION
                    && distance <= remoteHailRadius) {
                rangeClass = UtteranceRangeClass.REMOTE_HAIL;
            } else {
                continue;
            }
            boolean activePartner = sessions.active(
                    input.playerId(), profile.id(), now).isPresent();
            RelationshipRecord relationship = relationships.getOrDefault(
                    profile.id(), input.playerId(), profile.defaultDisposition());
            double score = (direct ? 1_000.0 : 0.0) + (activePartner ? 500.0 : 0.0)
                    + Math.max(0.0, conversationListenRadius - distance) * 12.0
                    + Math.min(80.0, relationship.interactionCount() * 4.0)
                    + relationship.disposition() * 0.20 + profile.sociability() * 10.0;
            listeners.add(new EligibleNpcListener(profile.id(), profile.name(), distance,
                    KnownNpcLocatorService.distanceBand(distance),
                    KnownNpcLocatorService.direction(delta), rangeClass, direct,
                    activePartner, score));
        }

        listeners.sort(Comparator.comparingDouble(
                EligibleNpcListener::attentionScore).reversed()
                .thenComparingDouble(EligibleNpcListener::distanceMeters)
                .thenComparing(EligibleNpcListener::npcName,
                        String.CASE_INSENSITIVE_ORDER));
        List<EligibleNpcListener> owners = selectResponseOwners(listeners, directTargets);
        long audienceMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                Math.max(0, System.nanoTime() - started));
        PlayerUtteranceEvent event = new PlayerUtteranceEvent(input.utteranceId(),
                input.playerId(), input.transcript(), input.worldId(), input.playerX(),
                input.playerY(), input.playerZ(), input.timestamp(), directTargets,
                speechIntent, listeners, input.endpointMillis(), input.sttMillis(),
                audienceMillis);
        Map<UUID, GroundedIntent> intents = new LinkedHashMap<>();
        Set<UUID> ownerIds = owners.stream().map(EligibleNpcListener::npcId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Map<UUID, String> suppressionReasons = new LinkedHashMap<>();
        Map<UUID, PlayerFactMemoryService.PersistenceResult> observations =
                new LinkedHashMap<>();
        for (EligibleNpcListener listener : listeners) {
            intents.put(listener.npcId(), ownerIds.contains(listener.npcId())
                    && listener.rangeClass() == UtteranceRangeClass.REMOTE_HAIL
                            ? GroundedIntent.RESPOND_TO_REMOTE_HAIL
                            : ownerIds.contains(listener.npcId())
                                    ? GroundedIntent.PROCESS_INFORMATION
                                    : GroundedIntent.AMBIENT_RESPONSE);
            ConversationSession existing = sessions.active(event.playerId(),
                    listener.npcId(), now).orElse(null);
            PlayerFactMemoryService.PersistenceResult observation = playerFacts.persist(
                    listener.npcId(), event.playerId(),
                    existing == null ? null : existing.sessionId(), null,
                    event.utteranceId(), event.transcript(), event.timestamp());
            if (!ownerIds.contains(listener.npcId())) {
                suppressionReasons.put(listener.npcId(), listener.directAddress()
                        ? "ARBITRATION_LOWER_PRIORITY" : "LISTENER_NOT_SELECTED_TO_SPEAK");
            }
            observations.put(listener.npcId(), observation);
        }
        Resolution resolution = new Resolution(event, owners, Map.copyOf(intents),
                Map.copyOf(suppressionReasons), Map.copyOf(observations));
        if (turnAudit != null) {
            for (EligibleNpcListener listener : listeners) {
                profiles.byId(listener.npcId()).ifPresent(profile -> {
                    try {
                        turnAudit.hearing(profile, resolution, listener,
                                observations.get(listener.npcId()));
                    } catch (RuntimeException failure) {
                        diagnostics.accept("TRACE_DIAGNOSTIC_FAILED stage=hearing npc="
                                + listener.npcId() + " type="
                                + failure.getClass().getSimpleName());
                        try {
                            turnAudit.diagnosticFailure(profile, existingSession(
                                    event.playerId(), listener.npcId(), now), null,
                                    "hearing", failure);
                        } catch (RuntimeException ignored) { }
                    }
                });
            }
        }
        diagnostics.accept("PLAYER_UTTERANCE_AUDIENCE utteranceId=" + event.utteranceId()
                + " player=" + event.playerId() + " sttRuns=1 candidates="
                + listeners.stream().map(EligibleNpcListener::npcName).toList()
                + " directTargets=" + listeners.stream()
                        .filter(EligibleNpcListener::directAddress)
                        .map(EligibleNpcListener::npcName).toList()
                + " owners=" + owners.stream().map(EligibleNpcListener::npcName).toList()
                + " audienceMs=" + audienceMillis);
        return resolution;
    }

    private ConversationSession existingSession(UUID playerId, UUID npcId, Instant now) {
        return sessions.active(playerId, npcId, now).orElse(null);
    }

    public Set<UUID> resolveDirectTargets(String transcript, PlayerSpeechIntent intent) {
        String text = normalize(transcript);
        // An explicit leading vocative is the response owner. Other names later in the
        // sentence are semantic objects/recipients, not additional addressees.
        for (NpcProfile profile : profiles.profiles()) {
            String name = normalize(profile.name());
            if (text.equals(name) || text.startsWith(name + " ")
                    || text.startsWith("hey " + name + " ")
                    || text.equals("hey " + name)) return Set.of(profile.id());
        }
        Set<UUID> resolved = new LinkedHashSet<>();
        for (NpcProfile profile : profiles.profiles()) {
            String name = normalize(profile.name());
            if (containsWholePhrase(text, name)
                    || intent != PlayerSpeechIntent.CONVERSATION
                            && fuzzyNamePresent(text, name)) {
                resolved.add(profile.id());
            }
        }
        return Set.copyOf(resolved);
    }

    public static PlayerSpeechIntent classify(String transcript) {
        String text = normalize(transcript);
        if (contains(text, "where are you", "where're you", "where did you go",
                "where can i find you")) return PlayerSpeechIntent.LOCATE_SPEAKER;
        if (contains(text, "answer me", "respond", "can you hear me", "call back")) {
            return PlayerSpeechIntent.REQUEST_ANSWER;
        }
        if (contains(text, "i'm looking for", "im looking for", "shouting for",
                "calling for", "come out", "are you there")) {
            return PlayerSpeechIntent.SEARCH_CALL;
        }
        if (text.matches("[\\p{L}\\p{N}' -]{2,80}[!?]*")
                && text.split(" ").length <= 8) {
            return PlayerSpeechIntent.DIRECT_ADDRESS;
        }
        return PlayerSpeechIntent.CONVERSATION;
    }

    public static SpeechProjection projectionFor(EligibleNpcListener listener,
            double remoteHailRadius) {
        if (listener == null || listener.rangeClass() == UtteranceRangeClass.ORDINARY) {
            return SpeechProjection.NORMAL;
        }
        return listener.distanceMeters() >= Math.max(1.0, remoteHailRadius * 0.72)
                ? SpeechProjection.SHOUT : SpeechProjection.CALL;
    }

    /** Speech arbitration is downstream of hearing and never mutates the listener list. */
    public static List<EligibleNpcListener> selectResponseOwners(
            List<EligibleNpcListener> eligibleListeners) {
        List<EligibleNpcListener> listeners = List.copyOf(
                eligibleListeners == null ? List.of() : eligibleListeners);
        List<EligibleNpcListener> directlyAddressed = listeners.stream()
                .filter(EligibleNpcListener::directAddress).toList();
        return !directlyAddressed.isEmpty() ? directlyAddressed
                : listeners.isEmpty() ? List.of() : List.of(listeners.getFirst());
    }

    static List<EligibleNpcListener> selectResponseOwners(
            List<EligibleNpcListener> eligibleListeners, Set<UUID> explicitTargets) {
        List<EligibleNpcListener> listeners = List.copyOf(
                eligibleListeners == null ? List.of() : eligibleListeners);
        Set<UUID> targets = explicitTargets == null ? Set.of() : explicitTargets;
        if (!targets.isEmpty()) {
            return listeners.stream().filter(value -> targets.contains(value.npcId())).toList();
        }
        return listeners.isEmpty() ? List.of() : List.of(listeners.getFirst());
    }

    private static boolean fuzzyNamePresent(String text, String name) {
        if (name.length() < 5) return false;
        int words = name.split(" ").length;
        String[] tokens = text.split(" ");
        for (int start = 0; start + words <= tokens.length; start++) {
            String candidate = String.join(" ", java.util.Arrays.copyOfRange(
                    tokens, start, start + words));
            if (candidate.length() >= 5 && levenshtein(candidate, name) <= 2) return true;
        }
        return false;
    }

    static int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            previous = current;
        }
        return previous[right.length()];
    }

    private static boolean containsWholePhrase(String text, String phrase) {
        return (" " + text + " ").contains(" " + phrase + " ");
    }

    private static boolean contains(String text, String... needles) {
        for (String needle : needles) if (text.contains(needle)) return true;
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}' ]", " ").replaceAll("\\s+", " ").strip();
    }

    private static String compact(String value, int maximum) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        return text.length() <= maximum ? text : text.substring(0, maximum) + "...";
    }

    public record Resolution(PlayerUtteranceEvent event,
            List<EligibleNpcListener> responseOwners,
            Map<UUID, GroundedIntent> selectedIntents,
            Map<UUID, String> suppressionReasons,
            Map<UUID, PlayerFactMemoryService.PersistenceResult> listenerObservations) {
        public Resolution {
            responseOwners = List.copyOf(responseOwners == null ? List.of() : responseOwners);
            selectedIntents = Map.copyOf(selectedIntents == null ? Map.of() : selectedIntents);
            suppressionReasons = Map.copyOf(
                    suppressionReasons == null ? Map.of() : suppressionReasons);
            listenerObservations = Map.copyOf(
                    listenerObservations == null ? Map.of() : listenerObservations);
        }
    }
}
