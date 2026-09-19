package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.cognition.SourcedBeliefStore;
import com.inigmasgames.persistentnpcs.epistemic.*;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class R090H8MultiAgentTest {
    private R090H8MultiAgentTest() { }
    public static void main(String[] args) throws Exception {
        UUID mara = UUID.randomUUID(), lycander = UUID.randomUUID();
        var scene = new ConversationSceneCoordinator(8, 2, .04);
        String[] replies = {"Good morning, Grandfather.", "Morning, Mara.",
                "The forge is quiet today.", "Then use the time well."};
        var report = scene.run(new ConversationSceneCoordinator.SceneSeed(mara, lycander,
                "Good morning, Lycander."), (index, speaker, listener, utterance) ->
                CompletableFuture.completedFuture(new ConversationSceneCoordinator.CanonicalTurn(
                        listener, UUID.randomUUID(), replies[index % replies.length], false))).get();
        assert report.singleFloorOwnerPerTurn(); assert report.bounded();
        assert report.privateTranscripts().size() == 2;
        assert report.turns().stream().allMatch(value -> value.floorOwner().equals(
                value.listenerId()));
        assert report.authorizedTestimonyCount() == 0;

        // The real E6 actor-model boundary rejects generated speech as testimony and stores
        // only an explicitly authorized proposition in the recipient's independent belief set.
        var root = Files.createTempDirectory("orbis-h8-");
        var beliefs = new SourcedBeliefStore(root); beliefs.load();
        var relationships = new RelationshipStore(root); relationships.load();
        var profiles = new NpcProfileRegistry(new ProfileRepository(root)); profiles.load();
        var actors = new ActorModelService(beliefs, relationships, profiles);
        var generated = assertion(lycander, mara, true);
        boolean rejected = false;
        try { actors.ingestDeliveredTestimony(mara, lycander, generated,
                "CANONICAL_DELIVERY:" + UUID.randomUUID(), Instant.now()); }
        catch (IllegalArgumentException expected) { rejected = true; }
        assert rejected;
        var authorized = assertion(lycander, mara, false);
        var testimony = actors.ingestDeliveredTestimony(mara, lycander, authorized,
                "CANONICAL_DELIVERY:" + UUID.randomUUID(), Instant.now());
        assert testimony.testimony().ownerNpcId().equals(mara);
        assert beliefs.current(lycander, mara, "PREFERENCE").isEmpty();
        assert !beliefs.current(mara, mara, "PREFERENCE").isEmpty();
        beliefs.close();
        System.out.println("R090 H8 multi-agent headless gate passed: turns="
                + report.turns().size());
    }

    private static BeliefAssertion assertion(UUID owner, UUID subject, boolean generated) {
        Instant now = Instant.now();
        return new BeliefAssertion(UUID.randomUUID(), owner, subject, "Mara", "PREFERENCE",
                "quiet mornings", "Mara prefers quiet mornings.",
                BeliefAssertion.Polarity.POSITIVE, EpistemicStatus.KNOWN, .9,
                new BeliefProvenance(generated ? EvidenceSourceKind.CONVERSATION_WORKSPACE
                        : EvidenceSourceKind.AUTHORED_CANON, owner,
                        generated ? List.of() : List.of("PROFILE:Mara:likes"), generated, false),
                BeliefAssertion.TemporalScope.stable(now),
                BeliefAssertion.AssertionScope.ENTITY, List.of(), List.of(), 1, now, now);
    }
}
