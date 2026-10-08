# R234-U7P5 — natural Elite pre-root rollback repair

Date: 2026-10-08. Deployed to the active RPG save as `mods/Hywind.jar`.

## Connected R233 failure

`2026-10-08_17-28-33_server.log` shows `default` recovered six births and six packs at 21:28:49 UTC. `freshAdmission=false` in that initial recovery log precedes the native rebind callback. At 21:28:59, a natural spawn callback reported `ENCOUNTER_CONTEXT_GENERATION_BUSY`, followed by `ENCOUNTER_EFFECTS_UNAVAILABLE` on the world thread. The stack enters `NativeEnemyBirthDecision`'s fallback, which released the original staged NPCs and then called `HytaleEncounterRewards.added()` on each one. That ordinary attachment route is not safe during pre-root rollback: it can reenter an attachment already in progress, poison the effects lane, and throw from the native spawn tick. The R233 missing-environment/headroom NPE is absent from this log.

The recovered inventory did not by itself prove a stale writer. `NativeEnemyBirthOwner.eligible` only captures when `EnemyWorldAdmission.admits` is true, so the failing capture occurred after admission opened. R234 now logs `RPG_ENEMY_WORLD_REBIND_COMPLETE` after the recovered inventory has passed native rebind inspection and fresh admission opens. Connected logs are needed to confirm the recovered actors remain healthy in this save.

## Repair and boundaries

- Pre-root fallback releases the exact staged original roster and its native flags without calling `added`, `attachNative`, or the encounter effects/persistence lanes. A scoped world-thread guard blocks a synchronous tracking callback from attaching the same actors during release.
- A local selection/extension rejection before any durable root returns the unmodified native group. The existing R233 missing-environment/zero-headroom behavior remains intact. An uncertain partial extension or failed native release still closes Elite admission and leaves its staged actors protected for recovery.
- The native birth handoff logs and quarantines an uncertain failure without throwing it into Hytale's world spawn tick.
- Post-root compensation remains writer-owned: it durably restores ordinary contexts before its separate native restoration/attachment path. An accepted Elite still reserves the original durable birth root and follows the existing attachment/publication path.
- A saved pre-root staging marker on reload uses the same native-only release. A saved compensated marker retains the writer-backed ordinary restoration path.
- No changes to the 27 affix implementations, rarity, rewards, pack planning, R233 headroom calculation, or save records.

## Verification and deployment

- Focused rollback wiring, R233 absent-environment/headroom, recovered-world admission, and birth-persistence tests passed.
- Offline `gradlew check` passed: **3,417 tests in 343 suites**, zero failures/errors, plus package and asset validation. No standalone Hytale server was launched.
- Active JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\Hywind.jar`; SHA-256 `8D099DB16E4AFE20535FF7370A06AAD17D7E5D8BABA2C6F859ECFFA325A4E90C`.
- Backup of R233: `C:\Users\Zemio\.codex\backups\Hytale\Hywind-R233-U7P5-8780D34D-20261008.jar`; SHA-256 `8780D34D2511D1E1061B352A4421525083247937217FC0C27053C0A517C9EA00`.
- **Connected acceptance pending owner QA** under `AGENTS.md`: restart the same RPG save, retain recovered data, enter `default`, and fly through natural spawn areas for several minutes. Confirm `RPG_ENEMY_WORLD_REBIND_COMPLETE` and absence of world-thread exceptions from birth fallback/rollback; verify ordinary native groups and accepted Elite packs continue spawning. Deployment and offline tests do not establish connected acceptance.
