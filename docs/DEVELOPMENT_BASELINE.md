# HyARPG/Hywind development baseline

## HRO Stage 3 final engineering candidate (2026-10-13)

State: **IN_PROGRESS — implementation complete; committed-source release gate
and owner-authorized main integration pending**. Branch `codex/hro-stage3`,
worktree `C:/hyhro3`, revision **R262C1HRO3-CANDIDATE**, version
`0.2.0-R262C1HRO3-CANDIDATE`, Hytale `=0.7.0`.
Exact parent: `985c9c06f3d370d2afc882299a16682aa0cd66c3` (`codex/hro-stage2`),
including Stage 1 and remote main `ce3865dd5f81bee95003cb5dde8dc2f959bdac2b`.
The latest user request explicitly authorizes safe integration of completed HRO
engineering into main. No additional gameplay ancestor is being introduced.
Source integration is not connected gameplay acceptance.

Stage 3 delivers immutable per-contract approved baselines, semantic comparison,
versioned legacy imports with preserved provenance gaps, selected native/asset
fingerprints, source/reuse maps, explicit correlation cascade groups, bounded
extraction, fifteen backend-neutral UI fixtures and an offline parity seam.
The analyzer also propagates sequence/count gaps into mechanical evidence
completeness. See [HRO_STAGE_3.md](HRO_STAGE_3.md) and [COMMANDS.md](COMMANDS.md).

Focused checkpoint: 97 HRO root tests plus six native production bridge tests
passed; strict SpotBugs passed after importer null handling corrections.
JaCoCo: 1,045/1,168 HRO lines at that checkpoint. One subsequent sequence-gap
regression fixture and small offline hardening changes await the final gate.
Heap bound: 41,300,256 bytes (39.4 MiB), below 64 MiB. Warm disabled allocation
zero; producer p99 0.0085 ms; synthetic sixteen-observation batch mean/p99
0.0215/0.0562 ms. Whole diagnostic process trials include concurrent manual
recording, initialization, worker drain and GC; native world/client measurements
remain UNVERIFIED. Final exact-HEAD evidence is under ignored `build/guardian/`.

Deployment: **unchanged**, active Hywind save JAR remains
`R262C1INV1-CANDIDATE`, source `afdf9bfbce9d5fb395bd68848833480a849f0e0b`,
SHA-256 `3B80550CCA85E59061C40B393A574FA42F7009180E5B711F91930BE1AF719B47`.
No deployment, game/server launch, authentication, save mutation or connected QA
was performed. Main's latest integrated candidate and the deployed build differ;
no newer cumulative QA-accepted build is established by these records.

The primary checkout remains `main` at `1dfa2cea3085196c7fd4492518c65a03d5e3b49d`
with user-owned AGENTS/artwork/icon/script changes preserved. Its inherited
committed AGENTS policy still names a legacy save; the newer user-supplied policy
governs this task. AGENTS.md is not changed in any worktree. Final integration
must preserve this dirty checkout and use the isolated task branch.

## HRO Stage 2 mechanical evidence (2026-10-13)

State: **ENGINEERING_READY — connected acceptance/performance UNVERIFIED**. Branch
`codex/hro-stage2`, worktree `C:/hyhro2`, candidate `R262C1HRO2-CANDIDATE`,
version `0.2.0-R262C1HRO2-CANDIDATE`, exact Hytale requirement `=0.7.0`.
Verified Stage 1 parent: `92ac44fb23c515acd59f2470403dd9feca292b30` on
`codex/hro-stage1`. Reconciliation commit
`255a94c1138169b1ab492d813023643eed232616` includes integrated remote main
`ce3865dd5f81bee95003cb5dde8dc2f959bdac2b` and preserves Stage 4 pickup/reward
corrections. Main is ahead of the former Stage 1 parent on Chapter I recovery,
but lacks HRO Stage 1/2. Stage 2 explicitly depends on Stage 1. Earlier migration
integration was owner-authorized; connected acceptance is not inferred, and
the available records do not establish a newer QA-accepted cumulative build.

This pass adds eight mechanical contracts (seventeen total), native combat
reconciliation using the existing trace rule, actual percentage/roll evidence,
R261 defensive receipts, natural spawn decisions/publication, real durable
save/load checksums and explicit cross-session receipt joins. The bounded
affix/receipts report, priority reserve, loss handling, synthetic examples and
capability-blocked outcomes are documented in [HRO_STAGE_2.md](HRO_STAGE_2.md).
Exact implementation source: `24968cab388ceefee463edc21566c27417ca6c17`,
including foundation `c5e5ae9b35cd181184c3ab19233b7d42d619d1d4` and the
test-only saturated heap census. The final correlation correction records the
proc target/frozen equipment revision and rejects cross-target joins; it does
not change RNG or gameplay formulas. The subsequent handoff is documentation-only.
The preceding complete offline checkpoint passed **4,265 JUnit tests**: 3,722 root, 489 native, 49 CanvasUI,
5 Taverns; zero failures, errors or skips. Strict SpotBugs passed with zero
root findings. The correlation follow-up adds one passing regression test;
its focused production bridge, strict SpotBugs and JaCoCo checks passed.
JaCoCo measured 735/832 HRO lines; unobserved native outcomes
remain explicit gaps, not inferred passes. Current release assets, CustomUI,
spatial grant audit and package checks passed. The package has 19,576 entries,
2,285 classes, 90 Tavern classes and zero ImmersiveNPCs payload entries.

Offline producer p99 was 0.0102 ms, synthetic 16-observation batch mean
0.0234 ms / p99 0.1016 ms, warmed disabled allocation zero, overload p99
0.0025 ms with critical outcome retention and visible routine losses. The
expanded test-only census measures 4,351,208 queue/session bytes plus
20,171,824 saturated analyzer/report bytes and a 16 MiB allowance: 41,300,248
bytes (39.4 MiB), below 64 MiB. The shared queue remains capped at 16 MiB.
The exact final handoff HEAD, packaged source identity, JAR checksum and
mandatory post-commit check/package results are bound by
`build/guardian/verification.json`; publication requires that receipt and
Guardian completion. No artifact is accepted from its R-label alone.
No installed JAR, server, asset archive or save is modified.

Active deployed build remains INV1, source
`afdf9bfbce9d5fb395bd68848833480a849f0e0b`; deployment status **NOT_DEPLOYED**
for HRO2. Its unchanged installed SHA-256 is
`3B80550CCA85E59061C40B393A574FA42F7009180E5B711F91930BE1AF719B47`.
Connected QA/world-tick/client-frame qualification **UNVERIFIED**,
reserved for Stage 3. Primary Windows main at `1dfa2cea` remains dirty and
behind remote main, with AGENTS.md/artwork changes preserved. Existing
worktrees were inspected and left intact. Next action: finish exact-HEAD
publication gates, publish the reviewed matching `codex/hro-stage2` branch, and retain it
for Stage 3/acceptance and serialized integration review. No main merge or
owner in-game QA is requested by this offline assignment.

## HRO Stage 1 diagnostic foundation (2026-10-12)

State: **ENGINEERING_READY — native performance/connected acceptance UNVERIFIED**. Scoped candidate
`R262C1HRO1-CANDIDATE`, version `0.2.0-R262C1HRO1-CANDIDATE`, exact server
requirement `=0.7.0`. Branch `codex/hro-stage1`, isolated worktree `C:/hyhro`,
parent `3e359b8e036ad023c9c8bd93064bcb0f78068f83` on
`codex/chapter1-stage3-elite-restoration`. The candidate preserves the cumulative
unaccepted Chapter I startup, skill bar, inventory, world, native combat and
Elite recovery ancestry; it must not carry those changes into `main` before
their acceptance gates. Remote `main` remains
`6d3ef15ff0145532ef193c42e3b648307ee6e915`, while installed INV1 embeds
`afdf9bfbce9d5fb395bd68848833480a849f0e0b`. Neither has newly inferred QA acceptance.

While this task ran, Stage 3 published test/documentation-only follow-up
`d28d92f49e7639a6c75da71a810088918456b79b` (`R262C1S3V2-CANDIDATE`), and
`codex/chapter1-stage4-gear-loot-recovery` began isolated, uncommitted work
including gear ownership code. HRO remains on its original verified parent;
reconcile these separate branches before integration. No newer accepted build
or active deployed artifact was replaced.

The [Stage 1 report](HRO_STAGE_1.md) records the nine contract families,
asynchronous capture/stop, bounded streaming bundle, loss-aware evaluation,
offline recovery, provider/registration evidence, installed server/assets
hashes, focused fixtures and measured producer/heap qualification. Exact
implementation source is `codex/hro-stage1` at
`486db1d2af40c8884bd0dc9387772a5b0777c568`; the subsequent handoff commit only
updates this record and the report. Full offline check passed 4,210 JUnit
tests (3,672 root, 484 native, 49 CanvasUI, 5 Taverns), with zero failures,
errors or skips. Package/asset checks, strict SpotBugs (zero reported findings),
JaCoCo review (466/528 HRO lines), slow/full disk, recovery, producer timing
and heap qualification passed. Final committed source HEAD, candidate version,
JAR hash, repeated exact-head check and package audit are bound by
`build/guardian/verification.json`; the matching remote branch is the only
publication target. No build is deployed. Whole-world tick/client-frame
qualification and connected acceptance remain unmeasured; no owner QA is
requested as a prerequisite for another assignment. HRO Stages 2 and 3 have
not begun. The primary checkout's policy/artwork, all saves, and the installed
stock server/INV1 JAR remain preserved. Integration remains gated by acceptance
of the cumulative ancestors and intentional reconciliation of the Stage 3 V2
and Stage 4 overlaps. HRO Stage 2 requires a separate assignment. The primary
Windows `main` checkout remains dirty and behind remote `main`; publishing
this feature branch does not synchronize it.

## Chapter I Stage 4 natural equipment and reward recovery (2026-10-12)

Candidate: **ENGINEERING_READY / QA_PENDING** after the committed completion
gates in the [Stage 4 report](release-migration/STAGE_4_NATURAL_GEAR_AND_REWARD_DELIVERY_RECOVERY.md).
Branch `codex/chapter1-stage4-gear-loot-recovery`, worktree `C:\hystage4`,
parent Stage 3 `d28d92f49e7639a6c75da71a810088918456b79b`.
Version `0.2.0-R262C1S4-CANDIDATE`, revision `R262C1S4-CANDIDATE`, release API
`0.7.0`, exact requirement `=0.7.0`. Death-share XP now uses the existing
frozen reward-context helper, and automatic pickup waits for a live native
item position. Existing generation, drop probabilities, claims, receipts,
spatial/native inventory owners and Stage 1–3 behavior are preserved.

New stateful tests compose natural birth/death, interrupted delivery, frozen
MF/gear and pickup custody across restarts, plus native ECS world-item
projection/deferral/cleanup. Full committed offline check results, exact
source commit and artifact SHA-256 are bound in
`build/guardian/verification.json` and `build/stage4/deployment-manifest.json`.
The matching native patch remains
`e0bcd36c7a981949d2c15b1b48f1cdd7f9623373269ef03b58f2d9b9da7f6edf`.
Publication and integration identities are recorded in
`build/stage4/integration.json` and the corresponding GitHub history.

The owner's explicit Stage 4 instruction authorizes safe integration of this
reviewed cumulative migration candidate into GitHub `main` after engineering
completion. This supersedes the historical unmerged instructions in earlier
stage entries below. It does **not** mark any connected gameplay accepted.
At task start `origin/main` was
`6d3ef15ff0145532ef193c42e3b648307ee6e915`, 26 commits behind Stage 3.
The cumulative integration includes startup, four-slot/readiness/inventory
recovery and Stage 0–4, with their existing bulk-attribute ancestor. It excludes
the separate unfinished `codex/hro-stage1` task, which must reconcile the new
main baseline before integration. Do not retire dependency worktrees yet.

No artifact is installed. The authoritative release `UserData/Saves/Hywind`
still has INV1; the installed server is stock. Both saves, the primary
checkout's artwork/uncommitted work and protected AGENTS.md are preserved.
The older AGENTS.md RPG-target wording is not applied to release deployment.
The primary local `main` remains behind and dirty; a GitHub integration does
not synchronize it. Next action is separately authorized paired deployment
and owner-connected QA, then repair any reported failure without discarding
the existing architecture. Source integration is not gameplay acceptance.

## Chapter I Stage 3 natural Elite and affix recovery (2026-10-12)

