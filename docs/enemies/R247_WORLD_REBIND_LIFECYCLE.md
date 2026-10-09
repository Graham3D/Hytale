# R247-U7P5 — initial world rebind lifecycle

## Connected R246 evidence

`2026-10-09_19-26-57_server.log` and `monster-spawn-1791588458298.jsonl` show three natural Raptor Elite packs published (14 actors). Pack `70a70be3-771a-3068-86d9-1e1305d9242c` suspended, then reactivated at 23:28:04 UTC with its original encounter `08457694-05d1-3f23-9681-5668387bb5da`. Four milliseconds later a repeated `WORLD_INVENTORY_REBIND` failed with `ENEMY_WORLD_REBIND_ORPHAN_IDENTITY`; two more repeat audits reported `ENEMY_WORLD_REBIND_UNACCOUNTED_STAGING`. The world gate closed with zero pending births. This log contains no R246 context-load or effect timeout.

`HyArpgPlugin` registered both staging reconciliation and whole-birth publication callbacks directly to `NativeEnemyWorldRebind.begin`. That method reuses `EnemyWorldAdmission.begin`'s initial durable inventory. `activatePublished` correctly adds new packs to the live catalog but does not alter the startup snapshot. Thus a valid actor from a later birth can be rejected by a repeated startup-only `inspect` pass.

## Correction

- `EnemyWorldAdmission.initialRebindPending` checks the exact world lifetime, successful initial inventory load, pending native review, and absence of quarantine.
- Both recovery callbacks now call `NativeEnemyWorldRebind.retryInitial`. This retries the full audit only while initial review is pending; normal post-publication LOAD reactivation continues through the existing birth-root, pack, generation, UUID and staging checks in `NativeEnemyWholeBirthRecovery` and `NativeEnemyStagingRecovery`.
- `NativeEnemyWorldRebind.begin` rechecks the lifetime and pending state before executing a queued audit. Obsolete callbacks cannot inspect a replacement world or reopen a failed one.
- `inspect` and its orphan/staging rejection rules are unchanged. No persistence data, rewards, affixes, pack capacity, rarity, spawn rates or world configuration were changed.

## Verification

`R247WorldRebindLifecycleTest` failed on unmodified R246 with two `NoSuchMethodException` results for the missing lifecycle admission; it passed after the correction. Its real file-store/capacity scenario opens initial admission, publishes three new packs, unloads and reactivates two members of one pack, verifies subsequent publications, and rejects an obsolete lifetime. It also checks pending initial retries and quarantine. The affected world-admission, R245 transaction, birth-persistence, staging, pre-root rollback, Stage 12 reward, and Stage 13 durability suites passed. Connected gameplay acceptance remains with the owner; no standalone server was launched.
