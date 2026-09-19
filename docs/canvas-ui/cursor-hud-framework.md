# CanvasUI Cursor-HUD Backend
## Implementation framework, research basis, and real-client proof gates

**Prepared:** 2026-09-15  
**Status:** Proposed experimental backend. Not implemented or client-verified by this document.  
**Decision:** Implement a bounded feasibility probe first. Integrate the backend only after the probe passes. The complete original CanvasUI vision remains the acceptance target.  
**Suggested repository destination:** `docs/canvas-ui/cursor-hud-framework.md`

## 1. Engineering recommendation

Test a different input context, not another imitation of dragging inside a CustomUI page:

```text
Existing route:
CanvasSession -> CustomUIPage -> client Ui context
             -> gameplay pointer delivery absent in R006

Proposed route:
CanvasSession -> temporary, passive, keyed CustomUIHud
              + supported cursor-server-camera configuration
              -> mouse events or decoded MouseInteraction packets
              -> normalized canvas input -> existing graph and geometry
```

The proposal is to use Hytale's HUD transport only as the rendering host for the existing canvas. CanvasUI would perform its own hit testing, gesture ownership, dragging, panning, and port targeting using a separately delivered pointer stream. This is not a replacement for the gameplay HUD, a new skill system, or a request to install a client mod.

**Why this merits a probe:** the official camera schema exposes `displayCursor` and `sendMouseMotion`; the pointer protocol contains screen position, button transitions, and motion; Hytale's Update 7 notes distinguish working cursor-camera clicks from intentionally suppressed input behind menus/chat/pages. These are documented ingredients, not proof that their combination will work with a passive HUD on the installed build. [W1–W6]

**Important limitation:** this research did not establish a usable wheel-delta stream, physical modifier-key state, native pointer capture, or native text-field interaction on this HUD route. A successful drag/pan probe would resolve a major blocker, but would not itself satisfy every original requirement. Do not silently substitute buttons, inventory slots, or a separate browser and call the original vision complete.

## 2. Repository baseline and evidence hierarchy

The reviewed `RPG` branch head was `25b85cb2819351a0d727e33132b2f57013cda024`. CanvasUI's canonical report still identifies implementation R008, commit `82cf726c393e54541b193503f758038b9515345e`, targeting Hytale `0.7.0-pre.1`, revision `e8b4d191fc98a977bf5546a951a7b25473d323e3`. R009 in the report is a later RPG-backend milestone; it did not replace R008 CanvasUI. Do not recycle that revision label. [R1]

Use this evidence order:

1. Exact installed client/server build and reproducible client traces.
2. Current checked-out source and canonical development report.
3. Matching official API declarations and official patch notes.
4. Third-party author's source as implementation precedent, not compatibility proof.
5. Proposed mechanisms in this document, which require tests.

The older `canvas-ui/docs/input-and-panning.md` still describes fixed zoom 1.0. That is superseded by the R007/R008 report's zoom transform and snapshot-format-2 support; do not remove zoom to reconcile the documents. [R1, R10]

### What the repository actually proves

R006 rendered the generic graph in a real client but logged zero pointer events during an open CustomUI page. R007/R008 introduced capability reporting and corrected discrete event bindings. Their report does not establish failure of a passive HUD plus deliberately configured cursor-camera session. [R1]

The implementation also needs more than a capability-flag change:

| Existing location | Relevant implementation constraint |
|---|---|
| `CanvasService` | Always opens `CanvasPage` through `openCustomPage`; routes global mouse events without its own world-action suppression. |
| `CanvasSession` | Constructs concrete `HytaleCustomUiBackend`; constructor opens the page; public mutations often render and persist immediately. |
| `CanvasInputController` | Accepts Hytale event classes directly and depends on the concrete page renderer. Raw screen positions are treated as canvas-screen positions. |
| `HytaleCustomUiBackend` | Flushes through `CanvasPage`. Hover changes can rebuild topology. |
| `CanvasInputBackend` | Currently contains only `id()` and `capabilities()`. It is not yet a lifecycle-managed input subscription. |
| `CanvasRenderBackend` | Covers topology, node/edge updates, and viewport updates, but not the full preview/hover/close behavior used by the controller. |