State: **ENGINEERING_READY / QA_PENDING** on dependent branch
`codex/chapter1-stage3-elite-restoration`, parent Stage 2 commit
`b5bd3585fb69bcd5f615555cebfea2a2d9d7c88f`. Stage 1, Stage 2, and the
earlier cumulative ancestors remain unaccepted and unmerged. Build revision
`R262C1S3V2-CANDIDATE`, version `0.2.0-R262C1S3V2-CANDIDATE`, exact server
requirement `=0.7.0`. The [Stage 3 engineering report](release-migration/STAGE_3_NATURAL_ELITE_AND_AFFIX_RECOVERY.md)
records the release native class audit, actual pre-release/release evidence,
existing owners and regression coverage. The natural spawn and flock classes
needed no replacement: their release bytes match the functioning pre-release
server; the Stage 1 world and Stage 2 hook corrections are retained. A new
release-world fixture exercises Normal fallback, Champion and Unique planning,
affix freeze, durable birth, and restart recovery. The exact Stage 3 source
HEAD, package SHA-256, full offline test result, and Guardian status are bound
in `build/guardian/verification.json` after committed verification. The
matching native patch and Hywind JAR remain offline; the installed stock
server and INV1 JAR, both saves, and the dirty primary checkout are untouched.
Deployment requires separate authorization and matching-artifact installation.
Owner-operated connected natural spawn, combat, presentation, and persistence
QA remains pending. Do not merge the dependent work into `main` yet.

The Stage 3 continuation starts from published commit
`3e359b8e036ad023c9c8bd93064bcb0f78068f83`. It adds native ECS whole-roster
staging release checks and a 480-attempt workload with 48 durable publications,
overlapping pending/active leases, duplicate acknowledgments, suspension, and
restart. The production limits, spawn probabilities, and gameplay owners are
unchanged. The matching artifacts and exact final source/checksums are in
`build/stage3-v2/deployment-manifest.json`; the committed offline completion
receipt is `build/guardian/verification.json`. The separate, unfinished
`codex/hro-stage1` diagnostics branch was observed based on `3e359b8e`; its
changes are not included here and must reconcile this follow-up before its
own integration. Publication is to the existing Stage 3 remote branch;
`origin/main` remains `6d3ef15ff0145532ef193c42e3b648307ee6e915`.

## Chapter I Stage 2 native compatibility and combat recovery (2026-10-12)

State: **ENGINEERING_READY / QA_PENDING** on dependent branch
`codex/chapter1-stage2-native-combat-recovery`, based on Stage 1 commit
`63bf6a616e9031e33fb722a1e30f515abe6bb7c5`. Stage 1 and its INV1/Stage 0
ancestors are not connected-QA accepted or integrated. Build revision
`R262C1S2-CANDIDATE`, version `0.2.0-R262C1S2-CANDIDATE`, exact server
requirement `=0.7.0`. The [Stage 2 engineering report](release-migration/STAGE_2_NATIVE_COMPATIBILITY_AND_COMBAT_RECOVERY.md)
records the exact 0.7.0 native class pins, call-site restoration, artifact
SHA-256, tests, rollback, and connected-QA procedure. The matching patched
server and Hywind JAR are isolated in `build/` and have **not** been installed.
The source implementation commit is recorded in this branch's Git history;
the final exact branch HEAD, JAR hash, full offline check log, and Guardian
status are bound in the generated `build/guardian/verification.json` after
committed verification. The original Hywind release save, historical RPG save,
installed stock server, deployed INV1 JAR, and dirty primary checkout were
preserved. Owner-connected combat, protection, projectile, population, and
Stage 1 world QA remain pending. Do not merge dependent unaccepted work to
`main`; deployment requires separate authorization and a game-stopped,
hash-verified paired installation.

## Chapter I Stage 1 world and encounter recovery (2026-10-12)

State: **ENGINEERING_READY / QA_PENDING** on dependent
`codex/chapter1-stage1-world-encounter-recovery`, based on Stage 0 commit
`0ed8d6c80a84696a2d923771cfd100a661812daa` and the deployed INV1
source commit `afdf9bfbce9d5fb395bd68848833480a849f0e0b`. Build revision
`R262C1S1-CANDIDATE`, version `0.2.0-R262C1S1-CANDIDATE`, exact server
requirement `=0.7.0`. The Stage 1 report at
[world and encounter recovery](release-migration/STAGE_1_WORLD_IDENTITY_AND_ENCOUNTER_RECOVERY.md)
records the scoped code, evidence, tests, and owner QA gate. Exact final source
commit, package hash, and offline check log are independently bound in
`build/guardian/verification.json` after the final committed verification;
Git history identifies this report commit. No active `Hywind` or historical
`RPG` save, installed JAR, native server binary, combat formula, Elite rule,
or gear economy was changed. The INV1 JAR remains the active release candidate;
S1 is prepared offline for separately authorized deployment. Connected owner
QA remains pending; this dependent branch and its unaccepted ancestors must
not be merged to `main` yet. The older `AGENTS.md` save-target wording remains
untouched; the owner's explicit `Hywind` selection governs this work.

## Chapter I Stage 0 regression inventory (2026-10-12)

State: **ENGINEERING_READY, investigation only** on dependent
`codex/chapter1-stage0-regression-inventory`, based on the clean, published
`codex/chapter1-inventory-recovery` commit
`afdf9bfbce9d5fb395bd68848833480a849f0e0b`. The authoritative release
save is `UserData/Saves/Hywind`; the pre-release `RPG` save is historical
comparison only, per the owner's explicit clarification. The older `AGENTS.md`
save-target wording was preserved and its discrepancy is recorded in the
[Stage 0 inventory](release-migration/STAGE_0_BASELINE_AND_REGRESSION_INVENTORY.md).
The report identifies copied difficulty-registry/world-UUID drift, the missing
release native hook instrumentation, and a separate encounter context-capacity
rejection whose exact instantaneous condition remains unverified. It also
records two actual release Blizzard Health-loss receipts; a blanket skill
damage failure is not supported by this capture. No gameplay source, save,
native patch, JAR, version/revision input, or deployment was changed. No new
build or standalone/connected test was run. The INV1 candidate remains
deployed and QA pending; `origin/main` remains `6d3ef15f`, and neither this
report's dependent branch nor its unaccepted ancestors should be integrated
until their acceptance/dependency gates are resolved. The exact report commit
and remote publication are recorded in Git history after review.

## Chapter I inactive weapon-slot transfer correction (2026-10-12)

State: **QA_PENDING** on `codex/chapter1-inventory-recovery`, stacked on the
unaccepted `codex/chapter1-skill-bar` branch and its Chapter I startup, R262
bulk-attributes, and R261QA2 ancestors. The gameplay correction is commit
`685bcee20ab2526036497ec66a4c637856d611ab`, based on the documented
spatial bag recovery at `2f81120a987e6a1199cba664fe7e03da8aefebba`.
GitHub `main` remains `6d3ef15ff0145532ef193c42e3b648307ee6e915`.
The primary checkout and original RPG save remain untouched.

The owner inventory trace `inventory-trace-20261012T221822524Z-ebd847da.jsonl`
records four staff drops into `gear.weapon`. Each reached protected equipment
transfer with `ownershipMode=SPATIAL` and then returned `Slot is less than zero!
-1 < 0`. The same trace records successful legs and chest transfers. Chapter I
reports an inactive hotbar/utility slot as `-1`; the transfer read that index
directly. The installed release API's Hotbar decoder maps this sentinel to
slot zero when capacity exists. The correction resolves the same valid index
for hand/utility reads, selects it on a protected equip, and keeps an inactive
slot unavailable for unequip selection. It does not alter receipt or bag
custody. Both generated staffs remain in the owner's Hywind bag for QA.

`R262C1INV1-CANDIDATE` / `0.2.0-R262C1INV1-CANDIDATE` targets the installed
Chapter I `0.7.0` API. The focused inactive-slot regression failed before the
fix and passed afterward; the 16 receipt/transaction tests also passed.
The exact-head offline check, package validation, Guardian receipt, remote
publication, and deployment result are recorded at the final documentation
HEAD and in the external deployment receipt. Connected weapon, offhand,
armor, and bag transfer behavior still requires owner QA; no standalone
Hytale server or client is launched by Codex. Do not integrate this branch or
its unaccepted ancestors into `main` until connected acceptance.

## Chapter I inventory owner-state recovery (2026-10-12)

State: **QA_PENDING** on `codex/chapter1-inventory-recovery`, stacked on
`codex/chapter1-skill-bar` at `d4b2a333b9b601253c25dd41fdd44b1907681370`.
GitHub `main` remains at `6d3ef15ff0145532ef193c42e3b648307ee6e915`.
The owner reported that generated gear entered the native hotbar and equipped
gear could not be dragged into Hywind's empty inventory. The Chapter I Hywind
player save lacked `RpgSpatialBag`; the protected pre-release RPG save for the
same player had a `MIGRATION_PROOF` bag with two entries. Existing gear delivery
and inventory UI code select the native fallback when this component is absent.
This is a copied-save state omission, not a missing BAR3 class or new gameplay
implementation requirement.

With the game stopped and the owner's selection to restore the RPG bag contents,
the Hywind player file was backed up outside `Saves` and atomically replaced
with a version adding only the original `RpgSpatialBag` component. Its other
fields, including two current hotbar items, are semantically unchanged. The
original RPG file is unchanged. The restored Hywind player SHA-256 is
`67C4B300F30A0A53296F5E79E1C1C97CD3291724C54B0153BF66CDF6FA95F250`;
the pre-repair backup SHA-256 is
`153B3004EF887F170845E6D2738C4D35F4F58C6222610A30FB33B51CB1FE8264`.
Recovery files are under `AppData/Roaming/Hytale/HywindJarBackups/Hywind-spatial-repair-20261012T180043`.
The deployed BAR3 `Hywind.jar` remains the exact previously verified candidate,
SHA-256 `4E1E5ED201D6B6A49CE0672B53FC55C1837446EC82BE747782ECD7738B4736BA`,
version `0.2.0-R262C1BAR3-CANDIDATE`, revision `R262C1BAR3-CANDIDATE`,
with exact `=0.7.0` metadata and packaged spatial inventory classes. There
is no new binary revision or source code change. No standalone server or client
was launched. Owner QA must confirm the restored bag contents, generated gear
delivery, and equipment drag-and-drop. Prior native `Window id ... is invalid`
warnings and BAR3 skill casting remain unverified in connected play. Do not
promote this branch or its unaccepted ancestors to `main` yet.

## Chapter I skill bar owner admission preload correction (2026-10-12)

State: **QA_PENDING** on `codex/chapter1-skill-bar`, stacked on the unaccepted
Chapter I startup, R262 bulk-attributes, and R261QA2 branches. GitHub `main`
remains at `6d3ef15ff0145532ef193c42e3b648307ee6e915`; the primary
checkout and both world saves were not changed. The deployed candidate remains
BAR2 until the owner authorizes replacing its JAR. The BAR3 gameplay source is
`4afd67d3a193c0d70eca4dbd6bef77dc77c094d3`.

The owner's BAR2 launch loaded schema-13 player state and joined the world,
including a later direct join to `default`, but emitted no native skill
projection or `HUD_LAYOUT_READY`. The screenshot shows a vanilla-only HUD
without the RPG revision label, while CanvasUI still shows Cyclone Armor and
Blizzard in skill01/skill02. This rules out missing skill assignments and
demonstrates that BAR2's instance-world guard was not the sole cause. The
active HyARPG entrypoint enables durable Cyclone by default, but omitted the
pre-connect preload hooks that the older Phase00 entrypoint already registered.
`HytaleSupportSystem.connectionReady` requires prepared Cyclone and shared
leases; without those hooks its `attachPrepared` calls reject admission, so
the owner installer cannot reach native abilities, stats, or the custom HUD.
This gate is established by source and the absent runtime projection; the
corrected path still requires connected confirmation.
The same wiring defect was present before the Chapter I migration.

`R262C1BAR3-CANDIDATE` / `0.2.0-R262C1BAR3-CANDIDATE` restores the two
existing preload hooks in the production entrypoint. A focused production
entrypoint regression failed before the fix and passed after it, alongside
Chapter I admission and durable-ledger tests. The full offline `check` and
JaCoCo run passed 4,162 JUnit tests in 467 reports with zero failures or skips.
Strict SpotBugs, CustomUI, release asset, and JAR package validation passed;
the package contains 19,544 entries and 2,255 classes with exact `=0.7.0`
server metadata. Guardian verified and published the BAR3 branch. The final
documentation HEAD still requires its exact package verification and branch
publication. Connected HUD, E/R casting, and world-transfer behavior require
owner QA; no native server or client was launched by Codex. The BAR2 log also
contains a later native inventory-window error, separate from the absent HUD
at join. Existing asset-key format warnings and dormant patch-version warnings
remain outside this skill-bar correction.

## Chapter I skill bar instance admission correction (2026-10-12)

State: **QA_PENDING** on `codex/chapter1-skill-bar`. The correction source is
`12b729afe8db35c9b4dc69edca0dc186d07fdcab`, continuing the previous
skill-bar candidate `86488834de7b5ca5245c7ae0a1f88ea6b1be66dd` and its
unaccepted `codex/chapter1-startup`, R262 bulk-attributes, and R261QA2
ancestry. GitHub `main` remains at `6d3ef15ff0145532ef193c42e3b648307ee6e915`.
The primary checkout's unrelated changes and the pre-release RPG save remain
untouched. The exact packaging commit is verified after this record is committed.

