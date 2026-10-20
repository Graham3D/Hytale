package com.inigmasgames.hytalerpg.phase00;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Arrays;

final class CapabilitiesProbeCommand extends PlayerProbeCommand {
    CapabilitiesProbeCommand() {
        super("rpgp00-capabilities", "Print exact current-build Phase 00 API enums.");
    }

    @Override
    protected void executeProbe(CommandContext context, Store<EntityStore> store,
                                Ref<EntityStore> ref, PlayerRef playerRef, Player player, World world) {
        context.sendMessage(Message.raw("InteractionType=" + Arrays.toString(InteractionType.values())));
        context.sendMessage(Message.raw("CustomUIEventBindingType=" + Arrays.toString(CustomUIEventBindingType.values())));
        context.sendMessage(Message.raw("HudComponent=" + Arrays.toString(HudComponent.values())));
        context.sendMessage(Message.raw("Page=" + Arrays.toString(Page.values())));
    }
}
