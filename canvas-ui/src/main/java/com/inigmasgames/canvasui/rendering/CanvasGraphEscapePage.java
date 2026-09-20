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

/** Transparent native page lease whose dismiss event maps Escape onto the editor's exact close path. */
public final class CanvasGraphEscapePage extends CustomUIPage {
    private final Runnable dismissed;
    private boolean armed=true;
    public CanvasGraphEscapePage(PlayerRef playerRef,Runnable dismissed){
        super(playerRef, CustomPageLifetime.CanDismiss);this.dismissed=dismissed;
    }
    @Override public void build(@Nonnull Ref<EntityStore> ref,@Nonnull UICommandBuilder commands,
                                @Nonnull UIEventBuilder events,@Nonnull Store<EntityStore> store){
        commands.append("CanvasGraphEscapePage.ui");
    }
    public void closeSilently(){armed=false;close();}
    @Override public void onDismiss(@Nonnull Ref<EntityStore> ref,@Nonnull Store<EntityStore> store){if(armed)dismissed.run();}
}
