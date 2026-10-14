# HyARPG gameplay and QA commands

## Master Enemies candidate QA lease (guarded Stage 4)

`/rpg me12qa activate <candidateSha> <worldUuid> <saveName> <seconds> <guardianReceipt>`
arms natural v2 births for the player's current loaded world only. The five arguments
are a 40-character lowercase source commit, the native world UUID, the exact active
save directory name, a lifetime from 1 to 600 seconds, and an absolute path to the
Guardian verification JSON for that exact installed JAR. Hash verification runs
asynchronously; activation is announced only after rechecking the live world.
`/rpg me12qa status` reads
the current lease; `/rpg me12qa revoke` immediately stops new v2 births. All three
are in-game player commands requiring `inigmasgames.rpg.me12.qa` and the parent
`/rpg` permission. The player must be in the named world. The world configuration
must be `masterEnemiesV2=DRAIN`; world recovery/admission, all native hooks, at
least one production-eligible role, the exact verified 0.7.1 native-hook patch
and assets hashes, and Guardian's
candidate/test receipt must pass. Each natural roster is checked against its
own pinned production roles before v2 planning. An uncertified role retains
the existing v1/native path. Firesteel remains outside v2 QA until its
incoming-only lifecycle is verified. Any missing prerequisite rejects activation.
Unloading the world, reloading world configuration, expiry, revocation, or a lost
owner hook stops new v2 births. Existing v2 roots and archives remain recoverable;
the production `ENABLED` guard remains closed. The production birth-owner fixture
is included in the exact-commit Guardian receipt. Turret and Seeker remain
independently blocked. Nothing was deployed or activated in Stage 3.

## Master Enemies capture (Stage 3 candidate; Stage 4 workflow)

`R262ME12S3-CANDIDATE`, Hytale `=0.7.1`, registers `/rpg-trace enemies start`,
`/rpg-trace enemies stop`, and `/rpg-trace enemies status`. All three require
an in-game player with `inigmasgames.rpg.trace.patch`. Stop requires the capture owner.
Start uses the same single HRO session as `patch start`; it cannot start a competing recorder.
Play normally after start, stop when finished, then use status until finalization completes.
The report is `master-enemies.md` plus `master-enemies.json` in
`%APPDATA%/Hytale/UserData/Diagnostics/Hywind/HRO/<runId>/`, outside Saves.

The existing ten-minute limit, 512-event queue, shared trace budget, reserved critical
capacity, asynchronous worker and eight-retained-run limit apply. Stop or expiry
restores ordinary capture-off behavior; no telemetry settings need changing.
Capture never enables v2 births or changes monsters, rewards, configuration or saves.
Production v2 births remain DISABLED; SHADOW is diagnostic only; DRAIN retains readers.
Missing native/source certificates remain BLOCKED, absent opportunities NOT_OBSERVED,
and server UI publication never proves client rendering. Connected acceptance is Stage 4.
See [the Stage 4 run sheet](enemies/ME12_STAGE4_RUN_SHEET.md); no Stage 3 deployment is authorized.

## Hywind Regression Observatory (HRO)

Available in `R262C1HROF-CANDIDATE` and descendants targeting Hytale `=0.7.0`.
These commands require the administrative permission `inigmasgames.rpg.trace.patch`;
they are not assigned the Adventurer group or the ordinary UI/gear permissions.
They require an in-game player and never modify save state.

| Command | Effect | Gate |
| --- | --- | --- |
| `/rpg-trace patch start` | Request one cross-system session for the invoking player; returns STARTING, then RECORDING. Maximum ten minutes. A second start reports the existing session. Does not enable manual legacy recorders. | Operator; HRO build; one process-wide session |
| `/rpg-trace patch stop` | Stop new admission immediately; finalize evidence and approved-baseline comparison on the HRO worker. | Operator; any current HRO session |
| `/rpg-trace patch status` | Show STARTING, RECORDING, WAITING_RECONNECT, FINALIZING, COMPLETE or PARTIAL; run ID, admitted/dropped counts and absolute output path. No file scan. | Operator |
| `/rpg-trace patch mark [label]` | Append a timestamped optional checkpoint for the invoking player's capture. The single-word label is sanitized to `[A-Za-z0-9_.-]` and capped at 64 characters; omission records `USER_CHECKPOINT`. The checkpoint includes the observed run, connection and world segment. No gameplay mutation. | `inigmasgames.rpg.trace.patch`; invoking player must own the capture; HROF build; Hytale `=0.7.0` |
| `/rpg-trace patch arm-next` | Arm one owner-scoped capture for the next ordinary plugin boot; starts when HRO is installed in plugin setup and expires after 24 hours if unused. An existing live recording is not rearmed. No launcher setting or save edit. | `inigmasgames.rpg.trace.patch`; invoking player; HROF build; Hytale `=0.7.0` |
| `/rpg-trace patch cancel-arm` | Cancel the pending next-boot arm record outside Saves. Does not stop a live recording. | `inigmasgames.rpg.trace.patch`; invoking player; HROF build; Hytale `=0.7.0` |

