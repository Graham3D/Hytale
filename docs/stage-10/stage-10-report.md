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

## Cohort B — Revive Fallen pilot

### Earliest native boundary and constrained source coverage

The adapter observes actual `DeathComponent` addition on native NPCs, snapshots
their authoritative maximum Health and position, and accepts only explicitly
classified roles. It does not manufacture corpses from an RPG timer. Native body
removal, death-component removal, and owner cancellation invalidate uncommitted
claims. The body must still exist, be dead, be hostile to the caster, and pass the
existing protected-target/LOS policy when selected and at release.

The first audited source is shipped `Wolf_Black`: its modified Health is read
from the native stat map, not assumed from the template's older default; its
authored source power is 27. `Template_Predator` has attack pause range [2,3],
pre-delay .3 and post-delay .4, rather than one universal attack cadence.
The source profile deliberately uses a conservative authored 3-second interval;
this is not a measurement of native attacks. Selected source/template bytecode
and asset evidence are in `evidence/stage-10/cohort-b/api`.

That template also gives a 1.5-second death animation. Hytale's deferred corpse
removal follows the native death lifecycle. This pilot preserves that lifetime
and original loot handling; it does not extend bodies or duplicate native loot.
Consequently, the usable native corpse window is short and still needs connected
testing. No generic encounter/story/boss classifier is inferred from role names.
Unclassified roles reject with `NO_ELIGIBLE_CLASSIFIED_NATIVE_CORPSE` before
payment. The domain rules admit common/specialist/elite and reject miniboss,
boss, player, owned, protected and story sources; **elite native-source coverage
is not proven by the fixture that tests those rules**. Broader audited source
classification remains a coverage item, not an implied working feature.

### Transaction, persistence and stat calculation

Revive costs 30 Mana, starts a 30-second RPG cooldown, requires a Spellbook and a
corpse within 15 metres, and creates one 25-second controlled projection. Its
compiled target stores the corpse UUID/world/death anchor. Source archetype
selects an audited RPG-owned role; source inventory, attack scripts, loot and
encounter identity are never copied. The existing catalog's undefined INNATE
source was corrected to MAGIC_WEAPON so its canonical resolved-Magic-Power cap
has the same authoritative input as the runtime profile.

The stat contract is `min(.60 sourceMaxHealth, 2 casterMaxHealth)` Health,
`min(.60 sourceBasePower, .80 resolvedMagicPower)` hit power, and
`max(sourceInterval, 1 second)` cadence. The clamped power becomes a coefficient
against the captured resolved Magic Power, avoiding a second attribute multiplier.
The same immutable cast/root/instance/correlation snapshot reaches summon attacks.
The profile uses Necrotic damage; ordinary Wolf Summon retains Nature.

Claims are exclusive across owners and use exact runtime claim identity. Duplicate
claim calls for the same owner/root return the same claim; consumption succeeds
once. A bounded 1024-entry runtime ledger follows native corpse lifetime. Before
spawn dispatch, an immutable receipt is created with CREATE_NEW and flushed under
the plugin data directory `corpse-consumption/<world>/<corpse>.consumed`. This
avoids replay after process restart or native corpse reload. Even an empty/torn
receipt means consumed. An IO failure rejects the operation conservatively; an
ambiguous claim cannot become reusable. Native death observation catches receipt
IO failure and rejects that corpse instead of throwing into native death handling.

Receipts use filesystem lookups without a growing in-memory index or a full
directory scan on each cast. They intentionally accumulate on disk; they must be
backed up/restored with world and player state and must not be purged merely
because a body disappeared. Hard power-loss/filesystem guarantees and live IO
latency are not established by these tests. A crash after consumption but before
spawn may lose that activation, but cannot create a second summon from the body.
The system does not claim an atomic transaction with Hytale's world-save engine.

Native spawn remains deferred through the world command buffer and validates
space. If the corpse body prevents placement, the actual native rejection is
reported; no collision bypass was invented to make the pilot appear complete.
Spawn failure rolls back only actors created by that batch. An executor failure
after a committed summon retains both its paid resource and cooldown, like other
already-dispatched persistent families. The regression exposed the old cooldown
clear path; it now excludes summon dispatch as well as excluding its refund.
No existing Stage04/05 executor, resource formula, HUD or projectile is changed.

### Local checks, rollback and connected gate

The full retained gate is 555 passing tests, including 20 new corpse/revive tests,
with no failures/skips. They cover contention, identity forgery, duplicate roots,
world mismatch, owner/body cleanup, rank/ownership rules, stat caps, source
allowlisting, capacity, durable reopening, torn receipts, two repository instances,
IO failure, real cast payment/cooldown and post-consumption error behavior.
The initial focused run had one failed assertion because a terminated paid cast
correctly reports `committed=true`; the corrected test asserts TERMINATED while
requiring payment/cooldown retention. It also exposed and fixed the cooldown clear.
The initial compile caught the wrong native NetworkId package, fixed from the
installed JAR before the gate. All failures were development failures, not waived
regressions.

There are 53 zero-cost/zero-cooldown native trigger assets, including Revive. The
packaged UI is unchanged and validated. The final JAR must pass the ordinary
three-mod loopback network boot/clean shutdown and be archived separately from A.
Exact results and hashes: `evidence/stage-10/cohort-b/verification.json`.
Rollback: cohort A .22, SHA-256
`BD61D4692312E3CC30E0B00AE418BF3086C5B19867224BE47B842DFFDB1F8562`.
Player schema remains 5 and compiled-plan schema 6. Keep the new receipt directory
even when rolling back to A, which cannot consume corpses. No live deployment.

**Connected gate remains UNVERIFIED.** Required observations include an actual
eligible death, acquisition during the native corpse window, exactly one claim,
native spawn acceptance, visible undead ownership/readability, measured Health
and attack cadence, no duplicate native rewards, duplicate/restart rejection,
and cleanup on death/logout/unload. The wolf model is an audited archetype
placeholder, not verified undead art/animation. Neither the in-memory fixture nor
server boot proves any of these connected behaviors. Stage 10 remains in progress.
