# Master Enemies
## Hywind / Hytale — Codex implementation specification

**Version:** 1.1  
**Date:** 2 October 2026  
**Audience:** Codex, as the implementing engineer  
**Deliverable:** Integrated, playable enemy rarity, passive-affix, pack, immunity, presentation and reward runtime.  
**Status:** Implementation authority and initial balance configuration; not a claim that the code has already been written or verified in-game. v1.1 establishes a project-wide reuse-first rule for systems and content used by Master Enemies.

**v1.1 change:** Codifies the mature-project rule that Codex must discover and reuse native Hytale and existing Hywind systems/content before authoring ME-specific implementations; adds a mandatory per-mechanic reuse audit and decision gate.

---

## 00. Read first: scope, authority and non-negotiable outcomes

Implement this specification as one integrated feature on the current working Hywind branch. Preserve the existing game and its saves. The final implementation must make every one of the **27 admitted monster affixes** executable in an appropriate real encounter. Registration, tags, JSON, a simulator, or an enabled flag alone does not constitute completion.

This document closes the recent design discussion. Values marked **ME decision** are new, concrete starting balance rules, not claims about previously implemented behavior or another game's exact mechanics. They are the defaults to implement, not questions to return to the owner. Exposed balance values remain configurable without changing the contracts.

### 00.1 Requested feature boundary

Included: Normal, Champion, Unique, Super Unique and authored Boss presentation; explicit encounter-minion membership; difficulty-dependent affix counts; all 27 passive affixes specified below; Packbound; elemental and authored Physical immunity; status/control distinctions; tags below the enemy name; rarity palette variants; additional equipment quantity and XP; modest skill/passive learning bonuses; deterministic persistence; server-authoritative rewards; data authoring for Super Uniques; commands and tests.

Excluded: new learned Skills or Passives, new NPC casts, projectiles, projectile multiplication, summons during combat, resurrection, teleport combat behavior, ground hazards, death explosions, new animations, new encounter phases, a new loot generator, a parallel damage/status engine, an in-game graphical Super Unique editor, third-party rendering dependencies, and unrelated player attribute/gear/UI fixes. The graphical editor may be built later over the schema delivered here; file authoring, validation and spawning work now.

**An affix is not a SkillId.** Affixes use typed stat contributions, existing direct-hit composition, existing status application, existing health/resource changes, a bounded passive aura membership query, or a pack-state predicate. They do not appear in the Skill Library, teach themselves, consume Skill slots, or invoke a newly invented monster ability.

### 00.2 Source hierarchy and important corrections

1. Preserve the owner's explicit latest decisions in this document: no active-skill feature creep; minions are a relationship; Packbound is fully protective until its bound guards are defeated; all mechanics are visible; more affixes improve XP/equipment quantity; rarity modestly improves learning.
2. Preserve the current implemented owners and accepted, newer field-level contracts. Do not replace the working branch with an older snapshot.
3. The retrieved **Hytale RPG Master Implementation Specification v1.7.0**, especially §§02.3, 04.4–04.4.2, 05.1–05.2 and Appendix G, supplies the canonical channels, explicit immunity and acquisition constraints. [S1]
4. **Master Affix v1.0**, especially §§2, 4–6, 10 and 12, supplies typed snapshots, hit receipts, one-pass mitigation, status/proc ownership and evidence requirements. This feature extends those owners; it does not copy the 160 gear affixes into monsters. [S2]
5. Current `COMMANDS.md` and the actual registered commands outrank old conversational command suggestions. The role catalog proves combat-capable identities, not natural spawning or permission to award a skill. [S3–S4]
6. `loot-profiles-v1.json` is an actual retrieved example of a picks/no-drop/cap configuration. Preserve the checked-out, accepted version if newer. It is not the different drop table recalled in earlier conversational summaries. [S5]

**Corrections explicitly adopted:** Ordinary elemental resistance remains capped at **75%**. Immunity is an explicit channel flag, **not** resistance reaching 100%. Ordinary penetration does not break immunity. A monster's rarity is not its species, acquisition rarity, existing encounter rank, or item quality. Learning uses the existing Wisdom term and pity; it is not replaced by a bare base-chance multiplier. Learning gains no extra affix-count bonus in this release. XP and equipment quantity do.

### 00.3 Established reuse-first engineering and content policy

Hywind is no longer a greenfield prototype. Master Enemies MUST assume that substantial combat, status, progression, UI, asset, reward, persistence, Skill, Passive and gear-affix infrastructure already exists. **Codex must not invent a new subsystem, effect identity, content asset or parallel implementation merely because doing so is locally convenient.** The default task is to discover the existing owner and extend or reuse it.

For every Master Enemies mechanic, resolve implementation ownership in this order:

1. **Native Hytale owner first.** If the installed Hytale runtime or data already exposes a supported behavior, property, component, modifier, status, NPC capability, animation/effect hook, resource owner, resistance/immunity path, spawn/lifecycle path, UI projection or other primitive that exactly represents the required semantic effect, use or adapt that owner rather than recreating it.
2. **Existing shared Hywind runtime second.** Reuse existing Hywind damage operators, stat providers, status owners, resource owners, protection/immunity rules, aura/support membership, timing systems, deterministic RNG, encounter services, reward/loot/learning systems, persistence, diagnostics and presentation primitives already proven by Skills, Passives, gear affixes or encounters.
3. **Existing Skill/Passive/gear/content implementation third.** If an implemented Passive, Skill, gear affix, encounter effect or other current Hywind content expresses the same mechanic, reuse or extract its underlying shared operator/profile/data instead of duplicating the logic. Reuse the gameplay primitive, not player ownership. A monster MUST NOT be granted a player Skill/Passive, inserted into the Skill Library, equipped with invisible gear, or made dependent on player progression merely to obtain the shared effect.
4. **Master Enemies-specific implementation last.** Create a new ME-specific operator, provider, status-like identity, asset or runtime subsystem only when no compatible native or shared Hywind implementation can satisfy the required semantic contract. New code should be the narrowest adapter/profile necessary, not a second general-purpose engine.

This hierarchy applies to **systems and content**. Before authoring a new status, particle/effect identity, model/texture variant mechanism, sound binding, animation behavior, NPC action, aura provider, modifier type, immunity flag, loot path, reward calculation, UI widget or persistent record, inspect the current working tree and installed Hytale/Hywind content for a usable existing equivalent. Prefer existing IDs and assets when their semantics match. Do not create near-duplicate content just to give Master Enemies its own namespace.

**Semantic equivalence is mandatory.** Similar numbers or names are not enough. Reuse only when ownership, timing, stacking, targeting, mitigation, persistence and cleanup semantics are compatible. If a player Passive contains the desired effect but also contains player-specific progression/UI assumptions, extract or call the underlying shared effect operator/profile; do not instantiate the player Passive on an NPC. If an existing effect is only partially compatible, reuse the compatible primitive and add a thin ME adapter for the missing boundary rather than copying the whole implementation.

Examples of the intended rule:

- `Stone Skin` should reuse the existing Defense/physical-mitigation provider used by current Hywind systems when compatible; it should not implement a second Defense formula.
- `Cold Enchanted`, `Poison Enchanted` and other status-bearing affixes must use the established Chill/Poison/status admission owners and canonical channel mapping; they must not register duplicate ailments.
- `Reflective`, `Vampiric`, `Mana Burn`, `Bulwark`, `Unwavering` and `Unstoppable` must first locate the current damage-receipt, Health/resource, shield, control-immunity and movement-modifier primitives already used by Skills, Passives or gear.
- `Aura Enchanted` must reuse the existing support/passive stat-provider membership machinery and existing stat operators where possible. Its Might/Ward/Haste selectors are ME configuration over shared primitives, not three new Skills or three bespoke aura engines.
- Existing native Hytale NPC movement, targeting, attacks, animations, projectiles and spawn/lifecycle behavior remain authoritative unless the affix explicitly modifies one supported property. Do not recreate an NPC combat controller for rarity monsters.

Before creating any new ME runtime operator or new gameplay-content identity, Codex MUST add a row to `docs/enemies/INTEGRATION_MAP.md` with at least:

```text
ME mechanic / affix
required semantic contract
native Hytale candidates inspected
existing Hywind runtime candidates inspected
existing Skill / Passive / gear / encounter content inspected
chosen owner / shared primitive
adapter required? and exact boundary
reason a new ME-specific primitive is unavoidable, if applicable
tests/evidence proving semantic compatibility
```

A row stating only “not reusable” is insufficient. The implementation must identify the concrete candidates inspected or explain that the relevant registry/category contains none. New general-purpose combat/status/resource/reward engines are prohibited by this specification unless the owner explicitly approves a documented architectural gap after that audit.

This policy is an enduring Hywind engineering invariant, not a one-time optimization for the initial 27 affixes. Future monster affixes, Super Uniques, editor tooling and balance/content passes inherit it unless a later architecture explicitly supersedes it.

### 00.4 Evidence boundary and mandatory initial integration audit

The repository and installed server were not inspected while writing this document. Names of new records/services below are domain contracts, not invented SDK methods. Retrieved design documents identify existing seams but do not establish what is deployed today.

Before code changes, record the branch/commit, dirty files, server JAR and assets hashes, mod JAR hash, current balance revisions, persistence owners, and current passing tests. Produce `docs/enemies/INTEGRATION_MAP.md` mapping every port in §02 to actual symbols/files and identifying any newer accepted contracts. The map MUST also contain the reuse audit required by §00.3 for every admitted affix and shared Master Enemies runtime responsibility. Resolve naming differences by adapting the existing owner; do not create a duplicate subsystem. Preserve newer behavior outside the explicit ME decisions. Record any real conflict instead of silently overwriting it.

The integration audit must inspect both the current Hywind working tree and the installed/native Hytale data/API surfaces relevant to the mechanic. Architecture/example class names in this document never outrank a functioning existing owner. When a reusable primitive exists behind a Skill, Passive, gear affix or native behavior, bind Master Enemies to that primitive rather than duplicating the feature at the content wrapper level.

A missing adapter is implementation work, not permission to ship a permanently disabled admitted affix. Contextual ineligibility is different: Mana Burn correctly does nothing against a creature with no Mana; it must demonstrably drain a legal Mana-bearing target. Every admitted affix needs its positive case, rejection case, integrated native route and tag evidence.

### 00.5 Completion contract

Deliver production code and data, schema validation, stable migration, deterministic unit/integration tests, stress results, documentation, command registration, full affix coverage, and a connected-game QA run on the pinned build. Preserve separate fields for `implemented`, `automatedPassed`, `nativeConnectedPassed` and `ownerAccepted`; do not mark the last field without the owner's actual acceptance. Codex must not represent an automated simulation as a successful live test.

---

## 01. Identity model: do not merge unrelated kinds of rarity

### 01.1 Canonical fields

| Field | Meaning | Values / ownership |
|---|---|---|
| `canonicalRoleId` | Actual creature/combat source identity | Existing canonical role registry; aliases and phase wrappers resolve here. |
| `encounterRank` | Existing authored combat/reward classification | `COMMON`, `SPECIALIST`, `ELITE`, `MINIBOSS`, `BOSS`; preserve current authority. |
| `enemyRarity` | New random/authored enemy distinction | `NORMAL`, `CHAMPION`, `UNIQUE`, `SUPER_UNIQUE`, `BOSS`. |
| `packRole` | Relationship, not rarity | `NONE`, `MEMBER`, `LEADER`, `MINION`. |
| `origin` | Whether this instance can earn ordinary rewards | `NATURAL`, `AUTHORED_ENCOUNTER`, `INITIAL_PACK_MINION`, `COMBAT_SUMMON`, `REVIVED`, `PLAYER_OWNED`, `QA`. |
| `abilityAcquisitionRarity` | Rarity of the one existing taught ability | Existing Common/Rare/Unique ability classification; never overwritten by enemy rarity. |
| `itemQuality` | Rolled gear quality | Existing gear owner; not inferred from enemy name color. |

Champion packs have **members, not a leader and minions**. Initial minions have `enemyRarity=NORMAL`, `packRole=MINION`, a pack/leader binding and their own canonical role. They may receive explicitly inherited effects, but do not roll a separate affix set or inherit leader learning/loot/XP multipliers. Boss rarity is legal only for an existing authored Boss encounter. A Super Unique is not automatically a Boss.

Do not promote `encounterRank` merely because `enemyRarity` changes. A random Unique Trork with a Common authored encounter profile remains `encounterRank=COMMON`. Its extra difficulty/rewards come from the ME rarity layer exactly once. An already-authored Elite remains Elite and receives any explicitly approved rarity layer once.

### 01.2 Rarity baseline — ME decision

Apply these factors **after** the existing role/difficulty stat profile and **before** the individual affix contributions. Factors are not replacements for the authored difficulty multipliers.

| Rarity / relationship | Max Health factor | Direct damage factor | Explicit affix count Normal / Nightmare / Hell | XP rarity factor | Equipment quantity factor | Learning factor |
|---|---:|---:|---|---:|---:|---:|
| Normal | 1.00 | 1.00 | 0 / 0 / 0 | 1.00 | 1.00 | 1.00 |
| Champion | 1.60 | 1.05 | 1 / 1 / 1 | 1.50 | 1.20 | 1.15 |
| Unique | 2.40 | 1.10 | 1 / 2 / 3 | 2.00 | 1.50 | 1.35 |
| Super Unique | 3.00 | 1.15 | Authored fixed + 0 / 1 / 2; total at most 4 | 2.50 | 1.80 | 1.60 |
| Authored Boss | 1.00 | 1.00 | No automatic additions; authored 0–4 | 1.00 | 1.00 | 1.75 |
| Initial bound Minion | 1.15 | 1.00 | 0; selected inherited effects only | 1.05 | 1.00 | 1.05 |

For Minions, the relationship row replaces the Normal rarity factors; it does not stack them with the leader's factors. Existing bosses already own their Health/damage/XP and source loot profile; the 1.00 factors prevent a second Boss multiplier. Super Unique templates may select smaller/larger Health and direct-damage multipliers within §14 limits, but not independent reward multipliers in v1.

The XP factor replaces the old `RarityMultiplier` slot in the shared XP calculation; do not multiply the legacy `unique=2` by another `Unique=2`. Normal actors not participating in this system retain any distinct legacy rare-spawn classification until explicitly migrated (§17).

### 01.3 Explicit affix-count reward supplement

For `n` distinct explicit affix instances on the rewarded actor, let:

```text
extra = clamp(n - 1, 0, 3)
affixXpFactor       = 1 + 0.15 * extra
affixQuantityFactor = 1 + 0.15 * extra
```

Thus 0 or 1 affix gives 1.00, 2 gives 1.15, 3 gives 1.30, 4 gives 1.45. All affixes count equally in v1. Do not introduce guessed per-affix danger weights. Inherited effects, aura sublabels, immunity tags, native abilities, Packbound's live guard counter and Avenger's stacks are not additional affixes. Affix rewards are frozen from the spawn descriptor; a consumed shield or dead guard does not reduce the reward mid-fight.

Minions always use `n=0`; Champions normally use `n=1`. These bonuses apply to XP and managed equipment quantity only. They do not multiply skill/passive learning, Link Insight, rarity tiers of gear, currency, native materials, or player Magic Find.

---

## 02. Runtime ownership and required ports

Implement these as extensions or thin ports over existing owners. New proposed names are not a requirement to duplicate similarly named live classes. **§00.3 is binding on every port below:** the proposed port name describes a responsibility, not permission to create a new service when native Hytale or current Hywind already owns that responsibility.

### 02.0 Reuse decision gate

Before implementing any §02 port or §06 affix card, Codex must complete this decision gate:

```text
A. Does native Hytale already own the exact semantic behavior/property?
   YES -> integrate there through supported APIs/data.
   NO  -> continue.

B. Does a shared Hywind runtime owner already implement the mechanic?
   YES -> reuse/extend that owner.
   NO  -> continue.

C. Does an implemented Skill, Passive, gear affix or encounter already exercise the mechanic?
   YES -> identify and reuse/extract the underlying shared primitive; do not grant the wrapper content to the monster.
   NO  -> continue.

D. Is an ME-specific primitive actually required?
   YES -> document the inspected candidates and exact architectural gap in INTEGRATION_MAP.md, then implement the narrowest adapter/operator possible.
   NO  -> do not add a new system/content identity.
```

The gate applies independently to runtime logic and presentation/content. For example, an affix may need a new ME definition record while still reusing an existing Defense operator, existing native particle, existing status owner and existing nameplate/tag renderer. A new affix ID does not justify new combat arithmetic, new status identities or duplicate assets.

| Responsibility / proposed port | Required existing integration | Contract |
|---|---|---|
| `EnemyEncounterResolver` | Existing encounter/difficulty classifier and role alias registry | Freeze role, level, world mode, rank, origin and source at authorized birth. |
| `EnemyAffixRegistry` | Existing asset/config loading and validation | Immutable 27-definition catalog, typed operators, revisions, weights and constraints. |
| `EnemyAffixSnapshot` | Existing combat snapshot/stat projection | Resolve rarity, own affixes, inherited modifiers and current pack state once per revision. |
| `EnemyPackService` | Existing NPC spawn/lifecycle + encounter owner | Atomic spawn plan, membership, minion deaths, resets, cleanup and Packbound predicate. |
| `EnemyCombatPort` | Existing damage calculator, native damage filters, applied-hit receipts | One vector, one mitigation/application path, no raw HP subtraction. |
| `EnemyStatusPort` | Existing status/proc/cleanse owners | Admit existing statuses and typed timed stat packages; respect resistance and immunity. |
| `EnemyResourcePort` | Existing health, shield and resource owner | Actual damage leech, bounded Mana drain, shield capacity; no UI-local resource mutation. |
| `EnemyTimingPort` | Existing NPC locomotion and attack scheduling | Movement plus post-action recovery rate; preserve attacks, animation timing and telegraphs. |
| `EnemyAuraPort` | Existing ally/support membership machinery | Pack-only stat providers, no cast, tick damage, new skills or unrestricted entity search. |
| `EnemyRewardExtension` | Existing death ledger, XP, loot/claim owner and learning service | One frozen defeat receipt; factors in the correct slots, durable child outcomes. |
| `EnemyPresentationProjection` | Existing target HUD/nameplate and NPC model projection | Name, rank, all affix/immunity tags and palette reference from one authoritative DTO. |

Existing source documents identify `GearAffixRuntime`, `ManagedGearDamageInteraction`, `ManagedGearProjectile`, `SkillExecutionService`, `HytaleSkillExecutionSystem`, `IronSentinelAffixes`, `IronSentinelStatProjection` and `RpgUiProjectionService`. These are seams to locate, not a mandate to route NPC effects through player gear or to equip invisible items on monsters. [S2 §2]

### 02.1 Required immutable instance records

```text
EnemyDescriptor {
  schemaVersion, descriptorRevision, balanceRevision, nativeBindingRevision,
  worldId, encounterId, encounterGeneration, logicalActorId, entityId,
  spawnCycleId, spawnOrigin, canonicalRoleId, nativeRoleId,
  combatLevel, difficulty, encounterRank, enemyRarity, packRole, packId?, leaderId?,
  sourceValidationId, lootSourceId, acquisitionSourceId?,
  nameToken, nameSeed, paletteId?, ownAffixes[], inheritedAffixes[],
  nativeImmunityChannels[], selectedImmunityGrants[], controlProfileId,
  immutableRewardContext, nativeBaselineFingerprint
}
AffixInstance {
  affixId, definitionRevision, parameters, selector?, origin: OWN|INHERITED,
  sourceLeaderId?, contributesToRewardCount, persistedDrawKey
}
EnemyRuntimeState {
  logicalActorId, generation, healthAndResourceOwnerRef, statRevision,
  combatElapsedMillis, activeTimerState, avengerCreditedGuardIds[],
  bulwarkConsumed, acceptedProcLocks, cleanupGeneration
}
EnemyPackRecord {
  packId, worldId, encounterId, generation, state, anchor, birthRoster[],
  leaderId?, guardIds[], deadMemberReceipts[], activeBindingCount,
  sealedRoster, packboundReleased, spawnPlanReceipt, abortReason?
}
```

Use the existing entity Health/resource store, not parallel serialized Health fields. The durable encounter snapshot must reference or transactionally accompany that store for recovery. Runtime state contains only additional affix state. `logicalActorId` survives a phase wrapper or rebind; native entity ID reuse cannot reuse a reward identity.

### 02.2 Unit and numerical conventions

Percentages in configuration are fractions: `.20` means 20%, not 0.2%. Additive resistance values are percentage points represented as fractions. Durations use integer milliseconds; distances use meters in the established game-unit adapter. Use existing deterministic fixed-point types where available. Otherwise use doubles for combat arithmetic and explicit round-half-up only at existing display/XP/item quantization boundaries; reject NaN/infinity.

Stat modifiers are keyed by `(logicalActorId, generation, providerId, field)`. Reapplying the same provider replaces its value, not adds a second copy. Rebuild derived totals from the native baseline and providers; never repeatedly multiply already modified stats.

---

## 03. Spawn promotion, pack construction and difficulty

### 03.1 Eligibility

Run the promotion decision once per **eligible encounter spawn group**, not once per update and not independently for each member of a pre-authored group. Default group outcomes are Normal 92%, Champion pack 2%, Unique pack 6%, identical across difficulties. These are ME initial tuning, not a claimed optimal population distribution.

Eligible: a spawn the current encounter classifier already accepts for hostile combat, with known canonical role, authoritative combat level and normal defeat lifecycle. A neutral/provokable role requires an explicit opt-in encounter binding; promotion does not change allegiance or make civilian wildlife hostile. Exclude protected/story actors, ImmersiveNPCs characters, players, babies, tamed/player-owned creatures, test actors, abstract/templates, objects, attack appendages, native Boss encounters and already-special authored encounters unless their definition opts in. The source role catalog is an input to classification, never an automatic allowlist for promotion or teaching. [S4]

### 03.2 Atomic spawn algorithm

