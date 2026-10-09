# R199 raw attribute presentation consistency

## Change

Inventory and the standalone Character page now display Strength, Dexterity, Intelligence, Wisdom and Luck through `CharacterSheetViewModel.attributeText`, which reads the integer `DerivedStats.rawAttributes` map. Removed raw/effective dual wording from the normal Character layout. `/rpg stats` retains its existing raw/effective diagnostics; `docs/COMMANDS.md` now explicitly distinguishes that debug view.

This is a presentation correction. No diminishing-return curve, effective attribute, combat formula, allocation, gear requirement, affix or save data changes.

## Three-path audit

| Path | Existing owner and raw definition | Result |
| --- | --- | --- |
| Inventory/Character attribute rows | `RpgUiProjectionService.derive` combines saved baseline attributes with admitted permanent gear attribute bonuses through `GearAffixRuntime.Effects.raw`. `DerivedStats.rawAttributes` retains that pre-diminishing-return map. Both panels now read it via `CharacterSheetViewModel.attributeText`. | Displays integer raw values. |
| Requirement text/color | `GearNativeItems.tooltipView` uses `HytaleGearEquipment.requirementAttributes`. This resolves saved raw baseline plus eligible other equipment bonuses with `GearRequirements.resolve`, excluding the candidate's own bonus. `GearTooltip.describe` calls `Gate.missingAttributes` and selects normal/error presentation. | Already raw; preserved. |
| Equip validation | `HytaleGearLoot.requirementFeedback` resolves the raw baseline and surviving valid gear after displacement, then calls `Gate.playerFeedback`. `GearEquipmentResolution` and `HytaleGearEquipment.canUse` enforce the same raw gates for admitted equipment/use. | Already raw; preserved. |

Existing candidate/self-bonus, displaced-equipment, broken-item and circular-support rules remain intact. The panel shows the current admitted raw total; a candidate must still meet requirements without relying on its own bonus or a displaced item's bonus.

Combat remains owned by `EffectiveAttributeService` and `DerivedStatService`: the raw-to-effective transformation is unchanged, and existing damage/resource/other consumers still receive diminished effective attributes.

## Regression acceptance

`RawAttributePresentationTest` covers all five attributes at raw 185, verifies panel text `185`, effective value 176.25, tooltip color and native equipment admission at requirements 180/185/186, unchanged primary combat scaling and health, and permanent gear bonuses before diminishing returns. Met requirements retain the existing neutral white/gray style; unmet requirements remain red.

## Validation / deployment

Passed one final `:test :nativeControlTest :jar --offline` run: 3,185 unit tests + 404 native-control tests, zero failures/errors/skips. Offline package validation and U7P5 asset validation passed (40,440 references; zero failures). Logs: `build/r199-final-checks.log`, `build/r199-package-check.log`, `build/r199-u7p5-check.log`. No client/server/authentication flow launched. No saved state edits.

## User-run QA

1. With raw STR 185 (`/rpg stats` or the existing `/rpg dev attribute str 185` QA command), open Inventory: Strength must display 185. `/rpg stats` must still report effective 176.25 in the same gear state.
2. Hover/equip an item requiring 180 Strength: met coloring and equip allowed, provided other requirements are met. An item requiring 186 must show red and reject equip.
3. Check Dexterity, Intelligence, Wisdom and Luck likewise display raw integer totals; combat derived values should remain unchanged.

Deployed `0.2.0-R199-U7P5` to `Saves/RPG/mods/HyARPG.jar`. Previous R198 backed up at `evidence/hyarpg/jar-deploy-20261002T182803232Z/HyARPG-before.jar`. Installed SHA256 verified: `8601A97585D848BEDCE9D9EB7DF928AA3E1325763AE8EE855E890E7E3DA71B22`. Connected acceptance remains user QA.
