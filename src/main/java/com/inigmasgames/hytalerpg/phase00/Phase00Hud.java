package com.inigmasgames.hytalerpg.phase00;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

final class Phase00Hud extends CustomUIHud {
    static final String KEY = "inigmas:hytalerpg:phase00";

    Phase00Hud(@Nonnull PlayerRef playerRef) {
        super(playerRef, KEY, 100);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commands) {
        commands.append("Phase00Hud.ui");
    }
}

