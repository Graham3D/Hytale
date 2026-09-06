package com.inigmasgames.canvasui.api;

import java.util.Optional;

public interface CanvasPersistenceAdapter {
    Optional<CanvasSnapshot> load(String canvasId);
    void save(String canvasId, CanvasSnapshot snapshot);

    static CanvasPersistenceAdapter none() {
        return new CanvasPersistenceAdapter() {
            public Optional<CanvasSnapshot> load(String canvasId) { return Optional.empty(); }
            public void save(String canvasId, CanvasSnapshot snapshot) { }
        };
    }
}
