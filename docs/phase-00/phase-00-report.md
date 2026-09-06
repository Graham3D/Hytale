# Stage 00 — Evidence & Feasibility report

## Outcome

`exitGate: BLOCKED`

The installed 0.6.3 server can load both HTDevLib 0.5.0 and the isolated Phase
00 probe, and the requested catalogs were exported from the installed assets.
The build and server-load gates pass. The real-client interaction gates do not:
this environment cannot operate or capture the native Hytale client, and four
requirements have no supported public-server API in the audited build.

No Phase 01 work was started. No attributes, combat, skills, passives,
persistence, XP, progression, or production-save data were implemented.

## Repository and build identity

- Repository before audit: **absent**; the workspace had specifications and a
  prior handoff archive, but no Git repository or buildable mod source.
- Audit branch: `phase-00/evidence-feasibility`.
- Probe/evidence commit: `6e3996bae9b217ae04646f0ab8f454c8e65b562c`.
- Active patchline: `release`.
- Server/client: Hytale `0.6.3`, revision
  `ff802bf5a538f7e4b1df43a575c72f9d2bebb504`.
- Compile SDK: the installed `HytaleServer.jar` public API, pinned by SHA-256.
- Java: Temurin OpenJDK/Javac `25.0.4`.
- Exact paths, sizes, hashes, source commits, and the master-spec hash are in
  `evidence/phase-00/environment.json`.

## What changed

The new `HytaleRPG` repository contains:

- a Java 25 Gradle build pinned to the installed release jar;
- an audit-only plugin with six `/rpgp00-*` commands;
- three temporary `.ui` surfaces (Character, Link canvas, HUD);
- read-only native-stat and HTDevLib comparison probes;
- reversible HUD visibility snapshot/restore logic;
- API and installed-asset exporters;
- machine-readable capability, environment, input, verification, and smoke
  evidence; and
- install/uninstall scripts scoped to the dedicated `RPG` save.

The only external save mutation is the optional, removable audit jar documented
below. It is installed only in the `RPG` save, with its exact hash and preserved
preexisting files recorded in `evidence/phase-00/installation.json`. Existing
HTDevLib and mod-state files are not modified.

## Verified APIs and classes

The exact `javap` output is preserved in
`evidence/phase-00/api/javap-public-api.txt` and
`evidence/phase-00/api/htdevlib-public-api.txt`.

Verified current-build surfaces include:

- UI pages: `CustomUIPage`, `InteractiveCustomUIPage`, `PageManager`,
  `UICommandBuilder`, `UIEventBuilder`, `CustomPageLifetime`, and
  `CustomUIEventBindingType`;
- HUD: keyed `CustomUIHud`, `HudManager.addCustomHud`,
  `HudManager.removeCustomHud`, visibility get/set/show/hide/reset, and native
  `HudComponent.Health`, `.Mana`, and `.Stamina`;
- authority: `EntityStatMap`, `EntityStatValue`, and
  `DamageSystems.executeDamage` overloads;
- input enums: `InteractionType.Ability1`, `.Ability2`, and `.Ability3`;
- pages/windows: distinct `Page.Inventory` and `Page.Custom`, plus
  `PageManager.openCustomPageWithWindows`; and
- raw world pointer events: `PlayerMouseButtonEvent` and
  `PlayerMouseMotionEvent` (not proven to operate while a Custom page owns the
  pointer).

HTDevLib exposes `StatsHelper`, `UIHelper`, `ParticleHelper`, `SoundHelper`,
`InventoryHelper`, and `DeathHelper`. It declares the older server target
`2026.02.17-255364b8e`; 0.6.3 treats that legacy form as a wildcard and logs a
warning. Nevertheless, its plugin setup and enable markers both passed in the
0.6.3 smoke run. The `/rpgp00-stats` command is the safe, read-only helper
exercise awaiting a player client.

## Capability verdicts

| Requirement | Verdict | Evidence/reason |
|---|---|---|
| Server + HTDevLib load | Supported | Both plugins discovered, set up, and enabled in `server-smoke-summary.json`. |
| Character Custom page lifecycle | Compiles/server loads; client pending | `/rpgp00-character`, Escape/reopen checklist. |
| Keyed HUD + native HUD hide/restore | Compiles/server loads; client pending | `/rpgp00-hud` and `/rpgp00-hud-clear`; original set is snapshotted. |
| Native stat authority | Supported API; client read pending | Direct `EntityStatMap`/`EntityStatValue`, compared with HTDevLib read helpers. |
| Native damage hooks | Supported API, not exercised | `DamageSystems.executeDamage`; gameplay changes forbidden in Stage 00. |
| Ability 1–3 types | Supported enum | Exact installed `InteractionType`. |
| Ability 4 | Unsupported | `BLOCKER_ABILITY4_NO_INTERACTION_ENUM`: no `InteractionType.Ability4`. |
| Fixed Link canvas + activation events | Compiles/server loads; client pending | `/rpgp00-link`; three `Activating` bindings and a `KeyDown` binding. |
| Node drag, pointer pan, pointer capture | Unsupported on exposed page event surface | No page-scoped generic press/move event with coordinates and no capture API. |
| Dynamic splines/16-segment fallback | Unproven | No public vector/path primitive; rotation/layout needs client proof. |
| Global C/K handlers and C camera unbind | Unsupported public server API | Only page-scoped `KeyDown`; client bindings are client-owned. |
| Native Inventory augmentation | Unsupported public server API | Custom pages can open with windows, but no injection hook exists for `Page.Inventory`. |
| Installed asset IDs | Supported archive evidence | Per-record path/size/SHA-256 catalogs were exported. |
| Master candidate particle IDs | 95/95 exact archive matches | Playback still needs a real client. |

