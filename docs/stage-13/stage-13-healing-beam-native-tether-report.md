# R032-AC — Healing Beam native elastic tether

**IMPLEMENTED / PACKAGED / DEPLOYED. CONNECTED-VERIFIED = false.**

2026-09-12, branch `RPG`, source baseline `1df592d3dc93c43201bc38716a91863e352a1157` (R032-AB). This is a presentation-only Healing Beam correction. It was initially packaged without deployment under the task-specific instruction, then deployed at the owner's immediate follow-up direction.

## Scope and invariants

The correction replaces the sampled particle representation of Healing Beam with a persistent native Beam representation and adds bounded elastic visual motion. It does not change target selection, target polarity, healing magnitude, Mana upkeep, LOS grace, range, channel ownership, support credit, Triage/Overflow, or Arc/Fork/Chain coefficients and eligibility. It does not change native ability projection, cooldown/resource authority, encounter persistence, escrow, rewards, XP, skills, HUD ownership, or unrelated Stage 13 systems.

Presentation refresh reads the most recent targets accepted by the existing healing pulse. It does not perform a second target search and cannot add or remove a healing recipient. Visual failures are isolated from gameplay and remove that caster's visual root instead of refunding, terminating, or changing the channel.

## Prior AB diagnosis

The requested `beam.mp4` was not present in the accessible repository, Codex attachments, Temp, Downloads, Videos, or Desktop locations, so no frame-by-frame media claim is made here. The prior lifecycle is nevertheless explicit in the AB production code: every visual refresh reconstructed a row of independent `Beam_Heal_Green` particle-system samples, each requested with a 0.1-second maximum duration, at approximately 0.1-second refresh cadence. Those emitters had no persistent identity or update operation. Client scheduling, particle birth/death and network delivery could therefore expose gaps or a reconstruction flash even at nominally matching cadence. Increasing density or overlapping more short-lived emitters was rejected as the primary solution.

AB also authored a permanent quadratic sag proportional to distance. That geometry was present even with two motionless endpoints, contrary to the corrected contract.

## Installed native Beam audit

The exact installed 0.7.0-pre.1 build was inspected:

- `HytaleServer.jar` SHA-256: `9CDC1E77DADFDAD0B1D849977CDAD5C2752207F538272FB3D76D4E915546B25E`.
- `Assets.zip` SHA-256: `0E35DF2D4E4A803F7752CB300FA7C9FE7ACDF62B4311978DA95C7AFF7ABBC126`.
- `BeamComponent.spawn(...)` creates a native nonserialized network entity with `Transform`, `NetworkId`, `UUID`, and `BeamComponent`. Omitting the optional lifetime leaves the component alive until explicitly removed.
- `AttachedBeam` supports an entity or position destination and rejects ambiguous definitions. This correction uses explicit position destinations for each polyline piece.
- `BeamSystems.Tracker` publishes component changes when `networkOutdated` is set and publishes removal when the owning entity is removed.
- `BeamComponent` permits at most 64 attached beams per owner. AC uses one attached beam per native segment entity and remains below this native limit.
- The shipped hookshot/rope path uses an attached `Rope` beam whose lifetime follows its projectile entity; this confirms that a native Beam is the intended continuous textured primitive rather than a periodically rebuilt particle row.
- Shipped Beam assets reference textures below `Common/Beams` or `Common/Trails`. Relevant available trails included `Charged_Blue.png`, `Fire.png`, `Rope.png`, and `Void_Green.png`.

The first candidate attempted the visually suitable shipped `Particles/Textures/Rnd/NatureBeam.png`. The real native asset validator rejected it because Beam texture paths must be rooted below `Beams/` or `Trails/`. That failed smoke is retained in `evidence/stage-13/cohort-ac/first-candidate/`; the validator was not bypassed. The final `RPG_Healing` Beam asset uses the shipped, validator-supported `Trails/Void_Green.png`.

## Native elastic tether design

Each logical Healing Beam connection owns an independent `ElasticBeamTether` state and a persistent set of six native Beam entities (seven pinned/model points). There is no fixed sag term.

At initialization, every interior point is exactly on the straight interpolation between authoritative source and destination. During each presentation step:

1. authoritative source and destination anchors replace the first and final points exactly;
2. the straight interpolation for each interior fraction becomes that point's moving rest position;
3. a critically damped spring integrates the interior point and velocity toward that rest position;
4. displacement and velocity are clamped to bounded presentation limits; and
5. points within the settled epsilon snap to the exact straight line.

Constants are deliberately bounded: spring coefficient 64, damping 16, integration slices no larger than 1/60 second, accepted elapsed time no larger than 0.10 second, maximum visual lag 1.35 m and maximum interior velocity 12 m/s. This produces inertial lag opposite endpoint motion without an unbounded whip. When movement ceases, damping returns the interior to an exactly straight line. The endpoints are never spring-integrated and remain attached to the authoritative anchors.

The runtime presents at 20 Hz in one coherent visual frame. Existing healing application cadence and gameplay timing are unchanged. Native entities and `AttachedBeam` objects are updated rather than despawned/recreated, so the texture remains continuous across refreshes. Six contiguous pieces approximate the elastic strand; the server never creates a new row of short-lived particle emitters.

## Continuations and cleanup

The primary segment key is stable for the channel target. Each Arc, Fork, or Chain connection uses a stable key derived from its continuation type, source and recipient. Every key owns its own point positions, velocities and native entities; no branch shares another segment's curve or motion history.

The visual frame is based on the secondary recipients selected by the latest authoritative healing pulse. Missing segments are reconciled and removed immediately. A root is also removed on normal release, invalid/removed target, LOS/range terminal, actor cancellation, incoming-damage cancellation, world/plugin lifecycle cleanup, or presentation failure. Native Beam entities are `NonSerialized`; they cannot become persisted save objects. Logical roots and segments are bounded to prevent an orphan or unbounded-branch failure mode.

## Changed implementation

- `ConnectionWorldPort`: added a batched logical tether frame while preserving the prior default port behavior for non-Hytale tests.
- `ConnectionRuntime`: publishes one primary plus the latest authoritative continuation segments at 20 Hz without recomputing gameplay targets.
- `ElasticBeamTether`: new bounded spring-damped point simulation with exact endpoints and exact straight-line settling.
- `NativeHealingBeamVisuals`: new owner for native Beam allocation, in-place updates, per-segment state and immediate reconciliation/cleanup.
- `HytaleSkillExecutionSystem`: routes Healing Beam presentation to the native owner and invokes cleanup at every existing channel terminal/cancellation boundary.
- `NativeBeamTransform`: removes the Healing Beam fixed-sag/sample contract; unrelated retained transform helpers remain available.
- `NativeSupportTetherAudit`: resolves the packaged Beam asset through the real installed registry and logs its texture and persistence contract.
- `Server/Entity/Beams/RPG_Healing.json`: points to shipped `Trails/Void_Green.png`.
- Build/smoke/package tooling: adds the AC native control, startup audit gate and a differential overlay package based on the exact accepted AB artifact.

## Verification

Focused production-path regressions passed for straight stationary geometry, exact endpoints, bounded movement lag, damped return to straight, abrupt direction reversal and independent branch state. Existing Healing Beam/Blizzard/support regressions passed. The new native control test uses the actual installed `AttachedBeam`/`BeamComponent` construction and native validation path rather than only simulating an RPG plan.

Final retained validation:

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| RPG | 2,157 | 1 | 0 | 0 |
| Native control | 54 | 0 | 0 | 0 |
| CanvasUI | 21 | 0 | 0 | 0 |
| Total | **2,232** | **1** | **0** | **0** |

**2,231 tests passed.** The sole retained failure is the unchanged `TraceArchiveFixtureRoundTripTest.suppliedSkillAndUiFixturesRoundTripByteForByteWithSubstantialCompression()` assertion: `fixture must compress to no more than 15% of source bytes`, expected true but was false. No trace fixture, compression threshold, writer, archive behavior, or assertion was changed. All retained Stage 13 durability, exact-once, escrow and nonblocking persistence tests remained passing.

The final candidate passed the isolated exact-three-mod server smoke with exit code 0, native `RPG_Healing` Beam resolution, plugin/manager/network startup and clean shutdown. The smoke is server/asset evidence only; it is not connected rendering evidence.

