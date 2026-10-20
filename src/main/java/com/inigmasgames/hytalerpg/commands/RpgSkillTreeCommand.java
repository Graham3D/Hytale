package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.ui.skilltree.RpgSkillTreeMutationService;
import com.inigmasgames.hytalerpg.ui.skilltree.RpgSkillTreePage;
import com.inigmasgames.hytalerpg.ui.skilltree.RpgSkillTreeProjectionService;
import com.inigmasgames.hytalerpg.ui.trace.RpgUiTraceService;

public final class RpgSkillTreeCommand extends AbstractPlayerCommand {
    private final RpgSkillTreeProjectionService projection;
    private final RpgSkillTreeMutationService mutations;
    private final RpgUiTraceService trace;

    public RpgSkillTreeCommand(RpgSkillTreeProjectionService projection,
                               RpgSkillTreeMutationService mutations, RpgUiTraceService trace) {
        super("skilltree", "Open the static server-authoritative RPG Skill Tree.");
        this.projection = projection; this.mutations = mutations; this.trace = trace;
        setPermissionGroup(GameMode.Adventure);
    }

    @Override protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                     PlayerRef playerRef, World world) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) { context.sendMessage(Message.raw("Player UI manager is unavailable.")); return; }
        player.getPageManager().openCustomPage(ref, store,
                new RpgSkillTreePage(playerRef, projection, mutations, trace));
    }
}
