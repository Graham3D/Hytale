# Hywind merger and deployment report

Status: **DEPLOYED / CONNECTED VALIDATION PENDING**
Candidate/deployed SHA-256: `B16870EC46996B35E009922FA48BBB9D49EDAB070A16E13CFB945DDC993371A1`
Version/revision: `0.1.0-merge.2` / `R047`
Deployment time: `2026-09-19T17:11:46.4529513Z`

Hywind now contains the required RPG, CanvasUI, Immersive NPC/Orbis, and Tavern Management subsystems behind one manifest and one Hytale plugin lifecycle. `Hywind.jar` is installed as the only active first-party project JAR in the RPG save. Automated tests, package validation, fresh and copied-data server smokes, rollback rehearsal, live deployment, and two post-deployment restart cycles passed. Connected-client rendering/input/gameplay QA remains explicitly unverified.

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

`B16870EC46996B35E009922FA48BBB9D49EDAB070A16E13CFB945DDC993371A1`

Full stopped-save backup and deployment journal:

`C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260919T171145Z`

The backup contains 599 files and 486,658,752 bytes. The previous merge.1 `Hywind.jar` was retired there with SHA-256 `B07071798C994FE3CF1062C138BA38AB20B3108B529ABE8A74384D0566A751C1`.

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
