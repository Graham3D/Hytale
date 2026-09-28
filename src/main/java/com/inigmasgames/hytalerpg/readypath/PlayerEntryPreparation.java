package com.inigmasgames.hytalerpg.readypath;

import com.inigmasgames.hytalerpg.progress.RpgLoadoutService;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutView;
import com.inigmasgames.hywind.readypath.ReadyPathProbe;
import java.util.*;
import java.util.concurrent.*;

/** Owns preparation lifetimes only, never authoritative player state or native readiness. */
public final class PlayerEntryPreparation implements AutoCloseable {
    public record Token(UUID player, UUID world, long generation, String contentRevision) {}
    public record PlayerEntryView(Token token, long playerRevision, RpgLoadoutView loadout) {}
    private record Pending(Token token, CompletableFuture<PlayerEntryView> result) {}
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final RpgLoadoutService authority;
    private final WorkBudget budget;
    private long generation;
    private boolean closed;
    public PlayerEntryPreparation(RpgLoadoutService authority) { this(authority, new WorkBudget(2, 32)); }
    public PlayerEntryPreparation(RpgLoadoutService authority, WorkBudget budget) { this.authority = authority; this.budget = budget; }

    public synchronized CompletableFuture<PlayerEntryView> prepare(UUID player, UUID world, String revision) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("ENTRY_PREPARATION_STOPPED"));
        if (pending.size() >= 256 && !pending.containsKey(player))
            return CompletableFuture.failedFuture(new RejectedExecutionException("ENTRY_PREPARATION_CAPACITY"));
        var token = new Token(player, world, ++generation, revision);
        var result = new CompletableFuture<PlayerEntryView>();
        var current = new Pending(token, result);
        var prior = pending.put(player, current);
        if (prior != null) prior.result.completeExceptionally(new CancellationException("ENTRY_SUPERSEDED"));
        var ticket = ReadyPathProbe.queued();
        try {
            authority.preload(player).thenCompose(ignored -> budget.submit(() -> {
                if (!current(token)) throw new CancellationException("STALE_ENTRY_PREPARATION");
                try (var span = ReadyPathProbe.execute("RPG_ENTRY_VIEW_PREPARE", player, ticket)) {
                    if (!authority.ready(player)) throw new IllegalStateException("PLAYER_PERSISTENCE_UNCERTAIN");
                    var view = authority.getPresentationView(player);
                    return new PlayerEntryView(token, view.state().revision, view);
                }
            })).whenComplete((view, failure) -> {
                synchronized (this) {
                    if (!current(token)) { result.completeExceptionally(new CancellationException("STALE_ENTRY_PREPARATION")); return; }
                    if (failure != null) result.completeExceptionally(failure);
                    else result.complete(view);
                }
            });
        } catch (RuntimeException failure) { result.completeExceptionally(failure); }
        result.orTimeout(30, TimeUnit.SECONDS);
        return result;
    }
    public synchronized boolean current(Token token) {
        var entry = pending.get(token.player());
        return !closed && entry != null && entry.token.equals(token) && !entry.result.isCompletedExceptionally();
    }
    public synchronized Optional<PlayerEntryView> prepared(UUID player, UUID world) {
        var entry = pending.get(player);
        if (entry == null || !Objects.equals(entry.token.world(), world) || !entry.result.isDone()
                || entry.result.isCompletedExceptionally()) return Optional.empty();
        return Optional.of(entry.result.getNow(null));
    }
    public synchronized void detach(UUID player) {
        var entry = pending.remove(player);
        if (entry != null) entry.result.completeExceptionally(new CancellationException("ENTRY_DETACHED"));
    }
    public synchronized Map<String, Object> inspect() {
        return Map.of("sessions", pending.size(), "maxSessions", 256, "queued", budget.queued(),
                "workers", 2, "queueCapacity", 32, "closed", closed, "nativeGameplayGate", "UNPROVEN");
    }
    @Override public synchronized void close() {
        closed = true;
        for (var entry : pending.values()) entry.result.completeExceptionally(new CancellationException("ENTRY_SHUTDOWN"));
        pending.clear(); budget.close();
    }
}
