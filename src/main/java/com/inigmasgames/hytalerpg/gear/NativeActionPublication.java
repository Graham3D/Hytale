package com.inigmasgames.hytalerpg.gear;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** Asset-store writes need the world tick's global read lock to be released first. */
final class NativeActionPublication {
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "HyARPG-gear-action-assets");
        thread.setDaemon(true);
        return thread;
    });
    private static final ConcurrentHashMap<String, CompletableFuture<String>> publications = new ConcurrentHashMap<>();

    private NativeActionPublication() {}

    /** Queue once per asset identity and return null until the complete variant is published. */
    static String ready(String id, Supplier<String> publish) {
        var future = publications.computeIfAbsent(id, key -> {
            var queued = CompletableFuture.supplyAsync(() -> {
                String result = publish.get();
                if (!key.equals(result)) throw new IllegalStateException("Published unexpected gear action " + result);
                return result;
            }, LOADER);
            queued.whenComplete((result, failure) -> {
                if (failure != null) System.err.println("GEAR_ACTION_PUBLICATION_FAILED id=" + key
                        + " cause=" + failure.getCause());
            });
            return queued;
        });
        return future.isDone() && !future.isCompletedExceptionally() ? future.getNow(null) : null;
    }
}