These are source observations, not newly discovered Hytale restrictions. [R3–R8]

## 3. Preserve these contracts

Retain the standalone `canvas-ui` library and its public consumer boundary. It must not acquire HytaleRPG, HTDevLib, HyUI, CameraEditor, browser, or desktop-overlay dependencies. Reuse existing graph definitions, typed ports, connection policy, graph validation, render geometry, persistence interfaces, and session isolation. Consumers continue to own node meaning and gameplay authority. [R2]

Preserve free placement in continuous canvas coordinates, off-center grab offsets, the existing four-unit drag threshold, configured background/middle panning, attached connections, drag-to-connect previews, invalid-target rejection, search metadata, right-click disconnection, and the existing zoom range of 0.35–2.00. Preserve snapshot compatibility and stable IDs. The existing orthogonal edge renderer remains acceptable; new spline rendering is not a prerequisite for investigating input. [R1, R9]

A temporary HUD transport must not restyle resource bars, replace the player's inventory, clear another mod's HUD, or change combat, attributes, XP, or skill rules. Probe persistence must use an isolated demo namespace, never production player builds.

Maintain prior regression protections: static `.ui` templates rather than reintroducing rejected `AppendInline`; object-codec anchor updates through `setObject`; static EventData identity keys without `@`; dynamic `@` keys only for actual supported selector properties. [R1]

## 4. Research findings and alternatives

### 4.1 Cursor-camera input: selected candidate

`ServerCameraSettings` declares cursor display and motion transmission fields. `MouseInteraction` carries `screenPoint`, `mouseButton`, and `mouseMotion`; the nested motion record includes `relativeMotion` and a nullable button array. Neither field names nor their presence prove frequency, coordinate units, targetless delivery, or button-array semantics. [W2–W4]

The current official API also documents keyed HUD layers and HUD command updates. The older project Stage 00 audit already recorded keyed HUD methods, so this is not being presented as a newly invented Update 7 feature. [W5, W6, R11]

CameraEditor's author demonstrates cursor-camera configuration, and its source distinguishes pressed/released states for several mouse buttons. This is precedent for server-camera interaction, not proof of CanvasUI dragging, motion quality, safe world-action interception, or Update 7 compatibility. Do not copy its block-editing behavior. [W10]

### 4.2 Why the other researched routes are not the primary plan

| Route | Finding and decision |
|---|---|
| More generic CustomUI page event bindings | The documented enum still lacks a generic coordinate-bearing move/wheel stream. The R007 incompatible-control failure must not be repeated. [W7, R1] |
| HyUI / HTML-like markup | Its author describes translation into Hytale's native UI system, not a browser executing arbitrary pointer-event JavaScript. It can simplify authoring but does not establish the missing input bridge. [W11] |
| ReorderableList / DynamicPane | Official-authored control documentation describes list reordering and pane resizing. These are not evidence of unrestricted two-dimensional graph dragging. Absence from the old installed examples was not proof that the controls did not exist. [W12] |
| ItemGrid | Slot-based transfer is not a continuous-coordinate graph input contract. Grid snapping would change the vision. [R1] |
| Slider/color-picker pointer encoding | A scalar value or color change is not a demonstrated full press/move/release/capture stream. Do not ship a widget-encoding trick without proving every required semantic; none was established here. [R1, W13] |
| Hytale's own Node Editor | A first-party editor demonstrates client capability, not a public server-plugin embedding API for arbitrary in-game canvases. No such embedding contract was established in this research. [W1] |
| Direct Noesis/client extension | A future backend remains possible, but the modding strategy is not evidence that plugins can currently inject Noesis code-behind or arbitrary client handlers. [W14] |
| External browser or native overlay | Could move interaction outside CustomUI, but changes delivery, installation, and/or the in-game experience. Not authorized as a substitute in this framework. |

## 5. Phase A: prove the input channel before refactoring CanvasUI

