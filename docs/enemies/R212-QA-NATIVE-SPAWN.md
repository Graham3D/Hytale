# R212-U7P5 — QA native placement and post-spawn drop guard

Deployed to the active RPG save on 2026-10-07. `HyARPG.jar` SHA-256:
`22AB334BE278A21989413FB003C02BBD205C32B6B554C8998B43C42853DCDA01`.
The R211 JAR is backed up outside `Saves` at
`C:\Users\Zemio\.codex\backups\HyARPG\HyARPG-before-R212-U7P5-477AB7265DFA.jar`.

## Connected R211 evidence

The active RPG server log recorded two local QA failures, with no global encounter failure: `Golem_Crystal_Frost` exhausted nine `PLACE` attempts, and `Trork_Warrior` failed at `PRE_ADD` because `NPCEntity.getRole()` was null when native death-drop suppression ran. These are separate native spawn boundary issues. R211's transient encounter backend and all 27 affix implementations remain unchanged.

## Narrow correction

- QA position planning now uses the installed SDK's `SpawningContext.setSpawnable`, `set(world, column X/Z, Y hint)`, and `canSpawn` sequence, matching the native `spawnNPCWithColumnProbe` path. It retains nine deterministic anchor attempts and probes the full intended leader/minion roster before creating any actor. Each accepted native position carries the model selected by the native context into the existing staged `NPCPlugin.spawnEntity` overload.
- Native rejection details are logged per candidate at `FINE` and summarized in the one `PLACE` failure log. The chat reason remains `QA_SPAWN_NO_VALID_POSITION attempts=9`. Chunk, spawn span, ground/validation, model, and native `SpawnTestResult` information is retained where the SDK provides it.
- The `preAddToWorld` callback attaches only QA provenance, `NonSerialized`, the exact native UUID marker, and `EnemyStaging` freeze/invulnerability/intangibility. The `postSpawn` callback requires the spawned actor's own `Role`, calls `setDeathItemsDropped`, checks `hasDroppedDeathItems`, and verifies the actor UUID. No role builder or shared asset is mutated.
- An absent post-spawn Role throws `QA_ROLE_UNAVAILABLE_POST_SPAWN`; the local QA attempt removes its staged actors and does not publish/unfreeze the pack. The same applies to a failed drop guard or native spawn result. Production encounter persistence and reward fail-closed behavior are untouched.

## Verification and owner QA

Focused offline compilation, QA planner/registry tests, native-staging tests, package validation, and checksum verification passed. `verifyHyArpgJar` reported revision `R212-U7P5`, 18,761 entries, 2,067 classes, and zero ImmersiveNPCs payload entries. No standalone Hytale server was started. After restarting the world, run the Trork command repeatedly, then Frost Golem and Larva commands from `docs/COMMANDS.md`. Check native drop suppression, local failure recovery, and absence of durable/global encounter errors. Connected results are owner acceptance, not claimed by the offline build.
