package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService;
import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService.Category;
import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService.PrimaryCategory;
import com.inigmasgames.persistentnpcs.ui.AppearanceEditorPresentation;
import com.inigmasgames.persistentnpcs.ui.NpcProfilePage;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Layout budgets and packaged/SDK contracts, not a substitute for connected rendering. */
public final class R150NpcAppearancePolishTest {
    public static void main(String[] args) throws Exception {
        Path pages = Path.of("src/main/resources/Common/UI/Custom/Pages");
        String ui = Files.readString(pages.resolve("ImmersiveNpcProfile.ui"));
        String appearance = ui.substring(ui.indexOf("$C.@PageOverlay #AppearanceEditorPage"),
                ui.indexOf("$C.@PageOverlay #VoiceRecorderPage"));
        assert appearance.contains("$C.@DecoratedContainer #AppearanceWindow");
        assert appearance.contains("$C.@Title #AppearanceEditorTitle");
        assert appearance.contains("Width: 1380, Height: 910");
        assert appearance.contains("#AppearanceDraftMeta { Visible: false");
        assert appearance.contains("#AppearanceRegistryMeta { Visible: false");
        assert !appearance.contains("No game-owned thumbnails") && !appearance.contains("REGISTRY OPTIONS");
        assert appearance.contains("#AppearanceEmptyState") && appearance.contains("#AppearancePreviewFallback");

        // R151 supersedes the R150 fixed-grid/rail proportions; retain the shared
        // native frame, asset, selection, swatch and authority contracts below.
        for (int[] viewport : List.of(new int[] {1920, 1080}, new int[] {2560, 1440})) {
            assert 1380 + 24 < viewport[0] && 910 + 24 < viewport[1];
        }

        for (PrimaryCategory category : PrimaryCategory.values()) {
            assert appearance.contains("#AppearancePrimary" + category.name());
            checkIcon(pages, ui, AppearanceEditorPresentation.icon(category));
        }
        for (Category category : Category.values()) {
            checkIcon(pages, ui, AppearanceEditorPresentation.icon(category));
        }
        for (var spec : List.of(new Object[] {"Category", 7}, new Object[] {"Variant", 6})) {
            for (int i = 0; i < (int) spec[1]; i++) assert appearance.contains("#Appearance" + spec[0] + i);
        }
        var textures = Pattern.compile("\"(ImmersiveNpcAppearance/[^\"]+\\.png)\"").matcher(ui);
        while (textures.find()) {
            Path path = pages.resolve(textures.group(1).replace(".png", "@2x.png"));
            if (!Files.exists(path)) path = pages.resolve(textures.group(1));
            assert Files.isRegularFile(path) : path;
            assert ImageIO.read(path.toFile()) != null : "Unreadable packaged PNG " + path;
        }
        assert !appearance.contains("C:") && !appearance.contains("Client/Data");
        assert Files.readString(pages.resolve("ImmersiveNpcAppearanceSwatch.ui")).contains("MaskTexturePath: \"ImmersiveNpcAppearance/ColorOptionMask.png\"");
        assert ui.contains("@AppearanceAction = Button") && ui.contains("@Style = $C.@SecondaryButtonStyle");
        assert appearance.contains("@Style = $C.@DefaultButtonStyle");

        assert NpcAppearanceCatalogService.validSwatchColors(null).isEmpty();
        assert NpcAppearanceCatalogService.validSwatchColors(new String[] {"Blue", "#bad", null}).isEmpty();
        assert NpcAppearanceCatalogService.validSwatchColors(new String[] {
                "#121325", "#d7b698", "#ffffff"}).equals(List.of("#121325", "#d7b698"));
        assert AppearanceEditorPresentation.label("BrownSemiLight", 42).equals("Brown Semi Light");
        assert AppearanceEditorPresentation.label("A".repeat(200), 42).length() == 42;

        UICommandBuilder commands = new UICommandBuilder();
        var iconMethod = NpcProfilePage.class.getDeclaredMethod("setAppearanceIcon",
                UICommandBuilder.class, String.class, String.class, boolean.class);
        iconMethod.setAccessible(true);
        var selectedMethod = NpcProfilePage.class.getDeclaredMethod("setAppearanceSelection",
                UICommandBuilder.class, String.class, boolean.class);
        selectedMethod.setAccessible(true);
        for (boolean selected : List.of(false, true)) {
            for (Category category : Category.values()) {
                iconMethod.invoke(null, commands, "#AppearanceCategory0",
                        AppearanceEditorPresentation.icon(category), selected);
                selectedMethod.invoke(null, commands, "#AppearanceCategory0", selected);
            }
        }
        commands.setObject("#AppearanceColor0 #ColorA.Background",
                new PatchStyle().setColor(Value.of("#121325")));
        assert commands.getCommands().length == Category.values().length * 4 + 1;
        String page = Files.readString(Path.of("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        assert page.contains("swatchColors(") && page.contains("#Unknown.Visible");
        assert page.contains("setAppearanceSelection(commands, selector,")
                && page.contains("#AppearanceEmptyState.Visible");
        for (String action : List.of("APPEARANCE_PRIMARY", "APPEARANCE_CATEGORY", "APPEARANCE_SEARCH",
                "APPEARANCE_COLOR", "APPEARANCE_VARIANT",
                "APPEARANCE_RANDOMIZE", "APPEARANCE_RESET", "APPEARANCE_CANCEL", "APPEARANCE_SAVE")) {
            assert page.contains("authoringEvent(\"" + action + "\")");
        }
        String preview = Files.readString(Path.of("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcMeshPreviewSession.java"));
        assert preview.contains("authoritativeViewerEcsMutation=false") && preview.contains("restoreAuthoritativeTarget");
        System.out.println("R150 Appearance polish asset, layout-budget, selection and swatch SDK gates passed; connected render pending.");
    }

    private static void checkIcon(Path pages, String ui, String icon) throws Exception {
        for (String suffix : List.of("", "Selected")) {
            assert ui.contains("@AppearanceIcon" + icon + suffix + " = PatchStyle(");
            Path path = pages.resolve("ImmersiveNpcAppearance/" + icon + suffix + "@2x.png");
            assert Files.isRegularFile(path) && ImageIO.read(path.toFile()) != null : path;
        }
    }
}
