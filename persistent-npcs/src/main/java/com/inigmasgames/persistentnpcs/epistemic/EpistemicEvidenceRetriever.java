package com.inigmasgames.persistentnpcs.epistemic;

import com.inigmasgames.persistentnpcs.autonomy.AgentOperation;
import com.inigmasgames.persistentnpcs.autonomy.AgentOperationStore;
import com.inigmasgames.persistentnpcs.cognition.CognitionContext;
import com.inigmasgames.persistentnpcs.cognition.NpcSelfModel;
import com.inigmasgames.persistentnpcs.cognition.SourcedBelief;
import com.inigmasgames.persistentnpcs.cognition.SourcedBeliefStore;
import com.inigmasgames.persistentnpcs.epistemic.BeliefAssertion;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.PerceivedItem;
import com.inigmasgames.persistentnpcs.perception.RawPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.SemanticWorldModel;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.relationship.RelationshipRecord;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.task.NpcTask;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * E2 bounded read adapter over existing authoritative stores. It owns no persistence and makes
 * no provider calls. The requested query kind determines admissible stores and source priority.
 */
public final class EpistemicEvidenceRetriever {
    private static final Pattern STATED_NAME = Pattern.compile(
            "(?i)(?:stated name|name(?: is|=))\\s*[=:]?\\s*([\\p{L}][\\p{L}'-]{0,31})");
    private static final int MAX_PER_SOURCE = 4;

    private final MemoryStore memories;
    private final SourcedBeliefStore beliefs;
    private final RelationshipStore relationships;
    private final NpcTaskStore tasks;
    private final AgentOperationStore operations;
    private final NpcProfileRegistry profiles;
    private final ActorModelService social;

    public EpistemicEvidenceRetriever(MemoryStore memories, SourcedBeliefStore beliefs,
            RelationshipStore relationships, NpcTaskStore tasks,
            AgentOperationStore operations, NpcProfileRegistry profiles) {
        this(memories, beliefs, relationships, tasks, operations, profiles, null);
    }

    public EpistemicEvidenceRetriever(MemoryStore memories, SourcedBeliefStore beliefs,
            RelationshipStore relationships, NpcTaskStore tasks,
            AgentOperationStore operations, NpcProfileRegistry profiles,
            ActorModelService social) {
        this.memories = memories;
        this.beliefs = beliefs;
        this.relationships = relationships;
        this.tasks = tasks;
        this.operations = operations;
        this.profiles = profiles;
        this.social = social;
    }

    public EpistemicContract enrich(EpistemicContract base, UUID responseId,
            NpcProfile profile, UUID playerId, String utterance,
            RawPerceptionSnapshot rawPerception, CognitionContext cognition,
            ConversationWorkspace workspace, List<String> validActions) {
        return enrich(base, responseId, profile, playerId, utterance, rawPerception, cognition,
                null, workspace, validActions);
    }

    public EpistemicContract enrich(EpistemicContract base, UUID responseId,
            NpcProfile profile, UUID playerId, String utterance,
            RawPerceptionSnapshot rawPerception, CognitionContext cognition,
            NpcSelfModel selfModel, ConversationWorkspace workspace,
            List<String> validActions) {
        if (base == null || profile == null || base.mode() == EpistemicFeatureMode.OFF) return base;
        long started = System.nanoTime();
        EpistemicBudget budget = base.budget();
        ConversationWorkspace.Snapshot workspaceSnapshot = workspace == null ? null
                : workspace.snapshot(Instant.now());
        E5QueryExpansion expansion = E5QueryExpansion.expand(utterance,
                base.dialogueFrame(), workspaceSnapshot, Instant.now());
        MutableEvidence found = new MutableEvidence(base, responseId, profile.id(), playerId,
                rawPerception == null ? null : rawPerception.engineSnapshot().worldId(), budget,
                started, expansion, workspaceSnapshot);
        EpistemicQueryKind kind = kind(base.queryPlan().queryKind());
        ActorModelService.DisclosureEvaluation disclosure = social == null
                ? null : social.evaluateDisclosure(profile.id(), playerId, utterance);
        if (base.dialogueFrame().predicateKey().startsWith("BELIEVES_ACTOR_")) {
            // A question about another actor's mental state cannot authorize disclosure of
            // the protected underlying proposition merely because words overlap.
            disclosure = null;
        }
        if (!base.dialogueFrame().inputQualityConcern() && !base.queryPlan().ambiguous()) {
            persistentBeliefs(found, profile.id(), playerId, base.dialogueFrame(), utterance);
            switch (kind) {
                case IDENTITY_RECALL -> identity(found, profile.id(), playerId);
                case EPISODIC_RECALL -> episodic(found, profile.id(), playerId, utterance);
                case CURRENT_PERCEPTION -> perception(found, rawPerception, cognition);
                case NPC_SELF_STATE -> selfState(found, profile, cognition);
                case RELATIONSHIP_FACT -> {
                    if (base.dialogueFrame().predicateKey().equals("KNOWLEDGE_TOPIC")) {
                        actorLocalTopicKnowledge(found, profile.id(), utterance);
                    } else if (base.dialogueFrame().predicateKey().equals("RELATIONSHIP")) {
                        relationships(found, profile, playerId);
                    }
                }
                case SUBJECTIVE_PREFERENCE -> preference(found, profile, utterance, selfModel);
                case ACTION_REQUEST -> action(found, profile, validActions);
                case CLARIFICATION -> clarification(found, base.queryPlan().sourceProposition());
                case CORRECTION -> correction(found, base.dialogueFrame(), playerId);
                case OBJECTIVE_PROPERTY -> objectiveProperty(found, rawPerception);
                case GENERAL_SOCIAL, UNRESOLVED -> { }
            }
            socialEvidence(found, profile.id(), playerId, base.dialogueFrame(), utterance,
                    disclosure);
        }
        if (kind == EpistemicQueryKind.SUBJECTIVE_PREFERENCE
                && contains(utterance, " because ", " since ", " after your ", " when your ")) {
            found.restrict("SUBJECTIVE_EMBEDDED_OBJECTIVE_PREMISE_REQUIRES_EVIDENCE");
        }
        EvidencePacket packet = found.finish();
        if (workspace != null) workspace.observeEvidence(packet, Instant.now());
        Answerability answerability = AnswerabilityClassifier.classify(
                base.dialogueFrame(), base.queryPlan(), packet);
        EpistemicAnswerPlanner.Result planning = EpistemicAnswerPlanner.compile(
                base.dialogueFrame(), base.queryPlan(), packet, answerability,
                base.mode() == EpistemicFeatureMode.AUTHORITATIVE);
        if (social != null && disclosure != null && disclosure.applies()) {
            planning = new EpistemicAnswerPlanner.Result(
                    social.applyDisclosure(planning.answerPlan(), disclosure),
                    planning.claimPolicy());
        }
        ArrayList<String> diagnoses = new ArrayList<>(base.diagnoses());
        diagnoses.remove("E1_SHADOW_ONLY");
        diagnoses.add(base.mode() == EpistemicFeatureMode.AUTHORITATIVE
                ? "E3_AUTHORITATIVE_FOREGROUND" : "E2_SHADOW_ONLY");
        if (packet.budgetExhausted()) diagnoses.add("E2_EVIDENCE_BUDGET_EXHAUSTED");
        if (answerability == Answerability.CONFLICTED) diagnoses.add("E2_CONFLICT_PRESERVED");
        long totalMicros = base.planningMicros() + (System.nanoTime() - started) / 1_000L;
        return new EpistemicContract(base.schemaVersion(), base.mode(), base.dialogueFrame(),
                base.queryPlan(), packet, answerability, planning.answerPlan(),
                planning.claimPolicy(), base.budget(), diagnoses, totalMicros, base.compiledAt());
    }

