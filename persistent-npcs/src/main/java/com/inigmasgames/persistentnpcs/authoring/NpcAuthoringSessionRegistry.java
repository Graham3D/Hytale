package com.inigmasgames.persistentnpcs.authoring;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Process-wide single-writer lease registry for NPC Authoring Studio sessions. */
public final class NpcAuthoringSessionRegistry {
    private static final NpcAuthoringSessionRegistry SHARED =
            new NpcAuthoringSessionRegistry();

    private final Map<UUID, NpcAuthoringSession> byNpc = new HashMap<>();
    private final Map<UUID, NpcAuthoringSession> byViewer = new HashMap<>();
    private final AtomicLong pageGenerations = new AtomicLong();

    public static NpcAuthoringSessionRegistry shared() { return SHARED; }

    public synchronized NpcAuthoringSession acquire(
            UUID viewerPlayerId, UUID npcStableId, UUID npcEntityUuid,
            Map<String, String> domainRevisions, Predicate<String> permissionCheck,
            Consumer<String> diagnostics) {
        if (viewerPlayerId == null || npcStableId == null) {
            throw new IllegalArgumentException("Viewer and stable NPC identity are required.");
        }
        NpcAuthoringSession npcLease = byNpc.get(npcStableId);
        if (npcLease != null && npcLease.acceptingEvents()) {
            throw new IllegalStateException("That NPC is already open in another authoring session.");
        }
        NpcAuthoringSession viewerLease = byViewer.get(viewerPlayerId);
        if (viewerLease != null && viewerLease.acceptingEvents()) {
            throw new IllegalStateException("Close the current NPC Authoring Studio first.");
        }
        UUID sessionId = UUID.randomUUID();
        long pageGeneration = pageGenerations.incrementAndGet();
        NpcAuthoringSession[] holder = new NpcAuthoringSession[1];
        NpcAuthoringSession created = new NpcAuthoringSession(
                sessionId, viewerPlayerId, npcStableId, npcEntityUuid,
                pageGeneration, domainRevisions, permissionCheck, diagnostics,
                () -> release(holder[0]));
        holder[0] = created;
        byNpc.put(npcStableId, created);
        byViewer.put(viewerPlayerId, created);
        return created;
    }

    public void closeForViewer(UUID viewerPlayerId) {
        NpcAuthoringSession session;
        synchronized (this) { session = byViewer.get(viewerPlayerId); }
        if (session != null) session.close();
    }

    public void closeAll() {
        NpcAuthoringSession[] sessions;
        synchronized (this) { sessions = byNpc.values().toArray(NpcAuthoringSession[]::new); }
        for (NpcAuthoringSession session : sessions) session.close();
    }

    public synchronized int activeSessionCount() { return byNpc.size(); }

    private synchronized void release(NpcAuthoringSession session) {
        if (session == null) return;
        byNpc.remove(session.npcStableId(), session);
        byViewer.remove(session.viewerPlayerId(), session);
    }
}
