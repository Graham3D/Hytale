# Hywind / HyARPG development baseline

The current Chapter I Stage 3 engineering candidate is **R262C1S3-CANDIDATE**
(`0.2.0-R262C1S3-CANDIDATE`) on `codex/chapter1-stage3-elite-restoration`,
targeting Hytale `0.7.0`. It retains the Stage 1 release-world identity and
encounter recovery and the Stage 2 version-pinned native patch and combat
receipts. Stage 3 verifies the existing natural Elite birth and affix pipeline
against the release world and pins the native spawn/flock binary seam. No new
spawn or affix framework was needed: the reviewed release classes are byte-for-byte
identical to their functioning pre-release counterparts. The paired artifacts
remain offline; connected QA is pending. See the [Stage 3 report](docs/release-migration/STAGE_3_NATURAL_ELITE_AND_AFFIX_RECOVERY.md).
It builds on the unmerged R262QA1 bulk attribute candidate and carries the R261
durable Cyclone, persistent Reflective Ward, and Shared Aegis work. Durable
Cyclone is enabled by default in the production entrypoint for normal Hytale
launches. `-Dhywind.cyclone.durable=false` is an explicit rollback option.
Inventory now offers +1, +5, +25, and Max allocation choices through one
server-authoritative progression mutation per click. R261QA2 also registers
Mana Before Life before native Health application.
The Chapter I skill bar candidate adds four saved CanvasUI skill nodes. BAR3
restores the missing durable Cyclone and shared-aegis player preloads that
blocked initial HUD/rune installation; connected verification is pending.
The inventory candidate resolves inactive native hand slots so staff drops
from the spatial bag can reach the protected weapon transfer. Connected
verification is pending. Native Ability2/3 remain the only supported
independent Hywind inputs; the third
and fourth nodes are visibly unbound because Ability4 belongs to held-item
interactions and there is no public Ability5 input.
ReadyPath/JFR capture is optional. See
the [R261 requirement audit](docs/R261_FINAL_MATRIX.md),
[durability contract](docs/CYCLONE_DURABILITY.md), and
[baseline](docs/DEVELOPMENT_BASELINE.md) for verification status.

The GitHub `main` gameplay baseline before R261 integration is **R250-U7P5B**
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
The Stage 1 source targets Hytale `0.7.0` and the release-candidate
`UserData/Saves/Hywind` save. The pre-release `RPG` save remains untouched.
No plugin identity or save-schema migration is part of Stage 1.

## Offline verification

Run validation from an isolated worktree based on the latest verified source:

```powershell
.\gradlew.bat --offline --no-daemon check --console=plain
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-HyArpgPackage.ps1 -JarPath build/libs/HyARPG.jar -ExpectedVersion 0.2.0-R262C1INV1-CANDIDATE -ExpectedRevision R262C1INV1-CANDIDATE -ExpectedServerVersion '=0.7.0'
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
