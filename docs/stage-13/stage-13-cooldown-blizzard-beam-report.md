# R032-AB — Cooldown sweep, Blizzard cleanup, curved Healing Beam

**IMPLEMENTED / PACKAGED / DEPLOYED. CONNECTED-VERIFIED = false.**

2026-09-12, `RPG`, cumulative baseline `2c8cf4a` / deployed AA. Owner requested this correction and deployment remains the default. [Scoped contract amendment](../corrections/R032-AB-presentation-contract.md). The companion checkpoint records the implementation commit SHA after commit creation.

## Connected evidence and diagnosis

Reviewed the 2026-09-12 09:58:29 server log, latest active skill trace and owner screenshot. Startup recorded `RPG_HEAL_BLIZZARD_ASSETS cohort=AA`, confirming the prior connected session loaded AA. The owner's observation that the beam still flowed into the caster falsifies AA's +Z particle-velocity interpretation. Do not treat AA's mathematical rotation test as proof of client velocity direction.

Blizzard trace entries show `AREA_PRESENTATION phase=WARNING template=NATIVE_GEOMETRY`, radius 2, height 3, duration 0.45: these are the black wire circles/cylinders in the screenshot. They are debug/warning presentation, not the collision volume itself.

The same trace shows terminating impacts at explicit surface coordinates, e.g. `(10.701545248403212, 121, 161.26006462562975)`, with `template=RPG_Blizzard_Impact` and `SFX_Ice_Ball_Death`. Therefore the server was requesting impact presentation at the actual contact; missing-looking impact VFX is not evidence that the collision code selected the old sampled point. AA used a heavily shortened derivative, not the unchanged native `Impact_Ice` requested by the owner.

An additional concrete native API error was found during this audit: the last float in
`ParticleUtil.spawnParticleEffect(String, Vector3dc, float, float, float, float, float, ComponentAccessor)`
is **maxDuration**, not visibility distance. Actual bytecode uses a fixed 75 m spatial collection radius and forwards that float into `SpawnParticleSystem.maxDuration`. AA passed `75` for snow and beam emission. This report corrects the prior report's incorrect interpretation. AB bounds it explicitly. Client-visible lifetime is still a connected gate, not inferred solely from packet fields.

Installed evidence remains Hytale 0.7.0-pre.1:

- Server SHA-256: `9CDC1E77DADFDAD0B1D849977CDAD5C2752207F538272FB3D76D4E915546B25E`.
- Assets SHA-256: `0E35DF2D4E4A803F7752CB300FA7C9FE7ACDF62B4311978DA95C7AFF7ABBC126`.

## HUD radial cooldown

Added two **read-only** `CircularProgressBar` overlays over the native E/R icons. Native `InGame/Hud/Abilities/Ability.ui` already uses this control for charge presentation. Its actual mask property, color and value syntax were reused; the native `FillAbilityCooldown.png` was copied byte-for-byte as `Common/UI/Custom/Assets/RpgHud/CooldownMask.png`.

Native `AbilitiesHud.ui` positions its container at Right 50 / Bottom 40, with 66-pixel cells and 28-pixel spacing. The overlays are inset 4 pixels: 58×58, Right 176 and 82, Bottom 72. They do not replace icon/background/frame assets, native input buttons, Signature, AbilitySlots or any Health/Mana/Stamina control. Custom scale/layout variations still require connected alignment QA.

`RpgUiProjectionService` publishes authoritative cooldown remaining and calculated effective duration. Blizzard additionally includes the remaining active primary storm lifetime, because it cannot be recast while that storm is active. `CooldownSweep.progress` is elapsed fraction `1 - remaining / duration`, clamped and hidden when ready. The dark radial sector progresses toward full, then clears at readiness. It uses native CircularProgressBar forward sweep; **actual clockwise direction, origin and visual appearance are not yet connected-verified**. This is not a second native gameplay cooldown or an independent countdown. Native ItemAbility cost/cooldown remain zero; RPG still owns payment and cooldown enforcement.

HUD polling is 100 ms rather than 250 ms. It sends changed presentation values; timing is therefore quantized by owner ticks and the 100 ms UI poll, not a new gameplay timer. UI duration calculations are read-only and do not submit persistence. RpgHud's top-right badge is now `R032-AB` so the owner can distinguish this visual test build.

Changed: `SkillSlotView`, `RpgUiProjectionService`, `CooldownSweep`, `RpgHud`, `RpgHudCoordinator`, `Phase00Plugin` wiring and the new `RpgCooldownSweep.ui`/mask.

## Blizzard

