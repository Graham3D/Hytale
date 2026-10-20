# Stage 10 — summons, corpses, conversion and selective collision

Revision R029 / version 0.0.22. Local Stage10 scope is complete after cohorts A–G.
Status: **IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION**, with Bone Cage
**intentionally disabled by the master's selective-collision safety gate**.
This is not a connected Stage10 PASS or release approval. Earlier cohort sections
retain their historical implementation state; the closure below is current.

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

## Cohort C — native batches, Swarm and Minion Empowerment

Adds Summon Void Crawlers (3 actors, 28 Mana, 28-second cooldown, 6-metre placement,
18-second lifetime, 35% caster maximum Health and .40 Magic coefficient per actor)
and Brood Call (4 actors, 26 Mana, 28-second cooldown, 8-metre placement,
16-second lifetime, 25% caster maximum Health and .35 INNATE base-20 INT-scaled
power). Brood has no weapon requirement: its previous MAGIC_WEAPON catalog token
was inconsistent with the normative closure and now resolves INNATE/20 in both
catalog and runtime. Both retain one attack/second and the 24-metre owner leash.

The installed `Crawler_Void` and `Scarak_Louse` models, textures and animation
sets are audited in `evidence/stage-10/cohort-c/api`. New Generic roles reference
those shipped presentations and reuse the effect-free Walk/Seek role structure.
No broodmother combat, native attack roots, drops or encounter scripts are copied.
Labels identify them as RPG summons. Native animation sets existing on disk are
not evidence that the connected client played the intended animation. Portal,
fade and readable ownership/art acceptance still need connected presentation QA.

The formation changed from an expanding one-sided line to a deterministic ring
centered on the committed point. Pair spacing is at least 1.05 metres, and the
ring stays within 1.4 metres of its centre for counts 2–8. Each offset is grounded
with the existing native block collision query and checked against caster range
and LOS before any actor is spawned. Native role/space validation still runs per
actor; a later failure removes the exact earlier actors in that batch. These are
local placement contracts, not proof of safe connected navigation on all terrain.

Swarm adds one actor and multiplies every actor's Health/power by .75. Empowerment
multiplies Health/power by 1.30 and lifetime by .75, bounded below by the one-second
runtime minimum (none of these authored profiles reaches that floor). Combined
Health/power factor is .975, not an additive shortcut. Revive's base clamps run
before Empowerment. The caster's own Health/power/cost/cooldown is not modified.
Admission uses the modified whole-batch count; neither passive bypasses 8/owner
or 256/global limits. Their typed component operators enter the deterministic
plan hash; compiled-plan schema advances to 7, player schema stays 5.

The catalog incorrectly granted TEMPORARY_COMBAT_SUMMON to the explicitly
noncombat Simulacrum. A new compatibility test exposed this; that token is now
removed. Swarm additionally rejects DECOY, CONVERSION and corpse consumers in the
shared compatibility authority. This does not remove Simulacrum's legitimate
TEMPORARY_SUMMON capability. Conversion remains ineligible for Empowerment.
There are still exactly 87 skills and 66 passives.

The full retained gate is 576 tests, including 21 new tests for this cohort.
Existing role/ability asset tests now cover all three safe roles and all four
Stage10 neutral native triggers. New coverage includes both skills' exact
counts/costs/stats/lifetimes, no-weapon INNATE resolution, modified counts, passive
composition/order, Revive clamps before Empowerment, non-stacking duplicate
operator IDs, compatibility rollback, compact/spaced formation, actor-local
attack claims, paid delayed release and pending whole-batch cap rejection.

Initial failures distinguished an actual decoy-tag bug from two fixture issues:
the shared test harness advances execution time but its kernel uses real cooldown
time, and it reused an identical input root for separate simulated presses.
Capacity/rollback fixtures now explicitly clear only their test cooldown and use
fresh input identities; they do not claim to prove production cooldown expiry.
The duplicate-root production guard remains intact and retains its dedicated test.
Paid-but-terminated results are not accepted as successful actor creation in the
two-batch test: it requires COMMITTED status and exactly eight registry entries.
No tests were skipped or replaced with weaker expected outcomes.

