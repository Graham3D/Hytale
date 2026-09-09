# Hytale RPG: implementation history, Stages 00–13

Prepared 2026-09-09 for the owner and ChatGPT. Implementation checkpoint: `cd7cdbd0299de4bf18740b2cca08756b64b77f69`, branch `RPG`, Stage 13 L. This documentation pass changes no Java, gameplay, assets, installed JARs or saves.

## What this record does and does not claim

This is a reconstruction from committed implementation reports, correction reports, test/evidence records, source and Git history. It is not an invented verbatim transcript of every development conversation, every command ever run, or undocumented internal reasoning. The accompanying [complete historical report collection](implementation-history-full.md) includes the full retained Markdown reports/checklists and Git commit/stat chronology at the checkpoint, including failures and superseded conclusions. Original sources and their Git blob identities are indexed there. Consult linked evidence and commit diffs for underlying code and machine-readable results.

Read this synthesis and the [current QA/QC runbook](qa-qc-checklist.md) before using historical checklists. Those older documents contain deliberately preserved instructions that are no longer current: four skill slots, custom resource HUD, packet interception, paused stage approvals, older JAR versions, and pre-Stage-07 non-executing Fork metadata. Historical text is evidence of progression, not current operational instructions.

### Current evidence boundary

- The exact L RPG JAR is `1E4A5CAA1344CE71C8701EBCFBFCB3883BD288DC90BE9732066338A5F6A5B0F6`, filename `HytaleRPG-0.0.25.jar`, code label R032. Same-version older JARs exist; use the hash.
- L passed 2,093 tests, zero failures/errors/skips, isolated normal three-mod smoke, exact ZIP-entry checks and rollback-JAR verification. These results were obtained in the implementation turn, not rerun during this documentation pass.
- L was deployed to `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods`. HytaleDevLib 0.5.0 and CanvasUI 0.1.0 remain the two supporting mods. CanvasUI's demo is in its library JAR; no fourth demo JAR is required.
- **New owner evidence:** after L deployment, the owner reported “The mod loads.” This is owner-reported successful loading of the correction. No fresh trace audit or gameplay recording is implied. Attribute allocation, RPG ability casting, full restart persistence, all-skill behavior and multi-client performance are not promoted to PASS.
- Stage 01B and R011 Stage 02 retain their historical connected acceptance, scoped to those tests/builds. Stages 03–13 require current connected coverage. Stage 13 production acceptance is not closed merely because a playable test package was requested and delivered.
- The compiled inventory is 87 skill records and 66 passives, not 87 connected-proven skills. Four whole-skill gates remain: Frenzy, Guard, Snipe and Bone Cage. Partial limitations also remain.

## Stage chronology

### 00 — evidence and feasibility, not a combat implementation

Initial commit `6e3996b` established a Java 25/Gradle mod scaffold, pinned installed server API, asset inventories and temporary Character/Link-canvas/HUD probes. Initial work targeted Hytale 0.6.3; `eaeb93d` re-audited 0.7.0-pre.1. The initial report explicitly separated successful loading/export from missing connected input/UI proof and unsupported public surfaces. A server plugin starting was never sufficient evidence of global hotkeys, arbitrary pointer input or native client rendering.

This stage produced the fundamental architecture rule: source-audit the exact installed API/asset boundary, then prove client-dependent behavior with a connected client. Reports: [Stage 00](../phase-00/phase-00-report.md), including its appended re-audit, and the retained 0.6.3 history.

### 01 / CanvasUI R002–R008 — reusable graph library and a real capability wall

The requested reusable CanvasUI was built separately from RPG. Early revisions implemented graph models, rendering, transforms and persistence plus a demo. Connected testing exposed recurring CustomUI failures: document grammar/widget schemas, inline document assembly, typed anchor encoding and event bindings. Cleanup validation was added to prevent known invalid document constructs reaching packages. R004 consolidated the separate demo into the CanvasUI JAR. R005/R006 switched/fixed rendering paths until the owner could see the graph.

