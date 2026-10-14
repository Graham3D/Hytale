# Master Enemies v1.2 — Stage 1 evidence and blockers

Status: **BLOCKED; Stage 2 is not authorized or ready.** This is an isolated
engineering checkpoint, not completion or acceptance of Stage 1. The stage's
explicit blocked-exit rule applies. Monster Affix remains OPEN and
`ownerAccepted=false`. All unverified Elite/HRO behavior remains QA_PENDING.

The owner authorized Stage 1 from ME12-STAGES-4P-01 using ME12-ARCH-AD1, then
explicitly authorized selective inclusion of the reviewed Elite/HRO fixes.
The earlier read-only restriction was superseded only for this isolated Stage 1.
No implementation of the v2 sampler, encounter schema, archive reader, rewards,
learning, UI expansion or live activation has been undertaken.

## Source selection and accepted-change matrix

Production-source checkpoint: `c37a10408501b11c49fd273eeec39725158c863d`,
branch `codex/me12-stage1`, worktree
`C:/Users/Zemio/.codex/worktrees/me12-stage1/Hytale-main-clean`.
Parent and current remote main:
`b839526e7106f780648c315692b04bb372b58d9e`.
This is the latest verified integrated baseline, **not a cumulatively
QA-accepted 0.7.1 gameplay baseline**. No such accepted baseline is established
by the available evidence.

| Source | Disposition | Acceptance |
| --- | --- | --- |
| Integrated main b839526e | Preserved as parent, including hostile-mob audit | Its documentation integration does not establish gameplay QA |
| HRO f7d414e6 | Reviewed source/tests selectively included | QA_PENDING |
| Elite 3327a565 | Reviewed source/tests selectively included | QA_PENDING |
| Elite remote 1ff1f069 | Local Elite remains four commits newer; unpublished work preserved | No new acceptance inferred |
| Primary main 1dfa2cea | Left on its existing branch, 124 commits behind remote main | Dirty AGENTS/artwork/Spirit Shield/icons/script preserved |
| Installed ELITE4, embedded e491c692 | Read and hashed only | Unchanged, QA_PENDING, still targets 0.7.0 |
| Stage 1 c37a1040 | Local engineering checkpoint only | INTEGRATION_BLOCKED |

Eight implementation commits were individually reviewed. Their final source/test
deltas were ported, not merged as whole branches. Dependencies are provenance;
this branch does not falsely claim those source SHAs as ancestors:

- `70b66944da796cc9f7c81538275a51dd21372836`: scoped HRO loss and producer timing.
- `7ebe4207f9db3faba088e757baa8408d21334c11`: initially unbound world alias.
- `9a2c1dcaf3caacf3c03fab7b121c8c7d52ac0050`: conservative stop-time loss attribution.
- `8e9f1ab2632773ccd5b74ac914432181f43563ba`: hook readiness diagnostics.
- `53e9bf0807db4e9ecb00768394b897b8faf0b38d`: native inventory Tab handoff.
- `f52d25fe69e640208a5dc55c82c1e746c1062a60`: gear asset publication off world ticks.
- `e491c692bcf0ea48d210ef5fd378ed4e1a7d5adc`: numeric revision badge.
- `1d7e1ab4a3205d27705885da359af20d01e078d9`: single promoted-name ownership.

The port retains conservative unknown-loss handling, bounded HRO loss details,
native inventory ownership, failed publication retention without retry spin,
and native-name fallback. The new candidate revision is provisional
`R262ME12S1-CANDIDATE`, version `0.2.0-R262ME12S1-CANDIDATE`, Hytale `=0.7.1`.
HyARPG identity/entrypoint and the ImmersiveNPCs boundary are unchanged.
The inherited tracked AGENTS.md is older than current user policy; it was not edited.

## Native artifact manifest

