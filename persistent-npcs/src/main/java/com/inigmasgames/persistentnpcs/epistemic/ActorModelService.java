package com.inigmasgames.persistentnpcs.epistemic;

import com.inigmasgames.persistentnpcs.cognition.SourcedBeliefStore;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.relationship.RelationshipRecord;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * E6 bounded actor-local social projection. Relationship values stay in RelationshipStore and
 * all durable social assertions/secret metadata stay in the E4 event store.
 */
public final class ActorModelService {
    public static final int MAX_TOM_DEPTH = 2;
    private static final double DEFAULT_TRANSMISSION_FACTOR = .82;
    private static final int MAX_ACTOR_ASSERTIONS = 16;
    private final SourcedBeliefStore beliefs;
    private final RelationshipStore relationships;
    private final NpcProfileRegistry profiles;
    private final double transmissionFactor;
    private final Map<UUID, SecretMetadata> secrets = new LinkedHashMap<>();

    public ActorModelService(SourcedBeliefStore beliefs, RelationshipStore relationships,
            NpcProfileRegistry profiles) {
        this.beliefs = beliefs; this.relationships = relationships; this.profiles = profiles;
        this.transmissionFactor = configuredTransmissionFactor();
        reloadSecrets();
    }

    public ActorModel snapshot(UUID observer, UUID target) {
        RelationshipRecord relationship = relationships == null ? null
                : relationships.get(observer, target).orElse(null);
        List<BeliefAssertion> facts = beliefs == null ? List.of()
                : beliefs.current(observer, target, "").stream()
                        .limit(MAX_ACTOR_ASSERTIONS).toList();
        List<BeliefAssertion> nested = believedKnowledge(observer, target).stream()
                .limit(MAX_ACTOR_ASSERTIONS).toList();
        List<UUID> commitments = facts.stream().filter(value -> Set.of("PROMISED_TO", "OWES",
                        "INTENDS").contains(value.predicate()))
                .map(BeliefAssertion::assertionId).toList();
        List<UUID> sharedSecrets = secrets.values().stream()
                .filter(value -> value.ownerId().equals(observer)
                        && value.permittedRecipients().contains(target))
                .map(SecretMetadata::secretId).limit(8).toList();
        return new ActorModel(observer, target, relationship,
                facts.stream().map(BeliefAssertion::assertionId).toList(),
                believedActorState(observer, target, "BELIEVES_ACTOR_PREFERS").stream()
                        .map(BeliefAssertion::assertionId).toList(),
                java.util.stream.Stream.concat(
                        believedActorState(observer, target, "BELIEVES_ACTOR_WANTS").stream(),
                        believedActorState(observer, target, "BELIEVES_ACTOR_INTENDS").stream())
                        .map(BeliefAssertion::assertionId).toList(),
                nested.stream().map(BeliefAssertion::assertionId).toList(), commitments,
                sharedSecrets, relationship == null || relationship.familiarity() == null
                        ? 0 : relationship.familiarity(),
                relationship == null ? null : relationship.lastInteraction());
    }

