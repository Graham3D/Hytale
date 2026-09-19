# R042 CanvasUI Cursor-HUD Phase A.5 / Phase B Entry Report

**Prepared:** 2026-09-19  
**Branch:** `RPG`  
**Source checkpoint:** `25b85cb2819351a0d727e33132b2f57013cda024` plus the preserved cumulative working tree  
**Hytale:** `0.7.0-pre.2`  
**Status:** IMPLEMENTED, PACKAGED, DEPLOYED, CONNECTED-VERIFIED = **NO**

## 1. Outcome

R042 converts the R041 passive-HUD pointer proof into a bounded candidate input
backend. It adds one coordinate transform, a session-scoped native gameplay
guard, a preserved CustomUI-page negative control, and a two-node drag proof
which reuses the existing Canvas model, `CanvasInputController`, viewport,
hit-testing, edge geometry, and persistence callback.

R042 does **not** migrate `/canvasui-demo`, the RPG Link Tree, search, wheel
zoom, modifiers, or the complete graph editor. It does not declare the new
backend production-ready. Those claims require the connected checklist in
section 12.

## 2. Evidence baseline and raw coordinate status

The connected R041 session remains the authoritative input baseline:

- no-HUD context: 1,099 high-level pointer events;
- passive-HUD context: 798 events in the first run and 3,943 in the final run;
- packet-adapter pointer count: zero in those runs;
- cleanup: PASS;
- native `PlaceBlockInteraction` leaked while the passive HUD owned input;
- observed `screenPoint` values looked normalized, including samples near
  `(-0.33, -0.28)`, but the exact raw domain was not established;
- the page negative control was not run.

The installed pre.2 server API exposes `MouseInteraction.screenPoint`, but the
audited server declarations do not expose the remote client's physical viewport
dimensions or logical UI scale. R042 therefore does not assert a fixed `[-1,1]`
contract and does not read the server desktop. It obtains a per-player affine
mapping from five logical landmarks: TL, TR, BL, BR, and CENTER. The fitted
mapping survives physical resolution and UI-scale changes because both are
absorbed by the observed raw bounds. It is retained only in memory and must be
recaptured after a plugin restart.

Connected status of the raw domain: **NOT YET MEASURED ON R042**.

## 3. Authoritative coordinate pipeline

`CanvasPointerTransform` is the only conversion path used by the marker, close
region, graph hit testing, and drag controller.

For captured raw samples `TL`, `TR`, `BL`, and `BR`:

```text
rawLeft   = average(TL.x, BL.x)
rawRight  = average(TR.x, BR.x)
rawTop    = average(TL.y, TR.y)
rawBottom = average(BL.y, BR.y)

normalizedX = (rawX - rawLeft) / (rawRight - rawLeft)
normalizedY = (rawY - rawTop)  / (rawBottom - rawTop)

viewportX = 60  + normalizedX * 830
viewportY = 116 + normalizedY * 470

canvasLocal = viewport - (60, 116)
canvasWorld = CanvasViewport.toCanvas(canvasLocal)
```

This formula naturally supports an inverted raw Y axis. The four corners fit
first; CENTER is an independent acceptance check. A mapping is rejected above
8 logical pixels maximum corner error or 5 logical pixels center error. The HUD
shows raw, normalized, logical viewport, local coordinates, mapping state, and
the next calibration target.

The installed API did not provide a trustworthy physical-viewport/UI-scale
source, so the report deliberately records both as `UNAVAILABLE`. R042's
logical target is the actual 900x660 HUD surface and its 830x470 canvas region,
not 1920x1080.

Automated synthetic mappings passed at 1280x720 scale 1.0, 1920x1080 scale
1.25, and 2560x1440 scale 1.5 with zero mathematical landmark error. These are
transform tests, not connected rendering proof.

## 4. Native input ownership audit

The exact installed bytecode was inspected before implementation. In pre.2,
`InteractionManager.executeChain0` emits `InteractionChainStartEvent` before
the first native operation tick. `InteractionManager.cancelChains(chain)` marks
the client and server chains failed and sends cancellation recursively. The
installed `InventoryActiveSlotRequestEvent`, `DropItemEvent`, and
`PlayerInteractEvent` are cancellable.

| Action | Observed/authoritative route | R042 interception |
|---|---|---|
| Pointer move/button | `MouseInteraction` and high-level player mouse events | Observed and routed to Canvas; high-level event cancelled without suppressing Canvas routing |
| Attack, mining, placement, primary/secondary item use | Primary/Secondary/Use/Pick interaction chain | `InteractionChainStartEvent`, then `cancelChains` before first operation tick |
| Entity/block interaction | Use/Primary/Secondary chain plus `PlayerInteractEvent` | Start-chain cancellation plus cancellable event safety layer |
| Ability activation | Ability1, Ability2, Ability3, Ability4 chains | Start-chain cancellation |
| Dodge | Dodge chain | Start-chain cancellation |
| Client hotbar change | `InventoryActiveSlotRequestEvent`; packet fallback for `SetActiveSlot`/`SwitchHotbarBlockSet` | Cancellable ECS request plus session-scoped packet fallback |
| Item drop | `DropItemEvent`; packet fallback for `DropItemStack` | Cancellable ECS request plus session-scoped packet fallback |
| Native inventory drag | No separate safe pre.2 route was established for a passive HUD with no inventory page | Must remain absent/blocked in connected QA; not claimed independently proven |