The packaged build contains 55 native zero-cost triggers and must pass the normal
three-mod network boot/clean stop, including all three spawnable RPG role assets.
Exact JAR/test/smoke hashes: `evidence/stage-10/cohort-c/verification.json`.
Rollback is cohort B .22 SHA-256
`6D8CD1110E3572CFFF43E960CD2D115BC21A2D5A2A0F2D0E24B3E3BE5B186DA8`.
Earlier archives remain unchanged. No live deployment, HUD/resource formula,
Stage04/05 executor or XP artwork change. Connected casting, native batch spawn,
animation/damage, encounter ownership and cleanup remain **UNVERIFIED**.

## Cohort D — consuming actions and Death Pact

### Consume Minion

Adds a typed consuming-action profile, separate from the native spawn profile.
Consume Minion requires a Spellbook, costs 15 Mana, starts a 12-second cooldown,
and selects one live, unexpired, owned combat summon within 12 metres and LOS.
The registry publishes the benefit and removes the lease under the same lock.
If benefit publication fails, the lease remains available; the already-paid
activation is not refunded. After success the exact native actor is removed
through the world command buffer, without native death, corpse or loot creation.
Another owner, world or duplicate request cannot publish a second benefit.

The existing bounded finite-support store carries a self-only eight-second
effect: +20% Increased RPG skill damage and a shield equal to 10% captured caster
maximum Health. This resolves the inherited 18-versus-8-second contradiction in
favor of the normative closure. The shield enters the existing post-filter,
pre-native-Apply absorption path, not a second Health store. Its depletion leaves
the damage bonus alive until eight seconds; it does not accidentally erase both
benefits. The bonus is not added to native basic-attack damage. Reapplication
replaces the named effect rather than stacking duplicate shields. Owner/world
cleanup uses the retained finite-support teardown.

The catalog also wrongly labelled Consume Minion as a created temporary combat
summon. Those two creation capabilities are removed; Swarm, Empowerment and Death
Pact cannot target the sacrifice action. This does not change its SUMMON/CONSUME
family identity or any canonical passive count.

### Corpse Burst and paid-commit correction

Corpse Burst requires a Spellbook, costs 18 Mana, starts a six-second cooldown,
selects an eligible classified native corpse within 20 metres, and applies a
4-metre, 1.60 Magic-Power Necrotic burst through the existing calculation and
HytaleDamageAdapter. Target acquisition remains bounded by 4096 scanned NPCs,
256 candidates and 64 accepted targets. It uses authoritative bounds, hostility,
LOS and protected-target checks; no damage is submitted if the accepted budget
overflows. Each accepted target is visited once. Expanded Radius modifies the
burst radius to five metres and retains its existing .90 magnitude factor.
Generic Echo/Retaliation/Critical Trigger/Kill Trigger cannot automate corpse or
owned-summon consumption in the shared compatibility authority.

The delayed-cast review found that the B pilot consumed at release, which does
not meet the normative paid-commit requirement for a corpse consumer. Both Revive
and Corpse Burst now run a family-specific commit hook after the actual resource
and cooldown commit, but before any delayed release is armed. The hook reserves
and durably consumes the native corpse, then retains a bounded, one-use release
permit keyed by owner/instance and checked against root/world/corpse identity.
The permit stores the original death anchor and source stats. A later native body
removal does not retarget or invalidate that already-consumed source. This
supersedes B's earlier release-time requirement that the native body still exist.

The permit is taken before native damage or spawn dispatch. Cancellation drops
the pending permit, never the durable consumption receipt. A process restart
does not re-arm the delayed effect and still rejects the consumed body. If commit
fails after payment, the cast terminates with a recorded earliest boundary and
retains the charge/cooldown; it cannot schedule damage or imply a refund restored
an uncertain corpse. Unconsumed claims are released when commit cannot reserve
capacity. Native callbacks explicitly abandon permits on release rejection;
owner cleanup clears all pending permits. The runtime permit limit is 1024.
No native corpse lifetime or world-save behavior is changed.

### Death Pact and offensive snapshot ownership

Death Pact is a typed summon-component operator. A native DeathComponent observer
claims termination before native corpse removal can race the ordinary summon
tick. The tick separately handles natural expiry. Both use the same atomic
terminal operation; only an active actor ending from natural expiry or confirmed
hostile EntitySource death may produce a burst. Environmental/unknown death,
owner loss, world removal, leash cleanup, pending spawn, voluntary sacrifice and
duplicate termination do not. Forced cleanup never becomes a damage event.