**Initial implementation boundary:** one isolated `/canvasui-cursor-probe` command, passive static probe assets, trace instrumentation, reversible camera/HUD ownership, and narrowly scoped gameplay protection. Keep the existing demo route available for comparison. Names in this document are proposed unless explicitly identified as existing APIs.

### A0. Fingerprint and inspect

Record repository commit, dirty state, client/server versions, server JAR hash, protocol identity, Java version, deployed mod set, and actual camera/HUD/input signatures. Follow existing repository deployment policy. Do not upgrade the game or unrelated modules merely because a moving web reference differs from the local SDK.

Inspect the actual dispatch path for `MouseInteraction`, the global mouse events, camera state, and the supported packet adapter. Establish where cancellation occurs relative to gameplay side effects. Check nullable payloads and control-state updates. The audit must identify its source, not infer handler order from an event name.

Verify a supported reset/restore path before applying any camera override. The public `CameraManager` has input-state accessors and `resetCamera`; that does not supply a documented getter for reconstructing any arbitrary previous camera configuration. [W8]

### A1. Three controlled contexts

Run identical gestures in these contexts with the same camera settings and client:

| Context | Purpose |
|---|---|
| Cursor camera, no CustomUI page and no custom HUD | Establish whether the input stream exists at all. |
| Same camera, passive probe HUD only | Determine whether passive HUD rendering preserves that stream. This is the central hypothesis. |
| Same camera, an ordinary CustomUI page open | Negative/control comparison against the original input-context boundary. |

Create a valid camera configuration from the installed SDK's supported patterns, explicitly requesting cursor visibility and motion transmission. Do not invent enum values such as `MouseInputType.None`, leave required fields unset, or assume that disabling movement also preserves pointer emission. Vary only the necessary options and log them.

For each context, record raw observed fields and independent counters for packet delivery and high-level event delivery. Test pointer movement without a button; left, middle, and right press/move/release; stationary holds; fast motion; and overlapping button presses. Test with empty hands, equipped weapons, and the cursor pointing at sky/empty space, blocks, and entities. Cursor coordinates must not depend on a valid world target.

### A2. Coordinate and visible-marker proof

Render known markers at corners and center of a fixed probe rectangle. Draw a separate diagnostic marker at the decoded pointer position. Compare actual cursor placement with that marker, including screenshots or video and logged samples.

Determine whether `screenPoint` is normalized, physical pixels, or another space; where its origin lies; how UI scaling, viewport size, aspect ratio, and window resizing affect it; and whether relative motion uses the same units. Never read the server machine's desktop resolution as though it described a remote client.

A manual multi-point calibration can diagnose the mapping. It is not the finished solution. Production requires a reliable mapping across supported resolutions and UI scales, and an observed way to invalidate it when those change. No invented `#Root.Width` event property or undocumented viewport packet is permitted.

### A3. Small gesture proof

Only after A1/A2 work, render one draggable rectangle, one attached segment, and a fixed target port. Prove continuous following, preservation of grab offset, release at the actual endpoint, and complete restoration on close. Do not integrate the whole graph first.

**Stop condition:** if passive-HUD input is still absent, target-dependent, unmappable, unsafe, or cannot reliably terminate a gesture, publish the trace and mark this backend blocked. One bounded event-versus-packet comparison is justified; another graph rewrite is not. Headless tests cannot satisfy this gate.

## 6. Phase B: minimal integration into the existing library

After Phase A passes, introduce a host/input selection seam without breaking existing consumers. Keep the experimental backend opt-in until later gates pass. Prefer a new overload or internal factory over silently changing `CanvasService.open` for all callers.

Suggested responsibilities, not mandatory class proliferation:

| Proposed component | Responsibility |
|---|---|
| `CanvasHost` / command sink | Attach, flush geometry, and close through either the existing page or a keyed HUD. No graph policy. |
| `HytaleCursorInputBackend` | Acquire one supported pointer route, normalize samples, report evidence-backed capabilities, and detach. |
| `CanvasPointerSample` | Immutable session-scoped input with explicit validity and coordinate-space metadata. |
| `CanvasViewportMapping` | Convert delivered coordinates into logical canvas-content coordinates. |
| `CanvasInteractionLease` | Own camera override, named HUD, gameplay guard, and cleanup for one player/session. |
| Existing input controller | Consume normalized samples; retain existing drag/pan/connection semantics without depending on Hytale event classes. |

