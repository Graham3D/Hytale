# Current-build QA/QC runbook — Stage 13 L

For the owner, 2026-09-09. Tested implementation checkpoint `cd7cdbd0299de4bf18740b2cca08756b64b77f69`. This is a **procedure**, not a report that these tests have been run. Your statement “The mod loads” is recorded as successful owner-reported loading only.

Start with sections 1–4. If native casting fails, stop the casting-dependent tests and send the logs; do not spend hours trying all skills through a broken common input path. No new build or deployment is needed for this documentation.

## 1. Safe setup and evidence capture

- [ ] Use a disposable **copy** of the RPG world/test character for equipment, graph, resource, point, damage and progression tests. While the world/server is fully stopped, back up the entire world folder, including `mods` plugin data, native inventories and all reward/encounter journals. Do not copy an actively writing world as a consistent backup.
- [ ] Keep exactly HytaleRPG 0.0.25, CanvasUI 0.1.0 and HytaleDevLib 0.5.0. L's RPG SHA256 must be `1E4A5CAA1344CE71C8701EBCFBFCB3883BD288DC90BE9732066338A5F6A5B0F6`. Same filename/version does not distinguish older builds.
- [ ] Record world name, date/time/time zone, player, Hytale version, JAR hash, held item, raw attributes, linked passives and video filename for each case. Current pinned Hytale is 0.7.0-pre.1.
- [ ] Use an authorized test operator for developer commands. Some current dev commands have Adventure-group permissions; do not expose this development-entitlement build as a permission-audited public server. Permission denial is a setup result, not proof of gameplay failure.
- [ ] Use normal Hytale controls/settings to determine Ability2 and Ability3 bindings. E and R are your previously used bindings; use the actual configured keys if changed. Ability1/Signature belongs to Hytale. Logical skill03 has no supported native Ability4 binding; no fourth/fake input is supplied.
- [ ] Record start/end times for each small batch. Allow pending durable operations to finish; do not spam commands or restart while a result is pending.