The allowed burst is queued on the same native world command buffer, retains the
observed death/expiry position, rechecks owner/world availability, and goes through
the same bounded native-damage burst adapter. It uses a three-metre radius and .60
caster-snapshotted offensive power in the parent summon's primary element, not
.60 times the minion's already-reduced attack coefficient. Its unique effect ID
is derived from the summon lease. It creates no actor/corpse and is marked
canProc=false, so it cannot create another generic repeat/proc controller. The
ordinary captured critical chance remains available to this direct damage
calculation; no additional critical-trigger execution is authorized by the burst.

The review also found that A–C summon attacks added *current* outgoing buffs at
each attack. Created summons now capture outgoing buff/debuff buckets at commit;
their attacks and Death Pact use that frozen offensive snapshot, with only
victim-side modifiers evaluated at the actual hit. This prevents a later caster
buff change from rewriting the captured summon offense and avoids applying
Potency twice. The delayed-release admission check now includes Swarm's modified
count, matching initial admission and final registry allocation.

Burst presentation uses the retained short-lived procedural geometry template;
it is not a claim that an artist-approved bone burst, sacrifice stream, shield
flash or native animation has been rendered. No gameplay is scheduled from VFX.

### Validation, failures and boundaries

Full retained gate: 610 tests, including 34 new D tests. New tests cover paid
consumption, shield absorption without premature bonus expiry, exact eight-second
duration, exclusion from native basic damage, wrong owner/world, expired actors,
publication failure, corpse cross-skill/restart deduplication, captured burst
coefficient, repeat exclusions, all terminal reasons, independent Swarm terminal
claims, frozen offensive modifiers, cleanup, radius composition, commit-before-
delay ordering, native-body removal after commit, abandoned permits, failed
commit, and no replay after repository recreation. The focused D tests passed;
the full suite retains all prior Stage01B/CanvasUI/Stage02–09 regressions.
Three historical profile-count tests exclude the new typed action component
while preserving their original 12/35-skill inventories and behavior assertions.

Compiled-plan schema is now 8; player schema remains 5. The packaged build has
57 neutral native ability triggers, six Stage10 skill profiles and three safe
native summon roles. It must pass the unchanged normal three-mod network boot
and clean shutdown. Exact proof inventory and JAR hash:
`evidence/stage-10/cohort-d/verification.json`. Rollback is C .22, SHA-256
`2D3D5EDF3020FC61E5A892A3ACA5D9251D601DB4E13B1F64C3F38ECB62782D6F`.
Retain corpse receipts when rolling back. No live deployment or HUD change.

Connected gates remain **UNVERIFIED**: actual native death callback order, enemy
kill versus cleanup, authoritative burst Health loss/crit, visible shield/bonus
behavior, native minion disappearance without loot, delayed corpse timing and
rejoin/restart observations. The domain's once-only terminal tests do not prove
the native event arrived. Unclassified corpse roles still reject; the source
coverage limit remains explicit. Simulacrum, Dominate and Bone Cage remain next.

## Cohort E — Simulacrum and native control audit

This cohort deliberately contains only Simulacrum. Dominate remains the next
independent native integration boundary, rather than being combined with a
decoy prototype and described as completed conversion.

### Native evidence and scope

`tools/Capture-Stage10ControlAudit.ps1` archives installed bytecode for
WorldSupport, MarkedEntitySupport, attitude providers, Blackboard, native role
changes, flock membership and EntitySavingSystem, plus the shipped Mannequin
model and Empty_Role. Sources are under `evidence/stage-10/cohort-e/api`.

WorldSupport's direct attitude override writes a timed entry to an optionally
allocated memory map. Its public API exposes the current override, but not the
entry's remaining duration or an exact remove/restore operation. Using it as a
general reversible conversion would overwrite native encounter state. Changing
the whole NPC role would also replace execution support/AI state. Neither is
used for the decoy.

