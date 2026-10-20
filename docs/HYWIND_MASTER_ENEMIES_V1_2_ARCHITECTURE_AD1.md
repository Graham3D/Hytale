# Hywind | Master Enemies v1.2
## Architecture AD1: Tiered Unique Identities and Threat-Based Rewards

**Document revision:** `ME12-ARCH-AD1`  
**Status:** Architecture design delivered for owner review.  
**Authority:** The six product decisions in §1.1 are owner-approved. The additional choices in this document are the designer's selected implementation defaults, made under the request to complete the architecture. They are not claims of existing behavior, completed tests, deployment approval, or owner acceptance of a release.  
**Execution boundary:** This document does not authorize repository changes, native patching, builds, deployment, branch reconciliation, save mutation, or live testing. Those require the owner's separate engineering go-ahead.  
**Release status:** Monster Affix remains **OPEN**. Architecture readiness is not implementation or gameplay acceptance.  
**Evidence basis:** The supplied engineering specification, two Codex read-only reviews, and `Master_Enemies_v1.1(2).md`. Source-state statements below describe those reviews, not a newly verified checkout or installation.

---

## Contents

1. Product contract and design decisions
2. Baseline, dependencies, and scope
3. Domain model and ownership boundaries
4. Starting name and affix content
5. Exact probability contract
6. Feasibility and native integration
7. Bounded exact sampler
8. Versioned data and configuration
9. Birth planning and publication
10. Threat, experience, equipment, and learning
11. Persistence, migration, and rollback
12. Presentation and inspection
13. Performance and observability
14. Implementation phases
15. Automated acceptance gates
16. Connected QA and release acceptance
17. Risks and explicit non-goals
18. Final handoff and authorization boundary
19. Source and evidence register

---

## 1. Product contract and design decisions

### 1.1 The six approved requirements

| ID | Approved requirement | Binding interpretation |
|---|---|---|
| A1 | Each name component has Tier I/II/III target frequencies of 70%/25%/5%. | Roll tier mass independently of how many names or legal affix completions a tier contains. Exact independent marginals apply when all tier pairs are feasible. Otherwise use the explicitly conditioned joint distribution in §5. |
| A2 | Maximum eight affixes: prefix tier + suffix tier + difficulty bonus. Champions remain at one. | New procedural Uniques have exactly `TP + TS + D` distinct own affixes; `D = 0/1/2` in Normal/Nightmare/Hell. No clipping a selected budget to a lower number. |
| A3 | Each component guarantees a compatible thematic signature. | One fixed signature per name entry, included in that component's budget. A pair needing the same affix twice is ineligible. |
| A4 | Weighted selection, not cascading; preserve equipment rarity and Magic Find distributions. | Change monster identity selection and eligible equipment quantity only. Do not change the gear quality algorithm, quality weights, category/base selection, or Magic Find ownership. |
| A5 | Bounded threat-based XP, eligible equipment opportunities, and learning. | Use one frozen reward policy for the new procedural Unique. Reuse the existing XP, gear, and acquisition transaction owners. |
| A6 | Authored Super Uniques stay separately controlled. | Toepecker and other authored encounters do not automatically roll procedural tiers or inherit this new reward policy. |

### 1.2 Selected AD1 defaults

These choices resolve the open architectural alternatives. They are the proposed basis for the eventual implementation authorization; no additional generic investigation round is required simply to restate them.

| Decision | Selected design |
|---|---|
| Name content | Adopt the latest Codex review's 16-name mapping and thematic filler lists in §4. Use equal positive name weights within a tier. |
| Generic difficulty pool | All 27 existing affix IDs, subject to every current rarity, capability, group, roster, and immunity rule. Keep current per-difficulty weights. This deliberately permits eligible leader modifiers in difficulty slots instead of making them globally unreachable. |
| Outcome being weighted | Each distinct affix-ID/selector set, once. Valid slot assignments do not multiply its probability. Assign deterministic ownership after selection. |
| Threat calibration | Adopt the latest reviewed four-band operator assignment in §4.4 as `me12-threat-ad1`. It is an initial calibration, not a measured ranking of every encounter. |
| XP and equipment formulas | Adopt the exact bounded formulas in §10, with expected equipment count capped at 16 before deterministic rounding. |
| Learning formula | Keep the existing rarity-capped chance, then add a separate bonus of at most **three percentage points**, calculated from that rarity-adjusted chance. Preserve the 95% ordinary cap and separate pity guarantee. |
| Learning readiness | Design a shared typed first-acquisition boundary for Skills and first-copy Passives. Do not enable uncertified sources. Full v1.2 gameplay acceptance requires a real accepted source for each promised ability kind. |
| Native immunity policy | Preserve the current Normal-difficulty promotion exclusions. Do not remove native immunity or reinterpret its budget in this update. List the exclusions explicitly; relaxing them is separate work. |
| Minimum native coverage | Every intended, non-policy-excluded procedural role must support at least one complete v2 plan for its applicable difficulty. Hell requires at least four. Restricted roles are certification work, not candidates for silent removal. |
| Migration | Dual readers/evaluators first; write v2 only for newly authorized v2 births. Keep old bytes and receipt identities compatible. |
| Rollback | Disable new v2 births while retaining v2-capable readers and evaluators. Do not install an old incompatible JAR against a save containing v2 history. |
| QA destination | Offline tests on copied data; connected owner QA in the authorized main Hywind save. No new playable QA world or standalone server without separate permission. |

**Important tradeoff:** Keeping the existing Normal immunity policy means some naturally immune creatures cannot become procedural Elites in Normal. That is an explicit inherited policy limitation, not a solved lava-biome coverage claim. AD1 records it rather than silently changing it.

### 1.3 Player-facing behavior

A player can recognize a name's guaranteed signatures, but not every filler affix. A Tier III name means more modifier slots, not a stronger numerical version of its signature. The same creature type can produce multiple legal combinations, and the rarer combinations generally offer greater rewards up to declared caps.

Example under AD1:

- **Ruinbone** is Tier III: Stone Skin plus two compatible prefix fillers.
- **the Unbroken** is Tier III: Unwavering plus two compatible suffix fillers.
- In Hell, two more affixes come from the generic difficulty pool.
- The result has eight distinct own affixes, not eight affixes plus two signatures.

One legal structural example is Stone Skin, Unstoppable, Reflective, Unwavering, Extra Strong, Armor Breaker, Lightning Enchanted, and Cursed. Whether a real native role can execute it still requires that role's complete certification and birth checks. [R2: example and coverage]

### 1.4 Scope is not every use of the word Elite

| Actor/context | v1.2 identity behavior | v1.2 threat reward behavior |
|---|---|---|
| Newly created, eligible natural procedural Unique | New tiered composite identity, 2–8 own affixes | New frozen threat policy |
| Existing saved Unique | Original name, affixes, parameters, and evaluator | Original reward policy |
| Champion | One own affix; peer Champion group; no leader/minions | Preserve current economics; no implicit threat-policy conversion |
| Normal actor | Zero own affixes | Preserve current economics |
| Initial minion of a v2 Unique | Normal rarity; zero own affixes; legal inherited effects only | Its own existing minion economics, never leader threat bonuses |
| Authored Super Unique | Existing authored name, template, additions, and cap | Existing authored/legacy policy unless separately versioned later |
| Authored Boss | Existing campaign rules | Existing rank/reward rules |
| QA actor, combat summon, revived or player-owned actor | Appropriate existing identity rules | No production economic eligibility |

This update must not turn a Super Unique into a Boss, change `encounterRank` to manufacture better loot, or allow minions to roll the leader's name tiers.

---

## 2. Baseline, dependencies, and scope

### 2.1 Reviewed state, not a chosen implementation baseline

The latest supplied review reports:

- Integrated remote `main`: `b839526e7106f780648c315692b04bb372b58d9e`.
- Newer Elite candidate: `3327a5650cadf0173b336072f614a13c63cecc56`, with unpublished local commits and divergent ancestry relative to `main`.
- HRO corrections: `f7d414e6348c84653d03a51117bdfe2fc427dded`.
- Installed candidate: `R262C1ELITE4-CANDIDATE`, embedded source `e491c692…`.
- Installed Hytale/server: 0.7.1, while the reviewed/deployed target is 0.7.0.
- No cumulatively accepted implementation baseline is established by those records. [R2: source/dependency status]

Do not select the highest revision number as a substitute for accepted integration. At future P0, record actual branch, HEAD, dirty state, dependencies, server/assets/JAR hashes, command registration, and owner acceptance evidence. Preserve local artwork, icons, scripts, and policy changes. Do not reset, force-push, or blindly merge divergent branches.

### 2.2 Dependency sequence

1. Restore native compatibility on the installed target using actual asset/API certification.
2. Reconcile the explicitly selected Elite/HRO fixes in a clean working branch, preserving unpublished work.
3. Pass applicable existing automated and connected baseline gates.
4. Record the exact accepted cumulative baseline SHA and installed artifacts.
5. Branch the implementation of this architecture from that baseline.

Pure design and isolated algorithm work can be prepared before these complete. Native integration, deployment, and release acceptance cannot bypass them. This document does not authorize any of these steps to execute now.

### 2.3 What is deliberately excluded

No new affix operators; no Golden; no Toepecker/Gravebrood encounter; no Revive implementation; no Champion 5× gold change; no new spawn-density tuning; no new Set/Legendary pipeline; no campaign portal changes; no automatic broadening of Boss or Super Unique affix caps; no blanket rewrite of combat, HUD, persistence workers, or world admission.

Role certification needed for already-intended procedural coverage is an explicit prerequisite work package, not permission to invent new attacks or copy another role's certificate.

---

## 3. Domain model and ownership boundaries

### 3.1 Keep these concepts separate

`canonicalRoleId`, native role/alias, `encounterRank`, `enemyRarity`, `packRole`, economic origin, name-component tier, individual affix selection weight, threat value, ability acquisition rarity, and equipment quality are different domains.

The data model must not overload one field to carry another. In particular:

- Name tier contributes slots; it does not alter level or rarity stat multipliers.
- Affix weight controls selection; it does not automatically determine threat.
- Affix ownership identifies prefix/signature/filler/difficulty provenance; it does not grant an extra copy of an effect.
- A displayed immunity, aura sublabel, guard count, shield, or status is not another reward-counting affix.
- Species/source identity remains unchanged when a name is generated.

