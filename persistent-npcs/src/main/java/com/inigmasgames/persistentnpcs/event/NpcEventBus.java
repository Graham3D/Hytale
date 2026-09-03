package com.inigmasgames.persistentnpcs.event;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class NpcEventBus {
    private final CopyOnWriteArrayList<Consumer<NpcFrameworkEvent>> listeners =
            new CopyOnWriteArrayList<>();

    public AutoCloseable register(Consumer<NpcFrameworkEvent> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void emit(NpcFrameworkEvent event) {
        NpcFrameworkEvent normalized = event.normalized();
        listeners.forEach(listener -> listener.accept(normalized));
    }
}