The audit found a supported alternative: native AttitudeView accepts a
prioritised IAttitudeProvider. The new provider returns Hostile only for an
active, explicitly recorded enemy-to-decoy request. It returns null otherwise,
leaving the existing native providers and original allegiance untouched. Native
explicit overrides have priority zero; this provider uses priority ten and does
not supersede them. Native WorldSupport clears its attitude cache every .1s;
this is a bytecode observation, not measured connected reaction latency.
Registration is once per world view with weak-key tracking. Request storage is
bounded at 1024, expires with the underlying summon lease, and is cleaned when
the decoy terminates or is removed. No per-tick provider registrations occur.

The same audit confirms that the installed periodic EntitySavingSystem query
excludes NonSerialized entities. This strengthens the earlier structural
serialization evidence; an actual connected crash/restart is still required.

### Behavior and exclusions

Simulacrum requires Wand/Spellbook, costs 20 Mana, starts the authored 18-second
cooldown through the existing stat-adjusted cooldown authority, places within
four metres, lasts eight seconds, and has 25% captured maximum Health. It has
zero damage and never claims an attack or invokes the damage adapter. The shared
registry enforces one pending-or-active decoy per owner, including at delayed
release. A second decoy rejects without replacing the existing one or charging
again when admission fails. Combat summons do not occupy this singleton slot;
all actors still count toward the existing total caps.

The role uses the exact installed human-shaped Mannequin appearance and an empty
instruction, following the specification's labelled-template fallback. Its
localized name is `Simulacrum (decoy template)`. It does not copy the player skin,
inventory, equipment, quest state, permissions or skills. It has no Seek,
attack, reward, interaction or flock instruction. Native summon cleanup, native
Health projection, nonserialization and no-drop defenses are retained. This is
not a claim of translucent appearance, visible label or fade quality.

Attraction is an explicit encounter opt-in, initially Wolf_Black only. The NPC
must already target the caster, remain hostile, have LOS, and pass the retained
bounded/protected native query; reserved and RPG-created actors reject. No
unknown NPC is inferred to be compatible. The request changes the default
marked target to the decoy, preserving the prior caster identity. Cleanup
restores that target only if the decoy still owns it; a newer encounter or taunt
target is not overwritten. The native AI may reject or replace this request.
`DECOY_ATTRACT_REQUEST` records the request, expressly not a fabricated native
attack or connected success. Initial coverage is intentionally not all enemies.

Swarm and Death Pact reject the noncombat decoy. Empowerment (canonical internal
ID `minion_empowerment`) changes HP to 32.5% and life to six seconds, but cannot
create damage or another decoy. Consume Minion rejects it. There is no second
gameplay subsystem, HUD modification, stat rebalance or native input change.

### Validation and remaining gate

The full retained build passes 624 tests: fourteen new decoy/asset cases cover
the canonical values, zero attack claims, singleton admission, pending actors,
separate owners, shared caps, passive compatibility, consumption exclusion,
termination reasons, cancellation and explicit encounter policy. A first test
pass incorrectly compared the authored cooldown with the stat-adjusted snapshot
and used the display name as a passive ID; those fixture errors were corrected
without changing production cooldowns or modifier values. An initial Java
compile also caught a wrong Candidate accessor; trace identity now reads the
actual native UUID component. These failures were local, before any deployment.

The normal three-mod boot must resolve seven Stage10 profiles, four RPG-created
roles and 58 neutral native triggers, then stop cleanly. Exact hashes, retained
test inventory and smoke result are in cohort E's verification.json. Rollback is
cohort D, SHA-256
`6480E6CDD352647E76C77378E663461DA11A212053826FEFF758A056670EECE8`.
Player schema remains 5, compiled-plan schema 8. No live deployment occurred.

Connected gates remain UNVERIFIED: static human appearance and name, actual
native NPC attraction/attack delivery, decoy Health loss, protection against
newer threat owners, and expiry/death/logout/world-removal/restart cleanup.
Dominate still requires a restoration-safe conversion implementation; the
existence of a provider alone does not prove full conversion. Bone Cage still
requires proven enemy-only collision before activation may be enabled.

## Cohort F — Dominate native relationship leases

### Native boundary and eligibility

