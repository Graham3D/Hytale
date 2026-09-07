package com.inigmasgames.hytalerpg.ui.hud;

import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.inigmasgames.hytalerpg.ui.CharacterXpProjectionService;
import com.inigmasgames.hytalerpg.ui.model.NativeResourceView;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class R019HudCorrectionTest {
    private static final Path HUD = Path.of("src/main/resources/Common/UI/Custom/RpgHud.ui");
    private static final Path ASSETS = Path.of("src/main/resources/Common/UI/Custom/Assets/RpgHud");

    @Test void nativeHealthAndStaminaRemainVisibleAndOnlyNativeManaIsLeased() {
        Set<HudComponent> initial = EnumSet.of(HudComponent.Hotbar, HudComponent.Abilities,
                HudComponent.Health, HudComponent.Mana, HudComponent.Stamina);
        class Port implements HudVisibilityLease.Port {
            Set<HudComponent> visible = Set.copyOf(initial);
            @Override public Set<HudComponent> visible() { return visible; }
            @Override public void setVisible(Set<HudComponent> components) { visible = Set.copyOf(components); }
        }
        Port port = new Port();
        HudVisibilityLease lease = HudVisibilityLease.hideNativeManaForCustomPlacement(port);
        assertTrue(port.visible.contains(HudComponent.Health));
        assertTrue(port.visible.contains(HudComponent.Stamina));
        assertTrue(port.visible.contains(HudComponent.Abilities));
        assertFalse(port.visible.contains(HudComponent.Mana));
        lease.restore();
        assertEquals(initial, port.visible);
    }

    @Test void customHudContainsOnlyManaAndUsesReleaseInventoryGraphics() throws Exception {
        String hud = Files.readString(HUD);
        assertTrue(hud.contains("#ManaHud"));
        assertTrue(hud.contains("#ManaFill"));
        assertFalse(hud.contains("#HealthBar"));
        assertFalse(hud.contains("#HealthFill"));
        assertFalse(hud.contains("#StaminaBar"));
        assertFalse(hud.contains("#StaminaFill"));
        assertTrue(hud.contains("TexturePath: \"Assets/RpgHud/CharacterPanelStatIconMana.png\""));
        assertTrue(hud.contains("TexturePath: \"Assets/RpgHud/ProgressBar.png\""));
        assertTrue(hud.contains("TexturePath: \"Assets/RpgHud/ProgressBarFill.png\""));
        assertFalse(hud.contains("TexturePath: \"Common/UI/Custom/"));
        assertEquals(0, RpgHud.manaFillWidth(new NativeResourceView(10, 0)));
        assertEquals(71, RpgHud.manaFillWidth(new NativeResourceView(50, 100)));
        assertEquals(RpgHud.MANA_FILL_WIDTH, RpgHud.manaFillWidth(new NativeResourceView(120, 100)));
    }

    @Test void experienceUsesRequestedAssetsAndExactCenteredLayerGeometry() throws Exception {
        String hud = Files.readString(HUD);
        int background = hud.indexOf("#ExperienceBackground");
        int fill = hud.indexOf("#ExperienceFill");
        int frame = hud.indexOf("#ExperienceFrame");
        assertTrue(background >= 0 && background < fill && fill < frame);
        assertTrue(hud.contains("#ExperienceHud { Anchor: (Bottom: 138, Width: 931, Height: 28)"));
        assertFalse(hud.contains("#ExperienceHud { Anchor: (Horizontal:"));
        assertTrue(hud.contains("#ExperienceFill { Anchor: (Left: 3, Top: 3, Width: 0, Height: 22)"));
        assertFalse(hud.contains("#XpLabel"));
        assertEquals(931, ImageIO.read(ASSETS.resolve("ExperienceFrame.png").toFile()).getWidth());
        assertEquals(925, ImageIO.read(ASSETS.resolve("ExperienceBackground.png").toFile()).getWidth());
        assertEquals(1, ImageIO.read(ASSETS.resolve("ExperienceBar.png").toFile()).getWidth());
        assertEquals(22, ImageIO.read(ASSETS.resolve("ExperienceBar.png").toFile()).getHeight());
    }

    @Test void experienceFillIsExactAtAllBoundaryFixtures() {
        CharacterXpProjectionService projection = new CharacterXpProjectionService();
        assertEquals(0, RpgHud.xpFillWidth(projection.fixturePercent(0).progress()));
        assertEquals(92, RpgHud.xpFillWidth(projection.fixturePercent(9.9).progress()));
        assertEquals(93, RpgHud.xpFillWidth(projection.fixturePercent(10).progress()));
        assertEquals(463, RpgHud.xpFillWidth(projection.fixturePercent(50).progress()));
        assertEquals(924, RpgHud.xpFillWidth(projection.fixturePercent(99.9).progress()));
        assertEquals(925, RpgHud.xpFillWidth(projection.fixturePercent(100).progress()));
    }

    @Test void requiredPortableAssetsExistAndRetiredResourceCopiesAreGone() {
        for (String name : new String[]{"CharacterPanelStatIconMana@2x.png", "ProgressBar@2x.png",
                "ProgressBarFill@2x.png", "ExperienceFrame.png", "ExperienceBackground.png",
                "ExperienceBar.png", "Background_Ability_NotReady.png", "Frame_Ability_NotReady.png",
                "Frame_Ability_Ready.png", "OverlayAbilityErrorState.png"})
            assertTrue(Files.isRegularFile(ASSETS.resolve(name)), name);
        for (String retired : new String[]{"HealthBackground.png", "HealthBarFill.png", "HealthIcon.png",
                "ManaBackground.png", "ManaFill.png", "ManaIcon.png", "StaminaBackground.png",
                "StaminaBar.png", "StaminaIcon.png"})
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

    @Test void meaningfulHudTransitionVocabularyRemainsBounded() throws Exception {
        String coordinator = Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/ui/hud/RpgHudCoordinator.java"));
        for (String event : new String[]{"RESOURCE_HUD_REFRESH", "ABILITY_HUD_REFRESH", "ABILITY_SLOT_CHANGED",
                "XP_HUD_REFRESH", "HUD_LAYOUT_READY"}) assertTrue(coordinator.contains(event));
        assertFalse(coordinator.contains("HUD_REFRESHED"));
        assertFalse(coordinator.contains("RATE_TRACE_NANOS"));
    }
}
