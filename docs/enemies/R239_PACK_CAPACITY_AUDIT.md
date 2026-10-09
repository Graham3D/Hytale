# R239 pack-capacity and native-extension audit

## Connected R238 evidence and save inventory

The two-minute trace counted 419 intercepted native groups, 273 rarity decisions, two pack-reservation decisions, three early `NATIVE_EXTENSION_UNAVAILABLE` fallbacks, and 419 original-group restorations. The first detailed decisions saw `activePackReservations=12`; high-volume native events exhausted the 10,000-row detail buffer. Natural spawning itself continued (1,906 native NPC additions). The trace cannot prove the exact extension gate because R238 did not record it.

The active RPG save contains 14 durable pack records: two terminal `DEFEATED` and the following 12 nonterminal. All 12 belong to world `9a19439a-004d-44e6-934d-e82f0fdf662c` (`default`). Times are UTC. “Last durable activity” is the pack file's modification time; “last log sighting” is the latest matching actor log in retained Oct 7–8 logs. None of these offline observations proves an actor is currently loaded. The `enemy-native-actors` mappings exist for all 12, but mappings persist after defeat and are not a live-entity index. “Alive” means no durable death receipt for that actor, not that it is presently in a loaded chunk.

| Encounter ID | Birth UTC | Origin / role | Durable state, alive/roster | Last durable activity UTC | Last retained log sighting UTC |
| --- | --- | --- | --- | --- | --- |
| `029f7f0c-de7e-3488-9212-74971c83017d` | 2026-10-08 22:06:40 | Natural Goblin Scrapper | SUSPENDED 4/4 | 2026-10-08 22:06:55 | 2026-10-08 22:06:50 |
| `22921d10-9e1f-307f-b23c-f69f1e19e280` | 2026-10-08 21:52:11 | Natural Goblin Scrapper | SUSPENDED 6/6 | 2026-10-08 21:52:50 | 2026-10-08 21:52:42 |
| `2bfb434c-15f2-3cac-9af8-80e796f2f95e` | 2026-10-08 21:52:45 | Natural Goblin Scrapper | SUSPENDED 4/4 | 2026-10-08 21:53:01 | 2026-10-08 21:52:51 |
| `36ba164a-821c-3d63-98e9-ea4267bad740` | 2026-10-08 20:30:52 | Natural Skeleton Fighter | SUSPENDED 2/2 | 2026-10-08 21:12:42 | 2026-10-08 21:12:35 |
| `5325c1dc-ac01-4c20-8b9e-499573e2a315` | 2026-10-04 20:53:08 | QA Larva Void | RELEASED 3/3 | 2026-10-04 20:53:08 | unavailable in retained logs |
| `78787a4e-c591-33de-b280-e0dee50546a5` | 2026-10-08 22:06:55 | Natural Piranha | SUSPENDED 6/6 | 2026-10-08 22:07:09 | 2026-10-08 22:06:55 |
| `aa3ea0c5-0226-3b54-8b6b-530d9633281f` | 2026-10-08 21:51:39 | Natural Wolf Black | RELEASED 6/6 | 2026-10-08 21:51:39 | 2026-10-08 21:52:07 |
| `ab3c3fd4-b0af-3315-a514-91736ab04a93` | 2026-10-08 21:52:31 | Natural Goblin Scrapper | SUSPENDED 4/4 | 2026-10-08 21:53:05 | 2026-10-08 21:52:57 |
| `bb3c5de0-0787-37b0-bca1-57286ccac599` | 2026-10-08 21:52:49 | Natural Goblin Scrapper | SUSPENDED 4/4 | 2026-10-08 21:52:51 | 2026-10-08 21:52:49 |
| `e10fa4a7-7378-4d69-ae6d-044b3f3924f6` | 2026-10-07 13:03:51 | QA Trork Warrior | RELEASED 2/3 | 2026-10-07 13:04:39 | unavailable in retained logs |
| `e206fef0-6990-4a08-ad54-ccd65fbb17d4` | 2026-10-07 12:26:03 | QA Larva Void | RELEASED 3/3 | 2026-10-07 12:26:03 | unavailable in retained logs |
| `e23d2a60-629b-34b2-b048-36547e763da2` | 2026-10-08 22:07:00 | Natural Wolf Black | SUSPENDED 4/4 | 2026-10-08 22:07:19 | 2026-10-08 22:07:10 |

