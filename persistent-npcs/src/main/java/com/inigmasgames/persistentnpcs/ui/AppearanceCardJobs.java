package com.inigmasgames.persistentnpcs.ui;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/** One running and one latest pending render per viewer; one queued world delivery. */
public final class AppearanceCardJobs implements AutoCloseable {
    public static final int MAX_CARDS = 128;
    private static final class Renderer { static final AppearanceColorCards INSTANCE = new AppearanceColorCards(); }
    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<Batch> pending = new AtomicReference<>();
    private final AtomicBoolean deliveryQueued = new AtomicBoolean();
    private final ThreadPoolExecutor worker;
    private final Function<AppearanceColorCards.Request, AppearanceColorCards.Rendered> renderer;
    private volatile boolean closed;
    public record Card(int slot, AppearanceColorCards.Rendered image) { }
    public record Batch(long generation, List<Card> cards, String failure) { }

    public AppearanceCardJobs() { this(request -> Renderer.INSTANCE.render(request)); }
    public AppearanceCardJobs(Function<AppearanceColorCards.Request, AppearanceColorCards.Rendered> renderer) {
        this.renderer = renderer;
        worker = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), r -> {
            Thread thread = new Thread(r, "npc-private-color-cards"); thread.setDaemon(true); return thread;
        }, new ThreadPoolExecutor.DiscardOldestPolicy());
        worker.allowCoreThreadTimeOut(true);
    }
    public boolean current(long token) { return !closed && generation.get() == token; }
    public void invalidate() { generation.incrementAndGet(); pending.set(null); worker.getQueue().clear(); }
    public void request(List<AppearanceColorCards.Request> input, Executor world, Consumer<Batch> receiver) {
        invalidate();
        if (closed) return;
        long token = generation.get();
        List<AppearanceColorCards.Request> requests = List.copyOf(input);
        if (requests.size() > MAX_CARDS || requests.stream().anyMatch(r -> r.slot() < 0 || r.slot() >= MAX_CARDS))
            throw new IllegalArgumentException("Private card budget exceeded");
        worker.execute(() -> {
            List<Card> cards = new ArrayList<>();
            String failure = "";
            try {
                for (var request : requests) {
                    if (!current(token)) return;
                    var image = renderer.apply(request);
                    if (image != null) cards.add(new Card(request.slot(), image));
                }
            } catch (RuntimeException e) { failure = e.getClass().getSimpleName() + ": " + e.getMessage(); }
            if (!current(token)) return;
            pending.set(new Batch(token, List.copyOf(cards), failure));
            if (deliveryQueued.compareAndSet(false, true)) {
                try { world.execute(() -> {
                    // Reset before taking the latest batch: concurrent completion may enqueue
                    // one extra no-op, but cannot strand a newer batch behind an old callback.
                    deliveryQueued.set(false);
                    Batch batch = pending.getAndSet(null);
                    if (batch != null && current(batch.generation())) receiver.accept(batch);
                }); } catch (RuntimeException stopped) { deliveryQueued.set(false); pending.set(null); }
            }
        });
    }
    @Override public void close() { closed = true; invalidate(); worker.shutdownNow(); }
}
