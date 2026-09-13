# R032-AO — production Healing Beam world-particle renderer

**IMPLEMENTED: YES · PACKAGED: YES · DEPLOYED: YES · CONNECTED-VERIFIED: NO.**
Installed 2026-09-13 02:32:01 UTC (2026-09-12 local). Live startup remains pending.

## Decision and connected evidence

Starting branch/HEAD: RPG / `ffe57ce` (deployed AN). The owner confirmed:
direct `RPG_Heal_Path_Blips` and `RPG_Heal_Path_Pulse` world-particle commands
render correctly; AM empty-model attachments do not; AN's visible mannequin
renders and cleans up, but its particle attachment still does not render.
The attached instructions explicitly authorize the direct packet fallback now.
AN's screenshot proves model visibility, not attached-particle visibility.

AO is a **production renderer replacement**, not another diagnostic-only cohort.
It uses the `SpawnParticleSystem` constructor and native packet write path already
used by the connected WORLD control. There are no new required probe commands.
No claim of connected AO rendering is inferred from tests or native smoke.

## Renderer changes

`SplineHealingParticleVisuals` now emits world-space particle packets. It creates
no ModelComponent, BeamComponent, mannequin, empty-model carrier, or persistent
visual entity. The AN visible model JSON is removed from the packaged build.
Legacy explicit diagnostic controls remain isolated from normal skill execution.

Every logical segment retains its own recipient identity and
`HealingWorldParticleFrame`: existing `ElasticBeamTether`, independent pulse arc
positions, next render time, and a reusable 67-position buffer. The existing
`HealingParticlePath` interpolation/arc-length algorithm is unchanged. A small
bounded 49-point interpolation table is created only on an eligible render pass;
packet buffers are reused and shared among eligible viewers for that pass.

Bounds and behavior:

- At most one render pass every 0.1 seconds, with no catch-up loop after a stall.
- Body spacing starts at 0.325 metres. At most 64 samples cover the **whole** arc,
  including exact source and recipient endpoints; long curves are not truncated.
- Three pulse positions advance at 6 metres/second using elapsed render time and
  wrap along the same arc. Each branch has independent flow and elastic history.
- Recipient replacement resets that branch's visual history while inheriting its
  cadence deadline, preventing replacement from bypassing the 10 Hz limit.
- Maximum six segments per channel, 512 roots/pending roots, and 2,048 active
  sample slots globally (the former entity-anchor budget). A segment emits at
  most 67 packets per eligible viewer/pass; six segments at most 402. Packet
  delivery fanout scales only with the relevant viewers, not every world/player.
- Pending presentations still coalesce at the existing command-buffer boundary.
  Removal immediately invalidates pending jobs and discards root/segment state.
  There is no persistent entity or broad particle-cancel packet to clean up.

The packet sender iterates the current world's native PlayerRefs, requires a
valid entity reference belonging to that exact Store, and reads its native
TransformComponent and Player view radius. Each sample is distance-filtered in
3D against the smaller of the 30-metre particle cull distance and native view
radius (chunks converted to blocks). Missing/other-world viewers are skipped.
No Universe-wide broadcast is used. Zero-viewer passes send zero packets.

## Packet-oriented derivative assets

New assets are under `Server/Particles/RPG/HealingWorld/`. AM assets are retained
byte-for-byte; these are separate packet derivatives:

| System | Child spawners | Existing textures |
| --- | --- | --- |
| RPG_Heal_World_Blips | RPG_Heal_World_Sparks | Basic/Ball3.png |
| RPG_Heal_World_Pulse | RPG_Heal_World_Glow + RPG_Heal_World_Plus | Circles/Circle_Glow.png + Shapes/Health_Regen_Plus.png |

The complete `Particle` appearance object, colors, sprite animation, billboard
mode, scale, render mode, texture references, and filtering match AM exactly.
Only emission/lifetime controls differ: immediate burst, TotalParticles min/max
1, concurrency 1, zero wave delay, and both emitter and particle lifespan 0.18 s.
Velocity and emission offsets remain zero. System lifespan and packet maxDuration
are also 0.18 s. The stationary world emitter has no endpoint-following transform;
sample placement is wholly controlled by the renderer.

After termination, no new packets are submitted. The last one-shot samples are
designed to disappear naturally within 0.18 s. Connected rendering must still
confirm the client honors these lifetimes and that repeated samples look
continuous; local verification cannot certify the absence of visual blinking.