    private void socialEvidence(MutableEvidence out, UUID observer, UUID playerId,
            DialogueFrame frame, String utterance,
            ActorModelService.DisclosureEvaluation disclosure) {
        if (social == null || out.full()) return;
        if (frame.predicateKey().startsWith("BELIEVES_ACTOR_")) {
            UUID target = resolveActor(frame.subjectKey());
            if (target == null) return;
            int depth = frame.signals().contains("tom-depth-2-explicit") ? 2 : 1;
            List<BeliefAssertion> values = social.believedActorState(observer, target,
                    frame.predicateKey());
            out.addCandidates(values.size());
            for (BeliefAssertion value : values.stream().limit(4).toList()) {
                if (!matchesSocialQuery(value, frame.objectKey())) continue;
                EvidenceRef evidence = ref("social:" + value.assertionId(),
                        value.provenance().sourceKind(), value.status(), value.confidence(), true,
                        compact(value.statement(), 220), frame.subjectKey(), value.predicate(),
                        value.value(), value.learnedAt(), "PERSISTENT",
                        key(value.provenance().sourceActorId()), false, false, "",
                        "TOM_DEPTH_" + depth);
                out.rank(evidence.stableId(), "social ToMDepth=" + depth + " confidence="
                        + "%.3f".formatted(value.confidence()) + " source="
                        + value.provenance().sourceKind() + " evidence="
                        + value.provenance().evidenceIds());
                out.supportRanked(evidence, value.confidence(), .38, false);
            }
        }
        if (disclosure == null || !disclosure.applies()) return;
        out.rank("secret:" + disclosure.secret().secretId(), "disclosure="
                + disclosure.decision() + " policy="
                + disclosure.secret().disclosurePolicy() + " reason=" + disclosure.reason());
        if (disclosure.assertion() == null) {
            out.restrict("SECRET_CONTENT_DISCLOSURE"); return;
        }
        BeliefAssertion value = disclosure.assertion();
        EvidenceRef evidence = ref("secret-evidence:" + value.assertionId(),
                value.provenance().sourceKind(), value.status(), value.confidence(), true,
                compact(value.statement(), 220), key(value.subjectId()), value.predicate(),
                value.value(), value.learnedAt(), "PERSISTENT",
                key(value.provenance().sourceActorId()), false,
                value.provenance().authorityRank() >= 80, "", "SECRET_PERMITTED");
        out.supportRanked(evidence, value.confidence(), .38, false);
    }

    private UUID resolveActor(String key) {
        if (profiles == null || key == null || !key.startsWith("NPC_NAME:")) return null;
        return profiles.byName(key.substring("NPC_NAME:".length()).replace('_', ' '))
                .map(NpcProfile::id).orElse(null);
    }