### 3.2 Responsibility map

Names below describe responsibilities. Extend an existing matching owner instead of creating parallel services solely to match a class name.

| Responsibility | Existing integration anchor | New responsibility |
|---|---|---|
| Policy loading | Existing asset/config owners; `EnemyNamePools`, `EnemyAffixRegistry`, `EnemyBalance` | Strict versioned immutable name/selection/reward snapshots |
| Feasibility and selection | `EnemyAffixSelection`, `EnemyEliteRoleMatrix`, native bindings | Version-dispatched v2 compiler and exact sampler |
| Birth planning | `EnemyBirthPlanner` | Select complete v2 identity and freeze it into the existing plan |
| Native population/publication | Reservation, attachment, publication, compensation, world admission owners | Consume the plan; preserve native accounting and exactly one publication |
| Combat projection | `EnemyAffixSnapshot`, existing damage/status/resource/pack owners | Apply a certified larger frozen set through existing primitives |
| Persistence | `EnemyDescriptor`, `EnemyBirthPlan`, `EnemyBirthRoot`, `FileEncounterStore`, checkpoint/journal owners | Strict nested version dispatch and preserved v1 codecs |
| Reward computation | `EnemyRewardContext`, `HytaleEncounterRewards` | Frozen v2 threat factors with one XP/quantity/acquisition path |
| Durable reward delivery | `DurableEncounterEffects`, `FileEarnedRewardStore`, `GearLootService` | Same entitlement/receipt authority; recoverable version holds |
| First acquisition | `LearningSources`, `RpgLoadoutService`, acquisition checkpoints | Typed Skill/first-copy Passive adapter with existing transaction semantics |
| Presentation | `EnemyDisplayDto`, `MonsterAffixLabel`, health-bar presentation, existing target HUD | Full 8-affix information and stable single name authority |
| Operations | Existing world config, `/rpg enemies` and trace/HRO tools | Feature activation, inspection and bounded diagnostics |

**No second death listener, Health store, reward ledger, learning roll loop, or independently writable name sidecar.**

### 3.3 Two orthogonal kinds of versioning

- Storage schema describes serialization.
- Identity/selection/reward policy revisions describe behavior.

A record can require a v2-capable codec while still using legacy economics, as with an initial minion carrying inherited effects from a v2 leader. Master Enemies v1.2 is also not the same version domain as the existing encounter journal's own “v2” format.

---

## 4. Starting name and affix content

### 4.1 Name registry selected for AD1

Use stable lowercase IDs independently of display text. Every entry below has name weight **1** within its side and tier. Tier mass is not a sum of name counts.

| Side | ID | Display | Tier | Signature |
|---|---|---|---:|---|
| Prefix | `ashclaw` | Ashclaw | 1 | ME-005 Fire Enchanted |
| Prefix | `grimtooth` | Grimtooth | 1 | ME-002 Extra Strong |
| Prefix | `ironhide` | Ironhide | 1 | ME-004 Stone Skin |
| Prefix | `gorefang` | Gorefang | 2 | ME-002 Extra Strong |
| Prefix | `duskmaw` | Duskmaw | 2 | ME-014 Cursed |
| Prefix | `stormscar` | Stormscar | 2 | ME-007 Lightning Enchanted |
| Prefix | `dreadtusk` | Dreadtusk | 2 | ME-025 Armor Breaker |
| Prefix | `ruinbone` | Ruinbone | 3 | ME-004 Stone Skin |
| Suffix | `cruel` | the Cruel | 1 | ME-002 Extra Strong |
| Suffix | `stubborn` | the Stubborn | 1 | ME-017 Unwavering |
| Suffix | `hollow` | the Hollow | 1 | ME-013 Mana Burn |
| Suffix | `ashen` | the Ashen | 1 | ME-005 Fire Enchanted |
| Suffix | `scarred` | the Scarred | 1 | ME-026 Reflective |
| Suffix | `relentless` | the Relentless | 2 | ME-018 Unstoppable |
| Suffix | `watchful` | the Watchful | 2 | ME-003 Magic Resistant |
| Suffix | `unbroken` | the Unbroken | 3 | ME-017 Unwavering |

This is the latest reviewed mapping, selected instead of mixing earlier incompatible tables. Ruinbone's theme is hardened/petrified bone; Unbroken's is control resistance. Repeated signatures across different names are permitted. Duplicate signatures in one selected name pair are not. [R2: revised name mapping]

This catalog gives only one Tier III name per side initially. That is a content-variety limitation, not a reason to force new affix mechanics. More names can be authored later without changing tier mass.

### 4.2 Thematic filler lists

A Tier I component has no filler slots. A Tier II component has one; Tier III has two. Lists are membership filters, not owner-specific weight multipliers.

| Component | Allowed filler IDs |
|---|---|
| Gorefang | ME-001, ME-004, ME-015, ME-017, ME-018, ME-019, ME-025 |
| Duskmaw | ME-001, ME-011, ME-012, ME-017, ME-018, ME-025, ME-026 |
| Stormscar | ME-001, ME-002, ME-017, ME-018, ME-025, ME-026 |
| Dreadtusk | ME-002, ME-004, ME-010, ME-015, ME-017, ME-018, ME-026 |
| Ruinbone | ME-002, ME-003, ME-017, ME-018, ME-026, ME-027 |
| the Relentless | ME-001, ME-002, ME-017, ME-019, ME-025 |
| the Watchful | ME-001, ME-002, ME-017, ME-018, ME-025, ME-026 |
| the Unbroken | ME-001, ME-002, ME-003, ME-004, ME-018, ME-019, ME-025, ME-026, ME-027 |

An allowed candidate can still be excluded by the whole set. For example, Ruinbone already requires Stone Skin, so Magic Resistant and Bulwark cannot accompany it under the existing one-survival-affix rule. Such entries are not permission to override that rule. They should be reported as signature-conflicting content during linting; they contribute no selection mass.

Retain the reviewed lists as source authoring, but compile their effective membership after signature locks. A lint report should expose permanently dead entries so a later content-only cleanup can remove them without altering probabilities.

### 4.3 Generic difficulty pool

AD1 uses ME-001 through ME-027 as the candidate allowlist. Selection weights come from the accepted per-difficulty affix registry and permitted operator-config overrides captured in the policy snapshot.

No bonus immunity, selector, inherited effect, or unnamed stat buff can fill a slot. An Aura selector is one alternative for the Aura affix, not a second affix.

Leader-affix requirements remain binding. Horde's extra **initial** minions must fit the native plan, inheritance restrictions, and the existing maximum initial pack size. Packbound needs a valid finite, reachable guard roster. A difficulty slot does not conjure additional capacity or turn a Champion into a leader.

**Catalog consequence:** In Normal there are no generic difficulty slots. With this small name catalog, some modifiers are reachable only in Nightmare/Hell or existing authored/legacy encounters. Preserve and publish a per-operator/per-era reachability report. Do not promise unchanged individual-affix frequencies or equal access to all 27 affixes in every era.

### 4.4 Threat policy selected for AD1

Store threat as integer hundredths to avoid ambiguous decimal scoring. Keep operator selection weights unchanged; scarcity labels do not alter these values.

| Threat units | Threat value | Operators |
|---:|---:|---|
| 100 | 1.00 | ME-002 Extra Strong; ME-003 Magic Resistant; ME-004 Stone Skin; ME-017 Unwavering |
| 115 | 1.15 | ME-001 Extra Fast; ME-005 Fire Enchanted; ME-009 Wind Enchanted; ME-010 Earth Enchanted; ME-012 Spectral Hit; ME-013 Mana Burn; ME-018 Unstoppable; ME-025 Armor Breaker |
| 130 | 1.30 | ME-006 Cold Enchanted; ME-007 Lightning Enchanted; ME-008 Poison Enchanted; ME-011 Void Enchanted; ME-014 Cursed; ME-015 Knockback; ME-019 Frenzied; ME-021 Empowered Minions; ME-023 Aura Enchanted; ME-027 Bulwark |
| 150 | 1.50 | ME-016 Vampiric; ME-020 Avenger; ME-022 Horde; ME-024 Packbound; ME-026 Reflective |

These 27 assignments come from the latest review and are initial design estimates. They are not a claim that Reflective always creates more real danger than Extra Fast. Role/build context, diminishing returns from aggregate caps, and pack economics must be measured during balancing. [R2: threat calibration]

In AD1, Aura selectors share the parent operator's threat score. Guard counts, Frenzy/Avenger stacks, spent shields, and the number of currently alive minions do not recalculate reward value mid-fight.

### 4.5 Scarcity metadata

Do not invent a new scarcity multiplier to double-weight affixes. Preserve current numerical per-era weights. An optional authored `scarcityLabel` may be added as informational metadata, initially unset unless already approved. A name's guaranteed signature can make an otherwise low-weight affix common in that name's outcomes; expose actual measured marginal frequency rather than labeling weight as a final probability.

---

## 5. Exact probability contract

### 5.1 Affix count

For new procedural Uniques:

`A = TP + TS + D`, with `TP,TS in {1,2,3}`, `D in {0,1,2}`, and `2 <= A <= 8`.

| Pair | Normal | Nightmare | Hell |
|---|---:|---:|---:|
| 1/1 | 2 | 3 | 4 |
| 1/2 or 2/1 | 3 | 4 | 5 |
| 1/3, 2/2, or 3/1 | 4 | 5 | 6 |
| 2/3 or 3/2 | 5 | 6 | 7 |
| 3/3 | 6 | 7 | 8 |

The two signatures are part of `A`. There is no separate post-selection addition beyond `D`.

### 5.2 Stage one: feasible tier pair

Let `q = (70,25,5)` and `Iij = 1` exactly when tier pair `(i,j)` has at least one actual realizable name pair and complete affix/roster plan for the captured context.

`P(i,j | context) = qi*qj*Iij / sum_ab(qa*qb*Iab)`.

Use a single weighted joint tier-pair draw. This is mathematically identical to independent tier draws conditioned on feasibility, but avoids failed-draw retries and hidden downgrades. Do not independently renormalize each side when feasibility couples the two sides.

