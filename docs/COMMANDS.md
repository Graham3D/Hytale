# HyARPG gameplay and QA commands

This is the maintained command reference for the current HyARPG source. Update it in the **same change** as an essential command's implementation. Syntax and availability come from command registration, not from old QA reports. Commands omitted here are internal experiments or narrow one-off probes. `Adventure` means available to an ordinary player in Adventure mode; `operator` means the named server permission is required. Examples that grant items or alter progress are intended for QA unless the row says otherwise.

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
| `/rpg equip <skill01..skill03\|passive01..passive06> <skillOrPassiveName>` | Put an owned skill or passive in a permanent slot. Example: `/rpg equip skill01 Iron Sentinel`. | Adventure; changes loadout |
| `/rpg unequip <slot>` | Remove a skill/passive and affected links. | Adventure; changes loadout |
| `/rpg link <sourceNode> <targetNode>` / `/rpg unlink <sourceNode>` | Edit a validated Link Tree edge. | Adventure; changes loadout |
| `/rpg loadout` / `/rpg compile` | Inspect the equipped graph or validate its compiled plan. | Adventure; read-only |
| `/rpg managuard <1..50>` | Set the percentage of Mana reserved for Managuard; does not refill Mana or shield. | Adventure; changes allocation |
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
| `/rpg gear odds <era> <1..99> <common\|specialist\|elite\|miniboss\|boss> [seed]` | R191+ with gear QA enabled; requires `inigmasgames.rpg.gear.author`; read-only. Shows rank-default guaranteed picks, optional pick chances, cap, each deterministic optional roll and child receipt ID, and successful item's quality roll/weights/result at current Magic Find. Does not grant or save an item. Natural kills resolve canonical enemy roles; no role overrides are authored in this revision. Inspect deaths with `/rpg geartrace on`. |
| `/rpg gear affixqa qa159 list` | R195+: list all 17 distinct bases, native IDs, families/slots, saved spatial footprints, affix IDs and Sentinel eligibility. Requires `inigmasgames.rpg.gear.author`; read-only. |
| `/rpg gear affixqa spawn qa159-01` through `/rpg gear affixqa spawn qa159-17` | R195+: grant the named deterministic item through existing saved delivery. Four armor pieces have ten affixes; two held items have ten; eleven held items have nine. Exactly 159 functional affixes, once each, WA-155 excluded. Bypasses only the rarity count/prefix-suffix budget; carrier, exclusion groups, requirements, magnitude grids and functional/native gates remain. All affixes work at runtime, including reward bonuses. Requires `inigmasgames.rpg.gear.author`; changes inventory/save. |
| `/rpg gear affixqa list` | List 160 deterministic affixed/control pairs plus current legal combined fixtures, with base, group, rarity, frozen IDs, names, rolls, selectors, and `SPAWNABLE` or `PENDING_ADAPTER`. Requires `inigmasgames.rpg.gear.author`; read-only. |
| `/rpg gear affixqa spawn <fixtureId\|armor\|weapons\|support\|summons\|all>` | Issue only mapped, legal, currently enabled fixtures with protected QA provenance. Requires `inigmasgames.rpg.gear.author`; changes inventory/save. Equipped affixes use the same runtime mechanics as looted gear, including reward-affecting Magic Find and Gold Find. Group issuance saves at most eight items per call; repeat the same command to resume at the next saved item in this server process. A full bag or failed save stops at the named fixture. `-control` rows have the same base, intrinsic and requirements as their `-affixed` pair. |
| `/rpg gear affixqa spawn tooltip-common-battleaxe` | R176+: deterministic Common Adamantite Battleaxe for native tooltip comparison. Normal-era base, item level 40, 95% intrinsic roll, no affixes. Requires `inigmasgames.rpg.gear.author`; creates protected QA gear through the existing saved delivery path. |
| `/rpg gear affixqa spawn tooltip-magic-battleaxe` | R176+: same base/intrinsic roll with one legal Honed (WA-001) affix. Quality label is **Rare**, with blue presentation. Same author permission and save effects. |
| `/rpg gear affixqa spawn tooltip-rare-battleaxe` | R176+: same base/intrinsic roll with legal Brutal (WA-002), Glacial-Edged (WA-018), and Alacrity (WA-008) rolls. Quality label is **Epic**, with purple presentation and blue affix text. Same author permission and save effects. |
| `/rpg level <1..99\|reset>` | Set your RPG level; `reset` atomically sets level 1 / XP 0, restores every raw attribute to 10, refunds points above baseline into unspent points, and clears the pending level-up indicator. Already-unspent points remain. Requires `inigmasgames.rpg.level`; changes persistent progression, with no save/build marker gate. Skills, rewards, and gear are retained. |
| `/rpg iron-sentinel` | Learn Iron Sentinel for QA, then equip it via `/rpg equip skill01 Iron Sentinel`. Requires `inigmasgames.rpg.gear.author`; changes progression. |
| `/rpg dev points grant <positiveInteger>` | Grant pending/unspent attribute points without XP. Adventure-accessible development fixture; changes progression. |
| `/rpg dev attribute <str\|dex\|int\|wis\|luck> <nonnegativeInteger>` | Override one raw attribute. Adventure-accessible development fixture. `/rpg dev reset` was removed in R196; use `/rpg level reset` with its operator permission. |
| `/rpg dev xp-display <0..100\|clear>` | Set or clear a temporary HUD XP percentage. It grants no XP and does not persist. |