    private static boolean matchesSocialQuery(BeliefAssertion value, String query) {
        Set<String> ignored = Set.of("does", "know", "knows", "what", "about", "that",
                "believe", "believes", "actor", "want", "wants", "feel", "feels",
                "prefer", "prefers", "intend", "intends");
        Set<String> wanted = java.util.Arrays.stream((query == null ? "" : query)
                        .toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(term -> term.length() >= 4 && !ignored.contains(term))
                .collect(java.util.stream.Collectors.toSet());
        String candidate = (value.statement() + " " + value.value()).toLowerCase(Locale.ROOT);
        return wanted.isEmpty() || wanted.stream().anyMatch(candidate::contains);
    }

    /** E4 exact typed belief lookup precedes memory/perception expansion. */
    private void persistentBeliefs(MutableEvidence out, UUID npcId, UUID playerId,
            DialogueFrame frame, String utterance) {
        if (beliefs == null || out.full()) return;
        String rawPredicate = frame.predicateKey();
        String predicate = switch (rawPredicate) {
            case "HELD_ITEM" -> "HOLDS";
            case "" -> "";
            default -> rawPredicate.startsWith("PROPERTY:") ? "HAS_PROPERTY"
                    : BeliefPredicateRegistry.canonical(rawPredicate);
        };
        // Actor-local social projections and secret disclosure are resolved below by
        // ActorModelService. A broad E4 lookup here would erase the target-actor boundary.
        if (predicate.startsWith("BELIEVES_ACTOR_") || predicate.equals("SECRET")
                || predicate.equals("SECRET_METADATA")
                || predicate.equals("KNOWLEDGE_TOPIC")) return;
        UUID subject = frame.subjectKey().equals("CURRENT_PLAYER")
                || predicate.equals("NAME") || predicate.equals("HOLDS") ? playerId : null;
        List<SourcedBeliefStore.RankedAssertion> ranked = beliefs.queryAssertionsReadOnly(
                npcId, subject, predicate, utterance, out.expansion,
                MAX_PER_SOURCE * 2, Instant.now());
        if (out.expansion.historical() && out.expansion.timeConstraintIsLoose()) {
            List<SourcedBeliefStore.RankedAssertion> historical = ranked.stream()
                    .filter(value -> value.assertion().status() == EpistemicStatus.SUPERSEDED)
                    .toList();
            if (!historical.isEmpty()) ranked = historical;
        }
        out.addCandidates(ranked.size());
        for (SourcedBeliefStore.RankedAssertion scored : ranked.stream()
                .limit(MAX_PER_SOURCE).toList()) {
            BeliefAssertion value = scored.assertion();
            // Older builds could misclassify quoted/vocative questions as player facts. Keep
            // those records for forensic/persistence compatibility, but never authorize an
            // answer with an interrogative proposition.
            if (looksInterrogative(value.statement())) continue;
            boolean direct = value.provenance().sourceKind()
                    == EvidenceSourceKind.DIRECT_OBSERVATION;
            boolean authoritative = value.provenance().authorityRank() >= 80;
            EvidenceRef evidence = ref("assertion:" + value.assertionId(),
                    value.provenance().sourceKind(), value.status(), value.confidence(), true,
                    compact(value.statement(), 220), key(value.subjectId()), value.predicate(),
                    value.value(), value.learnedAt(),
                    value.temporalScope().stability().name(),
                    key(value.provenance().sourceActorId()), direct, authoritative, "",
                    value.temporalScope().authoredReference());
            out.rank(evidence.stableId(), "belief total=%.3f semantic=%.3f temporal=%.3f "
                    .formatted(scored.score(), scored.semanticScore(), scored.temporalScore())
                    + "authority=%.3f status=%.3f".formatted(scored.authorityScore(),
                            scored.statusScore()));
            boolean singleton = authoritative || value.status() == EpistemicStatus.DISPUTED
                    || Set.of("NAME", "HOLDS", "IS_AT", "CURRENT_TASK")
                            .contains(value.predicate());
            out.supportRanked(evidence, scored.score(), .36, singleton);
        }
    }

    private static boolean looksInterrogative(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceFirst("^[\\\"'“”‘’]+", "")
                .replaceFirst("^[\\p{L}][\\p{L}'-]{0,31}\\s*[,.:;-]\\s*", "")
                .replaceAll("[^\\p{L}\\p{N}' ]", " ").replaceAll("\\s+", " ").strip();
        return normalized.matches("^(?:who|what|when|where|why|how|which|can|could|"
                + "would|will|do|does|did|is|are|am|have|has|should|may)\\b.*");
    }

    /**
     * E6 topic knowledge is strictly actor-local. Relationship rows prove acquaintance only;
     * they are never evidence that the observer knows a rumor or a property's details.
     */
    private void actorLocalTopicKnowledge(MutableEvidence out, UUID observer,
            String utterance) {
        if (beliefs == null || out.full()) return;
        Set<EvidenceSourceKind> admissible = Set.of(EvidenceSourceKind.PLAYER_TESTIMONY,
                EvidenceSourceKind.NPC_TESTIMONY, EvidenceSourceKind.DIRECT_OBSERVATION,
                EvidenceSourceKind.PERSISTENT_FACT, EvidenceSourceKind.EPISODIC_MEMORY,
                EvidenceSourceKind.AUTHORED_CANON, EvidenceSourceKind.DOCUMENTED_WORLD_LORE);
        List<SourcedBeliefStore.RankedAssertion> ranked = beliefs.queryAssertionsReadOnly(
                observer, null, "", utterance, out.expansion,
                MAX_PER_SOURCE * 3, Instant.now()).stream()
                .filter(value -> admissible.contains(
                        value.assertion().provenance().sourceKind()))
                .toList();
        out.addCandidates(ranked.size());
        for (SourcedBeliefStore.RankedAssertion scored : ranked.stream()
                .limit(MAX_PER_SOURCE).toList()) {
            BeliefAssertion value = scored.assertion();
            EvidenceRef evidence = ref("topic:" + value.assertionId(),
                    value.provenance().sourceKind(), value.status(), value.confidence(), true,
                    compact(value.statement(), 220), key(value.subjectId()), value.predicate(),
                    value.value(), value.learnedAt(),
                    value.temporalScope().stability().name(),
                    key(value.provenance().sourceActorId()),
                    value.provenance().sourceKind() == EvidenceSourceKind.DIRECT_OBSERVATION,
                    value.provenance().authorityRank() >= 80, "",
                    value.temporalScope().authoredReference());
            out.rank(evidence.stableId(), "actor-local-topic total=%.3f source=%s actor=%s"
                    .formatted(scored.score(), value.provenance().sourceKind(),
                            key(value.provenance().sourceActorId())));
            out.supportRanked(evidence, scored.score(), .38, false);
        }
        if (ranked.isEmpty()) out.prune("NO_ACTOR_LOCAL_TOPIC_TESTIMONY_OR_FACT");
    }

    private void identity(MutableEvidence out, UUID npcId, UUID playerId) {
        if (memories == null) return;
        List<MemoryRecord> values = memories.queryReadOnly(npcId, playerId,
                "stated player name identity", Set.of(MemoryType.PLAYER_FACT), 4);
        for (MemoryRecord value : values) {
            Matcher matcher = STATED_NAME.matcher(value.summary());
            if (!matcher.find()) { out.prune("IDENTITY_MEMORY_WITHOUT_STRUCTURED_NAME"); continue; }
            String name = matcher.group(1);
            out.support(ref("memory:" + value.memoryId(), EvidenceSourceKind.PLAYER_TESTIMONY,
                    EpistemicStatus.KNOWN, value.confidence(), true,
                    "The player's stated name is " + name + ".", "CURRENT_PLAYER", "NAME",
                    name, value.timestamp(), freshness(value.timestamp()),
                    key(value.playerId()), false, false, "", "PERSISTENT"), true);
        }
    }

    private void episodic(MutableEvidence out, UUID npcId, UUID playerId, String utterance) {
        if (memories != null) {
            MemoryStore.FactRetrieval retrieval = memories.queryFactsReadOnly(npcId, playerId,
                    utterance, Set.of(MemoryType.EPISODIC, MemoryType.PLAYER_FACT,
                            MemoryType.ACTION_RESULT, MemoryType.COMMITMENT), 6,
                    out.expansion, Instant.now());
            out.addCandidates(retrieval.candidateCount());
            for (MemoryStore.RejectedFact value : retrieval.rejected()) {
                out.reject("fact:" + value.factId(), value.reason(), value.score());
            }
            for (MemoryStore.ScoredFact scored : retrieval.selected()) {
                MemoryRecord value = scored.record();
                MemoryStore.FactUnit fact = scored.fact();
                String evidencePredicate = episodicPredicate(fact.statement(), value.type(),
                        value.type() == MemoryType.ACTION_RESULT ? "ACTION_RESULT" : "");
                if (!episodicPredicateCompatible(out.base.dialogueFrame().predicateKey(),
                        evidencePredicate)) {
                    out.reject("fact:" + fact.factId(), "PREDICATE_MISMATCH:"
                            + evidencePredicate + "->"
                            + out.base.dialogueFrame().predicateKey(), scored.score());
                    continue;
                }
                EvidenceRef evidence = ref("fact:" + fact.factId(), source(value),
                        value.type() == MemoryType.ACTION_RESULT ? EpistemicStatus.KNOWN
                                : EpistemicStatus.BELIEVED,
                        value.confidence(), value.confidence() != null,
                        compact(fact.statement(), 220), "CURRENT_PLAYER", evidencePredicate,
                        compact(fact.statement(), 120), value.timestamp(), freshness(value.timestamp()),
                        key(value.playerId()), false, value.type() == MemoryType.ACTION_RESULT,
                        "", out.expansion.temporalMode());
                out.rank(evidence.stableId(), "memory-fact total=%.3f exact=%.3f semantic=%.3f "
                        .formatted(scored.score(), scored.breakdown().exactMatch(),
                                scored.breakdown().semanticRelevance())
                        + "temporal=%.3f importance=%.3f confidence=%.3f source=%s"
                                .formatted(scored.breakdown().temporalCompatibility(),
                                        scored.breakdown().importance(),
                                        scored.breakdown().confidence(), fact.sessionSource()));
                out.rank(evidence.stableId() + ":influence",
                        "relationship=%.3f emotion=%.3f goal=%.3f topic=%.3f"
                                .formatted(scored.breakdown().relationshipRelevance(),
                                        scored.breakdown().emotionalRelevance(),
                                        scored.breakdown().goalRelevance(),
                                        scored.breakdown().currentTopicRelevance()));
                out.factSessionSource = fact.sessionSource();
                out.supportRanked(evidence, scored.score(), .38, false);
            }
        }
        if (beliefs != null && !out.full() && out.supporting.isEmpty()) {
            for (SourcedBelief value : beliefs.queryReadOnly(npcId, playerId, "", utterance, 3)) {
                String evidencePredicate = episodicPredicate(value.proposition(), null,
                        value.predicate());
                if (!episodicPredicateCompatible(out.base.dialogueFrame().predicateKey(),
                        evidencePredicate)) continue;
                out.support(ref("belief:" + value.beliefId(), EvidenceSourceKind.PLAYER_TESTIMONY,
                        EpistemicStatus.BELIEVED, value.confidence(), true,
                        compact(value.proposition(), 220), key(value.subjectEntityId()),
                        evidencePredicate, value.object(), value.timestamp(),
                        freshness(value.timestamp()), key(value.sourceEntityId()), false, false,
                        "", value.temporalReference()), false);
            }
        }
    }

    /** Prevents semantic laundering (for example possession becoming a hiding event). */
    static String episodicPredicate(String statement, MemoryType type, String storedPredicate) {
        String text = statement == null ? "" : statement;
        if (text.matches("(?is).*\\b(?:i|you|we)\\s+(?:(?:have|had)\\s+)?"
                + "(?:hide|hid|hidden|left|put|dropped)\\b.*")) return "PAST_EVENT";
        if (text.matches("(?is).*\\bi\\s+(?:have|own|carry|keep|possess)\\b.*")) {
            return "POSSESSION";
        }
        if (type == MemoryType.ACTION_RESULT || type == MemoryType.COMMITMENT
                || "ACTION_RESULT".equals(storedPredicate)) return "PAST_EVENT";
        String stored = storedPredicate == null ? "" : storedPredicate.strip();
        if (stored.equals("LOCATION_REPORT") && text.matches(
                "(?is).*\\b(?:hidden|hid|left|put|dropped)\\b.*")) return "PAST_EVENT";
        return stored.isBlank() ? "UNRESOLVED" : stored;
    }

    static boolean episodicPredicateCompatible(String requested, String evidence) {
        String wanted = requested == null ? "" : requested.strip();
        String actual = evidence == null ? "" : evidence.strip();
        if (wanted.equals("PAST_EVENT")) return actual.equals("PAST_EVENT");
        if (wanted.equals("HOLDS")) return actual.equals("HOLDS")
                || actual.equals("HELD_ITEM") || actual.equals("POSSESSION");
        if (wanted.equals("PAST_ACTIVITY")) return actual.equals("PAST_EVENT")
                || actual.equals("CURRENT_TASK");
        return !wanted.isBlank() && wanted.equals(actual);
    }

    private void perception(MutableEvidence out, RawPerceptionSnapshot raw,
            CognitionContext cognition) {
        if (raw == null || raw.engineSnapshot().npcEntityId() == null) return;
        NpcPerceptionSnapshot snapshot = raw.engineSnapshot();
        DialogueFrame frame = out.base.dialogueFrame();
        if (frame.predicateKey().equals("HELD_ITEM")) {
            PerceivedItem item = snapshot.focusedPlayerHeldItem();
            String object = item == null ? "NONE" : item.displayName() == null
                    || item.displayName().isBlank() ? item.itemId() : item.displayName();
            out.support(ref("perception:" + out.responseId + ":held-item",
                    EvidenceSourceKind.DIRECT_OBSERVATION, EpistemicStatus.KNOWN,
                    -1, false, item == null ? "The player is holding nothing."
                            : "The player is holding " + object + ".",
                    "CURRENT_PLAYER", "HELD_ITEM", object, raw.capturedAt(), "CURRENT",
                    "HYTALE_WORLD", true, true, key(snapshot.worldId()), "CURRENT"), true);
            return;
        }
        SemanticWorldModel semantic = cognition != null && cognition.semanticWorld() != null
                ? cognition.semanticWorld() : null;
        if (semantic == null) return;
        int index = 0;
        if (!semantic.terrain().isBlank()) out.support(scene(out, raw, "TERRAIN",
                semantic.terrain(), index++), false);
        for (String value : semantic.meaningfulObjects()) {
            out.support(scene(out, raw, "VISIBLE_OBJECT", value, index++), false);
        }
        for (String value : semantic.visiblePlayers()) {
            out.support(scene(out, raw, "VISIBLE_PLAYER", value, index++), false);
        }
        for (String value : semantic.visibleNpcs()) {
            out.support(scene(out, raw, "VISIBLE_NPC", value, index++), false);
        }
    }

    private EvidenceRef scene(MutableEvidence out, RawPerceptionSnapshot raw,
            String predicate, String value, int index) {
        return ref("perception:" + out.responseId + ":scene:" + index,
                EvidenceSourceKind.DIRECT_OBSERVATION, EpistemicStatus.KNOWN,
                -1, false, predicate.replace('_', ' ').toLowerCase(Locale.ROOT)
                        + ": " + compact(value, 140), "CURRENT_SCENE", predicate,
                compact(value, 120), raw.capturedAt(), "CURRENT", "HYTALE_WORLD", true,
                true, key(raw.engineSnapshot().worldId()), "CURRENT");
    }

    private void selfState(MutableEvidence out, NpcProfile profile, CognitionContext cognition) {
        if (tasks != null) {
            for (NpcTask task : tasks.activeFor(profile.id()).stream().limit(3).toList()) {
                String activity = task.type() + (task.purpose() == null || task.purpose().isBlank()
                        ? "" : ": " + task.purpose());
                out.support(ref("task:" + task.taskId(), EvidenceSourceKind.SELF_STATE,
                        EpistemicStatus.KNOWN, -1, false,
                        profile.name() + "'s current task is " + compact(activity, 140) + ".",
                        "CURRENT_NPC", "CURRENT_TASK", activity, task.createdAt(), "CURRENT",
                        "CURRENT_NPC", true, true, key(task.worldId()), "CURRENT"), true);
            }
        }
        if (cognition != null && !out.full()) {
            AgentOperation operation = cognition.activeOperation();
            if (operation != null) out.support(ref("operation:" + operation.operationId(),
                    EvidenceSourceKind.SELF_STATE, EpistemicStatus.KNOWN, -1, false,
                    profile.name() + " has active operation " + operation.kind() + ".",
                    "CURRENT_NPC", "ACTIVE_OPERATION", operation.kind(), operation.startedAt(),
                    "CURRENT", "CURRENT_NPC", true, true, "", "CURRENT"), false);
        }
        if (out.supporting.isEmpty() && cognition != null
                && cognition.semanticWorld() != null) {
            String activity = cognition.semanticWorld().selfState().currentActivity();
            out.support(ref("self-state:" + out.responseId, EvidenceSourceKind.SELF_STATE,
                    EpistemicStatus.KNOWN, -1, false,
                    profile.name() + " is currently " + activity + ".", "CURRENT_NPC",
                    "CURRENT_TASK", activity, cognition.capturedAt(), "CURRENT", "CURRENT_NPC",
                    true, true, "", "CURRENT"), true);
        }
    }

    private void relationships(MutableEvidence out, NpcProfile profile, UUID playerId) {
        if (relationships == null) return;
        String target = out.base.dialogueFrame().targetKey();
        List<RelationshipRecord> candidates;
        if (target.equals("CURRENT_PLAYER")) {
            candidates = relationships.get(profile.id(), playerId).stream().toList();
        } else if (target.startsWith("NPC_NAME:")) {
            String name = target.substring("NPC_NAME:".length()).replace('_', ' ');
            UUID targetId = profiles == null ? null : profiles.byName(name)
                    .map(NpcProfile::id).orElse(null);
            candidates = targetId == null ? List.of()
                    : relationships.get(profile.id(), targetId).stream().toList();
        } else {
            candidates = relationships.forNpc(profile.id()).stream()
                    .filter(RelationshipRecord::knowsEntity)
                    .filter(value -> value.relationshipType() != null
                            && value.relationshipType().toUpperCase(Locale.ROOT).contains("FRIEND"))
                    .limit(4).toList();
        }
        for (RelationshipRecord value : candidates) {
            if (!value.knowsEntity()) continue;
            String other = profiles == null ? key(value.playerId())
                    : profiles.byId(value.playerId()).map(NpcProfile::name)
                            .orElse(value.playerId().equals(playerId)
                                    ? "the current player" : key(value.playerId()));
            String relation = value.relationshipType() == null
                    || value.relationshipType().isBlank() ? "known person"
                            : value.relationshipType().toLowerCase(Locale.ROOT).replace('_', ' ');
            boolean authored = value.interactionCount() == 0 && value.lastInteraction() == null
                    && !relation.equals("known person");
            out.support(ref("relationship:" + profile.id() + ":" + value.playerId(),
                    authored ? EvidenceSourceKind.AUTHORED_CANON
                            : EvidenceSourceKind.RELATIONSHIP_STATE,
                    EpistemicStatus.KNOWN, -1, false,
                    profile.name() + " has a " + relation + " relationship with " + other + ".",
                    "CURRENT_NPC", "RELATIONSHIP", other + ":" + relation,
                    value.lastInteraction(), value.lastInteraction() == null
                            ? "PERSISTENT" : freshness(value.lastInteraction()),
                    "CURRENT_NPC", true, true, "", "PERSISTENT"), false);
        }
    }

    private void preference(MutableEvidence out, NpcProfile profile, String utterance,
            NpcSelfModel selfModel) {
        String requested = out.base.dialogueFrame().predicateKey();
        if (requested.equals("DESIRE")) {
            String desire = profile.goals().stream().filter(value -> value != null
                            && !value.isBlank()).findFirst()
                    .orElse(selfModel == null ? "" : selfModel.currentGoal());
            if (!desire.isBlank()) out.support(ref("profile:" + profile.id() + ":desire",
                    EvidenceSourceKind.AUTHORED_CANON, EpistemicStatus.KNOWN, -1, false,
                    profile.name() + " wants " + compact(desire, 160) + ".", "CURRENT_NPC",
                    "DESIRE", compact(desire, 140), null, "PERSISTENT", "AUTHORED_PROFILE",
                    true, true, "", "PERSISTENT"), true);
            return;
        }
        if (requested.equals("EMOTION")) {
            String emotion = selfModel == null || selfModel.emotion() == null
                    || selfModel.emotion().emotion() == null ? ""
                            : selfModel.emotion().emotion().name();
            if (!emotion.isBlank()) out.support(ref("self-state:" + out.responseId + ":emotion",
                    EvidenceSourceKind.SELF_STATE, EpistemicStatus.KNOWN, -1, false,
                    profile.name() + " currently feels " + emotion.toLowerCase(Locale.ROOT) + ".",
                    "CURRENT_NPC", "EMOTION", emotion, selfModel.emotion().updatedAt(),
                    "CURRENT", "CURRENT_NPC", true, true, "", "CURRENT"), true);
            return;
        }
        String target = out.base.dialogueFrame().targetKey().replaceFirst("^CONCEPT:", "")
                .replace('_', ' ').strip();
        String matched = profile.likes().stream().filter(value -> conceptMatches(value, target))
                .findFirst().orElse(null);
        String predicate = "LIKE";
        if (matched == null) {
            matched = profile.dislikes().stream().filter(value -> conceptMatches(value, target))
                    .findFirst().orElse(null);
            predicate = "DISLIKE";
        }
        if (matched != null) out.support(ref("profile:" + profile.id() + ":preference:"
                        + normalize(matched), EvidenceSourceKind.AUTHORED_CANON,
                EpistemicStatus.KNOWN, -1, false,
                profile.name() + " has an authored " + predicate.toLowerCase(Locale.ROOT)
                        + " preference for " + matched + ".", "CURRENT_NPC", predicate,
                matched, null, "PERSISTENT", "AUTHORED_PROFILE", true, true, "",
                "PERSISTENT"), true);
    }

    private void action(MutableEvidence out, NpcProfile profile, List<String> validActions) {
        String requested = normalize(out.base.queryPlan().requestedAction());
        boolean available = validActions != null && validActions.stream()
                .map(EpistemicEvidenceRetriever::normalize).anyMatch(requested::equals);
        if (!available) return;
        out.support(ref("action-capability:" + profile.id() + ":" + requested,
                EvidenceSourceKind.ACTION_CAPABILITY, EpistemicStatus.KNOWN, -1, false,
                profile.name() + " has the capability " + requested + ".", "CURRENT_NPC",
                "ACTION_CAPABILITY", requested, null, "CURRENT", "ACTION_REGISTRY", true,
                true, "", "CURRENT"), true);
    }

    private void clarification(MutableEvidence out, String proposition) {
        if (proposition == null || proposition.isBlank()) return;
        out.support(ref("workspace:" + out.responseId + ":prior-npc-claim",
                EvidenceSourceKind.CONVERSATION_WORKSPACE, EpistemicStatus.KNOWN,
                -1, false, compact(proposition, 240), "PRIOR_NPC_CLAIM", "MEANING",
                compact(proposition, 180), Instant.now(), "CURRENT_CONVERSATION",
                "CURRENT_NPC", true, true, "", "CURRENT_CONVERSATION"), true);
    }

    private void correction(MutableEvidence out, DialogueFrame frame, UUID playerId) {
        String value = frame.objectKey()
                .replaceFirst("^(?:PERSON_NAME|CORRECTED_VALUE):", "").replace('_', ' ');
        if (value.isBlank()) return;
        String proposition;
        if (frame.predicateKey().equals("NAME")) {
            proposition = "The player states that their name is " + value + ".";
        } else if (frame.predicateKey().equals("RESIDENCE")) {
            proposition = "The player states that they live in " + value + ".";
        } else {
            String subject = frame.subjectKey().replaceFirst("^OBJECT:", "")
                    .replace('_', ' ').toLowerCase(Locale.ROOT);
            proposition = "The player corrects " + subject + " to " + value + ".";
        }
        out.support(ref("turn:" + out.responseId + ":player-correction",
                EvidenceSourceKind.PLAYER_TESTIMONY, EpistemicStatus.KNOWN, -1, false,
                proposition, frame.subjectKey(), frame.predicateKey(), value,
                Instant.now(), "CURRENT_TURN", key(playerId), true, false,
                "", "CURRENT_TURN"), true);
    }

    private void objectiveProperty(MutableEvidence out, RawPerceptionSnapshot raw) {
        if (raw == null || raw.engineSnapshot().npcEntityId() == null) return;
        PerceivedItem item = raw.engineSnapshot().focusedPlayerHeldItem();
        if (item == null || !itemMatches(item, out.base.dialogueFrame().subjectKey())) return;
        String itemName = item.displayName() == null || item.displayName().isBlank()
                ? item.itemId() : item.displayName();
        out.context(ref("perception:" + out.responseId + ":entity-exists",
                EvidenceSourceKind.DIRECT_OBSERVATION, EpistemicStatus.KNOWN, -1, false,
                "The player is holding " + itemName + ".", "CURRENT_PLAYER", "HELD_ITEM",
                itemName, raw.capturedAt(), "CURRENT", "HYTALE_WORLD", true, true,
                key(raw.engineSnapshot().worldId()), "CURRENT"));
        String property = out.base.dialogueFrame().predicateKey().replaceFirst("^PROPERTY:", "");
        if ((property.equals("DURABILITY") || property.equals("DAMAGED"))
                && item.maxDurability() > 0) {
            String value = property.equals("DAMAGED")
                    ? Boolean.toString(item.durability() < item.maxDurability())
                    : Math.round(item.durability()) + "/" + Math.round(item.maxDurability());
            out.support(ref("perception:" + out.responseId + ":property:" + property,
                    EvidenceSourceKind.DIRECT_OBSERVATION, EpistemicStatus.KNOWN,
                    -1, false, itemName + " " + property.toLowerCase(Locale.ROOT)
                            + " is " + value + ".", out.base.dialogueFrame().subjectKey(),
                    out.base.dialogueFrame().predicateKey(), value, raw.capturedAt(), "CURRENT",
                    "HYTALE_WORLD", true, true, key(raw.engineSnapshot().worldId()), "CURRENT"),
                    true);
        }
    }

    private static EvidenceRef ref(String id, EvidenceSourceKind source, EpistemicStatus status,
            double confidence, boolean confidenceKnown, String proposition, String subject,
            String predicate, String object, Instant acquiredAt, String freshness,
            String actor, boolean direct, boolean authoritative, String world,
            String temporal) {
        return new EvidenceRef(EvidenceRef.SCHEMA_VERSION, id, source, status, confidence,
                confidenceKnown, compact(proposition, 260), subject, predicate, object,
                acquiredAt, freshness, actor, direct, authoritative, world, temporal);
    }

    private static EvidenceSourceKind source(MemoryRecord value) {
        if (value.type() == MemoryType.ACTION_RESULT) return EvidenceSourceKind.ACTION_RESULT;
        if (value.source() != null && value.source().startsWith("PLAYER_REPORT:")) {
            return EvidenceSourceKind.PLAYER_TESTIMONY;
        }
        return value.type() == MemoryType.PLAYER_FACT ? EvidenceSourceKind.PERSISTENT_FACT
                : EvidenceSourceKind.EPISODIC_MEMORY;
    }

    private static boolean conceptMatches(String authored, String target) {
        String a = normalize(authored); String t = normalize(target);
        return !a.isBlank() && !t.isBlank() && (a.equals(t) || a.contains(t) || t.contains(a));
    }

    private static boolean itemMatches(PerceivedItem item, String subjectKey) {
        String wanted = normalize(subjectKey.replaceFirst("^OBJECT:", ""));
        return normalize(item.itemId()).contains(wanted)
                || normalize(item.displayName()).contains(wanted);
    }

    private static String freshness(Instant at) {
        if (at == null) return "UNSPECIFIED";
        long minutes = Math.max(0, Duration.between(at, Instant.now()).toMinutes());
        return minutes < 5 ? "CURRENT" : minutes < 1_440 ? "RECENT" : "HISTORICAL";
    }

    private static EpistemicQueryKind kind(String value) {
        try { return EpistemicQueryKind.valueOf(value); }
        catch (RuntimeException ignored) { return EpistemicQueryKind.UNRESOLVED; }
    }

    private static String key(UUID value) { return value == null ? "UNSPECIFIED" : value.toString(); }
    private static String normalize(String value) { return value == null ? "" : value.strip()
            .toUpperCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "_")
            .replaceAll("^_+|_+$", ""); }
    private static String compact(String value, int maximum) { String text = value == null ? ""
            : value.replaceAll("\\s+", " ").strip(); return text.length() <= maximum ? text
                    : text.substring(0, maximum); }
    private static boolean contains(String value, String... needles) { String text = value == null
            ? "" : value.toLowerCase(Locale.ROOT); for (String needle : needles)
                if (text.contains(needle)) return true; return false; }

