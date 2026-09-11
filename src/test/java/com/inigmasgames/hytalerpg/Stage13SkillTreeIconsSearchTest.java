package com.inigmasgames.hytalerpg;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.ui.skilltree.*;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises real CustomUI event codec/builders and production projection, not connected-client proof. */
class Stage13SkillTreeIconsSearchTest {
    private static final Path RES = Path.of("src/main/resources");

    @Test void boundDynamicSearchValueDecodesThroughActualPageCodecIncludingClear() throws Exception {
        var events = new UIEventBuilder();
        var bind = RpgSkillTreePage.class.getDeclaredMethod("bindStatic", UIEventBuilder.class);
        bind.setAccessible(true); bind.invoke(null, events);
        var search = Arrays.stream(events.getEvents()).filter(e -> e.selector.equals("#SearchInput")).toList();
        assertEquals(1, search.size());
        var wire = BsonDocument.parse(search.getFirst().data);
        assertEquals("#SearchInput.Value", wire.getString("@Value").getValue());
        var codecField = RpgSkillTreePage.Data.class.getDeclaredField("CODEC"); codecField.setAccessible(true);
        var valueField = RpgSkillTreePage.Data.class.getDeclaredField("value"); valueField.setAccessible(true);
        var actionField = RpgSkillTreePage.Data.class.getDeclaredField("action"); actionField.setAccessible(true);
        var codec = (BuilderCodec<?>) codecField.get(null);
        for (String query : List.of("firebolt", "Quick Slash", "potency", "")) {
            wire.put("@Value", new BsonString(query)); // Client property substitution, not a network simulation.
            Object decoded = codec.decode(wire, new ExtraInfo());
            assertEquals(query, valueField.get(decoded));
            assertEquals("search", actionField.get(decoded));
        }
    }

    @Test void skillSearchSupportsSpacedCompactCanonicalAndCaseInsensitiveNames() {
        var bundle = Stage01BTestSupport.bundle();
        var projector = new RpgSkillTreeProjectionService(bundle.catalog(), bundle.service(), new StaticSkillTreeLayout(), true);
        UUID player = UUID.randomUUID();
        for (var skill : Map.of("fire_bolt", List.of("fire bolt", "Firebolt", "FIRE_BOLT", "fire-bolt"),
                "quick_slash", List.of("quick slash", "Quickslash", "QUICK_SLASH")).entrySet()) {
            for (String query : skill.getValue()) {
                var result = projector.project(player, StaticSkillTreeViewModel.Tab.SKILLS, query, "", "", LinkNodeId.SKILL01, "");
                assertEquals(List.of(skill.getKey()), result.library().stream().map(StaticSkillTreeViewModel.LibraryItem::id).toList(), query);
            }
        }
        assertEquals(89, projector.project(player, StaticSkillTreeViewModel.Tab.SKILLS, "", "", "", null, "").library().size());
        assertTrue(projector.project(player, StaticSkillTreeViewModel.Tab.SKILLS, "no-such-content-xyz", "", "", null, "").library().isEmpty());
        assertTrue(projector.project(player, StaticSkillTreeViewModel.Tab.SKILLS, "firebolt", "Swords", "SWORD", null, "").library().isEmpty());
    }

    @Test void passiveSearchAndClearRetainAll66CanonicalEntries() {
        var bundle = Stage01BTestSupport.bundle();
        var projector = new RpgSkillTreeProjectionService(bundle.catalog(), bundle.service(), new StaticSkillTreeLayout(), true);
        UUID player = UUID.randomUUID();
        assertEquals(List.of("potency"), projector.project(player, StaticSkillTreeViewModel.Tab.PASSIVES, "POTENCY", "", "", null, "")
                .library().stream().map(StaticSkillTreeViewModel.LibraryItem::id).toList());
        assertEquals(67, projector.project(player, StaticSkillTreeViewModel.Tab.PASSIVES, "", "", "", null, "").library().size());
    }

