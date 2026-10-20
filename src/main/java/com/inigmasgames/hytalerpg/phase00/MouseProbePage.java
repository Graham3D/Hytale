package com.inigmasgames.hytalerpg.phase00;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.MouseButtonEvent;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.protocol.MouseMotionEvent;
import com.hypixel.hytale.protocol.Vector2i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

final class MouseProbePage extends CustomUIPage {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final long UPDATE_INTERVAL_NANOS = 100_000_000L;
    private final PlayerRef playerRef;
    private int nodeX = 590;
    private int nodeY = 195;
    private int panX;
    private int panY;
    private boolean dragging;
    private boolean panning;
    private long buttonEvents;
    private long motionEvents;
    private long openedNanos;
    private long lastUpdateNanos;
    private String latest = "Waiting for PRESS / MOVE / RELEASE";

    MouseProbePage(@Nonnull PlayerRef playerRef, @Nonnull CustomPageLifetime lifetime) {
        super(playerRef, lifetime);
        this.playerRef = playerRef;
        loadLayout();
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        openedNanos = System.nanoTime();
        commands.append("Phase00MouseProbe.ui");
        render(commands);
        MouseProbeService.activate(playerRef, this);
        LOGGER.atInfo().log("PHASE00_MOUSE_OPEN revision=%s player=%s restoredNode=%d,%d restoredPan=%d,%d",
                BuildIdentity.REVISION, playerRef.getUuid(), nodeX, nodeY, panX, panY);
    }

    void onMouseButton(Ref<EntityStore> ref, Store<EntityStore> store, PlayerMouseButtonEvent event) {
        buttonEvents++;
        MouseButtonEvent button = event.getMouseButton();
        if (button == null) {
            return;
        }
        boolean pressed = button.state == MouseButtonState.Pressed;
        if (button.mouseButtonType == MouseButtonType.Left) {
            dragging = pressed;
        } else if (button.mouseButtonType == MouseButtonType.Middle) {
            panning = pressed;
        }
        latest = button.state + " " + button.mouseButtonType + " clicks=" + button.clicks
                + " screen=" + event.getScreenPoint() + " clientTime=" + event.getClientUseTime();
        LOGGER.atInfo().log("PHASE00_MOUSE_BUTTON revision=%s player=%s state=%s button=%s clicks=%d screen=%s clientTime=%d",
                BuildIdentity.REVISION, playerRef.getUuid(), button.state, button.mouseButtonType,
                button.clicks, event.getScreenPoint(), event.getClientUseTime());
        if (!pressed) {
            saveLayout();
        }
        refresh(true);
    }

    void onMouseMotion(Ref<EntityStore> ref, Store<EntityStore> store, PlayerMouseMotionEvent event) {
        motionEvents++;
        MouseMotionEvent motion = event.getMouseMotion();
        Vector2i delta = motion == null ? null : motion.relativeMotion;
        if (delta != null) {
            if (dragging) {
                nodeX = clamp(nodeX + delta.x, 30, 850);
                nodeY = clamp(nodeY + delta.y, 30, 350);
            }
            if (panning) {
                panX = clamp(panX + delta.x, -240, 240);
                panY = clamp(panY + delta.y, -140, 140);
            }
        }
        latest = "MOVE delta=" + (delta == null ? "null" : delta.x + "," + delta.y)
                + " held=" + Arrays.toString(motion == null ? null : motion.mouseButtonType)
                + " clientTime=" + event.getClientUseTime();
        refresh(false);
    }

    private void refresh(boolean force) {
        long now = System.nanoTime();
        if (!force && now - lastUpdateNanos < UPDATE_INTERVAL_NANOS) {
            return;
        }
        lastUpdateNanos = now;
        UICommandBuilder commands = new UICommandBuilder();
        render(commands);
        sendUpdate(commands, false);
    }

    private void render(UICommandBuilder commands) {
        commands.clear("#CanvasContents");
        for (String element : connectionMarkup()) {
            commands.appendInline("#CanvasContents", element);
        }
        commands.set("#MouseStatus.TextSpans", Message.raw(latest));
        double seconds = Math.max(0.001, (System.nanoTime() - openedNanos) / 1_000_000_000.0);
        commands.set("#Counters.TextSpans", Message.raw("buttons=" + buttonEvents + " moves=" + motionEvents
                + " rawPackets=" + MouseProbeService.rawCount(playerRef.getUuid())
                + " moveHz=" + String.format(java.util.Locale.ROOT, "%.1f", motionEvents / seconds)
                + " node=" + nodeX + ',' + nodeY + " pan=" + panX + ',' + panY));
    }