Visible nodes were not draggable nodes. The subsequent input probes distinguished discrete button/value events from continuous coordinate-bearing pointer movement/capture and modifier/wheel data. R007 introduced capability abstraction and discrete/slider-based fallbacks; R008 corrected unsafe bindings and EventData handling following disconnect/client-crash evidence. Unsupported input was not synthesized from server gameplay mouse events. Free drag, background pan and wheel-driven canvas interaction remained blocked on the audited surface; development was paused rather than claiming a FigJam-equivalent editor. No Noesis availability date or capability was assumed.

This failure changed the implementation strategy: graph authority must not depend on canvas coordinates/widgets. A fixed-layout frontend and independent server graph backend could proceed without pretending the input problem was solved. [Full revision ledger](../canvas-ui/development-report.md).

### 01B / R009 — catalog, graph, compiler and durable loadout foundation

`8a933e7` implemented typed IDs for 87 skills/66 passives, canonical lookup, compatibility verdicts, graph validation and compilation, persistent player loadout and temporary command editing. Accepted mutations remain transactional: validation/compile failure must preserve the previous valid state. Coordinates do not influence semantics. Cycles, illegal edge types, invalid sinks and incompatible modifier application are rejected, rather than silently accepted by a frontend.

Connected commands and restart/rejoin were subsequently accepted. The important retained examples are Fork→Fire Bolt compilation and Fork→Quick Slash rejection/rollback. Later R016 changed the logical topology to three skills, six passive slots and joints; old four-slot examples are historical. [Stage 01B report](../stage-01b/stage-01b-report.md).

### 02 / R010–R011 — shared combat authority and proof-driven closure

`9f1fe9a` introduced the combat kernel: soft-capped attributes, derived native-resource maxima, costs/reservations, cooldown recovery, base-power/scaling/modifier buckets, crit, statuses and Hytale damage adapters. Martial skills spend Stamina and magical skills spend Mana; gameplay resource ownership is separate from native HUD presentation.

R010's initial diagnostic damaged the invoking player and produced calculation/Gather without downstream evidence. R011 (`fceaed0`) corrected the evidence path to a living, valid non-self NPC through the actual native damage pipeline. It did not fabricate Filter/Apply/Inspect events. Health-before/after and actual Health loss were recorded with stable root, instance and correlation IDs. Recovery proof was made deterministic by depleting resources and applying normal/charged recovery and duplicate IDs in one bounded diagnostic, avoiding a race with native regeneration.

Content reconciliation restored Potency to **+15% increased scalable magnitude**, removed the +10% override, and retained generic cooldown-recovery support without inventing a 67th “Swift Recovery” passive. `83fce1b` records connected R011 closure. These diagnostic proofs do not prove native ability input or every later skill. [Stage 02 report](../stage-02/stage-02-report.md).

### 03 / R012, then R016–R020 — Character, fixed tree and native HUD ownership

`0676b59` and `df7a199` established server-owned Character allocation, UI projections, trace coverage and XP/HUD state. The initial implementation gate was not connected visual acceptance. R016 introduced the corrected three-slot fixed-layout Skill Tree. Subsequent connected failures drove R017 document grammar repair and R018/R019 resource/XP corrections.

R020 is the controlling final resource-presentation contract: Hytale alone owns Health, Mana, Stamina and native Stamina temporary visibility; RPG does not add a Mana overlay or visibility lease. The XP-only composition uses the owner's artwork with Background→Fill→Frame, horizontal centering and a left-anchored fill. Resized assets are 702×28 frame, 696×28 background and 1×22 fill. Old ten-pip XP, resource-strip, fake hotkey and four-HUD-cell expectations must not be revived from older specification examples. C/K global binding and native Inventory extension remain capability boundaries; `/rpg character` and `/rpg skilltree` are the supported frontends.

Reports: [Stage 03](../stage-03/stage-03-report.md), [R016](../corrections/R016-correction-report.md), [R017](../corrections/R017-customui-load-fix.md), [R018](../corrections/R018-hud-correction-report.md), [R019](../corrections/R019-native-resources-xp-correction.md), [R020](../corrections/R020-vanilla-resources-resized-xp.md).

