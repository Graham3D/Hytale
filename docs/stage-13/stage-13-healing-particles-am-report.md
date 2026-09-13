# R032-AM — shipped Healing Beam particles on an RPG-controlled path

Date: 2026-09-12 local / 2026-09-13 UTC. Branch `RPG`; starting HEAD `fa8adf3e591acdcae5028e319d51999e22a5e7da`.

## Status and scope

**IMPLEMENTED · PACKAGED · DEPLOYED** to the normal RPG single-player mods directory at 2026-09-13T01:17:16Z. **AM connected visual acceptance and live startup are unverified.** This is not another probe-only cohort.

The owner explicitly rejected a new visual style and native Beam/solid laser geometry. AM replaces AL's selected native Beam renderer with particle anchors that reuse the shipped `Beam_Heal_Green2` child visuals. No sprites, colors, gradients, cross graphics or textures were authored for AM. Historical AL Beam code/materials remain available to retained isolated regression checks; normal Healing Beam never selects them.

Gameplay is unchanged: healing, full-health eligibility, targeting, equipment, Mana upkeep, LOS/range, cooldown, continuation membership/coefficients, credit, channel lifecycle, native input/HUD ownership, persistence and Blizzard were not edited. The badge/optional observer identifies AM.

## Video and asset identification

Inspected `C:\Users\Zemio\OneDrive\Desktop\beam.mp4`: 1242x314, 60 fps, 2.02 seconds. SHA-256 `F3B77CCF6E8D4001BA6C8F961B794020C262BA48D3653861C7E1B5240FA48823`. Extracted frames show a dotted green/yellow body with separately visible large glowing cross pulses, not a solid strip. The source clip is retained in its original location; temporary decoding tools/frames are under ignored `run` directories, not a runtime dependency.

Read the exact installed `Server/Particles/_Test/HealBeams/Beam_Heal_Green2.particlesystem` and all associated spawners, and inspected the referenced sprites.

| Visible component | Exact child | Existing texture | Shipped rendering/appearance |
| --- | --- | --- | --- |
| Small green/yellow blips | `Beam_Heal_Green2_Sparks` | `Particles/Textures/Basic/Ball3.png` | Erosion, billboard, linear filtering, random U flip; initial scale 0.1–0.15; yellow/green age colors |
| Circular green glow behind a pulse | `Beam_Heal_Green2_Glow` | `Particles/Textures/Circles/Circle_Glow.png` | BlendAdd, billboard; `#72ff1f` to `#0f7000`, expanding glow |
| Healing cross within the pulse | `Beam_Heal_Green2_Plus` | `Particles/Textures/Shapes/Health_Regen_Plus.png` | Erosion, billboard; `#f8ffe4` to `#79ff00`, original scale/opacity animation |

The large healing symbol is the **paired Glow + Plus**, not a newly drawn cross or the unrelated `Effect_Health_Pack` recipient effect. The similarly named `Beam_Heal_Green2.particlespawner` exists but is **not referenced** by the shipped parent system; it was not substituted for one of the actual children.

Shipped Sparks emits 20/s, has 3-second particle life, forward speed 4, and `TrailSpawnerPositionMultiplier=1` (particles remain in world space when the anchor moves). Glow/Plus have 0.3-second particle life and -4 particle speed; their parent spawner groups additionally have +6 velocity and repeated group creation. Those independent emitter/particle motions cannot encode the RPG path endpoints.

Pinned build remains **0.7.0-pre.2**:

- Server JAR SHA-256: `9CDC1E77DADFDAD0B1D849977CDAD5C2752207F538272FB3D76D4E915546B25E`.
- Assets.zip SHA-256: `0E35DF2D4E4A803F7752CB300FA7C9FE7ACDF62B4311978DA95C7AFF7ABBC126`.

The installed ParticleSpawner codec documents `TrailSpawnerPositionMultiplier`: **0 moves fully with the anchor; 1 does not move with it**. AM sets it to zero, disables autonomous velocity and uses model-bound particles with clear-on-remove. These are actual supported schema/protocol fields, not guessed client APIs.

## Derivative assets: exactly what changed

`Server/Particles/RPG/HealingPath` contains three derivative spawners and two small systems:

- `RPG_Heal_Path_Sparks`, `_Glow`, `_Plus`: copied child appearance data with unchanged `Particle` object, render mode, billboard behavior, filtering, scale, color/opacity keys, texture/frame/UV references and particle lifespan.
- All derivative initial speeds are zero. Position/rotation trail multipliers are zero; emit offsets are zero so particle centers do not scatter beyond the externally placed path point.
- Sparks has a bounded two-particle concurrent cap and 1/s emission **per sample anchor**, instead of replaying the stock 20/s full-beam emitter at every sample. This gives overlapping stock three-second fades without replacing the entire body on a timer. It changes emission topology/count, not the sprite or colors.
- Glow/Plus retain their stock burst/0.3-second wave/life settings and stock 20-particle concurrency ceilings. The ceiling is not an instruction to emit 20 particles; each child still emits one per 0.3-second wave. Keeping the native ceiling avoids imposing a new one-particle cap that might suppress renewal at the lifetime boundary. The `Pulse` system pairs the two at the same anchor, preserving the stock large symbol composition.
- Each system creates one stationary spawner of each declared child. The old autonomous parent-group velocity and indefinitely created moving spawner groups are not copied.
- `RPG_Heal_Path_Blips` and `_Pulse` model wrappers carry only these particle systems at scale 1, attached to the entity, with `ClearParticlesOnRemove=true`. The empty native model is a **non-rendered positioning anchor**, not the old full `Beam_Heal_Green2` carrier. No solid geometry or `BeamComponent` is attached.

The pure regression compares every derivative's complete `Particle` object and lifespan to the installed originals, and permits only the named placement/emission-budget differences. The native startup audit repeats appearance equality after actual asset resolution and verifies zero velocity, anchor following, child counts and model-particle binding. There are **no new texture files in the AM JAR diff**.

## Runtime architecture

`HealingTetherPresentation` now selects `SplineHealingParticleVisuals` for the body/pulses, retaining the separate existing recipient/staff cosmetic owner. There is no production native Beam allocation. `HytaleSkillExecutionSystem` reports the selected Blips + Pulse system IDs rather than the previous Beam texture asset.

Each logical segment has independent elastic history. The existing bounded critically damped seven-knot motion is sampled through a cubic Hermite interpolant (eight subdivisions per interval, 49 points) and a cumulative arc-length table. Stationary endpoints give a straight line. Motion retains the previous lag/settling/reset rules; no gravity or gameplay geometry uses this interpolation.

### Base stream

Persistent particle anchors are distributed approximately every **0.2 world units** along arc length, including both endpoint centers. At 18 units this is 91 anchors. Each retains its ModelComponent and emission state across ordinary updates; only TransformComponent positions change. Growing/shrinking a segment adds/removes the necessary pool tail. Up to 128 body anchors per segment bounds range/continuation extremes; above roughly 25.4 units density decreases rather than allocating unbounded entities.

This is a denser emitter-placement strategy, not an assertion of pixel-identical particle count or brightness. Each anchor can have at most two live stock Spark sprites, and their original fade/color animation remains. Startup fill, apparent density and client particle-budget effects need connected observation.

### Healing pulses

There are **three moving pulse anchors per segment**. Each pairs Glow + Plus and advances at 6 world units/s using arc distance, never uniform spline-parameter steps. Six is the shipped parent-group forward-speed value; it is not claimed to reproduce the net velocity of every original parent/child animation combination. At the destination, the pulse anchor is retired and a new one begins at the source; this prevents client interpolation of the same entity backward through the beam. The body pool remains alive during pulse retirement.

No sample/pulse center is placed beyond the recipient. Sprites remain billboards at stock size, so their pixel edges naturally have a radius around their center; there is no invented clipping shader. Source is the existing authoritative caster anchor, **not a newly proven animated staff-head transform**. Native staff-head cosmetics and recipient `Effect_Health_Pack` remain unchanged.

### Safety and cleanup

- Immutable jobs coalesce per channel and execute through the command buffer after Store processing. No `Store is currently processing` mutation bypass was added.
- At most six logical segments per channel, 128 body + three pulse anchors per segment, 512 root/pending entries, and **2,048 active particle anchors globally in the owner**. Visual-capacity failure is reported and cleans up that visual root; it does not bypass, refund or modify gameplay.
- All particle anchors are native non-serialized entities. No NPC spawn, Health component, progression record or save-data mutation is used for presentation.
- Pending creation is invalidated before release cleanup. Branch replacement resets only that branch; other histories remain intact. Missing/stale branches remove their anchors.
- Release, terminal channel state, invalid target/range/world/owner cleanup reuse the established production cleanup path. Clear-on-remove is asserted on all derivative model bindings.
- Normal telemetry remains event-driven (started/first update/removed/failure). Per-anchor activity is not dumped into NORMAL tracing; the existing optional channel observer can inspect representative Blip/Pulse model delivery.

