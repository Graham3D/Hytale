# R032-AG — Healing particle selection, recipient effect, staff heads

Date: 2026-09-12. Branch: `RPG`. Starting pushed HEAD: `28c19ab` (R032-AF).

## Delivery status

- IMPLEMENTED: requested native particle selection, recipient attachment, 26 staff-head attachments; no gameplay changes.
- PACKAGED: `HyARPG.jar` and exact three-mod archive, hashes below.
- DEPLOYED: 2026-09-12 18:37:09 UTC to the actual pre-release RPG save.
- CONNECTED-VERIFIED: **NO**. Native isolated integration is not client rendering proof.
- PARTIAL REQUIREMENT: the directed stream's source is **not** attached to the animated staff-tip node. Staff-head sparkles are node-attached; the directed stream uses authoritative server spatial anchors. Do not conflate these two attachment paths.

## Request and evidence

The owner rejected the remaining native ribbon presentation and explicitly selected the shipped `Beam_Heal_Green2` particle system, with screenshots. This supersedes the prior visual preference for a textured continuous Beam. The old `RPG_Healing` texture and width no longer select the live Healing Beam appearance. The prior native Beam class remains only as a retained native-API regression fixture; it is not instantiated by the production execution adapter.

The pinned installed runtime is 0.7.0-pre.2, native revision `b41721d651ef241809e402f6c3371781b2ea5f84`:

- Server JAR SHA-256: `9CDC1E77DADFDAD0B1D849977CDAD5C2752207F538272FB3D76D4E915546B25E`.
- Assets ZIP SHA-256: `0E35DF2D4E4A803F7752CB300FA7C9FE7ACDF62B4311978DA95C7AFF7ABBC126`.

Inspection used the actual installed assets and `javap` signatures for `ModelParticle`, `ModelComponent`, `Model`, `SpawnModelParticles`, `CancelParticleSystems`, `ParticleAttractor`, `ParticleSystem`, `ParticleSpawnerGroup`, `EntityEffect`, `EffectControllerComponent`, `ActiveEntityEffect`, and native Beam types. No web-based version inference was substituted for this installed contract.

### Exact assets

- `Server/Particles/_Test/HealBeams/Beam_Heal_Green2.particlesystem` references `_Sparks`, `_Glow`, and `_Plus`. The similarly named standalone `Beam_Heal_Green2.particlespawner` is **not** one of those references.
- Textures: `Particles/Textures/Basic/Ball3.png`, `Particles/Textures/Circles/Circle_Glow.png`, and `Particles/Textures/Shapes/Health_Regen_Plus.png`.
- Recipient system uses the case-sensitive shipped ID **`Effect_Health_Pack`**, from `Server/Particles/Status_Effect/Heal/Effect_Health_Pack.particlesystem`.
- Staff system: `Server/Particles/Weapon/Staff/Staff_Bronze.particlesystem`.
- Native Bronze/Bone items already used `Staff_Bronze` on `Handle` with X=1. That was concrete evidence that staff item particles can follow model nodes, not a guessed skeleton API.

## Implementation

### 1. Persistent particle stream

`HealingParticleVisuals` now owns presentation. Each logical primary/Arc/Fork/Chain segment gets one unsaved, noncombat native model carrier using the shipped empty model `NPC/MISC/Empty.blockymodel`. Its sole model particle is `Beam_Heal_Green2`, at native scale 1 with `ClearParticlesOnRemove=true`. No BeamComponent ribbon is created. No row of short-lived world emitters, particle-density increase, or repeated reconstruction is used.

Carriers retain their Ref, ModelComponent and TransformComponent. Frames update position/rotation only. The emitter points from the segment's authoritative source anchor toward its destination using the existing tested native -Z-forward transform. Each segment has its own carrier. Native particle velocities, textures, colors, rates, and lifetimes are unchanged.

The prior ECS correction remains: mutation is queued through the current command buffer and consumed after Store processing ends. Jobs coalesce within the same store; stale cross-world jobs cannot execute a new store's frame. Release invalidates pending jobs, including same-buffer create/cancel. Roots are capped at 512 and logical segments at six. Carriers are nonserialized, contain no physics/gameplay interaction, and are removed on the existing channel termination/cancel callbacks. Failed presentation cannot refund a paid heal or alter the channel math.

### 2. Target-attached health effect

The presentation-only `TetherVisualSegment` value now carries the recipient's stable identity explicitly; the renderer does not infer it by parsing the display ID. `ConnectionRuntime` supplies that identity for primary and continuation segments without altering recipient selection, pulse cadence, healing coefficients, LOS, resource payment, or targeting policy.