Move shared geometry-command assembly out of its dependence on `CanvasPage`; keep page-specific text and event binding in the page host. Extend the render boundary narrowly for preview, hover, status, and lifecycle rather than duplicating the entire renderer.

Use a dedicated passive HUD template. Do not blindly reuse `CanvasUIPage.ui`, which contains native text, slider, and button controls. Initially draw groups, labels, and geometry, with hit regions held in the server-side canvas model. If a native control is later introduced, retest whether it captures focus or suppresses the cursor-camera stream. [R12]

The documented keyed API is `HudManager.addCustomHud(ref, hud)` and `removeCustomHud(ref, key)`; `CustomUIHud.update(clear, commands)` provides updates. Compile against the actual local signatures. Own a unique CanvasUI key and remove only that key. Never call a global HUD/UI reset to close this canvas. [W5, W6]

## 7. Input transport, ordering, and authority

Choose exactly one authoritative gesture source per session:

**Event path:** use `PlayerMouseButtonEvent` and `PlayerMouseMotionEvent` only if the installed dispatch path delivers all required samples and supports cancellation before relevant gameplay effects.

**Packet path:** otherwise, where supported, use the public `PacketAdapters` API on already-decoded `MouseInteraction` objects. The official `PlayerPacketFilter` contract says true cancels and false defers. This is not permission to invent packet IDs, patch codecs, or read private client memory. A filter cannot recover packets the client never emitted. [W9]

A probe may observe both paths, but the production controller must not process both. Early packet cancellation may prevent high-level events or camera bookkeeping from running. In that case maintain CanvasUI's own observed button state; do not rely on stale `CameraManager` state as an independent confirmation channel.

A normalized sample should carry server-observed sequence, session epoch, arrival timestamp, event kind, optional logical position/delta, explicit button transition, and any genuinely observed button snapshot. Mark absent data unknown, not zero. Physical modifiers and wheel delta remain unavailable unless separately evidenced.

At the network boundary, copy only bounded immutable values and enqueue work to the appropriate world/session executor. Do not access mutable graph/ECS/UI state on an IO callback. Validate finite coordinates and limits; reject mismatched player/session epochs. Client-supplied position or node identity must never authorize a gameplay change.

Bound queues. Coalesce redundant motion samples between button transitions, but preserve press/release/cancel ordering and the final endpoint. Sequence assignment is server receipt order, not proof of the client's original event ordering. On overflow or irreconcilable button state, cancel safely rather than continuing with a guessed gesture.

## 8. Coordinate math and gestures

Separate delivery coordinates, logical content coordinates, and persistent canvas coordinates:

```text
u = deliveryToLogical(rawScreenPoint) - contentOrigin
c = (u - viewportOffset) / zoom
u = c * zoom + viewportOffset
```

`deliveryToLogical` is established by Phase A, not assumed to be identity. If only deltas are usable, their scaling and absolute starting position must be proven; do not accumulate deltas forever without a reliable re-anchor.

For node dragging:

```text
grabOffsetCanvas = inverseViewport(pressPoint) - nodeStartPosition
nodePreviewPosition = inverseViewport(currentPoint) - grabOffsetCanvas
```

Preserve the existing threshold behavior, with an explicitly documented logical-screen interpretation. A click below threshold selects without moving. A drag preserves the exact grab point at every supported zoom.

For panning, derive logical displacement from consecutive mapped pointer positions whenever possible. Update viewport offset only; never alter persistent node coordinates as a substitute. Keep the existing configured pan gestures and the report's independent middle-pan intent. Toolbar/modal hit regions take priority; canvas hits resolve port, node, edge, then background. [R1, R9]

Connection previews use current mapped coordinates, typed source/target ports, and existing policy validation. Revalidate on release against current graph state. Invalid releases create no edge. Right-click edge testing must use visible edge geometry with a sensible logical-screen tolerance, including after pan/zoom, rather than a screen-sized transparent native Button.