| Fully feasible pair | Unnormalized mass | Final probability |
|---|---:|---:|
| 1/1 | 4900 | 49% |
| 1/2 | 1750 | 17.5% |
| 1/3 | 350 | 3.5% |
| 2/1 | 1750 | 17.5% |
| 2/2 | 625 | 6.25% |
| 2/3 | 125 | 1.25% |
| 3/1 | 350 | 3.5% |
| 3/2 | 125 | 1.25% |
| 3/3 | 25 | 0.25% |

For a fully feasible role, pre-difficulty affix counts 2/3/4/5/6 occur with 49%/35%/13.25%/2.5%/0.25%. Expected counts are 2.7, 3.7, and 4.7 in Normal, Nightmare, and Hell. These are conditional on reaching the procedural Unique planner, not percentages of all native spawn attempts.

### 5.3 Stage two: name pair

Within the selected tier pair, select a feasible `(prefix,suffix)` with weight `prefix.nameWeight * suffix.nameWeight`. All AD1 name weights are 1.

A name pair receives one unit of feasibility regardless of whether it has one legal affix completion or ten thousand. Do not favor names by total completion mass.

Adding names within a tier leaves tier mass unchanged **provided the feasible-tier mask stays the same**. Adding the first feasible name for a previously impossible tier pair legitimately changes that mask and the conditioned probabilities. Test and document this distinction.

### 5.4 Stage three: distinct affix set

For a fixed name pair and native context, let `F` be the complete legal distinct affix-ID/selector sets that:

- contain both signatures;
- have exactly `A` affixes;
- admit a valid assignment to the residual prefix/suffix/difficulty slots;
- satisfy native capabilities, group/conflict rules, and the complete roster/immunity finalizer.

For each set `S`:

`W(S) = product_i(wi(difficulty) * selectorWeight_i_if_applicable)`.

`P(S | names,context) = W(S) / sum_{T in F} W(T)`.

Fixed signature weights are constant within that name-pair distribution and may be factored out. A disabled or zero-effective-weight affix is not eligible even as a signature. Never count two ownership assignments of the same set as two outcomes.

### 5.5 Stage four: canonical ownership

After drawing the set, assign the two signature affixes to their declared owners. Match all other selected affixes to exactly `TP-1` prefix, `TS-1` suffix, and `D` generic slots.

Choose the lexicographically first valid assignment under:

1. stable affix ID, then stable selector ID;
2. owner order `PREFIX_FILLER`, `SUFFIX_FILLER`, `DIFFICULTY`.

Choose an owner only when the remaining entries still have a complete legal matching. This is a small deterministic matching problem, not another random roll. Ownership is explanatory provenance; it does not change an affix's numerical weight, parameters, reward score, or effect in AD1.

### 5.6 Probability invariants

No cascade, no “keep rolling until something fits,” no bias from count of names in another tier, no bias from completion multiplicity, and no multiplication of gear Magic Find by monster tier. Selection policy changes need a new revision even when their aggregate frequencies happen to match.

---

## 6. Feasibility and native integration

### 6.1 Coverage is more than a count of registered affixes

The review identifies a three-affix structural ceiling for Scout/restricted projectile and mixed-action families: they admit Extra Strong, Magic Resistant, Stone Skin, and Unwavering, but the two survival modifiers cannot coexist. Such a role cannot meet Hell's four-affix minimum. Changing an integer cap does not solve it. [R2: legal coverage]

| Reported family | Reviewed limit/status | Required treatment |
|---|---|---|
| Skeleton Scout and applicable aliases | 3 | Certify additional actual routes before Hell v2 release coverage |
| Eye Void and listed ranged Skeleton families | 3 | Same; do not assume projectile elemental support |
| Outlander Berserker/Hunter | 3 | Certify all reachable melee/mixed actions and their intersection |
| Spider/Snapdragon | 5 | Preserve explicit conditional high-tier ineligibility; expand genuine native routes where supported |
| Firesteel Golem | 3 plus Normal immunity policy restriction | Separate missing action coverage from policy exclusion |
| Broad melee/predator families and reviewed Larva/Trork cases | At least 8 structurally | Prove actual installed-native execution, roster, and state behavior |
| Goblin Turret/Scarak Seeker | No certified production route in the review | Remain explicit prerequisite blockers, not silently removed rows |
| Authored campaign golems | Existing campaign exclusions | Do not force procedural coverage onto them |

These are reviewed structural findings, not 0.7.1 test results.

### 6.2 Certification work packages

Follow the narrower routes proposed by Codex:

- For restricted roles, first test Reflective using incoming **applied-Health** receipts. That may supply a fourth legal affix without rewriting projectile damage.
- Certify movement controllers before enabling Extra Fast or Unstoppable; seeing an NPC move is not an API certificate.
- Keep projectile elemental modifiers blocked until the multi-channel damage path is certified. Preserve original single-Projectile-channel assumptions where still required.
- Cover every reachable Outlander and Firesteel action root, including conditional alternatives and damage variables.
- For Spider/Snapdragon, prove selected damage alternatives do not both apply.
- Goblin Turret/Scarak Seeker require certification of their actual projectile-creation seam, impact, ownership, removal, and recovery. Preserve Scarak's native Antidote/poison conditions.

No new mechanics are necessary merely to fill a tier. Unsupported behavior stays unsupported until proved. Missing minimum coverage is a release blocker.

### 6.3 Explicit Normal immunity boundary

Retain the existing native-immunity promotion policy for this release. The review lists Firesteel Golem, Slug Magma, Toad Rhino Magma, Burnt Lancer, Burnt Soldier, Incandescent Footman, Burnt Archer, Burnt Gunner, Incandescent Mage, Burnt Praetorian, and applicable aliases as affected. Validate exact IDs against the selected native baseline. [R2: immunity-policy conflict]

This policy leaves the original creature/encounter intact; it does not delete its immunity or make it unspawnable. The v1.1 document explicitly says preserved native flags consume the applicable safety budget and an over-budget new promotion is rejected. [V11 §09.3]

Record these as `POLICY_EXCLUDED_NORMAL_NATIVE_IMMUNITY`, not `UNSUPPORTED_AFFIX`, not a silent success, and not a newly approved global exclusion. The exact preserved-exception manifest is part of owner release review. Broadening it or changing its semantics requires separate approval.

### 6.4 Complete immutable feasibility context

Capture through existing world-thread owners:

- canonical/native role and binding revision;
- difficulty, source/profile revision, source level and economic origin;
- native immunity/control baseline;
- incoming native group identity and original members;
- deterministic base initial-roster demand under the existing planner;
- candidate slots and their inheritance/action compatibility;
- a bounded native capacity/placement preflight certificate, where the existing owner supplies one;
- immutable policy digests and world-lifetime/generation token.

The sampler may hold values and IDs, never live entity stores or mutable native refs. Global pack-cap admission remains a separate gate, not a rarity weight. Recheck all time-sensitive native conditions immediately before native publication.

A physically invalidated plan is not reweighted or rerolled in the same birth opportunity. Use the current compensation/ordinary fallback appropriate to its durable state and record the reason.

### 6.5 Candidate-dependent constraints

Horde, Packbound, inherited effects, native immunity restrictions, and aggregate stat protections must be included in feasibility before a name pair receives positive mass. If a candidate changes roster demand, compute its full finalization summary as part of the set state.

Do not roll probabilistic immunity grants merely to ask whether a name pair is feasible. Feasibility checks legal grant possibilities and pre-existing hard exclusions; the existing dedicated immunity draw runs once for the final plan. A grant rejected by a safety cap follows the existing “no grant, no reroll” contract, not a new name reroll.

### 6.6 Failure classification

| Failure | Permitted outcome |
|---|---|
| Genuine impossible tier/name pair | Zero feasibility mass; conditioned odds exposed |
| No legal v2 plan for an intended covered role | Preserve native ordinary fallback; `NO_COMPLETE_V2_PLAN`; coverage gate FAIL |
| Declared existing campaign/immunity exclusion | Preserve current behavior; explicit policy reason |
| Missing native certification | Block that feature/route; integration gate FAIL |
| Planner cache/work budget exceeded | Local diagnosed failure/defer within existing lifetime; performance gate FAIL; never call it normal ineligibility |
| Native headroom/placement changed after selection | Existing rollback/compensation; no second random promotion attempt |
| Uncertain durable write | Existing fail-closed recovery boundary; do not fabricate an acknowledgment |

Normal fallback does not mean an implementation defect is accepted. It prevents a local enhancement failure from suppressing the original natural spawn.

---

## 7. Bounded exact sampler

### 7.1 Decision graph, not a list of completed sets

Process affix IDs in canonical order. Each ID has one skip branch and eligible take alternatives. Aura selectors are mutually exclusive take alternatives at a single ID node.

Memo state contains at least:

- next ID index;
- selected count;
- semantic-group counters;
- pair-conflict flags;
- a set of reachable ownership-allocation counts;
- candidate-dependent roster/inheritance/immunity finalization summary;
- any remaining identity needed by an existing legality predicate.

Policy/context identity belongs in the compiled-context key, not mutable state.

A compact summary may replace selected IDs only when it is a sufficient statistic for **every** remaining legality/finalization predicate. If a predicate needs an exact selected-ID mask, retain it. Do not trade correctness for a misleading complexity claim.

### 7.2 Ownership allocation without multiplicity bias

After the signatures, residual target capacities are `(p,s,d) = (TP-1,TS-1,D)`, each at most two. Start with the reachable allocation set `R={(0,0,0)}`.

Taking affix `a` transforms `R` into the **set union** of all counts reachable by assigning `a` to one permitted owner with spare capacity. Identical triples are deduplicated. Skipping `a` leaves `R` unchanged.

There are at most `3*3*3=27` allocation triples for a fixed residual target, so this reachability set fits in a small bitset. A completed distinct set is accepted if `(p,s,d)` is reachable. It contributes terminal mass **1**, not the number of ways to reach that triple.

### 7.3 Exact recurrence

Conceptual pseudocode, not a proposed second service:

```text
mass(state):
    enforce per-context resource budget
    if state already memoized: return memo[state]

    if all candidate IDs processed:
        return 1 if exact count, ownership target,
                    and complete finalization are valid else 0

    total = mass(skip(currentID, state))
    for eligible alternative a of currentID:
        next = take(a, state)  // includes union/dedup of allocation possibilities
        if next is not immediately impossible:
            total += integerWeight(a) * mass(next)

    memo[state] = total
    return total

sample(state):
    form skip/take branch masses using the same recurrence
    draw one unbiased integer in [0, sum(branchMasses))
    select its interval and recurse
    after complete set selection, compute canonical legal ownership
```

