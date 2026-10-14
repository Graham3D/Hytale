# ME12 Stage 1 coverage reconciliation — 2026-10-14

**Stage 1 remains BLOCKED.** This pass completes the 309-ID evidence reconciliation and fixes an incomplete native action certificate check. It does not complete the outstanding native combat implementations, determine new product scope, or certify connected execution. The previous 4,324-test baseline and fixes are retained. No eligibility, outgoing-affix permission, native asset, progression, save or installed binary was changed. Monster Affix stays OPEN, ownerAccepted=false. Elite/HRO remain QA_PENDING. Stage 2 has not started.

## Source and preservation

Continue `codex/me12-stage1` in `C:/Users/Zemio/.codex/worktrees/me12-stage1/Hytale-main-clean` from Guardian-published `b7b952ba02e9f49265f15ad4adac6df17a2b70c8`. Reconciliation implementation checkpoint: `2d021f9c727c6de039c26e87fc32488d6f67f53b`. The documentation/build successor is provisional `R262ME12S1R2-CANDIDATE`, version `0.2.0-R262ME12S1R2-CANDIDATE`, targeting Hytale `=0.7.1`. The exact tested/published successor SHA is in `build/reports/me12/reconciliation-final-manifest.json` and Guardian's exact-HEAD receipt. A provisional candidate is not an accepted cumulative revision.

Fetched remote main remains `b839526e7106f780648c315692b04bb372b58d9e`; it is the integrated parent, not an established QA-accepted 0.7.1 cumulative build. Main was six commits behind the starting candidate. All other local branch tips are reachable from remote main except ME12, Elite `3327a5650cadf0173b336072f614a13c63cecc56` and HRO `f7d414e6348c84653d03a51117bdfe2fc427dded`. The latter two remain preserved and QA_PENDING. Their eight reviewed implementation ports are recorded in [the preceding report](ME12_STAGE1_REPORT.md); this pass preserves every file from b7 except its listed narrow changes. No accepted branch-specific work was found abandoned by this continuation. Ancestry and selective-port provenance remain distinct; Elite/HRO tips are not claimed as ME12 ancestors.

Primary main remains `1dfa2cea3085196c7fd4492518c65a03d5e3b49d`, 124 commits behind remote main, with its AGENTS/artwork/Spirit Shield/icon/script changes preserved. No new worktree, branch merge, policy edit to AGENTS, deployment, game/server/auth launch or save mutation occurred. The installed mod remains R262C1ELITE4, embedded e491c692, targeting 0.7.0; no accepted installed-pair agreement is inferred.

## Complete reconciliation and its limits

Use the [309-row matrix](ME12_STAGE1_RECONCILIATION.md) and [309-row evidence CSV](ME12_STAGE1_RECONCILIATION.csv). Each row includes catalog canonical/aliases, native Reference ancestry, exact native path/hash, spawn paths/pointers, referring owners/pointers, static combat roots/channels, effects, movement, flock data, unresolved expressions, existing rejection, N/NM/H plans and missing requirements. Full graph/source hashes are reproducible in `build/reports/me12/reconciliation.json` with `tools/audit/me12_reconcile.py`. Gradle checks generated parity.

The audit union is **275 historical catalog IDs + 267 native attacker inventory IDs = 309 distinct IDs**, not 309 approved procedural monsters. All **304 present role assets are concrete native Variant specializations**; none of these IDs is itself an abstract/template/component asset. Some concrete variants participate in encounters, summon chains or phase changes; those are separate provenance questions. Five IDs have no installed role asset. Merely inheriting an abstract template does not make a concrete role abstract.

| Disjoint accounting | IDs | Interpretation |
| --- | ---: | --- |
| Existing configured production roles | 84 | 83 overlap native attacker inventory; Fox is catalog-only. Configuration is not execution certification. |
| Existing authored campaign exclusions | 5 | Unchanged policy; two have bindings, three do not. No new exclusions. |
| Uncertified/unmapped native inventory | 179 | 145 mapped IDs plus 34 unmapped IDs; not automatically 179 new implementation obligations. |
| Uncertified catalog-only entries | 41 | 36 present native roles plus five retired IDs; provenance/category review below. |

