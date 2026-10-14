# Master Enemies v1.2 Stage 1 remediation handoff

**Stage 1 remains BLOCKED by offline coverage/certification gaps.** The seven known compatibility assertions and all 26 enabled-role Hell minimum failures have focused passing evidence. This is not STAGE1_OFFLINE_READY: broader intended-role prerequisites are incomplete. Connected evidence is independently **BLOCKED / DEFERRED_TO_STAGE4** by owner instruction. Monster Affix remains OPEN, ownerAccepted=false; Elite/HRO remain QA_PENDING. Stage 2 has not begun. No intermediate deployment or gameplay test is requested.

## Source and verification records

Branch `codex/me12-stage1`, worktree `C:/Users/Zemio/.codex/worktrees/me12-stage1/Hytale-main-clean`. Remediation implementation checkpoint: **a83e9ef548932ebbe47a9bdc10a4bf4d3f2e7471**. Its documentation/build-identity successor uses provisional **R262ME12S1R1-CANDIDATE**, version `0.2.0-R262ME12S1R1-CANDIDATE`, Hytale `=0.7.1`. No completed or accepted cumulative R-number is claimed.

The final cumulative command is `gradlew --offline --no-daemon check jar testReleaseNativePatch guardianSpotBugs -PguardianSpotBugs=true --continue --console=plain`. Unmodified output: `build/guardian/me12-remediation-final.log`. Exact tested HEAD, counts, hashes, gate axes and synchronization are recorded after execution in `build/reports/me12/remediation-final-manifest.json`; Guardian independently verifies `build/guardian/verification.json`. Generated evidence remains outside Git. This tracked report records focused evidence and the required cumulative gate; it does not predeclare the future result. Publication requires exact-HEAD cumulative and Guardian success.

| Source | Exact identity / disposition | Acceptance |
| --- | --- | --- |
| Integrated parent / observed remote main | b839526e7106f780648c315692b04bb372b58d9e | Latest verified integrated source; no cumulatively QA-accepted 0.7.1 baseline established |
| HRO dependency | f7d414e6348c84653d03a51117bdfe2fc427dded | Reviewed inclusion, QA_PENDING |
| Elite dependency | 3327a5650cadf0173b336072f614a13c63cecc56 | Reviewed selective inclusion, QA_PENDING |
| Observed Elite remote | 1ff1f06989493135e62b2866393673185ab5083d | Local Elite four commits newer; preserved |
| Primary dirty main | 1dfa2cea3085196c7fd4492518c65a03d5e3b49d | 124 behind observed remote main; AGENTS/artwork/Spirit Shield/icons/script preserved |
| Active installed mod | R262C1ELITE4-CANDIDATE, embedded e491c692 | Unchanged, QA_PENDING, targets 0.7.0 |

No whole feature branch was merged. Individually reviewed implementation ports (provenance, not a claim that every source SHA is an ancestor):

- `70b66944da796cc9f7c81538275a51dd21372836`: scoped HRO loss and producer timing.
- `7ebe4207f9db3faba088e757baa8408d21334c11`: initially unbound world alias.
- `9a2c1dcaf3caacf3c03fab7b121c8c7d52ac0050`: conservative stop-time loss attribution.
- `8e9f1ab2632773ccd5b74ac914432181f43563ba`: hook readiness diagnostics.
- `53e9bf0807db4e9ecb00768394b897b8faf0b38d`: native inventory Tab handoff.
- `f52d25fe69e640208a5dc55c82c1e746c1062a60`: gear asset publication off world ticks.
- `e491c692bcf0ea48d210ef5fd378ed4e1a7d5adc`: numeric revision badge.
- `1d7e1ab4a3205d27705885da359af20d01e078d9`: single promoted-name ownership.

The inherited AGENTS.md differs from current user policy and remains untouched. Current user policy governs. HyARPG identity, entrypoint and the separate ImmersiveNPCs boundary are unchanged. No game/server/authentication launch, installation, deployment, save edit, main merge or acceptance occurred.

## Seven regression corrections