Signature locks are installed before this recurrence and removed from the candidate IDs. Include their constant weight in the distribution metadata or factor it out consistently. The algorithm is weighted set sampling, not a descending-rarity cascade.

### 7.4 Numerical rules

Use exact integer weights and arbitrary-precision completion totals. Existing configured multipliers must resolve once to the same accepted integer-weight policy before compilation. Do not silently convert set products or total mass to `double` or overflow-prone `long`.

Use the project's deterministic RNG framework with a versioned unbiased `uniformBelow(BigInteger)` helper. A valid implementation uses enough random bits, masks excess high bits, and rejects values outside the bound. A bounded rejection watchdog may report an internal RNG failure; it must never use modulo or silently select a fallback branch.

Document byte order, domain-key encoding, counter progression, and the exact RNG version. Replay vectors must work across supported machines/JVMs. No wall-clock time or iteration order of hash maps may affect outcomes.

### 7.5 Required analytical toy test

Two signatures are fixed. One prefix-filler slot and one suffix-filler slot remain. Candidate `X` has weight 2 and can fill either side; `Y` has weight 3 and can fill either side; `Z` has weight 5 and can fill only prefix.

| Distinct filler set | Valid ownership assignments | Correct weight | Correct probability |
|---|---:|---:|---:|
| X,Y | 2 | 6 | 6/31 |
| X,Z | 1 | 10 | 10/31 |
| Y,Z | 1 | 15 | 15/31 |

Counting assignments would incorrectly produce weights 12,10,15. The compiler and sampler must produce 6,10,15 and the canonical matcher must assign exactly one valid ownership afterward.

### 7.6 Compilation and cache

Compile on immutable data off the world thread. Share compiled subgraphs only when signatures, pools, resolved weights, all native restrictions, and finalization summaries are equivalent. Display strings alone are not a cache key; entity UUIDs generally are not part of structural cache keys.

Cache keys include all behavior-affecting revisions and resolved operator settings. A collision or missing policy field must not cause a graph from one role/era/configuration to be reused for another.

Prepare common role/era contexts before enabling v2 admission. Coalesce identical outstanding compilation requests. Retain required immutable snapshots until outstanding births using them finish. Config reload publishes a complete new snapshot atomically; it does not mutate an in-use graph.

### 7.7 Initial engineering limits

The following are design targets to measure and adjust with an explicit performance report, not achieved results:

- 100,000 memo states per compiled context.
- 64 MiB shared planner memory budget, including cached graphs and accounted in-flight graph construction, not just cache entries.
- At most two concurrent builders, using an existing suitable bounded executor where available.
- At most 32 outstanding requests per world and 256 per server; single-flight coalescing before queue admission.
- 500 ms cold-build deadline per context, with cooperative cancellation and no late world mutation.
- No synchronous graph compilation on a world tick. Warm-plan world-thread work should target ≤0.5 ms p95 and ≤1 ms p99 on the recorded test machine, with a bounded per-tick dispatch budget.

Worst-case reachable-state growth can still be exponential. A bounded compiler does not make that disappear. If a required context cannot compile within the budget, the sampler gate fails and must be optimized before release. Never disable a tier merely because a compiler timed out.

---

## 8. Versioned data and configuration

### 8.1 Proposed assets

Paths below are architecture targets, not files already installed:

| Asset | Purpose |
|---|---|
| `rpg/enemies/name-pools-v2.json` | Stable component IDs, frozen text/localization keys, tiers, signatures, filler membership and name weights |
| `rpg/enemies/selection-policy-v2.json` | Tier weights, difficulty slots, allowed generic IDs, exact sampling/ownership policy, production cap |
| `rpg/enemies/reward-policy-v2.json` | Threat units and rational reward coefficients/caps |
| Existing `monster-affixes-v1.json` and native registry | Current 27 effect definitions and certified execution semantics |
| Existing per-save immutable policy archive | Content-addressed versions referenced by births and pending rewards |

Do not rename or overwrite v1 policy revisions in place. A repeated revision ID with different bytes is invalid.

### 8.2 Example policy fragments

These examples specify field intent, not a finalized native Java codec signature.

```json
{
  "schemaVersion": 2,
  "revision": "me12-names-ad1",
  "format": "{prefix} {suffix}",
  "prefixes": [
    {
      "id": "ruinbone",
      "text": "Ruinbone",
      "tier": 3,
      "weight": 1,
      "signature": {"affixId": "ME-004"},
      "fillerAffixIds": ["ME-002", "ME-003", "ME-017", "ME-018", "ME-026", "ME-027"]
    }
  ],
  "suffixes": [
    {
      "id": "unbroken",
      "text": "the Unbroken",
      "tier": 3,
      "weight": 1,
      "signature": {"affixId": "ME-017"},
      "fillerAffixIds": ["ME-001", "ME-002", "ME-003", "ME-004", "ME-018", "ME-019", "ME-025", "ME-026", "ME-027"]
    }
  ]
}
```

The sample shows one pair only. Production validation requires the complete approved catalog and reachable entries in all three tiers.

```json
{
  "schemaVersion": 1,
  "revision": "me12-selection-ad1",
  "appliesTo": "NEW_NATURAL_PROCEDURAL_UNIQUE",
  "tierWeights": {"1": 70, "2": 25, "3": 5},
  "difficultySlots": {"NORMAL": 0, "NIGHTMARE": 1, "HELL": 2},
  "maximumOwnAffixes": 8,
  "outcomeWeighting": "DISTINCT_SET_PRODUCT",
  "ownership": "CANONICAL_VALID_MATCHING",
  "genericPool": "ALL_27_EXISTING_IDS_FILTERED_BY_LEGALITY",
  "nativeImmunityPolicy": "PRESERVE_ACCEPTED_LEGACY_POLICY",
  "gearQualityPolicy": "UNCHANGED"
}
```

In the production file, expand the generic pool to explicit IDs and pin its resolved registry digest. The symbolic description above must not turn into a future wildcard that admits new operators automatically.

### 8.3 Frozen descriptor extension

Embed new identity data in the authoritative descriptor within the sealed birth plan/root. Do not create an independently writable companion naming database.

Conceptual v2 Unique extension:

```json
{
  "modelVersion": 2,
  "identityKind": "PROCEDURAL_UNIQUE_COMPOSITE",
  "namePoolRevision": "me12-names-ad1",
  "selectionRevision": "me12-selection-ad1",
  "rewardPolicyRevision": "me12-rewards-ad1",
  "prefix": {"id": "ruinbone", "tier": 3},
  "suffix": {"id": "unbroken", "tier": 3},
  "displayNameAtBirth": "Ruinbone the Unbroken",
  "difficultyBonus": 2,
  "ownAffixCount": 8,
  "ownership": [
    {"affixId": "ME-004", "source": "PREFIX_SIGNATURE"},
    {"affixId": "ME-018", "source": "PREFIX_FILLER"},
    {"affixId": "ME-002", "source": "PREFIX_FILLER"},
    {"affixId": "ME-017", "source": "SUFFIX_SIGNATURE"},
    {"affixId": "ME-026", "source": "SUFFIX_FILLER"},
    {"affixId": "ME-025", "source": "SUFFIX_FILLER"},
    {"affixId": "ME-007", "source": "DIFFICULTY"},
    {"affixId": "ME-014", "source": "DIFFICULTY"}
  ],
  "threatUnits": 940,
  "bonusThreatUnits": 840,
  "xpFactor": {"numerator": 113, "denominator": 25},
  "equipmentFactor": {"numerator": 123, "denominator": 40}
}
```

This is an identity/reward extension fragment, not a complete serialized encounter. Production records also retain every existing world, encounter, logical actor, generation, pack, source, native fingerprint, affix parameter, selector, health/protection and policy digest required by current owners. A concrete implementation may place this under a discriminated union instead of an optional object, provided §11's old-shape requirements hold.

### 8.4 Scope-aware caps and descendants

A global `MAX_AFFIXES=8` is unsafe. Dispatch bounds by identity/record policy:

- v1 actor: exact v1 limits and evaluator.
- new v2 procedural Unique: exact budget up to eight own affixes.
- Champion: one own affix regardless of codec capability.
- Normal/initial minion: zero own affixes.
- authored Super Unique/Boss: existing policy, not an inherited eight-affix permission.
- QA: existing explicitly authorized QA bounds, never production economics.

A v2 leader may produce more inherited entries on its initial minions than a v1 codec permits. Such descendants need a v2-capable record representation with explicit parent generation and inheritance provenance, even though they retain zero own affixes and legacy minion rewards. Reject a v1 minion record silently containing eight inherited entries.

### 8.5 Validation rules

Strictly reject unknown schema versions, unknown keys where current contracts require strictness, duplicate IDs, nonpositive name weights, invalid tier values, missing signatures, duplicate signature IDs within a pair, selector misuse, invalid references, nonfinite numbers, and revision/hash collisions.

Policy validation must generate a reachability report, not only parse JSON. A policy that unexpectedly removes every v2 plan for a required role/era cannot replace the last valid production policy. Publish nothing partially.

Use rational coefficients and integer threat units for new arithmetic. Where an existing owner supplies a decimal chance, define and persist its canonical representation at that owner boundary. Do not retroactively replace v1 floating-point arithmetic.

### 8.6 Operator configuration

Reuse the existing save-root `world-config.json` and `/rpg worldconfig` mechanisms where actually registered on the chosen baseline. A proposed namespaced block controls activation and selects immutable policy revisions. Do not put ordinary operator controls in an obscure new folder.

Suggested feature states:

- `DISABLED`: legacy births only; compatible readers can still be installed.
- `SHADOW`: compute and compare v2 plans without publishing them or delivering v2 rewards.
- `ENABLED`: eligible new procedural Uniques use v2.
- `DRAIN`: no new v2 births; existing v2 actors and entitlements remain fully supported.

Only `DISABLED` is the installation default for the compatibility-first phase. Activation requires a validated policy, accepted prerequisite manifest, and explicit owner authorization. Old encounters never read live mutable coefficients from this block.

---

## 9. Birth planning and publication

### 9.1 Existing transaction remains authoritative

