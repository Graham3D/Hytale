package com.inigmasgames.persistentnpcs.cognition;

import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared deterministic boundary from an immutable utterance to sourced beliefs and episodic
 * memory. Importance affects retention; it never decides whether an explicit fact existed.
 */
public final class PlayerFactMemoryService {
    private static final Pattern ACKNOWLEDGEMENT = Pattern.compile(
            "^(?:yes|yeah|yep|no|nope|right|correct|exactly|sure|okay|ok|fine|got it|"
                    + "i understand|thanks|thank you)(?:[.! ]*)$");
    private static final Pattern CONFIRMATION = Pattern.compile(
            "^(?:(?:yes|yeah|yep|right|correct|exactly)[, ]+)?(?:"
                    + "that(?:'s| is) (?:what i said|what i meant|right|correct|it)|"
                    + "as i said|i already told you|you heard me)(?:[.! ]*)$");
    private static final Pattern COMMAND = Pattern.compile(
            "^(?:please )?(?:take|follow|lead|come|go|give|show|tell|bring|find|wait|"
                    + "stop|look|listen|remember|forget|put|place|move|walk|help)\\b.*");
    private static final Pattern QUESTION = Pattern.compile(
            "^(?:who|what|when|where|why|how|which|can|could|would|will|do|does|did|"
                    + "is|are|am|have|has|should|may)\\b.*");
    private static final Pattern FIRST_PERSON = Pattern.compile(
            "^(?:i|i'm|i am|i've|i have|we|we're|we are|my|our)\\b.*");
    private static final Pattern DECLARATIVE_VERB = Pattern.compile(
            "\\b(?:am|are|is|was|were|have|has|had|live|lived|work|worked|saw|see|"
                    + "heard|hear|hid|hide|stashed|stash|buried|bury|placed|place|put|"
                    + "left|leave|found|find|lost|lose|kept|keep|stored|store|gave|give|"
                    + "promised|promise|will|want|wants|need|needs|asked|said|arrived|"
                    + "changed|hurt|missing|belongs|own|owns|made|met|visited)\\b");
    private static final Pattern LOCATION = Pattern.compile(
            "\\b(?:at|near|under|beside|behind|inside|outside|by|within)\\s+"
                    + "(.+?)(?=\\b(?:yesterday|today|tomorrow|tonight|earlier|last|"
                    + "this morning|this evening|[0-9]+ days? ago)\\b|$)");
    private static final Pattern TEMPORAL = Pattern.compile(
            "\\b(yesterday|today|tomorrow|tonight|earlier|last (?:night|week|month|year)|"
                    + "this (?:morning|evening|week)|[0-9]+ days? ago)\\b");
    private static final Pattern SELF_REPORTED_NAME = Pattern.compile(
            "^(?:my name is|i am|i'm)\\s+([\\p{L}][\\p{L}'-]{0,31})(?:[, ]+not\\s+"
                    + "[\\p{L}][\\p{L}'-]{0,31})?.*$");

    private final NpcProfileRegistry profiles;
    private final SourcedBeliefStore beliefs;
    private final MemoryStore memories;

    public PlayerFactMemoryService(NpcProfileRegistry profiles,
            SourcedBeliefStore beliefs, MemoryStore memories) {
        this.profiles = profiles;
        this.beliefs = beliefs;
        this.memories = memories;
    }

    public Analysis analyze(UUID playerId, String message) {
        String text = compact(message, 600);
        String normalized = normalize(text);
        PlayerInputKind kind = classify(text);
        if (kind != PlayerInputKind.DECLARATIVE_FACT) {
            return new Analysis(kind, List.of(), "Dialogue act is not a new proposition");
        }
        Subject subject = subject(playerId, normalized);
        Matcher statedName = SELF_REPORTED_NAME.matcher(normalized);
        if (statedName.matches()) {
            String name = statedName.group(1);
            PlayerFactProposition proposition = new PlayerFactProposition(subject.id(),
                    subject.label(), "NAME", name, "", "", text, 0.86);
            return new Analysis(kind, List.of(proposition),
                    "Explicit self-reported identity proposition");
        }
        String action = action(normalized);
        String predicate = predicate(normalized, action);
        String location = match(LOCATION, normalized);
        String temporal = match(TEMPORAL, normalized);
        String object = object(normalized, action, location, temporal);
        PlayerFactProposition proposition = new PlayerFactProposition(subject.id(),
                subject.label(), predicate, object, location, temporal, text, 0.72);
        return new Analysis(kind, List.of(proposition), "Explicit declarative proposition");
    }