    private static final class MutableEvidence {
        private final EpistemicContract base;
        private final UUID responseId, npcId, playerId, worldId;
        private final EpistemicBudget budget;
        private final long started;
        private final E5QueryExpansion expansion;
        private final ConversationWorkspace.Snapshot workspace;
        private final List<EvidenceRef> supporting = new ArrayList<>();
        private final List<EvidenceRef> contradicting = new ArrayList<>();
        private final List<EvidenceRef> contextual = new ArrayList<>();
        private final List<String> restrictions = new ArrayList<>();
        private final List<String> prunedReasons = new ArrayList<>();
        private final EnumMap<EvidenceSourceKind, Integer> perSource =
                new EnumMap<>(EvidenceSourceKind.class);
        private final LinkedHashMap<String, String> rankings = new LinkedHashMap<>();
        private final List<String> rejectedLowConfidence = new ArrayList<>();
        private int candidateCount;
        private String factSessionSource = "";
        private boolean partialSupport;
        private int estimatedTokens, pruned;
        private boolean budgetExhausted;

        private MutableEvidence(EpistemicContract base, UUID responseId, UUID npcId,
                UUID playerId, UUID worldId, EpistemicBudget budget, long started,
                E5QueryExpansion expansion, ConversationWorkspace.Snapshot workspace) {
            this.base = base; this.responseId = responseId; this.npcId = npcId;
            this.playerId = playerId; this.worldId = worldId; this.budget = budget;
            this.started = started; this.expansion = expansion; this.workspace = workspace;
            this.restrictions.addAll(base.evidence().restrictions());
        }