    @Test void equippedAndLibraryIconsFollowAuthoritativeSkillIds() {
        var bundle = Stage01BTestSupport.bundle();
        var layout = new StaticSkillTreeLayout();
        var mutations = new RpgSkillTreeMutationService(bundle.service(), layout);
        var projector = new RpgSkillTreeProjectionService(bundle.catalog(), bundle.service(), layout, true);
        UUID player = UUID.randomUUID();
        for (var assignment : Map.of(LinkNodeId.SKILL01, "quick_slash", LinkNodeId.SKILL02, "fire_bolt").entrySet()) {
            assertTrue(mutations.assign(player, bundle.service().getPresentationView(player).state().revision,
                    assignment.getKey(), assignment.getValue()).success());
            var model = projector.project(player, StaticSkillTreeViewModel.Tab.SKILLS, assignment.getValue(), "", "", assignment.getKey(), "");
            assertEquals(RpgSkillIcons.forSkill(assignment.getValue()), model.nodes().get(assignment.getKey()).iconPath());
            assertEquals(RpgSkillIcons.forSkill(assignment.getValue()), model.library().getFirst().iconPath());
            assertEquals(assignment.getValue(), model.details().id());
        }
        assertEquals(RpgSkillTreeProjectionService.PLACEHOLDER_ICON, RpgSkillIcons.forSkill("frost_bolt"));
        assertFalse(projector.project(player, StaticSkillTreeViewModel.Tab.SKILLS, "", "", "", null, "").nodes().get(LinkNodeId.SKILL03).occupied());
    }

    @Test void realRendererWritesAllIconSurfacesAndDoesNotRebindSearchOrRewriteTypedValue() throws Exception {
        var bundle = Stage01BTestSupport.bundle();
        var projector = new RpgSkillTreeProjectionService(bundle.catalog(), bundle.service(), new StaticSkillTreeLayout(), true);
        var page = new RpgSkillTreePage(null, projector, null, null);
        var modelField = RpgSkillTreePage.class.getDeclaredField("model"); modelField.setAccessible(true);
        var tabField = RpgSkillTreePage.class.getDeclaredField("tab"); tabField.setAccessible(true);
        var render = RpgSkillTreePage.class.getDeclaredMethod("render", UICommandBuilder.class, UIEventBuilder.class);
        render.setAccessible(true);
        for (var tab : StaticSkillTreeViewModel.Tab.values()) {
            var model = projector.project(UUID.randomUUID(), tab, tab == StaticSkillTreeViewModel.Tab.SKILLS ? "firebolt" : "potency",
                    "", "", LinkNodeId.SKILL01, tab == StaticSkillTreeViewModel.Tab.SKILLS ? "fire_bolt" : "potency");
            modelField.set(page, model); tabField.set(page, tab);
            var commands = new UICommandBuilder(); var events = new UIEventBuilder();
            render.invoke(page, commands, events);
            var selectors = Arrays.stream(commands.getCommands()).map(c -> c.selector).filter(Objects::nonNull).toList();
            assertTrue(selectors.contains("#DetailsIcon #Overlay.Background"));
            assertTrue(selectors.contains("#LibraryRows[0] #Icon #Overlay.Background"));
            assertTrue(selectors.contains("#Skill01Icon #Overlay.Background"));
            assertTrue(selectors.contains("#SearchLabel.TextSpans"));
            assertFalse(selectors.contains("#SearchInput.Value"));
            assertTrue(Arrays.stream(events.getEvents()).allMatch(e -> e.selector.startsWith("#LibraryRows[") || e.selector.startsWith("#FilterRows[")));
            String commandsText = Arrays.toString(Arrays.stream(commands.getCommands()).map(c -> c.data).toArray());
            assertTrue(commandsText.contains(tab == StaticSkillTreeViewModel.Tab.SKILLS ? "Search skills" : "Search passives"));
            if (tab == StaticSkillTreeViewModel.Tab.SKILLS) assertTrue(commandsText.contains("SkillFirebolt.png"));
        }
    }

