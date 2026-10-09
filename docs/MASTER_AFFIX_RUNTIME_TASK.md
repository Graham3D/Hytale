TASK: MASTER AFFIX RUNTIME COMPLETION — SINGLE IMPLEMENTATION STAGE

Authority:
- Master_Affix_v1.0 = runtime implementation authority.
- Gear Master v1.2 = authored affix identity, prefix/suffix identity, eligibility, tiers, values, requirements, tooltip wording, and intended gameplay effect.
- GEAR_AFFIX_RUNTIME_AUDIT R164 = current implementation baseline.

Scope:
Complete the existing 160 affixes:

WA-001 through WA-158
GA-159
GA-160

Do not invent new affixes.
Do not redesign their intended effects.
Do not change the spatial inventory system, Skill Tree, unrelated UI, or unrelated RPG systems.

This is ONE continuous development stage.

Codex may internally group related mechanics for implementation efficiency, but those groups are not separate delivery stages and are not stopping points.

The task is complete only when all 160 affixes have actual runtime behavior and pass the automated affix simulator described below.

======================================================================
1. PRIMARY COMPLETION RULE
======================================================================

An affix is NOT considered functional because:

- its definition exists;
- it can roll;
- it persists;
- its tooltip displays;
- it appears in GearAffixRuntime.ENABLED;
- an Iron Sentinel projection recognizes it;
- a unit test can parse it.

An affix is FUNCTIONAL only when:

equipped item
→ authoritative gear snapshot
→ intended gameplay owner
→ measurable gameplay result

and that result is proven by deterministic automated testing.

Target at completion:

160 FUNCTIONAL
0 GATED
0 MISSING_RUNTIME

The R164 audit found no affix proven impossible through the current server/API architecture, so this task assumes every existing affix has a viable Hywind runtime path.

Do not leave an affix disabled merely because its adapter does not currently exist.
Build the required shared adapter.

======================================================================
2. PRESERVE EXISTING AUTHORITATIVE SYSTEMS
======================================================================

Do not create parallel combat/stat systems.

Reuse and extend the existing Hywind/Hytale owners wherever applicable, including:

- GearAffixRuntime
- HytaleGearEquipment
- ManagedGearDamageInteraction
- ManagedGearProjectile
- DamageCalculationService
- ModifierBuckets
- HytaleDamageAdapter
- HytaleDamageLifecycleSystems
- CriticalRoller
- HytaleConditionalDamage
- StatusService
- PeriodicStatusRuntime
- HitProcRuntime
- RpgCooldownService
- RpgResourceService
- RootLeechBudget
- HealingCalculationService
- HytaleSupportSystem
- SupportRuntime
- HytaleSummonSystem
- SummonProjection
- IronSentinelAffixes
- IronSentinelStatProjection
- IronSentinelMaterial
- projectile execution/ballistics systems
- RpgUiProjectionService
- Advanced Stats projection

If several affixes need the same mechanic, implement ONE shared typed runtime adapter and feed the relevant affixes into it.

Do not implement 160 unrelated switch statements spread through gameplay code.

======================================================================
3. CREATE ONE AUTHORITATIVE EQUIPMENT EFFECT SNAPSHOT
======================================================================

Extend the gear runtime so valid equipped gear resolves into an immutable authoritative effect snapshot.

The snapshot should contain typed values needed by the 160 affixes, such as:

- raw attributes
- Health/Mana/Stamina bonuses
- local weapon Physical range
- global attack modifiers
- spell modifiers
- Crit Chance
- Crit Multiplier
- cast rate
- cooldown recovery
- elemental flat damage
- elemental increased damage
- elemental penetration
- elemental conversion
- resistances
- Status Resistance / penetration
- status potency/duration
- regeneration
- skill cost modifiers
- healing/barrier modifiers
- summon modifiers
- skill-rank modifiers
- Magic Find / currency modifiers
- utility modifiers
- proc definitions
- item Aura definitions

Only valid equipped items contribute.

Backpack ownership alone contributes nothing.

Invalid equipment contributes nothing.

Unequipping immediately removes future contributions.

Committed attacks/casts/projectiles retain their accepted snapshot where the RPG runtime already uses snapshot semantics.