1. Resolve world difficulty and authored role/rank/combat level. Retain source validation and native loot identity.
2. Reserve one encounter ID/generation, deterministic seed and a valid native spawn plan. One duplicate birth callback must resolve the same reservation.
3. Choose rarity once. If a Champion pack is selected, select 2–4 compatible members. If a Unique pack is selected, select one leader plus 3–5 compatible minions. Reuse unengaged members of the same incoming spawn plan before adding actors; never recruit unrelated live monsters from the surrounding world.
4. Require compatible faction, navigation and combat role for each member; use the original pack's approved role mix, otherwise the same canonical role. Preserve meaningful ranged/melee roles. Do not create a new species or overwrite skill-teaching aliases.
5. Reserve spawn capacity before publication. Default limit: 8 members per ME pack; 12 special packs per loaded world and 3 pack anchors per 64×64 horizontal cell, subject to the existing lower native spawn budget. Count published and reserved packs. No per-player copy. Budget exhaustion produces the original unpromoted native spawn for this attempt; do not retry until the next legitimate native spawn cycle.
6. Select legal affixes (§04), resolve immunity (§09), produce projected stats and the complete presentation DTO. Champion members share one selected affix ID/selector and each owns its corresponding instance.
7. Persist the sealed descriptor, membership, seed, immunity decisions and reward context before exposing a special encounter. Spawn all members in a non-interactable staging state through supported APIs. Validate the roster, bindings, navigable placement and defense predicates.
8. Publish all members together. If any required member fails, discard staged special actors without defeat rewards and restore the original spawn plan exactly once. Do not expose a half-built Packbound leader or reroll a more profitable pack on retry.

### 03.3 Placement and lifecycle

Place minions on existing valid ground/navigation within 4–10 m of the leader, sharing its reachable local encounter area. Use the current spawn collision/terrain checks. A Packbound roster must have at least two valid living guards and must fit its reachable 24 m guard area. Ordinary pack anchors have a 32 m leash; use any stricter existing encounter leash. Do not enlarge navigation boundaries just because rarity adds actors.

Freeze level/difficulty/affixes/rewards at birth. A player crossing a biome, using a portal or changing equipment cannot rescale the enemy. Initial Health scaling is applied once before publication. Never refill an injured actor on a UI update, aura refresh, config reload or reconnect.

Natural despawning, world unloading, role-phase transitions and a missing entity reference are **not deaths**. Only the existing authoritative terminal defeat event can create a death receipt. Native phase actors map to one logical actor when the source defines them as one boss; no phase-by-phase payout.

Horde adds two initial minions within the eight-member cap. It never periodically summons. Champions cannot roll Horde, Packbound, Avenger, Empowered Minions or Aura Enchanted; their pack has no leader relation.

---

## 04. Affix selection and compatibility

### 04.1 Closed catalog and selection rules

Only ME-001–ME-027 are admitted. Resolve definitions against the actor's real capabilities, not strings in its name: `DIRECT_HIT`, `PHYSICAL_DIRECT_HIT`, `MOBILE`, `RECOVERY_TIMELINE`, `DEFENSE_STAT`, `PACK_LEADER`, `MINION_ROSTER`, `RESOURCE_TARGETING`, `HOSTILE_STATUS_ADMISSION`, `SHIELD_OWNER` and `APPLIED_HIT_RECEIPTS`.

Use the nonzero per-difficulty integer weight in each affix card. Fixed authored affixes bypass random weights, but not capability checks, hard exclusions or immutable protection rules. Choose without replacement, maximum one elemental family affix, and find a complete legal set using deterministic weighted backtracking. Candidate order and tie-breaking are stable by affix ID. Maximum 4 explicit affixes; the fixed 27-entry catalog makes bounded combination enumeration practical. Cache legal set candidates by capability/profile/difficulty and policy revision; do not enumerate them per combat tick.

If a natural role has no complete legal set for its requested count, that role cannot be promoted to that rarity until a legal binding exists. Preserve the original native spawn, diagnose the role, and fail the content-coverage check for a role explicitly marked as supported. Do not silently create a three-affix Unique with two inert/duplicate affixes. Authored invalid templates fail validation and are not published.

### 04.2 Hard constraints

| Constraint | Rule |
|---|---|
| Elemental family | At most one of Fire, Cold, Lightning, Poison, Wind, Earth, Void Enchanted or Spectral Hit. |
| Leader mechanic | At most one of Avenger, Empowered Minions, Horde, Aura Enchanted or Packbound. |
| Survival | At most one of Magic Resistant, Stone Skin, Vampiric, Bulwark or Packbound. Ward Aura also occupies this slot. |
| Displacement/control pressure | At most one of Cold Enchanted or Knockback. No new Fear/Jailer effect is introduced. |
| Speed/rage | Frenzied and Avenger cannot coexist. Extra Fast can coexist with Frenzied but shares the aggregate cap. |
| Retaliation sustain | Reflective cannot coexist with Vampiric or Packbound. Reflection never feeds any leech. |
| Denial | Mana Burn and Cursed cannot coexist on a random enemy. Both remain legal separately. |
| Immunity + sustain | A two-element-immune random actor cannot have Vampiric, Bulwark or Packbound. Reject the second immunity grant rather than reroll the actor. |
| No-op rejection | Stone Skin requires positive usable Defense; Extra Strong requires a physical direct hit; Magic Resistant must improve at least one non-immune channel; Unwavering/Unstoppable must add a defense not already native. |
| Offensive stacking | At most two major offense providers: Extra Strong, one elemental family, Frenzied, Avenger, or Might Aura. The selector matters. |

These rules apply to Super Uniques too; no `ignoreSafety=true` flag. They limit lethal/tedious combinations without requiring a new difficulty-solving AI.

### 04.3 Shared magnitude ceilings

All ME providers share the following incremental caps relative to the already-resolved native/difficulty/rarity baseline: movement speed at most ×1.50; post-action recovery rate at most ×1.50; additional all-direct Increased at most +60%; additional Physical Increased at most +60%; extra elemental channel power at most 30% of the native source vector; Defense increase at most +100%; non-rarity Max Health increases at most +50%. These do not clamp unrelated existing player buffs or rewrite the native difficulty profile. Affix eligibility/constraints must make each selected affix useful at baseline; later temporary overlap may reach a cap without changing rewards.

Elemental damage is not automatically an ailment. Fire Enchanted has no Burn unless specifically stated in its card; Lightning Enchanted has no retaliation projectile; Cold Enchanted has no death nova. This is an intentional adaptation, not a promise to reproduce Diablo behavior. Blizzard's original bonus page is inspiration only. [H3]

---

## 05. Combat integration: the common execution contract

### 05.1 Source vector, snapshots and damage order

A native role keeps its existing attacks, targeting, trajectory, hit count, animation and combat decisions. Every native melee/projectile/area action that can be affixed must produce a logical root and authored strike IDs. One multi-component hit has one crit result, status/proc budget and application receipt.

At action acceptance/launch build `EnemyOffenseSnapshot`. Let `S[channel]` be its native direct damage vector after the existing native/difficulty/rarity resolution and normal action coefficient but before ME affix power, crit and victim defenses. Let `B=sum(S)`. Do not derive `B` from the player's damage received or reroll it independently for each victim. Same-strike original victims share its sample.

Order:

```text
existing role/difficulty source resolution
 -> rarity direct factor once
 -> freeze S and B
 -> affix extra elemental power (coefficient * B; no conversion/debit)
 -> additive ME Physical / all-direct factors in their typed buckets
 -> existing one crit result and other already-owned source factors once
 -> protection / Packbound gate
 -> per-channel explicit immunity
 -> existing physical Defense or elemental resistance/penetration once
 -> existing absorb, redirect and authoritative Health application
 -> AppliedHitReceipt
 -> permitted status / Mana Burn / leech / reflection intents
```

For the ME extension itself, define `Aall` as its capped all-direct Increased sum and `Aphysical` as its capped Physical Increased sum. With `E[e]` the one extra-elemental contribution, its pre-crit result is `(S[e] + E[e]) * (1 + Aall + (e == PHYSICAL ? Aphysical : 0))`. These are additive eligible ME contributions, not successively compounded multipliers. Do not apply native source factors already contained in S again at this extension boundary.

Extra Strong modifies the Physical component, not the elemental addition. All-direct buffs such as Frenzied apply once to eligible direct components, including the addition. Status periodic damage does not recursively acquire elemental additions or direct buffs. Existing native elemental conversion remains native; this feature adds no conversion rule.

A damage-only contact rejected by protection/Packbound/immunity creates no damage-confirmed status, leech, Mana drain, on-hit resource reward or reflection. Preserve independently authorized player status payloads where §09 permits them.

### 05.2 Native pipeline constraints

The public Hytale damage API describes Gather for submission, Filter for modifying/canceling, and an internal application phase. It forbids submitting new damage inside Filter. Queue reflected children to a later permitted Gather; use an actual post-application dependency/receipt for Health-loss reactions rather than assuming an Inspect callback has already reduced Health. Pin this against the installed SDK. [H1]

No direct Health subtraction, global damage-bypass flags, native protection removal, fake player attacks, or simultaneous native + RPG mitigation of the same component. A runtime that changes the tooltip but not native NPC melee/projectile damage fails completion.

### 05.3 Existing status gateway and effect-source identity

For one applicable status effect per accepted logical strike/victim, use its authored proc coefficient `c` in [0,1] and the existing source-side merge gateway. Pellet/contact duplication cannot create multiple full-coefficient opportunities. Coefficients across simultaneous pellets of one shot sum to at most 1; separately authored sequential strikes retain their established budgets.

```text
q = cardBaseChance * c
combinedSourceChance = 1 - (1 - existingSourceChance) * (1 - q)
statusChance = combinedSourceChance *
               (1 - clamp(targetStatusResistance - sourceStatusPenetration, 0, .75))
```

This is one merged attempt before the existing Fortune Status Escape and status-specific admission rules; never roll a second resistance check after an accepted package. NoProc/Reflect/tick/visual-only events do not enter this gateway. Pair locks in the cards supplement, never replace, native status caps/locks. Consume a chance-opportunity lock when a valid attempt is rolled, whether it succeeds or fails; rejection before eligibility does not consume it.

Extend the existing typed provenance to permit `EffectSourceKind.MONSTER_AFFIX` with a stable `affixId`; do not manufacture a learnable SkillId to satisfy a legacy parameter. Status ownership/stack locks use `(actor logical ID, affix ID, target)` plus existing grouping, not a new source for every projectile. Preserve source credit, generation and root event.

Cold uses WATER; Poison periodic damage uses EARTH; Burn uses FIRE. Do not create Cold, Poison, Arcane or generic Magic as seventh/eighth elemental resistance channels. Preserve current Poison, Chill, Electrified, Slow, Stun and cleanse definitions rather than rewriting their stack/duration rules. [S1 §§02.3,04.3–04.4; S2 §6]

### 05.4 Timing and movement

Extra Fast/Frenzied/Aura Haste may change locomotion and **post-action recovery**, not attack animations, wind-up, release times, hit windows, fixed cooldowns or player global cooldowns. For an NPC action profile with scalable recovery `t`, apply `t / recoveryRate`. Snapshot it when the action starts. No mid-swing shortening; a non-scalable script remains unchanged.

Bind to actual NPC navigation speed and action scheduler fields, or compile immutable supported recovery-profile variants. Do not mutate shared vanilla profiles. Never simulate faster attacks by silently increasing damage or advancing an animation without matching authoritative timings. `RECOVERY_TIMELINE` is a capability to prove on representative roles. Extra Fast remains eligible for mobile roles with no scalable recovery only if the card's movement component is applied and the descriptor explicitly advertises `recoveryBonus=0` for that role; its player description must then omit recovery. This is a legal movement-only specialization, not an unimplemented timing consumer.

### 05.5 Passive reactions and stat packages

Cursed and Armor Breaker use the existing timed negative-modifier/status owner with distinct source keys and UI labels, not a new spell. Benefits granted by auras/minion providers use existing stat buckets, not new active abilities. All packages have source, expiry, stacking policy and cleanup. Hostile packages go through status admission; beneficial provider membership does not roll hostile Status Resistance.

Leech is based on actual Health lost, excluding absorbed damage and overkill. Reflection children are `DERIVED|REFLECT|NO_PROC|NO_CRIT|NO_LEECH|NO_REFLECT`, preserve original attacker/deliverer identities, and never generate enemy reward credit for their defending NPC. An attacking summon receives reflection itself, not its owner.

---

## 06. Complete admitted affix catalog

Every card below is mandatory, with no new active skill. Values are **Normal / Nightmare / Hell**. Weights are random-selection weights; a zero weight means authored-only for that difficulty, not an unfinished operator. Absent special inheritance, minions receive nothing. Every effect still respects §§04–05 and the current protection/ownership rules.

**Implementation interpretation rule:** each card specifies the required gameplay semantics and acceptance behavior; it does **not** authorize a bespoke ME implementation when the same primitive already exists. Apply the reuse decision gate in §§00.3 and 02.0 before writing code for every card. Prefer native Hytale behavior, then shared Hywind runtime, then the underlying operators/profiles already exercised by Skills, Passives, gear affixes or encounters. Only the unresolved semantic delta may be ME-specific.

### ME-001 — Extra Fast

**Stable key:** `extra_fast`  
**Player tag:** `Extra Fast`  
**Random weights N / NM / H:** 100 / 100 / 100  
**Required capabilities:** `MOBILE`

**Parameters:**

```json
{
  "movementIncrease": [
    0.2,
    0.25,
    0.3
  ],
  "recoveryRateIncrease": [
    0.1,
    0.15,
    0.2
  ]
}
```

**Runtime:** Add the movement value to the ME locomotion bucket. Add the recovery-rate value only on certified scalable NPC recovery timelines; t becomes t/(1+bonus). Never speed up wind-up, release, hit windows or animation playback. On a movement-only role freeze recoveryRateIncrease=0 in its resolved instance and describe only movement.

**Minions:** Minions receive half the movement increase, no recovery bonus. Example Hell: +15% movement.

**Eligibility and exclusions:** Reject stationary, scripted-immobile or navigation-incompatible roles. A mobile role is a valid positive case even when its timeline is not scalable. Shared speed caps apply.

**Lifetime, stacking and cleanup:** Persistent provider. Snapshot recovery at action acceptance; locomotion may update at the next safe navigation update. Remove only this provider at cleanup.

**Required positive/negative vectors — ME-001-P/N:** A 4 m/s Hell actor becomes 5.2 m/s without other providers; a 1000 ms scalable recovery becomes 833.333 ms. Minion becomes 4.6 m/s. A fixed 600 ms warning and the attack hit count remain unchanged.

### ME-002 — Extra Strong

**Stable key:** `extra_strong`  
**Player tag:** `Extra Strong`  
**Random weights N / NM / H:** 100 / 100 / 100  
**Required capabilities:** `PHYSICAL_DIRECT_HIT`

**Parameters:**

```json
{
  "physicalIncrease": [
    0.25,
    0.35,
    0.45
  ]
}
```

**Runtime:** Add the value to Physical direct-hit Increased. It affects existing melee, projectile and native Physical direct components, not every channel or separately owned DoT.

**Minions:** Minions receive half the value: +12.5/+17.5/+22.5% Physical direct damage.

**Eligibility and exclusions:** Must have an eligible Physical direct action. Observe the two-major-offense-provider constraint.

**Lifetime, stacking and cleanup:** Persistent offensive provider in accepted action snapshots. Existing launched shots retain their snapshot.

**Required positive/negative vectors — ME-002-P/N:** S=100 Physical +20 Fire on Hell becomes 145 Physical +20 Fire before defenses. An inherited minion has 122.5 Physical +20 Fire.

### ME-003 — Magic Resistant

**Stable key:** `magic_resistant`  
**Player tag:** `Magic Resistant`  
**Random weights N / NM / H:** 40 / 80 / 100  
**Required capabilities:** General actor/defense provider support

**Parameters:**

```json
{
  "elementalResistanceAdd": [
    0.15,
    0.2,
    0.25
  ]
}
```

**Runtime:** Add this resistance to WIND, WATER, FIRE, EARTH, LIGHTNING and VOID through the existing NPC resistance owner. Clamp ordinary effective resistance to .75. Never create an immunity flag and never modify Physical Defense.

**Minions:** None.

**Eligibility and exclusions:** At least one non-immune channel must gain effective resistance. Occupies the survival slot. Overcap raw values may be retained for the established debuff system.

**Lifetime, stacking and cleanup:** Persistent defensive provider; recompute on provider changes. One mitigation owner.

**Required positive/negative vectors — ME-003-P/N:** Raw Fire resistance .40 on Hell becomes .65. With .10 direct penetration effective mitigation is .55 and 100 Fire deals 45. Raw .70 becomes .95, effective .75; it is not Fire Immune.

### ME-004 — Stone Skin

**Stable key:** `stone_skin`  
**Player tag:** `Stone Skin`  
**Random weights N / NM / H:** 60 / 80 / 100  
**Required capabilities:** `DEFENSE_STAT`

**Parameters:**

```json
{
  "defenseIncrease": [
    0.4,
    0.6,
    0.8
  ]
}
```

**Runtime:** Increase the canonical NPC Physical Defense stat by the listed fraction. The existing Physical mitigation curve consumes it once. Do not add an independent percent-Physical-resistance layer or change evasion under the same label.

**Minions:** None.

**Eligibility and exclusions:** Positive usable baseline Defense and a proven native Physical mitigation binding required. Occupies the survival slot. Cannot randomly create Physical immunity.

**Lifetime, stacking and cleanup:** Persistent Defense provider; native Health is untouched.

**Required positive/negative vectors — ME-004-P/N:** Baseline Defense 100 becomes 140/160/180. The same known Physical test hit must use the existing curve exactly once; a Fire-only hit is unchanged.

### ME-005 — Fire Enchanted

**Stable key:** `fire_enchanted`  
**Player tag:** `Fire Enchanted`  
**Random weights N / NM / H:** 70 / 80 / 90  
**Required capabilities:** `DIRECT_HIT`

**Parameters:**

```json
{
  "extraPowerFraction": [
    0.15,
    0.2,
    0.25
  ],
  "resistanceAdd": [
    0.2,
    0.3,
    0.4
  ],
  "channel": "FIRE"
}
```

**Runtime:** Add coefficient × B to FIRE in the same original direct-hit vector and add the listed FIRE resistance. B is the frozen native source vector sum from §05; this is extra power, not conversion. No extra crit or proc opportunity. No automatic Burn and no death explosion.

**Minions:** Minions receive half the extraPowerFraction, but no resistance, status chance or immunity grant.

**Eligibility and exclusions:** At most one elemental-family affix. Resistance does not imply immunity; see separate §09 grants. No other specialized restriction.

**Lifetime, stacking and cleanup:** Persistent offense/defense providers; accepted status packages retain normal owners/lifetimes after source death. No on-death effect.

**Required positive/negative vectors — ME-005-P/N:** At Hell, S=100 Physical creates 100 Physical +25 FIRE before other modifiers. An immune FIRE victim still takes the Physical component; a pure immune-only contact cannot proc the status. Inherited extra power is 12.5. A rejected/root-duplicate contact creates no second application.

### ME-006 — Cold Enchanted

**Stable key:** `cold_enchanted`  
**Player tag:** `Cold Enchanted`  
**Random weights N / NM / H:** 70 / 80 / 90  
**Required capabilities:** `DIRECT_HIT`, `HOSTILE_STATUS_ADMISSION`

**Parameters:**

```json
{
  "extraPowerFraction": [
    0.15,
    0.2,
    0.25
  ],
  "resistanceAdd": [
    0.2,
    0.3,
    0.4
  ],
  "channel": "WATER",
  "status": "CHILL",
  "statusChance": [
    0.2,
    0.25,
    0.3
  ],
  "stacksPerAcceptedPackage": 1,
  "opportunityLockMs": 1000
}
```

**Runtime:** Add coefficient × B to WATER in the same original direct-hit vector and add the listed WATER resistance. B is the frozen native source vector sum from §05; this is extra power, not conversion. No extra crit or proc opportunity. After positive actual Health damage, attempt one existing Chill stack using the listed chance and a 1000 ms actor/target lock. Preserve canonical Chill/Freeze admission; do not author a nova or a separate freeze skill.

**Minions:** Minions receive half the extraPowerFraction, but no resistance, status chance or immunity grant.

**Eligibility and exclusions:** At most one elemental-family affix. Resistance does not imply immunity; see separate §09 grants. Occupies the control-pressure slot.

**Lifetime, stacking and cleanup:** Persistent offense/defense providers; accepted status packages retain normal owners/lifetimes after source death. No on-death effect.

**Required positive/negative vectors — ME-006-P/N:** At Hell, S=100 Physical creates 100 Physical +25 WATER before other modifiers. An immune WATER victim still takes the Physical component; a pure immune-only contact cannot proc the status. Inherited extra power is 12.5. A rejected/root-duplicate contact creates no second application.

### ME-007 — Lightning Enchanted

**Stable key:** `lightning_enchanted`  
**Player tag:** `Lightning Enchanted`  
**Random weights N / NM / H:** 70 / 80 / 90  
**Required capabilities:** `DIRECT_HIT`, `HOSTILE_STATUS_ADMISSION`

**Parameters:**

```json
{
  "extraPowerFraction": [
    0.15,
    0.2,
    0.25
  ],
  "resistanceAdd": [
    0.2,
    0.3,
    0.4
  ],
  "channel": "LIGHTNING",
  "status": "ELECTRIFIED",
  "statusChance": [
    0.2,
    0.25,
    0.3
  ],
  "stacksPerAcceptedPackage": 1,
  "opportunityLockMs": 1000
}
```

**Runtime:** Add coefficient × B to LIGHTNING in the same original direct-hit vector and add the listed LIGHTNING resistance. B is the frozen native source vector sum from §05; this is extra power, not conversion. No extra crit or proc opportunity. After positive actual Health damage, attempt one existing Electrified stack with the listed chance and a 1000 ms actor/target lock. Use the current Electrified owner, duration and strongest Physical-miss rule; no charged bolts, retaliation cast or additional projectile.

**Minions:** Minions receive half the extraPowerFraction, but no resistance, status chance or immunity grant.

**Eligibility and exclusions:** At most one elemental-family affix. Resistance does not imply immunity; see separate §09 grants. No other specialized restriction.

**Lifetime, stacking and cleanup:** Persistent offense/defense providers; accepted status packages retain normal owners/lifetimes after source death. No on-death effect.

**Required positive/negative vectors — ME-007-P/N:** At Hell, S=100 Physical creates 100 Physical +25 LIGHTNING before other modifiers. An immune LIGHTNING victim still takes the Physical component; a pure immune-only contact cannot proc the status. Inherited extra power is 12.5. A rejected/root-duplicate contact creates no second application.

### ME-008 — Poison Enchanted

**Stable key:** `poison_enchanted`  
**Player tag:** `Poison Enchanted`  
**Random weights N / NM / H:** 50 / 70 / 90  
**Required capabilities:** `DIRECT_HIT`, `HOSTILE_STATUS_ADMISSION`

**Parameters:**