## Preserved authority and lifecycle

Healing Beam targeting, healing math, Mana upkeep, LOS grace, range, continuation
coefficients, support credit, and channel transitions are unchanged. The existing
staff cosmetic and recipient Effect_Health_Pack leases remain in the separate
`HealingParticleVisuals(true)` effects-only layer; that layer is unchanged.
Gameplay still owns release, exhaustion, invalid target, range, death, logout and
world-change termination. The renderer uses their existing remove/cancel hooks.
No Blizzard, resource, cooldown, native input/HUD or persistence behavior changes.

Revision markers/badge and existing diagnostic text advance to R032-AO. AM's
historical asset audit keeps its AM label because those retained assets are
unchanged; AO has separate world-asset and world-packet native audit records.

## Verification

Focused tests cover exact appearance preservation, immediate finite bursts,
paired pulse child assets, all-range endpoint sampling, full-arc cap behavior,
10 Hz/no-catch-up cadence, 6 m/s wrapping, independent elastic/flow state,
settling to a straight line, and 3D/native-view-distance culling. The old AN
control test retains its archived fixture checks while asserting its wrapper is
absent from current production resources; no prior test was deleted.

The isolated native audit resolves the derivatives through Hytale's actual asset
loader and constructs real SpawnParticleSystem packets. It checks deferred
submission, cadence, endpoints, six-branch bounds, recipient replacement,
cancellation before pending-frame consumption, and unchanged native entity count.
It is **not** a connected-client rendering or transport certificate. Existing
Mana replication, staff leases, projectile, resource/persistence and other retained
native gates are kept. The required full retained suite is run once after the
candidate is coherent, followed by final exact-JAR smoke/package validation.

Final receipts are in `evidence/stage-13/cohort-ao/`. Focused tests passed, followed
by an initial native contract smoke. **One** complete retained run then passed:
2,284 tests (2,207 RPG, 56 native-control, 21 CanvasUI), zero failures/errors/skips.
The final exact-JAR native three-mod smoke passed with exit code 0. The initial
native contract log is preserved separately under `iterations/native-contract/`.

Packaging verified all archive entry hashes, the strictly scoped JAR diff, the
sole removed AN model asset, and binary rollback **AN → AO → AN**. AM assets,
compiled interpolation/elastic code, gameplay, staff/recipient effect layer,
Blizzard and persistence entries were verified unchanged. Internal mod identity
remains `InigmasGames:HytaleRPGPhase00Audit`; the distribution name is HyARPG.jar.

Installed target:
`C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`

Built / archived / installed JAR SHA-256, identical:
`0586FB98DA3349584632FAF062C07644371C51C29E51B00EFA9E7E7E14457B36`

Three-mod archive `evidence/stage-13/cohort-ao/HyARPG-R032-AO-three-mods.zip` SHA-256:
`AB2F84EB38D0A0E5E86A0AF1B9D1A0C4A221C81833B661741EDDC5884AF7A0BD`

Stopped-save backup:
`evidence/stage-13/cohort-ao/before/save/20260913T023157Z/RPG`
(527 files, 307,357,400 bytes; all hashes verified). Every non-target live file
remains unchanged, including the NPC mod, CanvasUI, HytaleDevLib, NPC/player data,
and existing traces. The old AN JAR is retained in sibling
`retired-live-name/HyARPG.jar`, SHA-256
`83404F4B97F419092561FD606D4E81276EF8D4B0DF92951417B58D7FCF4CFC1A`.

Rollback requires Hytale fully closed. Preserve any newer test data and replace
only the JAR with the retained AN copy; AO makes no data migration. The full-save
backup is an additional recovery safeguard, not an instruction to overwrite
subsequent player progress.

## Short connected acceptance

1. Rejoin the normal RPG world; confirm **R032-AO**.
2. Cast Healing Beam normally at a valid ally with a supported staff and enough
   Mana. Expect a green/yellow blip stream plus three travelling healing-cross
   pulses, **no mannequin and no solid beam**.
3. Move yourself/the recipient, then release. Check the path follows both
   endpoints and disappears within roughly 0.2 s. Test linked branches if equipped.

No particle spawn or carrier diagnostic commands are requested. Long-channel
continuity, actual client lifetime, visual flow and multi-viewer behavior remain
connected acceptance items; no blanket PASS is declared from local checks.