The 179 split into six mapped AD1 priority-family entries, 88 mapped hostile roles with marker definitions, 40 mapped conditional/flock/provenance candidates, 11 behavior/encounter-review entries and 34 unmapped IDs. These labels are evidence buckets, not changes to promotion. In particular, a missing default attitude on the newer Goblin templates is **unknown**, not a neutral/noncombatant verdict.

The 41 split into:

- **Five retired IDs:** Bear_Voidtaken_D1/D2/D3, Goblin_Feastmaster, Coffer_Goblin_Feastmaster. Preserve historical reader/catalog identities. Bear_Voidtaken and the new Glutton/Feastmaster encounter variants are possible semantic successors, not safe automatic aliases; stats, phases and damage routes differ.
- **14 prior WILDLIFE entries:** Antelope, Bison, Boar, Camel, Cow, Deer_Stag, Goat, Horse, Horse_Skeleton, Moose_Bull, Mosshorn, Mosshorn_Plain, Ram, Warthog. They have world selectors and retaliation/combat graphs. Neither fact authorizes a new procedural hostile population. WILDLIFE is existing population metadata, not a newly imposed promotion exclusion.
- **17 other neutral/provenance cases:** five Feran roles; Kweebec_Razorleaf and Patrol; Scarak_Broodmother/Defender/Defender_Patrol/Louse and their four Dungeon counterparts; Horse_Skeleton_Armored; Moose_Cow. They are concrete and possess combat routes, but hostile procedural provenance is not established by their catalog membership.
- **Five summon/encounter cases:** Risen_Gunner/Knight (Ignore player attitude), Skeleton_Elite_Phase_2, Void_Tentacle/Surge. Preserve identities; establish the creator/phase owner before any procedural promotion decision.

The 34 unmapped IDs include natural-world candidates **Chicken_Undead** and **Skeleton_Incandescent_Head**, plus dungeon Goblin variants, six Gobliterator phase variants, Feastmaster components, Bear_Voidtaken, Hedera, Skeleton, Spider_Cave_Spiderling, Void_Spawn and Void_Spectre_Static. All are listed individually. Chicken/Head have exact world selectors and hostile attack evidence; prioritize catalog reconciliation without treating the entire 34 as newly approved. Dungeon_Goblin_Thief/Patrol have no resolved combat root: the old broad attacker heuristic is not proof of an attack. Boss-directory and creator references are review evidence, not new runtime exclusions.

**Spawn evidence boundary:** World selectors, placed Markers and Beacons are distinct. The current `NativeEnemySpawnGroups` owner intercepts world spawn jobs. Marker/beacon definitions do not prove execution through that owner. Raw role references are recorded as inheritance or reference-only; they do not prove natural spawning. Flock/encounter creators and dynamic selection need route-specific analysis. None of this pass observes a live spawn.

**Combat evidence boundary:** the offline resolver applies simple parameter substitutions, concrete overrides and literal disabled-node pruning, follows components, role interaction variables, graph assets and held-item roots, and retains unresolved native expressions. It is a conservative graph inventory, not a replacement native role builder. It must not be used to issue runtime certificates or derive probabilities. In particular it resolves Seeker's Spit override rather than accidentally certifying the inherited Eye attack, and includes Burner's held flamethrower Primary rather than only its reload/death roots. Native conditional state, actual equipment slot, full expression semantics and effect timing remain uncertified.

## Source correction

`NativeBasicAttackPaths` previously counted DamageEntity and legacy LaunchProjectile operations, but omitted modern ProjectileInteraction. A mixed legacy-plus-modern graph could therefore pass `NativeEnemyAction.Binding.validate` with only its legacy strike bound. The corrected regression first validates the legacy-only graph with actual native asset-store fixtures, then proves the mixed graph must reject. Before the production change it failed because **no exception was thrown**. Afterward it passes with `ENEMY_ACTION_CONFIG_PROJECTILE_NOT_CERTIFIED`.