1. Receive an existing validated native spawn opportunity.
2. Capture its current difficulty/source context and keep the original group restorable.
3. Use the existing rarity selection. Champion, Normal, and authored pathways remain separate.
4. For an eligible v2 Unique, obtain the immutable feasibility context and compiled distribution.
5. Draw tier pair, name pair, and distinct affix set on purpose-separated streams.
6. Compute canonical ownership, resolved effect parameters, roster demand, inheritance, threat, and policy digests.
7. Run complete final validation; reserve native/pack resources through existing owners.
8. Persist the authoritative birth root/plan through the current durable sequence.
9. Attach native state and publish only after the appropriate durable acknowledgment.
10. Index/register the published plan idempotently and project its presentation.

The exact placement of preflight and reservation calls must fit the accepted native birth owner. Do not hold a world tick or long-lived new resource lease while compiling a graph. Do not allow a late completion to attach an encounter to a replaced world.

### 9.2 One random identity per birth attempt

Derive v2 selection streams from the existing stable birth identity plus the v2 algorithm/policy identity. Use separate domains for tier pair, name pair, set selection, and existing independent roster/immunity choices.

Do not hash mutable display text, current time, thread IDs, process-local counters, or a global mutable random generator into the identity. Name-localization changes must not change combat outcomes for an already-frozen encounter.

Reuse the selected plan for native additional-slot preparation and final publication. Do not run an independent second tier roll during preflight versus commit.

Selected v2 random domains are `me12.tier-pair/v1`, `me12.name-pair/v1`, `me12.affix-set/v1`, `me12.quantity/v1`, and `me12.learn/v1`. Each is derived from its stable owning event and explicitly frozen relevant policy revision. Preserve the existing rarity, roster, immunity and gear domains. Adding the BigInteger helper or v2 sampler must not bump a shared gear RNG version or change existing gear stream encoding. Give the ME12 sampler its own algorithm version.

### 9.3 Counterpart and stale-callback rules

Every asynchronous callback carries world lifetime, encounter, generation, and operation identity. Validate these before applying effects. A stale callback may release only its own reservation and may not reopen a quarantined world or replace a newer plan.

Preserve the corrected startup-rebind boundary: ordinary actor reactivation must not rerun a startup inventory audit against a stale snapshot. Recover the original descriptor and state rather than regenerating the name or affixes.

### 9.4 Optional enhancement failure versus uncertain persistence

Before durable publication, an unsupported or unavailable optional promotion must leave ordinary native spawning usable through the established fallback. After a durable root or native mutation creates uncertainty, recovery must consult authoritative records. Do not treat “we did not see the entity” as a defeat, and do not release another encounter's capacity.

Neither a failed sampler compile nor an expected capacity denial should be relabeled as corruption in the encounter store. Conversely, do not bypass a real uncertain-write gate to improve spawn counts.

---

## 10. Threat, experience, equipment, and learning

### 10.1 Common scoring contract

For the new procedural Unique only:

`S = sum(t_i)` for its distinct own affixes.  
`B = max(0, S - 1)`.

The first unit is the existing rarity baseline allowance. Sum frozen operator scores once. Signatures and fillers have the same scoring rules. Inherited effects, native immunity tags, selector sublabels, summons, live guard count, and temporary stacks are not extra scored instances.

Freeze scores and factors at birth. A shield being consumed or a guard dying does not reduce the earned threat basis.

### 10.2 XP

`F_XP = rarityXP * [1 + 0.15 * min(B, 9)]`.

For the current reviewed Unique baseline, `rarityXP=2`, so the maximum factor is 4.7. This is not the final player XP. Feed it once into the existing rarity-factor position; preserve rank, level-difference, eligibility, party allocation and rounding owners.

Do not multiply `rewardXp` that already contains the rarity factor by another Unique factor. Zero eligibility remains zero. Currency, Link Insight, quest flags, and mastery are not multiplied by this formula.

### 10.3 Eligible equipment quantity

`Q = rarityQuantity * min(2.05, 1 + 0.15*B)`.

Let `K0` be the existing finalized base managed-equipment slot count after its no-drop and base cap decisions, but before item generation/publication.

`mu = min(16, K0*Q)`.

`N = floor(mu) + Bernoulli(frac(mu))`.

Use one stable defeat-specific quantity stream and freeze `N` before creating bonus children. Keep all original `K0` base slots and add `N-K0` independent eligible children. Since the validated source has `0 <= K0 <= 16` and `Q >= 1`, capping expected count at 16 cannot discard a base entitlement.

This replaces the old v1 quantity-headroom rejection only for the v2 policy. Invalid source profiles still fail validation, but a valid v2 birth must not be rejected solely because its uncapped reward projection exceeds 16.

**Semantics:** `K0=0` stays zero. More threat increases successful-roll quantity, not the chance to turn a failed base no-drop roll into an item. Never apply an extra no-drop roll to a bonus slot, copy a generated item, or generate then discard items to meet a cap. [V11 §10.3; R2: equipment fixtures]

### 10.4 Gear quality firewall

Do not change `GearMagicFind.distribution`, current quality profiles, diminishing returns, eligibility, per-item RNG streams, category/base ordering, or item-affix generation. Every bonus item goes through the existing guaranteed-pick generator with its own stable child identity and the same frozen eligible source/Magic Find context.

Preserving per-item rarity does **not** preserve high-quality items per encounter: more draws increase the chance of seeing one. Measure both. Do not claim that unchanged selection weights preserve exact old outcomes after changing seed/version identity.

Existing base children must retain their established event/seed identity. Keep existing `/me-equipment/<index>` conventions where those are the accepted keys; append new deterministic children, not a second namespace that pays the same item again.

The proposed future eligibility-first Set/Legendary pipeline is outside this architecture.

### 10.5 Learning formula, with the ambiguity removed

Define:

- `P_w`: existing eligible first-acquisition chance after the existing Wisdom calculation.
- `P_r`: existing monster-rarity-adjusted chance after its existing rarity bonus-point cap and ordinary cap.
- `Delta = min(0.03, 0.10 * P_r * B)`.
- `P_final = min(0.95, P_r + Delta)`.

The new three-point allowance is **separate from**, and follows, the old rarity bonus ceiling. Its base is `P_r`, not the pre-rarity base chance. This is the selected AD1 resolution of the earlier conflicting formulas.

This intentionally gives common, high-base-chance abilities an early ceiling; lower-base-chance abilities retain a longer threat-to-learning gradient. More affixes do not promise a strictly larger chance after a cap is reached. The economy report must show saturation rather than implying every extra affix always adds a visible bonus.

Eligibility is checked first. An invalid, unassigned, already-acquired, excluded-origin, or zero-base-chance source is not made eligible by Wisdom or affix bonuses. Do not call the learning RNG or increment pity for such an opportunity.

Pity remains its existing guarantee outside the ordinary 95% limit. Keep its canonical key, threshold, alias handling, one increment per failed eligible attempt, and atomic success/reset behavior. Do not reduce thresholds by threat or give one learning roll per affix.

### 10.6 Acquisition adapter and public readiness

The review found no shipped public learning bindings and a Skill-specific resolver; Passive purchasing is not proof of monster first-acquisition. The older v1.1 design nevertheless explicitly specifies Skills and first-copy Passives in the shared first-acquisition contract. Treat this as an implementation gap, not a license to silently drop Passive learning from the vision. [R1: learning gap; V11 §10.5]

Define a typed opportunity under the existing acquisition owner:

`AbilityKind = SKILL | FIRST_COPY_PASSIVE`.

Each eligible canonical source still supplies its existing single assigned ability, not one Skill plus one Passive and not an affix-derived ability. The shared opportunity carries source ID, ability ID/kind, acquisition rarity, readiness proof, chance inputs, pity identity, and stable defeat/player identity.

- Skill success uses the existing skill-unlock mutation.
- First-copy Passive success grants the existing canonical first-acquisition entitlement through the same progression authority.
- Existing passive-copy purchases and their prices remain unchanged.
- Buying/learning concurrently must be serialized by the existing player progression transaction. An already acquired passive cannot be farmed again merely because a copy was spent or an inventory view changed.
- Reuse an existing durable “ever acquired”/unlocked fact where available. Where only historic ownership/purchase receipts establish it, define a compatibility read through that owner; do not invent a second unsynchronized unlock ledger.
- Missing or ambiguous historic acquisition provenance must block public activation of the affected source until resolved, not create duplicate first copies.

No new skill content or reassignment is authorized here. Enable sources only after their already-assigned ability and actual native defeat path are accepted. Production rollout may technically keep acquisition bindings gated, but **full Master Enemies v1.2 completion remains BLOCKED** until a real Skill and a real first-copy Passive demonstrate the promised behavior. A narrower release claim would need separate owner scope approval.

### 10.7 Learning determinism and transactions

For v2 opportunities, replace unversioned ambient randomness only at the new opportunity-policy boundary. Derive a dedicated deterministic draw from stable defeat, recipient, ability, and learning-policy identity. Freeze permitted chance inputs at the existing opportunity boundary, then commit the result and pity mutation through the existing player transaction.

On retry, reuse a committed or pending opportunity receipt. Do not recompute with the player's newer Wisdom/equipment or reset the random stream. Concurrent known-ability checks run against authoritative serialized player state. Preserve v1 decisions and replay behavior; do not retroactively reroll legacy opportunities.

### 10.8 Numeric acceptance fixtures

These factors assume the reviewed Unique baselines `rarityXP=2` and `rarityQuantity=1.5`. Production captures the accepted baseline values in the policy snapshot.

| Fixture | Own affixes | B | XP factor | Q |
|---|---|---:|---:|---:|
| Normal, Grimtooth the Stubborn | Extra Strong, Unwavering | 1.00 | 2.300 | 1.725 |
| Normal, Ruinbone the Cruel | Stone Skin, Extra Strong, Unstoppable, Reflective | 3.65 | 3.095 | 2.32125 |
| Normal, Ruinbone the Unbroken | Stone Skin, Unwavering, Extra Strong, Unstoppable, Armor Breaker, Reflective | 5.80 | 3.740 | 2.805 |
| Nightmare, preceding six plus Lightning Enchanted | Seven affixes | 7.10 | 4.130 | 3.075 |
| Hell, preceding seven plus Cursed | Eight affixes | 8.40 | 4.520 | 3.075 |