    @Test void originalTransparentIconBytesArePackagedForNativeItemsAndCustomUi() throws Exception {
        for (var entry : Map.of("SkillFirebolt.png", "8B4E285602E6A13A37A594F9E23B4C53E8A3AF0A0BC403D0083553910D6A7C7C",
                "SkillQuickslash.png", "D3DBBBF03497A5CE845A48FE6F816955934271C668A6461F0D44CD1AEC077EC1").entrySet()) {
            byte[] nativeBytes = Files.readAllBytes(RES.resolve("Common/Icons/Items/RPG/" + entry.getKey()));
            assertEquals(entry.getValue(), HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(nativeBytes)));
            assertArrayEquals(nativeBytes, Files.readAllBytes(RES.resolve("Common/UI/Custom/Icons/RPG/" + entry.getKey())));
            var image = ImageIO.read(new java.io.ByteArrayInputStream(nativeBytes));
            assertEquals(128, image.getWidth()); assertEquals(128, image.getHeight());
            assertTrue(image.getColorModel().hasAlpha());
            boolean transparent = false;
            for (int x = 0; x < 128; x++) for (int y = 0; y < 128; y++)
                if ((image.getRGB(x, y) >>> 24) == 0) transparent = true;
            assertTrue(transparent);
        }
    }

    @Test void nativeItemIconsChangeWithoutAddingNativeGameplayCostsOrCastBranches() throws Exception {
        for (var entry : Map.of("Fire_Bolt", "SkillFirebolt.png", "Quick_Slash", "SkillQuickslash.png").entrySet()) {
            var item = BsonDocument.parse(Files.readString(RES.resolve("Server/Item/Items/RPG/Abilities/RPG_Ability_" + entry.getKey() + ".json")));
            assertEquals("Icons/Items/RPG/" + entry.getValue(), item.getString("Icon").getValue());
            assertTrue(Files.exists(RES.resolve("Common/" + item.getString("Icon").getValue())));
            var ability = item.getDocument("Ability");
            assertEquals(0, ability.getNumber("Cost").intValue()); assertEquals(0, ability.getNumber("Cooldown").intValue());
            assertEquals("None", ability.getString("CostType").getValue());
            assertEquals("Root_RPG_Ability_Bridge", ability.getString("Cast").getValue());
            assertEquals("Primary", ability.getString("Slot").getValue());
        }
    }

    @Test void customUiLayersBackgroundThenTransparentOverlayThenNativeFrameAndLabelsSearch() throws Exception {
        for (String file : List.of("RpgSkillTree.ui", "RpgSkillTreeLibraryRow.ui")) {
            String ui = Files.readString(RES.resolve("Common/UI/Custom/" + file));
            int count = 0, start = 0;
            while ((start = ui.indexOf("Icons/RPG/Background_Ability_Ready.png", start)) >= 0) {
                int overlay = ui.indexOf("#Overlay", start), frame = ui.indexOf("Icons/RPG/Frame_Ability_Ready.png", start);
                assertTrue(overlay > start && frame > overlay); count++; start = frame + 1;
            }
            assertEquals(file.equals("RpgSkillTree.ui") ? 10 : 1, count);
        }
        String ui = Files.readString(RES.resolve("Common/UI/Custom/RpgSkillTree.ui"));
        assertTrue(ui.indexOf("#SearchLabel") < ui.indexOf("#SearchInput"));
        for (var entry : Map.of("Background_Ability_Ready.png", "BD6479423B1AC5AFEFA70DB19A37BE9B0A9C1CC0500E87E0B8AAFB17CE2CD556",
                "Frame_Ability_Ready.png", "E58BD509B7CB138C5875348747DEAC3251EDEF44BD6D0095D6D2257AAAF076FE").entrySet())
            assertEquals(entry.getValue(), HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(RES.resolve("Common/UI/Custom/Icons/RPG/" + entry.getKey())))));
    }
}