```json
{
  "status": "POISON",
  "statusChance": [
    0.3,
    0.35,
    0.4
  ],
  "poisonPowerFraction": [
    0.5,
    0.6,
    0.7
  ],
  "earthResistanceAdd": [
    0.2,
    0.3,
    0.4
  ],
  "stacksPerAcceptedPackage": 1,
  "opportunityLockMs": 1000
}
```

**Runtime:** After positive actual Health damage, attempt one canonical Poison stack. Its fixed source-power override is poisonPowerFraction × B, passed to the existing Poison package creator as already-resolved power; do not multiply enemy stats or a nonexistent Skill rank again. Canonical baseline Poison uses .06 × source power per second, a 6 s source package and its existing stack/replacement policy. Preserve any later accepted shared Poison balance. Add Earth resistance. This affix adds no direct hit channel and no poison cloud.

**Minions:** Minions receive a flat 15% Poison application chance (all modes), one stack with half the leader potency fraction, and the same 1000 ms opportunity lock; no Earth resistance. Minion source identity is its own actor + ME-008, never the leader root.

**Eligibility and exclusions:** Elemental-family slot. This is EARTH periodic damage, not a new Poison resistance channel. Merge with an existing same-source Poison opportunity once. Never multiply separate native and ME Poison stack opportunities.

**Lifetime, stacking and cleanup:** Poison outlives source death only under existing status lifetime rules. Source ownership remains stable across projectiles and pack detach.

**Required positive/negative vectors — ME-008-P/N:** At Normal B=100, source override=50; under the canonical .06 coefficient one stack deals 3 raw Earth DPS, 18 over an uninterrupted 6 s. An Earth-immune target takes no Poison tick damage. A 3-stack source cap cannot be bypassed using different strike IDs.

### ME-009 — Wind Enchanted

**Stable key:** `wind_enchanted`  
**Player tag:** `Wind Enchanted`  
**Random weights N / NM / H:** 50 / 60 / 70  
**Required capabilities:** `DIRECT_HIT`

**Parameters:**

```json
{
  "extraPowerFraction": [
    0.15,
    0.2,
    0.25
  ],
  "resistanceAdd": [
    0.2,
    0.3,
    0.4
  ],
  "channel": "WIND"
}
```

**Runtime:** Add coefficient × B as WIND to the existing direct-hit vector. Add the listed resistance to WIND. No Slow, Root, curse, displacement, zone, summon or extra attack is implicit in the element label.

**Minions:** Minions receive half the extra power fraction, no resistance or immunity.

**Eligibility and exclusions:** One elemental-family affix maximum; all channel/proc/crit safety rules apply.

**Lifetime, stacking and cleanup:** Persistent snapshot and defensive provider. No timers, casts or death effects.

**Required positive/negative vectors — ME-009-P/N:** Hell B=80 adds 20 WIND; the original vector is not debited. With target WIND resistance .50, no penetration and no other factors, that component deals 10. A corresponding explicit immunity rejects only that component.

### ME-010 — Earth Enchanted

**Stable key:** `earth_enchanted`  
**Player tag:** `Earth Enchanted`  
**Random weights N / NM / H:** 50 / 60 / 70  
**Required capabilities:** `DIRECT_HIT`

**Parameters:**

```json
{
  "extraPowerFraction": [
    0.15,
    0.2,
    0.25
  ],
  "resistanceAdd": [
    0.2,
    0.3,
    0.4
  ],
  "channel": "EARTH"
}
```

**Runtime:** Add coefficient × B as EARTH to the existing direct-hit vector. Add the listed resistance to EARTH. No Slow, Root, curse, displacement, zone, summon or extra attack is implicit in the element label.

**Minions:** Minions receive half the extra power fraction, no resistance or immunity.

**Eligibility and exclusions:** One elemental-family affix maximum; all channel/proc/crit safety rules apply.

**Lifetime, stacking and cleanup:** Persistent snapshot and defensive provider. No timers, casts or death effects.

**Required positive/negative vectors — ME-010-P/N:** Hell B=80 adds 20 EARTH; the original vector is not debited. With target EARTH resistance .50, no penetration and no other factors, that component deals 10. A corresponding explicit immunity rejects only that component.

### ME-011 — Void Enchanted

**Stable key:** `void_enchanted`  
**Player tag:** `Void Enchanted`  
**Random weights N / NM / H:** 50 / 60 / 70  
**Required capabilities:** `DIRECT_HIT`

**Parameters:**

```json
{
  "extraPowerFraction": [
    0.15,
    0.2,
    0.25
  ],
  "resistanceAdd": [
    0.2,
    0.3,
    0.4
  ],
  "channel": "VOID"
}
```

**Runtime:** Add coefficient × B as VOID to the existing direct-hit vector. Add the listed resistance to VOID. No Slow, Root, curse, displacement, zone, summon or extra attack is implicit in the element label.

**Minions:** Minions receive half the extra power fraction, no resistance or immunity.

**Eligibility and exclusions:** One elemental-family affix maximum; all channel/proc/crit safety rules apply.

**Lifetime, stacking and cleanup:** Persistent snapshot and defensive provider. No timers, casts or death effects.

**Required positive/negative vectors — ME-011-P/N:** Hell B=80 adds 20 VOID; the original vector is not debited. With target VOID resistance .50, no penetration and no other factors, that component deals 10. A corresponding explicit immunity rejects only that component.

### ME-012 — Spectral Hit

**Stable key:** `spectral_hit`  
**Player tag:** `Spectral Hit`  
**Random weights N / NM / H:** 30 / 50 / 70  
**Required capabilities:** `DIRECT_HIT`

**Parameters:**

```json
{
  "extraPowerFraction": [
    0.2,
    0.25,
    0.3
  ],
  "channels": [
    "WIND",
    "WATER",
    "FIRE",
    "EARTH",
    "LIGHTNING",
    "VOID"
  ]
}
```

**Runtime:** For each accepted authored strike, choose one of the six channels with an equal deterministic draw. Add coefficient × B in that channel to the original hit vector. All original victims and pellets belonging to that strike use that selection; do not resample on collision or after discovering target immunity. No resistance and no status application is granted.

**Minions:** Minions use half the fraction and their own stable strike selection.

**Eligibility and exclusions:** One elemental-family affix maximum. Selection is frozen with the offense snapshot, never rerolled because damage is resisted.

**Lifetime, stacking and cleanup:** Persist the chosen channel when an action is persisted; regeneration/replay uses the same root draw.

**Required positive/negative vectors — ME-012-P/N:** Hell B=100 and a FIRE selection adds 30 Fire to every original victim. A Fire-immune target rejects it; the same action is not rerolled to Water for that target.

### ME-013 — Mana Burn

**Stable key:** `mana_burn`  
**Player tag:** `Mana Burn`  
**Random weights N / NM / H:** 25 / 50 / 70  
**Required capabilities:** `DIRECT_HIT`, `APPLIED_HIT_RECEIPTS`

**Parameters:**

```json
{
  "maxManaFraction": [
    0.02,
    0.03,
    0.04
  ],
  "damageFractionCap": 0.25,
  "opportunityLockMs": 1000,
  "victimAggregateMaxManaFractionPerSecond": 0.08
}
```

**Runtime:** After positive actual Health damage, remove min(modeFraction × victim total Max Mana, .25 × actualHpLoss, currentSpendableMana, remainingVictimDrainBudget). Debit the shared resource owner once. Never consume reserved Mana, change maximum Mana or convert a failed debit into Health damage. No chance roll; one actor/victim opportunity per 1000 ms. All ME Mana Burn sources together may drain at most 8% of that victim Max Mana in any rolling second.

**Minions:** None.

**Eligibility and exclusions:** A legal target must actually expose a Mana pool. Zero-Mana creatures are an expected contextual no-op. Random Cursed + Mana Burn is excluded. Damage admission must precede resource mutation.

**Lifetime, stacking and cleanup:** Maintain rolling victim drain receipts across source death and world-local callbacks; swaps/reconnect cannot reset a live admission window. Normal resource persistence owns the resulting balance.

**Required positive/negative vectors — ME-013-P/N:** Hell victim MaxMana=100, spendable=40, actualHpLoss=20 loses 4 Mana. With actualHpLoss=4 it loses 1. A fully absorbed hit or zero-spendable pool loses 0. Three different attackers cannot exceed 8 Mana/rolling second.

### ME-014 — Cursed

**Stable key:** `cursed`  
**Player tag:** `Cursed`  
**Random weights N / NM / H:** 30 / 50 / 60  
**Required capabilities:** `DIRECT_HIT`, `HOSTILE_STATUS_ADMISSION`

**Parameters:**

```json
{
  "statusChance": [
    0.25,
    0.3,
    0.35
  ],
  "outgoingDirectReduction": [
    0.1,
    0.125,
    0.15
  ],
  "durationMs": 4000,
  "opportunityLockMs": 2000,
  "statusPackage": "ME_WEAKENED"
}
```

**Runtime:** After positive actual Health damage, attempt the existing timed negative-stat package with the displayed status name Weakened: reduce victim outgoing direct damage by the listed fraction for 4 s. This is a debuff payload, not a cast of Weakening Hex and not a new learnable skill. Different Cursed sources use the strongest magnitude; equal/weaker applications cannot extend a stronger provider. Independent provider expiry remains tracked. It does not reduce DoT, healing, movement, resources or item stats.

**Minions:** None.

**Eligibility and exclusions:** Use the canonical hostile status gateway/cleanse family. At most one random denial affix with Mana Burn. Do not substitute an unimplemented curse spell or silently amplify incoming damage instead.

**Lifetime, stacking and cleanup:** Source-keyed expiring package persists for its remaining duration when the NPC dies, unless the existing cleanse removes it. Same-source successful reapplication may refresh its own 4 s provider after the 2 s lock.

**Required positive/negative vectors — ME-014-P/N:** Hell accepted package changes victim direct 100 to 85 for exactly 4 s. Two equal providers still give 85, not 72.25. Status resistance .50 with c=1 gives .175 chance before Fortune Escape, not two resistance rolls.

### ME-015 — Knockback

**Stable key:** `knockback`  
**Player tag:** `Knockback`  
**Random weights N / NM / H:** 35 / 50 / 60  
**Required capabilities:** `DIRECT_HIT`, `HOSTILE_STATUS_ADMISSION`

**Parameters:**

```json
{
  "statusChance": [
    0.2,
    0.25,
    0.3
  ],
  "horizontalDisplacementMeters": [
    0.5,
    0.65,
    0.8
  ],
  "opportunityLockMs": 3000,
  "verticalDisplacementMeters": 0
}
```

**Runtime:** After positive actual Health damage, attempt a bounded horizontal displacement away from the delivery actor using the existing knockback/control admission and terrain collision owner. Request the listed displacement, with zero added vertical launch. If actor and victim centers coincide, use the strike forward vector. Stop at blocking terrain; do not teleport to the desired end position.

**Minions:** None.

**Eligibility and exclusions:** Existing boss/player protection and control resistance remain authoritative. Cold Enchanted + Knockback is excluded. No native hit plus second duplicate affix displacement: merge the requested affix magnitude with the existing same-strike displacement using the stronger permitted request.

**Lifetime, stacking and cleanup:** One admitted displacement intent and a 3 s actor/victim opportunity lock. No persistent controller or new animation.

**Required positive/negative vectors — ME-015-P/N:** Normal unobstructed permitted victim receives at most .50 m requested displacement; a wall .20 m away clips it to the collision-safe distance. Protected/Boss-disallowed target receives none; duplicate damage components do not double it.

### ME-016 — Vampiric

**Stable key:** `vampiric`  
**Player tag:** `Vampiric`  
**Random weights N / NM / H:** 25 / 45 / 60  
**Required capabilities:** `DIRECT_HIT`, `APPLIED_HIT_RECEIPTS`

**Parameters:**

```json
{
  "healthLeechFraction": [
    0.05,
    0.07,
    0.1
  ],
  "maxOwnHealthFractionPerSecond": 0.01
}
```

**Runtime:** Heal the living attacking NPC for modeFraction × actual Health lost by valid hostile targets, after overkill exclusion. Aggregate at most 1% of its current resolved Max Health in a rolling second. Heal through the shared owner, at most missing Health. No delayed pool, spell, channel or over-heal shield.

**Minions:** None.

**Eligibility and exclusions:** Only original direct hits; no reflected/NoLeech/derived/proc/tick damage. Cannot coexist with Reflective or another survival affix. Reject a second immunity grant on this actor.

**Lifetime, stacking and cleanup:** Rolling source budget survives same-encounter rebind. A dead actor cannot be resurrected by a late hit receipt.

**Required positive/negative vectors — ME-016-P/N:** Hell actualHpLoss=40 requests 4 healing. With only 1 missing HP heal 1. With 1000 MaxHP aggregate healing cannot exceed 10/second. A 100-power overkill on a 5-HP victim requests .5, not 10.

### ME-017 — Unwavering

**Stable key:** `unwavering`  
**Player tag:** `Unwavering`  
**Random weights N / NM / H:** 25 / 40 / 55  
**Required capabilities:** General actor/defense provider support

**Parameters:**

```json
{
  "statusImmunityFamilies": [
    "STUN_STAGGER"
  ]
}
```

**Runtime:** Add immunity to the canonical Stun/Stagger control family. Do not infer Freeze, Root, Slow, knockback, Taunt, damage or all-status immunity. Nameplate also projects Stun Immune from the resulting defense state.

**Minions:** None.

**Eligibility and exclusions:** Must add a defense the native control profile does not already have. Bosses already immune to Stun cannot use this as a reward-padding affix.

**Lifetime, stacking and cleanup:** Persistent source-keyed immunity provider; removing the provider does not remove independent native immunity.

**Required positive/negative vectors — ME-017-P/N:** A valid Stun is rejected with STATUS_IMMUNE and no duration/stack mutation. A valid Poison or Root remains subject to its ordinary defenses. Duplicate native Stun immunity makes the affix ineligible.

### ME-018 — Unstoppable

**Stable key:** `unstoppable`  
**Player tag:** `Unstoppable`  
**Random weights N / NM / H:** 25 / 40 / 55  
**Required capabilities:** General actor/defense provider support

**Parameters:**

```json
{
  "immuneMovementComponent": "SLOW_ONLY"
}
```

**Runtime:** Ignore hostile movement-speed-reduction components, including the movement component of Chill, through the existing modifier resolver. Reject a pure Slow package; allow unrelated components of compound statuses under their own rules. Chill stacks may still build and Freeze can still immobilize the actor. Root/Freeze/Stun are hard control and are NOT bypassed. Nameplate additionally shows Slow Immune.

**Minions:** None.

**Eligibility and exclusions:** Requires ordinary mobility and a native profile not already Slow-immune. This is not a blanket minimum-speed hack that overrides a stun, root, script, wall or leash.

**Lifetime, stacking and cleanup:** Persistent movement-component immunity provider. Do not delete unrelated status records.

**Required positive/negative vectors — ME-018-P/N:** A 4 m/s actor under a -30% pure Slow still moves at its normal allowed 4 m/s. A valid Root still immobilizes it. Chill retains stack behavior but does not reduce movement before an accepted Freeze.

### ME-019 — Frenzied

**Stable key:** `frenzied`  
**Player tag:** `Frenzied`  
**Random weights N / NM / H:** 25 / 45 / 60  
**Required capabilities:** `DIRECT_HIT`

**Parameters:**

```json
{
  "cycleMs": 12000,
  "activeWindowStartMs": 8000,
  "activeWindowDurationMs": 4000,
  "allDirectIncrease": [
    0.2,
    0.25,
    0.3
  ],
  "movementIncrease": [
    0.1,
    0.125,
    0.15
  ],
  "recoveryRateIncrease": [
    0.1,
    0.125,
    0.15
  ]
}
```

**Runtime:** Use accumulated engaged-combat time. In each 12 s cycle, the final 4 s grant the listed direct damage and compatible movement/recovery providers. No cast, animation, new attack or hard-control immunity. The first activation begins after 8 s of engaged time, not immediately at spawn. Inactive tag is Frenzied; active state adds Enraged.

**Minions:** None.

**Eligibility and exclusions:** Cannot combine with Avenger. Mobile/timing components apply only to certified capabilities and are omitted from resolved detail text otherwise. Direct damage is the required positive component. Respect the offense and speed caps.

**Lifetime, stacking and cleanup:** Persist combatElapsedMillis modulo cycle. It advances while the encounter is engaged and loaded, pauses while suspended, and does not restart from a favorable window on a brief leash break. Cleanup removes providers.

**Required positive/negative vectors — ME-019-P/N:** At engaged time 7999 ms the buff is absent; at 8000 it is active; at 12000 absent. Hell direct 100 becomes 130 in-window. An action already accepted before the change keeps its old offense snapshot.

### ME-020 — Avenger

**Stable key:** `avenger`  
**Player tag:** `Avenger {stacks}/5`  
**Random weights N / NM / H:** 15 / 35 / 50  
**Required capabilities:** `PACK_LEADER`, `MINION_ROSTER`, `DIRECT_HIT`

**Parameters:**

```json
{
  "perDefeatedMinionDirectIncrease": [
    0.05,
    0.06,
    0.07
  ],
  "perDefeatedMinionMovementIncrease": 0.02,
  "maxStacks": 5
}
```

**Runtime:** Each distinct initial bound minion authoritative death adds one permanent encounter stack, up to five. Each stack grants the listed direct increase and +2% movement on mobile roles. Only the sealed roster counts. This deliberately consolidates the Avenger/Soul Eater idea into one bounded mechanic; no eating arbitrary nearby corpses, growing model, new attacks or escalating unlimited stacks.

**Minions:** None; minions provide death events, not copies of Avenger.

**Eligibility and exclusions:** Leader mechanic slot; cannot coexist with Packbound, Horde, Empowered Minions, Aura Enchanted or Frenzied. Minion despawn/unload/phase change does not grant a stack.

**Lifetime, stacking and cleanup:** Persist the set of credited logical minion IDs and stack count. Source death clears its providers. Revival elsewhere cannot produce another stack for the same logical member.

**Required positive/negative vectors — ME-020-P/N:** Three legitimate minion deaths on Hell give +21% direct and +6% movement. A repeated death callback is ignored. Ten unrelated corpse deaths give zero. Five stacks cap direct at +35%.

### ME-021 — Empowered Minions

**Stable key:** `empowered_minions`  
**Player tag:** `Empowered Minions`  
**Random weights N / NM / H:** 25 / 45 / 60  
**Required capabilities:** `PACK_LEADER`, `MINION_ROSTER`

**Parameters:**

```json
{
  "minionMaxHealthIncrease": [
    0.15,
    0.2,
    0.25
  ],
  "minionDirectIncrease": [
    0.1,
    0.125,
    0.15
  ],
  "minionMovementIncrease": [
    0.05,
    0.075,
    0.1
  ]
}
```

**Runtime:** At initial pack construction add these providers to each sealed initial minion, on top of the Minion baseline. The leader gains none. This is the final snapshot strength of the initial minions, not an aura and not a repeating heal.

**Minions:** This affix exists only as its explicit minion projection; minions display Empowered. It never propagates to a minion's summons or unrelated actors.

**Eligibility and exclusions:** Leader mechanic slot. At least two valid initial minions. Must reserve/validate health and movement projections before publishing the pack.

**Lifetime, stacking and cleanup:** Minion providers persist for that minion's lifetime even if the leader dies. Do not repeatedly refill Health when inspecting or relinking. Initial Health is set once from the final pre-publication Max Health.

**Required positive/negative vectors — ME-021-P/N:** Hell base-role MaxHP=100 -> Minion baseline115 -> empowered143.75. Direct baseline100 becomes115; leader stats do not change. Reapplying the provider keeps143.75 rather than multiplying again.

### ME-022 — Horde

**Stable key:** `horde`  
**Player tag:** `Horde`  
**Random weights N / NM / H:** 15 / 25 / 35  
**Required capabilities:** `PACK_LEADER`, `MINION_ROSTER`

**Parameters:**

```json
{
  "additionalInitialMinions": 2,
  "maximumPackMembers": 8
}
```

**Runtime:** Add two minions to the initial spawn plan, subject to the eight-member total. For a normal 3–5-minion Unique this produces 5–7 minions plus leader. All are the same approved initial encounter origin; no timed reinforcements or spawn-on-death effect.

**Minions:** None. Extra members use ordinary Minion projections.

**Eligibility and exclusions:** Leader mechanic slot. If two additional actors cannot be reserved and legally placed, Horde is not an eligible choice for that spawn plan. Never quietly award the tag without the two extra minions.

**Lifetime, stacking and cleanup:** The extra roster is sealed and persisted at birth. Unload/reload cannot spawn two additional members again.

**Required positive/negative vectors — ME-022-P/N:** A four-minion plan becomes six minions plus one leader. An existing six-minion authored plan cannot randomly select Horde under the eight-member cap. Replaying publication still yields six, not eight, minions.

### ME-023 — Aura Enchanted

**Stable key:** `aura_enchanted`  
**Player tag:** `Aura: {selectorLabel}`  
**Random weights N / NM / H:** 20 / 35 / 50  
**Required capabilities:** `PACK_LEADER`, `MINION_ROSTER`

**Parameters:**

```json
{
  "selectorWeights": {
    "MIGHT": 1,
    "WARD": 1,
    "HASTE": 1
  },
  "radiusMeters": 8,
  "membershipUpdateMs": 250,
  "providerExpiryMs": 500,
  "mightPhysicalIncrease": [
    0.15,
    0.2,
    0.25
  ],
  "wardElementalResistanceAdd": [
    0.1,
    0.15,
    0.2
  ],
  "hasteMovementIncrease": [
    0.1,
    0.125,
    0.15
  ],
  "hasteRecoveryRateIncrease": [
    0.05,
    0.075,
    0.1
  ]
}
```

**Runtime:** Freeze one selector at birth: Might adds Physical direct Increased; Ward adds the six ordinary elemental resistances; Haste adds movement and compatible post-action recovery. Use the existing passive support/stat-provider membership system; these are three affix parameter sets, not newly registered Skills. Affect the living leader and same-pack living minions of matching allegiance within 8 m and line of sight. No players, pets, other packs, damage pulses, reservations or automatic skill cast.

**Minions:** A minion receives a temporary provider only while in range/LOS. It is not an aura emitter. Show the active short label Might/Ward/Haste; do not count it as an explicit affix.

**Eligibility and exclusions:** Leader mechanic slot. Might requires a meaningful Physical recipient and occupies an offense slot. Ward occupies a survival slot. Haste requires a meaningful mobile/scalable recipient. Reject a useless selector before the draw. Same effect from overlapping allowed providers uses the strongest magnitude, not additive copies.

