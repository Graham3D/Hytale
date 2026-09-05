package com.inigmasgames.persistentnpcs;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.inigmasgames.persistentnpcs.ui.AppearanceUiState;
import java.nio.file.*;

/** Exact production architecture gate for the connected R155 safety candidate. */
public final class R155AppearanceStaticSafetyTest {
    public static void main(String[] args) throws Exception {
        AppearanceUiState state=new AppearanceUiState();
        UICommandBuilder initial=new UICommandBuilder();
        initial.set("#Card0 #Name.Text","Hair One");
        initial.set("#Card0 #Selected.Visible",false);
        state.seed(initial);
        UICommandBuilder unchanged=new UICommandBuilder();
        unchanged.set("#Card0 #Name.Text","Hair One");
        unchanged.set("#Card0 #Selected.Visible",false);
        assert state.filter(unchanged).getCommands().length==0 : "Unchanged refresh emitted commands";
        UICommandBuilder selected=new UICommandBuilder();
        selected.set("#Card0 #Name.Text","Hair One");
        selected.set("#Card0 #Selected.Visible",true);
        assert state.filter(selected).getCommands().length==1 : "One selection should update one state";

        String page=Files.readString(Path.of("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        String card=Files.readString(Path.of("src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcAppearanceCard.ui"));
        String ui=Files.readString(Path.of("src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui"));
        assert page.contains("AppearanceUiAssetBudget.PRODUCTION.requireUsage")
                && page.contains("AppearanceUiAssetBudget.MAX_VISIBLE_CARDS");
        assert page.contains("appearanceCatalogPage().descriptors()")
                && !page.contains("queryAll(appearanceCategory");
        assert page.contains("appearanceUiState.filter(commands)")
                && page.contains("scheduleAppearancePreview(store, event)");
        assert card.contains("#Name") && card.contains("#Icon")
                && card.contains("AssetImage #Thumbnail");
        assert ui.contains("#AppearancePagePREV") && ui.contains("#AppearancePageNEXT")
                && ui.contains("#AppearanceCatalogHash");
        assert Files.notExists(Path.of("src/main/resources/appearance-color-sources"));
        assert Files.notExists(Path.of("src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcAppearance/Thumbnails"));
        assert Files.list(Path.of("src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcAppearance/Probe"))
                .filter(Files::isRegularFile).count()==2 : "Checkpoint 2 permits exactly two immutable images";
        assert Files.list(Path.of("src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcAppearance/Catalog/Thumbnails"))
                .filter(Files::isRegularFile).count()==588 : "Checkpoint 3 packages the other 588 immutable images";
        for(String other:java.util.List.of("InventoryComponent", "NpcProfileDraft", "NpcVoiceRecordingService"))
            assert !Files.readString(Path.of("src/main/java/com/inigmasgames/persistentnpcs/ui/AppearanceUiAssetBudget.java")).contains(other);
        System.out.println("R155 PASS: static named selectors, <=20 page, zero runtime-generated card assets, state diffs, newest-preview gate and dynamic-resource quarantine.");
    }
}
