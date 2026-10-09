# Master Affix

> Authority correction (2026-09-30): [Master Affix scope correction](MASTER_AFFIX_SCOPE_CORRECTION.md) supersedes stale skill assumptions below. Existing base skill values/mechanics/acquisition remain authoritative. WA-112 consumes the generic successful-cleanse event; no Ameliorate is created. WA-120 reduces the existing finite 20-Mana Simulacrum cost. Original authored excerpts remain historical references.
## Hywind runtime implementation master · Version 1.0

30 September 2026 · Implementation contract for all 160 existing affixes

**Scope:** WA-001–WA-158 and GA-159/GA-160. No new affix identities. Preserve source design, frozen rolls, equipment ownership and working gameplay. Complete every required runtime path; a definition, enabled flag or tooltip is not completion.

**Evidence boundary:** This is an implementation specification, not a deployed mod or a claim of connected-game verification. The supplied R164 audit reports 19 code-functional and 141 unfinished entries, with no proven API impossibility. The required final state is 160 implemented, tested, and verified in their intended contexts. Historical “GATED” labels describe the baseline only; they are not authorized final deliverables. [A1]

---

# 1. Execution authority and completion

## 1.1 What this document changes

This document supersedes the Gear Master’s permission to finish gear delivery with permanently deferred affix implementations. It does not delete, rename, consolidate or replace the 160 affix families. Gear Master v1.2 remains the source for names, prefix/suffix sides, roll ranges, requirement policy, gear-family/armor-slot scope and authored effects. Individual cards in §13 retain that source contract and add implementation instructions. [G1 §§04,07,17]

Labels in this document distinguish three things: **SOURCE CONTRACT** is retained Gear Master behavior; **IMPLEMENTATION** is a required Hywind integration; **D01–D12** are explicit new engineering/gameplay decisions closing previously unspecified boundaries. New class/interface names below are proposed Hywind domain contracts, not claimed Hytale SDK methods. Map them to existing owners rather than adding duplicate systems.

The final deliverable from Codex is the working runtime, tests, generated coverage report, supported spawn/trace commands and owner-ready connected QA. An audit-only change, 160 registered no-op handlers, displaying all tooltips, or enabling every ID without consumers does not satisfy this task.

## 1.2 Preserve the current mod

Pin the actual working branch, commit, dirty-file inventory, installed server JAR/assets hashes, mod JAR hash and accepted balance profiles before editing. R164 is the supplied baseline audit, not a claim that an older GitHub branch is the installed build. The supplemental source inspected here is pinned at 1d3761bbbd586e03eaaf9275c74f81af8c7d9f21; use it only to locate the existing integration seams. [A1, R1–R4]

Preserve SpatialBag capacity/footprints, frozen item payloads, existing transfers/receipts, UI layout/art, Tab/Escape routing, progression, passives, difficulty ownership and ordinary native timing outside explicitly affixed actions. No save reset, silent reroll, client modification, binary patch, reflection-based safety bypass, Java agent or replacement combat engine is authorized. Supported plugin systems, registered interactions, server assets and normal native replication are the implementation boundary.

## 1.3 No exception that disguises missing work

All 160 require a real positive gameplay demonstration on a legal carrier. “Missing adapter,” “no mapped base” and “not wired yet” are work items to complete, not reasons to retire an affix. A proc failing its chance roll, an unlearned skill receiving no rank bonus, or a shield-only modifier not applying to a sword is correct contextual behavior, not a missing implementation.

Maintain separate evidence fields: implementation complete, deterministic tests passed, native integration passed, owner connected QA passed. Do not relabel the entire set FUNCTIONAL to satisfy a count. If an unexpected technical limitation survives investigation, record the exact failing experiment and the required supported alternative; the task remains incomplete until resolved. Do not silently substitute a different effect or call an unverified path successful.

# 2. Runtime architecture and ownership

## 2.1 Extend the owners that already exist

The inspected code already has GearAffixRuntime, GearRequirements, GearBindings, GearNativeItems, HytaleGearEquipment, ManagedGearDamageInteraction and ManagedGearProjectile. It projects attributes, resource capacities, cast/recovery modifiers and local physical/protection values. Reuse these instead of routing the entire feature through tooltips or the inventory page. [R1–R3]

| Existing boundary | Required extension |
| --- | --- |
| GearCatalog / GearAffixTiers / GearDropGenerator | Complete legal carriers, rank/rarity constraints and per-affix runtime capability descriptors. |
| GearAffixRuntime / HytaleGearEquipment | Build immutable typed valid-equipment snapshots with source provenance and local/global scope. |
| ManagedGearDamageInteraction / ManagedGearProjectile | Carry per-strike multi-channel power, crit, conditions and root identity through native contact and launch paths. |
| SkillExecutionService / HytaleSkillExecutionSystem | Consume item modifiers at power, rank, wind-up, resource quote, release and lifecycle boundaries. |
| Existing damage/status/heal/resource owners | Consume the same snapshot once, at the correct semantic stage. |
| IronSentinelAffixes / IronSentinelStatProjection | Complete compatible source-item inheritance; do not mistake its partial NPC adapters for player coverage. |
| RpgUiProjectionService / Advanced Stats view model | Project resolved runtime values and scoped descriptions, never independently calculate affixes. |
| Existing loot, claims and transfer coordinators | Persist one outcome and one item owner; expose real producer failures. |

## 2.2 Typed data contract

Define or extend the following immutable records. Fields may live in existing classes; the contract is normative, the proposed names are not.

**AffixRuntimeDescriptor:** stableId, operator, side, exclusionGroup, legalScopes, carrierRequirements, typedValueUnit, resolutionPhase, snapshotPolicy, playerConsumer, sentinelPolicy, displayDescriptor, positiveTestId, negativeTestId, integrationEvidenceIds.

**GearCombatSnapshot:** revision, actorId, validInstanceIds, activeHandIds, perItemLocalPower, perChannelIncreased, spell/attack/direct/DoT buckets, crit chance/multiplier, penetration vectors, conditional predicates, proc descriptors, effective ranks, resource modifiers and eligible source-policy flags.

**HitEnvelope:** world/root/strike/contact IDs, source actor and exact item instance, accepted snapshot revision, action tags, launch origin, impact target/context, channel vector, ordinary and critical components, procCoefficient, visited/hit ledger, NoProc/NoCrit/NoLeech/Reflect flags, and source/child generation.

**AppliedHitReceipt:** envelope IDs, final cancellation/block result, damage accepted by channel, actual HP loss, absorbed/overkill amounts, target survival/death identity and source credit. Generate it at the authoritative application boundary, not from a tooltip number or initial Damage amount.

**AffixEvidence:** stable ID, legal carrier, player/native/skill route, test/build hashes, exact input/output, positive/negative results, displayed value, Sentinel disposition result and owner connected trace reference.

## 2.3 Equip validity and scope

Resolve equipment validity to the least fixed point from base/allocated attributes plus permanently valid non-gear sources. Add only candidates whose requirements are already met without their own attributes. Recompute after equip, removal, respec, active-hand change, broken durability, login and relevant permanent progression change. Temporary buffs, mutually dependent items and the candidate’s own bonus cannot bootstrap validity. [G1 §04]

Aggregate global modifiers only from valid currently equipped sources. Local weapon/shield modifiers follow the actual contributing item, never an idle weapon or item in the bag. Snapshot offensive values at accepted cast/attack/launch; use target defense and conditional impact facts at impact. Already-launched projectiles and committed skills keep their source snapshot. Live defensive, capacity, cooldown-work and availability changes follow their designated owners.

The old allMatch-enabled item check must not become a permanent 21-ID ceiling. Replace it with complete typed capability validation as adapters are implemented. Never silently skip an entire otherwise-owned item in a collector without an observable diagnostic. During development, invalid/unimplemented candidates remain owned and safely inspectable; do not grant their unimplemented behavior or erase the item. A release candidate must contain no such unresolved current-catalog item paths.

## 2.4 Cache and cleanup

Cache snapshots by equipment/progression/balance revision, not by UI page state. Do not scan all assets, parse JSON or inspect 160 definitions per victim/tick. Treat ledger updates as world-thread operations; serialize bounded diagnostic copies off-thread. Every timer/entity/effect has a root owner, world, generation and expiry. Source death, unequip, world unload and reconnect use explicit cleanup/persistence rules. Preserve accepted cooldown/proc locks across weapon swaps.

# 3. Catalog, carriers, rarity and frozen values

## 3.1 Finish the carrier map

The audit’s zero focus/shield counts mean the production mapping is incomplete, not that the authored effects are impossible. Map actual installed staff, wand, spellbook and shield assets and their genuine action/block profiles. Resolve Parent inheritance and exact native model/texture/action IDs from the installed assets. Keep one managed item identity, neutralize only duplicated native power/protection on its managed carrier, and preserve approved models/art. [A1; H4]

M = melee; R = bows/crossbows/guns; B = durable bomb carrier; C = staffs/wands/spellbooks; H = shields. ALL means the original weapon/shield categories, not every armor/tool. Armor legality is only the explicit extension printed in each card. MATCH resolves the persisted skill selector against the real allowed weapon list. A star requires the genuine corresponding attack/block/dual-wield profile, not merely an item name. Do not turn feet/boots or rings into new affix-bearing categories. [G1 §17]

Implement armor extensions independently of weapon-class mapping. A focus-affix allowed on WIS Chest armor must not be excluded just because no focus base has been mapped. For every family prove at least one legal production carrier and test every authored carrier class with a representative; do not narrow family scope to the easiest item. Native bomb ammunition is not the persistent affixed carrier.

## 3.2 Prefix/suffix and rarity remain separate from effect execution

Preserve each card’s side and exclusion group. A numerical effect being implemented does not make it rollable on every item. Never solve ten-affix QA packing by duplicating an exclusive group or putting a weapon-only affix on armor. The ordinary generation budgets below are retained source mechanics, not a new Diablo II redesign. [G1 §§06–08]

| Mechanical band | Current owner-approved display | Source ordinary affix budget |
| --- | --- | --- |
| Common | Common / white | 0 |
| Former Uncommon | Magic / blue; use approved blue token | 1, either prefix or suffix |
| Rare | Rare / #FFFF00 | 2–3; 1P/1S, 2P/1S or 1P/2S |
| Very Rare | Retain accepted Very Rare presentation | 4–5; up to 3 of either side |
| Legendary | Retain accepted Legendary presentation | 6, exactly 3P/3S |

The owner’s Magic/Rare color-name change does not by itself authorize changing affix budgets. The later conversation considered Magic having 1–2 modifiers, but that was not a committed replacement budget in the supplied materials. Do not quietly decide it while implementing these affixes. Use stable mechanical rarity IDs and an explicit display map; never string-replace “Rare” throughout generation, salvage and rank rules. [C1]

Complete the existing five-band production path and original era/item-level eligibility so high-rarity affixes have a legal route. Do not disable a whole band as a substitute for completing it. Very Rare requires ilvl 35+, Legendary ilvl 60+ and Hell source in G1; source rank-affix gates are stricter where specified. Fix WA-121’s erroneous Rare allowance. Already-issued inconsistent items need a versioned, non-rerolling correction policy and a preserved payload snapshot, not silent loss.

## 3.3 Rank-affix legality table

| Selector | Bonus | Minimum ilvl | Required character level | Mechanical rarity eligibility / weight |
| --- | --- | --- | --- | --- |
| One named skill | +1 | 35 | 28 | Magic/former Uncommon, Rare, Very Rare, Legendary / 100 |
| One named skill | +2 | 60 | 48 | Rare, Very Rare, Legendary / 30 |
| One named skill | +3 | 82 | 66 | Rare, Very Rare, Legendary / 6 |
| One named skill | +4 | 94 | 80 | Rare only / 1 |
| Element or explicit skill family | +1 | 55 | 44 | Rare, Very Rare, Legendary / 100 |
| Element or explicit skill family | +2 | 80 | 64 | Rare, Very Rare, Legendary / 20 |
| Element or explicit skill family | +3 | 94 | 80 | Rare only / 2 |
| All learned active skills | +1 | 78 | 63 | Very Rare, Legendary / 100 |
| All learned active skills | +2 | 92 | 80 | Legendary only / 4 |

These source rules intentionally permit focused Rare specialization. One item may not stack named, all-skill and family-rank affixes within the shared skiller exclusion group. Different valid items contribute once. Preserve BaseSkillLevel, acquisition, structural caps and the current accepted per-skill scaling profile; do not impose a universal rank multiplier or infer tags from names. [G1 §08]

## 3.4 Roll, precision and requirement formulas

For Q tiers, j=0…4 denotes T5…T1. minimumIlvl(j)=ceil(L0+(90-L0)*j/4); strength factors are [0.40,0.55,0.70,0.85,1.00]; eligible tier weights are [100,60,30,12,4]. Multiply the card’s top V range by the factor, then apply its armor factor BEFORE quantizing. Power/pools use 0.1 units; percentages 0.1 percentage point; distances 0.01 m; attributes/ranks integers. Round the lower range up and upper range down to the grid. Freeze the chosen V, side, tier, selector, definition revision and source provenance. [G1 §07]

SOFT requirement floors T5…T1 are 10/15/25/35/45; CORE10/25/45/65/85. RANK is max(10,ceil(0.8*selected-rank minimumIlvl)); FIXED is max(10,ceil(0.75*firstIlvl)), maximum 80. BASE selects the base’s primary requirement; explicit STR/DEX/INT/WIS/LUCK codes retain that attribute. Combine base/affix requirements by maximum, not addition. For nonzero gates apply local Ease once: max(10,ceil(rawGate*(1-V/100))). Required character level is max(base, all tier equip levels), never reduced by Ease. [G1 §04]

IntrinsicBaseRoll remains 0.900…1.000 by 0.001 and affects positive intrinsic fields once, not affix V, timing, requirements or durability. Armor factors affect V, not timers, fixed probabilities, caps or requirements. F/S/R/C family weights remain 1000/300/75/10. Preserve legal-completion selection so the generator cannot loop forever, silently downgrade rarity or invent missing affixes.

# 4. Damage integration and canonical arithmetic

## 4.1 Local Physical power — source contract

rawMin = intrinsicMin + flatBoth + flatMin
rawMax = intrinsicMax + flatBoth + flatMax
normalizedMax = max(rawMin, rawMax)
resolvedMin = quantize 0.1(max(0.1, rawMin*(1+localEnhanced)))
resolvedMax = quantize 0.1(max(resolvedMin, normalizedMax*(1+localEnhanced)))

Sample one value on the 0.1 grid per authored strike and carry it to all its victims/carriers. Distinct explicitly authored sequential strikes may resample. Do not reapply intrinsic roll or native RandomPercentageModifier. A native Physical-versus-Projectile DamageCause can describe delivery differences; both remain the Physical RPG channel. [G1 §09; R2]

Golden vector:12–20 base +Honed 8 both +Measure 5 minimum +Slaughter 12 maximum =25–40; Brutal 60%=40–64. A sampled 52 plus 8 Fire at coefficient 1.10 becomes 57.2 Physical +8.8 Fire, total 66 before other modifiers/mitigation. This vector must pass through the actual melee AND projectile consumer.

## 4.2 Multi-channel composition and one roll

Extend the existing calculator/launch snapshot into a vector over Physical, Wind, Water, Fire, Earth, Lightning and Void. Prefer its native multi-cause result surface while retaining the native action coefficient and collision. One logical hit owns the vector, crit outcome and proc budget; component Damage events must not become independent full proc/loot/recovery opportunities. [R2; H1,H2]

Order: frozen intrinsic/local Physical → one Physical sample → skill-required conversion → item conversion from remaining original Physical → local elemental additions → existing skill/attribute scaling → applicable additive Increased/Reduced per channel → existing multiplicative More/Less → one crit-or-Deadly outcome → target immunity/mitigation → actual application receipt. Local Enhanced never enhances added elemental power. Conditional predicates observe pre-hit target context.

**D02, explicit conversion closure:** itemConverted=min(originalPhysical*V/100,unconvertedPhysicalRemaining). Debit that amount from Physical and credit the destination once. Converted amounts carry a consumed-conversion ledger into Fork/Return/etc.; no reapplication or chain conversion. Destination hit modifiers apply once; do not retain a second source-Physical Increased bucket on converted damage unless the skill’s accepted conversion contract explicitly requires it. Validate conservation before destination bonuses. This resolves the original-remainder ambiguity without creating extra damage.

## 4.3 Mitigation and event order

Hytale documents damage submission in Gather, modification/cancellation in Filter, and internal application. It explicitly prohibits creating new damage in Filter. Post-hit procs therefore enqueue bounded children for a later valid Gather rather than recursively submitting during mitigation. Use explicit dependencies on actual application for HP-loss receipts; the documentation’s Inspect wording is not proof of post-HP state. [H1]

For elemental channel e: R=clamp(rawResistance[e],0,0.75); direct-hit effectiveR=max(0,R-penetration[e]); result=channelPower*(1-effectiveR). Preserve native immunity, PvP, protected regions, block/absorption and independent modifiers in their actual order. Never set a global bypass flag to force an affix to appear effective. Do not subtract resistance twice through both a Hywind filter and a native asset modifier.

Map every actual native cause used by the participating skills/items to the canonical channel in one versioned map. Keep damage channel, delivery and school separate. A Void-school physical summon does not deal Void damage automatically. Preserve already accepted Water/Earth mapping and status ownership; do not rename native IDs or infer elemental classification from textures.

## 4.4 Actual Health-loss receipts

Observe an authoritative before/after HP change at the native damage application boundary and correlate it to the precise hit envelope. Do not subtract a whole tick’s unrelated healing/damage and attribute it to one affix. Reuse existing native stat modification events/metadata when they identify the applied damage. Where application batches events, maintain an ordered admission ledger and test multiple hits, a barrier, healing and a kill in one tick against the actual batch application. This port must be proven before leech or post-hit kill effects are marked complete. [H1,H3]

For a controlled isolated hit, actualHpLoss=max(0,HPbefore-HPafter); general receipts must exclude separately identified heals, reservations, HP-cap changes and unrelated damage. Overkill credit is limited to actual surviving HP consumed. Sum component losses within a root only once. A canceled/blocked/absorbed hit has the corresponding precise receipt, not a guessed positive damage value.

# 5. Native action and presentation adapters

## 5.1 Attack-rate timing, not fake DPS

Use the existing managed interaction adapter and native item-animation assets. Hytale’s asset schema exposes ItemAnimation.Speed and first/third-person animation references; this supports a data-driven timing profile, not an assumption that a universal runtime attack-speed setter exists. [H5; R2]

**D03:** generate immutable managed profiles keyed by base action, normalized attack-rate factor and asset revision. Scale the whole approved attack timeline’s scalable timestamps and matching animations together. Snapshot the profile at chain acceptance and preserve all authored hit-count/charge/combo transitions. Do not change fixed telegraph/warning or global cooldown durations. Respect client-predicted versus server-owned interactions; compile against the installed API and test both viewpoints. [H6]

If the installed public API supports an exact per-chain rate parameter, use it. Otherwise compile supported root-interaction/item-animation variants and bind the managed carrier to the matching profile before the action starts. Preserve the original logical item/base ID, payload, durability and ownership when selecting a visual/action carrier variant. Never mutate the shared vanilla root/animation while another player is using it. Generate only the bounded representable factors; cache by profile, not every item UUID. Native NPC timing needs its own adapter and matching animation/release schedule, not player-only code.

## 5.2 Melee reach and projectiles

For reach, extend the existing managed contact query along its authored swing geometry. Prefer the native selector’s supported range/sweep configuration; otherwise add a managed swept contact segment that submits through the same contact/damage owner. The existing native swing still determines timing and target polarity. Share the victim ledger between original and added contact volume, clip the extra segment to terrain, and synchronize a native/custom supported weapon trail or reach cue. Do not make hit detection a larger invisible sphere.

For projectile speed/range, modify launch trajectory and path-distance budget separately. Use native launch/replication where available; a managed projectile component is acceptable only if it preserves existing native collision/protection/impact ownership and presentation. A straight-line lifetime equals range/speed; curved paths use measured path length and a finite safety lifetime. Neither modifier expands target search, homing strength or instant-line geometry without an authored rule.

## 5.3 Block costs and genuine block triggers

Use the installed successful-block metadata and resource-cost stage. Public Damage metadata includes BLOCKED and STAMINA_DRAIN_MULTIPLIER, but their existence alone does not prove a usable pre-cost registration order; pin and test the actual DamageStamina/block owner. [H2]

Prefer composing the affix multiplier before native Stamina debit, preserving other sources. If no admission seam is exposed, neutralize only the managed guard profile’s duplicated native cost and make the existing managed block-cost leaf apply the unchanged base quote plus the affix BEFORE the original guard-break decision. Do not refund afterward: a refund cannot undo a guard break. A successful block may produce zero HP loss and must still trigger Succor. A damage event of arbitrary zero amount is not proof of a block.

## 5.4 Offhand extra strikes

Implement one real supported dual-wield action profile before admitting starred families. Use the existing paired-weapon/offhand action model and snapshot the actual offhand item. The schema’s RenderDualWielded flag is visual only, not a second independent attack guarantee. [H4]

Twin Assault requires a real offhand contact with its own eligible local power, one synchronized offhand animation and shared root budgets. If a native dual-hand profile exposes only one composite hit, extend a managed server interaction with an explicit offhand contact at the appropriate timeline checkpoint and corresponding supported animation. Do not add a generic extra hit to two-handed weapons or manufacture a second equipment store. Empty/invalid/incompatible offhand is a legitimate false condition, not a new unsupported affix category.

## 5.5 Durability before mutation

**D10:** Lasting Craft must decide before a destructive durability write. First enumerate actual managed weapon/armor loss producers: attacker contact, damaged armor, successful guard, permissible tool-like use and death loss. Public item assets define loss settings, but a cancellable universal durability event is not established by this research. Do not invent an SDK class called DurabilityLossEvent. [H4,H1]

Use a verified existing pre-debit hook where present. Otherwise compile managed carrier variants that disable the duplicated automatic loss producer and route the SAME base loss amount through a registered managed loss service at the producer boundary. That service validates item UUID/owner/revision, computes the loss once, performs the prevention roll once and commits the resulting ItemStack through the existing inventory owner. This is a narrow durability adapter, not new custody or a post-loss refund.

Account for damage-to-armor writers that use source DamageCause/default loss rather than only item fields. Do not neutralize a setting until tests prove the managed route replaces every affected producer. Repairs, salvage, quantity changes and debug edits are not positive loss opportunities. Prove boundary behavior at durability 1, breakage, death, split/transfer, concurrent events and restart; no delayed “repair” of a destroyed object.

## 5.6 Light and pickup

Native DynamicLight/ColorLight is the preferred light projection, using one actor-owned contribution composed with its held light. **D11:** calibrate the native radius unit and integer/fractional representation on the pinned build. Keep the frozen rolled V in metres and target radius Rbase+V. If native radius is quantized, choose the nearest supported representation and expose the effective radius in Advanced Stats/QA; do not claim the native renderer has exact 0.01 m resolution. Retain the canonical item tooltip’s stored V; the projection tooltip states native quantization. A positive bonus that rounds away must use the next representable radius so the affix has an actual visible effect. This is an explicit visual-precision implementation rule, not extra damage or a new affix. [H7]

Do not use stacked cloned lights to pretend exact linear interpolation is physically equivalent. Preserve hue/intensity contracts; undo only the affix-owned contribution on cleanup. A genuinely unavailable radius mutation must be addressed by a supported actor light-component composition, not a client shader patch.

Gathering uses a per-player bounded proximity query and existing pickup transactions. Do not set a shared item asset’s PickupRadius to a player-specific value: it would affect other actors. Candidate filtering must distinguish allowed material/currency from equipment and honor claims, protection, line-of-access and full-bag rejection. [H4]

# 6. Status, proc and recovery algorithms

## 6.1 One status/proc gateway

Reuse the status owners already used by skills/passives. Gear supplies typed trigger intent and a frozen magnitude/duration source, not a duplicate Burn/Poison/Bleed/controller. Trigger-eligible hits require positive accepted direct damage and valid hostile targets, unless a card explicitly triggers on block/kill/cleanse instead. NoProc, reflected damage, ticks and visual-only contacts do not generate ordinary on-hit opportunities. [G1 §§11–14]

Keep actor/root/strike/target identity across every channel, projectile, area victim and child. Sum same-status item chances within the existing group cap, then use one application attempt with the canonical status-admission rules. Proc coefficient c is a bounded share of the authored strike budget, never automatically 1 per pellet.

**D04, explicit merge closure:** let s be the existing skill/passive opportunity probability AFTER its own normal coefficient/budget and BEFORE Status Resistance; let g=clamp(sum(same-effect gear base chances),0,1)*itemProcCoefficient. Never multiply s by the item-only coefficient. Let a=1-clamp(targetSR-sourceSP,0,0.75). The combined status chance is a*(s+g-s*g). With no gear, it remains exactly a*s, preserving the skill’s baseline.

Use one canonical uniform draw u and source-success intervals: u<a*s*g means BOTH skill and gear succeed; otherwise u<a*s means skill only; otherwise u<a*(s+g-s*g) means gear only; otherwise neither. This preserves each source’s marginal chance while creating ONE status admission. Retain source-success flags: Rending’s extra regeneration suppression executes only when its own item source succeeded, even when a skill also supplied Bleed. Multiple same-effect item sources share the capped gear budget with explicit weighted source attribution.

An accepted skill multi-stack package retains its authored payload; a gear-only success uses its own generic package. On both-success, merge through the canonical stronger/package policy without duplicate stacks, timers or source records. Status immunity/control locks still apply. Non-status triggers such as Crushing and Succor omit Status Resistance. Keep the existing no-gear RNG/result path intact; do not perturb unrelated passive randomness.

## 6.2 Shot, area and channel budgets

For an authored shot with N simultaneous carriers, allocate at most 1/N coefficient to each. For an area release, determine the eligible victim set first and share the remaining coefficient across that set. Stable-sort recipients for deterministic records; do not allow target enumeration order to increase total coefficient. Explicit sequential strikes retain their distinct strike IDs; generic Volley/Fork/Return follow the accepted root/leg ledger rather than granting fresh free budgets. [G1 §11]

For an eligible continuous direct-hit channel, use the existing authored coefficient profile; where missing, D04 defines cWindow=min(1,paidDelta/1 s), shared among that interval’s carriers/victims. Unpaid/invalid ticks receive 0. This is a documented normalization decision, not a universal change to unrelated skill procs. Tests must show total gear-proc opportunity stays bounded when tick frequency, pellet count or AoE crowd size changes.

Use the existing deterministic RNG service with stable root/event/purpose keys and journal the selected outcomes when recovery requires replay. Do not seed from wall time or assign a new event ID on retry. Maintain group/pair locks by actor and effect family, not by current weapon UUID alone; equipment swaps cannot clear them.

## 6.3 Status rules that must remain intact

Bleed, Burn, Poison, Chill, Electrified, Slow, Stun, Silence, Blind, Fear and Root retain their canonical independent owners and source IDs. Use the existing current skill profiles for DPS, stack admission and immunity behavior; an item does not gain a damaging status merely by adding elemental hit power. Preserve the strongest-applicable Physical miss rule for Blind/Electrified rather than adding miss percentages.

**D05:** WA-068 explicitly modifies damaging-status duration, including Burn. Preserve the ordinary 4.0 s Burn baseline and current stack/intake/ownership rules; only an actual eligible duration modifier changes the duration of its own new package. Combustion and other DPS-only modifiers still do not change duration. This is the narrow explicit resolution of the original fixed-default text versus WA-068, not permission to rewrite all Burn duration behavior.

Slow retains its 10%-per-stack and five-stack cap, five-second build window, five-second same-source-skill reapplication lock,0.75 s target/status acceptance interval, and independently expiring five-second stacks. Use (owner, canonical source SkillId, target, status) for locks; all basic attacks share one stable basic-attack identity, not an item UUID. Rejected/at-cap attempts never refresh timers. Do not transplant its timing to Chill or replace Hard Control immunity with an invented generic diminishing-returns system. A modifier affecting duration acts after admission and never manufactures immunity when the resulting duration is zero.

## 6.4 Recovery accounting

Maintain owner-level rolling payout windows: Health on hit + Health leech share 4% normal MaxHealth/s; Mana on hit + Mana leech share 2% spendable MaxMana/s; Health on kill has its separate 4% normal MaxHealth/s budget. Pending recoveries expire after 3 s and cancel according to actor/world lifecycle. The existing native basic-hit 4%/12% refunds remain a different owner and must not be multiplied or reset. [G1 §11]

Leech amount is actual eligible HP lost×V/100. On-hit flat recovery triggers once per authored execution, not each target. Admit actual gains through existing capacity/reservation/lock logic and charge the cap for the actual paid amount. No Overheal permission is invented. Snapshot creation value and recheck current caps at payout; increasing capacity or swapping gear cannot reuse past receipts or clear a rolling window.

# 7. Defense rating closure and resistance ownership

## 7.1 D01 — explicit Defense rating representation

