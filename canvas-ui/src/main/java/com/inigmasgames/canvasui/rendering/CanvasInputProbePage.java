package com.inigmasgames.canvasui.rendering;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.canvasui.CanvasUI;

import javax.annotation.Nonnull;
import java.util.EnumMap;
import java.util.Map;

/** R008 diagnostic page for the safe event surface exposed by public CustomUI. */
public final class CanvasInputProbePage extends InteractiveCustomUIPage<CanvasInputProbePage.Data> {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final CustomUIEventBindingType[] DISCRETE = {
            CustomUIEventBindingType.Activating, CustomUIEventBindingType.RightClicking,
            CustomUIEventBindingType.DoubleClicking, CustomUIEventBindingType.MouseEntered,
            CustomUIEventBindingType.MouseExited
    };
    private final Map<CustomUIEventBindingType, Long> counts = new EnumMap<>(CustomUIEventBindingType.class);
    private final long openedNanos = System.nanoTime();

    public CanvasInputProbePage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, Data.CODEC);
    }

    @Override public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append("CanvasInputProbePage.ui");
        commands.set("#ProbeRevision.TextSpans", Message.raw(CanvasUI.REVISION));
        for (String[] target : new String[][]{{"#ProbeBackground", "background"}, {"#ProbeNode", "node"},
                {"#ProbePort", "port"}, {"#ProbeEdge", "edge"}}) {
            for (CustomUIEventBindingType type : DISCRETE) bind(events, type, target[0], target[1], "");
        }
        bind(events, CustomUIEventBindingType.ValueChanged, "#ProbeText", "text", "#ProbeText.Value");
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ProbeSlider",
                new EventData().append("Event", "ValueChanged").append("Target", "slider")
                        .append("@SliderValue", "#ProbeSlider.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ProbeFloatSlider",
                new EventData().append("Event", "ValueChanged").append("Target", "float-slider")
                        .append("@FloatValue", "#ProbeFloatSlider.Value"), false);
    }

    @Override public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
            @Nonnull String rawData) {
        LOGGER.atInfo().log("CANVASUI_INPUT_PROBE_RAW revision=%s payload=%s", CanvasUI.REVISION, rawData);
        super.handleDataEvent(ref, store, rawData);
    }

    @Override public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, Data data) {
        CustomUIEventBindingType type;
        try { type = CustomUIEventBindingType.valueOf(data.event); }
        catch (IllegalArgumentException error) { return; }
        String value = switch (data.target) {
            case "slider" -> Integer.toString(data.sliderValue);
            case "float-slider" -> Float.toString(data.floatValue);
            default -> data.value;
        };
        long count = counts.merge(type, 1L, Long::sum);
        double elapsed = Math.max(0.001, (System.nanoTime() - openedNanos) / 1_000_000_000.0);
        LOGGER.atInfo().log("CANVASUI_INPUT_PROBE revision=%s event=%s target=%s value=%s count=%d rateHz=%.2f",
                CanvasUI.REVISION, data.event, data.target, value, count, count / elapsed);
        UICommandBuilder update = new UICommandBuilder();
        update.set("#ProbeLastEvent.TextSpans", Message.raw(data.event + " / " + data.target
                + (value.isBlank() ? "" : " / " + value)));
        update.set("#ProbeEventCount.TextSpans", Message.raw(total() + " total server events"));
        sendUpdate(update, false);
    }

    @Override public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        LOGGER.atInfo().log("CANVASUI_INPUT_PROBE_CLOSE revision=%s counts=%s", CanvasUI.REVISION, counts);
    }

    private long total() { return counts.values().stream().mapToLong(Long::longValue).sum(); }
    private static void bind(UIEventBuilder events, CustomUIEventBindingType type, String selector,
                             String target, String dynamicValue) {
        EventData data = new EventData().append("Event", type.name()).append("Target", target);
        if (!dynamicValue.isBlank()) data.append("@Value", dynamicValue);
        events.addEventBinding(type, selector, data, false);
    }

    public static final class Data {
        static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("Event", Codec.STRING), (d, v) -> d.event = v, d -> d.event).add()
                .append(new KeyedCodec<>("Target", Codec.STRING), (d, v) -> d.target = v, d -> d.target).add()
                .append(new KeyedCodec<>("@Value", Codec.STRING), (d, v) -> d.value = v, d -> d.value).add()
                .append(new KeyedCodec<>("@SliderValue", Codec.INTEGER), (d, v) -> d.sliderValue = v, d -> d.sliderValue).add()
                .append(new KeyedCodec<>("@FloatValue", Codec.FLOAT), (d, v) -> d.floatValue = v, d -> d.floatValue).add()
                .build();
        private String event = "";
        private String target = "";
        private String value = "";
        private int sliderValue;
        private float floatValue;
    }
}