| K0, Q | mu | Result |
|---|---:|---|
| 0, any legal Q | 0 | Always zero |
| 1, 1.725 | 1.725 | 1 item 27.5%; 2 items 72.5% |
| 1, 3.075 | 3.075 | 3 items 92.5%; 4 items 7.5% |
| 2, 3.075 | 6.15 | 6 items 85%; 7 items 15% |
| 5, 3.075 | 15.375 | 15 items 62.5%; 16 items 37.5% |
| 6, 3.075 | 16 | Always 16 |

The last two rows are synthetic cap tests, not proposals to increase base profile drops.

| P_w at zero Wisdom | P_r | P_final at B=1 | P_final at B=8.4 |
|---:|---:|---:|---:|
| 15% | 20.25% | 22.275% | 23.25% |
| 5% | 6.75% | 7.425% | 9.75% |
| 2% | 2.70% | 2.970% | 4.968% |

### 10.9 Economy exclusions and farming safeguards

Preserve origin/source eligibility for QA, combat summons, revived actors, player-owned/tamed actors, nonterminal appendages/phases, administrative cleanup, and duplicate defeats. Audit both managed rewards and any native drop path so an excluded summon cannot leak ordinary material or currency drops through a parallel handler.

Initial sealed minions retain their own allowed baseline economics. They do not inherit the leader's tier or threat multiplier. Horde can still increase whole-encounter rewards by adding legitimate initial minions; measure that separately rather than applying the leader factor to each minion.

Do not implement Champion 5× gold here. Preserve existing Gold Find and currency ownership without falsely labeling the earlier proposed gold feature as shipped.

---

## 11. Persistence, migration, and rollback

### 11.1 Preserve strict historical serialization

The reviewed store and checkpoint code validate checksums and exact deserialize/reserialize shapes. Adding a default field to a shared constructor does not prove compatibility. [R1: persistence finding; R2: migration owners]

Implement version-dispatched codecs at every nested boundary. Prefer explicit v1/v2 wire DTOs mapped into a shared read-only runtime view. A v1 record must round-trip with its accepted serialized shape; a newly optional field must remain absent, not appear as `null`, an empty object, or a new default value in v1 bytes.

Unknown versions are not v1. Missing v2 data selects v1 only in an explicitly recognized legacy envelope. Contradictory schema and policy markers are invalid, not an invitation to guess.

### 11.2 Serialization surfaces requiring tests

Descriptors and indexes; birth plans and roots; frozen attachment sources; nested spawn snapshots; context/death records; quantity plans/receipts; player reward/acquisition intents where extended; checkpoint bundles; journal payloads and replay; archived policy references; inherited minion records.

Each surface needs tests with actual old shapes from the accepted implementation. Existing copied save records are fixtures, not permission to rewrite the user's save.

### 11.3 Freeze the right data at the right time

| Boundary | Frozen information |
|---|---|
| Policy activation | Validated assets, effective settings, algorithm IDs, content digests and archives |
| Birth planning/publication | Name IDs/tiers, name text, affix set/ownership/parameters, source, roster, native fingerprint, threat and reward factors |
| Existing contribution/defeat boundary | Eligible recipients, source event identity and recipient-specific snapshots already owned there |
| Quantity finalization | K0, Q, capped expectation, rounded N, deterministic child identities |
| Existing gear generation boundary | MF/source snapshot, seed, generator revision and then the exact generated item/entitlement |
| Existing acquisition opportunity boundary | Eligible ability, Wisdom/chance inputs, pity/known-state decision, stable draw and result receipt |

Player Magic Find or Wisdom cannot be frozen at enemy birth; the future recipients may not even be present. A hot reload after birth must not change that actor's threat factors. A retry after death must not use a new policy to replace a frozen entitlement.

### 11.4 Archive lifetime

Archive effective policy bytes and digests durably **before** publishing a birth that references them. Preserve definitions/evaluators for all referenced active and historical records, pending rewards, checkpoints, retained journals, and recovery obligations.

AD1 performs no automatic archive garbage collection. If archival cleanup is added later, it requires a proven reference scan covering retained history and rollback needs, not just currently loaded actors.

A missing/corrupt revision is a diagnosed recoverable dependency block. Do not substitute the latest content, silently drop fields, invent new affix values, or delete a record to open admission. Use the existing safest admission scope; do not broaden a local recoverable problem to an unrelated global worker failure without integrity evidence.

### 11.5 Exactly-once means recoverable, not merely nonduplicating

Retain existing defeat and child event identities. Add provenance fields without replacing an old key with a “better” new key that looks like a second defeat.

A reward plan commits immutable intent before effects are acknowledged. Its children can apply idempotently, and aggregate completion means all legitimate children are durably resolved. Inventory-full, disconnected recipients, and temporary content unavailability do not erase an entitlement.

Fault injection must prove both:

1. no duplicated XP, item, first acquisition, or pity mutation;
2. eventual completion after the required dependency or storage service is restored.

### 11.6 Pending gear and generator changes

The reviewed mismatch-to-`CONTENT_ERROR` behavior protects against rerolling but does not by itself prove eventual payout. For v2 entitlements:

- Prefer persisting an exact generated item before publishing its ownership effect.
- Before generation is finalized, retain the original source, seed, generator implementation version, relevant definitions and capability/configuration snapshot.
- If the exact evaluator is unavailable, retain a durable recoverable hold such as `WAITING_FOR_GENERATOR_REVISION` under the existing reward owner.
- Resume with the exact evaluator/definitions. If the current generator happens to have identical logic but a different fingerprint, equivalence must be demonstrated and explicitly versioned, not assumed.
- Never mark the child delivered, cancel it as a normal no-drop, or generate a new item using current rules to clear the queue.

Retaining asset JSON alone may not be enough when algorithm code changed. The compatibility strategy must retain the required evaluator or freeze the concrete outcome before that evaluator is retired.

### 11.7 Migration sequence

1. Inventory actual wire shapes, dependency versions, native/runtime hooks, and all current reader/writer callers.
2. Add dual readers and legacy evaluators with v2 writes disabled.
3. Validate old fixtures, strict round trips, journal/checkpoint replay, and existing reward outcomes.
4. Add and validate immutable v2 policies and archive creation, still without v2 production births.
5. Add shadow planning and numerical comparison without consuming production RNG or producing effects.
6. Exercise synthetic/copy-based mixed v1/v2 records and interrupted publication/reward recovery offline.
7. Obtain explicit authorization for the accepted candidate and a consistent stopped-world/main-save backup.
8. Deploy the compatible candidate with new-v2-birth activation under operator control.
9. Complete connected owner QA and retain exact build/evidence receipts before acceptance.

No eager save-wide rewrite. Existing v1 actors keep their policy until their normal lifecycle ends, including legitimately retained historical records afterward.

### 11.8 Rollback

`DRAIN` is the normal rollback: stop creating new v2 actors, keep v2 readers/evaluators active, and let existing actors/rewards recover and complete. Draining active creatures does not make old binaries safe; v2 receipts and history remain.

A full backup restore is disaster recovery only, requiring explicit approval because it can discard post-backup progress. A consistent backup covers native entities/worlds, player state, encounter stores/journals/bundles, reward/pity ledgers, gear ownership/custody, policy archives, configuration, mod and native artifacts. Restoring only one side of that set can break consistency.

Use offline copies for migration and rollback drills. Backup/recovery copies belong outside `Saves`. Do not create another playable QA save or launch a standalone native server without permission.

---

## 12. Presentation and inspection

### 12.1 One identity, coordinated renderers

Keep the existing colored, black-outlined Elite name. Preserve rarity colors; component tiers are not a replacement rarity color system. The native plain name fallback and custom colored rendering must coordinate so one actor is not shown with two primary names.

Keep the existing accepted close spacing between the colored name and its affix row. The future level-danger badge and authored graphic are separate art/UI tasks, not a reason to rebuild this presentation now.

### 12.2 Full-information target surface

The primary target card must visibly show all eight own affixes, meaningful inherited-effect information where applicable, active channel/status immunity, and current protection state. Expand the existing card's height/wrapping behavior instead of assuming that a DTO containing eight strings proves they are visible. The reviewed two-line limit is a concrete integration point. [R1: target HUD finding]

Use the existing UI composition. Keep name, rarity/species/level, affix tags, and active protection readable at supported resolutions and UI scales. Do not use `+N`, ellipsis, offscreen rows, or clipping as the only way to expose mechanically important modifiers. A bounded responsive layout is preferred; if the native surface cannot meet the contract, its gate stays blocked until a supported solution is implemented.

Overhead labels may use a compact presentation to limit crowd clutter, but the selected target's full-information card and existing inspect output must expose everything. Do not add an unbounded carrier entity per letter or per new affix as a scalability shortcut.

### 12.3 Operator inspection

Extend actual registered inspection/status commands, rather than assuming historical commands still exist. Show:

- model/storage/selection/reward revisions;
- native role, source, difficulty, encounter and generation;
- prefix/suffix IDs, tiers, signatures and name;
- exact affix count, each ownership source and resolved parameters;
- native certification and rejection details;
- target and conditioned tier probabilities;
- frozen threat units, B, XP factor and equipment factor;
- eligible learning formula inputs for a permitted observed opportunity, without inventing an unbound source;
- frozen quantity/death child state, including recoverable holds;
- current versus archived policy identity.

No production client request may provide authoritative affixes, tiers, seeds, immunity, reward amounts, or guaranteed-learning flags.

---

## 13. Performance and observability

### 13.1 Bounded diagnostics

Use the existing trace/HRO infrastructure. Default logs should aggregate rather than record every candidate set. Preserve rare failure detail without letting routine messages consume all space.

Required counters distinguish:

- eligible native opportunities;
- existing rarity selections;
- policy exclusions;
- genuine impossible tier/name pairs;
- no-complete-plan covered-role failures;
- cache hits/misses, compiled contexts, memo peaks, bytes, queue pressure, compile time;
- pack/world/cell/native headroom denials;
- published v2 births and ordinary restorations;
- rebinds and stale callbacks;
- reward expectations, finalized children, pending definition holds and completions;
- acquisition eligible/success/failure/pity/known/unbound outcomes.

Every counter must have a documented denominator. A percentage of all native placement attempts is not a percentage of eligible Unique rolls. Repeated native retries must not be counted as independent creature encounters when analyzing rarity.

