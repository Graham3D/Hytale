# R200 — Iron Sentinel investigation and cast feedback

## Connected evidence from R199

- Session: `2026-10-02_14-34-07_server.log` and `mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl` in the main RPG save.
- Native bridge asset audit passed at 18:34:15 UTC. Iron Sentinel was projected into native Ability2 / slot 0 at 18:34:31; its compiled plan succeeded at 18:34:43.
- Detailed tracing was enabled at 18:37:08; disconnect followed at 18:37:18. No accepted ability input, skill cast, summon request, or summon rejection appears for this session, including its earlier normal-mode records. There is no trace writer loss reported.
- The user confirms aiming at a dropped weapon and attempting the assigned ability. The physical key was not identified.
- Native window-close exceptions are present during inventory use. They do not establish a summon failure and are outside this change.

## Finding and limits

The installed U7P5.1 `CoreItemAbility.canUseWith` returns false for a null active hand or an item without `ItemWeapon`, before checking optional weapon-family tags. `InteractionContext.forInteraction` uses that result before selecting the ability rune. Iron Sentinel's rune has no additional weapon-family filter. Its ground source accepts supported managed HELD or ARMOR gear; acquisition through QA does not exclude the source.

This establishes a native prerequisite, **not proof that it caused the reported attempt**. R199 production packet observation returned before diagnostic input records, so absence of an accepted callback cannot distinguish a client-side block from another native input/bridge failure. A server cannot display a per-key rejection for a physical key the client never transmits.

## R200 changes

- Keep native execution as the only manual cast authority. Observed packets do not enqueue casts or consume source items.
- `/rpg-trace detailed` records `NATIVE_ABILITY_BOUNDARY`: `NATIVE_READINESS` on held-item/rune changes, `INBOUND_ABILITY` for initial ability packets, `NATIVE_CALLBACK`, and `BRIDGE_REJECTED`. Records include native action, chain identity, held-item identity and relevant native slots. The diagnostic stream is capped at 16 records per second per player, uses the existing bounded writer, and clears player state on disconnect/transfer. Normal/performance mode does not enable this new stream.
- Show a persistent HUD hint while skills are equipped and the active hand lacks native weapon eligibility. This describes availability; it does not claim to observe a keypress.
- Manual skill rejections through the shared execution owner show a centered notice, with a one-second full-intensity hold and short fade steps. Repeated identical failures do not restart the notice during its lifetime. Triggered/proc failures do not spam player notices. The same rejection owner handles delayed validation/commit failures.
- Reuse the existing HUD owner and world-thread poll, including its CanvasUI ownership guard. Disconnect removes notice state. No extra HUD owner or delayed task is introduced.
- No changes to summon mechanics, source eligibility, resource costs, skills, item custody, gear affixes, or save data.

## Connected QA

1. Enable `/rpg-trace detailed` **before** testing. Equip Iron Sentinel in skill01 if necessary: `/rpg equip skill01 Iron Sentinel`.
2. Keep a usable weapon in the active hand. Drop a separate Hywind gear weapon/armor, aim at it within 6 blocks, and use the key bound to native Ability2 (skill01; normally E).
3. Try with an empty active hand; check the availability hint. Try with a weapon while aiming away from all source items; check the centered source-item message.
4. If the valid attempt still fails, retain this trace and report the physical key used. `NATIVE_READINESS` → `INBOUND_ABILITY` → `NATIVE_CALLBACK` → the existing skill/summon events identifies the missing boundary.
5. Return telemetry to `/rpg-trace normal`.

Connected summon success remains unverified; R200 is a feedback and diagnostic build, not a claimed root-cause repair.

## Offline validation and deployment

- One final `:test :nativeControlTest :jar --offline --console=plain` run passed: 3,190 unit tests and 405 native controls, zero failures/errors/skips. Includes installed native empty-hand/non-weapon rejection and managed-staff acceptance, packet observation without cast authority, callback deduplication, readiness transitions, rejection feedback without resource/cooldown mutation, triggered-failure silence, and notice timing.
- Package validation and U7P5 asset compatibility checks both passed. Logs: `build/r200-final-checks.log`, `build/r200-package-check.log`, `build/r200-assets-check.log`.
- Deployed R200-U7P5 to the main RPG save on 2026-10-02 at 19:01 UTC. SHA-256: `12F2E0DA1E2DF5C8794741A8F0DCBC03C0F526A0B31A731D97DB548007B9EEDA`.
- Previous R199 JAR backed up outside Saves at `evidence/hyarpg/jar-deploy-20261002T190148935Z/HyARPG-before.jar`. Deployment checksum verified. No game/server was launched and no saved player records were edited.
