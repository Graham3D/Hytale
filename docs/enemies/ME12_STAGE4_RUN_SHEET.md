# Master Enemies Stage 4 run sheet

**Current candidate readiness: BLOCKED.** `R262ME12S3B-CANDIDATE` cannot be activated
for natural v2 gameplay. The modern projectile route has a Task 1 offline
candidate; Task 2 still lacks complete mixed-flock owner certification,
the full native-provider birth/publication/recovery fixture, Firesteel
lifecycle proof, Turret/Seeker production certificates, and a candidate-only
QA activation authority. `masterEnemiesV2=ENABLED` is rejected with
`ME12_PRODUCTION_RELEASE_GATE_OPEN`. No command or undocumented config switch
bypasses this. SHADOW only compiles plans and retains v1 gameplay.

## Activation and rollback boundary

This is the exact required sequence for a future ready candidate, not executable
activation instructions for this blocked checkpoint:

1. Complete and verify the offline blockers in `ME12_STAGE3_CONTINUATION.md`. Add
   a separate QA-only activation authority tied to an exact candidate SHA, native
   hook/assets identity and owner-selected existing world(s), with bounded lifetime
   and explicit revocation. Retain the global production ENABLED guard. Its exact
   registered command/config syntax must be implemented, tested and documented
   before requesting authorization; none is invented here.
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
5. Revoke the future QA-only birth authority to stop new v2 births. Keep the same
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
