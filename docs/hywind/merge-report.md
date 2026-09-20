# Hywind merger and deployment report

Status: **DEPLOYED / CONNECTED VALIDATION PENDING**
Candidate/deployed SHA-256: `25F2751EAD48767D0FABF958D983B7FC52B93CFA657EE165307FB450C68703B4`
Version/revision: `0.1.0-merge.4` / `R049`
Deployment time: `2026-09-19T20:15:10.0413574Z`
Implementation commit: `da18f0f8594dd745798cc117588eccd37047127b`

Hywind now contains the required RPG, CanvasUI, Immersive NPC/Orbis, and Tavern Management subsystems behind one manifest and one Hytale plugin lifecycle. `Hywind.jar` is installed as the only active first-party project JAR in the RPG save. Automated tests, package validation, fresh and copied-data server smokes, rollback rehearsal, live deployment, and two post-deployment restart cycles passed. Connected-client rendering/input/gameplay QA remains explicitly unverified.

R049 adds the modular Link Tree interaction refinement. Its detailed implementation, validation, deployment record, and one explicit pre.3 keyboard API limitation are in `docs/hywind/r049-link-tree-interaction-report.md`. R048's Lightning implementation remains cumulative and documented in `docs/hywind/r048-lightning-update-report.md`.

## 1. Correction to the earlier report

The merge.1 report incorrectly said Tavern Management was intentionally excluded. Taverns was required by the final merger handoff and is included in merge.2.

The authoritative Tavern source is `origin/main:hytale-taverns`, last changed by commit `981f9aafefb6e974626a473020b4152251da9f1a`. That tree contains all 36 production Java files, eight retained executable test programs, authored resources, and the original Tavern build pipeline. The preserved R056 artifact:

`C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\Tavern\mods\Taverns-0.1.0.jar`

was used only as a parity/reference artifact. Its SHA-256 is `B2F19818D33B8095F462CDF77825CDEC8D949E9B7EEEFB1F9979691B0E137947`.

Reconciliation results:

- all 36 production Java files and all eight retained Tavern tests came from Git, not decompilation;
- 59 authored source resources match `origin/main` byte-for-byte;
- the language file was relocated unchanged into a dedicated merge input and is appended to Hywind's single `en-US/server.lang`;
- the source-only `Comfort.png` and `Relaxed.png` HUD icons are intentionally absent because the authoritative R056 build script removes them as obsolete, matching the reference JAR;
- the reference JAR's additional patron-order particle systems/textures are deterministic outputs of the authoritative Tavern build script and are retained in Hywind;
- no newer intentional Tavern gameplay change was found only in the JAR.

## 2. Preserved subsystem behavior

The merger retains the existing Tavern implementation without adding content:

- Tavern, Kitchen, and Bedroom Cores and their authored starting volumes;
- placement validation, primary/specialized-room containment, and non-overlap rules;
- Zoning Editor input, virtual Selection Tool presentation, and scoped permission provider;
- expansion shard charging, paid-unit accounting, shrink refunds, and Creative-mode behavior;
- schema-1/schema-2 migration into current schema 3 with migration backups;
- Tavern/open-service state, persistent Tavern/Core records, prepared crafting, Comfort scoring, Relaxed regeneration, table serving, patron orders, native NPC behavior, HUD, sounds, and generated order particles;
- legacy writable data root `mods/InigmasGames_Taverns`.

The separate historical Tavern save was not modified or cross-imported into the RPG world because its world UUIDs and coordinates belong to that save. Compatibility was instead proven by copying its ten-file schema-3 data root into an isolated smoke environment. Hywind loaded one Tavern and one Core, and all ten files/171,181 bytes remained hash-identical. The live RPG save had no Tavern root before this deployment; startup created a new empty schema-3 root for that world.

## 3. Unified architecture

Hywind has one discoverable plugin:

```text
InigmasGames:Hywind
com.inigmasgames.hywind.HywindPlugin
build/libs/Hywind.jar
```

