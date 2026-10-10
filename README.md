# Hywind / HyARPG development baseline

The current GitHub `main` gameplay baseline is **R250-U7P5B**
(`0.2.0-R250-U7P5B`). [PR #1](https://github.com/Graham3D/Hytale/pull/1)
merged when the recovery branch fast-forwarded `main` to
`e4927d06e9a3071bc2023aafec68b8fa68d76268`. Later documentation commits
do not create a new gameplay revision. [Guardian PR #2](https://github.com/Graham3D/Hytale/pull/2)
merged the engineering safeguards into `main` at
`401700bc6b2eb2bb509ab6cd1ccfd2b90cc8662a`; R250-U7P5B remains the
gameplay revision. Read [the development baseline](docs/DEVELOPMENT_BASELINE.md)
and [engineering rules](AGENTS.md) before starting work; verify the actual
branch HEAD and status rather than relying on a revision label alone.

HyARPG owns combat, skills, passives, progression, equipment, encounters,
inventory, the skill tree, CanvasUI, HUD, native Ability4 support, and Tavern
gameplay. `hytale-taverns` contributes to the HyARPG build. ImmersiveNPCs is a
**separate mod**: its `persistent-npcs` source, AI runtime, data, and resources
are not packaged into `HyARPG.jar`. The optional ImmersiveNPCs bridge does not
change that ownership boundary.

The internal plugin identity remains `InigmasGames:HyARPG`, with entrypoint
`com.inigmasgames.hywind.HyArpgPlugin`. The build artifact is
`build/libs/HyARPG.jar`; the active RPG save uses the filename `Hywind.jar`.
The source targets Hytale `0.7.0-pre.5.1`. No plugin identity or save migration
is part of this recovery.

## Offline verification

Run validation from an isolated worktree based on the latest verified source:

```powershell
.\gradlew.bat --offline --no-daemon check --console=plain
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-HyArpgPackage.ps1 -JarPath build/libs/HyARPG.jar -ExpectedVersion 0.2.0-R250-U7P5B -ExpectedRevision R250-U7P5B
```

The full `check` compiles source and runs RPG, native-control, CanvasUI, and
Tavern tests, the R250 JSON compatibility audit, resource/reference checks,
and package validation. The package validator verifies the plugin identity and
absence of ImmersiveNPCs payload. Do not launch a standalone Hytale server for
QA. Connected-game acceptance is performed by the user in the main game and is
separate from offline validation.

Deployment requires an explicit request. Follow `AGENTS.md` for backup,
checksum, and the single active RPG save; never treat a local build or branch
publication as a deployment. Preserve modified and untracked work in the
original primary checkout.

## References

- [Gameplay and QA commands](docs/COMMANDS.md)
- [Engineering Guardian](docs/GUARDIAN.md)
- [Focused regression testing, JaCoCo, and SpotBugs](docs/ENGINEERING_ANALYSIS.md)
- [Deployment and revision ledger](docs/DEPLOYMENT_REVISIONS.md)
- [Hywind / ImmersiveNPCs split history](docs/HYWIND_SPLIT_BOUNDARY_RECOVERY.md)
- [Independent ImmersiveNPCs source](persistent-npcs/README.md)
