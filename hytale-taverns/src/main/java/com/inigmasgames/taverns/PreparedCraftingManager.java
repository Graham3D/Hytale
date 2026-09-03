package com.inigmasgames.taverns;

import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.builtin.crafting.window.CraftingWindow;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.BlockWindow;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.inventory.InventoryUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.Optional;

/** Validates prepared cooking at the actual open Chef's Stove and stamps fresh outputs. */
final class PreparedCraftingManager {
    static final String CHEFS_STOVE_ITEM_ID = "Bench_Cooking";

    private final TavernRepository repository;
    private final PreparedFoodRegistry foods;

    PreparedCraftingManager(TavernRepository repository, PreparedFoodRegistry foods) {
        this.repository = repository;
        this.foods = foods;
    }

    void validate(
            CraftRecipeEvent.Pre event,
            PlayerRef playerRef,
            Player player,
            World world) {
        Optional<PreparedFoodRegistry.Definition> definition =
                preparedOutput(event.getCraftedRecipe());
        if (definition.isEmpty()) {
            return;
        }
        if (event.getQuantity() != 1) {
            event.setCancelled(true);
            playerRef.sendMessage(Message.raw(
                    "Tavern Prepared meals are made one serving at a time."));
            return;
        }

        Optional<BlockWindow> stove = activeChefsStove(player, world);
        if (stove.isEmpty()) {
            event.setCancelled(true);
            playerRef.sendMessage(Message.raw(
                    "Tavern Prepared meals require an open Chef's Stove."));
            return;
        }
        BlockWindow window = stove.get();
        boolean insideKitchen = repository.findCoreContaining(
                world.getWorldConfig().getUuid(),
                CoreType.KITCHEN,
                window.getX(), window.getY(), window.getZ()).isPresent();
        if (!insideKitchen) {
            event.setCancelled(true);
            playerRef.sendMessage(Message.raw(
                    "That Chef's Stove is outside a Kitchen Core volume."));
        }
    }

    void complete(
            CraftRecipeEvent.Post event,
            Ref<EntityStore> playerEntity,
            PlayerRef playerRef,
            CommandBuffer<EntityStore> commandBuffer) {
        Optional<PreparedFoodRegistry.Definition> definition =
                preparedOutput(event.getCraftedRecipe());
        if (definition.isEmpty()) {
            return;
        }

        // CraftingManager dispatches Post after consuming inputs but before it
        // gives outputs. Cancelling lets Tavern replace only the prepared output
        // with a timestamped stack while preserving side outputs such as buckets.
        event.setCancelled(true);
        long preparedAt = System.currentTimeMillis();
        List<ItemStack> outputs = CraftingManager.getOutputItemStacks(
                event.getCraftedRecipe(), 1);
        PlayerSettings settings = commandBuffer.getComponent(
                playerEntity, PlayerSettings.getComponentType());
        if (settings == null) {
            settings = PlayerSettings.defaults();
        }
        for (ItemStack output : outputs) {
            ItemStack delivered = output.getItemId().equals(definition.get().preparedFoodId())
                    ? PreparedMeal.stamp(output, definition.get(), preparedAt)
                    : output;
            ItemContainer target = InventoryUtils.getContainerForItemPickup(
                    playerEntity, delivered.getItem(), settings, commandBuffer);
            SimpleItemContainer.addOrDropItemStack(
                    commandBuffer, playerEntity, target, delivered);
        }
        playerRef.sendMessage(Message.raw(
                "Prepared meal ready for delivery for 60 seconds."));
    }

    Optional<PreparedFoodRegistry.Definition> preparedOutput(CraftingRecipe recipe) {
        if (recipe == null) {
            return Optional.empty();
        }
        for (ItemStack output : CraftingManager.getOutputItemStacks(recipe, 1)) {
            Optional<PreparedFoodRegistry.Definition> found =
                    foods.byPreparedFoodId(output.getItemId());
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockWindow> activeChefsStove(Player player, World world) {
        List<BlockWindow> windows = player.getWindowManager().getWindows().stream()
                .filter(CraftingWindow.class::isInstance)
                .map(BlockWindow.class::cast)
                .filter(window -> window.getBlockType() != null
                        && CHEFS_STOVE_ITEM_ID.equals(window.getBlockType().getId()))
                .filter(window -> {
                    BlockType current = BlockType.getAssetMap().getAsset(world.getBlock(
                            window.getX(), window.getY(), window.getZ()));
                    return current != null && CHEFS_STOVE_ITEM_ID.equals(current.getId());
                })
                .toList();
        return windows.size() == 1 ? Optional.of(windows.getFirst()) : Optional.empty();
    }
}
