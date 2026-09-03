package com.inigmasgames.persistentnpcs.cognition;

import com.inigmasgames.persistentnpcs.action.NpcActionRequest;
import com.inigmasgames.persistentnpcs.action.NpcActionResult;
import com.inigmasgames.persistentnpcs.epistemic.*;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.perception.RawPerceptionSnapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Existing sourced-belief owner upgraded to an E4 event log plus RAM materialized view. */
public final class SourcedBeliefStore implements AutoCloseable {
    private static final int MAX_WRITES = 256;
    private static final int SNAPSHOT_INTERVAL = 32;
    private final Path legacyPath;
    private final Path eventPath;
    private final Path snapshotPath;
    private final List<SourcedBelief> legacy = new ArrayList<>();
    private final List<BeliefEvent> history = new ArrayList<>();
    private final Map<UUID, BeliefEvent> eventsById = new LinkedHashMap<>();
    private final Map<UUID, BeliefAssertion> assertions = new LinkedHashMap<>();
    private final Set<UUID> readOnlyScopes = new HashSet<>();
    private final ArrayBlockingQueue<BeliefEvent> writes = new ArrayBlockingQueue<>(MAX_WRITES);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread writer;
    private long nextSequence = 1;
    private long snapshotSequence;
    private int writesSinceSnapshot;
    private volatile RuntimeException writerFailure;
    private volatile RestorationStats restorationStats = RestorationStats.empty();
    private volatile com.inigmasgames.persistentnpcs.sentinel.OrbisDegradationSentinel sentinel;

    public SourcedBeliefStore(Path dataDirectory) {
        Path root = dataDirectory.resolve("persistence");
        legacyPath = root.resolve("sourced-beliefs.json");
        eventPath = root.resolve("belief-events-v1.jsonl");
        snapshotPath = root.resolve("belief-snapshot-v1.json");
        writer = new Thread(this::writeLoop, "orbis-belief-event-writer");
        writer.setDaemon(true);
        writer.start();
    }

    public synchronized void load() {
        long restoreStarted = System.nanoTime();
        legacy.clear(); history.clear(); eventsById.clear(); assertions.clear();
        nextSequence = 1; snapshotSequence = 0;
        if (Files.isRegularFile(legacyPath)) {
            SourcedBelief[] values = JsonFiles.read(legacyPath, SourcedBelief[].class);
            if (values != null) Arrays.stream(values).map(SourcedBelief::normalized)
                    .forEach(legacy::add);
        }
        loadSnapshot();
        loadEvents();
        // Additive migration. Legacy storage remains for rollback and is never deleted.
        for (SourcedBelief value : List.copyOf(legacy)) {
            if (current(value.npcId(), value.subjectEntityId(), value.predicate()).stream()
                    .anyMatch(existing -> existing.statement().equalsIgnoreCase(
                            value.proposition()))) continue;
            assertInternal(fromLegacy(value), false);
        }
        restorationStats = new RestorationStats(snapshotSequence > 0,
                snapshotSequence, history.size(), Math.max(0, nextSequence - snapshotSequence - 1),
                Duration.ofNanos(System.nanoTime() - restoreStarted).toMillis());
    }

    /** Backward-compatible player/NPC testimony entrypoint. */
    public synchronized SourcedBelief append(SourcedBelief belief) {
        SourcedBelief value = belief.normalized();
        BeliefAssertion assertion = assertInternal(fromLegacy(value), true);
        if (legacy.stream().noneMatch(existing -> existing.beliefId().equals(value.beliefId()))) {
            legacy.add(value);
        }
        return new SourcedBelief(assertion.assertionId(), assertion.ownerNpcId(),
                value.sourceEntityId(), assertion.subjectId(), assertion.subject(),
                assertion.predicate(), assertion.value(), value.semanticLocation(),
                value.temporalReference(), assertion.statement(), assertion.learnedAt(),
                assertion.confidence(), value.urgency(), value.conversationId(),
                value.responseId(), value.utteranceId(),
                assertion.provenance().evidenceIds()).normalized();
    }

    public synchronized BeliefAssertion assertBelief(BeliefProposal proposal) {
        return assertInternal(proposal, true);
    }