Transient previews must not become authoritative gameplay state. Prefer committing the final layout mutation on valid release. If existing code temporarily moves the model during a drag, keep a start-state transaction and restore it on cancellation before any persistence or externally meaningful commit event. Structural changes that invalidate the active node/port must cancel the gesture.

## 9. Logical capture, interruption, and safe closing

Server-side gesture ownership can continue across canvas element boundaries, but that is not native OS/client pointer capture. Report these separately. Do not set `supportsPointerCapture=true` merely because a Java field retains the dragged node ID.

Maintain explicit states such as preparing, ready, dragging, panning, connecting, suspended, and closing. Only a fresh, valid press in an armed session starts a gesture. A release must match its owning gesture; stale releases after reopening must do nothing.

Test release outside the node, outside the canvas but inside the game window, and outside the application after focus loss. Test opening chat, inventory, pause, and another custom page during a gesture. Test death, disconnect, world transition, and a competing camera owner. A missing release must never leave a permanent drag or accidentally commit on the next click.

Use verified context/lifecycle signals and verified button-snapshot semantics where available. The motion array's name alone does not prove it is a complete held-button snapshot. Likewise, silence does not distinguish a stationary hold from a lost focus event. An inactivity timeout may be a conservative recovery mechanism, not proof of capture or focus detection. Unknown state requires cancellation/re-arming, not an invented release coordinate.

**Escape is also an acceptance gate.** The page previously supplied Escape dismissal; a passive HUD does not inherit that behavior. The initial probe must offer a visible close hit region and an administrative emergency-close path. Restore the original Escape behavior only through a verified supported signal. Do not label the full interaction experience complete without it.

## 10. Gameplay isolation and camera/HUD ownership

Because the proposed input route belongs to gameplay, preventing click-through is mandatory before testing near real world content.

Install a session-scoped guard before activating the cursor camera. Prove that left/right/middle gestures do not attack, cast, mine, place, use, pick blocks, or alter equipped slots underneath the canvas. Check both empty-hand and item-specific interaction routes. Handle necessary ability/hotbar suppression through existing supported server boundaries, not a blanket network blackout. Do not discard movement replication, teleport acknowledgments, or unrelated packets indiscriminately.

Use supported camera/movement controls only after verifying their effect on both pointer delivery and player motion/look. An opaque background or hidden held-item model does not prevent gameplay actions. Do not grant invulnerability or change global gameplay rules to conceal this problem.

Camera ownership is a lease, not unconditional reset-on-close. Capture an authoritative known prior state where available. If another system owns a camera and its configuration cannot be safely restored, refuse the session or require explicit coordination. Do not invent a camera getter. An observed outbound camera stream may support bookkeeping, but it cannot reconstruct an unknown override from before observation began.

Associate every owned resource with a session epoch. Do not overwrite a newer camera owner during stale cleanup. Retain only CanvasUI's HUD key. Opening failures must release all partially acquired resources.

Closing must invalidate queued work, cancel/rollback any active gesture, remove the owned HUD, restore/release camera and temporary controls, release the gameplay guard without leaking the closing click, and deregister the input route. Every step needs independent exception-safe cleanup. A save failure must not prevent camera/HUD restoration; the existing close path currently persists before subsequent cleanup. [R5]

Test held-button transitions during entry/exit. World actions must not resume from a press consumed by the canvas. Use a bounded, verified release barrier where necessary; do not leave a player's controls blocked indefinitely.

## 11. Search, zoom, and unresolved input requirements

The following matrix is the starting status of this proposal, not a test result:

| Capability | Planned route | Initial status |
|---|---|---|
| Free node drag and off-center grab | Cursor stream plus existing drag math | Unproven; Phase A gate |
| Background / middle pan | Same stream, independent pan state | Unproven |
| Port-drag preview and connection | Logical hit testing and existing policy | Unproven |
| Right-click disconnect | Pointer button plus edge hit testing and confirmation | Unproven on new host |
| Cursor-centered zoom math | Reuse current transform | Existing headless coverage; new host pending |
| Mouse-wheel zoom | Needs an actual wheel input source | No supported route established by this research |
| Physical Ctrl/Shift/Alt gestures | Needs actual modifier state | No supported route established by this research |
| Native pointer capture | Needs supported capture semantics | Not supplied by server-held gesture state |
| Search/text entry | Existing native text page, with explicit suspension | Modal technique proposed; inline parity unproven |
| Escape closure and interruption safety | Needs verified lifecycle/input signal | Unproven on HUD host |