    public PersistenceResult persist(UUID npcId, UUID playerId, UUID conversationId,
            UUID responseId, UUID utteranceId, String message, Instant at) {
        Analysis analysis = analyze(playerId, message);
        if (beliefs == null || analysis.propositions().isEmpty()) {
            return new PersistenceResult(analysis, List.of(), List.of(),
                    analysis.reason());
        }
        List<SourcedBelief> accepted = new ArrayList<>();
        List<UUID> memoryWrites = new ArrayList<>();
        String rejection = "";
        for (PlayerFactProposition proposition : analysis.propositions()) {
            List<String> evidence = new ArrayList<>();
            evidence.add("PLAYER_REPORT:source=" + playerId);
            if (utteranceId != null) evidence.add("PLAYER_UTTERANCE:" + utteranceId);
            if (conversationId != null) evidence.add("CONVERSATION:" + conversationId);
            if (responseId != null) evidence.add("RESPONSE:" + responseId);
            SourcedBelief candidate = new SourcedBelief(UUID.randomUUID(), npcId,
                    playerId, proposition.subjectEntityId(), proposition.subject(),
                    proposition.predicate(), proposition.object(), proposition.semanticLocation(),
                    proposition.temporalReference(), proposition.proposition(), at,
                    proposition.confidence(), urgency(message), conversationId, responseId,
                    utteranceId, evidence).normalized();
            var existing = beliefs.byFingerprint(npcId, candidate.fingerprint());
            if (existing.isPresent()) {
                // Audience delivery deliberately precedes response arbitration. Reuse the
                // exact same-utterance belief in the speaking NPC's cognition without a
                // second durable write; later repetitions remain rejected duplicates.
                if (utteranceId != null && utteranceId.equals(existing.get().utteranceId())) {
                    accepted.add(existing.get());
                } else {
                    rejection = "DUPLICATE_PROPOSITION";
                }
                continue;
            }
            SourcedBelief stored = beliefs.append(candidate);
            accepted.add(stored);
            if (memories != null) {
                UUID memoryId = UUID.randomUUID();
                List<UUID> involved = stored.subjectEntityId() == null
                        || stored.subjectEntityId().equals(playerId) ? List.of(playerId)
                        : List.of(playerId, stored.subjectEntityId());
                memories.append(new MemoryRecord(memoryId, npcId, playerId, at,
                        MemoryType.PLAYER_FACT, Math.max(0.45, stored.urgency()),
                        "Player-reported belief: " + stored.proposition(),
                        stored.confidence(), provenance(stored), involved,
                        stored.semanticLocation(),
                        "The player reported this; it was not directly perceived."));
                memoryWrites.add(memoryId);
            }
        }
        return new PersistenceResult(analysis, List.copyOf(accepted),
                List.copyOf(memoryWrites), rejection);
    }

    public static PlayerInputKind classify(String message) {
        String value = normalize(stripLeadingVocative(message));
        if (value.isBlank()) return PlayerInputKind.OTHER;
        // Interrogative form is authoritative even when STT/chat supplies smart quotes,
        // trailing quote marks, or a mistyped terminal character instead of '?'.
        if (QUESTION.matcher(value).matches()) return PlayerInputKind.QUESTION;
        if (ACKNOWLEDGEMENT.matcher(value).matches()) return PlayerInputKind.ACKNOWLEDGEMENT;
        if (CONFIRMATION.matcher(value).matches()) return PlayerInputKind.CONFIRMATION;
        if (COMMAND.matcher(value).matches()) return PlayerInputKind.COMMAND;
        if (value.length() >= 6 && (FIRST_PERSON.matcher(value).matches()
                && DECLARATIVE_VERB.matcher(value).find()
                || DECLARATIVE_VERB.matcher(value).find()
                        && value.split(" ").length >= 3)) {
            return PlayerInputKind.DECLARATIVE_FACT;
        }
        return PlayerInputKind.OTHER;
    }

    private static String stripLeadingVocative(String message) {
        String value = message == null ? "" : message.strip()
                .replaceFirst("^[\\\"'“”‘’]+", "");
        return value.replaceFirst("(?iu)^[\\p{L}][\\p{L}'-]{0,31}\\s*[,.:;-]\\s*", "");
    }