Package differential contains only the scoped AC classes and `RPG_Healing.json`; all other accepted AB JAR entries remain byte-identical. Binary AB -> AC -> AB rollback validation passed. No save migration was required or performed.

Evidence is retained in [cohort-ac](../../evidence/stage-13/cohort-ac/), including package validation, entry differential, native test XML, isolated smoke logs, the rejected first-candidate asset evidence and rollback result.

## Package

| Artifact | SHA-256 | Bytes |
| --- | --- | ---: |
| `HytaleRPG-0.0.25.jar` | `C3AC781F8BB5948490DE45E9C7176AACA8178F0B29A7729E9A9A71B02A42E891` | 2,943,840 |
| `HytaleRPG-R032-AC-three-mods.zip` | `D66F9C0163AC63ED2DC6BBFCB1B4B65D2862F5C45A79D8CE006B05A06A41A7C9` | — |
| `CanvasUI-0.1.0.jar` | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` | 117,075 |
| `HYTALEDEVLIB-0.5.0.jar` | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` | 265,646 |

The archive contains exactly those three JARs and their bytes match the individually hashed artifacts.

**DEPLOYED** at 2026-09-12 15:40:53 UTC to `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HytaleRPG-0.0.25.jar`. The installed SHA-256 is `C3AC781F8BB5948490DE45E9C7176AACA8178F0B29A7729E9A9A71B02A42E891`, exactly matching the packaged and smoke-tested RPG artifact.

Before replacement, all 493 files in the stopped RPG save were copied and individually hash-verified at `evidence/stage-13/cohort-ac/before/save/20260912T154050Z` (local rollback copy). The previous AB JAR hash was `1CA247EF81C0066C8D4D6CC1C40D0EF0FE2F9E56E0D574E27DFB3D349B430217`. Only the target RPG JAR was replaced; CanvasUI, HytaleDevLib, ImmersiveNPCs, mod data, world/player data and traces were not mutated. No save migration occurred. Live startup and client rendering remain pending owner launch, so connected verification remains false.

## Connected acceptance checklist

Using the deployed AC build:

1. Start/rejoin the RPG world and confirm the AC startup marker: `RPG_SUPPORT_TETHER_ASSETS cohort=AC beam=RPG_Healing texture=Void_Green persistent=true connectedProof=false`.
2. Equip Healing Beam, use an accepted staff/spellbook, injure a friendly player or NPC and hold the assigned native ability key. Confirm a continuous green textured stream remains visible for the entire channel without a periodic disappearance or refresh flash.
3. Keep caster and recipient stationary. Confirm the segment settles perfectly straight with both endpoints attached to their authoritative anchors.
4. Strafe, move forward/backward, jump/fall and have the recipient move independently. Confirm the interior trails opposite motion in both lateral and vertical axes, does not whip/oscillate excessively, and smoothly returns to straight after movement stops.
5. Test Arc, Fork and Chain separately and together. Confirm every branch remains attached, reacts only to its own endpoints and does not inherit another branch's curve or velocity.
6. Release the key, invalidate/remove the target, cross LOS/range grace, cancel by the existing damage rule and change world/disconnect. Confirm immediate beam cleanup and no later orphan reappearance.
7. Verify unchanged gameplay with traces: healing values, Mana upkeep, LOS grace, range, target polarity, continuation coefficients, support credit and channel terminal reason. Rejoin once to verify unrelated persistence remains intact.

Do not label continuity, attachment, movement response, branch independence, texture suitability or cleanup as connected-PASS until those observations are made in the Hytale client.

## Remaining presentation limitation / custom texture contract

`Void_Green.png` is the strongest validator-supported shipped healing-like trail found, but its artistic suitability is still an owner connected judgment. If a bespoke stream is desired, author a transparent, horizontally tileable RGBA trail texture under the Beam-supported `Common/Trails` root (recommended source size 256 x 96, matching shipped continuous trail atlases). Keep transparent/soft vertical edges, avoid baked world-space endpoints, and make the horizontal beam axis seamless. Package it as `Common/Trails/RPG_Healing.png` and change only `RPG_Healing.json` to `"TexturePath": "Trails/RPG_Healing.png"`. A particle texture such as `Particles/Textures/Rnd/NatureBeam.png` cannot be substituted directly because the installed native Beam validator rejects paths outside `Beams/` and `Trails/`.
