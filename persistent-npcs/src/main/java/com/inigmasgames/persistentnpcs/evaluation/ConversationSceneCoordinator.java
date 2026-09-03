package com.inigmasgames.persistentnpcs.evaluation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Evaluation-only floor/loop adapter; cognition remains owned by the supplied real host. */
public final class ConversationSceneCoordinator {
    private final int maximumTurns;
    private final int maximumRepeatedCanonical;
    private final double minimumNovelty;

    public ConversationSceneCoordinator(int maximumTurns, int maximumRepeatedCanonical,
            double minimumNovelty) {
        this.maximumTurns = Math.max(2, Math.min(32, maximumTurns));
        this.maximumRepeatedCanonical = Math.max(1, maximumRepeatedCanonical);
        this.minimumNovelty = Math.max(0, Math.min(1, minimumNovelty));
    }

    public CompletableFuture<SceneReport> run(SceneSeed seed, NpcTurnExecutor executor) {
        return CompletableFuture.supplyAsync(() -> execute(seed, executor));
    }

    private SceneReport execute(SceneSeed seed, NpcTurnExecutor executor) {
        if (seed == null || seed.speakerId().equals(seed.listenerId())) {
            throw new IllegalArgumentException("two distinct scene actors required");
        }
        ArrayList<SceneTurn> turns = new ArrayList<>();
        Map<UUID, ArrayList<String>> privateTranscripts = new LinkedHashMap<>();
        privateTranscripts.put(seed.speakerId(), new ArrayList<>());
        privateTranscripts.put(seed.listenerId(), new ArrayList<>());
        HashMap<String, Integer> repetitions = new HashMap<>();
        UUID speaker = seed.speakerId(), listener = seed.listenerId();
        String utterance = seed.utterance();
        String terminal = "MAX_TURNS";
        for (int index = 0; index < maximumTurns; index++) {
            UUID floorOwner = listener; // the addressed recipient owns the response floor.
            if (privateTranscripts.get(listener) == null) throw new IllegalStateException(
                    "private context crossed scene boundary");
            CanonicalTurn reply = executor.execute(index, speaker, listener, utterance).join();
            if (!reply.npcId().equals(listener) || reply.canonicalText().isBlank()) {
                terminal = "INVALID_RESPONSE_OWNER"; break;
            }
            String normalized = normalize(reply.canonicalText());
            int repeated = repetitions.merge(normalized, 1, Integer::sum);
            double novelty = novelty(normalized, turns.isEmpty() ? ""
                    : normalize(turns.getLast().canonicalText()));
            SceneTurn turn = new SceneTurn(index, speaker, listener, floorOwner, utterance,
                    reply.canonicalText(), reply.responseId(), novelty,
                    reply.authorizedTestimonyDelivered());
            turns.add(turn);
            privateTranscripts.get(speaker).add("SELF: " + utterance);
            privateTranscripts.get(listener).add("HEARD " + speaker + ": " + utterance);
            privateTranscripts.get(listener).add("SELF: " + reply.canonicalText());
            privateTranscripts.get(speaker).add("HEARD " + listener + ": "
                    + reply.canonicalText());
            if (repeated > maximumRepeatedCanonical) { terminal = "REPETITION_GUARD"; break; }
            if (index > 1 && novelty < minimumNovelty) { terminal = "NOVELTY_GUARD"; break; }
            utterance = reply.canonicalText(); UUID prior = speaker; speaker = listener;
            listener = prior;
        }
        Map<UUID, List<String>> immutable = new LinkedHashMap<>();
        privateTranscripts.forEach((id, lines) -> immutable.put(id, List.copyOf(lines)));
        boolean oneOwner = turns.stream().allMatch(value -> value.floorOwner().equals(
                value.listenerId()));
        boolean bounded = turns.size() <= maximumTurns;
        return new SceneReport(List.copyOf(turns), Map.copyOf(immutable), terminal,
                oneOwner, bounded, turns.stream().filter(
                        SceneTurn::authorizedTestimonyDelivered).count());
    }

    private static double novelty(String current, String previous) {
        if (previous.isBlank()) return 1;
        Set<String> a = new LinkedHashSet<>(List.of(current.split(" ")));
        Set<String> b = new LinkedHashSet<>(List.of(previous.split(" ")));
        Set<String> intersection = new LinkedHashSet<>(a); intersection.retainAll(b);
        Set<String> union = new LinkedHashSet<>(a); union.addAll(b);
        return union.isEmpty() ? 0 : 1d - intersection.size() / (double) union.size();
    }
    private static String normalize(String text) { return text.toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N} ]", " ").replaceAll("\\s+", " ").strip(); }

    @FunctionalInterface public interface NpcTurnExecutor {
        CompletableFuture<CanonicalTurn> execute(int index, UUID speakerId, UUID listenerId,
                String utterance);
    }
    public record SceneSeed(UUID speakerId, UUID listenerId, String utterance) { }
    public record CanonicalTurn(UUID npcId, UUID responseId, String canonicalText,
            boolean authorizedTestimonyDelivered) { }
    public record SceneTurn(int index, UUID speakerId, UUID listenerId, UUID floorOwner,
            String input, String canonicalText, UUID responseId, double novelty,
            boolean authorizedTestimonyDelivered) { }
    public record SceneReport(List<SceneTurn> turns, Map<UUID, List<String>> privateTranscripts,
            String terminalReason, boolean singleFloorOwnerPerTurn, boolean bounded,
            long authorizedTestimonyCount) { }
}
