package com.inigmasgames.hytalerpg.ui.hud;

import com.inigmasgames.hytalerpg.ui.CharacterXpProjectionService;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class R020HudCorrectionTest {
    private static final Path HUD = Path.of("src/main/resources/Common/UI/Custom/RpgHud.ui");
    private static final Path ASSETS = Path.of("src/main/resources/Common/UI/Custom/Assets/RpgHud");
    private static final Path COORDINATOR = Path.of(
            "src/main/java/com/inigmasgames/hytalerpg/ui/hud/RpgHudCoordinator.java");

    @Test void vanillaHytaleExclusivelyOwnsAllResourceBarPresentation() throws Exception {
        String hud = Files.readString(HUD);
        String coordinator = Files.readString(COORDINATOR);
        String runtimeHud = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/hytalerpg/ui/hud/RpgHud.java"));
        for (String marker : new String[]{"#Health", "#Mana", "#Stamina", "HealthBackground",
                "ManaBackground", "StaminaBackground", "ProgressBar@2x", "ProgressBarFill@2x",
                "CharacterPanelStatIconMana"}) assertFalse(hud.contains(marker), marker);
        for (String mutation : new String[]{"HudVisibilityLease", "setVisibleHudComponents",
                "getVisibleHudComponents", "RESOURCE_HUD_REFRESH", "manaFillWidth"}) {
            assertFalse(coordinator.contains(mutation), mutation);
            assertFalse(runtimeHud.contains(mutation), mutation);
        }
        assertFalse(Files.exists(Path.of(
                "src/main/java/com/inigmasgames/hytalerpg/ui/hud/HudVisibilityLease.java")));
    }

    @Test void experienceUsesOwnerAssetsAtAuthoritativeDimensionsAndExactCenteredLayerGeometry()
            throws Exception {
        String hud = Files.readString(HUD);
        int background = hud.indexOf("#ExperienceBackground");
        int fill = hud.indexOf("#ExperienceFill");
        int frame = hud.indexOf("#ExperienceFrame");
        assertTrue(background >= 0 && background < fill && fill < frame);
        assertTrue(hud.contains("#ExperienceHud { Anchor: (Bottom: 138, Width: 702, Height: 28)"));
        assertFalse(hud.contains("#ExperienceHud { Anchor: (Horizontal:"));
        assertTrue(hud.contains("#ExperienceBackground { Anchor: (Left: 3, Top: 0, Width: 696, Height: 28)"));
        assertTrue(hud.contains("#ExperienceFill { Anchor: (Left: 3, Top: 3, Width: 0, Height: 22)"));
        assertTrue(hud.contains("#ExperienceFrame { Anchor: (Left: 0, Top: 0, Width: 702, Height: 28)"));
        assertFalse(hud.contains("#XpLabel"));
        assertEquals(702, ImageIO.read(ASSETS.resolve("ExperienceFrame.png").toFile()).getWidth());
        assertEquals(28, ImageIO.read(ASSETS.resolve("ExperienceFrame.png").toFile()).getHeight());
        assertEquals(696, ImageIO.read(ASSETS.resolve("ExperienceBackground.png").toFile()).getWidth());
        assertEquals(28, ImageIO.read(ASSETS.resolve("ExperienceBackground.png").toFile()).getHeight());
        assertEquals(1, ImageIO.read(ASSETS.resolve("ExperienceBar.png").toFile()).getWidth());
        assertEquals(22, ImageIO.read(ASSETS.resolve("ExperienceBar.png").toFile()).getHeight());
    }

    @Test void experienceFillUsesNewBackgroundUsableWidthAtEveryBoundaryFixture() {
        CharacterXpProjectionService projection = new CharacterXpProjectionService();
        assertEquals(696, RpgHud.XP_FILL_WIDTH);
        assertEquals(0, RpgHud.xpFillWidth(projection.fixturePercent(0).progress()));
        assertEquals(69, RpgHud.xpFillWidth(projection.fixturePercent(9.9).progress()));
        assertEquals(70, RpgHud.xpFillWidth(projection.fixturePercent(10).progress()));
        assertEquals(348, RpgHud.xpFillWidth(projection.fixturePercent(50).progress()));
        assertEquals(695, RpgHud.xpFillWidth(projection.fixturePercent(99.9).progress()));
        assertEquals(696, RpgHud.xpFillWidth(projection.fixturePercent(100).progress()));
    }

    @Test void requiredPortableAssetsExistAndEveryRpgResourceAssetIsRetired() {
        for (String name : new String[]{"ExperienceFrame.png", "ExperienceBackground.png",
                "ExperienceBar.png", "Background_Ability_NotReady.png", "Frame_Ability_NotReady.png",
                "Frame_Ability_Ready.png", "OverlayAbilityErrorState.png"})
            assertTrue(Files.isRegularFile(ASSETS.resolve(name)), name);
        for (String retired : new String[]{"HealthBackground.png", "HealthBarFill.png", "HealthIcon.png",
                "ManaBackground.png", "ManaFill.png", "ManaIcon.png", "StaminaBackground.png",
                "StaminaBar.png", "StaminaIcon.png", "CharacterPanelStatIconMana@2x.png",
                "ProgressBar@2x.png", "ProgressBarFill@2x.png"})
            assertFalse(Files.exists(ASSETS.resolve(retired)), retired);
    }

    @Test void abilityAreaStillContainsAllThreeRpgSlotsBesideNativeSignature() throws Exception {
        String hud = Files.readString(HUD);
        assertTrue(hud.contains("#RpgAbility2"));
        assertTrue(hud.contains("#RpgAbility3"));
        assertTrue(hud.contains("#RpgAbility4"));
        assertTrue(hud.contains("Right: 390, Bottom: 40"));
        assertFalse(hud.contains("#RpgRevision"));
    }

    @Test void traceDescribesVanillaResourceOwnershipWithoutPollingResourcePresentation() throws Exception {
        String coordinator = Files.readString(COORDINATOR);
        for (String event : new String[]{"ABILITY_HUD_REFRESH", "ABILITY_SLOT_CHANGED",
                "XP_HUD_REFRESH", "HUD_LAYOUT_READY"}) assertTrue(coordinator.contains(event));
        assertTrue(coordinator.contains("VANILLA_HYTALE"));
        assertTrue(coordinator.contains("nativeResourceVisibilityMutation\", false"));
        assertFalse(coordinator.contains("RESOURCE_HUD_REFRESH"));
        assertFalse(coordinator.contains("HUD_VISIBILITY_RESTORED"));
        assertFalse(coordinator.contains("HUD_REFRESHED"));
    }
}
