# R018 — HUD Correction Pass

R018 is a presentation-only correction over R017. It does not begin Stage 06 or add
gameplay behavior.

## Outcome

The production HUD now presents one coherent player-facing ability area and a
centered bottom HUD stack:

```text
XP bar
Health | Mana | Stamina
native hotbar
```

The always-visible upper-right revision/skill diagnostic and the obsolete ten-pip
XP strip are removed. The native Hytale Abilities HUD remains enabled, so the native
Signature Move remains owned by Hytale. Three RPG cells sit immediately to its left:

```text
Hytale Ability1 -> native Signature Move
Hytale Ability2 -> RPG skill01
Hytale Ability3 -> RPG skill02
Hytale Ability4 -> RPG skill03
```

## Native HUD and input audit

The audit used the installed `0.7.0-pre.1` pre-release client and server artifacts.
The native `AbilitiesHud.ui` contains `SignatureAbility` and two initially-hidden,
item-container-backed optional cells. The public protocol exposes only `Primary` and
`Support`; it does not expose a supported arbitrary three-slot RPG projection. The
server CustomUI surface also does not expose the player's configured physical input
labels.

For that reason R018 preserves the native Abilities component and Signature cell,
then adds the three authoritative RPG cells beside it. Their captions are the
logical actions `Ability2`, `Ability3`, and `Ability4`; R018 does not guess or hardcode
keyboard/controller buttons. The machine-readable API evidence and source hashes are
in `evidence/corrections/R018/native-hud-api-audit.json`.

The resource graphics and ability frames were copied from the installed Hytale
client into the mod's portable resource bundle. Runtime documents reference only
JAR-relative logical asset paths under `Common/UI/Custom/Assets/RpgHud`; they do not
reference the owner's installation paths.

## Resource bars

Health, Mana, and Stamina are equal 244-pixel cells with a 24-pixel icon and a
210-pixel graphical fill. The group is centered at `Horizontal: 0, Bottom: 118`, in
the exact required order `Health | Mana | Stamina`. Values remain projections of the
native authoritative `EntityStatMap`; no duplicate resource model was introduced.

Fill widths are clamped and calculated as:

```text
round(210 * current / maximum)
```

Native Health, Mana, and Stamina HUD components are hidden under the existing exact
snapshot/restore lease to avoid duplicates. `HudComponent.Abilities` is not hidden.

## XP bar

The owner-supplied assets were copied without alteration:

| Asset | Dimensions | SHA-256 |
|---|---:|---|
| `ExperienceFrame.png` | 931 x 28 | `F173DE47BAB0EAAA5D0F1DB79EB701D03D4EBC94266CF7D05F479FCB69B26CAE` |
| `ExperienceBackground.png` | 925 x 28 | `79200B6BE97B648F4B642067A266C0C9C64DE81B02C315BC7136131203E7D6AF` |
| `ExperienceBar.png` | 1 x 22 | `B8DCBDE4295DDC2810AB043B075202D1C337D9444A867B4DF5D927794F2C3D2B` |

The rendering order is background, fill, frame, then the existing level/progress
label. The fill is anchored at the left inset and only its width changes. The usable
range is exactly 925 pixels: 0% produces width 0 and 100% produces width 925.
`CharacterXpProjectionService` remains authoritative for XP projection; diagnostic
fixtures remain presentation-only.

## Ability states and refresh behavior

Each RPG cell displays occupied/empty, ready, cooldown, and unavailable states. Live
equip/unequip and cooldown changes continue to come from the existing loadout and
runtime projection services. The HUD coordinator still polls presentation state at a
bounded four times per second, but it emits trace records only when projected state
actually changes.

R018 uses these transition records:

- `RESOURCE_HUD_REFRESH`
- `ABILITY_HUD_REFRESH`
- `ABILITY_SLOT_CHANGED`
- `XP_HUD_REFRESH`
- `HUD_LAYOUT_READY`

Each record is written through `RpgUiTraceService`, which supplies the actual
revision, player, and correlation fields. Slot records include the actual slot,
logical action, Skill ID, state, cooldown, and reason. The previous periodic
`HUD_REFRESHED` trace spam was removed.

## Verification

| Gate | Result |
|---|---|
| Branch | `RPG` |
| Hytale target | `0.7.0-pre.1` |
| RPG revision/version | `R018` / `0.0.11` |
| Player schema | `3` (unchanged) |
| Implementation commit | `434f2dcea265a176952eedadfcc1e75f49ef7aec` |
| Evidence/deployment commit | `faf1bc377f18c27b42d0e7cba96af0818448d0d9` |
| Clean RPG + CanvasUI build | PASS |
| Aggregate tests | PASS — 132 tests, 0 failures/errors/skips |
| Source CustomUI validation | PASS — 17 documents |
| Packaged RPG CustomUI validation | PASS — 10 documents |
| Portable HUD assets in JAR | PASS — all required entries present |
| Native Signature preservation audit | PASS |
| CanvasUI source | PASS — unchanged |
| Isolated server smoke | PASS — exactly three mods, ready, clean shutdown |
| RPG JAR SHA-256 | `A7453A4F15AB57C3972F463E622310282E2DF71ED0170176334F74C3934751AA` |

Machine-readable verification, server smoke, and deployment evidence is retained
under `evidence/corrections/R018/`.

## Deployment and rollback

The RPG save contains exactly:

| Mod | SHA-256 |
|---|---|
| `CanvasUI-0.1.0.jar` | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| `HYTALEDEVLIB-0.5.0.jar` | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| `HytaleRPG-0.0.11.jar` | `A7453A4F15AB57C3972F463E622310282E2DF71ED0170176334F74C3934751AA` |

The prior `HytaleRPG-0.0.10.jar` is retained under
`evidence/corrections/R018/rollback/`. Stop the RPG world before replacing the JAR
with that rollback artifact.

## Remaining connected proof

Static validation proves the document grammar, packaged paths, state math, native
API boundary, and server startup. Only the Hytale client can prove final visual
composition and texture resolution. Follow
`docs/corrections/R018-client-verification.md`, then retain the newest client/server
logs and `ui-trace.jsonl`.

```ini
R018 = DEPLOYED_AWAITING_CONNECTED_HUD_QA
Stage06Started = false
```
