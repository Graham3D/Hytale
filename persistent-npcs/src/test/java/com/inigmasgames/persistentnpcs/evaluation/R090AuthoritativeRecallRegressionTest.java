package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.epistemic.Answerability;
import com.inigmasgames.persistentnpcs.epistemic.ConversationWorkspace;
import com.inigmasgames.persistentnpcs.epistemic.E5QueryExpansion;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicClaimFirewall;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicContract;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicEvidenceRetriever;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicFeatureMode;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicShadowAnalyzer;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Live-discovered H2/H4 regression: exact two-slot recall must retain its evidence. */
public final class R090AuthoritativeRecallRegressionTest {
    private static final UUID NPC = UUID.randomUUID();
    private static final UUID PLAYER = UUID.randomUUID();

    private R090AuthoritativeRecallRegressionTest() { }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("r090-authoritative-recall-");
        MemoryStore memories = new MemoryStore(root, 64);
        memories.load();
        memories.append(new MemoryRecord(UUID.randomUUID(), NPC, PLAYER, Instant.now(),
                MemoryType.PLAYER_FACT, .45,
                "Player-reported belief: I hid a silver key under a large rock.",
                .72, "PLAYER_REPORT:source=" + PLAYER, List.of(PLAYER),
                "a large rock", "The player reported this; it was not directly perceived."));

        List<String> queries = List.of(
                "What did I hide, and where did I hide it?",
                "What did I conceal and where?",
                "Where did I stash the silver key?",
                "What item did I bury under the rock?");
        for (String query : queries) {
            var retrieval = memories.queryFactsReadOnly(NPC, PLAYER, query,
                    Set.of(MemoryType.PLAYER_FACT), 2,
                    E5QueryExpansion.expand(query, null, null, Instant.now()), Instant.now());
            assert !retrieval.selected().isEmpty() : query + " -> " + retrieval;
            assert retrieval.selected().getFirst().score() >= .55
                    : query + " fell below sufficiency: " + retrieval.selected().getFirst();
        }

        System.setProperty(EpistemicFeatureMode.PROPERTY, "AUTHORITATIVE");
        String query = "What did I hide, and where did I hide the silver key?";
        ConversationWorkspace workspace = new ConversationWorkspace();
        EpistemicContract base = EpistemicShadowAnalyzer.analyzeInitial(query, workspace);
        NpcProfile profile = new NpcProfile(NPC, "Mara", "inventor", "curious",
                "", "", "", "", List.of(), List.of(), List.of(), List.of(), 0);
        EpistemicContract contract = new EpistemicEvidenceRetriever(memories, null, null,
                null, null, null).enrich(base, UUID.randomUUID(), profile, PLAYER, query,
                        null, null, workspace, List.of());
        assert contract.evidence().supporting().size() == 1 : contract.evidence();
        assert contract.answerability() == Answerability.INFERRED
                || contract.answerability() == Answerability.KNOWN : contract.answerability();
        assert contract.answerPlan().maxObjectiveClaims() >= 1 : contract.answerPlan();

        var repaired = new EpistemicClaimFirewall().validate(
                "You hid four jars in your pocket.", contract, false);
        assert repaired.valid() : repaired;
        assert repaired.dialogue().toLowerCase().contains("silver key") : repaired;
        assert repaired.dialogue().toLowerCase().contains("large rock") : repaired;
        assert !repaired.dialogue().toLowerCase().contains("four jars") : repaired;
        assert repaired.dialogue().startsWith("You told me you hid") : repaired.dialogue();
        memories.flush();
        System.out.println("R090 authoritative recall exact/adjacent regression passed.");
    }
}
