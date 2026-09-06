package com.inigmasgames.canvasui.runtime;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class SessionRegistry<T> {
    private final ConcurrentHashMap<UUID, T> values = new ConcurrentHashMap<>();
    void register(UUID playerId, T value) {
        if (values.putIfAbsent(playerId, value) != null) throw new IllegalStateException("player already has a session");
    }
    T get(UUID playerId) { return values.get(playerId); }
    T remove(UUID playerId) { return values.remove(playerId); }
    void remove(UUID playerId, T value) { values.remove(playerId, value); }
    int size() { return values.size(); }
    Collection<T> snapshot() { return List.copyOf(values.values()); }
    void clear() { values.clear(); }
}
