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

final class HudShowProbeCommand extends PlayerProbeCommand {
    HudShowProbeCommand() {
        super("rpgp00-hud", "Show the temporary Phase 00 HUD replacement probe.");
    }

    @Override
    protected void executeProbe(CommandContext context, Store<EntityStore> store,
                                Ref<EntityStore> ref, PlayerRef playerRef, Player player, World world) {
        HudManager hud = player.getHudManager();
        HudProbeState.remember(playerRef.getUuid(), hud.getVisibleHudComponents());
        if (hud.getCustomHud(Phase00Hud.KEY) == null) {
            hud.addCustomHud(playerRef, new Phase00Hud(playerRef, StatSnapshot.read(store, ref)));
        }
        hud.hideHudComponents(playerRef, HudComponent.Health, HudComponent.Mana, HudComponent.Stamina);
        context.sendMessage(Message.raw("Phase 00 HUD shown; native Health/Mana/Stamina hidden. Run /rpgp00-hud-clear to restore."));
    }
}
