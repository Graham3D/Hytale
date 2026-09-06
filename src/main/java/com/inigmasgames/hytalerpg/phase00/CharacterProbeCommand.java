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

final class CharacterProbeCommand extends PlayerProbeCommand {
    CharacterProbeCommand() {
        super("rpgp00-character", "Open the temporary Phase 00 Character page probe.");
    }

    @Override
    protected void executeProbe(CommandContext context, Store<EntityStore> store,
                                Ref<EntityStore> ref, PlayerRef playerRef, Player player, World world) {
        StatSnapshot snapshot = StatSnapshot.read(store, ref);
        player.getPageManager().openCustomPage(ref, store,
                new CharacterProbePage(playerRef, CustomPageLifetime.CanDismiss, snapshot));
        context.sendMessage(Message.raw("Phase 00: Character probe opened with server-owned native stat snapshot."));
    }
}