**Lifetime, stacking and cleanup:** Reconcile bounded membership every 250 ms; provider lease expires after 500 ms without refresh. Death/abort/allegiance change removes membership immediately on the same world owner. Persist selector, not transient membership. Recompute on load before publication.

**Required positive/negative vectors — ME-023-P/N:** Hell Might turns minion Physical100 into125 at7.99m/LOS. At8.01m it loses that provider on the next membership pass. A nearby different pack receives nothing. Ward cannot produce immunity from an overcap number.

### ME-024 — Packbound

**Stable key:** `packbound`  
**Player tag:** `Packbound: {guardLabel}`  
**Random weights N / NM / H:** 8 / 18 / 25  
**Required capabilities:** `PACK_LEADER`, `MINION_ROSTER`

**Parameters:**

```json
{
  "minimumInitialGuards": 2,
  "maximumInitialGuards": 5,
  "guardAreaMeters": 24,
  "allDamageBlocked": true,
  "allExternalStatusMutationsBlocked": true
}
```

**Runtime:** While any sealed bound guard remains alive, reject every damage component and external status mutation on the leader, including positive buffs, negative effects, control, dispels, resource drains and forced movement. This is a mandatory pre-mitigation protection gate. Existing intrinsic encounter/affix stat providers are not external status applications and keep operating. On the last guard's authoritative defeat, permanently release this protection for the current encounter generation. Full algorithm is in §08.

**Minions:** None. Guards receive Guard of {leaderName}, never invulnerability. No member can itself have Packbound, and no chained protection graph is legal.

**Eligibility and exclusions:** Unique/Super Unique only; Boss opt-in only through an explicit existing encounter binding. Leader/survival slots. No Horde, Avenger, Empowered Minions, Aura, Reflective, resurrection, combat summons added to the guard roster, or two-element-immune actor. Reject Physical-immune guards.

**Lifetime, stacking and cleanup:** Persistent sealed roster and monotonic release state. Missing/unloaded guards do not count as dead. An unrecoverable guard aborts the remaining encounter without new rewards; it does not conveniently release protection. See §08.

**Required positive/negative vectors — ME-024-P/N:** With one living guard,999999 damage, execute, Poison, stun, slow, heal-over-time status and dispel are rejected. A normal direct heal is not a status but is also blocked as an external resource mutation while guarded. When the last valid defeat is committed, subsequent hits in authoritative event order work. Duplicate death never toggles protection back on.

### ME-025 — Armor Breaker

**Stable key:** `armor_breaker`  
**Player tag:** `Armor Breaker`  
**Random weights N / NM / H:** 25 / 40 / 55  
**Required capabilities:** `DIRECT_HIT`, `HOSTILE_STATUS_ADMISSION`

**Parameters:**

```json
{
  "statusChance": [
    0.2,
    0.25,
    0.3
  ],
  "defenseReduction": [
    0.15,
    0.2,
    0.25
  ],
  "durationMs": 4000,
  "opportunityLockMs": 2000,
  "statusPackage": "ME_ARMOR_BROKEN"
}
```

**Runtime:** After positive actual Health damage, attempt a source-keyed negative modifier reducing the target's existing Physical Defense by the listed fraction for 4 s. Use the canonical stat-package/status owner and player effect presentation. Different Armor Breaker sources use strongest magnitude with independent expiries. It does not damage durability, subtract raw item values, reduce elemental resistance or make Defense negative.

**Minions:** None.

**Eligibility and exclusions:** A target with zero Defense is an expected no-op. Existing Armor Shred effects share the same canonical reduction group: use the stronger total group rule already owned, never two multiplicative Armor Breaker copies. No new attack or armor-destruction subsystem.

**Lifetime, stacking and cleanup:** Same-source successful reapplication refreshes its own4s provider after the2s opportunity lock. Cleanse removes the provider through the ordinary negative-effect route. Unequipping armor recomputes from current valid Defense, not a stale absolute subtraction.

**Required positive/negative vectors — ME-025-P/N:** Hell Defense200 becomes150 for 4 s; a second equal source remains150. A target changing baseline to100 while affected has75. Expiry restores current100, not obsolete200.

### ME-026 — Reflective

**Stable key:** `reflective`  
**Player tag:** `Reflective`  
**Random weights N / NM / H:** 10 / 20 / 30  
**Required capabilities:** `APPLIED_HIT_RECEIPTS`

**Parameters:**

```json
{
  "healthDamageReflectFraction": [
    0.06,
    0.08,
    0.1
  ],
  "maxVictimHealthFractionPerHit": 0.01,
  "maxVictimHealthFractionPerSecond": 0.02,
  "maximumOriginDistanceMeters": 24
}
```

**Runtime:** When the living defender survives a positive original direct Health-damage receipt, enqueue a PHYSICAL reflection child against the actual delivery actor if still hostile, alive and in the same world within24m. Raw reflected power is min(fraction × defender actualHpLoss,1% recipientMaxHP,remaining2%-of-recipientMaxHP rolling1s global ME reflection budget). Victim defenses apply once. All ME reflectors share the recipient budget. No projectile, beam, death retaliation, reflection chain or source-leech.

**Minions:** None.

**Eligibility and exclusions:** Cannot coexist with Vampiric or Packbound. Derived/NoReflect/Reflect/DoT hits are ineligible. Blocked/absorbed/overkill portions produce no power. Revalidate protection and distance at submission. Use a later Gather, never Filter.

**Lifetime, stacking and cleanup:** Pending child carries defender generation and original receipt ID. Cancel it if the defender has died before submission, world changes, or generation is invalid. Persist admitted budget during recoverable event replay.

**Required positive/negative vectors — ME-026-P/N:** Hell defender loses100 Health; attacker MaxHP200 -> raw reflection min(10,2)=2. Two admitted returns may total4 raw power/second, not more. With attacker50% applicable Physical mitigation, a2-power child applies1. The return cannot reflect or leech.

### ME-027 — Bulwark

**Stable key:** `bulwark`  
**Player tag:** `Bulwark`  
**Random weights N / NM / H:** 20 / 35 / 50  
**Required capabilities:** `SHIELD_OWNER`

**Parameters:**

```json
{
  "initialShieldMaxHealthFraction": [
    0.15,
    0.2,
    0.25
  ],
  "regenerates": false
}
```

**Runtime:** Create one initial absorb capacity equal to the listed fraction of resolved Max Health using the existing shield/absorb owner. The shield has no timer, regeneration, recast or invulnerability window. It is consumed by ordinary permitted damage; display Shielded while positive and retain Bulwark as the explicit affix label after depletion. Do not count this capacity as extra max Health for leech or XP.

**Minions:** None.

**Eligibility and exclusions:** Survival slot. Cannot coexist with Packbound or a second immunity grant. Source-keyed capacity must not add to itself on reload. Existing shield-order rules remain authoritative.

**Lifetime, stacking and cleanup:** Persist remaining capacity and consumed flag with the existing shield owner. Full depletion is permanent for the encounter generation. Routine reset/reload does not restore it; only a legitimate fresh spawn cycle does.

**Required positive/negative vectors — ME-027-P/N:** Hell resolvedMaxHP1000 creates250 absorb. A300 admitted hit consumes250 and then applies the remaining50 through the established application pipeline without a second mitigation pass. Reload after depletion does not create another250.

### 06.28 Catalog integration clarifications

All on-hit additions from this catalog require positive **actual Health damage** unless their card explicitly triggers before damage (elemental composition), on state/death (Packbound/Avenger) or through passive membership. This restriction applies to the added ME opportunity only; it does not remove an existing native attack's independent status rule. Fully absorbed hits therefore do not activate ME Poison, Chill, Electrified, Cursed, Armor Breaker, Mana Burn or Vampiric. A shield-breaking hit with positive remaining Health damage can activate them once.

Use typed source-power overrides for Monster Affix Poison; do not register a fake Poison skill or apply a player skill-level multiplier. Shared status caps, resistance, Fortune Escape, cleansing and damage-channel ownership remain unchanged. Canonical current status timings from the checked-out implementation are the integration baseline; the document does not reinstate superseded historical status rules. The positive Poison vector explicitly identifies the documented .06/6 s baseline so a newer accepted coefficient difference is visible in the integration report, not hidden.

The common negative-stat package operators used by Cursed and Armor Breaker are part of the admitted runtime and must be completed in the existing status owner if the generic adapter is incomplete. New effect profile IDs (`ME_WEAKENED`, `ME_ARMOR_BROKEN`) are permitted data; new casts/Skills or duplicate effect engines are not.

For group constraints, Aura selector MIGHT adds OFFENSE and selector WARD adds SURVIVAL in addition to LEADER. Add the corresponding flags before validating the full set. Packbound's guard count is not an additional rolled affix. Every inherited effect has a projected tag even though it contributes zero to `n`.

---

## 07. Initial-minion inheritance, ownership and credit

### 07.1 Explicit propagation, not arbitrary descriptor cloning

The cards are authoritative. The admitted inheritance is limited to Extra Fast movement; Extra Strong Physical damage; half direct extra power for Fire/Cold/Lightning/Wind/Earth/Void/Spectral; the specifically reduced Poison package; Empowered Minions; and temporary membership in a leader's one Aura. Do not copy the leader's entire snapshot, HP multiplier, rank, immunity, source skill, all-status resistance, XP, item quantity or learning chance.

Inherited persistent providers are frozen per initial minion and remain after leader death. Aura providers expire/remove when the leader stops providing them. Neither behavior requires a new skill. A leader's tag is not automatically printed on every minion: project only the effects actually present on that minion.

### 07.2 Death and reward distinction

Initial encounter minions are legitimate finite encounter actors and may earn normal, individually eligible XP/equipment/learning, using their own canonical source and the Minion factors. This is an explicit extension to the old ban on *combat-summoned/revived* farming actors, not permission for every NPC spawn call to award loot. `COMBAT_SUMMON`, `REVIVED`, `PLAYER_OWNED` and `QA` remain ineligible for economic awards. A sealed, persisted initial spawn plan is required to grant `INITIAL_PACK_MINION` provenance.

An initial minion can teach only the existing skill/passive assigned to its own canonical source. A Skeleton Archer accompanying an authored different-species leader does not teach the leader's ability. Removing the leader does not revoke previously earned minion rewards. Each minion defeat has a distinct persistent logical actor/defeat receipt; a pack ID is not permission to pay the leader reward once for every member.

### 07.3 Allegiance and conversion

Do not permit charm, taming, player ownership or other allegiance conversion to leave an active sealed protection graph in an inconsistent state. For ordinary packs, detach via the existing conversion transaction and suppress future enemy economic rewards as appropriate. Packbound guards and its guarded leader reject ownership-changing interactions until protection releases. This is an encounter ownership rule, not a status immunity granted to guards. Do not kill or silently delete a guard to complete a conversion.

---

## 08. Packbound — full state machine and failure safety

This is a shared protection predicate, not a shield pool or 100% resistance modifier.

### 08.1 State machine

```text
RESERVED -> STAGED -> GUARDED -> RELEASED -> DEFEATED
                    |              |
                    +-> SUSPENDED <-+
                    |              |
                    +----> ABORTED <-+
```

`SUSPENDED` stores the prior guarded/released state; it is not a new protective reset. `packboundReleased` is monotonic for one encounter generation. Release happens only when **every exact sealed guard ID** has a legitimate terminal defeat receipt. A guard absent from the current ECS view remains alive/unknown, not defeated. Neither a minion from another pack nor a new combat summon can enter the sealed guard set.

The published leader must be GUARDED or RELEASED. Staged actors are not targetable and grant no rewards. Publish the guard roster before enabling the leader's normal AI; there is no one-frame vulnerable window.

### 08.2 Protection gate coverage

While guarded, reject direct and periodic damage in every canonical/native mapped channel, reflected damage, environmental combat damage, execute/current-Health/maximum-Health damage, raw hostile resource mutation, forced movement, all external status applications/refreshes/stacks/removals/cleanses, and external Health/resource healing. The effect cannot be dispelled, stunned, bypassed by an ignored-defense flag, or removed by elemental penetration.

Intrinsic encounter fields, bound stat providers and the leader's ordinary AI/resource spending are not external statuses and continue. Packbound does not pause the leader's existing attacks. Administrative cleanup and approved world unload are lifecycle operations, not damage; they terminate/suspend the encounter without a defeat reward. Do not make out-of-world corruption immortal forever: §08.5 handles it through abort, not combat damage.

Apply the gate both at target/intent admission and immediately before the authoritative mutation; the second check wins if a stale accepted intent arrives while guarded. A player must not receive leech/proc/learning/kill progress for striking the protected leader. Legitimate support or guard damage still contributes to the shared encounter as the existing eligibility owner permits.

### 08.3 Release ordering and state projection

Commit the final guard defeat receipt, then recompute the guard count on the same authoritative world/encounter owner before accepting a subsequent leader mutation. Events earlier in the ordered stream are blocked; events later see released state. Do not retroactively replay damage already blocked. Handle an AoE hitting the last guard and leader deterministically with this order; no special bonus splash is generated.

On release remove only the `PACKBOUND` protection provider. Keep native/affix channel immunities, shields not illegal in the template, and other defenses. Update the DTO immediately: `[Packbound: 1] [Invulnerable]` becomes `[Packbound: Broken]`. Remove `[Invulnerable]`, not every immunity label. The explicit affix still counts toward the frozen rewards.

### 08.4 Guard behavior and reachability

Guards must be alive, initially reachable and inside the same bounded encounter area at publication. They may use their existing attacks/AI; no teleport skill is created. Use the existing leash/navigation to return a straying guard. Evaluate roster existence at most twice per second, and reuse native navigation-failure evidence rather than running unbounded pairwise path searches.

If a guard moves beyond24m from the encounter anchor for5s, request its ordinary return/navigation behavior. If the engine reports unreachable/invalid terrain for a continuous10s after that request, suspend new encounter interactions and enter recovery. Do not treat valid player-applied Root/Freeze or a temporary crowd-control state as missing/unreachable corruption. Use the current controller's actual failure reason.

### 08.5 Recovery, abort and duplicate prevention

For a transient rebind/unload, restore the same logical guard with the same Health, affix state and identity from the existing persistent store. Do not generate a new loot/learn identity or a full-health duplicate. Resume only after all live roster entries resolve.

If reconstruction or navigation recovery cannot complete within30s of loaded, attempted recovery time, abort the remaining encounter: atomically mark it `ABORTED_NO_NEW_REWARDS`, suppress unresolved future leader/live-member death payouts, remove remaining actors through non-death cleanup, and emit a diagnostic. Already committed genuine guard rewards remain committed. The next spawn is permitted only by the normal future native/author template respawn schedule, not an immediate recovery loop. An authored template has at least a300s respawn delay; default900s.

There is no fallback that declares a missing guard dead, removes Packbound for free, transfers protection to another nearby mob, or grants the leader's rewards for administrative deletion. Administrative `/rpg enemies clear` uses this same abort path.

### 08.6 Required adversarial cases

Cover lost entity refs, duplicate defeat events, damage and last guard death in the same tick, source invalidation after attack launch, server restart with partially defeated guards, chunk unload while another chunk holds the leader, attack appendage deaths, phase changes, void/fall corruption, charm attempts, every status family, execute, DoT applied before an erroneous rebind, reflection, disconnected contributors, and two simultaneous players killing the final guards. Each must have an asserted state and reward count.

---

## 09. Resistance, damage immunity and status immunity

### 09.1 Preserve the canonical distinction

| Mechanism | Runtime meaning | Player tag |
|---|---|---|
| Elemental resistance | Ordinary mitigation, effective cap75% | No immunity label; an affix may explain it. |
| Explicit channel immunity | Reject that component before ordinary mitigation | `Fire Immune`, etc. |
| Physical immunity | Explicit authored exception; not Stone Skin | `Physical Immune` |
| Status-family immunity | Reject that status/family only | `Stun Immune`, `Poison Immune`, etc., only when actually true. |
| Slow-component immunity | Ignore speed-reduction components, not all hard CC | `Slow Immune` |
| Packbound protection | Reject all relevant external mutations until guards are defeated | `Packbound: N` and `Invulnerable` |

The channel order is `PHYSICAL, WIND, WATER, FIRE, EARTH, LIGHTNING, VOID`. Element labels are **Wind, Water, Fire, Earth, Lightning, Void**. Frost damage is Water and Poison damage is Earth. A Fire-immune enemy is not automatically immune to Stun or Taunt; Earth immunity is not the same record as Poison-status immunity. The UI uses explicit active state, not names or a guessed resistance threshold. [S1 §04.4.1]

### 09.2 Damage arithmetic

For each component e of a legal incoming hit:

```text
if protectionBlocks(event): reject whole mutation with protection reason
else if e in activeImmuneChannels: accepted[e] = 0; reason[e] = IMMUNE_CHANNEL
else if e == PHYSICAL: use the existing canonical Physical mitigation owner
else:
    R = clamp(rawResistance[e], 0, .75)
    effectiveR = max(0, R - eligibleDirectPenetration[e])
    accepted[e] = power[e] * (1 - effectiveR)
```

The exact existing block/absorb/redirect stages remain under their owners. Ordinary direct-hit penetration, resistance reduction, increased damage taken and status penetration **do not remove immuneChannels**. DoT uses its existing penetration eligibility; do not extend direct-hit penetration to Poison/Burn through this feature. No immunity-breaking/sundering skill or item is added.

Example: a hit of100 Physical +100 Fire against Fire immunity retains its Physical path; Fire becomes0. It does not reject the whole hit. A100 Fire-only hit remains0 with75% Fire penetration. Ordinary raw Fire resistance1.10 without an immunity flag still clamps to75%; with10% eligible direct penetration it mitigates65%, giving35 damage.

Do not equate native out-of-world/death cleanup causes with the RPG VOID element. The shared native-cause map must distinguish environmental/lifecycle causes from authored elemental damage.

### 09.3 Difficulty introduction — ME decision

Preserve existing explicit authored native immunities. The following policy governs **new ME-generated grants**, not retrospective deletion of old encounters:

| Difficulty | Native-affinity immunity grant | Affix-associated grant | New random actor maximum |
|---|---|---|---|
| Normal | None | None | 0 |
| Nightmare |20% once for an explicitly mapped role affinity |15% once for eligible Unique/Super Unique elemental affix |1 elemental channel |
| Hell |100% for an explicitly mapped role affinity |35% once for eligible Unique/Super Unique elemental affix |Normal/Champion1; Unique/Super Unique2 elemental channels |

Affix-associated candidates exist for Fire→FIRE, Cold→WATER, Lightning→LIGHTNING, Poison/Earth→EARTH, Wind→WIND, Void→VOID. Spectral Hit and Magic Resistant have **no** immunity candidate. Roll per spawn descriptor, never per attacker/hit. Preserved native and explicit authored template flags take first priority and consume the applicable count budget. Native-affinity grants take next priority; sort remaining grant sources by stable ID. Authored flags must pass template safety/accessibility validation before publication. Skip duplicates rather than replacing them with a different channel. If a slot is full or a safety rule rejects a grant, add no extra immunity and do not reroll.

Do not grant new random Physical immunity in any mode. Existing authored Physical immunity requires the accessibility checks below. The global count caps do not erase separately approved old native flags; a new promotion whose native profile exceeds the allowed random safety budget is rejected, leaving the original authored encounter unchanged.

### 09.4 Explicit starter affinity overlay

These are **new authored affinity choices**, not claims derived from a monster's name. Apply to the exact canonical role only after resolving the current registry/alias and source profile. Do not use substring matching. Missing/renamed assets must be resolved to an exact manifest identity in the integration map; no invented NPC role is permitted. Ordinary neutral/provokable ecology still requires the §03 opt-in.

| Canonical role family / exact proposed role | Affinity | Minimum raw affinity resistance Normal / Nightmare / Hell |
|---|---|---|
| `Golem_Crystal_Flame` |FIRE|.20 / .40 / .60|
| `Golem_Firesteel` |FIRE|.20 / .40 / .60|
| `Spirit_Ember` |FIRE|.20 / .40 / .60|
| `Emberwulf` |FIRE|.20 / .40 / .60|
| `Slug_Magma` |FIRE|.20 / .40 / .60|
| `Toad_Rhino_Magma` |FIRE|.20 / .40 / .60|
| `Golem_Crystal_Frost` |WATER|.20 / .40 / .60|
| `Spirit_Frost` |WATER|.20 / .40 / .60|
| `Golem_Crystal_Thunder` |LIGHTNING|.20 / .40 / .60|
| `Spirit_Thunder` |LIGHTNING|.20 / .40 / .60|
| `Golem_Crystal_Earth` |EARTH|.20 / .40 / .60|
| `Golem_Crystal_Sand` |EARTH|.20 / .40 / .60|
| `Spirit_Root` |EARTH|.20 / .40 / .60|
| `Feran_Windwalker` |WIND|.20 / .40 / .60|
| `Void_Spawn_Entombed` |VOID|.20 / .40 / .60|
| `Void_Spectre` |VOID|.20 / .40 / .60|

For the mapped channel, use `max(existingRawResistance, minimumRaw)` before additive affix contributions; this is a floor, not another additive .60 applied to an already resistant role. Other channels retain their existing values. The immunity roll remains independent of the floor.

Do not automatically alter the five progression golem **Boss encounters** through this ordinary-role overlay. Their authored encounter override has priority; preserve existing milestone combat and access rules. A non-Boss encounter using the same role may opt into this overlay. Other roles have no new innate-affinity grant unless explicitly added to this versioned data; elemental affixes still offer their separate controlled grant.

### 09.5 Accessibility and mandatory-encounter validation

For random packs, Physical remains damageable on every new immunity grant, giving a conventional weapon fallback rather than an invented immunity-break skill. This does not guarantee that every single-element loadout is equally efficient; elemental immunity is intended to require an alternate available attack or target choice.

For each mandatory progression/Boss/Super Unique binding, verify at least one existing, obtainable, permitted loadout/attack route can damage every required target in that difficulty without needing an ability taught exclusively by that same blocked encounter. Test actual damage admission, not merely nonempty channel sets. A fixed Physical+element immune combination needs a proved existing alternate route. Do not use a proposed future skill, an unimplemented affix, group assistance that is not required by the encounter contract, or a forced developer weapon as the solution.

A Packbound guard cannot be Physical-immune, have Packbound or another all-damage protection state, or depend on the leader's death to become vulnerable. No cyclic dependency graph. Two-element immunity is rejected for a Packbound leader under the v1 survival rule. The authored guard roster must be finite and reachable.

### 09.6 Status and UI consistency