Current live paths (substitute the copied world's name for `RPG` when testing a copy):

```text
Server logs:
C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\logs

Skill/combat/progression trace and rotations:
C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\InigmasGames_HytaleRPGPhase00Audit\logs\rpg\skill-trace.jsonl

UI trace:
C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\InigmasGames_HytaleRPGPhase00Audit\logs\rpg\ui-trace.jsonl

Client logs:
C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Logs
```

Tracing is already enabled; there is no need for an invented `/rpg trace start` command. Default skill tracing is bounded to 8 MiB/file with four retained files total, including the current file (`.1`, `.2`, `.3` rotations). Collect all available rotations promptly. For long load tests, an engineer must continuously archive rotations so they are not overwritten. Console summaries are rate-limited and cannot establish that an event never occurred. `TRACE_GAP`, failed writes or missing rotations invalidate absence-based conclusions.

Logs may contain account/network details; share privately or redact authentication material before public posting. Do not edit your only copy of evidence.

## 2. First-join and basic UI — do these first

Run after the world has loaded and readiness has settled:

```text
/rpg loadout
/rpg compile
/rpg dev ability-status
/rpg progress status
/rpg character
/rpg skilltree
```

Close one page before opening the next for the first pass.

- [ ] No world-thread exception, disconnect, CustomUI error or desktop crash.
- [ ] Loadout reflects your saved skills/passives, not an unexpected empty replacement. Compilation succeeds for the saved valid graph or reports an explicit saved-content limitation.
- [ ] Native ability status eventually shows an active session. A brief persistence-not-ready state must not crash the world; a persistent state is a failure requiring logs, not repeated key presses.
- [ ] Character and fixed-layout Skill Tree open/close without stacking broken pages; selection, inspection and apply/rejection feedback work. Tab still opens native Inventory.
- [ ] Exactly three logical skill slots and six passive slots; no expectation of free canvas drag, panning, spline editing or C/K global shortcuts. `/canvasui-demo` is optional historical library inspection, not the production RPG editing test. Do not resume old input probes as ordinary QA.
- [ ] Health/Mana/Stamina presentation remains entirely native, including Stamina's normal temporary visibility. Absence of an always-visible Mana bar is not automatically a bug. No duplicate bars, RPG resource overlays or changed Signature Move presentation.

**Stop here on a load/UI crash.** Send the new server/client logs and timestamp; no skill test can certify a world that cannot stay connected.

## 3. Native activation — isolate weapon prerequisites

These equip commands change the loadout. Use the test copy. To get a clean comparison, remove existing passive links through the UI or use the unlink commands in section 5, and inspect the resulting loadout.

```text
/rpg equip skill01 quick_slash
/rpg equip skill02 fire_bolt
/rpg loadout
/rpg compile
/rpg dev ability-status
```

Wait for each mutation to finish before the next command. The exact native item IDs below are fixture identifiers from the audited registry, not new slash commands. Obtain them using the game's normal authorized inventory tools; this runbook does not guess a native item-grant command.

### Q03A — Quick Slash positive

1. Hold **Weapon_Sword_Iron** (or another audited supported sword/longsword/dagger). An arbitrary modded weapon may not be in the power registry.
2. Stand within 2.6 m of a living hostile target, facing it inside the 120° frontal arc, with clear line of sight. Have enough Stamina and no active conflicting action.
3. Run `/rpg stats` to record pools/attributes. Note that this diagnostic also reapplies derived native maxima; it is not a completely read-only command.
4. Press Ability2 **once**. Record visible hit/animation and target response.

Expected unmodified profile: base Stamina cost **5**, base cooldown **0.8 s**. Effective values can differ with attributes/passives/charges; compare the trace's resolved cost/cooldown, not rounded HUD snapshots while regeneration is occurring.

- [ ] Native input reaches RPG request → validation → commit → executor dispatch.
- [ ] Native damage lifecycle reaches actual target Health loss for a valid hit.
- [ ] One root pays once and starts its cooldown once; no duplicate native-plus-RPG hit.

### Q03B — Fire Bolt positive

1. **Switch the held item** to **Weapon_Staff_Crystal_Flame**, **Weapon_Staff_Crystal_Ice** or **Weapon_Wand_Wood**. A sword does not satisfy Fire Bolt's staff/wand requirement. Quick Slash need not work while this weapon is held.
2. Aim at a living hostile target with unobstructed flight within 24 m; have sufficient Mana.
3. Press Ability3 **once**. Record flight, impact and subsequent Burn separately.

Expected unmodified profile: base Mana cost **8**, base cooldown **1.4 s**, projectile speed **24**, maximum distance **24**, radius **0.30**. Damage scaling/mitigation and periodic Burn are separate from an accidental duplicate hit.

- [ ] `PROJECTILE_SPAWN_REQUEST` and `PROJECTILE_SPAWNED` follow the same root's accepted cast.
- [ ] Actual native flight/impact occurs, not just an icon flash; target Health loss is observed.
- [ ] One initial charge/cooldown, no duplicate native projectile damage. Periodic Burn must be identified as owned periodic damage, not miscounted as a duplicate direct hit.

### Q03C — controlled rejection/cancellation

- [ ] Hold the staff and try Quick Slash once: explicit equipment rejection, no paid execution.
- [ ] Hold the sword and try Fire Bolt once: explicit equipment rejection, no paid execution.
- [ ] With valid equipment, press again immediately during cooldown: reject/no second charge or execution.
- [ ] Try the skill with insufficient appropriate resource on the test copy: no partial spend/free effect.
- [ ] Move a target out of range, put a wall in the path, then record actual behavior. A projectile cast into empty space may validly commit and expire; a miss is not automatically refundable. Once dispatch may have applied effects, do not expect a refund.

If either positive test fails, report the earliest missing boundary. Relevant trace sequence:

```text
NATIVE_ABILITY_INPUT_OBSERVED
SKILL_ACTIVATION_REQUEST
SKILL_VALIDATION_PASS  (or SKILL_VALIDATION_REJECTED with failureCode)
SKILL_COMMITTED
EXECUTOR_DISPATCH
PROJECTILE_SPAWN_REQUEST / PROJECTILE_SPAWNED  (projectile skills only)
DAMAGE_GATHERED -> DAMAGE_FILTERED -> DAMAGE_APPLIED -> DAMAGE_INSPECTED
```

Filter by player/time/correlation and the `rootCastId`/`skillInstanceId` in event details; child effects have their own instance IDs under the same root. A damage fixture's success does not prove the key-input chain. Do not rerun the temporary `/rpg dev rune-control start` experiment routinely: it replaces native Rune slots temporarily and has a recovery journal. It is an engineer-guided diagnostic only if the new evidence requires another control.

## 4. XP graphics and Character allocation

### Q04A — presentation only, no XP award

```text
/rpg progress status
/rpg dev xp-display 0
/rpg dev xp-display 25
/rpg dev xp-display 50
/rpg dev xp-display 100
/rpg dev xp-display clear
/rpg progress status
```

Take a screenshot at each percentage before continuing.

- [ ] Frame centered over slots 1–9, above native resources, with no red missing-texture crosses or added XP text.
- [ ] Layers are Background→Fill→Frame; at 0% no fill, at 50% left half, at 100% the usable background width is exactly filled without overflow. Fill must not grow from center or right.
- [ ] Native-size assets: frame 702×28, background 696×28, fill texture 1×22. UI scaling may alter screen pixels; compare relationships rather than assuming screenshot pixels equal asset pixels.
- [ ] `clear` restores earned-progress display. Total earned XP did not change because of this fixture. This proves display behavior only, not XP rewards.
- [ ] Repeat at a second supported resolution/UI scale; inspect centering, clipping and overlap.

### Q04B — allocation, test copy only

```text
/rpg dev points grant 5
/rpg character
```

- [ ] Record pending/unspent points, click one attribute + once and apply as the UI requires. Exactly one point is consumed for one increment; pending points are consumed first.
- [ ] Close/reopen and rejoin. Committed allocation persists; remaining pending notification persists until consumed. No negative balance or duplicate allocation after rapid double-click/retry.
- [ ] Attribute grant is explicitly development-only; this is not an earned level-up test. No automatic undo is promised—restore the disposable test checkpoint when finished.

## 5. Graph/compiler — real commands, current direction

Start from a saved screenshot/export of the test loadout. To remove existing outgoing passive links:

```text
/rpg unlink passive01
/rpg unlink passive02
/rpg unlink passive03
/rpg unlink passive04
/rpg unlink passive05
/rpg unlink passive06
```

An already absent edge may return a no-op/rejection; verify state rather than assuming every command changes it. Do not reset an important build for this test.

```text
/rpg equip passive06 fork
/rpg link passive06 skill02
/rpg compile
/rpg loadout
```

- [ ] Fire Bolt + Fork compiles and persists. Cast with the correct staff/wand and enough targets: current Stage 07+ behavior should execute its valid continuation, not the obsolete Stage 05 “metadata only” behavior.

Then test transactional rejection:

```text
/rpg link passive06 skill01
/rpg loadout
```

- [ ] Quick Slash + Fork is rejected; the previous valid Fire Bolt link/loadout remains unchanged. Rejection must not erase the valid route or partially alter revision/state.

```text
/rpg dev potency-proof
/rpg equip passive01 potency
/rpg link passive01 skill02
/rpg compile
```

- [ ] Isolated Potency proof reports **15%** and **66** passives, leaving the loadout unchanged until the explicit equip/link commands. Potency is increased scalable magnitude, not universally “15% final damage after every other modifier.”
- [ ] In UI, test direct links, joint fan-out and illegal cycles/capacity conflicts; compare pre/post state on rejection. Moving/selecting UI elements must not silently alter compilation semantics.
- [ ] Use `/rpg unlink passive06` to remove the Fork connection; verify compiler and native behavior update. Save/rejoin and verify remaining graph.

## 6. Kernel diagnostics — useful but not substitute casting proof

**Test world only:** these commands change pools or damage a real NPC.

### Q06A — deterministic recovery

```text
/rpg dev recovery-proof
```

This deliberately sets Mana and Stamina to zero, then applies all four steps in one operation. It does not restore the original pool values.

- [ ] Normal recovery = **4% of maximum Mana and 4% of maximum Stamina**.
- [ ] Same normal root again = rejected/deduplicated, zero second recovery.
- [ ] New charged root = **12% + 12%**.
- [ ] Same charged root again = rejected/deduplicated, zero second recovery.
- [ ] Four corresponding trace entries share the proof correlation; compare captured before/after, not a later `/rpg stats` snapshot after native regeneration. With no other constraints the final proof value is 16% of each max, not 12%.

Use this without active reservations/Managuard for the baseline. Separately test real audited melee basic hits and charged hits; the fixture does **not** prove native hit detection. Native ranged/projectile-parent basic recovery is currently outside that melee observer's verified scope.

### Q06B — native damage pipeline

Aim at a living, expendable non-player NPC within **20 blocks** and run:

```text
/rpg dev damage never 5
/rpg dev damage force 5
```

- [ ] Each request has calculation→Gather→Filter→Apply→Inspect with one root/instance/correlation preserved through that request. Inspect records native Health-before/after and actual loss; crit decision matches `never`/`force` respectively.
- [ ] A missing/dead/non-NPC target is rejected safely. Do not damage pets, important NPCs or another player's assets.
- [ ] “Submitted damage” in chat alone is not PASS. Native mitigation can change the actual loss.

### Q06C — attributes/resources (persistent changes: test copy only)

Record baseline with `/rpg stats`. On a clean raw-10 test character:

```text
/rpg dev attribute dex 20
/rpg stats
/rpg dev attribute int 20
/rpg stats
```

- [ ] Dexterity raises maximum Stamina; Intelligence raises maximum Mana. Cast martial/magical skills with appropriate equipment and confirm the corresponding resource pays. No HUD redesign is expected.
- [ ] Restore the recorded raw values with the same attribute command, or `/rpg dev reset` **only when intentionally restoring all five attributes to 10**. Reset is not a full character reset.

Optional `/rpg dev resource mana spend 5` and `/rpg dev resource stamina spend 5` exercise actual pool spending; `regen <number>` uses **seconds**, not a raw resource amount. Do not use large arbitrary values to refill or manipulate release evidence.

`/rpg dev status chill` exercises the kernel status fixture on the invoking player's ID. It does not by itself prove a native enemy freeze, animation or combat delivery. Use Frost Bolt/other legitimate status delivery to test native Chill→Frozen and control policy.

## 7. Systematic skill QA — all 87 records

Use [qa-skills.csv](qa-skills.csv). It supplies every exact `/rpg equip skill01 <id>` command, current main/off-hand constraints, base costs/cooldowns, profile source, full runtime parameters and known gates. Blank/empty main-hand kinds do not waive a required off-hand or target condition. An item must also be supported by the audited power registry where power is required.

For **each enabled skill**, start with no passives and correct equipment, then:

1. Record a valid positive cast: request, payment, cooldown, geometry/impact count, statuses, visuals and final cleanup.
2. Record an invalid/cancellation case appropriate to that family (wrong gear, illegal target, obstruction, insufficient resource, interruption or owner departure).
3. Verify misses/cancel/expiry do not award damage/mastery or produce orphan effects. Distinguish valid paid misses from pre-dispatch failures.
4. Fill positive/negative/cleanup result and evidence paths in the worksheet. Record `BLOCKED_SETUP` if the required native fixture is unavailable; do not fake a positive.

| Family / examples | Positive setup and required observation | Negative/cleanup case |
|---|---|---|
| Strike: Quick Slash, Heavy Swing, Backstab, Execution/Finishing Strike | Proper weapon and frontal/line/rear/low-Health/combo condition as specified by profile; actual hit count and conditional magnitude | Outside arc/range, wrong facing/Health threshold, interrupted windup; no free repeat or duplicate Finisher consumption |
| Movement: Quickstep, Charge, Pounce, Jump/Dive Strike, Void Dash | Valid loaded ground/path; actual movement distance/readback and any landing payload | Solid wall, unloaded/invalid destination, departure mid-action; no clipping or invisible landing damage |
| Reaction: Riposte, Reflective Hide | Qualifying native blocked/hit event during the documented window | Window expires without event; no reaction damage or endlessly armed state |
| Projectile: Fire/Frost Bolt, Fireball, missiles, shots/tosses | Valid weapon/ammo; native flight, wall/target impact, range termination and owned periodic effects | Moving target, wall/stairs, range expiry, caster moves or logs out; no damaging effect on harmless expiration |
| Burst/cone/line/wall: Frost Nova, Cold Wave, Lightning Bolt, Wall of Fire | Targets inside shown footprint, bounded tick/impact counts and LOS | Target just outside footprint/wall, owner leaves; shown geometry must match damage within master's 0.25 m sampling tolerance |
| Ground zone/trap: Blizzard, Vortex, Powder Mine, Root Snare | Ground placement, arming/trigger timing, periodic cadence | Trigger too early, invalid ground, leave/unload, natural expiry; no orphan field or duplicate trigger |
| Overhead/bombardment: Meteor, Comet, Avalanche, Void Cataclysm | Warning precedes actual scheduled impact, correct positions/counts | Depart/unload during delay, invalid geometry; warning must not appear after impact |
| Beam/orb/tether: Void Beam, Life Drain, Ball Lightning, Orbiting Shadow Blades | Valid target/range, maintained pulses, actual upkeep and source ownership | Release/interruption, obstruction, low Mana at a pulse, jitter; no unpaid pulses after termination |
| Direct target/support: Minor Heal, Hunter's Mark, Taunt, hexes | Eligible target/allegiance and actual healing/control change | Full-Health or illegal/protected target, boss/control resistance; no credit for requested-but-unapplied effect |
| Aura/barrier/buff: Managuard, Chilling Aura, Spirit Shield, Flame Weapon | Reservation/activation, actual capacity/buff effect, deactivation | Insufficient spendable Mana, repeated toggle, cap changes, departure; no duplicated reservation/refill |
| Summon/corpse/conversion: Wolf Summon, Revive Fallen, Consume Minion, Dominate, Simulacrum | Supported native role/corpse/allegiance, ownership/caps, exclusive consumption, restoration | Competing corpse users, boss/protected target, target death, owner logout/unload; no reusable corpse or lost allegiance restoration |

**Known whole-skill gates:** Frenzy, Guard, Snipe and Bone Cage. Test that their explicit rejection occurs before resource/cooldown payment and no effect is spawned. Record `EXPECTED_GATE_REJECTION`, not “skill works.” Do not force-enable them for QA. Other records have partial limitations (e.g. Flame Weapon contact, Pedanticism native cooldown, restricted corpse/conversion roles); the CSV retains them.

## 8. Passive QA — all 66 records

Use [qa-passives.csv](qa-passives.csv). For each passive:

1. Choose a compiler-approved skill/component from the canonical compatibility matrix/description. Do not assume every passive applies to every skill.
2. Record an unmodified baseline with identical attributes/gear/target; equip passive01, link it to skill01 using the worksheet commands, compile, then repeat.
3. Verify the authored modifier effect, correct component scope, one root cost and bounded children. Match actual outcomes and trace to profile, not just UI description.
4. Exercise an incompatible pairing: explicit rejection or documented inactive disposition with prior valid graph/ownership retained, never silent corruption.
5. Exercise natural expiry versus cancellation for Aftermath/delayed effects, root caps for repeats/procs, and actual Health-loss requirements for leech/recovery.

Priority combinations: Fireball with Potency/Echo/Skill Delay; a compatible projectile with Pierce/Fork/Chain/Return; a zone with Mobile Domain/Expanded Radius; compatible Aura with Conservation/Resonance; Managuard with Shared Aegis/Reflective Ward. Explicitly reject Quick Slash+Chain and Managuard+Resonance. Apply links through the compiler; do not manually edit JSON to force a pairing.

The 5,742-cell and 2,145-pair automated matrices remain evidence; you need not manually perform every matrix cell. Connected primitive coverage and high-risk combinations are still required. There is no canonical Swift Recovery passive. Generic cooldown-recovery capability has independent local coverage.

## 9. Shield escrow, progression and persistence

### Q09A — Managuard / Shared Aegis, controlled test setup

Equip Managuard using the worksheet, set allocation with `/rpg managuard 20`, and activate via its projected supported ability. The command requests allocation; its message is not proof that asynchronous authorization is complete. Use a supported controlled damage source and trace inspection.

- [ ] No reduced native damage before durable absorption authority exists.
- [ ] Authorized hits consume capacity with no per-hit persistence wait; measured loss never exceeds the authorized budget and respects native filtering.
- [ ] Repeated toggle, changed allocation, reconnect and capacity changes cannot refill spent shielding for free.
- [ ] Shared Aegis redirect/reflection does not recurse and does not award recovery for fully absorbed/non-Health damage. If a trusted allied-target fixture is unavailable under SOLO_ONLY, record the blocker.
- [ ] Natural recharge and late durable completion do not double-authorize capacity.

Abnormal-crash forfeiture and failed/slow storage are **engineer-only copied-world tests**; do not kill the live server or corrupt live journal files. Unused escrow may be lost conservatively after abnormal crash. It must never become reusable free shielding.

### Q09B — progression and honest acquisition limits

```text
/rpg progress status
/rpg character
```

Record before and after an actual eligible encounter. The three mapped roles are Wolf_Black, Trork_Warrior and Skeleton_Archer, but matching a role name alone does not guarantee an eligible spawn. Native provenance, biome, contribution, exclusions and anti-farm state must all qualify. A dev-spawned NPC may deliberately award nothing. Do not bypass exclusions to manufacture XP evidence.

- [ ] Inspect credit, death plan and durable award IDs; one death/reward application, no duplicate after normal reconnect/restart.
- [ ] XP/mastery reflect meaningful actual outcomes, not requests, over-healing, invulnerable targets or decorative effects.
- [ ] Earned level transition awards five unspent and five pending attribute points per new level; notification and XP display follow real persisted values. Development points/XP-display fixtures do not prove this.
- [ ] Unknown/ineligible enemy identities fail closed. If no eligible fixture can be located, mark `BLOCKED_SETUP`; there is no public XP grant command in this build.
- [ ] Acquisition remains blocked without a VERIFIED_CONNECTED source. Development entitlement access to skills is not proof of earning/learning them.

Optional transaction tests, **copy only**:

```text
/rpg progress export
/rpg progress status
/rpg progress import <exported-base64url-text> <current-revision>
/rpg progress buy <canonical-passive-id> <current-revision> <new-request-UUID>
/rpg progress respec <current-revision>
```

Replace placeholders; obtain the latest revision from status before each new transaction. Export is a build layout/ID transfer, **not a full save backup**. Import cannot grant XP or ownership. Buy needs eligibility and sufficient Insight; retry with the same UUID to test deduplication without a second debit. Insufficient eligibility should reject, not be bypassed. Respec requires the ten-second quiet window/no pending cast; it changes allocations. Do not expect a blanket “reset everything” behavior.

### Q09C — normal persistence (after previous actions settle)

1. Record `/rpg loadout`, `/rpg progress status`, Character allocation and ability status.
2. Leave to main menu and rejoin; compare persisted state.
3. Exit cleanly, restart Hytale and rejoin; compare again.
4. Confirm no first-join crash, unexpected empty replacement, free cooldown/charge reset, duplicate reward or shield refill. Expired real-time cooldowns may legitimately finish while offline; compare the contract and timestamps, not literal identical remaining seconds.
5. Native owned effects must tear down without orphan projectiles/fields/summons. Owed durable reward work must not vanish with the entity.

## 10. Engineer-only release qualification / QC

These are not required for your first short playtest, but remain required release gates. They are not automatically PASS from the 2,093 local tests or your successful join.

- [ ] Every skill has positive plus invalid/cancel evidence, or its explicit supported gate; every passive primitive and required combination has coverage.
- [ ] Permission audit with a non-operator client: developer mutations must not be available to ordinary production users. Record current Adventure-group/development-entitlement exposures as failures requiring correction; do not grant permission broadly to hide the issue.
- [ ] Native damage, self/ally/protected/boss policy, status resistance and effect cleanup tested with multiple clients and world transitions.
- [ ] Copied-world fault/recovery: delayed/cancelled save, real process halt at owned boundaries, corrupt data fail-closed, duplicate reward delivery, pending death/removal, shield authorization/restart, coordinated archived-build rollback. Never downgrade one schema/ledger in place.
- [ ] Missing icon/particle/template and unsupported-event faults on a copied package; clearly distinguish missing presentation from missing mechanics. Do not modify the tested JAR and report it as the same hash.
- [ ] Execute the [native persistence qualification scenario](../stage-13/native-persistence-isolated-qa.md) against L, not its historical J artifact. Four connected players, 16 hostiles, 24 projectiles/caster, eight fields/caster and eight summons/caster; measure actual populations, base-game baseline, server/GC/disk behavior and backlog.
- [ ] Added RPG native tick p95 ≤4 ms, p99 ≤8 ms; complete `NATIVE_RPG_TICK_SAMPLE` coverage grouped by world/tick, no double-counted nested spans. Storage acknowledgement latency is a different measurement. Missing ticks are not zeroes. Growing worker backlog is independently a failure.
- [ ] Intended-deployment player-count scaling/200-player simulation where applicable, network-profile tests, UI/feedback timing and final complete RC validation remain separate gates. Do not label a reduced one-player battle the specified load test.

## Result sheet and what to send back

For each case use:

```text
Case ID / skill / passive:
Build hash, world, player, start/end time with time zone:
Weapon/main hand/off hand, raw attributes, loadout revision, modifiers:
Exact commands and key presses:
Expected:
Observed:
Result: PASS / FAIL / BLOCKED_SETUP / EXPECTED_GATE_REJECTION / NOT_RUN
Trace correlation/root/instance and relevant event sequence:
Server/client log filenames, screenshots/video:
Earliest failure boundary; any TRACE_GAP or incomplete evidence:
```

For a short first response, send results for **Q03A Quick Slash, Q03B Fire Bolt, Q04A XP display, Q05 graph rejection, Q06A recovery and Q09C restart/rejoin**, plus the complete session trace rotations and server log. If Q03 fails, stop later casting-dependent cases and send that failure first. No requirement to finish all 153 records before asking for a fix.
