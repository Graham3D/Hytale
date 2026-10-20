package com.inigmasgames.hytalerpg.phase00;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

final class RevisionHud extends CustomUIHud {
    static final String KEY = "inigmas:hytalerpg:revision";

    RevisionHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, KEY, 1000);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commands) {
        commands.append("Phase00RevisionHud.ui");
        commands.set("#BuildRevision.TextSpans", Message.raw(BuildIdentity.REVISION));
    }
}