    /** Only a canonical, actually delivered authorized proposition may cross actors. */
    public TestimonyResult ingestDeliveredTestimony(UUID recipientNpcId, UUID senderActorId,
            BeliefAssertion original, String canonicalDeliveryEvidenceId, Instant receivedAt) {
        if (beliefs == null || recipientNpcId == null || senderActorId == null
                || original == null) throw new IllegalArgumentException("complete testimony required");
        if (canonicalDeliveryEvidenceId == null
                || !canonicalDeliveryEvidenceId.startsWith("CANONICAL_DELIVERY:")) {
            throw new IllegalArgumentException("actually delivered canonical evidence required");
        }
        if (original.provenance().generatedSpeechOnly()) {
            throw new IllegalArgumentException("generated speech is not social evidence");
        }
        List<UUID> chain = sourceChain(original);
        if (chain.isEmpty() || !chain.getLast().equals(senderActorId)) chain.add(senderActorId);
        int depth = transmissionDepth(original) + 1;
        if (depth > MAX_TOM_DEPTH) throw new IllegalArgumentException("gossip depth exceeds policy");
        double trust = trustFactor(recipientNpcId, senderActorId);
        double freshness = freshness(original.lastConfirmedAt(), receivedAt);
        double confidence = clamp(original.confidence() * trust
                * Math.pow(transmissionFactor, depth) * freshness);
        List<String> evidence = new ArrayList<>(List.of(
                "ORIGINAL_ASSERTION:" + original.assertionId(), canonicalDeliveryEvidenceId,
                "SOCIAL_CHAIN:" + chain.stream().map(UUID::toString)
                        .collect(java.util.stream.Collectors.joining(">")),
                "SOCIAL_DEPTH:" + depth, "SOCIAL_TRUST:" + "%.4f".formatted(trust)));
        BeliefProvenance provenance = new BeliefProvenance(EvidenceSourceKind.NPC_TESTIMONY,
                senderActorId, evidence, false, false);
        BeliefAssertion testimony = beliefs.assertBelief(new BeliefProposal(null,
                recipientNpcId, original.subjectId(), original.subject(), original.predicate(),
                original.value(), original.statement(), original.polarity(),
                EpistemicStatus.BELIEVED, confidence, provenance, original.temporalScope(),
                original.assertionScope(), List.of(original.assertionId()), receivedAt));
        BeliefAssertion actorKnows = recordBelievedKnowledge(recipientNpcId, senderActorId,
                original, depth, provenance, receivedAt, true);
        return new TestimonyResult(testimony, actorKnows, List.copyOf(chain), depth, trust,
                confidence, false);
    }

    public BeliefAssertion recordBelievedKnowledge(UUID observer, UUID target,
            BeliefAssertion proposition, int depth, BeliefProvenance provenance,
            Instant at, boolean explicitlyRequired) {
        return recordActorBelief(observer, target, "BELIEVES_ACTOR_KNOWS", proposition,
                depth, provenance, at, explicitlyRequired);
    }

    /** Records one bounded typed depth-1/2 social projection; never mutates the target actor. */
    public BeliefAssertion recordActorBelief(UUID observer, UUID target, String predicate,
            BeliefAssertion proposition, int depth, BeliefProvenance provenance,
            Instant at, boolean explicitlyRequired) {
        if (depth < 1 || depth > MAX_TOM_DEPTH || depth == 2 && !explicitlyRequired) {
            throw new IllegalArgumentException("nested belief depth is not authorized");
        }
        if (proposition == null || provenance == null || provenance.evidenceIds().isEmpty()) {
            throw new IllegalArgumentException("nested belief provenance/support required");
        }
        String actorPredicate = BeliefPredicateRegistry.canonical(predicate);
        if (!Set.of("BELIEVES_ACTOR_KNOWS", "BELIEVES_ACTOR_WANTS",
                "BELIEVES_ACTOR_FEELS", "BELIEVES_ACTOR_PREFERS",
                "BELIEVES_ACTOR_INTENDS").contains(actorPredicate)) {
            throw new IllegalArgumentException("unsupported actor-belief predicate");
        }
        UUID semanticSubject = UUID.nameUUIDFromBytes((target + "|" + proposition.assertionId())
                .getBytes(StandardCharsets.UTF_8));
        double confidence = clamp(proposition.confidence() * Math.pow(.82, depth));
        return beliefs.assertBelief(new BeliefProposal(null, observer, semanticSubject,
                "actor:" + target, actorPredicate,
                proposition.assertionId() + ":" + proposition.statement(),
                "The observer " + actorPredicate.toLowerCase(Locale.ROOT)
                        .replace("believes_actor_", "believes actor " + target + " ") + ": "
                        + proposition.statement(), BeliefAssertion.Polarity.POSITIVE,
                EpistemicStatus.BELIEVED, confidence, provenance,
                proposition.temporalScope(), BeliefAssertion.AssertionScope.RELATIONSHIP,
                List.of(proposition.assertionId()), at));
    }

    public List<BeliefAssertion> believedKnowledge(UUID observer, UUID target) {
        return believedActorState(observer, target, "BELIEVES_ACTOR_KNOWS");
    }