The owner launched deployed `R262C1BAR1-CANDIDATE` in the existing `Hywind`
save. The 2026-10-12 12:34 server log shows the player joining a newly created
CreativeHub instance after the pre-connect preparation, while the plugin's
initial owner installer required the pre-connect world UUID to equal the
player's actual world UUID. The plugin recorded a successful schema-12 to
schema-13 migration and load, but no native ability projection or
`HUD_LAYOUT_READY` event. The saved schema-13 state still contains
`spirit_shield` and `blizzard` in skill01/skill02. The exact runtime guard
failure is inferred from this sequence and the source; it requires connected
confirmation. A native `Window id 0 is invalid` exception occurred later
during inventory handling, after the player had joined, and is not the first
cause of the missing skill HUD.

The `R262C1BAR2-CANDIDATE` / `0.2.0-R262C1BAR2-CANDIDATE` correction lets
only the initial prepared admission install in the world actually entered.
A later `PlayerReadyEvent` still binds to an exact world, and the current
connection and generation guards still reject stale sessions. Focused
instance-admission and pre-connect tests passed. Full offline `check` and
JaCoCo passed 4,162 JUnit tests in 467 reports with zero failures or skips;
instruction/line/branch coverage is 51.1%/48.6%/40.4%. Strict SpotBugs,
CustomUI, release asset, and package validation passed. The existing six
reviewed dormant native-patch findings remain in the SpotBugs baseline.

The installed `Hywind.jar` is still the BAR1 candidate pending authorization
for a BAR2 deployment. No server or client was launched by Codex. Connected
E/R projection, skill activation, and HUD presentation remain unverified.
Next: verify and publish the exact BAR2 package commit, then request owner
authorization before replacing the installed candidate. No integration into
`main` is appropriate while this feature and its dependencies await QA.

## Chapter I skill bar and four-slot QA candidate (2026-10-12)

State: **QA_PENDING**. `codex/chapter1-skill-bar` is stacked on the published
Chapter I startup checkpoint `133dba62e2da20195515feade652d902ad2ce3b6`
(`codex/chapter1-startup`). Its gameplay source ends at
`2fb8b5c340d32b9864245bc2dc9f78e2a436ec68`, including source changes
`41e327c2578eec183ccac2d5937a53b3bb81f5ed` and
`495ad4ebf6d96d94b8380361f9282b222ff398a7`. This branch still depends
on the unaccepted R262 bulk-attributes and R261QA2 ancestry. GitHub `main`
was `6d3ef15ff0145532ef193c42e3b648307ee6e915` when work began; neither
this branch nor its ancestors are accepted for integration. The primary
checkout's unrelated changes were preserved.

The provisional build identity is `R262C1BAR1-CANDIDATE` /
`0.2.0-R262C1BAR1-CANDIDATE`, targeting the installed Chapter I `0.7.0`
release server and assets with exact manifest requirement `=0.7.0`.
The observed Stage 1 owner launch joined the existing `Hywind` world and
loaded schema-12 skill01/skill02 assignments but emitted no native ability
projection or RPG HUD-ready event. The production pre-connect completion now
arms owner-thread installation; installation waits for `AbilitySlots` instead
of succeeding without that component. The native Ability2/3 dispatch also
requires the release ability-container section. Offline native-container
tests cover assignment, projection, dispatch deduplication, reassignment,
and clearing. The exact connected cause and restored E/R behavior still
require owner QA.

Chapter I retains two independent core Runestone cells, used by Hywind
skill01/skill02. Ability4 is a held-item interaction, including Hywind's
crossbow reload, and the public release API supplies no independent fourth
Hywind skill input. Skill03 and the new skill04 are therefore assignable,
saved, and visibly **UNBOUND**, with no false activation route. CanvasUI,
link validation, build transfer, authoritative HUD view models, cooldown
overlays, and commands now cover all four logical slots. The custom 3/4 HUD
cells sit clear of native Signature and the two native ability cells.
Schema 13 expands a valid three-entry loadout with an empty fourth entry,
preserves an already populated fourth entry, rejects malformed array sizes,
and keeps the repository's pre-migration checkpoint safeguard. No player
save was modified during engineering.

The source check before the final HUD placement passed 4,161 JUnit tests
across 466 reports with zero failures or skips. Focused tests and CustomUI
validation passed after the final source change. Strict SpotBugs passed after
six exact findings in dormant pre-release native-patch hooks were added to
the reviewed SpotBugs baseline; those hooks were not changed in this task.
The exact documentation-HEAD full check, JaCoCo report, package validation,
and Guardian completion receipt are required before publication or deployment.
The installed Stage 1 JAR and both RPG saves remain unchanged. No server or
client was launched by Codex; connected HUD, binding, layout, and cast QA is
pending. Next: publish the verified branch, then deploy only with task
authorization and have the owner test in the normal `Hywind` world.

## Chapter I Stage 1 startup candidate (2026-10-12)

State: **QA_PENDING**. Branch `codex/chapter1-startup` starts from the
R262QA1 candidate `codex/bulk-attributes` at
`69483c6b8071822656bed092636797ae8281fe46`; it depends on that unmerged
feature and its R261QA2 ancestor. At task start, GitHub `main` was
`6d3ef15ff0145532ef193c42e3b648307ee6e915` and the primary Windows
checkout was 76 commits behind it with unrelated artwork and `AGENTS.md`
changes. Those checkouts were preserved. No Stage 1 merge is authorized by
deployment; connected acceptance of this feature and its dependencies remains
pending.

The Stage 1 source commit is `220b84f847687244b341d93990788ec2a336f4ed`.
The provisional build identity is `R262C1S1-CANDIDATE` /
`0.2.0-R262C1S1-CANDIDATE`, compiled against the installed Chapter I
`0.7.0` release server (SHA-256
`C3B5985B9E72727BF3BCF220923D7E61E78FE0F37CD1CD9C2A73F51E4F408FF8`)
and release `Assets.zip`. The manifest requires exactly `=0.7.0`. The
pre-release server patch was compile-only and absent from the copied JAR, so
`NativePopulationProjectionHook` caused the initial setup failure. The
release build uses the existing population projection through an ordered
ChunkStore tick and no longer requires that patched-server class at setup.
The old native final-selection seam is unavailable on the stock release
server; native unspawnable-chunk recalculation can reset weights for that tick.
The candidate logs this limitation. Other pre-release native patch seams for
Packbound, damage receipts, and projectile receipts are not active on the
stock release server; their dependent promotion gates remain closed and need
separate gameplay migration and connected QA. No gameplay acceptance is
claimed.
The U7P5 frozen pre-release asset comparison remains a pre-release-only
`check` gate; Chapter I release checks retain the current asset, package,
CustomUI, native metadata, campaign biome, and spatial grant validators.
The pre-release Larva binding fixture, which requires the old patched server
JAR and exact pre-release asset hashes, is excluded from release `test` only;
its runtime role and saved binding schema are unchanged.

The release asset audit found the new stock
`Dungeon_Klops_Scrap_Trader` native barter route. Its packaged role is the
stock release role with only `OpenBarterShop` replaced by the existing
`HywindOpenBarterShop` spatial owner gate. The asset comparison and spatial
grant audit pass. One healing probe live-save guard now targets the release
`UserData/Saves/Hywind` folder; the original pre-release `RPG` save and player
data were not modified.

Offline verification: release-API `compileJava`, native proc metadata audit,
CustomUI validation (88 documents), campaign biome coverage, spatial grant
asset audit, exact server-version package validator (19,544 entries, 2,255
classes, zero ImmersiveNPCs payload entries), and focused JUnit tests passed.
The focused test reports include five population plan, two release population
projection, and one R261QA2 startup registration test, all with zero failures.
No standalone server or connected client was launched. No newer server log is
available, so startup, world loading, and player join remain unverified.

The exact-commit JAR SHA-256 is
`797AA45221AF9ADF9004508843C3F9AD5B84AE6F1BA15A961F9FA2D42129A72E`.
The prior R262QA1 JAR (SHA-256
`6873F21FD266E1AA1D6B3EB634CA2254E85C97B111EA23BA98A4B7F00A80A314`)
was backed up outside `Saves` at
`C:/Users/Zemio/AppData/Roaming/Hytale/HywindJarBackups/Hywind-R262QA1-before-Chapter1-20261012T110639.jar`.
Only `UserData/Saves/Hywind/mods/Hywind.jar` was replaced. Its checksum,
manifest, and embedded source commit were verified after deployment. Next:
owner launches Chapter I, enters the `Hywind` world, and supplies the new log
or startup findings. Do not integrate this dependent branch until connected
acceptance and dependency review.

## R262QA1 bulk attribute allocation QA candidate (2026-10-11)

Branch `codex/bulk-attributes` starts from GitHub `main` at
`6d3ef15ff0145532ef193c42e3b648307ee6e915`, including the deployed
R261QA2 gameplay source `b15aa49d1a3c61a0f88ec168494d4f15c4154880`.
R261QA2 connected acceptance remains owner QA, so this is a dependent
development candidate and must not be integrated into `main` before its own
connected acceptance and dependency review. The candidate revision is
R262QA1-U7P5B / `0.2.0-R262QA1-U7P5B`.

The Inventory workspace gains +1, +5, +25, and Max controls in the existing
attribute header. Server-side allocation clamps one requested transaction to
unspent points and raw capacity, then persists one revised state. The current
native CustomUI event surface has no reliable button-press state or modifier
snapshot for this control; hold and Ctrl shortcuts are therefore omitted.
The gameplay source commit is `4f33de61e22101b1b9303618109871f83cc76a07`.
Focused regression tests passed (59 tests), followed by the exact-commit
offline gate: 4,156 JUnit tests in 466 reports, JaCoCo, strict SpotBugs,
native package and asset validation, and Guardian verification. The verified
JAR SHA-256 is
`6873F21FD266E1AA1D6B3EB634CA2254E85C97B111EA23BA98A4B7F00A80A314`.
Guardian published `codex/bulk-attributes` at the gameplay commit; GitHub
`main` remains at `6d3ef15ff0145532ef193c42e3b648307ee6e915` pending
connected QA and dependency review.

The prior R261QA2 JAR was backed up outside `Saves` at
`data/pre-release/HywindJarBackups/Hywind-R261QA2-before-R262QA1-20261012T030311Z.jar`.
Its SHA-256 matches the prior installed artifact:
`56BB3F27DFE5BB25F2915A59E6EC9C55C08D7C03D1AB77892A24FDEFA045BA2A`.
Only `Saves/RPG/mods/Hywind.jar` was replaced. The installed JAR matches the
verified R262QA1 checksum, manifest revision, production entrypoint, and
embedded gameplay source commit; offline package validation passed on the
installed file. Player-save data was untouched and Hytale was not launched.
Connected startup and gameplay acceptance remain owner QA.

## R261QA2 startup registration hotfix (2026-10-11)

The first connected R261QA1 launch failed at native system registration:
`SupportDamageSystems.BeforeApply` declared an `AFTER ManaBeforeLife` dependency,
but the production entrypoint did not register `ManaBeforeLife`. Hytale validates
all dependency targets when each system is registered. Registering Mana first
also required removing its redundant forward `BEFORE BeforeApply` edge; the
`BeforeApply` `AFTER ManaBeforeLife` edge retains the intended order. The QA2
entrypoint now registers Mana Before Life with the existing combat kernel's
resource service before BeforeApply. A native `ComponentRegistry` regression
reproduced both missing-target failures and verifies Mana precedes BeforeApply
and native Health application. Later production system registrations were
audited for missing targets. The durable Cyclone gate remains enabled by
default, with an explicit false opt-out. The gameplay source branch
`codex/r261-qa2-startup-registration` and GitHub `main` reached commit
`b15aa49d1a3c61a0f88ec168494d4f15c4154880`, revision R261QA2-U7P5B /
`0.2.0-R261QA2-U7P5B`. Focused tests and the exact-commit offline Gradle
gate passed 4,149 JUnit tests in 466 reports, JaCoCo, strict SpotBugs,
native package and asset validation, and Guardian completion. The verified
JAR SHA-256 is
`56BB3F27DFE5BB25F2915A59E6EC9C55C08D7C03D1AB77892A24FDEFA045BA2A`.
The prior R261QA1 JAR was backed up outside `Saves` at
`data/pre-release/HywindJarBackups/Hywind-R261QA1-before-R261QA2-20261012T021123Z.jar`;
the backup matches its SHA-256
`4F610DD10371C4FFB8312A71952EE63A1DFA06C7A7458BC34E5FCB953667B635`.
Only `Saves/RPG/mods/Hywind.jar` was replaced. The installed JAR matches the
verified QA2 checksum, manifest revision, production entrypoint, and embedded
gameplay source commit; package validation passed on the installed file.
Player-save data was untouched and Hytale was not launched. Connected startup
and gameplay acceptance remain owner QA.

## R261 first connected QA candidate (2026-10-11)