    private BeliefAssertion assertInternal(BeliefProposal raw, boolean enforce) {
        if (raw == null || raw.ownerNpcId() == null || raw.provenance() == null) {
            throw new IllegalArgumentException("complete belief proposal required");
        }
        if (readOnlyScopes.contains(raw.ownerNpcId())) throw new IllegalStateException(
                "Persistence is read-only for this NPC after repeated integrity failures");
        Instant now = raw.learnedAt() == null ? Instant.now() : raw.learnedAt();
        String predicate = BeliefPredicateRegistry.canonical(raw.predicate());
        BeliefPredicateRegistry.Definition definition =
                BeliefPredicateRegistry.definition(predicate);
        BeliefAssertion.TemporalScope temporal = raw.temporalScope();
        if (temporal == null) temporal = new BeliefAssertion.TemporalScope(
                definition.stability(), now, definition.defaultTtl() == null ? null
                        : now.plus(definition.defaultTtl()), "");
        BeliefAssertion incoming = new BeliefAssertion(
                raw.assertionId() == null ? UUID.randomUUID() : raw.assertionId(),
                raw.ownerNpcId(), raw.subjectId(), raw.subject(), predicate, raw.value(),
                raw.statement(), raw.polarity(), status(raw), raw.confidence(),
                raw.provenance(), temporal, raw.assertionScope(), raw.supportIds(), List.of(),
                1, now, now);
        validateSocialMutation(incoming);
        validateReflectionMutation(incoming);
        // Validate the untrusted proposal before any supersede/contradiction side effect.
        if (enforce) gate(incoming, signed(new BeliefEvent(1, nextSequence,
                UUID.randomUUID(), BeliefEvent.EventType.BELIEF_ASSERTED, incoming,
                List.of(), now, "")), null, true);
        List<BeliefAssertion> active = current(incoming.ownerNpcId(), incoming.subjectId(),
                incoming.predicate());
        BeliefAssertion proposed = incoming;
        BeliefAssertion same = active.stream().filter(value ->
                value.value().equalsIgnoreCase(proposed.value())
                        && value.polarity() == proposed.polarity()).findFirst().orElse(null);
        if (same != null) {
            BeliefAssertion reinforced = copy(same, same.status(),
                    Math.max(same.confidence(), incoming.confidence()), same.revision() + 1,
                    now, same.conflictIds(), union(same.supportIds(), incoming.supportIds()));
            commit(newEvent(BeliefEvent.EventType.BELIEF_REINFORCED, reinforced,
                    List.of(same.assertionId())), enforce, same);
            return reinforced;
        }
        if (!active.isEmpty()) {
            BeliefAssertion strongest = active.stream().max(Comparator.comparingInt(
                    value -> value.provenance().authorityRank())).orElseThrow();
            boolean correction = sameActorTestimony(strongest, incoming);
            boolean volatileRefresh = definition.stability()
                    == BeliefPredicateRegistry.Stability.VOLATILE
                    && incoming.provenance().authorityRank()
                            >= strongest.provenance().authorityRank()
                    && incoming.provenance().sourceKind() != EvidenceSourceKind.PLAYER_TESTIMONY
                    && incoming.provenance().sourceKind() != EvidenceSourceKind.NPC_TESTIMONY
                    && !incoming.learnedAt().isBefore(strongest.lastConfirmedAt());
            int authority = Integer.compare(incoming.provenance().authorityRank(),
                    strongest.provenance().authorityRank());
            if (correction || volatileRefresh || authority > 0) {
                BeliefAssertion old = copy(strongest, EpistemicStatus.SUPERSEDED,
                        strongest.confidence(), strongest.revision() + 1, now,
                        union(strongest.conflictIds(), List.of(incoming.assertionId())),
                        strongest.supportIds());
                commit(newEvent(BeliefEvent.EventType.BELIEF_SUPERSEDED, old,
                        List.of(incoming.assertionId())), enforce, strongest);
                incoming = conflict(incoming, incoming.status(), strongest.assertionId());
            } else if (authority == 0) {
                BeliefAssertion old = copy(strongest, EpistemicStatus.DISPUTED,
                        strongest.confidence(), strongest.revision() + 1, now,
                        union(strongest.conflictIds(), List.of(incoming.assertionId())),
                        strongest.supportIds());
                commit(newEvent(BeliefEvent.EventType.BELIEF_CONTRADICTED, old,
                        List.of(incoming.assertionId())), enforce, strongest);
                incoming = conflict(incoming, EpistemicStatus.DISPUTED,
                        strongest.assertionId());
            } else incoming = conflict(incoming, EpistemicStatus.DISPUTED,
                    strongest.assertionId());
        }
        BeliefEvent.EventType assertedType = incoming.status() == EpistemicStatus.DISPUTED
                ? BeliefEvent.EventType.BELIEF_CONTRADICTED
                : incoming.provenance().sourceKind() == EvidenceSourceKind.DERIVED_REFLECTION
                        ? BeliefEvent.EventType.BELIEF_DERIVED
                        : incoming.provenance().sourceKind() == EvidenceSourceKind.NPC_TESTIMONY
                                ? BeliefEvent.EventType.BELIEF_SHARED
                                : BeliefEvent.EventType.BELIEF_ASSERTED;
        commit(newEvent(assertedType,
                incoming, incoming.conflictIds()), enforce, null);
        return incoming;
    }

    private static void validateSocialMutation(BeliefAssertion value) {
        boolean e6Social = value.provenance().evidenceIds().stream().anyMatch(id ->
                id.startsWith("SOCIAL_") || id.startsWith("CANONICAL_DELIVERY:"));
        if (value.provenance().sourceKind() == EvidenceSourceKind.NPC_TESTIMONY && e6Social) {
            boolean chain = value.provenance().evidenceIds().stream()
                    .anyMatch(id -> id.startsWith("SOCIAL_CHAIN:"));
            boolean depth = value.provenance().evidenceIds().stream()
                    .anyMatch(id -> id.matches("SOCIAL_DEPTH:[12]"));
            boolean delivery = value.provenance().evidenceIds().stream()
                    .anyMatch(id -> id.startsWith("CANONICAL_DELIVERY:"));
            if (!chain || !depth || !delivery || value.status() == EpistemicStatus.KNOWN) {
                throw new IllegalArgumentException(
                        "NPC testimony requires delivered chain/depth and cannot be world truth");
            }
        }
        if (value.predicate().startsWith("BELIEVES_ACTOR_")
                && (value.supportIds().isEmpty() || value.provenance().evidenceIds().stream()
                        .noneMatch(id -> id.matches("SOCIAL_DEPTH:[12]")))) {
            throw new IllegalArgumentException("invalid nested-belief reference");
        }
        if (value.predicate().equals("SECRET_METADATA")
                && (value.supportIds().isEmpty()
                        || value.provenance().sourceKind() != EvidenceSourceKind.AUTHORED_CANON
                        || value.provenance().generatedSpeechOnly())) {
            throw new IllegalArgumentException("secret metadata requires authored provenance");
        }
    }