`RPG_Healing_Recipient` is a cosmetic EntityEffect with only `ApplicationEffects.Particles`, selecting `Effect_Health_Pack` on `Entity` with clear-on-remove. It has no EntityStats, modifiers, damage, resistance, movement, invulnerability, or HUD icon. There is no missing-Health check. The primary effect is presented even when healing returns zero at full Health.

The renderer renews a 0.3-second native cosmetic lease on existing visual frames. A root tracks its recipient set; removing a stale branch/releasing a channel removes that effect only when no other live root in the same store still owns the recipient. The finite lease is a fallback if ownership is abandoned. Other buffs/effects are never cleared wholesale. The lease's connected visual smoothness remains a client QA item; native lifecycle success does not prove it cannot visibly restart on a particular client.

Existing gameplay only selects injured secondary recipients. That rule remains unchanged: AG does not create new full-health continuation recipients solely for decoration. Every recipient actually present in an active visual frame receives the cosmetic effect.

### 3. All shipped staff variants

26 item assets in the installed `Server/Item/Items/Weapon/Staff` directory are overridden with exactly one `Staff_Bronze` entry, bound to a unique audited node. Existing Bronze entries are replaced rather than duplicated; all other native particles are retained in order. All non-Particles JSON fields are byte-content-equivalent as parsed JSON to the pinned native source, including interactions, powers, costs, recipes, tags, animations, and models.

The mapping is recorded in `src/main/resources/rpg/presentation/staff-heads-ag.json`:

- 22 variants use their authored `Origin_Projectile` attachment marker.
- Crystal Ice uses unique `TopPommel`.
- Crystal Purple and Red use unique `Block5` (the named `Block4` nodes are duplicated, so were not selected).
- Doomed uses unique `Knob`.

Coverage includes Bronze, Mithril, Flame/Ice crystal staffs, wood/rotten/Kweebec, metal tiers, NPC-themed staffs, Bo wood/bamboo, and the staff-family broomstick. It does not override unrelated prototype tools or unrecognized third-party staff assets. Native startup verifies every resolved item's model and exact particle/node assignment. Tests compare all non-particle data against installed native JSON and require each selected node to exist exactly once. These full native item overrides are pinned to this asset revision and must be re-audited after a native asset update; they are not a future-version-independent patch format. Another mod overriding the same items is a potential pack-order conflict; startup reports mismatched head assignments explicitly.

### 4. What is not implemented or proven

`ModelParticle` supports an entity part and a model node. Its particle system has directions, rates, offsets, velocities and static attractors, but no target-entity endpoint. `SpawnModelParticles` provides no individual persistent instance update/removal handle; `CancelParticleSystems` cancels by system and region, which could erase another caster's particles. A native Beam has entity/node endpoints but accepts a beam texture rather than this particle system.

No audited path found here combines an exact client-animated staff-tip emitter with an independently targeted recipient endpoint. The server spatial anchor is **not** the animated item mesh pose. AG therefore does not pretend a hand/head/body offset is a staff bone. It implements real staff-node sparkles and a separately directed stream, and leaves exact animated staff-tip stream attachment unresolved.

The unchanged native particle system also has fixed velocities/lifetimes rather than a recipient-bound endpoint. Its visible travel may overshoot a nearby target or not span a distant target exactly. Distance adaptation and connected orientation are **unverified**, not certified by the vector test. The former scripted spring/Bezier strand is not applied to this native particle system; naturally moving particles follow the shipped particle behavior. No gameplay path depends on any of these visual limitations.

## Validation and failures retained

Focused implementation tests passed, including `Stage13PresentationAGTest`, prior presentation tests and retained support-tether tests. The new tests prove exact asset selection/no live ribbon allocation, cosmetic-only recipient fields, full-health primary presentation identities, continuation identity propagation, native forward-axis math, and native staff JSON/node coverage.

One complete retained invocation was run:

```powershell
.\gradlew.bat :test :nativeControlTest :canvas-ui:test :jar --rerun-tasks --continue --console=plain
```

Its first result had **seven failures**, all `Stage13IconUpdaterTest`: the icon script correctly refused because the separately running isolated smoke server matched its Hytale-process safety guard. This was an orchestration error, not a reason to relax the guard. The full command exited 1 and this is retained in `package-validation.json`. The initial XML remains under `evidence/stage-13/cohort-ag/validation/initial-full-run`.

After the smoke process exited, only the unchanged icon suite was rerun:

```powershell
.\gradlew.bat :test --tests '*Stage13IconUpdaterTest' --console=plain
```

All nine passed. The combined retained evidence has **2,167 RPG + 55 native controls + 21 CanvasUI = 2,243 passing tests**, zero remaining failures/skips. No test assertion, safety guard, gameplay expectation, persistence test, or performance threshold was weakened. The original failing XML and replacement successful icon XML are both retained. The complete suite was not repeatedly run.

