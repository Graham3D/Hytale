# Input and panning

Consumers do not register packet or mouse listeners. CanvasUI routes Hytale's
global player mouse button/motion events into the active player session.

The fixed transform is always:

```text
screenPoint = canvasPoint + viewportOffset
canvasPoint = screenPoint - viewportOffset
zoom = 1.0
```

Configure `PanGesture.MIDDLE_BUTTON` or `PanGesture.LEFT_BACKGROUND`. A press on
a node or port never begins background panning. Motion changes only the viewport
offset during pan and only the selected node position during drag.

The model processes every delivered event. Hytale UI geometry transmission is
bounded to 10 Hz and touches only the moved node plus attached edges during node
drag; panning necessarily updates all visible nodes and edges. Topology rebuilds
occur for structural or visual-state changes, not every drag motion.

Current limitation: delivery and screen-coordinate semantics while a custom
page owns the pointer require real-client confirmation on Hytale 0.7.0-pre.1.
There is no supported pointer-capture API, so CanvasUI maintains logical capture
from press until release and must verify release delivery in the client.
