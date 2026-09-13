# R032-AP — Healing Beam visual/audio polish

**IMPLEMENTED: YES · PACKAGED: YES · DEPLOYED: YES · CONNECTED-VERIFIED: NO.**

Installed 2026-09-13 03:18:37 UTC (2026-09-12 local). This is the normal live
HyARPG JAR, not a diagnostic-only build. Live startup, visual appearance and
audibility still require connected acceptance.

## Scope and evidence

Started on RPG at `ebdaee9` (AO). The owner's new instructions confirm the AO
direct world-particle renderer now renders correctly. That connected observation
justifies retaining its delivery path; it does not establish AP's appearance.

No changes to targeting, healing, Mana upkeep, LOS/range, continuation coefficients,
support credit, channel authority, persistence, escrow, exact-once contracts,
Blizzard, cooldowns, equipment or native input. The existing elastic centerline
and arc-length interpolation classes are unchanged. No Beam geometry, carrier,
new diagnostic cohort, synthetic recipient or live data migration was introduced.

## Single animated helix

New `HealingHelix` is presentation state owned independently by each existing
`HealingWorldParticleFrame`. The body samples now form one strand around the
unchanged centerline:

- radius 0.175 m;
- pitch 1.35 m;
- angular rotation 0.875 turns/sec;
- forward body sampling flow 2 m/sec;
- three large pulses remain on the centerline at the existing 6 m/sec.

The source normal is transported from the previous frame; successive arc
tangents transport that normal using the minimal rotation. A projected axis is
used only to initialize a frame or recover numerical degeneracy, not independently
for each body sample. Exact tangent reversal preserves the previous normal.
This avoids the independent world-up flip at vertical headings. Each branch owns
its own previous tangent/normal, phase and existing elastic state.

The first and last samples remain exactly at the authoritative anchors. Radius
tapers within 0.5 m of the ends to join them precisely. Interior body samples
advance along the arc and wrap their sampling phase. Rotation time wraps every
eight seconds, which is an exact seven-turn repeat; phase does not grow unbounded.
The three pulse positions use the old arc progression without the radial offset.

Retained delivery bounds: 10 Hz eligible redraws, no catch-up bursts, at most
64 body samples plus three pulse positions per segment, one strand only, six
segments/root, existing 512-root and 2,048-sample global capacity limits,
same-world/native-view-distance culling and coalesced deferred submission.
Existing 0.18-second particle lifetimes are unchanged. Terminal cleanup stops
new packets immediately; the last finite body/pulse burst expires naturally.
These lifetimes still need connected confirmation, not a headless visual claim.

## Exact red derivatives and recipient effect

All new particle assets are RPG-owned under
`src/main/resources/Server/Particles/RPG/HealingRed/`. No stock texture or global
particle asset is overwritten.

| Production system | Source child assets | Retained textures |
| --- | --- | --- |
| RPG_Heal_Red_Blips | AO world Sparks | Basic/Ball3.png |
| RPG_Heal_Red_Pulse | AO world Glow + Plus | Circles/Circle_Glow.png; Shapes/Health_Regen_Plus.png |
| RPG_Heal_Red_Recipient | shipped Effect_Heal: Heal2 + Heal_Rays | Shapes/Health_Regen_Plus.png; Basic/Ray.png |
| RPG_Heal_Red_Staff | shipped Staff_Bronze: Air + Sparks | Original stock sprite references |

Only color animation/tint changes in the seven derivative spawners. System child
references change to the corresponding RPG IDs. Scale, sprite animation,
billboarding, opacity, velocity, lifespan, emission geometry, material and texture
references are preserved. Body/pulses use red/crimson and pale-red hot highlights;
recipient/staff tint is red. Original AM, AO and vanilla assets remain unchanged.
Legacy explicit diagnostic commands deliberately retain their stock appearance.

The real recipient's existing channel-owned native effect lease now references
the red derivative of **Effect_Heal**, replacing Effect_Health_Pack. It contains
Heal2 and Heal_Rays, not the travelling beam pulse system. The 0.3-second renewable
cosmetic effect remains attached to the real recipient; it does not heal or modify
stats itself. It is applied based on active tether membership, never on
`actualHealing > 0`, so full-Health recipients and active Arc/Fork/Chain recipients
qualify. Existing recipient replacement, last-root ownership and explicit removal
remain in place. Staff node wrappers are still channel-only, with the same
renewal/removal behavior and node selection, now referencing a red local derivative.

The native real-recipient route has not been replaced by world packets. If the
connected AP test shows this specific effect fails to appear, that evidence
would justify the already specified finite world-particle fallback. Local asset
resolution alone does not prove attachment visibility.

## Root-owned audio

Inspected the installed 0.7.0-pre.2 native asset
`SFX_Deployable_Totem_Heal_Effect_Local`. Its layer is looped, using the three shipped
Totem_Heal_Loop_Stereo files. The shipped Healing_Totem_Heal effect demonstrates
`ApplicationEffects.LocalSoundEventId`; that gameplay effect was NOT reused
because it also changes Health.

New `HealingChannelAudio` uses eight cosmetic-only RPG EntityEffect assets,
each with Infinite=true and only the requested LocalSoundEventId. It sends a
native EntityEffectsUpdate Add once to the casting player's entity, and a matching
Remove for the same effect index/network entity on the existing root cleanup path.
Repeated presentation calls do not restart/replay audio. Branches do not allocate
additional loops. Distinct effect IDs isolate simultaneous roots on one caster;
capacity is bounded to eight per caster and 512 roots globally.

