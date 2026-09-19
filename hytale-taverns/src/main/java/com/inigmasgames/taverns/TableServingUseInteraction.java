package com.inigmasgames.taverns;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.Interaction;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import javax.annotation.Nonnull;

/**
 * Server operation used by the replicated serving prop's Secondary interaction.
 * It emits an ordinary Simple packet, so no custom client interaction type is needed.
 */
public final class TableServingUseInteraction extends SimpleInstantInteraction {
    static final String TYPE_ID = "TavernServingUse";
    static final BuilderCodec<TableServingUseInteraction> CODEC = BuilderCodec
            .builder(
                    TableServingUseInteraction.class,
                    TableServingUseInteraction::new,
                    SimpleInstantInteraction.CODEC)
            .build();

    private static volatile TableServingManager manager;

    static void install(TableServingManager installedManager) {
        manager = installedManager;
    }

    static void uninstall(TableServingManager installedManager) {
        if (manager == installedManager) {
            manager = null;
        }
    }

    @Override
    protected void firstRun(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nonnull CooldownHandler cooldownHandler) {
        TableServingManager installed = manager;
        if (installed == null || !installed.useServing(type, context)) {
            context.getState().state = InteractionState.Failed;
        }
    }

    @Override
    @Nonnull
    protected Interaction generatePacket() {
        return new com.hypixel.hytale.protocol.SimpleInteraction();
    }
}
