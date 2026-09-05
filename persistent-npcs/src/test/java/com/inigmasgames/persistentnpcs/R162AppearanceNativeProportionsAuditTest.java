package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService;
import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService.Category;
import java.nio.file.Files;
import java.nio.file.Path;

/** Gate for native 92x149 card proportions and the rejected flat-mask color fallback. */
public final class R162AppearanceNativeProportionsAuditTest {
    public static void main(String[] args) throws Exception {
        String card = Files.readString(Path.of(
                "src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcAppearanceCard.ui"));
        String page = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        String catalog = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/appearance/NpcAppearanceCatalogService.java"));
        String audit = Files.readString(Path.of(
                "tools/audit_appearance_two_card_composition.py"));

        assert card.contains("Anchor: (Left: 0, Right: 0, Top: 0, Bottom: 0)");
        assert !card.contains("#ThumbnailNamePlate") && !card.contains("#ThumbnailName");
        assert page.contains("CARD_COLUMNS, 92, 149, 10");
        assert page.contains(".TooltipText\", option.displayName()") : "Names remain tooltips";
        assert page.contains("sourceDimensions=\" + reference.width() + \"x\" + reference.height()")
                && page.contains("hostDimensions=92x149 aspectPreserved=true");
        for (String event : java.util.List.of("APPEARANCE_NATIVE_ICON_RESOLVED",
                "APPEARANCE_NATIVE_ICON_MISSING", "APPEARANCE_ICON_FRAMING_RESOLVED",
                "APPEARANCE_COLOR_ICON_STATE_CHANGED",
                "APPEARANCE_COLOR_ICON_STATE_UNCHANGED")) {
            assert page.contains(event) : event;
        }
        assert catalog.contains("record NativeIconPresentation")
                && catalog.contains("nativeIconPresentation(")
                && catalog.contains("\"NONE\", categoryFraming(category)");
        assert NpcAppearanceCatalogService.categoryFraming(Category.PANTS)
                .equals("92x149:WAIST_TO_FEET");
        assert NpcAppearanceCatalogService.categoryFraming(Category.UNDERTOP)
                .equals("92x149:SHOULDERS_TO_WAIST");
        assert NpcAppearanceCatalogService.categoryFraming(Category.HAIRCUT)
                .equals("92x149:HEAD_AND_SHOULDERS");
        assert audit.contains("UNDERTOP:Wide_Neck_Shirt")
                && audit.contains("UNDERTOP:VNeck_Shirt")
                && audit.contains("runtimeImagesCreated")
                && audit.contains("REJECTED_NATIVE_COLOR_PARITY_NOT_REPRODUCIBLE");
        assert !page.contains("MaskTexturePath")
                && !page.contains("runtimeThumbnailRecolors++");
        System.out.println("R162 PASS: native 92x149 card geometry is preserved; the unsafe flat-mask color fallback remains rejected.");
    }
}