The collector now counts modern operations, retains repeated occurrences and treats them as a subsequent-damage boundary. Binding validation rejects an unowned config projectile before accepting the action. Modern operations receive neither a legacy receipt nor ordinary basic-attack classification. No producer, damage submission, projectile, affinity or immunity rule was added or broadened. The original native graph and ordinary spawn remain untouched. Existing legacy-only fixtures pass.

Goblin_Scrapper also has an auxiliary modern brawl-taunt projectile. This is not evidence that its certified player-facing club action uses a modern projectile, and is not authorization to turn NPC brawling into affix-bearing attacks. Its auxiliary route is separately flagged. The same applies to Coffer/Voidtaken Scrapper-derived variants.

## Shared remaining engineering blockers

Counts below overlap and describe static route candidates, not a requested scope expansion.

| Group | Evidence / offline work remaining | Connected obligation |
| --- | --- | --- |
| Modern config projectile owner | 15 unbound IDs share modern route candidates: three Turret variants, four Seeker variants, six Lobber variants, two Scrapper/brawl variants. First implement/test the two confirmed priority natural roles, then assess aliases individually. | Real creation/impact/miss/removal/reload ordering, protection and cleanup; Stage 4. |
| Existing enabled Firesteel attachment | Static X/2/2 name pairs, but empty action certificate causes production `NativeEnemyActionAttachment.prepareWithRoots` to throw ENEMY_ACTION_GROUP_NO_CERTIFIED_STRIKES. Review its seven native variables and damage/status/Force paths; implement a safe certificate or a deliberately reviewed incoming-only attachment contract. Do not remove the guard or widen outgoing permissions. | Original Firesteel behavior and accepted incoming affixes; Stage 4. |
| Legacy projectile families | 67 unbound rows reference legacy launches. Reuse the established receipt owner only after exact role/root/projectile/source proof; verify multi-strike, native status and conditional branches individually. | Actual owned hits, misses and persistence; Stage 4. |
| Melee/config damage | 148 unbound rows reach DamageEntity candidates. Group by native template/root and interaction variables; prove contact multiplicity, effect ownership, native Health and protection. Some are config impacts, not melee. | Actual combat state and original attack behavior; Stage 4. |
| Held-item attacks | 14 unbound rows contain a possible item-driven attack; four Burner variants have concrete flamethrower Primary roots. Resolve actual equipped-slot variables and native burns/overheat; retain historical RPG power separately. | Actual item-driven attack/effect timing; Stage 4. |
| Conditional effects | 150 unbound rows contain ApplyEffect/EffectCondition candidates, including harmless visual/protection effects. Inspect effect identity and ownership before classifying as damage/status. Never count them all as proc permissions. | Original immunity, status, death/expiry and protection behavior; Stage 4. |
| Catalog / spawn provenance | 34 missing mappings; 197 unbound present roles have no direct World selector. Resolve definitions/creator routes offline; settle only genuinely new product population questions with owner. | Actual natural admission and roster reachability; Stage 4. |

**Turret/Seeker implementation boundary:** `ProjectileInteraction.firstRun` calls `ProjectileModule.spawnProjectile`, attaches ImpactModifiers/ProjectileLaunchContext through the command buffer and emits ProjectileLaunchEvent. The existing patch targets the legacy LaunchProjectileInteraction route. Its legacy `ProjectileComponent` receipt requires DamageCause.PROJECTILE and the original scalar packet; it cannot certify modern Physical or Poison config impact graphs by relabeling their channel.

Extend the existing action/receipt owner with immutable accepted-root/strike/config/creator/world-generation correlation at modern creation. Carry it on the native projectile entity; adapt only the original native impact damage, preserving RNG, cause, effect conditions, collision semantics and one strike/victim claim. Verify original miss/expiry/removal and persistence/recovery, stale generations, duplicate callbacks and cancellation before adding any exact-role certificate. Do not infer root ownership from launch-event proximity or create another damage pipeline.

Turret must retain Physical 8, Stone_Death, native impulse and RemoveEntity. Seeker must retain Poison 10 with native variance, Antidote Match None, ClearEntityEffect then Poison_T1, independent projectile removal, flight and environmental/Poison immunity. Native API inspection and existing source fixtures verify these paths **offline**; they do not execute firstRun or native collisions. This shared implementation and native-class/fault fixtures can be developed offline. Live-world ordering and gameplay acceptance genuinely require Stage 4. They are two different blockers, and neither has been marked passed.