    private Subject subject(UUID playerId, String text) {
        if (FIRST_PERSON.matcher(text).matches() || profiles == null) {
            return new Subject(playerId, "focused player");
        }
        String padded = " " + text + " ";
        for (NpcProfile profile : profiles.profiles()) {
            if (padded.contains(" " + normalize(profile.name()) + " ")) {
                return new Subject(profile.id(), profile.name());
            }
        }
        return new Subject(playerId, "focused player");
    }

    private static String predicate(String text, String action) {
        if (contains(text, "danger", "attack", "hurt", "missing")) return "DANGER_REPORT";
        if (contains(text, "promise", "promised", " will ")) return "COMMITMENT_REPORT";
        if (List.of("CONCEALED", "PLACED", "OBSERVED", "HEARD", "LEFT", "FOUND", "LOST")
                .contains(action)) return action;
        if (!match(LOCATION, text).isBlank()) return "LOCATION_REPORT";
        if (contains(text, "arrived", "left", "changed", "became")) return "STATE_CHANGE";
        return "PLAYER_REPORT";
    }

    private static String action(String text) {
        Matcher matcher = DECLARATIVE_VERB.matcher(text);
        return matcher.find() ? canonicalVerb(matcher.group()) : "reported";
    }

    private static String object(String text, String action, String location, String temporal) {
        Matcher verb = DECLARATIVE_VERB.matcher(text);
        String value = verb.find() ? text.substring(verb.end()).strip() : text;
        if (!location.isBlank()) value = value.replaceFirst(
                "\\b(?:at|near|under|beside|behind|inside|outside|by|within)\\s+"
                        + Pattern.quote(location), " ");
        if (!temporal.isBlank()) value = value.replace(temporal, " ");
        value = value.replaceFirst("^(?:at|near|under|beside|behind|inside|outside|by)\\b", "")
                .replaceAll("\\s+", " ").strip();
        return compact(value, 180);
    }

    private static String canonicalVerb(String verb) {
        return switch (verb) {
            case "hid", "hide", "stashed", "stash", "buried", "bury" -> "CONCEALED";
            case "placed", "place", "put", "stored", "store", "kept", "keep" -> "PLACED";
            case "saw", "see" -> "OBSERVED";
            case "heard", "hear" -> "HEARD";
            case "promised", "promise", "will" -> "COMMITTED";
            case "left", "leave" -> "LEFT";
            case "found", "find" -> "FOUND";
            case "lost", "lose" -> "LOST";
            default -> verb.toUpperCase(Locale.ROOT);
        };
    }

    private static String provenance(SourcedBelief belief) {
        return "PLAYER_REPORT:source=" + belief.sourceEntityId()
                + (belief.utteranceId() == null ? "" : ";utterance=" + belief.utteranceId())
                + (belief.conversationId() == null ? ""
                        : ";conversation=" + belief.conversationId())
                + (belief.responseId() == null ? "" : ";response=" + belief.responseId());
    }

    private static double urgency(String message) {
        String value = normalize(message);
        if (contains(value, "emergency", "danger", "attack", "dying", "hurt", "missing")) {
            return 0.90;
        }
        if (contains(value, "urgent", "right now", "needs", "asked", "wants")) return 0.68;
        return 0.38;
    }

    private static String match(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? compact(matcher.group(1), 180) : "";
    }

    private static boolean contains(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}' ]", " ").replaceAll("\\s+", " ").strip();
    }

    private static String compact(String value, int maximum) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }

    private record Subject(UUID id, String label) { }

    public record Analysis(PlayerInputKind classification,
            List<PlayerFactProposition> propositions, String reason) {
        public Analysis {
            propositions = List.copyOf(propositions == null ? List.of() : propositions);
            reason = reason == null ? "" : reason;
        }
    }

    public record PersistenceResult(Analysis analysis, List<SourcedBelief> beliefWrites,
            List<UUID> memoryWrites, String rejectionReason) {
        public PersistenceResult {
            beliefWrites = List.copyOf(beliefWrites == null ? List.of() : beliefWrites);
            memoryWrites = List.copyOf(memoryWrites == null ? List.of() : memoryWrites);
            rejectionReason = rejectionReason == null ? "" : rejectionReason;
        }
    }
}
