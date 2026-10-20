package com.inigmasgames.hytalerpg.phase00;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

final class AbilityInputsProbeCommand extends PlayerProbeCommand {
    AbilityInputsProbeCommand() {
        super("rpgp00-ability-inputs", "Report observation-only Ability1-4 input chains and reset counts.");
    }

    @Override
    protected void executeProbe(CommandContext context, Store<EntityStore> store,
                                Ref<EntityStore> ref, PlayerRef playerRef, Player player, World world) {
        context.sendMessage(Message.raw(AbilityInputObserver.reportAndReset(playerRef.getUuid())));
        context.sendMessage(Message.raw("Press each current client Ability1-4 binding, then run this command again."));
    }
}
