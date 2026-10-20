# Hywind — shared monster progression implementation brief

**Proposed balance revision:** `monster-progression-v1`  
**Date:** 4 October 2026  
**Scope:** A baseline stat layer inside the existing encounter/difficulty/combat owners. Not a new combat architecture, NPC AI, player character system, or Master Enemies rewrite.  
**Status:** The formulas below are a proposed TEST_BASELINE. Sending this brief to Codex authorizes the explicitly identified changes; it does not establish measured balance or runtime acceptance.

## 1. Authority and preservation

Read the current affected owners and their accepted balance data, not every historical implementation phase. Map this brief to existing classes rather than creating a service for every name used here.

The four supplied sources are:

- **G:** Gear Master v1.2 (26 September): item power, armor, requirements, source-era bands, and immutable gear.
- **A:** Master Affix v1.0 (30 September): typed combat snapshots, damage composition, D01 Defense, penetration, and item-affix integration.
- **R:** The attached RPG Master Implementation identifies itself as **v1.5.2**, not v1.7.0. Its retained attribute formulas and geometry-based accuracy are relevant, but its older world bands and other superseded rules are not a rollback target.
- **ME:** Master Enemies v1.1 (2 October): rarity/minion factors, source snapshots, affinity floors, protections, persistence, and presentation.

**Explicit source reconciliation for this task:**

1. Preserve the current accepted encounter-level resolver. G §10.1 supplies the later three-era bands: Normal 1–40, Nightmare 40–59, Hell 60–99. Do not restore R's old Normal/world-spanning 1–99 layout. Difficulty is a separate input: level 40 alone cannot distinguish Normal from Nightmare.
2. A §7 D01 closes G's earlier missing percentage-to-rating conversion. Use the accepted D01 implementation, not two mitigation paths.
3. Preserve the later approved **additive Stone Skin**: `K_def(L) * [1/4, 1/3, 3/7]` for Normal/Nightmare/Hell. Do not restore the uploaded ME card's old +40/+60/+80% rule or positive-baseline requirement.
4. Preserve the nine simplified affixes already reported deployed. In particular, elemental damage does not reintroduce Chill/Electrified/Poison; Knockback enhances native knockback; ME Armor Breaker is native Physical/Projectile vulnerability; Reflective uses the accepted live receipt path. Do not restore their old ledgers or contracts in this pass.
5. The new positive ordinary-monster Defense baseline deliberately supersedes A §7.2's zero-unarmored baseline **only for newly opted-in hostile encounter profiles**. It does not grant players, pets, civilians, or all native entities hidden Defense.
6. Existing item affixes, requirements, rolls, skill ranks, acquisition, XP, rarity selection, rewards, protection, and save contents remain under their existing owners. Do not change them to make balance tests pass.

Keep the existing native server patch unchanged. Preserve the current working inventory/catalog revision, UI fixes, and QA spawn implementation. Do not reset or downgrade saves or replace newer code with a document-era snapshot.

## 2. Decisions: monster stats, not player characters

**Do not give ordinary enemies STR/DEX/INT/WIS/LUCK allocations, RpgPlayerProgress, player equipment-validity simulation, a skill tree, Magic Find, Fortune Break, or player resource regeneration.** Reuse the shared arithmetic and native stat/damage adapters without instantiating those player systems.

A managed enemy needs a compact combat profile containing:

- Frozen combat level, era, role/variant, existing encounter rank, rarity/pack relationship, and profile revision.
- A native/accepted reference Health value and per-action native damage source, with a defined reference level.
- Resolved Health maximum, one primary damage scale, baseline Defense rating, and six permanent elemental resistance inputs.
- Existing native control/resource/crit capabilities, plus the already implemented affix state when present.

**No new Attack Rating, Accuracy, or Evasion contest.** R §03.1 specifies geometry-based hits; retain explicit Blind/Electrified, block, Fortune, protection, and other accepted exceptions. D01 Defense reduces damage, not hit probability.

