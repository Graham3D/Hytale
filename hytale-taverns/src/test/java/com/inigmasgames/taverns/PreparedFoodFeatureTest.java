package com.inigmasgames.taverns;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Prepared food registry, freshness, recipe, and plate-loop regression coverage. */
public final class PreparedFoodFeatureTest {
    private PreparedFoodFeatureTest() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("PreparedFood: registry");
        PreparedFoodRegistry registry = PreparedFoodRegistry.loadDefault();
        Map<String, String> expected = expectedFoods();
        require(registry.definitions().size() == 16, "Prepared registry does not contain 16 foods");
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            PreparedFoodRegistry.Definition definition = registry
                    .byBaseFoodId(entry.getKey()).orElseThrow();
            require(definition.preparedFoodId().equals(entry.getValue()),
                    "Wrong prepared mapping for " + entry.getKey());
            require(registry.byPreparedFoodId(entry.getValue()).orElseThrow()
                            .baseFoodId().equals(entry.getKey()),
                    "Prepared reverse mapping failed for " + entry.getValue());
            requirePreparedAsset(definition);
        }

        System.out.println("PreparedFood: metadata");
        PreparedFoodRegistry.Definition bread = registry.byBaseFoodId("Food_Bread").orElseThrow();
        long preparedAt = 1_000_000L;
        PreparedMeal.Details details = PreparedMeal.inspect(
                bread.preparedFoodId(),
                PreparedMeal.createMetadata(bread, preparedAt),
                registry).orElseThrow();
        require(details.metadataValid(), "Freshness metadata was not valid");
        require(details.baseFoodId().equals("Food_Bread"), "Base food metadata was wrong");
        require(details.preparedAt() == preparedAt, "preparedAt was wrong");
        require(details.expiresAt() == preparedAt + 60_000L, "expiresAt was wrong");
        require(details.isFresh(details.expiresAt() - 1), "Meal expired too early");
        require(!details.isFresh(details.expiresAt()), "Meal stayed fresh at its expiry instant");
        require(PreparedMeal.inspect(
                        "Food_Bread", PreparedMeal.createMetadata(bread, preparedAt), registry).isEmpty(),
                "Ordinary vanilla Bread was identified as Tavern Prepared");

        System.out.println("PreparedFood: integration");
        String tableComponent = source("TableServingComponent.java");
        require(tableComponent.contains("new KeyedCodec<>(\"Item\", ItemStack.CODEC)"),
                "Table serving no longer persists complete ItemStack metadata");
        String tableManager = source("TableServingManager.java");
        require(tableManager.contains("ItemStack servingStack = heldStack.withQuantity(1)"),
                "Table placement no longer preserves the prepared ItemStack metadata");
        require(tableManager.contains("replacePlate(key, DIRTY_PLATE_ITEM_ID"),
                "Successful meal completion does not create a Dirty Plate");
        require(tableManager.contains("removePlate(key, commandBuffer)"),
                "Prepared meal pickup can duplicate its Clean Plate");

        String patron = source("TavernPatronManager.java");
        require(patron.contains("PreparedMeal.inspect(snapshot.foodStack(), preparedFoods)"),
                "Patron delivery does not inspect prepared metadata");
        require(patron.contains("prepared.get().isFresh(System.currentTimeMillis())"),
                "Patron delivery does not enforce freshness");
        require(!patron.contains("getAssetMap().values().stream()"),
                "Patron orders still scan every vanilla consumable");

        String crafting = source("PreparedCraftingManager.java");
        require(crafting.contains("CraftRecipeEvent.Pre"),
                "Prepared crafting has no cancellable Pre validation");
        require(crafting.contains("window.getX(), window.getY(), window.getZ()"),
                "Prepared crafting does not use the workstation coordinates");
        require(crafting.contains("CoreType.KITCHEN"),
                "Prepared crafting does not require a Kitchen Core");
        require(crafting.contains("event.getQuantity() != 1"),
                "Prepared bulk crafting is not rejected");
        require(crafting.contains("event.setCancelled(true)"),
                "Prepared output is not replaced with timestamped output");

        System.out.println("PreparedFood: plate loop");
        String cleanPlate = resource("/Server/Item/Items/Taverns/Tavern_Clean_Plate.json");
        String dirtyPlate = resource("/Server/Item/Items/Taverns/Dirty_Plate.json");
        require(cleanPlate.contains("Blocks/Miscellaneous/Plate.blockymodel"),
                "Clean Plate does not reuse Deco_Plate model");
        require(dirtyPlate.contains("Blocks/Miscellaneous/Plate.blockymodel"),
                "Dirty Plate does not reuse Deco_Plate model");
        require(dirtyPlate.contains("Blocks/Taverns/Tableware/Plate_Dirty_Texture.png"),
                "Dirty Plate does not use its Tavern texture");
        require(!dirtyPlate.contains("Consumable"), "Dirty Plate is marked as food");
        String dishwasher = resource("/Server/Item/Items/Bench/Taverns/Bench_Dishwasher.json");
        require(dishwasher.contains("Blocks/Miscellaneous/Cauldron.blockymodel"),
                "Dishwasher does not reuse Alchemy_Cauldron model");
        require(dishwasher.contains("\"Id\": \"TavernDishwasher\""),
                "Dishwasher bench ID is missing");
        String washRecipe = resource(
                "/Server/Item/Recipes/Taverns/Tavern_Dishwasher_Clean_Plate.json");
        require(washRecipe.contains("\"ItemId\": \"Dirty_Plate\""),
                "Dishwasher recipe does not consume a Dirty Plate");
        require(washRecipe.contains("\"ItemId\": \"Tavern_Clean_Plate\""),
                "Dishwasher recipe does not output a Clean Plate");

        System.out.println("PreparedFood: currency assets");
        requireCurrencyAsset("Copper", "Coins", "Deco_Treasure", "Copper_Pile_Texture.png");
        requireCurrencyAsset("Silver", "Coins", "Deco_Treasure", "Silver_Pile_Texture.png");
        requireCurrencyAsset("Gold", "Coins", "Deco_Treasure", null);
        requireCurrencyAsset("Copper", "Pile", "Deco_Treasure_Pile_Small", "Copper_Pile_Texture.png");
        requireCurrencyAsset("Silver", "Pile", "Deco_Treasure_Pile_Small", "Silver_Pile_Texture.png");
        requireCurrencyAsset("Gold", "Pile", "Deco_Treasure_Pile_Small", null);
        requireCurrencyAsset("Copper", "Coffer", "Deco_Treasure_Pile_Large", "Copper_Pile_Texture.png");
        requireCurrencyAsset("Silver", "Coffer", "Deco_Treasure_Pile_Large", "Silver_Pile_Texture.png");
        requireCurrencyAsset("Gold", "Coffer", "Deco_Treasure_Pile_Large", null);
        System.out.println("PreparedFoodFeatureTest passed");
    }

    private static void requireCurrencyAsset(
            String denomination, String form, String parent, String texture) throws Exception {
        String id = "Tavern_Currency_" + denomination + "_" + form;
        String item = resource("/Server/Item/Items/Taverns/Currency/" + id + ".json");
        require(item.contains("\"Parent\": \"" + parent + "\""),
                id + " does not inherit the requested vanilla treasure asset");
        if (texture == null) {
            require(!item.contains("CustomModelTexture"),
                    id + " does not preserve the vanilla gold presentation");
        } else {
            require(item.contains("Blocks/Taverns/Currency/" + texture),
                    id + " does not use its denomination texture");
        }
    }

    private static void requirePreparedAsset(
            PreparedFoodRegistry.Definition definition) throws Exception {
        String item = resource(
                "/Server/Item/Items/Food/Taverns/" + definition.preparedFoodId() + ".json");
        require(item.contains("\"Parent\": \"" + definition.baseFoodId() + "\""),
                definition.preparedFoodId() + " does not inherit its vanilla presentation");
        require(item.contains("\"MaxStack\": 1"),
                definition.preparedFoodId() + " is stackable");
        require(item.contains("\"ItemId\": \"Tavern_Clean_Plate\""),
                definition.preparedFoodId() + " recipe is missing Clean Plate");
        require(item.contains("\"Id\": \"Cookingbench\""),
                definition.preparedFoodId() + " is not on the vanilla Chef's Stove");
    }

    private static Map<String, String> expectedFoods() {
        LinkedHashMap<String, String> foods = new LinkedHashMap<>();
        foods.put("Food_Bread", "Tavern_Prepared_Food_Bread");
        foods.put("Food_Cheese", "Tavern_Prepared_Food_Cheese");
        foods.put("Food_Fish_Grilled", "Tavern_Prepared_Food_Fish_Grilled");
        foods.put("Food_Kebab_Fruit", "Tavern_Prepared_Food_Kebab_Fruit");
        foods.put("Food_Kebab_Meat", "Tavern_Prepared_Food_Kebab_Meat");
        foods.put("Food_Kebab_Mushroom", "Tavern_Prepared_Food_Kebab_Mushroom");
        foods.put("Food_Pie_Apple", "Tavern_Prepared_Food_Pie_Apple");
        foods.put("Food_Pie_Meat", "Tavern_Prepared_Food_Pie_Meat");
        foods.put("Food_Pie_Pumpkin", "Tavern_Prepared_Food_Pie_Pumpkin");
        foods.put("Food_Popcorn", "Tavern_Prepared_Food_Popcorn");
        foods.put("Food_Salad_Berry", "Tavern_Prepared_Food_Salad_Berry");
        foods.put("Food_Salad_Caesar", "Tavern_Prepared_Food_Salad_Caesar");
        foods.put("Food_Salad_Mushroom", "Tavern_Prepared_Food_Salad_Mushroom");
        foods.put("Food_Vegetable_Cooked", "Tavern_Prepared_Food_Vegetable_Cooked");
        foods.put("Food_Wildmeat_Cooked", "Tavern_Prepared_Food_Wildmeat_Cooked");
        foods.put("Plant_Fruit_Apple", "Tavern_Prepared_Plant_Fruit_Apple");
        return foods;
    }

    private static String resource(String path) throws Exception {
        try (InputStream stream = PreparedFoodFeatureTest.class.getResourceAsStream(path)) {
            require(stream != null, "Missing resource " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String source(String name) throws Exception {
        return Files.readString(Path.of(
                "src", "main", "java", "com", "inigmasgames", "taverns", name));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