| Exact failing test | Root cause and narrow correction |
| --- | --- |
| EnemyNativeAssetRoutingTest.packagedAssetsDifferOnlyAtTheFivePinnedNativeAdapterSeams | Actual 0.7.1 Predator sensor added Ignore attitude. Reconcile that native change; preserve Hywind walk/attack seams and the original full-JSON equality assertion after undoing only certified adapters. |
| ProductionEliteArchetypeAssetTest.reportedSkeletonFlockMembersHaveExactCertifiedNativeRoutes | Native Skeleton bytes changed, including DisableDamageGroups Skeleton. Reconcile ten copied melee roles, preserving existing damage adapters. Separate current source pins from frozen historical reward catalog pins. |
| ProductionEliteArchetypeAssetTest.certifiedNativeMeleeGraphAndOnlyExistingLeafAdapterDelta | Rat stock Health became 10 and stock Healthbar is hidden. Copied native role matches stock apart from its prior damage adapter. Historical Hywind progression Health remains 15; reward progression is not rebased to native 10. |
| EnemyNativeProjectilePathTest.installedScoutKeepsItsOriginalSingleArrowWithoutNativeDisplacement | Stale Scout role hash. Pin actual 0.7.1 source; retain one original arrow, path equality and native displacement assertions. |
| DifficultyCombatTest.installedRoleHashesAndHealthMatchEveryAuthoredBaseline | Authored native reference bytes changed. Explicit current hashes are tested independently of frozen gameplay catalog values. Every authored native Health equality is still asserted. |
| Stage12NativeBiomeAssetTest.exactInstalledAssetsMatchRegistryPins | Stale source audit and five retired role IDs. Assert the exact missing set: Bear_Voidtaken_D1/D2/D3, Goblin_Feastmaster, Coffer_Goblin_Feastmaster. Preserve historical records; no remapping. A deprecated Feastmaster spawn marker is not a role asset. |
| Stage13VanillaPowerExpansionTest.everyExpandedEntryResolvesExactTagsAndMatchesInstalledLeafOrExplicitReferencePolicy | Native Scrap Flamethrower burn tick changed 6 to 4. Preserve RPG selected power 6; assert native 4 and exact item/path/property/SHA independently. Other entries retain prior assertions; production power table unchanged. |

All seven pass focused runs. No existing failing test was disabled, removed or moved to another suite. Three new native Health/control fixtures run in the established nativeControlTest JVM because they need native logger/stat setup. Four new ordinary fixtures check all 86 source pins, unchanged historical policy bytes, v1 binding wire identity and explicit unsupported modern projectile routes. An initial new-fixture registration error and new-test missing brace were corrected before the cumulative gate.

Historical c37a1040 evidence was 4,317 tests / eight failures; the prior Windstrike fixture-path correction removed one. That historical run is not current passing evidence. Focused remediation logs: `build/me12-remediation-focused-final.log` and `build/me12-compat-final.log`. Final cumulative counts belong to the exact-HEAD manifest above. The first remediation cumulative at 57ddc525 ran 4,324 tests (3,778 ordinary, 492 native, 49 CanvasUI, 5 Taverns), with two failures and zero errors/skips: existing generated ELITE_ROLE_MATRIX parity and the previous candidate-string assertion in BuildIdentityTest. Regenerate the former through its existing generator and update only the explicit candidate string in the latter; all test assertions remain. A subsequent exact-commit cumulative run is mandatory. The failed run is preserved as build/guardian/me12-remediation-first-cumulative.log.

## Enabled-role remediation and complete static feasibility

The restricted pool ME-002/003/004/017 had at most three compatible IDs because ME-003 and ME-004 share SURVIVAL. One outgoing-action supported-ID intersection also unnecessarily governed an independent incoming defender route. Add a separate immutable exact role/path/SHA certificate for **ME-026 Reflective on 25 concrete roles/aliases**, and **ME-017 Unwavering on Firesteel**. Changed source identity fails closed; resources validate at binding setup. No outgoing action allowlist or capability flag was expanded.