======================================================================
4. IMPLEMENT EVERY AFFIX THROUGH ITS REAL GAMEPLAY OWNER
======================================================================

Follow Master Affix v1.0 individually for all 160 IDs.

Examples of the required integration standard:

LOCAL PHYSICAL DAMAGE
Honed / Brutal / Measure / Slaughter

→ alter the actual managed weapon damage range before the native attack coefficient is applied.

CRITICAL CHANCE

→ feeds the same critical-roll owner used by real Hywind attacks.

CRITICAL MULTIPLIER

→ modifies the actual critical multiplier used when that attack resolves.

COOLDOWN RECOVERY

→ changes actual cooldown work rate, not merely the Advanced Stats number.

MAX HEALTH / MANA / STAMINA

→ changes native authoritative capacity while preserving current deficit.

ELEMENTAL DAMAGE

→ becomes part of the actual attack/skill damage-channel composition.

ELEMENTAL RESISTANCE

→ modifies incoming Hywind combat damage of the matching RPG element.

Environmental hazards such as lava are explicitly excluded unless a later design changes this.

Example:

100 Fire combat damage
25% Fire Resistance
→ 75 damage before other downstream mitigation.

100 lava/environmental damage
25% Fire Resistance
→ unchanged by the RPG Fire Resistance affix.

ELEMENTAL PENETRATION

→ applies only to the matching RPG combat hit and reduces effective resistance for that hit according to Master Affix rules.

PHYSICAL → ELEMENT CONVERSION

→ replaces the authored percentage of the resolved Physical component.

It must not create equivalent free bonus damage.

STATUS AFFIXES

→ use the existing StatusService / PeriodicStatusRuntime.

Do not create separate gear-only Burn, Poison, Bleed, Chill, Fear, etc.

ON-HIT / ON-KILL / PROC AFFIXES

→ use root-event identity, proc locks, deterministic RNG and existing recursion protections.

One authored execution may not manufacture extra proc attempts from pellets, AoE victims or duplicate callbacks unless the affix explicitly permits it.

SKILL-RANK AFFIXES

→ alter EffectiveSkillLevel used by actual execution.

They must never permanently modify learned progression.

SUMMON AFFIXES

→ feed the real summon snapshot and affect only explicitly owned combat summons.

IRON SENTINEL

→ continue using the authored inheritance policy and the existing Iron Sentinel binding/material system.

AURA AFFIXES

→ instantiate the existing Aura/support runtime with explicit owner/item lifecycle.

Removing the item removes the Aura.

RESOURCE AFFIXES

→ change the actual resource owner/payment/regeneration path.

HEALING/BARRIER AFFIXES

→ change the existing healing/support owners exactly once.

DEFENSIVE AFFIXES

→ apply through the authoritative mitigation/control/displacement owners rather than visual-only stats.

======================================================================
5. PREFIX / SUFFIX IDENTITY
======================================================================

Preserve every affix's existing authored Prefix or Suffix identity.

Do not convert Prefixes into Suffixes or vice versa.

Do not alter group/exclusion legality simply to make generation easier.

Existing rarity and affix-budget rules are outside the scope of this runtime implementation unless a current rule directly prevents an authored affix from functioning.

Do not use this task to redesign rarity.

======================================================================
6. DAMAGE-CHANNEL CONTRACT
======================================================================

Use the canonical Hywind damage channels:

PHYSICAL
WIND
WATER
FIRE
EARTH
LIGHTNING
VOID

No generic MAGIC damage channel is introduced.

"Magic" may remain a power/scaling classification, not a seventh damage resistance.

Damage packets must retain source metadata sufficiently to distinguish:

- RPG combat damage
- status damage
- reflected/secondary damage
- environmental/native hazards

Affix effects must only apply to the sources allowed by their Master Affix contract.

======================================================================
7. DETERMINISTIC AFFIX SIMULATOR
======================================================================

Build a dedicated automated simulator/test harness for all 160 affixes.

This is the primary QA method for this stage.

The owner will perform connected gameplay QA later.

The simulator must construct controlled synthetic gameplay scenarios using the real Hywind runtime services.

It must NOT merely test the affix math in isolation.

Required synthetic actors:

SYNTHETIC PLAYER
- fixed level
- fixed raw attributes
- known learned skills/ranks
- known equipment
- known Health/Mana/Stamina
- controlled position
- controlled active statuses

SYNTHETIC TARGET
- known Health/max Health
- known elemental resistances
- known status resistance
- known Defense/protection
- known encounter rank
- known position/orientation
- known current statuses
- controllable enemy/boss classification

SYNTHETIC SUMMON
- controlled base Health/damage
- controlled movement/attack interval
- known owner
- known source equipment where applicable

======================================================================
8. EVERY AFFIX REQUIRES POSITIVE + CONTROL TESTING
======================================================================

Each of the 160 affixes requires at minimum:

A. POSITIVE CASE
Prove the intended effect occurs.

B. CONTROL CASE
Run the same event without the affix and prove the expected difference.

C. NEGATIVE / BOUNDARY CASE
Prove the affix does NOT apply when its conditions are not met.

Examples:

--------------------------------------------------
+1 ALL SKILLS
--------------------------------------------------

Player owns:

Fireball rank 5
Quick Slash rank 3
Healing Beam rank 7

Baseline execution:
5 / 3 / 7

Equip +1 All Skills:
6 / 4 / 8

Unequip:
5 / 3 / 7

Also assert:

- unlearned skills remain unlearned;
- passives are not raised unless explicitly included;
- saved progression remains unchanged;
- displayed effective ranks match execution ranks.

--------------------------------------------------
FIRE RESISTANCE
--------------------------------------------------

100 RPG Fire combat damage
0% Fire Resistance
→ 100

Equip +25 percentage points Fire Resistance:
→ 75

Verify authored resistance cap.

Then send an environmental/lava damage event:
→ no reduction from RPG Fire Resistance.

Then send Water damage:
→ no reduction from Fire Resistance.

--------------------------------------------------
FIRE PENETRATION
--------------------------------------------------

Target Fire Resistance = 40%
Attack Fire Penetration = 15 pp

Effective resistance for this hit:
25%

Do not permanently alter target resistance.

--------------------------------------------------
CONDITIONAL: FINISHING
--------------------------------------------------

Target at 29% normal Max Health:
→ bonus applies

Target above threshold:
→ bonus absent

Test exact threshold behavior defined by Master Affix.

--------------------------------------------------
25% PROC
--------------------------------------------------

Do not statistically sample thousands of attacks.

Inject deterministic RNG:

roll 0.20
→ proc

roll 0.30
→ no proc

Same committed event identity must replay identically.

--------------------------------------------------
LIFE LEECH
--------------------------------------------------

Known post-mitigation Health damage:
100

10% Life Leech:
→ queue 10 Health subject to the authored recovery cap.

Overkill, shields, reflection and invalid sources must not inflate recovery.

======================================================================
9. TEST THE REAL CONSUMER, NOT A DUPLICATE FORMULA
======================================================================

Simulator tests must invoke the same runtime owner used by production gameplay.

Bad:

test computes expectedFireDamage() using a simulator-only formula.

Good:

simulator constructs the equipment snapshot
→ passes event through the production damage/status/resource owner
→ asserts resulting state.

Do not copy gameplay formulas into the simulator.

The simulator supplies inputs and assertions.
Production services perform the calculations.

======================================================================
10. ELEMENTAL / STATUS TEST MATRIX
======================================================================

For every elemental family, automatically test all six channels where relevant.

For example:

Flat elemental damage:
Wind
Water
Fire
Earth
Lightning
Void

Increased elemental damage:
all six

Penetration:
all six

Conversion:
all six

Resistance:
all six

Where behavior is structurally identical, parameterized tests are preferred.

Statuses must likewise test their real shared owners:

- Burn
- Poison
- Bleed
- Chill
- Electrified
- Slow
- Stun
- Silence
- Blind
- Fear
- Root

Verify:

- source ownership
- chance
- duration
- potency
- cap
- refresh/replacement rules
- resistance/penetration
- recursion restrictions

======================================================================
11. CONDITIONAL DAMAGE SIMULATION
======================================================================

Test every condition at both sides of its boundary:

- close range
- long range
- rear arc
- low Health
- high Health
- Hard Control
- Burn
- Chill
- Electrified
- Poison
- Bleed
- Elite/Miniboss/Boss rank

Conditions must be evaluated by the production hit context at impact where authored.

======================================================================
12. RESOURCES, HEALING AND SUPPORT
======================================================================

Simulator must prove:

- Mana regeneration
- Stamina regeneration
- Mana cost reduction
- Stamina cost reduction
- channel upkeep reduction
- Healing Power
- outgoing healing
- healing received
- barrier strength
- support duration
- cleanse-triggered barrier
- tether reach
- channel ramp behavior

Verify that:

capacity increases do not refill missing resource;

cost reductions cannot create negative/free costs unless the authored formula explicitly allows zero;

reservations are not accidentally affected by finite-cost modifiers;

healing modifiers apply exactly once.

======================================================================
13. SUMMON AND IRON SENTINEL SIMULATION
======================================================================

Test:

- minion damage
- minion Health
- minion Defense
- minion resistance
- minion attack speed
- minion movement speed
- minion duration
- summon Mana cost

For Iron Sentinel:

create a synthetic eligible iron source item
→ bind the real material audit
→ create Sentinel snapshot
→ verify every inheritable affix alters the expected Sentinel runtime property.

Verify OWNER_ONLY effects do not leak into Sentinel.

Verify the source item cannot simultaneously remain usable/tradable while bound if the current Sentinel ownership contract forbids it.

======================================================================
14. ITEM SKILLS, TRIGGERS AND AURAS
======================================================================

Test:

- Borrowed Arts
- Fire Bolt trigger
- Frost Bolt trigger
- Succor
- Emanatism item Aura
- Thorns item Aura
- Pedanticism item Aura

Requirements:

- use existing skill executors;
- triggered children are NoProc where authored;
- shared cooldown/ICD enforced;
- no permanent skill learning;
- item Aura lifecycle follows valid equipment;
- removal/invalidity cleans up the effect;
- no duplicated Aura layers.

======================================================================
15. SPECIAL COMBAT AFFIXES
======================================================================

Individually prove mechanics including:

- Crushing
- Deadly
- Barbed / stored fragment
- Armor Break
- Cull
- Fortifying Hit
- Twin Assault
- Thorns / Retribution
- Kill Burst

Use exact deterministic event sequences.

Test all ICDs, caps and anti-recursion rules.

Example:

Cull:
target after legitimate damaging hit <= authored threshold
→ one valid execution

Boss/PvP/protected target when excluded
→ no execution

Never create duplicate death rewards.

======================================================================
16. UTILITY AFFIXES
======================================================================

Implement and simulate where possible:

- Magic Find
- currency quantity
- durability efficiency
- reduced requirements
- light radius
- pickup radius

For world/native-facing behaviors that cannot be fully represented by a synthetic monster:

test the complete Hywind adapter contract automatically.

Example durability:

synthetic validated durability-loss event
+ deterministic success roll
→ event prevented exactly once

failure roll
→ normal durability loss proceeds

The later connected QA pass will verify the actual Hytale-native event reaches this adapter.

======================================================================
17. ADVANCED STATS CONSISTENCY
======================================================================

Every affix represented in Advanced Stats must use the same authoritative resolved value as gameplay.

Simulator must compare:

authoritative equipment snapshot
vs
AdvancedStatsViewModel

Examples:

- Crit Chance
- Crit Damage
- cooldown recovery
- Magic Find
- attributes
- max resources
- resistances
- elemental modifiers
- status modifiers
- healing/support values
- summon values where displayed

UI projection disagreement is a test failure.

Do not calculate UI values independently.

======================================================================
18. EQUIP / UNEQUIP / INVALIDITY TESTS
======================================================================

Every persistent/stat affix family must test:

baseline
→ equip
→ expected effect appears
→ unequip
→ exact baseline restored

Also test:

- broken item
- unmet requirements
- duplicate identity
- invalid equipment
- backpack-only item
- reconnect/reload snapshot where relevant

Invalid gear must never contribute.

======================================================================
19. PRODUCTION SAFETY
======================================================================

Do not enable an affix in production until its runtime implementation and simulator fixture pass.

During development, preserve the existing capability gate.

At completion:

GearAffixRuntime / generation eligibility must reflect all 160 implemented affixes.

No dead tooltip affix may remain.

No definition may be silently removed to obtain 160/160.

======================================================================
20. QA FIXTURE SUITE FOR LATER OWNER TESTING
======================================================================

After the simulator passes, regenerate deterministic QA gear for later connected owner testing.

Use the existing protected QA provenance.

Create enough QA equipment to cover all 160 affixes while respecting normal gear-family legality.

QA items may exceed normal rarity affix counts where necessary for test coverage.

Prefer approximately 10 affixes per QA item when legal, but create additional items where family/slot restrictions require it.

Create/update commands:

/rpg gear affixqa list
/rpg gear affixqa spawn <fixtureId>
/rpg gear affixqa spawn armor
/rpg gear affixqa spawn weapons
/rpg gear affixqa spawn support
/rpg gear affixqa spawn summons
/rpg gear affixqa spawn all

Each fixture listing must show:

- fixture ID
- base item
- item family/slot
- rarity
- all affix IDs
- affix names
- affix rolls

Owner manual gameplay QA is explicitly AFTER this development stage.

Do not block completion waiting for the owner to manually test all 160.

======================================================================
21. AUTOMATED COVERAGE MANIFEST
======================================================================

Generate a machine-readable matrix for all 160:

affixId
name
prefixOrSuffix
operator
eligibleFamilies
runtimeOwner
simulatorFixture
positiveTest
negativeTest
advancedStatsField if applicable
sentinelBehavior
result

Assertions:

- exactly 160 unique IDs
- zero omitted
- zero duplicate IDs
- every ID has a production runtime owner
- every ID has simulator coverage
- every ID passes

======================================================================
22. REGENERATE THE RUNTIME AUDIT
======================================================================

After implementation, regenerate:

GEAR_AFFIX_RUNTIME_AUDIT.md
GEAR_AFFIX_RUNTIME_AUDIT.csv

Expected final classification:

160 FUNCTIONAL
0 GATED
0 UNSUPPORTED

Do not obtain this result by weakening the audit.

The audit must continue requiring a real effect-bearing runtime consumer.

======================================================================
23. FULL BUILD AND REGRESSION
======================================================================

Run:

- focused affix tests
- complete 160-affix simulator matrix
- existing RPG retained test suite
- passive compatibility tests
- inventory/equipment tests
- persistence tests
- isolated server startup/shutdown smoke test

Do not modify unrelated working behavior to satisfy the affix suite.

======================================================================
24. PERFORMANCE
======================================================================

Affix resolution must not perform expensive catalog scans during every hit/tick.

Prefer:

equipment mutation
→ rebuild immutable resolved snapshot
→ combat reads snapshot

Do not repeatedly parse gear JSON or recompute all equipped affixes per victim.

Proc ledgers must be bounded and cleaned up.

Summon/Aura state must have deterministic lifecycle cleanup.

======================================================================
25. FINAL COMPLETION REPORT
======================================================================

Do not stop after implementing one mechanic family.

Do not stop after the simulator framework exists.

Do not stop after an audit.

Continue until the entire stage is complete.

Final report must include:

- total authored affixes: 160
- runtime-functional: 160
- gated: 0
- unsupported: 0
- simulator fixtures: 160/160 covered
- positive tests passed
- negative/control tests passed
- shared runtime adapters added
- existing adapters reused
- Advanced Stats projection coverage
- Iron Sentinel inheritance coverage
- QA fixture count
- full test-suite result
- isolated server smoke result
- regenerated audit result
- exact JAR/build produced

Do not claim connected gameplay verification.

The owner will perform the later in-game QA pass using the generated QA equipment.

======================================================================
26. HARD ACCEPTANCE CONDITION
======================================================================

This development stage is complete only when:

1. Every existing affix from Master Affix v1.0 is implemented.
2. Every affix affects actual Hywind gameplay state or the intended supported Hytale-facing adapter.
3. Every affix has deterministic positive and negative simulator evidence.
4. The complete automated suite passes.
5. The regenerated audit reports 160 FUNCTIONAL.
6. QA equipment is ready for later manual connected testing.

No GATED status is an acceptable final state for this task.