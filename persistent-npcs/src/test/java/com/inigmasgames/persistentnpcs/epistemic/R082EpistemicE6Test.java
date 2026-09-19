package com.inigmasgames.persistentnpcs.epistemic;

import com.inigmasgames.persistentnpcs.cognition.SourcedBeliefStore;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** E6 trusted/distrusted testimony, bounded ToM, secret policy, and persistence gate. */
public final class R082EpistemicE6Test {
    private static final UUID LYCANDER = UUID.randomUUID();
    private static final UUID MARA = UUID.randomUUID();
    private static final UUID GARRICK = UUID.randomUUID();
    private static final UUID DISTRUSTFUL = UUID.randomUUID();
    private static final UUID GRAHAM = UUID.randomUUID();
    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();
    private static final UUID BRIDGE = UUID.randomUUID();

    private R082EpistemicE6Test() { }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("r082-e6-");
        NpcProfileRegistry profiles = profiles(root);
        RelationshipStore relationships = new RelationshipStore(root);
        relationships.load();
        relationships.adjust(MARA, LYCANDER, 0, 80, 0, 0, 0, 0, 0, Instant.now());
        relationships.adjust(DISTRUSTFUL, LYCANDER, 0, -80, 0, 0, 0, 0, 0, Instant.now());
        relationships.adjust(GARRICK, MARA, 0, 60, 0, 0, 0, 0, 0, Instant.now());