For early zoom testing, retain an explicitly labeled slider/control path, implemented with the proven pointer route or in a suspended native page. It tests zoom, not wheel support.

For early text testing, cancel any gesture, suspend canvas pointer handling, open a small native search page, reuse the existing substring-search model, then close it and require a fresh press before resuming. This intentionally switches input contexts. It must not be reported as native inline HUD text input or as proof of unchanged search UX. Keep inline search parity open until demonstrated.

Audit the installed schemas for a genuine wheel/modifier channel once, recording exact fields and real packets. Do not infer wheel input from hotbar-slot changes, or physical Ctrl/Shift from crouch/sprint actions, which are different semantics. The current reviewed mouse records do not establish either channel. [W3, W4, W7]

If no supported channel exists, preserve those requirements as blocked. A user-approved interaction change or future supported backend would be a separate decision, not an implicit outcome of this implementation.

## 12. Responsiveness and update budget

Keep model input processing separate from UI transmission. Reuse the existing 10 Hz geometry limit for the first comparison; that interval is 100 ms before considering network transit or client presentation. It cannot justify a claim of native 60-fps dragging. The current metrics measure server processing, not input-to-photon latency. [R2, R5]

During a gesture, update only the moved node, its attached edges, and affected preview/highlight properties. Avoid clearing and rebuilding topology on each motion or hover transition. On release/cancel, always flush the final state even when the throttler would suppress an intermediate update. Check child port anchors, sizes, text, clipping, and edge hits at every zoom, not just root-node positions.

After correctness, compare 10 Hz with experimentally bounded higher rates such as 20 and 30 Hz behind a development setting. These are test candidates, not promised performance. Record event arrival rate, UI update rate, commands/bytes, queue depth, processing percentiles, actual RTT, and observed visual lag. Test local play and a separate remote client; local-only success does not establish multiplayer usability.

Do not add smoothing that merely conceals a lagging authoritative position. Any interpolation proposal must have a real supported client-side mechanism; server-side interpolation is not client prediction. Owner approval of recorded dragging quality remains a separate gate.

## 13. Verification gates and delivery stages

The sequence prevents large speculative implementation before the key uncertainty is resolved.

| Gate | Required evidence | Allowed next work |
|---|---|---|
| G0: local API and restoration audit | Build fingerprints, exact signatures, dispatch/cancellation analysis, owned-resource plan | Isolated guarded probe |
| G1: passive-HUD input | Real press/move/release trace with no page, all needed buttons, targetless motion, marker alignment | Small one-node gesture proof |
| G2: mapping and gesture ownership | Grab offsets, zoom/pan transforms, final release, interruption/cancel behavior on multiple display settings | Narrow host/input integration |
| G3: safety and lifecycle | No world actions, restored controls/camera/HUD, failure cleanup, repeated open/close, no stale queues | Full generic demo on new host |
| G4: original core interactions | Existing topology tests, dragging/panning/connecting/rejection/disconnection, persistence, two clients | Performance and UX verification |
| G5: responsiveness | Measured budgets plus real local/remote-client recordings and owner review | Candidate backend assessment |
| G6: full vision | Wheel/modifiers/capture-equivalent safety/Escape/search parity and every retained acceptance requirement evidenced | Consider production Link Tree approval |

G1–G5 passing is a meaningful core-canvas result. It does not make G6 pass. A requirement can be changed only by an explicit owner decision, recorded separately from implementation evidence.

Add headless regression tests for transform round trips at 0.35, 0.5, 0.75, 1, 1.5, and 2; threshold/grab behavior; transient rollback; stale epochs; duplicate releases; motion coalescing; final flush; port invalidation; connection policy; and cleanup when persistence/rendering throws. These supplement, not replace, connected-client tests.

