package com.inigmasgames.taverns;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Regression coverage for Relaxing progress, duration pause/resume, and assets. */
public final class RelaxedFeatureTest {
    private RelaxedFeatureTest() {
    }

    public static void main(String[] args) throws Exception {
        RelaxedSession session = new RelaxedSession();

        RelaxedSession.RelaxingUpdate update = session.tickInside(5.0f, true, 7);
        require(update.visible(), "Relaxing HUD did not start while seated");
        require(close(update.progress(), 0.5f), "Relaxing progress was not time-based");
        require(!update.completed(), "Relaxed completed before ten seconds");

        update = session.tickInside(1.0f, false, 7);
        require(!update.visible(), "Relaxing HUD remained after standing up");
        update = session.tickInside(9.9f, true, 7);
        require(update.visible(), "Relaxing did not restart after sitting again");
        require(!update.completed(), "Relaxed completed before the restarted ten seconds");
        update = session.tickInside(0.1f, true, 7);
        require(update.completed(), "Relaxed was not granted at ten seconds");
        require(session.isRelaxed(), "Relaxed state was not retained");
        require(close(session.durationForLeaving(), 420.0f),
                "Comfort-derived seven-minute duration was not snapshotted");

        session.pauseEffect(123.5f);
        require(close(session.durationForLeaving(), 123.5f),
                "Re-entering did not preserve the remaining outside duration");

        session.tickInside(0.1f, false, 9);
        session.tickInside(10.0f, true, 9);
        require(close(session.durationForLeaving(), 540.0f),
                "Relaxing again did not reset the leave snapshot");
        session.expire();
        require(!session.isRelaxed(), "Relaxed did not expire");

        require(RelaxedRegenerationSystem.nativeRegenerationHookAvailable(),
                "Hytale native regeneration hook is unavailable");
        require(close(
                        RelaxedRegenerationSystem.HEALTH_AND_MANA_REGENERATION_BONUS,
                        0.25f),
                "Relaxed Health/Mana regeneration bonus changed");
        require(close(RelaxedRegenerationSystem.STAMINA_REGENERATION_BONUS, 2.0f),
                "Relaxed testing Stamina regeneration bonus is not 200%");

        String effect = resource("/Server/Entity/Effects/Taverns_Relaxed.json");
        require(effect.contains("\"Name\": \"Relaxed\""),
                "Relaxed EntityEffect is missing");
        require(effect.contains(
                        "\"StatusEffectIcon\": \"Icons/CraftingCategories/Furniture/Misc.png\""),
                "Relaxed icon is incorrect");
        require(!effect.contains("StatModifiers"),
                "Relaxed reverted to periodic EntityEffect stat changes");

        String hud = resource("/Common/UI/Custom/Hud/TavernsRevision.ui");
        require(hud.contains("Group #RelaxingHud"), "Relaxing HUD is missing");
        require(hud.contains("Text: \"Relaxing\""), "Relaxing label is missing");
        require(hud.contains("Background: #D83A3A"), "Relaxing bar is not red");
        for (int index = 1; index <= 20; index++) {
            require(hud.contains("#RelaxingFill" + index),
                    "Relaxing fill segment " + index + " is missing");
        }
        require(!hud.contains("Group #TavernBuffStrip"),
                "Obsolete duplicate Tavern buff icons remain on CustomUIHud");
        require(!hud.contains("Background: \"Icons/Comfort.png\""),
                "Obsolete duplicate Comfort icon is still rendered");
        require(!hud.contains("Background: \"Icons/Relaxed.png\""),
                "Obsolete duplicate Relaxed icon is still rendered");
        require(hud.contains("Group #ComfortValueOverlay"),
                "Native Comfort status icon value overlay is missing");
        require(hud.contains("Label #ComfortValueWithRelaxed"),
                "Comfort value position for the Relaxed state is missing");
        require(hud.contains("Label #ComfortValueOnly"),
                "Comfort-only value position is missing");

        String comfortEffect = resource("/Server/Entity/Effects/Taverns_Comfort.json");
        require(comfortEffect.contains("\"StatusEffectIcon\": \"Icons/ResourceTypes/Fuel.png\""),
                "Native Comfort status icon changed");
        requireSupportedUiEscapes(hud);

        System.out.println("RelaxedFeatureTest passed");
    }

    private static String resource(String path) throws Exception {
        try (InputStream stream = RelaxedFeatureTest.class.getResourceAsStream(path)) {
            require(stream != null, "Missing resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static boolean close(float actual, float expected) {
        return Math.abs(actual - expected) < 0.001f;
    }

    private static void requireSupportedUiEscapes(String ui) {
        for (int index = 0; index < ui.length(); index++) {
            if (ui.charAt(index) != '\\') {
                continue;
            }
            require(index + 1 < ui.length(), "CustomUI document ends with an invalid escape");
            char escaped = ui.charAt(++index);
            require(escaped == '\\' || escaped == '"',
                    "CustomUI document contains an unsupported escape: \\" + escaped);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
