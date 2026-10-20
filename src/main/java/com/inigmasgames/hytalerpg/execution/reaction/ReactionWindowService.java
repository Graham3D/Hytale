package com.inigmasgames.hytalerpg.execution.reaction;

import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Generic one-shot reaction window keyed by actor and authenticated incoming-event identity. */
public final class ReactionWindowService {
    private final LongSupplier nanoTime;
    private final Map<UUID, Window> windows = new HashMap<>();
    private final Map<String, Long> consumedEvents = new HashMap<>();
    public ReactionWindowService(LongSupplier nanoTime) { this.nanoTime = nanoTime; }

    public synchronized boolean arm(UUID actor, SkillExecutionContext context, double seconds) {
        if (active(actor).isPresent()) return false;
        windows.put(actor, new Window(context, nanoTime.getAsLong() + Math.round(seconds * 1_000_000_000.0)));
        return true;
    }
    public synchronized Optional<SkillExecutionContext> trigger(UUID actor, String signal, String eventId) {
        long now = nanoTime.getAsLong();
        consumedEvents.entrySet().removeIf(entry -> now - entry.getValue() > 10_000_000_000L);
        Window window = windows.get(actor);
        String dedup = actor + ":" + eventId;
        if (window == null || now >= window.endsAtNanos
                || !window.context.profile().reaction().qualifyingSignals().contains(signal)
                || consumedEvents.putIfAbsent(dedup, now) != null) return Optional.empty();
        windows.remove(actor);
        return Optional.of(window.context);
    }
    public synchronized Optional<SkillExecutionContext> expire(UUID actor) {
        Window window = windows.get(actor);
        if (window == null || nanoTime.getAsLong() < window.endsAtNanos) return Optional.empty();
        windows.remove(actor); return Optional.of(window.context);
    }
    public synchronized Optional<SkillExecutionContext> cancel(UUID actor) {
        Window removed = windows.remove(actor); return removed == null ? Optional.empty() : Optional.of(removed.context);
    }
    public synchronized Optional<Window> active(UUID actor) {
        return Optional.ofNullable(windows.get(actor));
    }
    public record Window(SkillExecutionContext context, long endsAtNanos) { }
}