The production lifecycle is one inheritance chain:

```text
HywindPlugin
  -> TavernsPlugin
    -> PersistentNpcsPlugin
      -> JavaPlugin
```

Hywind calls the existing CanvasUI and RPG owners inside that lifecycle. Setup/start are ordered character/NPC foundation, Taverns, CanvasUI, then RPG completion as required by the inherited boundaries; shutdown reverses owned resources and invokes parent cleanup. Partial-start cleanup includes Taverns.

The existing writable roots remain authoritative:

| Domain | Data root |
| --- | --- |
| RPG/gameplay | `mods/InigmasGames_HytaleRPGPhase00Audit` |
| CanvasUI/presentation | `mods/InigmasGames_CanvasUI` |
| NPC/Orbis | `mods/ImmersiveNPCs` |
| Tavern Management | `mods/InigmasGames_Taverns` |

No gameplay formula, RPG skill, reward, HUD ownership, NPC profile schema, Orbis provider policy, or Tavern content was redesigned.

## 4. Hytale pre.3 compatibility seam

The authoritative R056 source required bounded API adaptation for installed Hytale `0.7.0-pre.3.1`:

- `UpdatePlayerInventory` snapshots now preserve AbilitySlots and RuneBag;
- the Zoning Editor permission provider uses `PermissionQuery.getId()` and implements `getUsersWithPermission`;
- `ModelParticle` supplies the new constructor flag;
- obsolete explicit `TransformComponent.markChunkDirty` calls were removed because current transform setters notify mutation;
- native NPC greeting targets use the current ref/accessor `Role.setMarkedTarget` signature and `MarkedEntitySupport` component;
- `TavernsPlugin` became an abstract internal lifecycle owner with a data-root hook so Hywind remains the sole plugin bootstrap.

These changes alter integration signatures, not Tavern behavior or persistence semantics.

## 5. Build, package, and validation

The root Gradle project now includes `:hytale-taverns`. Root `check` depends on Tavern JUnit and retained executable gates, and root `jar` embeds Tavern classes/resources while excluding its standalone manifest and separate language file.

Complete retained command:

`gradlew.bat clean check build --no-daemon --console=plain`

Result: **PASS**, 42 tasks executed in 3m27s.

| Gate | Result |
| --- | --- |
| RPG/native JUnit | 2,428 tests, 0 failures/errors/skips |
| CanvasUI JUnit | 28 tests, 0 failures/errors/skips |
| Tavern JUnit | 5 tests, 0 failures/errors/skips |
| Tavern authoritative retained harnesses | 8/8 PASS |
| Persistent NPC retained harness | 149 named executable gates PASS |
| NPC exact release-resource validator | PASS |
| CustomUI validator | 58 source documents PASS |
| Unified package audit | PASS |
| Fresh isolated Hywind-only server smoke | PASS |
| Copied RPG + historical Tavern data smoke | PASS |
| Deployment dry run | PASS |
| Full deployment/rollback rehearsal | PASS; 599/599 files and all hashes restored |
| Live deployment | PASS; candidate/installed hashes identical |
| Live post-deployment restart cycles | 2/2 PASS |
| Connected Hytale client QA | UNVERIFIED |

One retained NPC harness race was exposed during the first full run: an asynchronous producer modified a synchronized `ArrayList` while the main thread streamed it. The harness now uses `CopyOnWriteArrayList`; all original assertions and timing expectations are unchanged, and the complete suite then passed.

Package properties:

| Property | Value |
| --- | ---: |
| JAR bytes | 12,606,843 |
| ZIP entries | 5,736 |
| classes | 2,163 |
| CustomUI documents | 2,089 |
| generated NPC sections | 2,032 |
| total NPC sections | 2,048 |
| root manifests | 1 |

Pinned runtime:

| Runtime input | Exact value |
| --- | --- |
| Hytale | `0.7.0-pre.3.1` |
| `HytaleServer.jar` | `7928797E148E4B15F787E1449BF020FCA41A9BB2F605E464F6A0699484CE71BC` |
| `Assets.zip` | `1A48A64DA959F1A1EBCCB461B6C518BF2AF94F1116DEBCE84F7CC1E0E95A89D9` |

## 6. Deployment and rollback

Installed artifact:

`C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\Hywind.jar`

Installed SHA-256:

`25F2751EAD48767D0FABF958D983B7FC52B93CFA657EE165307FB450C68703B4`

Full stopped-save backup and deployment journal:

`C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260919T201509Z`

The final backup contains 618 files and 515,982,955 bytes. The immediately-prior R049 candidate was retired there with SHA-256 `828C2F95E24F95A9F20006B2FB79D8C753704D70F0484E14F784A2F1A9B6AC1A`; the preceding backup retains the original R048 artifact.

Active JARs in the RPG load path:

- `Hywind.jar` — the sole first-party project mod;
- `HYTALEDEVLIB-0.5.0.jar` — preserved optional external dependency, SHA-256 `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230`.

No standalone RPG, CanvasUI, ImmersiveNPCs, or Taverns JAR remains active. Both post-deployment server cycles discovered only `InigmasGames:Hywind`, emitted Tavern/RPG/Canvas/NPC startup markers, reached server boot, and shut down cleanly.

## 7. Remaining connected checks

Automated/startup validation cannot prove client rendering or input. A connected session should still verify:

1. place/use each Tavern, Kitchen, and Bedroom Core; enter/exit Zoning Editor; resize, charge, refund, restart, and confirm persistence;
2. prepared cooking, Comfort tooltip/scoring, Relaxed effect, service opening, table serving, patrons, orders, payment, HUD, particles, and sounds;
3. existing RPG casting/combat/HUD/progression and restart/rejoin;
4. CanvasUI cursor, node/library drag-and-drop, links/joints, close/cleanup, and restored gameplay input;
5. NPC profiles, appearance, inventory transfer, Orbis providers/voice, persistence, and no duplicate plugin behavior.

Until those connected checks pass, the release status remains **DEPLOYED / CONNECTED VALIDATION PENDING**.

## R050 modular Skill Tree stabilization

R050 is implemented, packaged, and deployed from implementation commit `c0fafac51a4d32b1287d4a5f768baf1d099961ce`. It enlarges and centers the editor, introduces integrated read-only Search Mode over the shared immutable projection, replaces PREV/NEXT with a draggable proportional scrollbar, coalesces obsolete cursor motion, uses bounded partial drag/link patches, restores explicit port-link state and white preview geometry, and makes owner-authored icon bytes canonical build inputs.

The deployed `Hywind.jar` SHA-256 is `BCED6B2B7386AE047890C70A48C31767BD1B497A25BFF3959D3585BF3EC49466`. A full rollback copy is at `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260919T213704Z`. Retained validation, package audit, isolated smoke, deployment dry run, and two post-deployment restart cycles pass. Connected-client R050 validation remains pending; see `r050-link-tree-connected-stabilization-report.md`.

## R052 unified Skill Tree inspector and polish

R052 is implemented, packaged, and deployed from implementation commit `39f85447bc383eba1f6483a5e60309dd2cc157fd`. It adds stable edge-ID keyed connector-port persistence, topology-preserving occupied-Skill unequip/re-equip, structured Skill/Passive inspection, deterministic drag/hover/selection precedence, an exact modal native/custom HUD lease, and the approved Hytale/owner-authored node presentation assets in a three-region editor frame.

The installed `Hywind.jar` SHA-256 is `CF062E57BBB6DB4FC230CFBC4277B8A2B0DF2AAAEA7159B55F9996EFA07EFD39`. The full stopped-save rollback is `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T001005Z`. The complete retained suite, 59-document CustomUI validator, package/hash audit, isolated smoke, and two post-deployment restart cycles pass. Connected-client rendering and input validation remain pending; see `r052-skill-tree-inspector-polish-report.md`.

