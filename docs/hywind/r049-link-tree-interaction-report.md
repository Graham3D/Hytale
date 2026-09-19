# Hywind R049 modular Link Tree interaction report

Status: **IMPLEMENTED / PACKAGED / DEPLOYED / CONNECTED QA PENDING**  
Version/revision: `0.1.0-merge.4` / `R049`  
Implementation commit: `2b2cf1af4d749704a765ef74f7261ae2923987f9`  
Candidate/deployed SHA-256: `828C2F95E24F95A9F20006B2FB79D8C753704D70F0484E14F784A2F1A9B6AC1A`

## Implementation

R049 refines the existing passive cursor-HUD Skill Tree rather than introducing a second graph or persistence model. `RpgSkillTreeProjectionService` remains the Skill/Passive library and icon authority, `RpgSkillTreeMutationService`/`RpgLoadoutService` remain the assignment and topology authorities, persisted `LinkEdge.edgeId` remains the stable edge identity, and `CanvasInputController` still owns port-drag link creation.

New bounded presentation/controller components are:

- `LibraryBrowser`: case-insensitive player-facing-name substring filtering over the complete active library, followed by pagination and page clamping;
- `TreeDragController`: explicit `IDLE`, `ARMED`, `DRAGGING`, `SNAPPING_TO_TARGET`, `RETURNING_TO_ORIGIN`, and `CANCELLED` states, a six-pixel threshold, and a named 160 ms cubic ease-out animation;
- `TreeDropResolver`: live node-bounds/center resolution, strict Skill-to-Skill and Passive-to-Passive compatibility, occupied-node rejection, and an 18-pixel snap margin;
- `TreeLinkGeometry`: a continuous three-segment orthogonal route anchored to current port centers, shared by rendering and a separate ten-pixel hit tolerance;
- `TreeLinkInteraction`: single-link selection plus context state bound to the exact stable link ID and popup anchor;
- `CanvasGraphSearchPage`: native `ValueChanged` text entry layered over the passive HUD. Filtering is live while typing and the query is preserved across Skill/Passive tab changes.

The HUD now renders the canonical entry icon in each library row, carries that icon as the only drag ghost, displays assigned-node icons from authoritative projection metadata, animates accepted drops to the current node center, and animates rejected drops to the captured library origin. A page/session close cancels all transient state and delayed animation frames are generation-checked.

Links no longer use 12 visible square samples per edge. Each edge is rendered as one visually continuous orthogonal connector with overlapping joints. Left click selects one exact edge and brightens/thickens it. Right click records that edge ID and opens `Break Link? / Yes / No`; No and outside click are mutation-free, while Yes calls the same exact-edge `CursorCanvasEditor.breakLink` operation as the visible `BREAK SELECTED` action. The RPG adapter revalidates the edge against the current loadout and delegates to `RpgLoadoutService.unlink(player, EdgeId)`. Node assignments, Joint nodes, and unrelated edges are not removed.

## Hytale keyboard limitation

The installed `0.7.0-pre.3.1` API exposes `CustomUIEventBindingType.KeyDown` only to an `InteractiveCustomUIPage`. `CustomUIHud` has no event-binding surface, and keeping an interactive page open suppresses the continuous raw cursor stream required by this editor. Therefore global Delete-key capture cannot be implemented safely without breaking drag/link interaction. R049 does not install an unsafe global listener. Search-field Delete remains owned by the native text field; selected links can be broken through the visible `BREAK SELECTED` control or the required right-click confirmation. This is the one explicit API-limited difference from the requested contract.

## Changed areas

- CanvasUI editor API and interaction components under `canvas-ui/src/main/java/com/inigmasgames/canvasui/api/editor`;
- passive HUD controller in `CursorHudProbeService`;
- graph/search renderers and CustomUI documents;
- RPG Canvas adapter and exact-edge mutation seam;
- deterministic CanvasUI and RPG authority tests;
- R049 build, smoke, deployment, and restart gates.

## Validation

| Gate | Result |
| --- | --- |
| Empty/full, substring, case-insensitive search | PASS automated |
| Search before pagination; page clamp; zero results | PASS automated |
| Typed Skill/Passive icon carried after threshold | PASS automated |
| No assignment on mouse-down/movement | PASS by state/authority separation |
| Valid snap and invalid return state/timing | PASS automated |
| Wrong-type, occupied, empty, and distant rejection | PASS automated |
| Canonical node icon projection | PASS package/structure |
| Continuous port-anchored connector geometry | PASS automated |
| Geometry hit testing and single-link selection | PASS automated |
| Exact edge break preserves assignments/other edges | PASS automated |
| Right-click stable target; Yes/No/outside behavior | PASS deterministic controller/authority paths |
| Delete keyboard shortcut | **API-LIMITED; visible selected-link break provided** |
| Existing port-link creation | PASS retained suite |
| Joint degree-three behavior | PASS retained RPG tests |
| Assignment/link persistence and reload | PASS retained RPG tests |
| Close during drag/animation | PASS generation/cleanup path |
| Source CustomUI validation | PASS, 59 documents |
| RPG/native JUnit | PASS, 2,363 tests |
| CanvasUI JUnit | PASS, 32 tests |
| Tavern JUnit and retained gates | PASS |
| Persistent NPC retained gates/resources | PASS |
| Unified package audit | PASS |
| Isolated Hywind-only smoke | PASS |
| Deployed two-start restart check | PASS |
| Connected rendering/input | **UNVERIFIED** |

`gradlew.bat clean check --no-daemon` completed successfully with 39 tasks. The exact package contains 5,786 entries, 2,190 classes, and 2,090 CustomUI documents.

## Deployment and rollback

Installed JAR:

`C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\Hywind.jar`

SHA-256:

`828C2F95E24F95A9F20006B2FB79D8C753704D70F0484E14F784A2F1A9B6AC1A`

Full stopped-save backup, prior JAR, deployment receipt, and journal:

`C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260919T200837Z`

The backup contains 614 files / 514,799,982 bytes. The retired prior `Hywind.jar` hash is `EB90BD93B26EBB9A894A9A07E6B6FB6FC53904C376A20A64641B19FD766141B3`. The active load path contains only `Hywind.jar` as a first-party project mod; external `HYTALEDEVLIB-0.5.0.jar` remains unchanged.

Connected QA should now open `/rpg skilltree` and exercise search, icon drag/snap/return, port linking, link selection, right-click Yes/No/outside dismissal, the selected-link break control, reopen persistence, and page-close cleanup. No connected success is inferred from automated or server-only evidence.