## Static plans and gate ledger

No affix pool, signature, weight, policy exclusion, tier weight or reward policy changed. Existing configured roster retains **68 Normal / 84 Nightmare / 84 Hell** complete static minimum plans and 16 prior Normal immunity exclusions. These are conditional on admitted reachable homogeneous minion rosters of base size 3/4/5 and the initial eight-member cap. Restricted Scout remains 14/8/5 feasible name pairs; restricted Hell conditions the unchanged tier weights onto the legal tier-I pair. No downgrade or invented fourth outgoing capability.

All 220 unbound non-authored entries display **?**, meaning no certified plan input, not zero feasible plans and not permission to remove the role. Every configured static witness also retains the independent native-execution boundary; Firesteel demonstrates why this distinction matters.

The [ledger](ME12_STAGE1_GATES.json) leaves B01–B06, N03 and N08 normative status BLOCKED, releaseStatus OPEN and all nativeConnectedPassed/ownerAccepted false. B02/B05/N08 retain applicable offline evidence; B03/B04/B06 remain incomplete offline as well. Public Skill and first-copy Passive acquisition remain unresolved Stage 3 dependencies. No sampler, v2 persistence, economics or release gate activation was introduced.

## Stage 2 dependency handoff and next action

[Pinned offline inputs](ME12_STAGE2_INPUT_SNAPSHOT.json) identify this implementation checkpoint, the installed server/assets pair, normalized policy/source hashes, unchanged historical binding wire ID and the regenerated capability matrix. The three inactive AD1 policies plus v1 legality owners are suitable **only for separately authorized isolated algorithm/codec tests with explicit static contexts**. There is no fully certified all-intended-role v2 policy to hand off. Missing capability contexts must stay blocked and cannot become zero-mass exclusions. Final artifact/commit identity is independently pinned by the final verification manifest; consume that exact tested successor rather than a moving branch name.

Recommended next engineering step is **continue Stage 1 offline** on the shared modern projectile owner for Goblin_Turret and Scarak_Seeker, with Firesteel attachment as a separate focused fix. First obtain a demonstrable native-class creation→impact→removal/recovery fixture and exact source pins; only then reconcile each alias and rerun legal-plan coverage. Keep all connected obligations queued for Stage 4. Do not begin Stage 2 automatically.

Owner decisions are limited to product boundaries: whether neutral/retaliatory catalog families are intended procedural targets; which marker/beacon/dungeon/encounter variants belong in that population; and whether retired-ID successors should receive new canonical progression identities or remain unrelated. Recommended default is preserve existing behavior and saved identities while engineering the already named priority roles. A missing mapping, incomplete effect path or Firesteel attachment failure is engineering work, not a reason to ask the owner to accept a weaker safety rule. No intermediate QA or deployment is requested.

## Verification and rollback record

Focused native projectile/outgoing tests pass (`build/me12-reconcile-focused.log`); the meaningful red test is preserved in `build/me12-reconcile-red.log`. Sixteen Python content/reconciliation tests pass, including Seeker override, held-item Burner route, separate spawn types, cycles/unresolved expressions and no invented exclusions. `check` now regenerates and verifies both 309-row artifacts. Existing patch verification and all prior suites remain mandatory.

Run one final clean-HEAD `gradlew --offline --no-daemon check jar testReleaseNativePatch guardianSpotBugs -PguardianSpotBugs=true --continue --console=plain` after the documentation/identity commit. Record its unmodified output in `build/guardian/me12-reconcile-final.log`, actual test totals/artifact hashes in `build/reports/me12/reconciliation-final-manifest.json`, then generate the Guardian receipt and publish only the matching feature branch. This tracked record does not predeclare that future cumulative result. Preserve the previous successful 4,324-test log/manifest.

Rollback for this isolated pass is the preserved b7 source and its verified artifact; nothing was installed and no v2 records exist. Future v2 rollback still requires a v2-capable reader/new-birth gate per AD1. Publication is not main integration, deployment or acceptance.
