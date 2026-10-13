# Hywind / HyARPG development baseline

HRO finalization engineering candidate: **R262C1HROF-CANDIDATE**
(`0.2.0-R262C1HROF-CANDIDATE`), Hytale `=0.7.0`, branch
`codex/hro-finalization`. It adds one-shot next-boot capture, bounded
owner reconnect/world continuity, labeled checkpoints, integrated
performance evidence and worker-side automatic reports. The 17 Stage 1–3
contracts remain intact. See [commands](docs/COMMANDS.md),
[finalization handoff](docs/HRO_FINALIZATION.md) and
[source/integration status](docs/DEVELOPMENT_BASELINE.md).
The verified implementation source `4372e178f4ed550de370aacdbba06767905d84f7`
is integrated into GitHub `main` and deployed to the active Hywind save with
a checksum-verified rollback copy. Connected gameplay and client smoothness
remain unverified. The primary checkout retains its owner-owned changes.

### Earlier engineering checkpoints

HRO engineering release: **R262C1HRO3-CANDIDATE**
(`0.2.0-R262C1HRO3-CANDIDATE`), Hytale `=0.7.0`, branch `codex/hro-stage3`.
The three-stage observatory includes the bounded cross-system recorder,
seventeen evidence contracts, immutable approved scenario baselines,
historical comparison/import, native fingerprints and offline UI parity seam.
Use `/rpg-trace patch start`, ordinary QA, then `/rpg-trace patch stop`.
See [commands](docs/COMMANDS.md), [Stage 3 release/owner handoff](docs/HRO_STAGE_3.md)
and [source/integration status](docs/DEVELOPMENT_BASELINE.md).
Offline qualification does not establish connected gameplay or client smoothness.
This pass does not deploy a JAR or launch the game/server.
The complete HRO source was integrated into GitHub `main` by a verified
non-force fast-forward to `64a3ba4e051c9b39a453a0c9ea98335eac6d72de`.
The full offline gate passed 4,292 tests plus strict static/package checks.
The primary Windows checkout remains preserved with its existing local changes.

HRO Stage 2 diagnostic candidate: **R262C1HRO2-CANDIDATE**
(`0.2.0-R262C1HRO2-CANDIDATE`) on `codex/hro-stage2`, descended from
HRO Stage 1 `92ac44fb23c515acd59f2470403dd9feca292b30` and reconciled with
integrated Chapter I Stage 4 `ce3865dd5f81bee95003cb5dde8dc2f959bdac2b`.
The operator-only `/rpg-trace patch start|mark|status|stop` captures seventeen
contract families in bounded bundles outside saves. See [HRO Stage 2](docs/HRO_STAGE_2.md)
for mechanical evidence, tested gaps and qualification, and
[the baseline record](docs/DEVELOPMENT_BASELINE.md) for exact source/status.
This candidate is not deployed or connected-QA accepted. Stage 3 remains separate.

The current Chapter I Stage 4 engineering candidate is **R262C1S4-CANDIDATE**
(`0.2.0-R262C1S4-CANDIDATE`) on `codex/chapter1-stage4-gear-loot-recovery`,
targeting Hytale `0.7.0`. Stage 4 restores frozen natural-death reward sharing
and requires a live native world item before automatic pickup. Existing loot
profiles, drop rates, generation and inventory custody are preserved. The
owner authorized safe integration into GitHub `main` after engineering
verification; deployment and connected gameplay acceptance remain pending.
See the [Stage 4 report](docs/release-migration/STAGE_4_NATURAL_GEAR_AND_REWARD_DELIVERY_RECOVERY.md)
and generated `build/stage4/integration.json` for the final integration receipt.

The inherited Stage 3 candidate is **R262C1S3V2-CANDIDATE**,
targeting Hytale `0.7.0`. It retains the Stage 1 release-world identity and
encounter recovery and the Stage 2 version-pinned native patch and combat
receipts. Stage 3 verifies the existing natural Elite birth and affix pipeline
against the release world and pins the native spawn/flock binary seam. No new
spawn or affix framework was needed: the reviewed release classes are byte-for-byte
identical to their functioning pre-release counterparts. V2 adds native ECS
staging-release assertions and overlapping durable-birth load coverage. The paired artifacts
remain offline; connected QA is pending. See the [Stage 3 report](docs/release-migration/STAGE_3_NATURAL_ELITE_AND_AFFIX_RECOVERY.md).
It builds on the R262QA1 bulk attribute candidate and carries the R261
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
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/Test-HyArpgPackage.ps1 -JarPath build/libs/HyARPG.jar -ExpectedVersion 0.2.0-R262C1S4-CANDIDATE -ExpectedRevision R262C1S4-CANDIDATE -ExpectedServerVersion '=0.7.0'
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