Publish immunity sources in diagnostics: native profile, specific affix, deterministic affinity grant, script or Packbound. Active tag DTOs are derived from the same predicate set checked by damage/status admission. Never independently calculate them from raw resistance in the UI.

Cold's Chill and Poison are separate payloads, not damage components. An Earth-immune creature can retain a legally applied Poison status while taking zero Earth tick damage; a Water-immune creature can still be chilled by an independently legal payload. Only an explicit matching status-family immunity suppresses that status. A damage-confirmed ME status still needs positive actual Health damage from the triggering hit. Packbound blocks all of them regardless.

---

## 10. XP, equipment quantity, learning and durable rewards

### 10.1 One canonical defeat context

Extend the existing death receipt, not a second kill listener. Freeze role, level, difficulty, encounter rank, rarity, own affix count, immutable loot source, source validation, origin and encounter generation. At legitimate defeat, derive eligible recipients with the existing contribution/party/distance rules. Preserve the current64m and20s contribution baseline unless a newer accepted owner supersedes it; never force last-hit ownership or duplicate a pet kill for owner and pet. [S1 §05.1]

No economic reward for QA, combat summons, revived actors, player-owned/tamed farming actors, attack appendages, a nonterminal phase, invalid source, administrative cleanup or duplicate receipts. A normal allowed minion death is separate from a special leader death. Counterfeit client rarity/tags cannot affect rewards.

### 10.2 XP factor placement

Use the existing XP service and existing character-level/party allocation formula:

```text
rarityInputForExistingXp = table[rarityOrMinion].xpFactor
additionalXpFactor      = 1 + .15 * clamp(ownExplicitAffixCount - 1, 0, 3)
```

Replace only the legacy rarity slot for migrated ME-managed actors, then add `additionalXpFactor` in the shared calculation **before its established final rounding/party distribution**. Keep existing rank, combat-level base, level-difference rules, party pot and award ledger. No additional per-player bonus after the party share is awarded.

Illustrative normalized vectors, where the existing XP numerator before its rarity slot is100:

| Actor | Own affixes | Resulting numerator before existing rounding/distribution |
|---|---:|---:|
|Normal|0|100|
|Champion|1|150|
|Unique|1|200|
|Unique|2|230|
|Unique|3|260|
|Super Unique|4|362.5|
|Initial Minion|0|105|
|Existing Boss|0|100 — its original Boss rank factor is already upstream|
|Existing Boss with4 authored affixes|4|145 — no second Boss/Unique rarity factor|

This is not a flat promise of the final displayed XP for every player. The shared level and eligibility/party stages still apply.

### 10.3 Equipment quantity without rewriting gear quality

The retrieved loot profile is picks/no-drop/cap based, with role overrides. Resolve the actual checked-out profile through its existing owner, including all existing eligible-role restrictions and any guaranteed outcomes. Do not copy an obsolete probability table into this feature. [S5]

First let the existing loot path select its ordinary **equipment slots** and produce `K0`, its finalized count after native managed no-drop and base equipment caps, before generating or publishing items. Then:

```text
Q = table[rarityOrMinion].equipmentQuantityFactor * affixQuantityFactor
bonusExpectation = K0 * (Q - 1)
bonusCount = floor(bonusExpectation)
           + (stableBernoulli(frac(bonusExpectation)) ? 1 : 0)
Kfinal = K0 + bonusCount
```

`Q` is at least1. Each bonus slot generates a **new independent eligible item** through the same gear table and current quality/affix/era/item-level logic. It is not a copy of a rolled item and does not rerun a second no-drop check. The original base cap restricts the base plan; it does not silently erase the approved bonus slots. The combined plan has a default hard limit of16 managed equipment items per actor defeat. Validate `ceil(profileMaximumEquipment * maximumLegalQ) <=16` for each enabled binding before release; lower/configure that binding's Q explicitly if a future exceptionally large base table exceeds the limit. Never silently discard an already-owned/rolled item to meet the cap.

Quantity does **not** change the chance that a zero-equipment base roll stays zero. `K0=0 -> Kfinal=0`. It increases expected item count conditional on successful base slots, and consequently overall expected quantity. This is an intentional narrowly scoped quantity layer, not a hidden drop-chance or quality redesign.

Examples: a three-affix Unique has Q=1.50×1.30=1.95. K0=1 yields1 item5% of the time and2 items95% of the time; K0=2 yields3 items10% and4 items90%. A four-affix Super Unique has Q=1.80×1.45=2.61. K0=2 yields5 items78% and6 items22%. These are expectation vectors, not guaranteed high-tier gear.

The existing Magic Find and Fortune/Upgrade systems apply once through their current quality/roll owners for **each** independent item. ME rarity/affix count does not alter quality weights, tier legality, item level, intrinsic ranges, affix count on items, guaranteed quest drops, salvage, native materials, currency, or item requirements. Loot-source rank remains the original authored rank. Native non-managed drops must not be duplicated by both a replacement native table and the ME extension.

### 10.4 Initial minions and encounter economy

A minion uses Q=1.00 and n=0 even if it has three inherited visual labels. Horde increases finite reward opportunities by adding actual initial actors; it does not multiply each minion by the leader's Q. Champion packs give multiple moderate rewards; their composition must be included in encounter-level telemetry. Do not advertise per-monster increases as a proven per-minute balance result.

### 10.5 Learning rate — preserve the existing acquisition contract

Skills and first-copy Passives use the same learning pipeline. Preserve the exact current canonical role→ability mapping, stable aliases, one current taught ability per assigned source, and no automatic teaching for reserve/unassigned roles. Do not give a Super Unique a second ability or create an affix-derived skill. Already-owned abilities do not reroll or produce extra copies. Link Insight and passive-copy purchases remain unchanged. [S1 §05.2 and Appendix G]

The documented base chance is .15/.05/.02 for Common/Rare/Unique **ability acquisition rarity**, not monster rarity. Preserve the existing effective-Wisdom bonus and ordinary cap:

```text
baseChance = existingAbilityAcquisitionBaseChance
wisdomBonus = min(.40, .0015 * existingEffectiveWisdom)
P0 = clamp(baseChance + wisdomBonus, 0, .95)
relativeBonus = P0 * (monsterLearningFactor - 1)
rarityBonus = min(relativeBonus, rarityBonusPointCap)
P = clamp(P0 + rarityBonus, 0, .95)
```

Concrete ME bonus-point caps prevent a high-Wisdom character from turning every boss into a near-guaranteed teaching source while preserving the requested small-to-medium increases:

| Actor | Learning factor | Maximum added probability |
|---|---:|---:|
|Normal|1.00|0|
|Initial Minion|1.05|.005 =0.5 percentage point|
|Champion|1.15|.03 =3 percentage points|
|Unique|1.35|.06 =6 percentage points|
|Super Unique|1.60|.09 =9 percentage points|
|Authored Boss|1.75|.10 =10 percentage points|

With P0=.10, results are .10/.105/.115/.135/.16/.175 respectively. With P0=.354, results are .354/.359/.384/.414/.444/.454. Both tables must pass exactly within numeric tolerance. The caps and factors are ME initial balance, not pre-existing deployment claims.

Apply no new affix-count, difficulty, minion-inheritance, loot-quality or number-of-guards multiplier to learning. It is resolved once per eligible player/source defeat from the canonical learning owner. Snapshot the existing permitted Wisdom projection at the owner's normal opportunity point; do not move its equipment-snapshot semantics to the loot UI.

### 10.6 Pity, eligibility and duplicate transactions

Keep the existing pity key `(canonicalSourceId, abilityId, playerId)` and threshold `ceil(3/baseChance)` —20/60/150 eligible failures for documented .15/.05/.02. After that many failures, the **next** eligible opportunity is guaranteed. Rarity does not reduce the threshold or add extra failures per affix. Pity guarantee resolves to100%, outside the ordinary95% random cap.

One failed eligible attempt increments once. Ineligible, invalid, already-owned, aborted and duplicate defeat events do not increment. A successful first learn and its pity reset occur in the existing atomic progression mutation, independent of whether the enemy also dropped equipment. Preserve pity across difficulty/world transitions and alias changes. Persist a receipt even on a failed random roll so a retry does not make another chance.

SourceValidationState still needs a real reachable combat/defeat binding and the already-assigned ability's actual readiness. This feature must not make an unfinished current ability publicly learnable by bypassing its gate. Report such an existing limitation separately; do not invent a replacement skill as part of Master Enemies.

### 10.7 Durable reward transaction and crash recovery

Use one durable defeat-event key `(worldId, encounterId, generation, logicalActorId, terminalDefeatSequence)` and purpose-separated child keys. Proposed child names: `xp/playerId`, `learn/playerId/abilityId`, `gear/base/index`, `gear/bonus/index`, and existing Insight/quest slots. Reserve and persist the full count/roll/recipient plan before publication. Every item gets one stable instance ID and existing ownership/claim transfer receipt.

Prefer the project's existing ledger transaction and outbox. Where rewards span stores, commit an immutable plan first and replay idempotent consumers; do not acknowledge completion solely because an in-memory callback ran. Retry after a crash must resume the same item quality/affixes/learn result, not make new random choices. XP and learning can complete even when an inventory is full; gear uses the existing ground/claim fallback with durable ownership, never silent deletion or a repeated death payout.

No client request may supply absolute reward amounts, affix counts, immunity flags, random seeds for production spawning or guaranteed-learning flags. Operator QA requests carry ineligible QA provenance and cannot be laundered into a production drop.

---

## 11. Player presentation: name, tags, state and palette

### 11.1 Required target card

All explicit affixes and every active damage/status immunity must appear as readable tags **under the monster's name** in the primary target-enemy card. Reuse/extend the current Hywind HUD composition; do not create a new full-screen inventory page. The target card is the required full-information surface whether or not native overhead nameplates support rich multiline text.

Normative text layout examples (text mockups, not copied game art):

```text
Gorefang the Cruel                         Lv 52
Unique · Trork Warrior
[Fire Enchanted] [Extra Strong] [Extra Fast]
[Fire Immune]

Grimgor the Ashen                          Lv 68
Super Unique · Trork Warrior
[Packbound: 4] [Fire Enchanted] [Extra Fast]
[Invulnerable] [Fire Immune]

Trork Warrior                             Lv 68
Minion · Guard of Grimgor the Ashen
[Fire Enchanted (Inherited)]

Grimgor the Ashen                          Lv 68
Super Unique · Trork Warrior
[Packbound: Broken] [Fire Enchanted] [Extra Fast]
[Fire Immune]
```

Minion example is a separate inheritance illustration; Packbound cannot combine with a second leader-mechanic affix, but an elemental leader affix may still propagate. Guard count is current living sealed guards, not nearby entity count.

Order: name; rarity/base role/level metadata; explicit affix tags in stable selection order; current protection and channel/status immunity tags; relevant changing state labels. Put the **Invulnerable** status immediately below/adjacent to Packbound when guarded. Source-owned short labels such as Enraged, Shielded and Avenger3/5 are current state, not extra reward-affix entries.

The card supports up to4 explicit affixes,2 generated channel immunities and the permitted status/state tags. Wrap whole tags across content-driven rows; never hide a critical immunity behind `+3`, ellipsis or an unhoverable icon. Width defaults to420 UI logical pixels, maximum560 or available viewport width minus32; outer padding12, tag padding6 horizontal/3 vertical, gap4; title18px, detail/tag14px at default scale. Keep the title at most2 lines with content-driven height; fixed content must not overlap it. Long authored names are validated at most40 Unicode codepoints; generated names at most32. Scale the existing UI tokens for accessibility; do not shrink safety tags below the project's minimum readable size.

Full name and mechanic descriptions are available in the Bestiary/inspect view using the same DTO and localization keys. The card does not show hidden RNG, fabricated exact drop items, or an affix's internal tier/weight.

### 11.2 Single source of truth and supported native surfaces

`EnemyDisplayDto` contains identity/rarity, explicit tag records, active immunity/protection tags, guard/Avenger/Frenzy/shield state and visual variant. Build it from the authoritative resolved snapshot and pack state. Combat and tags must never use independent guesses; this avoids the same displayed-versus-checked-value defect observed in the attribute UI discussion.

A public Hytale `Nameplate` component exposes text, but that API reference alone does not prove color markup, multiline wrapping or rich chips on the installed client. [H2] Keep ordinary overhead identification concise (`Lv 52 · Unique · Gorefang the Cruel`) through supported text. If the current installed UI/nameplate path supports proper under-name tags, mirror the same DTO there. Do not require per-letter entities, a custom client, a global packet hook, or unverified markup to complete the **required target card**. Plain readable text is the fallback for overhead color, not for hiding mandatory target-card tags.

There is one writer/compositor for each native nameplate; integrate with the current mod owner rather than racing it every tick. Clean up on death, range loss, target change, despawn and world transfer. Project the current logical phase/entity binding correctly.

### 11.3 Colors and accessible identity

Use existing Hytale UI tokens when available; initial semantic choices: Normal neutral, Champion blue, Unique yellow/gold, Super Unique orange, Boss red. They are enemy presentation choices, not gear quality mapping. Always include rank text and status words; color alone is not the identifier. Element tags may use the current element icon/color mapping, but the words `Fire Immune` etc. remain visible. Do not use red as an ambiguous generic rarity/status/hostility signal without text.

### 11.4 Palette variants: artist-owned, runtime-selectable

Graham may author palette-swapped textures. Implement deterministic per-role per-rarity `visualVariantId` lookup with optional Super Unique override. Only replace the appropriate texture/model presentation reference; preserve model geometry, rig, animation set, attachments, hitbox, collision, scale, navigation and role identity. A texture is not proof of elemental damage or immunity.

Preference: explicit Super Unique override → exact role+rarity authored variant → optional certified tint adapter → original texture. The ordinary name/rank/tags remain fully informative when no recolor has been authored. Missing optional art is not an inert gameplay affix; missing a promised shipped texture is a validation error for that art entry. Never substitute a broken/missing texture asset.

Runtime tint is an optional optimization, not an assumed Hytale capability. Codex must prove the real supported per-instance parameter before enabling it; otherwise use asset-backed texture variants. Do not add shader hacks, emissive/outline dependencies, alter every species with an uncontrolled global filter, or overwrite source texture files. `runtimeTint.enabled=false` is the default.

Provide `visual-variants-v1.json` plus a manifest/validation command and two demonstrated role entries if artist assets are already supplied. Without those assets, ship a valid empty override map and demonstrate the selection path with a controlled QA texture asset; do not claim all monsters have custom art. The future artist can add entries without changing combat code. The graphical Super Unique editor remains out of scope.

### 11.5 Update budget

Identity/static tags update at birth/rebind only. State changes dirty only the affected DTO. Coalesce routine dynamic state to at most4 updates/second per tracked target; protection release must publish in the next replication opportunity, not wait for a slow UI poll. Use current entity visibility/target selection, not one world-wide scan per viewer. No per-frame JSON parsing or rebuilding every monster's text.

---

## 12. Determinism, threading, performance and failure containment

### 12.1 Randomness contract

Use the existing deterministic RNG owner and its versioned serialization where possible. Preserve all pre-existing base loot and acquisition draws when their inputs are unchanged; ME must not shift their sequence by consuming an extra shared random number.

New domains are separate: `me.rarity`, `me.pack-size`, `me.affix-set`, `me.affix-selector`, `me.affinity-immunity`, `me.affix-immunity`, `me.name`, `me.spectral/strike`, `me.status/strike/target/effect`, `me.quantity/defeat`, and `me.gear-bonus/defeat/index`. Include encounter generation and stable logical IDs. No wall-clock or iteration-order seeds.

Where the current owner lacks named substreams, implement this adapter, not a second global RNG: encode each key component as UTF-8 preceded by an unsigned32-bit big-endian byte length; feed the frozen32-byte encounter seed, encoded domain/components and an unsigned64-bit big-endian draw counter into SHA-256. Take the first8 bytes as an unsigned64-bit integer. For a [0,1) draw, take its top53 bits divided by2^53. For an unbiased integer in [0,bound), reject values at/above `floor(2^64/bound)*bound` and advance only that domain's counter. Persist selected outcomes in the existing replay ledger. QA seeds are explicit; production seeds come from the server's existing authorized spawn seed.

For affix sets, expand Aura into its three selector candidates with selector-specific constraints, but treat them as one mutually exclusive affix ID. Enumerate legal distinct-ID combinations of the required size from the capability-resolved candidate list, sorted by `(affixId,selector)`. The weight of a combination is the product of member weights times any explicit selector integer weights. Choose one combination proportionally to that integer weight with the bounded integer draw. Use checked64-bit arithmetic; validation rejects overflow rather than wrapping. With this catalog and maximum4 the enumeration is bounded. Cache it by resolved capability/constraint/difficulty/spawn-capacity signature. Fixed authored affixes occupy their IDs and constraints before enumerating additions.

If all selector weights/candidates for an affix are invalid, it is ineligible; never emit a no-op `Aura: Unknown`. Name/palette draws cannot alter affix/reward draws. Spectral Hit cannot resample its element after observing a victim.

### 12.2 World-thread and effect ordering

All entity mutation, membership, derived stat replacement, protection release and defeat ordering happen on the owning world/encounter thread or its documented command buffer. Cross-world references are invalid for this feature. IO/serialization may run off-thread on immutable snapshots; results return with world/entity/generation checks.

Before publishing an actor, attach its descriptor/protection and register its membership. At damage admission recheck generation and active defenses. Post-application receipts schedule bounded work through normal queues. At death, mark terminal state once, process membership/release, freeze reward intent, then schedule durable consumers. Clean pending effects by generation on abort or native rebind.

### 12.3 Performance design limits

No affix performs an all-world enemy scan. Static modifiers are cached. Per-hit work is fixed-size vector arithmetic and small provider lookups; only the affixes present on the actor are considered. Pack lists are bounded8. Aura queries examine only that roster, every250 ms. Timers use an existing scheduler/time wheel rather than one independently ticking object per affix. Provider leases and state changes dirty only affected snapshots.

Default stress fixture:1000 loaded managed enemies,100 special packs,100 simulated contributing players, with16 simultaneous nearby special packs visible to the same observer in a separate rendering stress test. Temporarily lift population limits only inside the test harness; do not change production defaults. Compare same scenes with the feature disabled/enabled, recording median/p95/p99 tick time, native damage throughput, new allocation rate, packet volume, queue depth and cleanup counts. Initial acceptance target: under10% p95 tick-time overhead at this fixed workload and no sustained queue backlog, monotonically growing memory or entity leak after ten spawn/cleanup cycles. This is a test target, not measured performance or a1000-player promise. Record hardware and build hashes.

Rate-limit detailed tracing with configurable bounded ring buffers. Default detailed trace:30s or10000 events, whichever comes first. Summary counters are aggregate. Never serialize full snapshots and27 descriptions on every hit in production.

### 12.4 Fault containment

Invalid new config leaves the previous valid registry active and reports all errors. Invalid new encounter descriptors do not publish. Do not turn off all native enemy damage, make a whole world invulnerable, or reset player data because one affix file is invalid. A runtime assertion on an active special pack suspends/aborts that pack through its owner; it must not grant victory/rewards or silently delete already-owned items.

A production-ready build still requires every admitted descriptor and certified role path to pass; fault containment is not permission to ignore known failures and ship a shrinking catalog.

---

## 13. Data files, codecs and validation contract

### 13.1 Required assets

Use the existing `rpg/` data namespace and supported asset loaders. Proposed repository paths below may be adapted to the actual layout but must be mapped in the integration report:

```text
rpg/enemies/master-enemies-v1.json       # global ME policy and balance
rpg/enemies/monster-affixes-v1.json      # closed27-entry catalog
rpg/enemies/affinities-v1.json           # exact role affinity overlays
rpg/enemies/visual-variants-v1.json      # artist assets, optional tint adapter
rpg/enemies/super-uniques-v1.json        # validated authorable templates
rpg/enemies/name-pools-v1.json           # short deterministic generated names
rpg/enemies/native-bindings-v1.json     # actual installed role/action/channel adapters
schemas/enemies/*.schema.json           # or equivalent existing strict codecs
```

Appendix A supplies the complete new policy/catalog/affinity/name/template seeds. The native bindings file is generated from the actual installed assets and checked-in integration mapping; this document does not fabricate native asset IDs or absolute world UUIDs. Codex must deliver that resolved file and its validation evidence, not a `TODO` or an empty “supported” manifest.

### 13.2 Strict codecs

Unknown keys are errors. Missing required fields are errors. IDs and enumerations are case-sensitive canonical values; role aliases resolve through the existing registry. Enforce schema version and type, finite numerical values, unique IDs, no duplicate affix family, fixed N/NM/H array length3, nonnegative weights, chance/fraction ranges, valid paths and bounds. Do not coerce a string `"30%"` into `.30`, parse `Fire` as a native damage cause, or infer a role from a display name.

Each affix has its own typed parameter codec using **exactly** the parameter keys in its card and Appendix A entry. Core semantics such as Packbound `allDamageBlocked=true`, `allExternalStatusMutationsBlocked=true`, Bulwark `regenerates=false`, fixed status/channel labels and maximum4 affixes are invariant, not freely authorable booleans. Numeric tuning may change within the validated bounds below.

| Field class | Validation |
|---|---|
| ID/key/name | Stable IDME-001..027 unique; key `[a-z][a-z0-9_]{1,63}`; displayed text escaped/localized. |
| Difficulty arrays | Exactly3 values in orderNORMAL,NIGHTMARE,HELL. |
| Probability / leech / drain fractions |0..1; aggregate caps must remain greater than0 and internally consistent. |
| Resistance add/floor |0...75 per provider; effective shared cap remains.75; explicit immunity separate. |
| Extra elemental fraction |0...30 after valid stacking; no negative power. |
| Damage/Defense increments |0..1 per provider, shared runtime caps in§04 apply. |
| Movement/recovery increments |0...50 per provider; unsupported component explicitly zeroed in resolved binding. |
| Durations/locks |1..60000 ms; no zero-duration periodic busy loop. |
| Distances |0.1..32m; displacement0..1m; vertical Knockback fixed0. |
| Pack sizes |At most8; Unique initial minions3..5; Packbound guards2..5. |
| Affix counts |0..4; no duplicate IDs/element family; complete set required. |
| Health scaling |Rarity/template factor.25..10; ordinary random defaults fixed as§01. |
| Super Unique direct factor |.50..2.00 for authored templates; defaults1.15. |
| Respawn delay |300..86400s for reusable authored anchors; optional one-shot mode has no respawn. |
| Reward multipliers |No arbitrary per-template factor in v1; derive from rarity/own affix count and existing source. |
| Texture paths |Existing packaged asset references only; no file traversal, absolute OS paths or runtime remote URLs. |