Dominate uses the audited native AttitudeView provider path; it does not spawn a
replacement actor, call `NPCEntity.setRole`, overwrite an attitude-memory entry,
change faction data, or copy inventory/skills. Its typed conversion component is
separate from created summons and consumes the existing DIRECT_TARGET executor
dispatch seam. The native NPC retains its original AI, damage interactions,
Health, role and serialized identity. The RPG only owns a twelve-second
relationship/targeting lease. The explicit project Dominatable opt-in currently
covers **Wolf_Black common actors only**. This is authored coverage, not a claim
that every vanilla common NPC is Dominatable. The policy supports specialist and
explicit elite opt-in records in tests; no native elite role has been opted in.

Admission rejects players, allies, bosses, native invulnerability, reserved or
RPG-owned entities, NonSerialized actors, flock members, unclassified roles,
dead actors, and actors with persistent marked-target bindings. Flock membership
is read, never changed. Eligibility is checked before cost and at committed
release, with authoritative range/LOS and stable target UUID. Selection scans
at most 4096 NPCs and admits at most 64 spatial candidates; overflow rejects,
never silently selects the first arbitrary subset. Nearest distance then UUID
breaks ties. Later native role/protection/flock/persistent-owner changes cancel
the temporary control instead of overwriting the new owner.

The persistent-target exclusion follows a specific bytecode observation:
MarkedEntitySupport's ordinary `setMarkedEntity` clears a slot's rebind entry.
Its codec serializes `RebindTargets`, not ordinary live target references. Thus
an arbitrary overwrite could destroy saved encounter ownership. This build
only changes a non-persistent default target and captures its original UUID.
On end, a still-valid original target is restored; an unavailable target is
cleared according to native reference validity. A newer persistent owner is
never erased. Other marked slots are not overwritten. The natural NPC remains
serialized; only the RPG control marker and lease are unsaved. A restart cannot
resume conversion or persist rewritten allegiance because no such rewrite is
performed. Actual native unload/restart behavior still requires connected proof.

### Gameplay, ownership and cleanup

Wand/Spellbook, 30 Mana, authored 35-second cooldown, 15-metre target range and
12-second duration are retained. The normal RPG resource/cooldown authority
applies stats and charges once. The native ItemAbility remains zero-cost,
zero-cooldown with CostType None. Conversion creates no direct damage, projectile
or new actor. One pending/active conversion is allowed per caster and an existing
target cannot be leased by two owners; the registry is capped at 256 leases.
Expired pending leases are reclaimed before new admission, including a queued
native actor that never became available. The created-summon registry remains
separate because conversion does not create another NPC.

A priority -10 provider supplies temporary relationships for the explicitly
admitted actor without modifying the lower native providers or timed memories.
It inherits other NPCs' attitude toward the caster and treats players and other
converted actors as friendly to avoid indirect PvP. Returning null when the
lease ends restores ordinary native provider evaluation; the native .1-second
attitude cache remains native-owned. A 5 Hz bounded target update directs the
existing AI toward an eligible hostile target or clears the default target when
none is available. Native animation, perception and attack execution have not
been observed. The native Filter guard rejects converted-source damage to
players/allies/protected/owned targets, and rejects friendly EntitySource damage
to an actively converted actor. It also rejects stale converted-source damage
after lease expiry or owner loss. There is no second damage calculation engine.

Expiry, owner death/logout, role/protection changes, native death and entity
removal release control. The marker carries restoration metadata independently
of registry cancellation, so removing the logical lease cannot lose the saved
threat identity before the native cleanup callback. Death cleanup is ordered
before native NPC DropDeathItems and marks the actual dead role's drops handled;
it never changes a living natural NPC's loot state. The conversion marker stays
on the dead body until native removal, preventing corpse reuse/reward eligibility.
The corpse adapter now rejects converted bodies. If another native mechanism
resurrects the body, the already-restored marker is removed rather than leaving a
permanent no-damage conversion marker on a living actor. Stage12 reward handling
must retain the same conversion exclusion.

The presentation fallback is a bounded procedural overhead diamond and ground
outline with a final-second blink. Each fragment lasts .21 seconds and is
refreshed at most 5 Hz; cancellation stops refreshes. These verified DebugUtils
calls do not mutate gameplay. They are not proof of a rendered crown, native
allied silhouette outline, animation or artist approval. No speculative particle
or sound asset was introduced.

### Failures found and corrected locally

