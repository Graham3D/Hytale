package com.inigmasgames.persistentnpcs;

import com.hypixel.hytale.protocol.PlayerSkin;
import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService.Category;
import com.inigmasgames.persistentnpcs.appearance.NpcSkinCodecAdapter;
import java.nio.file.Files;
import java.nio.file.Path;

/** Regression for preview-only reveal of cosmetics hidden by outer layers. */
public final class R158AppearanceFocusedPreviewTest {
    private R158AppearanceFocusedPreviewTest() { }

    public static void main(String[] arguments) throws Exception {
        coveredClothingIsRevealedWithoutMutatingTheDraft();
        faceAndHairObstructionsArePreviewOnly();
        schedulingIsFocusAwareAndGenerationGated();
        System.out.println("R158 PASS: covered cosmetics are revealed in preview without mutating authoritative appearance layers.");
    }

    private static void coveredClothingIsRevealedWithoutMutatingTheDraft() {
        PlayerSkin source = skin();

        PlayerSkin undertop = NpcSkinCodecAdapter.focusedPreviewSkin(source, Category.UNDERTOP);
        assert undertop.overtop == null;
        assert "Inner_Shirt.Blue".equals(undertop.undertop);

        PlayerSkin pants = NpcSkinCodecAdapter.focusedPreviewSkin(source, Category.PANTS);
        assert pants.overpants == null;
        assert "Pants.Black".equals(pants.pants);

        PlayerSkin underwear = NpcSkinCodecAdapter.focusedPreviewSkin(source, Category.UNDERWEAR);
        assert underwear.pants == null && underwear.overpants == null;
        assert underwear.undertop == null && underwear.overtop == null;
        assert "Suit.Red".equals(underwear.underwear);

        assert "Outer_Coat.Black".equals(source.overtop);
        assert "Overpants.Brown".equals(source.overpants);
        assert "Pants.Black".equals(source.pants);
        assert "Inner_Shirt.Blue".equals(source.undertop)
                : "Preview focus must never mutate the authoritative draft";
    }

    private static void faceAndHairObstructionsArePreviewOnly() {
        PlayerSkin source = skin();
        PlayerSkin hair = NpcSkinCodecAdapter.focusedPreviewSkin(source, Category.HAIRCUT);
        assert hair.headAccessory == null;
        assert "Hair.Brown".equals(hair.haircut);

        PlayerSkin face = NpcSkinCodecAdapter.focusedPreviewSkin(source, Category.EYES);
        assert face.faceAccessory == null;
        assert "Glasses.Black".equals(source.faceAccessory);
        assert "Hat.Black".equals(source.headAccessory);
    }

    private static void schedulingIsFocusAwareAndGenerationGated() throws Exception {
        String page = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        assert page.contains("|focus=\" + focusCategory.name()")
                && page.contains("appearanceCategory != focusCategory")
                && page.contains("appearancePreview.show(appearanceDraft, focusCategory)");
        String preview = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/appearance/NpcAppearancePreviewService.java"));
        assert preview.contains("focusedPreviewSkin")
                && preview.contains("outerLayersSuppressed=PREVIEW_ONLY")
                && preview.contains("viewerEcsMutation=false");
    }

    private static PlayerSkin skin() {
        PlayerSkin skin = new PlayerSkin();
        skin.underwear = "Suit.Red";
        skin.pants = "Pants.Black";
        skin.overpants = "Overpants.Brown";
        skin.undertop = "Inner_Shirt.Blue";
        skin.overtop = "Outer_Coat.Black";
        skin.haircut = "Hair.Brown";
        skin.headAccessory = "Hat.Black";
        skin.faceAccessory = "Glasses.Black";
        return skin;
    }
}
