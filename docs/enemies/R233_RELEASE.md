# R233-U7P5 — natural Elite pack headroom crash repair

Date: 2026-10-08. Deployed to the active RPG save as `mods/Hywind.jar`.

## Connected failure

The R232 connected log `2026-10-08_17-11-56_server.log` crashed the `default` world at 21:12:42 UTC while a natural Elite birth tried to reserve extra flock members. The first exception was `NullPointerException: Failed to get environment data for chunk` in native `ChunkSpawnData.getEnvironmentSpawnData`, called by `NativeEnemyFlockExtension.headroom` at line 157. A nearby loaded chunk had spawn data but no entry for the job's environment. The later player-disconnect exception occurred during shutdown after the world tick had already failed.

## Repair

The extension reads the SDK's nullable `getChunkEnvironmentSpawnDataMap().get(environment)` entry. A chunk without that environment contributes zero extension headroom. If there is insufficient room, the existing local fallback restores the original native spawn group without admitting extra Elite pack members. This does not create a new spawn path, relax population caps, alter Elite rarity/affixes, or change native flock spawning.

## Verification and deployment

- Focused regression reproduces the SDK's throwing getter on an absent entry and proves the adapter returns zero headroom and declines extension.
- Offline `gradlew check` passed: **3,414 tests in 342 suites**, zero failures/errors, plus package/asset validation. No standalone native server was launched.
- Connected acceptance is pending: restart the RPG world and fly through natural spawn areas; confirm the world stays up and no `Failed to get environment data for chunk` crash recurs.
- Active JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\Hywind.jar`; SHA-256 `8780D34D2511D1E1061B352A4421525083247937217FC0C27053C0A517C9EA00`.
- Prior R232 backup: `C:\Users\Zemio\.codex\backups\Hytale\Hywind-R232-U7P5-5F97EDE0-20261008.jar`; SHA-256 `5F97EDE07B3B58C42A83008E1459F264ABE5AAF5A9BCF06436C995A8609FA48B`.