The first conversion fixtures exposed an actual new-component integration
omission: the shared service did not capture a committed target for conversion.
Consequently an immediate activation reached dispatch without a target and
entered its old refundable-failure branch. Conversion is now included in target
capture and in the retained paid-failure policy for effects that may already
have started. A delayed cast uses the original target; it cannot retarget during
release. An uncertain post-dispatch failure keeps the charge/cooldown and leaves
the finite lease to explicit cleanup/expiry instead of granting a free effect.
Tests assert COMMITTED status explicitly; the historical `committed` boolean by
itself is not sufficient because a paid termination can also carry that flag.
No Stage04/05 family mechanics or resource formula was changed.

The shared compatibility authority now explicitly rejects generic Echo,
Retaliation, Critical Trigger and Kill Trigger for conversion targets. Summon
operators also reject conversion. Compiled-plan schema is 9, forcing the changed
compatibility contract to recompile; player schema remains 5. Three historical
profile-inventory tests exclude the new typed conversion component while
retaining their original 12/35-profile assertions.

Review also tightened E's decoy admission and cleanup: native Invulnerable,
observed boss and NonSerialized markers now reject explicitly, and persistent
target bindings can neither be acquired nor overwritten during restoration.
This corrects reliance on a spatial/hostility query that does not itself enforce
every protected-target rule. It is a conservative safety change, not new proof
that all external mod ownership schemes are recognized.

### Evidence and rollback

Full retained gate: **651 tests**, including 27 new conversion cases. Coverage
includes exact payment/dispatch, owner/target/global caps, identity, typed
eligibility, persistent-threat exclusion, elite opt-in, lifetime/expiry,
cancellation, delayed target retention, generic repeat rollback, no replay from
a recreated registry and paid failure after dispatch. One test executes the
**actual installed AttitudeView class** to verify provider priority and null
fallthrough; it uses no world/client and does not prove native NPC behavior.

The normal isolated three-mod boot resolves eight Stage10 profiles and 59 neutral
native triggers, registers the native conversion systems, reaches network boot
and shuts down cleanly. Final artifact/smoke/test hashes are recorded in
`evidence/stage-10/cohort-f/verification.json`. Rollback is cohort E, SHA-256
`C9BA5B077674939605CE46C8E5F5D16CD1DFFCAEE8D3C37047390243624B55E8`.
No live deployment or native HUD/XP geometry change occurred.

Connected gates remain UNVERIFIED: a real eligible Wolf must accept the lease,
retarget and use its native attacks, refuse friendly damage, then restore its
original threat and ordinary allegiance at every terminal boundary. Native
death/drop ordering, corpse exclusion, chunk unload/reload and restart still
need connected evidence. The R024 ability-input boundary is not promoted by this
cohort. **Bone Cage is the final independent Stage10 feature still to audit.**

## Cohort G — Bone Cage safety gate and Stage10 local closure

The exact installed collision audit is retained under
`evidence/stage-10/cohort-g/api`, generated by
`tools/Capture-Stage10CollisionAudit.ps1`. CollisionConfig exposes block/material
predicates and EntityCollisionProvider accepts a per-query entity predicate.
These APIs filter a particular collision query; calling one does not register
an owner-relative persistent barrier in native player/NPC movement. The native
HitboxCollisionConfig packet contains collision type, soft offset, rotation and
anchoring flags, but no faction/owner-relative pass-through rule. A generic
solid hitbox would not prove that allies/caster pass or that bosses/world-critical
actors cannot be trapped. This is **not** a claim that no future adapter can ever
implement selective collision; it identifies what the audited paths establish
and, importantly, what they do not.

The master explicitly says to keep this family disabled if safe selective
collision cannot be proven. The packaged Bone Cage profile therefore retains
18-metre range, 28 Mana, 18-second cooldown, five-second duration, radius four,
twelve equally spaced boundary segments, 2.5-metre height, .25-metre thickness,
.60 Magic eruption and one-second Root as **design data only**. Its segment
geometry is deterministic and tested for closure, equal spacing and dimensions.
It is not registered as a native collider. No substitute wall, terrain mutation,
teleport clamp, forced movement, damage-only cage or decorative cage is called
a working collision implementation.

