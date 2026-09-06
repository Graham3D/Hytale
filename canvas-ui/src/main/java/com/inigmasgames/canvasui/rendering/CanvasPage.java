package com.inigmasgames.canvasui.rendering;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.canvasui.runtime.CanvasSession;

import javax.annotation.Nonnull;

public final class CanvasPage extends InteractiveCustomUIPage<CanvasPage.Data> {
    private final CanvasSession session;
    private final HytaleCustomUiBackend backend;

    public CanvasPage(PlayerRef playerRef, CanvasSession session, HytaleCustomUiBackend backend) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
        this.session = session; this.backend = backend;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        backend.build(commands, events);
    }

    void flush(UICommandBuilder commands) { sendUpdate(commands, false); }
    void flush(UICommandBuilder commands, UIEventBuilder events) { sendUpdate(commands, events, false); }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String rawData) {
        backend.recordRawEvent(rawData);
        super.handleDataEvent(ref, store, rawData);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, Data data) {
        backend.handleEvent(data.event, data.targetKind, data.targetId, data.value);
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        session.close("PAGE_DISMISS");
    }

    static final class Data {
        static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("@Event", Codec.STRING), (d, v) -> d.event = v, d -> d.event).add()
                .append(new KeyedCodec<>("@TargetKind", Codec.STRING), (d, v) -> d.targetKind = v, d -> d.targetKind).add()
                .append(new KeyedCodec<>("@TargetId", Codec.STRING), (d, v) -> d.targetId = v, d -> d.targetId).add()
                .append(new KeyedCodec<>("@Value", Codec.STRING), (d, v) -> d.value = v, d -> d.value).add()
                .build();
        private String event = "";
        private String targetKind = "";
        private String targetId = "";
        private String value = "";
    }
}