The approved R261QA1-U7P5B deployment correction makes durable Cyclone Armor the
normal production path in `HyArpgPlugin` without launch flags. Only an explicit
`-Dhywind.cyclone.durable=false` selects the R260 compatibility path. The
earlier SHA-256 `F1299CEFF598DD4EE67B85D95C52FD2708567FE8BA19E3483906BC70FD9D2359`
candidate had its production gate wired but defaulted off and is superseded.
The corrected JAR was built from implementation commit
`26b0946206852f9a9ab80bd03aecf73b0230a55d` at revision
R261QA1-U7P5B / `0.2.0-R261QA1-U7P5B`, SHA-256
`4F610DD10371C4FFB8312A71952EE63A1DFA06C7A7458BC34E5FCB953667B635`.
The exact-commit offline gate passed 4,148 JUnit tests in 465 reports with
zero failures, errors, or skips, plus JaCoCo, strict SpotBugs, native package
and asset validation, offline ReadyPath tests, and Guardian verification.
The task branch and GitHub `main` both reached that implementation commit by
non-force publication. ReadyPath/JFR remains optional.

Before the correction, GitHub `main` was fast-forwarded non-forcibly to
`0183f2acae08c79d30a0d477c71bdd5544f95bf6`. The R260 JAR backup at
`data/pre-release/HywindJarBackups/Hywind-R260-before-R261-20261012T004415Z.jar`
matches the installed R260 SHA-256
`487C9B89274496C329A24D65C13F232ED9E01E5A4A5430E0DD5D0F808BFB23FD`.
The separate save backup at
`data/pre-release/RpgSaveBackups/RPG-before-R261QA1-20261012T011447Z`
contains 23,266 files and 2,830,076,488 bytes, each verified byte-for-byte
against the RPG source. Its sole excluded reparse point is a junction to the
separate `ImmersiveNPCs` save's voice environment. Player-save data was not
modified. The approved deployment replaced only `Saves/RPG/mods/Hywind.jar`;
its installed SHA-256, production entrypoint, and embedded source commit match
the verified implementation artifact above. The R260 JAR and RPG save backups
remain outside `Saves`. No Hytale process was launched for deployment.
Connected gameplay acceptance remains owner QA.

Branch `codex/r261-qa-capture` starts from GitHub-integrated R261 mainline
`e42b903291409bde99fde985e8e07174cbb92401`. Capture-tooling commit
`1889c0d0999c01a6c0616b1a8b8b685e8e350180` added the combined ReadyPath
JFR and durable Cyclone launch option. Package inspection then found that the
durable gate and trusted-player command existed only in excluded legacy
`Phase00Plugin.java`; the production `HyArpgPlugin` omitted their wiring.
The R261 package from `e42b903291409bde99fde985e8e07174cbb92401` must
not be used for connected QA. A focused compiled-entrypoint regression failed
before the correction and passed afterward.

Production wiring was corrected at implementation commit
`d6252d7e08eec9deb6697a21721d46c3bc062137`; the candidate revision is
R261QA1-U7P5B / `0.2.0-R261QA1-U7P5B`. The previously verified artifact was
`build/release/Hywind.jar`, SHA-256
`F1299CEFF598DD4EE67B85D95C52FD2708567FE8BA19E3483906BC70FD9D2359`.
The exact-implementation-commit offline `check` passed 4,147 JUnit tests in
465 reports, with zero failures, errors, or skips; strict Guardian SpotBugs
and the native package/asset validator passed. The offline startup-capture
test passed process matching, actual Java inheritance of both properties and
JFR startup, recording readiness/recovery, legacy JAR rejection, and
true/false/missing/ambiguous gate markers. Unflagged `-CheckOnly` passed;
flagged `-CheckOnly` correctly rejects the still-installed R260 JAR.

Before the authorized deployment, the active RPG save contained R260 `Hywind.jar`, SHA-256
`487C9B89274496C329A24D65C13F232ED9E01E5A4A5430E0DD5D0F808BFB23FD`.
An identical backup was made outside `Saves`; player-save data was untouched.
That artifact was not deployed. The optional capture will require
`RPG_CYCLONE_DURABLE_GATE enabled=true` in the matched integrated-server log;
that live observation and connected gameplay acceptance remain owner QA.
Final tooling commit and GitHub publication are established by Guardian's
completion receipt rather than inferred from this record.

## R261 Final engineering candidate (2026-10-11)

Branch `codex/r261-final` continues the published R261FDEV checkpoint
`60e718532b463cbb0ffc8503f0ae0049d55c56e6` and contains the frozen
Reflective Ward journal, durable Shared Aegis recipient journal, native Mana
payment reconciliation, and mutually consented trusted-player ally authority.
The reviewed R261 Final implementation source commit is
`2e34548e961b412825afbe876038bf2b716df270`, with the final caster-death
eligibility correction at `73fca57fc8463c47d2b99c4aa2e18a809f56405e`.
Build revision/version are R261-U7P5B / `0.2.0-R261-U7P5B`.
The [R261 Final matrix](R261_FINAL_MATRIX.md) maps implementation and
regression evidence; [the acceptance checklist](QA-R261-FINAL.md) is reserved
for the owner's later connected-game QA. This candidate has not been deployed.
The active RPG save and R260 `Hywind.jar` are unchanged. The exact source
commit, final build SHA-256, and GitHub integration status are checked at the
release gate rather than inferred from the revision label.

At the published implementation source `da4f49cca5eb6cc88a7b7d13cd31e15ce8841a49`,
the complete offline Gradle `check` passed 4,146 JUnit tests across 464 reports,
with zero failures, errors, or skips. JaCoCo completed; line coverage was
90.1% for `CycloneReflectionJournal`, 87.3% for `CycloneSharedAegisLedger`,
and 83.3% for `TrustedPlayerAllies`. JaCoCo and strict SpotBugs ran on the
code-identical source before the README-only commit; native package and asset
validation and Guardian's exact-commit receipt passed at the published HEAD.
Guardian confirmed
task-branch publication, and a normal non-force fast-forward placed that
source on GitHub `main`; a second offline `check` passed after the push. The
native ECS presentation and connected combat behavior remain for owner QA.

## R261 Final development checkpoint (2026-10-11)

Branch `codex/r261-final` starts from the published R261-C commit
`e1c5562728b4617ac2dfab4c3a8f10445cc36b92`, with R261-A, A.1, and B
in ancestry. The reviewed R261FDEV implementation source commit is
`3d38e675b35abb3fd785d49517e941200f57f261`. The development-only build
identity is R261FDEV-U7P5B /
`0.2.0-R261FDEV-U7P5B`. [The requirement audit](R261_FINAL_MATRIX.md)
records what has been confirmed offline and the remaining release blockers.
It includes a native Mana value/marker recovery path for plain durable
Cyclone, while Reflective Ward reconnect context and Shared Aegis durable
recipient protection remain incomplete. The feature gate stays off by
default. This is **not** a completed R261 revision and must not be merged to
`main` or deployed as the owner's first R261 QA build. The active RPG JAR and
player saves remain R260 and untouched. Exact source commit, verification,
package hash, and publication status are established by Guardian after this
entry is committed; this record does not predeclare them.

## R261-C progression, Mana Before Life, and resource HUD development (2026-10-11)

Branch `codex/r261c-progression-mana-hud` starts from published R261-B
`bfa9a6c25a5658dfd10b21a8dd2227ae22daf0b7`. R261-A and A.1 are
ancestors. Build identity is R261C-U7P5B / `0.2.0-R261C-U7P5B` once the
implementation is committed and Guardian verifies the exact commit.
The [R261-C contract](R261C_ENGINEERING.md) describes the level 1–99
Health curve, effective STR/INT coefficients, versioned armor affix,
post-barrier native Mana conversion, and read-only resource presentation.
The [R261-C offline evidence map](QA-R261C-DEVELOPMENT.md) identifies the
focused checks and remaining R261 Final acceptance boundaries.
The active RPG save and R260 JAR remain untouched. R261 Final must review
the known reconnect, Shared Aegis, and damage-thread latency limitations.
Exact verification, package checksum, and publication are established by
Guardian after this record is committed, not by this in-progress text.

## R261-B live Cyclone Armor candidate (2026-10-11)

Branch `codex/r261b-live-cyclone` starts from published R261-A.1
`90ed801a53b7be45498a5a5f07cd931ac87e6104`; both R261-A and A.1
are ancestors. Its reviewed gameplay implementation commit is
`dc67e78265316a3ad5a359a7de8548e855b403e6`. Build revision/version
are R261B-U7P5B / `0.2.0-R261B-U7P5B`. The immutable JVM property
`hywind.cyclone.durable=true` enables the new path only in a protected
test launch; omission retains R260 gameplay. The deployed active RPG save
and its R260 `Hywind.jar` remain untouched.

The live cast now uses the R261-A.1 coordinator and a single durable
generation. It requires at least 100 spendable native Mana and spends the
whole unreserved amount at final commit. Capacity starts at 100, gains
effective WIS/INT above the canonical baseline, scales to exactly +60%
at skill level 20, and is frozen for the generation. The native damage
bridge forces a debit before reducing post-mitigation damage; confirmed
absorption precedes finite shields, Managuard, and Health. Reflective Ward
uses the actual confirmed absorbed amount. An infinite native effect
provides the active icon and particles; reconnect forfeits surviving
protection without a second Mana debit. Shared Aegis-linked durable
casts reject before payment until a durable ally design exists.

Fourteen focused live-cast cases, the focused native debit-bridge cases,
and the existing Cyclone durability tests passed during implementation.
Strict SpotBugs passed before the implementation commit. Offline bridge
profiling on this host measured sequential one-player p50/p95/p99/max
2.250/2.497/3.196/6.381 ms; eight contending threads on one player
2.284/69.919/154.070/160.398 ms; eight independent players
3.592/4.792/5.108/5.122 ms. Those timings exclude full ECS work and do
not establish connected-game frame safety. The gate stays disabled by
default and connected acceptance belongs to the owner. The exact
documentation HEAD, full offline `check`, coverage, package validator,
strict static analysis, JAR SHA-256, and remote publication are established
by the final Guardian verification receipt and completion check, rather
than inferred from this implementation commit. See the
[contract](CYCLONE_DURABILITY.md) and [protected QA notes](QA-R261B-CYCLONE.md).

## R261-A.1 Cyclone durability hardening (2026-10-11)

Branch `codex/r261a1-cyclone-hardening` starts from published R261-A
`e418e1ee1fa430a288b45307310ebe7cc2197912`. The implementation
source commits are `896b750b20434984ce52fe1fdc8f4291b63f9975` and
`52554e223554d3df0fe4ab185e8e40c9f336c959` (final implementation).
Build revision/version are R261A1-U7P5B / `0.2.0-R261A1-U7P5B`.
The [durability contract](CYCLONE_DURABILITY.md) now specifies that
reconnect with an uncertain native Mana payment durably invalidates the
barrier without another debit or an unproven refund. The existing cooldown
authority and activation history remain intact. The feature gate remains
disabled and R260 is still the deployed RPG build.

Thirty-three focused Cyclone durability JUnit cases passed, including native Mana
rollback and retained-payment scenarios, cooldown recovery, reconnect
races, forced-write failure, equal-revision journal conflict, capacity
ceilings, and multi-player latency profiling. The
240-hit local profile measured repeated p50/p95/p99/worst at
2.267/3.589/4.014/5.300 ms; eight concurrent threads on one player at
2.297/70.534/93.142/96.398 ms; and eight independent players at
3.580/4.119/4.553/4.622 ms. These are offline measurements with run-to-run
variance, not connected-game latency guarantees. Full offline verification,
package validation, strict static analysis, and exact-commit publication
are reported by Guardian's receipt and completion check for this branch.
The active `RPG/mods/Hywind.jar` remains the R260 artifact; no R261-A.1
build is deployed and no player save is modified. Connected-game QA is
pending R261-B integration and owner testing.

## R261-A durable Cyclone architecture (2026-10-11)

Branch `codex/r261a-durable-cyclone` starts from the verified R260 baseline
documentation commit `ac33ff4c30848079eddc97f590921c0f98ec81c5`.
Its implementation source commit is
`609a78e5f64777b4b1ab0c48bce4a7915bce4c8e`.
Build revision/version are R261A-U7P5B / `0.2.0-R261A-U7P5B`.
The [durability contract](CYCLONE_DURABILITY.md) describes the versioned
per-player journal, forced damage debit, conservative payment recovery, and
R261-B integration boundary. R261-A's feature gate is disabled, so R260
Cyclone gameplay and the deployed R260 JAR remain authoritative.

The 25 focused Cyclone durability JUnit cases passed, including crash and
checkpoint fault injection. At the implementation commit, the complete
offline Gradle `check` passed 4,076 JUnit tests across 450 reports in 5m31s,
with zero failures or skips. Strict SpotBugs passed after the
reviewed baseline recorded 15 existing internal collaborator-reference
findings; no new Cyclone finding remains. Guardian's 32 fixture cases passed,
including an old-commit/fresh-reflog regression for its baseline-age check.
The active `RPG/mods/Hywind.jar` retained SHA-256
`487C9B89274496C329A24D65C13F232ED9E01E5A4A5430E0DD5D0F808BFB23FD`.
No R261-A build was deployed; player saves and connected-game QA remain
untouched. The final exact-commit verification receipt and live publication
status are established by Guardian after this record is committed.