    private void validateReflectionMutation(BeliefAssertion value) {
        if (value.provenance().sourceKind() != EvidenceSourceKind.DERIVED_REFLECTION) return;
        if (value.supportIds().isEmpty()
                || value.provenance().evidenceIds().stream().noneMatch(id ->
                        id.startsWith("REFLECTION_ID:"))
                || value.provenance().generatedSpeechOnly()) {
            throw new IllegalArgumentException("reflection requires support provenance");
        }
        double strongest = 0;
        for (UUID id : value.supportIds()) {
            BeliefAssertion support = assertions.get(id);
            if (support == null || !support.ownerNpcId().equals(value.ownerNpcId())
                    || support.provenance().generatedSpeechOnly()) {
                throw new IllegalArgumentException("invalid reflection support");
            }
            strongest = Math.max(strongest, support.confidence());
        }
        if (value.confidence() > strongest + .000001) {
            throw new IllegalArgumentException("reflection confidence exceeds support");
        }
        String payload = (value.statement() + " " + value.value()).toLowerCase(Locale.ROOT);
        if (payload.contains("chain-of-thought") || payload.contains("hidden reasoning")
                || payload.contains("internal monologue")) {
            throw new IllegalArgumentException("hidden reasoning may not persist");
        }
    }

    /** Public deterministic replay path for duplicate/idempotence validation. */
    public synchronized BeliefAssertion appendEvent(BeliefEvent proposed) {
        if (proposed == null) throw new IllegalArgumentException("belief event required");
        BeliefEvent priorEvent = eventsById.get(proposed.eventId());
        boolean checksumValid = checksum(unsigned(proposed)).equals(proposed.checksum());
        if (priorEvent != null && checksumValid
                && priorEvent.checksum().equals(proposed.checksum())) {
            return assertions.get(priorEvent.assertion().assertionId());
        }
        BeliefAssertion priorAssertion = assertions.get(proposed.assertion().assertionId());
        gate(proposed.assertion(), proposed, priorAssertion, priorEvent == null);
        if (priorEvent != null) throw new IllegalArgumentException("divergent duplicate event");
        if (!checksumValid) {
            throw new IllegalArgumentException("corrupt belief event checksum");
        }
        if (!writes.offer(proposed)) throw new IllegalStateException("belief writer queue full");
        apply(proposed);
        return proposed.assertion();
    }

    public synchronized BeliefAssertion retract(UUID assertionId, String evidenceId,
            Instant at) {
        BeliefAssertion prior = assertions.get(assertionId);
        if (prior == null) throw new IllegalArgumentException("unknown assertion " + assertionId);
        BeliefProvenance provenance = new BeliefProvenance(prior.provenance().sourceKind(),
                prior.provenance().sourceActorId(),
                unionText(prior.provenance().evidenceIds(), List.of(evidenceId)), false,
                prior.provenance().authoritativeActionResult());
        BeliefAssertion retracted = new BeliefAssertion(prior.assertionId(),
                prior.ownerNpcId(), prior.subjectId(), prior.subject(), prior.predicate(),
                prior.value(), prior.statement(), prior.polarity(), EpistemicStatus.RETRACTED,
                prior.confidence(), provenance, prior.temporalScope(), prior.assertionScope(),
                prior.supportIds(), prior.conflictIds(), prior.revision() + 1,
                prior.learnedAt(), at);
        commit(newEvent(BeliefEvent.EventType.BELIEF_RETRACTED, retracted,
                List.of(prior.assertionId())), true, prior);
        return retracted;
    }

    public synchronized void ingestPerception(UUID ownerNpcId, UUID playerId,
            RawPerceptionSnapshot raw) {
        if (raw == null || ownerNpcId == null || playerId == null
                || raw.engineSnapshot().npcEntityId() == null) return;
        String item = raw.engineSnapshot().focusedPlayerHeldItem() == null ? "NONE"
                : raw.engineSnapshot().focusedPlayerHeldItem().itemId();
        assertBelief(new BeliefProposal(null, ownerNpcId, playerId, "focused player",
                "HOLDS", item, item.equals("NONE") ? "The player is holding nothing."
                        : "The player is holding " + item + ".",
                BeliefAssertion.Polarity.POSITIVE, EpistemicStatus.KNOWN, 1,
                new BeliefProvenance(EvidenceSourceKind.DIRECT_OBSERVATION, ownerNpcId,
                        List.of("PERCEPTION:" + raw.responseId()), false, false), null,
                BeliefAssertion.AssertionScope.ENTITY, List.of(), raw.capturedAt()));
    }

