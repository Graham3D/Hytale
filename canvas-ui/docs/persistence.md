# Persistence

CanvasUI exposes snapshots but does not choose a player's data store.

```java
final class MyAdapter implements CanvasPersistenceAdapter {
    public Optional<CanvasSnapshot> load(String canvasId) {
        return database.read(canvasId).map(CanvasSnapshotCodec::decode);
    }

    public void save(String canvasId, CanvasSnapshot snapshot) {
        database.write(canvasId, CanvasSnapshotCodec.encode(snapshot));
    }
}
```

Register the adapter with `.persistence(adapter)`. Snapshots include canvas ID,
viewport, node IDs/types/positions/enabled state/metadata, edges, port
relationships, styles, and selection. Restoration validates all graph
references, port limits, policy, and cycle configuration. A bad snapshot fails
instead of creating a partially valid graph.

Opaque metadata is round-tripped as `Map<String,String>`; CanvasUI never
interprets it. Authoritative gameplay data remains in the consumer's model.