## R260 Cyclone Armor barrier rebalance QA deployment (2026-10-11)

Published task branch `codex/cyclone-barrier-rebalance` starts from R259
documentation commit `d72ad29b32d8b34b4552ced8d4b8315e6a40b0be`; its
deployed gameplay build source is `8d392e9199dc9a751f05c98349d6e6777a580c7d`.
It retains the R259 encounter-store correction. Build revision/version are
R260-U7P5B / `0.2.0-R260-U7P5B`. Cyclone Armor now takes flat gear Healing
Power plus 0.35 per effective Wisdom and 0.15 per effective Intelligence
point above the starting effective attribute, then applies the existing
skill-level factor and downstream barrier modifiers once. It no longer
multiplies by Wisdom-derived Healing Power. Saved skill identity and barrier
lifecycle are unchanged. The existing skill trace now reports the capacity
calculation; absorption traces already report damage absorbed and remaining
capacity. See [the balance table](CYCLONE_ARMOR_BALANCE.md).

Focused Cyclone, gear, barrier, healing, support, and attribute JUnit suites
passed offline. Full offline Gradle `check` passed 4,051 JUnit tests across
449 reports; strict Guardian SpotBugs passed, the R260 package validator
passed, and Guardian recorded the matching release receipt and verified
GitHub task-branch publication. On 2026-10-11 at 14:18:30 EDT, the R260
candidate replaced `RPG/mods/Hywind.jar`. Deployed SHA-256 is
`487C9B89274496C329A24D65C13F232ED9E01E5A4A5430E0DD5D0F808BFB23FD`;
the deployed JAR embeds source commit `8d392e91`. The prior R259 JAR is
backed up outside `Saves` at
`data/pre-release/HywindJarBackups/Hywind-before-R260-20261011T181830Z-044835B05404.jar`
with its original SHA-256
`044835B0540478000CA9FDDB3D20D746D0D20785F66FF77ADD7FC66E7BD1E1CE`.
No standalone server was launched, and no player save data were changed.
GitHub `main` integration and connected-game acceptance remain pending.

## R259 encounter-store contention correction (2026-10-11)

The published `codex/ready-path-gear-lock` branch contains gameplay commit
`98576f40d4e8a167a2c117bb3d279a387a7996e6` and deployed build commit
`2c20529767a5c776067050dd5bcf99b04b34551d` (R259-U7P5B,
`0.2.0-R259-U7P5B`). In the owner's valid integrated-server JFR under
`StartupCaptures/20261011T165547Z-9ff067cb`, a gear receipt scan held the
`FileEncounterStore` monitor for 6.925 seconds while reading receipt JSON.
Main-thread `configureGearDelivery` waited 6.89 seconds for that monitor,
and encounter-effect workers also waited. The active save contains 2,082
gear receipt files. The scan now snapshots its file list and reads records
in batches of 16, releasing the monitor between batches; checksummed record
validation and atomic file replacement remain in place. A targeted
concurrency regression confirms that encounter configuration and a second
durable gear write proceed during a paused scan, then verifies the read
receipts. Focused encounter/gear JUnit and the full offline Gradle `check`
passed at the deployed source commit; Guardian recorded matching verification
and confirmed the GitHub task branch publication. On 2026-10-11 at 13:26 EDT,
the candidate was deployed as `RPG/mods/Hywind.jar`, SHA-256
`044835B0540478000CA9FDDB3D20D746D0D20785F66FF77ADD7FC66E7BD1E1CE`.
The previous JAR, SHA-256
`3DA33D47F2990B25FE99EFB6CEAD8543630054D35193932F261BD2F51648CA12`,
has checksum-verified backups under `data/pre-release/HywindJarBackups`,
outside `Saves`. The deployed package validator passed; its embedded source
commit matches `2c205297`. The active RPG player/save data were untouched,
and no Hytale server was launched. GitHub `main` integration and connected
game acceptance remain pending.

The server completed boot at 12:56:37 EDT. The client then spent 46.875
seconds in `RequiredAssets` from 12:56:38.939 to 12:57:25.823 EDT. The
server JFR demonstrates the pre-boot lock contention but does not attribute
the later client asset wait. Client CPU/file-I/O evidence is needed before
claiming a fix for that separate interval.

## ReadyPath capture correction (2026-10-11)

Startup diagnostic source on `codex/startup-bottleneck-capture` is commit
`9181d3b4551feb0fdabd691737989d3c9ab45e89`, based on the R258
diagnostic checkpoint below. Windows PowerShell 5.1 previously terminated
when `jfr summary` wrote `file is empty` to stderr while the JFR was
still being produced. The corrected capture matches the new integrated
server to the new Hytale client by PID, process creation time, bundled Java
and native server JAR; waits for a finished, stable JFR; retries missing,
empty, incomplete and locked files; and writes a failure manifest if no
valid recording arrives. Matching client and RPG server logs are copied
into each capture folder outside `Saves`. The owner's first failed run
finalized as a valid 38-second JFR, recovered with log snapshots and
checksums in `StartupCaptures/20261011T150803Z-8909cada`.

Offline PowerShell 5.1 syntax checks, `-CheckOnly`, synthetic process
identity checks, and generated JFR missing/empty/incomplete/locked/valid
tests passed. An offline log-copy fixture passed. A one-minute no-server
timeout produced a `FAILED` manifest with the reason, retained its
capture folder, and removed `JAVA_TOOL_OPTIONS` as expected. Guardian
change and commit checks passed. Guardian's gameplay completion gate
requires a matching build receipt, so it remains unverified for this
diagnostics-only commit. The branch remains local and unpublished. No Hytale server
was launched for verification. This is a startup diagnostics correction
only: build revision/version remain R258-U7P5B /
`0.2.0-R258-U7P5B`; no gameplay JAR or RPG save was changed.
The corrected capture awaits the owner's next connected launch.

## Startup bottleneck capture checkpoint (2026-10-11)

Branch `codex/startup-bottleneck-capture` starts from verified R258
documentation commit `21292be88598dde1a3e39a60d1eb5892c1f6d98b`.
The diagnostic source commit is
`7d4b7fdf52843a52816ff162f17431e149d24a41`.
It adds an owner-run, opt-in Java startup JFR/ReadyPath capture and the
[startup investigation](STARTUP_BOTTLENECK_INVESTIGATION.md). Five existing
R256 integrated-server logs show one 29.463-second boot and four
18.708–20.626-second boots. A variable approximately nine-second interval
falls within RPG plugin setup, but no JFR or thread evidence identifies its
cause; none of those logs measures the current R258 JAR. The bundled Java 26
runtime successfully created a short startup JFR offline. PowerShell 5.1
`-CheckOnly` and `-Restore` passed; the temporary environment setting was
confirmed removed. Guardian preflight and change/commit checks passed.
No gameplay source, packaged assets, save data or deployed JAR changed.
The build revision/version remain R258-U7P5B / `0.2.0-R258-U7P5B`; this
diagnostic checkpoint is not a new gameplay revision. Connected captures,
root-cause attribution, correction and before/after acceptance remain open.

## R258 icon-preserved deployment (2026-10-11)

Branch `codex/r258-icon-preserved-deploy` starts from the published, locally
Guardian-verified R258 documentation commit
`6ad419d92f3086c5211aeef313eb6dc70218cf30`. The previously active RPG
`mods/Hywind.jar` was R256-U7P5B with SHA-256
`84AF5EE7F843BB13480258D10A412838A6A435B9C92F9CAA59AEF10425B7DBE7`.
Read-only comparison of all registered icon paths found one asset missing from
R258 source: Cyclone Armor's legacy `SkillSpiritshield.png` native/UI pair.
The candidate adds that identical 30,514-byte PNG in both paths, points the
existing `spirit_shield` native item to it, and retains its internal skill ID.
It also carries the existing bulk icon updater correction: the RPG target is
`Hywind.jar`, canonical display-name inputs map through the icon registry, and
duplicates and invalid filenames are rejected before mutation. All other
registered icon bytes already match the active JAR. The U7P5 asset audit pins
the old and new normalized rune JSON hashes and both packaged PNG hashes;
its existing guard still covers every other asset. The primary checkout's
unrelated edits, PSD, alpha art and player save data remain untouched.
The exact R258-U7P5B (`0.2.0-R258-U7P5B`) deployment source is
`03abca2f55400bf157e42bae53f9a691267b79cd`. Its full offline `check`
passed 3,527 root, 466 native-control, 49 CanvasUI, and 5 Tavern JUnit tests
with zero failures or skips, plus asset and package validation. The focused
icon tests passed, and `Update-RpgIcons.ps1 -CheckOnly` found 19 supplied icons
with zero pending changes. All 38 icon entries present in the old deployed
JAR match the candidate byte for byte. Guardian recorded the source and
published `origin/codex/r258-icon-preserved-deploy`; the verified deployment
JAR SHA-256 is
`3DA33D47F2990B25FE99EFB6CEAD8543630054D35193932F261BD2F51648CA12`.
The old JAR was backed up outside `Saves` at
`Hytale/data/pre-release/HywindBackups/Hywind-before-R258-20261011T143105Z-84AF5EE7F843BB13480258D10A412838A6A435B9C92F9CAA59AEF10425B7DBE7.jar`;
its hash matches the old JAR. The active `Saves/RPG/mods/Hywind.jar` has the
new verified hash and embeds the exact deployment source commit. The deployed
JAR passes package validation and a default-target icon `-CheckOnly` run with
zero pending changes. Only one authoritative `Hywind.jar` is present. This
documentation checkpoint follows deployment; connected QA and acceptance
remain with the user.

## R258 unified diagnostic commands offline QA candidate (2026-10-11)

Branch `codex/unified-diagnostics` starts from the locally Guardian-verified
R257 documentation commit `cb1b1b5e2609e2080c06402405f4c4fcbcad4002`;
the exact R257 implementation source embedded in its JAR is
`73c5d9d955f9e90a82bd136868c8fdfce393958a`. R258-U7P5B
(`0.2.0-R258-U7P5B`) consolidates commands under `/rpg-trace`, adds bounded
personal skill capture and independently filtered UI/Inventory/Skill Tree
sessions, retains separate Gear/Sentinel evidence and native one-shot probes,
and moves WA-155 calibration under `/rpg lightqa`. The previous trace wrappers
are retired after source-level migration; the underlying recorders remain.
The implementation source commit is
`94badeac23de0bb7917687152d2d25de21535dc8`. Its exact-commit offline
`check` passed 3,524 root, 466 native-control, 49 CanvasUI and 5 Tavern JUnit
tests with zero failures or skips, plus asset and package validation. Strict
SpotBugs passed. Guardian recorded the implementation JAR SHA-256 as
`A6CBF9FD2340186D1FB305A74B526A5FCB14D2A0FE885174DDD0EF8EB75DFE8B`
and published `origin/codex/unified-diagnostics` at that commit. A later
documentation checkpoint changes only this baseline record and receives its
own exact-commit package identity. The primary checkout's unrelated edits and
the RPG save remain untouched. No R258 JAR has been deployed; connected
command behavior and acceptance remain with the user.

## R257 combat damage trace offline QA candidate (2026-10-11)

Branch `codex/combat-damage-trace` starts from verified R256 documentation
commit `df833376e5dfa212035e7ddd19fc363baee133cd`; the built R256 source is
`5ee89e07032aac1141562e9121fd02f6d0fc7154`. The primary checkout's
unrelated edits and the RPG save remain untouched. R257-U7P5B
(`0.2.0-R257-U7P5B`) adds bounded, operator-only native combat hit receipts,
authored offensive inputs, gear and NPC defense stages, and NPC profile
outcomes. Source-level evidence identifies an initial Goblin breach route that
spawns `Goblin_Scrapper`; the connected event's actual roles, difficulty
profiles, and lethal hit are pending owner QA. No balance coefficients change.
The implementation source commit is
`73c5d9d955f9e90a82bd136868c8fdfce393958a` (following the gameplay
commit `25cfe14cf2ce0d1c3c4fd3b3ea187f7333abaa96`). Its exact-commit
offline `check` passed 3,520 root, 462 native-control, 49 CanvasUI, and 5
Tavern JUnit tests with zero failures or skips, plus asset compatibility and
package validation. Strict SpotBugs passed. Guardian recorded the R257 JAR
SHA-256 as
`0A746AAD5E3FE0F0E18352C4AD8B40C88DED49F4C3B2320D8DA366BAE838D008`.
The identical QA copy is `build/qa/Hywind-R257-U7P5B.jar`. Guardian local
completion is VERIFIED; GitHub synchronization has not been checked. The
branch has not been published, and the RPG save has not been changed.
The active RPG `Hywind.jar` contains user icon updates absent from this
source branch, so deploying this candidate directly would lose those icons.
Actual event-Goblin scaling and per-hit calculations still require connected
owner QA; no connected acceptance is claimed.