### 04 / R013 — activation orchestration and initial strike/movement/reaction pilots

`5e3257b` introduced shared skill activation, validation, immutable compiled execution plans, cost/cooldown commit and family dispatch. Representative pilots were Quick Slash, Heavy Swing, Shield Bash, Quickstep, Pounce and Riposte. Native equipment/geometry, movement and reaction signals are adapter responsibilities; there is not a second damage system for each skill. Native boss policy was corrected to the audited identity rather than arbitrary classification.

Local tests and server loading allowed bounded subsequent implementation under the owner's instructions. They did not prove that E/R reached activation, nor that every strike/movement mechanic in the 87-entry catalog was implemented then. Remaining records and irreversible-dispatch fixes were addressed in Stage 13. [Stage 04 report](../stage-04/stage-04-report.md).

### 05 / R014–R015 — native projectile carriers, not duplicate gameplay authority

`ac4d9c3` and `78b314a` implemented six projectile pilots, native carrier configuration, flight/collision/termination ownership, element/status payloads and shared lineage. RPG owns cost, cooldown and damage; native carrier assets must not apply a duplicate native payload. Source resolution, finite lifetime, target deduplication, wall/range handling and teardown received local coverage. Early Fork metadata did not yet execute children; Stage 07 supplied that functionality.

Connected casting remained blocked earlier than projectile execution. This distinction prevented compensating for absent input by changing projectile math. [Stage 05 report](../stage-05/stage-05-report.md).

### Cross-stage R021–R024 — input hypothesis, falsification, vanilla control

R021 successfully projected RPG ItemAbility triggers into native Ability2/Ability3, with native Cost=0, CostType=None and Cooldown=0. The server adapter watched inbound SyncInteractionChains. Equipped icons were not evidence of input reaching RPG.

R022 replaced the lone Simple bridge with effect-free FirstClick after auditing Client wait/remote-sync behavior. Its root compiled and remote-sync hints were true, yet the connected session still recorded zero input/request/commit events. Static synchronization metadata did not establish packet traffic; the hypothesis failed.

R023 used an unmodified shipped Fireball Rune and the same watcher. The owner confirmed visible native Fireball casting while the watcher remained silent. The control localized the problem to observation architecture, not Stage 04/05 executors. The exact installed Quiche path bypassed the watcher. R024 (`5c5e55e`) moved activation to native server interaction execution via the bridge, retaining zero native costs and RPG gameplay authority. Temporary control item ownership/recovery was journaled rather than deleting or overwriting player runes. RPG casting on the latest build still needs its own connected proof; vanilla Fireball success is not RPG success.

Sources: [R021](../corrections/R021-native-ability-integration.md), [R022](../corrections/R022-native-ability-synchronization.md), [R023](../corrections/R023-native-rune-control.md), [R024](../corrections/R024-native-execution-boundary.md).

### 06 / R025 — spatial effect families and four general Links

Cohorts A–D, closing at `689d451`, supplied 15 spatial skills across bursts, zones, cones, traps, walls, overhead impacts and bombardment. Shared work covered bounded server geometry, LOS/protection, warning/impact order, finite effect ownership, source-owned periodic statuses, movement/displacement and deterministic release/cleanup. Potency, Expanded Radius, Echo and Skill Delay were consumed by the shared runtime, not UI-only metadata. Local closure retained 217 tests; connected warning footprint, timing and actual effects remain separate. [Stage 06 report](../stage-06/stage-06-report.md).

### 07 / R026 — twelve projectile modifier primitives

Cohorts A–C, closing at `61fcc86`, implemented projectile continuations/multiplicity and flight modifiers, including Fork, Pierce, Chain, Return, Volley, Barrage, Ricochet, Homing and secondary behaviors. Root/instance lineage, deterministic ordering, depth/generation/secondary caps, no extra child resource cost and once-per-target rules were centralized. Subsequent Stage 13 work strengthened the shared root-lifetime budgets across families. Local closure: 274 tests, not all combinations connected-proven. [Stage 07 report](../stage-07/stage-07-report.md).

