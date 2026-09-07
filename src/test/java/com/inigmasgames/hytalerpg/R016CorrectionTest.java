package com.inigmasgames.hytalerpg;

import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.links.ValidationCode;
import com.inigmasgames.hytalerpg.ui.skilltree.RpgSkillTreeMutationService;
import com.inigmasgames.hytalerpg.ui.skilltree.RpgSkillTreeProjectionService;
import com.inigmasgames.hytalerpg.ui.skilltree.StaticSkillTreeLayout;
import com.inigmasgames.hytalerpg.ui.skilltree.StaticSkillTreeViewModel;
import com.inigmasgames.hytalerpg.ui.trace.RpgUiTraceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class R016CorrectionTest {
    @TempDir Path temporary;

    @Test void staticTopologyIsExactlyTheOwnerSuppliedThreeBranchAdjacency() {
        assertEquals(List.of(
                "passive01->joint01", "passive02->joint01", "passive03->joint01", "joint01->skill01",
                "passive04->joint02", "passive05->joint02", "joint02->skill02", "passive06->skill03"),
                StaticSkillTreeLayout.ADJACENCY.stream()
                        .map(edge -> edge.source().externalId() + "->" + edge.target().externalId()).toList());
    }

    @Test void staticAssignmentsProduceAuthoritativeEdgesAndCompatibilityFailureRollsBackAtomically() {
        var bundle = Stage01BTestSupport.bundle();
        var mutations = new RpgSkillTreeMutationService(bundle.service(), new StaticSkillTreeLayout());
        UUID player = UUID.randomUUID();
        long revision = bundle.service().getPresentationView(player).state().revision;
        assertTrue(mutations.assign(player, revision, LinkNodeId.SKILL01, "fire_bolt").success());
        for (var assignment : List.of(Map.entry(LinkNodeId.PASSIVE01, "potency"),
                Map.entry(LinkNodeId.PASSIVE02, "efficiency"), Map.entry(LinkNodeId.PASSIVE03, "fork"))) {
            revision = bundle.service().getPresentationView(player).state().revision;
            assertTrue(mutations.assign(player, revision, assignment.getKey(), assignment.getValue()).success());
        }
        var state = bundle.service().getPresentationView(player);
        assertEquals(4, state.state().linkEdges().size());
        assertEquals(LinkNodeId.SKILL01, state.routes().get(com.inigmasgames.hytalerpg.domain.PassiveSlot.PASSIVE03).getLast());

        UUID rejectedPlayer = UUID.randomUUID();
        revision = bundle.service().getPresentationView(rejectedPlayer).state().revision;
        assertTrue(mutations.assign(rejectedPlayer, revision, LinkNodeId.SKILL01, "quick_slash").success());
        var before = bundle.service().getPresentationView(rejectedPlayer).state();
        var rejected = mutations.assign(rejectedPlayer, before.revision, LinkNodeId.PASSIVE01, "fork");
        assertFalse(rejected.success());
        assertEquals(ValidationCode.WRONG_FAMILY, rejected.code());
        var after = bundle.service().getPresentationView(rejectedPlayer).state();
        assertEquals(before.revision, after.revision);
        assertTrue(after.passive(com.inigmasgames.hytalerpg.domain.PassiveSlot.PASSIVE01).isEmpty());
        assertEquals(before.linkEdges(), after.linkEdges());
    }

    @Test void catalogProjectionSupportsTabsSearchWeaponFiltersDetailsAndPlaceholderIcon() {
        var bundle = Stage01BTestSupport.bundle();
        var projection = new RpgSkillTreeProjectionService(bundle.catalog(), bundle.service(),
                new StaticSkillTreeLayout(), true);
        UUID player = UUID.randomUUID();
        var skills = projection.project(player, StaticSkillTreeViewModel.Tab.SKILLS, "fire",
                "Staffs", "STAFF", LinkNodeId.SKILL01, "fire_bolt");
        assertFalse(skills.library().isEmpty());
        assertTrue(skills.library().stream().anyMatch(item -> item.id().equals("fire_bolt")));
        assertTrue(skills.library().stream().allMatch(item -> item.weaponRequirement().contains("Staff")));
        assertTrue(skills.weaponFilters().contains(RpgSkillTreeProjectionService.CURRENT_WEAPON_FILTER));
        assertTrue(skills.library().stream().allMatch(item -> item.iconPath().equals(RpgSkillTreeProjectionService.PLACEHOLDER_ICON)));
        assertEquals("SKILL", skills.details().kind());
        assertTrue(skills.details().facts().stream().anyMatch(value -> value.startsWith("Power:")));

        var passives = projection.project(player, StaticSkillTreeViewModel.Tab.PASSIVES, "potency",
                "", "", LinkNodeId.PASSIVE01, "potency");
        assertEquals(1, passives.library().size());
        assertEquals("PASSIVE", passives.details().kind());
        assertTrue(passives.details().description().contains("15%"));
    }

    @Test void hudResourceDefinesTenVisibleSegmentsAndOnlyThreeNonOverlappingRpgCells() throws Exception {
        String hud = Files.readString(Path.of("src/main/resources/Common/UI/Custom/RpgHud.ui"));
        for (int index = 1; index <= 10; index++) assertTrue(hud.contains("#XpPip" + index + "Fill"));
        assertTrue(hud.contains("#Skill1Name")); assertTrue(hud.contains("#Skill2Name")); assertTrue(hud.contains("#Skill3Name"));
        assertFalse(hud.contains("#Skill4Name"));
        assertTrue(hud.contains("Right: 18, Top: 112"));
    }

    @Test void installedNativeHudExposesSeparateAbilitiesComponentButNoRpgProjectionApi() {
        assertEquals("Abilities", HudComponent.Abilities.name());
        assertDoesNotThrow(() -> Class.forName("com.hypixel.hytale.server.core.inventory.InventoryComponent$AbilitySlots",
                false, getClass().getClassLoader()));
    }

    @Test void normalUiLoggerContainsActualValuesAndSuppressesRoutineHudInfo() {
        Logger logger = Logger.getLogger(RpgUiTraceService.class.getName());
        class Capture extends Handler { String message = ""; @Override public void publish(LogRecord record) { message = record.getMessage(); }
            @Override public void flush() {} @Override public void close() {} }
        Capture capture = new Capture(); logger.addHandler(capture); logger.setLevel(Level.INFO);
        RpgUiTraceService trace = new RpgUiTraceService(temporary.resolve("ui-trace.jsonl"));
        UUID player = UUID.randomUUID();
        trace.trace(player, "SKILLTREE_TEST", "corr-16", Map.of("page", "skilltree", "result", "PASS"));
        assertTrue(capture.message.contains("event=SKILLTREE_TEST"));
        assertTrue(capture.message.contains("player=" + player));
        assertFalse(capture.message.contains("{0}"));
        capture.message = "unchanged";
        trace.trace(player, "HUD_REFRESHED", "poll", Map.of("component", "hud"));
        assertEquals("unchanged", capture.message);
        trace.close(); logger.removeHandler(capture);
    }
}