The guard is keyed by player UUID and an owner token. A replacement session
cannot be released by a stale owner. Telemetry is downstream from cancellation;
an exception or full telemetry queue cannot prevent the action from being
cancelled.

Diagnostics now distinguish pointer samples, gameplay actions observed,
guarded and allowed, motion-queue drops, and trace drops. The previous
misleading generic `PACKET 0 / GUARDED 0` display is gone. Trace records name
the actual boundary, such as `INTERACTION_CHAIN_START`,
`INVENTORY_ACTIVE_SLOT_REQUEST`, `DROP_ITEM_EVENT`, or
`INBOUND_PACKET_FILTER`.

## 5. Session and camera lifecycle

All positive contexts use the same explicit custom camera contract:

- cursor displayed;
- reticle hidden;
- mouse motion sent;
- world mouse target disabled;
- first-person disabled;
- top-down custom rotation;
- no passive-HUD operation is allowed to replace camera ownership.

The service watches outbound `SetServerCamera`, snapshots the latest external
packet before acquisition, and restores that exact packet. If no external
packet was observed, it calls native `CameraManager.resetCamera`; it does not
invent a first-person packet. A locked external camera owner causes open to
fail rather than overwrite another owner. A later external camera write
supersedes and closes the Canvas session.

Cleanup releases the input guard **before** clearing graph input, removing the
HUD/page, and restoring the camera. Implemented terminal paths are explicit
close, visible close region, page dismiss, player death (`DeathComponent`),
disconnect, world removal, plugin shutdown, session replacement, open failure,
UI update failure, queue transition overflow, graph-input exception, and
camera supersession. The trace summary records guard state after cleanup.

## 6. Page negative control

`/canvasui-cursor-probe-page` remains a separate normal CustomUI page and now
shows `CUSTOMUI_PAGE_NEGATIVE_CONTROL`. It records lifecycle markers before
open, during ownership, and after close, plus whatever pointer counters pre.2
delivers. It is not conflated with the passive HUD.

Connected result: **NOT RUN**. If the event counter continues in this mode,
stop before selecting a final host architecture because the historical page
blocker may have changed in pre.2.

## 7. Minimal graph proof

`/canvasui-cursor-drag-proof` renders two real Canvas nodes, typed ports, and
one existing orthogonal edge through `CursorHudCanvasBackend`. Pointer input is
converted once and passed to the public/generalized `CanvasInputController`.
That controller retains the existing four-unit drag threshold, grab offset,
logical capture, node mutation, edge geometry updates, connection machinery,
and terminal persistence callback.

The proof requires a successful calibration from `/canvasui-cursor-probe` in
the same plugin process. Persistence is deliberately a diagnostic callback;
the trace records exactly one snapshot write at each terminal graph gesture.
It does not alter RPG player builds.

Automated proof passed press, threshold-crossing movement, continued movement,
release far outside the node, final coordinates, and exactly one persistence
call. The graph backend is event-driven and permits at most one render
submission every 40 ms (25 Hz); superseded motion samples are coalesced in a
bounded 1,024-entry queue, preserving button transitions or failing closed.

Connected drag, actual render rate, average/peak processing latency, and drop
counts: **NOT YET MEASURED**. They are emitted in the session summary.

Middle-button panning: **not claimed**; capability remains false.  
New connection drag: **not included in this bounded entry proof**; existing
connection rendering and controller semantics remain intact.  
Native pointer capture: **not claimed**; the implementation is named
`SESSION_LOGICAL_CAPTURE`.

The candidate capability object reports only `supportsPointerMove=true`, which
R041 proved. Primary drag, middle drag, right-click, wheel, modifiers, native
capture, and text remain false until matching connected evidence exists.

## 8. Files and boundaries

Primary R042 implementation files:

- `CanvasPointerTransform.java`: calibration, mapping, error budgets, close hit;
- `CanvasInputGuard.java`: leases and native authoritative guards;
- `CursorHudProbeService.java`: contexts, camera ownership, bounded queues,
  trace, cleanup, graph routing;
- `HytaleCursorHudInputBackend.java`: conservative candidate capabilities;
- `CursorHudCanvasBackend.java`: existing Canvas geometry to passive HUD;
- `CanvasCursorProbeHud.java` / `CanvasCursorProbePage.java`: diagnostics;
- `CanvasCursorProbeHud.ui`: landmarks, marker, counters, and two-node proof;
- `CanvasInputController.java` / `CanvasRenderBackend.java`: narrow host-neutral
  seams while preserving the page backend;