G1 defines shield Defense, increased Total Defense, Smite’s Defense source and Riveting’s percentage-of-rating debuff but leaves a usable rating-to-mitigation adapter unresolved. Preserve an already accepted current Defense curve if one exists and record its exact authority. When none exists, this document supplies the following baseline-preserving closure rather than leaving those affixes unimplemented. This is NEW Hywind design, not a claim about native Hytale’s armor formula.

K(level)=100+10*clamp(authoritativeCombatLevel,1,99)
Darmor=K*pArmor/(1-pArmor)
Dshield=(intrinsicShieldDefense+localFlatDefense)*(1+localShieldIncreased)
Dtotal=max(0,(Darmor+Dshield+Dother)*(1+globalDefenseIncreased))
Deffective=Dtotal*(1-winningDefenseBreakFraction)
pManaged=clamp(Deffective/(K+Deffective),0,currentManagedProtectionCap)

Use pArmor from the existing resolved managed Physical/Projectile protection (fraction), including GA-159/160, not raw individual percentages or already post-hit damage. G1’s managed armor cap remains 60%; preserve it for this managed layer unless a later accepted balance profile explicitly says otherwise. Level comes from the existing player/encounter owner, not weapon level or an arbitrary attacker.

With no shield/global/debuff contributions, this mapping returns exactly the original pArmor, preserving the existing armor baseline. GA-159/160 still modify percentage-point armor contributions; they do NOT directly grant a new rating bonus. Smite reads Dtotal once through the accepted power resolver. Riveting modifies Deffective, not pArmor by 15 percentage points.

## 7.2 Native filter integration

One owner applies each protection source once. For managed armor/carriers, replace only the duplicate managed resistance projection with pManaged; preserve foreign/native nonmanaged modifiers, block, immunity, PvP and world protection. Do not apply pManaged and the old managed armor effect to the same hit. Use native effects/modifiers where they represent the final projection faithfully, or one ordered Filter contribution. [R3; H1,H4,H8]

NPC targets require a real DefenseView too. Normalize their existing cause-specific physical protection into an equivalent rating through the same invertible mapping when no rating exists, without changing zero-affix damage. Keep each native cause’s original defensive behavior. An unarmored actor legitimately has 0 rating and gains no benefit from a rating-percent debuff; that is not a failure to implement Riveting.

## 7.3 Required numeric regression

Check p=0,.10,.30,.60 at levels 1/50/99: rating round-trip returns the same protection within final native quantization. A shield atD=100 withK=100 produces 50% before cap. Plated+50 then Reinforced 50% gives 225 rating and 69.2308% before the existing cap. Global Defense adds after local sources. Riveting on 200 rating gives 170 for 3 s, not a flat 15-point subtraction. Preserve final native quantization and log raw/effective/capped values.

# 8. Resources, support, ranks and economy

## 8.1 Single resource loop

Use existing EntityStatMap / DerivedStatEntityAdapter and the project’s resource owner. Native stat writes must follow the installed stat-modification lifecycle; use keyed owned modifiers and remove only those keys. Extra maximum capacity does not grant current resource. Preserve current absolute value on increases; clamp on decreases and reconcile reservations without duplicate deficit reset. [H3]

Percentage Mana/Stamina regen increases multiply the existing passive rate; at a 1.5%-max/s baseline, +20% yields 1.8%-max/s, not 21.5%. Skill costs use the same compiled quote and exact precision for preview, affordability and payment. Combine eligible Reduced modifiers additively and Less modifiers multiplicatively. Preserve current minimum/floor rules; full-pool drains, sacrifices, reservations and locks are not reduced by generic finite-cost effects.

## 8.2 Healing, barriers and availability

Healing Power is a source input, Healing Done is an outgoing bucket, and Healing Received is a recipient bucket. Apply each once and retain scalability, target polarity and Overheal rules. Barrier capacity/duration use the existing source-bound barrier lifecycle; equipment reprojection cannot refill an existing deficit. Real cleanse/block receipts—not speculative requested actions—own conditional support effects.

**D06:** Borrowed Arts grants temporary skill availability, not an automatically occupied slot or permanent acquisition. Store a source-item reference count in the existing availability service. The player uses an ordinary slot. Remove a now-unavailable temporary assignment safely on last-source removal while preserving learned ownership, stored progression and unrelated graph links. The granted skill’s base rank is 1; use its existing item-grant rank policy for any explicit effective-rank extension rather than forging a new learned record.

**D07:** Succor is block-triggered. The audit’s generic ITEM_TRIGGER missing-boundary prose mentioning direct attacks is not its mechanic. Route actual block success to one rank 1 self Minor Heal with 8 s lock, not to the on-hit attack stream. [A1 WA-147; G1 §14]

## 8.3 Magic Find and currency

Use the accepted GearMagicFind curve. The G1 formula, when no later owner-approved formula supersedes it, is:

E(A)=min(max(A,0),150)+.75*clamp(A-150,0,100)+.50*clamp(A-250,0,100)+.35*clamp(A-350,0,100)+.20*max(A-450,0)
MF=max(0,.005*E(validRawLuck)+sumEligibleGearMF/100)
Tail(MF,K,T)=min(MF,K)+T*x/(x+.50), where x=max(0,MF-K)
RareBonus=Tail(MF,1,.50); VeryRareBonus=Tail(MF,.75,.30); LegendaryBonus=Tail(MF,.50,.20)
Magic/formerUncommonBonus=.50*RareBonus

Apply 1+bonus to eligible non-Common rarity weights then normalize once; zero/ineligible tiers remain zero. Preserve base opportunity chance, source level, era, intrinsic roll and affix budgets. Gear Luck enters E once; do not also count it as GearMF. Use immutable recipient/sponsor MF at loot commit, not the highest nearby player or equipment at pickup. [G1 §10]

Production QA-provenance items remain excluded from MF as documented in A1. Add an isolated test-economy context or shadow result that evaluates the real production MF function against controlled nonproduction inputs, without clearing QA provenance or releasing its rewards into production. Trace both the production-excluded result and isolated expected result explicitly.

**D09:** integer currency bonuses use one durable owner/currency fractional remainder at the deduplicated reward-pot boundary: total=base*(1+bonus)+remainder; grant=floor(total); remainder=total-grant. Persist grant and remainder with the same reward receipt. Preserve an existing equivalent currency rounding policy instead of adding a second remainder. Never grant the bonus again on pickup, transfer or sale.

# 9. Shared proc children, item skills and Auras

All damage children enter native Gather with target/LOS/protection checks. In Filter, only modify or cancel the current hit; in post-application handling, enqueue children rather than immediate reentrant damage. NoProc disables further item-proc entry, NoLeech excludes child leech, NoCrit fixes noncritical behavior, and Reflect prevents reflection chains. These tags must travel through every projectile/area/skill child and be checked by existing passive hooks as well. [H1,H2]

Item Fire/Frost Bolt triggers are explicit fixed-rank 1, zero-additional-Mana exceptions from G1. They are not ordinary paid casts, do not acquire unrelated linked passives or +skill ranks and share 3 s owner/group cooldown. Succor uses its separate 8 s block group. Do not reset any lock on equip/unequip or by changing item UUID within the same actor’s group. [G1 §14]

Item Auras use the actual rank 1 Emanatism, Thorns Aura and Pedanticism controllers. Player activation remains subject to normal resource reservation/upkeep and existing active-Aura admission. Use an item-owned token and recipient contribution identity, never a duplicate manual active slot. Same-named Aura overlap uses the existing strongest-valid-recipient policy, not summed duplicate effects. On source removal/replacement/death/world transfer, release its upkeep and projection exactly once.

**D08:** complete the explicitly proposed G1 Sentinel exception: a forged Sentinel’s inherited item Aura uses fixed rank 1 with no NPC Mana sustain payment. This is an explicit exception for the bound item Aura, not free player Auras or a new player skill. Preserve the same recipient overlap, protection, range and lifecycle rules. No other source item or summoner gear is silently inherited. [G1 §14]

# 10. Iron Sentinel integration

## 10.1 Preserve the actual accepted forging contract

The affix implementation must not reinstate stale iron-only material restrictions or change the current accepted item-to-Sentinel chassis conversion. Keep the project’s accepted forge eligibility, base-stat conversion, source binding and death/replacement behavior. Resolve the exact existing conversion functions during source integration and reference them in evidence; do not invent a new material-power multiplier in this affix pass. Material tags, native base ID, intrinsic roll and full affix payload must survive in the bound snapshot; material text is not a free damage multiplier. [C1; R4]

One exact source item is reserved/bound and removed from free inventory/world ownership through existing receipts. Persist the binding before accepting active summon ownership. The same item cannot also be picked up, traded or salvaged. Logout/restart is not a Sentinel death. Replacement, failed spawn and recovery resolve to one authoritative item owner under the existing transaction rules, not duplication or item deletion.

## 10.2 D12 — explicit per-affix actor scope

Each card names SELF_STAT, ATTACK_PROC, AURA or OWNER_ONLY as its required semantic treatment. These are not implementation readiness labels. OWNER_ONLY means the player-facing effect works but is not a transferable Sentinel property; never classify the whole affix as unsupported because the NPC lacks a corresponding player-only economy/skill-management behavior.

Preserve already adapted Sentinel effects and their accepted numeric contributions. Reconcile existing classifier entries against the card’s actual operator, rather than treating an old broad switch statement or UNSUPPORTED default as final design. For an applicable inherited effect, use the same arithmetic/resolution phase as the player, with Sentinel actor/root identity. Intentional nonapplication must name the absent semantic context—e.g. a projectile-only modifier on an exclusively melee action—not hide a missing adapter behind “not applicable.”

SELF_STAT does not recursively grant the summoner’s attributes, inventory bonuses or entire equipment set. Local weapon power is resolved once before the existing source-weapon-to-chassis conversion; local armor projection is resolved once before accepted armor inheritance. Preserve base native chassis damage/health/protection and avoid adding the same local range a second time as a global bonus. ATTACK_PROC uses genuine NPC contacts/block/kill receipts as relevant, not a timer that damages nearby entities regardless of hits.

For a compatible inherited recovery/stored-fragment proc, D12 treats the Sentinel as its own direct root actor. It may heal itself from its own accepted bound-item attack or consume its own fragment records; it may not leech for the summoner, consume player-owned records or import other summons’ hits. Preserve the ordinary player path’s summon-child exclusions.

Minion modifiers worn by the player affect summon creation through the owner snapshot. They are not recursively copied from a consumed item into an army. Player skill ranks do not grant Sentinel skills, and a source item’s +Iron Sentinel rank cannot increase the same forging operation that consumes it. Item Aura inheritance is the explicit D08 special case.

## 10.3 Proof for every inheritance decision

For every source affix record, output: ID, persisted V, material/base source, expected disposition, actual consumer, pre/post Sentinel value or proc/Aura result, and explicit exclusion reason where intentionally OWNER_ONLY/inapplicable. Test one compatible source weapon, source armor, source with a proc, source with an Aura and source with only owner-only bonuses. Show that inherited buffs/procs stop with the correct lifecycle and do not react to later unrelated owner equipment swaps.

Do not claim Sentinel coverage proves player coverage, or vice versa. Both are separate columns. A forged proc test requires actual Sentinel attack/event behavior, not merely a copied JSON descriptor. A compatible effect with no adapter is unfinished work, not an acceptable forge-gate end state.

# 11. Advanced Stats, tooltip and diagnostics

## 11.1 One calculation, several projections

Extend the existing CharacterSheet/Advanced Stats view model from the same immutable runtime snapshot. Each numerical row carries units, scope, source components, raw/effective/capped values and relevant selected weapon/skill. Do not parse tooltip strings, independently sum affixes in UI code or convert a conditional effect into an unconditional scalar. The card states its required player-facing destination.

Preserve exactly one canonical modifier line per rolled affix from G1 §17.1. Substitute persisted V and localized SkillId only; do not reroll, recalculate tier strength or leak IDs/adapter language into normal item tooltips. Detailed conditions/locks and source arithmetic belong in Advanced Stats expansion or diagnostics. Rarity name/color comes from the centralized presentation map, not affix count heuristics.

On equip/unequip, invalidation, respec and login, assert gameplay projection and UI projection agree. Test zero and cap values honestly. An absent live target makes conditional DPS unavailable, not 0 bonus disguised as total damage. Magic Find’s QA exclusion and native-light quantization must be represented truthfully in context.

## 11.2 Bounded tracing

Extend the currently registered gear/UI trace service rather than creating an unrelated logger. Preferred command contract: /rpg geartrace on|off|status and /rpg geartrace mark [label]; confirm registration and document actual syntax in COMMANDS.md. Disabled by default, permission-gated, per actor, bounded duration/size, no per-frame full-state dumps.

Correlate: SOURCE_ITEM → VALID_EQUIPMENT → SNAPSHOT → POWER/COST/RANK/DEFENSE/PROC_CONSUMER → NATIVE_APPLIED_RESULT → UI_PROJECTION. Record affix ID, item UUID, root/strike/contact, source/target, V/unit, before/contribution/after, cap, chance/roll/ICD, rejection reason, owner revision and timing phase. Item generation/drop traces additionally record death/source/roll/rarity/base/carrier/delivery/claim so missing loot is not confused with missing affix runtime.

Special status outcomes must distinguish missed trigger, wrong channel, unlearned skill, immunity, cooldown, resource lock, invalid gear and missing integration. Only the last is an implementation failure; the others need negative tests. Persist enough actor/build hashes to correlate the owner’s manual connected run without logging account credentials or unrelated private state.

# 12. Delivery cohorts and acceptance protocol

## 12.1 Implement shared boundaries, then prove every ID

This remains ONE completion task with implementation cohorts, not an authorization to stop after the first audit. Use the dependency order below; keep the accepted working behavior as the regression baseline.

| Cohort | Core work and representative IDs | Completion evidence |
| --- | --- | --- |
| 0 | Carrier/rank legality, evidence instrumentation, preserve existing 19 | Natural eligible carrier and positive A/B proof of existing consumers. |
| 1 | Typed player snapshot, direct damage/crit, resources and armor | Real melee/projectile damage, actual capacities, matching stats UI. |
| 2 | Element vectors, penetration, conversion, Defense/resistances | Mixed-channel conservation and one mitigation owner on player/NPC. |
| 3 | Conditions, canonical status gateway, duration and recovery | Positive/negative hits, no duplicate proc/HP gain, replay safety. |
| 4 | Timing/reach/block, support/healing, complete rank selectors | Actual native timing/animation/geometry and paid casts, not formulas alone. |
| 5 | Summon projections, special procs, item grants/Auras, utility | Real lifecycle/events and source-scope behavior. |
| 6 | Sentinel parity, full 160 coverage, source-driven loot and owner QA | All required columns pass on pinned artifact; no disabled substitute for missing work. |

Always complete a shared adapter’s legal scope, not only the test item. Dependency order may be adjusted to avoid rewriting an existing accepted subsystem, but no ID disappears from the final reconciliation.

## 12.2 Tests required for each card

Create at least one positive consumer test, one negative/context test, and the card’s arithmetic/property test for every affix. Add equip/unequip/reconnect, source snapshot and illegal-family tests through shared parameterized fixtures. Test actual consumer input/output—not just descriptors or tooltip rendering. Enumerate exactly 160 stable IDs from source, exactly 160 runtime descriptors and complete test links; fail coverage on omissions, duplicate IDs or no-op handlers.

Run cross-affix vectors: local flat/min/max/Enhanced; converted plus added elemental power; additive global versus local buckets; crit versus Deadly; status chance plus penetration; generic DoT plus specific potency plus duration; Economy plus Sustenance/Muster; Plated plus Reinforced plus Guarding/Riveting; same Aura duplicates; ability ranks plus existing structural caps. Include legacy passive interaction tests so this integration does not silently double apply them.

Do not assert that the final audit must still have the historical 19/141 counts. Recompute statuses from evidence. A registered method or “ENABLED.contains(id)” is not proof. A unit test that passes hand-built effective damage into a fake consumer is not proof of the actual native input path.

## 12.3 Deterministic QA items and spawn commands

Build single-affix A/B fixtures first: same real base, same intrinsic roll, same level/requirements and seed; B differs only by the target affix. Use legal representative armor/weapon/focus/shield carriers. Then add combined ten-affix protected QA items where legality permits, but never change ordinary rarity budgets. The diagnostic suite can repeat one affix across contexts when necessary; “no repeated affix” is not a reason to omit armor/player/Sentinel parity.

Extend existing gear-author commands; proposed syntax is /rpg gear affixqa list, /rpg gear affixqa spawn <fixtureId>, and optional armor/weapons/all batches. These names are requirements to implement/reuse, not a claim they exist now. Deliver through SpatialInventoryTransferCoordinator, preflight full footprints, preserve full payload and stop/report no-fit without native-inventory fallback or silent world drops. Spawn-all must return a resumable list of remaining IDs rather than duplicate earlier grants on retry. [CMD1]

For chance tests use a test-only deterministic roll stream with explicit trace labeling. Production RNG and probabilities remain unchanged. Real natural-drop smoke tests must use normal generation, not the forced fixture producer. Test Magic Find through the isolated economy route described in §8.3.

## 12.4 Owner-run connected QA

Codex does not have permission or capability here to control the owner’s computer. Supply short exact manual steps and commands, an expected numeric result, and the trace path for each cohort. Distinguish offline implementation completion from connected evidence pending; do not fabricate screenshots or infer gameplay from a successful build.

Protocol P: baseline/control item → enable trace and mark ID → equip test item → inspect Advanced Stats → perform the card’s positive action → perform negative/context case → unequip and repeat → stop trace. For defenses, receive controlled typed incoming damage; for healing/resource/cost effects, create a real deficit and record actual payment/gain. For rarity/carriers, kill a valid natural enemy under the normal source profile. For Sentinel, forge the exact eligible fixture and observe the NPC result plus bound-item recovery.

## 12.5 Final acceptance, not another document-only finish

Every ID must have a legal carrier, correct persisted roll/tooltip, an effect-bearing player consumer, applicable UI projection, explicit Sentinel treatment, deterministic positive/negative tests and connected evidence for the relevant path. All authored scopes must be represented in the matrix; one successful sword is not proof of every weapon family. Existing development-disabled paths may remain during implementation only, never as the completed result of this Master Affix task.

Release report must give implemented/verified counts separately; expected final 160/160, not “160 entries loaded.” Report the exact mod/server hashes, active balance revision, production pool coverage, natural-drop outcome, failed/retried tests and migration policy. Keep working saves and rollback artifacts. Do not silently deploy, overwrite a running save’s JAR or push branches beyond the owner’s existing deployment policy.


# 13. Individual runtime contracts — all 160 affixes

All cards are mandatory. `V` is the frozen, already armor-scaled rolled value; percent values divide by 100 only at the typed runtime boundary. The algebra vectors are controlled unit/integration inputs; QA item generation still respects the original legal tiers/groups. [G1 §§07,17]

The runtime instructions below are normative. Historical baseline status is not a production permission. Every card also inherits the shared scope, lifecycle, replay and connected-test requirements in §§2–12.

## WA-001 · Honed

**Prefix · A · Physical power**

**Frozen generation:** ilvl 1+ | Q; M, R, B; top 8–12; local phys flat  | weight F. [G1 §17]

**Source contract:** Adds V to both the minimum and maximum Physical damage of this item before local Enhanced Damage. This raises the whole weapon range; it is not a second final-damage hit. Require: BASE-CORE. Armor: none.

**Integration:** GearAffixRuntime.physical → ManagedGearDamageInteraction.Calculator and ManagedGearProjectile launch snapshot; HytaleEquipmentAdapter/weapon-power resolver for weapon-scaling skills.

**Execution:** Add V to BOTH intrinsic Physical endpoints before minimum/maximum normalization and local Enhanced Damage. Run §4.1 once per source item, then sample once per authored strike; retain the sample across victims and projectile carriers.

**Display:** Active weapon’s resolved Physical range; source breakdown identifies the two-endpoint addition. Canonical item line: “+V to Minimum and Maximum Physical Damage”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** Base 12–20, V=10 gives 22–30. The same strike hits two targets with the same sampled power. Fire additions and a noncontributing offhand remain unchanged. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-001. R164 baseline: Code path found; connected proof pending.

## WA-002 · Brutal

**Prefix · A · Physical power**

**Frozen generation:** ilvl 1+ | Q; M, R, B; top 60–90%; local phys inc  | weight F. [G1 §17]

**Source contract:** V% Enhanced Damage to this item’s local Physical weapon range. Multiplies both resolved endpoints once after local flat/min/max additions; never also enters the global Increased bucket. Require: BASE-CORE. Armor: none.

**Integration:** GearAffixRuntime.physical → ManagedGearDamageInteraction.Calculator and ManagedGearProjectile launch snapshot; HytaleEquipmentAdapter/weapon-power resolver for weapon-scaling skills.

**Execution:** Multiply both normalized Physical endpoints by 1+V/100 AFTER all local flat/min/max additions. Do not insert this local multiplier into the global Increased bucket; do not amplify added elemental power.

**Display:** Local Enhanced Physical Damage and final weapon range. Canonical item line: “+V% Enhanced Physical Damage”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** The §4.1 fixture produces 40–64 after +60% Enhanced Damage. Added Fire 8 remains 8 before skill scaling. No second native random damage roll. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-002. R164 baseline: Code path found; connected proof pending.

## WA-003 · Attuned

**Prefix · A · Focus and magic power**

**Frozen generation:** ilvl 10+ | Q; C; top 6–10; local magic flat  | weight F. [G1 §17]

**Source contract:** Adds V to this item’s authored Magic Power. Only skills explicitly reading item Magic Power benefit. Require: INT-CORE. Armor: none.

**Integration:** GearBindings/GearNativeItems focus carriers → GearAffixRuntime.magic → HytaleEquipmentAdapter → BasePowerResolver/SkillExecutionService.

**Execution:** Map real staff, wand and spellbook bases under §3. Add V to that item’s intrinsic magicPower before the existing attribute and skill coefficients. Only a skill whose declared power source reads that item uses the result.

**Display:** Active item Magic Power; do not substitute a universal player spell-damage statistic. Canonical item line: “+V Magic Power”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** Item Magic Power 40 plus V=8 becomes 48. A 0.5-coefficient item-Magic skill receives 24 before other scaling; an innate fixed-power skill is unchanged. Prove a naturally eligible focus carrier exists. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-003. R164 baseline: Runtime/carrier/legality completion required.

## WA-004 · Sorcerous

**Prefix · A · Direct damage**

**Frozen generation:** ilvl 15+ | Q; C; top 24–36%; spell damage  | weight F. [G1 §17]

**Source contract:** V% increased damage of the wielder’s explicitly tagged spells. No bonus to untagged attacks or summons. Require: INT-CORE. Armor: Head, Chest; INT armor; 0.5 × V.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** Append V/100 to the spell Increased bucket only when the primary execution is explicitly spell-tagged. Sum legal Head/Chest INT-armor extensions once. It modifies matching spell damage channels, not untagged weapon attacks or autonomous summons.

**Display:** Increased Spell Damage, with eligible-source breakdown. Canonical item line: “+V% Spell Damage”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A spell with 100 base hit power and +20% existing Increased plus V=30 resolves 150, not 156. The same untagged basic hit remains 120. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-004. R164 baseline: Runtime/carrier/legality completion required.

## WA-005 · Soldier’s

**Prefix · A · Direct damage**

**Frozen generation:** ilvl 15+ | Q; M, R, B; top 24–36%; attack damage  | weight F. [G1 §17]

**Source contract:** V% increased attack damage with this item. Scoped to contributing weapon, not an offhand stat stick. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** Store V/100 in the CONTRIBUTING item’s Attack Increased bucket. Bind item identity in the accepted attack snapshot. Basic attacks and weapon-scaling attack skills may consume it; unrelated skills and idle offhands may not.

**Display:** Attack Damage for the selected/active weapon, not an unconditional all-skill bonus. Canonical item line: “+V% Attack Damage”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** A 100 attack with V=30 resolves 130 before mitigation. Switching weapons after projectile launch cannot change it; the idle weapon adds zero. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-005. R164 baseline: Runtime/carrier/legality completion required.

## WA-006 · Relentless

**Prefix · A · Direct damage**

**Frozen generation:** ilvl 20+ | Q; M, R, B, H; top 20–30%; physical damage global  | weight F. [G1 §17]

**Source contract:** V% increased direct Physical damage. Does not increase Bleed; use its dedicated modifier. Require: BASE-CORE. Armor: Hands; 0.5 × V.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** Add V/100 to the direct Physical Increased bucket. Sum eligible valid Hands extensions with other sources once. Apply after channel composition; damaging statuses do not consume this direct-hit bucket.

**Display:** Increased Direct Physical Damage. Canonical item line: “+V% Physical Damage”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** 100 Physical +20 Fire with V=25 becomes 125 Physical +20 Fire. A separately snapshotted Bleed tick does not increase. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-006. R164 baseline: Runtime/carrier/legality completion required.

## WA-007 · Prismatic

**Prefix · A · Direct damage**

**Frozen generation:** ilvl 25+ | Q; C, M, R; top 18–28%; elemental damage  | weight S. [G1 §17]

**Source contract:** V% increased direct damage across the six elemental channels. One bonus per channel; Physical excluded. Require: INT-CORE. Armor: Hands; 0.5 × V.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** Add V/100 to each of the SIX elemental direct-hit Increased buckets, once per channel. Combine additively with the matching single-element increase, not as an extra multiplier. Physical and independent DoT are excluded.

**Display:** Increased Elemental Hit Damage; retain channel-specific sources in breakdowns. Canonical item line: “+V% Elemental Damage”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** 100 Fire with +30% Fire Increased and V=20 becomes 150, not 156; 100 Physical is unchanged. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-007. R164 baseline: Runtime/carrier/legality completion required.

## WA-008 · of Alacrity

**Suffix · B · Action timing**

**Frozen generation:** ilvl 5+ | Q; M, R, B, C*; top 12–18%; local attack speed  | weight F. [G1 §17]

**Source contract:** V% increased attack rate of this item’s native attacks. C* only when a native weapon-attack profile exists; not the Alacrity buff. Require: DEX-CORE. Armor: none.

**Integration:** Managed native attack-root binding + proposed GearActionTimingProfile; registered managed interactions and item-animation assets (§5.1).

**Execution:** Speed factor = 1+V/100 for this item’s scalable native attack timeline. Divide recovery/wind-up/hit-checkpoint times by the factor and multiply first/third-person animation Speed by it. Use immutable profile-specific assets or an equivalent verified supported timing path; never edit a shared base asset at runtime.

**Display:** Active weapon Attack Rate +V%; optionally measured attacks/s. Canonical item line: “+V% Attack Speed”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** A 1.00 s repeatable attack becomes 1/1.15 = 0.869565 s at V=15; hit marker and animation agree. Skill cooldown, channel ticks, warning duration and unrelated weapon timing do not change. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-008. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D03.

## WA-009 · of Invocation

**Suffix · B · Action timing**

**Frozen generation:** ilvl 15+ | Q; C; top 12–18%; cast speed  | weight F. [G1 §17]

**Source contract:** V% increased casting rate for nonzero wind-ups. Does not change channel ticks, cooldowns or warning times. Require: WIS-CORE. Armor: Head, Hands; INT/WIS armor; 0.5 × V.

**Integration:** GearAffixRuntime.effects → SkillExecutionService.prepare and its cast presentation/timeline.

**Execution:** Sum valid casting-rate sources in the accepted rate bucket. Nonzero scalable wind-up = authoredWindup/(1+totalRate). Keep zero wind-ups zero. Preserve minimum/global lock policy and independently authored telegraph times; no channel/status acceleration.

**Display:** Faster Cast Rate, sourced from valid gear and other owners. Canonical item line: “+V% Faster Cast Rate”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A scalable 2.0 s wind-up at +15% is 1.739130 s. A zero-wind-up skill remains zero; a 1 s channel payment interval remains 1 s. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-009. R164 baseline: Code path found; connected proof pending.

## WA-010 · of Precision

**Suffix · A · Critical hits**

**Frozen generation:** ilvl 20+ | Q; ALL; top 3–5 pp; crit chance  | weight S. [G1 §17]

**Source contract:** Adds V percentage points of critical chance to eligible wielder hits. Existing total 75% critical-chance cap remains. Require: DEX-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems. Extend the shared critical-chance resolver before its ONE crit roll.

**Execution:** critChance = clamp(existingChanceWithoutGear + sum(V)/100, 0, 0.75). Respect CanCrit and inherited root roll policy. Never add this affix again when reading an already gear-resolved CharacterSheet.

**Display:** Critical Hit Chance; raw/capped contributions must match the actual roll input. Canonical item line: “+V% Critical Hit Chance”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** Baseline 0.05 plus V=5 pp gives 0.10; baseline 0.73 gives capped 0.75. A NoCrit child/DoT does not roll. Crit RNG is stable on replay. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-010. R164 baseline: Runtime/carrier/legality completion required.

## WA-011 · of Lethality

**Suffix · A · Critical hits**

**Frozen generation:** ilvl 30+ | Q; M, R, C; top 15–25 pp; crit multiplier  | weight S. [G1 §17]