Output: `%APPDATA%/Hytale/UserData/Diagnostics/Hywind/HRO/<runId>/` on this
Windows installation, outside `Saves`. On owner disconnect HRO waits up to 30 seconds
for a native-correlated reconnection, then finalizes PARTIAL if the owner does not return.
Shutdown stops admission;
expiry and resource pressure also stop capture. At eight retained run folders,
new captures refuse to start; existing evidence is never automatically deleted.
Use status after stop to obtain the completed folder. Ordinary start cannot reconstruct
pre-start boot/admission; `arm-next` observes from plugin setup onward. Code before
plugin setup, physical key presses and client rendering cannot be reconstructed.
See [HRO Stage 1](HRO_STAGE_1.md) for offline analysis/recovery and proof limits.
Stage 2 adds eight mechanical contract families and `mechanical.json`, including
the authored affix matrix, natural opportunity counts, native damage stages,
defensive receipts and recovery evidence. See [HRO Stage 2](HRO_STAGE_2.md).

Stage 3 was integrated into GitHub `main` at `5287bb7ceecaeb7c6bcdfbfd748503886c6b1eb4`.
The HRO finalization candidate is `R262C1HROF-CANDIDATE`
(`0.2.0-R262C1HROF-CANDIDATE`, Hytale `=0.7.0`). Source integration and deployed
JAR identity are recorded separately in the [development baseline](DEVELOPMENT_BASELINE.md).
After a verified HRO deployment, start once, perform ordinary skill, inventory, combat,
travel and UI QA, then stop and use status to locate the folder. A reconnect
is optional and is needed before claiming recovery coverage. No reset, test
population, individual subsystem trace activation, or save migration is required.

Stage 3 adds `report.json`, `cascades.json`, `implementation-map.json` and
`compatibility-diff.json` to finalized captures. Finalization adds `checkpoints.json`,
`auto-analysis.json`, optional `comparisons/*.json` from explicitly approved compatible
baselines in the engineer-managed diagnostics registry, and integrated server-side
performance evidence. No candidate is automatically approved as a baseline. The offline `hroAnalyze`
Gradle task supports `analyze`, `extract`, `recover`, `recovery-join`,
`baseline`, `compare`, `import`, `fingerprint`, `compatibility`, and
`ui-fixtures`. These are engineering tools, not in-game commands or permission
grants. Baseline approval is per contract/scenario; imports and missing client
evidence cannot silently establish acceptance. Exact arguments, limits,
proof boundaries and the single owner QA pass are in [HRO Stage 3](HRO_STAGE_3.md).
Source integration, installed JAR identity and connected acceptance remain
separate; check the [development baseline](DEVELOPMENT_BASELINE.md) before use.

This is the maintained command reference for the current HyARPG source. Update it in the **same change** as an essential command's implementation. Syntax and availability come from command registration, not from old QA reports. Commands omitted here are internal experiments or narrow one-off probes. `Adventure` means available to an ordinary player in Adventure mode; `operator` means the named server permission is required. Examples that grant items or alter progress are intended for QA unless the row says otherwise.

Hywind retains the internal `HyARPG` plugin identity and `HyArpgPlugin` entrypoint even when the active mod file is named `Hywind.jar`. The maintained command reference now uses the R258 `/rpg-trace` family. Use the [development baseline record](DEVELOPMENT_BASELINE.md) to distinguish source, build, deployment, and connected QA status.

R195-U7P5 requires Hytale **0.7.0-pre.5.1**. It adds the approved 17-item QA159 pack and bounded Sentinel snapshots. Gear requirements and native capabilities remain enforced; QA provenance does not disable any affix mechanic, including Magic Find and Gold Find.

R205-U7P5 keeps the R201 Master Enemies and monster progression port on the verified R200 HyARPG baseline. It accepts positional QA spawn affixes through the native command parser, reads the binding's native model asset for QA spawn clearance, and defers ordinary-hostile health-bar baseline writes until the ECS store is writable. QA spawn now selects an existing authored encounter profile at the spawn point, from the same native zone, or from the authored spawn zone if the native zone is unavailable. Natural-spawn classification remains unchanged. The following Master Enemies commands require the active RPG save and installed 0.7.0-pre.5.1 native receipt patch. `/rpg spawn` creates QA-only actors with no XP, loot, learning, pity, campaign, or milestone rewards.

R208-U7P5 adds the QA-only Frost Crystal Golem binding to the same spawn path. Its seven existing native damage variables are certified under the original chained attack root; the two previously bound Golems remain passive-only. A staged QA NPC must have native hostile player attitude. The Frost binding does not enable natural Master Enemies promotion.

R210-U7P5 attaches Hytale's native Healthbar to QA-spawned Master Enemies, including pack members, and hides their R209 projected target bar. Natural monster presentation stays on R209. The QA native component is removed on death or native removal; the clear command removes loaded QA actors through their existing lifecycle.

R211-U7P5 gives `/rpg spawn` a transient, session-only encounter backend. The command validates the real role/profile/affixes, searches up to nine nearby placement anchors, and uses the same Master Enemies descriptor, state, action, combat, and presentation owners as production. QA actors and their native drops yield no economic reward; their registry clears on death/removal, `/rpg spawn clear`, or world shutdown. A QA spawn failure is local and does not disable production encounter awards or later QA commands. Natural promotion and production persistence remain durable.

R215-U7P5 requires an explicit era on `/rpg spawn`, freezes the corresponding authored difficulty profile and affix count at birth, and uses the compact projected name/Health/affix stack for visible connected QA. The world difficulty registry is never rebound by this command.

