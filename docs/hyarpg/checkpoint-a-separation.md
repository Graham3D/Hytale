# HyARPG / ImmersiveNPCs Separation — Checkpoint A

## Ownership audit

| File/component | Current purpose | Owner/disposition |
|---|---|---|
| `src/main/java/com/inigmasgames/hytalerpg/**` and RPG resources | Combat, skills/passives, progression, gear, HUD, Ability4, encounters | HyARPG; retained |
| `canvas-ui/**` | Shared UI/input framework used by HyARPG | Genuinely shared; embedded in HyARPG without its plugin bootstrap |
| `ReadyPathProbe` | Readiness instrumentation used directly by current RPG systems | Genuinely shared utility; copied unchanged into the HyARPG source surface |
| `persistent-npcs/**` | ImmersiveNPCs cognition, memory, profiles, perception, AI providers, voice, persistence, Orbis integration | ImmersiveNPCs; excluded from HyARPG settings, dependencies, packaging, bootstrap, and deployment |
| `hytale-taverns/**` | Tavern cores, comfort, service, food, patrons, UI, and persistence | Merged ARPG gameplay; retained and embedded without ImmersiveNPCs |
| `TavernsPlugin` inheritance | Used `PersistentNpcsPlugin` only as its Java/Hytale lifecycle parent | Rebased directly onto `JavaPlugin`; no AI lifecycle is started |
| Tavern spatial item policy | Tavern item transfers imported `persistentnpcs.api.SpatialPlayerItemPolicy` | Replaced by Tavern-owned neutral host contract, bound by HyARPG's spatial inventory owner |
| Tavern ReadyPath hooks | Tavern source referenced root merged-runtime diagnostics | Replaced by explicit `TAVERNS_SETUP ... aiIntegration=NONE` lifecycle evidence; gameplay is unchanged |
| root `HywindPlugin` | Merged lifecycle owner for RPG + Tavern + ImmersiveNPCs | Replaced by `HyArpgPlugin`, which owns RPG + CanvasUI + Tavern only |
| root Gradle dependencies/resource merge | Embedded ImmersiveNPCs and Tavern outputs in `Hywind.jar` | ImmersiveNPCs removed; CanvasUI and Tavern outputs are embedded |
| merged `Hywind.jar` deployment/smoke/package tools | Installed and validated the combined runtime | Replaced by HyARPG-only tools |
| `Metal_Orbis` and similar stock catalog identifiers | First-party Hytale asset names | Not the Orbis cognition/training system; retained |

## Boundary

The merged ARPG product has no compile/runtime dependency on ImmersiveNPCs. It
does not probe AI services, load AI/provider configuration, launch voice workers,
or create ImmersiveNPCs data. Tavern is an intentional gameplay dependency. Its
patrons use the official `Hytale:NPC` engine module and deterministic navigation;
that module is not PersistentNPCs or ImmersiveNPCs.

No Tavern gameplay capability was classified as ImmersiveNPCs-owned. The only
couplings were lifecycle inheritance, a misplaced inventory ownership callback,
and boot diagnostics. Consequently no Tavern feature needs the future optional
ImmersiveNPCs compatibility bridge at this checkpoint.

Checkpoint A does not alter the independent ImmersiveNPCs source tree, the
working base-game R170 installation, pre-release ImmersiveNPCs data, or Orbis
training/distillation artifacts. R137 remains the explicit pre-correction
rollback/reference point.

## Compatibility notes

The installed pre-release API is now `0.7.0-pre.4`, newer than the task's recorded
`0.7.0-pre.1`. R138 targets the installed API exactly. Existing RPG, CanvasUI,
and Tavern data roots are retained in place; no schema migration is performed.

## R138 corrected validation

- `gradlew check build --rerun-tasks`: PASS (2,610 RPG tests, CanvasUI,
  native-control tests, and all eight retained Tavern behavior harnesses).
- Merged package audit: PASS; 6,755 entries, 1,449 classes including 89 Tavern
  classes, 75 Ability4 item assets, and zero ImmersiveNPCs/AI payload entries.
- Isolated Hytale server boot with only the merged artifact plus optional
  HytaleDevLib: PASS; Tavern and ARPG setup/start/shutdown observed, no AI runtime
  observed, and no ImmersiveNPCs data root created.
- Candidate artifact SHA-256:
  `5DDAFB5BD0131DF1888DE70DE693ED0F9755123638F12EBAB64A2B8CB0710041`.
- R137 rollback/reference artifact before corrected deployment:
  `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`
  (`A303C8C9FB5670EB392B70D2D4F6D8C913EA2911DB7A8778443DFB10C24CEFDA`).
- Former merged R136 rollback artifact:
  `C:\HytaleRollback\HyARPG-Checkpoint-A-R136-20260928-122535\Hywind.jar`
  (`7AE998E6C96D391693E0DC5843AF037208A6D3A815E723CBCBA6EB99B05FA6CE`).
- Recursive content fingerprints for the RPG, CanvasUI, ImmersiveNPCs, and Tavern
  data roots matched before/after deployment.

Connected-client Ability4/HUD interaction was not automated during this checkpoint;
the production code/assets and their deterministic test coverage were preserved.
