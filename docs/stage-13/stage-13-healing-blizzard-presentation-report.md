# R032-AA — Healing Beam presentation and falling Blizzard

**DEPLOYED — owner-authorized test build, 2026-09-12 13:47:29 UTC.** The owner's subsequent instruction, “Always deploy a new build!”, supersedes this task's initial package-only restriction. The exact packaged AA JAR was installed in `Hytale/data/pre-release/Saves/RPG/mods/HytaleRPG-0.0.25.jar`; deployed and artifact SHA-256 both equal `C39517031B70B77241EC0FDF676C6679F74DF09F8EE1945CB8FA784DC97E3588`.

The complete stopped save (487 files, including previous RPG JAR and mod data) was backed up and hash-verified under `evidence/stage-13/cohort-aa/before/save/20260912T134723Z` (local, Git-ignored). All 486 non-target files remained unchanged. Other mods, saves and traces were not modified. See [deployment receipt](../../evidence/stage-13/cohort-aa/deployment.json).

Current status: **IMPLEMENTED / PACKAGED / DEPLOYED; CONNECTED-VERIFIED = false; live startup confirmation pending owner launch.** The one known compression-ratio test failure and native presentation capability gaps remain unresolved. Installation is not connected evidence.

The sections below retain the original package-time findings and package-only status as historical evidence; the deployment update above supersedes their deployment restrictions and references to Z as the currently installed JAR.

Date: 2026-09-12. Branch: `RPG`. Cumulative baseline: `eea0fa128053300187815bff8ae70bfbd30e3f33` (R032-Z), retaining R032-Y trace-storage hardening. Implementation commit is the commit introducing this report; its exact SHA is recorded in the companion `stage-13-healing-blizzard-checkpoint.md` after commit creation.

## Authority and scope

Owner input: `Hytale RPG - Skill Bug List.docx.md`, Healing Beam HB-001–008 and Blizzard BL-001–005. The subsequent owner reply explicitly requires **only `SFX_Ice_Ball_Death` for each shard**. This supersedes BL-004's storm-loop requirement: no loop was added. Sound is emitted on a shard's terminating solid impact; an expired/cancelled shard does not fabricate an impact or sound.

[Scoped master amendment](../corrections/R032-AA-healing-blizzard-contract.md) reconciles SK-069's old 8-second duration to the owner-approved 3 seconds. Both packaged catalog and executable profile now say 3 seconds. The external Downloads master document was not edited; the checked-in amendment is the explicit scoped supersession, not a claim that the external document was rewritten.

No native ability projection/input, equipment resolution, HUD ownership, XP artwork, resource formulas, cooldown formulas, persistence, shield escrow, rewards or exact-once code was redesigned. Owner artwork under `art/` remains untouched/untracked. No live save, mod data, trace file or installed JAR was changed.

## Connected evidence reviewed before editing

Read the latest RPG server log `2026-09-11_20-47-02_server.log` and active skill trace. The sampled session contained 3 connection starts/terminations, 38 `CONNECTION_TICK` events and 38 `HEAL_APPLIED` events. The observed recipient was full: Health-before and Health-after were both 100, requested healing was 2.3175 per sampled pulse, and actual healing was zero. This proves attempted healing against that full target, not a failure to reach the healing service. It does not prove visible particle direction or healing-number rendering.

The owner's connected observations establish the world-North row artifact and reversed-looking beam. The existing position-only `ParticleUtil.spawnParticleEffect` overload supplied identity rotation at every sampled point. That is the verified code-level cause of the fixed-world heading: moving a sample did not orient its emitter. The apparent recipient-to-caster flow is consistent with that fixed native emitter heading when the recipient is on the opposite side; **a distinct client-side velocity-axis inversion has not been proven**. Do not close HB-001 purely from the new transform test.

## Exact installed native audit

Pinned installation: Hytale **0.7.0-pre.1**, pre-release package.

| Input | SHA-256 |
| --- | --- |
| HytaleServer.jar | `9CDC1E77DADFDAD0B1D849977CDAD5C2752207F538272FB3D76D4E915546B25E` |
| Assets.zip | `0E35DF2D4E4A803F7752CB300FA7C9FE7ACDF62B4311978DA95C7AFF7ABBC126` |

The audit used the actual installed assets and Java API/bytecode, not assumed online documentation.

