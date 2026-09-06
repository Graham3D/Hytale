# Stage 03 Report — Character Screen, RPG HUD, Skill Bar, and XP Presentation

## Status

```ini
Stage03 = BLOCKED
Stage04SafeToBegin = false
RpgRevision = R012
RpgVersion = 0.0.5
HytaleTarget = 0.7.0-pre.1
ProductionLinkTreeFrontend = DEFERRED_PENDING_HYTALE_NOESIS_SERVER_UI
```

R012 is implemented, tested, bare-server-smoked, and deployed. Stage 03 remains
`BLOCKED` because no connected client has yet exercised the R012 CustomUI documents,
HUD positioning, attribute buttons, native Ability inputs, teardown, or restart/rejoin.
The earliest unproved boundary is a normal connected join that successfully creates
`RpgHud.ui`. This status is evidence discipline, not an implementation failure and
not a consequence of the owner-approved Link Tree deferral.

Stage 04 has not begun and is not safe to begin until the connected checklist closes.

## Revision and artifact identity

| Item | Value |
|---|---|
| Starting commit | `83fce1bd1cde71a871c5e734c1377da861d49b33` (Stage 02 R011 closure) |
| Ending R012 implementation commit | `df7a1996e2e7393b86effff9d9d44b41668738c2` |
| Branch | `RPG` |
| Hytale target | `0.7.0-pre.1` |
| HytaleServer.jar SHA-256 | `EC57E9BD6E2CA3CB16CC5883D42B04A0C64D382DEE532C5BC1CFCF68421E1EE3` |
| RPG artifact | `HytaleRPG-0.0.5.jar` |
| RPG JAR SHA-256 | `301F3DF1E0B2E64725CF770403028791A9E918C4E4EEBFB6B7EFEC6133CCA960` |
| Persisted player schema | `2` (unchanged) |
| Canonical content | 87 skills, 66 passives |

Packaged `manifest.json` and `rpg-build.properties` identify revision `R012`,
version `0.0.5`, and stage `03`. The startup marker is
`RPG_STAGE03_READY revision=R012` and the RPG HUD renders `R012` at its top-right.

## Authority and UI architecture

The production dependency direction is:

```text
RPG gameplay/state services
        -> RpgUiProjectionService
        -> immutable CharacterSheetViewModel / RpgHudViewModel
        -> RpgCharacterPage / RpgHud
```

`RpgUiProjectionService` is the one presentation projection boundary. It reads the
existing authoritative `RpgLoadoutOperations`, `DerivedStatService`,
`RpgCooldownService`, and native resource snapshots. It does not persist state.
The hot projection path uses `getPresentationView`, which intentionally avoids the
command-oriented compiler trace so a 4 Hz HUD poll cannot flood
`skill-trace.jsonl`.

The immutable presentation types are:

- `CharacterSheetViewModel`
- `RpgHudViewModel`
- `SkillSlotView`
- `XpView`
- `NativeResourceView`

No UI class writes player JSON, graph edges, loadout slots, native resources, XP, or
attributes directly. UI allocation submits an intent to
`AttributeAllocationService`; the service reaches the same `RpgLoadoutService`
transaction/persistence boundary used by the established backend.

## Exact 0.7.0-pre.1 capability audit

The installed server JAR was re-audited before implementation. Public API evidence
confirmed:

- `InteractionType.Ability1`, `Ability2`, `Ability3`, and `Ability4`;
- `HudManager.getVisibleHudComponents`, `setVisibleHudComponents`, and
  `hideHudComponents`;
- `CustomUIHud.build`, `show`, and incremental `update`;
- `InteractiveCustomUIPage.handleDataEvent` and normal `PageManager` opening;
- `PlayerReadyEvent` and `PlayerDisconnectEvent` lifecycle hooks.

No supported arbitrary global Character/Link-tree key registration route was found.
`RpgUiOpenInputAdapter` is retained and reports `COMMAND_ONLY`; `/rpg character` is
the supported Stage 03 entry. C/K, client settings edits, and camera-binding theft
were not implemented.

Machine-readable results are in `evidence/stage-03/R012/api-audit.json`.

## RPG HUD

`RpgHud.ui` contains three independent regions:

- centered above the hotbar: `Mana | Health | Stamina`, each with current and maximum;
- a ten-pip XP/level line below the resource strip;
- four logical skill cells immediately to the right of the nine-slot native hotbar.

The four cells always map in order to `skill01`, `skill02`, `skill03`, and `skill04`.
They consume the same persisted loadout used by `/rpg equip`; there is no HUD loadout
copy. A cell projects empty, cooldown, or unavailable state, its canonical name, and
a deterministic family/empty text placeholder while final icon assets are absent.
Equip/unequip changes are detected by the regular diff poll, so reconnect is not
required.