    public List<BeliefAssertion> believedActorState(UUID observer, UUID target,
            String predicate) {
        if (beliefs == null) return List.of();
        String actor = "actor:" + target;
        String typed = BeliefPredicateRegistry.canonical(predicate);
        if (!typed.startsWith("BELIEVES_ACTOR_")) return List.of();
        return beliefs.current(observer, null, typed).stream()
                .filter(value -> value.subject().equals(actor)).limit(MAX_ACTOR_ASSERTIONS).toList();
    }

    public SecretMetadata registerSecret(UUID owner, UUID underlyingAssertionId,
            AudiencePolicy audience, DisclosureDecision policy, Set<UUID> recipients,
            Instant validUntil) {
        if (beliefs == null || owner == null || underlyingAssertionId == null
                || beliefs.assertion(underlyingAssertionId).isEmpty()) {
            throw new IllegalArgumentException("secret must reference an existing assertion");
        }
        UUID secretId = UUID.randomUUID();
        boolean deceptionRejected = policy == DisclosureDecision.DECEIVE;
        SecretMetadata metadata = new SecretMetadata(secretId, underlyingAssertionId, owner,
                audience, policy == null ? DisclosureDecision.WITHHOLD : policy.safe(),
                recipients, Instant.now(), validUntil, deceptionRejected);
        BeliefProvenance provenance = new BeliefProvenance(EvidenceSourceKind.AUTHORED_CANON,
                owner, List.of("SECRET_POLICY:" + secretId,
                        "UNDERLYING_ASSERTION:" + underlyingAssertionId,
                        deceptionRejected ? "DECEPTION_REJECTED" : "DISCLOSURE_POLICY_VALID"),
                false, false);
        beliefs.assertBelief(new BeliefProposal(secretId, owner, secretId,
                "secret:" + secretId, "SECRET_METADATA", encode(metadata),
                "Secret policy metadata for assertion " + underlyingAssertionId + ".",
                BeliefAssertion.Polarity.POSITIVE, EpistemicStatus.KNOWN, 1, provenance,
                new BeliefAssertion.TemporalScope(BeliefPredicateRegistry.Stability.STABLE,
                        metadata.createdAt(), validUntil, "SECRET_POLICY"),
                BeliefAssertion.AssertionScope.RELATIONSHIP,
                List.of(underlyingAssertionId), metadata.createdAt()));
        secrets.put(secretId, metadata);
        return metadata;
    }

    public DisclosureEvaluation evaluateDisclosure(UUID owner, UUID requester, String query) {
        String text = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<SecretMetadata> candidates = secrets.values().stream()
                .filter(value -> value.ownerId().equals(owner) && value.validAt(Instant.now()))
                .toList();
        SecretMetadata secret = candidates.stream().max(java.util.Comparator
                .comparingInt(value -> secretMatchScore(value, text))).orElse(null);
        int matchScore = secret == null ? 0 : secretMatchScore(secret, text);
        // A generic request cannot select arbitrarily between multiple protected facts.
        if (candidates.size() > 1 && matchScore == 0) secret = null;
        if (secret != null && matchScore == 0 && !text.contains("secret")
                && !text.contains("private")) secret = null;
        if (secret == null) return DisclosureEvaluation.notApplicable();
        boolean permitted = secret.audiencePolicy() == AudiencePolicy.PUBLIC
                || secret.permittedRecipients().contains(requester);
        DisclosureDecision decision = permitted ? secret.disclosurePolicy().safe()
                : secret.audiencePolicy() == AudiencePolicy.OWNER_PERMISSION_REQUIRED
                        || secret.disclosurePolicy() == DisclosureDecision.ASK_PERMISSION
                        ? DisclosureDecision.ASK_PERMISSION : DisclosureDecision.WITHHOLD;
        BeliefAssertion assertion = permitted ? beliefs.assertion(secret.assertionId())
                .orElse(null) : null;
        if (assertion != null && assertion.provenance().sourceKind()
                == EvidenceSourceKind.NPC_TESTIMONY && decision == DisclosureDecision.SHARE) {
            decision = DisclosureDecision.SHARE_WITH_UNCERTAINTY;
        }
        return new DisclosureEvaluation(true, secret, decision.safe(), assertion,
                secret.deceptionRejected() ? "DECEPTION_REJECTED"
                        : permitted ? "AUDIENCE_PERMITTED" : "AUDIENCE_NOT_PERMITTED");
    }

