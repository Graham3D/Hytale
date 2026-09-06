package com.inigmasgames.hytalerpg.phase00;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Set;

final class HudClearProbeCommand extends PlayerProbeCommand {
    HudClearProbeCommand() {
        super("rpgp00-hud-clear", "Remove the Phase 00 HUD probe and restore the native HUD snapshot.");
    }

    @Override
    protected void executeProbe(CommandContext context, Store<EntityStore> store,
                                Ref<EntityStore> ref, PlayerRef playerRef, Player player, World world) {
        HudManager hud = player.getHudManager();
        if (hud.getCustomHud(Phase00Hud.KEY) != null) {
            hud.removeCustomHud(playerRef, Phase00Hud.KEY);
        }
        Set<HudComponent> original = HudProbeState.take(playerRef.getUuid());
        if (original != null) {
            hud.setVisibleHudComponents(playerRef, original);
            context.sendMessage(Message.raw("Phase 00 HUD removed; original native HUD snapshot restored."));
        } else {
            hud.resetVisibleHudComponents(playerRef);
            context.sendMessage(Message.raw("Phase 00 HUD removed; no snapshot existed, so native HUD defaults were restored."));
        }
    }
}