R216-U7P5 displays the actual selected affix names in successful `/rpg spawn` chat (for example, `Trork Warrior spawned with Extra Strong, Frenzied, and Armor Breaker.`). Explicitly incompatible affixes are named together in a plain-language rejection. Automatic selection with no legal set, unsafe placement, and world-not-ready failures also use player-readable messages. Internal IDs and encounter UUIDs remain in server diagnostics. The command syntax, author permission, transient save behavior, and zero-reward gate are unchanged.

R212-U7P5 resolves each of those nine anchors with Hytale's native walking-NPC column probe and logs native rejection outcomes. QA staging and provenance still enter in `spawnEntity` pre-add; native death drops are suppressed and verified in post-spawn, after the actor Role exists. A missing post-spawn Role rejects the whole local QA group as `QA_ROLE_UNAVAILABLE_POST_SPAWN` before unfreezing it.

| Command | Effect | Access |
| --- | --- | --- |
| `/rpg enemies status` | Show the current world mode, affix and native binding revisions, enabled role count, native hook availability, and production admission gate. | Adventure; read-only |
| `/rpg enemies affixes` | List all 27 authored Master Enemies affixes and mechanical descriptions. | Adventure; read-only |
| `/rpg enemies inspect [entityUuid]` | R244: inspect any targeted native actor within 24 m, or a loaded current-world actor by UUID. Reports actual native Nameplate text, compositor owner/lifetime, accepted profile or UNPROFILED, staging, and client rendering UNKNOWN without consuming tracker flags. Published Master Enemies additionally show rarity visual, skin tint, selected weapon and armor visual owners from the frozen affixes. Operators also see frozen provenance, providers, immunities, rewards, and mitigation projections. | Adventure for nearby target; `inigmasgames.rpg.enemies.author` for UUID/operator detail; read-only; requires the active RPG save and current HyARPG build |
| `/rpg spawn <concreteRole\|random> <champion\|unique\|superunique> <normal\|nightmare\|hell> [affixAlias...]` | Spawn one cataloged, spawnable, hostile native role as a transient QA-only Master Enemy at the chosen authored era. A family name such as `Skeleton` is rejected with matching concrete role suggestions. `random` draws from compatible catalog roles. With no aliases, the existing planner selects a compatible affix set; explicit aliases must supply the exact era count and identify an incompatible affix. Champion needs 1; Unique needs 1/2/3 for Normal/Nightmare/Hell. Authored Super Unique templates retain their fixed affixes and add their authored era count; the existing ad hoc QA fallback uses its three-card base with the four-card cap. Add aliases as trailing words, separated by spaces, with no `--affixAlias` flag. Concrete roles without an audited action binding use the shared passive-only QA catalog binding; direct-hit affixes remain unavailable for them. Native column probing tries nine nearby anchors, then returns `QA_SPAWN_NO_VALID_POSITION attempts=9` without affecting the next request. A native Role missing at post-spawn rejects locally as `QA_ROLE_UNAVAILABLE_POST_SPAWN`. The command requires the active RPG save and the installed 0.7.0-pre.5.1 native receipt patch. | `inigmasgames.rpg.enemies.author`; active campaign world; zero XP, native/RPG loot, learning, pity, campaign, and milestone rewards |
| `/rpg spawn clear` | Remove loaded transient QA-spawned Master Enemies actors in the current world through native entity removal and discard their session state. Does not remove natural monsters or rewrite save data. | `inigmasgames.rpg.enemies.author`; removes loaded QA actors and presentation state |

## Inventory, character, and skills

| Command | Effect | Access |
| --- | --- | --- |
| `/rpg character` or `/rpg inventory` | Open the unified Inventory/Character workspace. `/rpg inventory` is a development entry point; the normal Inventory key is the intended player entry. | Adventure |
| `/rpg iconposes` | Open the read-only, 20-sample leather/weapon spatial-icon pose gallery. Esc closes it; no gear is granted. | Adventure |
| `/rpg skilltree` | Open the existing Skill Tree directly. The Skills tab in the character workspace opens this same tree. | Adventure |
| `/rpg stats` | QA/debug view: show raw/effective attributes, derived combat values, and native resources. Normal Inventory/Character attribute rows display raw values only; diminishing returns remain internal to combat scaling. | Adventure |
| `/rpg progress status` | Show level, XP, Insight, revision, and learning state. Use its revision for progression mutations. | Adventure |
| `/rpg progress buy <passiveId> <revision> <requestUuid>` | Buy an eligible passive. Reuse the UUID when retrying the same purchase. | Adventure; changes progression |
| `/rpg progress respec <revision>` | Respec allocated attributes. | Adventure; changes progression |
| `/rpg progress export` / `/rpg progress import <base64urlBuild> <revision>` | Export or import a build. | Adventure; import changes progression |
| `/rpg equip <skill01..skill04\|passive01..passive06> <skillOrPassiveName>` | Put an owned skill or passive in a permanent slot. Example: `/rpg equip skill01 Iron Sentinel`. Chapter I `skill03` and `skill04` are saved and shown as unbound; only `skill01`/`skill02` have supported native execution input. | Adventure; changes loadout; schema 13 migration saves a backup of the prior player state on first load |
| `/rpg unequip <skill01..skill04\|passive01..passive06>` | Remove the selected skill/passive and affected links. | Adventure; changes loadout; schema 13 build |
| `/rpg link <sourceNode> <targetNode>` / `/rpg unlink <sourceNode>` | Edit a validated Link Tree edge. | Adventure; changes loadout |
| `/rpg loadout` / `/rpg compile` | Inspect the equipped graph or validate its compiled plan. | Adventure; read-only |
| `/rpg managuard <1..50>` | Set the percentage of Mana reserved for Managuard; does not refill Mana or shield. | Adventure; changes allocation |
| `/rpg trusted request <exactOnlineName\|playerUuid>` | Record a Shared Aegis player ally request by stable UUID. The other player must explicitly accept before either player becomes eligible. | Adventure; writes the server trust journal; R261QA1 default build |
| `/rpg trusted accept <exactOnlineName\|playerUuid>` | Accept that player's pending request, creating a mutually trusted pair. Offline players can be named by UUID. | Adventure; writes the server trust journal; R261QA1 default build |
| `/rpg trusted revoke <exactOnlineName\|playerUuid>` | Revoke a trusted pair or pending request. Existing Shared Aegis protection from that pair becomes ineligible; renewed consent creates a new identity. | Adventure; writes the server trust journal; R261QA1 default build |
| `/rpg trusted list` | Show incoming requests and trusted player UUIDs. | Adventure; read-only; R261QA1 default build |
| `/rpg unsummon` | Dismiss owned summons, including Iron Sentinel; consumed bound gear stays consumed. | Adventure; changes active summons |