**No generic Physical Attack versus Magic Attack attribute pair.** Scale each action's existing channel vector with one progression factor. Preserve its Physical/Wind/Water/Fire/Earth/Lightning/Void composition. Damage channel, attack-versus-spell classification, and power source remain separate concepts. Do not infer them from a name, color, weapon, or school. Existing action-specific overrides remain usable; do not invent new channels or convert native attacks.

**No new Mana/Stamina pools for ordinary monsters.** Leave any actual native pool/action requirement intact. A creature without Mana remains a legitimate no-Mana target. Mana Burn operates on its target's existing spendable Mana; it does not require giving the attacker Mana.

Level alone adds no movement/attack speed, crit chance, status resistance, status penetration, regeneration, or immunity. Existing native capabilities and explicitly authored providers retain their rules.

## 3. Scope and reference baselines

Integrate at the **existing shared combat-encounter stat boundary**, upstream of Master Enemies. Normal-rarity managed enemies must receive the baseline too. Do not put this solely in an ME affix handler or require a Normal enemy to acquire an ME pack.

Apply to registered hostile encounter profiles and explicitly opted-in provokable encounters. Preserve native civilians, ImmersiveNPCs, players, pets, player summons, converted/revived actors, objects, and attack appendages. Existing summon inheritance owns those actors. Authored progression bosses and exceptional encounters keep their explicit final-stat overrides unless their own definition opts into the shared curve.

### Required reference inputs

For each existing role/variant baseline, use:

- `referenceLevel`: the combat level at which the baseline amounts are defined, before era and ME rarity factors.
- `referenceHealth`: unmodified native or accepted authored role Health at that reference level.
- `referenceActionSource`: existing native damage calculators/power inputs, retaining action coefficients, ranges, channels, and timing.
- A source/profile revision and a clear input interpretation: raw reference, already level-resolved, or authored final override.

These are domain responsibilities, not mandatory new SDK fields or duplicate data stores.

**Do not assume every native monster is a level-1 creature.** Multiplying an already high-tier native enemy by the full level-30/60 growth index without a reference would count its built-in progression again.

Reference resolution:

1. Prefer explicit reference metadata already present in the accepted role/difficulty profile.
2. For a raw native baseline without such metadata, use the lowest accepted Normal combat level at which that exact baseline/variant is mapped by the existing encounter registry. This is a new default authoring policy, not a Hytale fact. Resolve once during content preparation, record the result in versioned data, and permit an explicit override.
3. If no Normal profile exists, use that baseline's lowest accepted combat level in the existing authored registry, still treating the raw amounts as pre-era inputs. Report this fallback for tuning. Do not infer an era multiplier already embedded in a number.
4. If the baseline or reference cannot be identified, preserve the prior behavior and report that profile as not migrated. Do not guess level 1, silently claim coverage, or shut down all enemy combat.

If an existing owner already supplies `RoleHealthAtLevel`/`RoleDamageAtLevel`, replace its generic progression calculation with the new curve for opted-in profiles; **do not multiply an already level-scaled output by the new ratio**. Preserve explicit exceptional/final overrides. Read native/reference definitions, never a live actor's previously modified maximum or a victim's received damage, as the baseline.

This avoids full hand-authored sheets. Most profiles need only a reference and STANDARD Defense; current role Health/actions retain species differences. It does not prove that every native baseline is balanced—unusual reference values still need representative gameplay checks.

## 4. Global curves — proposed TEST_BASELINE

Use one versioned configuration through the current balance loader. Extend an existing appropriate file, or add one small `monster-progression-v1.json`; do not duplicate the formulas in several owners.

For integer combat level L in 1..99, linearly interpolate these anchors:

| L | Health growth index G_HP | Baseline managed Physical mitigation p_base | All-element baseline floor R_level |
|---:|---:|---:|---:|
| 1 | 1.00 | 0.05 | 0.00 |
| 10 | 1.80 | 0.10 | 0.03 |
| 20 | 2.70 | 0.15 | 0.06 |
| 30 | 3.80 | 0.20 | 0.09 |
| 40 | 5.00 | 0.25 | 0.12 |
| 59 | 8.50 | 0.30 | 0.16 |
| 79 | 14.00 | 0.35 | 0.20 |
| 99 | 22.00 | 0.40 | 0.25 |

```text
For x_i <= L <= x_(i+1):
    t = (L - x_i) / (x_(i+1) - x_i)
    value(L) = value_i + t * (value_(i+1) - value_i)

G_DMG(L) = 1 + 0.04 * (L - 1)
K_def(L) = 100 + 10 * L                 # retain accepted D01
```

Reject invalid authored levels/nonfinite inputs. The authoritative resolver supplies L; diagnostics must expose invalid data rather than hiding it with an arbitrary substitute.

The new Health and damage growth curves are independently tunable. G §18 has item-power anchors rising from 12 to 155, while R's resource formulas tie player Health to STR/gear rather than an automatic level increment. Consequently, do not force monster Health and outgoing damage to grow at an identical rate. These proposed coefficients are starting values, **not measurements or a claim that all builds already fit them**.

Do not evaluate the current player's gear, level, DPS, or allocation to set enemy stats. Player builds are offline/manual balance fixtures only.

### Existing era factors, applied once

| Era | Health factor E_HP | Primary damage factor E_DMG |
|---|---:|---:|
| Normal | 1.00 | 1.00 |
| Nightmare | 1.80 | 1.35 |
| Hell | 3.00 | 1.80 |

These are the documented starting era factors; preserve a newer explicitly accepted checked-in revision if one exists, recording that difference. Do not add another era Defense multiplier or blanket resistance increment in this task. The global defensive curves use level; existing affinity floors already have an era policy.

## 5. Health and primary attack equations

For a raw role reference with level L_ref:

```text
levelHealthRatio = G_HP(L) / G_HP(L_ref)
levelDamageRatio = G_DMG(L) / G_DMG(L_ref)

roleHealthAtLevel = referenceHealth * levelHealthRatio

MaxHealth = roleHealthAtLevel
          * existingUnappliedEncounterHealthFactor
          * E_HP(era)
          * rarityOrMinionHealthFactor
          * existingNonRarityHealthProviderFactor
```

Use existing composition for actual flat Health providers, if present. Do not turn flat contributions into percentages or stack an already included encounter factor. Default new profile factors are 1.0.

For a primary action/channel c:

```text
S[c] = referenceNativeActionAmount[c]
     * levelDamageRatio
     * existingUnappliedEncounterDamageFactor
     * E_DMG(era)
     * rarityOrMinionDirectFactor
```

`referenceNativeActionAmount` retains the existing action's sampling/coefficient and the accepted native source-modifier order. Insert the new ratio at its current level-power boundary, not by re-running the entire native calculation. S is the existing ME pre-affix source vector; it then enters ME §05.1 unchanged:

```text
B = sum(S[c])
extra[e] = existing elemental-affix fraction * B
preCrit[c] = (S[c] + extra[c])
          * (1 + A_all + (c == PHYSICAL ? A_physical : 0))
```

Then use the current crit, protection, immunity, mitigation, absorption, Health application, and aftermath owners. Do not add a second damage event merely to express level scaling.

Preserve ME rarity/minion factors:

| Rarity/relationship | Health | Direct damage |
|---|---:|---:|
| Normal | 1.00 | 1.00 |
| Champion | 1.60 | 1.05 |
| Unique | 2.40 | 1.10 |
| Super Unique default | 3.00 | 1.15 |
| Existing authored Boss | 1.00 | 1.00 |
| Initial pack minion | 1.15 | 1.00 |

A minion uses its own role/level reference and its Minion row, not the leader's rarity multiplier. Preserve accepted Super Unique overrides. Do not borrow Common/Specialist/Elite/Miniboss/Boss **XP multipliers** as combat multipliers.

