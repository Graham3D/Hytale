# CanvasUI

CanvasUI is a reusable Hytale `0.7.0-pre.1` server-side library for fixed-zoom,
FigJam-style node canvases. It owns input routing, coordinate conversion, drag
state, panning, graph validation, edge geometry, bounded Hytale UI updates,
session cleanup, and instrumentation. Consumers own node meaning, policy,
metadata, persistence location, and gameplay authority.

CanvasUI has no HytaleRPG or HTDevLib dependency. The current development build
is `0.1.0`, revision `R006`.

## Build and artifacts

```powershell
.\gradlew.bat :canvas-ui:clean :canvas-ui:test :canvas-ui:jar
```

- `canvas-ui/build/libs/CanvasUI-0.1.0.jar` — reusable library/plugin
- `canvas-ui/build/libs/CanvasUI-0.1.0-sources.jar` — sources
- `canvas-ui/build/libs/CanvasUI-0.1.0-javadoc.jar` — API documentation

The R006 development JAR also contains the public-API-only generic demo and
topology-proof commands. They remain isolated under the demo source tree so the
consumer boundary is testable, but there is no second installed mod.

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
- `CanvasSession` and `CanvasMetrics`

See [Getting started](docs/getting-started.md), [nodes](docs/nodes.md),
[ports and connections](docs/ports-and-connections.md),
[input and panning](docs/input-and-panning.md),
[persistence](docs/persistence.md), [custom renderers](docs/custom-renderers.md),
and the [topology integration example](docs/example-link-tree.md).

## Experimental Hytale limitations

The graph/model and automated tests are production-oriented, but the current
Hytale backend remains experimental until the real client proves that
`PlayerMouseButtonEvent` and `PlayerMouseMotionEvent` continue while a custom
page owns the cursor. The public API contains no native spline/path primitive;
the current `EdgeRenderer` uses three efficient orthogonal segments. UI updates
are capped at 10 Hz. `CanvasMetrics` reports event/update rates and server-side
processing latency; true client presentation latency is not exposed.

CanvasUI never patches the client, implements zoom, or stores authoritative
gameplay data only in UI state.