### 08 / R027 — connections, beams, tethers and orbiting effects

Two cohorts closed at `31d4a74`, delivering eight line/beam/tether/orb records. Work included finite sampling, per-tick/upkeep ledgers, geometry, periodic execution and owner cleanup. A channel must not receive free pulses after affordability fails, and repeated samples must not create duplicate impact authority. Local closure: 320 tests. Native channel release, moving targets, line obstruction and jitter still require client recordings. [Stage 08 report](../stage-08/stage-08-report.md).

### 09 / R028 — support, shields, reservations and Auras

Cohorts A–F closed at `837ed80`: 16 support/barrier/Aura records plus seven passive primitives, native stat regeneration decorators, finite buff/reaction ownership, reservations and work-preserving cooldown recovery. Support durability evolved through player schemas 4/5. Shield deficit had to survive toggling/rejoin; changing capacity or allocation could not refill it for free. Native contact attribution and native enemy cooldown manipulation remained explicitly limited where adapters were unavailable.

Local closure: 516 tests. The original per-hit persistence implementation later became incompatible with the nonblocking native damage requirement. Stage 13 J changed its authorization mechanism to pre-durable escrow under explicit owner approval; it did not remove durable shield authority. [Stage 09 report](../stage-09/stage-09-report.md).

### 10 / R029 — summons, corpse claims and reversible conversion

After workspace recovery to the C: GitHub checkout (`cda4d99`), cohorts A–G closed at `2e5d928`. The work covers Wolf Summon, native batch summons, Revive Fallen, consuming actions, Corpse Burst, Death Pact, Simulacrum and Dominate. Native role/source coverage is bounded and audited, not universal NPC compatibility.

Exclusive corpse claims prevent competing consumers from both succeeding. Owned entity limits, target death/logout/unload cleanup, reversible conversion leases and restoration/exclusion are correctness requirements. Minion/decoy lineage must not become farmable reward authority. Bone Cage is explicitly safety-disabled because an enemy-only collision/protection contract was not established; a decorative cage is not a substitute. Local closure: 662 tests. Owner art and recovered loose classes in `art/lost and found` were preserved, not treated as deployable JARs. [Stage 10 report](../stage-10/stage-10-report.md), [workspace recovery](../workspace-recovery-20260908.md).

### 11 / R030 — remaining forty passive primitives and full compatibility coverage

Cohorts A–Z closed at `e9944e1`. The work systematically consumed scoped magnitude/cost/reach/windup modifiers; charge recovery; reaction windows; live victim conditions; geometry; Mobile Domain; pulse clocks; source-owned Burn/Poison/Chill; Lifeblood/Attunement; actual-Health Leeching; repeated strikes; cleave/secondary components; Cascade/Aftermath/Orbit; hit/crit/kill/death procs; and finite retaliation permits.

Component scope is fundamental: a passive applicable to an introduced area/bleed/impact component must not alter unrelated parts of the skill. Conditional checks use the correct live/native event boundary, while compiled plans remain immutable. Natural expiration and cancellation are distinct; proc lineage and repeat budgets prevent recursive/free execution. Schema 7 preserves unsupported saved passive nodes as inactive without deleting ownership/topology.

Closure retained 1,377 tests, 5,742 skill/passive classifications, 2,145 passive pairs and 1,000 valid six-Link graph fixtures. These counts are deterministic/local coverage, not thousands of connected tests. [Stage 11 report](../stage-11/stage-11-report.md).

### 12 / R031 — progression, attribution and exactly-once rewards

Cohorts A–H closed at `de60a02`. A established canonical 64-bit XP/rounding, bands/difficulties and capability gates. B established earned-reward identity/durability. C audited native enemy roles and bounded eligibility. D persisted encounter credit, watermarks and partial death payouts. E attached native spawn/Inspect/death observations. F added actual support credit and trusted party snapshots. G awarded meaningful mastery from observed outcomes. H added acquisition, source-specific pity, Insight spending, guarded respec, fixed-layout build transfer and schema-9 persistence/rollback.