| Artifact | SHA-256 |
| --- | --- |
| Installed stock 0.7.1 server | a60fa0b31f1418691f8d8f7cc1159693e3ec504d4f0567fb91493a842d71f8ca |
| Installed 0.7.1 Assets.zip | b9164eb07d7c42e697d208ba3d35078a9b4a0125bef6261847a2f91e293e53b4 |
| Unchanged installed Hywind mod | ee5a5d9ffc7200e2308798332ee1f7cb34c9068d82dc4cb75b32d07ca0c11639 |
| Offline patched 0.7.1 server | 68f37bdf2dfee6f9834f7035e606a506a2e13dede3c36f51ecae4bf11f425b89 |
| Offline mod built from c37a1040 | 093a2b18180832af0250a1bd1d8eea52de7150700afe8bb37525352cc5fc1210 |
| AD1 input | b862dea2a82310f06f4b61557cd32e0d2000f2ddb5c395d0ebd5031b64405cf7 |
| Four-stage input | fa6ac6cfc63b6a19dc4feb7201ba6919beA89344D4846293094843A915159A9F |
| Unchanged Monster Affix gate | a6926627b6295c9a2c82e3a86068071eb4dd6a19718d04a9016cb0f4bdc1ad91 |

The server manifest is 0.7.1, revision
`69c03ffa58d7e2f46bce8b0356ed206cfc7f10e6`, Java 25.
Installed paths are under
`C:/Users/Zemio/AppData/Roaming/Hytale/install/release/package/game/latest`;
the installed mod is
`C:/Users/Zemio/AppData/Roaming/Hytale/UserData/Saves/Hywind/mods/Hywind.jar`.

All six patch target classes match the previous pinned class bytes exactly.
The offline patch preserves their original instruction shapes, changes only
the six certified entries, and adds eleven hook classes. Unknown whole-JAR
identity, changed class/instruction layout and duplicate patching still fail.
The runtime bridge now requires the exact new patched-JAR hash and co-located
hook classes; missing or substituted hooks still refuse promotion.
Historical hook ABI identifiers retain their original 0.7.0 strings.
**No patched native JAR was installed.**

Build evidence is under `build/native-patch/verification.json`,
`build/guardian/stage1-exact-source.log` and
`build/reports/me12/`. Generated binaries and large audit JSON are excluded
from Git. The final artifact hashes/test summary are in the local
`build/reports/me12/final-manifest.json`.

## Native coverage and safe route proposals

[Complete 309-role matrix](ME12_STAGE1_COVERAGE.md) contains 213 direct-spawn
attackers, 54 conditional attackers and 42 additional legacy catalog IDs.
The union includes all 275 catalog canonical/alias IDs. There are 86 bound
IDs, 85 of them in the native attacker inventory. All 223 remaining union IDs
stay visible as missing-certificate/catalog cases, not silently removed from
the intended-coverage discussion. Legacy catalog-only wildlife, protected
campaign actors and authored bosses do not acquire procedural eligibility.

The regenerable `build/reports/me12/native-coverage.json` records native
role/lineage/action-reference paths and hashes, override differences, declared
movement controllers, existing capabilities, immunity channels, per-era
individual rejections and exact feasible name pairs with witness sets.
The reference inventory is **not** a complete native execution certificate.
Dynamic Compute values, native execution, incoming receipts, inherited effects,
guard reachability and live headroom remain separate proof obligations.

| Role family | Current structural N / Nightmare / Hell name-pair counts | Safe route to investigate; remaining proof |
| --- | --- | --- |
| Scout and aliases; Eye Void; ten ranged Skeleton bindings and aliases; Outlander Berserker/Hunter | 5 / 3 / 0, subject to explicit Normal immunity exclusions | Incoming applied-Health Reflective route; certify real player/NPC/projectile original receipts, deduplication, caps, stale generations and non-recursion |
| Spider / Snapdragon | 19 / 17 / 10 | Existing conditional melee route; preserve one delivery per native conditional leaf and receipt; no duplicate effect through both generic and conditional handlers |
| Firesteel Golem | excluded / 1 / 0 | Its actual Root_NPC_Golem_Firesteel_Attack and seven damage variables need certification; current actionless binding is insufficient |
| Goblin Turret | no certificate in all eras | Projectile_Config_Goblin_Turret_Trash: Physical 8, native Stone_Death, impulse and removal; prove the projectile-config creation/impact/cleanup seam |
| Scarak Seeker | no certificate in all eras | Projectile_Config_Scarak_Seeker_Spitball: Poison 10, Antidote condition, clear/apply Poison_T1, removal; never reinterpret as a Physical single-channel arrow |
| Broad melee / Larva / Trork / CAE bindings | 57 / 57 / 57 structurally, subject to native exclusions | Full role-specific 0.7.1 and roster certification still required; upper budget reachability is not a live admission guarantee |