Port the existing R002 real-client checklist to the new host rather than replacing it. Include ten open/close cycles, seeded source/transform/router/output graphs, allowed and rejected connections, router-to-router paths, save/reopen, two-player isolation, disconnect/world transition mid-gesture, and visible revision markers. Add the new wheel, text, resolution, safety, and performance gates. [R9]

## 14. Evidence, rollback, and Codex handoff

Store evidence under `evidence/canvas-ui/cursor-hud/<actual-revision>/`, including build identity, camera settings, capability audit, sanitized input trace, context-comparison results, test output, metrics, and client recordings. Avoid account tokens, private paths, or unnecessarily persistent player identifiers in shared traces.

After each actual development revision, append the canonical development report using its existing distinction between proven, implemented-but-unproven, and unavailable-in-audited-API. State what was not run. No screenshots, timing values, or success counters may be synthesized.

Retain a switch back to the unchanged page backend. Rollback removes only the experimental input registration, camera/HUD ownership, and probe deployment; it must not delete other mods or overwrite production snapshots. Build/deploy only through the project's existing policy, and never replace a loaded JAR while the save is running.

### Codex execution instruction

> Read this framework and the current canonical CanvasUI report, then inspect the local source, SDK, and deployment policy. Implement Phase A only first: a reversible cursor-camera/passive-HUD input probe with gameplay protection and independent packet/event diagnostics. Preserve the existing CanvasUI implementation and all gameplay HUD behavior. Do not open a CustomUI page during the positive input test. Prove pointer delivery and mapping in the real client before integrating a new host. If a connected client is unavailable, stop at an explicitly pending client gate and provide exact run instructions. After G1/G2 evidence passes, make the narrow host/input changes described here, reuse the graph model, and progress through the safety and existing interaction tests. Keep wheel, modifiers, Escape, capture semantics, text parity, and network responsiveness separately tracked. Do not declare the original CanvasUI vision complete from headless tests or a partial drag demo.

## 15. Source register

### Repository sources

All R references are files inspected on `Graham3D/Hytale`, branch `RPG`, at the reviewed branch state identified above. Re-resolve local source before implementation. Repository URL: `https://github.com/Graham3D/Hytale/tree/RPG`.

- **R1:** `docs/canvas-ui/development-report.md`. R006–R009 evidence, binding failures, capability audit, zoom/persistence, and approval state.
- **R2:** `canvas-ui/README.md`. Standalone library contracts, artifacts, limits, and public API.
- **R3:** `canvas-ui/src/main/java/com/inigmasgames/canvasui/runtime/CanvasService.java`. Session creation and unconditional page hosting.
- **R4:** `canvas-ui/src/main/java/com/inigmasgames/canvasui/runtime/CanvasInputController.java`. Event coupling and gesture implementation.
- **R5:** `canvas-ui/src/main/java/com/inigmasgames/canvasui/runtime/CanvasSession.java`. Renderer construction, mutation/persistence, rate limiting, and close sequence.
- **R6:** `canvas-ui/src/main/java/com/inigmasgames/canvasui/rendering/HytaleCustomUiBackend.java` (inspected lines 1–160). Page sink, topology rebuilds, hover, and geometry updates.
- **R7:** `canvas-ui/src/main/java/com/inigmasgames/canvasui/api/CanvasInputBackend.java` and `CanvasInputCapabilities.java`. Actual capability contract.
- **R8:** `canvas-ui/src/main/java/com/inigmasgames/canvasui/api/CanvasRenderBackend.java`. Current renderer interface.
- **R9:** `docs/canvas-ui/client-verification-R002.md`. Original connected-client interaction acceptance checklist.
- **R10:** `canvas-ui/docs/input-and-panning.md`. Older fixed-zoom documentation; historical rather than current zoom authority.
- **R11:** `docs/phase-00/phase-00-report.md`. Earlier installed API audit, including keyed HUDs and page-input limitations.
- **R12:** `canvas-ui/src/main/resources/Common/UI/Custom/CanvasUIPage.ui`. Existing native text/slider/button-containing page template.

### External primary sources