Only approved native identities are rewarded: three pilot roles (Wolf_Black, Trork_Warrior, Skeleton_Archer) and four biome bindings were audited; unknown roles are not assigned invented vanilla levels/XP. Native spawn provenance and anti-farm rules still apply. Party fallback is SOLO_ONLY without a trusted provider. There are zero connected-verified acquisition sources; development entitlements cannot certify earned learning.

Death plans freeze ordered credit and reward identities. Durable player application plus delivery receipts prevents duplicate awards after interruption. Rewinding one ledger independently would violate that safety model. Local closure: 1,653 tests, actual archived-schema/coordinated rollback checks and three-mod smoke. Stage 13 subsequently exposed a performance defect not measured by the Stage 12 local closure. [Stage 12 report](../stage-12/stage-12-report.md).

### 13 / R032 — full inventory, shared hardening and the persistence sequence

The stage was developed in cohorts, not one monolithic final rewrite:

| Cohort | Checkpoint | Change / consequence |
|---|---|---|
| A | `7c5c4f6` | Remaining strike profiles; an irreversible dispatch keeps paid costs when an effect may have happened, while safe pre-dispatch failures retain rollback. |
| B | `255463f` | Remaining projectile payloads and audited item base power, without averaging unrelated charged/combo/signature native damage. |
| C | `057c436` | Authored projectile patterns/fuses/secondaries and shared 48-effect/16-secondary root-lifetime accounting; Snipe remains range-gated. |
| D | `55a551b` | Bounded native ground/path movement and swept clearance; Guard remains gated on held-item/release integration. |
| E | `845e371` | Actual native melee basic-hit witnesses, Execution Strike, Backstab and Finishing Strike/root token ownership; no packet-sniffed recovery authority. |
| F | `14a0f42` | Bounded tracing, cached presentation/compile hot paths, 87/66 coverage, disabled Frenzy decision, full 1,886-test run and the real-storage latency failure. |
| G | `f25bf99` | Long-lived encounter WAL channel/lock, immutable checksummed deltas, sequence/replay/checkpoints and force-before-acknowledgment; disk gate still failed. |
| H | `eecd64c` | Bounded provisional submission versus durable completion, real group commit and asynchronous immutable checkpoints; 1,963 tests, gate still failed. |
| I | `f5f9cb3` | Prearmed WAL v2 and checkpoint grouping; 2,053 tests/48 process-halt cases, remaining barrier-admission/submission architecture boundary retained. |
| Audit | `d28a9f2` | Native handoff audit stopped rather than inventing a damage suspend/resume API. |
| J | `eb42b1c` | Owner-approved nonblocking native handoff and pre-durable shield escrow; 2,087 tests and additional recovery coverage. |
| K | `84e29cb` | Bounded encounter-load frontier capture; 2,090 tests, exact three-mod testing archive. |
| L | `cd7cdbd` | Connected first-join crash corrected by gating native projection on deferred player readiness; 2,093 tests, deployment, then owner reports successful loading. |

#### What the performance evidence actually says

F's four-actor/16-victim, 64-update real-storage diagnostic showed approximately 187.8359 ms p50 / 731.5307 ms p95 / 4053.5935 ms p99. Every accepted contribution serialized/validated/replaced a complete forced JSON snapshot. G removed that application overhead with an append-only WAL, without weakening force-before-durable-success. H reduced forces from 3,840 to 119 across the measured workload, but retained p95 16.0318 ms / p99 19.8859 ms. I reduced checkpoint-related forces and measured p95 7.8891 ms / p99 16.1037 ms; standalone barrier results did not justify claiming the drive universally incapable of the target.

The owner then authorized separating native callback work from durable completion. J and K retain the historical storage diagnostic, but it is **not a native tick measurement**: J recorded 4.6011/8.1914/19.2184 ms and K 4.5710/8.0007/18.9611 ms p50/p95/p99. The actual 4 ms p95 / 8 ms p99 added-RPG-native-tick gate remains NOT_MEASURED. Background backlog, durable latency and native tick costs must be measured separately; no percentile or assertion was relaxed to manufacture closure.