        private void addCandidates(int count) { candidateCount += Math.max(0, count); }
        private void rank(String id, String value) {
            if (rankings.size() < 8) rankings.put(id, value);
        }
        private void reject(String id, String reason, double score) {
            if (rejectedLowConfidence.size() < 8) rejectedLowConfidence.add(
                    id + ":" + reason + ":" + "%.3f".formatted(score));
        }
        private void supportRanked(EvidenceRef value, double score, double threshold,
                boolean singleton) {
            if (score < threshold) {
                reject(value.stableId(), "BELOW_SUFFICIENCY_GATE", score);
                prune("LOW_CONFIDENCE_OR_IRRELEVANT_EVIDENCE");
                return;
            }
            if (score < .55) partialSupport = true;
            support(value, singleton);
        }

        private void support(EvidenceRef value, boolean singleton) {
            if (!admit(value)) return;
            if (singleton) {
                EvidenceRef prior = supporting.stream().filter(existing ->
                        existing.subjectKey().equals(value.subjectKey())
                                && existing.predicateKey().equals(value.predicateKey()))
                        .findFirst().orElse(null);
                if (prior != null && !prior.objectValue().equalsIgnoreCase(value.objectValue())) {
                    contradicting.add(value); return;
                }
            }
            supporting.add(value);
        }