## R256 Cyclone Armor barrier QA candidate (2026-10-10)

Source branch `codex/cyclone-armor` starts from verified, published R255
commit `18987e622b02f5dfb593d3821af9f5e241292e28`, descending from
GitHub `main` commit `b6a2bd10e877800d702ec5ecd8bc1464e1724f83`.
Guardian preflight passed. The primary checkout's unrelated modified
`AGENTS.md` remains untouched.

R256-U7P5B (`0.2.0-R256-U7P5B`) replaces the historical Spirit Shield
design while keeping its saved `spirit_shield` ID. Cyclone Armor is a self
Wind barrier costing 18 Mana with a 14-second cooldown, ten-second maximum
duration, authoritative post-mitigation absorption, level-scaled effective
Healing Power, and a caster-attached Wind Swoosh particle. `/rpg cyclone`
provides an operator-protected grant for QA. The required Scarak Defender
acquisition mapping remains proposed and cannot roll publicly until connected
validation. The normalized source is also proposed by Thorns Aura; learning
source admission now permits multiple proposals but refuses ambiguous verified
signatures. Existing save data remains compatible.

This is an absorption and visual QA candidate. Projectile return is not
enabled: the pinned native impact paths expose callbacks before the native hit
interaction, but this pass has not established a safe ownership/payload-preserving
return for the legacy and modern projectile implementations. The original hit
continues through normal barrier absorption. Healing Breeze, Gale Cutter, and
Hurricane are absent from the current catalog, so their synergies are not yet
testable here.

The gameplay implementation commit is
`74e3465c9afc70b5419e66a760fb23aee162e711`; the icon owner inventory
correction commit is `5ee89e07032aac1141562e9121fd02f6d0fc7154`.
The latter is the exact source commit embedded in the R256 build. Focused
Cyclone, acquisition, presentation, and icon tests passed. Strict root
SpotBugs passed. The exact-commit offline `check` passed 3,519 root, 453
native-control, 49 CanvasUI, and 5 Tavern JUnit tests with zero failures or
skips, plus asset compatibility and package validation. Guardian recorded
the JAR SHA-256
`5CE7C8D8E2A9E5E412C57D52C0DE328FCEF8CAEB9FAEEDE1298F9176FC226A7B`
and confirmed `codex/cyclone-armor` published on GitHub at the build commit;
`main` integration remains pending.

The previous R255 JAR was backed up outside `Saves` at
`C:/Users/Zemio/AppData/Roaming/Hytale/Hywind-backups/Hywind-before-R256-20261011T023615128Z.jar`,
SHA-256 `FE2CCF595D47DBD58DE363D145F3C8FCCF8F13E62D29F45F86D630288082D04F`.
Only `Saves/RPG/mods/Hywind.jar` was replaced. Its installed SHA-256 and
embedded source commit match the verified R256 build. Save data, the game,
and standalone native servers were untouched. Connected-game Cyclone Armor
acceptance remains with the user. This documentation checkpoint does not
change the deployed JAR's source identity.

## R255 Teleport landing sound QA candidate (2026-10-10)

Source branch `codex/teleport-landing-sound` starts from verified, published
R254 commit `f6cc79b1820a5dce2f84bf96895ad1a7421b3902`, which descends from
the refreshed GitHub `main` commit `b6a2bd10e877800d702ec5ecd8bc1464e1724f83`.
Guardian preflight passed; the primary checkout's unrelated modified
`AGENTS.md` remains untouched. The active RPG save carries R254 at SHA-256
`7353B2A657AD8D9249B7617B9BE66F7008028AA65C23609B60901BBAE25C1031`
before this candidate.

R255-U7P5B (`0.2.0-R255-U7P5B`) adds only the Teleport landing cue. The
installed Hytale 0.7.0-pre.5.1 `Assets.zip` contains native event
`Server/Audio/SoundEvents/SFX/Magic/Portals/SFX_Portal_Neutral_Teleport_Local.json`.
Its ID is `SFX_Portal_Neutral_Teleport_Local`; Hywind does not duplicate the
asset. After native relocation succeeds, the arrival particle is followed by
`SoundUtil.playSoundEvent3d` with `SoundCategory.SFX` at the saved destination.
Hytale's native sound event has a 16 m default maximum range and its 3D method
addresses players in the event's spatial range. A per-Teleport presentation
gate and pending-map removal prevent duplicate playback. Audio failure is
reported as cosmetic and cannot hold the completed cast. Failed, cancelled,
and timed-out Teleports do not enter arrival presentation. No save migration
or changes to targeting, movement, Mana, cooldowns, summons, or hazard rules.

The implementation commit is `a33cda5618715e06a928273d34ee62dbe73666f0`.
All 11 focused Teleport arrival, native receipt, and cast-lifecycle JUnit tests
passed with zero failures or skips. Strict SpotBugs passed. Its exact-commit
offline `check` passed 3,513 root, 452 native-control, 49 CanvasUI, and 5
Tavern JUnit tests with zero failures or skips. Guardian package validation
passed and recorded the JAR SHA-256
`FE2CCF595D47DBD58DE363D145F3C8FCCF8F13E62D29F45F86D630288082D04F`.
Guardian confirmed `codex/teleport-landing-sound` published on GitHub; `main`
integration remains pending.

The previous R254 JAR was backed up outside `Saves` at
`C:/Users/Zemio/AppData/Roaming/Hytale/Hywind-backups/Hywind-before-R255-20261010T225523356Z.jar`,
SHA-256 `7353B2A657AD8D9249B7617B9BE66F7008028AA65C23609B60901BBAE25C1031`.
Only the active `Saves/RPG/mods/Hywind.jar` was replaced. Its installed SHA-256
matches the R255 candidate, and its embedded source commit is the implementation
commit above. Save data, the game, and standalone native servers were untouched.
Connected-game Teleport acceptance remains with the user. This deployment
record is a later documentation checkpoint and does not change the deployed
JAR's source identity.

## R254 operator skill-level QA command (2026-10-10)

Source branch `codex/skill-level-qa` starts from the verified R253 documentation
commit `864c8f16868c01612ae0fdc7fb29ada146b7c834`, descended from the
refreshed GitHub `main` commit `b6a2bd10e877800d702ec5ecd8bc1464e1724f83`.
The implementation commit is `e5ce62864fa4d4eb081ff6130a76a12d2b7aed35`.
The primary checkout's unrelated modified `AGENTS.md` remains untouched;
Guardian preflight passed in the clean task worktree.

R254-U7P5B (`0.2.0-R254-U7P5B`) adds self-targeted operator command
`/rpg skilllevel <skillId> <1..20>` for a learned skill. It writes that skill's
base-rank checkpoint through the existing player transaction, preserving
mastery XP, economy materials and receipts, cooldowns, rewards, other ranks,
and gear-level bonuses. Unknown or unlearned skills and ranks outside 1..20
are rejected; setting the current rank is a no-op. The command requires
`inigmasgames.rpg.level`, has no marker gate, and changes only the active RPG
player's saved progression. Teleport behavior and the save schema are unchanged.

The focused `Stage12ProgressionClosureTest` passed all 27 tests, including a
rank-20-to-1 override of earned mastery that survives reload. The
pre-annotation implementation commit `9169ccf96837c9c4d237022961078949568c9731`
passed unmodified offline `check`: 3,510 root, 452 native-control, 49 CanvasUI,
and 5 Tavern JUnit tests, with zero failures or skips; the 41,251-reference
asset audit passed. Package validation passed 19,445 entries, 2,167 classes,
identity `InigmasGames:HyARPG@0.2.0-R254-U7P5B`, and zero ImmersiveNPCs
payload, SHA-256 `FFD2043EAB44CC8B1F503662AB22E6A348670C6A92AB1C9D5EB841406F85FE09`.
The amended source adds a documented suppression for the intentional injected
player authority; strict SpotBugs then passed. Focused JaCoCo covered 11/11
lines and 10/10 branches in `setOperatorSkillRank`. Documentation commit
`8d3f6f3d6eaa991c972c81cb7c448dee189a0368` then passed its own exact-HEAD
offline `check` with the same four test totals and zero failures or skips.
Guardian recorded its package validation and JAR SHA-256
`7353B2A657AD8D9249B7617B9BE66F7008028AA65C23609B60901BBAE25C1031`,
and confirmed `codex/skill-level-qa` published on GitHub; `main` integration
remains pending.

The previous R253 JAR was backed up outside `Saves` at
`C:/Users/Zemio/AppData/Roaming/Hytale/Hywind-backups/Hywind-before-R254-20261010T223734289Z.jar`,
SHA-256 `E57E8F5F77063653982F4190FC64E4178A0B6EC5F00FEF1D4002EEC73FC89730`.
Only the active `Saves/RPG/mods/Hywind.jar` was replaced; its installed
SHA-256 matches the R254 candidate and its embedded source commit is
`8d3f6f3d6eaa991c972c81cb7c448dee189a0368`. Save data, the game, and
standalone native servers were untouched. Connected-game skill-level and
Teleport acceptance remain with the user. This deployment record is a later
documentation-only commit; the deployed JAR intentionally embeds the verified
pre-record commit.

## R253 Teleport native relocation QA candidate (2026-10-10)

Source branch `codex/teleport-native-target` continues the published R252
history from implementation commit `320287ad75098135ae0a3007d4cf8aded6bb5412`
and its later documentation follow-up. The verified GitHub `main` ancestor is
`b6a2bd10e877800d702ec5ecd8bc1464e1724f83`. Guardian preflight passed;
the primary checkout's unrelated modified `AGENTS.md` remains untouched.
The active RPG save carried R252 at SHA-256
`97C004B06D9592D11337AA89CC70798EBE4FFFB04436B84FEF7A2C4029ED4778`
before this candidate.

R252 owner QA at 21:36:14 UTC recorded a committed Teleport, landing particle,
and `MOVEMENT_END` over 5.65 m, but the player did not visibly move. The same
instance remained in MOVEMENT until disconnect at 21:36:54 UTC; subsequent
Teleport and Charged Bolt requests were rejected as `INCOMPATIBLE_ACTIVE_STATE`.
Native Hytale 0.7.0-pre.5.1 bytecode shows `Player.moveTo` updates the server
transform without sending `ClientTeleport`, whereas the native `Teleport`
component's player system queues that packet and completes an operation future.
The separate `OUT_OF_RANGE_OR_ELEVATION` side-face rejection is consistent with
its resolved landing center lying beyond the rank's 10 m horizontal limit.

R253-U7P5B (`0.2.0-R253-U7P5B`) queues that native component through the
world tick's command buffer and waits for its completion before relocating
summons, presenting arrival, emitting `MOVEMENT_END`, and terminating the
action lock. Failure, timeout, death, disconnect, and world transfer release
the retained cast without refunding a committed root. Teleport target rules,
environmental damage, and other movement skills remain unchanged. At the
implementation checkpoint, full offline verification, publication, and
deployment were pending. Before commit, all 26 focused Teleport, native receipt,
target, and skill tests passed. JaCoCo covered 15/15 lines and 13/16 branches
in `NativeTeleportReceipt`, and 51/62 lines and 39/54 branches in
`HytaleTeleportTarget`; the native client cannot be exercised offline. Strict
SpotBugs passed with no findings outside the reviewed baseline.

The implementation source commit is `ffdd65536f82f827e723a3dd5763a71bb89b8e85`
on `codex/teleport-native-target`. Its exact-commit unmodified offline `check`
passed 3,509 root, 452 native-control, 49 CanvasUI, and 5 Tavern JUnit tests
with zero failures or skips. The 41,251-reference asset audit passed. Package
validation passed 19,444 entries, 2,166 classes, identity
`InigmasGames:HyARPG@0.2.0-R253-U7P5B`, and zero ImmersiveNPCs payload.
Guardian recorded that exact source commit in the JAR, verified SHA-256
`E57E8F5F77063653982F4190FC64E4178A0B6EC5F00FEF1D4002EEC73FC89730`,
and confirmed the task branch published on GitHub; `main` integration is
pending.

On October 10, the prior R252 `Hywind.jar` was preserved outside `Saves` as
`C:/Users/Zemio/AppData/Roaming/Hytale/Hywind-backups/Hywind-before-R253-20261010T220227914Z.jar`,
SHA-256 `97C004B06D9592D11337AA89CC70798EBE4FFFB04436B84FEF7A2C4029ED4778`.
Only the active RPG save's `mods/Hywind.jar` was replaced; its installed
SHA-256 matches the R253 candidate above and its embedded source commit is
`ffdd65536f82f827e723a3dd5763a71bb89b8e85`. The save data, game, and
standalone native server were untouched. Connected-game Teleport acceptance
remains with the user. This documentation follow-up creates a later Git commit;
the deployed JAR intentionally embeds the implementation commit.

## R252 Teleport hazard-permissive QA candidate (2026-10-10)