## Generated gear and gameplay QA

Gear generation is self-targeted and protected by `inigmasgames.rpg.gear.author`. It creates real QA gear with protected QA provenance; use the single RPG save. If a spatial bag owns inventory, delivery uses the spatial transfer coordinator; otherwise it goes to native inventory.

| Command | Effect |
| --- | --- |
| `/rpg gear types` / `/rpg gear rarities` | List accepted gear type tokens and qualities. Eras are `normal`, `nightmare`, `hell`; qualities are `normal`, `magic`, `rare`. |
| `/rpg gear <type> <normal\|magic\|rare> <normal\|nightmare\|hell> [1..99\|max\|seed:<seed>] [seed]` | Generate one protected item. Examples: `/rpg gear shortbow magic normal`, `/rpg gear sword rare nightmare max seed:qa-sword-01`. The optional seed reproduces the roll. |
| `/rpg gear spawn <gm.base.id> <900..1000> <NORMAL\|MAGIC\|RARE>` | Requires `inigmasgames.rpg.gear.author`; creates and saves a protected QA item with a fixed intrinsic thousandths roll. Example: `/rpg gear spawn gm.garb_wayfarer.legs.h 1000 RARE`. Use a mapped base ID. Equipped affixes, including Magic Find and Gold Find, affect combat stats and reward capture; QA provenance still identifies the authored source and keeps the item out of random generation. |
| `/rpg gear ring` | Create one Copper Ring (permission `inigmasgames.rpg.gear.author`). In spatial mode it is admitted to the private bag and saved before use; in native mode it goes to native inventory. Select it in the spatial grid, then click a left/right ring slot to equip. Click an occupied ring slot to return it to the bag; a free rectangle is required. No ring affixes or stat bonuses are authored yet. |
| `/rpg gear inspect` | Describe the held managed item, its frozen identity and requirements. This inspection command is available to Adventure players. |
| `/rpg gear odds <era> <1..99> <common\|specialist\|elite\|miniboss\|boss> [seed]` | R191+ with gear QA enabled; requires `inigmasgames.rpg.gear.author`; read-only. Shows rank-default guaranteed picks, optional pick chances, cap, each deterministic optional roll and child receipt ID, and successful item's quality roll/weights/result at current Magic Find. Does not grant or save an item. Natural kills resolve canonical enemy roles; no role overrides are authored in this revision. Inspect deaths with `/rpg-trace gear start`. |
| `/rpg gear affixqa qa159 list` | R195+: list all 17 distinct bases, native IDs, families/slots, saved spatial footprints, affix IDs and Sentinel eligibility. Requires `inigmasgames.rpg.gear.author`; read-only. |
| `/rpg gear affixqa spawn qa159-01` through `/rpg gear affixqa spawn qa159-17` | R195+: grant the named deterministic item through existing saved delivery. Four armor pieces have ten affixes; two held items have ten; eleven held items have nine. Exactly 159 functional affixes, once each, WA-155 excluded. Bypasses only the rarity count/prefix-suffix budget; carrier, exclusion groups, requirements, magnitude grids and functional/native gates remain. All affixes work at runtime, including reward bonuses. Requires `inigmasgames.rpg.gear.author`; changes inventory/save. |
| `/rpg gear affixqa list` | List the 160 frozen v1 deterministic affixed/control pairs, the R261-C GA-161 pair, and current legal combined fixtures, with base, group, rarity, frozen IDs, names, rolls, selectors, and `SPAWNABLE` or `PENDING_ADAPTER`. Requires `inigmasgames.rpg.gear.author`; read-only. |
| `/rpg gear affixqa spawn <fixtureId\|armor\|weapons\|support\|summons\|all>` | Issue only mapped, legal, currently enabled fixtures with protected QA provenance. Requires `inigmasgames.rpg.gear.author`; changes inventory/save. Equipped affixes use the same runtime mechanics as looted gear, including reward-affecting Magic Find and Gold Find. Group issuance saves at most eight items per call; repeat the same command to resume at the next saved item in this server process. A full bag or failed save stops at the named fixture. `-control` rows have the same base, intrinsic and requirements as their `-affixed` pair. |
| `/rpg gear affixqa spawn ab-ga-161-control` or `/rpg gear affixqa spawn ab-ga-161-affixed` | R261-C protected Mana Before Life armor pair. Requires `inigmasgames.rpg.gear.author`; delivers a saved QA item to inventory. The affixed item has `GA-161`; the control has the same base and requirements without it. Development build only; active R260 does not provide these IDs. |
| `/rpg gear affixqa spawn tooltip-common-battleaxe` | R176+: deterministic Common Adamantite Battleaxe for native tooltip comparison. Normal-era base, item level 40, 95% intrinsic roll, no affixes. Requires `inigmasgames.rpg.gear.author`; creates protected QA gear through the existing saved delivery path. |
| `/rpg gear affixqa spawn tooltip-magic-battleaxe` | R176+: same base/intrinsic roll with one legal Honed (WA-001) affix. Quality label is **Rare**, with blue presentation. Same author permission and save effects. |
| `/rpg gear affixqa spawn tooltip-rare-battleaxe` | R176+: same base/intrinsic roll with legal Brutal (WA-002), Glacial-Edged (WA-018), and Alacrity (WA-008) rolls. Quality label is **Epic**, with purple presentation and blue affix text. Same author permission and save effects. |
| `/rpg level <1..99\|reset>` | Set your RPG level; `reset` atomically sets level 1 / XP 0, restores every raw attribute to 10, refunds points above baseline into unspent points, and clears the pending level-up indicator. Already-unspent points remain. Requires `inigmasgames.rpg.level`; changes persistent progression, with no save/build marker gate. Skills, rewards, and gear are retained. |
| `/rpg skilllevel <skillId> <1..20>` | R254 QA: set your own **learned** skill's saved base level, e.g. `/rpg skilllevel teleport 10`. Use the catalog skill ID (or an unambiguous one-word alias). Equipment skill-level bonuses remain additive; this does not grant a skill, change mastery XP, spend materials, or reset cooldowns. Repeating the same level does not write a new revision. Requires `inigmasgames.rpg.level`; operator-only; changes player progression in the active RPG save with an R254+ build; no QA marker gate. |
| `/rpg iron-sentinel` | Learn Iron Sentinel for QA, then equip it via `/rpg equip skill01 Iron Sentinel`. Requires `inigmasgames.rpg.gear.author`; changes progression. |
| `/rpg teleport` | Learn Teleport for QA, then equip it via `/rpg equip skill01 Teleport` and activate its native skill hotkey. Requires `inigmasgames.rpg.gear.author`; changes progression only on the active RPG save with the Teleport test build. It does not unlock an enemy acquisition source. |
| `/rpg cyclone` | R256+ QA: learn Cyclone Armor for yourself, then use `/rpg equip skill01 Cyclone Armor` and its native skill hotkey. Requires `inigmasgames.rpg.gear.author`; changes saved progression in the active RPG save with the R256+ build. R260 changes barrier capacity scaling; use `/rpg-trace skill start` to inspect final capacity and absorption. The proposed Scarak Defender learning source remains independently gated; projectile return is not enabled. |
| `/rpg dev points grant <positiveInteger>` | Grant pending/unspent attribute points without XP. Adventure-accessible development fixture; changes progression. |
| `/rpg dev attribute <str\|dex\|int\|wis\|luck> <nonnegativeInteger>` | Override one raw attribute. Adventure-accessible development fixture. `/rpg dev reset` was removed in R196; use `/rpg level reset` with its operator permission. |
| `/rpg dev xp-display <0..100\|clear>` | Set or clear a temporary HUD XP percentage. It grants no XP and does not persist. |