For the R167 affix QA pass, keep the original fixtures above or select an isolated pair from `affixqa list`. For example, `/rpg gear affixqa spawn ab-wa-074-affixed` and `/rpg gear affixqa spawn ab-wa-074-control` compare Fire Resistance on the same base. The `ab-wa-001-affixed` / `ab-wa-001-control` pair compares local weapon damage. Equip only one side of the pair at a time; carrying it in the backpack does not apply its affixes. Gear requirements and broken-item rules still apply. Use `/rpg geartrace on`, label the baseline/equipped/Sentinel steps with `/rpg geartrace mark <label>`, then `/rpg geartrace off` to save the capture.

WA-155 remains unavailable for item generation pending your measured light-radius calibration. Use the bounded `lightprobe` and read-only `lightcal` commands below for that measurement. The contract stays in metres. QA gear contributes Magic Find and Gold Find to the same UI and reward owners as ordinary gear; QA provenance is not a runtime exclusion. Simulacrum retains its finite 20-Mana base cost; summon-cost affixes reduce that existing cost. WA-112 listens to successful cleanse receipts and adds no new skill.

## Difficulty and world controls

| Command | Effect | Access |
| --- | --- | --- |
| `/rpg difficulty status` | Show current world difficulty, unlocks, and golem checklists. | Adventure |
| `/rpg difficulty inspect` | Show nearby authored monster combat profiles. | Adventure |
| `/rpg difficulty return` | Return to the initial Normal campaign spawn. | Adventure; moves character |
| `/rpg difficulty prepare` | Prepare persistent Nightmare and Hell worlds. | `inigmasgames.rpg.difficulty.author` |
| `/rpg difficulty portal <normal\|nightmare\|hell>` | Place an authored portal on clear ground ahead. | `inigmasgames.rpg.difficulty.author`; persists world asset |
| `/rpg difficulty remove <portalUuid>` | Remove an authored portal. | `inigmasgames.rpg.difficulty.author`; changes world |
| `/rpg difficulty encounter <earth\|flame\|frost\|sand\|thunder>` | Place the selected native campaign golem on clear ground ahead, with a saved campaign encounter marker for eligible death credit. R192 resolves the native appearance bounds before spawn; an obstructed location is rejected without placing an encounter. | `inigmasgames.rpg.difficulty.author`; changes world, no save/build gate beyond U7P5 |
| `/rpg spawns status` / `/rpg spawns <0.25..8.0>` / `/rpg spawns reset` | Inspect or change the native environmental NPC density. R237 writes the same `world.nativeEnvironmentSpawnMultiplier` field in the active save-root `world-config.json`; the old density file is read once on first migration and retained for recovery. Changes apply to loaded spawning worlds. `reset` sets native 1×. | `inigmasgames.rpg.spawns`; operator-only; requires R237+ and the active RPG save |
| `/rpg worldconfig status` / `/rpg worldconfig reload` | Show active save-root configuration, per-environment native population targets/actuals, and per-world Elite pack capacity (loaded, pending, dormant, historical QA, over-cap and 64 m cells); or validate and atomically load edited `Saves/RPG/world-config.json`. Invalid JSON leaves the previous policy active. New encounter tuning applies only to future births; existing actor and reward revisions remain frozen. Reducing pack limits grandfathers loaded encounters and blocks new special births until capacity is available. See [world-config operator guide](world-config/OPERATOR.md). | `inigmasgames.rpg.worldconfig`; operator-only; requires R240+ for active-pack diagnostics and the active RPG save |

`/rpg difficulty force <era>` is a test-only travel command. It additionally requires `inigmasgames.rpg.difficulty.force` **and** a server started with `-Drpg.difficulty.testProfile=true`; it does not grant milestones or unlocks. Do not use it as a player travel route.

