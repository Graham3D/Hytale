# CanvasUI development report

## Report policy

This is the canonical, living engineering report for CanvasUI. It must be
updated after every deployed development revision. Each revision entry records
the artifact identity, observed client behavior, server evidence, implementation
decision, and remaining uncertainty. A new report may replace this one after a
major milestone; the previous report must then be retained under
`docs/canvas-ui/history/` and linked from its successor.

This report distinguishes three different kinds of claims:

- **Proven** means observed in the real Hytale client or directly measured in
  its server log.
- **Implemented but unproven** means source and automated checks exist, but the
  relevant real-client behavior has not occurred.
- **Unavailable in the audited API** means the pinned public server API exposes
  no matching capability. This does not claim that no future or undocumented
  client implementation could provide it.

## Current status

- CanvasUI version: `0.1.0`, development revision `R006`.
- Hytale target: `0.7.0-pre.1`, revision
  `e8b4d191fc98a977bf5546a951a7b25473d323e3`.
- Branch: `RPG`.
- R006 implementation commit: `04834b9096cbd896db20674fc99265728ae01fbd`.
- R006 evidence commit: `cb5cf2a`.
- R006 CanvasUI JAR SHA-256:
  `653AFEAFA1D55781DC21E1B74FECC6EFEA1EAB00EC98912E5193425148C35D4C`.
- Rendering gate: **PROVEN**.
- Custom page lifecycle/open gate: **PROVEN**.
- Node dragging and canvas panning: **BLOCKED by the current input route**.
- Interactive link removal: **NOT IMPLEMENTED**.
- Production Link Tree approval: **NOT APPROVED**.

R006 is the first major milestone because it proves that a generic graph with
multiple node types, ports, and attached orthogonal connections can be rendered
by a standalone CanvasUI JAR in the real client. It is not yet a FigJam-like
canvas: the displayed graph is presently passive.

## R006 real-client result and diagnosis

### What was proven

The user ran `/canvasui-demo` in the isolated pre-release `RPG` save. The page
opened without a command exception or client disconnect and visibly rendered:

- Source A and Source B;
- Transform A and Transform B;
- a three-port Router;
- an Output node;
- five routed connections attached to their ports; and
- the `R006` revision marker.

The server log records the command and page open at `2026-09-06 16:15:11`.
The client log immediately records `Pushed context: Ui`, confirming that the
client switched from gameplay input to its UI input context while the page was
open. At session close, the server recorded:

```text
CANVASUI_CLOSE revision=R006 ... pointerEvents=0 uiUpdates=0 rebuilds=1
commands=0 pointerHz=0.00 uiHz=0.00 avgProcessingMs=0.000
peakProcessingMs=0.000
```

The page was open for approximately five minutes. Zero routed pointer events
and zero interaction-driven UI updates are therefore a measured result, not a
visual guess. The sanitized evidence record is
[`evidence/canvas-ui/R006/client-interaction-diagnosis.json`](../../evidence/canvas-ui/R006/client-interaction-diagnosis.json).

### Why nodes do not move

There are two independent input layers, and R006 does not have a working bridge
through either one.

First, [`CanvasPage`](../../canvas-ui/src/main/java/com/inigmasgames/canvasui/rendering/CanvasPage.java)
extends `CustomUIPage`, not `InteractiveCustomUIPage`. Its `build` method ignores
the supplied `UIEventBuilder`, so the page registers zero CustomUI event
bindings. [`CanvasUIPage.ui`](../../canvas-ui/src/main/resources/Common/UI/Custom/CanvasUIPage.ui)
and the node/port templates contain passive `Group` and `Label` elements rather
than interactive controls. Consequently, the client has no node-specific event
binding to send back to the server.

Second, the implemented drag controller does not use CustomUI events. The
plugin globally subscribes to `PlayerMouseButtonEvent` and
`PlayerMouseMotionEvent`, then
[`CanvasInputController`](../../canvas-ui/src/main/java/com/inigmasgames/canvasui/runtime/CanvasInputController.java)
expects those gameplay/world events to provide screen coordinates and relative
motion. The real R006 session delivered none while the CustomUI page owned the
UI context. Since the controller never receives a press or motion event, hit
testing, drag threshold handling, panning, persistence, and incremental geometry
updates never run.

The state/geometry implementation is not the first failure. The first failure
is input delivery into that implementation.

### Why links cannot be broken

Link removal exists at the model and session API levels:

- `Canvas.removeEdge(edgeId)` removes the model edge and publishes
  `CONNECTION_REMOVED`.
- `CanvasSession.removeEdge(id)` rebuilds the topology and persists the result.

However, no production interaction path calls `CanvasSession.removeEdge`.
`CanvasHitTester` recognizes only ports, node rectangles, and background; it
does not hit-test edge segments. `CanvasInputController` implements creation by
dragging from an output port to a compatible target port, but it contains no
gesture for detaching, rewiring, selecting, or deleting an existing edge.

Therefore link breaking would remain unavailable even if the current global
mouse route began delivering events. It is an unimplemented feature, separate
from the zero-pointer-event blocker.

### Audited Hytale event boundary

The pinned public `CustomUIEventBindingType` enum exposes discrete events such
as `Activating`, `RightClicking`, `DoubleClicking`, `MouseEntered`,
`MouseExited`, `MouseButtonReleased`, `KeyDown`, `Dropped`, and several
inventory-slot drag events. It does not expose a generic mouse-move event,
mouse-down event with coordinates, or pointer-capture event for arbitrary
CustomUI elements.