**Source contract:** Adds V percentage points to critical damage multiplier. 1.50 + 0.20 = 1.70, not 1.50 × 1.20. Require: DEX-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** criticalMultiplier = existingMultiplierWithoutThisSource + sum(V)/100. Apply only on the accepted critical outcome. Deadly is mutually exclusive. The unit is percentage POINTS of multiplier, not a relative percent increase.

**Display:** Critical Damage: 170% total (or 1.70×), with +20 pp source detail. Canonical item line: “+V% Critical Damage”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** Base 1.50 plus V=20 pp is 1.70; a 100 critical hit becomes 170, not 180. Noncritical damage stays 100. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-011. R164 baseline: Runtime/carrier/legality completion required.

## WA-012 · of Readiness

**Suffix · B · Cooldown work**

**Frozen generation:** ilvl 25+ | Q; ALL; top 10–16%; cooldown recovery  | weight S. [G1 §17]

**Source contract:** V% increased skill cooldown recovery rate. Adds to the existing recovery-rate bucket and 75% cap. Require: WIS-CORE. Armor: Head, Chest; 0.5 × V.

**Integration:** GearAffixRuntime.effects → shared cooldown-work owner → RpgUiProjectionService/Advanced Stats.

**Execution:** recovery = min(0.75, existingRecoveryWithoutGear + sum(V)/100); completedWork += deltaSeconds*(1+recovery). Gear changes affect future advancement only; preserve accumulated work and unrelated locks.

**Display:** Skill Cooldown Recovery, not an incorrectly labeled duration-reduction percentage. Canonical item line: “+V% Skill Cooldown Recovery”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A 10-work cooldown at +10% finishes in 9.090909 s. Swap halfway without resetting completed work; totals above 75% cap at 75%. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-012. R164 baseline: Code path found; connected proof pending.

## WA-013 · Malignant

**Prefix · C · Damaging statuses**

**Frozen generation:** ilvl 20+ | Q; M, R, C; top 24–36%; dot damage  | weight F. [G1 §17]

**Source contract:** V% increased damage from the wielder’s damaging statuses. Only sourced Burn/Poison/Bleed; not extra hits. Require: INT-CORE. Armor: Hands; 0.5 × V.

**Integration:** GearCombatSnapshot → existing Burn/Poison/Bleed source-package creation → canonical periodic status owner.

**Execution:** Add V/100 to the Increased damaging-status magnitude bucket at source application. Add the matching Searing/Toxic/Lacerating source in the same bucket. Preserve owner, duration, stack limits and status-specific scaling; do not modify direct-hit power.

**Display:** Damage over Time; status-specific breakdown where useful. Canonical item line: “+V% Damage Over Time”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** A 10 DPS sourced status at +30% generic DoT becomes 13 DPS. With another +20% matching potency it becomes 15, not 15.6. Another player’s status is unchanged. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-013. R164 baseline: Runtime/carrier/legality completion required.

## WA-014 · of Extension

**Suffix · B · Reach and projectiles**

**Frozen generation:** ilvl 25+ | Q; M; top 0.30–0.50 m; melee reach  | weight S. [G1 §17]

**Source contract:** Adds V metres to this item’s eligible direct melee reach. Requires audited hit geometry and animation parity. Require: DEX-CORE. Armor: none.

**Integration:** Managed melee selector/sweep adapter at accepted native contact query; same profile drives hit-trail/presentation (§5.2).

**Execution:** Extend the eligible melee reach by V metres along its authored attack geometry, not every radius. Sweep the added segment against targets and terrain with unchanged width/arc. Admit each victim once in the existing strike ledger and synchronize the visible reach cue.

**Display:** Active Melee Reach in metres, including base and added component. Canonical item line: “+V m Melee Reach”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** With reach 2.0 m and V=0.4 m, an unobstructed target at 2.3 m can be hit, one at 2.5 m cannot. A wall at 2.1 m blocks the extension; no double hit in the original area. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-014. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D03.

## WA-015 · of Flight

**Suffix · B · Reach and projectiles**

**Frozen generation:** ilvl 15+ | Q; R, C; top 20–30%; projectile speed  | weight F. [G1 §17]

**Source contract:** V% increased projectile speed for eligible item attacks/spells. Range unchanged; recompute lifetime; no effect on instant Lines. Require: DEX-CORE. Armor: none.

**Integration:** ManagedGearProjectile / canonical projectile executor at launch; replicated projectile motion and bounded lifetime.

**Execution:** speed = baseSpeed*(1+V/100); distance budget stays fixed. Recompute a safety lifetime from travel budget and speed; authoritative termination tracks actual swept travel distance. Preserve collision, homing/gravity contract, hit budgets and owner snapshot.

**Display:** Projectile Speed for the affected item/action. Canonical item line: “+V% Projectile Speed”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** Straight shot: 10 m/s over 20 m with +25% travels 12.5 m/s for 1.6 s. Range remains 20 m. An instantaneous line receives no modifier. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-015. R164 baseline: Runtime/carrier/legality completion required.

## WA-016 · of the Horizon

**Suffix · B · Reach and projectiles**

**Frozen generation:** ilvl 20+ | Q; R, C; top 20–30%; projectile range  | weight F. [G1 §17]

**Source contract:** V% increased maximum projectile travel distance. No automatic targeting, area or tether-range increase. Require: DEX-CORE. Armor: none.

**Integration:** ManagedGearProjectile / canonical projectile travel-distance owner.

**Execution:** maxTravel = baseTravel*(1+V/100); preserve launch speed unless WA-015 also applies. Track accumulated path distance, recompute lifetime budget, and retain terrain collision. Do not increase targeting or tether reach by inference.

**Display:** Projectile Range; distinguish range from speed. Canonical item line: “+V% Projectile Range”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** A 20 m, 10 m/s shot at +25% travels up to 25 m in 2.5 s. A wall at 8 m still terminates it; target-selection radius is unchanged. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-016. R164 baseline: Runtime/carrier/legality completion required.

## WA-017 · Zephyr-Edged

**Prefix · A · Elemental composition**

**Frozen generation:** ilvl 10+ | Q; M, R, B, C*; top 6–10; local element flat  | weight F. [G1 §17]

**Source contract:** Adds V Wind power to this item’s attack composition. At most one flat elemental affix per item; does not automatically apply a status. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** Add the frozen V as local Wind power to the source attack channel vector after local Physical enhancement and conversion allocation. Apply the actual native/skill attack coefficient once to every contributing component. This addition never automatically applies a status.

**Display:** Added Wind Damage on the active item; the resolved channel range shares the same source vector. Canonical item line: “Adds V Wind Damage”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** With 52 Physical and V=8 Wind, coefficient 1.10 gives 57.2 Physical +8.8 Wind. +60% local Physical enhancement does not enhance the added 8. Only one flat-element group is legal per item. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-017. R164 baseline: Runtime/carrier/legality completion required.

## WA-018 · Glacial-Edged

**Prefix · A · Elemental composition**

**Frozen generation:** ilvl 10+ | Q; M, R, B, C*; top 6–10; local element flat  | weight F. [G1 §17]

**Source contract:** Adds V Water power to this item’s attack composition. At most one flat elemental affix per item; does not automatically apply a status. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** Add the frozen V as local Water power to the source attack channel vector after local Physical enhancement and conversion allocation. Apply the actual native/skill attack coefficient once to every contributing component. This addition never automatically applies a status.

**Display:** Added Water Damage on the active item; the resolved channel range shares the same source vector. Canonical item line: “Adds V Water Damage”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** With 52 Physical and V=8 Water, coefficient 1.10 gives 57.2 Physical +8.8 Water. +60% local Physical enhancement does not enhance the added 8. Only one flat-element group is legal per item. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-018. R164 baseline: Runtime/carrier/legality completion required.

## WA-019 · Ember-Edged

**Prefix · A · Elemental composition**

**Frozen generation:** ilvl 10+ | Q; M, R, B, C*; top 6–10; local element flat  | weight F. [G1 §17]

**Source contract:** Adds V Fire power to this item’s attack composition. At most one flat elemental affix per item; does not automatically apply a status. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** Add the frozen V as local Fire power to the source attack channel vector after local Physical enhancement and conversion allocation. Apply the actual native/skill attack coefficient once to every contributing component. This addition never automatically applies a status.

**Display:** Added Fire Damage on the active item; the resolved channel range shares the same source vector. Canonical item line: “Adds V Fire Damage”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** With 52 Physical and V=8 Fire, coefficient 1.10 gives 57.2 Physical +8.8 Fire. +60% local Physical enhancement does not enhance the added 8. Only one flat-element group is legal per item. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-019. R164 baseline: Runtime/carrier/legality completion required.

## WA-020 · Earthen-Edged

**Prefix · A · Elemental composition**

**Frozen generation:** ilvl 10+ | Q; M, R, B, C*; top 6–10; local element flat  | weight F. [G1 §17]

**Source contract:** Adds V Earth power to this item’s attack composition. At most one flat elemental affix per item; does not automatically apply a status. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** Add the frozen V as local Earth power to the source attack channel vector after local Physical enhancement and conversion allocation. Apply the actual native/skill attack coefficient once to every contributing component. This addition never automatically applies a status.

**Display:** Added Earth Damage on the active item; the resolved channel range shares the same source vector. Canonical item line: “Adds V Earth Damage”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** With 52 Physical and V=8 Earth, coefficient 1.10 gives 57.2 Physical +8.8 Earth. +60% local Physical enhancement does not enhance the added 8. Only one flat-element group is legal per item. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-020. R164 baseline: Runtime/carrier/legality completion required.

## WA-021 · Voltaic-Edged

**Prefix · A · Elemental composition**

**Frozen generation:** ilvl 10+ | Q; M, R, B, C*; top 6–10; local element flat  | weight F. [G1 §17]

**Source contract:** Adds V Lightning power to this item’s attack composition. At most one flat elemental affix per item; does not automatically apply a status. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** Add the frozen V as local Lightning power to the source attack channel vector after local Physical enhancement and conversion allocation. Apply the actual native/skill attack coefficient once to every contributing component. This addition never automatically applies a status.

**Display:** Added Lightning Damage on the active item; the resolved channel range shares the same source vector. Canonical item line: “Adds V Lightning Damage”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** With 52 Physical and V=8 Lightning, coefficient 1.10 gives 57.2 Physical +8.8 Lightning. +60% local Physical enhancement does not enhance the added 8. Only one flat-element group is legal per item. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-021. R164 baseline: Runtime/carrier/legality completion required.

## WA-022 · Umbral-Edged

**Prefix · A · Elemental composition**

**Frozen generation:** ilvl 10+ | Q; M, R, B, C*; top 6–10; local element flat  | weight F. [G1 §17]

**Source contract:** Adds V Void power to this item’s attack composition. At most one flat elemental affix per item; does not automatically apply a status. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** Add the frozen V as local Void power to the source attack channel vector after local Physical enhancement and conversion allocation. Apply the actual native/skill attack coefficient once to every contributing component. This addition never automatically applies a status.

**Display:** Added Void Damage on the active item; the resolved channel range shares the same source vector. Canonical item line: “Adds V Void Damage”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** With 52 Physical and V=8 Void, coefficient 1.10 gives 57.2 Physical +8.8 Void. +60% local Physical enhancement does not enhance the added 8. Only one flat-element group is legal per item. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-022. R164 baseline: Runtime/carrier/legality completion required.

## WA-023 · Zephyr

**Prefix · A · Elemental composition**

**Frozen generation:** ilvl 15+ | Q; ALL; top 24–36%; element damage  | weight F. [G1 §17]

**Source contract:** V% increased direct Wind damage from the wielder. Choose one element within this family; no extra hit or status. Require: INT-CORE. Armor: Hands; 0.5 × V.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** Append V/100 to the direct Wind Increased bucket of eligible wielder hits. Sum the same channel’s gear/passive increases and Prismatic once. Do not infer eligibility from a skill name or a status’s art.

**Display:** Increased Wind Hit Damage; actual channel contribution, not an all-damage total. Canonical item line: “+V% Wind Damage”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** 100 Wind and V=30 gives 130 before defenses. At +20% other Increased it gives 150. A different channel and an independent damaging-status tick are unchanged. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-023. R164 baseline: Runtime/carrier/legality completion required.

## WA-024 · Glacial

**Prefix · A · Elemental composition**

**Frozen generation:** ilvl 15+ | Q; ALL; top 24–36%; element damage  | weight F. [G1 §17]

**Source contract:** V% increased direct Water damage from the wielder. Choose one element within this family; no extra hit or status. Require: INT-CORE. Armor: Hands; 0.5 × V.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** Append V/100 to the direct Water Increased bucket of eligible wielder hits. Sum the same channel’s gear/passive increases and Prismatic once. Do not infer eligibility from a skill name or a status’s art.

**Display:** Increased Water Hit Damage; actual channel contribution, not an all-damage total. Canonical item line: “+V% Water Damage”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** 100 Water and V=30 gives 130 before defenses. At +20% other Increased it gives 150. A different channel and an independent damaging-status tick are unchanged. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-024. R164 baseline: Runtime/carrier/legality completion required.

## WA-025 · Ember

**Prefix · A · Elemental composition**

**Frozen generation:** ilvl 15+ | Q; ALL; top 24–36%; element damage  | weight F. [G1 §17]

**Source contract:** V% increased direct Fire damage from the wielder. Choose one element within this family; no extra hit or status. Require: INT-CORE. Armor: Hands; 0.5 × V.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** Append V/100 to the direct Fire Increased bucket of eligible wielder hits. Sum the same channel’s gear/passive increases and Prismatic once. Do not infer eligibility from a skill name or a status’s art.

**Display:** Increased Fire Hit Damage; actual channel contribution, not an all-damage total. Canonical item line: “+V% Fire Damage”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** 100 Fire and V=30 gives 130 before defenses. At +20% other Increased it gives 150. A different channel and an independent damaging-status tick are unchanged. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-025. R164 baseline: Runtime/carrier/legality completion required.

## WA-026 · Earthen

**Prefix · A · Elemental composition**

**Frozen generation:** ilvl 15+ | Q; ALL; top 24–36%; element damage  | weight F. [G1 §17]

**Source contract:** V% increased direct Earth damage from the wielder. Choose one element within this family; no extra hit or status. Require: INT-CORE. Armor: Hands; 0.5 × V.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** Append V/100 to the direct Earth Increased bucket of eligible wielder hits. Sum the same channel’s gear/passive increases and Prismatic once. Do not infer eligibility from a skill name or a status’s art.

**Display:** Increased Earth Hit Damage; actual channel contribution, not an all-damage total. Canonical item line: “+V% Earth Damage”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** 100 Earth and V=30 gives 130 before defenses. At +20% other Increased it gives 150. A different channel and an independent damaging-status tick are unchanged. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-026. R164 baseline: Runtime/carrier/legality completion required.

## WA-027 · Voltaic

**Prefix · A · Elemental composition**

**Frozen generation:** ilvl 15+ | Q; ALL; top 24–36%; element damage  | weight F. [G1 §17]

**Source contract:** V% increased direct Lightning damage from the wielder. Choose one element within this family; no extra hit or status. Require: INT-CORE. Armor: Hands; 0.5 × V.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** Append V/100 to the direct Lightning Increased bucket of eligible wielder hits. Sum the same channel’s gear/passive increases and Prismatic once. Do not infer eligibility from a skill name or a status’s art.

**Display:** Increased Lightning Hit Damage; actual channel contribution, not an all-damage total. Canonical item line: “+V% Lightning Damage”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** 100 Lightning and V=30 gives 130 before defenses. At +20% other Increased it gives 150. A different channel and an independent damaging-status tick are unchanged. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-027. R164 baseline: Runtime/carrier/legality completion required.

## WA-028 · Umbral

**Prefix · A · Elemental composition**

**Frozen generation:** ilvl 15+ | Q; ALL; top 24–36%; element damage  | weight F. [G1 §17]

**Source contract:** V% increased direct Void damage from the wielder. Choose one element within this family; no extra hit or status. Require: INT-CORE. Armor: Hands; 0.5 × V.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** Append V/100 to the direct Void Increased bucket of eligible wielder hits. Sum the same channel’s gear/passive increases and Prismatic once. Do not infer eligibility from a skill name or a status’s art.

**Display:** Increased Void Hit Damage; actual channel contribution, not an all-damage total. Canonical item line: “+V% Void Damage”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** 100 Void and V=30 gives 130 before defenses. At +20% other Increased it gives 150. A different channel and an independent damaging-status tick are unchanged. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-028. R164 baseline: Runtime/carrier/legality completion required.

## WA-029 · of Piercing Gales

**Suffix · D · Elemental defense**

**Frozen generation:** ilvl 60+ | Q; M, R, C; top 8–12 pp; element penetration  | weight R. [G1 §17]

**Source contract:** Eligible hits treat effective Wind resistance as V percentage points lower. Hit-only; floor at 0%; new mitigation adapter required. Require: BASE-CORE. Armor: none.

**Integration:** GearCombatSnapshot penetration vector → target mitigation adapter in native Filter phase (§4.3).

**Execution:** For a direct Wind hit only: effectiveR=max(0,min(0.75,targetRawR)-sum(V)/100). Multiply that channel by 1-effectiveR once. Do not edit the victim’s persistent resistance or bypass protection/immunity; damaging statuses do not inherit hit-only penetration.

**Display:** Wind Penetration in percentage points; show separately from increased and added damage. Canonical item line: “+V% Wind Penetration”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** 100 Wind against 40% resistance with V=10 pp deals 70 rather than 60. Against 5% it deals 100, not 105. The next unrelated hit still sees the original resistance. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-029. R164 baseline: Runtime/carrier/legality completion required.

## WA-030 · of Piercing Tides

**Suffix · D · Elemental defense**

**Frozen generation:** ilvl 60+ | Q; M, R, C; top 8–12 pp; element penetration  | weight R. [G1 §17]

**Source contract:** Eligible hits treat effective Water resistance as V percentage points lower. Hit-only; floor at 0%; new mitigation adapter required. Require: BASE-CORE. Armor: none.

**Integration:** GearCombatSnapshot penetration vector → target mitigation adapter in native Filter phase (§4.3).

**Execution:** For a direct Water hit only: effectiveR=max(0,min(0.75,targetRawR)-sum(V)/100). Multiply that channel by 1-effectiveR once. Do not edit the victim’s persistent resistance or bypass protection/immunity; damaging statuses do not inherit hit-only penetration.

**Display:** Water Penetration in percentage points; show separately from increased and added damage. Canonical item line: “+V% Water Penetration”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** 100 Water against 40% resistance with V=10 pp deals 70 rather than 60. Against 5% it deals 100, not 105. The next unrelated hit still sees the original resistance. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-030. R164 baseline: Runtime/carrier/legality completion required.

## WA-031 · of Piercing Embers

**Suffix · D · Elemental defense**

**Frozen generation:** ilvl 60+ | Q; M, R, C; top 8–12 pp; element penetration  | weight R. [G1 §17]

**Source contract:** Eligible hits treat effective Fire resistance as V percentage points lower. Hit-only; floor at 0%; new mitigation adapter required. Require: BASE-CORE. Armor: none.

**Integration:** GearCombatSnapshot penetration vector → target mitigation adapter in native Filter phase (§4.3).

**Execution:** For a direct Fire hit only: effectiveR=max(0,min(0.75,targetRawR)-sum(V)/100). Multiply that channel by 1-effectiveR once. Do not edit the victim’s persistent resistance or bypass protection/immunity; damaging statuses do not inherit hit-only penetration.

**Display:** Fire Penetration in percentage points; show separately from increased and added damage. Canonical item line: “+V% Fire Penetration”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** 100 Fire against 40% resistance with V=10 pp deals 70 rather than 60. Against 5% it deals 100, not 105. The next unrelated hit still sees the original resistance. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-031. R164 baseline: Runtime/carrier/legality completion required.

## WA-032 · of Piercing Stone

**Suffix · D · Elemental defense**

**Frozen generation:** ilvl 60+ | Q; M, R, C; top 8–12 pp; element penetration  | weight R. [G1 §17]

**Source contract:** Eligible hits treat effective Earth resistance as V percentage points lower. Hit-only; floor at 0%; new mitigation adapter required. Require: BASE-CORE. Armor: none.

**Integration:** GearCombatSnapshot penetration vector → target mitigation adapter in native Filter phase (§4.3).

**Execution:** For a direct Earth hit only: effectiveR=max(0,min(0.75,targetRawR)-sum(V)/100). Multiply that channel by 1-effectiveR once. Do not edit the victim’s persistent resistance or bypass protection/immunity; damaging statuses do not inherit hit-only penetration.

**Display:** Earth Penetration in percentage points; show separately from increased and added damage. Canonical item line: “+V% Earth Penetration”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** 100 Earth against 40% resistance with V=10 pp deals 70 rather than 60. Against 5% it deals 100, not 105. The next unrelated hit still sees the original resistance. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-032. R164 baseline: Runtime/carrier/legality completion required.

## WA-033 · of Piercing Storms

**Suffix · D · Elemental defense**

**Frozen generation:** ilvl 60+ | Q; M, R, C; top 8–12 pp; element penetration  | weight R. [G1 §17]

**Source contract:** Eligible hits treat effective Lightning resistance as V percentage points lower. Hit-only; floor at 0%; new mitigation adapter required. Require: BASE-CORE. Armor: none.

**Integration:** GearCombatSnapshot penetration vector → target mitigation adapter in native Filter phase (§4.3).

**Execution:** For a direct Lightning hit only: effectiveR=max(0,min(0.75,targetRawR)-sum(V)/100). Multiply that channel by 1-effectiveR once. Do not edit the victim’s persistent resistance or bypass protection/immunity; damaging statuses do not inherit hit-only penetration.

**Display:** Lightning Penetration in percentage points; show separately from increased and added damage. Canonical item line: “+V% Lightning Penetration”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** 100 Lightning against 40% resistance with V=10 pp deals 70 rather than 60. Against 5% it deals 100, not 105. The next unrelated hit still sees the original resistance. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-033. R164 baseline: Runtime/carrier/legality completion required.

## WA-034 · of Piercing the Void

**Suffix · D · Elemental defense**

**Frozen generation:** ilvl 60+ | Q; M, R, C; top 8–12 pp; element penetration  | weight R. [G1 §17]

**Source contract:** Eligible hits treat effective Void resistance as V percentage points lower. Hit-only; floor at 0%; new mitigation adapter required. Require: BASE-CORE. Armor: none.

**Integration:** GearCombatSnapshot penetration vector → target mitigation adapter in native Filter phase (§4.3).

**Execution:** For a direct Void hit only: effectiveR=max(0,min(0.75,targetRawR)-sum(V)/100). Multiply that channel by 1-effectiveR once. Do not edit the victim’s persistent resistance or bypass protection/immunity; damaging statuses do not inherit hit-only penetration.

**Display:** Void Penetration in percentage points; show separately from increased and added damage. Canonical item line: “+V% Void Penetration”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** 100 Void against 40% resistance with V=10 pp deals 70 rather than 60. Against 5% it deals 100, not 105. The next unrelated hit still sees the original resistance. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-034. R164 baseline: Runtime/carrier/legality completion required.

## WA-035 · Zephyr-Forged

**Prefix · A · Elemental composition**

**Frozen generation:** ilvl 65+ | Q; M, R, B; top 20–30%; conversion  | weight R. [G1 §17]

**Source contract:** Convert V% of this item’s original Physical attack power to Wind. Replacement, not extra damage; one conversion; see resolution order. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** Use the one-time conversion ledger (§4.2). First honor the skill’s own conversion. Transfer V% of the ORIGINAL resolved Physical source, limited to its unconverted remainder, into Wind; subtract the same amount from Physical. No chained conversion and no additive extra-damage interpretation.

**Display:** Physical-to-Wind Conversion on the active item; retain pre/post channel vector in diagnostic detail. Canonical item line: “Converts V% Physical Damage to Wind”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** 100 Physical, V=25 gives 75 Physical +25 Wind before destination modifiers. A skill that already converted 90 leaves only 10 available. Replay and fork children cannot convert it again. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-035. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D02.

## WA-036 · Glacial-Forged

**Prefix · A · Elemental composition**

**Frozen generation:** ilvl 65+ | Q; M, R, B; top 20–30%; conversion  | weight R. [G1 §17]

**Source contract:** Convert V% of this item’s original Physical attack power to Water. Replacement, not extra damage; one conversion; see resolution order. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** Use the one-time conversion ledger (§4.2). First honor the skill’s own conversion. Transfer V% of the ORIGINAL resolved Physical source, limited to its unconverted remainder, into Water; subtract the same amount from Physical. No chained conversion and no additive extra-damage interpretation.

**Display:** Physical-to-Water Conversion on the active item; retain pre/post channel vector in diagnostic detail. Canonical item line: “Converts V% Physical Damage to Water”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** 100 Physical, V=25 gives 75 Physical +25 Water before destination modifiers. A skill that already converted 90 leaves only 10 available. Replay and fork children cannot convert it again. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-036. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D02.

## WA-037 · Ember-Forged

**Prefix · A · Elemental composition**

**Frozen generation:** ilvl 65+ | Q; M, R, B; top 20–30%; conversion  | weight R. [G1 §17]

**Source contract:** Convert V% of this item’s original Physical attack power to Fire. Replacement, not extra damage; one conversion; see resolution order. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** Use the one-time conversion ledger (§4.2). First honor the skill’s own conversion. Transfer V% of the ORIGINAL resolved Physical source, limited to its unconverted remainder, into Fire; subtract the same amount from Physical. No chained conversion and no additive extra-damage interpretation.

**Display:** Physical-to-Fire Conversion on the active item; retain pre/post channel vector in diagnostic detail. Canonical item line: “Converts V% Physical Damage to Fire”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** 100 Physical, V=25 gives 75 Physical +25 Fire before destination modifiers. A skill that already converted 90 leaves only 10 available. Replay and fork children cannot convert it again. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-037. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D02.

## WA-038 · Earthen-Forged

**Prefix · A · Elemental composition**

**Frozen generation:** ilvl 65+ | Q; M, R, B; top 20–30%; conversion  | weight R. [G1 §17]

**Source contract:** Convert V% of this item’s original Physical attack power to Earth. Replacement, not extra damage; one conversion; see resolution order. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** Use the one-time conversion ledger (§4.2). First honor the skill’s own conversion. Transfer V% of the ORIGINAL resolved Physical source, limited to its unconverted remainder, into Earth; subtract the same amount from Physical. No chained conversion and no additive extra-damage interpretation.

**Display:** Physical-to-Earth Conversion on the active item; retain pre/post channel vector in diagnostic detail. Canonical item line: “Converts V% Physical Damage to Earth”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** 100 Physical, V=25 gives 75 Physical +25 Earth before destination modifiers. A skill that already converted 90 leaves only 10 available. Replay and fork children cannot convert it again. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-038. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D02.

## WA-039 · Voltaic-Forged

**Prefix · A · Elemental composition**

**Frozen generation:** ilvl 65+ | Q; M, R, B; top 20–30%; conversion  | weight R. [G1 §17]

**Source contract:** Convert V% of this item’s original Physical attack power to Lightning. Replacement, not extra damage; one conversion; see resolution order. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** Use the one-time conversion ledger (§4.2). First honor the skill’s own conversion. Transfer V% of the ORIGINAL resolved Physical source, limited to its unconverted remainder, into Lightning; subtract the same amount from Physical. No chained conversion and no additive extra-damage interpretation.

**Display:** Physical-to-Lightning Conversion on the active item; retain pre/post channel vector in diagnostic detail. Canonical item line: “Converts V% Physical Damage to Lightning”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** 100 Physical, V=25 gives 75 Physical +25 Lightning before destination modifiers. A skill that already converted 90 leaves only 10 available. Replay and fork children cannot convert it again. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-039. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D02.

## WA-040 · Umbral-Forged

**Prefix · A · Elemental composition**

**Frozen generation:** ilvl 65+ | Q; M, R, B; top 20–30%; conversion  | weight R. [G1 §17]

**Source contract:** Convert V% of this item’s original Physical attack power to Void. Replacement, not extra damage; one conversion; see resolution order. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems.

**Execution:** Use the one-time conversion ledger (§4.2). First honor the skill’s own conversion. Transfer V% of the ORIGINAL resolved Physical source, limited to its unconverted remainder, into Void; subtract the same amount from Physical. No chained conversion and no additive extra-damage interpretation.

**Display:** Physical-to-Void Conversion on the active item; retain pre/post channel vector in diagnostic detail. Canonical item line: “Converts V% Physical Damage to Void”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** 100 Physical, V=25 gives 75 Physical +25 Void before destination modifiers. A skill that already converted 90 leaves only 10 available. Replay and fork children cannot convert it again. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-040. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D02.

## WA-041 · Brawling

**Prefix · E · Impact conditions**

**Frozen generation:** ilvl 15+ | Q; M, R, B; top 20–30%; conditional damage  | weight S. [G1 §17]

**Source contract:** V% increased direct damage when target is within 4 m of the attack origin. At most one conditional-damage affix per item; check condition at impact. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems. Read a single immutable target/impact context for the complete hit.

