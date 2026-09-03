package com.inigmasgames.taverns;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DoorInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Locale;
import java.util.function.Consumer;
import org.joml.Vector3i;

/**
 * Executes Hytale's own Door interaction for a server-controlled patron.
 *
 * <p>This deliberately delegates to {@link DoorInteraction}: paired doors,
 * connected states, collision hitboxes, animations and sound all stay owned by
 * vanilla. Tavern never writes a Door block state or visual transform itself.
 */
final class TavernDoorOperator extends DoorInteraction {
    private final InteractionManager proxyManager = new InteractionManager(null, null);
    private final CooldownHandler cooldowns = new CooldownHandler();
    private final Consumer<Throwable> error;

    TavernDoorOperator(Consumer<Throwable> error) {
        this.error = error;
    }

    boolean openIfClosed(
            World world,
            CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> patronRef,
            Vector3i doorPosition) {
        BlockType current = TavernPatronManager.loadedBlockType(
                world, doorPosition.x(), doorPosition.y(), doorPosition.z());
        if (current == null || !current.isDoor()) {
            return false;
        }
        if (!isClosed(current)) {
            return true;
        }
        try {
            InteractionContext context = InteractionContext.forProxyEntity(
                    proxyManager, patronRef, patronRef, commandBuffer);
            interactWithBlock(
                    world,
                    commandBuffer,
                    InteractionType.Use,
                    context,
                    null,
                    new Vector3i(doorPosition),
                    cooldowns);
            BlockType updated = TavernPatronManager.loadedBlockType(
                    world, doorPosition.x(), doorPosition.y(), doorPosition.z());
            return updated != null && updated.isDoor() && !isClosed(updated);
        } catch (RuntimeException exception) {
            error.accept(exception);
            return false;
        }
    }

    private static boolean isClosed(BlockType block) {
        String state = block.getStateForBlock(block);
        if (state != null && state.toLowerCase(Locale.ROOT).contains("open")) {
            return false;
        }
        String hint = block.getInteractionHint();
        return hint == null || !hint.toLowerCase(Locale.ROOT).contains("closedoor");
    }
}