For the R167 affix QA pass, keep the original fixtures above or select an isolated pair from `affixqa list`. For example, `/rpg gear affixqa spawn ab-wa-074-affixed` and `/rpg gear affixqa spawn ab-wa-074-control` compare Fire Resistance on the same base. The `ab-wa-001-affixed` / `ab-wa-001-control` pair compares local weapon damage. Equip only one side of the pair at a time; carrying it in the backpack does not apply its affixes. Gear requirements and broken-item rules still apply. Use `/rpg-trace gear start`, label the baseline/equipped/Sentinel steps with `/rpg-trace gear mark <label>`, then `/rpg-trace gear stop` to save the capture.

WA-155 remains unavailable for item generation pending your measured light-radius calibration. Use the bounded `lightprobe` and read-only `lightcal` commands below for that measurement. The contract stays in metres. QA gear contributes Magic Find and Gold Find to the same UI and reward owners as ordinary gear; QA provenance is not a runtime exclusion. Simulacrum retains its finite 20-Mana base cost; summon-cost affixes reduce that existing cost. WA-112 listens to successful cleanse receipts and adds no new skill.

## Difficulty and world controls

| Command | Effect | Access |
| --- | --- | --- |
| `/rpg difficulty status` | Show current world difficulty, unlocks, and golem checklists. | Adventure |
| `/rpg difficulty inspect` | Show nearby authored monster combat profiles. | Adventure |
| `/rpg difficulty return` | Return to the initial Normal campaign spawn. | Adventure; moves character |
| `/rpg difficulty prepare` | Prepare persistent Nightmare and Hell worlds. | `inigmasgames.rpg.difficulty.author` |
| `/rpg difficulty unlock <nightmare\|hell>` | Complete the five Normal golem milestones and unlock Nightmare; Hell also completes all five Nightmare milestones and unlocks Hell. Case-insensitive, permanent and idempotent. Changes only the invoking player's saved difficulty progress; grants no kill credit, XP or combat rewards. `/rpg difficulty status` shows the completed checklists. | `inigmasgames.rpg.difficulty.author`; persists player progress in the active RPG save; requires R247-U7P5A+ |
| `/rpg difficulty portal <normal\|nightmare\|hell>` | Place an authored portal on clear ground ahead. | `inigmasgames.rpg.difficulty.author`; persists world asset |
| `/rpg difficulty remove <portalUuid>` | Remove an authored portal. | `inigmasgames.rpg.difficulty.author`; changes world |
| `/rpg difficulty encounter <earth\|flame\|frost\|sand\|thunder>` | Place the selected native campaign golem on clear ground ahead, with a saved campaign encounter marker for eligible death credit. R192 resolves the native appearance bounds before spawn; an obstructed location is rejected without placing an encounter. | `inigmasgames.rpg.difficulty.author`; changes world, no save/build gate beyond U7P5 |
| `/rpg spawns status` / `/rpg spawns <0.25..8.0>` / `/rpg spawns reset` | Inspect or change the native environmental NPC density. R237 writes the same `world.nativeEnvironmentSpawnMultiplier` field in the active save-root `world-config.json`; the old density file is read once on first migration and retained for recovery. Changes apply to loaded spawning worlds. `reset` sets native 1×. | `inigmasgames.rpg.spawns`; operator-only; requires R237+ and the active RPG save |
| `/rpg worldconfig status` / `/rpg worldconfig reload` | Show active save-root configuration, per-environment native population targets/actuals, and per-world Elite pack capacity (loaded, pending, dormant, historical QA, over-cap and 64 m cells); or validate and atomically load edited `Saves/RPG/world-config.json`. Invalid JSON leaves the previous policy active. New encounter tuning applies only to future births; existing actor and reward revisions remain frozen. Reducing pack limits grandfathers loaded encounters and blocks new special births until capacity is available. See [world-config operator guide](world-config/OPERATOR.md). | `inigmasgames.rpg.worldconfig`; operator-only; requires R240+ for active-pack diagnostics and the active RPG save |

