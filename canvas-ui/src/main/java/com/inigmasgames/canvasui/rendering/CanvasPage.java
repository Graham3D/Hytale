package com.inigmasgames.canvasui.rendering;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.canvasui.runtime.CanvasSession;

import javax.annotation.Nonnull;

public final class CanvasPage extends CustomUIPage {
    private final CanvasSession session;
    private final HytaleCustomUiBackend backend;

    public CanvasPage(PlayerRef playerRef, CanvasSession session, HytaleCustomUiBackend backend) {
        super(playerRef, CustomPageLifetime.CanDismiss);
        this.session = session; this.backend = backend;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        backend.build(commands);
    }

    void flush(UICommandBuilder commands) { sendUpdate(commands, false); }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        session.close("PAGE_DISMISS");
    }
}