    public synchronized Optional<BeliefAssertion> ingestActionResult(UUID ownerNpcId,
            UUID playerId, NpcActionRequest request, NpcActionResult result, Instant at) {
        if (result == null || request == null) return Optional.empty();
        String id = request.id() == null ? "" : request.id().toUpperCase(Locale.ROOT);
        BeliefProvenance provenance = new BeliefProvenance(EvidenceSourceKind.ACTION_RESULT,
                ownerNpcId, List.of("ACTION_RESULT:" + request.toolCallId()), false, true);
        if (!result.success()) {
            BeliefAssertion failed = assertBelief(new BeliefProposal(null, ownerNpcId,
                    playerId, "action " + id, "ACTION_FAILED", result.code(),
                    result.eventDescription(), BeliefAssertion.Polarity.POSITIVE,
                    EpistemicStatus.KNOWN, 1, provenance,
                    new BeliefAssertion.TemporalScope(
                            BeliefPredicateRegistry.Stability.VOLATILE, at,
                            at.plus(java.time.Duration.ofHours(6)), "CURRENT_OUTCOME"),
                    BeliefAssertion.AssertionScope.EVENT, List.of(), at));
            appendProceduralOutcome(ownerNpcId, request, result, failed, provenance, at);
            return Optional.of(failed);
        }
        String predicate = id.contains("GIVE") || id.contains("TRANSACTION")
                ? "TRANSACTION_OCCURRED" : "ACTION_OCCURRED";
        BeliefAssertion occurred = assertBelief(new BeliefProposal(null, ownerNpcId, playerId,
                "player interaction", predicate, id, result.eventDescription(),
                BeliefAssertion.Polarity.POSITIVE, EpistemicStatus.KNOWN, 1,
                provenance,
                BeliefAssertion.TemporalScope.stable(at),
                BeliefAssertion.AssertionScope.EVENT, List.of(), at));
        if (predicate.equals("TRANSACTION_OCCURRED")) {
            String item = request.parameters() != null && request.parameters().has("itemId")
                    ? request.parameters().get("itemId").getAsString()
                    : result.eventDescription();
            assertBelief(new BeliefProposal(null, ownerNpcId, playerId, "player", "OWNS",
                    item, "The transaction gave the player ownership of " + item + ".",
                    BeliefAssertion.Polarity.POSITIVE, EpistemicStatus.KNOWN, 1, provenance,
                    BeliefAssertion.TemporalScope.stable(at),
                    BeliefAssertion.AssertionScope.ENTITY, List.of(occurred.assertionId()), at));
        }
        appendProceduralOutcome(ownerNpcId, request, result, occurred, provenance, at);
        invalidateDerivedMatching(ownerNpcId, "PROCEDURAL_RELIABILITY", id,
                "AUTHORITATIVE_SUCCESS:" + request.toolCallId(), at);
        return Optional.of(occurred);
    }

    private void appendProceduralOutcome(UUID ownerNpcId, NpcActionRequest request,
            NpcActionResult result, BeliefAssertion immediate, BeliefProvenance provenance,
            Instant at) {
        String id = request.id() == null ? "" : request.id().toUpperCase(Locale.ROOT);
        UUID episode = UUID.nameUUIDFromBytes((ownerNpcId + "|" + request.toolCallId())
                .getBytes(StandardCharsets.UTF_8));
        assertBelief(new BeliefProposal(null, ownerNpcId, episode, "action attempt " + id,
                "PROCEDURAL_OUTCOME", (result.success() ? "SUCCESS:" : "FAILURE:")
                        + id + ":" + result.code(),
                "Authoritative " + id + " attempt "
                        + (result.success() ? "succeeded: " : "failed: ")
                        + result.eventDescription(), BeliefAssertion.Polarity.POSITIVE,
                EpistemicStatus.KNOWN, 1,
                new BeliefProvenance(EvidenceSourceKind.PROCEDURAL_OUTCOME, ownerNpcId,
                        provenance.evidenceIds(), false, true),
                new BeliefAssertion.TemporalScope(BeliefPredicateRegistry.Stability.STABLE,
                        at, null, "ACTION_RESULT_EPISODE"),
                BeliefAssertion.AssertionScope.EVENT, List.of(immediate.assertionId()), at));
    }

    public synchronized int invalidateDerivedMatching(UUID ownerNpcId, String predicate,
            String valueToken, String evidenceId, Instant at) {
        int count = 0;
        for (BeliefAssertion value : List.copyOf(assertions.values())) {
            if (!value.ownerNpcId().equals(ownerNpcId)
                    || value.provenance().sourceKind() != EvidenceSourceKind.DERIVED_REFLECTION
                    || !value.predicate().equals(BeliefPredicateRegistry.canonical(predicate))
                    || !value.value().toUpperCase(Locale.ROOT).contains(
                            valueToken == null ? "" : valueToken.toUpperCase(Locale.ROOT))
                    || !value.activeAt(at)) continue;
            disputeDerived(value, evidenceId, at); count++;
        }
        return count;
    }

    public synchronized int expireVolatile(Instant at) {
        int count = 0;
        for (BeliefAssertion value : List.copyOf(assertions.values())) {
            if (value.activeAt(at) || value.status() == EpistemicStatus.EXPIRED
                    || value.temporalScope().stability()
                            != BeliefPredicateRegistry.Stability.VOLATILE) continue;
            BeliefAssertion expired = copy(value, EpistemicStatus.EXPIRED,
                    value.confidence(), value.revision() + 1, at, value.conflictIds(),
                    value.supportIds());
            commit(newEvent(BeliefEvent.EventType.BELIEF_EXPIRED, expired,
                    List.of(value.assertionId())), true, value);
            count++;
        }
        return count;
    }

    /** Exact typed RAM lookup. */
    public synchronized List<BeliefAssertion> current(UUID npcId, UUID subjectId,
            String predicate) {
        if (npcId == null) return List.of();
        String wanted = BeliefPredicateRegistry.canonical(predicate);
        Instant now = Instant.now();
        return assertions.values().stream().filter(value -> value.ownerNpcId().equals(npcId))
                .filter(value -> subjectId == null || subjectId.equals(value.subjectId()))
                .filter(value -> wanted.isBlank() || wanted.equals(value.predicate()))
                .filter(value -> value.activeAt(now)).sorted(Comparator
                        .comparingInt((BeliefAssertion value) ->
                                value.provenance().authorityRank())
                        .thenComparing(BeliefAssertion::lastConfirmedAt).reversed()).toList();
    }