**Execution:** Evaluate distance(impact,attackOrigin) <= 4 m. If true, add V/100 to the conditional direct-hit Increased bucket; otherwise add zero. Evaluate health/status before this hit changes them. Keep at most one conditional-damage affix per item; sum legal sources without multiplying identical buckets.

**Display:** Conditional Damage section: value plus condition, never falsely added to unconditional character damage. Canonical item line: “+V% Damage to Nearby Enemies”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** A controlled 100-power hit with a +20% injected predicate modifier is 120 only in the matching case: target at 3 m matches; target at 5 m does not. It does not amplify independent DoT. Test geometry/threshold boundaries and projectile gear-swap snapshots. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-041. R164 baseline: Runtime/carrier/legality completion required.

## WA-042 · Farseeing

**Prefix · E · Impact conditions**

**Frozen generation:** ilvl 20+ | Q; R, C; top 20–30%; conditional damage  | weight S. [G1 §17]

**Source contract:** V% increased direct damage when target is at least 12 m from the attack/cast origin. At most one conditional-damage affix per item; check condition at impact. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems. Read a single immutable target/impact context for the complete hit.

**Execution:** Evaluate distance(impact,attackOrigin) >= 12 m. If true, add V/100 to the conditional direct-hit Increased bucket; otherwise add zero. Evaluate health/status before this hit changes them. Keep at most one conditional-damage affix per item; sum legal sources without multiplying identical buckets.

**Display:** Conditional Damage section: value plus condition, never falsely added to unconditional character damage. Canonical item line: “+V% Damage to Distant Enemies”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** A controlled 100-power hit with a +20% injected predicate modifier is 120 only in the matching case: 12 m matches; 11.9 m does not. It does not amplify independent DoT. Test geometry/threshold boundaries and projectile gear-swap snapshots. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-042. R164 baseline: Runtime/carrier/legality completion required.

## WA-043 · Ambushing

**Prefix · E · Impact conditions**

**Frozen generation:** ilvl 30+ | Q; M, R; top 24–36%; conditional damage  | weight S. [G1 §17]

**Source contract:** V% increased direct damage when source is in the target’s rear 120-degree arc. At most one conditional-damage affix per item; check condition at impact. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems. Read a single immutable target/impact context for the complete hit.

**Execution:** Evaluate source direction lies inside target rear 120° cone at impact. If true, add V/100 to the conditional direct-hit Increased bucket; otherwise add zero. Evaluate health/status before this hit changes them. Keep at most one conditional-damage affix per item; sum legal sources without multiplying identical buckets.

**Display:** Conditional Damage section: value plus condition, never falsely added to unconditional character damage. Canonical item line: “+V% Damage from Behind”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** A controlled 100-power hit with a +20% injected predicate modifier is 120 only in the matching case: rear dot product <= -0.5 matches, side/front does not. It does not amplify independent DoT. Test geometry/threshold boundaries and projectile gear-swap snapshots. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-043. R164 baseline: Runtime/carrier/legality completion required.

## WA-044 · Finishing

**Prefix · E · Impact conditions**

**Frozen generation:** ilvl 25+ | Q; M, R, B, C; top 24–36%; conditional damage  | weight S. [G1 §17]

**Source contract:** V% increased direct damage when target is below 30% normal maximum Health. At most one conditional-damage affix per item; check condition at impact. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems. Read a single immutable target/impact context for the complete hit.

**Execution:** Evaluate target currentHealth / normalMaxHealth < 0.30. If true, add V/100 to the conditional direct-hit Increased bucket; otherwise add zero. Evaluate health/status before this hit changes them. Keep at most one conditional-damage affix per item; sum legal sources without multiplying identical buckets.

**Display:** Conditional Damage section: value plus condition, never falsely added to unconditional character damage. Canonical item line: “+V% Damage to Enemies Below 30% Health”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** A controlled 100-power hit with a +20% injected predicate modifier is 120 only in the matching case: 29/100 matches; 30/100 does not. It does not amplify independent DoT. Test geometry/threshold boundaries and projectile gear-swap snapshots. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-044. R164 baseline: Runtime/carrier/legality completion required.

## WA-045 · Opening

**Prefix · E · Impact conditions**

**Frozen generation:** ilvl 20+ | Q; M, R, B, C; top 24–36%; conditional damage  | weight S. [G1 §17]

**Source contract:** V% increased direct damage when target is at least 80% normal maximum Health. At most one conditional-damage affix per item; check condition at impact. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems. Read a single immutable target/impact context for the complete hit.

**Execution:** Evaluate target currentHealth / normalMaxHealth >= 0.80. If true, add V/100 to the conditional direct-hit Increased bucket; otherwise add zero. Evaluate health/status before this hit changes them. Keep at most one conditional-damage affix per item; sum legal sources without multiplying identical buckets.

**Display:** Conditional Damage section: value plus condition, never falsely added to unconditional character damage. Canonical item line: “+V% Damage to Enemies Above 80% Health”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** A controlled 100-power hit with a +20% injected predicate modifier is 120 only in the matching case: 80/100 matches; 79/100 does not. It does not amplify independent DoT. Test geometry/threshold boundaries and projectile gear-swap snapshots. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-045. R164 baseline: Runtime/carrier/legality completion required.

## WA-046 · Opportunistic

**Prefix · E · Impact conditions**

**Frozen generation:** ilvl 35+ | Q; ALL; top 20–30%; conditional damage  | weight S. [G1 §17]

**Source contract:** V% increased direct damage when target is actually Stunned, Frozen, Rooted or Feared. At most one conditional-damage affix per item; check condition at impact. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems. Read a single immutable target/impact context for the complete hit.

**Execution:** Evaluate canonical status owner reports active Stunned, Frozen, Rooted or Feared. If true, add V/100 to the conditional direct-hit Increased bucket; otherwise add zero. Evaluate health/status before this hit changes them. Keep at most one conditional-damage affix per item; sum legal sources without multiplying identical buckets.

**Display:** Conditional Damage section: value plus condition, never falsely added to unconditional character damage. Canonical item line: “+V% Damage to Stunned, Frozen, Rooted, or Feared Enemies”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** A controlled 100-power hit with a +20% injected predicate modifier is 120 only in the matching case: Rooted matches; only Slowed does not. It does not amplify independent DoT. Test geometry/threshold boundaries and projectile gear-swap snapshots. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-046. R164 baseline: Runtime/carrier/legality completion required.

## WA-047 · Scorch-Seeking

**Prefix · E · Impact conditions**

**Frozen generation:** ilvl 25+ | Q; ALL; top 18–28%; conditional damage  | weight S. [G1 §17]

**Source contract:** V% increased direct damage when target has an active Burn. At most one conditional-damage affix per item; check condition at impact. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems. Read a single immutable target/impact context for the complete hit.

**Execution:** Evaluate canonical owner reports at least one active Burn. If true, add V/100 to the conditional direct-hit Increased bucket; otherwise add zero. Evaluate health/status before this hit changes them. Keep at most one conditional-damage affix per item; sum legal sources without multiplying identical buckets.

**Display:** Conditional Damage section: value plus condition, never falsely added to unconditional character damage. Canonical item line: “+V% Damage to Burning Enemies”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** A controlled 100-power hit with a +20% injected predicate modifier is 120 only in the matching case: Burn matches; a fire-colored VFX without Burn does not. It does not amplify independent DoT. Test geometry/threshold boundaries and projectile gear-swap snapshots. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-047. R164 baseline: Runtime/carrier/legality completion required.

## WA-048 · Frost-Seeking

**Prefix · E · Impact conditions**

**Frozen generation:** ilvl 25+ | Q; ALL; top 18–28%; conditional damage  | weight S. [G1 §17]

**Source contract:** V% increased direct damage when target has an active Chill stack. At most one conditional-damage affix per item; check condition at impact. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems. Read a single immutable target/impact context for the complete hit.

**Execution:** Evaluate canonical owner reports at least one active Chill stack. If true, add V/100 to the conditional direct-hit Increased bucket; otherwise add zero. Evaluate health/status before this hit changes them. Keep at most one conditional-damage affix per item; sum legal sources without multiplying identical buckets.

**Display:** Conditional Damage section: value plus condition, never falsely added to unconditional character damage. Canonical item line: “+V% Damage to Chilled Enemies”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** A controlled 100-power hit with a +20% injected predicate modifier is 120 only in the matching case: one Chill matches; expired Chill does not. It does not amplify independent DoT. Test geometry/threshold boundaries and projectile gear-swap snapshots. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-048. R164 baseline: Runtime/carrier/legality completion required.

## WA-049 · Storm-Seeking

**Prefix · E · Impact conditions**

**Frozen generation:** ilvl 25+ | Q; ALL; top 18–28%; conditional damage  | weight S. [G1 §17]

**Source contract:** V% increased direct damage when target has at least one Electrified stack. At most one conditional-damage affix per item; check condition at impact. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems. Read a single immutable target/impact context for the complete hit.

**Execution:** Evaluate canonical owner reports at least one active Electrified stack. If true, add V/100 to the conditional direct-hit Increased bucket; otherwise add zero. Evaluate health/status before this hit changes them. Keep at most one conditional-damage affix per item; sum legal sources without multiplying identical buckets.

**Display:** Conditional Damage section: value plus condition, never falsely added to unconditional character damage. Canonical item line: “+V% Damage to Electrified Enemies”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** A controlled 100-power hit with a +20% injected predicate modifier is 120 only in the matching case: Electrified matches; electrical hit VFX alone does not. It does not amplify independent DoT. Test geometry/threshold boundaries and projectile gear-swap snapshots. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-049. R164 baseline: Runtime/carrier/legality completion required.

## WA-050 · Venom-Seeking

**Prefix · E · Impact conditions**

**Frozen generation:** ilvl 25+ | Q; ALL; top 18–28%; conditional damage  | weight S. [G1 §17]

**Source contract:** V% increased direct damage when target has active Poison. At most one conditional-damage affix per item; check condition at impact. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems. Read a single immutable target/impact context for the complete hit.

**Execution:** Evaluate canonical owner reports active Poison. If true, add V/100 to the conditional direct-hit Increased bucket; otherwise add zero. Evaluate health/status before this hit changes them. Keep at most one conditional-damage affix per item; sum legal sources without multiplying identical buckets.

**Display:** Conditional Damage section: value plus condition, never falsely added to unconditional character damage. Canonical item line: “+V% Damage to Poisoned Enemies”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** A controlled 100-power hit with a +20% injected predicate modifier is 120 only in the matching case: Poison matches; Earth damage alone does not. It does not amplify independent DoT. Test geometry/threshold boundaries and projectile gear-swap snapshots. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-050. R164 baseline: Runtime/carrier/legality completion required.

## WA-051 · Blood-Seeking

**Prefix · E · Impact conditions**

**Frozen generation:** ilvl 25+ | Q; M, R, B; top 18–28%; conditional damage  | weight S. [G1 §17]

**Source contract:** V% increased direct damage when target has active Bleed. At most one conditional-damage affix per item; check condition at impact. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems. Read a single immutable target/impact context for the complete hit.

**Execution:** Evaluate canonical owner reports active Bleed. If true, add V/100 to the conditional direct-hit Increased bucket; otherwise add zero. Evaluate health/status before this hit changes them. Keep at most one conditional-damage affix per item; sum legal sources without multiplying identical buckets.

**Display:** Conditional Damage section: value plus condition, never falsely added to unconditional character damage. Canonical item line: “+V% Damage to Bleeding Enemies”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** A controlled 100-power hit with a +20% injected predicate modifier is 120 only in the matching case: Bleed matches; Physical damage alone does not. It does not amplify independent DoT. Test geometry/threshold boundaries and projectile gear-swap snapshots. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-051. R164 baseline: Runtime/carrier/legality completion required.

## WA-052 · Giant-Hunting

**Prefix · E · Impact conditions**

**Frozen generation:** ilvl 50+ | Q; M, R, B, C; top 16–24%; conditional damage  | weight S. [G1 §17]

**Source contract:** V% increased direct damage when target’s authored encounter rank is Elite, Miniboss or Boss. At most one conditional-damage affix per item; check condition at impact. Require: BASE-CORE. Armor: none.

**Integration:** HytaleGearEquipment valid equipment → proposed GearCombatSnapshot → existing skill hit assembly and ManagedGearDamageInteraction/ManagedGearProjectile; submit through native DamageSystems. Read a single immutable target/impact context for the complete hit.

**Execution:** Evaluate frozen encounter rank is Elite, Miniboss or Boss. If true, add V/100 to the conditional direct-hit Increased bucket; otherwise add zero. Evaluate health/status before this hit changes them. Keep at most one conditional-damage affix per item; sum legal sources without multiplying identical buckets.

**Display:** Conditional Damage section: value plus condition, never falsely added to unconditional character damage. Canonical item line: “+V% Damage to Elite, Miniboss, and Boss Enemies”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** A controlled 100-power hit with a +20% injected predicate modifier is 120 only in the matching case: Elite matches; a Common actor with a cosmetic elite name does not. It does not amplify independent DoT. Test geometry/threshold boundaries and projectile gear-swap snapshots. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-052. R164 baseline: Runtime/carrier/legality completion required.

## WA-053 · of Serration

**Suffix · C · Status proc gateway**

**Frozen generation:** ilvl 15+ | Q; M, R, B; top 15–25%; status payload  | weight S. [G1 §17]

**Source contract:** V% chance on an eligible direct hit to apply Bleed. requires positive Physical hit; DPS follows shared Bleed profile; see proc rules. Require: BASE-CORE. Armor: none.

**Integration:** Completed eligible root-hit receipt → proposed GearStatusGateway → existing Bleed owner; never a second status implementation.

**Execution:** Require positive Physical. Use V/100 as base proc chance, root/shot coefficient, deterministic draw, Status Resistance/Penetration and canonical immunity in §6. On one accepted result submit the existing source Bleed DPS/duration profile. Merge same-status skill/item opportunities without duplicate application.

**Display:** Equipped Effects: chance to apply Bleed; any aggregate status stats must come from the gateway. Canonical item line: “V% Chance to Bleed on Hit”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** Force a draw below effective chance and verify exactly the existing source Bleed DPS/duration profile; repeat the same receipt and get no extra application. A draw above chance, no Physical component, reflection and NoProc children must not apply it. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-053. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D04.

## WA-054 · of Ignition

**Suffix · C · Status proc gateway**

**Frozen generation:** ilvl 15+ | Q; M, R, B, C; top 15–25%; status payload  | weight S. [G1 §17]

**Source contract:** V% chance on an eligible direct hit to apply Burn. requires positive Fire hit; no second Burn system; see proc rules. Require: BASE-CORE. Armor: none.

**Integration:** Completed eligible root-hit receipt → proposed GearStatusGateway → existing Burn owner; never a second status implementation.

**Execution:** Require positive Fire. Use V/100 as base proc chance, root/shot coefficient, deterministic draw, Status Resistance/Penetration and canonical immunity in §6. On one accepted result submit the canonical Burn potency/stack package. Merge same-status skill/item opportunities without duplicate application.

**Display:** Equipped Effects: chance to apply Burn; any aggregate status stats must come from the gateway. Canonical item line: “V% Chance to Burn on Hit”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** Force a draw below effective chance and verify exactly the canonical Burn potency/stack package; repeat the same receipt and get no extra application. A draw above chance, a Physical-only hit, reflection and NoProc children must not apply it. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-054. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D04.

## WA-055 · of Venom

**Suffix · C · Status proc gateway**

**Frozen generation:** ilvl 20+ | Q; M, R, B, C; top 15–25%; status payload  | weight S. [G1 §17]

**Source contract:** V% chance on an eligible direct hit to apply Poison. requires positive Physical or Earth hit; profile defines stack cap; see proc rules. Require: BASE-CORE. Armor: none.

**Integration:** Completed eligible root-hit receipt → proposed GearStatusGateway → existing Poison owner; never a second status implementation.

**Execution:** Require positive Physical or Earth. Use V/100 as base proc chance, root/shot coefficient, deterministic draw, Status Resistance/Penetration and canonical immunity in §6. On one accepted result submit the canonical Poison package and stack cap. Merge same-status skill/item opportunities without duplicate application.

**Display:** Equipped Effects: chance to apply Poison; any aggregate status stats must come from the gateway. Canonical item line: “V% Chance to Poison on Hit”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** Force a draw below effective chance and verify exactly the canonical Poison package and stack cap; repeat the same receipt and get no extra application. A draw above chance, only Wind/Water/Fire/Lightning/Void, reflection and NoProc children must not apply it. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-055. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D04.

## WA-056 · of Rime

**Suffix · C · Status proc gateway**

**Frozen generation:** ilvl 20+ | Q; M, R, B, C; top 15–25%; status payload  | weight S. [G1 §17]

**Source contract:** V% chance on an eligible direct hit to apply one Chill stack. requires positive Water hit; threshold policy remains authoritative; see proc rules. Require: BASE-CORE. Armor: none.

**Integration:** Completed eligible root-hit receipt → proposed GearStatusGateway → existing Chill owner; never a second status implementation.

**Execution:** Require positive Water. Use V/100 as base proc chance, root/shot coefficient, deterministic draw, Status Resistance/Penetration and canonical immunity in §6. On one accepted result submit exactly one Chill stack through threshold admission. Merge same-status skill/item opportunities without duplicate application.

**Display:** Equipped Effects: chance to apply Chill; any aggregate status stats must come from the gateway. Canonical item line: “V% Chance to Chill on Hit”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** Force a draw below effective chance and verify exactly one Chill stack through threshold admission; repeat the same receipt and get no extra application. A draw above chance, no Water component, reflection and NoProc children must not apply it. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-056. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D04.

## WA-057 · of Static

**Suffix · C · Status proc gateway**

**Frozen generation:** ilvl 25+ | Q; M, R, B, C; top 15–25%; status payload  | weight S. [G1 §17]

**Source contract:** V% chance on an eligible direct hit to apply one Electrified stack. requires positive Lightning hit; use canonical accuracy debuff; see proc rules. Require: BASE-CORE. Armor: none.

**Integration:** Completed eligible root-hit receipt → proposed GearStatusGateway → existing Electrified owner; never a second status implementation.

**Execution:** Require positive Lightning. Use V/100 as base proc chance, root/shot coefficient, deterministic draw, Status Resistance/Penetration and canonical immunity in §6. On one accepted result submit exactly one Electrified stack through canonical admission. Merge same-status skill/item opportunities without duplicate application.

**Display:** Equipped Effects: chance to apply Electrified; any aggregate status stats must come from the gateway. Canonical item line: “V% Chance to Electrify on Hit”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** Force a draw below effective chance and verify exactly one Electrified stack through canonical admission; repeat the same receipt and get no extra application. A draw above chance, no Lightning component, reflection and NoProc children must not apply it. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-057. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D04.

## WA-058 · of Hobbling

**Suffix · C · Status proc gateway**

**Frozen generation:** ilvl 30+ | Q; M, R, B, C; top 15–25%; status payload  | weight S. [G1 §17]

**Source contract:** V% chance on an eligible direct hit to apply one Slow stack. requires a valid direct hit; uses the 10% / five-stack / five-second Slow rules; see proc rules. Require: BASE-CORE. Armor: none.

**Integration:** Completed eligible root-hit receipt → proposed GearStatusGateway → existing Slow owner; never a second status implementation.

**Execution:** Require any valid positive direct hit. Use V/100 as base proc chance, root/shot coefficient, deterministic draw, Status Resistance/Penetration and canonical immunity in §6. On one accepted result submit one 10% Slow stack under five-stack / five-second rules. Merge same-status skill/item opportunities without duplicate application.

**Display:** Equipped Effects: chance to apply Slow; any aggregate status stats must come from the gateway. Canonical item line: “V% Chance to Slow on Hit”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** Force a draw below effective chance and verify exactly one 10% Slow stack under five-stack / five-second rules; repeat the same receipt and get no extra application. A draw above chance, a periodic damage tick, reflection and NoProc children must not apply it. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-058. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D04.

## WA-059 · of Concussion

**Suffix · C · Status proc gateway**

**Frozen generation:** ilvl 45+ | Q; M, R, H; top 8–12%; status payload  | weight R. [G1 §17]

**Source contract:** V% chance on an eligible direct hit to apply Stun for 0.6 s. requires a positive Physical hit; all control immunities apply; see proc rules. Require: BASE-CORE. Armor: none.

**Integration:** Completed eligible root-hit receipt → proposed GearStatusGateway → existing Stun owner; never a second status implementation.

**Execution:** Require positive Physical. Use V/100 as base proc chance, root/shot coefficient, deterministic draw, Status Resistance/Penetration and canonical immunity in §6. On one accepted result submit 0.6 s Stun subject to control immunity. Merge same-status skill/item opportunities without duplicate application.

**Display:** Equipped Effects: chance to apply Stun; any aggregate status stats must come from the gateway. Canonical item line: “V% Chance to Stun for 0.6 sec on Hit”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** Force a draw below effective chance and verify exactly 0.6 s Stun subject to control immunity; repeat the same receipt and get no extra application. A draw above chance, an immune target, reflection and NoProc children must not apply it. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-059. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D04.

## WA-060 · of Hushing

**Suffix · C · Status proc gateway**

**Frozen generation:** ilvl 55+ | Q; M, R, C; top 8–12%; status payload  | weight R. [G1 §17]

**Source contract:** V% chance on an eligible direct hit to apply Silence for 1.5 s. requires a valid hit; basic attacks remain available; see proc rules. Require: BASE-CORE. Armor: none.

**Integration:** Completed eligible root-hit receipt → proposed GearStatusGateway → existing Silence owner; never a second status implementation.

**Execution:** Require any valid positive direct hit. Use V/100 as base proc chance, root/shot coefficient, deterministic draw, Status Resistance/Penetration and canonical immunity in §6. On one accepted result submit 1.5 s Silence; basic attacks remain available. Merge same-status skill/item opportunities without duplicate application.

**Display:** Equipped Effects: chance to apply Silence; any aggregate status stats must come from the gateway. Canonical item line: “V% Chance to Silence for 1.5 sec on Hit”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** Force a draw below effective chance and verify exactly 1.5 s Silence; basic attacks remain available; repeat the same receipt and get no extra application. A draw above chance, an actor lacking a silenceable action, reflection and NoProc children must not apply it. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-060. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D04.

## WA-061 · of Dimming

**Suffix · C · Status proc gateway**

**Frozen generation:** ilvl 40+ | Q; M, R, C; top 8–12%; status payload  | weight R. [G1 §17]

**Source contract:** V% chance on an eligible direct hit to apply Blind for 2 s. 50% Physical miss rule; player vignette only; see proc rules. Require: BASE-CORE. Armor: none.

**Integration:** Completed eligible root-hit receipt → proposed GearStatusGateway → existing Blind owner; never a second status implementation.

**Execution:** Require any valid positive direct hit. Use V/100 as base proc chance, root/shot coefficient, deterministic draw, Status Resistance/Penetration and canonical immunity in §6. On one accepted result submit 2 s Blind with canonical 50% Physical miss behavior and player vignette. Merge same-status skill/item opportunities without duplicate application.

**Display:** Equipped Effects: chance to apply Blind; any aggregate status stats must come from the gateway. Canonical item line: “V% Chance to Blind for 2 sec on Hit”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** Force a draw below effective chance and verify exactly 2 s Blind with canonical 50% Physical miss behavior and player vignette; repeat the same receipt and get no extra application. A draw above chance, an already protected/immune target, reflection and NoProc children must not apply it. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-061. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D04.

## WA-062 · of Dread

**Suffix · C · Status proc gateway**

**Frozen generation:** ilvl 45+ | Q; M, R, C; top 8–12%; status payload  | weight R. [G1 §17]

**Source contract:** V% chance on an eligible direct hit to apply Fear for 1.5 s. retains target eligibility and damage-break rules; see proc rules. Require: BASE-CORE. Armor: none.

**Integration:** Completed eligible root-hit receipt → proposed GearStatusGateway → existing Fear owner; never a second status implementation.

**Execution:** Require any valid positive direct hit. Use V/100 as base proc chance, root/shot coefficient, deterministic draw, Status Resistance/Penetration and canonical immunity in §6. On one accepted result submit 1.5 s Fear with canonical eligibility and damage-break rules. Merge same-status skill/item opportunities without duplicate application.

**Display:** Equipped Effects: chance to apply Fear; any aggregate status stats must come from the gateway. Canonical item line: “V% Chance to Fear for 1.5 sec on Hit”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** Force a draw below effective chance and verify exactly 1.5 s Fear with canonical eligibility and damage-break rules; repeat the same receipt and get no extra application. A draw above chance, a Fear-immune target, reflection and NoProc children must not apply it. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-062. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D04.

## WA-063 · of Binding

**Suffix · C · Status proc gateway**

**Frozen generation:** ilvl 45+ | Q; M, R, C; top 8–12%; status payload  | weight R. [G1 §17]

**Source contract:** V% chance on an eligible direct hit to apply Root for 1 s. does not disable attacks or ignore boss protection; see proc rules. Require: BASE-CORE. Armor: none.

**Integration:** Completed eligible root-hit receipt → proposed GearStatusGateway → existing Root owner; never a second status implementation.

**Execution:** Require any valid positive direct hit. Use V/100 as base proc chance, root/shot coefficient, deterministic draw, Status Resistance/Penetration and canonical immunity in §6. On one accepted result submit 1.0 s Root; do not disable attacks. Merge same-status skill/item opportunities without duplicate application.

**Display:** Equipped Effects: chance to apply Root; any aggregate status stats must come from the gateway. Canonical item line: “V% Chance to Root for 1 sec on Hit”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** Force a draw below effective chance and verify exactly 1.0 s Root; do not disable attacks; repeat the same receipt and get no extra application. A draw above chance, a boss protected from Root, reflection and NoProc children must not apply it. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-063. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D04.

## WA-064 · of Intrusion

**Suffix · C · Status proc gateway**

**Frozen generation:** ilvl 35+ | Q; ALL; top 8–12 pp; status penetration  | weight S. [G1 §17]

**Source contract:** Adds V percentage points of Status Penetration. Counters Status Resistance only; never raises chance above base. Require: BASE-CORE. Armor: none.

**Integration:** GearCombatSnapshot → existing status-admission resolver used by both skills and GearStatusGateway.

**Execution:** effectiveStatusResistance=clamp(targetSR-sum(V)/100,0,0.75). Final chance=baseChance*procCoefficient*(1-effectiveSR). Apply the subtraction once; it never increases chance above base or affects elemental mitigation.

**Display:** Status Penetration, distinct from elemental penetration. Canonical item line: “+V% Status Penetration”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** Base chance 25%, target SR 40%, V=10 pp yields 17.5% at coefficient 1 rather than 15%. SR 5% yields 25%, not 26.25%. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-064. R164 baseline: Runtime/carrier/legality completion required.

## WA-065 · Searing

**Prefix · C · Damaging statuses**

**Frozen generation:** ilvl 30+ | Q; M, R, B, C; top 24–36%; status potency  | weight S. [G1 §17]

**Source contract:** V% increased DPS of Burn sourced from eligible wielder actions. No duration, stack-count, refresh or proc-chance bonus. Require: INT-CORE. Armor: none.

**Integration:** GearCombatSnapshot → canonical Burn source-package magnitude snapshot.

**Execution:** Add V/100 to the Increased Burn DPS bucket at application. Sum with Malignant and relevant status-specific skill/passive increases once. Preserve that owner’s fixed timers, stack counts, intake/refresh rules and proc chances; later weapon swaps do not rewrite old packages.

**Display:** Burn Damage / potency with source breakdown. Canonical item line: “+V% Burn Damage”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** A 10 DPS Burn with +20% potency becomes 12 DPS; +30% generic DoT gives 15 DPS, not 15.6. Duration and number of stacks are unchanged; another caster’s package is unchanged. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-065. R164 baseline: Runtime/carrier/legality completion required.

## WA-066 · Toxic

**Prefix · C · Damaging statuses**

**Frozen generation:** ilvl 30+ | Q; M, R, B, C; top 24–36%; status potency  | weight S. [G1 §17]

**Source contract:** V% increased DPS of Poison sourced from eligible wielder actions. No duration, stack-count, refresh or proc-chance bonus. Require: INT-CORE. Armor: none.

**Integration:** GearCombatSnapshot → canonical Poison source-package magnitude snapshot.

**Execution:** Add V/100 to the Increased Poison DPS bucket at application. Sum with Malignant and relevant status-specific skill/passive increases once. Preserve that owner’s fixed timers, stack counts, intake/refresh rules and proc chances; later weapon swaps do not rewrite old packages.

**Display:** Poison Damage / potency with source breakdown. Canonical item line: “+V% Poison Damage”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** A 10 DPS Poison with +20% potency becomes 12 DPS; +30% generic DoT gives 15 DPS, not 15.6. Duration and number of stacks are unchanged; another caster’s package is unchanged. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-066. R164 baseline: Runtime/carrier/legality completion required.

## WA-067 · Lacerating

**Prefix · C · Damaging statuses**

**Frozen generation:** ilvl 30+ | Q; M, R, B, C; top 24–36%; status potency  | weight S. [G1 §17]

**Source contract:** V% increased DPS of Bleed sourced from eligible wielder actions. No duration, stack-count, refresh or proc-chance bonus. Require: INT-CORE. Armor: none.