### 13.2 Distribution reporting

Report analytical tier-pair probabilities for each certified context alongside sampled and connected outcomes. Report per-name and per-affix marginals separately. Adding a signature or changing a theme can alter individual-affix frequency even when numerical weights stay unchanged.

Live pressure/placement can alter the distribution of **successfully observed** monsters after planning. Log selection and publication separately so this is not mistaken for RNG bias.

### 13.3 Economy and combat measurements

Compare accepted baseline and candidate under the same recorded configuration. Measure XP and equipment per eligible defeat, per pack, per route/time, and rare-item rate per item versus per defeat. Measure learning success against actual eligible attempts, not all kills.

Preserve currently selected density, wildlife/hostile target, pack limits, and rarity probabilities. Use historical 4×/65:35/12 settings as stress fixtures only when they match the accepted baseline or are separately authorized. Do not overwrite a newer accepted world config to match a stale document.

Combat checks include all admitted legacy operators, conditional actions, reflected damage identity, projectile status ownership, minion inheritance, shields and guard protection. Do not certify an affix because its label appeared.

---

## 14. Implementation phases

These phases describe future work after engineering authorization. They are not a request to execute now. No multi-day duration is asserted; use measured scope and stop at the stated gate instead of an open-ended rewrite.

| Phase | Deliverable | Exit evidence |
|---|---|---|
| P0: accepted native baseline | 0.7.1-compatible hooks/assets, reconciled selected Elite/HRO changes, exact baseline manifest | Existing regressions and applicable connected evidence; owner accepts cumulative baseline |
| P1: coverage and policies | Exact role/alias matrix, selected names/themes/threat policy, strict asset validation, explicit Normal exceptions | Minimum 2/3/4 coverage for intended non-excluded role/eras; conditioned higher tiers recorded |
| P2: pure exact planner | Joint tier/name selection, bounded exact set sampler, canonical ownership, isolated RNG | Analytical fixtures; deterministic replay; full-policy distributions; resource limits |
| P3: dual-format persistence | v1/v2 codecs, archived policies, inherited v2 descendants, recovery and DRAIN support | Old-shape round trips; mixed-version replay; fault injection and offline rollback drill |
| P4: rewards and acquisition boundary | Frozen threat factors, capped quantity plans, recoverable gear holds, typed first acquisition | Numerical fixtures and exactly-once/eventual-delivery tests; public acquisition dependencies remain explicit |
| P5: native integration and presentation | Existing birth owners consume v2 plans; full target UI; inspect/trace integration | Actual native-component regressions; no duplicate names/effects; performance report |
| P6: authorized connected candidate | Deployment receipt and one structured owner QA run | Natural births all eras, lifecycle, combat, economy, real Skill/Passive sources and owner acceptance |

### 14.1 Efficient work order

Run focused new failing tests first, then the affected suites, then one cumulative package/native verification gate for the phase candidate. Do not repeatedly run thousands of unrelated tests after every small edit.

Use existing selector, birth, descriptor, immunity, reward, gear multi-pick, checkpoint/journal, native action, and target-HUD tests as seams. A new architectural responsibility does not require an entirely new test harness.

P2 can be built as pure code once authorized without pretending P0 native certification passed. P3–P5 must integrate against the selected compatible baseline. Production activation is blocked until P0 and applicable preceding gates pass.

### 14.2 Learning dependency ownership

P4 implements and tests the multiplier/typed opportunity boundary. The skill-content/acquisition workstream supplies actual certified bindings and first-copy Passive readiness. Those dependencies have an explicit owner and gate; they are not repeatedly investigated inside the sampler task.

An automated synthetic opportunity may pass P4's math/transaction gates while real acquisition remains `BLOCKED`. Do not mark the full feature complete until P6 demonstrates the real paths or the owner explicitly revises scope.

---

## 15. Automated acceptance gates

### 15.1 Gate record model

Each requirement records implementation, automated/native evidence, and actual owner acceptance independently. Example only:

```json
{
  "schema": 1,
  "feature": "Master Enemies v1.2",
  "architectureRevision": "ME12-ARCH-AD1",
  "status": "OPEN",
  "ownerAccepted": false,
  "implementationBaselineSha": null,
  "requirements": [
    {
      "id": "ME12-S02",
      "title": "Distinct-set probabilities exclude ownership multiplicity",
      "status": "NOT_RUN",
      "implemented": false,
      "automatedPassed": false,
      "nativeConnectedPassed": false,
      "ownerAccepted": false,
      "evidence": [],
      "blockedBy": []
    }
  ]
}
```

`NOT_APPLICABLE` is allowed only with a reason for gates that truly do not require a native surface; it is not a synonym for untested. A dependency must remain `BLOCKED`, not be converted to PASS because it is outside one contributor's code.

### 15.2 Baseline and coverage

| ID | Required assertion |
|---|---|
| ME12-B01 | Selected branch/source, installed JAR, server and assets agree with the accepted cumulative baseline manifest. |
| ME12-B02 | Native hooks required for existing and new behavior are actually present; no bypass of missing-hook guards. |
| ME12-B03 | Every intended role and alias appears in the coverage matrix with capability, immunity, roster and name-conditioned feasibility. |
| ME12-B04 | Every intended non-policy-excluded role/era supports the minimum v2 count. Restricted Hell roles cannot silently vanish. |
| ME12-B05 | Current Normal native-immunity exclusions remain unchanged and explicitly listed; original native actors still spawn. |
| ME12-B06 | Actionless/mixed/conditional/projectile certificates cover every reachable native path they claim, including poison/Antidote restrictions. |

### 15.3 Names and probability

| ID | Required assertion |
|---|---|
| ME12-N01 | Fully feasible tier pairs match masses 4900/1750/350/1750/625/125/350/125/25 exactly. |
| ME12-N02 | Budget is exactly TP+TS+D; 1/1→2/3/4 and 3/3→6/7/8; two signatures are included, not appended. |
| ME12-N03 | Duplicate signatures, unsupported signatures, invalid selectors, zero-weight signatures and illegal combined groups are excluded before the name roll. |
| ME12-N04 | Conditioned three-affix Normal pairs are 7/12, 5/24, 5/24 for 1/1, 1/2, 2/1. |
| ME12-N05 | Count-feasible five-affix contexts have feasible tier mass 0.9975/0.9725/0.84 before additional name/native restrictions. |
| ME12-N06 | Adding 100 names within an already feasible tier changes names within that tier, not tier mass. Adding first feasibility to a previously blocked tier is a separate tested case. |
| ME12-N07 | Name-pair chance is independent of number or total weight of affix completions. |
| ME12-N08 | Champion, Normal and authored Super Unique/Boss paths retain their original counts/identities. |

### 15.4 Sampler and resource bounds

| ID | Required assertion |
|---|---|
| ME12-S01 | Exact masses agree with exhaustive enumeration on small synthetic catalogs, including signature locks and selector alternatives. |
| ME12-S02 | Ownership-multiplicity toy fixture yields 6/31, 10/31, 15/31, not 12/37, 10/37, 15/37. |
| ME12-S03 | Canonical ownership always satisfies exact slot capacities and membership; repeated runs select identical ownership. |
| ME12-S04 | BigInteger totals and unbiased draws remain correct beyond long range; zero and singleton distributions handled explicitly. |
| ME12-S05 | 100,000 fixed seeded samples per representative feasible role/era; zero illegal plans; expected conditioned distributions within predetermined statistical criteria. |
| ME12-S06 | Repeat seeds/policy/native context produce identical plans across supported runtime targets; legacy seed vectors unchanged. |
| ME12-S07 | Cache invalidation includes all behavior-affecting fields; changing text alone does not change an already-frozen outcome. |
| ME12-S08 | Memory, queue, state and time limits enforced before uncontrolled growth; no world-thread compilation. |
| ME12-S09 | Compile failure is a diagnosed performance failure, never zero legal mass; late callbacks cannot mutate replaced worlds. |
| ME12-S10 | Complete finalizer covers Horde demand, minion inheritance, native immunities, Packbound guard reachability and aggregate restrictions. |

Use analytical assertions as the primary probability proof. Statistical tests use pinned seeds, predeclared confidence/multiple-comparison criteria and explicit rare-cell expectations; they must not be repeatedly reseeded until green.

### 15.5 Rewards and acquisition

| ID | Required assertion |
|---|---|
| ME12-R01 | Every §10.8 factor/rounding fixture matches the specified exact arithmetic. |
| ME12-R02 | Increasing threat at fixed source inputs gives nondecreasing pre-rounding XP factor, equipment expectation and learning chance, up to caps. |
| ME12-R03 | K0=0→0; original base slots preserved; N≤16; no new no-drop on bonus children; no generated item discarded for cap. |
| ME12-R04 | Gear quality/MF distributions and established base-child seeds stay unchanged for fixed inputs; bonus children are independent new items. |
| ME12-R05 | Learning uses P_r as the new bonus base and adds at most .03 separately; ordinary .95 cap and guaranteed pity remain distinct. |
| ME12-R06 | Invalid/unknown/already-acquired/zero-chance opportunity yields no roll and no pity increment; aliases and difficulty changes preserve pity identity. |
| ME12-R07 | Exactly one failure increment or success/reset per eligible event, including concurrent purchase/learning and repeated callbacks. |
| ME12-R08 | Real Skill and first-copy Passive paths use the same accepted transaction authority; purchases/copies remain unchanged. Synthetic-only evidence does not satisfy connected acceptance. |
| ME12-R09 | QA, summons, revive/player-owned origins and native parallel drop paths cannot generate farmable duplicate rewards. |
| ME12-R10 | Initial minions retain own baseline rewards, never leader factors; whole-pack Horde economics measured separately. |
| ME12-R11 | Inventory-full, disconnect, save/restart and unavailable generator definitions retain entitlements and later complete without duplicates. |
| ME12-R12 | No extra multiplier applied to rank, existing rarity, currency, Insight, mastery, campaign milestones or quests. |

### 15.6 Persistence and presentation