Reject an operator not present in the 27-handler registry. A shipped definition's tag/detail text must match the handler's actual parameter interpretation and inherited effects. Do not allow user-authored arbitrary script expressions in JSON. Supply schema tests for malformed data and the complete positive seeds.

### 13.3 Native binding manifest

Per supported canonical role/action, record exact native Role/Model/Texture IDs, resolved inherited action/profile IDs, direct-hit channel mapping, logical root/strike producer, proc-coefficient allocation, Defense/resource capability, recovery scalar/timestamps, navigation speed projection, and contact/application receipt seam. Include source asset hash and a positive native test ID. The same NPC may have multiple actions; certify every action eligible to consume a given modifier, or explicitly mark only the meaningful subset in the resolved descriptor. Do not claim global NPC projectile support after testing one melee Trork.

For current roles with no positive direct damage, only genuinely meaningful stat/pack/defense affixes are eligible. Certified production promotion coverage must name its role set. Existing reward-disabled/unknown identities stay unpromoted until classified; do not auto-enable all catalog identities just to enlarge the supported count.

---

## 14. Super Unique authoring: complete backend now, graphical editor later

A Super Unique has a stable template ID, authored name, canonical role, fixed affixes, a finite initial minion composition, safe random difficulty additions, optional art override, and a placement/respawn policy. It is not a new AI class or Boss phase engine.

### 14.1 Template fields and semantics

| Field | Required behavior |
|---|---|
| `id`, `revision`, `name` | Stable author identity; nonempty name at most40 codepoints; fixed across respawns. |
| `canonicalRoleId` | Exact existing validated source identity; no automatically generated creature. |
| `difficultyModes` | Enabled modes only; resolve combat level from that existing authored encounter profile. |
| `baseProfileMode=INHERIT_ROLE` | Reuse role/difficulty rank, source, combat level, native attacks and reward eligibility. |
| `rarity=SUPER_UNIQUE` | Never silently set encounterRank=BOSS. |
| `maxHealthFactor`, `directDamageFactor` | Optional within§13; defaults3.00/1.15 applied once to role/difficulty baseline. |
| `fixedAffixes` | Stable admitted IDs plus explicit selectors when necessary; all capability/exclusion rules enforced. |
| `randomAdditionalAffixes` |Default0/1/2; maximum total4, selection without replacement. |
| `minions` |Exact approved role(s), count, initial origin and placement; finite sealed roster. |
| `explicitImmuneChannelsByMode` |Empty by default; finite explicit additions, subject to safety/accessibility and generated-cap rules. |
| `visualVariantId` |Optional artist asset mapping, not a model/AI rewrite. |
| `lootProfileOverrideId` |Optional existing compatible registered profile; no raw item strings or new special-drop subsystem. |
| `placement` |`ADMIN_PLACED_ANCHOR` or existing encounter binding ID; no made-up absolute world. |
| `respawn` |After genuine defeat/abort according to policy; default900s, only while anchor is valid/unoccupied. |
| `learningSourceMode=CANONICAL_ROLE` |Inherit exactly one current taught ability, or none if unassigned; no separate `teachesSkill` override. |

The initial Grimgor example in Appendix A is **new authored example content**, not a previously existing NPC. It uses the existing Trork Warrior role and fixed Fire Enchanted+Packbound. The template is fully validatable/spawnable; a permanent location is created explicitly by the operator's placement command. The document does not choose an unknown live-world coordinate or overwrite a town NPC.

### 14.2 Placement and repeat lifecycle

An operator placement command records the actual current world/difficulty, a safe navigable anchor derived from the selected location, template revision, unique anchor ID and respawn cycle counter. Validate collisions, protected areas and all guard positions before committing. A template with a known existing encounter binding uses that binding instead. Default one live instance per anchor.

On a legitimate future respawn, increment cycle and allocate new logical actor IDs/reward identities. Reroll only explicitly random additions and immunity grants; fixed name/affixes remain fixed. Server restart or chunk reload is not a respawn. Preserve the remaining cooldown from a server-authoritative timestamp and handle offline elapsed time under the existing encounter scheduling policy; never spawn multiple missed cycles to catch up.

Every permanent anchor is inspectable, exportable and removable through commands. Removal is an administrative abort of remaining actors, with no free kill payout. The backend accepts the same typed records a future editor will write; the editor itself is not required or authorized here.

---

## 15. Commands and diagnostics

These are **new commands to implement and register**, not claims that they already exist. Use the existing `/rpg` command owner and permission conventions. Current `/rpg difficulty inspect` remains the basic authored-profile inspection path, and `/rpg-trace` remains the skill timing/execution trace; neither should be repurposed invisibly. Update maintained `COMMANDS.md` in the same change as registration. [S3]

| Syntax | Access | Exact purpose |
|---|---|---|
| `/rpg enemies status` |Read-only Adventure|Show ME registry revision, current mode, production enablement and public feature status; no private seed. |
| `/rpg enemies inspect [entityUuid]` |Read-only Adventure for targeted nearby actor; full detail operator|Default uses current target within24m. Show rarity/role/rank, tags, guard count and actual defenses. Operator adds profile, provenance, factors and pending receipts. |
| `/rpg enemies affixes` |Read-only Adventure|List27 affix names/IDs and canonical short descriptions. |
| `/rpg enemies validate` |`inigmasgames.rpg.enemies.author`|Validate all configs, bindings, roles, templates, reward caps and enabled legal sets without mutating live actors. |
| `/rpg enemies reload` |Same author permission|Validate all files first; atomically replace registry for future spawns only; preserve active descriptors/revisions. |
| `/rpg enemies spawn <canonicalRoleId> <champion\|unique> <seed> [affixIdsCsv]` |Author + copied-save QA marker|Create a reproducible QA pack using current mode/profile; explicit IDs still obey constraints; alwaysQA economic provenance. |
| `/rpg enemies super <templateId> <seed>` |Author + copied-save QA marker|Spawn that template as an ineligible QA encounter; do not create permanent anchor. |
| `/rpg enemies place <templateId>` |Author|Create a validated permanent production anchor at the operator-selected safe location. Explicit write; log it. |
| `/rpg enemies remove <anchorId>` |Author|Remove the anchor and abort remaining actors without death rewards. |
| `/rpg enemies clear <encounterId>` |Author|Abort a live encounter safely without kill/XP/loot/learn awards; does not remove its anchor. |
| `/rpg enemies export <templateId>` |Author|Write canonical escaped JSON under the server export directory using a sanitized generated filename. |
| `/rpg enemies trace <off\|summary\|detailed>` |Author|Bounded ME trace controls; detailed automatically stops after30s/10000events. |

`[affixIdsCsv]` is optional; when supplied it must contain exactly the required count for the selected rarity/mode and may include one Aura selector using `ME-023:MIGHT`, `:WARD`, or`:HASTE`. Reject duplicates/unknown IDs with a precise error. Names are not parsed as ambiguous partial substrings. Production placement never accepts a client-authored reward override.

Define the QA marker as `enemy-qa-enabled` in the existing mod data directory **and** require the established copied-save/test-profile guard. Do not award real gear through the convenient QA spawn command. Test economic rewards using the harness and an isolated copied save with a controlled production-provenance fixture that cannot be invoked on a live world.

### 15.1 Trace schema

Structured events include timestamp/tick, world/encounter/generation/actor/root/strike IDs, descriptor/balance revisions, event kind, affix/source IDs, pre/post typed values, rejection reason, guard state and reward child IDs. Allowed reasons include `PACKBOUND`, `IMMUNE_CHANNEL`, `STATUS_IMMUNE`, `NO_HEALTH_DAMAGE`, `NO_MANA_POOL`, `PROC_LOCK`, `BUDGET_EXHAUSTED`, `INVALID_GENERATION`, `OUT_OF_RANGE`, `ALREADY_PAID`, `INVALID_SOURCE`, `INELIGIBLE_ORIGIN` and`PACK_RECOVERY_ABORT`.

Do not log private player conversation content, Orbis data, full inventory payloads or every probability draw in normal mode. The operator inspect path must show the exact values used by admission and rewards, not recalculated approximations.

---

## 16. Acceptance suite and evidence

### 16.1 Per-affix coverage matrix

For eachME-001..027, generate a coverage row containing: handler/operator, valid representative role(s), Native melee/projectile/applicable route, N/NM/H positive inputs and output, negative/no-op case, inheritance test, immunity/protection test, root-duplication test, persistence/cleanup test, tag screenshot/DTO assertion, exact code/build hashes and evidence path. Every card's `ME-nnn-P/N` vector is mandatory. Mark non-applicable routes explicitly with a semantic reason, not an empty test cell.

Completion requires a meaningful positive demonstration on each supported operator type and representative real NPC actions. For each advertised generic direct-hit adapter, test at least one native melee, one native projectile and one genuine multi-target native strike. Test statuses against a player through current defenses and at least one ordinary NPC/pet where relevant. Never claim an unsupported API path from an isolated Java arithmetic test.

### 16.2 Deterministic core test IDs

| ID | Required assertion |
|---|---|
|ME-S01|Same seed/bindings/inputs produce identical rarity, affixes, names, immunity, roster and reward-plan identities. |
|ME-S02|Changing cosmetic name pool/palette does not alter combat or reward draws. |
|ME-S03|Champion has members, no minion leader; every member owns exactly1 legal shared affix. |
|ME-S04|Unique counts are1/2/3 by mode; Super Unique fixed+0/1/2 never exceeds4. |
|ME-S05|No duplicate affix ID/element family; test all cached legal combinations against constraints. |
|ME-S06|Horde reserves exactly 2 extra initial actors and cannot overfill or partially publish. |
|ME-S07|Failed staging restores original native spawn once, without death or reward callbacks. |
|ME-S08|Unknown/protected/tamed/abstract/nativeBoss roles are not randomly promoted. |
|ME-C01|Native direct vectors apply rarity/affix factors once; components retain one crit/root. |
|ME-C02|Elemental immunity rejects only its channel; penetration cannot break it. |
|ME-C03|Raw resistance1.10 is75% ordinary mitigation, not immunity. |
|ME-C04|Full shield absorption gives no ME on-hit status/drain/leech; remainingHealthdamage does. |
|ME-C05|Multi-pellet strikes share budgets and cannot multiply full-power procs. |
|ME-C06|Reflection cannot chain, leech, use Filter submission, hit owner instead of summon, or exceed victim cap. |
|ME-C07|Extra Fast changes actual movement/recovery, never hit count/wind-up/animation. |
|ME-C08|Cursed/Armor Breaker recompute against changed current stats and expire independently. |
|ME-C09|Poison/Chill/Electrified use existing status owners and stable affix provenance. |
|ME-C10|Unstoppable blocks Slow movement but not Root/Freeze; Unwavering does not block unrelated statuses. |
|ME-P01|Packbound blocks damage/status/resource/execute/CC until the last sealed guard defeat. |
|ME-P02|Final guard ordering is deterministic; blocked damage is not retroactively reapplied. |
|ME-P03|Unload/despawn/missing ref/phase actor does not decrement living guards. |
|ME-P04|Guard ownership conversion, cyclic immunity and physical-immune guard templates are rejected. |
|ME-P05|Recovery preserves identity/currentHealth or aborts without new rewards; never releases for missing guards. |
|ME-P06|Packbound cannot be dispelled or inherited; minions remain damageable. |
|ME-P07|Avenger counts each initial minion death once and nothing outside its sealed roster. |
|ME-P08|Aura reaches only legal same-pack recipients; removal/expiry is bounded and source-owned. |
|ME-R01|XP legacy rarity slot is replaced, not multiplied twice; existing rank/party behavior preserved. |
|ME-R02|n=0/1/2/3/4 reward factors=1/1/1.15/1.30/1.45 exactly. |
|ME-R03|K0=0 stays0; fractional bonus rounding has the asserted expectation and stable replay. |
|ME-R04|Independent bonus items have new stable IDs; no rerolled base item, no native-loot duplication. |
|ME-R05|MF/quality/Luck eligibility remains unchanged at fixed gear seed; quantity is not MF-derived. |
|ME-R06|Minions use own source/Minion learning rate, not leader source/factor. |
|ME-R07|All rank learning values and bonus-point caps match§10.5, for Skills and first-copy Passives. |
|ME-R08|Pity20/60/150 failures guarantees the next opportunity; aliases/world changes do not reset it. |
|ME-R09|Already-known, reserve/invalid, QA, summon, revived and aborted sources yield no inappropriate learning. |
|ME-R10|Crash at every reward-stage boundary pays each XP/gear/learn child at most once and resumes remaining valid children. |
|ME-R11|Inventory-full fallback preserves ownership and does not blockXP/learning or silently destroy items. |
|ME-U01|All actual explicit/inherited affixes, immunity and Packbound state appear under target name with no overlap. |
|ME-U02|A4-affix/2-immunity target displays all tags at supported UI scales and long valid names. |
|ME-U03|Palette overrides change only appearance, preserve source/rig/collision and survive entity replication/rebind. |
|ME-U04|Missing optional art uses native appearance; missing declared asset fails validation; no debug-purple texture. |
|ME-U05|Target, overhead identifier, inspect and admission share the same descriptor/state revision. |
|ME-L01|Restart preserves injuredHealth, usedBulwark, Frenzy time, Avenger stacks, guard roster and rewards. |
|ME-L02|Retried role rebind does not double native modifiers/auras or create another pack. |
|ME-L03|Invalid reload leaves the previous valid registry and active encounters intact. |
|ME-L04|Ten cleanup cycles leave zero new leaked timers/providers/actors/receipts pending outside valid retention. |
|ME-L05|Existing Iron Sentinel gear inheritance, player Skills/passives, gear generation and difficulty milestone tests still pass. |

### 16.3 Fuzz, simulation and live fixture plan

Run at least100000 deterministic spawn plans across certified role capability sets, all modes and fixed seeds. Assert complete legal sets, bounded roster sizes, immunity safety, stable replay and nonnegative stats. Simulate equipment quantity for representative K0 values0..4 and all rarity/count combinations; observed averages must fall within the test's computed confidence interval around K0×Q rather than an arbitrary fixed tolerance. Do not use random flaky assertions; pin seeds and validate analytical expectations as well.

Use race/order fixtures for two same-tick guard deaths, simultaneous status applications, shield break plus death, source death before reflected child submission, and two clients receiving different native frame timings. All see the same authoritative encounter.

Live QA must demonstrate a Normal single-affix Unique, Nightmare two-affix Unique, Hell three-affix Unique, a Champion group, the Grimgor four-affix Hell template, the Packbound transition, inheritance, every immunity label and a controlled reward/learning comparison. Economic mutation fixtures run only in a copied save. Include one test with a player and Iron Sentinel attacking together, verifying no duplicate owner/pet reward or gear-affix recursion.

Save traces and screenshots with exact world/mode/seed/role/build. Screenshots prove presentation, not arithmetic; correlated receipts prove arithmetic. Each type of evidence is required where appropriate.

### 16.4 Release gate

No admitted handler lacks a positive path. No critical tag describes a defense absent from admission. No negative test succeeds by bypassing native protection. No unresolved double mitigation, duplicate economic receipt, invisible Packbound dependency or missing supported action binding. All modified modules compile against the pinned installed version. Any genuinely blocked integration is reported with the failing evidence and remains a failed release gate, not reclassified as an unsupported affix to achieve27/27.

---

## 17. Migration, reload and rollback

### 17.1 Do not rewrite live enemies on every update

Newly spawned eligible encounters receive ME descriptors. Existing live encounters retain their current stats/reward identity until a legitimate despawn/respawn, unless an explicit one-time migration safely records the existing baseline, Health fraction and consumed state. Default rollout does **not** retroactively add packs, affixes or immunity around players mid-fight.

For pre-existing persisted rarity labels, map only known authoritative values in a versioned migration: ordinary→NORMAL, genuinely matching champion→CHAMPION, genuinely matching unique→UNIQUE, authored named templates→SUPER_UNIQUE, existing Boss→BOSS. A legacy `rare-spawn` tag is not enough to infer which ME pack/affix set it should have. Preserve its old numerical reward under a `LEGACY_UNMIGRATED` descriptor until its next spawn. Never apply both legacy rarity and ME rarity.

Retain original role IDs and aliases. A native `Skeleton_Elite` role name is a creature identity, not automatic evidence for `enemyRarity=UNIQUE`. Phase wrappers do not generate new encounters. Save backups and a migration report; no player/gear/progression reset.

### 17.2 Registry reload

Validate a complete candidate registry into immutable structures, then atomically swap only for future spawns. Active descriptors carry resolved parameters and referenced revision records; they do not change HP, selected immunity, rewards or names because an author edited JSON. Localization/cosmetic assets can update safely through their existing version rules, but gameplay state stays pinned.

Keep referenced revisions available until all active/saved encounters using them are terminal or migrated. If an old native binding cannot load after an engine update, suspend/abort through the existing recovery policy; do not silently substitute another monster or reward profile.

### 17.3 Disable and rollback

A feature switch stops new special promotion, not the existing native spawn system. Already-active packs retain their descriptors and runtime until resolved. A full rollback drains/aborts remaining special packs without awarding administrative kills and retains committed economic receipts. Restore native appearance/nameplate ownership only after ME providers are removed. Never turn off the protection filter while leaving a guarded leader and then grant an accidental reward.

Keep all player-learned abilities and legitimately owned gear from earlier completed encounters. A rollback is not permission to delete those assets or reroll their affixes. Restore configuration/code from versioned backup and run save/ledger consistency checks.

---

## 18. Codex work breakdown and final artifacts

The sequence below is engineering order, not permission to stop after a schema-only milestone.

**E0 — Integrate and baseline.** Pin the actual branch/build, audit owners, source mapping, native bindings and current tests. Record the source-hierarchy decisions, including75% cap/explicit immunity and existing learning pity.

**E1 — Domain/config/persistence.** Implement strict codecs, immutable descriptors, named RNG substreams, trait selection, rank separation, state storage, migrations and legal-plan validation.

**E2 — Combat/passive operators.** Implement all 27 handlers against real native melee/projectile/action/status/resource paths. Complete shared post-application receipts, source provenance and rejection/budget handling. No new Skill records.

**E3 — Pack lifecycle and Packbound.** Implement atomic staged publication, finite initial minions, inheritance, aura membership, Avenger, Horde, state machine, guard recovery and anti-duplication.

**E4 — Immunities and authoring.** Deliver exact role affinity mappings, explicit immunity gate and tags, mandatory-accessibility tests, Super Unique file schema, template, persistent placement and export/reload commands.

**E5 — Rewards and presentation.** Extend existing XP slot, deterministic equipment quantity and modest learning factor/cap. Preserve pity/MF/Insight and canonical teaching sources. Deliver target-card tags, overhead identifier integration and palette-selection pipeline.

**E6 — End-to-end evidence.** Run deterministic tests, replay/crash tests, fuzz, native connected QA, performance comparison and existing-feature regression. Repair failures; update coverage and commands. Deliver a changelog with actual test outcomes, not a theoretical checklist marked complete.

Required checked-in reports:

```text
docs/enemies/INTEGRATION_MAP.md
docs/enemies/MASTER_ENEMIES_IMPLEMENTATION.md
docs/enemies/AFFIX_RUNTIME_COVERAGE.md
docs/enemies/ROLE_CAPABILITY_COVERAGE.md
docs/enemies/IMMUNITY_ACCESSIBILITY_MATRIX.md
docs/enemies/REWARD_REPLAY_QA.md
docs/enemies/CONNECTED_QA.md
docs/enemies/PERFORMANCE.md
```

Required runtime outputs: working mod artifact, all valid configuration assets, native binding manifest pinned to installed assets, maintained command documentation, isolated QA fixture data and automated tests. The owner should be able to use the implemented inspection/spawn/placement commands immediately after installation. No further gameplay decisions are required to implement the defaults in this document.

---

## Appendix A. Complete configuration seeds

These are complete JSON documents to create under the paths in§13. They are new ME domain files, **not** claimed native Hytale JSON schemas. All card parameter arrays use the declared difficulty order. Implement strict codecs for these structures and their per-affix parameter types; semantic rules in the main sections remain mandatory.

Do not replace the existing gear-loot configuration with these files. `native-bindings-v1.json` is the sole installation-specific generated manifest; its record contract follows Appendix B.

### A.1 `master-enemies-v1.json`