**Integration:** GearCombatSnapshot → canonical Bleed source-package magnitude snapshot.

**Execution:** Add V/100 to the Increased Bleed DPS bucket at application. Sum with Malignant and relevant status-specific skill/passive increases once. Preserve that owner’s fixed timers, stack counts, intake/refresh rules and proc chances; later weapon swaps do not rewrite old packages.

**Display:** Bleed Damage / potency with source breakdown. Canonical item line: “+V% Bleed Damage”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** A 10 DPS Bleed with +20% potency becomes 12 DPS; +30% generic DoT gives 15 DPS, not 15.6. Duration and number of stacks are unchanged; another caster’s package is unchanged. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-067. R164 baseline: Runtime/carrier/legality completion required.

## WA-068 · of Persistence

**Suffix · C · Damaging statuses**

**Frozen generation:** ilvl 30+ | Q; M, R, B, C; top 20–30%; damaging status duration  | weight S. [G1 §17]

**Source contract:** V% increased duration of sourced Burn, Poison and Bleed. Explicit duration modifier only; never extends Stun, Silence, Slow or immunity. Require: INT-CORE. Armor: none.

**Integration:** Source status-package builder → canonical Burn/Poison/Bleed expiry scheduling.

**Execution:** duration=baseDuration*(1+sum(V)/100) for sourced damaging statuses only. Preserve tick DPS, apply a correctly prorated final tick, and keep stack ownership/intake limits. D05 explicitly authorizes this affix’s duration extension where older general Burn text fixed the default at 4 s.

**Display:** Damaging Status Duration; distinguish this from DPS. Canonical item line: “+V% Burn, Poison, and Bleed Duration”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** A 4 s, 10 DPS package at +25% lasts 5 s and deals 50 before defenses, not 62.5. Stun/Silence/Slow/immunity windows and Combustion’s normal duration remain unchanged. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-068. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D05.

## WA-069 · Plated

**Prefix · D · Defense rating**

**Frozen generation:** ilvl 1+ | Q; H; top 40–60; shield defense flat  | weight F. [G1 §17]

**Source contract:** Adds V local Defense to this shield. Requires the audited Defense scalar; not an invented armor formula. Require: STR-CORE. Armor: none.

**Integration:** GearBindings shield carrier → GearAffixRuntime defense projection → proposed DefenseView → native mitigation and Smite power resolver (§7).

**Execution:** Add V to this shield’s intrinsic Defense before local Reinforced. Feed that final rating into the single DefenseView once; never treat rating points as protection percentage points. Build the missing shield carrier and usable block profile, not a tooltip-only shield.

**Display:** Shield Defense and Total Defense, not +50% protection. Canonical item line: “+V Defense”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** Shield Defense 100 plus V=50 gives 150 before local increase. Smite reads total Defense once. Removing shield removes exactly its contribution without changing armor ownership. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-069. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D01.

## WA-070 · Reinforced

**Prefix · D · Defense rating**

**Frozen generation:** ilvl 10+ | Q; H; top 40–60%; shield defense inc  | weight F. [G1 §17]

**Source contract:** V% increased local shield Defense. Smite reads final Total Defense once, not this bonus twice. Require: STR-CORE. Armor: none.

**Integration:** GearAffixRuntime shield projection → shared DefenseView (§7).

**Execution:** shieldDefense=(intrinsicDefense+Plated)*(1+V/100). Apply local multiplier only to this shield; global Defense increase comes later. No second Smite addition.

**Display:** Local Shield Defense +V%; resolved shield/total rating. Canonical item line: “+V% Enhanced Defense”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** Defense 100 +Plated 50 at V=50 yields 225 shield Defense. An unrelated armor piece is not multiplied by 1.5. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-070. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D01.

## WA-071 · of Guarding

**Suffix · D · Defense rating**

**Frozen generation:** ilvl 20+ | Q; M, H; top 12–18%; global defense  | weight S. [G1 §17]

**Source contract:** V% increased total authoritative Defense. Native/RPG mitigation ownership must be audited first. Require: STR-CORE. Armor: none.

**Integration:** Valid equipment → DefenseView global Increased rating bucket → native Filter mitigation.

**Execution:** Multiply the combined authoritative Defense rating by 1+sum(V)/100 once after local armor/shield evaluation. Use the accepted Defense curve, or the explicit D01 closure when no rating owner exists. Never subtract percentage points directly from a hit.

**Display:** Increased Defense and Total Defense; effective protection as a separate value. Canonical item line: “+V% Defense”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** A rating 100 with V=25 becomes 125; at K=100, rating mitigation changes 50%→55.5556%. Native protection and this projection cannot both apply the same source. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-071. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D01.

## WA-072 · of Gales Warding

**Suffix · D · Elemental defense**

**Frozen generation:** ilvl 10+ | Q; ALL; top 16–24 pp; resist wind  | weight F. [G1 §17]

**Source contract:** Adds V percentage points of Wind resistance. Shared effective 75% cap; no new damage channel. Require: BASE-SOFT. Armor: All armor; 1 × V.

**Integration:** HytaleGearEquipment → GearDefenseSnapshot → canonical incoming Wind mitigation / native Damage Filter.

**Execution:** rawResistance=nonGearResistance+sum(valid matching item V)/100. Cap effective resistance at 75% before per-hit penetration. Use explicit DamageCause→element mapping and preserve immunity/protection; Physical/Projectile do not become elemental damage.

**Display:** Wind Resistance: effective %, raw %, cap and overcap; same snapshot used by actual mitigation. Canonical item line: “+V% Wind Resistance”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** With no other defense, 100 Wind damage and V=20 pp yields 80 Health damage; raw 90% caps 75% and yields 25. A different element and invalid equipment show no added protection. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-072. R164 baseline: Runtime/carrier/legality completion required.

## WA-073 · of Tides Warding

**Suffix · D · Elemental defense**

**Frozen generation:** ilvl 10+ | Q; ALL; top 16–24 pp; resist water  | weight F. [G1 §17]

**Source contract:** Adds V percentage points of Water resistance. Shared effective 75% cap; no new damage channel. Require: BASE-SOFT. Armor: All armor; 1 × V.

**Integration:** HytaleGearEquipment → GearDefenseSnapshot → canonical incoming Water mitigation / native Damage Filter.

**Execution:** rawResistance=nonGearResistance+sum(valid matching item V)/100. Cap effective resistance at 75% before per-hit penetration. Use explicit DamageCause→element mapping and preserve immunity/protection; Physical/Projectile do not become elemental damage.

**Display:** Water Resistance: effective %, raw %, cap and overcap; same snapshot used by actual mitigation. Canonical item line: “+V% Water Resistance”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** With no other defense, 100 Water damage and V=20 pp yields 80 Health damage; raw 90% caps 75% and yields 25. A different element and invalid equipment show no added protection. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-073. R164 baseline: Runtime/carrier/legality completion required.

## WA-074 · of Embers Warding

**Suffix · D · Elemental defense**

**Frozen generation:** ilvl 10+ | Q; ALL; top 16–24 pp; resist fire  | weight F. [G1 §17]

**Source contract:** Adds V percentage points of Fire resistance. Shared effective 75% cap; no new damage channel. Require: BASE-SOFT. Armor: All armor; 1 × V.

**Integration:** HytaleGearEquipment → GearDefenseSnapshot → canonical incoming Fire mitigation / native Damage Filter.

**Execution:** rawResistance=nonGearResistance+sum(valid matching item V)/100. Cap effective resistance at 75% before per-hit penetration. Use explicit DamageCause→element mapping and preserve immunity/protection; Physical/Projectile do not become elemental damage.

**Display:** Fire Resistance: effective %, raw %, cap and overcap; same snapshot used by actual mitigation. Canonical item line: “+V% Fire Resistance”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** With no other defense, 100 Fire damage and V=20 pp yields 80 Health damage; raw 90% caps 75% and yields 25. A different element and invalid equipment show no added protection. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-074. R164 baseline: Runtime/carrier/legality completion required.

## WA-075 · of Stone Warding

**Suffix · D · Elemental defense**

**Frozen generation:** ilvl 10+ | Q; ALL; top 16–24 pp; resist earth  | weight F. [G1 §17]

**Source contract:** Adds V percentage points of Earth resistance. Shared effective 75% cap; no new damage channel. Require: BASE-SOFT. Armor: All armor; 1 × V.

**Integration:** HytaleGearEquipment → GearDefenseSnapshot → canonical incoming Earth mitigation / native Damage Filter.

**Execution:** rawResistance=nonGearResistance+sum(valid matching item V)/100. Cap effective resistance at 75% before per-hit penetration. Use explicit DamageCause→element mapping and preserve immunity/protection; Physical/Projectile do not become elemental damage.

**Display:** Earth Resistance: effective %, raw %, cap and overcap; same snapshot used by actual mitigation. Canonical item line: “+V% Earth Resistance”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** With no other defense, 100 Earth damage and V=20 pp yields 80 Health damage; raw 90% caps 75% and yields 25. A different element and invalid equipment show no added protection. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-075. R164 baseline: Runtime/carrier/legality completion required.

## WA-076 · of Storms Warding

**Suffix · D · Elemental defense**

**Frozen generation:** ilvl 10+ | Q; ALL; top 16–24 pp; resist lightning  | weight F. [G1 §17]

**Source contract:** Adds V percentage points of Lightning resistance. Shared effective 75% cap; no new damage channel. Require: BASE-SOFT. Armor: All armor; 1 × V.

**Integration:** HytaleGearEquipment → GearDefenseSnapshot → canonical incoming Lightning mitigation / native Damage Filter.

**Execution:** rawResistance=nonGearResistance+sum(valid matching item V)/100. Cap effective resistance at 75% before per-hit penetration. Use explicit DamageCause→element mapping and preserve immunity/protection; Physical/Projectile do not become elemental damage.

**Display:** Lightning Resistance: effective %, raw %, cap and overcap; same snapshot used by actual mitigation. Canonical item line: “+V% Lightning Resistance”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** With no other defense, 100 Lightning damage and V=20 pp yields 80 Health damage; raw 90% caps 75% and yields 25. A different element and invalid equipment show no added protection. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-076. R164 baseline: Runtime/carrier/legality completion required.

## WA-077 · of the Void Warding

**Suffix · D · Elemental defense**

**Frozen generation:** ilvl 10+ | Q; ALL; top 16–24 pp; resist void  | weight F. [G1 §17]

**Source contract:** Adds V percentage points of Void resistance. Shared effective 75% cap; no new damage channel. Require: BASE-SOFT. Armor: All armor; 1 × V.

**Integration:** HytaleGearEquipment → GearDefenseSnapshot → canonical incoming Void mitigation / native Damage Filter.

**Execution:** rawResistance=nonGearResistance+sum(valid matching item V)/100. Cap effective resistance at 75% before per-hit penetration. Use explicit DamageCause→element mapping and preserve immunity/protection; Physical/Projectile do not become elemental damage.

**Display:** Void Resistance: effective %, raw %, cap and overcap; same snapshot used by actual mitigation. Canonical item line: “+V% Void Resistance”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** With no other defense, 100 Void damage and V=20 pp yields 80 Health damage; raw 90% caps 75% and yields 25. A different element and invalid equipment show no added protection. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-077. R164 baseline: Runtime/carrier/legality completion required.

## WA-078 · of Prismatic Warding

**Suffix · D · Elemental defense**

**Frozen generation:** ilvl 55+ | Q; C, H; top 6–10 pp; all resistance  | weight R. [G1 §17]

**Source contract:** Adds V percentage points to each of the six elemental resistances. Does not include Physical or Status Resistance. Require: BASE-SOFT. Armor: Head, Chest; 0.6 × V.

**Integration:** HytaleGearEquipment → six-channel GearDefenseSnapshot → incoming native Filter; Head/Chest extension adapter.

**Execution:** Add V/100 independently to each elemental resistance. Include the explicit Head/Chest 0.6 scaling before freezing V; ensure eligible armor carriers exist. Cap each channel independently; never add this as a second all-damage reduction.

**Display:** All Elemental Resistances breakdown plus each of the six effective rows. Canonical item line: “+V% to All Elemental Resistances”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** V=8 pp changes 20% Fire to 28% and 0% Water to 8%. Physical and Status Resistance stay unchanged; a channel already at 75% gains only overcap. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-078. R164 baseline: Runtime/carrier/legality completion required.

## WA-079 · of Resolve

**Suffix · D · Status defenses**

**Frozen generation:** ilvl 25+ | Q; ALL; top 8–12 pp; status resistance  | weight S. [G1 §17]

**Source contract:** Adds V percentage points of Status Resistance. Avoidance, not shorter duration or blanket immunity. Require: WIS-CORE. Armor: All armor; 0.5 × V.

**Integration:** Valid equipment → shared status-admission resolver for incoming effects.

**Execution:** rawSR=existingSR+sum(V)/100; effectiveSR=clamp(rawSR-sourceStatusPenetration,0,0.75). This reduces admission chance, not duration. Do not apply it again inside individual status classes.

**Display:** Status Resistance and applicable cap. Canonical item line: “+V% Status Resistance”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** A 50% incoming proc against 20%SR has 40% effective chance. Force accepted and rejected draws. An admitted 1 s Root still lasts 1 s without a duration modifier. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-079. R164 baseline: Runtime/carrier/legality completion required.

## WA-080 · of Recovery

**Suffix · D · Status defenses**

**Frozen generation:** ilvl 45+ | Q; ALL; top 12–18%; control duration taken  | weight R. [G1 §17]

**Source contract:** V% reduced duration of successfully applied Hard Control on wielder. Uses Control Resistance adapter; immunity windows are not shortened. Require: WIS-CORE. Armor: All armor; 0.5 × V.

**Integration:** Canonical incoming Hard Control admission after immunity/avoidance → control duration owner.

**Execution:** After actual admission, duration=baseDuration*max(0,1-sum(reducedDuration)). Combine with compatible reduced-duration sources additively and separately apply authored Less factors. Preserve ordinary control minimums, if any, and immunity windows; zero-duration result emits no fake immunity.

**Display:** Reduced Hard Control Duration, not Status Resistance. Canonical item line: “V% Reduced Hard Control Duration”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** A 2 s admitted Stun at V=15 lasts 1.7 s. Admission chance and the following immunity timer are unchanged. Test stacking with Steadiness while channeling. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-080. R164 baseline: Runtime/carrier/legality completion required.

## WA-081 · of Surefooting

**Suffix · D · Status defenses**

**Frozen generation:** ilvl 35+ | Q; ALL; top 15–25%; slow effect taken  | weight S. [G1 §17]

**Source contract:** V% reduced magnitude of movement slows on wielder. Acts after strongest-slow selection; not stun/freeze immunity. Require: BASE-CORE. Armor: Hands, Legs; 0.5 × V.

**Integration:** Canonical strongest-slow resolver → movement modifier publication.

**Execution:** Select the winning native/RPG slow first, then multiply its magnitude by max(0,1-sum(V)/100). Do not increase unslowed movement speed or shorten timers. Attach one owned movement modifier; remove it on slow expiry.

**Display:** Reduced Slow Effect. Canonical item line: “V% Reduced Slow Effect”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** Winning 40% slow at V=20 becomes 32%; a losing 10% slow does not create a second reduction. Unslowed speed remains its normal value. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-081. R164 baseline: Runtime/carrier/legality completion required.

## WA-082 · of Anchoring

**Suffix · D · Status defenses**

**Frozen generation:** ilvl 25+ | Q; M, H; top 20–30%; knockback taken  | weight S. [G1 §17]

**Source contract:** V% reduced hostile displacement distance. Preserve collision and minimum placement safety. Require: STR-CORE. Armor: Chest, Legs; 0.5 × V.

**Integration:** Hostile knockback/displacement request before native motion submission and swept collision.

**Execution:** Scale the hostile displacement impulse/distance component by max(0,1-sum(V)/100), then run the existing safe collision/placement solver. Do not alter self-dashes, teleport, scripted relocation or create collision bypass.

**Display:** Reduced Hostile Knockback. Canonical item line: “V% Reduced Knockback”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** A legal 4 m hostile push at V=25 targets 3 m before collision; a wall at 2 m still limits travel. Self-dash distance remains 4 m. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-082. R164 baseline: Runtime/carrier/legality completion required.

## WA-083 · of Efficient Guarding

**Suffix · D · Block and mitigation**

**Frozen generation:** ilvl 20+ | Q; H, M*; top 12–18%; block cost  | weight S. [G1 §17]

**Source contract:** V% reduced native Stamina cost of eligible blocked hits. M* only audited block-capable weapon profiles; never random autoblock. Require: STR-CORE. Armor: none.

**Integration:** Accepted native block decision → Damage.BLOCKED / STAMINA_DRAIN_MULTIPLIER or managed block-cost leaf before debit (§5.3).

**Execution:** For a real successful block only, multiply the original Stamina cost by max(0,1-sum(V)/100), including the existing multiplier once. Do this before resource debit; do not refund after a guard break. Map true block-capable carriers for H/M*.

**Display:** Reduced Block Stamina Cost for the active guard profile. Canonical item line: “V% Reduced Block Stamina Cost”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** A 10 Stamina successful block at V=15 costs 8.5. A failed block gets no reduction. Guard break uses the reduced affordability result; resource replay spends once. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-083. R164 baseline: Runtime/carrier/legality completion required.

## WA-084 · of Composure

**Suffix · D · Block and mitigation**

**Frozen generation:** ilvl 55+ | Q; H; top 20–30%; crit damage taken  | weight R. [G1 §17]

**Source contract:** V% less bonus damage above an ordinary hit when critically struck. Only critical excess, not the entire incoming hit. Require: WIS-CORE. Armor: none.

**Integration:** Incoming hit envelope carrying ordinary/critical components → single native damage-filter contribution.

**Execution:** Reduce only the critical EXCESS: damage=ordinaryComponent+(criticalComponent-ordinaryComponent)*(1-V/100). Read the preserved precrit baseline; do not guess crit status from large damage or reduce the whole hit.

**Display:** Reduced Extra Damage from Critical Hits. Canonical item line: “V% Reduced Critical Bonus Damage Taken”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** An ordinary 100 hit that crits to 150 at V=20 becomes 140 before defenses. A normal 100 hit stays 100; no negative excess on noncritical packets. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-084. R164 baseline: Runtime/carrier/legality completion required.

## WA-085 · of Might

**Suffix · F · Attributes and pools**

**Frozen generation:** ilvl 1+ | Q; ALL; top 16–24; attribute  | weight F. [G1 §17]

**Source contract:** Adds V raw STR; shared diminishing returns still apply. One single-attribute affix per item; cannot satisfy its own requirements. Require: BASE-SOFT. Armor: All armor; 0.5 × V.

**Integration:** GearAffixRuntime.attributes → GearRequirements fixed-point validity → DerivedStatService / existing CharacterSheet.

**Execution:** Add frozen integer V to raw STR from already-valid equipment; then apply the existing Effective Attribute curve once. Resolve equip legality WITHOUT this candidate’s own bonus. No temporary buff or circular equipment dependency may bootstrap it.

**Display:** Strength: raw/effective breakdown and all legitimate downstream values. Canonical item line: “+V Strength”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** RawSTR=100 with V=20 becomes 120 and uses E(120). An item requiring 110 cannot equip on 100 solely by its own +20. Removing it restores the original derived totals. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-085. R164 baseline: Code path found; connected proof pending.

## WA-086 · of Dexterity

**Suffix · F · Attributes and pools**

**Frozen generation:** ilvl 1+ | Q; ALL; top 16–24; attribute  | weight F. [G1 §17]

**Source contract:** Adds V raw DEX; shared diminishing returns still apply. One single-attribute affix per item; cannot satisfy its own requirements. Require: BASE-SOFT. Armor: All armor; 0.5 × V.

**Integration:** GearAffixRuntime.attributes → GearRequirements fixed-point validity → DerivedStatService / existing CharacterSheet.

**Execution:** Add frozen integer V to raw DEX from already-valid equipment; then apply the existing Effective Attribute curve once. Resolve equip legality WITHOUT this candidate’s own bonus. No temporary buff or circular equipment dependency may bootstrap it.

**Display:** Dexterity: raw/effective breakdown and all legitimate downstream values. Canonical item line: “+V Dexterity”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** RawDEX=100 with V=20 becomes 120 and uses E(120). An item requiring 110 cannot equip on 100 solely by its own +20. Removing it restores the original derived totals. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-086. R164 baseline: Code path found; connected proof pending.

## WA-087 · of Insight

**Suffix · F · Attributes and pools**

**Frozen generation:** ilvl 1+ | Q; ALL; top 16–24; attribute  | weight F. [G1 §17]

**Source contract:** Adds V raw INT; shared diminishing returns still apply. One single-attribute affix per item; cannot satisfy its own requirements. Require: BASE-SOFT. Armor: All armor; 0.5 × V.

**Integration:** GearAffixRuntime.attributes → GearRequirements fixed-point validity → DerivedStatService / existing CharacterSheet.

**Execution:** Add frozen integer V to raw INT from already-valid equipment; then apply the existing Effective Attribute curve once. Resolve equip legality WITHOUT this candidate’s own bonus. No temporary buff or circular equipment dependency may bootstrap it.

**Display:** Intelligence: raw/effective breakdown and all legitimate downstream values. Canonical item line: “+V Intelligence”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** RawINT=100 with V=20 becomes 120 and uses E(120). An item requiring 110 cannot equip on 100 solely by its own +20. Removing it restores the original derived totals. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-087. R164 baseline: Code path found; connected proof pending.

## WA-088 · of Wisdom

**Suffix · F · Attributes and pools**

**Frozen generation:** ilvl 1+ | Q; ALL; top 16–24; attribute  | weight F. [G1 §17]

**Source contract:** Adds V raw WIS; shared diminishing returns still apply. One single-attribute affix per item; cannot satisfy its own requirements. Require: BASE-SOFT. Armor: All armor; 0.5 × V.

**Integration:** GearAffixRuntime.attributes → GearRequirements fixed-point validity → DerivedStatService / existing CharacterSheet.

**Execution:** Add frozen integer V to raw WIS from already-valid equipment; then apply the existing Effective Attribute curve once. Resolve equip legality WITHOUT this candidate’s own bonus. No temporary buff or circular equipment dependency may bootstrap it.

**Display:** Wisdom: raw/effective breakdown and all legitimate downstream values. Canonical item line: “+V Wisdom”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** RawWIS=100 with V=20 becomes 120 and uses E(120). An item requiring 110 cannot equip on 100 solely by its own +20. Removing it restores the original derived totals. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-088. R164 baseline: Code path found; connected proof pending.

## WA-089 · of Fortune

**Suffix · F · Attributes and pools**

**Frozen generation:** ilvl 1+ | Q; ALL; top 16–24; attribute  | weight F. [G1 §17]

**Source contract:** Adds V raw LUCK; shared diminishing returns still apply. One single-attribute affix per item; cannot satisfy its own requirements. Require: BASE-SOFT. Armor: All armor; 0.5 × V.

**Integration:** GearAffixRuntime.attributes → GearRequirements fixed-point validity → DerivedStatService / existing CharacterSheet.

**Execution:** Add frozen integer V to raw LUCK from already-valid equipment; then apply the existing Effective Attribute curve once. Resolve equip legality WITHOUT this candidate’s own bonus. No temporary buff or circular equipment dependency may bootstrap it.

**Display:** Luck: raw/effective breakdown and all legitimate downstream values. Canonical item line: “+V Luck”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** RawLUCK=100 with V=20 becomes 120 and uses E(120). An item requiring 110 cannot equip on 100 solely by its own +20. Removing it restores the original derived totals. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-089. R164 baseline: Code path found; connected proof pending.

## WA-090 · of Balance

**Suffix · F · Attributes and pools**

**Frozen generation:** ilvl 45+ | Q; ALL; top 5–8; attribute  | weight R. [G1 §17]

**Source contract:** Adds V to each of STR, DEX, INT, WIS and LUCK. No Vitality/Focus/class-stat substitution. Require: BASE-SOFT. Armor: Head, Chest; 0.5 × V.

**Integration:** GearAffixRuntime.attributes → fixed-point equipment validity → DerivedStatService.

**Execution:** Add V to each of the FIVE existing raw attributes once. Apply the one-attribute/skiller exclusion policies as authored; do not invent a sixth attribute. Head/Chest extension scales V before integer quantization and persistence.

**Display:** All five attribute rows and legitimate derived deltas; do not also display it as five independently rolled affixes. Canonical item line: “+V to All Attributes”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** Raw[10,10,10,10,10] with V=5 becomes[15,15,15,15,15] only if independently valid. Self-supporting/circular gear remains invalid. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-090. R164 baseline: Code path found; connected proof pending.

## WA-091 · Vital

**Prefix · F · Attributes and pools**

**Frozen generation:** ilvl 1+ | Q; ALL; top 80–120; max health  | weight F. [G1 §17]

**Source contract:** Adds V to maximum Health via the existing pool adapter. Capacity increase never fills current resource. Require: STR-SOFT. Armor: All armor; 0.50 Chest/Legs; 0.35 Head/Hands.

**Integration:** GearAffixRuntime.effects → HytaleGearEquipment.project → DerivedStatEntityAdapter / EntityStatMap modifier owner.

**Execution:** Add frozen V to maximum Health after the source’s authorized armor scaling. On capacity increases preserve current absolute resource; on decreases clamp current only to the new available cap. Recompute reservations/locks with their existing owner, not by refill.

**Display:** Maximum Health, current/maximum display and source breakdown. Canonical item line: “+V Maximum Health”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** Max 100/current 40 plus V=40 becomes max 140/current 40. Unequip at current 120 clamps to 100; repeated equip/unequip cannot produce resources. Slot scaling is applied once. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-091. R164 baseline: Code path found; connected proof pending.

## WA-092 · Reservoir

**Prefix · F · Attributes and pools**

**Frozen generation:** ilvl 1+ | Q; ALL; top 40–60; max mana  | weight F. [G1 §17]

**Source contract:** Adds V to maximum Mana via the existing pool adapter. Capacity increase never fills current resource. Require: INT-SOFT. Armor: All armor; 0.50 Chest/Legs; 0.35 Head/Hands.

**Integration:** GearAffixRuntime.effects → HytaleGearEquipment.project → DerivedStatEntityAdapter / EntityStatMap modifier owner.

**Execution:** Add frozen V to maximum Mana after the source’s authorized armor scaling. On capacity increases preserve current absolute resource; on decreases clamp current only to the new available cap. Recompute reservations/locks with their existing owner, not by refill.

**Display:** Maximum Mana, current/maximum display and source breakdown. Canonical item line: “+V Maximum Mana”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** Max 100/current 40 plus V=40 becomes max 140/current 40. Unequip at current 120 clamps to 100; repeated equip/unequip cannot produce resources. Slot scaling is applied once. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-092. R164 baseline: Code path found; connected proof pending.

## WA-093 · Enduring

**Prefix · F · Attributes and pools**

**Frozen generation:** ilvl 1+ | Q; ALL; top 30–45; max stamina  | weight F. [G1 §17]

**Source contract:** Adds V to maximum Stamina via the existing pool adapter. Capacity increase never fills current resource. Require: DEX-SOFT. Armor: All armor; 0.50 Chest/Legs; 0.35 Head/Hands.

**Integration:** GearAffixRuntime.effects → HytaleGearEquipment.project → DerivedStatEntityAdapter / EntityStatMap modifier owner.

**Execution:** Add frozen V to maximum Stamina after the source’s authorized armor scaling. On capacity increases preserve current absolute resource; on decreases clamp current only to the new available cap. Recompute reservations/locks with their existing owner, not by refill.

**Display:** Maximum Stamina, current/maximum display and source breakdown. Canonical item line: “+V Maximum Stamina”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** Max 100/current 40 plus V=40 becomes max 140/current 40. Unequip at current 120 clamps to 100; repeated equip/unequip cannot produce resources. Slot scaling is applied once. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-093. R164 baseline: Code path found; connected proof pending.

## WA-094 · of Reaping

**Suffix · G · Recovery**

**Frozen generation:** ilvl 25+ | Q; M, R, B; top 2–4; life on hit  | weight S. [G1 §17]

**Source contract:** Restore V Health on a valid root attack hit. Once per authored execution, not per victim or pellet; recovery caps apply. Require: BASE-CORE. Armor: none.

**Integration:** Completed positive root attack receipt → shared Health recovery queue/cap owner.

**Execution:** Queue fixed V Health once per authored execution, not per pellet/victim/channel. Share the rolling 4% normal MaxHealth/s cap with life leech; expire unpaid queue remainder after 3 s. Apply through canonical heal admission without adding recursive proc events.

**Display:** Health on Hit and shared recovery limit explanation. Canonical item line: “+V Health on Hit”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** MaxHealth 1000, V=3, five victims of one attack queues 3 total, not 15. Replayed hit queues 0. Combined hit/leech payouts cannot exceed 40 in a rolling second. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-094. R164 baseline: Runtime/carrier/legality completion required.

## WA-095 · of Clarity

**Suffix · G · Recovery**