## R053 Skill Tree visual polish correction

R053 is implemented, packaged, and deployed from implementation commit `a5f003857ade700844aabddfe6a98dbf3fa395e1`. It fixes the connected red-X texture boundary by using client-relative CustomUI URIs, corrects the empty/occupied Skill-slot mapping, packages pinned Hytale Inventory frame/button art, provides the required four-frame composition, and moves Save/Exit to the bottom-right without changing R052 graph authority.

The installed `Hywind.jar` SHA-256 is `62C33FC19C0D7FBCE2CD5EC484F366EABFA02796B89840D46E821737C26DB5F5`. The full stopped-save rollback is `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T010442Z`. The complete retained suite, CustomUI validator, 24-asset Skill Tree hash/URI gate, package audit, isolated smoke, and two post-deployment restart cycles pass. Connected-client visual validation remains pending; see `r053-skill-tree-visual-polish-correction-report.md`.

Connected QA subsequently proved R053's direct `@2x` texture references did not resolve and displayed red-X fallbacks. R054 supersedes that URI implementation.

## R054 Skill Tree density-reference hotfix

R054 is implemented, packaged, and deployed from implementation commit `859795861d6e3cc0deed37bfe66584849cf0f765`. It preserves the exact Inventory artwork bytes but references each Hytale density variant through its required logical filename, including occupied `Slot.png` and empty `SpecialSlotTemporary.png`. All copied frame, tab, panel, button, divider, placeholder, and connector-arrow references were corrected through the same central rule.

## R055 Skill Tree composition and interaction polish

R055 is implemented, packaged, and deployed from implementation commit `c09304047dca23cd2bbf818b764c0b1e0e7c59d9`. The editor now uses native Inventory container chrome for all three functional windows, renders the ornamental tree background without nine-slice distortion, exposes thirteen library rows, and presents a divided non-overlapping Details layout. The duplicate Search Mode overlay was removed in favor of a single native focus control aligned over the existing search field. Skill labels use the player-facing `E`, `R`, and `UNBOUND` bindings; connector ports overlap their node silhouettes; and both restored and actively dragged nodes are clamped to the center tree workspace.

Branding, revision, subtitle, layout-success text, passive Current Link, and validation footer copy were removed from the editor presentation. The complete retained Gradle gate, package audit, isolated unified-plugin smoke, deployment backup, hash check, and two-cycle deployed startup/restart validation passed. Connected client rendering and interaction remain explicitly pending.

The installed `Hywind.jar` SHA-256 is `D20A7E6C25989E28BF0D64DD313CEAF1708B357552B1CC2A607621F5549BD477`. The full stopped-save rollback is `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T013406Z`. Focused tests, the complete retained suite, 59-document CustomUI validation, package audit, isolated smoke, and two post-deployment restart cycles pass. Connected-client visual confirmation remains pending; see `r054-skill-tree-density-reference-hotfix-report.md`.

## R056 Skill Tree connected-QA corrections

R056 is implemented, packaged, and deployed from implementation commit `b2f2e273f35c229beab45f85f69cf3a8bb776231`. It removes the redundant `BREAK SELECTED` control, fixes the cross-document CustomUI patch that disconnected the client during link removal, permits authoritative replacement drops into occupied and `UNBOUND` Skill nodes, expands the library to fifteen rows, and corrects the header/tree artwork, title, tabs, instructions, search styling, and connector-port placement requested by connected QA.

The installed `Hywind.jar` SHA-256 is `3E5B854FE001DF71F3C0779D76778AF1BA39E6575521F258A565D5602AFF690B`. The verified full-save rollback is `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T025740Z`. The complete retained suite, unified package audit, isolated smoke, and two deployed startup/restart cycles pass. Connected-client rendering and interaction remain pending; see `r056-skill-tree-qa-corrections.md`.

