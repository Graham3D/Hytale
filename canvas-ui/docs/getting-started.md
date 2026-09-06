# Getting started

Install `CanvasUI-0.1.0.jar`, add it as a compile-only dependency, and declare
the runtime manifest dependency:

```json
"Dependencies": { "InigmasGames:CanvasUI": "*" }
```

From a player command or other world-thread callback:

```java
NodeDefinition source = NodeDefinition.builder("source")
    .size(140, 72)
    .port(CanvasPort.output("out", "data", 4, 140, 36))
    .renderer(ctx -> NodeVisual.simple("Source", "#28476aee"))
    .build();

NodeDefinition output = NodeDefinition.builder("output")
    .size(140, 72)
    .port(CanvasPort.input("in", "data", 4, 0, 36))
    .renderer(ctx -> NodeVisual.simple("Output", "#285b49ee"))
    .build();

CanvasDefinition definition = CanvasDefinition.builder("quest-editor")
    .fixedZoom(true)
    .pannable(true)
    .panGesture(PanGesture.MIDDLE_BUTTON)
    .registerNodeType(source)
    .registerNodeType(output)
    .connectionPolicy(ConnectionPolicy.allowAll())
    .build();

CanvasSession session = CanvasUI.service().open(player, definition, canvas -> {
    canvas.createNode("source-1", "source", CanvasPoint.of(300, 200), Map.of());
    canvas.createNode("output-1", "output", CanvasPoint.of(650, 200), Map.of());
});
```

The initializer runs before the first page frame. Later, use session methods for
programmatic changes. Only one CanvasUI page is active per player; opening a new
session closes the prior one cleanly.
