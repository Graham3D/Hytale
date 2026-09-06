package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.domain.LinkEdge;
import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.domain.PassiveId;
import com.inigmasgames.hytalerpg.domain.PassiveSlot;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.progress.FileRpgPlayerStateRepository;
import com.inigmasgames.hytalerpg.progress.RpgPlayerState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceAndMigrationTest {
    @TempDir Path temp;

    @Test void fileRoundtripPreservesSlotsJointsEdgesAndRevision() {
        FileRpgPlayerStateRepository repository = new FileRpgPlayerStateRepository(temp.resolve("players"));
        UUID player = UUID.randomUUID();
        RpgPlayerState state = RpgPlayerState.create(player);
        state.skill(SkillSlot.SKILL02, new SkillId("fire_bolt"));
        state.passive(PassiveSlot.PASSIVE06, new PassiveId("fork"));
        state.linkEdges(List.of(
                LinkEdge.create(LinkNodeId.JOINT01, LinkNodeId.SKILL02),
                LinkEdge.create(LinkNodeId.PASSIVE06, LinkNodeId.JOINT01)));
        state.revision = 4;
        repository.save(state);
        RpgPlayerState loaded = repository.load(player).state();
        assertEquals(new SkillId("fire_bolt"), loaded.skill(SkillSlot.SKILL02).orElseThrow());
        assertEquals(new PassiveId("fork"), loaded.passive(PassiveSlot.PASSIVE06).orElseThrow());
        assertEquals(2, loaded.linkEdges().size());
        assertArrayEquals(new String[]{"joint01", "joint02"}, loaded.joints);
        assertEquals(4, loaded.revision);
    }

    @Test void v1MigrationIsDeterministicAndPersistsCurrentSchema() throws Exception {
        FileRpgPlayerStateRepository repository = new FileRpgPlayerStateRepository(temp.resolve("players"));
        UUID player = UUID.randomUUID();
        Files.createDirectories(repository.path(player).getParent());
        String v1 = """
                {"schemaVersion":1,"playerUUID":"%s","characterLevel":7,"totalCharacterXP":1234,
                 "equippedSkillNodes":[null,"fire_bolt",null,null],
                 "passiveNodes":[null,null,null,null,null,"fork"],"revision":9}
                """.formatted(player);
        Files.writeString(repository.path(player), v1);
        var loaded = repository.load(player);
        assertTrue(loaded.migrated());
        assertEquals(1, loaded.sourceSchema());
        assertEquals(RpgPlayerState.CURRENT_SCHEMA, loaded.state().schemaVersion);
        assertEquals(7, loaded.state().level);
        assertEquals(1234, loaded.state().currentXp);
        repository.save(loaded.state());
        assertFalse(repository.load(player).migrated());
    }

    @Test void restartReloadPreservesRequiredCommandSequence() {
        var repository = new Stage01BTestSupport.InMemoryRepository();
        UUID player = UUID.randomUUID();
        var first = Stage01BTestSupport.bundle(repository, new Stage01BTestSupport.RecordingTracer());
        first.service().equipSkill(player, SkillSlot.SKILL02, new SkillId("fire_bolt"));
        first.service().equipPassive(player, PassiveSlot.PASSIVE06, new PassiveId("fork"));
        first.service().link(player, LinkNodeId.PASSIVE06, LinkNodeId.SKILL02);

        var restarted = Stage01BTestSupport.bundle(repository, new Stage01BTestSupport.RecordingTracer());
        var loadout = restarted.service().getLoadout(player);
        assertEquals(new SkillId("fire_bolt"), loadout.state().skill(SkillSlot.SKILL02).orElseThrow());
        assertEquals(new PassiveId("fork"), loadout.state().passive(PassiveSlot.PASSIVE06).orElseThrow());
        assertEquals(LinkNodeId.SKILL02, loadout.routes().get(PassiveSlot.PASSIVE06).getLast());
        assertTrue(loadout.plans().get(SkillSlot.SKILL02).continuation().getFirst().startsWith("FORK(children=2"));
    }

    @Test void corruptedGraphIsBackedUpAndRecoveredWithoutResettingProgress() {
        var repository = new Stage01BTestSupport.InMemoryRepository();
        UUID player = UUID.randomUUID();
        RpgPlayerState state = RpgPlayerState.create(player);
        state.level = 12; state.currentXp = 4567;
        RpgPlayerState.PersistedLinkEdge edge = new RpgPlayerState.PersistedLinkEdge();
        edge.edgeId = "bad-edge"; edge.sourceNodeId = "passive99"; edge.targetNodeId = "skill01";
        state.graphEdges.add(edge);
        repository.states.put(player, state);
        var bundle = Stage01BTestSupport.bundle(repository, new Stage01BTestSupport.RecordingTracer());
        var recovered = bundle.service().getLoadout(player).state();
        assertEquals(12, recovered.level);
        assertEquals(4567, recovered.currentXp);
        assertTrue(recovered.graphEdges.isEmpty());
        assertTrue(recovered.degradedReasons.stream().anyMatch(reason -> reason.startsWith("GRAPH_RECOVERED")));
    }
}
