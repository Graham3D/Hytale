# Nodes

Node definitions describe reusable visual/interaction types. Node state contains
only stable ID, type, canvas position, enabled state, and opaque string metadata.

```java
NodeDefinition transform = NodeDefinition.builder("transform")
    .size(160, 82)
    .draggable(true)
    .selectable(true)
    .port(CanvasPort.input("in", "data", 2, 0, 41))
    .port(CanvasPort.output("out", "data", 3, 160, 41))
    .renderer(myRenderer)
    .build();

CanvasNode node = session.createNode(
    "transform-7", "transform", CanvasPoint.of(420, 260),
    Map.of("externalId", "arbitrary-consumer-reference"));
```

Positions are canvas coordinates. Panning never changes them. A drag preserves
the initial grab offset and begins only after four UI units of pointer travel.
Selection supports none, one, and programmatic selection. Multi-selection is
reserved for a future compatible extension.