The installed reflection producer is `SupportDamageSystems.BeforeApply` / `.Reflect`: original direct native damage, Health before/after, shared provenance/protection, then `NativeEnemyReflectiveReaction.acceptPlayer` and later native Gather. `HyArpgPlugin` configures this through `sharedReflection.configureEnemyReaction`. Historical optional basic/projectile/skill callbacks are not installed as extra producers. Unwavering uses `EnemyAffixSnapshot` -> `HytaleDifficultyCombat.statusImmune` -> shared status admission. It blocks STUN/STAGGER, not ROOT. Firesteel's seven original damage variables, Force -10, fire immunity and attack graph remain untouched; no outgoing Firesteel action is newly certified.

Offline fixtures use the installed EntityStatMap with measured 100 -> 93 Health loss, verify 0.7 reflection, no direct Health mutation by the reaction, and zero-loss/indirect/stale rejection. Existing reflection/status/protection regressions remain required. These native-class fixtures establish offline contracts only; actual connected receipt delivery, native effects/protection and natural spawning remain Stage 4 observations.

The [309-role matrix](ME12_STAGE1_COVERAGE.md) is regenerated from Java binding, immunity and inheritance owners and checked by Gradle. Name pairs include locked signatures, filler/difficulty slots, distinct IDs, semantic conflicts, native immunity, guard and inherited-effect feasibility. Minimum witnesses also call whole-set immunity. Proof is conditional on a homogeneous **admitted, reachable** native roster for every base count 3/4/5; Horde produces 6/7/8 total members. Live capacity/reachability is not inferred. No weighted sampler or exhaustive eight-affix-set enumeration was introduced.

| Enabled roster | Normal | Nightmare | Hell |
| --- | ---: | ---: | ---: |
| Complete static minimum plan | 68 | 84 | 84 |
| Existing native-immunity exclusion | 16 | 0 | 0 |
| Enabled role missing minimum plan | 0 | 0 | 0 |

Restricted roles now have 14/8/5 name pairs (unless Normal-excluded), maximum own count 4. Firesteel is Normal-excluded and has 2 Nightmare / 2 Hell pairs; Hell witness: Ironhide + Stubborn, ME-004/017/022/026. Spider/Snapdragon retain 19/17/10 pairs and maximum 5; broad roles retain 57 pairs and Hell maximum 8. Restricted Hell only supports tier I/I: conditional mass 4900/4900. This conditions unchanged 70/25/5 weights, not a downgrade/retry rule. Restricted Normal/Nightmare normalizers are 9725/8400. AD1 names/fillers/operator weights are unchanged.

Every repaired ID follows. X is the unchanged Normal immunity exclusion. All rows are connected-unverified. Original action IDs identify native attack roots; bracketed values identify the original strike/leaf; the detailed native graph paths and hashes remain in the regenerable inventory.