| Capability | Classification and evidence | Candidate behavior / remaining boundary |
| --- | --- | --- |
| Beam particle system | **VERIFIED_NATIVE**: `Server/Particles/_Test/HealBeams/Beam_Heal_Green.particlesystem`; references `Beam_Heal_Green3_Sparks` and `Beam_Heal_Green3_Plus` | Reuses native system. Spawners have positive speed 2–4, local Z emission offset and `ParticleRotateWithSpawner=true`. Local +Z is the selected presentation-axis interpretation; connected velocity direction remains unverified. |
| Native rotation | **VERIFIED_NATIVE**: `Rotation3f` uses YXZ rotation; `lookAt` faces -Z; `ParticleUtil` explicit overload takes yaw, pitch, roll | Map local +Z to the segment direction instead of using -Z-facing `lookAt`. Native quaternion tests pass. This is not client rendering evidence. |
| Green healing cross | **VERIFIED_NATIVE**: `Common/Particles/Textures/Shapes/Health_Regen_Plus.png`, referenced by the native healing-beam plus spawner | Remains in the beam. Cross attached to each floating number is **UNVERIFIED / NOT IMPLEMENTED**. No replacement image authored. |
| Actor Healthbar | **VERIFIED_NATIVE**: `Server/Entity/UI/Healthbar.json`, `Type=EntityStat`, `EntityStat=Health`, actor hitbox offset Y=-30 | Native ownership retained; no duplicate bar, stat writes or UI-list replacement. Whether the particular ImmersiveNPC recipient attaches/shows it remains connected-unverified. |
| Authoritative generic combat-only visibility | **UNVERIFIED**: audited `DamageDataComponent.lastCombatAction` timestamp and NPC `CombatSupport.isExecutingAttack`; neither establishes a general current in-combat flag for all eligible recipients | Combat-only custom Healthbar remains gated. No arbitrary timer and no forcing peaceful NPCs into combat. This is not a claim that no suitable API could exist anywhere. |
| Actor combat text | **VERIFIED_NATIVE**: `CombatTextUpdate(hitAngleDeg,text,color)`, `EntityTrackerSystems.EntityViewer.queueUpdate`, native `DamageSystems.EntityUIEvents` producer, `UIComponentList`, `Server/Entity/UI/CombatText.json` | Queue actual healing through the native tracker only when the recipient already has CombatText and is visible to the caster. Missing attachment is reported, not replaced. |
| Per-event left-side offset / accompanying sprite | **VERIFIED_UNAVAILABLE in the audited CombatTextUpdate schema**: packet has angle, text and color only; no offset/image member | Native layout retained. Required left-side placement and sprite-number co-motion are **not fulfilled** by this candidate. Other API paths remain unverified. No global native CombatText asset override. |
| Native heal-number aggregation | **UNVERIFIED**: no aggregation capability established in the audited producer/packet | Separate bounded 0.5-second presentation accumulator; gameplay pulses and telemetry are unchanged. |

Native `CombatText.json` specifies X random offset 20–60, Y 10–30, 0.6-second duration, upward motion 80, scale/fade animation. These are native settings, not a new left-side RPG UI. Native queue acceptance does not prove the client displayed the number.

## Healing Beam changes

`NativeBeamTransform` derives `direction = normalize(target - source)`, yaw `atan2(x,z)`, pitch `-asin(y)`, roll zero. Samples are collinear, bounded to nine, and omit the exact target endpoint because the native emitter extends forward. Each refresh uses live endpoints. The same presentation port handles primary and Arc/Fork/Chain segments, so each continuation gets its own source-to-destination rotation rather than inheriting the primary beam's heading.

`HealingTextAccumulator` records only `max(0, HealthAfter - HealthBefore)`, keyed by owner, world, root, recipient, skill instance and correlation. It aggregates for 0.5 seconds independently of gameplay, including zero-only windows. It is bounded to 768 pending keys; capacity eviction flushes a value rather than creating unbounded storage. Channel end flushes; teardown without a valid viewer clears presentation state. No native ECS references are retained by the accumulator.

`NativeHealingText` uses the recipient's existing native CombatText component and the caster's visibility tracker. Text is green, positive actual healing uses `+value`, and full-Health attempts produce `0`. Near-full 96→100 contributes 4, not requested 9. Overheal is not actual healing. `HEAL_PRESENTATION` reports the queue outcome, actual amount, pulse count and original IDs. For example, `NATIVE_COMBAT_TEXT_NOT_ATTACHED` is an explicit capability boundary, not silent success. Existing `HEAL_APPLIED` pulses remain unsampled and separate.

