package com.inigmasgames.hytalerpg.readypath;

import java.util.concurrent.*;
import java.util.function.Supplier;

/** Values-only preparation. The durable repositories keep their own executors and ownership. */
public final class WorkBudget implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    public WorkBudget(int workers, int queued) {
        if (workers < 1 || queued < 1) throw new IllegalArgumentException("Positive work limits required");
        executor = new ThreadPoolExecutor(workers, workers, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queued), r -> Thread.ofPlatform().daemon().name("Hywind-entry-preparation").unstarted(r));
    }
    public <T> CompletableFuture<T> submit(Supplier<T> work) {
        var result = new CompletableFuture<T>();
        try { executor.execute(() -> {
            if (result.isDone()) return;
            try { result.complete(work.get()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        }); } catch (RejectedExecutionException full) { result.completeExceptionally(full); }
        return result;
    }
    public int queued() { return executor.getQueue().size(); }
    @Override public void close() { executor.shutdown(); }
}