    private int secretMatchScore(SecretMetadata secret, String query) {
        if (beliefs == null || secret == null) return 0;
        String statement = beliefs.assertion(secret.assertionId())
                .map(value -> value.statement() + " " + value.value()).orElse("")
                .toLowerCase(Locale.ROOT);
        Set<String> ignored = Set.of("tell", "what", "which", "about", "secret", "private",
                "please", "could", "would", "your", "the", "that", "this", "information");
        return (int) java.util.Arrays.stream((query == null ? "" : query)
                        .split("[^\\p{L}\\p{N}]+"))
                .filter(term -> term.length() >= 4 && !ignored.contains(term))
                .filter(statement::contains).count();
    }

    public AnswerPlan applyDisclosure(AnswerPlan plan, DisclosureEvaluation evaluation) {
        if (plan == null || evaluation == null || !evaluation.applies()) return plan;
        DisclosureDecision decision = evaluation.decision().safe();
        boolean share = decision == DisclosureDecision.SHARE
                || decision == DisclosureDecision.SHARE_WITH_UNCERTAINTY;
        LinkedHashSet<String> forbidden = new LinkedHashSet<>(plan.forbiddenClaimClasses());
        if (!share) forbidden.add("SECRET_CONTENT_DISCLOSURE");
        List<String> reasons = new ArrayList<>(plan.uncertaintyReasons());
        if ("DECEPTION_REJECTED".equals(evaluation.reason())) {
            reasons.add("DECEPTION_REJECTED");
        }
        return new AnswerPlan(AnswerPlan.SCHEMA_VERSION,
                share ? plan.answerKind() : decision.name(),
                share ? plan.authorizedPropositions() : List.of(),
                share ? plan.evidence() : List.of(),
                share ? plan.uncertaintyMode() : "WITHHELD", plan.maxSentences(),
                share ? plan.maxObjectiveClaims() : 0, plan.requiredSlots(), forbidden,
                plan.status(), share ? plan.responseGoal()
                        : "Do not disclose the protected proposition; " + decision.name()
                                .toLowerCase(Locale.ROOT).replace('_', ' ') + ".",
                plan.unsupportedRequestedProperties(), plan.requestedAction(), reasons,
                decision, evaluation.secret().secretId().toString());
    }

    public DisclosureDecision sanitizeProposedDisclosure(DisclosureDecision proposed) {
        return proposed == null ? DisclosureDecision.WITHHOLD : proposed.safe();
    }

