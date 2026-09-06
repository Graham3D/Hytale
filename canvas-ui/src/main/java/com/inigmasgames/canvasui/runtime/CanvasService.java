package com.inigmasgames.canvasui.runtime;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.canvasui.api.CanvasDefinition;
import com.inigmasgames.canvasui.api.CanvasInputBackend;
import com.inigmasgames.canvasui.rendering.CanvasPage;
import com.inigmasgames.canvasui.rendering.HytaleCustomUiBackend;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Player/session boundary. Consumers never register mouse listeners themselves. */
public final class CanvasService implements AutoCloseable {
    private final SessionRegistry<CanvasSession> sessions = new SessionRegistry<>();

    public CanvasSession open(Player player, CanvasDefinition definition) {
        return open(player, definition, canvas -> { });
    }

    /** Opens a page after optional initial graph construction, avoiding an empty first frame. */
    public CanvasSession open(Player player, CanvasDefinition definition,
                              java.util.function.Consumer<com.inigmasgames.canvasui.api.Canvas> initializer) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) throw new IllegalStateException("player reference is unavailable");
        PlayerRef playerRef = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) throw new IllegalStateException("PlayerRef component is unavailable");
        UUID playerId = playerRef.getUuid();
        close(playerId, "REPLACED_BY_NEW_SESSION");
        CanvasSession session = new CanvasSession(this, player, playerRef, definition, initializer);
        try {
            sessions.register(playerId, session);
        } catch (IllegalStateException collision) {
            session.close("SESSION_COLLISION");
            throw collision;
        }
        return session;
    }

    void openPage(Player player, PlayerRef playerRef, CanvasSession session, HytaleCustomUiBackend backend) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) throw new IllegalStateException("player reference is unavailable");
        Store<EntityStore> store = ref.getStore();
        CanvasPage page = new CanvasPage(playerRef, session, backend);
        backend.attach(page, () -> player.getPageManager().setPage(ref, store, Page.None));
        player.getPageManager().openCustomPage(ref, store, page);
    }

    public CanvasSession activeSession(UUID playerId) { return sessions.get(playerId); }
    public int activeSessionCount() { return sessions.size(); }
    public Collection<CanvasSession> activeSessions() { return sessions.snapshot(); }
    public CanvasInputBackend inputBackend() { return com.inigmasgames.canvasui.rendering.HytaleCustomUiInputBackend.INSTANCE; }

    public void route(PlayerMouseButtonEvent event) {
        PlayerRef playerRef = event.getPlayerRefComponent();
        if (playerRef == null) return;
        CanvasSession session = sessions.get(playerRef.getUuid());
        if (session != null) session.pointerButton(event);
    }

    public void route(PlayerMouseMotionEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef();
        PlayerRef playerRef = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;
        CanvasSession session = sessions.get(playerRef.getUuid());
        if (session != null) session.pointerMotion(event);
    }

    public void close(UUID playerId, String reason) {
        CanvasSession session = sessions.remove(playerId);
        if (session != null) session.close(reason);
    }

    void sessionClosed(CanvasSession session) { sessions.remove(session.playerId(), session); }
    @Override public void close() { for (CanvasSession session : sessions.snapshot()) session.close("SERVICE_CLOSE"); sessions.clear(); }
}
