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
import com.inigmasgames.hytalerpg.ui.skilltree.SkillTreeEntryAdapter;

public final class RpgSkillTreeCommand extends AbstractPlayerCommand {
    private final SkillTreeEntryAdapter entry;

    public RpgSkillTreeCommand(SkillTreeEntryAdapter entry) {
        super("skilltree", "Open the server-authoritative RPG Canvas Skill Tree.");
        this.entry = entry;
        setPermissionGroup(GameMode.Adventure);
    }

    @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                     PlayerRef playerRef, World world) {
        var result = entry.open(playerRef, ref, store, world);
        context.sendMessage(Message.raw(result.message()));
    }
}
