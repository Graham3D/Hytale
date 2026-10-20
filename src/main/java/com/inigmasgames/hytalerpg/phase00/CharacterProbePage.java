package com.inigmasgames.hytalerpg.phase00;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

final class CharacterProbePage extends CustomUIPage {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final PlayerRef playerRef;
    private final StatSnapshot stats;

    CharacterProbePage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime,
                       @Nonnull StatSnapshot stats) {
        super(playerRef, lifetime);
        this.playerRef = playerRef;
        this.stats = stats;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append("Phase00Character.ui");
        commands.set("#PlayerName.TextSpans", Message.raw("Name: " + playerRef.getUsername()));
        commands.set("#HealthValue.TextSpans", Message.raw("Health: " + stats.health()));
        commands.set("#ManaValue.TextSpans", Message.raw("Mana: " + stats.mana()));
        commands.set("#StaminaValue.TextSpans", Message.raw("Stamina: " + stats.stamina()));
        commands.set("#Revision.TextSpans", Message.raw(BuildIdentity.REVISION + " | Hytale "
                + BuildIdentity.HYTALE_VERSION));
        LOGGER.atInfo().log("PHASE00_CHARACTER_OPEN revision=%s player=%s lifetime=%s",
                BuildIdentity.REVISION, playerRef.getUuid(), getLifetime());
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        LOGGER.atInfo().log("PHASE00_CHARACTER_DISMISS revision=%s player=%s",
                BuildIdentity.REVISION, playerRef.getUuid());
    }
}