1. **Black geometry:** Blizzard's presentation adapter now ignores non-impact generic phases; no warning/debug circles are emitted. Sweep, footprint and damage queries are untouched.
2. **Impact:** use unchanged shipped `Impact_Ice`, via native ParticleUtil, at the same actual contact returned by the first-solid sweep. Keep one `SFX_Ice_Ball_Death` on a terminating solid impact, no storm loop. Its own authored spawner offsets and natural one-shot particle tails remain native. The core is removed at collision as before.
3. **Snow:** keep one logical snow owner per storm zone, starting on the first AreaRuntime tick during start. Its native SpawnParticleSystem packets now have explicit <=0.1-second emission leases; the existing derivative has <=0.2-second particle life. Stop new emission early enough that configured emission-plus-particle lifetime fits within remaining normal root duration. At abrupt cancellation the already-issued lease/particle tail is bounded to roughly 0.3 seconds; no fictitious native stop handle was added. This is a refreshed native particle stream, not a persistent uncontrolled weather emitter.
4. **Snow visibility:** corrected the animation's time-zero Scale override, which previously reset the small initial scale back to 1 before applying zone scale. Localized Snow_Heavy uses 600 particles/s while an emission lease is active, max 80 per spawner, native texture, compiled radius scaling, Important=true and cull distance 75. Generator updated so regeneration preserves these changes. Readability/density requires owner QA.
5. **Lifetime/cooldown:** catalog and runtime base cooldown changed 18→3 seconds; lifetime remains 3 seconds. Existing durable cooldown adoption occurs at paid commit just before dispatch, not at end. `AreaRuntime` rejects a second manual primary Blizzard as `BLIZZARD_ALREADY_ACTIVE` until the primary finishes. This includes cases where recovery/charges would otherwise permit a recast early. Existing legal derived releases are not treated as new manual casts and still obey shared spawn budgets. HUD displays the maximum of cooldown remaining and active lifetime remaining.

Base cadence remains 11 shards, 0.25-second spacing, 7.5 m initial height, nominal 0.45-second descent, final nominal impact 2.95 s. Preserve 6 m zone, 24 m placement, 2 m local impact, 0.38 coefficient, Chill 1 and per-target root ICD 0.75 s. Existing modifiers still affect effective duration/cooldown; the three-second promise is the base skill, not removal of authored modifier behavior. A three-second unmodified storm becomes recastable after completion, subject to normal actor/equipment/resources and normal tick scheduling.

Changed: `AreaRuntime`, `HytaleSkillExecutionSystem`, `NativeBlizzardVisuals`, native startup audit, Blizzard runtime/catalog entries, snow system/spawner and generator. Persistence, resource and cooldown service implementations were not changed.

## Healing Beam

The particle-facing axis is reversed from AA. Rotation now maps **native -Z** onto each presentation tangent, leaving caster/recipient gameplay ownership unchanged.

The stream samples a quadratic Bezier with fixed live endpoints and a midpoint sag of `min(1.2 m, 0.12 × segment length)`. Each sample has its own tangent-derived rotation, including each Arc/Fork/Chain segment. Sampling is bounded to 24 emitters per segment, zero-length segments emit none. Explicit beam maxDuration is 0.1 seconds, not 75.

This implements a curved, weighted-looking stream using native tangent-oriented short-lived particles. **It is not a native spline constraint that forces every individual particle to follow a Bezier for its entire lifetime.** Native velocity continues from each tangent; visual coherence, flow sign and behavior during movement must be checked connected. The actual target/LOS segment stays unchanged: the curve cannot heal around an obstacle or alter the existing 1.5-second LOS grace.

No change to healing magnitude, 4 Mana/s upkeep, target lock/polarity, 18 m base range, support credit, Triage/Overflow, continuation coefficients or the five-secondary cap. AA's native healthbar/text capability gaps remain as documented in its report; this task does not close them.

## Validation and observed failures

Focused regressions passed before final validation. New `Stage13PresentationABTest` has five tests: Bezier endpoints/sag/-Z tangents and bounds; authoritative sweep fraction; three-second active recast gate; bounded snow packet lifecycle; two read-only radial controls/no resource controls.

AA's transform regressions now check -Z because **connected evidence falsified the previous axis**; they still assert exact transformed vectors for cardinals, diagonals, elevation and each continuation. No assertion was relaxed. The shared-root spawn budget regression now uses derived echo fixtures instead of invalid second manual casts; it still asserts the same 48-effect limit and fifth-field rejection.

An initial full validation overlapped the isolated smoke server. Seven icon-updater tests correctly failed their process-stopped safety check. This was an orchestration mistake, not an icon-updater defect. Initial failed XML and first-candidate smoke are retained under `first-candidate/`. The safety check was not changed. After fixing the additional maxDuration issue and stopping the smoke, the complete RPG/native validation was rerun without a concurrent server; CanvasUI retained results were up-to-date.

Final retained results:

| Suite | Tests | Failures | Skipped |
| --- | ---: | ---: | ---: |
| RPG | 2,154 | 1 | 0 |
| Native control | 53 | 0 | 0 |
| CanvasUI | 21 | 0 | 0 |
| Total | **2,228** | **1** | **0** |

