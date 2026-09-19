package com.inigmasgames.canvasui.runtime.cursor;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.MouseButtonEvent;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.protocol.MouseInputTargetType;
import com.hypixel.hytale.protocol.MouseInputType;
import com.hypixel.hytale.protocol.MovementForceRotationType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.PositionDistanceOffsetType;
import com.hypixel.hytale.protocol.RotationType;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.protocol.Vector2i;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.inventory.DropItemStack;
import com.hypixel.hytale.protocol.packets.inventory.SetActiveSlot;
import com.hypixel.hytale.protocol.packets.inventory.SwitchHotbarBlockSet;
import com.hypixel.hytale.protocol.packets.player.MouseInteraction;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.CameraManager;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketWatcher;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.canvasui.CanvasUI;
import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.canvasui.api.CanvasDefinition;
import com.inigmasgames.canvasui.api.CanvasEvent;
import com.inigmasgames.canvasui.api.CanvasEventType;
import com.inigmasgames.canvasui.api.CanvasInputCapabilities;
import com.inigmasgames.canvasui.api.CanvasNode;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.CanvasPort;
import com.inigmasgames.canvasui.api.EdgeStyle;
import com.inigmasgames.canvasui.api.NodeDefinition;
import com.inigmasgames.canvasui.api.NodeVisual;
import com.inigmasgames.canvasui.api.PanGesture;
import com.inigmasgames.canvasui.rendering.CanvasCursorProbeHud;
import com.inigmasgames.canvasui.rendering.CanvasCursorProbePage;
import com.inigmasgames.canvasui.rendering.CursorHudCanvasBackend;
import com.inigmasgames.canvasui.rendering.HytaleCursorHudInputBackend;
import com.inigmasgames.canvasui.rendering.CanvasGraphEditorHud;
import com.inigmasgames.canvasui.rendering.CanvasGraphSearchPage;
import com.inigmasgames.canvasui.rendering.CursorHudGraphEditorBackend;
import com.inigmasgames.canvasui.runtime.CanvasInputController;
import com.inigmasgames.canvasui.runtime.CanvasHitTester;
import com.inigmasgames.canvasui.api.editor.CursorCanvasEditor;
import com.inigmasgames.canvasui.api.editor.CursorEditorOpenResult;
import com.inigmasgames.canvasui.api.editor.LibraryBrowser;
import com.inigmasgames.canvasui.api.editor.TreeDragController;
import com.inigmasgames.canvasui.api.editor.TreeDropResolver;
import com.inigmasgames.canvasui.api.editor.TreeLinkGeometry;
import com.inigmasgames.canvasui.api.editor.TreeLinkInteraction;
import org.joml.Vector2fc;
import org.joml.Vector3f;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** R042 cursor-HUD transform, gameplay-input guard, page control and graph drag proof. */
public final class CursorHudProbeService implements AutoCloseable {
    public enum Context {
        NO_HUD("CURSOR_CAMERA_NO_HUD"),
        PASSIVE_HUD("CURSOR_CAMERA_PASSIVE_HUD"),
        CUSTOM_PAGE_CONTROL("CURSOR_CAMERA_CUSTOM_PAGE_CONTROL"),
        DRAG_PROOF("CURSOR_CAMERA_DRAG_PROOF"),
        GRAPH_EDITOR("CURSOR_CAMERA_GRAPH_EDITOR");

        private final String label;
        Context(String label) { this.label = label; }
        public String label() { return label; }
    }

    public record OpenResult(boolean opened, String message, Path tracePath) { }

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int QUEUE_CAPACITY = 1024;
    private static final int GUARD_QUEUE_CAPACITY = 1024;
    private static final int TRACE_SAMPLE_LIMIT = 20_000;
    private static final long UI_INTERVAL_NANOS = 40_000_000L;
    private static final DateTimeFormatter TRACE_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final Set<Class<? extends Packet>> GUARDED_PACKETS = Set.of(
            SyncInteractionChains.class, SetActiveSlot.class,
            SwitchHotbarBlockSet.class, DropItemStack.class);

    private final Path traceDirectory;
    private final Path dataDirectory;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, CanvasPointerTransform.Calibration> calibrations = new ConcurrentHashMap<>();
    private final Map<UUID, SetServerCamera> lastExternalCamera = new ConcurrentHashMap<>();
    private final Set<UUID> ownedCameraWrites = ConcurrentHashMap.newKeySet();
    private final AtomicLong sequence = new AtomicLong();
    private final CanvasInputGuard inputGuard = new CanvasInputGuard();
    private final PacketFilter inboundFilter;
    private final PacketFilter outboundWatcher;
    private volatile boolean closed;

    public CursorHudProbeService(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        traceDirectory = dataDirectory.resolve("logs").resolve("cursor-hud");
        loadPersistedCalibrations();
        inboundFilter = PacketAdapters.registerInbound((PlayerPacketFilter)this::filterInbound);
        outboundWatcher = PacketAdapters.registerOutbound((PlayerPacketWatcher)this::observeOutbound);
    }

    public CanvasInputGuard inputGuard() { return inputGuard; }