| Role | Original action / strike | Added incoming route | N / NM / H pairs |
| --- | --- | --- | --- |
| Eye_Void | Eye_Void_Surge_Blast [projectile] | ME-026 | 14 / 8 / 5 |
| Golem_Firesteel | Root_NPC_Golem_Firesteel_Attack; seven original damage variables | ME-017 | X / 2 / 2 |
| Outlander_Berserker | Outlander_Berserker_Axe_Swing_Left [Melee_Damage]; Outlander_Berserker_Axe_Swing_Right [Melee_Damage]; Outlander_Berserker_Axe_Swing_Down [Melee_Damage]; Outlander_Berserker_Axe_Swing_Up_Left [Melee_Damage] | ME-026 | 14 / 8 / 5 |
| Outlander_Hunter | Outlander_Hunter_Daggers_Swing_Left_Right [Melee_Damage]; Outlander_Hunter_Daggers_Swing_Right_Left [Melee_Damage]; Outlander_Hunter_Bow_Shoot [projectile] | ME-026 | 14 / 8 / 5 |
| Skeleton_Archer | Skeleton_Archer_Bow_Shoot [projectile] | ME-026 | 14 / 8 / 5 |
| Skeleton_Archer_Wander | Skeleton_Archer_Bow_Shoot [projectile] | ME-026 | 14 / 8 / 5 |
| Skeleton_Burnt_Archer | Skeleton_Burnt_Archer_Bow_Shoot [projectile] | ME-026 | X / 8 / 5 |
| Skeleton_Burnt_Archer_Wander | Skeleton_Burnt_Archer_Bow_Shoot [projectile] | ME-026 | X / 8 / 5 |
| Skeleton_Burnt_Gunner | Skeleton_Burnt_Gunner_Gun_Shoot [projectile] | ME-026 | X / 8 / 5 |
| Skeleton_Frost_Archer | Skeleton_Frost_Archer_Bow_Shoot [projectile] | ME-026 | 14 / 8 / 5 |
| Skeleton_Frost_Archer_Patrol | Skeleton_Frost_Archer_Bow_Shoot [projectile] | ME-026 | 14 / 8 / 5 |
| Skeleton_Frost_Archer_Wander | Skeleton_Frost_Archer_Bow_Shoot [projectile] | ME-026 | 14 / 8 / 5 |
| Skeleton_Frost_Mage | Skeleton_Sand_Mage_Spellbook_Corruption_Orb [projectile] | ME-026 | 14 / 8 / 5 |
| Skeleton_Frost_Mage_Wander | Skeleton_Sand_Mage_Spellbook_Corruption_Orb [projectile] | ME-026 | 14 / 8 / 5 |
| Skeleton_Frost_Ranger | Skeleton_Frost_Ranger_Bow_Shoot [projectile] | ME-026 | 14 / 8 / 5 |
| Skeleton_Frost_Ranger_Patrol | Skeleton_Frost_Ranger_Bow_Shoot [projectile] | ME-026 | 14 / 8 / 5 |
| Skeleton_Frost_Ranger_Wander | Skeleton_Frost_Ranger_Bow_Shoot [projectile] | ME-026 | 14 / 8 / 5 |
| Skeleton_Incandescent_Mage | Skeleton_Sand_Mage_Spellbook_Corruption_Orb [projectile] | ME-026 | X / 8 / 5 |
| Skeleton_Incandescent_Mage_Wander | Skeleton_Sand_Mage_Spellbook_Corruption_Orb [projectile] | ME-026 | X / 8 / 5 |
| Skeleton_Mage | Skeleton_Mage_Wand_Corruption_Orb [projectile] | ME-026 | 14 / 8 / 5 |
| Skeleton_Mage_Wander | Skeleton_Mage_Wand_Corruption_Orb [projectile] | ME-026 | 14 / 8 / 5 |
| Skeleton_Sand_Mage | Skeleton_Sand_Mage_Spellbook_Corruption_Orb [projectile] | ME-026 | 14 / 8 / 5 |
| Skeleton_Sand_Mage_Wander | Skeleton_Sand_Mage_Spellbook_Corruption_Orb [projectile] | ME-026 | 14 / 8 / 5 |
| Skeleton_Scout | Skeleton_Scout_Bow_Shoot [arrow] | ME-026 | 14 / 8 / 5 |
| Skeleton_Scout_Patrol | Skeleton_Scout_Bow_Shoot [arrow] | ME-026 | 14 / 8 / 5 |
| Skeleton_Scout_Wander | Skeleton_Scout_Bow_Shoot [arrow] | ME-026 | 14 / 8 / 5 |

## Remaining offline blockers, separate from connected QA

Of 309 union IDs (275 historical catalog IDs, 267 native attackers), 86 have bindings and 84 are enabled; two bound authored campaign roles retain exclusion. The 223 unbound entries are **186 without a certificate** (107 direct native attackers, 38 conditional attackers, 41 catalog-only entries), **34 without a catalog mapping** (24 direct, 10 conditional), and **three explicitly excluded authored campaign actors**. Thus **179 non-excluded native attacker IDs** still lack a production binding/catalog mapping. These are implementation/certification gaps, not implicit exclusions or merely deferred connected tests. Catalog-only entries require scope reconciliation; retired IDs retain historical compatibility.

Goblin Turret and Scarak Seeker use modern `Type: Projectile` / ProjectileConfig creation and impact graphs. `NativeBasicAttackPaths` and existing attachment cover legacy LaunchProjectileInteraction / ProjectileComponent paths; they do not establish modern creation, ownership, impact, removal and recovery. Turret trash preserves Physical 8, Stone_Death, native impulse and removal. Seeker's effective Spit override preserves Poison 10, Antidote Match None, clear/apply Poison_T1 and removal. Inherited Eye Void references do not certify Seeker. New negative fixtures explicitly retain both as unsupported.