There are **26 currently production-enabled concrete IDs** with no structural
Hell name plan: 25 in the restricted four-ID pool plus Firesteel. The matrix
also lists two authored campaign golems with no plan; their existing
AUTHORED_CAMPAIGN_ENCOUNTER exclusion is deliberate and unchanged.

For restricted roles, current eligible IDs are ME-002/003/004/017, but ME-003
and ME-004 share SURVIVAL, so four eligible IDs provide at most three legal
affixes. Reflective is a plausible fourth because it depends on the defender's
incoming completed Health loss. Existing consumers are
NativeBasicAttackObserver, NativePlayerGearProjectileReceipts, skill/support
damage owners and NativeEnemyReflectiveReaction. That is source evidence,
not permission to widen an outgoing-action allowlist.

Outlander Berserker's four axe roots are Left, Right, Down and Up_Left.
Hunter's two dagger roots and Bow_Shoot must all be covered. Berserker
inherits HywindEnemyWalk; Hunter inherits native Walk. Controller declarations
alone do not certify movement scaling or slow resistance. Extra Fast and
Unstoppable remain restricted until their actual controllers are proven.
Firesteel's seven native variables include left/right swings, spin, heavy
spin, ground slam, stomp and clap; preserve its native signed knockback.
Turret uses Walk; Seeker uses Fly. Neither gets copied movement certification.

Keep the legacy projectile contract: one original Projectile packet, matching
creator/base damage/frozen receipt, no synthetic elemental vector. The Turret
and Seeker projectile-config graphs are different routes and need their own
certified interception/receipt lifecycle.

Normal exclusions remain explicit for Firesteel, Slug Magma, Toad Rhino Magma,
Burnt Lancer/Soldier/Archer/Gunner/Praetorian, Incandescent Footman/Mage and
their bound aliases: 16 concrete IDs. No immunity or ordinary-spawn policy
was changed. Packbound and sustain immunity limits remain independent.

## Precise structural probabilities and content

The audit uses current Java eligibility and verifies its semantic-group map
against the production selector. It searches for one witness per plan,
with a bounded memoized existence search; it never enumerates all eight-affix
sets, computes sampling masses, draws RNG, or produces a runtime birth plan.
Its output is a **structural upper bound**, not the complete certified
feasibility mask required by Stage 2.

- Fully feasible structural contexts: all nine tier pairs; masses
  4900/1750/350/1750/625/125/350/125/25, Z=10000.
- Restricted Normal: pairs 1/1, 1/2, 2/1; masses 4900/1750/1750, Z=8400.
  Names: Grimtooth the Stubborn, Grimtooth the Watchful, Ironhide the Cruel,
  Ironhide the Stubborn, Gorefang the Stubborn.
- Restricted Nightmare: only 1/1, Z=4900; Grimtooth the Stubborn,
  Ironhide the Cruel, Ironhide the Stubborn.
- Restricted Hell: no feasible pair, Z=0. This is a blocker, never a downgrade.
- Spider/Snapdragon: Normal Z=9975 (all except 3/3), Nightmare Z=9725
  (exclude 2/3, 3/2, 3/3), Hell Z=8400 (1/1, 1/2, 2/1).
- Firesteel Nightmare: only Ironhide the Scarred at 1/1; adding a legal leader
  affix can fill its third slot under the assumed roster. Hell remains empty.

