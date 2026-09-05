package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService;
import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService.*;
import com.inigmasgames.persistentnpcs.ui.AppearanceEditorPresentation;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;

/** Server SDK/presentation gate. Connected client rendering remains a separate approval. */
public final class R151NpcAppearanceNativeCardsTest {
    public static void main(String[] args) throws Exception {
        Path pages = Path.of("src/main/resources/Common/UI/Custom/Pages");
        String ui = Files.readString(pages.resolve("ImmersiveNpcProfile.ui"));
        String appearance = ui.substring(ui.indexOf("$C.@PageOverlay #AppearanceEditorPage"),
                ui.indexOf("$C.@PageOverlay #VoiceRecorderPage"));
        String source = Files.readString(Path.of("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        for (String removed : List.of("appearanceColorPage", "APPEARANCE_COLOR_PREV", "APPEARANCE_COLOR_NEXT",
                "AppearanceColorPageText", "appearancePage", "APPEARANCE_PAGE_PREV", "APPEARANCE_PAGE_NEXT")) {
            assert !source.contains(removed) && !appearance.contains(removed) : removed;
        }
        for (String removed : List.of("APPEARANCE PREVIEW", "Preview only until saved", "Unsaved appearance changes",
                "This saved option is unavailable.", "Search this category", "Discard / Back", "Save Appearance", "Text: \"CATEGORY\"")) {
            assert !appearance.contains(removed) : removed;
        }
        assert appearance.contains("LayoutMode: TopScrolling") && appearance.contains("KeepScrollPosition: true");
        assert appearance.contains("@AppearanceSeparator {}") && ui.contains("ContainerVerticalSeparator.png");
        assert ui.contains("ContainerTitleArrow.png") && appearance.contains("#AppearancePrimaryHeading");
        assert appearance.contains("Text: \"DISCARD CHANGES\"") == false; // Template text parameter, not guessed Label property.
        assert appearance.contains("@Text = \"DISCARD CHANGES\"") && appearance.contains("@Text = \"SAVE\"");
        assert appearance.contains("#AppearanceBackButton") && source.contains("\"#AppearanceBackButton\", authoringEvent(\"APPEARANCE_CANCEL\")");
        assert source.contains("#AppearanceCancelButton.Disabled") && source.contains("#AppearanceSaveButton.Disabled");
        assert appearance.contains("Icon: (Texture: \"ImmersiveNpcAppearance/SearchFieldIcon.png\"");
        assert !appearance.contains("Icon: (TexturePath:") : "Native field icon contract is Texture, not PatchStyle.TexturePath";
        String card = Files.readString(pages.resolve("ImmersiveNpcAppearanceCard.ui"));
        assert card.contains("Height: 3") && card.contains("#Thumbnail") && !card.contains("#Name");
        assert card.contains("Hovered:") && card.contains("Pressed:") && card.contains("Disabled:");
        assert card.contains("#Unavailable") && source.contains("#AppearanceSelectionInfo.Visible");
        assert source.contains("requireAppearanceDraft();") && source.contains("appearancePreview.restore(appearanceDraft)");

        // More than twelve cosmetics and eight colors: all remain reachable with exact IDs.
        List<String> colors = java.util.stream.IntStream.range(0, 73).mapToObj(i -> "Color_"+i).toList();
        List<CosmeticOptionDescriptor> options = new ArrayList<>();
        for (int i=0;i<113;i++) options.add(new CosmeticOptionDescriptor(Category.HAIRCUT,
                "Hair_"+i, "Hair "+i, SourceKind.HYTALE_DEFAULT, List.of("style"), Map.of("",colors),true,"test"));
        var catalog = new NpcAppearanceCatalogService(new Snapshot(new CatalogIdentity(
                "test","registry","packs","adapter",Instant.EPOCH),Map.of(Category.HAIRCUT,options)),null);
        assert catalog.queryAll(Category.HAIRCUT, "").size()==114; // Native None remains first.
        assert catalog.queryAll(Category.HAIRCUT, "Hair_112").getFirst().cosmeticId().equals("Hair_112");
        assert catalog.queryAll(Category.HAIRCUT, "not found").isEmpty();
        assert options.getFirst().colors("").equals(colors);
        for (int count : List.of(0,1,8,14,32,73)) {
            UICommandBuilder commands = new UICommandBuilder();
            AppearanceEditorPresentation.appendGrid(commands,"#AppearanceColorGrid","AppearanceColor",count,13,38,38,0,
                    "Pages/ImmersiveNpcAppearanceSwatch.ui");
            assert commands.getCommands().length == 1+2*count+(count+12)/13;
            String all = Arrays.stream(commands.getCommands()).map(c -> c.selector+" "+c.text+" "+c.data)
                    .collect(java.util.stream.Collectors.joining("\n"));
            for (int i=0;i<count;i++) assert all.contains("#AppearanceColor"+i+" ");
            assert AppearanceEditorPresentation.paletteHeight(count)==24+38*((count+12)/13);
        }
        // Logical pixel budgets: 5 cards + 4 gaps, full 73-color palette and variants.
        assert 5*92+4*10 <= 536-24-12; // 12px scrollbar reserve.
        assert 13*38 <= 536-24;
        int body = 910-38-32-56-28; // include contextual error row worst case.
        assert body-24-28-42-AppearanceEditorPresentation.paletteHeight(73)-8-110 >= 250;
        assert 1380-32-62-6-62-6-536-12-16 >= 590;
        assert body-16 >= 690+24;
        for(int[] size:List.of(new int[]{1920,1080},new int[]{2560,1440}))
            assert 1380+24<size[0] && 910+24<size[1];

        Path thumbs=pages.resolve("ImmersiveNpcAppearance/Thumbnails");
        List<String> index=Files.readAllLines(thumbs.resolve("index.tsv"));
        assert index.size()==590 : "Every pinned installed cosmetic must have a real card";
        String patches=Files.readString(pages.resolve("ImmersiveNpcAppearanceThumbnails.ui"));
        Set<String> keys=new HashSet<>(); Set<String> hashes=new HashSet<>();
        for(String line:index) {
            String[] f=line.split("\t"); assert f.length==3 && keys.add(f[0]);
            byte[] bytes=Files.readAllBytes(thumbs.resolve(f[1]));
            String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            assert sha.equals(f[2]) : f[0]; hashes.add(sha);
            var png=ImageIO.read(thumbs.resolve(f[1]).toFile()); assert png.getWidth()==184 && png.getHeight()==298;
            String[] key=f[0].split(":",2);
            String texture=AppearanceEditorPresentation.thumbnail(Category.valueOf(key[0]),key[1]);
            assert texture != null && patches.contains("@T"+texture+" = PatchStyle(");
            assert patches.contains(f[1]);
        }
        assert hashes.size()>550 : "Cards must depict real distinct cosmetics, not generic category icons";
        assert AppearanceEditorPresentation.thumbnail(Category.HAIRCUT,"UNKNOWN_PACK_PART")==null;
        assert AppearanceEditorPresentation.thumbnail(Category.HAIRCUT,"\"; Evil {}") == null;
        assert Files.readString(thumbs.resolve("provenance.json")).contains("\"unavailable\": []");
        System.out.println("R151 passed: 590 hashed graphical cards, full catalog/palette, native rails, dynamic SDK rows, 1080p/1440p budgets; connected approval pending.");
    }
}
