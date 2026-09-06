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

- CanvasUI version: `0.1.0`, development revision `R008`.
- Hytale target: `0.7.0-pre.1`, revision
  `e8b4d191fc98a977bf5546a951a7b25473d323e3`.
- Branch: `RPG`.
- R008 implementation commit: `82cf726c393e54541b193503f758038b9515345e`.
- R008 CanvasUI JAR SHA-256:
  `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6`.
- Deployed mod set: `CanvasUI-0.1.0.jar`, `HYTALEDEVLIB-0.5.0.jar`, and
  `HytaleRPG-0.0.2.jar`; the demo remains bundled in CanvasUI.
- Rendering gate: **PROVEN**.
- Custom page lifecycle/open gate: **PROVEN**.
- Node dragging and canvas panning: **BLOCKED by the audited public input surface**.
- Zoom model/rendering/persistence: **IMPLEMENTED AND HEADLESS-PROVEN**.
- Text search/highlighting: **IMPLEMENTED; R008 CLIENT CONFIRMATION PENDING**.
- Right-click link removal: **IMPLEMENTED; R008 CLIENT CONFIRMATION PENDING**.
- Production Link Tree approval: **NOT APPROVED**.

R006 remains the first rendering milestone because it proves that a generic graph with
multiple node types, ports, and attached orthogonal connections can be rendered
by a standalone CanvasUI JAR in the real client. It is not yet a FigJam-like
canvas: the displayed graph is presently passive.

## R008 event-binding correction

R008 is deployed specifically to correct the two R007 client failures reported
on 2026-09-06. The failure evidence is preserved in
[`client-event-binding-failures.json`](../../evidence/canvas-ui/R007/client-event-binding-failures.json).

The input-probe failure was exact and deterministic:

```text
Failed to apply CustomUI event bindings
Target element in CustomUI event binding has no compatible
MouseButtonReleased event. Selector: #ProbeBackground
```

R007 bound every candidate event to every Button. Hytale validates event/control
compatibility and rejected `MouseButtonReleased` on the background Button. The
five bindings placed before it—Activating, RightClicking, DoubleClicking,
MouseEntered, and MouseExited—were accepted. R008 keeps those accepted Button
bindings and removes MouseButtonReleased, KeyDown, Dropped, DragCancelled, and
ElementReordered from incompatible generic controls. This is a narrowed,
evidence-based probe, not a claim that the removed event types do not work on
their specialized controls.

The production-demo failure was also exact:

```text
Failed to gather CustomUI event binding data
Could not gather property value for CustomUI event binding. Key: @Event
```

Hytale uses ordinary EventData keys such as `Type` for static data. A key with
an `@` prefix is a request to gather a dynamic property from the selector named
by its value, as in `@Scale -> #Scale.Value`. R007 incorrectly used `@Event`,
`@TargetKind`, and `@TargetId` for static identity, causing the client to treat
`RightClicking` or similar literal values as property selectors. R008 changes
those keys to `Event`, `TargetKind`, and `TargetId`; only actual control values
retain the `@` prefix. Text values use `Codec.STRING`, Slider values use
`Codec.INTEGER`, and FloatSlider/zoom values use `Codec.FLOAT`, matching the
pinned server's own `ChangeModelPage` pattern.

The later desktop exception—`Collection was modified; enumeration operation
may not execute`—was logged after the binding-data failure, player disconnect,
embedded-server shutdown, and a new UI-context push. No CanvasUI CustomUI event
reached the server (`pointerEvents=0`, `uiUpdates=0`, `rebuilds=1`,
`commands=0`). The timing therefore identifies it as a secondary client cleanup
failure, not evidence of a Canvas graph collection being mutated during server
enumeration. R008 prevents the initiating bad payload.

R008 also adds a verification gate that rejects future static Event/Target keys
using the dynamic `@` prefix, and closes the confirmation modal after a
successful disconnect. Twenty-one tests, all CustomUI static checks, artifact
inspection, and bare-server setup/enable pass. The exact deployed set remains
three JARs. `HytaleRPG-0.0.2.jar` now has SHA-256
`F683A81D4B3F60CCD60005059888DB98ACA1433904416EC1126A1803A8E44167`.
R008 real-client confirmation is pending.

## R007 capability milestone

R007 converts the production CanvasUI page to
`InteractiveCustomUIPage<CanvasPage.Data>` and adds a separate
`/canvasui-input-probe`. The probe logs the raw serialized client payload before
typed decoding, then logs independent counters and rates per event type. It
contains interactive background, node, port, and edge hit areas, TextField,
Slider, FloatSlider, and a full-height `TopScrolling` surface. Interactive
buttons are intrinsically hit-testable; the installed CustomUI documents do not
expose a `HitTestVisible` property to copy safely, so R007 does not invent one.

The production demo now includes:

- wide transparent Button hit regions over thin rendered edge segments;
- `RightClicking` bindings carrying a static edge ID;
- a `Disconnect this link?` Yes/No dialog whose Yes path calls
  `CanvasSession.removeEdge`, rebuilds, and persists;
- a TextField `ValueChanged` binding with interface locking disabled;
- locale-stable substring search over `searchName`, `searchDescription`, and
  `searchTags`, with match count, strong matching color, and dimmed nonmatches;
- zoom in `CanvasViewport`, using `screen = canvas * zoom + offset` and its
  inverse, clamped to 0.35–2.00, persisted in snapshot format 2, and backward
  compatible with format 1 at zoom 1.0;
- a FloatSlider zoom fallback centered on the viewport's nominal center. It is
  intentionally not described as mouse-wheel zoom or cursor-centered UI zoom;
  the model itself supports cursor-centered zoom when a future backend supplies
  the cursor coordinate;
- public `CanvasInputBackend`, `CanvasRenderBackend`, and
  `CanvasInputCapabilities` contracts.

Twenty-one tests pass. They cover round-trip transforms at 0.35, 0.5, 0.75,
1.0, 1.5, and 2.0; cursor-point invariance during zoom; bound clamping; format-2
zoom persistence and format-1 migration; non-unit node hit testing, dragging
math and edge anchors; and search by name, description, tag, capitalization,
partial word, empty query, and multiple matches.

### API and installed-widget audit

The pinned `CustomUIEventBindingType` exposes:

```text
Activating, RightClicking, DoubleClicking, MouseEntered, MouseExited,
ValueChanged, ElementReordered, Validating, Dismissing, FocusGained,
FocusLost, KeyDown, MouseButtonReleased, SlotClicking, SlotDoubleClicking,
SlotMouseEntered, SlotMouseExited, DragCancelled, Dropped,
SlotMouseDragCompleted, SlotMouseDragExited,
SlotClickReleaseWhileDragging, SlotClickPressWhileDragging,
SelectedTabChanged
```

It exposes no `Scrolled` event, pointer-move event, pointer capture, wheel
delta, or modifier-key event. `UIEventBuilder` only binds a type, selector,
string EventData map, and interface-lock flag. `CustomPageEvent` contains the
event type plus that serialized map. R007 therefore binds only documented
control values (`TextField.Value`, `Slider.Value`, and `FloatSlider.Value`) and
does not fabricate selectors for pointer coordinates that the API does not
define.

The 168 installed server CustomUI documents were scanned. `Common.ui` defines
TextField, Slider, FloatSlider, SliderNumberField, ColorPicker, and scrollbar
styles; `TopScrolling` is used by shipped pages. ReorderableList,
ReorderableListGrip, and DynamicPane were not found in those installed CustomUI
documents. Slider and FloatSlider expose one scalar Value, which is useful for
a zoom control but not a legitimate two-dimensional freeform drag primitive.
ItemGrid events describe inventory-slot drag semantics and expose no arbitrary
screen position. No production widget abuse was adopted.