**Frozen generation:** ilvl 25+ | Q; M, R, B; top 1–2; mana on hit  | weight S. [G1 §17]

**Source contract:** Restore V Mana on a valid root attack hit. Adds a capped recovery event; does not multiply existing 4%/12% refunds. Require: BASE-CORE. Armor: none.

**Integration:** Completed positive root attack receipt → existing Mana recovery queue, reservation and resource-lock owner.

**Execution:** Queue fixed V Mana once per root execution. Share 2% spendable MaxMana/s with Mana leech. Keep native 4%/12% basic-hit refunds separate and unchanged; no multiplying them by this affix.

**Display:** Mana on Hit; source queue and admitted gain in trace. Canonical item line: “+V Mana on Hit”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** Spendable MaxMana 500, V=3 gives 3 queued once across a multi-target strike; combined Mana recovery pays at most 10/s. Locked or full Mana gives no phantom gain. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-095. R164 baseline: Runtime/carrier/legality completion required.

## WA-096 · Vampiric

**Prefix · G · Recovery**

**Frozen generation:** ilvl 40+ | Q; M, R, B; top 2–4%; life leech  | weight R. [G1 §17]

**Source contract:** Queue V% of eligible actual attack Health damage as Health recovery. Actual HP lost only; overkill, reflect, summons and shields excluded. Require: BASE-CORE. Armor: none.

**Integration:** Post-application actual Health-loss receipt from eligible attack → shared Health recovery queue.

**Execution:** Queue actual eligible victim HP lost * V/100, not requested damage, barrier absorption or overkill. Exclude reflected/NoLeech/summon-owned child damage. Share the 4% normal MaxHealth/s rolling cap and 3 s queue expiry; preserve source actor and cancellation.

**Display:** Health Leech percentage; actual recovery is contextual, not guaranteed DPS. Canonical item line: “V% Life Stolen per Hit”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** A 120 requested hit against a 50 HP unshielded target produces only 50 eligible HP loss. With an injected 10% coefficient, queue 5, not 12. Barrier-only damage queues 0; replay queues 0. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-096. R164 baseline: Runtime/carrier/legality completion required.

## WA-097 · Siphoning

**Prefix · G · Recovery**

**Frozen generation:** ilvl 40+ | Q; M, R, B; top 1–2%; mana leech  | weight R. [G1 §17]

**Source contract:** Queue V% of eligible actual attack Health damage as Mana recovery. Leech and recovery caps prevent cost-free proc loops. Require: BASE-CORE. Armor: none.

**Integration:** Post-application actual Health-loss receipt from eligible attack → shared Mana recovery queue.

**Execution:** Queue actual eligible victim HP lost * V/100, not requested damage, barrier absorption or overkill. Exclude reflected/NoLeech/summon-owned child damage. Share the 2% spendable MaxMana/s rolling cap and 3 s queue expiry; preserve source actor and cancellation.

**Display:** Mana Leech percentage; actual recovery is contextual, not guaranteed DPS. Canonical item line: “V% Mana Stolen per Hit”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A 120 requested hit against a 50 HP unshielded target produces only 50 eligible HP loss. With an injected 10% coefficient, queue 5, not 12. Barrier-only damage queues 0; replay queues 0. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-097. R164 baseline: Runtime/carrier/legality completion required.

## WA-098 · of Triumph

**Suffix · G · Recovery**

**Frozen generation:** ilvl 10+ | Q; ALL; top 1–2%; life on kill  | weight F. [G1 §17]

**Source contract:** Restore V% normal Max Health on a credited enemy kill. One enemy reward identity; no summons/dummies/self-farming. Require: BASE-SOFT. Armor: All armor; 0.5 × V.

**Integration:** Existing credited enemy death/kill receipt → item Health-on-kill recovery owner.

**Execution:** Queue normalMaxHealth*V/100 once per eligible death reward identity. Use the separate 4% MaxHealth/s on-kill cap. Exclude owned summons, test dummies, self-farming and replay; do not use pickup events as kill credit.

**Display:** Health on Kill; conditional equipped effect. Canonical item line: “Restores V% Maximum Health on Kill”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** MaxHealth 1000 and V=3% queues 30 once for a real credited enemy kill. Ten replays still queue 30; multiple kills share 40/s payout cap. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-098. R164 baseline: Runtime/carrier/legality completion required.

## WA-099 · of Flow

**Suffix · G · Regeneration**

**Frozen generation:** ilvl 10+ | Q; ALL; top 20–30%; mana regen  | weight F. [G1 §17]

**Source contract:** V% increased passive Mana regeneration rate. Not a percentage-point addition to the baseline 1.5%/s. Require: WIS-SOFT. Armor: Head, Chest; 0.5 × V.

**Integration:** Existing single passive-resource loop → equipped rate snapshot → normal resource admission.

**Execution:** passiveRate = existingBasePassiveRate*(1+sum(increased Mana regeneration)). Preserve regeneration delays, locks, reservation and canonical simulation clock. Do not increase the separate basic-hit 4%/12% refunds.

**Display:** Mana Regeneration per second and increased-rate breakdown. Canonical item line: “+V% Mana Regeneration”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** For MaxMana 1000 and base 1.5%/s=15/s, V=20 yields 18/s, NOT 215/s. A 4% basic-hit refund remains 40. Unequip returns to 15/s. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-099. R164 baseline: Runtime/carrier/legality completion required.

## WA-100 · of Endurance

**Suffix · G · Regeneration**

**Frozen generation:** ilvl 10+ | Q; ALL; top 20–30%; stamina regen  | weight F. [G1 §17]

**Source contract:** V% increased passive Stamina regeneration rate. Does not increase basic-hit refunds. Require: DEX-SOFT. Armor: Legs; 0.5 × V.

**Integration:** Existing single passive-resource loop → equipped rate snapshot → normal resource admission.

**Execution:** passiveRate = existingBasePassiveRate*(1+sum(increased Stamina regeneration)). Preserve regeneration delays, locks, reservation and canonical simulation clock. Do not increase the separate basic-hit 4%/12% refunds.

**Display:** Stamina Regeneration per second and increased-rate breakdown. Canonical item line: “+V% Stamina Regeneration”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** For MaxStamina 1000 and base 1.5%/s=15/s, V=20 yields 18/s, NOT 215/s. A 4% basic-hit refund remains 40. Unequip returns to 15/s. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-100. R164 baseline: Runtime/carrier/legality completion required.

## WA-101 · of Economy

**Suffix · G · Resource costs**

**Frozen generation:** ilvl 30+ | Q; C; top 8–12%; mana cost  | weight S. [G1 §17]

**Source contract:** V% reduced finite Mana costs and continuous upkeep. Excludes reservation, full-pool drains and resource locks. Require: WIS-CORE. Armor: Chest; INT/WIS armor; 0.5 × V.

**Integration:** Compiled skill cost quote → finite cast commit and paid continuous-upkeep tick owner.

**Execution:** Add V/100 to the applicable Reduced Mana Cost bucket. actualCost=baseEligibleCost*max(0,1-totalReduced)*product(Less). Preserve authoritative cost precision/floor. Exclude reservations, full-pool drains and lock mechanics. Compose once with channel/summon cost reductions.

**Display:** Reduced Mana Cost; channel and summon quotes share the same cost evaluator. Canonical item line: “V% Reduced Mana Cost”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** Eligible 20 Mana and V=10 costs 18. A 20%-max reservation remains 20%; a full-pool sacrifice still consumes its full declared pool. Failed validation pays 0. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-101. R164 baseline: Runtime/carrier/legality completion required.

## WA-102 · of Conservation

**Suffix · G · Resource costs**

**Frozen generation:** ilvl 30+ | Q; M, R, B, H; top 8–12%; stamina cost  | weight S. [G1 §17]

**Source contract:** V% reduced finite RPG Stamina skill costs. Not native sprint/block costs; no conflict with the passive named Conservation. Require: BASE-CORE. Armor: Hands, Legs; 0.5 × V.

**Integration:** Compiled finite Stamina skill cost quote → SkillExecutionService payment commit.

**Execution:** Apply V/100 once to the Reduced Stamina skill-cost bucket. Keep native sprint/dodge/block costs outside this operator unless their own approved mechanics say otherwise. Preserve precision, locks and affordability checks.

**Display:** Reduced Stamina Skill Cost. Canonical item line: “V% Reduced Stamina Skill Cost”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A 20 Stamina RPG skill at V=10 costs 18. An unrelated native 10-Stamina block remains 10. Rejected cast spends 0; replay does not spend twice. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-102. R164 baseline: Runtime/carrier/legality completion required.

## WA-103 · Restorative

**Prefix · H · Healing and support**

**Frozen generation:** ilvl 10+ | Q; C; top 6–10; healing power  | weight F. [G1 §17]

**Source contract:** Adds V to the caster’s authored Healing Power before WIS scaling. Explicit item contribution; does not replace the innate base. Require: WIS-CORE. Armor: Head, Chest; WIS armor; 0.5 × V.

**Integration:** Active focus / legal WIS armor → healing BasePowerResolver before existing Wisdom scaling.

**Execution:** Add frozen V to authored item Healing Power consumed by the healing skill. Preserve the skill’s innate healing component; do not replace it with item Magic Power. Map focus and explicit armor carriers first.

**Display:** Item Healing Power / outgoing healing source breakdown. Canonical item line: “+V Healing Power”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** Authored healing base 40 +V8 gives 48 before Wisdom/coefficient. A 0.5 coefficient yields 24 at neutral scaling. A damage-only skill is unchanged. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-103. R164 baseline: Runtime/carrier/legality completion required.

## WA-104 · Benevolent

**Prefix · H · Healing and support**

**Frozen generation:** ilvl 15+ | Q; C; top 20–30%; healing done  | weight S. [G1 §17]

**Source contract:** V% increased eligible outgoing healing. No extra Overheal permission or percentage-heal model. Require: WIS-CORE. Armor: Head, Chest; WIS armor; 0.5 × V.

**Integration:** Canonical outgoing healing assembly after base power and Wisdom, before recipient admission.

**Execution:** Add V/100 to the Increased outgoing-healing bucket for scalable eligible heals. Keep percentage/fixed heals’ explicit scalability. No automatic Overheal permission, no conversion of damage/leech into a new heal kind.

**Display:** Increased Healing Done. Canonical item line: “+V% Healing”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** Scalable 100 healing at V=30 gives 130 before recipient factors. With existing 20% Increased it gives 150. Normal max-HP clipping remains. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-104. R164 baseline: Runtime/carrier/legality completion required.

## WA-105 · Sheltering

**Prefix · H · Healing and support**

**Frozen generation:** ilvl 20+ | Q; C, H; top 20–30%; barrier strength  | weight S. [G1 §17]

**Source contract:** V% increased explicitly scalable barrier capacity. No free refill, duplicated deficit reset or protection-cap increase. Require: WIS-CORE. Armor: Head, Chest; WIS armor; 0.5 × V.

**Integration:** Barrier capacity builder → existing source-bound barrier controller.

**Execution:** Scale eligible barrier capacity by 1+sum(V)/100 in its Increased bucket. If updating an existing instance upward, increase capacity without refilling current absorption. On reduction clamp current only as required; preserve duration, reservation and protection caps.

**Display:** Increased Barrier Capacity; current absorption is not an affix stat. Canonical item line: “+V% Barrier Strength”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** Capacity 100/remaining 40 at +20% becomes 120/40 when reprojected. A new cast may create its normal 120 barrier only after normal payment. Swapping equipment cannot refill 40. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-105. R164 baseline: Runtime/carrier/legality completion required.

## WA-106 · of Connection

**Suffix · H · Healing and support**

**Frozen generation:** ilvl 35+ | Q; C; top 20–30%; tether reach  | weight S. [G1 §17]

**Source contract:** V% increased caster-to-primary range of explicit Tether skills. No extra targets or automatic branch-range increase. Require: WIS-CORE. Armor: none.

**Integration:** Explicit Tether primary-target validation and maintenance-distance checks.

**Execution:** primaryReach=authoredReach*(1+sum(V)/100). Feed initial acquisition and continuing tether break checks consistently. Do not extend Arc/Fork/Chain secondary hops, beam width or target count.

**Display:** Tether Primary Range. Canonical item line: “+V% Tether Range”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** Base 10 m at +25% reaches 12.5 m with valid LOS. A branch with 8 m reach remains 8 m;13 m primary is rejected/broken. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-106. R164 baseline: Runtime/carrier/legality completion required.

## WA-107 · of Sustenance

**Suffix · G · Resource costs**

**Frozen generation:** ilvl 30+ | Q; C; top 12–18%; channel cost  | weight S. [G1 §17]

**Source contract:** V% reduced declared continuous Mana upkeep of channels. Cost applies before each paid tick; no extra channel started. Require: WIS-CORE. Armor: none.

**Integration:** Each paid channel Mana-upkeep tick, using the shared cost evaluator.

**Execution:** Add V/100 only to the channel-upkeep Reduced bucket. Compose with Economy once before affordability and payment. Exclude initial one-off cast fee unless explicitly upkeep; no change to tick interval or effect.

**Display:** Reduced Channel Mana Upkeep. Canonical item line: “V% Reduced Channel Mana Cost”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A 10 Mana tick with Economy 10% and Sustenance 10% costs 8, not 8.1. Unpaid tick cannot produce damage/healing or build Patient stacks. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-107. R164 baseline: Runtime/carrier/legality completion required.

## WA-108 · Patient

**Prefix · H · Channel lifecycle**

**Frozen generation:** ilvl 60+ | Q; C; top 4–6% / s; channel ramp  | weight R. [G1 §17]

**Source contract:** While maintaining one valid channel, gain V% increased effect per second, capped at three seconds. Reset on termination/retarget; invalid or unpaid ticks cannot build. Require: WIS-CORE. Armor: none.

**Integration:** Existing accepted channel session clock → paid tick magnitude snapshot.

**Execution:** Track continuous valid paid channel time from commit. IncreasedEffect=V/100*min(validElapsedSeconds,3). Reset on stop/retarget/invalidity; unpaid/paused gaps add no elapsed time. Add to scalable channel magnitude, not costs, tick rate or status duration.

**Display:** Conditional Channel Effect: per-second increase and current stacks/time in context. Canonical item line: “+V% Channel Effect per Second, up to 3 sec”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** At injected V=10, seconds 0/1/2/3/4 add 0/10/20/30/30%. Retarget resets to 0. An unpaid third tick does not add progress or emit an effect. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-108. R164 baseline: Runtime/carrier/legality completion required.

## WA-109 · of Steadiness

**Suffix · D · Status defenses**

**Frozen generation:** ilvl 50+ | Q; C; top 12–18%; control duration taken  | weight R. [G1 §17]

**Source contract:** V% reduced successful Hard Control duration while actively channeling. Shares duration-reduction group; no immunity and no hidden stun avoidance. Require: WIS-CORE. Armor: none.

**Integration:** Hard Control duration resolver, conditioned on a currently valid active channel.

**Execution:** While channeling, add V/100 to the SAME reduced-control-duration bucket as Recovery. Read channel state at admission. Do not grant avoidance or immunity, and do not retroactively shorten a control that interrupted the channel.

**Display:** Reduced Hard Control Duration while Channeling. Canonical item line: “V% Reduced Hard Control Duration while Channeling”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A 2 s admitted stun with Recovery 10% and active Steadiness 15% lasts 1.5 s. Outside a valid channel it lasts 1.8 s; immunity timer stays unchanged. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-109. R164 baseline: Runtime/carrier/legality completion required.

## WA-110 · of Continuance

**Suffix · H · Healing and support**

**Frozen generation:** ilvl 40+ | Q; C; top 15–25%; finite support duration  | weight S. [G1 §17]

**Source contract:** V% increased finite friendly buff/barrier duration. No CC, indefinite Aura, cooldown or immunity duration. Require: WIS-CORE. Armor: Chest; WIS armor; 0.5 × V.

**Integration:** Friendly buff/barrier application → source snapshot and canonical expiry scheduler.

**Execution:** duration=baseFiniteFriendlyDuration*(1+sum(V)/100). Only explicitly finite friendly scalable buffs/barriers qualify; no indefinite Aura, CC, cooldown, immunity or reservation extension. Existing effects retain their accepted duration on gear swap.

**Display:** Friendly Buff and Barrier Duration. Canonical item line: “+V% Buff and Barrier Duration”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A 10 s buff at +20% lasts 12 s. An indefinite Aura stays sustain-controlled; enemy Root duration is unchanged. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-110. R164 baseline: Runtime/carrier/legality completion required.

## WA-111 · of Receptivity

**Suffix · H · Healing and support**

**Frozen generation:** ilvl 15+ | Q; ALL; top 10–16%; healing received  | weight F. [G1 §17]

**Source contract:** V% increased healing received by wielder. Once at recipient; lifesteal is excluded unless its adapter opts in. Require: WIS-SOFT. Armor: All armor; 0.5 × V.

**Integration:** Recipient-side canonical healing admission, after outgoing source modifiers.

**Execution:** Multiply eligible received healing by 1+sum(V)/100 once. Apply normal maximum/Overheal permission afterward. Lifesteal is excluded unless its existing adapter deliberately opts in; do not recurse to outgoing-heal modifiers.

**Display:** Increased Healing Received. Canonical item line: “+V% Healing Received”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** Incoming 100 with V=20 becomes 120 before clipping. At 90/100 HP it restores 10 unless Overheal already authorized. Default leech amount is unchanged. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-111. R164 baseline: Runtime/carrier/legality completion required.

## WA-112 · of Respite

**Suffix · H · Healing and support**

**Frozen generation:** ilvl 70+ | Q; C; top 3–5%; cleanse respite  | weight R. [G1 §17]

**Source contract:** After an actual cleanse, grant the recipient a barrier equal to V% normal Max Health for 3 s. 10 s per-caster/recipient ICD; no additional cleanse; Ameliorate integration needed. Require: WIS-CORE. Armor: none.

**Integration:** Actual successful cleanse receipt → existing barrier controller, keyed caster/recipient 10 s cooldown.

**Execution:** On an eligible cleanse that removed something, grant a 3 s barrier of normalRecipientMaxHP*V/100. Preserve barrier stacking/deficit rules. No additional cleanse is created; keep source identity and 10 s pair lock across gear swaps.

**Display:** Equipped Effects: barrier after cleanse; context shows 3 s duration/10 s lock. Canonical item line: “After Cleansing, gain a V% Max Health Barrier for 3 sec”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** MaxHP 1000, V=4 creates 40 barrier for 3 s after a real cleanse. Cleansing nothing creates 0. Repeat at 5 s fails; at 10 s may succeed after a new valid cleanse. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-112. R164 baseline: Runtime/carrier/legality completion required.

## WA-113 · Commanding

**Prefix · I · Owned summons**

**Frozen generation:** ilvl 20+ | Q; C; top 20–30%; minion damage  | weight S. [G1 §17]

**Source contract:** V% increased damage of explicitly owned combat summons. No automatic transfer of the wielder’s other affixes. Require: WIS-CORE. Armor: Head; INT/WIS armor; 0.5 × V.

**Integration:** Valid owner equipment at summon commit → existing summon creation snapshot → summon attack assembly.

**Execution:** Add V/100 to the owner’s Minion Damage Increased bucket once in a created combat summon’s offensive snapshot. This is a bonus TO summons, not permission to copy every owner affix into them. Preserve existing snapshot lifecycle.

**Display:** Minion Damage, not player damage. Canonical item line: “+V% Minion Damage”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A 100 summon attack with V=25 becomes 125 before mitigation. Owner melee remains 100. An unrelated player’s summon and later owner gear swap do not rewrite the accepted offensive snapshot. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-113. R164 baseline: Runtime/carrier/legality completion required.

## WA-114 · Stalwart

**Prefix · I · Owned summons**

**Frozen generation:** ilvl 20+ | Q; C; top 24–36%; minion health  | weight S. [G1 §17]

**Source contract:** V% increased maximum Health of owned combat summons. Does not refill an existing summon when gear is changed. Require: WIS-CORE. Armor: Chest; INT/WIS armor; 0.5 × V.

**Integration:** Summon creation/max-stat projection → summon native EntityStatMap capacity owner.

**Execution:** Increase eligible owned-summon MaxHealth by 1+sum(V)/100. New summons initialize once under the normal creation contract; reprojection of an existing summon never fills current HP. Preserve accepted snapshot/live policy rather than re-summoning.

**Display:** Minion Maximum Health bonus. Canonical item line: “+V% Minion Maximum Health”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** An existing max 100/current 40 summon reprojected at +20% is 120/40; a normal new summon may start 120/120. Gear swaps cannot heal an existing summon. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-114. R164 baseline: Runtime/carrier/legality completion required.

## WA-115 · Bulwarked

**Prefix · I · Owned summons**

**Frozen generation:** ilvl 35+ | Q; C; top 20–30%; minion defense  | weight S. [G1 §17]

**Source contract:** V% increased audited Defense of owned combat summons. No native/derived mitigation duplication. Require: WIS-CORE. Armor: none.

**Integration:** Summon DefenseView at creation/projection → same native mitigation adapter as players.

**Execution:** Multiply the summon’s resolved Defense rating by 1+sum(V)/100. Use D01 only when no accepted rating owner exists. Do not multiply already-mitigated incoming damage by another armor factor.

**Display:** Minion Defense bonus and selected summon’s effective value. Canonical item line: “+V% Minion Defense”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** Minion rating 100 at V=25 becomes 125. Baseline protection is unchanged with zero bonus; record one Defense mitigation owner. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-115. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D01.

## WA-116 · of the Shelterer

**Suffix · I · Owned summons**

**Frozen generation:** ilvl 40+ | Q; C; top 8–12 pp; minion resistance  | weight S. [G1 §17]

**Source contract:** Adds V percentage points of all-six-element resistance to owned summons. Explicit extension to actor profiles; cap remains 75%. Require: WIS-CORE. Armor: none.

**Integration:** Owner snapshot → each created combat summon’s six-element defensive profile.

**Execution:** Add V/100 points to each summon elemental resistance and cap each effective value 75%. Preserve native other-source defenses and explicit immunity; do not transfer unrelated owner armor/resistance.

**Display:** Minion Elemental Resistances. Canonical item line: “+V% Minion Elemental Resistances”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A summon with Fire 10% plus V=8 has 18%; Water 0% becomes 8%. Player resistance stays unchanged. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-116. R164 baseline: Runtime/carrier/legality completion required.

## WA-117 · of Command

**Suffix · I · Owned summons**

**Frozen generation:** ilvl 45+ | Q; C; top 8–12%; minion attack speed  | weight R. [G1 §17]

**Source contract:** V% increased owned-summon attack rate. Needs native AI timing adapter and projectile budget QA. Require: WIS-CORE. Armor: none.

**Integration:** Owner snapshot → summon native AI attack scheduler / animation and projectile-release profile.

**Execution:** AttackRate=baseRate*(1+sum(V)/100). Scale scalable recovery/attack animation and hit/release checkpoints together, using §5.1 for managed NPC interactions. Preserve telegraphs, lifetime and per-shot projectile/damage budgets.

**Display:** Minion Attack Speed. Canonical item line: “+V% Minion Attack Speed”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A summon 1.0 s attack period at +15% becomes 0.869565 s. Animation and arrow release agree; projectile count per attack remains unchanged. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-117. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D03.

## WA-118 · of Pursuit

**Suffix · I · Owned summons**

**Frozen generation:** ilvl 20+ | Q; C; top 12–18%; minion movement  | weight F. [G1 §17]

**Source contract:** V% increased owned-summon movement speed. No teleporting, terrain phasing or extra leash distance. Require: WIS-CORE. Armor: none.

**Integration:** Owner snapshot → existing NPC movement/navigation speed parameters.

**Execution:** speed=baseMoveSpeed*(1+sum(V)/100) for the owned summon’s native locomotion. Keep pathfinding, collision, acceleration policy and leash bounds. Do not implement speed as repeated teleport steps.

**Display:** Minion Movement Speed. Canonical item line: “+V% Minion Movement Speed”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A 4 m/s summon at +15% requests 4.6 m/s on a clear route. Walls remain impassable; path/leash limits do not expand. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-118. R164 baseline: Runtime/carrier/legality completion required.

## WA-119 · of Binding Pacts

**Suffix · I · Owned summons**

**Frozen generation:** ilvl 30+ | Q; C; top 20–30%; minion duration  | weight S. [G1 §17]

**Source contract:** V% increased finite created-summon lifetime. No effect on persistent Iron Sentinel, conversions or copied-build duration by default. Require: WIS-CORE. Armor: none.

**Integration:** Owner snapshot → finite created-summon lifetime scheduler.

**Execution:** lifetime=authoredFiniteLifetime*(1+sum(V)/100). Exclude persistent Iron Sentinel, temporary conversions and copied-build duration unless explicitly included by their accepted profile. Gear swaps do not extend a living summon indefinitely.

**Display:** Minion Duration for finite created summons. Canonical item line: “+V% Minion Duration”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A 20 s created summon at +25% lasts 25 s. Iron Sentinel remains persistent; a 12 s Dominate duration remains 12 s. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-119. R164 baseline: Runtime/carrier/legality completion required.

## WA-120 · of Muster

**Suffix · I · Owned summons**

**Frozen generation:** ilvl 35+ | Q; C; top 10–16%; summon cost  | weight S. [G1 §17]

**Source contract:** V% reduced finite numeric Mana costs of summon skills. Cannot reduce sacrificed items/corpses or Simulacrum full-pool drain. Require: WIS-CORE. Armor: none.

**Integration:** Summon creation finite Mana quote → canonical resource commit.

**Execution:** Apply V/100 in the summon-specific Reduced Mana Cost bucket and compose with Economy once. Only finite numeric Mana is eligible; sacrificed items/corpses, full-pool drain and locks remain exact.

**Display:** Reduced Summon Mana Cost. Canonical item line: “V% Reduced Summon Mana Cost”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A 40 Mana summon with Economy 10% +Muster 20% costs 28. A required bound item is still consumed/bound once; full-pool Simulacrum payment is not discounted. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-120. R164 baseline: Runtime/carrier/legality completion required.

## WA-121 · Paragon’s

**Prefix · J · Effective skill ranks**

**Frozen generation:** ilvl 78+ | ALL; ALL; top +1 / +2; skiller  | weight C. [G1 §17]

**Source contract:** Adds +1 or +2 to all learned active skills while equipped. Custom gates: +1 at 78 on Very Rare/Legendary; +2 at 92, Legendary only. Require: BASE-RANK. Armor: none.

**Integration:** GearAffixRuntime.effects → shared EffectiveSkillLevel resolver → HytaleSkillExecutionSystem and skill/Advanced Stats projection.

**Execution:** Restore +1 only at ilvl 78+ on Very Rare/Legendary and +2 at 92+ on Legendary; remove the incorrect Rare allowance. Select ALL learned active skills. Add frozen V ranks once from each valid item after grouping; apply the skill’s accepted scalable fields only. Do not teach skills, change BaseSkillLevel, add slots or reset cooldown work.

**Display:** All Active Skills +V and each affected skill’s effective/base rank split. Canonical item line: “+V to All Active Skills”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A learned base-rank 5 matching skill with legal +1 executes/displays effective 6; nonmatching or unlearned skills remain unchanged. Test all rarity/ilvl boundaries, remove/re-equip, alias identity, and structural caps. A bound source cannot boost its own Iron Sentinel forge. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-121. R164 baseline: Runtime/carrier/legality completion required.

## WA-122 · of [Skill] Mastery

**Suffix · J · Effective skill ranks**

**Frozen generation:** ilvl 35+ | NAMED; MATCH; top +1 to +4; skiller  | weight S. [G1 §17]

**Source contract:** Adds ranks to one eligible named active skill. Specific-skill eligibility table; +4 is Rare-only at 94. Require: BASE-RANK. Armor: none.

**Integration:** GearAffixRuntime.effects → shared EffectiveSkillLevel resolver → HytaleSkillExecutionSystem and skill/Advanced Stats projection.

**Execution:** Persist selected SkillId; use +1/+2/+3/+4 gates from §3.3. Select the one persisted stable SkillId that passes its base-family selector. Add frozen V ranks once from each valid item after grouping; apply the skill’s accepted scalable fields only. Do not teach skills, change BaseSkillLevel, add slots or reset cooldown work.

**Display:** [Skill] +V and each affected skill’s effective/base rank split. Canonical item line: “+V to [Skill]”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A learned base-rank 5 matching skill with legal +1 executes/displays effective 6; nonmatching or unlearned skills remain unchanged. Test all rarity/ilvl boundaries, remove/re-equip, alias identity, and structural caps. No generic coefficient replaces the skill’s authored scaling. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-122. R164 baseline: Runtime/carrier/legality completion required.

## WA-123 · Zephyr Sage’s

**Prefix · J · Effective skill ranks**

**Frozen generation:** ilvl 55+ | FAMILY; ALL; top +1 to +3; skiller  | weight R. [G1 §17]

**Source contract:** Adds ranks to learned active skills whose schoolElement is WIND. Explicit school tag, not damage present, skill name or status. +3 Rare-only at 94. Require: BASE-RANK. Armor: none.

