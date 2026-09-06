package com.inigmasgames.hytalerpg.phase00;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

final class LinkCanvasProbePage extends InteractiveCustomUIPage<LinkCanvasProbePage.Data> {
    private int eventCount;

    LinkCanvasProbePage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime, Data.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append("Phase00LinkCanvas.ui");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PanLeft",
                EventData.of("@Action", "pan-left"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PanRight",
                EventData.of("@Action", "pan-right"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#MoveNode",
                EventData.of("@Action", "move-node"), false);
        events.addEventBinding(CustomUIEventBindingType.KeyDown, "#Canvas",
                EventData.of("@Action", "key-down"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, Data data) {
        eventCount++;
        UICommandBuilder update = new UICommandBuilder();
        update.set("#EventStatus.TextSpans", Message.raw(
                "Server event received: " + data.action + " (#" + eventCount + ")"));
        sendUpdate(update, false);
    }

    static final class Data {
        static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("@Action", Codec.STRING),
                        (data, value) -> data.action = value, data -> data.action).add()
                .build();
        private String action = "unknown";
    }
}

