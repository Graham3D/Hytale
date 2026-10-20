package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Read-only visual proof of the bounded offline armor/weapon pose batch. */
public final class RpgIconPoseCommand extends AbstractPlayerCommand {
    public RpgIconPoseCommand() {
        super("iconposes", "Open the read-only leather and weapon spatial-icon pose batch.");
        setPermissionGroup(GameMode.Adventure);
    }

    @Override protected void execute(CommandContext context, Store<EntityStore> store,
                                     Ref<EntityStore> ref, PlayerRef playerRef, World world) {
        var player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Player page manager unavailable."));
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new GalleryPage(playerRef));
    }

    private static final class GalleryPage extends CustomUIPage {
        private GalleryPage(PlayerRef playerRef) {
            super(playerRef, CustomPageLifetime.CanDismiss);
        }

        @Override public void build(Ref<EntityStore> ref, UICommandBuilder commands,
                                    UIEventBuilder events, Store<EntityStore> store) {
            commands.append("RpgIconPoseBatch.ui");
        }
    }
}
