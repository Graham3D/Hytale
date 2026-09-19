# Hywind merger and deployment report

Status: **DEPLOYED / VALIDATION PENDING**  
Candidate/deployed SHA-256: `B07071798C994FE3CF1062C138BA38AB20B3108B529ABE8A74384D0566A751C1`  
Version/revision: `0.1.0-merge.1` / `R046`  
Deployment time: `2026-09-19T15:33:03.4885752Z`

Hywind is built and installed as the only active first-party project JAR in the RPG save. The previous RPG, CanvasUI and ImmersiveNPCs JARs are outside the active load path in a verified rollback set. Automated tests, package checks, isolated runtime checks, copied-data startup, rollback rehearsal, and two post-deployment server start/stop cycles passed. A connected Hytale client was not available to this implementation run, so rendering, input, live casting, voice, and rejoin behavior remain explicitly unverified; this is not reported as `COMPLETE`.

## 1. Scope and baseline

The merge preserved the current enabled behavior of:

- ARPG/HyARPG/HytaleRPG gameplay, Stage 13 persistence, skills, combat, HUD and progression;
- CanvasUI infrastructure, current cursor-HUD/editor work and diagnostics;
- ImmersiveNPCs profiles, inventory, appearance, autonomous behavior and Orbis coordination.

Tavern Management was intentionally excluded. It was present in the broader workspace but was neither part of the authoritative merger scope nor an active dependency of the target RPG save. No new skills, balance changes, quests, factions, AI features, provider changes, or model/training payloads were added.

Source provenance:

| Input | Provenance |
| --- | --- |
| RPG and CanvasUI | `25b85cb2819351a0d727e33132b2f57013cda024` plus the preserved cumulative RPG working tree |
| ImmersiveNPCs | `9033cbf2c421c45a521467761010d32bb2a967f6` (`origin/main`) |
| Consolidation branch | `hywind-merge` |

Installed runtime pinned for every compile, native test and asset check:

| Runtime input | Exact value |
| --- | --- |
| Hytale | `0.7.0-pre.3.1`, `pre-release` patchline |
| `HytaleServer.jar` | `7928797E148E4B15F787E1449BF020FCA41A9BB2F605E464F6A0699484CE71BC` |
| `Assets.zip` | `1A48A64DA959F1A1EBCCB461B6C518BF2AF94F1116DEBCE84F7CC1E0E95A89D9` |
| Target save | `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG` |
| Target load path | `...\Saves\RPG\mods` |

The global pre-release, EditorUserData and legacy UserData `Mods` directories were checked and contain no competing project JARs. Other saves have their own isolated `mods` directories and were not modified.

### Recoverable baseline

Before source restructuring or deployment, the stopped target and source tree were copied to:

`C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-merge-20260919T143610Z`

The initial save snapshot was verified at 599 files and 484,697,159 bytes. The final deployment took a second full stopped-save backup at:

`C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260919T153302Z`

Its copy was independently verified at the same 599 files and 484,697,159 bytes before cutover.

## 2. Consolidated architecture

The final package has one manifest and one Hytale plugin bootstrap:

```text
InigmasGames:Hywind
com.inigmasgames.hywind.HywindPlugin
build/libs/Hywind.jar
```

`HywindPlugin` owns setup, start, shutdown and partial-start rollback. Internally it uses these retained boundaries:

| Boundary | Implementation ownership |
| --- | --- |
| Bootstrap | Hywind identity, ordered lifecycle, data-root selection and cleanup |
| Gameplay | Existing RPG packages and authoritative services |
| Presentation | Existing CanvasUI packages and one Hywind-owned registration path |
| Characters | Existing Persistent NPC packages, native container/entity identity and commands |
| Intelligence | Existing Orbis Java coordination; providers/workers remain external |

Initialization is characters, CanvasUI, then RPG; shutdown is the reverse. The previous `Phase00Plugin`, `CanvasUIPlugin`, and Persistent NPC plugin entry lifecycles are not separately discoverable from the package. Commands and old compatibility surfaces remain wired through the internal owners.

The old data roots remain the writable authorities, deliberately avoiding a risky schema migration:

| Domain | Effective root |
| --- | --- |
| RPG/gameplay | `mods\InigmasGames_HytaleRPGPhase00Audit` |
| CanvasUI/presentation | `mods\InigmasGames_CanvasUI` |
| NPC/Orbis | `mods\ImmersiveNPCs` |

