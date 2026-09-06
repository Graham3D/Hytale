package com.inigmasgames.hytalerpg.phase00;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

final class MouseProbeCommand extends PlayerProbeCommand {
    MouseProbeCommand() {
        super("rpgp00-mouse-probe", "Open the low-level mouse drag/pan feasibility probe.");
    }

    @Override
    protected void executeProbe(CommandContext context, Store<EntityStore> store,
                                Ref<EntityStore> ref, PlayerRef playerRef, Player player, World world) {
        player.getPageManager().openCustomPage(ref, store,
                new MouseProbePage(playerRef, CustomPageLifetime.CanDismiss));
        context.sendMessage(Message.raw("Phase 00 mouse probe: hold left to drag; hold middle to pan; release and reopen to verify persistence."));
    }
}