Healing magnitude, Mana upkeep, 18 m range, target lock/polarity, 1.5-second LOS grace, release semantics, Triage/Overflow and continuation coefficients were not modified.

## Blizzard changes

Base profile: 3.0 s duration; 6 m zone radius; 24 m placement; 2 m local impact; coefficient 0.38; Chill 1; existing per-target root ICD 0.75 s. Eleven scheduled carriers at t=0.00, 0.25, …, 2.50, each beginning 7.5 m above its sampled legal surface and descending nominally in 0.45 s (16.67 m/s). Final nominal impact is t=2.95. Compiled modifiers still determine actual geometry, duration and cadence.

`AreaRuntime` gives each stratified overhead shard an authoritative moving position. It sweeps from the previous position to the next using `HytaleAreaQueries.shardContact`, which calls the actual native `CollisionModule.findCollisions` with a 0.2 m-wide box (half-extent 0.1). It selects the first solid collision and converts box-center time of impact to a surface contact point. Character/trigger collision is not a second damage authority. Existing RPG area resolution applies damage/Chill at the validated contact.

The terminal latch is set before applying an impact; a carrier impacts at most once. No valid surface means expiry, not a fabricated ground hit. Root expiry terminates first, so delayed processing does not award historical impacts after expiry. Under scheduling stalls some late shards can be omitted rather than violating the lifetime gate. Roof rejection is bypassed only for the stratified falling path so actual swept roof collision can resolve; existing non-stratified overhead skills keep their prerequisite.

Seeded/inset impact selection and existing root spawn/candidate budgets remain. On flat base geometry, center offsets are within 4 m so a 2 m local impact fits inside the 6 m zone. Legal compiled variants retain the original shared budget and hit ledger.

### Native rendering assets and audio

- Core source: `Common/Blocks/Miscellaneous/Portal_Shard.blockymodel` and matching `Portal_Shard_Texture.png`. Entity model validation does not allow a `Blocks/` model path. Byte-identical copies are packaged under `Common/VFX/RPG/Blizzard/`; `Server/Models/RPG/RPG_Blizzard_Shard.json` references them. Scale 1, local Y rotated downward by pitch pi. The purple/native texture was not silently described as recolored ice: silhouette, tint suitability, scale and visible descent orientation remain **UNVERIFIED** pending owner QA.
- Trail source: `Server/Particles/Projectile/Ice_Boulder/IceBoulderTrail.particlesystem` → `RPG_Blizzard_Trail`, attached via the native model's particle list.
- Impact source: `Server/Particles/Combat/Impact/Misc/Ice/Impact_Ice.particlesystem` → `RPG_Blizzard_Impact`, at actual surface contact, once per terminating impact.
- Ambient source: `Server/Particles/Weather/Snow/Snow_Heavy.particlesystem` → `RPG_Blizzard_Snow`. The original weather-sized emitter was unsuitable as-is: localized derivative uses a unit square inscribed in the effective radius, reduced density, short-lived particles and one logical emitter per zone, refreshed every 0.1 s rather than per shard. Derivative assets are generated reproducibly by `tools/Generate-BlizzardParticles.ps1`.
- Derivative particles are bounded to 80 per spawner and 0.1–0.2 s lifetime; impact/snow emitter lifetime 0.05 s. No emissions are scheduled after root cleanup. A configured cosmetic tail of up to roughly 0.25 s is not a guarantee of client cleanup timing; verify it connected.
- Only native sound `SFX_Ice_Ball_Death`, verified at `Server/Audio/SoundEvents/SFX/Projectiles/Ice_Ball/SFX_Ice_Ball_Death.json`, on each solid impact. Uses its native randomized `Crystal_Ice_Blast_01..03.ogg` clips and attenuation. **No storm loop.** Already-started native one-shots may finish after root expiry; no unverified sound-stop handle was invented.

The shard carrier has native Model, Transform, BoundingBox, UUID and NetworkId components, is non-serialized, and has a 0.6-second native despawn backup. It has no native projectile physics or damage interactions. AreaRuntime is the sole movement/collision/damage authority. Cleanup is root/owner scoped, including queued removal and world-thread removal when no command buffer is available.

### Failures caught and repaired in isolated testing

1. Native ModelAsset rejected direct `Blocks/Miscellaneous` paths: model assets must use Characters, NPC, Items or VFX. Fixed with byte-identical VFX-path copies. Initial failure retained in `initial-asset-failure/`.
2. The first carrier movement implementation replaced `TransformComponent`. That lost native `sectionRef` membership and left stale entity references during chunk serialization at teardown. Isolated world shutdown failed with `Invalid entity reference!`. Fixed by mutating the existing native component with `setPosition`, preserving membership. The native audit now asserts the same TransformComponent and section reference survive movement, followed by valid removal and clean server shutdown. Initial failure retained in `initial-transform-failure/`. No live world was used.

