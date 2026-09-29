# HyARPG gameplay and QA commands

This is the maintained command reference for the current HyARPG source. Update it in the **same change** as an essential command's implementation. Syntax and availability come from command registration, not from old QA reports. Commands omitted here are internal experiments or narrow one-off probes. `Adventure` means available to an ordinary player in Adventure mode; `operator` means the named server permission is required. Examples that grant items or alter progress are intended for QA unless the row says otherwise.

## Inventory, character, and skills

| Command | Effect | Access |
| --- | --- | --- |
| `/rpg character` or `/rpg inventory` | Open the unified Inventory/Character workspace. `/rpg inventory` is a development entry point; the normal Inventory key is the intended player entry. | Adventure |
| `/rpg iconposes` | Open the read-only, 20-sample leather/weapon spatial-icon pose gallery. Esc closes it; no gear is granted. | Adventure |
| `/rpg skilltree` | Open the existing Skill Tree directly. The Skills tab in the character workspace opens this same tree. | Adventure |
| `/rpg stats` | Show raw/effective attributes, derived combat values, and native resources. | Adventure |
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

Gear generation is self-targeted and protected by `inigmasgames.rpg.gear.author`. It creates real QA gear with protected QA provenance; use a test character/save when possible. If a spatial bag owns inventory, delivery uses the spatial transfer coordinator; otherwise it goes to native inventory.

| Command | Effect |
| --- | --- |
| `/rpg gear types` / `/rpg gear rarities` | List accepted gear type tokens and qualities. Eras are `normal`, `nightmare`, `hell`; qualities are `normal`, `magic`, `rare`. |
| `/rpg gear <type> <normal\|magic\|rare> <normal\|nightmare\|hell> [1..99\|max\|seed:<seed>] [seed]` | Generate one protected item. Examples: `/rpg gear shortbow magic normal`, `/rpg gear sword rare nightmare max seed:qa-sword-01`. The optional seed reproduces the roll. |
| `/rpg gear spawn <gm.base.id> <900..1000> <NORMAL\|MAGIC\|RARE>` | Create a specific catalog base with a fixed intrinsic thousandths roll. Example: `/rpg gear spawn gm.battleaxe_iron.n 950 MAGIC`. Use a base ID from the gear catalog; invalid/unmapped IDs are rejected. |
| `/rpg gear ring` | Create one Copper Ring (permission `inigmasgames.rpg.gear.author`). In spatial mode it is admitted to the private bag and saved before use; in native mode it goes to native inventory. Select it in the spatial grid, then click a left/right ring slot to equip. Click an occupied ring slot to return it to the bag; a free rectangle is required. No ring affixes or stat bonuses are authored yet. |
| `/rpg gear inspect` | Describe the held managed item, its frozen identity and requirements. This inspection command is available to Adventure players. |
| `/rpg gear odds <era> <1..99> <common\|specialist\|elite\|miniboss\|boss> [seed]` | Inspect production loot weights and one deterministic diagnostic result without granting an item. |
| `/rpg level <1..99\|reset>` | Set your RPG level or reset allocated attributes. Requires `inigmasgames.rpg.level`; changes persistent progression. |
| `/rpg iron-sentinel` | Learn Iron Sentinel for QA, then equip it via `/rpg equip skill01 Iron Sentinel`. Requires `inigmasgames.rpg.gear.author`; changes progression. |
| `/rpg dev points grant <positiveInteger>` | Grant pending/unspent attribute points without XP. Adventure-accessible development fixture; changes progression. |
| `/rpg dev attribute <str\|dex\|int\|wis\|luck> <nonnegativeInteger>` / `/rpg dev reset` | Override one raw attribute, or reset development attributes to 10. Adventure-accessible development fixtures. |
| `/rpg dev xp-display <0..100\|clear>` | Set or clear a temporary HUD XP percentage. It grants no XP and does not persist. |

## Difficulty and world controls

| Command | Effect | Access |
| --- | --- | --- |
| `/rpg difficulty status` | Show current world difficulty, unlocks, and golem checklists. | Adventure |
| `/rpg difficulty inspect` | Show nearby authored monster combat profiles. | Adventure |
| `/rpg difficulty return` | Return to the initial Normal campaign spawn. | Adventure; moves character |
| `/rpg difficulty prepare` | Prepare persistent Nightmare and Hell worlds. | `inigmasgames.rpg.difficulty.author` |
| `/rpg difficulty portal <normal\|nightmare\|hell>` | Place an authored portal on clear ground ahead. | `inigmasgames.rpg.difficulty.author`; persists world asset |
| `/rpg difficulty remove <portalUuid>` | Remove an authored portal. | `inigmasgames.rpg.difficulty.author`; changes world |
| `/rpg difficulty encounter <earth\|flame\|frost\|sand\|thunder>` | Place a campaign golem encounter. | `inigmasgames.rpg.difficulty.author`; changes world |
| `/rpg spawns status` / `/rpg spawns <0.25..8.0>` / `/rpg spawns reset` | Inspect, persistently scale, or restore native environmental NPC density. | `inigmasgames.rpg.spawns` |

`/rpg difficulty force <era>` is a test-only travel command. It additionally requires `inigmasgames.rpg.difficulty.force` **and** a server started with `-Drpg.difficulty.testProfile=true`; it does not grant milestones or unlocks. Do not use it as a player travel route.

## Traces and protected copied-save acceptance

| Command | Effect | Availability |
| --- | --- | --- |
| `/rpg-trace status` | Show skill telemetry mode and metrics. | Explicit/admin command permission |
| `/rpg-trace normal\|detailed\|performance` | Set skill telemetry detail. Use `performance` for raw tick timings; return to `normal` afterward. This is diagnostic, not a skill grant. | Explicit/admin command permission |
| `/rpg tabtrace` | Arm one bounded, two-second native Inventory-action trace; then press Inventory/Tab once, close it, and inspect the trace under RPG logs. | Adventure; diagnostic, no page mutation |

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