More native entities are used than AL's one Beam entity per segment. Bounds and lifecycle have been tested; this does **not** certify connected many-player performance or upgrade the formal Stage 13 performance gate.

## Validation

Focused regressions and the actual isolated three-mod native smoke passed during implementation. Native checks exercised production owner creation/update/removal, exact derivative appearance resolution, non-serialization, no BeamComponent, persistent body ModelComponents, forward pulse progression/retirement, endpoints at 2/6/12/18/25.2 units, six-branch allocation, recipient-identity replacement, unchanged sibling state and same-buffer cancellation. Every native result explicitly says `connectedProof=false`.

Five new retained tests cover stock asset equality/allowed derivative differences, exact Glow/Plus pairing, scale/attachment/lifecycle fields, dense bounded arc-length paths, smooth independent elastic history and production no-solid-renderer selection. Existing revision/renderer-selection assertions were updated to require the newly authorized AM path; gameplay/persistence assertions were not weakened.

### Final retained validation and delivery

The final complete invocation passed **2,274 tests: 2,197 RPG + 56 native-control + 21 CanvasUI**, with zero failures, errors or skips. 34 CustomUI documents passed validation. An earlier complete passing candidate had an unnecessary one-particle concurrency cap on Glow/Plus; final review restored their exact stock ceilings and added a preservation assertion. A second full run passed after that correction. The intermediate candidate/receipts remain under `attempts/pre-final-pulse-cap`; **that candidate was never deployed**. No failing assertions were deleted or release thresholds relaxed.

The exact final JAR passed the final isolated three-mod smoke, native derivative-resolution/appearance audit, actual particle-anchor lifecycle/branch/pulse audit and retained native gameplay/observer checks. `tools/Package-HealingParticlesAM.ps1` checks every changed JAR entry against an explicit presentation/revision/audit-only allowlist, rejects removed entries, verifies all suite counts, hashes each of the three archive entries, and validates binary rollback AL → AM → AL.

| Artifact | SHA-256 |
| --- | --- |
| Final built/archived/installed `HyARPG.jar` | `DA75E29BB572DDAE7639A9C8AF98A042332CDCDCD396B491C9D5C0A52C327237` |
| Final `HyARPG-R032-AM-three-mods.zip` | `9E7F3E7E553C06A98E8E47707E6A08422FCBE8E4CB90439B1FFF6D295F017E45` |
| Previous live AL JAR retained for rollback | `899C80A31F7165456909EC33DD7BCBC0ECD1EE6843DF0120F2822B037BBAF87D` |

Installed path: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`. Internal identity/data directory remains `InigmasGames:HytaleRPGPhase00Audit`.

The deployment checked that Hytale/server processes were stopped, backed up **522 files / 303,127,773 bytes**, verified every backup hash, staged the final JAR and matched its installed SHA-256. Backup: `evidence/stage-13/cohort-am/before/save/20260913T011712Z/RPG`, with the old JAR also retained in sibling `retired-live-name`. These private backups remain local and Git-ignored. Every non-target live file stayed byte-identical: NPC data, progression, traces and all supporting mods were preserved. ImmersiveNPCs remains installed live; the distribution archive contains only HyARPG, CanvasUI and HytaleDevLib.

Receipts are in `evidence/stage-13/cohort-am`: `asset-provenance.json`, `full-validation.txt`, `server-smoke.txt`, `server-smoke-summary.json`, `package-validation.json`, `deployment-validation.json`, archive-fixture results and retained JUnit XML. The provenance script records exact original and derivative hashes and structural appearance equality (JSON numeric `1` and `1.0` are compared as equal values, not different text formatting).

Deployment is verified on disk, and the final runtime executed in the isolated native smoke. The normal live world was **not** launched for connected acceptance during this task. No local result is presented as a visual-match or native client particle-rendering certificate.

## Connected check

Restart Hytale; confirm **R032-AM**. Equip Healing Beam normally and channel it on a friendly target. No probe command selects this version. Compare to `beam.mp4`: small green/yellow body blips, larger circular healing crosses with glow, no solid ribbon/laser. Check short/long distance, target/caster movement, abrupt reversal, settling, continuous long channel, full-health recipient visuals, continuation branches, and cleanup on release/target loss/world exit. Report density, brightness, pulse speed, lifetime flicker or residue as actually seen.

`/rpg-heal-probe channel none` remains optional telemetry only. The legacy standalone beam control is not this production test. **AM connected rendering, startup on the live world, exact visual match, no-blink behavior, animated staff-tip attachment and multi-player performance remain unverified.**
