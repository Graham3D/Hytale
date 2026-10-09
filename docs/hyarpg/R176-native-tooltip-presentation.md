# R176 native managed-gear tooltip

This pass changes presentation and adds three deterministic QA fixtures. Combat formulas, affix rolls, requirements, skills, inventory transfers, footprints, and saved ownership remain authoritative and unchanged. APS is omitted at the user's direction until sustained native combo/charged cadence is verified; a primary cooldown reciprocal would not be an authoritative APS value.

## Installed native capability audit

Inspected Hytale 0.7.0-pre.4's installed `Client/Data/Game/Interface/InGame/Tooltips/ItemTooltip.ui`, the shipped `ItemQuality` asset codec/protocol, `ItemDisplayMetadata`, `Message`, and existing gear owners. No client executable, client UI override, or second floating tooltip is used.

| Feature | Supported path / exact boundary |
| --- | --- |
| Background and pointer | Dedicated `ItemQuality.ItemTooltipTexture` and `ItemTooltipArrowTexture`; shared native 24 logical-pixel nine-slice border. Frame PNG is 107×107 at 2x; pointer is 66×48 at 2x, matching shipped assets. Dark navy interior, restrained corner geometry, thin rarity accent and inner edge; no glow or item art. |
| Title and upper-right rarity | Native `#Name` is bold 18px and wraps. `#Quality` is 14px, End aligned. `TextColor`, `LocalizationKey`, and `VisibleQualityLabel` supply identity colors and labels. Name metadata has no explicit color override. |
| Body emphasis | Native description is one wrapping 14px Label. `Message` supports per-span color, bold, italic, and newlines. Damage heading/range are bold; affixes blue; only unmet requirements red. |
| Font families and sizes | No per-item font-family or font-size fields in ItemQuality or ItemDisplayMetadata/Message. Damage cannot become an independently sized display-font control. Flavor uses supported italic styling at the native body size. |
| Margins / alignment | Native tooltip owns 24px content padding (21px top), 320–480px width, wrapping, and pointer placement. No supported per-item padding/alignment override was found. |
| Dynamic dividers | Native description/stats/footer boundaries have client-owned 1px `#25262c` separators. Metadata cannot insert separate divider/diamond widgets between arbitrary damage, affix, requirement, and flavor sections. Those body sections use blank-line separation; no fixed-position dividers are baked into the dynamic frame. |
| Durability | Existing native current/max durability and right-aligned footer remain. Its warm color, numeric format, footer position, and separate separator are client-owned; no duplicate durability description is added. |
| Internal IDs | Generated description contains no raw item IDs. Native `#Id` is client-owned; no per-item ID-visibility field was found, so a client-enabled debug ID may still be visible. |
| Type | Native generic `#Type` remains client-owned. Concise catalog-family classification is supplied as the first description line. |
| Variable height | Native wrapping Label sizes to actual text. Optional sections contribute no unused block; only the first affix starts a separated section. |

## Presentation authority

- `GearCombatEffects.physical` supplies the same local resolved range used by combat, including WA-001/002/157/158 once. There is no tooltip damage calculator.
- `GearAffixDisplay` uses each canonical template, persisted value, and persisted skill selector. Skill-specific lines resolve canonical skill names; historical missing selectors still show the existing unavailable state.
- `GearRequirements` supplies failed level/attribute results. Inventory presentation uses the existing equipment owner's candidate-excluding requirement attributes, preserving the no-self-bonus rule.
- `GearNativeItems.present` writes only native display metadata/quality. Inventory bag and gear views receive presentation copies; transfer fingerprints stay the original committed stacks. Frozen rolls, identity, custody payload, quantity, and durability are preserved.
- `tooltip-families-v1.json` contains authored concise names for all 149 current GearCatalog families, validated against the catalog. No player strings are inferred from raw item IDs at runtime.
- Optional existing native base description translation is retained in a separated muted italic span. No flavor is generated. Bases without an authored description omit it.

## Dedicated quality assets

Existing historical asset IDs remain intact: `RPG_Gear_Common` displays Common/white; `RPG_Gear_Rare` displays Magic/#1d4dff; `RPG_Gear_RandomRare` displays Rare/yellow. Legendary, legacy Uncommon, and Epic retain their configured orange/green/purple colors. All six use identical frame geometry with color variants generated from their own TextColor using `tools/Build-GearTooltipFrames.cjs`. Inventory rarity-footprint PNGs, grid textures, and item art are untouched.

## Connected QA

Requires `inigmasgames.rpg.gear.author`, the current RPG save, and R176+. These use the existing protected fixture delivery/custody path and write QA items to inventory. They do not change production generation. Repeat issuance of a fixture is subject to the existing deterministic identity/custody rules; use each fixture once for the comparison.

```text
/rpg gear affixqa spawn tooltip-common-battleaxe
/rpg gear affixqa spawn tooltip-magic-battleaxe
/rpg gear affixqa spawn tooltip-rare-battleaxe
```

All use the authored Normal-era Adamantite Battleaxe, item level 40, fixed 95% intrinsic roll. Common has no affix; Magic has WA-001; Rare has WA-002, WA-018, WA-008. These are legal current rarity budgets. Current authored requirements/durability and combat values prevail over illustrative numbers in the brief.

1. Hover all three in the spatial bag; compare native title/quality colors, matching frame/pointer, damage emphasis, blue affix lines, and wrapping.
2. Check unmet Strength versus met Level colors against the character's current attributes. Equip only a valid item and compare the gear-slot tooltip; unequip/reopen and confirm rolls/durability are unchanged.
3. Hover an existing skill-rank and elemental fixture; check the skill name/canonical line occurs once. Check worn gear's native durability footer.
4. Move off the item and press Escape while hovering; verify native dismissal and no disconnect. This pass does not replace native hover behavior with a custom HUD.

Offline tests cover canonical damage/affixes/requirements, rarity labels/colors, APS omission, dynamic optional content, frozen-roll stability, native quality decoding, ItemDisplay metadata, durability, CustomUI metadata object shape (R175 regression), and shared texture geometry. Compilation/offline package validation cannot prove connected rendering or hover lifecycle; those remain user QA.

## Build and deployment result

`gradlew check jar` passed in 3m17s (`build/r176-check.log`): 3,152 primary tests, 386 isolated native-codec tests, 49 CanvasUI tests, and 5 Tavern JUnit tests, with no failures/errors/skips; retained Tavern feature checks also passed. CustomUI validation covered 81 documents. Offline package validation includes all twelve new frame/pointer PNGs and the family vocabulary resource.

Deployed `0.2.0-R176` to the sole active `RPG/mods/HyARPG.jar`, with the prior R175 JAR backed up outside Saves. SHA-256: `0E0694365A08B4CF1F678C9E9809F0B66265F9A11ADF791E345A3B1AB64C4EEA`. Backup/receipt: `evidence/hyarpg/jar-deploy-20261001T141321885Z/`. No save migration, game launch, authentication, or standalone server occurred. Connected acceptance remains pending.