## Changed-file map

| Files / area | Reason |
| --- | --- |
| `NativeBeamTransform`, `NativeHealingText`, `HealingTextAccumulator` | Per-segment native transforms; capability-gated native text; actual-delta visual windows |
| `HytaleSkillExecutionSystem`, `RpgTraceEventType` | Wire presentation and teardown, bounded result telemetry, Blizzard impact sound |
| `AreaRuntime`, `AreaWorldPort`, `HytaleAreaQueries` | Authoritative falling transform, swept first-surface collision, root cleanup |
| `NativeBlizzardVisuals` | Unsaved native model/trail ownership and isolated actual-API construction/collision/removal audit |
| `NativeProjectileSpawnAuditCommand`, `NativeSupportTetherAudit` | Extend existing opt-in empty-world audit and native asset-resolution gates |
| Catalog, Stage 06 area profile, scoped amendment | Explicit owner duration/cadence change |
| Blizzard model, particle and VFX-path assets; generator | Reuse verified shipped assets with bounded localized derivatives |
| `Stage13HealingBlizzardTest` | Eight new transform, continuation, aggregation, first-contact, expiry and cancellation tests |
| Existing Stage 06/11 area tests | Supply swept-surface fixtures; update authored 8→3 timing/count expectations, while retaining spawn-budget, cap and cleanup assertions |
| Candidate/package/smoke tools | Preserve cumulative Z bytes, exact three-mod archive, stronger native gates and isolated rollback check |

No failing trace/persistence test was deleted or edited. Cascade stress uses legal modifiers to reach the same 48-spawn budget after shortening Blizzard; it still asserts the cap and rejection, rather than weakening the assertion.

## Validation results

Focused tests ran during implementation. Then `gradlew.bat check --console=plain` ran the complete RPG and native-control suites once. Because its failure prevented Gradle from continuing to CanvasUI, `gradlew.bat :canvas-ui:check --console=plain` ran the retained CanvasUI remainder separately.

| Suite | Tests | Passed | Failed | Skipped |
| --- | ---: | ---: | ---: | ---: |
| RPG retained | 2,149 | 2,148 | 1 | 0 |
| Native control | 53 | 53 | 0 | 0 |
| CanvasUI | 21 | 21 | 0 | 0 |
| Total | **2,223** | **2,222** | **1** | **0** |

42 CustomUI documents passed the retained validator. All eight new cohort tests passed. The retained durability, exact-once, escrow and nonblocking tests were unchanged and passed. This does not renew any connected performance qualification.

**Unresolved retained gate:** `TraceArchiveFixtureRoundTripTest.suppliedSkillAndUiFixturesRoundTripByteForByteWithSubstantialCompression`, line 54, requires compressed fixture size <=15% of source. Current active UI fixture is only 1,342 bytes. Read-only .NET GZIP diagnosis produced 614 bytes (45.75%); skill fixture was 291,602→19,895 bytes (6.82%). These diagnostic ratios are not substituted for Java test results. The Java test passed its byte-for-byte reconstruction, event-count/content and histogram checks before failing the ratio assertion. This is not evidence of event loss or broken rotation. Neither assertion nor live fixture was changed. **Full retained PASS is false.**

Exact candidate isolated smoke passed twice after the native membership fix; the final run additionally enabled all retained AA-applicable projectile/weapon/Snipe/movement/basic-path gates. It loaded exactly three mods, resolved all new assets, booted, exercised the native model allocation/movement/solid-cube sweep/removal contract, retained Fire Bolt native construction/expiry proof, and shut down cleanly (exit 0). The native cube at (4,200,4) produced first contact at y=201. The opt-in audit restores its temporary block and is restricted to the isolated audit world. This is actual server integration evidence, **not a connected visual, audio, NPC or gameplay-session pass**.

The smoke still reports retained plugin/asset target-version warnings and native SERR allocation messages. No claim that every log line is warning-free. No invalid entity reference occurred in the final run.

Machine-readable evidence and XML results: [cohort-aa](../../evidence/stage-13/cohort-aa/), especially `package-validation.json`, `differential.json`, `server-smoke.txt`, `native-spawn-integration.json` and `validation/`.