        ActorModelService.SecretMetadata persistedSecret;
        try (SourcedBeliefStore beliefs = new SourcedBeliefStore(root.resolve("beliefs"))) {
            beliefs.load();
            ActorModelService social = new ActorModelService(beliefs, relationships, profiles);
            BeliefAssertion bridgeBlocked = beliefs.assertBelief(direct(LYCANDER, BRIDGE,
                    "bridge", "CONDITION", "blocked",
                    "The north bridge is blocked."));

            // A/B: identical testimony is discounted deterministically by relationship trust.
            var trusted = social.ingestDeliveredTestimony(MARA, LYCANDER, bridgeBlocked,
                    "CANONICAL_DELIVERY:" + UUID.randomUUID(), Instant.now());
            var distrusted = social.ingestDeliveredTestimony(DISTRUSTFUL, LYCANDER,
                    bridgeBlocked, "CANONICAL_DELIVERY:" + UUID.randomUUID(), Instant.now());
            assert trusted.transmissionDepth() == 1 && !trusted.worldTruth();
            assert trusted.testimony().status() == EpistemicStatus.BELIEVED;
            assert trusted.confidence() > distrusted.confidence() : "trust was ignored";
            assert trusted.sourceChain().equals(List.of(LYCANDER));

            // C: second-hand testimony preserves the chain and loses confidence.
            var secondHand = social.ingestDeliveredTestimony(GARRICK, MARA,
                    trusted.testimony(), "CANONICAL_DELIVERY:" + UUID.randomUUID(),
                    Instant.now());
            assert secondHand.transmissionDepth() == 2;
            assert secondHand.sourceChain().equals(List.of(LYCANDER, MARA));
            assert secondHand.confidence() < trusted.confidence();
            assert secondHand.testimony().provenance().sourceKind()
                    == EvidenceSourceKind.NPC_TESTIMONY;

            // D: a later direct observation overrides rumor, preserving both events.
            BeliefAssertion directOpen = beliefs.assertBelief(direct(MARA, BRIDGE, "bridge",
                    "CONDITION", "open", "The north bridge is open."));
            assert beliefs.current(MARA, BRIDGE, "CONDITION").stream().anyMatch(value ->
                    value.assertionId().equals(directOpen.assertionId())
                            && value.provenance().sourceKind()
                            == EvidenceSourceKind.DIRECT_OBSERVATION);
            assert beliefs.history(MARA).stream().anyMatch(event -> event.assertion()
                    .assertionId().equals(trusted.testimony().assertionId()));

            // E/F/G: depth-one works, depth-two is explicit-only, depth three is impossible.
            assert social.believedKnowledge(MARA, LYCANDER).stream().anyMatch(value ->
                    value.supportIds().contains(bridgeBlocked.assertionId()));
            BeliefAssertion sword = beliefs.assertBelief(direct(MARA, PLAYER, "sword",
                    "OWNER", "Graham", "Graham owns the silver sword."));
            BeliefProvenance nested = deliveredProvenance(MARA, 1);
            social.recordBelievedKnowledge(MARA, GRAHAM, sword, 1, nested,
                    Instant.now(), true);
            expectFailure(() -> social.recordBelievedKnowledge(MARA, GRAHAM, sword, 2,
                    deliveredProvenance(GRAHAM, 2), Instant.now(), false));
            BeliefAssertion depthTwo = social.recordBelievedKnowledge(MARA, GRAHAM, sword, 2,
                    deliveredProvenance(GRAHAM, 2), Instant.now(), true);
            assert depthTwo.confidence() < sword.confidence();
            expectFailure(() -> social.recordBelievedKnowledge(MARA, GRAHAM, sword, 3,
                    deliveredProvenance(GRAHAM, 2), Instant.now(), true));
            BeliefAssertion preference = beliefs.assertBelief(direct(MARA, GRAHAM, "Graham",
                    "PREFERS", "ash wood", "Graham prefers ash wood."));
            social.recordActorBelief(MARA, GRAHAM, "BELIEVES_ACTOR_PREFERS", preference, 1,
                    deliveredProvenance(GRAHAM, 1), Instant.now(), true);
            BeliefAssertion intention = beliefs.assertBelief(direct(MARA, GRAHAM, "Graham",
                    "INTENDS", "visit forge", "Graham intends to visit the forge."));
            social.recordActorBelief(MARA, GRAHAM, "BELIEVES_ACTOR_INTENDS", intention, 1,
                    deliveredProvenance(GRAHAM, 1), Instant.now(), true);
            assert social.snapshot(MARA, GRAHAM).inferredPreferences().size() == 1;
            assert social.snapshot(MARA, GRAHAM).inferredGoals().size() == 1;
            BeliefAssertion suspicion = beliefs.assertBelief(direct(MARA, GRAHAM, "Graham",
                    "SUSPECTED_BY", "Lycander", "Lycander suspects Graham."));
            social.recordBelievedKnowledge(MARA, GRAHAM, suspicion, 2,
                    deliveredProvenance(LYCANDER, 2), Instant.now(), true);

            // H/I: secrets reference the assertion and enforce audience before wording.
            persistedSecret = social.registerSecret(MARA, sword.assertionId(),
                    ActorModelService.AudiencePolicy.PERMITTED_RECIPIENTS,
                    DisclosureDecision.SHARE, Set.of(PLAYER), null);
            var authorized = social.evaluateDisclosure(MARA, PLAYER,
                    "Tell me the silver sword secret.");
            var unauthorized = social.evaluateDisclosure(MARA, STRANGER,
                    "Tell me the silver sword secret.");
            assert authorized.decision() == DisclosureDecision.SHARE
                    && authorized.assertion().assertionId().equals(sword.assertionId());
            assert unauthorized.decision() == DisclosureDecision.WITHHOLD
                    && unauthorized.assertion() == null;
            AnswerPlan blocked = social.applyDisclosure(plan("The sword belongs to Graham."),
                    unauthorized);
            assert blocked.authorizedPropositions().isEmpty()
                    && blocked.forbiddenClaimClasses().contains("SECRET_CONTENT_DISCLOSURE");
            var permission = social.registerSecret(MARA, directOpen.assertionId(),
                    ActorModelService.AudiencePolicy.OWNER_PERMISSION_REQUIRED,
                    DisclosureDecision.SHARE, Set.of(), null);
            assert social.evaluateDisclosure(MARA, STRANGER,
                    "May I share the open bridge secret?").decision()
                    == DisclosureDecision.ASK_PERMISSION;

            // J: deception is rejected and durably normalized to withholding.
            var deceptive = social.registerSecret(MARA, bridgeBlocked.assertionId(),
                    ActorModelService.AudiencePolicy.PERMITTED_RECIPIENTS,
                    DisclosureDecision.DECEIVE, Set.of(), null);
            assert deceptive.disclosurePolicy() == DisclosureDecision.WITHHOLD
                    && deceptive.deceptionRejected();
            assert social.sanitizeProposedDisclosure(DisclosureDecision.DECEIVE)
                    == DisclosureDecision.WITHHOLD;
            assert social.evaluateDisclosure(MARA, STRANGER,
                    "What is the blocked bridge secret?").decision()
                    != DisclosureDecision.DECEIVE;
            expectFailure(() -> social.ingestDeliveredTestimony(MARA, LYCANDER,
                    bridgeBlocked, "UNDELIVERED:" + UUID.randomUUID(), Instant.now()));
            expectFailure(() -> beliefs.assertBelief(new BeliefProposal(null, MARA, BRIDGE,
                    "bridge", "CONDITION", "broken", "The bridge is broken.",
                    BeliefAssertion.Polarity.POSITIVE, EpistemicStatus.BELIEVED, .7,
                    new BeliefProvenance(EvidenceSourceKind.NPC_TESTIMONY, LYCANDER,
                            List.of("SOCIAL_DEPTH:1"), false, false), null,
                    BeliefAssertion.AssertionScope.WORLD, List.of(bridgeBlocked.assertionId()),
                    Instant.now())));

            // K: uninvolved actors do not inherit rumors or nested beliefs.
            assert social.believedKnowledge(STRANGER, LYCANDER).isEmpty();
            assert beliefs.current(STRANGER, BRIDGE, "CONDITION").isEmpty();
            assert social.snapshot(MARA, GRAHAM).believedKnowledge().contains(
                    depthTwo.assertionId());

            parserAndRetrievalGate(social, beliefs, relationships, profiles);
            Result timing = benchmark(social);
            assert timing.p95Micros < 40_000 : timing;
            System.out.println("R082 E6 in-memory gate p50Micros=" + timing.p50Micros
                    + " p95Micros=" + timing.p95Micros + " maxMicros=" + timing.maxMicros);
        }

