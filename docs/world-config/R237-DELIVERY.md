# R237-U7P5 world configuration delivery

Source revision: R237-U7P5. Candidate SHA-256: `43AD493E6F9B8D503B9209593D47DD4F1318F17CD654C9DC3FC4F28336B05928` (`build/libs/HyARPG.jar`, installed as `Hywind.jar`). Connected acceptance remains pending owner gameplay QA.

Deployment: the active `Saves/RPG/mods/Hywind.jar` matches that SHA-256. The previous JAR (SHA-256 `6FBFBE05BC2BA46A2F2EF6D6E74C6B8FD58DBC1057B8E69B846392928A33FE7B`) is backed up outside `Saves` at `C:/Users/Zemio/.codex/deploy-backups/R237-20261008T232720310Z/Hywind-before.jar`. The new active save-root `world-config.json` contains the migrated 8× density. No other save or mod was replaced.

## Implementation

- Added save-root `world-config.json` with automatic import of the current 8× legacy density value on first load, atomic edit/reload, strict rejection of unsupported controls, and `/rpg worldconfig status|reload`.
- Bound rarity, pack limits, rarity Health/direct damage, random affix weights and disables, immunity chances, and affix XP/equipment-quantity bonuses to their existing Master Enemies owners for **new** encounters. Archived balance revisions keep active and recovered actors on their birth policy. No affix implementation changed.
- Added native per-environment hostile/wildlife selection adjustment through `WorldNPCSpawnStat.expected`, preserving native capacities, jobs, spawn eligibility, native flock placement and exempt species. The 65/35 split is a soft controlled-subset target; it is not a live quota or 40% wildlife admission block.
- Added exact installed-asset role classification and bounded population weight/counter events to `/rpg spawntrace`.

## Offline verification

- `gradlew :test --offline`: **3,427 tests passed**.
- Focused config/population/affix/capacity tests after final diagnostic edits: passed.
- `gradlew :jar --offline`: passed.
- `Test-HyArpgPackage.ps1`: passed, 19,299 entries; no ImmersiveNPCs payload.
- The historical `Test-U7P5AssetCompatibility.py` R200 allowlist flags already-shipped R230–R236 assets, so it is not a valid R237-vs-R236 gate. Direct comparison against the installed R236 `Hywind.jar` shows exactly two added JSON resources (`rpg/spawning/native-population-roles-v1.json`, `rpg/world-config-default.json`), no removed resources and only the expected manifest change. Existing Hytale asset files are unchanged.
- No Hytale server or copied save was launched or created. The current `AGENTS.md` owner QA workflow governs connected acceptance.

## Connected gate

After restarting the active RPG world: `/rpg worldconfig status` should show 8× and 65/35. `/rpg spawns status` should match. Start `/rpg spawntrace start`, fly through naturally spawning mixed hostile/wildlife environments and inspect `POPULATION_WEIGHT`, `POPULATION_ROLE_WEIGHT`, native jobs, publication, and Elite fallback/rollback. Change shares to another valid sum of one and reload, then restore 0.65/0.35; invalid input must leave the previous active policy unchanged. The native job, terrain and global population limits remain authoritative; already full environments may not shift immediately.

Unsupported proposed fields and exact integration limits are listed in [OPERATOR.md](OPERATOR.md).