    private java.util.List<String> connectionMarkup() {
        int ax = 155 + panX;
        int ay = 195 + panY;
        int nx = nodeX + panX;
        int ny = nodeY + panY;
        int elbow = (ax + nx) / 2;
        int h1Left = Math.min(ax, elbow);
        int h1Width = Math.max(2, Math.abs(elbow - ax));
        int vTop = Math.min(ay, ny);
        int vHeight = Math.max(2, Math.abs(ny - ay));
        int h2Left = Math.min(elbow, nx);
        int h2Width = Math.max(2, Math.abs(nx - elbow));
        return java.util.List.of(
                "Group { Anchor: (Left: " + (ax - 56) + ", Top: " + (ay - 22)
                        + ", Width: 112, Height: 44); Background: (Color: #37516eee);"
                        + " Label { Text: \"ROOT\"; Style: (FontSize: 14, TextColor: #dce6ed, Alignment: Center); } }",
                "Group { Anchor: (Left: " + h1Left + ", Top: " + ay + ", Width: " + h1Width
                        + ", Height: 3); Background: (Color: #78c6d0); }",
                "Group { Anchor: (Left: " + elbow + ", Top: " + vTop + ", Width: 3, Height: " + vHeight
                        + "); Background: (Color: #78c6d0); }",
                "Group { Anchor: (Left: " + h2Left + ", Top: " + ny + ", Width: " + h2Width
                        + ", Height: 3); Background: (Color: #78c6d0); }",
                "Group { Anchor: (Left: " + (nx - 70) + ", Top: " + (ny - 28)
                        + ", Width: 140, Height: 56); Background: (Color: #6b4f28ee);"
                        + " Label { Text: \"DRAG NODE\"; Style: (FontSize: 14, TextColor: #f2d488, Alignment: Center); } }"
        );
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        saveLayout();
        MouseProbeService.deactivate(playerRef.getUuid(), this);
        LOGGER.atInfo().log("PHASE00_MOUSE_DISMISS revision=%s player=%s buttons=%d moves=%d raw=%d node=%d,%d pan=%d,%d",
                BuildIdentity.REVISION, playerRef.getUuid(), buttonEvents, motionEvents,
                MouseProbeService.rawCount(playerRef.getUuid()), nodeX, nodeY, panX, panY);
    }

    private Path layoutPath() {
        return MouseProbeService.dataDirectory().resolve("mouse-layout-" + playerRef.getUuid() + ".properties");
    }

    private void loadLayout() {
        Path path = layoutPath();
        if (!Files.isRegularFile(path)) {
            return;
        }
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            values.load(input);
            nodeX = Integer.parseInt(values.getProperty("nodeX", Integer.toString(nodeX)));
            nodeY = Integer.parseInt(values.getProperty("nodeY", Integer.toString(nodeY)));
            panX = Integer.parseInt(values.getProperty("panX", "0"));
            panY = Integer.parseInt(values.getProperty("panY", "0"));
        } catch (IOException | NumberFormatException error) {
            LOGGER.atWarning().withCause(error).log("PHASE00_MOUSE_LAYOUT_LOAD_FAILED player=%s", playerRef.getUuid());
        }
    }

    private void saveLayout() {
        Properties values = new Properties();
        values.setProperty("revision", BuildIdentity.REVISION);
        values.setProperty("nodeX", Integer.toString(nodeX));
        values.setProperty("nodeY", Integer.toString(nodeY));
        values.setProperty("panX", Integer.toString(panX));
        values.setProperty("panY", Integer.toString(panY));
        try {
            Files.createDirectories(MouseProbeService.dataDirectory());
            try (OutputStream output = Files.newOutputStream(layoutPath())) {
                values.store(output, "Stage 00 mouse probe layout; audit-only");
            }
        } catch (IOException error) {
            LOGGER.atWarning().withCause(error).log("PHASE00_MOUSE_LAYOUT_SAVE_FAILED player=%s", playerRef.getUuid());
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