    public synchronized List<BeliefEvent> history(UUID npcId) {
        return history.stream().filter(value -> value.assertion().ownerNpcId().equals(npcId))
                .toList();
    }
    public synchronized Optional<BeliefAssertion> assertion(UUID assertionId) {
        return Optional.ofNullable(assertionId == null ? null : assertions.get(assertionId));
    }
    public synchronized boolean containsFingerprint(UUID npcId, String fingerprint) {
        return byFingerprint(npcId, fingerprint).isPresent();
    }
    public synchronized Optional<SourcedBelief> byFingerprint(UUID npcId, String fingerprint) {
        Optional<SourcedBelief> old = legacy.stream().filter(value -> value.npcId().equals(npcId))
                .filter(value -> value.fingerprint().equals(fingerprint)).findFirst();
        return old.isPresent() ? old : current(npcId, null, "").stream()
                .map(value -> toLegacy(value, null, null, null, .4))
                .filter(value -> value.fingerprint().equals(fingerprint)).findFirst();
    }
    public synchronized List<SourcedBelief> relevant(UUID npcId, List<UUID> entityIds,
            int limit) {
        if (limit <= 0) return List.of();
        List<UUID> ids = entityIds == null ? List.of() : entityIds;
        return current(npcId, null, "").stream().filter(value -> value.subjectId() == null
                        || ids.contains(value.subjectId())
                        || ids.contains(value.provenance().sourceActorId()))
                .limit(limit).map(this::toLegacyPreservingContext).toList();
    }
    public synchronized List<SourcedBelief> queryReadOnly(UUID npcId, UUID subjectId,
            String predicate, String queryText, int limit) {
        if (limit <= 0) return List.of();
        Set<String> terms = terms(queryText);
        return current(npcId, subjectId, predicate).stream()
                .filter(value -> terms.isEmpty() || terms(value.statement() + " "
                        + value.value()).stream().anyMatch(terms::contains))
                .limit(limit).map(this::toLegacyPreservingContext).toList();
    }

    private SourcedBelief toLegacyPreservingContext(BeliefAssertion value) {
        SourcedBelief context = legacy.stream().filter(old -> old.beliefId()
                .equals(value.assertionId())).findFirst().orElse(null);
        if (context == null) return toLegacy(value, null, null, null, .4);
        SourcedBelief base = toLegacy(value, context.conversationId(), context.responseId(),
                context.utteranceId(), context.urgency());
        return new SourcedBelief(base.beliefId(), base.npcId(), base.sourceEntityId(),
                base.subjectEntityId(), base.subject(), base.predicate(), base.object(),
                context.semanticLocation(), context.temporalReference(), base.proposition(),
                base.timestamp(), base.confidence(), base.urgency(), base.conversationId(),
                base.responseId(), base.utteranceId(), base.evidenceRefs()).normalized();
    }

    /** E5 revision/time-aware lookup over the already materialized RAM projection. */
    public synchronized List<RankedAssertion> queryAssertionsReadOnly(UUID npcId,
            UUID subjectId, String predicate, String queryText,
            com.inigmasgames.persistentnpcs.epistemic.E5QueryExpansion expansion,
            int limit, Instant now) {
        if (npcId == null || limit <= 0) return List.of();
        Instant at = now == null ? Instant.now() : now;
        var plan = expansion == null
                ? com.inigmasgames.persistentnpcs.epistemic.E5QueryExpansion.expand(
                        queryText, null, null, at) : expansion;
        Set<String> query = new java.util.LinkedHashSet<>(terms(queryText));
        query.addAll(plan.terms());
        String wanted = BeliefPredicateRegistry.canonical(predicate);
        return assertions.values().stream()
                .filter(value -> value.ownerNpcId().equals(npcId))
                .filter(value -> subjectId == null || subjectId.equals(value.subjectId()))
                .filter(value -> wanted.isBlank() || wanted.equals(value.predicate()))
                .filter(value -> value.status() != EpistemicStatus.RETRACTED)
                .filter(value -> plan.historical()
                        || value.status() != EpistemicStatus.SUPERSEDED
                                && value.status() != EpistemicStatus.EXPIRED)
                .map(value -> ranked(value, query, plan, at))
                .filter(value -> value.semanticScore() >= .18
                        || !wanted.isBlank() && value.assertion().predicate().equals(wanted))
                .sorted(Comparator.comparingDouble(RankedAssertion::score).reversed()
                        .thenComparing(value -> value.assertion().lastConfirmedAt(),
                                Comparator.reverseOrder()))
                .limit(limit).toList();
    }

    private static RankedAssertion ranked(BeliefAssertion value, Set<String> query,
            com.inigmasgames.persistentnpcs.epistemic.E5QueryExpansion plan, Instant now) {
        Set<String> candidate = terms(value.statement() + " " + value.value() + " "
                + value.subject() + " " + value.predicate());
        double semantic = query.isEmpty() ? .5 : query.stream().filter(candidate::contains)
                .count() / (double) Math.max(1, query.size());
        double temporal = plan.temporalScore(value.temporalScope().validFrom(), now);
        if ((plan.validFrom() != null || plan.validUntil() != null)
                && !plan.matches(value.temporalScope().validFrom())) temporal = 0;
        double authority = value.provenance().authorityRank() / 100d;
        double status = value.status() == EpistemicStatus.DISPUTED ? .55
                : value.status() == EpistemicStatus.SUPERSEDED ? .72 : 1;
        double score = .34 * semantic + .2 * temporal + .18 * value.confidence()
                + .18 * authority + .1 * status;
        return new RankedAssertion(value, score, semantic, temporal, authority, status);
    }