## R057 Skill Tree frame hotfix

R057 is implemented, packaged, and deployed from implementation commit `a2d514c4`. It promotes the lighter-blue native header to the outer primary title frame, removes the inset duplicate, restores the instruction copy to a centered horizontal footer region, and doubles connector-arrow visuals to 18x18.

The installed `Hywind.jar` SHA-256 is `155715D457516954CDF2815900BD0A7331EA4ED17C7A1CB4FEA7BD9D586AF2D6`. The verified full-save rollback is `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T031728Z`. Full retained validation, isolated smoke, and two deployed startup/restart cycles pass. Connected visual confirmation remains pending; see `r057-skill-tree-frame-hotfix.md`.

## R058 Skill Tree UI QA corrections

R058 is implemented, packaged, and deployed from implementation commit `56d368cec533eca28fce67c7041010a8357e4ca8`. It restores one dominant native outer frame and title style, preserves the center ornament without non-uniform distortion, repairs frozen exact-edge link removal, adds a tree-only confirmed Reset operation, orients connector arrows outward, persists destination-facing port movement during active node drags, and routes Escape through the same exact-once close/cleanup path as Exit.

The installed `Hywind.jar` SHA-256 is `931071DF264A2BA2C7EFBBEFE2EEA8167A89AA57EAA627760ED8D9FA69FC075F`. The verified full-save rollback is `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T131311Z`. The complete retained suite, 60-document CustomUI validation, 31-asset Skill Tree hash gate, package audit, deployment dry run, isolated smoke, and two deployed startup/restart cycles pass. Connected-client visual and input verification remains pending; see `r058-skill-tree-ui-qa-corrections.md`.

## R059 CustomUI load hotfix

R059 is implemented, packaged, and deployed from implementation commit `55d724418f5907cca2be9b26f364c33fafee47f0`. It removes the client-unresolvable `Common/Container.ui` import introduced in R058 and embeds the exact installed native title-style properties locally, preserving the intended Hytale typography without crossing the CustomUI package boundary.

The installed `Hywind.jar` SHA-256 is `2072D6AAD3E834A6FAFA4A7E9BB2E73289E27A6D6953C36A6D3FBC9CE06DBEC8`. The verified full-save rollback is `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T140851Z`. The clean retained suite, 60-document CustomUI validation, package audit, and two deployed startup/restart cycles pass. Connected-client join verification remains pending; see `r059-customui-load-hotfix.md`.

## R060 Skill Tree HUD restoration hotfix

R060 is implemented, packaged, and deployed from implementation commit `b77ac826a5f69aa4cefa3488b1f208c73e89719f`. Connected traces proved that the R058 transparent Escape-capture `CustomUIPage` displaced the graph HUD and immediately dismissed the editor after `/rpg skilltree`. R060 removes that competing page and makes `CanvasGraphEditorHud` the sole graph-editor presentation surface again. Search retains its bounded temporary page lifecycle, and the visible red `EXIT` control retains the exact-once cleanup path.

The installed `Hywind.jar` SHA-256 is `67713D1DB43D1661DE9EAA94DE76434CBECDC01ECF40B9F0FEBB92FE8E50ABBD`. The verified full-save rollback is `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T142106Z`. The full retained suite, 59-document CustomUI validation, package audit, and two deployed startup/restart cycles pass. Connected verification that the editor is visible after `/rpg skilltree` remains pending; Escape-key parity is not claimed in this hotfix. See `r060-skill-tree-hud-restoration-hotfix.md`.

## R061 Skill Tree native-menu polish

R061 is implemented, packaged, and deployed from implementation commit `abd9b4f47f63afa3efaed64080f571bc3017c17e`. It adds the native Inventory title gradient and selected-page ornament, native-style button text hover states, sixteen visible Library rows, corrected focused-search overlay alignment, full-frame bordered center ornament placement, centered Reset confirmation, a fading green Save acknowledgement, and a centered dirty-exit confirmation. Node movement and newly drafted links now remain explicitly unsaved until Save; authoritative resets, unequips, assignments, and confirmed link removals retain their existing immediate authority behavior.