```json
{
  "schemaVersion": 1,
  "revision": "master-enemies-v1.0",
  "difficultyOrder": [
    "NORMAL",
    "NIGHTMARE",
    "HELL"
  ],
  "promotion": {
    "weights": {
      "NORMAL": 92,
      "CHAMPION": 2,
      "UNIQUE": 6
    },
    "championMembers": [
      2,
      4
    ],
    "uniqueMinions": [
      3,
      5
    ],
    "maximumPackMembers": 8,
    "specialPackLimitPerLoadedWorld": 12,
    "specialPackLimitPerHorizontalCell": 3,
    "horizontalCellMeters": 64,
    "ordinaryLeashMeters": 32,
    "minimumMinionSpawnDistanceMeters": 4,
    "maximumMinionSpawnDistanceMeters": 10,
    "promoteNativeBosses": false,
    "promoteNeutralWithoutExplicitBinding": false
  },
  "rarities": {
    "NORMAL": {
      "maxHealth": 1.0,
      "directDamage": 1.0,
      "xp": 1.0,
      "quantity": 1.0,
      "learn": 1.0,
      "learnCap": 0.0,
      "affixes": [
        0,
        0,
        0
      ]
    },
    "CHAMPION": {
      "maxHealth": 1.6,
      "directDamage": 1.05,
      "xp": 1.5,
      "quantity": 1.2,
      "learn": 1.15,
      "learnCap": 0.03,
      "affixes": [
        1,
        1,
        1
      ]
    },
    "UNIQUE": {
      "maxHealth": 2.4,
      "directDamage": 1.1,
      "xp": 2.0,
      "quantity": 1.5,
      "learn": 1.35,
      "learnCap": 0.06,
      "affixes": [
        1,
        2,
        3
      ]
    },
    "SUPER_UNIQUE": {
      "maxHealth": 3.0,
      "directDamage": 1.15,
      "xp": 2.5,
      "quantity": 1.8,
      "learn": 1.6,
      "learnCap": 0.09,
      "additionalAffixes": [
        0,
        1,
        2
      ]
    },
    "BOSS": {
      "maxHealth": 1.0,
      "directDamage": 1.0,
      "xp": 1.0,
      "quantity": 1.0,
      "learn": 1.75,
      "learnCap": 0.1,
      "automaticAffixes": [
        0,
        0,
        0
      ]
    }
  },
  "relationships": {
    "MINION": {
      "maxHealth": 1.15,
      "directDamage": 1.0,
      "xp": 1.05,
      "quantity": 1.0,
      "learn": 1.05,
      "learnCap": 0.005,
      "ownAffixCount": 0
    }
  },
  "affixRewards": {
    "firstCountAtBaseline": 1,
    "maximumCount": 4,
    "xpPerAdditionalAffix": 0.15,
    "quantityPerAdditionalAffix": 0.15,
    "learnPerAdditionalAffix": 0
  },
  "loot": {
    "quantityMode": "BONUS_FROM_FINALIZED_BASE_EQUIPMENT_SLOTS",
    "maximumEquipmentPerDefeat": 16,
    "modifyQualityWeights": false,
    "multiplyNativeMaterials": false
  },
  "learning": {
    "retainCanonicalSourceMapping": true,
    "retainWisdomFormula": true,
    "retainPity": true,
    "ordinaryChanceCap": 0.95,
    "bonusMode": "MIN_RELATIVE_BONUS_AND_RARITY_POINT_CAP",
    "allowAdditionalAbilityPerSource": false
  },
  "immunity": {
    "ordinaryElementalResistanceCap": 0.75,
    "ordinaryPenetrationBreaksImmunity": false,
    "nativeAffinityGrantChance": [
      0,
      0.2,
      1.0
    ],
    "affixGrantChance": [
      0,
      0.15,
      0.35
    ],
    "affixGrantRarities": [
      "UNIQUE",
      "SUPER_UNIQUE"
    ],
    "newElementalCaps": {
      "NORMAL": [
        0,
        1,
        1
      ],
      "CHAMPION": [
        0,
        1,
        1
      ],
      "UNIQUE": [
        0,
        1,
        2
      ],
      "SUPER_UNIQUE": [
        0,
        1,
        2
      ]
    },
    "randomPhysicalImmunity": false,
    "preserveExistingAuthoredNativeImmunities": true
  },
  "aggregateCaps": {
    "movementMultiplier": 1.5,
    "recoveryRateMultiplier": 1.5,
    "allDirectIncrease": 0.6,
    "physicalDirectIncrease": 0.6,
    "extraElementalPowerFraction": 0.3,
    "defenseIncrease": 1.0,
    "nonRarityMaxHealthIncrease": 0.5
  },
  "packRecovery": {
    "rosterCheckIntervalMs": 500,
    "strayTimeoutMs": 5000,
    "navigationFailureTimeoutMs": 10000,
    "loadedRecoveryTimeoutMs": 30000,
    "missingGuardCountsAsDefeated": false
  },
  "presentation": {
    "targetCardAllTagsRequired": true,
    "targetCardWidth": 420,
    "targetCardMaxWidth": 560,
    "viewportMargin": 16,
    "padding": 12,
    "tagGap": 4,
    "titleFontSize": 18,
    "tagFontSize": 14,
    "maxTitleLines": 2,
    "generatedNameMaxCodepoints": 32,
    "authoredNameMaxCodepoints": 40,
    "routineStateUpdatesPerSecond": 4,
    "overheadMode": "SUPPORTED_TEXT_IDENTIFIER"
  },
  "trace": {
    "detailedMaximumMillis": 30000,
    "detailedMaximumEvents": 10000
  },
  "qa": {
    "marker": "enemy-qa-enabled",
    "requiresCopiedSaveGuard": true,
    "qaEconomicRewards": false
  },
  "permissions": {
    "author": "inigmasgames.rpg.enemies.author"
  }
}
```

### A.2 `monster-affixes-v1.json`

`operator` selects the required typed handler. It is not a script name or new SkillId. Each inheritance policy has the exact semantics in its card; no dynamic evaluation of prose is permitted.

```json
{
  "schemaVersion": 1,
  "revision": "monster-affixes-v1.0",
  "difficultyOrder": [
    "NORMAL",
    "NIGHTMARE",
    "HELL"
  ],
  "definitions": [
    {
      "id": "ME-001",
      "key": "extra_fast",
      "operator": "EXTRA_FAST",
      "displayName": "Extra Fast",
      "tagTemplate": "Extra Fast",
      "randomWeights": [
        100,
        100,
        100
      ],
      "requiredCapabilities": [
        "MOBILE"
      ],
      "constraintGroups": [],
      "allowedRarities": [
        "CHAMPION",
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "movementIncrease": [
          0.2,
          0.25,
          0.3
        ],
        "recoveryRateIncrease": [
          0.1,
          0.15,
          0.2
        ]
      },
      "inheritance": {
        "mode": "MOVEMENT_ONLY",
        "parameterScale": 0.5
      },
      "positiveTestId": "ME-001-P",
      "negativeTestId": "ME-001-N"
    },
    {
      "id": "ME-002",
      "key": "extra_strong",
      "operator": "EXTRA_STRONG",
      "displayName": "Extra Strong",
      "tagTemplate": "Extra Strong",
      "randomWeights": [
        100,
        100,
        100
      ],
      "requiredCapabilities": [
        "PHYSICAL_DIRECT_HIT"
      ],
      "constraintGroups": [
        "OFFENSE"
      ],
      "allowedRarities": [
        "CHAMPION",
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "physicalIncrease": [
          0.25,
          0.35,
          0.45
        ]
      },
      "inheritance": {
        "mode": "PHYSICAL_INCREASE",
        "parameterScale": 0.5
      },
      "positiveTestId": "ME-002-P",
      "negativeTestId": "ME-002-N"
    },
    {
      "id": "ME-003",
      "key": "magic_resistant",
      "operator": "MAGIC_RESISTANT",
      "displayName": "Magic Resistant",
      "tagTemplate": "Magic Resistant",
      "randomWeights": [
        40,
        80,
        100
      ],
      "requiredCapabilities": [],
      "constraintGroups": [
        "SURVIVAL"
      ],
      "allowedRarities": [
        "CHAMPION",
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "elementalResistanceAdd": [
          0.15,
          0.2,
          0.25
        ]
      },
      "inheritance": {
        "mode": "NONE"
      },
      "positiveTestId": "ME-003-P",
      "negativeTestId": "ME-003-N"
    },
    {
      "id": "ME-004",
      "key": "stone_skin",
      "operator": "STONE_SKIN",
      "displayName": "Stone Skin",
      "tagTemplate": "Stone Skin",
      "randomWeights": [
        60,
        80,
        100
      ],
      "requiredCapabilities": [
        "DEFENSE_STAT"
      ],
      "constraintGroups": [
        "SURVIVAL"
      ],
      "allowedRarities": [
        "CHAMPION",
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "defenseIncrease": [
          0.4,
          0.6,
          0.8
        ]
      },
      "inheritance": {
        "mode": "NONE"
      },
      "positiveTestId": "ME-004-P",
      "negativeTestId": "ME-004-N"
    },
    {
      "id": "ME-005",
      "key": "fire_enchanted",
      "operator": "FIRE_ENCHANTED",
      "displayName": "Fire Enchanted",
      "tagTemplate": "Fire Enchanted",
      "randomWeights": [
        70,
        80,
        90
      ],
      "requiredCapabilities": [
        "DIRECT_HIT"
      ],
      "constraintGroups": [
        "ELEMENTAL",
        "OFFENSE"
      ],
      "allowedRarities": [
        "CHAMPION",
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "extraPowerFraction": [
          0.15,
          0.2,
          0.25
        ],
        "resistanceAdd": [
          0.2,
          0.3,
          0.4
        ],
        "channel": "FIRE"
      },
      "inheritance": {
        "mode": "EXTRA_ELEMENTAL_POWER_ONLY",
        "parameterScale": 0.5
      },
      "positiveTestId": "ME-005-P",
      "negativeTestId": "ME-005-N"
    },
    {
      "id": "ME-006",
      "key": "cold_enchanted",
      "operator": "COLD_ENCHANTED",
      "displayName": "Cold Enchanted",
      "tagTemplate": "Cold Enchanted",
      "randomWeights": [
        70,
        80,
        90
      ],
      "requiredCapabilities": [
        "DIRECT_HIT",
        "HOSTILE_STATUS_ADMISSION"
      ],
      "constraintGroups": [
        "ELEMENTAL",
        "OFFENSE",
        "CONTROL"
      ],
      "allowedRarities": [
        "CHAMPION",
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "extraPowerFraction": [
          0.15,
          0.2,
          0.25
        ],
        "resistanceAdd": [
          0.2,
          0.3,
          0.4
        ],
        "channel": "WATER",
        "status": "CHILL",
        "statusChance": [
          0.2,
          0.25,
          0.3
        ],
        "stacksPerAcceptedPackage": 1,
        "opportunityLockMs": 1000
      },
      "inheritance": {
        "mode": "EXTRA_ELEMENTAL_POWER_ONLY",
        "parameterScale": 0.5
      },
      "positiveTestId": "ME-006-P",
      "negativeTestId": "ME-006-N"
    },
    {
      "id": "ME-007",
      "key": "lightning_enchanted",
      "operator": "LIGHTNING_ENCHANTED",
      "displayName": "Lightning Enchanted",
      "tagTemplate": "Lightning Enchanted",
      "randomWeights": [
        70,
        80,
        90
      ],
      "requiredCapabilities": [
        "DIRECT_HIT",
        "HOSTILE_STATUS_ADMISSION"
      ],
      "constraintGroups": [
        "ELEMENTAL",
        "OFFENSE"
      ],
      "allowedRarities": [
        "CHAMPION",
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "extraPowerFraction": [
          0.15,
          0.2,
          0.25
        ],
        "resistanceAdd": [
          0.2,
          0.3,
          0.4
        ],
        "channel": "LIGHTNING",
        "status": "ELECTRIFIED",
        "statusChance": [
          0.2,
          0.25,
          0.3
        ],
        "stacksPerAcceptedPackage": 1,
        "opportunityLockMs": 1000
      },
      "inheritance": {
        "mode": "EXTRA_ELEMENTAL_POWER_ONLY",
        "parameterScale": 0.5
      },
      "positiveTestId": "ME-007-P",
      "negativeTestId": "ME-007-N"
    },
    {
      "id": "ME-008",
      "key": "poison_enchanted",
      "operator": "POISON_ENCHANTED",
      "displayName": "Poison Enchanted",
      "tagTemplate": "Poison Enchanted",
      "randomWeights": [
        50,
        70,
        90
      ],
      "requiredCapabilities": [
        "DIRECT_HIT",
        "HOSTILE_STATUS_ADMISSION"
      ],
      "constraintGroups": [
        "ELEMENTAL",
        "OFFENSE"
      ],
      "allowedRarities": [
        "CHAMPION",
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "status": "POISON",
        "statusChance": [
          0.3,
          0.35,
          0.4
        ],
        "poisonPowerFraction": [
          0.5,
          0.6,
          0.7
        ],
        "earthResistanceAdd": [
          0.2,
          0.3,
          0.4
        ],
        "stacksPerAcceptedPackage": 1,
        "opportunityLockMs": 1000
      },
      "inheritance": {
        "mode": "POISON_PACKAGE",
        "statusChance": 0.15,
        "potencyScale": 0.5,
        "opportunityLockMs": 1000
      },
      "positiveTestId": "ME-008-P",
      "negativeTestId": "ME-008-N"
    },
    {
      "id": "ME-009",
      "key": "wind_enchanted",
      "operator": "WIND_ENCHANTED",
      "displayName": "Wind Enchanted",
      "tagTemplate": "Wind Enchanted",
      "randomWeights": [
        50,
        60,
        70
      ],
      "requiredCapabilities": [
        "DIRECT_HIT"
      ],
      "constraintGroups": [
        "ELEMENTAL",
        "OFFENSE"
      ],
      "allowedRarities": [
        "CHAMPION",
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "extraPowerFraction": [
          0.15,
          0.2,
          0.25
        ],
        "resistanceAdd": [
          0.2,
          0.3,
          0.4
        ],
        "channel": "WIND"
      },
      "inheritance": {
        "mode": "EXTRA_ELEMENTAL_POWER_ONLY",
        "parameterScale": 0.5
      },
      "positiveTestId": "ME-009-P",
      "negativeTestId": "ME-009-N"
    },
    {
      "id": "ME-010",
      "key": "earth_enchanted",
      "operator": "EARTH_ENCHANTED",
      "displayName": "Earth Enchanted",
      "tagTemplate": "Earth Enchanted",
      "randomWeights": [
        50,
        60,
        70
      ],
      "requiredCapabilities": [
        "DIRECT_HIT"
      ],
      "constraintGroups": [
        "ELEMENTAL",
        "OFFENSE"
      ],
      "allowedRarities": [
        "CHAMPION",
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "extraPowerFraction": [
          0.15,
          0.2,
          0.25
        ],
        "resistanceAdd": [
          0.2,
          0.3,
          0.4
        ],
        "channel": "EARTH"
      },
      "inheritance": {
        "mode": "EXTRA_ELEMENTAL_POWER_ONLY",
        "parameterScale": 0.5
      },
      "positiveTestId": "ME-010-P",
      "negativeTestId": "ME-010-N"
    },
    {
      "id": "ME-011",
      "key": "void_enchanted",
      "operator": "VOID_ENCHANTED",
      "displayName": "Void Enchanted",
      "tagTemplate": "Void Enchanted",
      "randomWeights": [
        50,
        60,
        70
      ],
      "requiredCapabilities": [
        "DIRECT_HIT"
      ],
      "constraintGroups": [
        "ELEMENTAL",
        "OFFENSE"
      ],
      "allowedRarities": [
        "CHAMPION",
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "extraPowerFraction": [
          0.15,
          0.2,
          0.25
        ],
        "resistanceAdd": [
          0.2,
          0.3,
          0.4
        ],
        "channel": "VOID"
      },
      "inheritance": {
        "mode": "EXTRA_ELEMENTAL_POWER_ONLY",
        "parameterScale": 0.5
      },
      "positiveTestId": "ME-011-P",
      "negativeTestId": "ME-011-N"
    },
    {
      "id": "ME-012",
      "key": "spectral_hit",
      "operator": "SPECTRAL_HIT",
      "displayName": "Spectral Hit",
      "tagTemplate": "Spectral Hit",
      "randomWeights": [
        30,
        50,
        70
      ],
      "requiredCapabilities": [
        "DIRECT_HIT"
      ],
      "constraintGroups": [
        "ELEMENTAL",
        "OFFENSE"
      ],
      "allowedRarities": [
        "CHAMPION",
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "extraPowerFraction": [
          0.2,
          0.25,
          0.3
        ],
        "channels": [
          "WIND",
          "WATER",
          "FIRE",
          "EARTH",
          "LIGHTNING",
          "VOID"
        ]
      },
      "inheritance": {
        "mode": "EXTRA_ELEMENTAL_POWER_ONLY",
        "parameterScale": 0.5
      },
      "positiveTestId": "ME-012-P",
      "negativeTestId": "ME-012-N"
    },
    {
      "id": "ME-013",
      "key": "mana_burn",
      "operator": "MANA_BURN",
      "displayName": "Mana Burn",
      "tagTemplate": "Mana Burn",
      "randomWeights": [
        25,
        50,
        70
      ],
      "requiredCapabilities": [
        "DIRECT_HIT",
        "APPLIED_HIT_RECEIPTS"
      ],
      "constraintGroups": [
        "DENIAL"
      ],
      "allowedRarities": [
        "CHAMPION",
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "maxManaFraction": [
          0.02,
          0.03,
          0.04
        ],
        "damageFractionCap": 0.25,
        "opportunityLockMs": 1000,
        "victimAggregateMaxManaFractionPerSecond": 0.08
      },
      "inheritance": {
        "mode": "NONE"
      },
      "positiveTestId": "ME-013-P",
      "negativeTestId": "ME-013-N"
    },
    {
      "id": "ME-014",
      "key": "cursed",
      "operator": "CURSED",
      "displayName": "Cursed",
      "tagTemplate": "Cursed",
      "randomWeights": [
        30,
        50,
        60
      ],
      "requiredCapabilities": [
        "DIRECT_HIT",
        "HOSTILE_STATUS_ADMISSION"
      ],
      "constraintGroups": [
        "DENIAL"
      ],
      "allowedRarities": [
        "CHAMPION",
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "statusChance": [
          0.25,
          0.3,
          0.35
        ],
        "outgoingDirectReduction": [
          0.1,
          0.125,
          0.15
        ],
        "durationMs": 4000,
        "opportunityLockMs": 2000,
        "statusPackage": "ME_WEAKENED"
      },
      "inheritance": {
        "mode": "NONE"
      },
      "positiveTestId": "ME-014-P",
      "negativeTestId": "ME-014-N"
    },
    {
      "id": "ME-015",
      "key": "knockback",
      "operator": "KNOCKBACK",
      "displayName": "Knockback",
      "tagTemplate": "Knockback",
      "randomWeights": [
        35,
        50,
        60
      ],
      "requiredCapabilities": [
        "DIRECT_HIT",
        "HOSTILE_STATUS_ADMISSION"
      ],
      "constraintGroups": [
        "CONTROL"
      ],
      "allowedRarities": [
        "CHAMPION",
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "statusChance": [
          0.2,
          0.25,
          0.3
        ],
        "horizontalDisplacementMeters": [
          0.5,
          0.65,
          0.8
        ],
        "opportunityLockMs": 3000,
        "verticalDisplacementMeters": 0
      },
      "inheritance": {
        "mode": "NONE"
      },
      "positiveTestId": "ME-015-P",
      "negativeTestId": "ME-015-N"
    },
    {
      "id": "ME-016",
      "key": "vampiric",
      "operator": "VAMPIRIC",
      "displayName": "Vampiric",
      "tagTemplate": "Vampiric",
      "randomWeights": [
        25,
        45,
        60
      ],
      "requiredCapabilities": [
        "DIRECT_HIT",
        "APPLIED_HIT_RECEIPTS"
      ],
      "constraintGroups": [
        "SURVIVAL"
      ],
      "allowedRarities": [
        "CHAMPION",
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "healthLeechFraction": [
          0.05,
          0.07,
          0.1
        ],
        "maxOwnHealthFractionPerSecond": 0.01
      },
      "inheritance": {
        "mode": "NONE"
      },
      "positiveTestId": "ME-016-P",
      "negativeTestId": "ME-016-N"
    },
    {
      "id": "ME-017",
      "key": "unwavering",
      "operator": "UNWAVERING",
      "displayName": "Unwavering",
      "tagTemplate": "Unwavering",
      "randomWeights": [
        25,
        40,
        55
      ],
      "requiredCapabilities": [],
      "constraintGroups": [],
      "allowedRarities": [
        "CHAMPION",
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "statusImmunityFamilies": [
          "STUN_STAGGER"
        ]
      },
      "inheritance": {
        "mode": "NONE"
      },
      "positiveTestId": "ME-017-P",
      "negativeTestId": "ME-017-N"
    },
    {
      "id": "ME-018",
      "key": "unstoppable",
      "operator": "UNSTOPPABLE",
      "displayName": "Unstoppable",
      "tagTemplate": "Unstoppable",
      "randomWeights": [
        25,
        40,
        55
      ],
      "requiredCapabilities": [
        "MOBILE"
      ],
      "constraintGroups": [],
      "allowedRarities": [
        "CHAMPION",
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "immuneMovementComponent": "SLOW_ONLY"
      },
      "inheritance": {
        "mode": "NONE"
      },
      "positiveTestId": "ME-018-P",
      "negativeTestId": "ME-018-N"
    },
    {
      "id": "ME-019",
      "key": "frenzied",
      "operator": "FRENZIED",
      "displayName": "Frenzied",
      "tagTemplate": "Frenzied",
      "randomWeights": [
        25,
        45,
        60
      ],
      "requiredCapabilities": [
        "DIRECT_HIT"
      ],
      "constraintGroups": [
        "OFFENSE"
      ],
      "allowedRarities": [
        "CHAMPION",
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "cycleMs": 12000,
        "activeWindowStartMs": 8000,
        "activeWindowDurationMs": 4000,
        "allDirectIncrease": [
          0.2,
          0.25,
          0.3
        ],
        "movementIncrease": [
          0.1,
          0.125,
          0.15
        ],
        "recoveryRateIncrease": [
          0.1,
          0.125,
          0.15
        ]
      },
      "inheritance": {
        "mode": "NONE"
      },
      "positiveTestId": "ME-019-P",
      "negativeTestId": "ME-019-N"
    },
    {
      "id": "ME-020",
      "key": "avenger",
      "operator": "AVENGER",
      "displayName": "Avenger",
      "tagTemplate": "Avenger {stacks}/5",
      "randomWeights": [
        15,
        35,
        50
      ],
      "requiredCapabilities": [
        "PACK_LEADER",
        "MINION_ROSTER",
        "DIRECT_HIT"
      ],
      "constraintGroups": [
        "LEADER",
        "OFFENSE"
      ],
      "allowedRarities": [
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "perDefeatedMinionDirectIncrease": [
          0.05,
          0.06,
          0.07
        ],
        "perDefeatedMinionMovementIncrease": 0.02,
        "maxStacks": 5
      },
      "inheritance": {
        "mode": "NONE"
      },
      "positiveTestId": "ME-020-P",
      "negativeTestId": "ME-020-N"
    },
    {
      "id": "ME-021",
      "key": "empowered_minions",
      "operator": "EMPOWERED_MINIONS",
      "displayName": "Empowered Minions",
      "tagTemplate": "Empowered Minions",
      "randomWeights": [
        25,
        45,
        60
      ],
      "requiredCapabilities": [
        "PACK_LEADER",
        "MINION_ROSTER"
      ],
      "constraintGroups": [
        "LEADER"
      ],
      "allowedRarities": [
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "minionMaxHealthIncrease": [
          0.15,
          0.2,
          0.25
        ],
        "minionDirectIncrease": [
          0.1,
          0.125,
          0.15
        ],
        "minionMovementIncrease": [
          0.05,
          0.075,
          0.1
        ]
      },
      "inheritance": {
        "mode": "EMPOWERED_MINION_SNAPSHOT"
      },
      "positiveTestId": "ME-021-P",
      "negativeTestId": "ME-021-N"
    },
    {
      "id": "ME-022",
      "key": "horde",
      "operator": "HORDE",
      "displayName": "Horde",
      "tagTemplate": "Horde",
      "randomWeights": [
        15,
        25,
        35
      ],
      "requiredCapabilities": [
        "PACK_LEADER",
        "MINION_ROSTER"
      ],
      "constraintGroups": [
        "LEADER"
      ],
      "allowedRarities": [
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "additionalInitialMinions": 2,
        "maximumPackMembers": 8
      },
      "inheritance": {
        "mode": "NONE"
      },
      "positiveTestId": "ME-022-P",
      "negativeTestId": "ME-022-N"
    },
    {
      "id": "ME-023",
      "key": "aura_enchanted",
      "operator": "AURA_ENCHANTED",
      "displayName": "Aura Enchanted",
      "tagTemplate": "Aura: {selectorLabel}",
      "randomWeights": [
        20,
        35,
        50
      ],
      "requiredCapabilities": [
        "PACK_LEADER",
        "MINION_ROSTER"
      ],
      "constraintGroups": [
        "LEADER"
      ],
      "allowedRarities": [
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "selectorWeights": {
          "MIGHT": 1,
          "WARD": 1,
          "HASTE": 1
        },
        "radiusMeters": 8,
        "membershipUpdateMs": 250,
        "providerExpiryMs": 500,
        "mightPhysicalIncrease": [
          0.15,
          0.2,
          0.25
        ],
        "wardElementalResistanceAdd": [
          0.1,
          0.15,
          0.2
        ],
        "hasteMovementIncrease": [
          0.1,
          0.125,
          0.15
        ],
        "hasteRecoveryRateIncrease": [
          0.05,
          0.075,
          0.1
        ]
      },
      "inheritance": {
        "mode": "PACK_AURA_MEMBERSHIP_ONLY"
      },
      "positiveTestId": "ME-023-P",
      "negativeTestId": "ME-023-N"
    },
    {
      "id": "ME-024",
      "key": "packbound",
      "operator": "PACKBOUND",
      "displayName": "Packbound",
      "tagTemplate": "Packbound: {guardLabel}",
      "randomWeights": [
        8,
        18,
        25
      ],
      "requiredCapabilities": [
        "PACK_LEADER",
        "MINION_ROSTER"
      ],
      "constraintGroups": [
        "LEADER",
        "SURVIVAL"
      ],
      "allowedRarities": [
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "minimumInitialGuards": 2,
        "maximumInitialGuards": 5,
        "guardAreaMeters": 24,
        "allDamageBlocked": true,
        "allExternalStatusMutationsBlocked": true
      },
      "inheritance": {
        "mode": "NONE"
      },
      "positiveTestId": "ME-024-P",
      "negativeTestId": "ME-024-N"
    },
    {
      "id": "ME-025",
      "key": "armor_breaker",
      "operator": "ARMOR_BREAKER",
      "displayName": "Armor Breaker",
      "tagTemplate": "Armor Breaker",
      "randomWeights": [
        25,
        40,
        55
      ],
      "requiredCapabilities": [
        "DIRECT_HIT",
        "HOSTILE_STATUS_ADMISSION"
      ],
      "constraintGroups": [],
      "allowedRarities": [
        "CHAMPION",
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "statusChance": [
          0.2,
          0.25,
          0.3
        ],
        "defenseReduction": [
          0.15,
          0.2,
          0.25
        ],
        "durationMs": 4000,
        "opportunityLockMs": 2000,
        "statusPackage": "ME_ARMOR_BROKEN"
      },
      "inheritance": {
        "mode": "NONE"
      },
      "positiveTestId": "ME-025-P",
      "negativeTestId": "ME-025-N"
    },
    {
      "id": "ME-026",
      "key": "reflective",
      "operator": "REFLECTIVE",
      "displayName": "Reflective",
      "tagTemplate": "Reflective",
      "randomWeights": [
        10,
        20,
        30
      ],
      "requiredCapabilities": [
        "APPLIED_HIT_RECEIPTS"
      ],
      "constraintGroups": [],
      "allowedRarities": [
        "CHAMPION",
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "healthDamageReflectFraction": [
          0.06,
          0.08,
          0.1
        ],
        "maxVictimHealthFractionPerHit": 0.01,
        "maxVictimHealthFractionPerSecond": 0.02,
        "maximumOriginDistanceMeters": 24
      },
      "inheritance": {
        "mode": "NONE"
      },
      "positiveTestId": "ME-026-P",
      "negativeTestId": "ME-026-N"
    },
    {
      "id": "ME-027",
      "key": "bulwark",
      "operator": "BULWARK",
      "displayName": "Bulwark",
      "tagTemplate": "Bulwark",
      "randomWeights": [
        20,
        35,
        50
      ],
      "requiredCapabilities": [
        "SHIELD_OWNER"
      ],
      "constraintGroups": [
        "SURVIVAL"
      ],
      "allowedRarities": [
        "CHAMPION",
        "UNIQUE",
        "SUPER_UNIQUE",
        "BOSS"
      ],
      "parameters": {
        "initialShieldMaxHealthFraction": [
          0.15,
          0.2,
          0.25
        ],
        "regenerates": false
      },
      "inheritance": {
        "mode": "NONE"
      },
      "positiveTestId": "ME-027-P",
      "negativeTestId": "ME-027-N"
    }
  ]
}
```