The machine-readable audit is
[`evidence/canvas-ui/R007/api-capability-audit.json`](../../evidence/canvas-ui/R007/api-capability-audit.json).
The official pre-release API documents
[`InteractiveCustomUIPage`](https://pre-release.docs.hytale.com/api/com/hypixel/hytale/server/core/entity/entities/player/pages/InteractiveCustomUIPage).
Hytale's own [modding strategy](https://hytale.com/news/2025/11/hytale-modding-strategy-and-status)
states that its UI frameworks are being consolidated onto NoesisGUI. CanvasUI's
backend seams are intended to survive that transition rather than make today's
server markup permanent.

### Definitive capability matrix for the pinned public API

| Behavior | Current status | Evidence and decision |
|---|---|---|
| Free node drag | **BLOCKED** | No coordinate-bearing pointer move or capture; R006's global gameplay route delivered zero events in UI context. |
| Left-background pan | **BLOCKED** | Requires the same missing pointer down/move/up/capture stream. |
| Middle-mouse pan | **BLOCKED** | CustomUI exposes neither a middle-button payload nor continuous move. |
| Wheel zoom | **BLOCKED AT INPUT** | No `Scrolled`/wheel binding or wheel delta exists in the pinned enum. Zoom model/render/persistence are implemented; FloatSlider is a visible fallback, not a wheel backend. |
| Right-click edge disconnect | **IMPLEMENTED, CLIENT-PROVISIONAL** | The R007 client accepted `RightClicking` on Button hit regions, but static payload encoding then failed. R008 corrects it; action confirmation is pending. |
| Ctrl+left-click disconnect | **BLOCKED** | No modifier-key state is exposed; it is not faked or polled. |
| Text search/highlighting | **IMPLEMENTED, CLIENT-PROVISIONAL** | The R007 page accepted the TextField binding, but shared static payload encoding failed when an event fired. R008 corrects it; visual confirmation is pending. |

The matrix is definitive about what the pinned public API contains. The two
implemented UI rows remain client-provisional because server startup cannot
parse or exercise a client-owned page. Run `/canvasui-input-probe`, interact
with every labeled control, close it, then run `/canvasui-demo` and test search,
the zoom slider, and right-clicking a link. Those results will be appended to
this report without rewriting the API conclusion.

### Stage 01 gate and minimum Hytale capability request

Stage 01 is **BLOCKED**. A future supported backend needs, at minimum:

1. pointer down with screen coordinates and button identity;
2. pointer move with screen coordinates or delta;
3. pointer up;
4. pointer capture across element bounds;
5. wheel delta; and
6. modifier-key state.

That stream would be routed with priority `port > node > edge > background`,
with middle drag independently starting pan. Until Hytale exposes it through a
supported Noesis, client-extension, or server API, CanvasUI will preserve the
working graph, rendering, search, disconnect, and zoom model without claiming
FigJam-style free interaction. Modified client binaries will not be patched or
redistributed.

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

## Historical R006 recommendation: input capability probe

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

- Implementation commit: `04834b9096cbd896db20674fc99265728ae01fbd`.
- Evidence commit: `cb5cf2a`.
- CanvasUI JAR SHA-256:
  `653AFEAFA1D55781DC21E1B74FECC6EFEA1EAB00EC98912E5193425148C35D4C`.
- Changed every Anchor command update to Hytale's registered object codec via
  `setObject` and added a gate against direct `Value.of(...)` setter misuse.
- Proved the full initial demo topology renders in the real client.
- Measured zero pointer events and zero interaction UI updates during the live
  session.
- Confirmed that existing-edge removal has no interaction path.
- Outcome: rendering milestone achieved; FigJam-style input remains blocked.

### R007 — capability abstraction, zoom, search, and discrete interaction

- Implementation commit: `60cc3e8f610a9823e302d7d0114227f05c7d3c45`.
- Evidence/report commit: `2ad9b5912035b1467b3553e0984aa0c74e5d5c0c`.
- CanvasUI JAR SHA-256:
  `004FDD78666C045A05497DEA3E693BB4375E56C1DD59B3EF1CA1FFE51EDA698E`.
- Audited the complete pinned CustomUI event enum and installed widget
  templates; found no supported continuous two-dimensional or wheel event.
- Added the typed raw-payload input probe and bundled command.
- Added inspectable input/render backend contracts.
- Implemented and tested zoom transform, rendering geometry, hit testing,
  persistence migration, and a visible FloatSlider fallback.
- Implemented searchable node presentation metadata and live highlighting.
- Implemented wide edge hit areas and right-click confirmation removal.
- Deployed the exact three-mod set and passed 21 tests, CustomUI static
  validation, artifact checks, and bare-server startup.
- Real-client observation: probe rejected MouseButtonReleased on a Button;
  demo event gathering rejected static identity keys incorrectly prefixed with
  `@`; zero CustomUI events reached the server. A later desktop collection
  exception occurred during failure cleanup. These paths are rejected and
  corrected in R008.
- Outcome: Stage 01 remains **BLOCKED** on public continuous pointer input.

### R008 — safe binding matrix and correct EventData semantics

- Implementation commit: `82cf726c393e54541b193503f758038b9515345e`.
- Hytale build: `0.7.0-pre.1`, revision
  `e8b4d191fc98a977bf5546a951a7b25473d323e3`.
- Kept only the five Button event types accepted before the R007 compatibility
  failure and removed specialized events from generic controls.
- Corrected static versus dynamic EventData keys and numeric slider codecs by
  matching Hytale's shipped `ChangeModelPage` implementation.
- Added a regression gate against `@Event`/`@Target*` static keys.
- Deployed CanvasUI SHA-256
  `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6`.
- Automated result: 21 tests pass; CustomUI validation, JAR inspection, and
  bare-server setup/enable pass without CanvasUI-scoped errors.
- Real-client observation/event counts/latency: pending the next user run.
- Outcome: R007 crash trigger corrected; Stage 01 remains **BLOCKED** on the
  separate continuous-pointer capability gap.

## Evidence and reproducibility

- Per-revision verification, installation, and smoke records:
  [`evidence/canvas-ui/`](../../evidence/canvas-ui/).
- R006 interaction diagnosis:
  [`client-interaction-diagnosis.json`](../../evidence/canvas-ui/R006/client-interaction-diagnosis.json).
- R007 verification, installation, startup smoke, and API audit:
  [`evidence/canvas-ui/R007/`](../../evidence/canvas-ui/R007/).
- Original Stage 00 feasibility report:
  [`docs/phase-00/phase-00-report.md`](../phase-00/phase-00-report.md).
- R002 client checklist:
  [`client-verification-R002.md`](client-verification-R002.md).

The smoke tests prove plugin discovery/setup/enable and absence of
plugin-scoped startup exceptions. They do not simulate a client pointer or
prove interaction. Real-client claims in this report are based only on the
R006 screenshot and the matching client/server session logs.
