# Master Enemies Stage 4 run sheet

**Current candidate readiness: BLOCKED.** `R262ME12S3B-CANDIDATE` cannot be activated
for natural v2 gameplay. The guarded QA lease interface is implemented in source,
but its complete native-owner fixture is absent. The QA lease now checks each
concrete natural roster against its own pinned production binding, so Turret
and Seeker do not block independently certified roles. Goblin Turret has no
explicit hostile-player attitude in the pinned native template; both Turret
and Seeker lack production bindings. Firesteel is withheld from v2 QA until
its incoming-only lifecycle is verified. The actual
birth/publication/recovery owner fixture and all-27 causal affix predicates remain
incomplete. `masterEnemiesV2=ENABLED` still rejects with
`ME12_PRODUCTION_RELEASE_GATE_OPEN`; SHADOW only compiles plans.

## Activation and rollback boundary

This is the exact required sequence for a future ready candidate, not executable
activation instructions for this blocked checkpoint:

1. Complete and verify the offline blockers in `ME12_STAGE3_CONTINUATION.md`.
   The source registers `/rpg me12qa activate <candidateSha> <worldUuid> <saveName>
   <seconds> <guardianReceipt>` for an operator player with
   `inigmasgames.rpg.me12.qa` and the parent `/rpg` permission. Use the exact
   loaded world UUID, active save directory name, lowercase 40-character verified
   source commit, 1–600 second lifetime, and absolute path to its exact-commit
   Guardian verification JSON. Artifact/evidence hashes are checked off the
   world thread; the command reports activation only after live revalidation.
   The command requires `masterEnemiesV2=DRAIN`,
   admitted recovered world, all native owner hooks, at least one eligible
   production role and per-roster native certification,
   matching installed JAR/server/assets hashes, and PASS Guardian checks including
   owner, HRO diagnostic, and lease tests. The present candidate fails because
   the production birth-owner fixture does not yet exist.
   `/rpg me12qa status` is read-only. `/rpg me12qa revoke` stops new v2 births.
   Expiry, world unload, configuration reload, or owner-hook loss also stops them.
2. Obtain separate owner authorization identifying that tested candidate, target
   save and QA activation. Recheck cumulative ancestry, game stopped, actual save
   identity, native compatibility and previous mod hash. Back up the installed JAR
   outside Saves and the coordinated authoritative data/config/archive set. Do not
   create another QA save or launch a server/game/authentication flow.
3. Deploy only under that authorization; verify checksum. Owner starts the main
   game. Confirm hook readiness and no outstanding recovery holds before the
   separately authorized QA-only gate can admit new v2 births. Empty learning
   mappings remain empty unless the owner separately authors accepted sources.
4. Use the short capture sequence below. Missing source/operator evidence remains
   BLOCKED or NOT_OBSERVED and does not require extra diagnostic chores.
5. Revoke the QA-only birth authority with `/rpg me12qa revoke` to stop new v2 births. Keep the same
   v2-capable reader and all policy/learning archives, roots, reward intents and
   delivery cursors. After separate authorization, a world-config edit to
   `"masterEnemiesV2":"DRAIN"`, followed by `/rpg worldconfig reload`, preserves
   v2 recovery and uses the existing v1 new-birth path. This does not erase actors
   or roll back committed economy. `/rpg worldconfig status` is read-only.
6. Binary rollback must use a verified v2-capable build. Never replace it with the
   currently installed older JAR after v2 records have been created. Restoring a
   pre-candidate save requires explicit approval and a coordinated backup restore;
   it is not the default rollback procedure. Preserve diagnostic evidence outside
   Saves. A return to DISABLED or DRAIN is not owner acceptance.

`inigmasgames.rpg.worldconfig` is required for the existing reload/status command.
Revocation/expiry returns new births to the DRAIN v1 path without erasing any v2
roots, policy archives, or delivery cursors. The QA lease is memory-only and does
not persist across restart. A failed activation revokes any earlier lease.
No steps above were performed against the active installation in Stage 3.

## Future short capture

This is a future workflow, not a request to deploy, enable v2 births or begin owner QA.
Stage 4 requires separate authorization, an agreed cumulative candidate, native/source
certification resolution and the existing deployment checks. Monster Affix remains OPEN.

1. After the separately authorized candidate is installed, enter `/rpg-trace enemies start`.
2. Play normally. The capture observes only encountered opportunities; there is no requirement
   to manufacture encounters, run subsystem probes, reset a save or change telemetry settings.
3. Enter `/rpg-trace enemies stop` (or allow the ten-minute expiry), then `/rpg-trace enemies status`.
4. When COMPLETE/PARTIAL appears, use the printed folder under
   `%APPDATA%/Hytale/UserData/Diagnostics/Hywind/HRO/<runId>/`.
   `master-enemies.md` is the readable overview; `master-enemies.json` contains correlated
   facts and event locators. Preserve the whole folder for engineering analysis.

Permission: `inigmasgames.rpg.trace.patch`; in-game player only. The owner must stop their
own capture. The command shares HRO's single session and does not enable legacy recorders.
No setting restoration is needed. A disconnect has the existing 30-second reconnect grace.
Partial captures, dropped events, unobserved rare encounters and absent acquisition sources
are evidence gaps, never implicit acceptance. Frequency claims use offline statistical gates.

Connected acceptance must separately cover natural birth/publication, native collision and
affix effects, full target details, real Skill and Passive sources, and recovery. Engineering
will analyze the report; no additional manual diagnostic duties are introduced here.