**Double-scaling exclusions:** derived reflection, leech, resource debits, fixed percentage-of-Health payloads, costs, environment/cleanup causes, and an already snapshotted periodic/secondary payload do not receive the primary damage ratio again. A primary native periodic package with independent source power must follow its existing source owner once at package creation; never scale every tick again. Existing source-resolved metadata must distinguish these routes.

No new animation, attack cadence, collision, reach, projectile count, crit roll, targeting, or AI behavior is introduced by these equations.

## 6. Physical Defense and affixes

The new baseline is a rating input to D01, not another percentage reduction after damage:

```text
D_level = K_def(L) * p_base(L) / (1 - p_base(L))
D_baseline = defenseProfileMultiplier * D_level

D_flat = D_existingArmorEquivalent
       + D_existingShield
       + D_existingOtherFlat
       + D_baseline
       + D_StoneSkin

D_total = max(0, D_flat * existingGlobalDefenseFactor)
D_effective = D_total * (1 - existingWinningRatingBreakFraction)
p_managed = clamp(D_effective / (K_def(L) + D_effective), 0, 0.60)
```

The existing D01 owner determines which native protection belongs in its invertible armor representation and which effects remain separate. Do not flatten block, scripted immunity, foreign protection, or native vulnerability into D_existingArmorEquivalent.

Optional explicit Defense profiles:

```text
FRAGILE  0.70
STANDARD 1.00     # default
ARMORED  1.30
TANK     1.60
```

Apply this multiplier to D_level **only**, not to Stone Skin, equipment, or every other provider. Do not classify the entire NPC catalog now or infer profiles from appearance/name. Defaults plus existing explicit overrides suffice.

Stone Skin retains the approved additive `K_def(L) * [1/4, 1/3, 3/7]`. Item Riveting and other actual Defense-rating breaks continue through D_effective. **The simplified ME-025 Armor Breaker is different:** it retains its native Physical/Projectile vulnerability effect and does not become another rating-break factor.

Physical delivery and Projectile aliases share their existing Physical mitigation semantics; a physical arrow must not take both reductions. The denominator uses the defender's level, not the attacker's, and not XP's unrelated K(L).

The 40% level-99 baseline intentionally leaves space beneath the 60% managed cap for Stone Skin and other sources. Do not consume ME's incremental affix caps with the global baseline. Retain no-op eligibility checks where native protection or other providers already cap an affix's benefit.

## 7. Six elemental resistances: floors first, additions second

Retain PHYSICAL separately and use WIND, WATER, FIRE, EARTH, LIGHTNING, VOID. Do not create generic Magic/Cold/Poison resistance channels.

For each elemental channel e:

```text
permanentBase[e] = max(existingPermanentNativeOrAuthoredResistance[e],
                       R_level(L),
                       applicableExistingAffinityFloor[e, era])

rawResistance[e] = permanentBase[e]
                 + existingAdditiveAffixAndProviderContributions[e]

if explicitlyImmune(e):
    reject that channel through the existing immunity owner
else:
    cappedResistance[e] = clamp(rawResistance[e], 0, 0.75)
    hitResistance[e] = max(0, cappedResistance[e] - eligibleHitPenetration[e])
    damage[e] *= (1 - hitResistance[e])             # one owner only
```

This introduces R_level as a **minimum permanent baseline**, not another addition to an already resistant creature. ME §09.4 affinity values are already floors (documented starting values .20/.40/.60), not +20/+40/+60 percentage-point bonuses. Do not add both a new era resistance bonus and the same affinity-era floor.

Temporary effects remain under their current composition/expiry semantics. In particular, do not put an already debuffed or fully affix-resolved resistance inside `max(...)` and thereby erase the debuff or count the affix twice. Add this floor at the existing permanent-baseline input. Preserve separately handled native vulnerability behavior rather than changing it with a generic clamp.

