# Merged HyARPG + Tavern

This artifact is the merged gameplay product. It owns HyARPG combat, skills,
passives, progression, equipment, encounters, the skill tree, CanvasUI, HUD and
native Ability4 support, plus Tavern cores, comfort, service, prepared food,
table serving, patrons, and Tavern persistence.

The merged artifact does not package or start ImmersiveNPCs, PersistentNPCs,
Orbis, Nemotron/Ollama integration, speech recognition, speech synthesis, NPC
cognition, ImmersiveNPC persistence, provider configuration, voice workers, or
model-training assets. `persistent-npcs` remains an independent source product;
`hytale-taverns` is a dependency of this merged gameplay build and has no AI
runtime dependency.

Current identity:

- plugin: `InigmasGames:HyARPG`
- bootstrap: `com.inigmasgames.hywind.HyArpgPlugin`
- artifact: `build/libs/HyARPG.jar`
- revision: `R138`
- Hytale API: `0.7.0-pre.4`

Existing save-data roots are deliberately retained without migration:

- `mods/InigmasGames_HytaleRPGPhase00Audit`
- `mods/InigmasGames_CanvasUI`
- `mods/InigmasGames_Taverns`

## Build and verify

Close Hytale, then run:

```powershell
Set-Location "C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale"
.\gradlew.bat clean check build
```

The build runs deterministic RPG, CanvasUI, and Tavern tests, the native-control
cohort, CustomUI validation, asset-grant audit, and merged-JAR ownership audit.

For an isolated server smoke:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Run-HyArpgSmoke.ps1
```

Preview or perform the narrow RPG-save deployment:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Deploy-HyArpg.ps1 -DryRun
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\Deploy-HyArpg.ps1
```

Deployment moves the prior merged gameplay artifact to an explicit rollback
folder and installs exactly one `HyARPG.jar`. It never deploys ImmersiveNPCs.

## Owner-managed RPG icons

Put canonical `Skill*.png` and `Passive*.png` files in `art/Skills` and
`art/Passives`, close Hytale, and run `Update RPG Icons.cmd`. The updater validates
the standalone HyARPG manifest and never edits save data.

## Engineering records

- Checkpoint A ownership/separation report: `docs/hyarpg/checkpoint-a-separation.md`
- RPG Stage 13 history: `docs/stage-13/`
- CanvasUI history: `docs/canvas-ui/development-report.md`
- ImmersiveNPCs documentation: `persistent-npcs/docs/` (independent product)