The exact candidate also passed isolated three-mod startup and the actual native particle construction/lifecycle audit:

```text
RPG_HEAL_PARTICLE_NATIVE_INTEGRATION result=PASS asset=Beam_Heal_Green2
create=true persistentModel=true update=true remove=true sameBufferCancel=true
recipientAttached=true sharedRecipientCleanup=true connectedProof=false
RPG_STAFF_HEAD_ASSETS cohort=AG staffs=26 system=Staff_Bronze
attachment=MODEL_NODE result=PASS connectedProof=false
```

The native audit operates inside real Store processing, exercises queued production creation, verifies the same ModelComponent survives updates, removes carriers, cancels before creation, and tests two roots sharing one native recipient effect. All prior native projectile, Blizzard collision/lifecycle, damage-channel, progression, reward/persistence registration, native ability and three-mod smoke gates remain enabled. CustomUI validation: 34 documents. Trace archive verification fixtures are retained. Existing compiler deprecation/native-access warnings remain; no gate was relaxed for them.

After deployment the exact **installed JAR bytes** were copied into the isolated three-mod world and the startup/native audits passed again at 18:38:50 UTC. See `installed-byte-verification.json` and the final `server-smoke-summary.json`. The actual live save was not booted or modified by this check; it is not live connected proof.

## Packaging, installation and rollback

- RPG JAR SHA-256: **`724276A3F49817E497EFF99483B5C8447A592827FD9198464D8678C91E711D5D`**.
- Three-mod ZIP SHA-256: **`F71504085862C30514040BD92CCCFC99F73923DD74D1276D09FB051D42EC30F3`**.
- CanvasUI unchanged: `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6`.
- HytaleDevLib unchanged: `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230`.
- Artifacts: `evidence/stage-13/cohort-ag/artifacts/HyARPG.jar` and `evidence/stage-13/cohort-ag/HyARPG-R032-AG-three-mods.zip`.
- Installed: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`.
- Stopped-save backup: `evidence/stage-13/cohort-ag/before/save/20260912T183706Z/RPG` — **473 files / 287,458,725 bytes**, every hash verified.
- Prior AF JAR SHA-256: `1C485513F33E760EA3DE1AC8C52CD597D9CC54EBD2EF7B3C0C35C6922DA34134`, also retained in the adjacent `retired-live-name/HyARPG.jar`.

Installation changed only the one live RPG JAR. Save/mod-data, trace history, owner icons, CanvasUI, HytaleDevLib, and owner ImmersiveNPCs were preserved. Archive entries were individually hashed and exact three-mod count checked. A separate AF -> AG -> AF binary rollback rehearsal passed without using live save data. Internal manifest ID/data-directory name stays `InigmasGames:HytaleRPGPhase00Audit`; renaming the public JAR does not migrate or reset saves.

For rollback: close Hytale/server, preserve any QA logs/new save state, replace only live `HyARPG.jar` with the archived AF JAR and verify the AF hash. Do **not** restore the complete older save merely to roll back visuals; that would discard subsequent play. Use the full snapshot only for an explicitly intended save recovery.

## Minimal connected checklist

1. Restart Hytale, enter the RPG world, confirm top-right **R032-AG**. No live connected startup/appearance is claimed by packaging.
2. Equip Healing Beam through `/rpg skilltree`, hold its native assigned skill key while aiming at the existing friendly NPC. Test both injured and full-health targets. Healing behavior/upkeep should be unchanged; the green stream and target health-cross effect should be visible in both cases.
3. Hold and move for at least 15 seconds, then release. Check stream direction, continuity, removal and recipient residue. Repeat at short and long range and different elevations. Record overshoot/shortfall, wrong direction, blinking, or other deviations rather than accepting the local tests as visual proof.
4. Inspect staff-head sparkles with Bronze, Mithril, Flame and Ice staffs in first and third person. Node existence is proven, screen-space placement/visibility is not. Switching away must remove the item's effect. The directed stream is not yet expected to track the animated staff tip exactly.
5. Test Arc/Fork/Chain with injured friendly recipients; release, interrupt, break range/LOS, despawn the target, and rejoin. No persistent visual residue should remain. With two casters, releasing one must not remove the other's recipient effect.
6. Review `HEAL_PRESENTATION` in the existing `skill-trace.jsonl`: `HEAL_PARTICLE_STARTED`, one `HEAL_PARTICLE_UPDATED`, and `HEAL_PARTICLE_REMOVED`; failure paths retain bounded stage/class/message. NORMAL trace aggregation/rotation is unchanged. Do not run isolated audit commands on the live world.

Stop after this correction. No stage progression, performance claim, gameplay rebalance, native ability redesign, resource/HUD ownership change, or new persistence architecture is included.
