# R032-AD — Connected presentation correction

**IMPLEMENTED / PACKAGED / DEPLOYED. CONNECTED-VERIFIED = false.**

Date: 2026-09-12. Branch: `RPG`. Baseline: deployed R032-AC (`bfe800b`).
Scope was limited to the four owner-observed presentation/timing defects: the
invisible Healing Beam, missing Blizzard snow, Blizzard lifetime/cooldown
synchronization, and the absent red radial cooldown sweep. Gameplay formulas,
healing, targeting, native AbilitySlots/input, resource authority, persistence,
rewards, escrow and exact-once behavior were not redesigned.

## Connected evidence and root causes

The latest connected server/client logs and `skill-trace.jsonl` show that AC was
loaded and that both skills reached their gameplay paths. Healing Beam repeatedly
committed, started a connection root, ticked, and terminated normally on release.
Blizzard roots ran for approximately 3.03 seconds. No Beam asset, UI parse, or
client presentation exception was recorded. This localizes the symptoms to
presentation and cooldown projection rather than native ability input,
validation, payment, execution, or persistence.

Four concrete faults were found:

1. The custom `RPG_Healing` Beam resolved on the server and advertised
   `Trails/Void_Green.png`, but the connected client rendered no visible tether.
   Asset resolution alone therefore did not prove client visibility.
2. Blizzard used a very small, short-lived derivative of `Snow_Heavy`. Its
   authored scale/lifetime made it effectively unreadable in the connected scene.
3. Blizzard's nominal three-second cooldown was shortened by generic cooldown
   recovery (the trace showed roughly 2.95 seconds). The active-area gate still
   prevented overlap, but displayed cooldown and effect lifetime used different
   clocks.
4. `CooldownMask.png` was a copy of native `FillAbilityCooldown.png`, not the
   generic circular progress mask. The overlay was dark and represented elapsed
   time, so it began invisible and grew instead of beginning full and clearing.

## Healing Beam correction

`NativeHealingBeamVisuals` now uses Hytale's shipped `Basic` Beam asset at the
native command's 0.25 scale. The isolated runtime resolved it as
`Trails/Charged_Blue.png`. This uses a client-shipped Beam rather than the custom
texture whose connected visibility was falsified.

The bounded spring-damped tether and persistent native entities remain intact.
Each logical Arc/Fork/Chain segment retains independent motion history and exact
endpoint attachment. Visual entities remain non-serialized, are reused instead
of recreated each frame, and are removed on terminal/cancel paths. No sampled
particle fallback or gameplay dependency was introduced.

The presentation boundary now emits one event-driven trace record when a native
beam root is actually allocated: `HEAL_PRESENTATION` with either
`phase=NATIVE_BEAM_STARTED, asset=Basic`, or `phase=NATIVE_BEAM_FAILED` and a
bounded stage/class/message. This separates successful server-side allocation
from client rendering without adding per-tick trace volume. A custom green
appearance will require a Beam texture proven visible in a connected client; AD
deliberately uses a shipped asset for this test boundary.

## Blizzard correction

The unreadable RPG snow derivative and generator output were removed. Blizzard
now requests the exact installed `Snow_Heavy` ParticleSystem once per root. Its
installed spawner has a 15 by 15 horizontal footprint and particles lasting up to
4/3 second. AD translates/scales that footprint to the compiled 12 m Blizzard
diameter and ends emission 4/3 second before root expiry. For a three-second root
this is about 1.667 seconds of emission plus at most 1.333 seconds of existing
particle life, bounded to the same normal lifespan.

The request is root-owned and idempotent; a failed native send removes its
admission marker so the next bounded update can retry. Successful allocation
emits `AREA_PRESENTATION phase=STORM_STARTED template=Snow_Heavy`; failure emits
`STORM_FAILED`. Falling shard carriers, first-solid collision, `Impact_Ice`, and
per-shard `SFX_Ice_Ball_Death` are unchanged.

`BlizzardCooldownPolicy` is the single timing contract used by commit and HUD
projection. For Blizzard only, cooldown base equals compiled area lifetime,
duration factor is 1, recovery is 0, and kernel modifiers are empty. Both the
prepared snapshot and durable cooldown spend use these terms. The existing
active-root gate remains authoritative. Therefore ordinary three-second Blizzard
starts cooldown at accepted commit, cannot be recast during the active root, and
becomes ready when that root/cooldown completes. Other skills retain their
existing authored cooldown/recovery behavior.

## HUD radial correction

The packaged mask is byte-identical to installed Hytale
`Common/UI/Custom/Common/CircularProgressBarMask.png`:

`357843F21C9ACD3605E072E83B7DAF623F87A6ABA1BC4F01F9FC67032272D800`

Both native-skill overlays remain read-only 58 by 58 `CircularProgressBar`
controls at the audited E/R HUD anchors. Color is `#ff0000(0.50)`. Value is the
clamped remaining fraction `remaining / duration`: fully covered at commit, then
clearing to zero and hiding at readiness. No native skill icon, frame, input
control, Signature slot, Health, Mana, or Stamina element is replaced.

`RpgHudCoordinator` emits `COOLDOWN_HUD_STATE` only on slot skill/state
transitions, including remaining, duration, and radial value; it does not trace
every HUD poll. The build badge is `R032-AD`.

