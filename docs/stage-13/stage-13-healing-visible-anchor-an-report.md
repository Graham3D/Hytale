# R032-AN — one visible Healing Beam particle carrier

**IMPLEMENTED: YES · PACKAGED: YES · DEPLOYED: YES · CONNECTED-VERIFIED: NO.**
Installed 2026-09-13 02:11:10 UTC (2026-09-12 local). Live startup is also pending.

## Scope and evidence boundary

Starting RPG HEAD: `70cc011bb7355a122ddbb8e8af415869c8c8166c` (R032-AM).
The owner reports that both `/particle spawn RPG_Heal_Path_Blips` and
`/particle spawn RPG_Heal_Path_Pulse` visibly render the intended effects in the
connected client. This is accepted as **world-particle asset visibility PASS**,
not as evidence that model-attached particles render. The owner reports that
AM's empty-model carrier path remains invisible. AN tests that specific boundary.

**This is the requested one-anchor live-test candidate, not a full-pool repair.**
No assertion of attached-particle connected visibility is made before the owner
observes AN. No disposable Direct Connect server or authentication is needed.

## Implementation and rationale

`SplineHealingParticleVisuals` replaces one existing body sample's model wrapper
with `RPG_Heal_Path_Visible_AN`. The first segment in the presentation frame is
the primary segment. All other body samples and every pulse retain their AM
empty-model wrappers. The chosen body sample starts at the middle index, keeps
that index while available, and clamps if the pool shrinks. Its position is
calculated by the **unchanged** spline/arc-length sampling code. It follows the
existing path when endpoints move; AN does not give stationary blips new travel
velocity. Pulse travel speed and retirement behavior are unchanged.

The new wrapper reuses the shipped, renderable mannequin already used by the
visible standalone control:

- Model: `NPC/MISC/Mannequin/Models/Model.blockymodel`.
- Texture: `NPC/MISC/Mannequin/Models/Model_Default.png`.
- Full native scale 1, deliberately visible for this test.
- One attachment: `RPG_Heal_Path_Blips`, Entity part, scale 1,
  `DetachedFromModel=false`, `ClearParticlesOnRemove=true`.

The existing `HealingParticleVisuals.spawnCarrier()` path is unchanged: native
ModelAsset resolution, ModelComponent, TransformComponent, NetworkId, UUID,
BoundingBox and NonSerialized. This is visual geometry, **not an NPC spawn**:
there is no NPC role, Health, collision/physics simulation or progression state
added. The existing tiny visual bounding box is retained. The single visible
model is intentionally intrusive; it must not be hidden or reduced before the
owner confirms both geometry and its green particle render together.

There is no per-frame reconstruction of the chosen model on a stable pool.
When a changing pool/primary designation changes which index owns the proof,
only the affected model roles are reconstructed inside the existing deferred
ECS mutation boundary. Anchor counts do not increase. Failed allocation remains
subject to the existing whole-visual-root cleanup; released roots invalidate
pending frames before removing entities. The optional existing observer can
identify the selected reference as `segment-id/visible-proof` without new
commands or normal per-tick telemetry.

## Explicitly unchanged

- All AM `.particlespawner` and `.particlesystem` bytes and the Blips/Pulse model wrappers.
- `HealingParticlePath`, `ElasticBeamTether`, spacing, sample limits and pulse speed.
- Production Healing Beam healing math, target rules, LOS/range grace, Mana upkeep,
  channel lifecycle, Arc/Fork/Chain coefficients and support credit.
- Blizzard, native ability projection/input, HUD ownership, resource/cooldown
  formulas, persistence, escrow, exact-once and trace storage behavior.
- No native Beam geometry or full-tether world-particle packet fallback is introduced.

Revision badge, startup marker and existing diagnostic messages identify
**R032-AN**. The unchanged AM asset audit intentionally still identifies its
AM asset contract; this historical audit label is not the runtime revision.

## Validation design