## Traces and protected copied-save acceptance

| Command | Effect | Availability |
| --- | --- | --- |
| `/rpg-trace status` | Show skill telemetry mode and metrics. | Explicit/admin command permission |
| `/rpg spawntrace start` / `/rpg spawntrace status` / `/rpg spawntrace stop` | Start a read-only 120-second natural monster spawn trace, inspect the event count, or stop and write its JSONL file. Captures at most 10,000 ordinary detail rows plus 2,000 priority rows for rare Elite failures and pack-lease decisions; aggregate and priority-reason counts continue after row caps. Twelve 10-second aggregate buckets span the full session, including jobs, placement rejections, native fluid-job dry-column exits, NPC additions/removals, population/budget samples, Elite outcomes, and active pack leases. R244 adds a build/source/config/asset manifest in the summary, exact production profile context, presentation outcomes, habitat assessment/backoff/work measurements, published packs in priority retention, and ten-second player-local spatial samples. Rendering remains UNKNOWN; no client acknowledgment is inferred. Automatic expiry writes under `mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/monster-spawn-trace`. No QA marker or save migration. | `inigmasgames.rpg.spawntrace`; operator-only |
| `/rpg-trace normal\|detailed\|performance` | Set skill telemetry detail. In R200, `detailed` also records native ability readiness, inbound ability actions (held item, hotbar/tools slots), native callbacks, and bridge rejections, capped at 16 boundary records/second/player. Observation does not execute casts. Enable it before reproducing the failure. Use `performance` for raw tick timings; return to `normal` afterward. This is diagnostic, not a skill grant. | Explicit/admin command permission |
| `/rpg tabtrace` | Arm one bounded, two-second native Inventory-action trace; then press Inventory/Tab once, close it, and inspect the trace under RPG logs. | Adventure; diagnostic, no page mutation |
| `/rpg nativedroptrace` | Arm one passive ten-second native Inventory drop trace after a two-second countdown. During the trace, Hywind's per-player Inventory page substitution is bypassed so Tab opens the unmodified Hytale Inventory. Observes relevant inventory/window packets, including `DropItemStack` section/slot/quantity and `MoveItemStack` fields **if the local inbound adapter exposes them**, plus server `DropItemEvent.PlayerRequest`/`Drop` events in ordered JSONL. A zero inbound count is explicitly recorded and does not prove no packet was emitted. | Adventure; no QA marker or save migration; native drop itself moves the chosen item |
| `/rpg geartrace on` / `/rpg geartrace mark [label]` / `/rpg geartrace status` / `/rpg geartrace off` | Opt-in per-player gear drop, attack, Advanced Stats, pickup, and Iron Sentinel evidence. `on` prints the JSONL path; `mark` labels a step; `off` flushes it. R195 adds accepted/rejected fixture sources, equipment before/after operator values, EXPECTED/NOT_APPLICABLE check metadata, and authoritative combat/resource/status/support receipts (including owned summon receipts). Snapshot values are not claimed as connected PASS results. Captures up to 3,000 events, four MiB, or ten minutes; disconnect ends capture. | `inigmasgames.rpg.gear.author`; read-only instrumentation, no save/build gate |
| `/rpg sentineltrace on` / `/rpg sentineltrace snapshot` / `/rpg sentineltrace off` | R195+: opt-in Sentinel JSONL capture. `snapshot` writes one live, read-only record of bound item/fixture, every affix disposition, native Health and movement, resolved attack range/cadence, protection/resistances, source modifiers, and actual active inherited aura membership. Requires `on` first; no automatic per-tick logging. Up to 3,000 events, four MiB or ten minutes; disconnect stops capture. Output is under the existing RPG plugin data `evidence/sentinel-trace` directory. | `inigmasgames.rpg.gear.author`; no inventory/save mutation; gear QA must be enabled to forge a Sentinel |
| `/rpg geartrace light` | Read WA-155 target metres, effective native radius and actual packet units; renderer calibration status is explicit. | `inigmasgames.rpg.gear.author`; read-only, no save change |
| `/rpg geartrace lightprobe <0..127>` | Temporarily project a native actor light for at most 30 seconds. `0` restores immediately if the probe still owns the component. Refuses an active affix light projection and preserves another writer's changes. | `inigmasgames.rpg.gear.author`; transient world visual, no inventory/save change; calibration QA only |
| `/rpg geartrace lightcal <r1:m1,r2:m2,r3:m3,ceiling,toleranceMetres>` | Fit three measured native-radius/metre pairs; reject inconsistent measurements. Prints calibration values, without installing them or changing gameplay. The renderer ceiling must be measured separately. | `inigmasgames.rpg.gear.author`; read-only calculation, no save change |
| `/canvasui-cursor-probe` | Open the passive-HUD pointer calibration probe. Click the prompted squares in order: top-left, top-right, bottom-left, bottom-right, center. The accepted mapping is saved for the current display size and used by the CanvasUI Skill Tree editor. | Adventure; writes only CanvasUI cursor calibration |
| `/canvasui-cursor-probe-close` | Close the CanvasUI cursor probe if its on-screen Close control is unavailable. | Adventure |