    private void reloadSecrets() {
        if (beliefs == null || profiles == null) return;
        for (var profile : profiles.profiles()) for (BeliefAssertion value
                : beliefs.current(profile.id(), null, "SECRET_METADATA")) {
            decode(value.value()).ifPresent(secret -> secrets.put(secret.secretId(), secret));
        }
    }
    private double trustFactor(UUID observer, UUID source) {
        if (relationships == null) return .5;
        int trust = relationships.get(observer, source).map(value -> value.trust() == null
                ? value.disposition() : value.trust()).orElse(0);
        return Math.max(.1, Math.min(1, (trust + 100) / 200d));
    }
    private static double freshness(Instant learned, Instant at) {
        if (learned == null) return .7;
        double days = Math.max(0, Duration.between(learned, at == null
                ? Instant.now() : at).toSeconds() / 86_400d);
        return Math.max(.55, Math.exp(-days / 30d));
    }
    private static int transmissionDepth(BeliefAssertion value) {
        return value.provenance().evidenceIds().stream()
                .filter(id -> id.startsWith("SOCIAL_DEPTH:"))
                .map(id -> id.substring("SOCIAL_DEPTH:".length()))
                .mapToInt(id -> { try { return Integer.parseInt(id); }
                    catch (RuntimeException ignored) { return 0; } }).max().orElse(0);
    }
    private static ArrayList<UUID> sourceChain(BeliefAssertion value) {
        ArrayList<UUID> chain = new ArrayList<>();
        value.provenance().evidenceIds().stream().filter(id -> id.startsWith("SOCIAL_CHAIN:"))
                .findFirst().ifPresent(id -> {
                    for (String raw : id.substring("SOCIAL_CHAIN:".length()).split(">")) {
                        try { chain.add(UUID.fromString(raw)); } catch (RuntimeException ignored) { }
                    }
                });
        if (chain.isEmpty() && value.provenance().sourceActorId() != null) {
            chain.add(value.provenance().sourceActorId());
        }
        return chain;
    }
    private static String encode(SecretMetadata value) {
        return "v1|secret=" + value.secretId() + "|assertion=" + value.assertionId()
                + "|owner=" + value.ownerId()
                + "|audience=" + value.audiencePolicy() + "|policy="
                + value.disclosurePolicy() + "|created=" + value.createdAt()
                + "|until=" + (value.validUntil() == null ? "" : value.validUntil())
                + "|deceptionRejected=" + value.deceptionRejected()
                + "|recipients=" + value.permittedRecipients().stream().map(UUID::toString)
                        .collect(java.util.stream.Collectors.joining(","));
    }
    private static Optional<SecretMetadata> decode(String raw) {
        try {
            Map<String, String> values = new LinkedHashMap<>();
            for (String part : raw.split("\\|")) {
                int at = part.indexOf('='); if (at > 0) values.put(part.substring(0, at),
                        part.substring(at + 1));
            }
            Set<UUID> recipients = new LinkedHashSet<>();
            for (String id : values.getOrDefault("recipients", "").split(","))
                if (!id.isBlank()) recipients.add(UUID.fromString(id));
            return Optional.of(new SecretMetadata(UUID.fromString(values.get("secret")),
                    UUID.fromString(values.get("assertion")), UUID.fromString(values.get("owner")),
                    AudiencePolicy.valueOf(values.get("audience")),
                    DisclosureDecision.valueOf(values.get("policy")), recipients,
                    Instant.parse(values.get("created")), values.getOrDefault("until", "").isBlank()
                            ? null : Instant.parse(values.get("until")),
                    Boolean.parseBoolean(values.getOrDefault("deceptionRejected", "false"))));
        } catch (RuntimeException ignored) { return Optional.empty(); }
    }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }

    private static double configuredTransmissionFactor() {
        try {
            return Math.max(.1, Math.min(1, Double.parseDouble(System.getProperty(
                    "immersivenpcs.epistemic.socialTransmissionFactor",
                    Double.toString(DEFAULT_TRANSMISSION_FACTOR)))));
        } catch (RuntimeException ignored) { return DEFAULT_TRANSMISSION_FACTOR; }
    }

    public enum AudiencePolicy { PUBLIC, PERMITTED_RECIPIENTS, OWNER_PERMISSION_REQUIRED }
    public record SecretMetadata(UUID secretId, UUID assertionId, UUID ownerId,
            AudiencePolicy audiencePolicy, DisclosureDecision disclosurePolicy,
            Set<UUID> permittedRecipients, Instant createdAt, Instant validUntil,
            boolean deceptionRejected) {
        public SecretMetadata {
            permittedRecipients = Set.copyOf(permittedRecipients == null ? Set.of()
                    : permittedRecipients);
        }
        public boolean validAt(Instant at) { return validUntil == null
                || (at == null ? Instant.now() : at).isBefore(validUntil); }
    }
    public record DisclosureEvaluation(boolean applies, SecretMetadata secret,
            DisclosureDecision decision, BeliefAssertion assertion, String reason) {
        static DisclosureEvaluation notApplicable() { return new DisclosureEvaluation(false,
                null, DisclosureDecision.SHARE, null, "NO_MATCHING_SECRET"); }
    }
    public record TestimonyResult(BeliefAssertion testimony,
            BeliefAssertion believedKnowledge, List<UUID> sourceChain, int transmissionDepth,
            double trustFactor, double confidence, boolean worldTruth) { }
    public record ActorModel(UUID observerNpcId, UUID targetActorId,
            RelationshipRecord relationshipState, List<UUID> knownFacts,
            List<UUID> inferredPreferences, List<UUID> inferredGoals,
            List<UUID> believedKnowledge, List<UUID> commitments,
            List<UUID> sharedSecrets, int familiarity, Instant lastInteraction) { }
}