    /** Closes the owning player's cursor session at the native death-component boundary. */
    public static final class DeathCleanupSystem extends DeathSystems.OnDeathSystem {
        private final CursorHudProbeService service;
        public DeathCleanupSystem(CursorHudProbeService service) {
            this.service = java.util.Objects.requireNonNull(service);
        }
        @Override public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }
        @Override public void onComponentAdded(Ref<EntityStore> ref, DeathComponent death,
                                               Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
            PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
            if (player != null) service.close(player.getUuid(), "PLAYER_DEATH");
        }
    }

    public OpenResult open(Context context, Player player, PlayerRef playerRef, World world,
                           Store<EntityStore> store, Ref<EntityStore> ref) {
        if (closed) return new OpenResult(false, "Cursor-HUD service is closed.", null);
        UUID playerId = playerRef.getUuid();
        close(playerId, "REPLACED_BY_NEW_PROBE");

        SetServerCamera priorCamera = cameraSnapshot(playerId);
        if (priorCamera != null && priorCamera.isLocked && priorCamera.cameraSettings != null) {
            return new OpenResult(false, "Another observed server-camera owner is active; close it before CanvasUI.", null);
        }
        CanvasPointerTransform.Calibration saved = calibrations.get(playerId);
        if (context == Context.DRAG_PROOF && saved == null) {
            return new OpenResult(false,
                    "Run /canvasui-cursor-probe and complete TL, TR, BL, BR, CENTER calibration first.", null);
        }
        CameraManager camera = store.getComponent(ref, CameraManager.getComponentType());
        if (camera == null) return new OpenResult(false, "CameraManager is unavailable.", null);

        Session session;
        try {
            session = new Session(context, null, player, playerRef, world, camera, priorCamera, newTracePath(), saved);
        } catch (IOException error) {
            LOGGER.atWarning().withCause(error).log("CANVASUI_CURSOR_TRACE_OPEN_FAILED revision=%s", CanvasUI.REVISION);
            return new OpenResult(false, "Unable to create the bounded cursor-HUD trace.", null);
        }
        Session collision = sessions.putIfAbsent(playerId, session);
        if (collision != null) {
            session.finish("SESSION_COLLISION");
            return new OpenResult(false, "A cursor-HUD session is already active.", session.tracePath);
        }

        try {
            if (context == Context.CUSTOM_PAGE_CONTROL)
                session.traceLifecycle("PAGE_NEGATIVE_CONTROL_BEFORE", "pageOpen=false");
            session.installDisplay();
            if (context == Context.CUSTOM_PAGE_CONTROL)
                session.traceLifecycle("PAGE_NEGATIVE_CONTROL_DURING", "pageOpen=true expectedPointerContinuity=false");
            inputGuard.acquire(playerId, session.token, session::acceptGuardObservation);
            session.guardInstalled = true;
            session.traceLifecycle("INPUT_GUARD_ACQUIRED", "boundary=INTERACTION_CHAIN_START_AND_CANCELLABLE_ECS_REQUEST");
            writeOwnedCamera(playerId, () -> playerRef.getPacketHandler().writeNoCache(
                    new SetServerCamera(ClientCameraView.Custom, true, cameraSettings())));
            session.cameraInstalled = true;
            session.traceLifecycle("OPEN", "cameraRestore=" + (priorCamera == null ? "RESET_DEFAULT" : "EXACT_PACKET_COPY")
                    + " mapping=" + session.transform.diagnostic());
            LOGGER.atInfo().log("CANVASUI_CURSOR_OPEN revision=%s session=%s context=%s guard=ACTIVE transform=%s trace=%s",
                    CanvasUI.REVISION, session.token, context.label(), session.transform.state(),
                    session.tracePath.getFileName());
            String instruction = context == Context.PASSIVE_HUD
                    ? " Click TL, TR, BL, BR, CENTER targets in order."
                    : context == Context.DRAG_PROOF ? " Drag either graph node and release." : "";
            return new OpenResult(true, "CanvasUI " + CanvasUI.REVISION + " active: " + context.label() + "." + instruction,
                    session.tracePath);
        } catch (RuntimeException error) {
            sessions.remove(playerId, session);
            session.finish("OPEN_FAILED_" + error.getClass().getSimpleName());
            LOGGER.atWarning().withCause(error).log("CANVASUI_CURSOR_OPEN_FAILED revision=%s context=%s",
                    CanvasUI.REVISION, context.label());
            return new OpenResult(false, "Cursor-HUD failed to acquire display/input ownership: "
                    + error.getClass().getSimpleName(), session.tracePath);
        }
    }

    public CursorEditorOpenResult openEditor(CursorCanvasEditor editor, Player player, PlayerRef playerRef,
                                               World world, Store<EntityStore> store, Ref<EntityStore> ref) {
        java.util.Objects.requireNonNull(editor, "editor");
        if (closed) return new CursorEditorOpenResult(false, "CanvasUI cursor editor is closed.", null);
        UUID playerId = playerRef.getUuid();
        close(playerId, "REPLACED_BY_GRAPH_EDITOR");
        SetServerCamera priorCamera = cameraSnapshot(playerId);
        if (priorCamera != null && priorCamera.isLocked && priorCamera.cameraSettings != null)
            return new CursorEditorOpenResult(false, "Another observed server-camera owner is active.", null);
        CanvasPointerTransform.Calibration saved = calibrations.get(playerId);
        if (saved == null) {
            saved = recoverLatestCalibration();
            if (saved != null) {
                calibrations.put(playerId, saved);
                persistCalibration(playerId, saved);
            }
        }
        if (saved == null) return new CursorEditorOpenResult(false,
                "Canvas cursor mapping is unavailable. Run /canvasui-cursor-probe once to calibrate this client.", null);
        CameraManager camera = store.getComponent(ref, CameraManager.getComponentType());
        if (camera == null) return new CursorEditorOpenResult(false, "CameraManager is unavailable.", null);
        Session session;
        try {
            session = new Session(Context.GRAPH_EDITOR, editor, player, playerRef, world, camera, priorCamera,
                    newTracePath(), saved);
        } catch (IOException | RuntimeException error) {
            LOGGER.atWarning().withCause(error).log("CANVASUI_GRAPH_EDITOR_CREATE_FAILED revision=%s editor=%s",
                    CanvasUI.REVISION, editor.editorId());
            return new CursorEditorOpenResult(false, "Unable to create graph editor: "
                    + error.getClass().getSimpleName(), null);
        }
        Session collision = sessions.putIfAbsent(playerId, session);
        if (collision != null) {
            session.finish("SESSION_COLLISION");
            return new CursorEditorOpenResult(false, "A cursor-HUD session is already active.", session.tracePath);
        }
        try {
            session.installDisplay();
            inputGuard.acquire(playerId, session.token, session::acceptGuardObservation);
            session.guardInstalled = true;
            session.traceLifecycle("INPUT_GUARD_ACQUIRED", "boundary=GRAPH_EDITOR");
            writeOwnedCamera(playerId, () -> playerRef.getPacketHandler().writeNoCache(
                    new SetServerCamera(ClientCameraView.Custom, true, cameraSettings())));
            session.cameraInstalled = true;
            session.traceLifecycle("OPEN", "editor=" + editor.editorId() + " mapping=" + session.transform.diagnostic());
            return new CursorEditorOpenResult(true, "CanvasUI " + CanvasUI.REVISION + " editor opened.", session.tracePath);
        } catch (RuntimeException error) {
            sessions.remove(playerId, session);
            session.finish("OPEN_FAILED_" + error.getClass().getSimpleName());
            return new CursorEditorOpenResult(false, "Graph editor failed to acquire input/display ownership: "
                    + error.getClass().getSimpleName(), session.tracePath);
        }
    }

    public void route(PlayerMouseButtonEvent event) {
        PlayerRef playerRef = event.getPlayerRefComponent();
        if (playerRef == null) return;
        Session session = sessions.get(playerRef.getUuid());
        if (session == null) return;
        event.setCancelled(true);
        MouseButtonEvent button = event.getMouseButton();
        session.accept(sample(CursorProbeSample.Source.EVENT, CursorProbeSample.Kind.BUTTON,
                event.getScreenPoint(), null, button == null ? null : button.mouseButtonType,
                button == null ? null : button.state, button == null ? 0 : button.clicks, null,
                event.getTargetBlock() != null, event.getTargetEntityRef() != null, event.getItemInHand() != null));
    }

    public void route(PlayerMouseMotionEvent event) {
        Ref<EntityStore> entityRef = event.getPlayerRef();
        if (!entityRef.isValid()) return;
        PlayerRef playerRef = entityRef.getStore().getComponent(entityRef, PlayerRef.getComponentType());
        if (playerRef == null) return;
        Session session = sessions.get(playerRef.getUuid());
        if (session == null) return;
        event.setCancelled(true);
        com.hypixel.hytale.protocol.MouseMotionEvent motion = event.getMouseMotion();
        session.accept(sample(CursorProbeSample.Source.EVENT, CursorProbeSample.Kind.MOTION,
                event.getScreenPoint(), motion == null ? null : motion.relativeMotion,
                null, null, 0, motion == null ? null : motion.mouseButtonType,
                event.getTargetBlock() != null, event.getTargetEntityRef() != null, event.getItemInHand() != null));
    }

    public void route(PlayerInteractEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef();
        if (ref == null || !ref.isValid()) return;
        PlayerRef playerRef = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null && inputGuard.guardPlayerInteract(playerRef.getUuid(), event)) event.setCancelled(true);
    }

    public void close(UUID playerId, String reason) {
        Session session = sessions.remove(playerId);
        if (session != null) session.finish(reason);
    }

    public boolean active(UUID playerId) { return sessions.containsKey(playerId); }

    private boolean filterInbound(PlayerRef playerRef, Packet packet) {
        Session session = sessions.get(playerRef.getUuid());
        if (session == null) return false;
        if (packet instanceof MouseInteraction mouse) {
            session.accept(sample(mouse));
            return false;
        }
        if (guardedPacket(packet)) {
            session.acceptGuardObservation(new CanvasInputGuard.Observation(
                    "INBOUND_PACKET_FILTER", packet.getClass().getSimpleName(), "", true));
            return true;
        }
        return false;
    }

    private static boolean guardedPacket(Packet packet) {
        for (Class<? extends Packet> type : GUARDED_PACKETS) if (type.isInstance(packet)) return true;
        return false;
    }

    private void observeOutbound(PlayerRef playerRef, Packet packet) {
        if (!(packet instanceof SetServerCamera camera)) return;
        UUID playerId = playerRef.getUuid();
        if (ownedCameraWrites.contains(playerId)) return;
        lastExternalCamera.put(playerId, new SetServerCamera(camera));
        Session session = sessions.get(playerId);
        if (session == null) return;
        session.cameraSuperseded = true;
        session.world.execute(() -> close(playerId, "CAMERA_OWNERSHIP_SUPERSEDED"));
    }

    private SetServerCamera cameraSnapshot(UUID playerId) {
        SetServerCamera prior = lastExternalCamera.get(playerId);
        return prior == null ? null : new SetServerCamera(prior);
    }

    private CursorProbeSample sample(MouseInteraction mouse) {
        com.hypixel.hytale.protocol.MouseMotionEvent motion = mouse.mouseMotion;
        MouseButtonEvent button = mouse.mouseButton;
        return sample(CursorProbeSample.Source.PACKET,
                button != null ? CursorProbeSample.Kind.BUTTON : CursorProbeSample.Kind.MOTION,
                mouse.screenPoint, motion == null ? null : motion.relativeMotion,
                button == null ? null : button.mouseButtonType, button == null ? null : button.state,
                button == null ? 0 : button.clicks, motion == null ? null : motion.mouseButtonType,
                mouse.worldInteraction != null && mouse.worldInteraction.blockPosition != null,
                mouse.worldInteraction != null && mouse.worldInteraction.entityId >= 0,
                mouse.itemInHandId != null && !mouse.itemInHandId.isBlank());
    }

    private CursorProbeSample sample(CursorProbeSample.Source source, CursorProbeSample.Kind kind,
                                     Vector2fc point, Vector2i delta, MouseButtonType button,
                                     MouseButtonState state, int clicks, MouseButtonType[] held,
                                     boolean targetBlock, boolean targetEntity, boolean itemInHand) {
        double x = point == null ? Double.NaN : point.x();
        double y = point == null ? Double.NaN : point.y();
        boolean valid = finite(x, y) && Math.abs(x) <= 1_000_000.0 && Math.abs(y) <= 1_000_000.0;
        String heldText = held == null ? "UNKNOWN" : Arrays.stream(held)
                .filter(java.util.Objects::nonNull).map(Enum::name).sorted()
                .reduce((a, b) -> a + "+" + b).orElse("NONE");
        return new CursorProbeSample(sequence.incrementAndGet(), source, kind, valid, x, y,
                delta == null ? null : delta.x, delta == null ? null : delta.y,
                button == null ? "UNKNOWN" : button.name(), state == null ? "UNKNOWN" : state.name(),
                clicks, heldText, targetBlock, targetEntity, itemInHand);
    }

    private Path newTracePath() throws IOException {
        Files.createDirectories(traceDirectory);
        String token = UUID.randomUUID().toString().substring(0, 8);
        return traceDirectory.resolve("cursor-r042-" + TRACE_TIME.format(Instant.now()) + "-" + token + ".jsonl");
    }

    private void loadPersistedCalibrations() {
        Path directory = dataDirectory.resolve("cursor-calibration");
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            files.filter(path -> path.getFileName().toString().endsWith(".properties")).forEach(path -> {
                try {
                    java.util.Properties values = new java.util.Properties();
                    try (var input = Files.newInputStream(path)) { values.load(input); }
                    UUID player = UUID.fromString(path.getFileName().toString().replace(".properties", ""));
                    calibrations.put(player, calibration(values));
                } catch (RuntimeException | IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private void persistCalibration(UUID player, CanvasPointerTransform.Calibration value) {
        Path directory = dataDirectory.resolve("cursor-calibration");
        Path target = directory.resolve(player + ".properties");
        Path temporary = directory.resolve(player + ".tmp");
        try {
            Files.createDirectories(directory);
            java.util.Properties values = new java.util.Properties();
            values.setProperty("rawLeft", Double.toString(value.rawLeft()));
            values.setProperty("rawRight", Double.toString(value.rawRight()));
            values.setProperty("rawTop", Double.toString(value.rawTop()));
            values.setProperty("rawBottom", Double.toString(value.rawBottom()));
            values.setProperty("maximumCornerError", Double.toString(value.maximumCornerError()));
            values.setProperty("centerError", Double.toString(value.centerError()));
            try (var output = Files.newOutputStream(temporary, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) { values.store(output, "CanvasUI calibration"); }
            try { Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            LOGGER.atWarning().withCause(error).log("CANVASUI_CALIBRATION_SAVE_FAILED player=%s", player);
        }
    }

    private CanvasPointerTransform.Calibration recoverLatestCalibration() {
        if (!Files.isDirectory(traceDirectory)) return null;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "rawX=([-+0-9.Ee]+)\\.\\.([-+0-9.Ee]+) rawY=([-+0-9.Ee]+)\\.\\.([-+0-9.Ee]+) cornerErr=([-+0-9.Ee]+) centerErr=([-+0-9.Ee]+)");
        try (var stream = Files.list(traceDirectory)) {
            List<Path> paths = stream.filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .sorted(java.util.Comparator.comparingLong(this::lastModified).reversed()).limit(12).toList();
            for (Path path : paths) {
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                for (int i = lines.size() - 1; i >= 0; i--) {
                    if (!lines.get(i).contains("COORDINATE_MAPPING_ACCEPTED")) continue;
                    var match = pattern.matcher(lines.get(i));
                    if (match.find()) return new CanvasPointerTransform.Calibration(
                            Double.parseDouble(match.group(1)), Double.parseDouble(match.group(2)),
                            Double.parseDouble(match.group(3)), Double.parseDouble(match.group(4)),
                            Double.parseDouble(match.group(5)), Double.parseDouble(match.group(6)));
                }
            }
        } catch (IOException | RuntimeException ignored) { }
        return null;
    }

    private long lastModified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException ignored) { return 0L; }
    }

    private static CanvasPointerTransform.Calibration calibration(java.util.Properties values) {
        return new CanvasPointerTransform.Calibration(Double.parseDouble(values.getProperty("rawLeft")),
                Double.parseDouble(values.getProperty("rawRight")), Double.parseDouble(values.getProperty("rawTop")),
                Double.parseDouble(values.getProperty("rawBottom")),
                Double.parseDouble(values.getProperty("maximumCornerError")),
                Double.parseDouble(values.getProperty("centerError")));
    }

    private void applyStoredLayout(Canvas canvas, String editorId, UUID player) {
        Path path = layoutPath(editorId, player);
        if (!Files.isRegularFile(path)) return;
        try {
            java.util.Properties values = new java.util.Properties();
            try (var input = Files.newInputStream(path)) { values.load(input); }
            for (CanvasNode node : List.copyOf(canvas.nodes())) {
                String x = values.getProperty(node.nodeId() + ".x");
                String y = values.getProperty(node.nodeId() + ".y");
                if (x != null && y != null) canvas.moveNode(node.nodeId(),
                        CanvasPoint.of(Double.parseDouble(x), Double.parseDouble(y)));
            }
        } catch (IOException | RuntimeException error) {
            LOGGER.atWarning().withCause(error).log("CANVASUI_LAYOUT_LOAD_FAILED editor=%s player=%s", editorId, player);
        }
    }

    private Path layoutPath(String editorId, UUID player) {
        String safe = editorId.replaceAll("[^A-Za-z0-9._-]", "_");
        return dataDirectory.resolve("editor-layouts").resolve(safe + "-" + player + ".properties");
    }

    private static ServerCameraSettings cameraSettings() {
        ServerCameraSettings settings = new ServerCameraSettings();
        settings.positionLerpSpeed = 0.2f;
        settings.rotationLerpSpeed = 0.2f;
        settings.distance = 20.0f;
        settings.displayCursor = true;
        settings.displayReticle = false;
        settings.sendMouseMotion = true;
        settings.mouseInputTargetType = MouseInputTargetType.None;
        settings.isFirstPerson = false;
        settings.movementForceRotationType = MovementForceRotationType.Custom;
        settings.eyeOffset = true;
        settings.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffset;
        settings.rotationType = RotationType.Custom;
        settings.rotation = new Direction(0.0f, -1.5707964f, 0.0f);
        settings.mouseInputType = MouseInputType.LookAtPlane;
        settings.planeNormal = new Vector3f(0.0f, 1.0f, 0.0f);
        return settings;
    }

    private void writeOwnedCamera(UUID playerId, Runnable action) {
        ownedCameraWrites.add(playerId);
        try { action.run(); }
        finally { ownedCameraWrites.remove(playerId); }
    }

    private void restoreCamera(Session session) {
        if (!session.cameraInstalled || session.cameraSuperseded) return;
        if (session.priorCamera == null) {
            writeOwnedCamera(session.playerId, () -> session.camera.resetCamera(session.playerRef));
            lastExternalCamera.remove(session.playerId);
        } else {
            SetServerCamera restore = new SetServerCamera(session.priorCamera);
            writeOwnedCamera(session.playerId, () -> session.playerRef.getPacketHandler().writeNoCache(restore));
            lastExternalCamera.put(session.playerId, new SetServerCamera(session.priorCamera));
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        for (UUID playerId : List.copyOf(sessions.keySet())) close(playerId, "PLUGIN_SHUTDOWN");
        inputGuard.clear();
        PacketAdapters.deregisterInbound(inboundFilter);
        PacketAdapters.deregisterOutbound(outboundWatcher);
        calibrations.clear();
        lastExternalCamera.clear();
    }

    private final class Session {
        private final Context context;
        private final Player player;
        private final PlayerRef playerRef;
        private final World world;
        private final CameraManager camera;
        private final SetServerCamera priorCamera;
        private final UUID playerId;
        private final String token = UUID.randomUUID().toString().substring(0, 8);
        private final Path tracePath;
        private final BufferedWriter trace;
        private final CursorProbeInputBuffer input = new CursorProbeInputBuffer(QUEUE_CAPACITY);
        private final ConcurrentLinkedQueue<CanvasInputGuard.Observation> guardQueue = new ConcurrentLinkedQueue<>();
        private final AtomicInteger guardQueueSize = new AtomicInteger();
        private final AtomicBoolean drainScheduled = new AtomicBoolean();
        private final AtomicBoolean closing = new AtomicBoolean();
        private final AtomicLong gameplayObserved = new AtomicLong();
        private final AtomicLong gameplayGuarded = new AtomicLong();
        private final AtomicLong gameplayAllowed = new AtomicLong();
        private final AtomicLong traceDrops = new AtomicLong();
        private final CanvasPointerTransform transform;
        private final Canvas graph;
        private final CursorCanvasEditor editor;
        private CanvasInputController graphInput;
        private CanvasCursorProbeHud hud;
        private CanvasGraphEditorHud editorHud;
        private CanvasCursorProbePage page;
        private CursorCanvasEditor.LibraryKind libraryTab = CursorCanvasEditor.LibraryKind.SKILL;
        private int libraryPage;
        private final TreeDragController libraryDrag = new TreeDragController();
        private final TreeDropResolver dropResolver = new TreeDropResolver();
        private final TreeLinkGeometry linkGeometry = new TreeLinkGeometry();
        private final TreeLinkInteraction linkInteraction = new TreeLinkInteraction();
        private String libraryQuery = "";
        private CanvasGraphSearchPage searchPage;
        private String editorStatus = "Ready";
        private long packetSamples;
        private long eventSamples;
        private long tracedSamples;
        private long lastUiNanos;
        private long lastGraphUiNanos;
        private long graphPointerEvents;
        private long graphProcessingNanos;
        private long graphPeakNanos;
        private long persistenceWrites;
        private long dragBegins;
        private long dragEnds;
        private double lastRawX = Double.NaN;
        private double lastRawY = Double.NaN;
        private CanvasPointerTransform.Coordinates lastCoordinates;
        private String last = "No pointer sample received";
        private String lastGuarded = "";
        private volatile boolean guardInstalled;
        private volatile boolean cameraInstalled;
        private volatile boolean cameraSuperseded;

        private Session(Context context, CursorCanvasEditor editor, Player player, PlayerRef playerRef, World world,
                        CameraManager camera, SetServerCamera priorCamera, Path tracePath,
                        CanvasPointerTransform.Calibration saved) throws IOException {
            this.context = context;
            this.editor = editor;
            this.player = player;
            this.playerRef = playerRef;
            this.world = world;
            this.camera = camera;
            this.priorCamera = priorCamera;
            this.playerId = playerRef.getUuid();
            this.tracePath = tracePath;
            this.trace = Files.newBufferedWriter(tracePath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            this.transform = saved != null && context != Context.PASSIVE_HUD
                    ? new CanvasPointerTransform(saved) : new CanvasPointerTransform();
            this.graph = context == Context.DRAG_PROOF ? createProofGraph()
                    : context == Context.GRAPH_EDITOR ? java.util.Objects.requireNonNull(editor.canvas()) : null;
            if (context == Context.GRAPH_EDITOR) applyStoredLayout(graph, editor.editorId(), playerId);
            traceLifecycle("CREATED", "screenPointDomain=EMPIRICAL_LANDMARKS physicalViewport=UNAVAILABLE logicalScale=UNAVAILABLE");
        }

        private Canvas createProofGraph() {
            NodeDefinition source = NodeDefinition.builder("proof-source").size(150, 72)
                    .port(CanvasPort.output("out", "proof", 4, 150, 36))
                    .renderer(c -> proofVisual(c.node(), c.state().name())).build();
            NodeDefinition target = NodeDefinition.builder("proof-target").size(150, 72)
                    .port(CanvasPort.input("in", "proof", 4, 0, 36))
                    .renderer(c -> proofVisual(c.node(), c.state().name())).build();
            CanvasDefinition definition = CanvasDefinition.builder("cursor-drag-proof-" + playerId)
                    .pannable(false).zoomable(false).panGesture(PanGesture.MIDDLE_BUTTON)
                    .registerNodeType(source).registerNodeType(target)
                    .listener(this::traceCanvasEvent).build();
            Canvas canvas = new Canvas(definition);
            canvas.createNode("proof-a", "proof-source", CanvasPoint.of(120, 120), Map.of("label", "Proof Node A"));
            canvas.createNode("proof-b", "proof-target", CanvasPoint.of(520, 275), Map.of("label", "Proof Node B"));
            canvas.connect("proof-edge", "proof-a", "out", "proof-b", "in", EdgeStyle.standard("proof"));
            return canvas;
        }

        private NodeVisual proofVisual(CanvasNode node, String state) {
            return new NodeVisual(node.metadata().getOrDefault("label", node.nodeId()), state,
                    "#28476af2", "#78c6d0", "#eef6ff");
        }

        private void installDisplay() {
            if (context == Context.PASSIVE_HUD || context == Context.DRAG_PROOF) {
                HudManager manager = player.getHudManager();
                if (manager.getCustomHud(CanvasCursorProbeHud.KEY) != null)
                    throw new IllegalStateException("CanvasUI cursor-HUD key is already owned");
                hud = new CanvasCursorProbeHud(playerRef, context.label());
                manager.addCustomHud(playerRef, hud);
                if (context == Context.DRAG_PROOF) {
                    hud.setDragProofVisible(true);
                    CursorHudCanvasBackend backend = new CursorHudCanvasBackend(graph, hud);
                    graphInput = new CanvasInputController(graph, backend, this::persistGraph,
                            this::graphRenderDue, this::recordGraphPointer);
                    backend.topologyChanged();
                    CanvasInputCapabilities capabilities = HytaleCursorHudInputBackend.INSTANCE.capabilities();
                    traceLifecycle("GRAPH_BACKEND_READY", "backend=" + backend.id()
                            + " input=" + HytaleCursorHudInputBackend.INSTANCE.id()
                            + " capture=" + HytaleCursorHudInputBackend.INSTANCE.captureSemantics()
                            + " capabilities=" + capabilities);
                }
            } else if (context == Context.GRAPH_EDITOR) {
                HudManager manager = player.getHudManager();
                if (manager.getCustomHud(CanvasGraphEditorHud.KEY) != null)
                    throw new IllegalStateException("CanvasUI graph-editor HUD key is already owned");
                editorHud = new CanvasGraphEditorHud(playerRef, editor.title());
                manager.addCustomHud(playerRef, editorHud);
                CursorHudGraphEditorBackend backend = new CursorHudGraphEditorBackend(graph,
                        this::renderEditor, value -> editorStatus = value == null ? "" : value);
                graphInput = new CanvasInputController(graph, backend, this::persistGraph,
                        this::graphRenderDue, this::recordGraphPointer);
                renderEditor();
                traceLifecycle("GRAPH_EDITOR_READY", "editor=" + editor.editorId()
                        + " nodes=" + graph.nodes().size() + " edges=" + graph.edges().size());
            } else if (context == Context.CUSTOM_PAGE_CONTROL) {
                if (player.getPageManager().getCustomPage() != null)
                    throw new IllegalStateException("A CustomUI page is already open");
                page = new CanvasCursorProbePage(playerRef, context.label(),
                        () -> CursorHudProbeService.this.close(playerId, "PAGE_DISMISS"));
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) throw new IllegalStateException("Player reference became unavailable");
                player.getPageManager().openCustomPage(ref, ref.getStore(), page);
            }
        }

        private void accept(CursorProbeSample sample) {
            if (closing.get()) return;
            CursorProbeInputBuffer.OfferResult result = input.offer(sample);
            if (result == CursorProbeInputBuffer.OfferResult.REJECTED_TRANSITION) {
                world.execute(() -> CursorHudProbeService.this.close(playerId, "INPUT_QUEUE_TRANSITION_OVERFLOW"));
                return;
            }
            scheduleDrain();
        }

        private void acceptGuardObservation(CanvasInputGuard.Observation observation) {
            gameplayObserved.incrementAndGet();
            if (observation.guarded()) gameplayGuarded.incrementAndGet(); else gameplayAllowed.incrementAndGet();
            int size = guardQueueSize.incrementAndGet();
            if (size > GUARD_QUEUE_CAPACITY) {
                guardQueueSize.decrementAndGet();
                traceDrops.incrementAndGet();
                return;
            }
            guardQueue.add(observation);
            scheduleDrain();
        }

        private void scheduleDrain() {
            if (closing.get() || !drainScheduled.compareAndSet(false, true)) return;
            world.execute(this::drain);
        }

        private void drain() {
            drainScheduled.set(false);
            if (closing.get() || sessions.get(playerId) != this) return;
            for (CursorProbeSample sample : input.drain(256)) {
                record(sample);
                if (handleCalibration(sample)) continue;
                if (sample.source() == CursorProbeSample.Source.EVENT && sample.validPosition()
                        && transform.ready() && leftPressed(sample)
                        && transform.closeHit(sample.x(), sample.y())) {
                    CursorHudProbeService.this.close(playerId, "VISIBLE_CLOSE_REGION");
                    return;
                }
                try { routeGraph(sample); }
                catch (RuntimeException error) {
                    traceLifecycle("GRAPH_INPUT_FAILED", "error=" + error.getClass().getSimpleName());
                    CursorHudProbeService.this.close(playerId,
                            "GRAPH_INPUT_FAILED_" + error.getClass().getSimpleName());
                    return;
                }
            }
            for (int i = 0; i < 256; i++) {
                CanvasInputGuard.Observation observation = guardQueue.poll();
                if (observation == null) break;
                guardQueueSize.decrementAndGet();
                lastGuarded = observation.route() + ":" + observation.action();
                traceGuard(observation);
            }
            long now = System.nanoTime();
            if (now - lastUiNanos >= UI_INTERVAL_NANOS) {
                lastUiNanos = now;
                refreshDisplay();
            }
            if (!input.isEmpty() || !guardQueue.isEmpty()) scheduleDrain();
        }

        private boolean handleCalibration(CursorProbeSample sample) {
            if (sample.source() != CursorProbeSample.Source.EVENT || context != Context.PASSIVE_HUD
                    || transform.state() != CanvasPointerTransform.State.CAPTURING
                    || !sample.validPosition() || !leftPressed(sample)) return false;
            CanvasPointerTransform.Capture capture = transform.captureNext(sample.x(), sample.y());
            traceCalibration(capture);
            last = "CALIBRATION " + capture.landmark() + " " + capture.reason();
            if (capture.state() == CanvasPointerTransform.State.READY) {
                CanvasPointerTransform.Calibration accepted = transform.calibration().orElseThrow();
                calibrations.put(playerId, accepted);
                persistCalibration(playerId, accepted);
                traceLifecycle("COORDINATE_MAPPING_ACCEPTED", transform.diagnostic());
            } else if (capture.state() == CanvasPointerTransform.State.REJECTED) {
                traceLifecycle("COORDINATE_MAPPING_REJECTED", transform.diagnostic());
            }
            return true;
        }

        private void routeGraph(CursorProbeSample sample) {
            if (sample.source() != CursorProbeSample.Source.EVENT || graphInput == null
                    || !sample.validPosition() || !transform.ready()) return;
            CanvasPointerTransform.Coordinates coordinates = transform.convert(sample.x(), sample.y(), graph.viewport());
            if (context == Context.GRAPH_EDITOR && routeEditor(sample, coordinates.local())) return;
            if (sample.kind() == CursorProbeSample.Kind.BUTTON) {
                MouseButtonType button = enumValue(MouseButtonType.class, sample.button());
                MouseButtonState state = enumValue(MouseButtonState.class, sample.state());
                graphInput.button(coordinates.local(), button, state);
            } else {
                graphInput.motion(coordinates.local(), null, null);
            }
        }

        private boolean routeEditor(CursorProbeSample sample, CanvasPoint local) {
            if (libraryDrag.animating()) return true;
            if (sample.kind() == CursorProbeSample.Kind.MOTION && libraryDrag.active()) {
                boolean wasDragging=libraryDrag.dragging();
                if(libraryDrag.move(local)){
                    if(!wasDragging)traceLifecycle("SKILLTREE_DRAG_STARTED","entry="+libraryDrag.entry().id()
                            +" kind="+libraryDrag.entry().kind());
                    editorStatus = "Drop " + libraryDrag.entry().name() + " onto a "
                            + libraryDrag.entry().kind().name().toLowerCase(Locale.ROOT) + " node";
                    if(graphRenderDue())renderEditor();
                }
                return true;
            }
            if (sample.kind() != CursorProbeSample.Kind.BUTTON) return false;
            MouseButtonType button = enumValue(MouseButtonType.class, sample.button());
            MouseButtonState state = enumValue(MouseButtonState.class, sample.state());
            if(state==MouseButtonState.Pressed&&linkInteraction.contextOpen()){
                CanvasPoint anchor=linkInteraction.popupAnchor();
                if(button==MouseButtonType.Left&&inside(local,anchor.x()+8,anchor.y()+42,50,34)){
                    breakLink(linkInteraction.contextTargetLinkId(),"CONTEXT_CONFIRM");return true;
                }
                if(button==MouseButtonType.Left&&inside(local,anchor.x()+68,anchor.y()+42,50,34)){
                    linkInteraction.dismissContext();editorStatus="Link break cancelled";renderEditor();return true;
                }
                linkInteraction.dismissContext();renderEditor();
            }
            if(button==MouseButtonType.Right&&state==MouseButtonState.Pressed){
                CanvasHitTester.Hit foreground=new CanvasHitTester().hit(graph,local);
                if(!foreground.background())return false;
                String edge=linkGeometry.hit(graph,local);
                if(edge!=null){CanvasPoint popup=CanvasPoint.of(Math.min(690,Math.max(208,local.x())),
                            Math.min(372,Math.max(4,local.y())));
                    linkInteraction.openContext(edge,popup);editorStatus="Break Link?";
                    traceLifecycle("SKILLTREE_LINK_CONTEXT_OPENED","link="+edge);renderEditor();return true;}
                return false;
            }
            if (button != MouseButtonType.Left) return false;
            if (state == MouseButtonState.Pressed) {
                if(inside(local,700,10,116,30)&&linkInteraction.selectedLinkId()!=null){
                    breakLink(linkInteraction.selectedLinkId(),"VISIBLE_DELETE");return true;
                }
                if (local.x() >= 0 && local.x() <= 202 && local.y() >= 0 && local.y() <= 42) {
                    libraryDrag.cancel();
                    libraryTab = local.x() < 98 ? CursorCanvasEditor.LibraryKind.SKILL
                            : CursorCanvasEditor.LibraryKind.PASSIVE;
                    libraryPage = 0;
                    editorStatus = libraryTab == CursorCanvasEditor.LibraryKind.SKILL ? "Skill library" : "Passive library";
                    renderEditor();
                    return true;
                }
                if(inside(local,8,50,186,34)){openSearch();return true;}
                if (local.x() >= 8 && local.x() <= 194 && local.y() >= 92 && local.y() < 404) {
                    int row = (int)((local.y() - 92) / 52);
                    List<CursorCanvasEditor.LibraryEntry> page = editorPage();
                    if (row >= 0 && row < page.size()) {
                        CanvasPoint origin=CanvasPoint.of(32,92+row*52+23);
                        libraryDrag.arm(page.get(row),local,origin);
                        editorStatus = "Hold and drag " + page.get(row).name();
                        renderEditor();
                    }
                    return true;
                }
                if (local.y() >= 424 && local.y() <= 462 && local.x() <= 64) {
                    libraryPage = Math.max(0, libraryPage - 1); renderEditor(); return true;
                }
                if (local.y() >= 424 && local.y() <= 462 && local.x() >= 140 && local.x() <= 202) {
                    libraryPage = Math.min(editorPageCount() - 1, libraryPage + 1); renderEditor(); return true;
                }
                CanvasHitTester.Hit foreground=new CanvasHitTester().hit(graph,local);
                if(!foreground.background()){linkInteraction.clear();return false;}
                String edge=linkGeometry.hit(graph,local);
                if(edge!=null){linkInteraction.select(edge);editorStatus="Link selected";
                    traceLifecycle("SKILLTREE_LINK_SELECTED","link="+edge);renderEditor();return true;}
                linkInteraction.clear();renderEditor();
            } else if (state == MouseButtonState.Released && libraryDrag.active()) {
                if(!libraryDrag.dragging()){libraryDrag.cancel();renderEditor();return true;}
                CursorCanvasEditor.LibraryEntry entry=libraryDrag.entry();
                TreeDropResolver.Result target=dropResolver.resolve(graph,entry.kind(),local);
                boolean accepted=false;CanvasPoint destination=libraryDrag.origin();String message=target.reason();
                if(target.accepted()){
                    CursorCanvasEditor.Result result = editor.assign(entry.id(), target.nodeId(), graph.snapshot());
                    graph.restore(result.authoritativeSnapshot());
                    accepted=result.accepted();message=result.message();
                    if (accepted){destination=target.center();persistEditorLayout();}
                }
                editorStatus=message;
                libraryDrag.animateTo(destination,accepted,System.currentTimeMillis());
                traceLifecycle(accepted?"SKILLTREE_DROP_ACCEPTED":"SKILLTREE_DROP_REJECTED",
                        "entry="+entry.id()+" node="+target.nodeId()+" reason="+message);
                renderEditor();
                scheduleDragAnimation(libraryDrag.generation(),accepted);
                return true;
            }
            return libraryDrag.active();
        }

        private void record(CursorProbeSample sample) {
            if (sample.source() == CursorProbeSample.Source.PACKET) packetSamples++; else eventSamples++;
            if (sample.validPosition()) {
                lastRawX = sample.x();
                lastRawY = sample.y();
                if (transform.ready()) lastCoordinates = transform.convert(sample.x(), sample.y(),
                        graph == null ? com.inigmasgames.canvasui.api.CanvasViewport.ORIGIN : graph.viewport());
            }
            last = describe(sample);
            traceSample(sample);
        }

        private void refreshDisplay() {
            if (hud == null && page == null && editorHud == null) return;
            Double normX = null, normY = null, viewX = null, viewY = null, localX = null, localY = null;
            boolean visible = lastCoordinates != null;
            int markerLeft = 0, markerTop = 0;
            if (lastCoordinates != null) {
                normX = lastCoordinates.normalized().x(); normY = lastCoordinates.normalized().y();
                viewX = lastCoordinates.viewport().x(); viewY = lastCoordinates.viewport().y();
                localX = lastCoordinates.local().x(); localY = lastCoordinates.local().y();
                markerLeft = clamp(Math.round(viewX) - 36L - 7L);
                markerTop = clamp(Math.round(viewY) - 34L - 7L);
            }
            String prompt = calibrationPrompt();
            String status = lastGuarded.isBlank() ? last : last + " | " + lastGuarded;
            CanvasCursorProbeHud.Status frame = new CanvasCursorProbeHud.Status(lastRawX, lastRawY,
                    normX, normY, viewX, viewY, localX, localY, visible, markerLeft, markerTop,
                    packetSamples + eventSamples, gameplayObserved.get(), gameplayGuarded.get(),
                    gameplayAllowed.get(), input.droppedMotion(), traceDrops.get(), transform.diagnostic(),
                    prompt, status);
            try {
                if (hud != null) hud.refresh(frame);
                if (page != null) page.refresh(frame);
            } catch (RuntimeException error) {
                LOGGER.atWarning().withCause(error).log("CANVASUI_CURSOR_UI_UPDATE_FAILED revision=%s session=%s",
                        CanvasUI.REVISION, token);
                CursorHudProbeService.this.close(playerId, "UI_UPDATE_FAILED_" + error.getClass().getSimpleName());
            }
        }

        private String calibrationPrompt() {
            if (context == Context.PASSIVE_HUD) {
                if (transform.state() == CanvasPointerTransform.State.REJECTED)
                    return "MAPPING REJECTED — close/reopen and click target centers carefully";
                CanvasPointerTransform.Landmark next = transform.nextLandmark();
                return next == null ? "" : "CLICK " + next + " TARGET";
            }
            if (context == Context.CUSTOM_PAGE_CONTROL && !transform.ready())
                return "NEGATIVE CONTROL — NO SAVED MAPPING";
            return "";
        }

        private boolean graphRenderDue() {
            long now = System.nanoTime();
            if (now - lastGraphUiNanos < UI_INTERVAL_NANOS) return false;
            lastGraphUiNanos = now;
            return true;
        }

        private void recordGraphPointer(long nanos) {
            graphPointerEvents++;
            graphProcessingNanos += nanos;
            graphPeakNanos = Math.max(graphPeakNanos, nanos);
        }

        private void persistGraph() {
            persistenceWrites++;
            if (editor != null) {
                CursorCanvasEditor.Result result = editor.commit(graph.snapshot());
                graph.restore(result.authoritativeSnapshot());
                editorStatus = result.message();
                if (result.accepted()) persistEditorLayout();
                traceLifecycle(result.accepted() ? "GRAPH_COMMIT_ACCEPTED" : "GRAPH_COMMIT_REJECTED",
                        "message=" + result.message() + " nodes=" + graph.nodes().size()
                                + " edges=" + graph.edges().size());
                renderEditor();
            }
            traceLifecycle("GRAPH_SNAPSHOT_PERSISTED", "writes=" + persistenceWrites
                    + " nodes=" + graph.nodes().size() + " edges=" + graph.edges().size());
        }

        private void persistEditorLayout() {
            if (editor == null) return;
            Path target = layoutPath(editor.editorId(), playerId);
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            try {
                Files.createDirectories(target.getParent());
                java.util.Properties values = new java.util.Properties();
                for (CanvasNode node : graph.nodes()) {
                    values.setProperty(node.nodeId() + ".x", Double.toString(node.position().x()));
                    values.setProperty(node.nodeId() + ".y", Double.toString(node.position().y()));
                }
                try (var output = Files.newOutputStream(temporary, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                    values.store(output, "CanvasUI editor layout");
                }
                try { Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
                catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException error) {
                editorStatus = "Layout persistence failed: " + error.getClass().getSimpleName();
            }
        }

        private int editorPageCount() {
            return editorProjection().pageCount();
        }

        private List<CursorCanvasEditor.LibraryEntry> editorPage() {
            LibraryBrowser.Page result=editorProjection();
            libraryPage=result.pageIndex();
            return result.entries();
        }

        private LibraryBrowser.Page editorProjection(){
            return LibraryBrowser.project(editor.library(libraryTab),libraryQuery,libraryPage,
                    CanvasGraphEditorHud.LIBRARY_ROWS);
        }

        private void renderEditor() {
            if (editorHud == null) return;
            CanvasGraphEditorHud.DragVisual drag=libraryDrag.active()?new CanvasGraphEditorHud.DragVisual(
                    libraryDrag.entry(),libraryDrag.visualPoint(System.currentTimeMillis()),libraryDrag.state().name()):null;
            editorHud.render(graph, libraryTab, editorPage(), libraryPage, editorPageCount(), libraryQuery,
                    editorStatus,drag,linkInteraction);
        }

        private void openSearch(){
            if(searchPage!=null)return;
            if(player.getPageManager().getCustomPage()!=null){editorStatus="Close the current page before searching";renderEditor();return;}
            libraryDrag.cancel();
            searchPage=new CanvasGraphSearchPage(playerRef,libraryQuery,value->world.execute(()->{
                if(closing.get()||sessions.get(playerId)!=this)return;
                libraryQuery=value==null?"":value;libraryPage=0;
                editorStatus=libraryQuery.isBlank()?"Full "+libraryTab.name().toLowerCase(Locale.ROOT)+" library"
                        :"Search: "+libraryQuery;
                traceLifecycle("SKILLTREE_SEARCH_CHANGED","tab="+libraryTab+" query="+libraryQuery
                        +" matches="+editorProjection().totalMatches());renderEditor();
            }),()->world.execute(()->{if(sessions.get(playerId)==this){searchPage=null;renderEditor();}}));
            Ref<EntityStore> ref=playerRef.getReference();
            if(ref==null||!ref.isValid()){searchPage=null;editorStatus="Search unavailable: player reference lost";renderEditor();return;}
            player.getPageManager().openCustomPage(ref,ref.getStore(),searchPage);
        }

        private void scheduleDragAnimation(long generation,boolean accepted){
            java.util.concurrent.CompletableFuture.delayedExecutor(32,java.util.concurrent.TimeUnit.MILLISECONDS).execute(()->
                    world.execute(()->{
                        if(closing.get()||sessions.get(playerId)!=this||libraryDrag.generation()!=generation)return;
                        if(libraryDrag.completeIfDue(System.currentTimeMillis())){
                            traceLifecycle(accepted?"SKILLTREE_DRAG_SNAPPED":"SKILLTREE_DRAG_RETURNED",
                                    "result="+(accepted?"ACCEPTED":"REJECTED"));renderEditor();return;
                        }
                        renderEditor();scheduleDragAnimation(generation,accepted);
                    }));
        }

        private void breakLink(String linkId,String reason){
            traceLifecycle("SKILLTREE_LINK_BREAK_REQUESTED","link="+linkId+" reason="+reason);
            if(linkId==null||graph.edge(linkId)==null){linkInteraction.clear();editorStatus="Link is no longer present";
                traceLifecycle("SKILLTREE_LINK_BREAK_REJECTED","reason=STALE_LINK");renderEditor();return;}
            CursorCanvasEditor.Result result=editor.breakLink(linkId,graph.snapshot());
            graph.restore(result.authoritativeSnapshot());editorStatus=result.message();
            if(result.accepted()){linkInteraction.broken();persistEditorLayout();}
            else if(graph.edge(linkId)==null)linkInteraction.clear();
            traceLifecycle(result.accepted()?"SKILLTREE_LINK_BROKEN":"SKILLTREE_LINK_BREAK_REJECTED",
                    "link="+linkId+" reason="+reason+" message="+result.message());renderEditor();
        }

        private boolean inside(CanvasPoint point,double left,double top,double width,double height){
            return point.x()>=left&&point.x()<=left+width&&point.y()>=top&&point.y()<=top+height;
        }

        private void traceCanvasEvent(CanvasEvent event) {
            if (event.type() == CanvasEventType.DRAG_STARTED) dragBegins++;
            if (event.type() == CanvasEventType.DRAG_ENDED) dragEnds++;
            if (event.type() == CanvasEventType.NODE_CREATED || event.type() == CanvasEventType.CANVAS_CHANGED) return;
            writeLine("{\"type\":\"CANVAS_EVENT\",\"revision\":\"" + json(CanvasUI.REVISION)
                    + "\",\"session\":\"" + token + "\",\"event\":\"" + event.type()
                    + "\",\"nodeId\":\"" + json(event.nodeId()) + "\",\"edgeId\":\""
                    + json(event.edgeId()) + "\",\"after\":\"" + json(String.valueOf(event.after())) + "\"}");
        }

        private void traceSample(CursorProbeSample sample) {
            if (tracedSamples >= TRACE_SAMPLE_LIMIT) { traceDrops.incrementAndGet(); return; }
            tracedSamples++;
            CanvasPointerTransform.Coordinates coordinates = sample.validPosition() && transform.ready()
                    ? transform.convert(sample.x(), sample.y(), graph == null
                    ? com.inigmasgames.canvasui.api.CanvasViewport.ORIGIN : graph.viewport()) : null;
            writeLine("{\"type\":\"POINTER_INPUT\",\"revision\":\"" + json(CanvasUI.REVISION)
                    + "\",\"session\":\"" + token + "\",\"context\":\"" + context.label()
                    + "\",\"sequence\":" + sample.sequence() + ",\"source\":\"" + sample.source()
                    + "\",\"kind\":\"" + sample.kind() + "\",\"positionValid\":" + sample.validPosition()
                    + ",\"rawX\":" + number(sample.validPosition() ? sample.x() : null)
                    + ",\"rawY\":" + number(sample.validPosition() ? sample.y() : null)
                    + ",\"normalizedX\":" + point(coordinates, PointPart.NORMALIZED, true)
                    + ",\"normalizedY\":" + point(coordinates, PointPart.NORMALIZED, false)
                    + ",\"viewportX\":" + point(coordinates, PointPart.VIEWPORT, true)
                    + ",\"viewportY\":" + point(coordinates, PointPart.VIEWPORT, false)
                    + ",\"localX\":" + point(coordinates, PointPart.LOCAL, true)
                    + ",\"localY\":" + point(coordinates, PointPart.LOCAL, false)
                    + ",\"button\":\"" + json(sample.button()) + "\",\"state\":\"" + json(sample.state())
                    + "\",\"held\":\"" + json(sample.heldButtons()) + "\",\"targetBlock\":"
                    + sample.targetBlock() + ",\"targetEntity\":" + sample.targetEntity()
                    + ",\"itemInHand\":" + sample.itemInHand() + "}");
        }

        private void traceCalibration(CanvasPointerTransform.Capture capture) {
            writeLine("{\"type\":\"COORDINATE_CALIBRATION\",\"revision\":\"" + json(CanvasUI.REVISION)
                    + "\",\"session\":\"" + token + "\",\"landmark\":\"" + capture.landmark()
                    + "\",\"rawX\":" + number(capture.raw().x()) + ",\"rawY\":" + number(capture.raw().y())
                    + ",\"expectedX\":" + number(capture.expected().x()) + ",\"expectedY\":" + number(capture.expected().y())
                    + ",\"convertedX\":" + number(capture.converted() == null ? null : capture.converted().x())
                    + ",\"convertedY\":" + number(capture.converted() == null ? null : capture.converted().y())
                    + ",\"error\":" + number(Double.isFinite(capture.error()) ? capture.error() : null)
                    + ",\"state\":\"" + capture.state() + "\",\"reason\":\"" + json(capture.reason()) + "\"}");
        }

        private void traceGuard(CanvasInputGuard.Observation observation) {
            writeLine("{\"type\":\"GAMEPLAY_INPUT_" + (observation.guarded() ? "GUARDED" : "ALLOWED")
                    + "\",\"revision\":\"" + json(CanvasUI.REVISION) + "\",\"session\":\"" + token
                    + "\",\"route\":\"" + json(observation.route()) + "\",\"action\":\""
                    + json(observation.action()) + "\",\"rootInteractionId\":\""
                    + json(observation.rootInteractionId()) + "\"}");
        }

        private void traceLifecycle(String event, String detail) {
            writeLine("{\"type\":\"LIFECYCLE\",\"revision\":\"" + json(CanvasUI.REVISION)
                    + "\",\"session\":\"" + token + "\",\"context\":\"" + context.label()
                    + "\",\"event\":\"" + json(event) + "\",\"detail\":\"" + json(detail) + "\"}");
        }

        private void finish(String reason) {
            if (!closing.compareAndSet(false, true)) return;
            List<String> cleanupFailures = new ArrayList<>();
            libraryDrag.cancel();
            linkInteraction.clear();
            if (guardInstalled && !inputGuard.release(playerId, token)) cleanupFailures.add("GUARD:OWNER_MISMATCH");
            guardInstalled = false;
            traceLifecycle("INPUT_GUARD_RELEASED", "activeAfterRelease=" + inputGuard.active(playerId));
            try { if (graphInput != null) graphInput.clear(); }
            catch (RuntimeException error) { cleanupFailures.add("GRAPH_INPUT:" + error.getClass().getSimpleName()); }
            try {
                if (hud != null && player.getHudManager().getCustomHud(CanvasCursorProbeHud.KEY) == hud)
                    player.getHudManager().removeCustomHud(playerRef, CanvasCursorProbeHud.KEY);
            } catch (RuntimeException error) { cleanupFailures.add("HUD:" + error.getClass().getSimpleName()); }
            try {
                if (editorHud != null && player.getHudManager().getCustomHud(CanvasGraphEditorHud.KEY) == editorHud)
                    player.getHudManager().removeCustomHud(playerRef, CanvasGraphEditorHud.KEY);
            } catch (RuntimeException error) { cleanupFailures.add("EDITOR_HUD:" + error.getClass().getSimpleName()); }
            try { if (editor != null) editor.closed(reason); }
            catch (RuntimeException error) { cleanupFailures.add("EDITOR_CLOSE:" + error.getClass().getSimpleName()); }
            try {
                if (page != null) {
                    traceLifecycle("PAGE_NEGATIVE_CONTROL_AFTER", "pageCloseRequested=true");
                    page.closeFromService();
                }
            } catch (RuntimeException error) { cleanupFailures.add("PAGE:" + error.getClass().getSimpleName()); }
            try { if(searchPage!=null)searchPage.closeFromService(); }
            catch(RuntimeException error){cleanupFailures.add("SEARCH_PAGE:"+error.getClass().getSimpleName());}
            try { restoreCamera(this); }
            catch (RuntimeException error) { cleanupFailures.add("CAMERA:" + error.getClass().getSimpleName()); }

            for (CursorProbeSample sample : input.drain(QUEUE_CAPACITY)) record(sample);
            CanvasInputGuard.Observation observation;
            while ((observation = guardQueue.poll()) != null) { guardQueueSize.decrementAndGet(); traceGuard(observation); }
            String cleanup = cleanupFailures.isEmpty() ? "PASS" : String.join(",", cleanupFailures);
            double graphAverageMs = graphPointerEvents == 0 ? 0.0
                    : graphProcessingNanos / 1_000_000.0 / graphPointerEvents;
            writeLine("{\"type\":\"SUMMARY\",\"revision\":\"" + json(CanvasUI.REVISION)
                    + "\",\"session\":\"" + token + "\",\"context\":\"" + context.label()
                    + "\",\"reason\":\"" + json(reason) + "\",\"packetPointerSamples\":" + packetSamples
                    + ",\"eventPointerSamples\":" + eventSamples + ",\"gameplayObserved\":" + gameplayObserved.get()
                    + ",\"gameplayGuarded\":" + gameplayGuarded.get() + ",\"gameplayAllowed\":" + gameplayAllowed.get()
                    + ",\"queueMotionDrops\":" + input.droppedMotion() + ",\"traceDrops\":" + traceDrops.get()
                    + ",\"mappingState\":\"" + transform.state() + "\",\"mapping\":\"" + json(transform.diagnostic())
                    + "\",\"dragBegins\":" + dragBegins + ",\"dragEnds\":" + dragEnds
                    + ",\"persistenceWrites\":" + persistenceWrites + ",\"graphPointerEvents\":" + graphPointerEvents
                    + ",\"graphAverageProcessingMs\":" + number(graphAverageMs)
                    + ",\"graphPeakProcessingMs\":" + number(graphPeakNanos / 1_000_000.0)
                    + ",\"cameraSuperseded\":" + cameraSuperseded + ",\"guardActiveAfterCleanup\":"
                    + inputGuard.active(playerId) + ",\"cleanup\":\"" + json(cleanup) + "\"}");
            try { trace.flush(); trace.close(); }
            catch (IOException error) { cleanupFailures.add("TRACE:" + error.getClass().getSimpleName()); }
            LOGGER.atInfo().log("CANVASUI_CURSOR_CLOSE revision=%s session=%s context=%s reason=%s pointers=%d gameplay=%d/%d/%d mapping=%s drags=%d/%d persists=%d guardActive=%s cleanup=%s",
                    CanvasUI.REVISION, token, context.label(), reason, packetSamples + eventSamples,
                    gameplayObserved.get(), gameplayGuarded.get(), gameplayAllowed.get(), transform.state(),
                    dragBegins, dragEnds, persistenceWrites, inputGuard.active(playerId),
                    cleanupFailures.isEmpty() ? "PASS" : cleanupFailures);
        }

        private synchronized void writeLine(String line) {
            try { trace.write(line); trace.newLine(); }
            catch (IOException error) { traceDrops.incrementAndGet(); }
        }
    }

    private enum PointPart { NORMALIZED, VIEWPORT, LOCAL }

    private static String point(CanvasPointerTransform.Coordinates coordinates, PointPart part, boolean x) {
        if (coordinates == null) return "null";
        CanvasPoint value = switch (part) {
            case NORMALIZED -> coordinates.normalized();
            case VIEWPORT -> coordinates.viewport();
            case LOCAL -> coordinates.local();
        };
        return number(x ? value.x() : value.y());
    }

    private static boolean leftPressed(CursorProbeSample sample) {
        return sample.kind() == CursorProbeSample.Kind.BUTTON
                && "Left".equals(sample.button()) && "Pressed".equals(sample.state());
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String name) {
        try { return Enum.valueOf(type, name); }
        catch (RuntimeException ignored) { return null; }
    }

    private static String describe(CursorProbeSample sample) {
        String base = sample.source() + " " + sample.kind();
        if (sample.kind() == CursorProbeSample.Kind.BUTTON)
            return base + " " + sample.button() + " " + sample.state();
        return base + " d=" + number(sample.deltaX()) + "," + number(sample.deltaY())
                + " held=" + sample.heldButtons();
    }

    private static int clamp(long value) { return (int)Math.max(-4096L, Math.min(8192L, value)); }

    private static String json(String value) {
        if (value == null) return "";
        String bounded = value.length() <= 512 ? value : value.substring(0, 512);
        return bounded.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String number(Number value) {
        if (value == null) return "null";
        if (value instanceof Double d && !Double.isFinite(d)) return "null";
        return String.valueOf(value);
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