Player/NPC IDs, RPG persistence and journals, profiles, relationships, memories, native inventory state, provider settings, traces and external-worker layouts therefore retain their existing locations and formats.

## 3. Build and package changes

The existing Gradle wrapper is now the unified entry point. `persistent-npcs` is an internal Gradle project while retaining its authoritative PowerShell build, resource validator and executable regression harness. The root `check` task runs RPG tests, `nativeControlTest`, CanvasUI tests, NPC resource validation and all retained NPC gates; root `build` emits the deployable `Hywind.jar`.

Notable implementation files and groups:

- `src/main/java/com/inigmasgames/hywind/HywindPlugin.java`: unified production lifecycle;
- `src/main/java/com/inigmasgames/hytalerpg/phase00/RpgDiagnosticsModule.java`: narrow diagnostics bridge without reviving the old plugin;
- `persistent-npcs/`: imported R170 source, resources, tests and build integration;
- `build.gradle`, `settings.gradle`, `gradle.properties`: unified package/test wiring and pinned runtime;
- `tools/Test-HywindPackage.ps1`: exact manifest, class, resource, generated-UI and compatibility audit;
- `tools/Run-HywindSmoke.ps1`: one-plugin isolated and copied-data runtime smoke;
- `tools/Deploy-Hywind.ps1`: stopped-runtime backup, journaled cutover and automatic artifact recovery;
- `tools/Restore-Hywind.ps1`: artifact or full-save recovery;
- `tools/Test-HywindDeployment.ps1`: installed-hash and two-cycle post-deployment validation;
- `README.md`: Hywind build, test, smoke, deploy, rollback and icon operations.

The imported standalone `persistent-npcs/install.ps1` now fails immediately with a Hywind migration message; its former implementation remains below the guard only as provenance. Current operational documentation points exclusively to `Deploy-Hywind.ps1`, so a normal build or documented deploy cannot reinstall a split NPC plugin.

### Collision decisions

The source/package collision audit found no Java class collision. Resource collisions were resolved explicitly:

- the three prior `manifest.json` files became one root Hywind manifest;
- RPG and NPC `Server/Languages/en-US/server.lang` content was merged, with no duplicate language keys;
- NPC generated `NpcSection9.ui` through `NpcSection1024.ui` are produced for both inventory page directories (2,032 generated files; 2,048 total section files including checked-in 1–8);
- NPC `config.json`, absent from the imported source snapshot but present in the authoritative installed R170 JAR, was recovered byte-semantically as a packaged default;
- pre.3 ItemGrid compatibility uses `EphemeralIconPatch`/`EphemeralIconAnchor` in source rather than a post-package mutation;
- nested legacy manifests and NPC's separate language file are excluded from internal subproject output.

Package audit result:

| Property | Value |
| --- | ---: |
| JAR bytes | 12,027,569 |
| ZIP entries | 5,397 |
| classes | 2,075 |
| CustomUI documents | 2,088 |
| generated NPC sections | 2,032 |
| root manifests | 1 |

The package excludes Hytale server/assets, test fixtures, saves, credentials, private profile data, model weights and separate legacy plugin entry classes.

### Persistence race found during merger validation

Repeated R090 H6 campaigns exposed a real pre-existing Windows persistence race: independent stores shared a fixed `relationships.json.tmp`; after making temp names unique, OneDrive/Windows could still transiently deny replacement of the destination. The common JSON writer now:

1. serializes replacement per normalized target path using bounded lock striping;
2. writes an immutable UUID-named temp file;
3. retries only the final replacement for a bounded interval when Windows transiently denies it;
4. propagates failure after the retry budget and never acknowledges a write before replacement succeeds;
5. removes only its own temporary file.

Twenty consecutive H6 stress repetitions then passed (2,200 deterministic campaign decisions), followed by the complete retained suite. No persistence assertion or expected behavior was weakened.

## 4. Verification record

All commands below exited `0`.

