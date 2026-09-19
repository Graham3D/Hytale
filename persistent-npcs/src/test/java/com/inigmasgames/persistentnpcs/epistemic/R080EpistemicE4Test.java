package com.inigmasgames.persistentnpcs.epistemic;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.action.NpcActionRequest;
import com.inigmasgames.persistentnpcs.action.NpcActionResult;
import com.inigmasgames.persistentnpcs.cognition.PlayerFactMemoryService;
import com.inigmasgames.persistentnpcs.cognition.SourcedBelief;
import com.inigmasgames.persistentnpcs.cognition.SourcedBeliefStore;
import com.inigmasgames.persistentnpcs.perception.*;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.sentinel.OrbisDegradationSentinel;
import com.inigmasgames.persistentnpcs.sentinel.SentinelContracts.SentinelMode;
import com.inigmasgames.persistentnpcs.sentinel.SentinelGuardException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** E4 event, authority, revision, temporal, E2 and restart validation. */
public final class R080EpistemicE4Test {
    private R080EpistemicE4Test() { }
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("r080-e4-");
        UUID mara = UUID.randomUUID(), player = UUID.randomUUID();
        playerTestimonyCorrectionAndRestart(root, mara, player);
        contradictionVolatilityActionAndSentinel(root.resolve("authority"), mara, player);
        e2ExactTypedBelief(root.resolve("e2"), mara, player);
        System.out.println("R080 E4 persistent belief validation passed.");
    }

    private static void playerTestimonyCorrectionAndRestart(Path root, UUID mara,
            UUID player) {
        try (var store = store(root)) {
            PlayerFactMemoryService facts = new PlayerFactMemoryService(null, store, null);
            var hidden = facts.persist(mara, player, UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), "I hid a rock by the stream today.", Instant.now());
            assert hidden.beliefWrites().size() == 1;
            BeliefAssertion rock = store.current(mara, player, "CONCEALED").getFirst();
            assert rock.value().contains("rock") : rock;
            assert rock.temporalScope().authoredReference().equals("today");
            assert rock.provenance().sourceKind() == EvidenceSourceKind.PLAYER_TESTIMONY;
            assert rock.provenance().sourceActorId().equals(player);

            facts.persist(mara, player, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    "My name is Grant.", Instant.now());
            facts.persist(mara, player, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    "My name is Graham, not Grant.", Instant.now().plusMillis(1));
            List<BeliefAssertion> name = store.current(mara, player, "NAME");
            assert name.size() == 1 && name.getFirst().value().equals("graham") : name;
            assert name.getFirst().status() == EpistemicStatus.BELIEVED;
            assert store.history(mara).stream().anyMatch(event -> event.type()
                    == BeliefEvent.EventType.BELIEF_SUPERSEDED
                    && event.assertion().value().equals("grant"));

            int before = store.history(mara).size();
            BeliefEvent exact = store.history(mara).getFirst();
            store.appendEvent(exact);
            assert store.history(mara).size() == before : "duplicate event was not idempotent";
        }
        try (var reloaded = store(root)) {
            BeliefAssertion name = reloaded.current(mara, player, "NAME").getFirst();
            assert name.value().equals("graham");
            assert name.provenance().sourceActorId().equals(player);
            assert reloaded.current(mara, player, "CONCEALED").stream()
                    .anyMatch(value -> value.value().contains("rock"));
            assert Files.isRegularFile(reloaded.eventPath());
            assert Files.isRegularFile(reloaded.snapshotPath());
        }
    }

    private static void contradictionVolatilityActionAndSentinel(Path root, UUID mara,
            UUID player) {
        try (var store = store(root)) {
            UUID witness = UUID.randomUUID();
            store.assertBelief(proposal(mara, player, "IS_AT", "west gate",
                    EvidenceSourceKind.NPC_TESTIMONY, witness, false, .72));
            store.assertBelief(proposal(mara, player, "IS_AT", "east gate",
                    EvidenceSourceKind.DIRECT_OBSERVATION, mara, false, 1));
            List<BeliefAssertion> location = store.current(mara, player, "IS_AT");
            assert location.getFirst().value().equals("east gate") : location;
            assert store.history(mara).stream().anyMatch(event -> event.assertion()
                    .value().equals("west gate") && !event.assertion().conflictIds().isEmpty());

            store.ingestPerception(mara, player, perception(mara, player, "lantern"));
            assert store.current(mara, player, "HOLDS").getFirst().value().equals("lantern");
            store.ingestPerception(mara, player, perception(mara, player, null));
            assert store.current(mara, player, "HOLDS").getFirst().value().equals("NONE")
                    : store.current(mara, player, "HOLDS");

            int actionBefore = store.current(mara, player, "TRANSACTION_OCCURRED").size();
            NpcActionRequest request = new NpcActionRequest("GIVE_ITEM", new JsonObject(),
                    "tool-1");
            assert store.ingestActionResult(mara, player, request,
                    NpcActionResult.failure("NO_ITEM", "Nothing changed."), Instant.now())
                    .isPresent();
            assert store.current(mara, player, "TRANSACTION_OCCURRED").size() == actionBefore;
            assert store.ingestActionResult(mara, player, request,
                    NpcActionResult.success("Mara gave the player a sword."), Instant.now())
                    .isPresent();
            assert !store.current(mara, player, "OWNS").isEmpty();

            int verified = store.current(mara, player, "ACTION_OCCURRED").size()
                    + store.current(mara, player, "TRANSACTION_OCCURRED").size();
            boolean rejected = false;
            try {
                store.append(new SourcedBelief(UUID.randomUUID(), mara, mara, player,
                        "Mara", "ACTION_OCCURRED", "I sold a sword yesterday.",
                        Instant.now(), .9, .4, UUID.randomUUID(), UUID.randomUUID(),
                        List.of("NPC_SPEECH:" + UUID.randomUUID())));
            } catch (SentinelGuardException expected) { rejected = true; }
            assert rejected;
            assert verified == store.current(mara, player, "ACTION_OCCURRED").size()
                    + store.current(mara, player, "TRANSACTION_OCCURRED").size();

            int prior = store.history(mara).size();
            rejected = false;
            try {
                store.assertBelief(new BeliefProposal(null, mara, player, "player", "OWNS",
                        "mill", "The player owns the mill.", BeliefAssertion.Polarity.POSITIVE,
                        EpistemicStatus.BELIEVED, .5,
                        new BeliefProvenance(EvidenceSourceKind.PLAYER_TESTIMONY, player,
                                List.of(), false, false), null,
                        BeliefAssertion.AssertionScope.ENTITY, List.of(), Instant.now()));
            } catch (SentinelGuardException expected) { rejected = true; }
            assert rejected && store.history(mara).size() == prior;
        }
    }

    private static void e2ExactTypedBelief(Path root, UUID mara, UUID player) {
        try (var store = store(root)) {
            store.assertBelief(proposal(mara, player, "NAME", "Graham",
                    EvidenceSourceKind.PLAYER_TESTIMONY, player, false, .86));
            System.setProperty(EpistemicFeatureMode.PROPERTY, "AUTHORITATIVE");
            EpistemicContract base = EpistemicShadowAnalyzer.analyzeInitial(
                    "What's my name?", new ConversationWorkspace());
            NpcProfile profile = new NpcProfile(mara, "Mara", "blacksmith", "practical",
                    "", "", "", "", List.of(), List.of(), List.of(), List.of(), 0);
            EpistemicEvidenceRetriever retriever = new EpistemicEvidenceRetriever(null, store,
                    null, null, null, null);
            retriever.enrich(base, UUID.randomUUID(), profile, player, "What's my name?",
                    null, null, null, List.of()); // bounded first-use class warmup
            EpistemicContract enriched = retriever.enrich(base, UUID.randomUUID(), profile,
                    player, "What's my name?", null, null, null, List.of());
            assert enriched.evidence().supporting().stream().anyMatch(value ->
                    value.stableId().startsWith("assertion:")
                            && value.objectValue().equals("Graham")) : enriched.evidence();
        }
    }

    private static SourcedBeliefStore store(Path root) {
        SourcedBeliefStore result = new SourcedBeliefStore(root);
        result.load();
        result.setDegradationSentinel(new OrbisDegradationSentinel(
                SentinelMode.ENFORCE, ignored -> { }));
        return result;
    }
    private static BeliefProposal proposal(UUID owner, UUID subject, String predicate,
            String value, EvidenceSourceKind source, UUID actor, boolean speechOnly,
            double confidence) {
        Instant now = Instant.now();
        return new BeliefProposal(null, owner, subject, "player", predicate, value,
                "The player " + predicate.toLowerCase() + " " + value + ".",
                BeliefAssertion.Polarity.POSITIVE, source == EvidenceSourceKind.DIRECT_OBSERVATION
                        ? EpistemicStatus.KNOWN : EpistemicStatus.BELIEVED,
                confidence, new BeliefProvenance(source, actor,
                        List.of((speechOnly ? "NPC_SPEECH:" : source.name() + ":")
                                + UUID.randomUUID()), speechOnly, false), null,
                BeliefAssertion.AssertionScope.ENTITY, List.of(), now);
    }
    private static RawPerceptionSnapshot perception(UUID npc, UUID player, String item) {
        UUID response = UUID.randomUUID();
        PerceivedItem held = item == null ? null : new PerceivedItem(null, item, item, 1,
                0, 0, "", 1);
        NpcPerceptionSnapshot snapshot = new NpcPerceptionSnapshot(npc, UUID.randomUUID(),
                UUID.randomUUID(), LocalDateTime.now(), 0, 0, 0, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), 0, held, List.of());
        return new RawPerceptionSnapshot(response, Instant.now(), "test", 0, snapshot,
                0, List.of());
    }
}