Actual clockwise motion, native compositing order, alignment at the owner's UI
scale, and client visibility remain connected gates. Static UI validation and
isolated server boot do not prove them.

## Files changed

- `execution/BlizzardCooldownPolicy.java`
- `execution/SkillExecutionService.java`
- `execution/hytale/HytaleSkillExecutionSystem.java`
- `execution/hytale/NativeBlizzardVisuals.java`
- `execution/hytale/NativeHealingBeamVisuals.java`
- `input/NativeSupportTetherAudit.java`
- `ui/RpgUiProjectionService.java`
- `ui/hud/CooldownSweep.java`
- `ui/hud/RpgHud.java`
- `ui/hud/RpgHudCoordinator.java`
- `Common/UI/Custom/RpgCooldownSweep.ui`
- `Common/UI/Custom/Assets/RpgHud/CooldownMask.png`

The two unused custom Blizzard snow resources were deleted, and
`Generate-BlizzardParticles.ps1` no longer regenerates them. Tests and the
Stage-13 isolated smoke harness were updated for cohort AD.

## Validation

Focused presentation/native-Beam tests passed. Final retained validation against
the exact packaged source produced:

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| RPG | 2,158 | 1 | 0 | 0 |
| Native control | 54 | 0 | 0 | 0 |
| CanvasUI | 21 | 0 | 0 | 0 |
| Total | **2,233** | **1** | **0** | **0** |

The sole failure is the unchanged
`TraceArchiveFixtureRoundTripTest.suppliedSkillAndUiFixturesRoundTripByteForByteWithSubstantialCompression`
fixture compression-ratio assertion. Byte-for-byte recovery succeeds before the
ratio assertion. No trace assertion, fixture, writer, rotation setting, or gate
was changed. All native-control and CanvasUI tests passed.

The exact AD JAR passed isolated boot with exactly RPG, CanvasUI, and HytaleDevLib.
It resolved `beam=Basic texture=Trails/Charged_Blue.png`, `snow=Snow_Heavy`,
`impact=Impact_Ice`, and `SFX_Ice_Ball_Death`; all retained Stage 05–13
registrations and the native projectile audit passed, followed by clean exit 0.
No connected client participated.

## Package, rollback, and deployment

| Artifact | SHA-256 |
| --- | --- |
| RPG JAR / installed JAR | `3D01383C2100824F7D5AC8162CFFDF46CD05F31A2BA0E4825072303C2DE90FDF` |
| Three-mod ZIP | `474FE301E2AFC87F534590F46BC15F621614CBD241A98253965AA80E6C4A27E2` |
| CanvasUI | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| HytaleDevLib | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |

The ZIP contains exactly those three JARs, with byte-identical entries.
AC→AD→AC binary rollback simulation passed.

Before deployment, the stopped RPG save was copied to the local Git-ignored
backup `evidence/stage-13/cohort-ad/before/save/20260912-123335/RPG`.
All 497 files / 288,554,841 bytes matched by count and size; the prior RPG JAR
matched its backup at
`C3AC781F8BB5948490DE45E9C7176AACA8178F0B29A7729E9A9A71B02A42E891`.

AD was deployed to
`C:/Users/Zemio/AppData/Roaming/Hytale/data/pre-release/Saves/RPG/mods/HytaleRPG-0.0.25.jar`.
Installed SHA-256 exactly equals the package. No other mod or save-data file was
changed by deployment. The installed binary then passed the isolated smoke.

## Connected QA checklist

1. Launch/rejoin RPG and confirm `R032-AD` at the top right.
2. Equip Healing Beam and channel an injured friendly NPC while stationary, then
   move caster and target separately. Expect a continuous shipped blue Beam with
   exact endpoints, elastic lag while moving, straightening after stopping, no
   blinking, and immediate cleanup on release/range/LOS/death.
3. Repeat with Arc, Fork, and Chain; each segment must move independently. Healing,
   Mana upkeep, LOS, range, and support credit must remain unchanged.
4. Cast Blizzard. Expect `Snow_Heavy` immediately, falling shards, `Impact_Ice`
   at actual surface contacts, and one `SFX_Ice_Ball_Death` per shard. No black
   wire cylinders should appear. Snow and skill should be gone at three seconds.
5. Attempt Blizzard before three seconds: it must not start or charge twice. At
   completion it must be castable subject only to normal equipment/Mana/ticks.
6. Watch E/R. A half-transparent red sector must cover the cast slot at commit and
   clear clockwise in sync with cooldown. Verify both slots, swapped skills, and
   that empty/ready slots have no overlay.
7. Preserve server/client logs, `skill-trace.jsonl`, and `ui-trace.jsonl`. AD should
   record `NATIVE_BEAM_STARTED` or bounded `NATIVE_BEAM_FAILED`, `STORM_STARTED`
   or `STORM_FAILED`, and `COOLDOWN_HUD_STATE` start/ready transitions. Rejoin once
   to confirm loadout persistence.

Do not mark AD connected-PASS until these client-rendered behaviors are observed.
If `Basic` still does not render, the new trace proves whether native Beam
allocation succeeded and localizes the next boundary to client Beam presentation.

Evidence: `evidence/stage-13/cohort-ad/`.
