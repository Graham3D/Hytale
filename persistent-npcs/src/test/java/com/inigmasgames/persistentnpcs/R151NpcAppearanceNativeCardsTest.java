package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService;
import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService.*;
import com.inigmasgames.persistentnpcs.ui.AppearanceEditorPresentation;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** R151 visual foundations retained after R155 retired per-cosmetic card textures. */
public final class R151NpcAppearanceNativeCardsTest {
    public static void main(String[] args) throws Exception {
        Path pages = Path.of("src/main/resources/Common/UI/Custom/Pages");
        String ui = Files.readString(pages.resolve("ImmersiveNpcProfile.ui"));
        String appearance = ui.substring(ui.indexOf("$C.@PageOverlay #AppearanceEditorPage"),
                ui.indexOf("$C.@PageOverlay #VoiceRecorderPage"));
        String source = Files.readString(Path.of("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        for (String removed : List.of("appearanceColorPage", "APPEARANCE_COLOR_PREV", "APPEARANCE_COLOR_NEXT",
                "AppearanceColorPageText", "#Thumbnail", "#Unavailable")) {
            assert !source.contains(removed) && !appearance.contains(removed) : removed;
        }
        assert appearance.contains("LayoutMode: TopScrolling") && !appearance.contains("KeepScrollPosition: true");
        assert appearance.contains("@AppearanceSeparator {}") && ui.contains("ContainerVerticalSeparator.png");
        assert ui.contains("ContainerTitleArrow.png") && appearance.contains("#AppearancePrimaryHeading");
        assert appearance.contains("@Text = \"DISCARD CHANGES\"") && appearance.contains("@Text = \"SAVE\"");
        assert appearance.contains("#AppearanceBackButton")
                && source.contains("\"#AppearanceBackButton\", authoringEvent(\"APPEARANCE_CANCEL\")");
        assert appearance.contains("Icon: (Texture: \"ImmersiveNpcAppearance/SearchFieldIcon.png\"");
        assert !appearance.contains("Icon: (TexturePath:");
        String card = Files.readString(pages.resolve("ImmersiveNpcAppearanceCard.ui"));
        assert card.contains("#Icon") && card.contains("#Name") && card.contains("#Id");
        assert card.contains("$C.@SecondaryButtonStyle") && card.contains("#Selected");

        List<CosmeticOptionDescriptor> options = new ArrayList<>();
        for (int i=0;i<113;i++) options.add(new CosmeticOptionDescriptor(Category.HAIRCUT,
                "Hair_"+i, "Hair "+i, SourceKind.HYTALE_DEFAULT, List.of("style"), Map.of("",List.of("Black")),true,"test"));
        var catalog = new NpcAppearanceCatalogService(new Snapshot(new CatalogIdentity(
                "test","registry","packs","adapter",Instant.EPOCH),Map.of(Category.HAIRCUT,options)),null);
        assert catalog.queryAll(Category.HAIRCUT, "").size()==114;
        Set<String> reached = new LinkedHashSet<>();
        int pagesCount = catalog.query(Category.HAIRCUT,"",0).pageCount();
        assert pagesCount==6;
        for(int page=0;page<pagesCount;page++) {
            CatalogPage result=catalog.query(Category.HAIRCUT,"",page);
            assert result.descriptors().size()<=20 && result.pageSize()==20 && result.totalCount()==114;
            result.descriptors().forEach(option -> { assert reached.add(option.cosmeticId()); });
        }
        assert reached.size()==114 && reached.contains("Hair_112") && reached.contains("");
        assert catalog.query(Category.HAIRCUT,"Hair_112",0).descriptors().getFirst().cosmeticId().equals("Hair_112");

        assert 5*92+4*10 <= 536-24-12;
        assert 13*38 <= 536-24;
        for(int[] size:List.of(new int[]{1920,1080},new int[]{2560,1440}))
            assert 1380+24<size[0] && 910+24<size[1];
        for(Category category:Category.values()) assert AppearanceEditorPresentation.icon(category)!=null;
        System.out.println("R151 retained: native rails/chrome, static icon/name selectors, bounded 20-card pages and full catalog reachability.");
    }
}