    public record RankedAssertion(BeliefAssertion assertion, double score,
            double semanticScore, double temporalScore, double authorityScore,
            double statusScore) { }

    private void commit(BeliefEvent raw, boolean enforce, BeliefAssertion prior) {
        BeliefEvent event = signed(raw);
        if (enforce) gate(event.assertion(), event, prior, true);
        if (!writes.offer(event)) throw new IllegalStateException("belief writer queue full");
        apply(event);
        if (Set.of(EpistemicStatus.SUPERSEDED, EpistemicStatus.RETRACTED,
                EpistemicStatus.EXPIRED, EpistemicStatus.DISPUTED)
                .contains(event.assertion().status())) {
            invalidateDependents(event.assertion().assertionId(), event.eventId().toString(),
                    event.occurredAt());
        }
    }

    private void invalidateDependents(UUID supportId, String evidenceId, Instant at) {
        for (BeliefAssertion value : List.copyOf(assertions.values())) {
            if (value.assertionId().equals(supportId) || !value.supportIds().contains(supportId)
                    || value.provenance().sourceKind() != EvidenceSourceKind.DERIVED_REFLECTION
                    || !value.activeAt(at)) continue;
            disputeDerived(value, "SUPPORT_INVALIDATED:" + evidenceId, at);
        }
    }

    private void disputeDerived(BeliefAssertion value, String evidenceId, Instant at) {
        BeliefProvenance provenance = new BeliefProvenance(EvidenceSourceKind.DERIVED_REFLECTION,
                value.provenance().sourceActorId(), unionText(value.provenance().evidenceIds(),
                        List.of(evidenceId)), false, false);
        BeliefAssertion disputed = new BeliefAssertion(value.assertionId(),
                value.ownerNpcId(), value.subjectId(), value.subject(), value.predicate(),
                value.value(), value.statement(), value.polarity(), EpistemicStatus.DISPUTED,
                value.confidence() * .5, provenance, value.temporalScope(),
                value.assertionScope(), value.supportIds(), value.conflictIds(),
                value.revision() + 1, value.learnedAt(), at);
        commit(newEvent(BeliefEvent.EventType.BELIEF_CONTRADICTED, disputed,
                value.supportIds()), true, value);
    }

    private void gate(BeliefAssertion assertion, BeliefEvent event,
            BeliefAssertion prior, boolean duplicateConsistent) {
        var observer = sentinel;
        if (observer == null) return;
        boolean provenance = assertion != null && assertion.provenance() != null
                && !assertion.provenance().evidenceIds().isEmpty();
        boolean speechOnly = assertion != null && assertion.provenance().generatedSpeechOnly();
        boolean revision = assertion != null && (prior == null
                ? assertion.revision() == 1 : assertion.revision() == prior.revision() + 1);
        boolean action = assertion == null || !Set.of("ACTION_OCCURRED",
                "TRANSACTION_OCCURRED").contains(assertion.predicate())
                || assertion.provenance().sourceKind() == EvidenceSourceKind.ACTION_RESULT
                        && assertion.provenance().authoritativeActionResult();
        boolean valid = assertion != null && !assertion.predicate().isBlank()
                && !assertion.statement().isBlank() && event != null;
        var decision = observer.guard(new com.inigmasgames.persistentnpcs.sentinel
                .SentinelObservation(com.inigmasgames.persistentnpcs.sentinel
                        .SentinelContracts.Boundary.BELIEF_WRITE_PROPOSED,
                        "PERSISTENCE_STREAM:" + (assertion == null ? "unknown"
                                : assertion.ownerNpcId()), assertion == null ? null
                                : assertion.ownerNpcId(), event == null ? List.of()
                                : List.of("beliefEventId=" + event.eventId()), Map.of(
                                "factualPromotionAttempt", "true",
                                "provenancePresent", Boolean.toString(provenance),
                                "generatedSpeechOnlyEvidence", Boolean.toString(speechOnly),
                                "beliefRevisionValid", Boolean.toString(revision),
                                "duplicateEventConsistent", Boolean.toString(duplicateConsistent),
                                "actionOccurrenceSupported", Boolean.toString(action),
                                "persistenceProposalValid", Boolean.toString(valid))));
        if (!decision.allowed()) {
            if (assertion != null && decision.circuitState() == com.inigmasgames
                    .persistentnpcs.sentinel.SentinelContracts.CircuitState.OPEN) {
                readOnlyScopes.add(assertion.ownerNpcId());
            }
            throw new com.inigmasgames.persistentnpcs.sentinel.SentinelGuardException(
                    decision.invariantId(), decision.reasonCode());
        }
    }

    private void apply(BeliefEvent event) {
        history.add(event); eventsById.put(event.eventId(), event);
        assertions.put(event.assertion().assertionId(), event.assertion());
        nextSequence = Math.max(nextSequence, event.sequence() + 1);
    }
    private BeliefEvent newEvent(BeliefEvent.EventType type, BeliefAssertion assertion,
            List<UUID> related) {
        return new BeliefEvent(1, nextSequence++, UUID.randomUUID(), type, assertion,
                related, Instant.now(), "");
    }
    private static BeliefEvent unsigned(BeliefEvent value) {
        return new BeliefEvent(value.schemaVersion(), value.sequence(), value.eventId(),
                value.type(), value.assertion(), value.relatedAssertionIds(),
                value.occurredAt(), "");
    }
    private static BeliefEvent signed(BeliefEvent value) {
        BeliefEvent raw = unsigned(value);
        return new BeliefEvent(raw.schemaVersion(), raw.sequence(), raw.eventId(), raw.type(),
                raw.assertion(), raw.relatedAssertionIds(), raw.occurredAt(), checksum(raw));
    }
    private static String checksum(BeliefEvent value) {
        return com.inigmasgames.persistentnpcs.sentinel.SentinelPromptIdentity.hash(List.of(
                new ChatMessage("belief-event", value.schemaVersion() + "|" + value.sequence()
                        + "|" + value.eventId() + "|" + value.type() + "|"
                        + JsonFiles.GSON.toJson(value.assertion()) + "|"
                        + value.relatedAssertionIds() + "|" + value.occurredAt())));
    }