## Packaging, rollback and runtime identity

Build: current compiled scoped changes overlaid onto the exact accepted R032-Z artifact using `Build-R032AACandidate.ps1`. This preserves all unrelated Z entries byte-for-byte, including owner icon resources and cumulative Y trace-storage classes. It does not deploy the older Y artifact. The differential lists each changed entry. Use the archived candidate, **not the raw Gradle JAR**, for any later owner-authorized test deployment.

| Artifact | SHA-256 |
| --- | --- |
| RPG test JAR | `C39517031B70B77241EC0FDF676C6679F74DF09F8EE1945CB8FA784DC97E3588` |
| CanvasUI 0.1.0 | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| HytaleDevLib 0.5.0 | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| Three-mod ZIP | `F4B1DDEDE83DB3305B9A924461ED7164377370BD014656481D48D151B750EBB1` |

Archive: `evidence/stage-13/cohort-aa/HytaleRPG-R032-AA-three-mods.zip`; loose JARs in its `artifacts/` directory. Archive has exactly three entries, each hash-matched against its loose artifact. Isolated binary baseline→candidate→baseline rollback passed. No save-schema change or live migration was needed or performed.

Live RPG JAR remains R032-Z, SHA-256 `0737EC5DA371A8BC9E0E910EE48A362158D20E3F3D463F0F0E946E0A818C7B84`. No live runtime is claimed to run AA. The existing shared R032 version/HUD metadata is not a unique AA identifier; when deployment is later authorized, use the exact JAR hash plus startup `RPG_HEAL_BLIZZARD_ASSETS cohort=AA ... result=PASS`, then obtain connected evidence.

## Minimal connected QA after a separately authorized deployment

**Do not run this checklist against the currently installed Z JAR and attribute results to AA.** This document explicitly forbids automatic deployment. See the remaining feature gaps above before evaluating screenshots.

1. After backup/deployment/restart is authorized, confirm the AA JAR hash and startup marker. Open `/rpg skilltree`, equip Healing Beam in a native skill slot and an accepted staff/spellbook. Confirm equipped state with `/rpg dev ability-status`. Hold the assigned E/R input, then release; do not change native key projection.
2. Use a loaded permitted friendly recipient, not the caster. Test full Health (0 display), injured Health (actual increase), and near-full Health (clamped actual increase). Compare `HEAL_APPLIED` before/after values to aggregated `HEAL_PRESENTATION`. If outcome is `NATIVE_COMBAT_TEXT_NOT_ATTACHED`, record the recipient type; do not call it a gameplay-healing failure or claim a number rendered.
3. Test all eight horizontal headings, above/below elevation, caster/recipient movement and both moving. Record actual caster→recipient particle travel, not just particle placement. Test Arc, Fork and Chain individually and combined; each derived segment must orient from its actual previous endpoint. A reversed direction falsifies the current +Z velocity interpretation and calls for a presentation-axis-only follow-up.
4. Check LOS restoration just before 1.5 s vs termination at/after 1.5 s; range >18 m terminates immediately. Release, invalid alliance/equipment, Mana exhaustion, recipient despawn/death and world change must leave no orphan beam/text. Native healthbar/combat-only gaps must stay explicitly unverified.
5. Equip Blizzard through `/rpg skilltree`. On flat outdoor ground, verify 11 falls from about 7.5 m over 3 s, ~0.45 s descent, final nominal impact ~2.95 s, actual ice impact at contact and **only the per-shard Ice Ball Death sound, no storm loop**.
6. Repeat on slopes, beneath roofs/overhangs, at zone edges and with legal radius/duration/cadence modifiers. Verify first-solid contact, no late damage, no extra particle-driven Chill/damage, and the retained 0.75-second per-target root interval.
7. Inspect model tint/silhouette/orientation, attached trail and localized snow from near/far/third-person. Portal Shard may prove unsuitable visually; replace only presentation if so. Verify snow density/readability and cosmetic tail, cancellation, rapid repeated casts and world changes. One-shots may finish naturally; no persistent loop should exist.
8. Restart/rejoin and confirm the existing loadout persists. Retain skill/UI trace boundaries and server logs for review. This candidate adds `HEAL_PRESENTATION` and Blizzard impact position/sound telemetry; it does not fabricate client render acknowledgements.

Final status: **IMPLEMENTED (bounded candidate with explicit capability gaps); PACKAGED; NOT DEPLOYED; CONNECTED-VERIFIED = false; full retained release gate = FAIL (one unchanged compression-ratio test).**
