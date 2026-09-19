# R050 Modular Skill Tree Connected-QA Stabilization

Status: **IMPLEMENTED / PACKAGED / DEPLOYED / CONNECTED QA READY**  
Connected client status: **UNVERIFIED**

## Scope and result

R050 preserves the R049 authoritative graph, loadout, mutation, and persistence owners while correcting the first connected-QA usability failures. `Hywind.jar` is the only active first-party project JAR. No RPG formulas, skills, Tavern behavior, NPC behavior, persistence schema, or save data were redesigned.

## Root causes and corrections

| Symptom | Earliest proven cause | R050 correction | Remaining limitation |
| --- | --- | --- | --- |
| Editor was small and upper-left | `CanvasGraphEditorHud.ui` used a fixed 900x660 panel with small constant offsets | Full-screen scrim plus a centered surface anchored by 90px horizontal and 50px vertical margins; centered title/subtitle; library tabs moved into the library; Skill Points removed | `PatchStyle` in 0.7.0-pre.3.1 has no supported blur/backdrop-filter property, so R050 uses the required translucent scrim fallback |
| Search looked like an unrelated page | `InteractiveCustomUIPage` replaced/suppressed the passive HUD and the old page rendered only a small search form | Introduced immutable `SkillTreeViewModel`; both HUD and Search Mode render the same editor composition. Search Mode dims the graph and pauses graph mutation while preserving tab/query/offset state | Native page ownership still requires a mode transition; it is now deliberate and visually continuous |
| PREV/NEXT browsing and wheel leakage | Library projected pages before interaction; installed mouse packets/events expose pointer/button/motion but no wheel delta or CustomUI scroll binding | Continuous filtered window, proportional scrollbar, track click, thumb drag, offset clamping, and no PREV/NEXT controls | Safe wheel capture is unavailable in the installed API; scrollbar is authoritative. Hotbar slot changes are not repurposed as fake wheel input |
| Drag ghost lagged behind pointer | Every library-drag motion called full `renderEditor`, rebuilding library, nodes, and up to 96 connector slots; queued historical motion was processed in order | Adjacent obsolete motion is coalesced with latest-state-wins semantics; press/release transitions remain ordered; drag motion patches only ghost and target highlight | Every visible movement still requires a client packet, server processing, and server-issued HUD update; no supported client scripting/interpolation path was found |
| Port linking was intermittent and unclear | Link state was implicit and had no connected preview; users could not see capture, compatibility, or snap state | Explicit `IDLE -> PORT_ARMED -> LINK_DRAGGING -> COMMITTING/CANCELLED` contract, 4px threshold, port-first hit priority, white live preview, compatible-port snap/highlight, shared `TreeLinkGeometry`, and authoritative commit-on-release | Connected repetition matrix remains pending |
| Authored icons reverted after rebuild | `Update-RpgIcons.ps1` patched the deployed JAR but did not update Gradle source resources; `clean` then rebuilt older/missing bytes | The updater now synchronizes `art/Skills` or `art/Passives` into canonical `src/main/resources` surfaces before JAR replacement. Package audit verifies source/JAR SHA-256 equality. Fixture tests use an isolated `SourceRoot` and cannot mutate repository assets | Visual correctness of several categories remains a connected-client check |

The `getActiveSlot: 5 != 6` warning is consistent with client hotbar selection and native validation disagreeing while a HUD cannot consume wheel input. Inspection of `InventoryPacketHandler` showed `SetActiveSlot` emits cancellable `InventoryActiveSlotRequestEvent` before `InventoryUtils.setActiveSlot`, but the event contains the requested slot rather than wheel delta/direction. R050 therefore does not infer scrolling from this mutation and does not risk transient equipment changes.

## Installed Hytale API inspection

The pinned runtime is `0.7.0-pre.3.1`. The audit inspected:

- `MouseInteraction`: active slot, screen point, mouse button, mouse motion, and world interaction; no wheel axis/delta;
- `MouseMotionEvent`: held buttons and relative X/Y motion only;
- `PlayerMouseButtonEvent` and `PlayerMouseMotionEvent`: no wheel value;
- `CustomUIEventBindingType`: no scroll/wheel binding;
- `InventoryActiveSlotRequestEvent`: cancellable slot request but no wheel provenance or direction;
- `InventoryPacketHandler`: cancellable request occurs before native slot mutation;
- `PatchStyle`: no blur or backdrop-filter API;
- `Anchor`: supported left/right/top/bottom/full/horizontal/vertical responsive constraints.

## Input and rendering architecture

```text
MouseInteraction / PlayerMouse event
  -> CursorHudProbeService packet/event observation
  -> bounded CursorProbeInputBuffer
  -> owning World.execute drain
  -> CanvasPointerTransform calibration
  -> library router or CanvasInputController
  -> partial CustomUIHud.update
  -> Hytale client renders the patch
```

Motion samples may be coalesced only when the queue tail is another motion sample. Button transitions are never coalesced or discarded. Queue capacity remains bounded. No persistence, disk lookup, assignment mutation, or graph serialization occurs during library-drag motion.