The complete machine-readable verdicts and named blockers are in
`evidence/phase-00/build-capabilities.json`.

## Input audit

The local `Settings.json` was read but not changed. Its current serialized
overrides show Ability1 on SDL scancode 20 (Q), Ability3 on scancode 21 (R),
SwitchCameraMode on keycode 118 (V), and Inventory on scancode 43 (Tab).
Ability2 has no serialized user override, which neither proves nor disproves its
active default. No C binding is serialized, so the reported C camera behavior
cannot be assigned to a supported server-visible owner. Details are in
`evidence/phase-00/input-settings-snapshot.json`.

The blocker is architectural: the official strategy keeps mods server-side and
notes that some client behavior is not exposed. The audited server API provides
page-local `KeyDown`, not global arbitrary key registration or client binding
remapping. Phase 00 therefore did not patch the client or overwrite settings.
See Hytale's [modding strategy](https://hytale.com/news/2025/11/hytale-modding-strategy-and-status),
the [CustomUIHud API](https://docs.hytale.com/api/com/hypixel/hytale/server/core/entity/entities/player/hud/CustomUIHud),
and [UICommandBuilder API](https://docs.hytale.com/com/hypixel/hytale/server/core/ui/builder/UICommandBuilder).

## Asset catalogs

Catalogs were generated directly from the pinned `Assets.zip`. Every record has
category, filename ID, relative ID, archive path, byte size, and SHA-256.

| Catalog | Count |
|---|---:|
| Particle systems | 602 |
| Particle spawners | 1,762 |
| Sound definitions | 1,803 |
| Items | 3,672 |
| Item animations | 146 |
| Character animations | 2,309 |
| NPC roles | 1,000 |
| NPC groups | 72 |

All 95 distinct `Candidate SystemIds` extracted from the master specification
have exact `.particlesystem` filename matches. This is identity evidence, not
visual playback approval.

## Test record

- `tools/Verify-Phase00.ps1`: **pass**. Clean build succeeded; the jar contains
  manifest and all three UI assets; static scan found no damage/stat mutation
  calls. Final probe jar SHA-256 is recorded in `verification.json`.
- Gradle `clean build`: **pass** (no test source; compile/resource/jar/check all
  completed). Two current APIs used by the probe are deprecated but functional:
  adventure permission-group assignment and string-based stat lookup.
- `tools/Run-Phase00Smoke.ps1`: **pass for named plugins**. Hytale 0.6.3,
  HTDevLib 0.5.0, and the Phase 00 probe were discovered, set up, and enabled.
  Bare-mode `stop` returns process code 9 and emits unrelated core dependency /
  transport shutdown noise; the full log is in `server-smoke.txt`.
- Full `--validate-assets`: **not a valid clean gate in this isolated run**. The
  stock Instances validator fails because the bare audit directory has no valid
  universe paths; failures were in bundled Instance assets, not the probe pack.
- Real-client UI/input/HUD/playback checks: **not run**. There are no honest
  screenshots or timings to attach. The exact checklist is
  `docs/phase-00/client-verification.md`.

## First failing gate and rollback

The first unsatisfied exit requirement is real-client proof for UI/HUD/input.
Run the installed `RPG` save and start with `/rpgp00-capabilities`; on the first
failure, stop and retain that save's newest server log and a screenshot.

Rollback is exact and reversible:

1. Run `/rpgp00-hud-clear` before leaving if the HUD probe was enabled.
2. Exit the `RPG` save.
3. Run `tools/Uninstall-Phase00Probe.ps1`, which removes only
   `HytaleRPG-Phase00-Audit-0.0.1.jar`.
4. HTDevLib and existing save state remain untouched.

## Exit decision

Stage 00 is **not greenlit**. Phase 01 must not begin until the real-client
checklist is executed and the owner decides how to resolve or explicitly accept
the Ability4, global C/K, Link pointer/spline, and native Inventory blockers.
