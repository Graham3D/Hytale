# CanvasUI

CanvasUI is a reusable Hytale `0.7.0-pre.2` server-side library for zoomable,
FigJam-style node canvases. It owns input capabilities, coordinate conversion, drag
state, panning, graph validation, edge geometry, bounded Hytale UI updates,
session cleanup, and instrumentation. Consumers own node meaning, policy,
metadata, persistence location, and gameplay authority.

CanvasUI has no HytaleRPG or HTDevLib dependency. The current development build
is `0.1.0`, revision `R042`.

## Build and artifacts

```powershell
.\gradlew.bat :canvas-ui:clean :canvas-ui:test :canvas-ui:jar
```

- `canvas-ui/build/libs/CanvasUI-0.1.0.jar` — reusable library/plugin
- `canvas-ui/build/libs/CanvasUI-0.1.0-sources.jar` — sources
- `canvas-ui/build/libs/CanvasUI-0.1.0-javadoc.jar` — API documentation

The R042 development JAR also contains the public-API-only generic demo and
topology-proof commands. They remain isolated under the demo source tree so the
consumer boundary is testable, but there is no second installed mod.

R042 retains the Phase-A cursor-camera/passive-HUD controls and adds a bounded
landmark-calibrated cursor-HUD input backend entry gate. The passive probe now
uses the exact same `CanvasPointerTransform` for its marker, close hit, and graph
hit testing. Gameplay interactions are rejected at `InteractionChainStartEvent`
before their first native operation tick, with cancellable hotbar/drop requests
guarded at their ECS request events. The new `/canvasui-cursor-drag-proof`
command drives two real graph nodes through the existing `CanvasInputController`.

The development JAR also contains an opt-in cursor-camera/passive-HUD feasibility
probe. It deliberately does not drive the graph until connected-client pointer
delivery, coordinate mapping, gameplay isolation, and lifecycle cleanup pass.
Use `/canvasui-cursor-probe` for the passive-HUD context,
`/canvasui-cursor-probe-nohud` for the camera-only context,
`/canvasui-cursor-probe-page` for the CustomUI-page control, and
`/canvasui-cursor-probe-close` as the administrative close path.

See the living
[`development report`](../docs/canvas-ui/development-report.md) for the exact
per-revision client evidence, decisions, blockers, and next probe.

Install the CanvasUI jar in the save's `mods` directory. A consuming plugin
declares `"InigmasGames:CanvasUI": "*"` in its manifest and compiles against
the jar:

```groovy
dependencies {
    compileOnly files('libs/CanvasUI-0.1.0.jar')
}
```

## Public surface

- `CanvasUI.service()` and `CanvasService`
- `CanvasDefinition`, `NodeDefinition`, `Canvas`, `CanvasNode`, `CanvasPort`, `CanvasEdge`
- `ConnectionPolicy` and structured `ConnectionResult`
- `CanvasListener` and typed `CanvasEventType`
- `CanvasSnapshot`, `CanvasSnapshotCodec`, and `CanvasPersistenceAdapter`
- `NodeRenderer`, `EdgeRenderer`, and renderer-neutral visual/geometry records
- `CanvasInputBackend`, `CanvasRenderBackend`, and inspectable input capabilities
- locale-stable node presentation search and cursor-centered zoom transforms
- `CanvasSession` and `CanvasMetrics`

See [Getting started](docs/getting-started.md), [nodes](docs/nodes.md),
[ports and connections](docs/ports-and-connections.md),
[input and panning](docs/input-and-panning.md),
[persistence](docs/persistence.md), [custom renderers](docs/custom-renderers.md),
and the [topology integration example](docs/example-link-tree.md).

## Experimental Hytale limitations

The graph/model and automated tests are production-oriented, but the retained
CustomUI-page backend cannot perform freeform node drag or pointer pan: R006 measured
zero gameplay pointer events while the page owned UI input, and the pinned
CustomUI event enum has no generic pointer-move or pointer-capture event. The
R007 backend declares these limitations for consumers. The public API contains no native spline/path primitive;
the current `EdgeRenderer` uses three efficient orthogonal segments. UI updates
are capped at 10 Hz. R042 tests a separate cursor-camera plus passive-HUD route
without claiming success before connected evidence exists. `CanvasMetrics`
reports event/update rates and server-side
processing latency; true client presentation latency is not exposed.

CanvasUI never patches the client or stores authoritative gameplay data only in
UI state. R007 adds persisted zoom, a native slider fallback, search, and
right-click edge-disconnect plumbing; those UI paths still require real-client
confirmation.
