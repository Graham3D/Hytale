# R246-U7P5 — encounter effect queue deadline repair

## Evidence

The first R245 failure in `2026-10-09_18-39-24_server.log` is at 22:40:50 UTC: `DURABLE_DEATH_DELIVERY` reports `ENCOUNTER_EFFECTS_UNAVAILABLE`. The nested root is `CompletableFuture$Timeout.run`, not an I/O exception. Later `ENCOUNTER_PERSISTENCE_UNAVAILABLE` reports are consequences of the fail-closed transition.

The same-session `PERSISTENCE_HANDOFF_METRICS` show the context-load queue rise from 41 pending at 22:40:44 to 174 at 22:40:46. Its oldest admission age reaches 4.78 seconds at 22:40:48; at 22:40:49 it is uncertain with 173 failed receipts. The encounter WAL reports zero pending records and `failure=false` throughout. The death-effect queue becomes uncertain one second later. The spatial-stock recovery log is contemporaneous player join work, but uses a separate gear store; there is no evidence tying it to the first context-load timeout.

`DurableEncounterEffects.Reservation.submit` started `CompletableFuture.orTimeout(5s)` at submission, even when the job was waiting on its single ordered worker. That made queue residence count as active execution. The R245 trace does not record each worker-start timestamp, so it cannot identify the particular first timed-out job. It does prove the queue was saturated before the first failure, and the regression below reproduces the same timeout with healthy fast jobs and no failed durable write.

## Correction

Start each five-second uncertainty deadline when its ordered worker begins that job. Keep the existing five-second wait for the prerequisite durable receipt. The original future remains the receipt; a timeout never cancels a physical job, frees its ticket early, retries a write, or admits further effects. The first failure cause remains preserved while queued jobs fail closed behind it. Queue capacity stays at 256. No persistence format, reward policy, birth planner, spawn rate, save data, or world configuration changes.

## Verification

- `queuedFastEffectsDoNotExpireBeforeTheirWorkerStarts` failed on R245 with `TimeoutException` from `orTimeout` and passed after the correction. It holds the first worker briefly, then queues 160 otherwise fast ordered jobs whose total queue residence exceeds five seconds.
- `startedEffectStillFailsClosedWhenDurabilityIsUncertain` proves an active unresolved durable receipt still times out, applies no effect, and closes admission.
- Stage 13 durability/ordering, Stage 12 reward delivery, and R245 birth transaction suites passed.
- Full offline `:test` passed 3,456/3,456; `:nativeControlTest` passed 451/451; `:verifyHyArpgJar` passed. No game or standalone server was launched.
- The umbrella `check` task's `auditU7P5AssetCompatibility` still compares the current package with an R228 asset set and flags later R229–R245 additions. Its JSON asset-set comparison is stale for this revision; it is not a new R246 asset difference. Current package and native patch validators passed.

Connected acceptance remains the owner's RPG-save test: restart, check `/rpg worldconfig status`, travel through natural spawn areas, and capture the same-session server log and spawn trace. Look for multiple durable natural Elite publications and no new context-load/effect uncertainty. Offline tests cannot prove connected spawn publication.