Source branch `codex/teleport-native-target` descends from the verified GitHub
`main` commit `b6a2bd10e877800d702ec5ecd8bc1464e1724f83` through the published
R251 diagnostic task history. Guardian preflight passed. The active RPG save's
installed R251 JAR was read only; its SHA-256 is
`9555848D1EA699C89EDD68EFC1E55C68DB6C0AF4AB311C72E2869BB7E0FC772D`.

The R251 trace has eight first-hit `HAZARDOUS_SURFACE` rejections, including
upward normals on visible grass near the player's feet. Hytale 0.7.0-pre.5.1
`CollisionConfig` resolves a `Fluid` asset for fluid ID zero, where `Fluid.EMPTY`
is the non-null empty sentinel. `BlockCollisionData.setBlockData` copies that
asset to each collision, so the former `fluid != null` predicate rejected dry
ground. The trace did not capture the three native flags, so this exact field
assignment is established from native bytecode rather than the eight records.

R252-U7P5B (`0.2.0-R252-U7P5B`) removes hazard metadata from Teleport target
eligibility and ground-support checks. The native target ray now collides with
solid and fluid surfaces, while damage-only volumes do not block the ray.
Teleport explicitly disables Hytale's default damage blocking for target rays
and body clearance. Full player and summon body clearance still uses native
solid-block collision; range, elevation, first hit, loaded-world, and support
checks remain. Failed
target selections record the individual native hazard flags in the existing
single rejection event. Environmental damage and effects are untouched.
At the implementation checkpoint this was a QA candidate pending exact-commit
offline verification and user connected-game acceptance.

Before the source commit, all 13 focused target-selection JUnit tests passed,
including native collision-mask assertions and benign grass, damaging ground,
fluid, support, and solid-body cases. Focused JaCoCo covered 51/62 lines and
39/54 branches in `HytaleTeleportTarget`; the native world collision adapter
cannot be exercised offline. Strict SpotBugs passed against the reviewed
baseline with no new findings. The exact-commit standard `check` and Guardian
receipt remained to be run; the candidate had not yet been deployed.

The implementation source commit is `320287ad75098135ae0a3007d4cf8aded6bb5412`
on `codex/teleport-native-target`, still based on GitHub `main` at
`b6a2bd10e877800d702ec5ecd8bc1464e1724f83`. Its exact-commit unmodified
offline `check` passed 3,501 root, 452 native-control, 49 CanvasUI, and 5
Taverns JUnit tests with zero failures or skips. The 5,014-asset and
41,251-reference audit passed. Package validation passed 19,441 entries,
2,163 classes, identity `InigmasGames:HyARPG@0.2.0-R252-U7P5B`, and zero
ImmersiveNPCs payload. Guardian recorded the exact source commit in the JAR,
verified SHA-256 `97C004B06D9592D11337AA89CC70798EBE4FFFB04436B84FEF7A2C4029ED4778`,
and confirmed the branch published on GitHub; `main` integration is pending.

On October 10, the prior R251 `Hywind.jar` was backed up outside `Saves` as
`C:/Users/Zemio/AppData/Roaming/Hytale/Hywind-backups/Hywind-before-R252-20261010T212121448Z.jar`.
The atomic replacement also preserved
`C:/Users/Zemio/AppData/Roaming/Hytale/Hywind-backups/Hywind-before-R252-atomic-20261010T212121448Z.jar`.
Both backups match the previous SHA-256
`9555848D1EA699C89EDD68EFC1E55C68DB6C0AF4AB311C72E2869BB7E0FC772D`.
Only the active RPG save's `mods/Hywind.jar` was replaced; its installed
SHA-256 matches the R252 candidate above. The game and standalone native
server were not launched. Connected-game Teleport acceptance remains with the
user. This documentation follow-up creates a later Git commit; the deployed
JAR intentionally embeds implementation commit `320287ad`.

## R251 Teleport target diagnostic candidate (2026-10-10)

The source branch `codex/teleport-native-target` starts from freshly fetched GitHub
`main` at `b6a2bd10e877800d702ec5ecd8bc1464e1724f83`. The older primary
checkout's unrelated `AGENTS.md` addition remains preserved there. Guardian
preflight passed on the clean task worktree. The active RPG save's deployed
`Hywind.jar` was read only and remains R250-U7P5B, SHA-256
`F56BB5120BFA764088A30EBFB4ED62C067C1A4899E90A50B0A7B5DA5CBE14C30`.

The October 10 connected trace contains five `TELEPORT_NO_VALID_SURFACE`
rejections and one `TELEPORT_REQUIRES_GROUND`. No successful Teleport movement
was observed. The surface error combines several native collision checks; the
trace lacks aim, contact, and failing-stage data, so a gameplay root cause is
not established. This pass retains the R250 first-hit, hazard, range, upward
elevation, ground-support, body-clearance, and summon rules. R251-U7P5B
(`0.2.0-R251-U7P5B`) is a diagnostic QA revision that records a bounded
`TELEPORT_TARGET_REJECTED` event per failed target selection. Existing
activation rejection codes are unchanged. The earlier encounter-persistence
error remains outside this Teleport pass.

On the pre-commit candidate, 9 target-selection and 5 existing Teleport JUnit
tests passed. The focused JaCoCo run covered 40/54 lines and 42/64 branches
in `HytaleTeleportTarget`'s selection logic; its native geometry adapter was
not exercised offline. Strict SpotBugs passed against the reviewed baseline
with zero new findings. The full unmodified `check` passed 3,497 root, 452
native-control, 49 CanvasUI, and 5 Taverns JUnit tests with zero failures.
The asset audit passed 5,014 JSON assets and 41,251 references. Package
validation passed 19,441 entries, 2,163 classes,
`InigmasGames:HyARPG@0.2.0-R251-U7P5B`, and zero ImmersiveNPCs payload.
This pre-commit candidate JAR embeds the earlier HEAD, so its checksum is
not a release checksum. The complete exact-commit check and Guardian receipt
must follow the source commit. Connected QA is still needed to identify the
failed native stage and establish gameplay acceptance.

The implementation commit is `df70c72d2c5261b8835dd3181a4c6576ce16c5ca`
on `codex/teleport-native-target`. Its exact-commit unmodified `check` passed
the same 4,003 JUnit tests and offline asset/package gates; Guardian's
verification receipt confirms JAR SHA-256
`EF50A65FB8F2D378EC199F96C754C7E421B80F3710D24A69B051A74E1DB887DF`
with the embedded source commit `df70c72d2c5261b8835dd3181a4c6576ce16c5ca`.
That JAR was not deployed at that checkpoint. This documentation follow-up creates a new
Git commit and therefore requires its own exact-commit check, package receipt,
and checksum before publication or deployment. GitHub synchronization and
connected QA are pending at this checkpoint.

The documentation follow-up is `ec890714ecb8827b7a25815d3424bbe8b2104108`.
Its exact-commit unmodified `check` and Guardian verification passed 4,003
JUnit tests and the same asset/package gates. Its JAR SHA-256 is
`9555848D1EA699C89EDD68EFC1E55C68DB6C0AF4AB311C72E2869BB7E0FC772D`;
the embedded source commit is `ec890714ecb8827b7a25815d3424bbe8b2104108`.
Guardian confirmed `codex/teleport-native-target` published on GitHub while
GitHub `main` remained at the reviewed R250 baseline. On October 10, the
previous RPG `Hywind.jar` was backed up outside `Saves` as
`C:/Users/Zemio/AppData/Roaming/Hytale/Hywind-backups/Hywind-before-R251-20261010-165157.jar`;
its SHA-256 matched the installed R250 JAR above. The exact verified R251 JAR
was deployed only to the active RPG save's `mods/Hywind.jar`, and its installed
SHA-256 and embedded source commit match this checkpoint. The world, player,
inventory, and NPC data were not changed by the deployment. Connected-game
Teleport acceptance remains pending. This deployment record is documentation
only and will produce a later Git commit; the deployed JAR intentionally
retains source commit `ec890714`.

## R250 recovery and promoted development baseline (2026-10-10)