#### Final durability architecture

Owner-thread work validates native facts and reserves bounded capacity before mutation. Immutable work captures a finite predecessor and is processed in order. A provisional receipt is not an accepted-durable-success announcement. WAL append and a covering force precede durable completion, subsequent mastery and frozen death/reward work. Completion requiring ECS state returns through owner-thread revalidation; workers do not retain/read Hytale world objects. Owed work survives native entity removal. Overload rejects before admission, while genuine uncertainty remains fail-closed.

For Managuard/Shared Aegis, shield capacity is authorized durably **before** a hit. Hits spend memory-only authority and do not wait for persistence or suspend native damage. Durable debit includes outstanding authorization. Abnormal restart discards volatile escrow while keeping the debit: unused shield allowance can be conservatively forfeited but cannot reappear as free protection. Cooldown/support transitions may remain pending until durable completion. Defensive committed player views avoid IO-held locks on native hot reads.

K fixed another finite-ordering detail: native load captured its WAL/checkpoint frontier when admitted, rather than capturing a later global tail when its worker happened to execute. Later unrelated writes cannot indefinitely extend that prerequisite; earlier required writes and same-context ordering remain intact.

L exposed a missing consumer of that new readiness contract. The owner joined and immediately crashed because the ability tick created a session/read the loadout before asynchronous preload completed. L prohibits tick-created sessions and passes `loadouts::ready`; no sync-read fallback or broad exception suppression was added. The exact JAR diff changed projection/plugin classes only. See [L report](../stage-13/stage-13-startup-hotfix-report.md).

Detailed sources: [Stage 13 cohorts](../stage-13/stage-13-report.md), [G WAL](../stage-13/encounter-wal-correction-report.md), [H grouping](../stage-13/encounter-group-commit-report.md), [I boundary](../stage-13/encounter-durability-final-boundary-report.md), [J handoff](../stage-13/native-persistence-handoff-correction.md), [K package](../stage-13/stage-13-test-build-report.md).

## Current review priorities and unresolved work

1. Verify real native RPG activation, not merely native icon projection or vanilla Rune casting. Find the earliest missing event in a complete trace before diagnosing an executor.
2. Record positive and invalid/cancel cases for all enabled skills and each passive primitive, using the generated inventories and family procedures. Four gated skills require rejection evidence, not fabricated successful casts.
3. Verify fixed UI layout, native resource ownership, current XP artwork, Character mutations and restart persistence on L.
4. Verify shield escrow, progression/death ordering and cancellation under connected load; local halt tests are not physical power-loss proof.
5. Qualify four-player and deployment-scale native tick/backlog behavior without changing the 4/8 ms limits. Preserve test data and report trace gaps.
6. Audit public-server permissions/development entitlements. The current testing package enables development entitlements, and several development commands use Adventure permission groups. A production-safe debug-command policy must not be assumed from their names.

Known partial limitations include audited-melee-only native recovery observation, Flame Weapon native basic-hit attribution, Pedanticism native enemy cooldown progress, constrained revive/conversion roles, SOLO_ONLY parties and no connected-verified learning bindings. These are distinct from whole-skill gates and are listed per record in the inventory.

## Review packet map

- [Full source-report anthology and commit/stat chronology](implementation-history-full.md): exhaustive retained documentary history at the implementation checkpoint, with superseded assertions preserved and labeled.
- [QA/QC checklist](qa-qc-checklist.md): current commands, setup, expected evidence, safety and stop conditions.
- [87-skill QA worksheet](qa-skills.csv): exact equip command, equipment requirements, base cost/cooldown, complete runtime profile, current gate and blank result fields.
- [66-passive QA worksheet](qa-passives.csv): exact equip/link commands, authored modifier behavior, limitations and blank result fields.
- [Evidence/source manifest](review-manifest.json): checkpoint, source blob identities and generated file hashes.

No connected pass is prefilled into the worksheets. The owner's load confirmation is recorded above, independently of all unperformed QA. All files are in the GitHub checkout, not Google Drive.
