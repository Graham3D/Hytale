package com.inigmasgames.taverns;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Resource and state-transition coverage for Tavern Service. */
public final class TavernServiceFeatureTest {
    private TavernServiceFeatureTest() {
    }

    public static void main(String[] args) throws Exception {
        require(TavernsPlugin.toggledServiceStatus(TavernStatus.CLOSED).orElseThrow()
                        == TavernStatus.OPEN,
                "Closed Tavern did not open for service");
        require(TavernsPlugin.toggledServiceStatus(TavernStatus.OPEN).orElseThrow()
                        == TavernStatus.CLOSED,
                "Open Tavern did not close for service");
        require(TavernsPlugin.toggledServiceStatus(TavernStatus.REQUIRES_ATTENTION).isEmpty(),
                "Attention state was overwritten by Tavern Service");

        String service = resource(
                "/Server/Item/Items/Furniture/Tavern/Furniture_Tavern_Service.json");
        require(service.contains("\"Quality\": \"Uncommon\""),
                "Tavern Service is not Uncommon");
        require(service.contains(
                        "\"CustomModel\": \"Blocks/Decorative_Sets/Tavern/TavernSign.blockymodel\""),
                "Tavern Service does not use the Tavern sign visual");
        require(service.contains("\"ResourceTypeId\": \"Wood_Trunk\""),
                "Tavern Service does not use Any Tree Log");
        require(service.contains("\"ItemId\": \"Rock_Crystal_Green_Block\""),
                "Tavern Service recipe is missing Green Crystal");
        require(service.contains("\"Id\": \"Furniture_Bench\""),
                "Tavern Service is not assigned to Furniture Workbench");
        require(service.contains("\"Furniture_Misc\""),
                "Tavern Service is not assigned to Furniture Misc");
        require(service.contains("\"Use\": \"Tavern_Service_Use\""),
                "Tavern Service interaction is missing");

        String core = resource("/Server/Item/Items/Core/Core_Tavern.json");
        require(core.contains("\"Id\": \"Furniture_Bench\""),
                "Tavern Core is not assigned to Furniture Workbench");
        require(core.contains("\"Furniture_Misc\""),
                "Tavern Core is not assigned to Furniture Misc");
        require(core.contains("\"ItemId\": \"Rock_Crystal_Green_Block\""),
                "Tavern Core lost its Green Crystal ingredient");
        require(core.contains("\"ItemId\": \"Ingredient_Life_Essence_Concentrated\""),
                "Tavern Core lost its Greater Life Crystal ingredient");

        requireCoreRecipe("Core_Kitchen", "Rock_Crystal_Cyan_Block");
        requireCoreRecipe("Core_Bedroom", "Rock_Crystal_Blue_Block");

        String hud = resource("/Common/UI/Custom/Hud/TavernsRevision.ui");
        require(hud.contains("Group #ServiceAnnouncement"),
                "Tavern Service announcement HUD is missing");
        require(hud.contains("Anchor: (Top: 122, Width: 560, Height: 48)"),
                "Tavern Service announcement is not top-centered");

        System.out.println("TavernServiceFeatureTest passed");
    }

    private static void requireCoreRecipe(String coreId, String crystalId) throws Exception {
        String core = resource("/Server/Item/Items/Core/" + coreId + ".json");
        require(core.contains("\"Id\": \"Furniture_Bench\""),
                coreId + " is not assigned to Furniture Workbench");
        require(core.contains("\"Furniture_Misc\""),
                coreId + " is not assigned to Furniture Misc");
        require(core.contains("\"ItemId\": \"" + crystalId + "\""),
                coreId + " has the wrong crystal ingredient");
        require(core.contains("\"ItemId\": \"Ingredient_Life_Essence_Concentrated\""),
                coreId + " is missing its Greater Life Crystal ingredient");
        require(core.contains("\"Use\": \"Core_Tavern_Use\""),
                coreId + " does not reuse the Core editor interaction");
    }

    private static String resource(String path) throws Exception {
        try (InputStream stream = TavernServiceFeatureTest.class.getResourceAsStream(path)) {
            require(stream != null, "Missing resource: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
