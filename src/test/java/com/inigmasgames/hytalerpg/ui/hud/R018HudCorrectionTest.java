package com.inigmasgames.hytalerpg.ui.hud;

import com.inigmasgames.hytalerpg.ui.CharacterXpProjectionService;
import com.inigmasgames.hytalerpg.ui.model.NativeResourceView;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class R018HudCorrectionTest {
    private static final Path HUD = Path.of("src/main/resources/Common/UI/Custom/RpgHud.ui");
    private static final Path ASSETS = Path.of("src/main/resources/Common/UI/Custom/Assets/RpgHud");

    @Test void resourcesAreGraphicalNativeAuthorityAndInHealthManaStaminaOrder() throws Exception {
        String hud = Files.readString(HUD);
        assertTrue(hud.indexOf("#HealthBar") < hud.indexOf("#ManaBar"));
        assertTrue(hud.indexOf("#ManaBar") < hud.indexOf("#StaminaBar"));
        assertTrue(hud.contains("HealthBackground.png"));
        assertTrue(hud.contains("ManaBackground.png"));
        assertTrue(hud.contains("StaminaBackground.png"));
        assertFalse(hud.contains("TexturePath: \"Assets/RpgHud/"));
        assertTrue(hud.contains("TexturePath: \"Common/UI/Custom/Assets/RpgHud/"));
        assertEquals(0, RpgHud.resourceFillWidth(new NativeResourceView(10, 0)));
        assertEquals(105, RpgHud.resourceFillWidth(new NativeResourceView(50, 100)));
        assertEquals(RpgHud.RESOURCE_FILL_WIDTH, RpgHud.resourceFillWidth(new NativeResourceView(120, 100)));
    }

    @Test void abilityAreaContainsAllThreeRpgSlotsBesidePreservedNativeSignature() throws Exception {
        String hud = Files.readString(HUD);
        assertTrue(hud.contains("#RpgAbility2"));
        assertTrue(hud.contains("#RpgAbility3"));
        assertTrue(hud.contains("#RpgAbility4"));
        assertTrue(hud.contains("Text: \"Ability2\""));
        assertTrue(hud.contains("Text: \"Ability3\""));
        assertTrue(hud.contains("Text: \"Ability4\""));
        assertTrue(hud.contains("Right: 390, Bottom: 40"));
        assertFalse(hud.contains("#RpgRevision"));
        assertFalse(hud.contains("Right: 18, Top: 112"));
    }

    @Test void abilitySlotsExposeEmptyReadyCooldownAndUnavailableLayers() throws Exception {
        String hud = Files.readString(HUD);
        for (int index = 1; index <= 3; index++) {
            assertTrue(hud.contains("#Skill" + index + "Icon"));
            assertTrue(hud.contains("#Skill" + index + "Cooldown"));
            assertTrue(hud.contains("#Skill" + index + "Unavailable"));
            assertTrue(hud.contains("#Skill" + index + "ReadyFrame"));
            assertTrue(hud.contains("#Skill" + index + "NotReadyFrame"));
        }
    }

    @Test void experienceUsesOwnerAssetsInBackgroundFillFrameOrderAndLeftAnchor() throws Exception {
        String hud = Files.readString(HUD);
        int background = hud.indexOf("#ExperienceBackground");
        int fill = hud.indexOf("#ExperienceFill");
        int frame = hud.indexOf("#ExperienceFrame");
        assertTrue(background >= 0 && background < fill && fill < frame);
        assertTrue(hud.contains("#ExperienceFill { Anchor: (Left: 3, Top: 3, Width: 0, Height: 22)"));
        assertEquals(931, ImageIO.read(ASSETS.resolve("ExperienceFrame.png").toFile()).getWidth());
        assertEquals(925, ImageIO.read(ASSETS.resolve("ExperienceBackground.png").toFile()).getWidth());
        assertEquals(1, ImageIO.read(ASSETS.resolve("ExperienceBar.png").toFile()).getWidth());
        assertEquals(22, ImageIO.read(ASSETS.resolve("ExperienceBar.png").toFile()).getHeight());
    }

    @Test void experienceFixtureWidthsAreExactAndLeftToRight() {
        CharacterXpProjectionService projection = new CharacterXpProjectionService();
        assertEquals(0, RpgHud.xpFillWidth(projection.fixturePercent(0).progress()));
        assertEquals(92, RpgHud.xpFillWidth(projection.fixturePercent(9.9).progress()));
        assertEquals(93, RpgHud.xpFillWidth(projection.fixturePercent(10).progress()));
        assertEquals(463, RpgHud.xpFillWidth(projection.fixturePercent(50).progress()));
        assertEquals(924, RpgHud.xpFillWidth(projection.fixturePercent(99.9).progress()));
        assertEquals(925, RpgHud.xpFillWidth(projection.fixturePercent(100).progress()));
    }

    @Test void allRequiredPortableAssetsArePackaged() {
        for (String name : new String[]{"HealthBackground.png", "HealthBarFill.png", "HealthIcon.png",
                "ManaBackground.png", "ManaFill.png", "ManaIcon.png",
                "StaminaBackground.png", "StaminaBar.png", "StaminaIcon.png",
                "ExperienceFrame.png", "ExperienceBackground.png", "ExperienceBar.png",
                "Background_Ability_NotReady.png", "Frame_Ability_NotReady.png", "Frame_Ability_Ready.png",
                "OverlayAbilityErrorState.png"}) assertTrue(Files.isRegularFile(ASSETS.resolve(name)), name);
    }

    @Test void meaningfulHudTransitionVocabularyReplacesPollingTraceSpam() throws Exception {
        String coordinator = Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/ui/hud/RpgHudCoordinator.java"));
        for (String event : new String[]{"RESOURCE_HUD_REFRESH", "ABILITY_HUD_REFRESH", "ABILITY_SLOT_CHANGED",
                "XP_HUD_REFRESH", "HUD_LAYOUT_READY"}) assertTrue(coordinator.contains(event));
        assertFalse(coordinator.contains("HUD_REFRESHED"));
        assertFalse(coordinator.contains("RATE_TRACE_NANOS"));
    }
}
