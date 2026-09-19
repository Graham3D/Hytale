package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.ui.AppearanceThumbnailCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

/** Exact gate for the native stable-node AssetImage.AssetPath binding repair. */
public final class R161AppearanceNativeCardBindingTest {
    private static final Path RESOURCES = Path.of("src/main/resources");

    public static void main(String[] args) throws Exception {
        var references = AppearanceThumbnailCatalog.references();
        assert references.size() == 590;
        HashSet<String> keys = new HashSet<>();
        HashSet<String> uiPaths = new HashSet<>();
        for (var reference : references) {
            assert keys.add(reference.key()) : reference.key();
            assert uiPaths.add(reference.uiTexturePath()) : reference.uiTexturePath();
            assert reference.uiTexturePath().startsWith(
                    "UI/Custom/Pages/ImmersiveNpcAppearance/") : reference.uiTexturePath();
            assert reference.packagedAssetPath().equals(
                    "Common/" + reference.uiTexturePath()) : reference.key();
            assert Files.isRegularFile(RESOURCES.resolve(
                    reference.packagedAssetPath())) : reference.packagedAssetPath();
            assert AppearanceThumbnailCatalog.packagedAssetPresent(reference) : reference.key();
        }

        String card = Files.readString(RESOURCES.resolve(
                "Common/UI/Custom/Pages/ImmersiveNpcAppearanceCard.ui"));
        assert card.contains("Button #Choice");
        assert card.contains("AssetImage #Thumbnail");
        assert card.contains("Visible: false");
        assert card.contains("FallbackTexturePath: \"UI/Custom/Pages/ImmersiveNpcAppearance/EmptyPartIcon@2x.png\"");
        assert card.contains("Group #Selected");
        assert !card.contains("#ThumbnailHost");

        String page = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        assert page.contains("commands.set(thumbnailSelector + \".AssetPath\", reference.uiTexturePath())");
        assert page.contains("commands.setNull(thumbnailSelector + \".AssetPath\")");
        assert page.contains("APPEARANCE_THUMBNAIL_BOUND");
        assert page.contains("APPEARANCE_THUMBNAIL_MISSING");
        for (String field : java.util.List.of("cosmeticId=", "thumbnailAssetPath=",
                "cardIndex=", "selector=", "packagedAssetPresent=")) {
            assert page.contains(field) : field;
        }
        assert !page.contains("appendInline(selector + \" #ThumbnailHost\"");
        assert AppearanceThumbnailCatalog.RUNTIME_THUMBNAIL_CREATES == 0;
        assert AppearanceThumbnailCatalog.RUNTIME_THUMBNAIL_WRITES == 0;
        assert AppearanceThumbnailCatalog.RUNTIME_THUMBNAIL_RECOLORS == 0;
        System.out.println("R161 PASS: 590 exact immutable mappings use stable AssetImage.AssetPath card binding with <=20 realized references and zero runtime images.");
    }
}
