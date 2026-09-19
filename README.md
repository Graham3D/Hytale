# Hywind

Hywind is the single first-party Hytale plugin for this project. It combines the
ARPG/RPG runtime, CanvasUI presentation/input framework, persistent Immersive NPCs,
Orbis intelligence integrations, and Tavern Management under one lifecycle owner:

- plugin identity: `InigmasGames:Hywind`
- bootstrap: `com.inigmasgames.hywind.HywindPlugin`
- artifact: `build/libs/Hywind.jar`
- pinned Hytale API: `0.7.0-pre.3.1`

The merger deliberately preserves the existing save data roots instead of moving or
duplicating player/world data:

- `mods/InigmasGames_HytaleRPGPhase00Audit`
- `mods/InigmasGames_CanvasUI`
- `mods/ImmersiveNPCs`
- `mods/InigmasGames_Taverns`

`HytaleDevLib` remains an optional external dependency. Tavern, Kitchen and Bedroom
Core data remain in their established schema-3 root; no cross-world coordinate data
is imported or rewritten by the merger.

## Build and verify

Close Hytale, then run:

```powershell
Set-Location "C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale"
.\gradlew.bat clean check build
```

The root check includes the RPG retained and native-control suites, CanvasUI tests,
the retained deterministic NPC suite, Tavern persistence/Core/accounting tests,
installed-asset validation, CustomUI validation, and the merged JAR audit.

For an isolated server smoke:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\tools\Run-HywindSmoke.ps1"
```

For a cutover plan with no writes:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\tools\Deploy-Hywind.ps1" -DryRun
```

Historical cohort deployment scripts target superseded split artifacts and must not
be used for Hywind deployment.

## Owner-managed RPG icons

Put canonical `Skill*.png` and `Passive*.png` files in `art/Skills` and
`art/Passives`, close Hytale, and run `Update RPG Icons.cmd`. The updater now targets
the installed `Hywind.jar`, validates the unified manifest, takes a content-addressed
backup, and never touches save data.

## Engineering records

- Hywind merger/cutover: `docs/hywind/merge-report.md`
- RPG Stage 13 history: `docs/stage-13/`
- CanvasUI history: `docs/canvas-ui/development-report.md`
- persistent NPC retained documentation: `persistent-npcs/docs/`