`/rpg difficulty force <era>` is a test-only travel command. It additionally requires `inigmasgames.rpg.difficulty.force` **and** a server started with `-Drpg.difficulty.testProfile=true`; it does not grant milestones or unlocks. Do not use it as a player travel route.

## Unified diagnostic traces (R258)

Use `/rpg-trace <trace-type> <action> [arguments]`. In each row below, abbreviated actions keep the same `/rpg-trace <trace-type>` prefix. Native subcommands provide help and completion. `/rpg-trace help` and `/rpg-trace list` are Adventure-accessible. `/rpg-trace status` summarizes all categories for an admin. R258 is required. Each start response gives the exact file; all captures are bounded. Repeated starts retain the existing session. The plugin data root is `Saves/RPG/mods/InigmasGames_HytaleRPGPhase00Audit`.

| Commands | Capture and file | Permission and limits |
| --- | --- | --- |
| `/rpg-trace skill start` / `start detailed` / `start performance`; `stop`; `status` | Personal JSONL `logs/rpg/skill-trace-<session>.jsonl`. NORMAL, DETAILED compile stages, or PERFORMANCE raw native ticks from the starting world. Cyclone Armor emits `BARRIER_CREATED` with raw/effective WIS and INT, flat gear Healing Power, both attribute contributions, effective skill level/factor and final capacity; each absorbed hit emits `BARRIER_ABSORBED` with absorbed and remaining capacity. Stop closes and flushes. | Original explicit/admin gate. 60 seconds, 2,048 events, 8 MiB, max 16 sessions. The older bounded global skill telemetry continues at its configured level; status reports it separately. |
| `/rpg-trace combat start`; `stop`; `status`; `last [count]`; `inspect` | Personal incoming/outgoing hit receipts in `logs/rpg/combat-trace.jsonl`. `last` shows 1-32 recent equations; `inspect` compares a targeted NPC's profile with live Health (24 m). | Original explicit/admin gate. 60 seconds, 2,048 events, max 16 sessions. Queued writes drain asynchronously after stop. Native RNG and some internal filters remain `UNOBSERVABLE`; not every hit has a complete equation. |
| `/rpg-trace skilltree start`; `stop`; `status` | Filtered Skill Tree/CanvasUI inputs and authoritative assign/link/unlink/clear/reset outcomes in `evidence/ui-trace/skilltree-trace-*.jsonl`. | `inigmasgames.rpg.uitrace`. Ten minutes, 2,500 events, 4 MiB. Input receipt alone does not prove mutation success. |
| `/rpg-trace inventory start`; `stop`; `status` | Filtered Inventory/Character actions, equipment transfers, sorting, search, page and CanvasUI activity in `evidence/ui-trace/inventory-trace-*.jsonl`. | `inigmasgames.rpg.uitrace`. Ten minutes, 2,500 events, 4 MiB. Native packet visibility depends on the installed adapter. |
| `/rpg-trace ui start`; `stop`; `status`; `mark [label]` | Broad CanvasUI actions in `evidence/ui-trace/ui-trace-*.jsonl`. Marker label is limited to 64 characters. UI, Inventory and Skill Tree have separate sessions/files; stopping one leaves others active. | `inigmasgames.rpg.uitrace`. Ten minutes, 2,500 events, 4 MiB; max 64 combined UI sessions. Continuous mouse motion is not captured. |
| `/rpg-trace gear start`; `stop`; `status`; `mark [label]` | Equipment, affix, combat, stat, loot, resource, status and summon receipts in `evidence/gear-trace/gear-trace-*.jsonl`. | `inigmasgames.rpg.gear.author`. Ten minutes, 3,000 events, 4 MiB; max 64 sessions. Read-only recorder; fixture grants remain separate. |
| `/rpg-trace sentinel start`; `snapshot`; `stop`; `status` | Separate `evidence/sentinel-trace/sentinel-trace-*.jsonl`. Snapshot retains bound item and affix identity, native/derived stats, Health/movement, range/cadence, protection/resistance and inherited aura sources. | `inigmasgames.rpg.gear.author`. Start before snapshot; ten minutes, 3,000 events, 4 MiB. Use Gear trace for actual combat receipts. `/rpg iron-sentinel` remains the QA grant. |
| `/rpg-trace spawn start`; `stop`; `status` | One global 120-second native spawn and Elite admission capture in `logs/rpg/monster-spawn-trace`; 10,000 ordinary detail plus 2,000 priority rows. Status shows file, counts and errors. | `inigmasgames.rpg.spawntrace`. No spawn density or admission changes. Event-Goblin profile and hit evidence requires Combat trace. |
| `/rpg-trace inventory-tab start`; `stop`; `status` | One-shot two-second Tab/Inventory-action capture after a two-second arm delay, up to 20,000 records, `logs/rpg/tab-trace-*.jsonl`. Stop saves collected evidence. | Adventure. Only adapter-exposed native actions are visible; no Inventory mutation. |
| `/rpg-trace inventory-drop start`; `stop`; `status` | One-shot ten-second native drop capture after a two-second arm delay, up to 3,000 records, `logs/rpg/native-drop-*.jsonl`. Stop saves evidence and restores the normal page substitution. | Adventure. The player's actual drop may move an item. Zero inbound packets do not prove none were sent. |