`UIEventBuilder` binds an event type and selector to a string data map.
`CustomPageEvent` sends an event type plus serialized data. Neither audited
surface exposes continuous pointer coordinates. This agrees with the earlier
Stage 00 finding that node drag, pointer pan, and pointer capture were not
supported by the exposed page event surface and still required real-client
proof. R006 supplied that missing client evidence for the attempted global
mouse workaround: it produced zero events in the UI context.

The strongest current conclusion is therefore:

> The R006 server-only architecture can render a freeform graph, but its chosen
> global gameplay-mouse input route cannot drive that graph while a CustomUI
> page is open. The audited standard CustomUI event API supports discrete UI
> interactions but does not presently provide the continuous coordinate stream
> required for FigJam-style free dragging and panning.

This is a conclusion about the pinned public server API and tested architecture,
not about hypothetical future Hytale APIs or a separately installed client mod.

## Recommended next milestone: input capability probe

The next revision should be a deliberately small feasibility probe, not another
full interaction implementation. Its purpose is to determine exactly which
client-owned CustomUI events are usable before more graph code is written.

Recommended R007 scope:

1. Change the probe page to `InteractiveCustomUIPage` with a typed event payload.
2. Render one node and one port with interactive Button/hit-area elements.
3. Bind and log `Activating`, `RightClicking`, `MouseEntered`, `MouseExited`,
   `MouseButtonReleased`, and applicable `Dropped`/reorder events.
4. Keep independent counters for the global player mouse events so the two
   input routes cannot be confused.
5. Record whether any event contains a dynamic pointer position, relative
   motion, source element, destination element, or only static bound data.
6. Stop at the first parse, binding, or delivery failure and preserve the exact
   client/server log.

Decision after that probe:

- If a continuous coordinate-bearing event is discovered, implement true node
  drag and background pan against it.
- If only source/destination drag-drop is available, consider an explicitly
  grid-snapped canvas. Do not describe it as freeform dragging.
- If only discrete click/hover/key events are available, use a server-only
  fallback: selection, keyboard/button nudging, explicit connect/disconnect,
  and optional viewport pan buttons.
- If FigJam-like free movement is mandatory and no coordinate stream exists,
  the project needs a supported client-extension path or must wait for an API
  addition. A server-only CustomUI mod cannot honestly claim that capability on
  the currently pinned build.

Interactive link removal does not need continuous motion. Once discrete
CustomUI events are proven, a first safe implementation could right-click an
edge hit area, select an edge and press Delete, or expose a Disconnect action
on an input port. Edge hit regions and an input-to-edge lookup must be added;
none exists in R006.

## Revision ledger

### R002 — reusable library foundation

- Added the standalone CanvasUI graph model, node/port definitions, validation,
  persistence contracts, geometry, session isolation, and a separate demo.
- Sixteen headless tests and bare-server plugin startup passed.
- Kept interaction status blocked because mouse delivery during CustomUI was
  not client-proven.

### R003 — deployment and document validation

- Added fail-closed CustomUI document validation and corrected the pre-release
  UI button schema.
- Deployed the library/demo for real-client testing.
- Client entry/document failures showed that server startup alone was
  insufficient evidence for CustomUI compatibility.

### R004 — single CanvasUI JAR

- Bundled the generic demo command into `CanvasUI-0.1.0.jar`, eliminating the
  second demo mod JAR.
- The page command reached the client but `AppendInline` document assembly
  disconnected the client at selector `#CanvasContents`.
- Reasoning: consolidate the public library artifact while retaining the demo
  as public-API-only consumer code.

### R005 — static UI templates

- Replaced dynamic `AppendInline` markup with static `CanvasEdge.ui`,
  `CanvasNode.ui`, and `CanvasPort.ui` templates appended by document path.
- Added a verification gate that rejects any reintroduction of `AppendInline`
  in CanvasUI.
- The client no longer disconnected, but command construction failed because a
  direct `Value<Anchor>` was sent through a reference-only setter overload.

### R006 — rendering milestone and input diagnosis

- Changed every Anchor command update to Hytale's registered object codec via
  `setObject` and added a gate against direct `Value.of(...)` setter misuse.
- Proved the full initial demo topology renders in the real client.
- Measured zero pointer events and zero interaction UI updates during the live
  session.
- Confirmed that existing-edge removal has no interaction path.
- Outcome: rendering milestone achieved; FigJam-style input remains blocked.

## Evidence and reproducibility

- Per-revision verification, installation, and smoke records:
  [`evidence/canvas-ui/`](../../evidence/canvas-ui/).
- R006 interaction diagnosis:
  [`client-interaction-diagnosis.json`](../../evidence/canvas-ui/R006/client-interaction-diagnosis.json).
- Original Stage 00 feasibility report:
  [`docs/phase-00/phase-00-report.md`](../phase-00/phase-00-report.md).
- R002 client checklist:
  [`client-verification-R002.md`](client-verification-R002.md).

The smoke tests prove plugin discovery/setup/enable and absence of
plugin-scoped startup exceptions. They do not simulate a client pointer or
prove interaction. Real-client claims in this report are based only on the
R006 screenshot and the matching client/server session logs.