**Integration:** GearAffixRuntime.effects → shared EffectiveSkillLevel resolver → HytaleSkillExecutionSystem and skill/Advanced Stats projection.

**Execution:** Use +1/+2/+3 family gates from §3.3; +3 remains Rare-only. Select schoolElement == WIND. Add frozen V ranks once from each valid item after grouping; apply the skill’s accepted scalable fields only. Do not teach skills, change BaseSkillLevel, add slots or reset cooldown work.

**Display:** Wind Skills +V and each affected skill’s effective/base rank split. Canonical item line: “+V to Wind Skills”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A learned base-rank 5 matching skill with legal +1 executes/displays effective 6; nonmatching or unlearned skills remain unchanged. Test all rarity/ilvl boundaries, remove/re-equip, alias identity, and structural caps. No generic coefficient replaces the skill’s authored scaling. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-123. R164 baseline: Runtime/carrier/legality completion required.

## WA-124 · Glacial Sage’s

**Prefix · J · Effective skill ranks**

**Frozen generation:** ilvl 55+ | FAMILY; ALL; top +1 to +3; skiller  | weight R. [G1 §17]

**Source contract:** Adds ranks to learned active skills whose schoolElement is WATER. Explicit school tag, not damage present, skill name or status. +3 Rare-only at 94. Require: BASE-RANK. Armor: none.

**Integration:** GearAffixRuntime.effects → shared EffectiveSkillLevel resolver → HytaleSkillExecutionSystem and skill/Advanced Stats projection.

**Execution:** Use +1/+2/+3 family gates from §3.3; +3 remains Rare-only. Select schoolElement == WATER. Add frozen V ranks once from each valid item after grouping; apply the skill’s accepted scalable fields only. Do not teach skills, change BaseSkillLevel, add slots or reset cooldown work.

**Display:** Water Skills +V and each affected skill’s effective/base rank split. Canonical item line: “+V to Water Skills”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A learned base-rank 5 matching skill with legal +1 executes/displays effective 6; nonmatching or unlearned skills remain unchanged. Test all rarity/ilvl boundaries, remove/re-equip, alias identity, and structural caps. No generic coefficient replaces the skill’s authored scaling. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-124. R164 baseline: Runtime/carrier/legality completion required.

## WA-125 · Ember Sage’s

**Prefix · J · Effective skill ranks**

**Frozen generation:** ilvl 55+ | FAMILY; ALL; top +1 to +3; skiller  | weight R. [G1 §17]

**Source contract:** Adds ranks to learned active skills whose schoolElement is FIRE. Explicit school tag, not damage present, skill name or status. +3 Rare-only at 94. Require: BASE-RANK. Armor: none.

**Integration:** GearAffixRuntime.effects → shared EffectiveSkillLevel resolver → HytaleSkillExecutionSystem and skill/Advanced Stats projection.

**Execution:** Use +1/+2/+3 family gates from §3.3; +3 remains Rare-only. Select schoolElement == FIRE. Add frozen V ranks once from each valid item after grouping; apply the skill’s accepted scalable fields only. Do not teach skills, change BaseSkillLevel, add slots or reset cooldown work.

**Display:** Fire Skills +V and each affected skill’s effective/base rank split. Canonical item line: “+V to Fire Skills”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A learned base-rank 5 matching skill with legal +1 executes/displays effective 6; nonmatching or unlearned skills remain unchanged. Test all rarity/ilvl boundaries, remove/re-equip, alias identity, and structural caps. No generic coefficient replaces the skill’s authored scaling. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-125. R164 baseline: Runtime/carrier/legality completion required.

## WA-126 · Earthen Sage’s

**Prefix · J · Effective skill ranks**

**Frozen generation:** ilvl 55+ | FAMILY; ALL; top +1 to +3; skiller  | weight R. [G1 §17]

**Source contract:** Adds ranks to learned active skills whose schoolElement is EARTH. Explicit school tag, not damage present, skill name or status. +3 Rare-only at 94. Require: BASE-RANK. Armor: none.

**Integration:** GearAffixRuntime.effects → shared EffectiveSkillLevel resolver → HytaleSkillExecutionSystem and skill/Advanced Stats projection.

**Execution:** Use +1/+2/+3 family gates from §3.3; +3 remains Rare-only. Select schoolElement == EARTH. Add frozen V ranks once from each valid item after grouping; apply the skill’s accepted scalable fields only. Do not teach skills, change BaseSkillLevel, add slots or reset cooldown work.

**Display:** Earth Skills +V and each affected skill’s effective/base rank split. Canonical item line: “+V to Earth Skills”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A learned base-rank 5 matching skill with legal +1 executes/displays effective 6; nonmatching or unlearned skills remain unchanged. Test all rarity/ilvl boundaries, remove/re-equip, alias identity, and structural caps. No generic coefficient replaces the skill’s authored scaling. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-126. R164 baseline: Runtime/carrier/legality completion required.

## WA-127 · Voltaic Sage’s

**Prefix · J · Effective skill ranks**

**Frozen generation:** ilvl 55+ | FAMILY; ALL; top +1 to +3; skiller  | weight R. [G1 §17]

**Source contract:** Adds ranks to learned active skills whose schoolElement is LIGHTNING. Explicit school tag, not damage present, skill name or status. +3 Rare-only at 94. Require: BASE-RANK. Armor: none.

**Integration:** GearAffixRuntime.effects → shared EffectiveSkillLevel resolver → HytaleSkillExecutionSystem and skill/Advanced Stats projection.

**Execution:** Use +1/+2/+3 family gates from §3.3; +3 remains Rare-only. Select schoolElement == LIGHTNING. Add frozen V ranks once from each valid item after grouping; apply the skill’s accepted scalable fields only. Do not teach skills, change BaseSkillLevel, add slots or reset cooldown work.

**Display:** Lightning Skills +V and each affected skill’s effective/base rank split. Canonical item line: “+V to Lightning Skills”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A learned base-rank 5 matching skill with legal +1 executes/displays effective 6; nonmatching or unlearned skills remain unchanged. Test all rarity/ilvl boundaries, remove/re-equip, alias identity, and structural caps. No generic coefficient replaces the skill’s authored scaling. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-127. R164 baseline: Runtime/carrier/legality completion required.

## WA-128 · Umbral Sage’s

**Prefix · J · Effective skill ranks**

**Frozen generation:** ilvl 55+ | FAMILY; ALL; top +1 to +3; skiller  | weight R. [G1 §17]

**Source contract:** Adds ranks to learned active skills whose schoolElement is VOID. Explicit school tag, not damage present, skill name or status. +3 Rare-only at 94. Require: BASE-RANK. Armor: none.

**Integration:** GearAffixRuntime.effects → shared EffectiveSkillLevel resolver → HytaleSkillExecutionSystem and skill/Advanced Stats projection.

**Execution:** Use +1/+2/+3 family gates from §3.3; +3 remains Rare-only. Select schoolElement == VOID. Add frozen V ranks once from each valid item after grouping; apply the skill’s accepted scalable fields only. Do not teach skills, change BaseSkillLevel, add slots or reset cooldown work.

**Display:** Void Skills +V and each affected skill’s effective/base rank split. Canonical item line: “+V to Void Skills”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A learned base-rank 5 matching skill with legal +1 executes/displays effective 6; nonmatching or unlearned skills remain unchanged. Test all rarity/ilvl boundaries, remove/re-equip, alias identity, and structural caps. No generic coefficient replaces the skill’s authored scaling. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-128. R164 baseline: Runtime/carrier/legality completion required.

## WA-129 · Warmaster’s

**Prefix · J · Effective skill ranks**

**Frozen generation:** ilvl 55+ | FAMILY; M, R, B, H; top +1 to +3; skiller  | weight R. [G1 §17]

**Source contract:** Adds ranks to explicitly tagged martial weapon skills. Rare-only +3 at 94; does not mean every skill that requires a staff. Require: BASE-RANK. Armor: none.

**Integration:** GearAffixRuntime.effects → shared EffectiveSkillLevel resolver → HytaleSkillExecutionSystem and skill/Advanced Stats projection.

**Execution:** Use +1/+2/+3 family gates from §3.3; +3 remains Rare-only. Select explicit martial skill tag. Add frozen V ranks once from each valid item after grouping; apply the skill’s accepted scalable fields only. Do not teach skills, change BaseSkillLevel, add slots or reset cooldown work.

**Display:** Martial Skills +V and each affected skill’s effective/base rank split. Canonical item line: “+V to Martial Skills”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A learned base-rank 5 matching skill with legal +1 executes/displays effective 6; nonmatching or unlearned skills remain unchanged. Test all rarity/ilvl boundaries, remove/re-equip, alias identity, and structural caps. No generic coefficient replaces the skill’s authored scaling. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-129. R164 baseline: Runtime/carrier/legality completion required.

## WA-130 · Healer’s

**Prefix · J · Effective skill ranks**

**Frozen generation:** ilvl 55+ | FAMILY; C; top +1 to +3; skiller  | weight R. [G1 §17]

**Source contract:** Adds ranks to learned active skills with a primary Heal payload. Life Drain is Damage-primary, not included by incidental self-healing. Require: WIS-RANK. Armor: none.

**Integration:** GearAffixRuntime.effects → shared EffectiveSkillLevel resolver → HytaleSkillExecutionSystem and skill/Advanced Stats projection.

**Execution:** Use +1/+2/+3 family gates from §3.3; +3 remains Rare-only. Select primary payload == Heal. Add frozen V ranks once from each valid item after grouping; apply the skill’s accepted scalable fields only. Do not teach skills, change BaseSkillLevel, add slots or reset cooldown work.

**Display:** Healing Skills +V and each affected skill’s effective/base rank split. Canonical item line: “+V to Healing Skills”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A learned base-rank 5 matching skill with legal +1 executes/displays effective 6; nonmatching or unlearned skills remain unchanged. Test all rarity/ilvl boundaries, remove/re-equip, alias identity, and structural caps. No generic coefficient replaces the skill’s authored scaling. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-130. R164 baseline: Runtime/carrier/legality completion required.

## WA-131 · Binder’s

**Prefix · J · Effective skill ranks**

**Frozen generation:** ilvl 55+ | FAMILY; C; top +1 to +3; skiller  | weight R. [G1 §17]

**Source contract:** Adds ranks to learned summon-creation skills, across elemental schools. Includes Iron Sentinel when its rank profile exists; excludes Dominate and sacrifice-only skills. Require: WIS-RANK. Armor: none.

**Integration:** GearAffixRuntime.effects → shared EffectiveSkillLevel resolver → HytaleSkillExecutionSystem and skill/Advanced Stats projection.

**Execution:** Use +1/+2/+3 family gates from §3.3; +3 remains Rare-only. Select primary operation creates an owned summon, not conversion or sacrifice-only. Add frozen V ranks once from each valid item after grouping; apply the skill’s accepted scalable fields only. Do not teach skills, change BaseSkillLevel, add slots or reset cooldown work.

**Display:** Summoning Skills +V and each affected skill’s effective/base rank split. Canonical item line: “+V to Summoning Skills”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A learned base-rank 5 matching skill with legal +1 executes/displays effective 6; nonmatching or unlearned skills remain unchanged. Test all rarity/ilvl boundaries, remove/re-equip, alias identity, and structural caps. A bound source cannot boost its own Iron Sentinel forge. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-131. R164 baseline: Runtime/carrier/legality completion required.

## WA-132 · Blademaster’s

**Prefix · J · Effective skill ranks**

**Frozen generation:** ilvl 55+ | FAMILY; M, H; top +1 to +3; skiller  | weight R. [G1 §17]

**Source contract:** Adds ranks to learned active skills exposing a primary Strike component. No bonus for incidental child shockwaves becoming a new root family. Require: BASE-RANK. Armor: none.

**Integration:** GearAffixRuntime.effects → shared EffectiveSkillLevel resolver → HytaleSkillExecutionSystem and skill/Advanced Stats projection.

**Execution:** Use +1/+2/+3 family gates from §3.3; +3 remains Rare-only. Select primary component == Strike. Add frozen V ranks once from each valid item after grouping; apply the skill’s accepted scalable fields only. Do not teach skills, change BaseSkillLevel, add slots or reset cooldown work.

**Display:** Strike Skills +V and each affected skill’s effective/base rank split. Canonical item line: “+V to Strike Skills”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A learned base-rank 5 matching skill with legal +1 executes/displays effective 6; nonmatching or unlearned skills remain unchanged. Test all rarity/ilvl boundaries, remove/re-equip, alias identity, and structural caps. No generic coefficient replaces the skill’s authored scaling. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-132. R164 baseline: Runtime/carrier/legality completion required.

## WA-133 · Fletcher-Mage’s

**Prefix · J · Effective skill ranks**

**Frozen generation:** ilvl 55+ | FAMILY; R, C; top +1 to +3; skiller  | weight R. [G1 §17]

**Source contract:** Adds ranks to learned skills with primary Projectile delivery. Summon arrows do not reclassify the summon operation. Require: BASE-RANK. Armor: none.

**Integration:** GearAffixRuntime.effects → shared EffectiveSkillLevel resolver → HytaleSkillExecutionSystem and skill/Advanced Stats projection.

**Execution:** Use +1/+2/+3 family gates from §3.3; +3 remains Rare-only. Select primary delivery == Projectile. Add frozen V ranks once from each valid item after grouping; apply the skill’s accepted scalable fields only. Do not teach skills, change BaseSkillLevel, add slots or reset cooldown work.

**Display:** Projectile Skills +V and each affected skill’s effective/base rank split. Canonical item line: “+V to Projectile Skills”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A learned base-rank 5 matching skill with legal +1 executes/displays effective 6; nonmatching or unlearned skills remain unchanged. Test all rarity/ilvl boundaries, remove/re-equip, alias identity, and structural caps. No generic coefficient replaces the skill’s authored scaling. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-133. R164 baseline: Runtime/carrier/legality completion required.

## WA-134 · Rending

**Prefix · K · Signature procs**

**Frozen generation:** ilvl 45+ | Q; M, R, B; top 12–20%; status payload  | weight R. [G1 §17]

**Source contract:** V% chance to apply shared Bleed plus 50% reduced target Health regeneration for 4 s. New healing-suppression child; no duplicate Bleed with Hemorrhage. Require: BASE-CORE. Armor: none.

**Integration:** GearStatusGateway root hit → shared Bleed admission + recipient regeneration modifier (§6).

**Execution:** Require a valid positive Physical root attack. On successful V% proc, request one canonical Bleed and a separate 50% Health-regeneration reduction for 4 s. Merge Bleed with Hemorrhage/Serration rather than duplicate it. Healing suppression is strongest-only and changes regeneration, not healing/leech.

**Display:** Equipped Effects: Rending chance; detailed diagnostic lists both payloads. Canonical item line: “V% Chance to Bleed and Reduce Health Regen on Hit”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** A 10 HP/s regenerating target becomes 5/s for 4 s on accepted proc. Two sources remain 5/s, not 2.5. Direct heals are unchanged; the same hit applies one Bleed package. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-134. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D04.

## WA-135 · Crushing

**Prefix · K · Signature procs**

**Frozen generation:** ilvl 70+ | Q; M, R, B; top 6–10%; crushing blow  | weight C. [G1 §17]

**Source contract:** V% chance for a bounded bonus Physical hit based on current target Health. 1 s source/target ICD; fractions and source-power cap in special rules. Require: STR-CORE. Armor: none.

**Integration:** Completed direct root attack → bounded proc queue → next native Gather damage submission.

**Execution:** After main hit, bonus=min(f*survivingTargetCurrentHP,2*triggerNoncritPreMitigationPhysical). f=4% Common/Specialist,2% Elite/Miniboss,0.5% explicitly enabled Boss; Boss/PvP off by default. Roll V%, enforce 1 s source/target lock; child is NoProc/NoCrit/NoLeech.

**Display:** Equipped Effects: Crushing chance, not guaranteed added damage. Canonical item line: “V% Chance to Deal Bonus Physical Damage Based on Target Health”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** Common 1000 remainingHP and sourcePhysical 100 yields 40;100000 HP caps 200. Boss without opt-in and replays produce 0. Child is mitigated once. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-135. R164 baseline: Runtime/carrier/legality completion required.

## WA-136 · Deadly

**Prefix · K · Signature procs**

**Frozen generation:** ilvl 60+ | Q; M, R, B; top 8–12%; double hit damage  | weight R. [G1 §17]

**Source contract:** V% chance to double the noncritical Physical component of this item’s attack hit. Does not also double a critical; no fourth damage outcome. Require: DEX-CORE. Armor: none.

**Integration:** Shared root critical/outcome resolver before mitigation.

**Execution:** Only after a NONcritical eligible item attack outcome, roll V%. On success double its Physical component in the same hit vector. Do not double elemental components, emit an extra hit or permit critical+Deadly together.

**Display:** Deadly Strike chance and mutually exclusive critical/outcome diagnostic. Canonical item line: “V% Chance to Double Physical Damage”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** Physical 100+Fire 20 gives 200+20 on Deadly,150+30 on a 1.5× ordinary crit, never 300+30. NoProc children do not re-roll this property. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-136. R164 baseline: Runtime/carrier/legality completion required.

## WA-137 · Barbed

**Prefix · K · Signature procs**

**Frozen generation:** ilvl 55+ | Q; M, R; top 15–25%; impale  | weight R. [G1 §17]

**Source contract:** V% chance to lodge a stored Physical fragment for the next three qualifying hits. 8% of original noncritical pre-mitigation Physical hit per trigger; detailed caps apply. Require: BASE-CORE. Armor: none.

**Integration:** Positive root attack → source/target stored-fragment ledger → bounded native damage children.

**Execution:** On V% success store 0.08*originalNoncriticalPreMitigationPhysical for 3 charges/6 s; max 3 records per source/target; at capacity reject the new record without refreshing older ones. Later qualifying owner hits consume ONE oldest charge total before creating any new record. Child NoProc/NoCrit/NoLeech; another actor/summon cannot consume the record.

**Display:** Equipped Effects: stored-fragment proc chance; live records are target diagnostics. Canonical item line: “V% Chance to Impale; Next 3 Hits Deal Stored Physical Damage”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** SourcePhysical 100 stores 8 per charge. Next three eligible hits emit 8 each before defense; creation hit emits 0 stored damage. Fourth emits 0; expiry/replay cannot recover charges. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-137. R164 baseline: Runtime/carrier/legality completion required.

## WA-138 · Riveting

**Prefix · K · Signature procs**

**Frozen generation:** ilvl 55+ | Q; M, R, B; top 15–25%; armor break  | weight R. [G1 §17]

**Source contract:** V% chance to reduce target Defense rating by 15% for 3 s. Strongest-only; not 15 percentage points off mitigation; 5 s source/target lock. Require: STR-CORE. Armor: none.

**Integration:** Positive root hit → target DefenseView timed modifier → native Filter adapter.

**Execution:** Roll V%; apply target Defense rating×0.85 for 3 s with strongest-only merge and 5 s source/target lock. Modify the rating representation, not mitigation percentage points; preserve protected target rules and remove only this owned debuff on expiry. Boss/PvP effects require an explicit accepted opt-in.

**Display:** Equipped Effects: chance to reduce target Defense; target debuff view shows duration. Canonical item line: “V% Chance to Reduce Defense by 15% for 3 sec”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** Defense 100 becomes 85 for 3 s; at K100 mitigation changes 50%→45.9459%, not 35%. Duplicate applications do not multiply 0.85 twice; expiry restores 100. Use a below-cap fixture to prove a real damage difference. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-138. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D01.

## WA-139 · of Finality

**Suffix · K · Signature procs**

**Frozen generation:** ilvl 85+ | FIXED; M, R, B, C; top 5% threshold; cull  | weight C. [G1 §17]

**Source contract:** After an eligible damaging hit, execute an allowed target at or below 5% normal Max Health. Boss/PvP off by default; scripts always win; one death reward. Require: BASE-FIXED. Armor: none.

**Integration:** Post-hit actual survivor state → canonical execute/kill admission → one native damage/death path.

**Execution:** If a valid damaging hit leaves an allowed target at or below 5% normalMaxHealth, request an execute using the target’s normal protection/script veto and existing death owner. Boss/PvP off by default. Do not delete the entity, bypass scripted invulnerability or manufacture extra rewards.

**Display:** Equipped Effects: execute threshold 5%; no chance roll for this FIXED property. Canonical item line: “Execute Eligible Enemies at or Below 5% Health”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** A 1000-max allowed enemy left at 50 HP executes once;51 HP does not. Invulnerable/script-protected/Boss targets do not execute without explicit policy. One source death produces one loot/XP receipt. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-139. R164 baseline: Runtime/carrier/legality completion required.

## WA-140 · Resolute

**Prefix · K · Signature procs**

**Frozen generation:** ilvl 65+ | Q; M; top 15–25%; fortifying hit  | weight R. [G1 §17]

**Source contract:** V% chance after a valid melee hit to grant 5% less incoming Hit damage for 3 s. Strongest-only; cannot refresh during active duration; 5 s owner lock. Require: STR-CORE. Armor: none.

**Integration:** Accepted positive melee root hit → source-bound defensive timed effect.

**Execution:** Roll V%; on success grant 5% Less incoming Hit damage for 3 s. Keep strongest-only overlap, no refresh while active, and 5 s owner lock across item swaps. Apply as one Less factor; damaging statuses are excluded.

**Display:** Equipped Effects: chance to gain hit protection; active buff shows 5% Less. Canonical item line: “V% Chance on Melee Hit to Take 5% Less Hit Damage for 3 sec”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** Incoming 100 direct damage becomes 95 before other permitted factors; a 10 DoT tick remains 10. A second proc inside 3 s does not refresh, and a swap cannot bypass 5 s lock. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-140. R164 baseline: Runtime/carrier/legality completion required.

## WA-141 · of Twin Assault

**Suffix · K · Signature procs**

**Frozen generation:** ilvl 65+ | Q; M*, R*, C*; top 8–12%; dual strike  | weight R. [G1 §17]

**Source contract:** V% chance for an additional 40%-power offhand strike in a supported dual-wield attack. No execute threshold; native dual-wield support and action timing required. Require: DEX-CORE. Armor: none.

**Integration:** Audited dual-wield root profile → snapshot actual offhand → managed offhand contact/action schedule (§5.4).

**Execution:** Roll V% on a legal dual-wield attack and schedule one 0.40-power offhand strike through its real contact profile, with collision/LOS and synchronized animation. Use offhand local stats, not 0.4× main-hand damage. Child NoProc; preserve one root payment and recovery budget.

**Display:** Twin Assault proc chance; optional offhand power breakdown. Canonical item line: “V% Chance to Strike with Offhand for 40% Damage”.

**Sentinel:** ATTACK_PROC: apply only to an existing supported dual-wield Sentinel action with an actual eligible offhand. Snapshot the offhand and use its real contact/timing at 40% power; a single-weapon chassis does not acquire a fabricated offhand or bonus scalar (§10).

**Proof:** Main-hand 100/offhand 60 produces an additional 24 pre-defense offhand power on success. Empty offhand, shield or two-handed incompatibility produces none. Offhand animation/contact and damage must agree. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-141. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D03.

## WA-142 · of Retribution

**Suffix · K · Signature procs**

**Frozen generation:** ilvl 35+ | Q; H; top 6–10; thorns  | weight S. [G1 §17]

**Source contract:** After a hostile direct melee hit, return V Physical damage to its attacker. Reflect/NoProc/NoCrit/NoLeech; .5 s owner/attacker lock. Require: STR-CORE. Armor: none.

**Integration:** Completed hostile direct melee hit against wearer → reflection queue → next native Gather.

**Execution:** Return flat V Physical to the valid attacker with Reflect/NoProc/NoCrit/NoLeech tags and 0.5 s wearer/attacker lock. Do not reduce original damage. Respect distance/actor lifecycle and protection; reflected damage cannot trigger another reflection.

**Display:** Thorns / reflected Physical damage. Canonical item line: “Returns V Physical Damage to Melee Attackers”.

**Sentinel:** ATTACK_PROC: bind the same reflection rule to hostile direct melee hits received by the Sentinel, not its outgoing attacks. Use the Sentinel as reflector, retain the source-item snapshot and pair lock, and submit a protected Reflect/NoProc/NoCrit/NoLeech child (§10).

**Proof:** V=8 returns 8 before attacker mitigation, not a percent of received hit. Ranged/DoT/reflection events return 0; same-pair repeat inside 0.5 s returns 0. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-142. R164 baseline: Runtime/carrier/legality completion required.

## WA-143 · of Rupture

**Suffix · K · Signature procs**

**Frozen generation:** ilvl 80+ | Q; M, R, B, C; top 10–16%; kill burst  | weight C. [G1 §17]

**Source contract:** V% chance on credited kill to create one 2 m burst at 35% source pre-mitigation hit power. 1 s owner lock; channel preserved; no corpse-HP explosion or chain reaction. Require: BASE-CORE. Armor: none.

**Integration:** Deduplicated credited direct-hit kill receipt → area query → queued native channel damage.

**Execution:** Roll V%; create one 2 m LOS-respecting burst at 35% of the killing hit’s pre-mitigation channel composition.1 s owner lock; child NoProc/NoCrit/NoLeech, no recursive kill bursts. Do not use corpse HP as power.

**Display:** Equipped Effects: kill-burst chance. Canonical item line: “V% Chance on Kill to Explode for 35% of Hit Damage”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** Killing composition 100 Physical+20 Fire produces 35 Physical+7 Fire per eligible burst target before defense. Replayed death and burst-caused kills create no second burst. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-143. R164 baseline: Runtime/carrier/legality completion required.

## WA-144 · of Borrowed Arts

**Suffix · L · Granted skills and Auras**

**Frozen generation:** ilvl 70+ | FIXED; MATCH; top Rank 1 grant; item grant  | weight R. [G1 §17]

**Source contract:** Temporarily grants one allowlisted skill at rank 1 while equipped. Needs an ordinary skill slot and normal resource cost; never permanent learning. Require: BASE-FIXED. Armor: none.

**Integration:** Equipment lifecycle → existing skill-availability/loadout service → normal skill execution.

**Execution:** Add a temporary availability source for one allowlisted persisted SkillId at fixed base rank 1. The player assigns it to an ordinary existing slot; do not auto-overwrite a slot. On source removal, withdraw only temporary access and deactivate an otherwise unowned assignment safely. Normal resource/cooldown/weapon rules remain.

**Display:** Equipped Effects: grants [Skill]; normal skill UI distinguishes temporary access. Canonical item line: “Grants Rank 1 [Skill] while Equipped”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** Unlearned allowed skill becomes assignable at rank 1 while equipped; casting pays normally. Unequip removes temporary access, not permanent learned ownership or unrelated links. Two granting items use reference counts. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-144. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D06.

## WA-145 · Embercalling

**Prefix · L · Granted skills and Auras**

**Frozen generation:** ilvl 70+ | Q; M, R, C; top 8–12%; item trigger  | weight R. [G1 §17]

**Source contract:** V% chance after a valid direct item attack to trigger one rank-1 Fire Bolt. 3 s owner/group ICD; NoProc child; requires dedicated triggered-skill profile. Require: INT-CORE. Armor: none.

**Integration:** Completed eligible direct item attack → item-trigger queue → existing Fire Bolt executor with explicit rank 1 child profile.

**Execution:** Roll V%; enforce shared 3 s owner/item-trigger group. Enqueue one Fire Bolt at fixed rank 1 and zero ADDITIONAL Mana under the authored proc exception. Child carries NoProc and cannot inherit unrelated linked passives or +skill ranks. Validate target, LOS and legal source power.

**Display:** Equipped Effects: chance to trigger Fire Bolt. Canonical item line: “V% Chance on Hit to Cast Rank 1 Fire Bolt”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** Force success and observe one rank 1 Fire Bolt. Repeat inside 3 s, replay, reflected damage and child contact create none. Burn, when the skill authors it, still passes canonical rules. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-145. R164 baseline: Runtime/carrier/legality completion required.

## WA-146 · Rimecalling

**Prefix · L · Granted skills and Auras**

**Frozen generation:** ilvl 70+ | Q; M, R, C; top 8–12%; item trigger  | weight R. [G1 §17]

**Source contract:** V% chance after a valid direct item attack to trigger one rank-1 Frost Bolt. 3 s owner/group ICD; Chill obeys shared gates, not automatic control. Require: INT-CORE. Armor: none.

**Integration:** Completed eligible direct item attack → item-trigger queue → existing Frost Bolt executor with explicit rank 1 child profile.

**Execution:** Roll V%; enforce shared 3 s owner/item-trigger group. Enqueue one Frost Bolt at fixed rank 1 and zero ADDITIONAL Mana under the authored proc exception. Child carries NoProc and cannot inherit unrelated linked passives or +skill ranks. Validate target, LOS and legal source power.

**Display:** Equipped Effects: chance to trigger Frost Bolt. Canonical item line: “V% Chance on Hit to Cast Rank 1 Frost Bolt”.

**Sentinel:** ATTACK_PROC: install the same typed rule on eligible Sentinel root hits, with Sentinel actor/root identity, source-item snapshot, shared target protections and NoProc child flags (§10).

