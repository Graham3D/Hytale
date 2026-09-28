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

public final class RpgCharacterCommand extends AbstractPlayerCommand {
    private final InventoryEntryAdapter entry;

    public RpgCharacterCommand(InventoryEntryAdapter entry) {
        super("character", "Open the unified RPG Inventory/Character screen.");
        this.entry = entry;
        setPermissionGroup(GameMode.Adventure);
    }

    @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                     PlayerRef playerRef, World world) {
        if (!entry.open(playerRef, ref, store))
            context.sendMessage(Message.raw("Inventory workspace is unavailable for this player."));
    }
}
