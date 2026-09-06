package com.inigmasgames.hytalerpg.phase00;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

final class Phase00Hud extends CustomUIHud {
    static final String KEY = "inigmas:hytalerpg:phase00";

    private final StatSnapshot stats;

    Phase00Hud(@Nonnull PlayerRef playerRef, @Nonnull StatSnapshot stats) {
        super(playerRef, KEY, 100);
        this.stats = stats;
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commands) {
        commands.append("Phase00Hud.ui");
        commands.set("#Mana.TextSpans", Message.raw("MANA  " + stats.mana()));
        commands.set("#Health.TextSpans", Message.raw("HEALTH  " + stats.health()));
        commands.set("#Stamina.TextSpans", Message.raw("STAMINA  " + stats.stamina()));
    }
}