Penetration is subtracted **after** the 75% ordinary cap, floors at zero, and does not remove immunity. Existing DoT penetration eligibility and status resistance remain separate. This pass does not grant new random immunities to ordinary monsters merely because the global baseline exists; only the already authorized immunity/encounter owner can do that. Preserve boss-specific affinity overrides.

## 8. Lifecycle, caching, and high-speed traversal

This layer must be cheap for ordinary enemies, not just special packs.

- Precompute the 99 entries of G_HP, G_DMG, p_base, R_level, and K_def once per validated balance revision. Load/resolve native role references and affinities once through existing registries. Do not parse these documents or gear catalogs at runtime.
- At genuine birth, resolve the existing encounter level/era, perform bounded lookups/arithmetic, and attach one immutable baseline projection before the actor can interact. Do not run an asset scan or player-sheet compiler per entity.
- Reuse native maximum-Health projection and damage metadata. UI rendering or an entity becoming visible to another player must not trigger a new spawn/roll/stat initialization.
- Ordinary enemies do not gain ME pack transactions, per-hit disk writes, or full ME runtime records just to receive these baselines. Extend their current encounter/native persistence references with the minimum revision/reference data.
- Cache static base values. Recompose only the affected stats when a real provider changes. Native per-action damage sampling and impact-time defenses retain their own timing; there is no new every-tick all-monster stat calculation.
- On reload, restore the same frozen baseline/profile and existing Health state. Never derive the baseline from modified MaxHealth, reroll a reference level, or apply a second multiplier.
- Newly created full-Health actors may initialize against their final maximum once. Existing wounded actors do not refill on provider refresh, configuration reload, UI access, or rebind. Preserve native authored wounded-spawn behavior where applicable.
- Publish new configuration only for future births; existing actors retain their pinned revision by default. Keep prior referenced profile revisions available. No automatic live rescale/migration in this pass.
- Pure preparation may run off-thread. Native entity mutation stays on the owning world thread/command buffer. Do not add synchronous file/database writes, waiting futures, per-NPC threads, or network calls to spawn/combat callbacks.
- Do not enlarge spawn/pack counts, simulation range, or visibility range. Coalesce read-only UI updates through the existing compositor. A cache must not retain dead entity handles or grow with every unique affix permutation.

More numeric fields alone are not the important lag risk; repeated setup, allocations, synchronous IO, extra actors, and unbounded callbacks are. No claim of zero overhead is permitted without measurement.

## 9. Bounded verification and gameplay handoff

Do not repeat the entire 160-item-affix or 27-monster-affix implementation program. Run one focused parameterized set over the changed shared owner, update only directly affected expected values, and rerun failures after fixes. Preserve relevant save/boot checks; compiling alone does not validate those boundaries.

Required numeric checks:

| Check | Expected result |
|---|---|
| L=30 STANDARD, no existing Defense | K_def=400; D_baseline=100; managed mitigation=20% |
| Above + Normal Stone Skin | +100 rating; total 200; mitigation=33.3333% |
| Above + admitted 15% item Riveting | effective rating 170; mitigation=29.8246%, not a 15-point subtraction |
| L=99 STANDARD + Hell Stone Skin, no other sources | D_baseline=726.6667; Stone Skin=467.1429; mitigation=52.2727% |
| L=99 TANK + Hell Stone Skin, no other sources | mitigation=59.9237%, below the 60% managed cap |
| L=30 non-affinity + Normal Magic Resistant | .09 + .15 = .24 elemental resistance |
| L=30 Fire affinity + Fire Enchanted | max(.09,.20) + .20 = .40 Fire resistance |
| Raw resistance .95 and hit penetration .10 | .75 cap then .10 penetration -> .65 mitigation; 100 damage ->35 |
| Same target, identical incoming packet/penetration but attacker level 2 vs30 | Same damage; no invented level-difference damage penalty |
| L equals referenceLevel | Both level growth ratios exactly 1; existing era/rarity factors still apply once |
| Hypothetical H_ref=100, D_ref=10, L_ref=1; Normal L=30 | Health380, primary damage21.6 before defenses/affixes |
| Same hypothetical role as Unique | Health912, source direct damage23.76; no extra rank multiplier |

