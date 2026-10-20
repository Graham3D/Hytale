# Catalog worker result — 2026-09-30

## Changed files

- `src/main/java/com/inigmasgames/hytalerpg/gear/GearAffixTiers.java`: corrected named, family, and all-skill mechanical rarity gates, including WA-121.
- `src/main/java/com/inigmasgames/hytalerpg/gear/GearDropGenerator.java`: five-band budgets, fixed/rank roll branch, authored armor extensions and armor factors, Q-tier scaled-grid feasibility before selection, MATCH selector from executable skill weapon kinds, runtime `ENABLED` gate without historical TEST/GATED filtering. Starred scopes remain unavailable until the matching native profile is verified.
- `src/main/java/com/inigmasgames/hytalerpg/gear/GearRarity.java`, `GearQualityProfile.java`, `GearMagicFind.java`, `src/main/resources/rpg/gear/quality-profile-v1.json`: new production budgets (Magic 1, Rare 2–3, Very Rare 4–5, Legendary 6 with 3P/3S), era/item-level gates, five rarity distribution entries and deterministic enum ordering. The previous three-band rare mass is split 77/15/8 on eligible Hell 60+ sources; Very Rare receives 15% of that mass at 35+, Legendary receives none outside Hell. Existing presentation colors and carrier assets were left intact.
- `src/main/java/com/inigmasgames/hytalerpg/gear/GearInstance.java`: public frozen `AffixRoll.selector()` with the eight-argument constructor retained, protected QA over-budget fixtures, `legacyIssues()` diagnostics with `gear-legacy-legality-v1`. No payload is rerolled or rewritten. Historical Magic 2 and Rare 4–6 budgets remain decodable.
- `src/main/java/com/inigmasgames/hytalerpg/gear/GearAffixQaSuite.java`, `src/main/resources/rpg/gear/affix-qa-fixtures-v1.json`, `affix-qa-coverage-v1.json`: preserved nine combined seed fixtures, corrected WA-121's Very Rare QA carrier, programmatic 160 A/B pairs and combined groups covering all 160 IDs. Pending fixtures are listed but cannot be issued. WA-141 has a pending daggers descriptor; its starred dual-wield legality is not claimed as verified.
- `src/main/java/com/inigmasgames/hytalerpg/commands/RpgGearCommand.java`, `docs/COMMANDS.md`: affixqa list includes ID/name/roll/selector, base, rarity and spawnability; spawn accepts armor, weapons, support, summons, all and remembers the next fixture after a saved issuance in the running process. Existing spatial grant coordinator remains the delivery owner.
- `src/test/java/com/inigmasgames/hytalerpg/gear/GearCatalogLegalityTest.java`, `GearAffixQaSuiteTest.java`, `GearItemizationReworkTest.java`: authored legality, saved-selector, A/B control, five-band generation assertions. These are catalog/generation tests, not positive gameplay consumer tests.

## Exact ID coverage and limit

- All WA-001–WA-158 and GA-159/GA-160 are enumerated in A/B descriptors and combined groups. Q-tier generation remains available to the current `GearAffixRuntime.ENABLED` set only. The historical definition `featureDisposition` no longer governs production selection.
- WA-121–WA-133 use the corrected skiller tier/rarity table. WA-139, WA-144, WA-148–WA-150 have a fixed-roll generation branch. WA-122 and WA-144 can freeze a real executable skill ID from a matching weapon profile; their gameplay adapters remain gated.
- Armor extensions for WA-004, 006, 007, 009, 012, 013, 023–028, 072–082, 085–093, 098–105, 110–114, 151–156 and GA-159/160 now use their authored slots/attribute restrictions and factors. This is authoring legality, not an assertion that every effect executes.
- Production effect completion is **not 160/160**. No extra ID was enabled here. Positive/control/negative tests against each native gameplay consumer are still required before `ENABLED` expands.

## Native asset audit and blocked mapping

Read-only inspection used installed `C:/Users/Zemio/AppData/Roaming/Hytale/install/pre-release/package/game/latest/Assets.zip` (the patchline selected by `gradle.properties`). Confirmed examples:

| Asset | SHA-256 | Verified profile |
| --- | --- | --- |
| `Server/Item/Items/Weapon/Shield/Weapon_Shield_Copper.json` | `3A7D60075A5B351E75E839FAD4B750DB7228073E108EEF2B0ED148AA4F715210` | Parent `Template_Weapon_Shield`; inherited `Shield` animation, `Root_Unarmed_Attack_Swing_Left` primary, `Root_Weapon_Shield_Secondary_Guard` secondary. |
| `Server/Item/Items/Weapon/Staff/Weapon_Staff_Mithril.json` | `EB8EFBDEEE108FE2D2B17FAC22E028F77BDF8E29FD541F7DF5EA9AB2ADC23107` | `Staff` animation, `Staff_Primary` on both actions. |
| `Server/Item/Items/Weapon/Wand/Weapon_Wand_Wood.json` | `B276067C55A63D73EEB9D150EDB24A81D83C6F6D1DD137749FE1118FA0DB511B` | `Wand` animation, `Wand_Primary` on both actions. |
| `Server/Item/Items/Weapon/Spellbook/Weapon_Spellbook_Fire.json` | `4A7B7F96DA792CBF3027DE47BBAAE206CB2BA1C00C7D3503D72E2FC18733D563` | `Spellbook` animation, `Spellbook_Primary` on both actions. |
| `Server/Item/Items/Weapon/Bomb/Weapon_Bomb.json` | `EBFC39966BAA6A2E5F21AE68A5FE283D575AD8F8FB0E88042F3942E986748598` | Stack size 10 with `Bomb_Throw`; this is ammunition, not the required durable affixed carrier. |

The checked-in shield binding has the matching source hash but `MISSING_ADAPTER`; it must stay unmapped until shieldDefense/guard/Smite ownership is live. Staff, wand, book and bomb base rows have no verified managed native carrier. Mapping them now would make natural loot select a carrier that `GearNativeItems.create()` cannot load, or would advertise an unconsumed intrinsic. These are concrete cross-owner boundaries, not unsupported Hytale APIs. No carrier assets or binding rows were falsely activated.

## Cross-owner wiring required

1. **Main: `GearNativeItems`/`HytaleGearEquipment`/`HyArpgPlugin`.** Once managed carriers are compiled from the exact installed native Parent/model/texture/action graph, map each base in `native-bindings-v1.json`, provide all five carrier assets returned by `GearBindings.Binding.carrier(rarity)`, and verify `GearNativeItems.create/read` round trips. `HyArpgPlugin` already binds equipment (`GearNativeItems.bind(gearEquipment)`) and registers `new RpgGearCommand(gearEquipment)`; no extra command registration is needed. `HytaleGearEquipment.resolveEffects` must actually consume focus `magicPower` and shield `shieldDefense` with genuine action/block semantics before these bases enter drops. Bomb needs a persistent durable action carrier separate from stackable native ammunition.
2. **Skill/UI owners.** `GearAffixDisplay.format(roll)` and its tooltip caller should resolve `roll.selector()` through the canonical SkillId for WA-122 and WA-144; old null selectors need explicit legacy display/diagnostics. The item-rank and temporary availability consumers must read this persisted selector from the valid-equipment snapshot. WA-144's allowed skill list needs the explicit supported policy before its gate opens.
3. **Native combat owners.** Verify C-star native attack, M-star block and WA-141 dual-wield profiles before extending `GearDropGenerator.eligible`; daggers are a QA candidate only. Add native effect consumers and deterministic positive/control/negative tests before extending `GearAffixRuntime.ENABLED`.
4. **Main legacy policy.** `GearInstance.legacyIssues()` identifies old over-budget items, WA-121 on Rare, and missing selectors without modifying their payload. The runtime owner must implement a visible, versioned remediation decision for those issued items; do not silently reroll, delete or grant an illegal rank.
5. **Main final gate/report.** Regenerate `affix-qa-coverage-v1.json`, the runtime audit and simulator matrix after adapters pass. Its existing 20 TEST / 140 GATED rows describe the historical capability baseline, not 160 runtime proofs. Connected owner QA remains separate.

## Verification

- Parsed the edited gear JSON files; `affixes-v1.json` has 160 unique IDs.
- `git diff --check` passed for edited tracked files.
- Inspected the installed pre-release asset archive and verified the five source-entry hashes above; the shield Parent was resolved read-only.
- Did **not** run Gradle, launch a server, or claim Java compilation/gameplay test passage, per worker instruction. Main must compile and run the updated tests. Generated QA combo count and issuance behavior require that pass.