These are **transient client-side effect packets**, not additions to the server's
saved EffectController. This avoids persisting an infinite cosmetic effect into
player data. Stop retains the original packet handler/network ID, so cleanup does
not need to resolve a now-invalid actor again. World/logout removal also removes
the corresponding client entity state. Failed starts best-effort remove their own
effect and report bounded HEAL_AUDIO_FAILED telemetry; they cannot change gameplay.

The native audit verifies 100 starts for one root produce one Add, simultaneous
roots have distinct effect IDs, capacity rejects excess allocation, and cleanup
produces exactly matching Add/Remove counts with no repeat stop. Sound loop
metadata and cosmetic-only effect contents are loaded through the native asset
path. **The audit does not prove the client audibly starts/stops the loop.**
The sound is local to the casting player; this pass does not add an observer-
broadcast positional audio system.

## Validation and provenance

Focused polish/regression tests passed before the full run. Eight new AP tests
cover color-only contracts, stock recipient/staff preservation, helix radius,
flow/rotation, centerline pulse speed, transported frames, audio ownership and
full-Health-independent effect wiring.

Existing AO settling tests now inspect the centerline pulses rather than the
deliberately offset body samples. Existing appearance expectations assert the
new specific red IDs rather than weakening cosmetic invariants.

Exactly one full retained run was executed: RPG, native control and CanvasUI.
It found one stale test in Stage13HealingProbeAITest: the historical green
diagnostic wrapper was assumed to match production except duration. AP intentionally
recolors production and replaces the recipient system. The test now explicitly
asserts both original diagnostic IDs and new production IDs, then compares all
other wrapper fields exactly. The six-test AI class was rerun and passed.
**No production code changed after the full run.**

Final combined retained results: **2,292 tests, zero remaining failures/errors/skips**
(2,215 RPG, 56 native-control, 21 CanvasUI). This is NOT a claim that the first full
run exited successfully: it exited 1. Its original log/XML remain under
`evidence/stage-13/cohort-ap/full-validation.txt` and
`iterations/full-rpg-results/`. The corrected class has a separate focused log.
Final validation XML contains the original full-run classes plus that corrected
class's six passing results; package receipt records this provenance explicitly.

The final exact-JAR isolated three-mod smoke exited 0 and passed retained native
gates plus AP particle/color/audio checks. Initial native smoke is retained under
`iterations/native-contract/`. No disposable Direct Connect testing was requested
of the owner, and no connected success is inferred from this isolated smoke.

The strict archive diff permits only presentation/assets/audit/revision changes.
HytaleSkillExecutionSystem source is unchanged; its anonymous Port class inlines
the new presentation asset strings and revision. Packaging compares its complete
javap disassembly after normalizing only those constants, proving no other
execution bytecode change. No AO entries were removed. Archive members are
hash-verified and binary rollback AO → AP → AO passed.

## Deployment and rollback

Installed:
`C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`

Built, archived and installed SHA-256, identical:
`E11720D6AC54BFCD7FD294308E908E124967EFAE39B4E935C016AD2AA24CD285`

Three-mod archive:
`evidence/stage-13/cohort-ap/HyARPG-R032-AP-three-mods.zip`

Archive SHA-256:
`5843567BE502EEB1908D9E8906169B27A086BC6DA19F020C364134F47A46C314`

Stopped-save backup:
`evidence/stage-13/cohort-ap/before/save/20260913T031833Z/RPG`
— 539 files, 311,240,211 bytes, every hash verified. Only the live HyARPG.jar
changed; all NPC/player data, traces, CanvasUI, HytaleDevLib and ImmersiveNPCs files
were verified unchanged. Internal mod identity remains
InigmasGames:HytaleRPGPhase00Audit; distribution name is HyARPG.jar.

The previous AO JAR is retained beside the backup in
`retired-live-name/HyARPG.jar`, SHA-256
`0586FB98DA3349584632FAF062C07644371C51C29E51B00EFA9E7E7E14457B36`.
For rollback, fully close Hytale and replace only the JAR with that copy.
Preserve any newer player progress; restoring the whole backup is not necessary
for this presentation-only change.

## Normal connected checklist

1. Rejoin the normal RPG world and confirm the top-right badge is **R032-AP**.
2. Use your equipped Healing Beam normally with a staff and enough Mana at a valid
   ally, including one at full Health. Expect one red corkscrew body, three larger
   red healing crosses travelling through its center, red Effect_Heal on the ally
   and the continuous healing sound. No probe/particle commands are required.
3. Strafe, change elevation, reverse direction and stop. The old elastic centerline
   should remain attached while the body rotates/flows and the crosses travel
   caster → recipient. Check sustained continuity and no green skill effects.
4. Test existing Arc/Fork/Chain branches if equipped: each segment has independent
   motion and recipient effects; audio remains one loop for the root.
5. Release, reacquire/change target, move out of range and let Mana run out. Check
   recipient/staff effects and sound stop with the channel, no orphan effects,
   and body/pulses disappear after the finite ~0.18-second tail. Check logout/rejoin
   and death/world-change termination when practical.

Remaining connected limitations: AP live startup, red appearance/scale, perceptual
helix continuity, real-recipient attachment visibility, and audible loop start/
stop are **unverified** until observed in Hytale. Older Stage 13 gates are not
promoted by this presentation pass.