## Release contract and defect

`EnemyPackCapacity` releases a pre-seal reservation on an ordinary declined birth. A published pack releases its reservation only after the durable pack becomes `DEFEATED` (all roster death receipts committed) or `ABORTED` (compensated birth). `RELEASED` means Packbound guards are gone or absent; it is **not** a capacity release. On chunk `UNLOAD`, `NativeEnemyBirthOwner` persists `SUSPENDED` and retains the reservation. A native `REMOVE`, despawn, or administrative removal without a durable death receipt also does not establish defeat or release. World teardown clears only the in-memory capacity index; recovery reconstructs it from durable records. These rules preserve loaded/unloaded actors, encounter identity, and reward exactly-once safety.

The definite accounting defect is that `EnemyWorldAdmission.begin` restored the three old durable QA packs into the production capacity index. Current QA births use `TransientQaEncounters`, so those historical QA records must still be validated/rebound but cannot consume production slots. R239 filters **only fully QA-origin birth rosters** from the capacity restore input. No record, save, reward receipt, or rebind obligation is deleted or rewritten. The eight suspended natural packs remain reserved because offline evidence cannot establish permanent removal. Consequently this repair changes the observed production occupancy from 12/12 to 9/12; further saturation remains possible until natural packs are defeated or a separately proven lifetime policy exists. Do not classify the suspended records as orphans solely from inactivity.

## Diagnostics and rollback

`NATIVE_EXTENSION_REJECTED` now records a precise validated gate: flock identity/types, world population resource/maximum/expected, environment absence/expected, chunk reference/headroom, or native flock capture failure. `ELITE_FALLBACK` carries the same subreason. World/identity boundary exceptions retain their fail-closed path rather than being recast as ordinary headroom. Spawn trace keeps an independent 2,000-row priority buffer and reason counts for reservation decisions, extension rejection, non-normal fallback, and exceptional restores even after 10,000 ordinary rows. The buffers remain bounded.

The declined-birth path restores the original native group once: `Rollback.fallback` calls `restore`, which releases pre-root staging through `releasePreRootNativeGroup`; the caller receives `Optional.empty` and performs no second restore. An accepted extension is discarded before original release if later planning declines. Focused rollback tests check that this path does not call normal encounter attachment. Connected verification should compare original-group capture/restore IDs and confirm `alive=originals`, `stillStaged=0`, and no duplicate `ORIGINAL_GROUP_RESTORED` for a given native job.

## Remaining connected check

Restart the active RPG save, run `/rpg spawntrace start`, travel through natural spawn areas for two minutes, then `/rpg spawntrace status`. Inspect the trace JSONL for `priorityCounts`, exact `NATIVE_EXTENSION_REJECTED` subreasons, and pack-reservation decisions after the ordinary detail cap. Confirm production occupancy begins at 9 rather than 12, no native-world exception occurs, and that a rejected Elite extension leaves the original group intact exactly once. The game client must supply current loaded-actor status; the offline save cannot prove it.

## Offline verification and deployment

Focused JUnit tests passed for recovery/capacity, QA birth, trace saturation, headroom rejection, and pre-root rollback. `:jar --offline` and `Test-HyArpgPackage.ps1` passed. The active RPG save's `mods/Hywind.jar` is R239-U7P5, SHA-256 `8F23EF6F88D6ECC570421C36A797C5D51D83625AB9F75CDAF6C761DD93A59CBB`. The previous R238 JAR is preserved outside `Saves` at `C:\Users\Zemio\.codex\deploy-backups\R239-20261009T004419433Z\Hywind-before.jar`. `world-config.json` and all encounter/save records were left untouched. Deployment is not connected-game acceptance.
