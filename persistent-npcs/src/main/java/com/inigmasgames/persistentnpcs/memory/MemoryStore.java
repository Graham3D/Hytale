package com.inigmasgames.persistentnpcs.memory;

import com.inigmasgames.persistentnpcs.cognition.NpcCognitionService;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Duration;
import java.time.Instant;
import com.inigmasgames.persistentnpcs.epistemic.E5QueryExpansion;

public final class MemoryStore {
    private static final ExecutorService PERSISTENCE = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("immersive-memory-persistence", 0).factory());
    private static final Map<Path, CompletableFuture<Void>> PENDING_WRITES =
            new ConcurrentHashMap<>();
    private static final Set<String> QUERY_STOP_WORDS = Set.of(
            "about", "again", "am", "and", "are", "could", "did", "do", "does", "for", "from",
            "have", "hello", "here", "how", "just", "mara", "please", "said", "that",
            "the", "their", "them", "there", "they", "this", "what", "when", "where",
            "which", "with", "would", "you", "your");
    private final Path path;
    private final int maximumRecords;
    private final List<MemoryRecord> records = new ArrayList<>();
    private final AtomicLong persistenceReads = new AtomicLong();
    private final AtomicLong persistenceWrites = new AtomicLong();
    private volatile RuntimeException persistenceFailure;

    public MemoryStore(Path dataDirectory, int maximumRecords) {
        this.path = dataDirectory.resolve("persistence/memories.json");
        this.maximumRecords = maximumRecords;
    }

    public synchronized void load() {
        awaitPending(path);
        records.clear();
        if (!Files.exists(path)) {
            saveNow(List.of());
            return;
        }
        persistenceReads.incrementAndGet();
        MemoryRecord[] loaded = JsonFiles.read(path, MemoryRecord[].class);
        boolean migrationRequired = loaded != null && Arrays.stream(loaded)
                .anyMatch(record -> record.durability() == null);
        if (loaded != null) {
            Arrays.stream(loaded).map(MemoryRecord::normalized).forEach(records::add);
        }
        migrationRequired = deduplicatePersistedFacts() > 0 || migrationRequired;
        trim();
        if (migrationRequired) scheduleSave();
    }

    public synchronized void append(MemoryRecord record) {
        MemoryRecord normalized = record.normalized();
        MemoryRecord duplicate = duplicateFact(normalized);
        if (duplicate != null) {
            replace(duplicate.recalled(normalized.timestamp(), 0.035));
            scheduleSave();
            return;
        }
        records.add(normalized);
        consolidateRepetitive();
        trim();
        scheduleSave();
    }

    public synchronized List<MemoryRecord> recent(UUID npcId, UUID playerId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return records.stream()
                .filter(record -> record.npcId().equals(npcId)
                        && java.util.Objects.equals(record.playerId(), playerId))
                .sorted(Comparator.comparing(MemoryRecord::timestamp).reversed())
                .limit(limit)
                .sorted(Comparator.comparing(MemoryRecord::timestamp))
                .toList();
    }

    /** Strict lexical retrieval so low-value conversation history is not injected blindly. */
    public synchronized List<MemoryRecord> relevant(
            UUID npcId, UUID playerId, String currentMessage, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Set<String> queryTerms = semanticTerms(currentMessage);
        Instant now = Instant.now();
        return records.stream()
                .filter(record -> record.npcId().equals(npcId)
                        && java.util.Objects.equals(record.playerId(), playerId))
                .filter(MemoryStore::usableForCognition)
                .filter(record -> (record.type() == MemoryType.COMMITMENT
                                || record.type() == MemoryType.RELATIONSHIP
                                || record.type() == MemoryType.PLAYER_FACT)
                                && overlaps(queryTerms, record.summary())
                        || (record.type() != MemoryType.CONVERSATION
                                && overlaps(queryTerms, record.summary()))
                        || (record.type() == MemoryType.CONVERSATION
                                && record.importance() >= 0.5
                                && overlaps(queryTerms, record.summary())))
                .sorted(Comparator.comparingDouble((MemoryRecord record) ->
                        retrievalScore(record, queryTerms, now)).reversed())
                .limit(limit)
                .sorted(Comparator.comparing(MemoryRecord::timestamp))
                .toList();
    }

    /**
     * Bounded cognition retrieval across direct, autonomous, and background experience.
     * Entity affinity, lexical relevance, importance, confidence, and recency are explicit.
     */
    public synchronized List<MemoryRecord> retrieveForCognition(
            UUID npcId, UUID focusEntityId, String query, int limit) {
        return retrieveScoredForCognition(npcId, focusEntityId, query, limit).stream()
                .map(ScoredMemory::memory).toList();
    }

    /** Score-preserving retrieval used by cognition, traces, and the native inspector. */
    public synchronized List<ScoredMemory> retrieveScoredForCognition(
            UUID npcId, UUID focusEntityId, String query, int limit) {
        return retrieveDetailedForCognition(npcId, focusEntityId, query, limit,
                "CALM", 0.0, Instant.now()).selected();
    }

    public synchronized List<ScoredMemory> retrieveScoredForCognition(
            UUID npcId, UUID focusEntityId, String query, int limit,
            String currentEmotion, double currentEmotionalIntensity) {
        return retrieveDetailedForCognition(npcId, focusEntityId, query, limit,
                currentEmotion, currentEmotionalIntensity, Instant.now()).selected();
    }

    /** Clock-explicit overload for deterministic decay/rehearsal validation. */
    public synchronized List<ScoredMemory> retrieveScoredForCognition(
            UUID npcId, UUID focusEntityId, String query, int limit, Instant now) {
        return retrieveDetailedForCognition(npcId, focusEntityId, query, limit,
                "CALM", 0.0, now).selected();
    }

    public synchronized RetrievalResult retrieveDetailedForCognition(
            UUID npcId, UUID focusEntityId, String query, int limit,
            String currentEmotion, double currentEmotionalIntensity) {
        return retrieveDetailedForCognition(npcId, focusEntityId, query, limit,
                currentEmotion, currentEmotionalIntensity, Instant.now());
    }

    private RetrievalResult retrieveDetailedForCognition(
            UUID npcId, UUID focusEntityId, String query, int limit,
            String currentEmotion, double currentEmotionalIntensity, Instant now) {
        if (limit <= 0) return new RetrievalResult(List.of(), List.of());
        Set<String> queryTerms = semanticTerms(query);
        Instant retrievalAt = now == null ? Instant.now() : now;
        boolean autobiographical = autobiographicalQuery(query);
        List<ScoredMemory> scored = records.stream()
                .filter(record -> record.npcId().equals(npcId))
                .filter(MemoryStore::usableForCognition)
                .map(record -> scored(record, focusEntityId, queryTerms, query,
                        currentEmotion, currentEmotionalIntensity,
                        retrievalAt, autobiographical))
                .sorted(Comparator.comparingDouble(ScoredMemory::score).reversed()
                        .thenComparing(entry -> entry.memory().timestamp(),
                                Comparator.reverseOrder()))
                .toList();
        List<ScoredMemory> selected = scored.stream()
                .filter(entry -> passesSemanticGate(entry, autobiographical))
                .limit(limit).toList();
        Set<UUID> selectedIds = selected.stream().map(value -> value.memory().memoryId())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<RejectedMemory> rejected = new ArrayList<>();
        records.stream().filter(record -> record.npcId().equals(npcId))
                .filter(record -> !usableForCognition(record))
                .limit(24).forEach(record -> rejected.add(new RejectedMemory(
                        record.memoryId(), record.type() == MemoryType.CONVERSATION
                                ? "NPC_GENERATED_DIALOGUE_IS_NOT_FACTUAL_EVIDENCE"
                                : "UNUSABLE_PROVENANCE_OR_NONDECLARATIVE_REPORT",
                        0, 0)));
        scored.stream().filter(entry -> !selectedIds.contains(entry.memory().memoryId()))
                .limit(24).forEach(entry -> rejected.add(new RejectedMemory(
                        entry.memory().memoryId(),
                        passesSemanticGate(entry, autobiographical)
                                ? "OUTSIDE_BOUNDED_TOP_" + limit
                                : "SEMANTIC_RELEVANCE_BELOW_GATE",
                        entry.breakdown().semanticRelevance(), entry.score())));
        return new RetrievalResult(reinforceRetrieved(selected, query, retrievalAt),
                List.copyOf(rejected));
    }

    private static boolean passesSemanticGate(ScoredMemory entry, boolean autobiographical) {
        return entry.breakdown().semanticRelevance() >= 0.12
                || autobiographical && entry.breakdown().semanticRelevance() >= 0.06;
    }

    public Path path() {
        return path;
    }

    public synchronized List<MemoryRecord> forNpc(UUID npcId) {
        return records.stream().filter(record -> record.npcId().equals(npcId))
                .sorted(Comparator.comparing(MemoryRecord::timestamp)).toList();
    }

    /**
     * E2 read boundary: query-specific, in-memory, bounded, and deliberately non-reinforcing.
     * Unlike live cognition retrieval, this method cannot mutate rehearsal state or persist.
     */
    public synchronized List<MemoryRecord> queryReadOnly(UUID npcId, UUID focusEntityId,
            String query, Set<MemoryType> allowedTypes, int limit) {
        if (npcId == null || limit <= 0) return List.of();
        Set<MemoryType> types = allowedTypes == null ? Set.of() : Set.copyOf(allowedTypes);
        Set<String> queryTerms = semanticTerms(query);
        boolean identityQuery = queryTerms.contains("name") || queryTerms.contains("identity");
        return records.stream()
                .filter(record -> npcId.equals(record.npcId()))
                .filter(MemoryStore::usableForCognition)
                .filter(record -> types.isEmpty() || types.contains(record.type()))
                .filter(record -> focusEntityId == null
                        || java.util.Objects.equals(focusEntityId, record.playerId())
                        || record.involvedEntities().contains(focusEntityId))
                .filter(record -> identityQuery
                        ? factFingerprint(record.summary()).startsWith("STATED_NAME:")
                        : overlaps(queryTerms, record.summary() + " " + record.location()))
                .sorted(Comparator.comparingDouble((MemoryRecord record) ->
                        retrievalScore(record, queryTerms, Instant.now())).reversed()
                        .thenComparing(MemoryRecord::timestamp, Comparator.reverseOrder()))
                .limit(limit).toList();
    }

    /** Existing-store bridge for E5 open commitments; RAM-only and non-reinforcing. */
    public synchronized List<MemoryRecord> openCommitmentsReadOnly(UUID npcId,
            UUID playerId, int limit) {
        if (npcId == null || limit <= 0) return List.of();
        return records.stream().filter(value -> npcId.equals(value.npcId()))
                .filter(value -> java.util.Objects.equals(playerId, value.playerId()))
                .filter(value -> value.type() == MemoryType.COMMITMENT)
                .filter(value -> value.source() == null
                        || !value.source().equals("E5_CONVERSATION_WORKSPACE_OPEN_TOPIC"))
                .sorted(Comparator.comparing(MemoryRecord::timestamp).reversed())
                .limit(limit).sorted(Comparator.comparing(MemoryRecord::timestamp)).toList();
    }

    public synchronized List<MemoryRecord> openTopicsReadOnly(UUID npcId,
            UUID playerId, int limit) {
        if (npcId == null || limit <= 0) return List.of();
        return records.stream().filter(value -> npcId.equals(value.npcId()))
                .filter(value -> java.util.Objects.equals(playerId, value.playerId()))
                .filter(value -> value.type() == MemoryType.COMMITMENT)
                .filter(value -> "E5_CONVERSATION_WORKSPACE_OPEN_TOPIC"
                        .equals(value.source()))
                .sorted(Comparator.comparing(MemoryRecord::timestamp).reversed())
                .limit(limit).sorted(Comparator.comparing(MemoryRecord::timestamp)).toList();
    }

    /**
     * E5 fact-level RAM retrieval. A session record is decomposed into bounded declarative
     * units and only the matching unit is returned; the full conversation is never promoted.
     */
    public synchronized FactRetrieval queryFactsReadOnly(UUID npcId, UUID focusEntityId,
            String query, Set<MemoryType> allowedTypes, int limit,
            E5QueryExpansion expansion, Instant now) {
        if (npcId == null || limit <= 0) return new FactRetrieval(List.of(), List.of(), 0);
        long readsBefore = persistenceReads.get();
        Instant at = now == null ? Instant.now() : now;
        E5QueryExpansion plan = expansion == null
                ? E5QueryExpansion.expand(query, null, null, at) : expansion;
        Set<String> queryTerms = new java.util.LinkedHashSet<>(semanticTerms(query));
        queryTerms.addAll(plan.terms());
        Set<MemoryType> types = allowedTypes == null ? Set.of() : Set.copyOf(allowedTypes);
        ArrayList<ScoredFact> candidates = new ArrayList<>();
        ArrayList<RejectedFact> rejected = new ArrayList<>();
        for (MemoryRecord record : records) {
            if (!npcId.equals(record.npcId()) || !usableForCognition(record)
                    || !types.isEmpty() && !types.contains(record.type())
                    || focusEntityId != null && !java.util.Objects.equals(
                            focusEntityId, record.playerId())
                            && !record.involvedEntities().contains(focusEntityId)) continue;
            for (FactUnit fact : facts(record)) {
                Set<String> candidateTerms = semanticTerms(fact.statement());
                double exact = exactCoverage(queryTerms, candidateTerms);
                double semantic = semanticOverlap(queryTerms, candidateTerms);
                double temporal = plan.temporalScore(record.timestamp(), at);
                if ((plan.validFrom() != null || plan.validUntil() != null)
                        && !plan.matches(record.timestamp())) {
                    rejected.add(new RejectedFact(fact.factId(), "TEMPORAL_MISMATCH", 0));
                    continue;
                }
                double importance = record.importance();
                double confidence = record.confidence() == null ? .35 : record.confidence();
                double recency = decay(record.durability(), Math.max(0,
                        Duration.between(record.timestamp(), at).toSeconds() / 86_400d));
                double type = switch (record.type()) {
                    case PLAYER_FACT, COMMITMENT, ACTION_RESULT -> 1.0;
                    case EPISODIC, WORLD_EVENT, KNOWLEDGE -> .8;
                    default -> .45;
                };
                double relationship = Math.max(record.relationshipImpact(),
                        focusEntityId != null && (java.util.Objects.equals(focusEntityId,
                                record.playerId()) || record.involvedEntities().contains(
                                        focusEntityId)) ? .5 : 0);
                double emotional = record.emotionalIntensity();
                double goal = record.goalImpact();
                double topic = plan.currentTopic().isBlank() ? 0
                        : semanticOverlap(semanticTerms(plan.currentTopic()), candidateTerms);
                double score = .28 * exact + .22 * semantic + .13 * temporal
                        + .07 * importance + .06 * confidence + .04 * recency
                        + .03 * type + .02 * Math.min(1, record.rehearsalCount() / 5d)
                        + .05 * relationship + .03 * emotional + .04 * goal + .03 * topic;
                FactScore breakdown = new FactScore(exact, semantic, temporal, importance,
                        confidence, recency, type, relationship, emotional, goal, topic, score);
                if (semantic < .16 && exact < .2) {
                    rejected.add(new RejectedFact(fact.factId(),
                            "WEAK_SEMANTIC_MATCH", score));
                } else candidates.add(new ScoredFact(fact, record, score, breakdown));
            }
        }
        candidates.sort(Comparator.comparingDouble(ScoredFact::score).reversed()
                .thenComparing(value -> value.record().timestamp(), Comparator.reverseOrder()));
        if (plan.currentOnly() && !candidates.isEmpty()) {
            Instant latest = candidates.stream().map(value -> value.record().timestamp())
                    .max(Comparator.naturalOrder()).orElse(Instant.EPOCH);
            candidates.removeIf(value -> value.record().timestamp().isBefore(latest));
        }
        List<ScoredFact> selected = candidates.stream().limit(limit).toList();
        if (persistenceReads.get() != readsBefore) throw new IllegalStateException(
                "E5 live retrieval performed persistence I/O");
        return new FactRetrieval(selected, rejected.stream().limit(16).toList(),
                selected.size() + rejected.size());
    }

    private static List<FactUnit> facts(MemoryRecord record) {
        String source = record.summary() == null ? "" : record.summary()
                .replaceAll("\\s+", " ").strip();
        if (source.isBlank()) return List.of();
        String[] pieces = source.split("(?<=[.!?])\\s+|\\s*;\\s*");
        ArrayList<FactUnit> result = new ArrayList<>();
        for (int i = 0; i < pieces.length && result.size() < 8; i++) {
            String fact = pieces[i].strip();
            if (fact.length() < 4) continue;
            if (fact.length() > 240) fact = fact.substring(0, 240);
            UUID id = UUID.nameUUIDFromBytes((record.memoryId() + ":fact:" + i + ":" + fact)
                    .getBytes(StandardCharsets.UTF_8));
            result.add(new FactUnit(id, record.memoryId(), i, fact,
                    record.source(), record.timestamp()));
        }
        return List.copyOf(result);
    }

    private static double exactCoverage(Set<String> query, Set<String> candidate) {
        if (query.isEmpty() || candidate.isEmpty()) return 0;
        long overlap = query.stream().filter(candidate::contains).count();
        return Math.min(1, overlap / (double) query.size());
    }

    public record FactUnit(UUID factId, UUID sourceMemoryId, int ordinal,
            String statement, String sessionSource, Instant occurredAt) { }
    public record FactScore(double exactMatch, double semanticRelevance,
            double temporalCompatibility, double importance, double confidence,
            double recency, double typeCompatibility, double relationshipRelevance,
            double emotionalRelevance, double goalRelevance,
            double currentTopicRelevance, double total) { }
    public record ScoredFact(FactUnit fact, MemoryRecord record, double score,
            FactScore breakdown) { }
    public record RejectedFact(UUID factId, String reason, double score) { }
    public record FactRetrieval(List<ScoredFact> selected, List<RejectedFact> rejected,
            int candidateCount) {
        public FactRetrieval {
            selected = List.copyOf(selected == null ? List.of() : selected);
            rejected = List.copyOf(rejected == null ? List.of() : rejected);
            candidateCount = Math.max(0, candidateCount);
        }
    }

    /** Collapses three or more near-identical low-value dialogue memories. */
    public synchronized int consolidateRepetitive() {
        Map<String, List<MemoryRecord>> groups = records.stream()
                .filter(record -> record.type() == MemoryType.CONVERSATION
                        && record.importance() < 0.6)
                .collect(java.util.stream.Collectors.groupingBy(record ->
                        record.npcId() + ":" + record.playerId() + ":"
                                + terms(record.summary()).stream().sorted()
                                        .collect(java.util.stream.Collectors.joining(","))));
        int consolidated = 0;
        for (List<MemoryRecord> group : groups.values()) {
            if (group.size() < 3) {
                continue;
            }
            group.sort(Comparator.comparing(MemoryRecord::timestamp));
            MemoryRecord latest = group.getLast();
            double importance = Math.min(0.7, group.stream()
                    .mapToDouble(MemoryRecord::importance).max().orElse(0.35) + 0.15);
            records.removeAll(group);
            records.add(new MemoryRecord(UUID.randomUUID(), latest.npcId(),
                    latest.playerId(), latest.timestamp(), MemoryType.EPISODIC, importance,
                    "Consolidated " + group.size() + " similar interactions: "
                            + latest.summary(),
                    latest.confidence(), "CONSOLIDATED", latest.involvedEntities(),
                    latest.location(), latest.npcPerspective()).normalized());
            consolidated += group.size() - 1;
        }
        return consolidated;
    }

    private void trim() {
        if (records.size() <= maximumRecords) {
            return;
        }
        Instant now = Instant.now();
        records.sort(Comparator.comparingDouble(record -> retentionScore(record, now)));
        int remove = records.size() - maximumRecords;
        for (int i = 0; i < records.size() && remove > 0;) {
            if (records.get(i).durability() != MemoryDurability.LANDMARK) {
                records.remove(i);
                remove--;
            } else {
                i++;
            }
        }
        // LANDMARK memories may intentionally exceed the configured soft cap.
    }

    private void scheduleSave() {
        List<MemoryRecord> snapshot = List.copyOf(records);
        Path key = path.toAbsolutePath().normalize();
        PENDING_WRITES.compute(key, (ignored, previous) -> {
            CompletableFuture<Void> predecessor = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous.exceptionally(failure -> null);
            CompletableFuture<Void> next = predecessor.thenRunAsync(
                    () -> saveNow(snapshot), PERSISTENCE);
            next.whenComplete((unused, failure) -> {
                if (failure == null) PENDING_WRITES.remove(key, next);
            });
            return next;
        });
    }

    private void saveNow(List<MemoryRecord> snapshot) {
        try {
            JsonFiles.writeAtomic(path, snapshot);
            persistenceWrites.incrementAndGet();
        } catch (RuntimeException failure) {
            persistenceFailure = failure;
            throw failure;
        }
    }

    public void flush() {
        awaitPending(path);
        if (persistenceFailure != null) throw persistenceFailure;
    }

    public static void flushAll() {
        List.copyOf(PENDING_WRITES.values()).forEach(CompletableFuture::join);
    }

    public long persistenceReadCount() { return persistenceReads.get(); }

    public long persistenceWriteCount() { return persistenceWrites.get(); }

    private static void awaitPending(Path value) {
        CompletableFuture<Void> pending = PENDING_WRITES.get(
                value.toAbsolutePath().normalize());
        if (pending != null) pending.join();
    }

    private static boolean overlaps(Set<String> queryTerms, String candidate) {
        if (queryTerms.isEmpty()) {
            return false;
        }
        Set<String> candidateTerms = semanticTerms(candidate);
        return queryTerms.stream().anyMatch(candidateTerms::contains);
    }

    private static boolean usableForCognition(MemoryRecord record) {
        // NPC-authored wording is conversation history, never factual evidence. Older stores may
        // use DIRECT provenance, so also recognize the legacy combined-turn summary explicitly.
        String summary = record.summary() == null ? "" : record.summary().toLowerCase(Locale.ROOT);
        if (record.type() == MemoryType.CONVERSATION
                && (summary.contains("npc replied:") || summary.contains("npc said:")
                        || summary.contains("npc replied that"))) return false;
        if (record.source() != null
                && record.source().startsWith("CONVERSATION_HISTORY:NPC_GENERATED")) {
            return false;
        }
        if (record.type() != MemoryType.PLAYER_FACT
                || record.source() == null
                || !record.source().startsWith("PLAYER_REPORT:")) return true;
        summary = record.summary() == null ? "" : record.summary();
        String prefix = "Player-reported belief:";
        String proposition = summary.regionMatches(true, 0, prefix, 0, prefix.length())
                ? summary.substring(prefix.length()).strip() : summary;
        return NpcCognitionService.isDeclarativePlayerReport(proposition);
    }

    private static double retrievalScore(
            MemoryRecord record, Set<String> queryTerms, Instant now) {
        double lexical = semanticOverlap(queryTerms, semanticTerms(record.summary()));
        double ageDays = Math.max(0, Duration.between(record.timestamp(), now).toHours() / 24.0);
        double recency = decay(record.durability(), ageDays);
        double entity = record.involvedEntities().isEmpty() ? 0.0 : 0.1;
        return lexical * 0.50 + record.importance() * 0.20
                + record.durability().retrievalWeight() * 0.08
                + recency * 0.07 + record.confidence() * 0.05 + entity;
    }

    private static ScoredMemory scored(MemoryRecord record, UUID focusEntityId,
            Set<String> queryTerms, String query, String currentEmotion,
            double currentEmotionalIntensity, Instant now, boolean autobiographical) {
        Set<String> candidateTerms = semanticTerms(record.summary() + " "
                + record.location() + " " + record.npcPerspective());
        double semantic = semanticOverlap(queryTerms, candidateTerms);
        boolean directEntity = focusEntityId != null
                && (java.util.Objects.equals(record.playerId(), focusEntityId)
                || record.involvedEntities().contains(focusEntityId));
        double ageDays = Math.max(0,
                Duration.between(record.timestamp(), now).toMinutes() / 1440.0);
        double recency = decay(record.durability(), ageDays);
        double typeAdjustment = switch (record.type()) {
            case PLAYER_FACT -> 0.12;
            case EPISODIC, COMMITMENT -> 0.08;
            case RELATIONSHIP -> 0.05;
            case ACTION_RESULT -> autobiographical && semantic < 0.50 ? -0.18 : 0.0;
            default -> 0.0;
        };
        double temporal = temporalCompatibility(query, record);
        double emotional = emotionalRelevance(query, currentEmotion,
                currentEmotionalIntensity, record);
        double goal = goalRelevance(query, record);
        double rehearsal = Math.min(1.0,
                Math.log1p(record.rehearsalCount()) / Math.log(8.0));
        boolean relationshipQuery = relationshipQuery(query);
        double relationship = relationshipQuery && directEntity
                ? Math.max(0.35, record.relationshipImpact())
                : record.relationshipImpact() * (relationshipQuery ? 1.0 : 0.10);
        double total = semantic * 0.56 + record.importance() * 0.09
                + record.durability().retrievalWeight() * 0.08
                + relationship * 0.07 + emotional * 0.05 + goal * 0.04
                + recency * 0.05 + record.confidence() * 0.05
                + rehearsal * 0.03 + temporal * 0.02 + typeAdjustment;
        RetrievalScoreBreakdown breakdown = new RetrievalScoreBreakdown(semantic,
                record.importance(), record.durability().retrievalWeight(), relationship,
                emotional, goal, recency, record.confidence(), rehearsal, temporal,
                typeAdjustment, total);
        return new ScoredMemory(record, total, breakdown);
    }

    private static double retentionScore(MemoryRecord record, Instant now) {
        if (record.durability() == MemoryDurability.LANDMARK) {
            return Double.POSITIVE_INFINITY;
        }
        double ageDays = Math.max(0, Duration.between(record.timestamp(), now).toHours() / 24.0);
        double decay = decay(record.durability(), ageDays);
        double rehearsal = Math.min(0.15, record.rehearsalCount() * 0.015);
        return record.importance() * decay
                + record.durability().retrievalWeight() * 0.15
                + record.confidence() * 0.1 + rehearsal;
    }

    private List<ScoredMemory> reinforceRetrieved(List<ScoredMemory> selected,
            String query, Instant now) {
        if (selected.isEmpty() || query == null || query.isBlank()) return selected;
        List<ScoredMemory> result = new ArrayList<>(selected.size());
        boolean changed = false;
        for (ScoredMemory scored : selected) {
            MemoryRecord memory = scored.memory();
            boolean meaningful = scored.breakdown().semanticRelevance() >= 0.34;
            boolean cooledDown = memory.lastRecalledAt() == null
                    || Duration.between(memory.lastRecalledAt(), now).toSeconds() >= 30;
            if (meaningful && cooledDown) {
                MemoryRecord reinforced = memory.recalled(now,
                        0.015 + scored.breakdown().semanticRelevance() * 0.025);
                replace(reinforced);
                result.add(new ScoredMemory(reinforced, scored.score(), scored.breakdown()));
                changed = true;
            } else {
                result.add(scored);
            }
        }
        if (changed) scheduleSave();
        return List.copyOf(result);
    }

    public synchronized boolean reinforce(UUID memoryId, Instant at, double amount) {
        for (int i = 0; i < records.size(); i++) {
            MemoryRecord record = records.get(i);
            if (!record.memoryId().equals(memoryId)) continue;
            records.set(i, record.recalled(at, amount));
            scheduleSave();
            return true;
        }
        return false;
    }

    /** Reflection/discussion hook: reinforce the best matching record instead of duplicating it. */
    public synchronized boolean reinforceSimilar(UUID npcId, MemoryType type,
            String summary, Instant at, double minimumSemanticOverlap) {
        Set<String> query = semanticTerms(summary);
        MemoryRecord best = null;
        double bestScore = 0;
        for (MemoryRecord record : records) {
            if (!record.npcId().equals(npcId) || record.type() != type) continue;
            double score = semanticOverlap(query, semanticTerms(record.summary()));
            if (score > bestScore) {
                best = record;
                bestScore = score;
            }
        }
        return best != null && bestScore >= minimumSemanticOverlap
                && reinforce(best.memoryId(), at, 0.04 + bestScore * 0.03);
    }

    private void replace(MemoryRecord replacement) {
        for (int i = 0; i < records.size(); i++) {
            if (records.get(i).memoryId().equals(replacement.memoryId())) {
                records.set(i, replacement);
                return;
            }
        }
    }

    private MemoryRecord duplicateFact(MemoryRecord candidate) {
        if (candidate.type() != MemoryType.PLAYER_FACT) return null;
        Set<String> candidateTerms = semanticTerms(candidate.summary());
        String candidateFingerprint = factFingerprint(candidate.summary());
        return records.stream()
                .filter(existing -> existing.type() == MemoryType.PLAYER_FACT)
                .filter(existing -> existing.npcId().equals(candidate.npcId()))
                .filter(existing -> java.util.Objects.equals(
                        existing.playerId(), candidate.playerId()))
                .filter(existing -> !candidateFingerprint.isBlank()
                        && candidateFingerprint.equals(factFingerprint(existing.summary()))
                        || semanticOverlap(candidateTerms,
                                semanticTerms(existing.summary())) >= 0.90)
                .max(Comparator.comparing(MemoryRecord::timestamp)).orElse(null);
    }

    private int deduplicatePersistedFacts() {
        int removed = 0;
        Map<String, MemoryRecord> seen = new java.util.LinkedHashMap<>();
        for (MemoryRecord record : List.copyOf(records)) {
            if (record.type() != MemoryType.PLAYER_FACT) continue;
            String fingerprint = factFingerprint(record.summary());
            if (fingerprint.isBlank()) continue;
            String key = record.npcId() + ":" + record.playerId() + ":" + fingerprint;
            MemoryRecord existing = seen.get(key);
            if (existing == null) {
                seen.put(key, record);
                continue;
            }
            MemoryRecord reinforced = existing.recalled(record.timestamp(), 0.025);
            records.remove(existing);
            records.remove(record);
            records.add(reinforced);
            seen.put(key, reinforced);
            removed++;
        }
        return removed;
    }

    private static String factFingerprint(String summary) {
        String value = summary == null ? "" : summary.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9'= ]", " ").replaceAll("\\s+", " ").strip();
        java.util.regex.Matcher name = java.util.regex.Pattern.compile(
                "(?:name(?: is|=)|stated name=)\\s*([a-z][a-z'-]{0,31})").matcher(value);
        if (name.find()) return "STATED_NAME:" + name.group(1);
        return "";
    }

    private static boolean relationshipQuery(String query) {
        String value = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return value.matches(".*\\b(?:relationship|related|family|friend|know|trust|"
                + "love|hate|grandfather|grandmother|father|mother|sister|brother)\\b.*");
    }

    private static Set<String> terms(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(word -> word.length() >= 3 && !QUERY_STOP_WORDS.contains(word))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Set<String> semanticTerms(String text) {
        return terms(text).stream().map(MemoryStore::canonicalTerm)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static double semanticOverlap(Set<String> query, Set<String> candidate) {
        if (query == null || query.isEmpty() || candidate == null || candidate.isEmpty()) {
            return 0.0;
        }
        long overlap = query.stream().filter(candidate::contains).count();
        return Math.min(1.0, overlap / (double) Math.max(1,
                Math.min(query.size(), candidate.size())));
    }

    private static double decay(MemoryDurability durability, double ageDays) {
        MemoryDurability tier = durability == null ? MemoryDurability.NORMAL : durability;
        if (tier == MemoryDurability.LANDMARK) return 1.0;
        return Math.pow(0.5, Math.max(0, ageDays) / tier.decayHalfLifeDays());
    }

    private static double emotionalRelevance(String query, String currentEmotion,
            double currentIntensity, MemoryRecord record) {
        String value = query == null ? "" : query.toLowerCase(Locale.ROOT);
        boolean emotionalQuery = value.matches(".*\\b(feel|felt|love|grief|sad|happy|"
                + "angry|afraid|fear|regret|proud|trauma|betray|family)\\w*\\b.*");
        String emotion = currentEmotion == null ? "CALM"
                : currentEmotion.toUpperCase(Locale.ROOT);
        boolean negativeEmotion = Set.of("SAD", "UNEASY", "ANGRY", "AFRAID",
                "SUSPICIOUS").contains(emotion);
        boolean positiveEmotion = Set.of("EXCITED", "FRIENDLY", "AMUSED", "TENDER")
                .contains(emotion);
        double affectMatch = negativeEmotion && record.emotionalValence() < 0
                || positiveEmotion && record.emotionalValence() > 0
                        ? record.emotionalIntensity() * Math.max(0, Math.min(1, currentIntensity))
                        : record.emotionalIntensity() * 0.20;
        return emotionalQuery ? Math.max(record.emotionalIntensity(), affectMatch) : affectMatch;
    }

    private static double goalRelevance(String query, MemoryRecord record) {
        String value = query == null ? "" : query.toLowerCase(Locale.ROOT);
        boolean goalQuery = value.matches(".*\\b(goal|quest|task|mission|plan|promise|"
                + "work|craft|deliver|find|rescue)\\w*\\b.*");
        return record.goalImpact() * (goalQuery ? 1.0 : 0.25);
    }

    private static String canonicalTerm(String term) {
        return switch (term) {
            case "hid", "hide", "hidden", "stashed", "stash", "buried", "bury",
                    "concealed", "conceal" -> "conceal";
            case "placed", "put", "stored", "store", "kept", "keep", "left" -> "place";
            case "object", "item", "thing", "belonging" -> "item";
            case "location", "spot", "where" -> "location";
            case "recalled", "recall", "remembered", "remember", "told" -> "memory";
            default -> term.endsWith("ed") && term.length() > 5
                    ? term.substring(0, term.length() - 2) : term;
        };
    }

    private static boolean autobiographicalQuery(String query) {
        String value = query == null ? "" : " " + query.toLowerCase(Locale.ROOT) + " ";
        return (value.contains(" i ") || value.contains(" my "))
                && (value.contains("remember") || value.contains("tell")
                        || value.contains("where") || value.contains("what")
                        || value.contains("did") || value.contains("put")
                        || value.contains("hide") || value.contains("stash"));
    }

    private static double temporalCompatibility(String query, MemoryRecord record) {
        String value = query == null ? "" : query.toLowerCase(Locale.ROOT);
        boolean temporalQuery = value.matches(
                ".*\\b(yesterday|today|last|earlier|ago|when)\\b.*");
        if (!temporalQuery) return 0.5;
        String candidate = (record.summary() + " " + record.source())
                .toLowerCase(Locale.ROOT);
        for (String term : List.of("yesterday", "today", "last", "earlier", "ago")) {
            if (value.contains(term) && candidate.contains(term)) return 1.0;
        }
        return 0.2;
    }

    public record RetrievalScoreBreakdown(
            double semanticRelevance,
            double importance,
            double durability,
            double relationshipRelevance,
            double emotionalRelevance,
            double goalRelevance,
            double recency,
            double confidence,
            double rehearsal,
            double temporalCompatibility,
            double typeAdjustment,
            double total) { }

    public record ScoredMemory(MemoryRecord memory, double score,
            RetrievalScoreBreakdown breakdown) {
        public ScoredMemory(MemoryRecord memory, double score) {
            this(memory, score, new RetrievalScoreBreakdown(0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, score));
        }
    }

    public record RejectedMemory(UUID memoryId, String reason,
            double semanticRelevance, double score) { }

    public record RetrievalResult(List<ScoredMemory> selected,
            List<RejectedMemory> rejected) {
        public RetrievalResult {
            selected = List.copyOf(selected == null ? List.of() : selected);
            rejected = List.copyOf(rejected == null ? List.of() : rejected);
        }
    }
}