| ID | Required assertion |
|---|---|
| ME12-P01 | Actual v1 wire fixtures round-trip through v1 codecs with accepted checksum/shape behavior; no injected defaults. |
| ME12-P02 | Every nested descriptor/root/context/death/quantity/checkpoint/journal path dispatches mixed versions correctly. |
| ME12-P03 | New v2 leader and inherited minion representation loads without granting minions own affixes or v2 leader rewards. |
| ME12-P04 | Policies are archived before reference; missing/corrupt archives hold safely and resume on exact restoration without reroll. |
| ME12-P05 | Failures around every durable/native publication and effect boundary recover the same identities and exact planned rewards. |
| ME12-P06 | Health, used shields, engagement clock, immunities, guard state and live pack accounting survive unload/reload/restart. |
| ME12-P07 | Config reload affects new births only; DRAIN preserves existing v2 records and pending rewards; incompatible old binary rollback is refused operationally. |
| ME12-P08 | Eight own affixes plus two immunity tags and active protection fit the actual target UI at supported layouts/scales. |
| ME12-P09 | One visible primary name per actor; custom/native fallback coordination works during LOAD and after renderer failures. |
| ME12-P10 | Ten lifecycle/cleanup cycles leave no new leaked carriers, subscriptions, workers, providers or unjustified reservations; durable history remains intentionally retained. |

### 15.7 Do not replace existing regression suites

Retain the accepted R245–R249 lifecycle repairs and subsequent accepted Chapter I/HRO fixes through their existing tests. Extend rather than replace the production-owner fixtures. Test count alone is not an acceptance criterion; each gate needs a relevant behavior and evidence reference.

---

## 16. Connected QA and release acceptance

### 16.1 One controlled run sheet

Codex's eventual delivery must include a short operational run sheet using commands verified on the accepted build, with exact expected results and automatic evidence locations. Do not invent commands in the architecture or make the owner depend on naturally finding a 0.25% tier-pair roll.

Use existing authorized QA spawn controls for deterministic mechanics/visual tests with economic exclusions. Natural production encounters must separately establish spawn integration and genuine economy. Never mark a QA creature as natural to force a reward test.

### 16.2 Required connected scenarios

| Scenario | Evidence required |
|---|---|
| Natural Normal/Nightmare/Hell | Real v2 births with correct counts, signatures, profiles and unchanged native density accounting |
| Known previously restricted role | Demonstrated minimum legal Hell plan, actual attack/defense effects and ordinary fallback behavior |
| 2-, 4-, 6-, 8-affix exemplars | Full target-card readability and real admitted effects, using authorized controlled fixtures as needed |
| One-affix Champion peers | Correct generic Champion naming, no minions/leader and unchanged economics |
| Authored Grimgor/accepted Super Unique | Fixed name/template behavior unchanged; no procedural tier conversion |
| Wound → unload → reload → another natural birth | Same identity and residual state; no startup rebind false orphan; admission remains healthy |
| Defeat → inventory full → reconnect/restart | Entitlements remain recoverable and are delivered once |
| Real Skill source and real first-copy Passive source | Correct eligible first acquisition/pity behavior and preserved purchase semantics |
| Dense mixed v1/v2 scene | Readable selected-target information, no duplicate names, no unacceptable tick/memory regression |
| DRAIN and resume | No new v2 births in DRAIN; existing actors/rewards continue safely; new births resume only under validated activation |

A normal run can establish only observed outcomes. It need not statistically verify the rare tier distribution; deterministic offline simulations handle that. Conversely, a simulation cannot prove that the native client rendered eight rows or that an actual NPC executed its attack.

### 16.3 Evidence package

Record exact source SHA, branch integration manifest, JAR/server/assets hashes, policy digests, configuration snapshot, test identifiers, trace time ranges and source event IDs. Separate screenshots/native readbacks from internal DTO values. Keep sensitive tokens and unrelated personal logs out of the default report.

### 16.4 Completion standard

The feature remains OPEN while any required gate is FAIL, BLOCKED, or NOT_RUN. Architected acquisition adapters without accepted public sources are not complete gameplay learning. A disclosed but unapproved scope reduction does not convert a failure to PASS.

The owner accepts the exact tested candidate and declared inherited policy exceptions. No assertion of “100% bug-free forever” is possible; the release standard is every applicable requirement satisfied, no known in-scope defect deferred, and explicit owner acceptance of the evidence.

---

## 17. Risks and explicit non-goals

### 17.1 Risks with required mitigation

| Risk | Mitigation |
|---|---|
| Native update invalidates action certificates | Pin actual installed version/assets; fail certification, not source authority |
| Tier-conditioning hides missing coverage | Separate policy exclusions, structural limits, uncertified routes and planner failures in the matrix |
| Eight slots create sampler explosion | Exact compact allocation state, bounded compilation, prewarming and measured failure gate |
| Theme/signature locks change affix variety | Publish marginal/reachability reports; do not claim unchanged frequency from unchanged weights |
| Early reward cap saturation weakens high-tier incentive | Retain selected caps initially; show uncapped/capped values in economy reports; tune only in a versioned balance revision |
| More equipment floods economy despite unchanged quality odds | Measure items and valuable outcomes per pack/route/time, not only per-item rarity |
| Loot pending across generator upgrades is stranded | Freeze exact results or retain exact evaluator/content; durable recoverable holds with demonstrated resumption |
| Old saves fail strict checks after adding fields | Explicit wire-version codecs and every nested round-trip fixture |
| First-copy Passive duplication | Existing progression transaction plus durable acquisition/purchase facts; race and replay tests |
| Old UI truncates important effects | Native screenshot gate; responsive target card with all required information |
| Global failure from a local optional feature | Reuse scoped fallback/recovery; preserve integrity gates only where actually required |

### 17.2 Future extension seams, not hidden implementation scope

- **Toepecker/Gravebrood:** Future authored template + combat-summon lifecycle. Its 15–25 chickens do not fit the initial eight-member pack contract and must not be smuggled in by enlarging procedural pack limits.
- **Revive:** Retain immutable source/provenance so a later player-owned variant can copy only explicitly permitted mechanics. No economic rewards from repeatedly killing revived actors.
- **Golden:** Future economic affix needs separate effect, threat, quantity/quality and anti-farming rules. A cosmetic scarcity label is not an operator.
- **New names:** Add content in an existing tier without changing tier mass when feasibility remains unchanged.
- **New affixes:** Require certified mechanics, constraints, policy/reachability review and versioned ID validation; the AD1 generic pool must not auto-include them.
- **Set/Legendary:** Keep future authored gear-pool work isolated from this monster quantity layer.

---

## 18. Final handoff and authorization boundary

### 18.1 What this document settles

The algorithm weights distinct complete sets, not ownership plans; tier selection uses a conditioned joint draw; name content and generic pool are specified; threat bands and reward formulas are exact; learning's base/cap ambiguity is resolved; inherited minions have an explicit versioning path; missing-native coverage and Normal immunity policy are distinguished; rollback and pending-entitlement recovery are defined; and QA is aligned with the main-game workflow.

### 18.2 What still requires evidence, not more brainstorming

An accepted native baseline, actual role/action certification, bounded compiled-context performance, strict codec compatibility, real public Skill/Passive source readiness, native eight-affix UI readability, and connected owner acceptance. These are engineering or integration deliverables, not reasons to restart the whole architecture discussion.

### 18.3 Suggested next authorization, when the owner chooses

Authorize a bounded first work package: **P0 compatibility/baseline reconciliation and P1 coverage/content validation**, with a named source branch, protected local work, explicit stop/exit criteria and no unrelated tuning. Alternatively authorize pure P2 work in isolation while P0 remains blocked, without calling that branch release-ready.

Do not turn a request to review this architecture into permission for an all-at-once deployment. Each authorized work package must return its changed scope, relevant tests, unresolved gates and exact source evidence.

### 18.4 Delivery state of AD1

This document and its numerical design checks are the only deliverables of architecture preparation. No Hywind source, build, native patch, configuration, release gate or save is changed by this document. No repository tests or native gameplay tests are claimed to have run.

---

## 19. Source and evidence register

References below identify the supplied sources. Source-derived findings are attributed; the new normative algorithms and defaults are AD1 design choices. None of the reviewed local Windows paths establishes an accessible live checkout in this document's preparation environment.

### [S0] Earlier planning handoff

`HYWIND_MASTER_ENEMIES_V1_2_CODEX_ENGINEERING_SPEC.md`  
SHA-256: `50b8c96201fd9ece6fedb1536e47436c3ff457f3911e84f40f0b8df0a7c11b5c`

Basis: six approved decisions; initial ownership, cap, preservation and acceptance requirements. AD1 resolves its provisional learning-base formula, optional naming-sidecar alternative and copied-playable-save language rather than silently retaining contradictory instructions.

### [R1] First Codex review

`Pasted markdown(20261014-022654).md`  
SHA-256: `c53f3e2ba5aecb6b00e8fc6c46351e88a8ae38a22db92d51bddcc5db8352f010`

Basis: source/deployment divergence; integration map; strict nested serialization; public learning gap; pending-generation revision risk; target UI two-line limit; exact-sampler and distinct-set recommendation. This was a read-only review with no builds/tests/deployment.

### [R2] Revised Codex review

`Pasted markdown(20261014-024707).md`  
SHA-256: `7fc70b11bfca9f3a05c190aa56de101e67bd1d5323512da4102b7ebda7442ccc`

Basis: source/dependency status; detailed role capacity matrix and native-certification routes; Normal immunity conflict; revised 16-name and theme table; conditioned tier equations; exact decision-graph design; 27-operator threat assignment; numerical reward/learning fixtures; migration/rollback rules and phased acceptance. This also reports no implementation or tests performed.

### [V11] Existing enemy design contract

`Master_Enemies_v1.1(2).md`  
SHA-256: `050b6d2d7644bb02e85f126e03394282dc4c0ccf330a4fbbf602ec4b62398bbf`

Relevant sections: §00.5 separate acceptance states; §01 distinct identity domains; §02 reuse of current owners; §09.3 preserved native immunities and promotion-budget rejection; §§10.3–10.7 equipment, first-copy acquisition, pity and durable rewards; §11 required target-card information; §16 regression and connected acceptance.

The exact file was available with the conversation attachments during AD1 preparation. Codex's earlier report that this filename was absent describes its inspected local environment, not this attachment set. Do not claim the architecture preparation compared every checked-out implementation against all 3,741 lines of this document.

---

**End of architecture AD1. The next boundary is explicit engineering authorization, not another declaration that the concept is merely feasible.**
