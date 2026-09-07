# R021 — Native Ability Integration Correction

R021 is a bounded native-ability integration correction over R020. Stage 06 has not
begun. The Stage 02 resource/cooldown/damage authority and the Stage 04/05 skill
mechanics were not redesigned or rebalanced.

## Status

```ini
R021 = DEPLOYED_AWAITING_CONNECTED_NATIVE_ABILITY_QA
Stage03 = IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION
Stage04 = IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION
Stage05 = IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION
Stage06Started = false
```

Local verification and an isolated server smoke passed. These gates cannot prove
that a connected client renders the projected native cells or sends the expected
interaction chains, so this report does not claim connected completion.

## Root cause

R015/R016 observed `InteractionType.Ability2`, `Ability3`, and `Ability4` packets but
did not populate the player's native ability inventory. It then presented three
RPG-owned CustomUI lookalike cells. That architecture left Hytale's actual native
Ability2/Ability3 item controls empty, so pressing the configured native controls
had no ItemAbility root to start. The retained R015/R016 connected logs consequently
contained no `SKILL_ACTIVATION_REQUEST`, executor, projectile, or damage events.

The old conclusion that native projection was unavailable was incorrect for the
installed Update 7 build. The current API and assets provide an explicit
`InventoryComponent.AbilitySlots` item container and `Item.Ability` /
`ItemAbility` assets for the native Rune Ability path.

## Exact build and APIs audited

The correction was implemented against the installed pre-release package, not an
assumed API:

| Artifact | SHA-256 |
|---|---|
| `Server/HytaleServer.jar` (`0.7.0-pre.1`) | `EC57E9BD6E2CA3CB16CC5883D42B04A0C64D382DEE532C5BC1CFCF68421E1EE3` |
| `Assets.zip` (`0.7.0-pre.1`) | `46F6AA12DECF4F900FCFDF28ECC67568403C37A3AD0A03A4E4CF7653A324AE39` |

The JAR inspection confirmed:

- `InventoryComponent.AbilitySlots` is an `InventoryComponent` with
  `ABILITIES_LINES = 2`, `ABILITIES_LINE_WIDTH = 3`, and capacity 6;
- its two Primary indices are 0 and 3;
- `ItemContainer.getCapacity`, `getItemStack`, and transactional
  `setItemStackForSlot` are public supported mutation APIs;
- `ItemAbility` exposes Primary/Support slot, cast root, cost, cost type, cooldown,
  weapon, tag, and attribute data;
- `AbilityBenchWindow` binds the player's native `AbilitySlots` container; and
- `InteractionType` includes Ability1 through Ability4.

The installed assets confirmed native Rune item/root-interaction schemas and the
client documents confirmed the rendering boundary:

- `Hud/Abilities/AbilitiesHud.ui` contains the native Signature control and two
  item-container-backed ability controls;
- `Hud/Abilities/Ability.ui` owns the icon frame, binding glyph, cooldown overlay,
  and error treatment;
- `Windows/AbilityBench/AbilityBenchPanel.ui` contains exactly two Primary grids,
  one per three-cell line, with two Support cells on each line; and
- the shipped Rune items use `Ability.Slot = Primary` and an `Ability.Cast` root.

Primary sources used for the audit:

- [Update 7 pre-release notes](https://hytale.com/news/2026/9/pre-release-patch-notes-update-7)
- [ItemAbility API](https://pre-release.docs.hytale.com/api/com/hypixel/hytale/protocol/ItemAbility)
- [InventoryComponent.AbilitySlots API](https://pre-release.docs.hytale.com/api/com/hypixel/hytale/server/core/inventory/InventoryComponent.AbilitySlots)
- [AbilityBenchWindow API](https://pre-release.docs.hytale.com/api/com/hypixel/hytale/builtin/abilities/window/AbilityBenchWindow)
- [AbilitiesPlugin API](https://pre-release.docs.hytale.com/api/com/hypixel/hytale/builtin/abilities/AbilitiesPlugin)

Machine-readable hashes, constants, and source URLs are retained in
`evidence/corrections/R021/native-ability-api-audit.json`.

## Corrected ownership model

The runtime path is now:

```text
authoritative RPG loadout
-> runtime projection into native AbilitySlots Primary item
-> Hytale native ability HUD/input
-> observed native SyncInteractionChains Ability2/Ability3
-> RpgSkillActivationService / SkillExecutionService
-> existing Stage 04/05 executor
-> Hytale world, damage, and projectile authority
```

There is no independent UI loadout copy. Successful loadout transactions notify
the native projection immediately, and a bounded 4 Hz reconciliation pass repairs
login/rejoin/world/container changes from authoritative RPG state.

Mapping is exact:

| RPG logical slot | Native action | Native Primary index |
|---|---|---:|
| `skill01` | `Ability2` | 0 |
| `skill02` | `Ability3` | 3 |
| `skill03` | unavailable in this build | none |

The projection uses 12 project-owned Primary ItemAbility assets for the already
executable Stage 04/05 cohort. Each uses an existing Hytale Rune icon/model/texture,
while Hytale owns all frame, binding, cooldown/error, and lower-right layout chrome.
The RPG does not create or patch a physical key binding.

### One gameplay authority

Every projection item has:

```json
{"Slot":"Primary","Cooldown":0,"Cost":0,"CostType":"None","Cast":"Root_RPG_Ability_Bridge"}
```

`Root_RPG_Ability_Bridge` is a 0.01-second `Simple` interaction with no damage,
stat change, cooldown trigger, projectile, status, or resource effect. It exists
only to make Hytale initiate an identifiable native Ability2/Ability3 chain. RPG's
existing compiled plan, resource reservation/commit, cooldown, damage, projectile,
status, critical, and equipment validation remain the sole gameplay authority.

This intentionally prefers exactly-once correctness over native cooldown animation
in the first correction. If native presentation later needs RPG cooldown state, it
must use a proven presentation-only/native-single-authority design; a second
cooldown is not permitted.

### Safe native-rune policy

RPG projection items are recognized only by the `RPG_Ability_` prefix. The bridge:

- writes into an empty Primary cell;
- replaces or clears only an existing RPG-owned projection item;
- never overwrites, moves, or deletes a non-RPG Rune;
- reports `NATIVE_SLOT_OCCUPIED` when a player Rune occupies the needed Primary;
  and
- removes only RPG-owned projection items on world drain, disconnect, or plugin
  shutdown.

This policy needs no second persistent ability store and cannot silently destroy a
player's native Runes. A player may move a conflicting Rune to the Rune Bag through
Hytale's Ability Bench before using that RPG slot.

## Custom HUD and input cleanup

R021 deletes all three custom RPG ability cells, their action labels, skill labels,
ready/not-ready/cooldown/error controls, update commands, UI transition traces, and
four custom/native-lookalike PNGs. `HudComponent.Abilities` is never hidden or
mutated. The native Signature Move remains Ability1 and the adapter always maps it
to no RPG skill.

The duplicate historical `AbilityInputObserver` runtime call and its command were
removed. The sole active RPG input seam now observes Hytale's authoritative
`SyncInteractionChains` and maps only Ability2/Ability3. It does not inspect a
physical keyboard key, modify settings, patch input files, or register hotkeys.

## Ability4 capability result

```ini
Ability4 = NATIVE_ABILITY4_UNAVAILABLE
```

Update 7 and `InteractionType` name Ability4, but the exact installed implementation
does not expose a third supported Rune Primary line or a third item-container-backed
native ability cell. `AbilitySlots` has two lines only; the Ability Bench has two
Primary grids; and the native Abilities HUD has two optional item controls beside
Signature. No supported server container index -> client cell -> ItemAbility cast
path was found for a third Primary.

Therefore R021 does not infer support from the enum, fabricate a third cell, or
steal a key. `skill03` remains unchanged in persisted RPG progression/topology and
is reported as `NATIVE_ABILITY4_UNAVAILABLE` when equipped. A future Hytale build
can add a supported projection without schema migration or loss of that slot.

## Diagnostics

`/rpg dev ability-status` is observational. It reports:

- whether `HudComponent.Abilities` is in the native visible set;
- AbilitySlots presence, implementation identity, capacity, line count, and width;
- Primary indices and their current ItemIds;
- `skill01`/`skill02` mappings and projection/conflict state; and
- the exact Ability4 result for `skill03`.

Bounded JSONL events now distinguish projection, container conflict, native input,
and Ability4 unavailability:

```text
NATIVE_ABILITY_SLOT_PROJECTED
NATIVE_ABILITY_SLOT_CLEARED
NATIVE_ABILITY_SLOT_CONFLICT
NATIVE_ABILITY_INPUT_OBSERVED
NATIVE_ABILITY4_UNAVAILABLE
```

The input observation and the existing activation/validation/executor events share
the same correlation ID. Projection events are transition-only, and reconciliation
is throttled, so no per-frame trace spam is produced.

No R021 connected trace exists yet because the isolated smoke has no player client.
The earliest outstanding connected boundary is:

```text
projected AbilitySlots item -> native client HUD renders item
```

Once rendered, the next boundary is native Ability2/Ability3 interaction arrival.
The existing later traces will identify validation or executor failures without
assuming those systems are broken.

## Verification and deployment

| Gate | Result |
|---|---|
| Branch | `RPG` |
| Implementation commit | `a70be76410ddb76a75ed9945bf3faf31598907bc` |
| Revision/version | `R021` / `0.0.14` |
| Hytale target | `0.7.0-pre.1` |
| Player schema | `3` (unchanged) |
| Aggregate unit/regression tests | PASS — 134, 0 failures/errors/skips |
| Source CustomUI validation | PASS — 16 documents |
| Packaged RPG CustomUI validation | PASS — 9 documents |
| Native ItemAbility assets | PASS — 12 |
| Installed server asset validation | PASS |
| Custom RPG ability controls/art/update code absent | PASS |
| Single native interaction observer | PASS |
| Effect-free, zero-cost, zero-cooldown bridge | PASS |
| R020 vanilla resource ownership | preserved |
| R020 XP geometry | preserved — 702/696/1 px assets, 22 px fill |
| CanvasUI source | unchanged |
| Isolated three-mod server smoke | PASS — ready and clean shutdown |
| RPG JAR SHA-256 | `3DF2C1FA69B22B6F851EF4509EDCA33F1E4BA549F86C04D463A5E55C700F7B8C` |

The RPG save now contains exactly:

| Mod | SHA-256 |
|---|---|
| `CanvasUI-0.1.0.jar` | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| `HYTALEDEVLIB-0.5.0.jar` | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| `HytaleRPG-0.0.14.jar` | `3DF2C1FA69B22B6F851EF4509EDCA33F1E4BA549F86C04D463A5E55C700F7B8C` |

The previous `HytaleRPG-0.0.13.jar` is retained at
`evidence/corrections/R021/rollback/HytaleRPG-0.0.13.jar`.

## Rollback

Stop the RPG world, remove `HytaleRPG-0.0.14.jar`, and restore the retained
`HytaleRPG-0.0.13.jar`. No player schema changed. The R021 bridge never overwrites
non-RPG Runes, and it removes only its own `RPG_Ability_` runtime items during normal
teardown.

## Stage verification status

R021 changes no Stage 03/04/05 completion claim. Stage 03 still requires connected
proof of native HUD projection and input. Stage 04 still requires at least one
native-triggered strike/movement execution. Stage 05 still requires at least one
native-triggered projectile through native collision/callback authority. All remain
implemented and locally regressed, awaiting the connected checklist.