        private void context(EvidenceRef value) { if (admit(value)) contextual.add(value); }
        private void restrict(String value) { if (!restrictions.contains(value)) restrictions.add(value); }
        private void prune(String reason) { pruned++; if (prunedReasons.size() < 8
                && !prunedReasons.contains(reason)) prunedReasons.add(reason); }
        private boolean full() { return supporting.size() + contradicting.size()
                + contextual.size() >= Math.min(budget.maxEvidenceItems(),
                        base.queryPlan().maxEvidenceItems()); }

        private boolean admit(EvidenceRef value) {
            if (value == null) return false;
            if (!base.queryPlan().allowedSources().contains(value.sourceKind())) {
                prune("SOURCE_NOT_ADMISSIBLE:" + value.sourceKind()); return false;
            }
            if (System.nanoTime() - started > budget.maximumMillis() * 1_000_000L) {
                budgetExhausted = true;
                // Do not throw away the first already-resolved exact fact merely because JVM
                // class initialization consumed the cold-path budget. Expansion stops here.
                if (supporting.size() + contradicting.size() + contextual.size() >= 2) {
                    prune("RETRIEVAL_TIME_BUDGET"); return false;
                }
            }
            int maxItems = Math.min(budget.maxEvidenceItems(), base.queryPlan().maxEvidenceItems());
            int tokens = Math.max(1, (value.compactProposition().length() + 3) / 4);
            if (full() || estimatedTokens + tokens > Math.min(budget.maxEvidenceTokens(),
                    base.queryPlan().maxTokens())) {
                budgetExhausted = true; prune("EVIDENCE_ITEM_OR_TOKEN_BUDGET"); return false;
            }
            int count = perSource.getOrDefault(value.sourceKind(), 0);
            if (count >= MAX_PER_SOURCE) { prune("PER_SOURCE_LIMIT:" + value.sourceKind());
                return false; }
            perSource.put(value.sourceKind(), count + 1); estimatedTokens += tokens; return true;
        }