The shared activation service rejects with
`BONE_CAGE_NATIVE_ENEMY_ONLY_COLLISION_UNVERIFIED` before native preflight,
target capture, resource/cooldown commit or delayed scheduling. The WALL
dispatch seam and native preflight independently reject this component. Its
neutral native trigger/description makes the reason identifiable, not a silent
no-op. Even Potency, Skill Delay or repeated input cannot bypass the gate. No
eruption damage or Root is delivered while the required boundary is disabled.

The closure review also preserves the conversion marker's restoration/query
state when native component cloning occurs. Previously clone constructed a
fresh marker with the same lease but reset the `restored` flag, which could
re-arm a completed cleanup when metadata was copied. The new regression proves
that copying metadata preserves completion. This is local state-machine proof,
not a connected clone/unload observation.

Final full retained suite: **662 tests**, no failures/errors/skips. Eleven new
G cases cover exact geometry, closed/equal segments, zero-cost/no-dispatch
rejection, repeated input, delayed scheduling exclusion, Potency exclusion,
no false native collider component, malformed data, final catalog inventory and
conversion metadata cloning. The complete build reruns retained Stage01B,
CanvasUI and Stage02–09 regressions plus all Stage10 cohorts. The final normal
three-mod smoke must resolve nine Stage10 profiles, four native created roles
and 60 neutral native triggers, reach network boot and stop cleanly. Exact
artifact/tests/smoke and protected-path results are in cohort G's
`verification.json`. Rollback is cohort F, SHA-256
`3FB303092035B15A6E9FD0E6E682092C99BE83D05E71CCAD6986408191C21975`.
Every earlier cohort artifact remains separately archived. Player schema is 5,
compiled-plan schema 9; canonical inventories remain 87 skills and 66 passives.

### Current nine-skill disposition

| Skill | Local implementation / remaining connected gate |
| --- | --- |
| Wolf Summon | Native created combat actor; spawn, motion, Health and RPG attack proof pending |
| Revive Fallen | Exclusive durable corpse consumption and clamped proxy; initial native source role Wolf_Black only; connected corpse timing and native elite-source expansion pending |
| Summon Void Crawlers | Three native actors / shared batch ownership; connected batch/nav/combat proof pending |
| Brood Call | Four native actors with innate power; connected batch/nav/combat proof pending |
| Consume Minion | Owned combat-summon sacrifice and finite bonus/shield; connected sacrifice/benefit/cleanup proof pending |
| Corpse Burst | Paid corpse consumption and bounded real damage path; connected Health-loss/body-lifecycle proof pending |
| Simulacrum | Noncombat Mannequin fallback with explicit Wolf_Black attract request; native attraction/rendering/cleanup proof pending |
| Dominate | Existing-actor relationship lease; standalone unprotected Wolf_Black opt-in only; native AI/allegiance/threat/death/unload proof pending |
| Bone Cage | Intentionally disabled by master safety gate; native enemy-only collision has not been proven |

Swarm, Empowerment and Death Pact have typed component operators and automated
coverage; their native connected effects remain unverified. Unknown corpse,
decoy and conversion encounter roles fail closed. No source coverage is inferred
from a generic default rank. No new corpse/reward source, HUD ownership, native
input fix or connected PASS is claimed.

### Connected checklist for a later owner test build

First establish R024-or-later native Ability2/3 input reaching the shared
activation service; the live R023 build was not replaced during this program.
Then exercise each enabled Stage10 skill with correct equipment and an eligible
target, and correlate input, commit, native callback and authoritative state.
Specifically check native batches/Health/attacks, paid-once delayed casts, corpse
single-use across skills and restart, sacrifice without loot/Death Pact,
Death Pact only on enemy kill/natural expiry, decoy zero damage/actual attraction,
conversion friendly-fire and restoration at every terminal boundary, and no
summon/corpse/reward leaks after owner/world teardown. Native elite corpse support
needs an explicitly audited role in addition to connected tests; the pure clamp
fixture alone does not provide that coverage. Bone Cage must continue to reject
until a new collision adapter and connected safety proof exist.

The owner's continuous implementation authorization permits advancement to
Stage11 after this local gate while preserving these connected blockers. The
final cross-stage ChatGPT handoff remains due after Stage13; this detailed
Stage10 report and its incremental history are already in the GitHub checkout.
