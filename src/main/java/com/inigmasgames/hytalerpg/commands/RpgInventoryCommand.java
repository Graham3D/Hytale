package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.ui.inventory.InventoryEntryAdapter;

/** Development entry while Page.Inventory substitution is unavailable. */
public final class RpgInventoryCommand extends AbstractPlayerCommand {
    private final InventoryEntryAdapter entry;

    public RpgInventoryCommand(InventoryEntryAdapter entry) {
        super("inventory", "Open the RPG inventory workspace (development entry).");
        this.entry = entry;
        setPermissionGroup(GameMode.Adventure);
    }

    @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                     PlayerRef player, World world) {
        if (!entry.open(player, ref, store))
            context.sendMessage(Message.raw("Inventory workspace is unavailable for this player."));
    }
}
