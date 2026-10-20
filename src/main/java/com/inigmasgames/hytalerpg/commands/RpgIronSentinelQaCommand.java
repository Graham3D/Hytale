package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutService;
import java.util.UUID;

/** Operator-only acquisition fixture until Iron Sentinel has an earned source. */
public final class RpgIronSentinelQaCommand extends AbstractPlayerCommand {
    private final RpgLoadoutService loadouts;

    public RpgIronSentinelQaCommand(RpgLoadoutService loadouts) {
        super("iron-sentinel", "Learn Iron Sentinel for connected QA; equip it in a skill slot afterward.");
        this.loadouts = loadouts;
        requirePermission(RpgGearCommand.AUTHOR_PERMISSION);
    }

    @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> actor,
                                     PlayerRef player, World world) {
        try {
            var view = loadouts.getPresentationView(player.getUuid());
            if (view.state().learnedSkills.contains("iron_sentinel")) {
                context.sendMessage(Message.raw("Iron Sentinel is already learned. Use /rpg equip skill01 Iron Sentinel."));
                return;
            }
            var result = loadouts.mutateProgress(player.getUuid(), view.state().revision,
                    "qa-iron-sentinel/" + UUID.randomUUID(), state -> state.learnedSkills.add("iron_sentinel"));
            context.sendMessage(Message.raw(result.success()
                    ? "Iron Sentinel learned for QA. Use /rpg equip skill01 Iron Sentinel."
                    : "Iron Sentinel QA grant: " + result.message()));
        } catch (RuntimeException error) {
            context.sendMessage(Message.raw("Iron Sentinel QA grant: " + error.getMessage()));
        }
    }
}
