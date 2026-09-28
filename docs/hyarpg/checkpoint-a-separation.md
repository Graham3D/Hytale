# HyARPG / ImmersiveNPCs Separation — Checkpoint A

## Ownership audit

| File/component | Current purpose | Owner/disposition |
|---|---|---|
| `src/main/java/com/inigmasgames/hytalerpg/**` and RPG resources | Combat, skills/passives, progression, gear, HUD, Ability4, encounters | HyARPG; retained |
| `canvas-ui/**` | Shared UI/input framework used by HyARPG | Genuinely shared; embedded in HyARPG without its plugin bootstrap |
| `ReadyPathProbe` | Readiness instrumentation used directly by current RPG systems | Genuinely shared utility; copied unchanged into the HyARPG source surface |
| `persistent-npcs/**` | ImmersiveNPCs cognition, memory, profiles, perception, AI providers, voice, persistence, Orbis integration | ImmersiveNPCs; excluded from HyARPG settings, dependencies, packaging, bootstrap, and deployment |
| `hytale-taverns/**` | Tavern gameplay whose current plugin inherits `PersistentNpcsPlugin` | Separate product; excluded because retaining it would retain the ImmersiveNPCs runtime |
| root `HywindPlugin` | Merged lifecycle owner for RPG + Tavern + ImmersiveNPCs | Replaced by standalone `HyArpgPlugin` |
| root Gradle dependencies/resource merge | Embedded ImmersiveNPCs and Tavern outputs in `Hywind.jar` | Removed; only CanvasUI output is embedded |
| merged `Hywind.jar` deployment/smoke/package tools | Installed and validated the combined runtime | Replaced by HyARPG-only tools |
| `Metal_Orbis` and similar stock catalog identifiers | First-party Hytale asset names | Not the Orbis cognition/training system; retained |

## Boundary

HyARPG has no compile/runtime dependency on ImmersiveNPCs or Tavern. It does not
probe AI services, load AI/provider configuration, launch voice workers, or create
ImmersiveNPCs data. The `Hytale:NPC` dependency is the official engine NPC module
used by HyARPG encounters/summons; it is not an ImmersiveNPCs dependency.

Checkpoint A intentionally does not alter the independent ImmersiveNPCs/Tavern
source trees, the working base-game R170 installation, pre-release ImmersiveNPCs
data, or Orbis training/distillation artifacts.

## Compatibility notes

The installed pre-release API is now `0.7.0-pre.4`, newer than the task's recorded
`0.7.0-pre.1`. R137 targets the installed API exactly. Existing RPG and CanvasUI
data roots are retained in place; no schema migration is performed.

## R137 validation

- `gradlew clean check build --rerun-tasks`: PASS (2,610 RPG tests plus CanvasUI
  and native-control tests).
- Standalone package audit: PASS; 6,415 entries, 1,360 classes, 75 Ability4 item
  assets, zero ImmersiveNPCs/Tavern classes or AI payload entries.
- Isolated Hytale server boot with only HyARPG plus optional HytaleDevLib: PASS;
  setup/start/shutdown observed, no AI runtime observed, no ImmersiveNPCs data root
  created.
- Installed artifact SHA-256:
  `A303C8C9FB5670EB392B70D2D4F6D8C913EA2911DB7A8778443DFB10C24CEFDA`.
- Former merged R136 rollback artifact:
  `C:\HytaleRollback\HyARPG-Checkpoint-A-R136-20260928-122535\Hywind.jar`
  (`7AE998E6C96D391693E0DC5843AF037208A6D3A815E723CBCBA6EB99B05FA6CE`).
- Recursive content fingerprints for the RPG, CanvasUI, ImmersiveNPCs, and Tavern
  data roots matched before/after deployment.

Connected-client Ability4/HUD interaction was not automated during this checkpoint;
the production code/assets and their deterministic test coverage were preserved.