The three new `rpg/enemies/*-v2.json` assets pin AD1's sixteen names, signatures,
filler lists, explicit 27-ID generic pool, 70/25/5 tiers, 100/115/130/150 threat
units and rational reward coefficients. Existing selection weights are
unchanged. Strict offline lint rejects unknown fields/IDs, duplicate JSON
keys/names, zero weights, invalid types, changed AD1 mapping and activation.
Ruinbone ME-003/027 are retained but diagnosed as dead fillers. Duplicate
signature pairs Ashclaw/Ashen, Grimtooth/Cruel and Gorefang/Cruel have zero
feasibility; their catalog entries remain intact.

No production loader reads these assets; EnemyNamePools still loads v1.
Invalid candidate content cannot replace a live snapshot because Stage 1
does not install a v2 snapshot at all. Atomic archive/loader publication and
last-accepted-snapshot recovery are Stage 2 obligations, not completed features.

Use the existing HywindWorldConfiguration owner and save-root world-config.json
for the later optional procedural-Unique mode. Plan DISABLED as the default,
SHADOW as no writes/effects/economics, ENABLED as new natural births only, and
DRAIN as no new v2 births with v2 readers/entitlements retained. Stage 1 makes
no world-config schema or file change. Existing density, pack admission and
eight-member initial cap are unchanged. Read-only inspection of the actual
`hywind-world-r237` config found density **8.0**, hostile/wildlife **65:35**,
12 active special packs/world, three/cell and three-to-five Unique minions.
There are no affix weight overrides or disabled affixes. Its SHA-256 is
`c637d1caa1bb3494187611a624706fb2ee318340c01c109293ccf712c7e827f7`.
Use this 8x configuration for later performance qualification; do not substitute
the historical 4x example.

Optional promotion failure before durable publication preserves the original
ordinary native spawn through the existing reservation/compensation owners.
Missing hooks still disable promotion. After an uncertain durable write,
the existing recovery/quarantine behavior remains authoritative; no new
ordinary-spawn fallback is invented.

## Numeric fixtures for later reward implementation

Using the existing Unique XP factor 2 and quantity factor 1.5:

| B | K0 | XP factor | Quantity factor | Capped expectation | Rounded count |
| ---: | ---: | ---: | ---: | ---: | --- |
| 1 | 0 | 2.30 | 1.725 | 0 | 0 |
| 1 | 1 | 2.30 | 1.725 | 1.725 | 1, plus one with probability .725 |
| 2.5 | 2 | 2.75 | 2.0625 | 4.125 | 4, plus one with probability .125 |
| 5.9 | 5 | 3.77 | 2.8275 | 14.1375 | 14, plus one with probability .1375 |
| 9 | 6 | 4.70 | 3.075 | 16 | 16 |

K0 means successful eligible original base slots before item generation.
This definition is pinned content, not an implemented reward-path change.
For P_r=.0135 and B=1, learning becomes .01485; for P_r=.06 and B=5.9,
the +.03 cap gives .09. Source eligibility, Wisdom, ordinary .95 cap and pity
remain mandatory. Public Skill and first-copy Passive acquisition remain
explicit **uncompleted integration dependencies**.

GearMagicFind, rarity/quality draws, equipment categories, RNG identities,
progression arithmetic, reward delivery and v1 serialization were not changed.
The imported gear publication fix changes native asset scheduling only.
No claim of v2 exactly-once delivery or acquisition is made at this stage.

## Verification, unresolved exceptions and clearing sequence

Committed-source command:
`gradlew check jar testReleaseNativePatch guardianSpotBugs -PguardianSpotBugs=true --continue`.
It completed in 5m42s with **4,317 JUnit tests, eight failures, zero errors
and zero skips**: root 3,774/6 failures; native 489/2 failures; CanvasUI 49/0;
Taverns 5/0. Eight separate Python policy regressions passed.
The complete gate failed; no exact-source success receipt or publication exists.

Native patch regression, seven spawn/flock class pins, compilation, 88 UI
documents, 237 campaign biome keys, 2,040 managed carriers / 4,181 damage nodes,
spatial-grant asset audit and package identity validation passed.
SpotBugs strict tasks exited successfully with zero reported bugs/errors;
their filtered summaries report zero class totals, so this is not presented
as measured analysis coverage or proof of runtime correctness.