The resource adapter reads only `EntityStatMap`. The HUD owns no Health, Mana, or
Stamina values. `HudVisibilityLease` snapshots the full prior native visibility set,
hides only native `Mana`, `Health`, and `Stamina`, and restores the exact snapshot on
disconnect, plugin shutdown, reinstall/reset, initial-create failure, or refresh
failure. Native Hotbar and Compass remain untouched.

## Update model and performance policy

The server has no audited native stat-change callback appropriate for all three
display values, so R012 uses a maximum 4 Hz (250 ms) presentation poll per player.
The full HUD document is built once. Subsequent work compares immutable view models
and emits only property updates for changed resources, skills, XP, or the level-up
notice. It never rebuilds the HUD per server tick.

Every five seconds, one aggregate `HUD_REFRESHED` event records measured poll and
update rates. No per-frame trace is emitted. Local deterministic behavior is proven;
the actual connected rates remain pending and are a Stage 03 closure item.

## Skill input and activation boundary

`HytaleAbilitySkillInputAdapter` maps the player's configured native actions exactly:

```text
Ability1 -> skill01
Ability2 -> skill02
Ability3 -> skill03
Ability4 -> skill04
```

It observes initial `SyncInteractionChain` edges, deduplicates a chain/action pair,
and dispatches bounded queued requests on the world thread. It does not name or alter
physical keys. `RpgSkillActivationService` records `SKILL_ACTIVATION_REQUEST` and
then rejects an equipped Stage 03 slot with typed
`EXECUTOR_NOT_IMPLEMENTED` (`EMPTY_SLOT` for an empty slot). The request and rejection
retain one correlation ID plus explicit root/instance IDs. No resource service or
cooldown start is called. Quick Slash, Strike, Projectile, and Fire Bolt execution
were not implemented.

The public constants and pure mapping pass locally; actual client action emission is
still a connected proof item.

## Character screen

`/rpg character` opens the standalone `RpgCharacterPage`. Its Diablo-II-inspired,
Hytale-native layout displays:

- derived Character level, XP into the level, XP required, and ten pips;
- unspent and pending level-up attribute points;
- raw and effective STR, DEX, INT, WIS, and LUCK;
- maximum Health, Mana, and Stamina;
- live native Mana, Health, and Stamina;
- Heavy, Light, and Magic damage multipliers; Healing multiplier;
- Critical Chance and multiplier, Cooldown Recovery, Learn Rate, Upgrade Success,
  and Magic Find.

The page formats values only. Every formula comes from `DerivedStatService` or
`CharacterXpProjectionService`.

Each `+` button includes the view's expected RPG revision. A click validates the
revision and point balance, increments exactly one raw attribute, consumes exactly
one unspent point, consumes pending points first, compiles, atomically saves, swaps
the cached state only after successful persistence, reapplies derived native maxima,
and refreshes the page. Validation or save failure leaves attribute, unspent points,
pending points, and revision unchanged. Respec was not implemented.

`/rpg dev points grant 5` adds five unspent and five pending points without awarding
XP. It uses the same transaction boundary and exists only as a development fixture.
The notice beneath the compass derives from persisted `pendingLevelUpPoints` and
remains visible until that counter reaches zero.

## XP/level presentation

`CharacterXpProjectionService` owns the single equation:

```text
XP_to_Next(L) = round-half-up-to-10(
  100 * L^1.6 * (1 + 5 * (max(0, L - 80) / 18)^3)
)
```

It computes all transitions for levels 1–98, cumulative level starts, current
level/progress from total character XP, clamps display progress, produces exactly ten
fractional pip fills, and reports Level 99 as capped. Neither HUD nor page duplicates
the formula.

`/rpg dev xp-display <0..100|clear>` is a nonpersistent presentation fixture for pip
proof. It does not change `currentXp`, award XP, or implement Stage 12 progression.

## UI diagnostics

UI-only events write asynchronously to bounded
`logs/rpg/ui-trace.jsonl` (4 MiB, four retained files). Events cover HUD create,
diff refresh/rates, teardown/restoration, Character open/refresh/close, allocation
request/commit/reject, skill-bar changes, XP projection, and level-up notice changes.
Trace enqueue/write failures are caught and cannot roll back or stop gameplay.
Generic UI events are not written to `skill-trace.jsonl`; only the actual skill
activation boundary uses the skill trace.

Connected trace location:

```text
Saves/RPG/mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/ui-trace.jsonl
```

## Automated verification

`tools/Verify-Stage03.ps1` completed a clean multi-project build and packaged-entry
audit:

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| RPG (Stage 01B + Stage 02 + Stage 03) | 57 | 0 | 0 | 0 |
| CanvasUI retained regression | 21 | 0 | 0 | 0 |
| Total | 78 | 0 | 0 | 0 |

