package com.inigmasgames.persistentnpcs.epistemic;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;

/** Bounded discourse state; durable commitments are bridged through the existing MemoryStore. */
public final class ConversationWorkspace {
    private static final int MAX_ENTITIES = 8;
    private static final int MAX_COMMITMENTS = 4;
    private static final int MAX_OPEN_TOPICS = 4;
    private static final Duration TTL = Duration.ofMinutes(5);
    private final ArrayDeque<String> activeEntities = new ArrayDeque<>();
    private final ArrayDeque<String> commitments = new ArrayDeque<>();
    private final ArrayDeque<String> openTopics = new ArrayDeque<>();
    private String currentTopic = "", suspendedPriorTopic = "", currentObject = "", lastPlayerClaim = "";
    private String lastNpcClaim = "", unresolvedQuestion = "", activeActionRequest = "";
    private String recentCorrection = "";
    private Instant lastUpdated = Instant.EPOCH;

    public synchronized Snapshot snapshot(Instant now) {
        expire(now);
        return new Snapshot(currentTopic, suspendedPriorTopic, currentObject,
                List.copyOf(activeEntities),
                lastPlayerClaim, lastNpcClaim, unresolvedQuestion, activeActionRequest,
                recentCorrection, List.copyOf(commitments), List.copyOf(openTopics), lastUpdated);
    }

    public synchronized void observePlayer(DialogueFrame frame, String utterance, Instant now) {
        expire(now); lastUpdated = safe(now);
        if (frame == null) return;
        addEntity(frame.subjectKey()); addEntity(frame.targetKey()); addEntity(frame.objectKey());
        if (!frame.objectKey().isBlank()) currentObject = frame.objectKey();
        if (!frame.predicateKey().isBlank() && !frame.predicateKey().equals(currentTopic)) {
            suspendedPriorTopic = currentTopic;
            currentTopic = frame.predicateKey();
        }
        if (frame.act() == DialogueAct.INFORMATION_STATEMENT
                || frame.act() == DialogueAct.CORRECTION
                || frame.act() == DialogueAct.SELF_DISCLOSURE) {
            lastPlayerClaim = compact(utterance, 400);
        }
        if (frame.act() == DialogueAct.CORRECTION) recentCorrection = compact(utterance, 300);
        if (frame.act() == DialogueAct.ACTION_REQUEST) activeActionRequest = frame.requestedAction();
        if (commitmentLanguage(utterance)) addCommitment(utterance, now);
        if (deferredTopicLanguage(utterance)) addOpenTopic(utterance, now);
        if (frame.expectedAnswer() != ExpectedAnswerKind.ACKNOWLEDGEMENT
                && frame.act() != DialogueAct.INFORMATION_STATEMENT) {
            unresolvedQuestion = compact(utterance, 400);
        }
    }

    public synchronized void observeDelivered(String reply, Instant now) {
        expire(now); lastUpdated = safe(now); lastNpcClaim = compact(reply, 600);
        unresolvedQuestion = "";
    }

    /** Carries a compact entity referent forward without retaining raw perception. */
    public synchronized void observeEvidence(EvidencePacket packet, Instant now) {
        expire(now);
        if (packet == null) return;
        for (EvidenceRef value : packet.supporting()) {
            if (value == null || value.objectValue().isBlank()
                    || value.objectValue().equalsIgnoreCase("NONE")) continue;
            if (value.predicateKey().equals("HELD_ITEM")) {
                currentObject = "OBJECT:" + key(value.objectValue());
                addEntity(currentObject);
                lastUpdated = safe(now);
                return;
            }
            if (value.subjectKey().startsWith("OBJECT:")) {
                currentObject = value.subjectKey();
                addEntity(currentObject);
                lastUpdated = safe(now);
                return;
            }
            if (value.predicateKey().equals("PAST_EVENT")
                    && !value.objectValue().isBlank()) {
                String object = salientObject(value.objectValue());
                if (!object.isBlank()) {
                    currentObject = "OBJECT:" + key(object);
                    addEntity(currentObject);
                    lastUpdated = safe(now);
                    return;
                }
            }
        }
    }

    public synchronized void addCommitment(String value, Instant now) {
        expire(now); value = compact(value, 240); if (value.isBlank()) return;
        commitments.addLast(value);
        while (commitments.size() > MAX_COMMITMENTS) commitments.removeFirst();
        lastUpdated = safe(now);
    }

    public synchronized void restoreCommitments(List<String> values, Instant now) {
        if (values == null) return;
        for (String value : values) addCommitment(value, now);
    }

    public synchronized void addOpenTopic(String value, Instant now) {
        expire(now); value = compact(value, 240); if (value.isBlank()) return;
        openTopics.remove(value); openTopics.addLast(value);
        while (openTopics.size() > MAX_OPEN_TOPICS) openTopics.removeFirst();
        lastUpdated = safe(now);
    }

    public synchronized void restoreOpenTopics(List<String> values, Instant now) {
        if (values == null) return;
        for (String value : values) addOpenTopic(value, now);
    }

    private void addEntity(String entity) {
        if (entity == null || entity.isBlank()) return;
        LinkedHashSet<String> values = new LinkedHashSet<>(activeEntities);
        values.remove(entity); values.add(entity); activeEntities.clear(); activeEntities.addAll(values);
        while (activeEntities.size() > MAX_ENTITIES) activeEntities.removeFirst();
    }

    private void expire(Instant now) {
        Instant at = safe(now);
        if (lastUpdated.equals(Instant.EPOCH) || at.isBefore(lastUpdated.plus(TTL))) return;
        activeEntities.clear(); currentTopic = ""; suspendedPriorTopic = ""; currentObject = "";
        lastPlayerClaim = ""; lastNpcClaim = ""; unresolvedQuestion = "";
        activeActionRequest = ""; recentCorrection = ""; lastUpdated = Instant.EPOCH;
    }

    private static Instant safe(Instant now) { return now == null ? Instant.now() : now; }
    private static String compact(String value, int maximum) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }
    private static String key(String value) {
        return value == null ? "" : value.toUpperCase(java.util.Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "_").replaceAll("^_+|_+$", "");
    }

    private static boolean commitmentLanguage(String value) {
        String text = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
        return text.matches(".*\\b(?:meet me|we(?:'ll| will)|i(?:'ll| will)|promise|"
                + "remember to|come back|see you)\\b.*\\b(?:tonight|tomorrow|later|next|"
                + "meet|bring|return|help|show|give|tell)\\b.*");
    }
    private static boolean deferredTopicLanguage(String value) {
        String text = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
        return text.matches(".*\\b(?:talk|discuss|ask|tell|explain|return to|come back to)\\b"
                + ".*\\b(?:later|tomorrow|tonight|next time|another time)\\b.*");
    }

    private static String salientObject(String value) {
        String text = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
        for (String candidate : List.of("rock", "stone", "lantern", "dagger", "sword",
                "letter", "ring", "key", "book", "map", "package")) {
            if (text.matches(".*\\b" + candidate + "\\b.*")) return candidate;
        }
        String normalized = text.replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
        return normalized.split(" ").length == 1 ? normalized : "";
    }

    public record Snapshot(String currentTopic, String suspendedPriorTopic, String currentObject,
            List<String> activeEntities, String lastPlayerClaim, String lastNpcClaim,
            String unresolvedQuestion, String activeActionRequest, String recentCorrection,
            List<String> commitments, List<String> openTopics, Instant lastUpdated) { }
}
