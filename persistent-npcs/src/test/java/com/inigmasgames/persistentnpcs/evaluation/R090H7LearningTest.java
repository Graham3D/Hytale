package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.cognition.SourcedBeliefStore;
import com.inigmasgames.persistentnpcs.epistemic.*;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class R090H7LearningTest {
    private R090H7LearningTest() { }
    public static void main(String[] args) throws Exception {
        var root = Files.createTempDirectory("orbis-h7-");
        UUID npc = UUID.randomUUID(), player = UUID.randomUUID(), assertion = UUID.randomUUID();
        var store = new SourcedBeliefStore(root); store.load();
        var proposal = new BeliefProposal(assertion, npc, player, "player", "PREFERENCE",
                "tea", "The player prefers tea.", BeliefAssertion.Polarity.POSITIVE,
                EpistemicStatus.BELIEVED, .72, new BeliefProvenance(
                        EvidenceSourceKind.PLAYER_TESTIMONY, player,
                        List.of("AUTHORITATIVE_TRANSCRIPT:test-utterance"), false, false),
                BeliefAssertion.TemporalScope.stable(Instant.now()),
                BeliefAssertion.AssertionScope.ENTITY, List.of(), Instant.now());
        store.assertBelief(proposal); store.awaitIdle();
        var current = store.current(npc, player, "");
        assert new LearningCampaignOracle().validate(current,
                List.of("The player prefers tea.")).passed();
        store.close();
        var restored = new SourcedBeliefStore(root); restored.load();
        assert restored.current(npc, player, "").size() == 1;
        var bad = new BeliefAssertion(UUID.randomUUID(), npc, player, "player", "PREFERENCE",
                "swords", "The player prefers swords.", BeliefAssertion.Polarity.POSITIVE,
                EpistemicStatus.BELIEVED, .9, new BeliefProvenance(
                        EvidenceSourceKind.CONVERSATION_WORKSPACE, npc, List.of(), true, false),
                BeliefAssertion.TemporalScope.stable(Instant.now()),
                BeliefAssertion.AssertionScope.ENTITY, List.of(), List.of(), 1,
                Instant.now(), Instant.now());
        assert !new LearningCampaignOracle().validate(List.of(bad),
                List.of(bad.statement())).passed();
        restored.close();
        System.out.println("R090 H7 persistent learning/provenance gate passed.");
    }
}