### A.3 `affinities-v1.json`

```json
{
  "schemaVersion": 1,
  "revision": "enemy-affinities-v1.0",
  "applyToNativeBossEncounter": false,
  "roleEntries": [
    {
      "canonicalRoleId": "Golem_Crystal_Flame",
      "channel": "FIRE",
      "rawResistanceFloor": [
        0.2,
        0.4,
        0.6
      ]
    },
    {
      "canonicalRoleId": "Golem_Firesteel",
      "channel": "FIRE",
      "rawResistanceFloor": [
        0.2,
        0.4,
        0.6
      ]
    },
    {
      "canonicalRoleId": "Spirit_Ember",
      "channel": "FIRE",
      "rawResistanceFloor": [
        0.2,
        0.4,
        0.6
      ]
    },
    {
      "canonicalRoleId": "Emberwulf",
      "channel": "FIRE",
      "rawResistanceFloor": [
        0.2,
        0.4,
        0.6
      ]
    },
    {
      "canonicalRoleId": "Slug_Magma",
      "channel": "FIRE",
      "rawResistanceFloor": [
        0.2,
        0.4,
        0.6
      ]
    },
    {
      "canonicalRoleId": "Toad_Rhino_Magma",
      "channel": "FIRE",
      "rawResistanceFloor": [
        0.2,
        0.4,
        0.6
      ]
    },
    {
      "canonicalRoleId": "Golem_Crystal_Frost",
      "channel": "WATER",
      "rawResistanceFloor": [
        0.2,
        0.4,
        0.6
      ]
    },
    {
      "canonicalRoleId": "Spirit_Frost",
      "channel": "WATER",
      "rawResistanceFloor": [
        0.2,
        0.4,
        0.6
      ]
    },
    {
      "canonicalRoleId": "Golem_Crystal_Thunder",
      "channel": "LIGHTNING",
      "rawResistanceFloor": [
        0.2,
        0.4,
        0.6
      ]
    },
    {
      "canonicalRoleId": "Spirit_Thunder",
      "channel": "LIGHTNING",
      "rawResistanceFloor": [
        0.2,
        0.4,
        0.6
      ]
    },
    {
      "canonicalRoleId": "Golem_Crystal_Earth",
      "channel": "EARTH",
      "rawResistanceFloor": [
        0.2,
        0.4,
        0.6
      ]
    },
    {
      "canonicalRoleId": "Golem_Crystal_Sand",
      "channel": "EARTH",
      "rawResistanceFloor": [
        0.2,
        0.4,
        0.6
      ]
    },
    {
      "canonicalRoleId": "Spirit_Root",
      "channel": "EARTH",
      "rawResistanceFloor": [
        0.2,
        0.4,
        0.6
      ]
    },
    {
      "canonicalRoleId": "Feran_Windwalker",
      "channel": "WIND",
      "rawResistanceFloor": [
        0.2,
        0.4,
        0.6
      ]
    },
    {
      "canonicalRoleId": "Void_Spawn_Entombed",
      "channel": "VOID",
      "rawResistanceFloor": [
        0.2,
        0.4,
        0.6
      ]
    },
    {
      "canonicalRoleId": "Void_Spectre",
      "channel": "VOID",
      "rawResistanceFloor": [
        0.2,
        0.4,
        0.6
      ]
    }
  ]
}
```

### A.4 `visual-variants-v1.json`

A valid empty authored-art map is intentional: the artist has not supplied rarity-specific textures in this request. The runtime selection, validation and QA demonstration are still required; do not invent existing asset paths.

```json
{
  "schemaVersion": 1,
  "revision": "enemy-visual-variants-v1.0",
  "selectionOrder": [
    "SUPER_UNIQUE_OVERRIDE",
    "EXACT_ROLE_RARITY_VARIANT",
    "CERTIFIED_RUNTIME_TINT",
    "NATIVE_TEXTURE"
  ],
  "runtimeTint": {
    "enabled": false,
    "requiresCertifiedInstalledAdapter": true
  },
  "variants": []
}
```

### A.5 `super-uniques-v1.json`

```json
{
  "schemaVersion": 1,
  "revision": "enemy-super-uniques-v1.0",
  "templates": [
    {
      "id": "grimgor_the_ashen",
      "revision": 1,
      "name": "Grimgor the Ashen",
      "canonicalRoleId": "Trork_Warrior",
      "difficultyModes": [
        "NORMAL",
        "NIGHTMARE",
        "HELL"
      ],
      "baseProfileMode": "INHERIT_ROLE",
      "rarity": "SUPER_UNIQUE",
      "maxHealthFactor": 3.0,
      "directDamageFactor": 1.15,
      "fixedAffixes": [
        {
          "affixId": "ME-005"
        },
        {
          "affixId": "ME-024"
        }
      ],
      "randomAdditionalAffixes": [
        0,
        1,
        2
      ],
      "minions": [
        {
          "canonicalRoleId": "Trork_Warrior",
          "count": 4,
          "countsForPackbound": true
        }
      ],
      "explicitImmuneChannelsByMode": {
        "NORMAL": [],
        "NIGHTMARE": [],
        "HELL": [
          "FIRE"
        ]
      },
      "visualVariantId": null,
      "lootProfileOverrideId": null,
      "placement": {
        "mode": "ADMIN_PLACED_ANCHOR",
        "maximumLivePerAnchor": 1,
        "leashMeters": 32
      },
      "respawn": {
        "mode": "REUSABLE",
        "delaySeconds": 900
      },
      "learningSourceMode": "CANONICAL_ROLE"
    }
  ]
}
```

### A.6 `name-pools-v1.json`

```json
{
  "schemaVersion": 1,
  "revision": "enemy-name-pools-v1.0",
  "format": "{stem} the {epithet}",
  "stems": [
    "Gorefang",
    "Ashclaw",
    "Ironhide",
    "Grimtooth",
    "Duskmaw",
    "Stormscar",
    "Ruinbone",
    "Dreadtusk"
  ],
  "epithets": [
    "Cruel",
    "Relentless",
    "Scarred",
    "Stubborn",
    "Hollow",
    "Watchful",
    "Ashen",
    "Unbroken"
  ],
  "maxGeneratedCodepoints": 32,
  "championNameMode": "CANONICAL_ROLE_DISPLAY",
  "superUniqueNameMode": "AUTHORED"
}
```

---

## Appendix B. Installation-specific binding and display record contracts

### B.1 Native binding schema

Each record in the generated `native-bindings-v1.json` must contain the following fields. These are structural contracts, not fabricated asset values:

```text
schemaVersion: 1
revision: string
serverJarSha256: 64-lowercase-hex
assetsSha256: 64-lowercase-hex
bindings: array of {
  canonicalRoleId: exact resolved existing identity,
  nativeRoleIds: nonempty exact-ID array,
  encounterProfileIdsByMode: map of enabled mode -> existing profileId,
  modelAssetId: existing resolved model asset,
  defaultTextureAssetIds: nonempty resolved asset array,
  capabilities: validated enum array,
  defenseBinding: {ownerSymbol, valueField, mitigationOwnerSymbol} or null,
  movementBinding: {ownerSymbol, speedField, baselineUnit} or null,
  healthResourceBinding: {ownerSymbol, receiptProducerSymbol},
  actionBindings: array of {
    nativeActionId, sourceProducerSymbol, rootIdProducerSymbol,
    authoredStrikeKeys, nativeCauseToCanonicalChannelMap,
    procCoefficientPolicyId, offenseSnapshotBoundary,
    appliedHitReceiptConsumerSymbol,
    scalableRecoveryBinding: {ownerSymbol, fieldOrProfileId} or null,
    preservesAnimationAndHitCount: true,
    positiveNativeTestId, negativeNativeTestId
  },
  controlProfileId: existing resolved profile,
  sourceValidationId: existing validated source or no-learning classification,
  productionPromotionEnabled: boolean,
  evidenceFiles: nonempty array for every enabled binding
}
```

Capabilities cannot be asserted without matching owner/action evidence. Unsupported fields are null only when the actor does not need that capability and its eligible affix set remains meaningful. Null is not acceptable for a required path of an enabled definition on a certified role. At least one real certified role must support each admitted handler and its significant optional component, including scalable NPC recovery and existing shields.

### B.2 Artist variant entry

A future nonempty `variants` entry has strict fields: `id`, `canonicalRoleId`, `enemyRarity`, `weight`, `textureOverrides` and optional `superUniqueTemplateId`. Each `textureOverrides` entry has `modelAssetId`, `originalTextureAssetId` and `replacementTextureAssetId`, all validated against the installed/resolved asset graph. Overrides may not change geometry, attachment references, animation sets or collision. Weight is a positive integer used only in the cosmetic substream.

If a role has several model attachments/textures, change only listed references and leave the rest untouched. Record selection in the enemy descriptor. An empty override list for a declared variant is invalid; omit the variant instead. Super Unique template override points to one existing variant ID, not arbitrary JSON embedded in a command.

### B.3 Display DTO and tag record

```text
EnemyDisplayDto {
  worldId, logicalActorId, generation, descriptorRevision, stateRevision,
  name, baseRoleDisplayName, combatLevel, rarityLabel, packRoleLabel,
  ownAffixTags[], inheritedEffectTags[], activeDefenseTags[], stateTags[],
  remainingGuardCount?, paletteVariantId?, currentHealthRef, shieldRef?
}
EnemyTag {
  stableTagId, sourceKind, sourceId, labelKey, labelParameters,
  fallbackText, semanticStyle, sortPriority, mechanicDetailKey
}
```

`stableTagId` identifies the state/source, not a hash of the translated text. Packbound's `guardLabel` is the decimal living-guard count while guarded and the localized word `Broken` after release; the DTO still retains an integer `remainingGuardCount` for arithmetic. Never format an unreleased unresolved roster as Broken. A change from guardCount1 to0 updates the Packbound record and removes only the Invulnerable record. A Fire immunity source disappearing removes `Fire Immune` only when no other active source still grants it. Inherited tags do not enter the explicit reward-affix count. Affix and immunity labels are not tooltips-only; they are rendered on the target card itself.

### B.4 Negative stat package contract

`ME_WEAKENED`: familyNEGATIVE_STAT, fieldOUTGOING_DIRECT_DAMAGE, modeREDUCED_FRACTION, stackingSTRONGEST_WITH_INDEPENDENT_PROVIDER_EXPIRY, duration4000 ms, cleansable as an ordinary hostile stat debuff, standard status admission. It does not produce its own tick or new skill cast.

`ME_ARMOR_BROKEN`: familyNEGATIVE_STAT, fieldPHYSICAL_DEFENSE, modeREDUCED_FRACTION, same provider/expiry and admission policy, duration4000 ms, nonnegative final Defense. Resolve into the established Armor Shred/reduction owner rather than parallel multiplication.

If the existing source uses a more specific stat/status naming convention, map these semantic operators explicitly. Do not identify them by a player-visible string and do not silently redirect them to elemental resistance, attack speed or armor durability.

---

## Appendix C. Excluded brainstorm entries and duplicate names

These are **not** unfinished admitted affixes. They are outside v1's approved27-entry runtime and must not appear in the random pool, Super Unique selector or coverage denominator.

| Brainstorm idea | Disposition |
|---|---|
|Extra Fast/Fast/Hasted|One Extra Fast identity; Aura Haste is a selector of a different pack-only affix. |
|Extra Strong/Powerful|One Extra Strong identity. |
|Empowered Minions/Powerful Minions|One Empowered Minions identity. |
|Frenzied/Periodic Enrage|One Frenzied identity. |
|Avenger/Soul Eater|One bounded Avenger identity; arbitrary nearby souls/model growth excluded. |
|Shielding/Shell|Only finite initial Bulwark included; periodic invulnerability is not implied. |
|Nightmarish/Terrifying/Fear on hit|Excluded: do not change the existing restriction on hard Fear affecting players just to add an affix. |
|Gargantuan|Excluded: no geometry, hitbox, reach or navigation scaling in this pass. |
|Health Link/Treant-Protected|Excluded: multi-actor damage redirection and shared-health ordering add a separate integration burden; Packbound is not a damage-sharing substitute. |
|Multi-Shot/additional projectiles|Excluded: new projectile patterns/ownership paths. |
|Teleporting|Excluded: new combat reposition ability; pack-recovery lifecycle is not this affix. |
|Illusionist/Shadow Enchanted|Excluded: combat clones and new attacks/entities. |
|Summoner/Death Spawns/Reviving Minions/Soul Conduit|Excluded: dynamic spawning/resurrection and repeat-reward lifecycle; Horde only changes finite initial roster. |
|Drought Bringer/flask-charge drain/recovery-above50% denial|Excluded: no new consumable/Health-recovery lock subsystem; Mana Burn is the bounded existing-resource effect. |
|Hinder/periodic invulnerability aura|Excluded: no hostile field, no passive invulnerability cycles beyond explicit Packbound. |
|Proximal Tangibility/Suppressor/Temporal Bubble|Excluded: no origin-distance damage dome or projectile interception ability. Reflective's safety range is not a dome. |
|Fire Chains, Arcane Enchanted, Mortar, Waller, Jailer, Vortex, Wormhole|Excluded: new attack, terrain, displacement-field or trap mechanics. |
|Frozen crystals, Frozen Pulse, Orbiter, Thunderstorm, Molten, Plagued, Desecrator|Excluded: spawned hazards, trails, novas and on-death attacks. |
|Chilling Wind, Tempest, Explosive, Electrified Obelisks, Shock Lance, Hellbound|Excluded: battlefield objects and new active hazard behavior. |
|Barrier wall, Strong Winds, Frozen Ground, Serpent Totem, Siphon beam|Excluded: geometry, forced field movement, totems or beam casts. |
|Hexer, Effigy, Storm Herald, Volatile Plants, Volatile Crag|Excluded: proxies, spawned controllers and pursuit/explosion skills. |

Fire/Cold/Lightning Enchanted in this document intentionally do **not** recreate their original on-death or retaliation abilities. All selected affixes have the exact adapted behavior in their cards. Adding any excluded effect later requires an explicit new scope revision, not a hidden “faithful implementation” change by Codex.

---

## Appendix D. Source register and provenance

**[S1]** *Hytale RPG Master Implementation Specification v1.7.0.docx*, retrieved from the user's Library for this task. Relevant source sections:02.3 canonical elements;04.3 status/control semantics;04.4 ordinary resistance/status admission;04.4.1 explicit difficulty-aware immunity;05.1 XP/encounter eligibility;05.2 unified skill/passive acquisition and pity;AppendixG canonical teaching-source mapping. These are source design contracts, not proof of the current installed deployment. New ME decisions explicitly extend them as stated.

**[S2]** *Master_Affix_v1.0.md*,30 September 2026, retrieved from the user's Library. Relevant source sections:2 runtime ownership/snapshots;4 multi-channel arithmetic and native application receipts;5 timing/native adapters;6 status/proc/recovery;10 Sentinel inheritance;12 evidence requirements. Its160 gear identities, item carrier legality, gear balance and scopes remain separate and unchanged.

**[S3]** *COMMANDS.md*, provided current maintained command reference. Existing ordinary commands include `/rpg stats`, `/rpg difficulty inspect`, and `/rpg-trace normal|detailed|performance`; registration and the maintained file are authoritative. The new `/rpg enemies` family is proposed here for implementation, not reported as existing.

**[S4]** *monster-role-catalog.md*, role audit of the installed pre-release asset archive inspected25 September 2026. This is a catalog of hostile/provokable role identities, including explicit exclusions and phase/variant distinctions. It is not a spawn table, skill assignment or proof that every entry deserves a reward.

**[S5]** *loot-profiles-v1.json*, retrieved Library snapshot, revision`gear-loot-picks-v1`. It defines Common/Specialist/Elite/Miniboss/Boss picks/no-drop/maxEquipment fields and role overrides. Preserve the accepted checked-out version, not an old chat summary. The ME quantity layer is an explicit new post-base-slot extension.

**[H1]** Official Hytale Server API,`DamageSystems`, accessed2 October 2026. Used only for the documented damage-phase constraints and the need to verify post-application observation. Pin actual signatures against the installed SDK.

`https://docs.hytale.com/api/com/hypixel/hytale/server/core/modules/entity/damage/DamageSystems`

**[H2]** Official Hytale Server API,`Nameplate`, accessed2 October 2026. Used only to establish a supported text component; not evidence that the installed client supports rich multiline colored tags.

`https://docs.hytale.com/api/com/hypixel/hytale/server/core/entity/nameplate/Nameplate`

**[H3]** Blizzard's Arreat Summit,*Monster Bonuses* and*Basic Monster Information*, accessed2 October 2026. Historical inspiration for named Unique bonuses, minion packs and increasing bonuses across difficulties. This document deliberately does not import Diablo numerical balance, damage types, undocumented bugs or active abilities.

`https://classic.battle.net/diablo2exp/monsters/bonus.shtml`

`https://classic.battle.net/diablo2exp/monsters/basics.shtml`

**Source/conversation boundary:** Earlier cross-game brainstorming was not a verified implementation catalog and contained conflicting suggestions. The27 cards and explicit exclusions here, not an unbounded list of every Diablo/PoE modifier mentioned in chat, are the complete v1 implementation scope. Every new probability, multiplier, cap, affinity mapping, label, schema and example template is a specified ME design decision unless expressly attributed above.

---

## Final invariant checklist

A player's attack, the enemy's nameplate tags and operator inspection agree on the same defense state. A creature has one authoritative Health value and one damage application path. An initial minion is a member of exactly one sealed pack. A released Packbound state never re-arms within the same generation. Rarity never invents a learned ability. Each eligible defeat yields one durable reward plan. More affixes mean more XP/managed equipment quantity, not duplicated legacy multipliers or unconstrained learning. Every admitted affix has real behavior, tests, native evidence and a truthful tag. No new active skill is required to finish this architecture.