Clearing these gaps requires a narrow extension of existing projectile/action owners, positive/negative native-class fixtures, exact route/source pins and recalculated complete feasibility. Other missing rows require concrete route/catalog reconciliation, not mass promotion. This is unfinished offline engineering; deferring connected QA does not clear it. Full B03/B04/B06 and accepted-baseline integration remain BLOCKED.

Connected-only obligations include actual native hooks with the exact paired artifacts, original natural spawning/admission, combat/conditional/defender delivery, UI/name behavior and Elite/HRO acceptance. These are **DEFERRED_TO_STAGE4**; nativeConnectedPassed=false and ownerAccepted=false throughout. Installed stock missing-hook guards are not bypassed. No intermediate user test or deployment is requested.

## Patch, policy and legacy evidence

| Artifact | SHA-256 |
| --- | --- |
| Stock 0.7.1 server | a60fa0b31f1418691f8d8f7cc1159693e3ec504d4f0567fb91493a842d71f8ca |
| 0.7.1 Assets.zip | b9164eb07d7c42e697d208ba3d35078a9b4a0125bef6261847a2f91e293e53b4 |
| Unchanged installed Hywind mod | ee5a5d9ffc7200e2308798332ee1f7cb34c9068d82dc4cb75b32d07ca0c11639 |
| Offline patched server | 68f37bdf2dfee6f9834f7035e606a506a2e13dede3c36f51ecae4bf11f425b89 |
| Unchanged Monster Affix gate | a6926627b6295c9a2c82e3a86068071eb4dd6a19718d04a9016cb0f4bdc1ad91 |

Server revision: `69c03ffa58d7e2f46bce8b0356ed206cfc7f10e6`, Java 25. Six patch targets retain exactly the previously audited original bytes; the offline patch changes only those entries and adds eleven hook classes. Regression preserves instruction/archive identity, unknown-input and duplicate-patch rejection. The bridge requires exact patched-server hash and co-located hooks. No patch was installed.

Inactive v2 name/selection/reward assets declare DISABLED and are not runtime-loaded. Eight policy tests and strict lint enforce AD1 mappings, 70/25/5 conditioning, four-band threat, bounded XP, expected equipment cap 16 before rounding, unchanged quality/MF ownership, +.03 learning bound and .95 ordinary cap. Public Skill and first-copy Passive acquisition remain incomplete integration dependencies. No v2 reader/writer/sampler/reward implementation or save migration exists yet.

Six historical gameplay policies are hash-checked unchanged (canonical Git LF): progression, authored encounters, native skill power, v1 balance/affixes/names. The historical binding revision string is retained so v1 saved actors still resolve; current source hashes are separate evidence. Unknown wire revisions reject. No claim of completed v2 persistence or exactly-once rewards is made.

## Gates and next action

The [four-axis ledger](ME12_STAGE1_GATES.json) keeps normative gates OPEN. Passing enabled-roster checks do not pass all-intended-role B04. The final generated manifest adds exact cumulative/Guardian evidence without rewriting acceptance. The [Stage 3 trace contract](ME12_STAGE3_TRACE_CONTRACT.md) identifies owners, evidence, five outcome meanings and the future COMMANDS.md update; it implements no command.

Publish reviewed commits only to the matching codex branch through Guardian after exact-HEAD cumulative success. No main integration while Elite/HRO/native dependencies remain unaccepted. Future integration must reconcile accepted ancestry and selective ports, retest affected gates and serialize integration. Primary dirty main stays protected.

Recommendation: Stage 2 pure offline models/archive/sampler work may be separately authorized on this exact isolated dependent baseline while native blockers stay explicit. Do not claim full Stage 1 readiness or enable natural v2 births. Complete missing certification before release qualification; reserve connected QA/acceptance for Stage 4. No approved product-rule change is needed for these fixes. Stage 2 authorization, eventual exact-candidate deployment and final acceptance remain owner decisions.