Stage 03 coverage includes all 98 XP transitions and 99 cumulative starts,
round-half-up-to-ten, all requested pip boundaries, all five raw/effective values,
derived projections, transactional allocation, stale/no-point rejection, injected
save rollback, pending notice lifecycle, resource ordering, four-slot equip/unequip
projection, exact Ability1–4 mapping, typed activation rejection without cooldown,
native HUD snapshot/restore, and trace failure isolation.

CustomUI validation passed all 13 source documents. The packaged R012 JAR contains
both new UI documents and every required service/adapter/view-model class.

The separate bare-server smoke discovered and enabled exactly HytaleDevLib, CanvasUI,
and HytaleRPG, observed Hytale `0.7.0-pre.1`, emitted both R012 startup markers, and
found no RPG setup/enable exception. The boot-command shutdown process returns the
established exit code 9 after successful enable.

Evidence:

- `evidence/stage-03/R012/verification.json`
- `evidence/stage-03/R012/api-audit.json`
- `evidence/stage-03/R012/server-smoke.txt`
- `evidence/stage-03/R012/server-smoke-summary.json`
- `evidence/stage-03/R012/installation.json`

## Deployment

The RPG save contains exactly:

| Mod | SHA-256 |
|---|---|
| `CanvasUI-0.1.0.jar` | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| `HYTALEDEVLIB-0.5.0.jar` | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| `HytaleRPG-0.0.5.jar` | `301F3DF1E0B2E64725CF770403028791A9E918C4E4EEBFB6B7EFEC6133CCA960` |

CanvasUI code was not modified. The deployed R008 CanvasUI JAR hash remains unchanged.
Its presence is only for regression and the future frontend seam; the RPG backend,
HUD, Character page, and commands do not require opening CanvasUI.

## Connected-client and restart evidence

`PENDING`. No claim of connected CustomUI success, correct screen positioning,
native action delivery, measured live update rate, state persistence across the R012
restart, or visibility restoration is made yet. Stage 03 must remain `BLOCKED` until
these observations and the relevant UI/server trace records are captured.

## Connected closure checklist

1. Fully restart the RPG world, rejoin, and confirm the `R012` badge, native nine-slot
   hotbar, centered `Mana | Health | Stamina`, and four right-side skill cells appear
   without a CustomUI/server error.
2. Confirm Quick Slash is in skill01 and Fire Bolt is in skill02. Run
   `/rpg unequip skill01`, then `/rpg equip skill01 "Quick Slash"`; confirm cell 1
   changes live both times without reconnecting.
3. Run `/rpg stats`, then `/rpg character`; compare level, native pools, all five
   raw/effective attributes, and derived values.
4. Run `/rpg dev points grant 5`; confirm the notice appears beneath the compass.
   Spend the five `+` points across multiple attributes, checking values/pools and
   remaining points after each click; confirm the notice disappears after the fifth.
5. Run `/rpg dev xp-display 0`, `9.9`, `10`, `50`, `99.9`, and `100`, observing the
   ten pips each time; finish with `/rpg dev xp-display clear`.
6. Press the player's configured Ability1 through Ability4 controls. Equipped slots
   must trace `SKILL_ACTIVATION_REQUEST` followed by `EXECUTOR_NOT_IMPLEMENTED`, while
   Mana/Stamina and cooldown presentation remain unchanged.
7. Close/reopen `/rpg character` several times. Restart/rejoin and confirm the spent
   attributes, remaining points, and loadout persist and the native HUD remains
   healthy through teardown/recreation.

After this checklist, preserve screenshots plus server logs, `ui-trace.jsonl`, and
`skill-trace.jsonl`. The report can then be amended to `PASS` only if every Stage 03
gate has connected evidence and no serious UI diagnostic failure.

## Known limitations and explicit deferrals

- Production Link Tree frontend:
  `DEFERRED_PENDING_HYTALE_NOESIS_SERVER_UI` by owner decision.
- CanvasUI R008 is frozen; no pointer experiments or substitute grid/menu editor were
  added.
- C/K global open bindings remain unavailable through the audited public server API;
  `/rpg character` and existing backend commands are the supported entries.
- Skill art uses readable deterministic placeholders.
- Stage 03 activation stops at `EXECUTOR_NOT_IMPLEMENTED`; no family executor exists.
- Actual XP earning, rewards, level-up awards, mastery, acquisition, and anti-farm
  systems remain Stage 12 work.
- Connected visual fit and actual HUD update rates may require an R013 correction.

## Rollback

Stop the RPG world, remove `HytaleRPG-0.0.5.jar`, and restore
`evidence/stage-03/R012/rollback/HytaleRPG-0.0.4.jar` to the RPG save's `mods`
directory. Keep the existing CanvasUI and HytaleDevLib JARs. Player schema remains v2;
do not downgrade or replace player JSON files.

## Closure decision

```ini
Stage03 = BLOCKED
EarliestUnprovedBoundary = CONNECTED_JOIN_RPG_HUD_CREATE
Stage04SafeToBegin = false
```

No Stage 04 family implementation was started.
