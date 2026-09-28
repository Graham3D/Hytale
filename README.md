# HyARPG

HyARPG is the independently deployable Hytale ARPG runtime. It owns combat,
skills, passives, progression, equipment, encounters, the skill tree, CanvasUI,
HUD integration, and native Ability4 support.

HyARPG does not package or start ImmersiveNPCs, Tavern Management, Orbis,
Nemotron/Ollama integration, speech recognition, speech synthesis, NPC cognition,
NPC persistence, provider configuration, voice workers, or model-training assets.
The historical `persistent-npcs` and `hytale-taverns` source directories remain
available for their own products, but are not Gradle projects or dependencies of
the HyARPG build.

Current identity:

- plugin: `InigmasGames:HyARPG`
- bootstrap: `com.inigmasgames.hywind.HyArpgPlugin`
- artifact: `build/libs/HyARPG.jar`
- revision: `R137`
- Hytale API: `0.7.0-pre.4`

Existing save-data roots are deliberately retained without migration:

- `mods/InigmasGames_HytaleRPGPhase00Audit`
- `mods/InigmasGames_CanvasUI`

## Build and verify

Close Hytale, then run:

```powershell
Set-Location "C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale"
.\gradlew.bat clean check build
```

The build runs the deterministic RPG and CanvasUI tests, the native-control
cohort, CustomUI validation, asset-grant audit, and the standalone-JAR ownership
audit.

For an isolated server smoke:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Run-HyArpgSmoke.ps1
```

Preview or perform the narrow RPG-save deployment:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Deploy-HyArpg.ps1 -DryRun
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Deploy-HyArpg.ps1
```

Deployment moves the former merged `Hywind.jar` to an explicit rollback folder,
installs exactly one `HyARPG.jar`, and does not write any save/config directory.

## Owner-managed RPG icons

Put canonical `Skill*.png` and `Passive*.png` files in `art/Skills` and
`art/Passives`, close Hytale, and run `Update RPG Icons.cmd`. The updater validates
the standalone HyARPG manifest and never edits save data.

## Engineering records

- Checkpoint A ownership/separation report: `docs/hyarpg/checkpoint-a-separation.md`
- RPG Stage 13 history: `docs/stage-13/`
- CanvasUI history: `docs/canvas-ui/development-report.md`
- ImmersiveNPCs documentation: `persistent-npcs/docs/` (independent product)