**Proof:** Force success and observe one rank 1 Frost Bolt. Repeat inside 3 s, replay, reflected damage and child contact create none. Chill still passes canonical application/threshold rules. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-146. R164 baseline: Runtime/carrier/legality completion required.

## WA-147 · of Succor

**Suffix · L · Granted skills and Auras**

**Frozen generation:** ilvl 75+ | Q; H, C; top 8–12%; item trigger  | weight R. [G1 §17]

**Source contract:** V% chance after a valid block to trigger one rank-1 Minor Heal on the wielder. 8 s owner/group ICD; actual block required; no reset by swapping. Require: WIS-CORE. Armor: none.

**Integration:** Actual native successful-block receipt → item-trigger queue → Minor Heal rank 1 self-target.

**Execution:** Roll V% on a real block, not on a direct attack. Apply 8 s owner/group lock and invoke fixed-rank 1 Minor Heal through canonical healing with NoProc and the authored zero-additional-Mana proc exception. Retain normal healing admission.

**Display:** Equipped Effects: chance to trigger Minor Heal on block. Canonical item line: “V% Chance on Block to Cast Rank 1 Minor Heal”.

**Sentinel:** ATTACK_PROC: bind to an actual successful Sentinel block only, using a compatible block-capable chassis. Heal that Sentinel at fixed rank 1 through the shared healing owner; preserve the 8 s lock and NoProc policy. Do not fabricate blocks or owner healing (§10).

**Proof:** A valid block with forced success heals once at rank 1. An ordinary hit without a block heals 0. Gear swaps/replays inside 8 s do not reset the lock; full-health normal clipping applies. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-147. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D07.

## WA-148 · of the Wellspring

**Suffix · L · Granted skills and Auras**

**Frozen generation:** ilvl 90+ | FIXED; C; top Rank 1 Aura; item aura  | weight C. [G1 §17]

**Source contract:** Projects item-bound Emanatism at fixed effective rank 1 while validly equipped. No manual slot; ordinary wielder pays normal reservation; Sentinel exception explicit. Require: WIS-FIXED. Armor: none.

**Integration:** Valid equipment transition → canonical Emanatism controller with item-owned activation/cleanup token.

**Execution:** Automatically project Emanatism at fixed effective rank 1 without taking an ordinary active slot. The player pays the Aura’s normal reservation/upkeep; use the same affordability, strongest-valid-recipient overlap and active-Aura admission limit. Loss/invalidity of the item removes only its owned projection.

**Display:** Equipped Effects: grants rank 1 Emanatism while equipped; reservation appears in resource view. Canonical item line: “Grants Rank 1 Emanatism Aura while Equipped”.

**Sentinel:** AURA: forged Sentinel projects the same fixed-rank 1 Aura under the explicit D08 no-Mana sustain exception. Same overlap/protection/cleanup rules apply; no unrelated owner Aura is inherited.

**Proof:** Equip starts one affordable rank 1 Emanatism; duplicate sources do not double its recipient payload. Insufficient sustain stops it; unequip/reconnect leaves no orphan projection or free resource refill. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-148. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D08.

## WA-149 · of the Thorn Standard

**Suffix · L · Granted skills and Auras**

**Frozen generation:** ilvl 90+ | FIXED; C, H; top Rank 1 Aura; item aura  | weight C. [G1 §17]

**Source contract:** Projects item-bound Thorns Aura at fixed effective rank 1 while validly equipped. Normal activation/upkeep on wielder; no reflect loops or duplicate aura layers. Require: WIS-FIXED. Armor: none.

**Integration:** Valid equipment transition → canonical Thorns Aura controller with item-owned activation/cleanup token.

**Execution:** Automatically project Thorns Aura at fixed effective rank 1 without taking an ordinary active slot. The player pays the Aura’s normal reservation/upkeep; use the same affordability, strongest-valid-recipient overlap and active-Aura admission limit. Loss/invalidity of the item removes only its owned projection.

**Display:** Equipped Effects: grants rank 1 Thorns Aura while equipped; reservation appears in resource view. Canonical item line: “Grants Rank 1 Thorns Aura while Equipped”.

**Sentinel:** AURA: forged Sentinel projects the same fixed-rank 1 Aura under the explicit D08 no-Mana sustain exception. Same overlap/protection/cleanup rules apply; no unrelated owner Aura is inherited.

**Proof:** Equip starts one affordable rank 1 Thorns Aura; duplicate sources do not double its recipient payload. Insufficient sustain stops it; unequip/reconnect leaves no orphan projection or free resource refill. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-149. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D08.

## WA-150 · of Measured Time

**Suffix · L · Granted skills and Auras**

**Frozen generation:** ilvl 94+ | FIXED; C; top Rank 1 Aura; item aura  | weight C. [G1 §17]

**Source contract:** Projects item-bound Pedanticism at fixed effective rank 1 while validly equipped. Normal Mana reservation; no modifier to attack-animation rate. Require: WIS-FIXED. Armor: none.

**Integration:** Valid equipment transition → canonical Pedanticism controller with item-owned activation/cleanup token.

**Execution:** Automatically project Pedanticism at fixed effective rank 1 without taking an ordinary active slot. The player pays the Aura’s normal reservation/upkeep; use the same affordability, strongest-valid-recipient overlap and active-Aura admission limit. Loss/invalidity of the item removes only its owned projection.

**Display:** Equipped Effects: grants rank 1 Pedanticism while equipped; reservation appears in resource view. Canonical item line: “Grants Rank 1 Pedanticism Aura while Equipped”.

**Sentinel:** AURA: forged Sentinel projects the same fixed-rank 1 Aura under the explicit D08 no-Mana sustain exception. Same overlap/protection/cleanup rules apply; no unrelated owner Aura is inherited.

**Proof:** Equip starts one affordable rank 1 Pedanticism; duplicate sources do not double its recipient payload. Insufficient sustain stops it; unequip/reconnect leaves no orphan projection or free resource refill. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-150. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D08.

## WA-151 · of Discovery

**Suffix · M · Economy and utility**

**Frozen generation:** ilvl 10+ | Q; ALL; top 12–18%; magic find  | weight F. [G1 §17]

**Source contract:** Adds V% GearMagicFind to the existing quality-weight formula. Not quantity, affix level, rank success, guaranteed Legendary or free salvage. Require: LUCK-SOFT. Armor: All armor; 1 × V.

**Integration:** HytaleGearEquipment.magicFindBreakdown → existing GearMagicFind → HytaleEncounterRewards loot-event snapshot.

**Execution:** GearMF=sum(eligible frozen V)/100; MF=max(0,0.005*E(total valid rawLuck)+GearMF). Use §8 source rarity-weight formulas once at the accepted recipient/sponsor snapshot. Do not alter opportunity chance, quantity, item level, affix tiers or intrinsic roll.

**Display:** Magic Find total and separate Luck/Gear contributions; display policy matches actual loot policy. Canonical item line: “+V% Magic Find”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** RawLuck 100 gives 0.50; V=15 adds 0.15, total 0.65. Protected QA items remain excluded in production; isolated shadow-economy evidence uses the real MF path without laundering QA provenance. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-151. R164 baseline: Code path found; connected proof pending.

## WA-152 · of Prosperity

**Suffix · M · Economy and utility**

**Frozen generation:** ilvl 10+ | Q; ALL; top 20–30%; gold find  | weight F. [G1 §17]

**Source contract:** V% increased eligible currency reward quantity. One deduplicated reward pot; not inventory value or sell price. Require: LUCK-SOFT. Armor: All armor; 1 × V.

**Integration:** Deduplicated eligible currency reward pot → ownership/allocation receipt → currency admission.

**Execution:** bonus=baseEligibleCurrency*sum(V)/100. Quantize once at pot level using existing currency precision; D09 carries fractional remainder per owner/currency where units are integer. Allocate the resulting one pot; no per-party-member extra reward or sell-value multiplier.

**Display:** Currency Find; reward trace shows base/bonus/quantized pot. Canonical item line: “+V% Currency Find”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** 100 eligible currency and V=25 produces 125 once. Four 10-unit rewards at 25% pay 50 total with remainder accounting, not 48; replay adds 0. Looted existing currency stacks are not rebonused. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-152. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D09.

## WA-153 · of Lasting Craft

**Suffix · M · Economy and utility**

**Frozen generation:** ilvl 1+ | Q; ALL; top 20–30%; durability efficiency  | weight F. [G1 §17]

**Source contract:** V% chance to prevent a native durability-loss event. Only validated owned-item loss; no auto-repair or dupe. Require: BASE-SOFT. Armor: All armor; 1 × V.

**Integration:** Managed durability debit boundary before ItemStack mutation; use native loss admission if verified, otherwise §5.5 managed-carrier writer route.

**Execution:** For each valid positive loss event belonging to this exact item, deterministically roll V/100 once. Success prevents that debit; failure applies the full normal debit. No post-loss repair/refund, no avoidance on repair or inventory move, and no shared native asset mutation.

**Display:** Chance to Prevent Durability Loss. Canonical item line: “V% Chance to Prevent Durability Loss”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** A 1-durability item facing a 1-point debit survives unchanged on success, breaks normally on failure. Ten callbacks with one lossEventId debit/check once. Repair and death-loss policy remain explicitly covered. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-153. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D10.

## WA-154 · of Ease

**Suffix · M · Economy and utility**

**Frozen generation:** ilvl 20+ | Q; ALL; top 12–18%; reduced requirements  | weight F. [G1 §17]

**Source contract:** V% reduced attribute requirements of this item only. Round requirements up; no character-level requirement reduction. Require: BASE-SOFT. Armor: All armor; 1 × V.

**Integration:** GearRequirements.combine at item creation/migration validation → same native equip/use validity path.

**Execution:** req[a]=max(10,ceil(rawNonzeroReq[a]*(1-V/100))); zeros remain 0. Compute raw requirements by per-attribute MAX of base/affixes, not sum. Required character level is unchanged. Persist result/revision; do not apply the discount again at equip time.

**Display:** Actual required attributes in tooltip; optional local reduction breakdown. Canonical item line: “Requirements -V%”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** RawSTR 83 and V=20 yields 67, not 66. Character level requirement remains. A baseline 10 stays 10, and a zeroINT gate remains 0. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-154. R164 baseline: Code path found; connected proof pending.

## WA-155 · of Lanterns

**Suffix · M · Economy and utility**

**Frozen generation:** ilvl 1+ | Q; ALL; top 1–2 m; light radius  | weight F. [G1 §17]

**Source contract:** Adds V metres to the wielder’s purely visual light radius. Exploration utility; lower combat priority; no wall reveal or native shader invention. Require: BASE-SOFT. Armor: Head; 1 × V.

**Integration:** Equipment revision → actor-owned native DynamicLight/ColorLight projection or normal held-light composer (§5.6).

**Execution:** Increase the actor light-radius target by sum(V) metres without changing hue, world illumination ownership or revealing occluded space. Merge with the native held-light source without overwriting it. D11 defines native-radius quantization/calibration and truthful effective display; no shader/client patch.

**Display:** Light Radius target plus measured/native-quantized effective value in Advanced Stats. Canonical item line: “+V m Light Radius”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** At an audited 4 m baseline with +1 m, visible radius target is 5 m. Unequip restores native light; equip a torch and verify both contributors are composed rather than one erased. Test no extra light entities after reconnect. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-155. R164 baseline: Runtime/carrier/legality completion required. Explicit closure: D11.

## WA-156 · of Gathering

**Suffix · M · Economy and utility**

**Frozen generation:** ilvl 15+ | Q; ALL; top 0.5–1.0 m; pickup radius  | weight F. [G1 §17]

**Source contract:** Adds V metres to allowed currency/material pickup reach. Does not collect equipment, steal another player’s loot or bypass ownership. Require: BASE-SOFT. Armor: Legs; 1 × V.

**Integration:** Player pickup proximity query → existing claim/protection/SpatialInventoryTransferCoordinator receipt path.

**Execution:** candidateReach=normalPickupReach+sum(V). Query only allowlisted currency/material drops. For each candidate retain claim, world, protected-loot, capacity, line-of-access and atomic ownership checks. Never apply the radius to equipment or mutate a shared drop asset’s radius.

**Display:** Material/Currency Pickup Reach in metres. Canonical item line: “+V m Currency and Material Pickup Radius”.

**Sentinel:** OWNER_ONLY: the effect remains fully functional on a player. Do not copy it into the forged Sentinel, give the owner another copy, or recurse through other summons (§10).

**Proof:** Baseline 1.75 m plus V=0.75 reaches eligible material at 2.4 m, not 2.6 m. Equipment at 2.0 m and another player’s claimed currency remain untouched. Full bag produces no deletion. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-156. R164 baseline: Runtime/carrier/legality completion required.

## WA-157 · of Measure

**Suffix · A · Physical power**

**Frozen generation:** ilvl 10+ | Q; M, R, B; top 6–10; local phys min  | weight F. [G1 §17]

**Source contract:** Adds V to this item’s minimum Physical damage only. Raises the floor before local Enhanced Damage; if the floor exceeds the ceiling, the ceiling normalizes up to the floor. Require: BASE-CORE. Armor: none.

**Integration:** GearAffixRuntime.physical → ManagedGearDamageInteraction.Calculator and ManagedGearProjectile launch snapshot; HytaleEquipmentAdapter/weapon-power resolver for weapon-scaling skills.

**Execution:** Add V only to raw Physical MINIMUM before normalization and local Increased. If it exceeds the maximum, raise the maximum to the new minimum; do not swap endpoints. Preserve one sample per authored strike.

**Display:** Resolved minimum and full Physical range. Canonical item line: “+V Minimum Physical Damage”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** 12–20 plus minimumV=8 gives 20–20; with V=10 in the algebra vector it becomes 22–22. Maximum-only source stays independent. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-157. R164 baseline: Code path found; connected proof pending.

## WA-158 · of Slaughter

**Suffix · A · Physical power**

**Frozen generation:** ilvl 10+ | Q; M, R, B; top 10–16; local phys max  | weight F. [G1 §17]

**Source contract:** Adds V to this item’s maximum Physical damage only. Raises the ceiling before local Enhanced Damage; does not alter the minimum. Require: BASE-CORE. Armor: none.

**Integration:** GearAffixRuntime.physical → ManagedGearDamageInteraction.Calculator and ManagedGearProjectile launch snapshot; HytaleEquipmentAdapter/weapon-power resolver for weapon-scaling skills.

**Execution:** Add V only to raw Physical MAXIMUM before normalization and local Increased. Do not modify minimum. Preserve one per-strike random sample shared across derived carriers.

**Display:** Resolved maximum and full Physical range. Canonical item line: “+V Maximum Physical Damage”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** 12–20 plus maximumV=12 gives 12–32. Honed +8 and Measure+5 then produce 25–40 before Brutal. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 WA-158. R164 baseline: Code path found; connected proof pending.

## GA-159 · Laminated

**Prefix · D · Armor protection**

**Frozen generation:** ilvl 1+ | Q; All armor; top 2.0–3.0 pp (Chest); other slots proportional; local armor flat | weight F. [G1 §17]

**Source contract:** Adds V percentage points to this piece's managed native Physical/Projectile protection contribution. Head uses 0.20/0.36 of V, Hands 0.16/0.36 and Legs 0.28/0.36. Apply before Increased local protection. Does not add Defense rating or elemental resistance. Require: BASE-CORE. Armor-only definition; no weapon extension.

**Integration:** GearAffixRuntime.protection → HytaleGearEquipment.project → exactly one managed native protection effect.

**Execution:** Add frozen V percentage points to THIS armor piece before Fortified. Scale the Chest top-range by Head 20/36,Hands 16/36,Legs 28/36 before value quantization at generation; do not repeat scaling in runtime. This is not an independent Defense-rating grant.

**Display:** This piece’s Physical/Projectile Protection and total effective protection. Canonical item line: “+V Armor”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** Chest base 10 pp with V=3 becomes 13 pp; Fortified 20% makes 15.6 pp. Other pieces and elemental resistance stay unchanged; mitigation is counted once. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 GA-159. R164 baseline: Code path found; connected proof pending.

## GA-160 · Fortified

**Prefix · D · Armor protection**

**Frozen generation:** ilvl 1+ | Q; All armor; top 20–30%; local armor increased | weight F. [G1 §17]

**Source contract:** V% increased local armor protection of this piece, after its local protection-point additions. Does not multiply Health, Mana, Stamina, elemental resistance or the armor of other pieces. Native mitigation is applied once. Require: BASE-CORE. Armor-only definition; no weapon extension.

**Integration:** GearAffixRuntime.protection → HytaleGearEquipment.project / native effect owner.

**Execution:** pieceProtection=(intrinsicProtection+LaminatedPP)*(1+V/100). Sum valid pieces under the existing 60% managed physical/projectile cap. Do not multiply resource bonuses, other pieces or elemental resistance.

**Display:** Local Increased Armor Protection and total protection. Canonical item line: “+V% Enhanced Armor”.

**Sentinel:** SELF_STAT: resolve from the bound source once, not from the summoner’s later equipment. Apply only to a matching Sentinel stat/action; preserve native chassis and source-property conversion (§10).

**Proof:** A 10 pp piece plus 3 pp local flat at V=20 becomes 15.6 pp. An unrelated 5 pp piece stays 5 pp. Zero modifiers reproduce the pre-task armor result. Run owner protocol P (§12.4) on the affected real path.

**Traceability:** G1 §17 / §17.1, A 1 GA-160. R164 baseline: Code path found; connected proof pending.


# 14. Coverage and implementation decision register

## 14.1 Required operator coverage

| Runtime family | Affix IDs | Count |
| --- | --- | --- |
| A · Physical power | WA-001, WA-002, WA-157, WA-158 | 4 |
| A · Focus and magic power | WA-003 | 1 |
| A · Direct damage | WA-004, WA-005, WA-006, WA-007 | 4 |
| B · Action timing | WA-008, WA-009 | 2 |
| A · Critical hits | WA-010, WA-011 | 2 |
| B · Cooldown work | WA-012 | 1 |
| C · Damaging statuses | WA-013, WA-065, WA-066, WA-067, WA-068 | 5 |
| B · Reach and projectiles | WA-014, WA-015, WA-016 | 3 |
| A · Elemental composition | WA-017, WA-018, WA-019, WA-020, WA-021, WA-022, WA-023, WA-024, WA-025, WA-026, WA-027, WA-028, WA-035, WA-036, WA-037, WA-038, WA-039, WA-040 | 18 |
| D · Elemental defense | WA-029, WA-030, WA-031, WA-032, WA-033, WA-034, WA-072, WA-073, WA-074, WA-075, WA-076, WA-077, WA-078 | 13 |
| E · Impact conditions | WA-041, WA-042, WA-043, WA-044, WA-045, WA-046, WA-047, WA-048, WA-049, WA-050, WA-051, WA-052 | 12 |
| C · Status proc gateway | WA-053, WA-054, WA-055, WA-056, WA-057, WA-058, WA-059, WA-060, WA-061, WA-062, WA-063, WA-064 | 12 |
| D · Defense rating | WA-069, WA-070, WA-071 | 3 |
| D · Status defenses | WA-079, WA-080, WA-081, WA-082, WA-109 | 5 |
| D · Block and mitigation | WA-083, WA-084 | 2 |
| F · Attributes and pools | WA-085, WA-086, WA-087, WA-088, WA-089, WA-090, WA-091, WA-092, WA-093 | 9 |
| G · Recovery | WA-094, WA-095, WA-096, WA-097, WA-098 | 5 |
| G · Regeneration | WA-099, WA-100 | 2 |
| G · Resource costs | WA-101, WA-102, WA-107 | 3 |
| H · Healing and support | WA-103, WA-104, WA-105, WA-106, WA-110, WA-111, WA-112 | 7 |
| H · Channel lifecycle | WA-108 | 1 |
| I · Owned summons | WA-113, WA-114, WA-115, WA-116, WA-117, WA-118, WA-119, WA-120 | 8 |
| J · Effective skill ranks | WA-121, WA-122, WA-123, WA-124, WA-125, WA-126, WA-127, WA-128, WA-129, WA-130, WA-131, WA-132, WA-133 | 13 |
| K · Signature procs | WA-134, WA-135, WA-136, WA-137, WA-138, WA-139, WA-140, WA-141, WA-142, WA-143 | 10 |
| L · Granted skills and Auras | WA-144, WA-145, WA-146, WA-147, WA-148, WA-149, WA-150 | 7 |
| M · Economy and utility | WA-151, WA-152, WA-153, WA-154, WA-155, WA-156 | 6 |
| D · Armor protection | GA-159, GA-160 | 2 |

Every row above counts a distinct affix definition, not every rank tier or named-skill selection. Exactly 160 IDs are covered, including the minimum/maximum-damage additions WA-157/158 and the armor-specific GA-159/160.

## 14.2 New closure decisions, not claims about native engine behavior

| Decision | Topic | Location | Exact scope |
| --- | --- | --- | --- |
| D01 | Defense representation | §7 | Invertible rating/protection bridge with explicit K(level), preserving zero-affix armor behavior. Needed only where no accepted rating owner exists. |
| D02 | Conversion remainder | §4.2 | Consume a percentage of original Physical limited to its unconverted remainder; no chained conversion or hidden extra damage. |
| D03 | Native timing/contact profiles | §5.1–5.4 | Synchronized immutable managed action/animation/geometry profiles, including genuine offhand contact rather than a fake extra scalar. |
| D04 | Status merge and proc normalization | §6 | Single combined same-status opportunity, shared shot/area coefficient, explicit continuous-window default where absent. |
| D05 | Damaging-status duration | §6.3 | WA-068 may extend its own sourced Burn/Poison/Bleed duration; ordinary 4 s Burn and DPS-only passives retain their rules. |
| D06 | Temporary skill availability | §8.2 | Borrowed Arts exposes a source-bound rank 1 skill for normal slot assignment; it does not auto-overwrite or teach. |
| D07 | Succor trigger correction | §8.2 | Actual successful block, not generic direct-attack ITEM_TRIGGER prose from the audit. |
| D08 | Sentinel item-Aura sustain | §9 | Explicit fixed-rank 1 no-NPC-Mana exception; player reservation/upkeep remains normal. |
| D09 | Fractional currency | §8.3 | One durable remainder at the existing deduplicated reward-pot boundary, unless an accepted equivalent already exists. |
| D10 | Durability ownership | §5.5 | Prevent loss before mutation; use verified native admission or replace only duplicated managed-carrier debit producers. |
| D11 | Native light precision | §5.6 | Keep rolled metres and report effective native radius honestly after calibrated supported quantization. |
| D12 | Sentinel semantic scope | §10 | Per-card compatible effect versus intentional player-only scope; inheritance dispositions are not runtime-readiness labels. |

These decisions close missing implementation rules. They do not authorize adding affixes, changing ordinary budgets, weakening protection or replacing accepted newer gameplay. When current accepted code already implements an equivalent boundary, preserve it and document the exact mapping.

## 14.3 Required generated evidence schema

For each ID output `definitionRevision, operator, side, legalScopes, positiveCarrierIds, runtimeConsumerPaths, nativeBoundary, automatedTestIds, connectedEvidenceIds, advancedStatsProjection, sentinelDisposition, productionEligible, implementationComplete, connectedVerified, reason` in one generated report. Source hashes and actual current commit/JAR hashes accompany the report. Empty arrays or a no-op consumer fail completion.

Coverage assertion: `sourceIds == runtimeDescriptorIds == evidenceIds == {WA-001..WA-158, GA-159, GA-160}`. A declared producer scope cannot be covered solely by a different scope. Treat an affix that works on Sentinel but not the player as incomplete for player functionality. No audit-script constant may force the count to 160 without testing the consumers.

## 14.4 Existing 19 regression baseline

WA-001, WA-002, WA-009, WA-012, WA-085, WA-086, WA-087, WA-088, WA-089, WA-090, WA-091, WA-092, WA-093, WA-151, WA-154, WA-157, WA-158, GA-159, GA-160. These are source-audited code paths only; preserve and prove them during the integration. WA-003/WA-121 must not be added to this baseline merely because they appear in an older ENABLED set. [A1]


# 15. Source register and version limits

G1/A1/CMD1 are supplied project sources. R1–R4 are actual pinned repository reads used to identify integration seams, not a substitute for the current local workspace. H1–H8 are first-party Hytale references consulted on 30 September 2026; some pages expose different documentation versions. Compile and test against the installed 0.7.0-pre.4 artifact before asserting availability. New formulas/algorithms labeled Dxx are design prescriptions, not quotations from these sources.

**[G1]** Gear Master Document v1.2(1).docx, 26 September 2026. Owner-provided library document. §§04–17 supply rules; §17 all 160 definitions, §17.1 templates, §21 older delivery boundary. SHA-256 7f88d6e319e292f9e0a059dd1eddeb3f3e67e182a873bc5bf00ed9f8accef7f3

**[A1]** GEAR_AFFIX_RUNTIME_AUDIT.md and .csv, R164 / Hytale 0.7.0-pre.4. Owner-provided runtime audit, not connected QA. MD SHA-256 4e0fc4d551b943d8aefc556802a77e656126045aceb28ab5c5a521e0e0130035; CSV SHA-256 9b143e5c1aa7b9561c35bdbaf64426bf254ea9e009801239afa0ef9c7a7c5130

**[CMD1]** COMMANDS.md, owner-provided earlier R156 command reference. Command availability must be checked against current registration; this document does not assert later trace/fixture commands are already live.

**[C1]** Owner instructions in this conversation: all 160 existing affixes must become functional; no new affix designs; Magic blue / Rare #FFFF00; real combat, Advanced Stats and Iron Sentinel proof; owner performs connected game actions. Preserve latest accepted forge eligibility and current mod behavior rather than restoring obsolete source restrictions.

**[R1]** Pinned supplemental code: GearAffixRuntime.java at commit 1d3761bbbd586e03eaaf9275c74f81af8c7d9f21. Older than the R164 audit; demonstrates existing projections/21-ID whitelist only. https://github.com/Graham3D/Hytale/blob/1d3761bbbd586e03eaaf9275c74f81af8c7d9f21/src/main/java/com/inigmasgames/hytalerpg/gear/GearAffixRuntime.java

**[R2]** Pinned supplemental code: ManagedGearDamageInteraction.java at the same commit. Native damage-leaf adapter and one Physical/Projectile result. https://github.com/Graham3D/Hytale/blob/1d3761bbbd586e03eaaf9275c74f81af8c7d9f21/src/main/java/com/inigmasgames/hytalerpg/gear/ManagedGearDamageInteraction.java

**[R3]** Pinned supplemental code: HytaleGearEquipment.java at the same commit. Native equipment validity, pool/protection projections and QA-MF exclusion. https://github.com/Graham3D/Hytale/blob/1d3761bbbd586e03eaaf9275c74f81af8c7d9f21/src/main/java/com/inigmasgames/hytalerpg/gear/HytaleGearEquipment.java

**[R4]** Pinned supplemental code: execution/summon/IronSentinelAffixes.java at the same commit. Existing inheritance taxonomy and limited adapted set, not evidence all inherited effects work. https://github.com/Graham3D/Hytale/blob/1d3761bbbd586e03eaaf9275c74f81af8c7d9f21/src/main/java/com/inigmasgames/hytalerpg/execution/summon/IronSentinelAffixes.java

**[H1]** Official Hytale Server API: DamageSystems. Gather/Filter/application separation, no damage submission inside Filter, explicit ApplyDamage dependency for post-state inspection. Consulted 30 September 2026. https://docs.hytale.com/api/com/hypixel/hytale/server/core/modules/entity/damage/DamageSystems

**[H2]** Official Hytale Server API: Damage. Cancellable damage, metadata registry, BLOCKED, STAMINA_DRAIN_MULTIPLIER and hit/source metadata. Consulted 30 September 2026. https://pre-release.docs.hytale.com/api/com/hypixel/hytale/server/core/modules/entity/damage/Damage

**[H3]** Official Hytale Server API: EntityStatMap. Keyed modifiers and stat mutation methods; installed native stat-modification lifecycle remains authoritative. https://pre-release.docs.hytale.com/api/com/hypixel/hytale/server/core/modules/entitystats/EntityStatMap

**[H4]** Official Hytale item asset schema. Weapon/armor properties, durability configuration, pickup radius and RenderDualWielded; schema presence is not proof of a particular live adapter. https://pre-release.docs.hytale.com/assets/item/items/

**[H5]** Official Hytale item animation schema, displayed pre-release 0.7.0-pre.3 at retrieval. ItemAnimation.Speed and first/third-person references. https://pre-release.docs.hytale.com/assets/item/animations/

**[H6]** Official Hytale Server API: Interaction. Registered interaction execution/compilation and client/server simulation boundary. https://pre-release.docs.hytale.com/api/com/hypixel/hytale/server/core/modules/interaction/interaction/config/Interaction

**[H7]** Official Hytale Server API: DynamicLight. Native component representation; verify ColorLight radius units on the installed build. https://docs.hytale.com/com/hypixel/hytale/server/core/modules/entity/component/DynamicLight

**[H8]** Official Hytale entity effect schema. Source-owned effect duration/overlap/stat and resistance configuration. https://pre-release.docs.hytale.com/assets/entity/effects/