Accessed 2026-09-15. Official API pages are moving references and may not match the installed build. Stable and pre-release API hosts are intentionally identified; declarations are not client test results.

**W1: Hytale Team, Update 7 pre-release patch notes.** Cursor-camera fixes, suppression behind menus/chat/pages, and first-party Node Editor changes. The cursor fixes occur in the Part 1 material; they are not evidence of a new Part 2-only solution.
`https://hytale.com/news/2026/9/pre-release-patch-notes-update-7`

**W2: Official pre-release ServerCameraSettings API.**
`https://pre-release.docs.hytale.com/api/com/hypixel/hytale/protocol/ServerCameraSettings`

**W3: Official pre-release MouseInteraction API.**
`https://pre-release.docs.hytale.com/api/com/hypixel/hytale/protocol/packets/player/MouseInteraction`

**W4: Official mouse input types and events.**
`https://pre-release.docs.hytale.com/api/com/hypixel/hytale/protocol/MouseMotionEvent`
`https://pre-release.docs.hytale.com/api/com/hypixel/hytale/protocol/MouseButtonEvent`
`https://pre-release.docs.hytale.com/api/com/hypixel/hytale/protocol/MouseButtonType`
`https://pre-release.docs.hytale.com/api/com/hypixel/hytale/server/core/event/events/player/PlayerMouseButtonEvent`
`https://docs.hytale.com/api/com/hypixel/hytale/server/core/event/events/player/PlayerMouseMotionEvent`

**W5: Official pre-release CustomUIHud API.**
`https://pre-release.docs.hytale.com/api/com/hypixel/hytale/server/core/entity/entities/player/hud/CustomUIHud`

**W6: Official pre-release HudManager API.**
`https://pre-release.docs.hytale.com/api/com/hypixel/hytale/server/core/entity/entities/player/hud/HudManager`

**W7: Official pre-release CustomUIEventBindingType API.**
`https://pre-release.docs.hytale.com/api/com/hypixel/hytale/protocol/packets/interface_/CustomUIEventBindingType`

**W8: Official pre-release CameraManager API.**
`https://pre-release.docs.hytale.com/api/com/hypixel/hytale/server/core/entity/entities/player/CameraManager`

**W9: Official stable PacketAdapters and PlayerPacketFilter APIs.** Local pre-release compatibility must be inspected rather than assumed.
`https://docs.hytale.com/api/com/hypixel/hytale/server/core/io/adapter/PacketAdapters`
`https://docs.hytale.com/api/com/hypixel/hytale/server/core/io/adapter/PlayerPacketFilter`

**W10: CameraEditor author's project and source.** External repository head inspected: `5cef40bffb048c3e8e86118d5eca0d2269ab1f36`. Precedent only, not a required dependency.
`https://www.curseforge.com/hytale/mods/camera-editor`
`https://github.com/NeoWaffleSpy/CameraEditor/blob/5cef40bffb048c3e8e86118d5eca0d2269ab1f36/src/main/java/com/Varrell/CameraEditor/Camera/MouseControl/DefaultMouseControl.java`

**W11: HyUI author's project/documentation.**
`https://www.curseforge.com/hytale/mods/hyui`
`https://hyui.gitbook.io/docs/home/hyuiml-htmlish-in-hytale/hyuiml-elements`

**W12: Hypixel Studios-authored Custom UI control documentation, hosted by HytaleModding.** These describe controls, not guaranteed server event-binding payloads.
`https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/elements/reorderablelist`
`https://hytalemodding.dev/pt-BR/docs/official-documentation/custom-ui/type-documentation/elements/dynamicpane`

**W13: Official-authored ColorPicker control documentation, hosted by HytaleModding.**
`https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/elements/colorpicker`

**W14: Hytale Team, modding strategy and status.** Architectural direction, not a current arbitrary client-extension API guarantee.
`https://hytale.com/news/2025/11/hytale-modding-strategy-and-status`

---

**Current outcome:** a source-grounded, bounded path to test the core drag/pan blocker, with explicit remaining full-vision gaps. No new Hytale client results, code deployment, or production approval are claimed.