        private EvidencePacket finish() {
            EvidenceSufficiency sufficiency = !contradicting.isEmpty()
                    ? EvidenceSufficiency.CONFLICTED : !supporting.isEmpty()
                            ? supporting.stream().allMatch(value -> value.status()
                                    == EpistemicStatus.EXPIRED) ? EvidenceSufficiency.STALE
                                    : partialSupport ? EvidenceSufficiency.PARTIAL
                                            : EvidenceSufficiency.SUFFICIENT
                                    : !contextual.isEmpty() ? EvidenceSufficiency.NONE
                                    : !rejectedLowConfidence.isEmpty()
                                            ? EvidenceSufficiency.IRRELEVANT
                                    : base.dialogueFrame().inputQualityConcern()
                                            || base.queryPlan().ambiguous()
                                                    ? EvidenceSufficiency.UNRESOLVED
                                                    : EvidenceSufficiency.NONE;
            LinkedHashSet<EvidenceSourceKind> sources = new LinkedHashSet<>();
            supporting.forEach(value -> sources.add(value.sourceKind()));
            contradicting.forEach(value -> sources.add(value.sourceKind()));
            contextual.forEach(value -> sources.add(value.sourceKind()));
            List<String> unknown = new ArrayList<>();
            if (supporting.isEmpty() && !base.dialogueFrame().predicateKey().isBlank()) {
                unknown.add(base.dialogueFrame().predicateKey());
            }
            List<String> selectedIds = java.util.stream.Stream.of(supporting,
                            contradicting, contextual).flatMap(java.util.Collection::stream)
                    .map(EvidenceRef::stableId).toList();
            RetrievalDiagnostics diagnostics = new RetrievalDiagnostics(expansion.terms(),
                    expansion.temporalMode(), candidateCount, rankings, selectedIds,
                    rejectedLowConfidence, factSessionSource, "RAM_ONLY",
                    workspace == null ? List.of() : java.util.stream.Stream.of(
                            workspace.currentTopic(), workspace.suspendedPriorTopic())
                            .filter(value -> value != null && !value.isBlank())
                            .collect(java.util.stream.Collectors.collectingAndThen(
                                    java.util.stream.Collectors.toCollection(ArrayList::new),
                                    values -> { values.addAll(workspace.openTopics());
                                        return List.copyOf(values); })),
                    workspace == null ? List.of() : workspace.activeEntities(),
                    workspace == null ? List.of() : workspace.commitments());
            return new EvidencePacket(EvidencePacket.SCHEMA_VERSION, responseId, npcId,
                    playerId, worldId, base.queryPlan().queryKind(),
                    base.dialogueFrame().subjectKey(), base.dialogueFrame().predicateKey(),
                    supporting, contradicting, contextual, unknown, sufficiency,
                    restrictions, base.evidence().omittedExistingEvidence(), pruned,
                    prunedReasons, List.copyOf(sources), estimatedTokens,
                    (System.nanoTime() - started) / 1_000L, budgetExhausted, diagnostics);
        }
    }
}