| Failing assertion at c37a1040 | Resolution status |
| --- | --- |
| EnemyNativeAssetRoutingTest.packagedAssetsDifferOnlyAtTheFivePinnedNativeAdapterSeams | Predator native template changed; unresolved |
| ProductionEliteArchetypeAssetTest.reportedSkeletonFlockMembersHaveExactCertifiedNativeRoutes | Skeleton source pins changed; unresolved |
| ProductionEliteArchetypeAssetTest.certifiedNativeMeleeGraphAndOnlyExistingLeafAdapterDelta | Rat native source/Health/UI changed; unresolved |
| EnemyNativeProjectilePathTest.installedScoutKeepsItsOriginalSingleArrowWithoutNativeDisplacement | Scout source pin changed; unresolved |
| DifficultyCombatTest.installedRoleHashesAndHealthMatchEveryAuthoredBaseline | Native profile source differences; unresolved |
| Stage12NativeBiomeAssetTest.exactInstalledAssetsMatchRegistryPins | Goblin/native catalog reference pins changed; unresolved |
| Stage13VanillaPowerExpansionTest.everyExpandedEntryResolvesExactTagsAndMatchesInstalledLeafOrExplicitReferencePolicy | Scrap Flamethrower native damage 4 versus configured 6; unresolved |
| CycloneArmorTest.entityBoundBurstAssetUsesOneFiniteEmitter | Corrected moved native source path; appearance/physics comparison unchanged; focused class now passes |

The Windstrike asset moved from Server/Particles/Combat/Abilities to
Server/Particles/Abilities. The fixture-only correction retains its full
appearance/physics equality assertion and changes no production particle.
Thus **seven known assertions remain unresolved** after this narrow follow-up.
The follow-up does not convert the failed cumulative run into a pass.
Initial fixture-path failures and six archived-reader dependency failures were
resolved by targeting the installed release assets/server, with assertions
and archived-reader contents retained. A filtered follow-up accidentally
selected subproject test tasks with root-only filters; its no-tests-found
errors are invocation errors, not product failures. The subsequent unfiltered
committed-source run above ran all four suites.

Successful build/package checks do not override failing native assertions.
No checks were excluded or pins relaxed to manufacture a pass.

Remaining compatibility work includes 21 stale bound-role source pins,
the predator template's new Ignore attitude, Skeleton DisableDamageGroups,
native Rat Health/UI changes, native Flamethrower power changing from 6 to 4,
and the legacy progression registry's native role hashes. Each needs an
explicit native-versus-Hywind ownership decision and targeted regression proof,
not a whole-file replacement or global hash refresh. Preserve current
progression and equipment-quality contracts while reconciling those differences.

Clearing sequence:
1. Reconcile each source/asset discrepancy, maintaining explicit authored
   Hywind values where preservation is required; prove no unrelated balance
   or presentation drift. Correct renamed fixture references independently.
2. Complete role-specific native certificates, especially the 26 blocked
   production IDs and the missing Turret/Seeker routes. Prove full
   name/roster/immunity feasibility for every intended non-excluded role/era.
3. Obtain a clean cumulative automated result and exact-source Guardian receipt.
4. Only then present an exact paired mod/native candidate for separate
   deployment authorization and permitted connected QA in the main Hywind save.
   Stage 1 has no installation authorization. No alternate server/save.
5. Record affirmative owner acceptance of the relevant Elite/HRO/native
   behavior. Do not infer it from inclusion, logs or offline tests.

No new approval is needed to retain the already approved AD1 names, tier
weights, threat values, reward bounds or exclusions. Any proposal to adopt
native balance changes at the expense of preserved Hywind progression needs
owner approval; preservation remains the current instruction. Actual
deployment and connected acceptance require separate explicit owner actions.
Skill/Passive source implementation and any broader feature expansion remain
outside this stage. Stage 2 requires its own authorization.

Before v2 writes exist, rollback position is unchanged: this candidate has
never touched the installed mod, native server, configuration or save.
After future v2 writes, rollback must use the v2-capable reader and DRAIN,
not an old JAR. No persistence migration was attempted.