For Skill Tree QA, start `skilltree`, assign and link a node, try a rejected edit, inspect committed/rejected rows, then stop. For Inventory QA, start `inventory` and optionally `ui`, move/equip/sort an item, try an invalid placement, and stop each category. For Gear/Sentinel QA, start both, use the normal gated forge workflow, take `sentinel snapshot` before and after combat, then stop both. For damage QA, start `combat`, deal and receive a hit, use `combat last 2`, inspect an event Goblin with `combat inspect`, then stop. Connected gameplay is the owner's acceptance gate.

Retired routes: `/rpg uitrace`, `/rpg geartrace`, `/rpg sentineltrace`, `/rpg spawntrace`, `/rpg tabtrace`, `/rpg nativedroptrace`, and `/rpg-trace normal|detailed|performance`. Their recorder services and historical evidence files remain. WA-155 tools moved to `/rpg lightqa light`, `/rpg lightqa lightprobe <0..127>` (zero restores), and `/rpg lightqa lightcal <r1:m1,r2:m2,r3:m3,ceiling,toleranceMetres>` under `inigmasgames.rpg.gear.author`. The Adventure cursor-calibration command `/canvasui-cursor-probe` and emergency `/canvasui-cursor-probe-close` remain available.

See the [diagnostic command inventory](TRACE_HOUSEKEEPING.md) for the decision on each retained, migrated and gated probe.
The `/rpg spatialqa` family exists **only when** both `gear-qa-enabled` and `spatial-inventory-qa-enabled` markers are present in the RPG data directory. Every action also checks the spatial marker at execution and requires `inigmasgames.rpg.gear.author`. It is for an isolated copied save, never a live-inventory migration shortcut.

| Command | Effect |
| --- | --- |
| `/rpg spatialqa status` | Show bag mode, entry/receipt counts, and native Storage count; list up to eight entry IDs. |
| `/rpg spatialqa attach` | Attach an **empty** protected QA bag, without migrating native items. |
| `/rpg spatialqa migration-preview` / `/rpg spatialqa migration` | Preflight, then commit native Storage → spatial ownership on the copied save. Do not commit without a successful preview and acceptance plan. |
| `/rpg spatialqa export-preview` / `/rpg spatialqa export` | Preflight, then reverse spatial → native Storage ownership on the copied save. |
| `/rpg spatialqa equip <entryUuid> <held\|offhand\|head\|chest\|hands\|legs>` | Transfer a private-bag entry to native equipment. |
| `/rpg spatialqa unequip <slot>` | Transfer native equipment back into the private bag. |
| `/rpg spatialqa stocktake` | Admit the nearest protected stock world drop through the QA bag route. |
| `/rpg spatialqa fault-next <boundary>` | Arm a one-shot receipt interruption for reconnect recovery testing. `boundary` is exactly one of `pickup-after-prepare`, `pickup-after-save`, `equipment-after-prepare`, `equipment-after-save`, `stock-after-prepare`, or `stock-after-save`. Expect a deliberate interrupted operation. |
| `/rpg spatialqa fragment` | Insert 68 tagged rocks into an **empty** QA bag to test fragmented capacity. |