The source starting point was `codex/teleport-r250-latest` at
`44184d936d750e0a6729a8f797e7f6a4fe30356f`, revision
`R250-U7P5B` (`0.2.0-R250-U7P5B`). The isolated reconciliation branch is
`codex/hywind-r250-recovery`; its approved local recovery commit is
`e5ccefcb0fe228188c4f1d9d9b4aeee3dc9014c9`. The asset-audit correction
described below is recorded at
`c2f45417a53fe5ec5566f34d195bb88e491a4120`. The reviewed documentation
commit `e4927d06e9a3071bc2023aafec68b8fa68d76268` was fast-forwarded to
GitHub `main` through [PR #1](https://github.com/Graham3D/Hytale/pull/1).
Subsequent documentation updates do not create a new completed or deployed
gameplay revision. Before subsequent work, check the branch's actual HEAD and
status.

The active RPG save's `mods/Hywind.jar` was inspected read-only. SHA-256
`F56BB5120BFA764088A30EBFB4ED62C067C1A4899E90A50B0A7B5DA5CBE14C30`
matches the R250 build from that starting worktree. Its embedded source commit
is `44184d936d750e0a6729a8f797e7f6a4fe30356f`. Its internal plugin name
and entrypoint are `HyARPG` and `com.inigmasgames.hywind.HyArpgPlugin`.
Neither this observation nor deployment alone establishes connected-game
acceptance. Before this recovery, the [R249B receipt](enemies/R249B_DEPLOYMENT.md)
recorded the last full offline `check`; R250 had recorded targeted Teleport
tests. This candidate's fresh checks are recorded below.

## Source and ownership decisions

- R250's gameplay source remains the base. The second Teleport commit
  `ea63fd8a6c70ca808c51dc3697b948133476e37c` has the same patch ID as
  `44184d93`; the latter also retains three R249B deployment and release-gate
  documents. Before promotion, the original primary checkout's local `main`
  HEAD was `209e38c0`, 80 commits behind R250, with substantial uncommitted
  work and R141 build metadata. That checkout remains preserved separately.
- The separate ImmersiveNPCs runtime boundary remains mandatory. R250's Tavern
  plugin extends `JavaPlugin`; its package validator excludes `persistentnpcs`
  code and NPC-owned resources. The older split's neutral `ReadyPathProbe`
  source and inventory grid textures are already present in R250 at different
  build and resource paths. The internal `HyARPG` identity stays unchanged;
  any migration requires separate approval and save compatibility proof.
- Keep the newer R249/R250 skill, gear, summon, enemy, inventory, character,
  cooldown, and Teleport implementations. Do not replace them with primary R141
  files. Retain unique primary history, QA rules, and original art only after
  file-level review. See the [historical split report](HYWIND_SPLIT_BOUNDARY_RECOVERY.md)
  and [deployment ledger](DEPLOYMENT_REVISIONS.md).
- The primary-only R199 footprint catalog is byte-identical to R250's active
  `footprints-v1.json`; its older `InventoryGrid` textures are identical to
  R250's `RpgInventory` textures. Of 166 primary-only UI art files, 153 match
  packaged R250 images by SHA-256. The remaining original PSDs and distinct
  PNGs are preserved as source art without changing the game package.
- Four primary-only regression suites for timed defense, Fortune escape,
  original-hit health grouping, and player hit identity have been ported to
  the R250 test tree. The native adapter test's 925-byte, five-entry pin list
  is now a checked-in test fixture instead of an ignored evidence dependency.
  Explicit LF checkout rules cover byte-pinned Mantle and healing assets and
  the generated elite matrix on Windows; `managed-weapon-fire-v1.json` keeps
  its pinned CRLF bytes. These are checkout and test-reproducibility changes,
  not gameplay asset edits.

## Offline verification and inherited gate

The six focused regression/Teleport classes passed 29 tests. A fresh full
`check` reached `auditU7P5AssetCompatibility`, which rejected R250's JSON
asset set against its frozen deployed R248 baseline. This is an inherited
revision comparison: no gameplay resource is changed in this recovery diff.
That failure is retained here as the original recovery-commit result. The
historical `tools/u7p5-r248-package-baseline.json` is unchanged (SHA-256
`69464E8C153519CA2F97CF76F9A6E17055401A3F2F1A18084C1672CA57E8C168`
with CRLF normalized to LF; the Windows checkout's raw SHA-256 is
`5A4F9012188CFD99E6BE6EF35C4E9EF91567AE32ABD2B12A29C2A64F843D2E4B`).
The audit checks the normalized file hash before using this baseline, as well
as its internal R248 JAR identifier.

The separately approved R250 audit correction uses the verified R249B JAR
(SHA-256 `A0EA6EEB80577AA30AA787475E7B7750417F5DE7530A57EC7DF72DE7D8E13101`)
as its reference. Of 5,014 JSON assets, 1,727 differ from the recovered R250
candidate only in CRLF/LF bytes. The only two semantic JSON changes are the
Teleport skill catalog and lightning runtime definitions. A separate reviewed
record pins the normalized hash of all 4,917 other guarded JSON paths and the
individual normalized SHA-256 values of the remaining 97: 94 R249 additions,
one R249 native-binding change, and two R250 Teleport changes. Normalization
replaces CRLF with LF only; all other bytes still affect the digest. Existing
raw package-to-source checks on reviewed assets and byte-sensitive non-JSON
checks remain in force. The plugin identity, asset count, reference, and
packaging checks also remain in force.

The full `check` runs `auditU7P5AssetCompatibility` after `jar`; `jar` consumes
the root and Tavern `processResources` outputs, whose JSON resources are Gradle
task inputs. The audit also compares every packaged JSON asset path with the
root and Tavern source trees, except the single stock prefab generated by
`processResources`. It checks normalized content for guarded assets and exact
bytes for the 97 reviewed assets. A direct audit of an older JAR therefore
rejects a changed, added, or removed source JSON file instead of trusting the
packaged aggregate hash alone. Non-JSON byte-sensitive checks remain exact.

Offline adversarial probes passed: an intentional line-ending-only variant
was accepted, while a guarded-content edit, Teleport-content edit, missing
asset, and unexpected asset were each rejected. This correction is offline-only;
connected-game acceptance is pending. A direct audit against an older JAR also
accepted a source-only CRLF/LF change, rejected a guarded source-content edit,
and rejected an R248 baseline-file edit that left its internal JAR ID intact.

The earlier offline `check -x auditU7P5AssetCompatibility` passed: 3,488 root tests,
452 native-control tests, 49 CanvasUI tests, and 5 Tavern JUnit tests, all
with zero failures or skips. The retained Tavern feature checks and resource,
native-proc, spatial-grant, CustomUI, campaign-biome, and package audits also
passed. The package validator reported 19,436 entries, 2,158 classes,
`InigmasGames:HyARPG@0.2.0-R250-U7P5B`, and zero ImmersiveNPCs payload
entries. That pre-commit candidate build's SHA-256 was
`D57A803EA8D9519A12CD3EA5728D6D2698370C2D68F3EA1E77A382A95F5FB79D`;
it was not deployed or accepted in game.

Before the audit safeguard commit, the full unmodified offline `check` passed
with 3,488 root tests, 452 native-control tests, 49 CanvasUI tests, and 5
Tavern JUnit tests, all with zero failures or skips. The corrected asset audit
passed 5,014 JSON assets (4,917 aggregate-guarded and 97 individually pinned)
and 41,251 references. The package validator again passed with 19,436 entries,
2,158 classes, the same `HyARPG` identity, and zero ImmersiveNPCs payload
entries. That pre-follow-up **local, undeployed** JAR has SHA-256
`51574DA7B7006C550F0CB28F453767F41C8573FEF83E34A5EC2D2D63E081A919`.
The installed RPG save JAR remains at the earlier SHA-256 above.

After commit `c2f45417a53fe5ec5566f34d195bb88e491a4120`, the complete
unmodified offline `check` passed again: 3,488 root tests, 452 native-control
tests, 49 CanvasUI tests, and 5 Tavern JUnit tests, with zero failures or
skips. The corrected asset audit passed all 5,014 JSON assets and 41,251
references; direct package validation passed 19,436 entries, 2,158 classes,
the `HyARPG` identity, and zero ImmersiveNPCs payload. The JAR built from
that exact commit has SHA-256
`4D89CEC91E3B53C499624D155C88B48994771C86C742F441AB2D9AFF940B183C`.
It was not deployed or accepted in game. A later documentation commit changes
the embedded source-commit field and therefore produces a different JAR hash;
record that build separately rather than attributing it to `c2f45417`.

After documentation commit `e4927d06e9a3071bc2023aafec68b8fa68d76268`,
the complete unmodified offline `check` passed: 3,488 root tests, 452
native-control tests, 49 CanvasUI tests, and 5 Tavern JUnit tests, with zero
failures or skips. The asset audit passed all 5,014 JSON assets and 41,251
references; direct package validation passed 19,436 entries, 2,158 classes,
the `InigmasGames:HyARPG@0.2.0-R250-U7P5B` identity, and zero ImmersiveNPCs
payload. The JAR built from that exact commit has SHA-256
`D17B3A22E77C6A253C9FE09E25AEB501F8221C0A4AE887CA756072FDF2EEBF18`.
It was not deployed or accepted in game. A later documentation-only commit may
produce a different JAR hash because the build embeds its source commit; do
not attribute this checksum to a later commit.

## GitHub integration status

On 2026-10-10, GitHub `main` fast-forwarded from
`209e38c01ef5db153a18f723ddc3b05866844438` to
`e4927d06e9a3071bc2023aafec68b8fa68d76268` from
`codex/hywind-r250-recovery`. GitHub marked
[PR #1](https://github.com/Graham3D/Hytale/pull/1) merged, with the recovery
commit itself as the merge commit. GitHub `main` is now the authoritative
R250-U7P5B development baseline; subsequent documentation commits can advance
its Git HEAD without creating a new gameplay revision. The original primary
checkout remains on its older local `main` with modified and untracked work.
A separate clean clone tracks `origin/main`. Codex's saved Hytale project still
requires its primary folder to be changed to that clone. Future engineering
must verify the latest remote HEAD and worktree status rather than infer source
freshness from the old checkout or a revision label.

## Guardian engineering checkpoint (2026-10-10)

`codex/hywind-guardian` branches from authoritative GitHub `main` commit
`1dfa2cea3085196c7fd4492518c65a03d5e3b49d`. Guardian was implemented
at `b1a5389caa8a95fa99ce4306b6f62a62390890cd` and its native-control
report path was corrected at
`fec2fd65e24eb07cae5df2e0fa74bc10af4cc7d9`. The source remains
`R250-U7P5B` (`0.2.0-R250-U7P5B`), with the `HyARPG` plugin identity and
separate ImmersiveNPCs mod unchanged. At that checkpoint, Guardian's hook was
tracked but not installed, and the branch had not been pushed or merged.

At exact source commit `fec2fd65e24eb07cae5df2e0fa74bc10af4cc7d9`,
the complete unmodified offline Gradle `check` passed in 5m 55s. Its JUnit
reports recorded 3,488 root, 452 native-control, 49 Canvas UI, and 5 Tavern
tests with zero failures, errors, or skips. The asset audit covered 5,014 JSON
assets and 41,251 references. The existing offline package validator passed:
19,436 entries, 2,158 classes, `InigmasGames:HyARPG@0.2.0-R250-U7P5B`, and
zero ImmersiveNPCs payload entries. That commit's **local, undeployed** JAR has
SHA-256 `0E951FE0428AD3B66E9B4F034C6D209988F03B65609BA91A5252A33BE6786CB3`.
Guardian's 19 isolated Git fixture cases passed; its clean preflight took
653 ms and printed 14 bytes. The completion check validated the receipt and
warned, correctly, that the commit is local-only. The subsequent
documentation-only commit `15ffaff518813840d9f830c68e4fb16f7fa87a12`
passed another complete unmodified offline `check` (3,994 JUnit tests,
zero failures or skips), asset audit, and package validation. Its distinct,
undeployed JAR SHA-256 is
`47A1F081587BB545E40B4F6383661D59605C2F50CD97AE8CEFB46156B3154EF5`.
Neither offline validation nor this record establishes connected-game QA.

## Guardian remote synchronization follow-up (2026-10-10)

The local `codex/hywind-guardian` implementation commit
`14b3aac56cc9d1407f5eb1af2c38901efc1dbba2` adds a one-query,
read-only GitHub completion check and an optional reviewed, non-force
`codex/` task-branch publisher. Publication still requires explicit standing
authorization; this follow-up did not invoke the publisher, install hooks,
push, or integrate into `main`. The gameplay revision and version remain
`R250-U7P5B` and `0.2.0-R250-U7P5B`.

At that exact commit, 29 isolated Guardian fixture cases passed, including
local-only, published, divergent, failed-push, and integrated states. Clean
preflight took 609 ms and printed 14 bytes. The complete unmodified offline
Gradle `check` passed in 5m 52s: 3,994 JUnit tests with zero failures or
skips, 5,014 JSON assets and 41,251 references audited, and the existing
package validator passed with `InigmasGames:HyARPG@0.2.0-R250-U7P5B` and
zero ImmersiveNPCs payload. Its undeployed JAR SHA-256 is
`8C30C3117059FD4BF8BC1FD4BEAF48718A2168FA3446640182FCBF34832083EB`.
Guardian's live completion query found the task branch unpublished on GitHub;
`main` integration is pending. Deployment and connected-game acceptance
remain unverified. Any later documentation-only commit needs its own build
checksum because the JAR embeds the exact source commit.

The follow-up `1c3ae2a041541d78ab7a8217dcc7e7787349fccd` reserves
completion exit code 0 for a verified remote PASS; unpublished or unknown
remote state now returns 1, while blocked checks return 2. The 29 Guardian
fixtures passed again. A complete unmodified offline `check` passed in
5m 35s with 3,994 JUnit tests, the same 5,014-asset and 41,251-reference
audit, and passing offline package validation. The undeployed JAR built from
this exact commit has SHA-256
`5A444CF78BE481B296D202438689B0B5333029104F89E24320DD8A62E682A42D`.
A live completion query returned exit code 1: the task branch remained
unpublished, `main` integration pending, and deployment and connected-game
acceptance unverified. The gameplay revision and version remain R250-U7P5B
and 0.2.0-R250-U7P5B.

## Guardian production and analysis tooling (2026-10-10)

Infrastructure commit `a4bc11e6bb82870613c07434c8443b95ab458751`
continues the Guardian branch from `0c51f8b2141ad91f1be2939465618440e9f801ea`.
It installs opt-in JaCoCo 0.8.15 and SpotBugs Gradle plugin 6.5.11
(engine 4.10.2) for the R250-U7P5B source. The local Guardian worktree has a
worktree-specific pre-commit hook; the canonical main checkout does not. Its
uncommitted `AGENTS.md` addition remains preserved there. The policy and
regression guidance on this branch were reviewed separately; no gameplay
source, plugin identity, ImmersiveNPCs runtime, save, or deployed JAR changed.

Guardian's 32 isolated fixtures pass. Targeted JaCoCo instrumentation of the
existing Elite birth/persistence test produced root HTML and XML reports;
`FileEncounterStore.java` measured 446 covered of 709 lines and 333 covered
of 811 branches in that focused run. These numbers describe exercised source
paths, not defect status. A broad instrumented run tripped one existing
scheduling-sensitive backlog assertion; focused root/native coverage passed,
while the uninstrumented full `check` remains the engineering gate. The
initial exploratory SpotBugs analysis reported
546 root, 32 CanvasUI, and 14 Taverns findings. Strict analysis passed against
the reviewed baseline files, and removing one baseline finding made it fail as
expected. See [engineering analysis](ENGINEERING_ANALYSIS.md) for commands,
reports, and initial triage.

At this checkpoint, the authoritative GitHub `main` starting point was
`1dfa2cea3085196c7fd4492518c65a03d5e3b49d`. Guardian's remote completion
check, rather than this historical statement, determines whether the task
branch has since been published and integrated. A full exact-commit `check`
and package receipt are required before publication. This infrastructure
work does not establish a new gameplay revision, deployment, or connected-game
acceptance.

[Guardian PR #2](https://github.com/Graham3D/Hytale/pull/2) subsequently merged
the reviewed branch commit `eb58081b3e4203ff5858f3bb950e2febc23c5719`
into GitHub `main` as merge commit
`401700bc6b2eb2bb509ab6cd1ccfd2b90cc8662a`. At the **branch commit**,
the complete unmodified offline Gradle `check` passed with 3,994 JUnit tests,
5,014 guarded JSON assets, and 41,251 references. The existing package
validator passed: 19,436 entries, 2,158 classes, `InigmasGames:HyARPG`, and
zero ImmersiveNPCs runtime payload. The **undeployed** JAR built from
`eb58081b` has SHA-256
`EE9F9255D1B28A46652BD6A6CEA82059AE1718B12E956C611BF9F44F87AB49AA`.
The merge commit has the same source tree but a different Git identity, so
that checksum must not be attributed to a build of the merge commit. The
canonical main checkout still holds its separate uncommitted `AGENTS.md`
addition and was not updated. Future engineering must start from a freshly
verified `origin/main` rather than that stale working copy. Later documentation
commits can advance GitHub `main` without changing the R250 gameplay revision.

## Preservation and release gates

The full primary checkout and both Teleport worktrees, including untracked
files and Git metadata, have verified copies outside active checkouts and saves
in a local recovery backup. All 297,892 copied files passed source-to-backup
SHA-256 comparison. The primary checkout, Teleport branches, and active RPG
save remain untouched.

Do not stage archived saves, player or NPC data, generated binaries, caches,
large QA evidence archives, sensitive configuration, or unrelated primary
files. Review every staged path and run compilation, relevant unit tests, and
offline package validation for subsequent work. Do not launch a native server,
deploy a JAR, alter the save, or push new commits to `main` without the
appropriate approval and release gate. Assign a new revision only when an
engineering pass actually meets its completion gate.