    private void writeLoop() {
        while (!closed.get() || !writes.isEmpty()) {
            try {
                BeliefEvent event = writes.poll(250, TimeUnit.MILLISECONDS);
                if (event == null) continue;
                Files.createDirectories(eventPath.getParent());
                Files.writeString(eventPath, JsonFiles.GSON.toJson(event)
                        + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                if (++writesSinceSnapshot >= SNAPSHOT_INTERVAL) writeSnapshot();
            } catch (InterruptedException interrupted) {
                if (closed.get() && writes.isEmpty()) break;
            } catch (IOException | RuntimeException failure) {
                writerFailure = failure instanceof RuntimeException runtime ? runtime
                        : new java.io.UncheckedIOException((IOException) failure);
            }
        }
    }

    private synchronized void writeSnapshot() {
        long eventOffset = 0;
        try { if (Files.isRegularFile(eventPath)) eventOffset = Files.size(eventPath); }
        catch (IOException ignored) { }
        Snapshot raw = new Snapshot(2, nextSequence - 1, eventOffset,
                List.copyOf(assertions.values()), List.copyOf(history), "");
        Snapshot snapshot = new Snapshot(2, raw.lastSequence(), raw.eventByteOffset(),
                raw.assertions(), raw.events(),
                snapshotChecksum(raw));
        JsonFiles.writeAtomic(snapshotPath, snapshot);
        JsonFiles.writeAtomic(legacyPath, legacy);
        snapshotSequence = snapshot.lastSequence(); writesSinceSnapshot = 0;
    }
    private void loadSnapshot() {
        if (!Files.isRegularFile(snapshotPath)) return;
        try {
            Snapshot value = JsonFiles.read(snapshotPath, Snapshot.class);
            Snapshot raw = new Snapshot(value.schemaVersion(), value.lastSequence(),
                    value.eventByteOffset(), value.assertions(), value.events(), "");
            if ((value.schemaVersion() != 1 && value.schemaVersion() != 2)
                    || !snapshotChecksum(raw).equals(value.checksum()))
                return;
            value.assertions().forEach(item -> assertions.put(item.assertionId(), item));
            if (value.schemaVersion() >= 2 && value.events() != null) {
                value.events().forEach(item -> {
                    history.add(item); eventsById.put(item.eventId(), item);
                });
            }
            snapshotSequence = value.lastSequence(); nextSequence = snapshotSequence + 1;
        } catch (RuntimeException ignored) { }
    }
    private void loadEvents() {
        if (!Files.isRegularFile(eventPath)) return;
        try {
            long offset = snapshotSequence > 0 ? snapshotEventOffset() : 0;
            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(
                    eventPath, StandardOpenOption.READ)) {
                long size = channel.size();
                if (offset >= size) return;
                if (offset > 0) channel.position(offset);
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        java.nio.channels.Channels.newReader(channel, StandardCharsets.UTF_8))) {
                    com.google.gson.JsonStreamParser parser =
                            new com.google.gson.JsonStreamParser(reader);
                    while (parser.hasNext()) {
                try {
                    BeliefEvent value = JsonFiles.GSON.fromJson(parser.next(), BeliefEvent.class);
                    if (value == null || !checksum(unsigned(value)).equals(value.checksum())
                            || eventsById.containsKey(value.eventId())) continue;
                    history.add(value); eventsById.put(value.eventId(), value);
                    if (value.sequence() > snapshotSequence) {
                        assertions.put(value.assertion().assertionId(), value.assertion());
                    }
                    nextSequence = Math.max(nextSequence, value.sequence() + 1);
                } catch (RuntimeException ignored) { }
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) { }
    }
    private long snapshotEventOffset() {
        if (!Files.isRegularFile(snapshotPath)) return 0;
        try {
            Snapshot value = JsonFiles.read(snapshotPath, Snapshot.class);
            return value.schemaVersion() >= 2 ? Math.max(0, value.eventByteOffset()) : 0;
        } catch (RuntimeException ignored) { return 0; }
    }
    private static String snapshotChecksum(Snapshot value) {
        String payload = value.schemaVersion() + "|" + value.lastSequence() + "|"
                + (value.schemaVersion() >= 2 ? value.eventByteOffset() + "|" : "")
                + JsonFiles.GSON.toJson(value.assertions())
                + (value.schemaVersion() >= 2 ? "|" + JsonFiles.GSON.toJson(value.events()) : "");
        return com.inigmasgames.persistentnpcs.sentinel.SentinelPromptIdentity.hash(List.of(
                new ChatMessage("belief-snapshot", payload)));
    }

    public void awaitIdle() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!writes.isEmpty() && System.nanoTime() < deadline) Thread.onSpinWait();
        if (writerFailure != null) throw writerFailure;
    }
    public Path path() { return legacyPath; }
    public Path eventPath() { return eventPath; }
    public Path snapshotPath() { return snapshotPath; }
    public RestorationStats restorationStats() { return restorationStats; }
    public void setDegradationSentinel(
            com.inigmasgames.persistentnpcs.sentinel.OrbisDegradationSentinel value) {
        sentinel = value;
    }
    public synchronized boolean readOnly(UUID npcId) { return readOnlyScopes.contains(npcId); }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        writer.interrupt();
        try { writer.join(2_000); } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        writeSnapshot();
    }

    private static BeliefProposal fromLegacy(SourcedBelief value) {
        EvidenceSourceKind kind = source(value.evidenceRefs());
        return new BeliefProposal(value.beliefId(), value.npcId(), value.subjectEntityId(),
                value.subject(), value.predicate(), value.object().isBlank()
                        ? value.proposition() : value.object(), value.proposition(),
                BeliefAssertion.Polarity.POSITIVE, EpistemicStatus.BELIEVED,
                value.confidence(), new BeliefProvenance(kind, value.sourceEntityId(),
                        value.evidenceRefs(), speechOnly(value.evidenceRefs()),
                        kind == EvidenceSourceKind.ACTION_RESULT),
                new BeliefAssertion.TemporalScope(
                        BeliefPredicateRegistry.definition(value.predicate()).stability(),
                        value.timestamp(), null, value.temporalReference()),
                BeliefAssertion.AssertionScope.ENTITY, List.of(), value.timestamp());
    }
    private static EvidenceSourceKind source(List<String> refs) {
        if (refs != null) for (String ref : refs) {
            if (ref.startsWith("AUTHORED_CANON:")) return EvidenceSourceKind.AUTHORED_CANON;
            if (ref.startsWith("DIRECT_OBSERVATION:") || ref.startsWith("PERCEPTION:"))
                return EvidenceSourceKind.DIRECT_OBSERVATION;
            if (ref.startsWith("ACTION_RESULT:")) return EvidenceSourceKind.ACTION_RESULT;
            if (ref.startsWith("NPC_TESTIMONY:")) return EvidenceSourceKind.NPC_TESTIMONY;
        }
        return EvidenceSourceKind.PLAYER_TESTIMONY;
    }
    private static boolean speechOnly(List<String> refs) {
        return refs != null && !refs.isEmpty() && refs.stream().allMatch(value ->
                value.startsWith("RESPONSE:") || value.startsWith("NPC_SPEECH:"));
    }
    private static EpistemicStatus status(BeliefProposal value) {
        if (value.status() != null) return value.status();
        return value.provenance().authorityRank() >= 80 ? EpistemicStatus.KNOWN
                : EpistemicStatus.BELIEVED;
    }
    private static boolean sameActorTestimony(BeliefAssertion left, BeliefAssertion right) {
        return left.provenance().sourceActorId() != null
                && left.provenance().sourceActorId().equals(right.provenance().sourceActorId())
                && Set.of(EvidenceSourceKind.PLAYER_TESTIMONY,
                        EvidenceSourceKind.NPC_TESTIMONY).contains(left.provenance().sourceKind())
                && Set.of(EvidenceSourceKind.PLAYER_TESTIMONY,
                        EvidenceSourceKind.NPC_TESTIMONY).contains(right.provenance().sourceKind());
    }
    private static BeliefAssertion conflict(BeliefAssertion value, EpistemicStatus status,
            UUID conflict) {
        return copy(value, status, value.confidence(), value.revision(),
                value.lastConfirmedAt(), union(value.conflictIds(), List.of(conflict)),
                value.supportIds());
    }
    private static BeliefAssertion copy(BeliefAssertion value, EpistemicStatus status,
            double confidence, int revision, Instant confirmed, List<UUID> conflicts,
            List<UUID> supports) {
        return new BeliefAssertion(value.assertionId(), value.ownerNpcId(), value.subjectId(),
                value.subject(), value.predicate(), value.value(), value.statement(),
                value.polarity(), status, confidence, value.provenance(), value.temporalScope(),
                value.assertionScope(), supports, conflicts, revision, value.learnedAt(), confirmed);
    }
    private static List<UUID> union(List<UUID> left, List<UUID> right) {
        LinkedHashSet<UUID> result = new LinkedHashSet<>(left); result.addAll(right);
        return List.copyOf(result);
    }
    private static List<String> unionText(List<String> left, List<String> right) {
        LinkedHashSet<String> result = new LinkedHashSet<>(left); result.addAll(right);
        return List.copyOf(result);
    }
    private static SourcedBelief toLegacy(BeliefAssertion value, UUID conversation,
            UUID response, UUID utterance, double urgency) {
        UUID source = value.provenance().sourceActorId() == null ? value.ownerNpcId()
                : value.provenance().sourceActorId();
        return new SourcedBelief(value.assertionId(), value.ownerNpcId(), source,
                value.subjectId(), value.subject(), value.predicate(), value.value(), "",
                value.temporalScope().authoredReference(), value.statement(), value.learnedAt(),
                value.confidence(), urgency, conversation, response, utterance,
                value.provenance().evidenceIds()).normalized();
    }
    private static Set<String> terms(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.toLowerCase(Locale.ROOT)
                        .replaceAll("[^\\p{L}\\p{N}' ]", " ").split("\\s+"))
                .filter(term -> term.length() > 2)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private record Snapshot(int schemaVersion, long lastSequence, long eventByteOffset,
            List<BeliefAssertion> assertions, List<BeliefEvent> events, String checksum) { }
    public record RestorationStats(boolean snapshotHit, long snapshotSequence,
            long restoredEvents, long tailEvents, long durationMs) {
        static RestorationStats empty() { return new RestorationStats(false, 0, 0, 0, 0); }
    }
}