| Command/gate | Result |
| --- | --- |
| `.\gradlew.bat clean check build --no-daemon --console=plain` | PASS in 3m04s; 27 tasks executed |
| RPG JUnit task | 2,361 tests, 0 failures/errors/skips |
| RPG `nativeControlTest` | 67 tests, 0 failures/errors/skips |
| CanvasUI JUnit task | 28 tests, 0 failures/errors/skips |
| NPC retained harness | 149 named executable test invocations; all deterministic gates PASS |
| NPC exact release-resource validator | PASS; 590 immutable canonical cards and installed-registry coverage |
| CustomUI validator | PASS; 57 source documents |
| Unified package audit | PASS |
| H6 persistence stress | PASS, 20/20 repetitions |
| Isolated Hywind-only server smoke | PASS |
| Copied legacy-data Hywind-only smoke | PASS |
| Deployment dry run | PASS; no state change |
| Full deployment/rollback rehearsal | PASS; all 599 files, bytes and original JAR hashes restored |
| Live deployment | PASS; installed/candidate hashes equal |
| Live post-deployment start/stop | PASS twice; clean startup and shutdown both times |

The NPC Gradle `test` task is deliberately disabled because those retained tests are executable assertion-enabled Java main classes, not JUnit classes. Coverage is provided by the mandatory `retainedTests` harness above; this is not an omitted suite. The retained harness skipped only live local-model tests. Connected-client rendering/input/voice checks are separately pending.

Isolated and copied-data smoke each proved:

- one first-party JAR and one discovered `InigmasGames:Hywind` identity;
- selected old gameplay, presentation and character data roots;
- Canvas, RPG and NPC/Orbis internal startup markers;
- no discovery of `InigmasGames:HytaleRPGPhase00Audit`, `InigmasGames:CanvasUI`, or `InigmasGames:ImmersiveNPCs` as plugins;
- clean `HYWIND_SHUTDOWN` and process exit.

The two deployed-save cycles reproduced the same discovery/start/shutdown behavior. Data-root file counts were unchanged across both cycles (168 NPC, 13 Canvas, 34 RPG); expected logs/runtime state increased byte counts.

## 5. Deployment and rollback

Installed artifact:

`C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\Hywind.jar`

SHA-256:

`B07071798C994FE3CF1062C138BA38AB20B3108B529ABE8A74384D0566A751C1`

Retired from the active load path into `...\hywind-deploy-20260919T153302Z\retired-artifacts`:

| Artifact | Baseline SHA-256 |
| --- | --- |
| `CanvasUI-0.1.0.jar` | `DCB0C105F73AA421A3B2ECE8638BD7334BC0F1839AF71830597186086B5CBD71` |
| `HyARPG.jar` | `A06C7DC040665A0A3D7D8236B9E6B2EF8978654748276656F2C11E5E6E39D6F7` |
| `ImmersiveNPCs-0.6.3-R170-CREATIVE-FULL-PROFILE-GENERATION.jar` | `ED8BFDD6006B685F52FE1A466BBE6FAE98DA08E1E1BBFD826626EB9C8C2ED1BE` |

`HYTALEDEVLIB-0.5.0.jar` remains active as an optional external dependency; its hash is `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230`. Unrelated built-in mod directories were not changed. Inert historical `.icons.lock` files remain but are not JARs and cannot register plugins.

The rollback rehearsal used the actual baseline save copy, deployed Hywind, then performed a full-save restore. It recovered 599 files, 484,697,159 bytes, and all four original JAR hashes exactly. For the live deployment, the operation journal, complete backup, retired JARs and deployment result are retained under the final backup directory. The recovery command is documented in `README.md`; a live full restore requires the explicit `-ConfirmLiveRestore` guard.

## 6. Remaining checks and known limitations

Post-merger connected validation is **UNVERIFIED** and is the only reason status is `DEPLOYED / VALIDATION PENDING` instead of `COMPLETE`. A connected session must still exercise:

1. `/rpg` commands, loadout/progression persistence, native ability slots, representative melee/projectile/channel/summon skills, resource/cooldown HUD, damage and restart/rejoin;
2. CanvasUI open/close, cursor capture, scaling, node/library drag-and-drop, links/joints, death/disconnect/world-exit cleanup and restored gameplay input;
3. `/npc` UI, existing profiles/IDs, appearance and gear previews, native inventory transfers, save/restart/rejoin and no duplicate NPCs;
4. configured Orbis providers, stale-result cancellation, interruption, voice/spatial playback, and expected degraded/offline isolation.

No automated gate is failing. Compilation emits installed-API deprecation warnings for methods already used by the baseline; no version was widened and no warning was suppressed. The external provider/local-model tests remain environment-dependent and were not represented as passing. Until the connected list above passes, this build should be treated as a controlled validation build rather than unrestricted release completion.