R049 rebuilt the full frame per library motion, including ten library rows, eleven graph nodes, and ninety-six connector-pool entries. R050 library motion updates at most five elements with an estimated 220-byte patch. Link preview updates at most nine elements with an estimated 420-byte patch. Session summaries now aggregate motion/input rates, coalesced and dropped counts, maximum queue depth, elements and average estimated patch bytes, and server receipt-to-patch p50/p95 latency.

Actual connected rates and latency are intentionally pending; local tests cannot manufacture client/server/client timing evidence.

## Link, search, and graph semantics

- Skills are squares with authored icons; Passives are circles; Joints are triangles.
- Committed and preview links use continuous white geometry.
- Ports have priority over node bodies, connector hit regions, and background.
- Preview snaps only when existing compatibility authority accepts the destination.
- `Skill -> Skill` remains explicitly rejected.
- Invalid release removes the preview and commits nothing.
- R049 selection, visible break, right-click confirmation/dismissal, exact-edge deletion, assignments, and unrelated links are retained.
- Search filters before offset/window selection. Value changes update immediately; Clear resets; tab changes clamp; Done restores the same graph/query/tab/offset. Search Mode has no graph mutation binding.

## Icon ownership and workflow

```text
art/Skills/Skill*.png or art/Passives/Passive*.png
  -> tools/Update-RpgIcons.ps1
  -> src/main/resources/Common/UI/Custom/Icons/RPG/<file>
  -> for skills: src/main/resources/Common/Icons/Items/RPG/<file>
  -> for skills: source Item JSON Icon URI
  -> Gradle processResources
  -> Hywind.jar exact bytes
  -> RpgSkillIcons CustomUI URI / native ItemAbility Icon URI
```

Repository resources are canonical Gradle inputs; `build/`, extracted packages, and the deployed JAR are not authoring sources. Seventeen current owner-authored icons are source/package hash-gated.

## Validation

Complete retained validation result: **PASS**.

| Gate | Result |
| --- | --- |
| RPG JUnit | 2,365 tests, 0 failures/errors/skips |
| Native-control JUnit | 67 tests, 0 failures/errors/skips |
| CanvasUI JUnit | 37 tests, 0 failures/errors/skips |
| Tavern JUnit | 5 tests, 0 failures/errors/skips |
| Tavern retained executable gates | 8/8 PASS |
| Persistent NPC retained harness | 149 named gates PASS; live local-model tests skipped by retained harness |
| CustomUI source validation | 59 documents PASS |
| Icon source/package audit | 17 authored icons, exact hashes PASS |
| Unified package audit | PASS |
| Isolated Hywind-only startup/shutdown | PASS |
| Deployment dry run | PASS |
| Post-deployment startup/restart cycles | 2/2 PASS |
| Connected client matrix | UNVERIFIED |

The suite found stale expectations for old placeholder/icon bytes and one test-isolation defect. Assertions now validate current authored bytes and custom Fireball presentation; `Stage13IconUpdaterTest` supplies a temporary `SourceRoot`, preventing fixture runs from changing repository assets. No behavior assertion was removed or relaxed.

## Package and deployment

| Property | Value |
| --- | --- |
| Version / revision | `0.1.0-merge.5` / `R050` |
| Implementation commit | `c0fafac51a4d32b1287d4a5f768baf1d099961ce` |
| Artifact | `build/libs/Hywind.jar` |
| SHA-256 | `BCED6B2B7386AE047890C70A48C31767BD1B497A25BFF3959D3585BF3EC49466` |
| Bytes | 13,300,177 |
| ZIP entries / classes | 5,817 / 2,198 |
| CustomUI documents in JAR | 2,090 |
| Installed path | `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\Hywind.jar` |
| Rollback directory | `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260919T213704Z` |
| Full backup | 626 files / 529,891,572 bytes |

Candidate and installed hashes are identical. The previous R049 JAR (`25F2751EAD48767D0FABF958D983B7FC52B93CFA657EE165307FB450C68703B4`) is retained in the rollback directory. Active JARs are `Hywind.jar` plus preserved external `HYTALEDEVLIB-0.5.0.jar`; no superseded first-party JAR remains active.

Both post-deployment cycles discovered and started R050, started Taverns R056, reported no scoped failure or legacy plugin, and shut down normally. Startup-generated logs/trace growth was permitted; authoritative data roots and save architecture were preserved.

## Connected acceptance still required

Run `/rpg skilltree` and complete the R050 matrix from the task: centered layout, Search Mode (`Ligh`, Clear, Done), scrollbar top/bottom and drag, accepted/rejected icon drops, ten repeated port-link operations, invalid and Skill-to-Skill rejection, selection/break/context interactions, icon-category inspection, close/input cleanup, restart, and post-rebuild icon persistence. Review the cursor summary for rates, coalescing, queue depth, patch bytes, and receipt-to-patch p50/p95.

R050 remains **CONNECTED QA READY**, not CONNECTED-VERIFIED, until that in-game matrix is completed.