- `CanvasUIPlugin.java`: system/event/command registration;
- `CursorProbePhaseATest.java`: transform, guard, queue, drag, release and
  persistence regression tests.

No RPG skill, passive, damage, projectile, summon, progression, HUD gameplay,
or ImmersiveNPC logic was changed for R042. `HyARPG.jar` was rebuilt only to
keep the coupled numeric revision at R042.

## 9. Validation

- Complete retained RPG tests: **2,358 passed**.
- Native-control tests: **67 passed**.
- CanvasUI tests: **28 passed**.
- Aggregate retained tests: **2,453 passed**, zero failures/errors/skips.
- CustomUI static validation: **PASS**.
- CanvasUI isolated startup: **PASS** on pre.2; both cursor probe and drag-proof
  commands registered; no CanvasUI-scoped startup error.
- Exact three-mod smoke: **PASS**, process exit 0, clean shutdown, all retained
  RPG/native gates true, no asset rejection.
- Binary rollback simulation: **PASS**.
- Three-mod archive: exactly CanvasUI, HyARPG, and HYTALEDEVLIB; entry hashes
  verified.

These local gates prove structure and packaging only. They do not prove client
coordinates, world-action suppression, camera presentation, drag rendering,
page behavior, or restoration.

## 10. Packaging and deployment

Final deployed artifacts:

- `CanvasUI-0.1.0.jar` SHA-256  
  `2FAEBFE07DD9A3855F676144463D2B0864DE82DCA5F517078F63CF270F5FC3B5`
- `HyARPG.jar` SHA-256  
  `4574E0408D26A7B0D2420555977D2E6FADC1A0FF10BBD42BBC4BE66729DD0FBB`
- retained `HYTALEDEVLIB-0.5.0.jar` SHA-256  
  `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230`

Candidate/live hashes match. ImmersiveNPCs and every unrelated JAR were
hash-checked and left unchanged.

Final save/mod-state backup (605 files):

```text
evidence/canvas-ui/cursor-hud/R042/final/before/save/20260919T130925Z/RPG
```

Final archive:

```text
evidence/canvas-ui/cursor-hud/R042/final/CanvasUI-Cursor-HUD-R042-three-mods.zip
SHA-256 655AE3E5EEE6697477F96B9F9A788A60A6D1DC2FDF4E7A919385BA1ACB7C4EBA
```

## 11. Current decision gate

```ini
CursorHudPhaseB = CONNECTED_QA_REQUIRED
ProductionReady = NO
```

Approval requires accurate mapping, left press/hold/release, release outside,
zero world click-through, clean restoration, acceptable responsiveness, and no
unbounded queue growth in the connected client. Headless tests cannot close
this gate.

## 12. Exact connected checklist

Restart Hytale so both R042 JARs load, then run these in order.

1. `/canvasui-cursor-probe-nohud`
   - Move for 10 seconds.
   - Click/hold/release left, right, and middle over empty space, blocks, and an
     entity where safe.
   - Confirm pointer count changes and no attack/mine/place/use/ability/hotbar/
     drop action executes.
   - Close with `/canvasui-cursor-probe-close`.
   - Confirm ordinary gameplay immediately works again.

2. `/canvasui-cursor-probe`
   - Click the centers in the prompted order: TL, TR, BL, BR, CENTER.
   - Confirm mapping says READY rather than REJECTED.
   - Move the pointer to center and all four corners; verify the yellow marker
     overlaps it closely (preferred <=5 px center, <=8 px corners).
   - Verify raw, normalized, viewport, and local coordinates update.
   - Try attack, mine, place, use/entity interact, E/R abilities, hotbar change,
     and item drop; none should execute while the HUD is active.
   - Click just outside CLOSE: it must stay open. Click inside CLOSE: it must
     close. Confirm normal camera/input/gameplay returns.

3. `/canvasui-cursor-probe-page`
   - Confirm the page says `CUSTOMUI_PAGE_NEGATIVE_CONTROL`.
   - Move for 10 seconds and note whether pointer count changes continuously.
   - Close the page. Do not interpret zero events as a passive-HUD failure.

4. `/canvasui-cursor-drag-proof`
   - Hover Proof Node A and confirm its state changes.
   - Press left, move slightly below threshold, then drag well beyond threshold.
   - Confirm the node follows continuously without jumping and the attached
     edge follows.
   - Release with the pointer outside the original node/canvas region; movement
     must stop and final position must remain.
   - Repeat with Proof Node B.
   - During drag, retry attack/mine/place/use/abilities/hotbar/drop; none may
     reach gameplay.
   - Close via CLOSE or `/canvasui-cursor-probe-close`; confirm normal gameplay
     immediately returns and no camera/input remains stuck.

Afterward, leave Hytale closed or report that testing is complete so the
generated `mods/InigmasGames_CanvasUI/logs/cursor-hud/cursor-r042-*.jsonl`
files can be reviewed. The SUMMARY records event/packet counts, guard counts,
mapping/error state, drag begin/end, persistence count, average/peak controller
processing time, queue/trace drops, camera supersession, and cleanup result.