        // E4 persistence is the only durable store; social/secret state rehydrates from it.
        try (SourcedBeliefStore restarted = new SourcedBeliefStore(root.resolve("beliefs"))) {
            restarted.load();
            ActorModelService restored = new ActorModelService(restarted, relationships, profiles);
            var disclosure = restored.evaluateDisclosure(MARA, PLAYER,
                    "Tell me the silver sword secret.");
            assert disclosure.applies() && disclosure.secret().secretId()
                    .equals(persistedSecret.secretId());
            assert !restored.believedKnowledge(MARA, GRAHAM).isEmpty();
        }
        System.out.println("R082 E6 social cognition validation passed.");
    }

    private static void parserAndRetrievalGate(ActorModelService social,
            SourcedBeliefStore beliefs, RelationshipStore relationships,
            NpcProfileRegistry profiles) {
        System.setProperty(EpistemicFeatureMode.PROPERTY, "AUTHORITATIVE");
        ConversationWorkspace workspace = new ConversationWorkspace();
        EpistemicContract one = EpistemicShadowAnalyzer.analyzeInitial(
                "Does Graham know about the silver sword?", workspace);
        assert one.dialogueFrame().predicateKey().equals("BELIEVES_ACTOR_KNOWS");
        assert one.dialogueFrame().signals().contains("tom-depth-1");
        EpistemicContract enriched = new EpistemicEvidenceRetriever(null, beliefs,
                relationships, null, null, profiles, social).enrich(one, UUID.randomUUID(),
                profiles.requireName("Mara"), PLAYER,
                "Does Graham know about the silver sword?", null, null, workspace, List.of());
        List<EvidenceRef> socialEvidence = enriched.evidence().supporting().stream()
                .filter(value -> value.predicateKey().equals("BELIEVES_ACTOR_KNOWS")).toList();
        assert !socialEvidence.isEmpty();
        assert socialEvidence.stream().allMatch(value ->
                value.subjectKey().equals("NPC_NAME:GRAHAM")
                        && social.believedKnowledge(MARA, GRAHAM).stream().anyMatch(assertion ->
                        value.stableId().equals("social:" + assertion.assertionId())))
                : "actor-local filter leaked";
        EpistemicContract unknown = EpistemicShadowAnalyzer.analyzeInitial(
                "Does Garrick know about the silver sword?", new ConversationWorkspace());
        EpistemicContract absent = new EpistemicEvidenceRetriever(null, beliefs,
                relationships, null, null, profiles, social).enrich(unknown, UUID.randomUUID(),
                profiles.requireName("Mara"), PLAYER,
                "Does Garrick know about the silver sword?", null, null,
                new ConversationWorkspace(), List.of());
        assert absent.evidence().supporting().stream().noneMatch(value ->
                value.predicateKey().equals("BELIEVES_ACTOR_KNOWS"));
        assert absent.answerability() == Answerability.UNKNOWN
                : absent.answerability() + " " + absent.dialogueFrame() + " "
                        + absent.evidence().supporting();

        EpistemicContract preference = EpistemicShadowAnalyzer.analyzeInitial(
                "What does Graham prefer about ash wood?", new ConversationWorkspace());
        assert preference.dialogueFrame().predicateKey().equals("BELIEVES_ACTOR_PREFERS");
        EpistemicContract preferenceEvidence = new EpistemicEvidenceRetriever(null, beliefs,
                relationships, null, null, profiles, social).enrich(preference,
                UUID.randomUUID(), profiles.requireName("Mara"), PLAYER,
                "What does Graham prefer about ash wood?", null, null,
                new ConversationWorkspace(), List.of());
        assert preferenceEvidence.evidence().supporting().stream().anyMatch(value ->
                value.predicateKey().equals("BELIEVES_ACTOR_PREFERS"));

        EpistemicContract depthTwo = EpistemicShadowAnalyzer.analyzeInitial(
                "Does Graham know that Lycander suspects him?", new ConversationWorkspace());
        assert depthTwo.dialogueFrame().signals().contains("tom-depth-2-explicit");
        assert depthTwo.dialogueFrame().predicateKey().equals("BELIEVES_ACTOR_KNOWS");
        EpistemicContract depthTwoEvidence = new EpistemicEvidenceRetriever(null, beliefs,
                relationships, null, null, profiles, social).enrich(depthTwo,
                UUID.randomUUID(), profiles.requireName("Mara"), PLAYER,
                "Does Graham know that Lycander suspects him?", null, null,
                new ConversationWorkspace(), List.of());
        assert depthTwoEvidence.evidence().supporting().stream().anyMatch(value ->
                value.predicateKey().equals("BELIEVES_ACTOR_KNOWS")
                        && value.temporalScope().equals("TOM_DEPTH_2")
                        && value.compactProposition().contains("Lycander suspects Graham"));

        // Connected trace: actor-local bridge knowledge uses testimony/provenance, never a
        // generic relationship row. An uninvolved actor remains UNKNOWN and invented bridge
        // properties cannot pass the authoritative firewall.
        String bridgeQuestion = "Mara, what do you know about the old bridge?";
        EpistemicContract bridge = EpistemicShadowAnalyzer.analyzeInitial(
                bridgeQuestion, new ConversationWorkspace());
        assert bridge.dialogueFrame().predicateKey().equals("KNOWLEDGE_TOPIC");
        EpistemicContract knownBridge = new EpistemicEvidenceRetriever(null, beliefs,
                relationships, null, null, profiles, social).enrich(bridge, UUID.randomUUID(),
                profiles.requireName("Mara"), PLAYER, bridgeQuestion, null, null,
                new ConversationWorkspace(), List.of());
        assert knownBridge.evidence().supporting().stream().anyMatch(value ->
                value.sourceKind() == EvidenceSourceKind.NPC_TESTIMONY
                        || value.sourceKind() == EvidenceSourceKind.DIRECT_OBSERVATION);
        assert knownBridge.evidence().supporting().stream().noneMatch(value ->
                value.sourceKind() == EvidenceSourceKind.RELATIONSHIP_STATE);

        EpistemicContract absentBridge = new EpistemicEvidenceRetriever(null, beliefs,
                relationships, null, null, profiles, social).enrich(bridge, UUID.randomUUID(),
                profiles.requireName("Graham"), PLAYER, bridgeQuestion, null, null,
                new ConversationWorkspace(), List.of());
        assert absentBridge.answerability() == Answerability.UNKNOWN : absentBridge.answerability();
        var blockedHallucination = new EpistemicClaimFirewall().validate(
                "The old bridge has a rusty bolt and hides a fox.", absentBridge, false);
        assert blockedHallucination.repaired()
                && !blockedHallucination.dialogue().toLowerCase().contains("rusty")
                && !blockedHallucination.dialogue().toLowerCase().contains("fox")
                : blockedHallucination;
    }

    private static Result benchmark(ActorModelService social) {
        ArrayList<Long> samples = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            long start = System.nanoTime();
            social.snapshot(MARA, i % 2 == 0 ? GRAHAM : LYCANDER);
            social.evaluateDisclosure(MARA, PLAYER, "Tell me the silver sword secret.");
            samples.add((System.nanoTime() - start) / 1_000);
        }
        samples.sort(Comparator.naturalOrder());
        return new Result(samples.get(500), samples.get(950), samples.getLast());
    }

    private static NpcProfileRegistry profiles(Path root) {
        NpcProfileRegistry profiles = new NpcProfileRegistry(new ProfileRepository(root));
        profiles.register(profile(MARA, "Mara"));
        profiles.register(profile(LYCANDER, "Lycander"));
        profiles.register(profile(GARRICK, "Garrick"));
        profiles.register(profile(DISTRUSTFUL, "Darian"));
        profiles.register(profile(GRAHAM, "Graham"));
        return profiles;
    }

    private static NpcProfile profile(UUID id, String name) {
        return new NpcProfile(id, name, "villager", "practical",
                name + " lives in the village.", "Live responsibly.",
                "Village home", "Village workshop",
                List.of(), List.of(), List.of(), List.of(), 0);
    }

    private static BeliefProposal direct(UUID owner, UUID subjectId, String subject,
            String predicate, String value, String statement) {
        Instant now = Instant.now();
        return new BeliefProposal(null, owner, subjectId, subject, predicate, value, statement,
                BeliefAssertion.Polarity.POSITIVE, EpistemicStatus.KNOWN, .92,
                new BeliefProvenance(EvidenceSourceKind.DIRECT_OBSERVATION, owner,
                        List.of("DIRECT_OBSERVATION:" + UUID.randomUUID()), false, false),
                BeliefAssertion.TemporalScope.stable(now),
                BeliefAssertion.AssertionScope.WORLD, List.of(), now);
    }

    private static BeliefProvenance deliveredProvenance(UUID actor, int depth) {
        return new BeliefProvenance(EvidenceSourceKind.NPC_TESTIMONY, actor,
                List.of("CANONICAL_DELIVERY:" + UUID.randomUUID(),
                        "SOCIAL_CHAIN:" + actor, "SOCIAL_DEPTH:" + depth), false, false);
    }

    private static AnswerPlan plan(String proposition) {
        return new AnswerPlan(AnswerPlan.SCHEMA_VERSION, "FACT", List.of(proposition),
                List.of(), "CERTAIN", 2, 2, Set.of(), Set.of(), "SUPPORTED", "Answer.",
                List.of(), "", List.of());
    }

    private static void expectFailure(Runnable action) {
        try { action.run(); throw new AssertionError("expected policy rejection"); }
        catch (IllegalArgumentException expected) { }
    }

    private record Result(long p50Micros, long p95Micros, long maxMicros) { }
}
