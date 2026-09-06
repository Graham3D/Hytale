# Ports and connections

Ports have stable IDs, direction, consumer-defined semantic type, connection
limit, and node-relative anchor position.

```java
CanvasPort inputA = CanvasPort.input("in-a", "route", 2, 0, 28);
CanvasPort inputB = CanvasPort.input("in-b", "route", 2, 0, 70);
CanvasPort output = CanvasPort.output("out", "route", 6, 120, 49);

NodeDefinition router = NodeDefinition.builder("router")
    .size(120, 98).port(inputA).port(inputB).port(output).build();
```

A consumer policy makes domain decisions:

```java
ConnectionPolicy policy = (source, sourcePort, target, targetPort) -> {
    if (source.nodeId().equals(target.nodeId())) {
        return ConnectionResult.reject(
            ConnectionCode.REJECT_SELF_CONNECTION, "Choose another node");
    }
    return ConnectionResult.allow();
};
```

CanvasUI validates node/port existence, directions, limits, duplicates, and
optional cycles before invoking the consumer policy. Rejections are explicit,
visible, and emitted as events. Generic canvases allow cycles unless the
definition calls `.allowCycles(false)`.

Interactive connection dragging starts on an output/bidirectional port, renders
a live preview, evaluates the hovered target, and commits only an allowed edge.
The default connector is an internal three-segment orthogonal fallback; graph
logic does not depend on that implementation.
