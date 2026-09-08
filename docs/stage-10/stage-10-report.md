# Stage 10 — summons, corpses, conversion and selective collision

Revision R029 / version 0.0.22. Stage status: **IMPLEMENTATION_IN_PROGRESS**.
This report is incremental; it is not a Stage 10 completion or connected PASS.

## Authority and starting state

Owner requested continuation from the beginning of Stage 10, GitHub-local storage,
retention of lost files, and a detailed final handoff. The original continuous
Stages 06–13 authorization remains in effect. Master v1.2 Markdown SHA-256:
`750483855846FF6DB2564B4D6D626C2A12F1AC6EBBBBF12232B2C3ECCF667010`.
Baseline: Stage 09 `837ed80`; GitHub recovery commit `cda4d99`. Player schema 5,
compiled-plan schema 6. Neither schema changes in cohort A.

All new work is under `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale`.
See `docs/workspace-recovery-20260908.md` for destination reconciliation and the
hash inventory of the 18 unsynced class files plus the other supplied art.
The old Google Drive checkout is not a write destination.

Pinned installed server: 0.7.0-pre.1.

- Server SHA-256: `EC57E9BD6E2CA3CB16CC5883D42B04A0C64D382DEE532C5BC1CFCF68421E1EE3`.
- Assets SHA-256: `46F6AA12DECF4F900FCFDF28ECC67568403C37A3AD0A03A4E4CF7653A324AE39`.
- Audit capture: `evidence/stage-10/api/manifest.json`, adjacent installed bytecode
  and selected shipped NPC role definitions.

## Cohort A — Wolf Summon pilot

### Native audit and architecture decision

The installed `NPCPlugin.spawnNPCWithSpaceValidation` validates the role's motion
controller, placement, breathing and collisions before calling `spawnEntity`.
It accepts a native initialization callback. The helper uses `Store.addEntity`,
so calling it inside an entity-iteration loop would violate the ECS boundary.
The adapter queues the bounded spawn operation through the current native
`CommandBuffer.run`, preserving the world thread and rechecking owner/placement.
No scheduled worker thread mutates the entity store.

The shipped `Template_Summoned_Ally` is not an effect-free ownership primitive:
it includes automatic combat, a default Physical-damage interaction and a
nearest-player flock-join path. Blindly using it would risk unrelated ownership
and damage outside the RPG kernel. Instead, a small `Generic` project role uses
the verified native Wolf_Black model and native Walk/Seek movement, with its
target assigned by the RPG adapter. It has no attack actions, interaction roots,
flock creation, inventory, drop list, memory unlock, or source NPC scripts.

`NonSerialized`, obtained through `EntityStore.REGISTRY`, marks the temporary
native actor; the RPG ownership projection is also unsaved. Native
`Role.setDeathItemsDropped()` is used defensively. The installed NPC drop system
returns early for that flag; the authored role independently has no drop list and
disables pickup drops. Natural NPC roles and native loot tables are not modified.

### Implemented behavior and reasons

One Spellbook summon costs 20 Mana and starts a 25-second RPG cooldown through
the existing SkillExecutionService. The profile has 6-metre placement, 20-second
life, 60% caster maximum Health, 0.55 Magic-Power coefficient, one attack per
second and a 24-metre leash. The native trigger is still Cost=0, CostType=None,
Cooldown=0; it uses the existing R024 bridge. No native input fix is claimed.

The shared registry counts pending and live native actors together against
8/owner and 256/global limits. Admission and identity mutations are synchronized
across worlds. A root cannot reserve a second batch while active. Stable owner,
world, native entity, root, skill instance and correlation identities remain
separate. The original immutable combat snapshot is retained, and identity
mismatch is rejected. No native `Ref` is retained as domain ownership.