**2,227 passed.** The sole remaining failure is unchanged `TraceArchiveFixtureRoundTripTest`, <=15% compressed/source fixture ratio. Current UI fixture is 6,205 bytes; independent read-only GZIP diagnosis is 1,212 bytes (19.53%). Skill fixture is 2,071,438→109,011 bytes (5.26%). The Java test's byte-for-byte recovery/event checks pass before its compression-ratio assertion fails. No fixture, assertion, trace writer or rotation configuration was changed to produce a pass. Full retained PASS remains false. Durability, escrow, exact-once and nonblocking tests passed unchanged.

44 documents were scanned by the retained CustomUI validator. This is static syntax checking, **not Hytale client parsing/rendering proof**.

The final exact candidate passed isolated three-mod boot/native asset resolution, actual shard construction/first-solid collision/movement/cleanup, retained native projectile construction/expiry checks and clean shutdown (exit 0). The server audit resolves shipped `Impact_Ice` and logs `RPG_HEAL_BLIZZARD_ASSETS cohort=AB`. No client was attached; this does not prove radial HUD rendering, direction, snow appearance or curved particle motion.

Evidence: [cohort-ab](../../evidence/stage-13/cohort-ab/), including `package-validation.json`, `differential.json`, `server-smoke.txt`, XML validation results and `deployment.json`.

## Package and deployment

Exact cumulative AA artifact plus compiled scoped AB changes; unrelated AA entries remain byte-identical, including Y trace storage and owner icons. Use the archived AB test artifact rather than the raw Gradle JAR. Exactly three distribution JARs in the archive; existing ImmersiveNPCs in the live environment was preserved, not replaced.

| Artifact | SHA-256 |
| --- | --- |
| RPG / deployed JAR | `1CA247EF81C0066C8D4D6CC1C40D0EF0FE2F9E56E0D574E27DFB3D349B430217` |
| Three-mod ZIP | `8DFE483A472B9B1012735F17CC0E5480B92428D67183F91E80903A4DF7DC2631` |
| CanvasUI | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| HytaleDevLib | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |

Deployed **2026-09-12 14:31:18 UTC** to `Hytale/data/pre-release/Saves/RPG/mods/HytaleRPG-0.0.25.jar`. Installed SHA-256 equals the packaged and smoke-tested artifact. Before replacement, the stopped save's **489 files** were copied and hash-verified at `evidence/stage-13/cohort-ab/before/save/20260912T143113Z` (local Git-ignored backup). All **488 non-target files** remained unchanged. Other mods, NPC data, player saves and active traces were preserved. AA JAR remains in backup and in its previous archived artifact. Isolated binary AA→AB→AA rollback passed; no save migration occurred.

Live startup verification remains pending owner launch. Look for `R032-AB` at top right and the startup marker above; neither repository HEAD nor installed bytes alone proves a running client used AB.

## Owner connected checklist

1. Launch/rejoin RPG. Confirm the `R032-AB` badge. Use `/rpg skilltree` to equip Blizzard and Healing Beam; use `/rpg dev ability-status` to confirm E/R projection. Use an accepted staff/spellbook.
2. Cast Blizzard once on flat ground. Expect no black wire circles, immediately visible local snow, falling shards, native ice impact at each actual contact and per-impact Ice Ball Death sound only. Base storm ends at 3 seconds. Press again during the storm: it must reject without a second payment. At the end, cast again with sufficient Mana.
3. Watch the radial overlay throughout: verify it covers the correct E/R icon, moves **clockwise**, and clears at readiness. Test both skills, a different cooldown duration, resolution/UI scale changes and an empty slot. Native icon/frame/key bindings and Health/Mana/Stamina must remain unchanged. Report a screenshot if native control direction or installed anchor alignment differs.
4. Test Blizzard on slopes and under an overhang. Impact must appear where the shard terminates, not on the floor beneath a roof. Check snow stops with normal lifetime and does not accumulate across repeated casts. Check abrupt cancellation/world change for bounded cosmetic tails, not persistent emitters.
5. Hold Healing Beam on an allowed friendly NPC at several compass headings and elevations; move both actors. Expect a gently sagging stream flowing caster→target. Test Arc/Fork/Chain: each branch uses its own curve. Confirm Health change/zero at full Health via the existing trace, release cleanup, Mana upkeep and LOS/range rules.
6. Keep new skill/UI/server logs for review. Rejoin once to confirm loadout persistence. Do not label radial direction, beam flow or snow timing connected-PASS until actually observed on AB.

Remaining limitations: one retained compression-ratio gate; all new client visuals unverified; native particle motion is a tangent-sampled approximation, not a spline-bound simulation; previously documented native healing text/healthbar gaps; UI poll/tick quantization and brief already-emitted particle tails on abrupt cancellation.