The installed `Hywind.jar` SHA-256 is `61C1887E7D9A2418BE96C2CCCEE947B22375C9DD2D9C8C2782525745DDDB473E`. The verified full-save rollback is `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T150038Z`. The clean retained suite, 59-document CustomUI validation, 46 CanvasUI tests, unified package audit, and two deployed startup/restart cycles pass. Connected-client visual/input verification remains pending; see `r061-skill-tree-polish.md`.

## R062 Skill Tree search and layout correction

R062 is implemented, packaged, and deployed from implementation commit `3160d525b529827cd5700199d3d3e5be436b1d9d`. It contains wrapped Details text within its divided rows; turns the native Search page into a visually in-place Library focus state with an explicit `X`, Enter/outside/result completion, and preserved editor state; restores the selected-page trim at native proportions; replaces the gold arrow ports with the owner-authored white connector artwork; fixes Skill and Joint port anchors while retaining Passive drag orbit; and gives confirmed Reset a clean canonical two-Joint/three-Skill/six-Passive layout.

The installed `Hywind.jar` SHA-256 is `1FFD58CB854AE36EDC4EFD11A1304B3A99DDB338F49FC034EEC9A23BE3A49A1C`. The verified full-save rollback is `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T160051Z`. The clean retained suite, 59-document CustomUI validation, 47 CanvasUI tests, package audit, isolated smoke, dry run, and two deployed startup/restart cycles pass. Connected-client visual/input verification remains pending; see `r062-skill-tree-search-and-layout.md`.

## R063 Spark QA/QC pass

R063 is implemented, packaged, and deployed from implementation commit `cd52435c4f6494906c8feff1da925b621b749e6b`. It renames the existing `charged_bolt` skill to Spark at the player-facing boundary while retaining the old name as a migration alias, replaces the eye-level straight projectile with an independently steered ground crawler, preserves actual-path range ownership and per-projectile target deduplication, adds intrinsic enemy penetration, keeps wall termination and Ricochet authority, renders compact horizontal ground effects, and removes Spark's cast animation/origin effect. Damage, Mana, cooldown, bolt count, Electrified ownership, stable IDs, and unrelated Lightning skills are unchanged.

The installed `Hywind.jar` SHA-256 is `D0E6BE36FDD87323C83BC81650E95F6BDB79299BCD820BF90B7DFD6D5303B2EC`. The verified full-save rollback is `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T181744Z`. Focused Spark/Lightning/projectile/passive tests, the complete retained suite, 59-document CustomUI validation, package audit, isolated smoke, deployment dry run, and two deployed startup/restart cycles pass. Connected-client gameplay and presentation verification remains pending; see `r063-spark-qa-pass.md`.

## R064 Spark native-course hotfix

R064 is implemented, packaged, and deployed from implementation commit `c5b44f47a3c35eb1d7ab8591eedcd0b0e38df16d`. It fixes the connected-trace regression in which Hytale interpreted Spark's intentionally stationary native visual carrier as a completed native course and cancelled every ground crawler after roughly 65 ms. Spark now reclaims only contact-free course endings, removes the queued native despawn, restores manual carrier state, and emits a dedicated recovery trace event. Real terrain/entity contacts and all non-Spark projectiles retain their original authority path.

The installed `Hywind.jar` SHA-256 is `AD3D9D87CE0BECAA392AB158B9C7EFB15DC45FC86B9F8C774AF369A0C4AE6B01`. The verified full-save rollback is `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T183655Z`. Focused regression tests, the complete retained suite, package audit, isolated smoke, deployment dry run, and two deployed startup/restart cycles pass. Connected confirmation of visible Spark travel remains pending; see `r064-spark-native-course-hotfix.md`.
