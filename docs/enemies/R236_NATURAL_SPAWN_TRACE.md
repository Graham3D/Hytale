# R236 natural spawn investigation and connected trace

## Evidence available before R236

Source: active RPG save log `2026-10-08_18-04-07_server.log` (R235). It contains 27 `NO_ROOM`, 23 `FAIL_NO_POSITION`, and 4 `FAIL_INVALID_POSITION` native **marker** placement messages, plus 14 overpopulation removals. These messages establish failed placements and population cleanup, but do not count native environmental job selection or ordinary group publication. Boot recovered 11 birth and 11 pack records in `default`; nine rebinds succeeded. Unique presentation also occurred, so Elite creation is not universally broken. One `ENEMY_ACTION_ACCEPTANCE_SNAPSHOT_MISSING` on a Goblin Scrapper is an action failure after birth, not evidence of suppressed spawn opportunities. It remains a separate issue.

The current source follows `WorldSpawningSystem`'s native job selection, `WorldSpawnJobSystems.Ticking` through `NativeEnemySpawnGroups`, `NativeEnemyBirthDecision`'s rarity/rollback, and `NativeEnemyBirthOwner`'s publication. `NativeWorldSpawnDensity` updates native environment density and expected counts; no new scheduler was added. The source for the pre-Elite implementation is not tracked for these owners in this checkout, so a line-by-line historical source diff is unavailable. Archived JARs in the original checkout are not evidence of a compatible pre-sweep source baseline and were not run against the save.

**Finding:** The R235 log cannot localize the reported encounter-frequency loss. No spawn-rate or population-balance gameplay change is justified from this evidence.

## R236 trace

Operator command: `/rpg spawntrace start|status|stop` (permission `inigmasgames.rpg.spawntrace`). It observes for 120 seconds, stores at most 10,000 detailed JSONL rows, and continues aggregate counters after that cap. The log is written to the plugin's `logs/rpg/monster-spawn-trace` directory under the active RPG save. Stopping or automatic expiry writes the file; `status` reports its path and any write error.

The trace records the active native density multiplier; per-world native expected/actual and active job counts; per-job environment/role expected, actual and free slots; selected role, requested flock, 64 m sampled region, actual matching native actors, native job rejection deltas, Elite capture eligibility, rarity and pack-plan decisions, reservation outcomes, original group restoration, Elite publication, and NPC add/remove boundaries. Events carry world, environment, role, native job ID or entity ID when available. Actor category is the native default player attitude when available, otherwise `UNKNOWN`. Existing pack reservations are sampled as part of the birth/admission record. It only reads native data and delegates the original native tick.

Limits: Native marker placement failures occur outside the environmental job capture; continue to read those in the server log. Pre-selection job suppression and budget denials inside native `WorldSpawningSystem` have no exposed callback, so expected/actual/slot samples are the earliest available read-only boundary. `NPC_ADDED_OTHER` distinguishes actors outside a captured environmental job but does not prove their full provenance. Client tracking acknowledgments are not exposed by this server hook; compare add/remove and connected visibility, without claiming that an added actor was rendered. The trace does not scan all NPCs each tick or change capacities, weights, rolls, rollback, presentation, or rewards.

## Connected reproduction

1. Restart the active `RPG` world with the R236 build. Keep the world density at the operator's intended value (currently 8x) and note the setting.
2. In one location, run `/rpg spawntrace start`, traverse natural spawning areas for two minutes, then run `/rpg spawntrace status` (or `stop` earlier). Record the path printed by the command. Do not use `/rpg spawn` during the sample.
3. Keep the trace JSONL and same-session server log. Compare environmental `NATIVE_JOB_CREATED` and `NATIVE_JOB_TICK_RESULT`, actual matching members, `PACK_RESERVATION`, `ORIGINAL_GROUP_RESTORED`, `ELITE_PUBLISHED`, and `NPC_ADDED_NATIVE_JOB`/`NPC_REMOVED`. Check role availability and world expected/actual alongside native marker failures. A declined Elite roll should restore the original group exactly once.
4. If a controlled density comparison is useful, let existing population settle, set 1x with the existing operator setting, revisit the same route and repeat a separate two-minute trace. Record time/weather and visible population; do not interpret the pair as a controlled experiment if conditions differ.

No copied save, standalone server, temporary promotion gate, or historical JAR was used. The owner performs connected acceptance in the main game. R236's offline tests validate only tracing/package behavior and cannot establish the in-game scarcity cause.

## Build and deployment

Revision `R236-U7P5` was compiled and packaged. Focused tests `MonsterSpawnTraceTest`, `EnemyNativeStagingTest`, and `NativeEnemyPreRootRollbackTest` passed; `verifyHyArpgJar` and `auditU7P5AssetCompatibility` passed. The compatibility audit reported `nativeServerStarted=false` and connected acceptance pending. The active RPG save's `mods/Hywind.jar` was backed up outside `Saves` before replacement. Deployed SHA-256: `6FBFBE05BC2BA46A2F2EF6D6E74C6B8FD58DBC1057B8E69B846392928A33FE7B`; deployed checksum matches the tested package. The previous JAR's backup SHA-256 is `6C1C631E3F933C33967A45176D49F0C20190FC97B7C1630072378360084528ED`.