Also cover no channel invention, seven-channel one-pass damage, no double scaling of reflection/periodic children, reserved resources untouched, zero-affix ordinary enemies, current ME providers, idempotent rebind, wounded Health preservation, legacy profile retention, and explicit boss/pet/civilian exclusions.

**Balance evidence, not merely arithmetic:** compare real legal low/mid/high-level loadouts: STR melee, DEX ranged, item-powered caster, innate-power caster, and summoner. G §09.3 requires complete-loadout comparisons; R's inherited innate base-20 rule is not equivalent to G's item-power progression. Do not silently make an innate skill read a weapon or add player level scaling. If a current innate route still has that old fixed base, report its measured mismatch as a separate player-skill balance issue.

Record same-level ordinary-enemy time-to-kill, damage received per landed action, and the level-2 versus level-30 case. No final TTK guarantee can be inferred from reference power alone: native cadence, hit count, player investment, resource uptime, armor, and affixes all matter. A skilled underleveled player may still succeed; this task creates stat progression, not a hard access prohibition.

**Traversal measurement:** use existing profiling/trace tools, not a new telemetry framework. Compare baseline versus candidate on the same copied starting state, route, speed, view settings, and approximate entity counts. Test both already generated terrain and separate fresh-generation copies so the second run does not receive an unfair chunk-cache advantage. Record server tick p95/p99, stat setup/recomposition time, new/loaded actors per second, allocation/GC, save/work queue depth, and client frame time separately. Confirm queues and entity-linked memory settle after leaving the area. Use the project's existing performance targets as targets, not claimed results.

## 10. Delivery and stop condition

Deliver this focused baseline integration, its one versioned data source, and a concise changed-owner/coverage report. Extend the existing read-only enemy/difficulty inspection output to show: profile revision, level/reference level/era, reference versus final Health, level and era factors, per-action damage scale, baseline/total/effective Defense, managed mitigation, resistance floors/providers/capped values, and explicit immunity sources. This must work for ordinary managed enemies too; do not invent dummy affixes for inspection.

Use the existing QA spawn command where supported. A level selector for controlled tests may be added only as a backward-compatible optional QA argument using the existing level resolver; retain QA provenance and zero economic rewards. Report actual command syntax rather than claiming an unimplemented spelling exists.

Build and package through the existing pipeline. Deploy only under the owner's existing authorization, with the game closed and verified backups; never overwrite a running server. Report the JAR/hash, unchanged required native patch, migrated versus excluded profiles, affected tests, and exact manual boot/join and gameplay checks.

Stop after this scoped implementation and handoff. Do not add new NPC moves, universal resource/attribute systems, a new spawn framework, affix redesigns, or a whole-catalog balancing project. If one actual native route cannot consume the shared scale, report that specific gap and preserve its prior behavior; do not fake coverage or expand the native patch.

## Source notes

Source-derived mechanics above: G §§04,05.1,09,10.1,18; A §§2.4,4,7,8,10; R §§03,03.1,04.1,04.4,05,12.3; ME §§01.2,02.2,03.3,05.1,09.2–09.4,12,17. Later Stone Skin and nine-affix revisions are from the owner's subsequent conversation and pasted Codex reports, not the older ME attachment.

New decisions in this brief: monster-only baseline projection without player attributes; reference-level fallback policy; G_HP/G_DMG and defensive anchor curves; optional Defense profiles applying only to the baseline; R_level as a permanent resistance floor; ordinary-encounter integration scope and the bounded implementation handoff.

External performance checks consulted (not substitutes for the pinned local runtime): Hytale Server Manual (entity counts drive CPU use, loaded area drives memory); Hytale Generator Performance Optimization (same-seed/location comparisons and cold-context costs); official CommandBuffer API (thread ownership). These support the profiling plan, not any promised FPS or server capacity.