For native drop QA, run `/rpg nativedroptrace`, close chat, wait for **GO**, press Tab, drag one expendable **native** item outside the vanilla inventory frame, and release it within ten seconds. The file is written under `mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/native-drop-*.jsonl`; the command prints its exact path. Capture stops automatically, on disconnect, or at 3,000 records. It does not filter or rewrite Hytale packets or drop events. Hywind's usual Inventory entry returns when capture ends. This trace precedes any change to Hywind's outside-window drop handling.

### Opt-in Inventory / CanvasUI interaction trace

This developer trace is disabled by default and scoped to the player who enables it. It captures discrete Inventory, Gear, navigation, and CanvasUI actions, along with resolved targets, authoritative outcomes where exposed, and affected UI selectors. It does not change item movement, inventory ownership, or saves. Sessions end on `off`, disconnect, 2,500 records, or ten minutes. They write bounded JSONL to `mods/InigmasGames_HytaleRPGPhase00Audit/evidence/ui-trace/ui-trace-<UTC timestamp>-<session prefix>.jsonl` under the RPG save. The command replies with the exact path. The permission is `inigmasgames.rpg.uitrace`; no QA marker or save migration is required.

| Command | Effect | Availability |
| --- | --- | --- |
| `/rpg uitrace on` | Begin a per-player diagnostic session with a new trace session ID. | `inigmasgames.rpg.uitrace`; available since R157 |
| `/rpg uitrace mark [label]` | Write a `MARK` record in the active session. Example: `/rpg uitrace mark unequip-bow`. Label is optional and limited to 64 characters. | Same permission; active session required |
| `/rpg uitrace off` | End the session and flush the JSONL file. Disconnect also ends that player's session. | Same permission |

For QA, run `on`, click an empty cell, move a multi-cell item, attempt an invalid placement, equip/unequip, use Sort/Search, switch to Skills and Map, and click a CanvasUI control. Use `mark` before a reproduction step; run `off` before leaving the world. Correlation IDs group the records for each handled action, and a concise `UI_TRACE` completion line appears in the server log. The trace is separate from the two-second `/rpg tabtrace` and skill `/rpg-trace` commands. Pointer coordinates are recorded only when CanvasUI exposes them; continuous mouse motion is not traced.

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
2. `/rpg geartrace on`; mark your baseline, equip one fixture, inspect Inventory/Combat/Advanced Stats, then exercise its actual trigger (hit, block, status, cast, resource spending, cleanse, summon, or reward).
3. For bound inheritance, drop a fixture and forge it with the existing Iron Sentinel skill. This consumes that source item. `/rpg sentineltrace on`, `/rpg sentineltrace snapshot`, fight, and take another snapshot. Use `/rpg geartrace` for actual triggered outcomes.
4. `/rpg sentineltrace off` and `/rpg geartrace off`. Both commands print the saved trace path. Compare EXPECTED rows; NOT_APPLICABLE is not a failed check. OWNER_ONLY and INAPPLICABLE bound affixes must not appear as inherited modifiers. Owner-side minion affixes can still affect the companion through separately recorded owner equipment.

WA-141 needs two real distinct daggers: use qa159-08 together with qa159-09. A single-item Sentinel cannot fabricate an offhand. WA-083/147 need a real successful block; the current unguarded Sentinel cannot fabricate one. Passive proc chances are not guaranteed per hit. A status-potency line needs a compatible status source, and WA-112 requires an actual successful-cleanse event. These prerequisites are preserved.

### R245 Elite birth status

`/rpg worldconfig status` (permission `inigmasgames.rpg.worldconfig`) is read-only.
It also reports each world's Elite admission gate, pending birth transaction count, and oldest pending age in milliseconds.
Pending age is diagnostic only: it never releases a lease or assumes a write failed.
`/rpg spawntrace start` and `/rpg spawntrace status` retain their existing permissions and two-minute capture.
Birth root, seal, attachment, durable publication, finish, compensation, uncertain outcome and admission-close events use the bounded priority buffer.
No configuration changes or save reset are performed by these diagnostics. R245 or newer build required for these fields.
