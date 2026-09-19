package com.inigmasgames.canvasui.rendering;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/** Ordinary CustomUI page used only as the Phase-A negative input-context control. */
public final class CanvasCursorProbePage extends CustomUIPage {
    private final String context;
    private final Runnable dismissed;

    public CanvasCursorProbePage(PlayerRef playerRef, String context, Runnable dismissed) {
        super(playerRef, CustomPageLifetime.CanDismiss);
        this.context = context;
        this.dismissed = dismissed;
    }

    @Override public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
                                @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append("CanvasCursorProbeHud.ui");
        CanvasCursorProbeHud.writeIdentity(commands, context);
    }

    public void refresh(CanvasCursorProbeHud.Status status) {
        UICommandBuilder commands = new UICommandBuilder();
        CanvasCursorProbeHud.writeStatus(commands, status);
        sendUpdate(commands, false);
    }

    public void closeFromService() { close(); }

    @Override public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        dismissed.run();
    }
}