Focused tests cover the retained AM sprite/placement contract, revision marker,
and the new wrapper's actual shipped model/texture existence. An additional
regression compares AM particle files and empty wrappers byte-for-byte against
the deployed AM archive. Packaging checks separately prohibit changes to all
unrelated JAR entries, including compiled spline and gameplay classes.

The real native store audit exercises deferred creation while Store is processing,
one visible carrier across a six-segment frame, stable reference reuse, actual
Transform movement, shrinking pools, branch-recipient replacement, terminal
cleanup and same-buffer cancellation without resurrection. It verifies the native
model packet has the mannequin geometry/texture and exact AM particle attachment.
These checks prove server-side construction/ordering, **not client rendering**.

Validation and deployment receipts are in `evidence/stage-13/cohort-an/`.
One complete retained run passed: **2,276 tests** (2,199 RPG, 56 native-control,
21 CanvasUI), zero failures/errors/skips. Focused regressions passed beforehand.
The final exact-JAR three-mod smoke exited 0 and passed all retained gates,
including the new `RPG_HEAL_VISIBLE_ANCHOR_NATIVE` gate and retained AM particle,
Mana replication, staff lifecycle, projectile, support and persistence checks.
No assertions were relaxed and no existing test was deleted.

Archive entries/hashes and binary rollback **AM → AN → AM** passed. The JAR diff
contains only the carrier implementation, new visible wrapper, and revision or
observer metadata classes. AM assets, compiled spline, gameplay and persistence
entries are byte-identical. The public name remains `HyARPG.jar`; the internal
plugin identity remains unchanged to preserve mod-data ownership.

Deployment target:
`C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`

Built, archived and installed JAR SHA-256 (identical):
`83404F4B97F419092561FD606D4E81276EF8D4B0DF92951417B58D7FCF4CFC1A`

Three-mod archive `evidence/stage-13/cohort-an/HyARPG-R032-AN-three-mods.zip` SHA-256:
`59257CCD762A0D500056F8A2989E1821B756E4AA8353CF477DCA55508F3FE492`

Stopped-save backup:
`evidence/stage-13/cohort-an/before/save/20260913T021105Z/RPG`
(526 files, 305,962,181 bytes; every backup hash verified). The sibling
`retired-live-name/HyARPG.jar` retains the old AM build with SHA-256
`DA75E29BB572DDAE7639A9C8AF98A042332CDCDCD396B491C9D5C0A52C327237`.
Every non-target live file was verified unchanged. CanvasUI, HytaleDevLib,
ImmersiveNPCs, player/NPC/save data and existing traces were preserved.

Rollback: fully close Hytale, preserve any newer testing data, and restore only
the retired AM JAR to the same live filename. Do not restore the whole save
blindly over subsequent player progress. The full backup is a separate recovery
option; AN performs no data migration.

## Connected test — normal single-player RPG world

1. Start/rejoin the normal RPG world and confirm the top-right badge is **R032-AN**.
2. Equip Healing Beam normally and hold a supported staff. With enough Mana,
   channel it at a valid nearby ally, preferably 6–12 metres away in clear space.
3. Look for **one full-size floating mannequin on the beam path**, initially near
   its midpoint, and the **green blip particle attached at its anchor/feet**.
   The rest of the beam is intentionally unchanged and may remain invisible.
4. Strafe while holding the channel; then let the ally move if possible. Confirm
   both the mannequin and green particle follow the existing curved path together.
5. Release the skill: the mannequin and attached effect should disappear with
   normal channel cleanup. Repeat a cast to check no old mannequin remains.

No `/rpg-heal-probe` command is required. Normal costs and targeting still apply.
Report **model visible? particle visible? moves together? cleans up?** separately.
If the model is visible but its particle is not, attached rendering remains the
earliest failing boundary. If both are visible, the next separately authorized
step is a small nonintrusive renderable model using this same path, followed by
pool conversion only after that proof. AN does neither prematurely.
