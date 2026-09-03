package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class R090H5FrozenFixtureTest {
    private R090H5FrozenFixtureTest() { }
    public static void main(String[] args) throws Exception {
        var root = Files.createTempDirectory("orbis-h5-");
        var repository = new FrozenFixtureRepository(root.resolve("candidates"),
                root.resolve("fixtures"));
        var fixture = new FrozenConversationFixture(1, "lycander-player-kinship",
                "live-lycander-player-kinship", "h2-run", Instant.parse(
                        "2026-08-31T21:00:00Z"), Set.of("IDENTITY", "CLAIM_AUTHORITY"),
                Map.of("utterance", "Who are you?",
                        "providerOutput", "I am Lycander, your grandfather.",
                        "groundingEvidence", "RELATIONSHIP:player-id"),
                Map.of("CLAIM_FIREWALL", "reject unsupported player kinship"), Set.of(
                        "speaker identity is Lycander"), Set.of("grandfather"),
                List.of("paraphrase", "referent", "cross-profile"), "shared-factory", "CANDIDATE");
        var candidate = repository.freezeCandidate(fixture);
        assert Files.isRegularFile(candidate);
        boolean rejected = false;
        try { repository.promote(fixture.fixtureId(), false); }
        catch (IllegalStateException expected) { rejected = true; }
        assert rejected;
        var promoted = repository.promote(fixture.fixtureId(), true);
        assert JsonFiles.read(promoted, FrozenConversationFixture.class).reviewStatus()
                .equals("PROMOTED_REVIEWED");
        assert Files.readString(promoted.getParent().resolve("manifest.json"))
                .contains(fixture.fixtureId());
        var replayed = new FrozenFixtureReplayHarness().replay(
                JsonFiles.read(promoted, FrozenConversationFixture.class));
        assert replayed.passed() : replayed;
        var checkedIn = JsonFiles.read(java.nio.file.Path.of("src", "test", "resources",
                "conversation-matrix", "frozen", "lycander-player-kinship.json"),
                FrozenConversationFixture.class);
        assert new FrozenFixtureReplayHarness().replay(checkedIn).passed();
        System.out.println("R090 H5 frozen fixture/promotion gate passed.");
    }
}