The native adapter queries at most every 100 ms, with the retained 4096-scan,
256-candidate and 64-accepted-target limits. Targeting uses NPC collision bounds,
hostility to the owner and LOS; damage revalidates the existing protected-target
policy. Players and RPG-owned actors are not attack targets. Native motion heads
toward the selected target, or toward the owner when none is valid. A summon
outside its leash is removed instead of teleported through unsafe geometry.

Attacks claim their next interval before damage dispatch. Reentrant calls cannot
claim that interval twice, and a delayed server tick does not catch up a burst
of missed attacks. The attack passes through the existing damage calculation,
native HytaleDamageAdapter and normal native lifecycle. Canonical critical chance
is retained; minion attacks do not create another generic proc/repeat controller.
Trace events include SUMMON_SPAWN_REQUEST, SUMMON_SPAWNED, SUMMON_ATTACK,
SUMMON_REJECTED and SUMMON_TERMINATED with the original cast identities.

There are no native damage interactions in the role. An additional native filter
cancels accidental native outgoing damage from marked summons and rejects
nonhostile entity-source damage against them. This is defense in depth, not a
second combat engine. Environmental damage remains under native rules.

Spawn failure rolls back only the exact actors created by this batch. Cancelling
an owner invalidates queued reservations; an existing projection without a lease
is removed on its next native tick. Native removal releases its registry slot.
Death, expiry, owner loss and leash violation terminate the actor. A native
placement rejection after the paid commit creates no actor and does not refund
the paid activation; it emits its actual native boundary code. This policy avoids
pretending that the private native placement-preflight helper is public.

Skill Delay captures the point at commit, pays once and revalidates it at release;
the 20-second summon life starts at release. Delayed cancellation retains the
paid cooldown/cost. Echo is rejected by the existing component compatibility
policy, and the graph transaction rolls back. No native HUD, resource formula,
projectile executor, Stage 04/05 skill profile or XP artwork was changed.

### Validation and corrections during development

The first compile caught an incorrect suffix on the native behavior-tick class;
the adapter now uses the installed `RoleSystems.BehaviourTickSystem`. This was
corrected before any smoke or deployment. Three retained profile-inventory tests
initially counted the new summon as an old cohort. Their filters now exclude the
explicit summon profile while preserving their old expected counts and all
per-skill assertions; no regression was waived.

The retained regression gate includes 535 tests, with no failures/skips, including
19 new summon tests. Coverage includes payment/cooldown, delayed release and
cancellation, duplicate roots, duplicate entity ownership, expiry, pending/live
caps, owner isolation, nonfinite profile/clock rejection, asset safety and native
trigger neutrality. The packaged CustomUI documents are validated unchanged.
The isolated boot uses exactly the existing CanvasUI, HytaleDevLib and new RPG
JAR. It must reach normal network boot, resolve the new NPC role, resolve the
retained native bridge/control assets, and shut down normally with exit code 0.

Machine evidence and exact JAR/rollback hashes are in
`evidence/stage-10/cohort-a/verification.json`. The archived rollback is Stage 09
0.0.21, SHA-256 `50ED7ACA31BFD78125B1762D9A80FDC463C747A84C3C09DBEFC04BE933B8FE3A`.
No live deployment or save migration occurs. Cohort A does not implement Revive,
corpse claims, conversion, Bone Cage, the remaining summon skills or passives.

### Connected evidence still required

Local tests prove contracts and the isolated smoke proves loading/asset structure.
Neither proves a client can cast Wolf Summon, see a wolf, observe native walking,
receive the correct attack animation, or see an actual authoritative damage
cycle. Those gates remain **UNVERIFIED**. The named summoned-wolf model is a
functional presentation candidate, not an artist-approved or connected-reviewed
effect. Ownership readability, ground/nav safety on slopes/stairs, collision
avoidance, attack cadence/Health loss, no loot/memory rewards, eight-summon cap,
and death/logout/world-unload cleanup all require connected recordings.

Next authorized cohort: Revive Fallen, reusing this actor boundary and adding
exclusive death-anchor claims. It must not copy source combat/reward scripts or
turn unclassified actors into eligible corpse rewards.