The gear loot subsystem and `/rpg sentinel-affixes` are registered only when `gear-qa-enabled` exists or the guarded candidate-economy test profile is enabled. The latter toggles the bound Iron Sentinel's inherited affix display and requires the gear author permission. Legacy `inventoryprobe`, native UI experiments, visual controls, and one-off combat probes are intentionally excluded from this everyday reference.

Source anchors: `RpgCommand`, `RpgGearCommand`, `RpgTraceCommand`, `RpgSpatialQaCommand`, `RpgDifficultyCommand`, and registration in `HyArpgPlugin` under `src/main/java/com/inigmasgames/`. When adding a command, revise its row and gate here before considering the change complete.

## QA159 connected sequence

See [the full fixture and per-affix matrix](QA159-PACK.md). Spawn individual items so the bag has enough room. Legendary-colored QA fixtures are an explicit test-only count-budget exception; random drops remain Normal/Rare/Epic. Requirements still apply, and carried items grant no bonuses. Use existing level/attribute QA commands only as needed to satisfy the listed requirements.

1. `/rpg gear affixqa qa159 list`; then `/rpg gear affixqa spawn qa159-01` (continue through `qa159-17` as space permits).
2. `/rpg-trace gear start`; mark your baseline, equip one fixture, inspect Inventory/Combat/Advanced Stats, then exercise its actual trigger (hit, block, status, cast, resource spending, cleanse, summon, or reward).
3. For bound inheritance, drop a fixture and forge it with the existing Iron Sentinel skill. This consumes that source item. `/rpg-trace sentinel start`, `/rpg-trace sentinel snapshot`, fight, and take another snapshot. Use `/rpg-trace gear` for actual triggered outcomes.
4. `/rpg-trace sentinel stop` and `/rpg-trace gear stop`. Both commands print the saved trace path. Compare EXPECTED rows; NOT_APPLICABLE is not a failed check. OWNER_ONLY and INAPPLICABLE bound affixes must not appear as inherited modifiers. Owner-side minion affixes can still affect the companion through separately recorded owner equipment.

WA-141 needs two real distinct daggers: use qa159-08 together with qa159-09. A single-item Sentinel cannot fabricate an offhand. WA-083/147 need a real successful block; the current unguarded Sentinel cannot fabricate one. Passive proc chances are not guaranteed per hit. A status-potency line needs a compatible status source, and WA-112 requires an actual successful-cleanse event. These prerequisites are preserved.

### R245 Elite birth status

`/rpg worldconfig status` (permission `inigmasgames.rpg.worldconfig`) is read-only.
It also reports each world's Elite admission gate, pending birth transaction count, and oldest pending age in milliseconds.
Pending age is diagnostic only: it never releases a lease or assumes a write failed.
`/rpg-trace spawn start` and `/rpg-trace spawn status` retain their existing permissions and two-minute capture.
Birth root, seal, attachment, durable publication, finish, compensation, uncertain outcome and admission-close events use the bounded priority buffer.
No configuration changes or save reset are performed by these diagnostics. R245 or newer build required for these fields.

## Owner-authored learning assignments (ME12 Stage 3 continuation)

Registered in the isolated Stage 3 candidate; not deployed or connected-accepted.
Player or server console may use these commands with the explicit permission
`inigmasgames.rpg.learning.author`. No Adventurer permission group is granted.

| Command | Effect |
| --- | --- |
| `/rpg-learning status` | Read active revision, enabled count, file path and startup rejection. |
| `/rpg-learning validate` | Validate the complete edited JSON against catalog, source identity, readiness and historical provenance; does not activate it. |
| `/rpg-learning reload` | Validate, durably archive and atomically activate the whole configuration for new acquisition opportunities. Invalid edits retain the previous configuration. |

These operations use the existing bounded configuration/difficulty IO executor.
Configuration is `<Hywind save>/mods/InigmasGames_HytaleRPGPhase00Audit/learning-sources.json`,
retaining HyARPG's existing data-root identity. For the currently reported active save,
the eventual path is
`C:/Users/Zemio/AppData/Roaming/Hytale/UserData/Saves/Hywind/mods/InigmasGames_HytaleRPGPhase00Audit/learning-sources.json`.
It is created empty on candidate startup, not by this engineering pass. Deployment
and changes to that active-save file still require separate authorization.

Owner workflow: choose an existing ready ability and an authoritative monster source,
edit the JSON, run `validate`, then `reload`. See
[schema, gates and recovery](enemies/ME12_LEARNING_AUTHORING.md).
Reload does not enable v2 births, change rewards already prepared, reset pity or grant
an ability immediately. Unassigned/disabled/restricted sources receive no roll and no
pity increment. Historical assignment archives and `learning-sources.active` are
recovery data; preserve them. ME12-R08 remains BLOCKED pending actual owner-selected
Skill and Passive acquisition evidence.
