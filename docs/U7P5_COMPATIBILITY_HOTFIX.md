# R177-U7P5 compatibility hotfix

This hotfix preserves **R176**, as explicitly requested. It does not restore R165,
rebalance skills, regenerate gear balance/durability, change rarity rules, migrate
save data, or deploy to another world. Plugin identity remains `InigmasGames:HyARPG`.

## Reproduced failure

The canonical RPG log `2026-10-01_11-01-19_server.log` reports server
`0.7.0-pre.5`, revision `70c9872b6f3a6aff04a9015c06a28ba13b7daeea`, and plugin R176.
Exactly **97** owned ability items fail decoding:

```text
Failed to decode 'Ability'
ACodecMapCodec$UnknownIdException: No codec registered with for 'Slot': Primary
```

The installed server's `ItemAbility.CODEC` reproduces that failure in a plain Java
process. No server, world, client, or authentication flow was started. The native
U7P5 discriminator registers `CoreItemAbility` as `Slot: Core` and
`SupportItemAbility` as `Slot: Support`; the old `Primary` variant is gone.

Exact observed diagnostics and the complete per-item change list are retained in:

- `evidence/hyarpg/U7P5-compatibility/observed-decode-diagnostics.txt`
- `evidence/hyarpg/U7P5-compatibility/offline-legacy-reproduction.txt`
- `evidence/hyarpg/U7P5-compatibility/native-ability-references.json`
- `evidence/hyarpg/U7P5-compatibility/ability-migration-manifest.json`

## Changes and preservation boundaries

| Boundary | U7P5 change | Hotfix |
| --- | --- | --- |
| 97 RPG ability carrier items | `ItemAbility` split into Core/Support subtypes; `Primary` removed | Use `Core`; remove obsolete diagnostic `Ability.Tags`. Preserve zero native cost/cooldown, `None` cost type, all item fields and the existing cast roots. The old marker is not converted into a gameplay support keyword. |
| Ability execution adapters | Cast/cost/cooldown getters now belong to `CoreItemAbility` | Check the subtype before accessing those fields. Keep existing RPG activation, charging and held-channel owners. |
| Native block access | World/WorldChunk block and column-access APIs removed | Use loaded section references, `BlockSection` and `SectionReader`. Preserve table rotation/filler handling and spawn collision checks; asynchronously preload the existing spawn search bounds. |
| Native ItemStack serialization | Quality now follows the Item asset unless explicitly overridden; explicit key is `QualityOverride` | Use the installed native codec. Test inherited and explicit quality, durability and metadata round trips. No save files are rewritten by the hotfix. |
| Managed carrier and action assets | Installed native references and schemas changed | Requalify all managed carriers, projectile configs/models, generated animation profiles and ability action roots against the installed U7P5 codecs. Retain authored RPG damage, action timing and durability values. |
| Managed proc metadata verification | Installed stock archive fingerprint changed | Requalify the existing 2,040 carriers / 4,181 damage nodes against U7P5, then pin its new hash. No proc coefficient or action template regeneration. |
| Language references | Observed missing owned translation keys | Correct file-scoped `server.lang` keys, retaining canonical R176 quality labels. Verify references against combined native and plugin language layers. |
| Native encounter reference audit | U7P5 retired three `Bear_Voidtaken_D*` roles and edited eight audited native files | Keep the historical RPG registry and authored reward/health/attack values intact. Record the native changes separately in the test audit; do not remap retired roles onto the new boss. |

The updated carrier migration is reproducible with
`tools/Update-U7P5AbilityAssets.py`. It rejects nonzero native costs/cooldowns or
unreviewed legacy tags instead of silently modifying skill semantics.

## Offline acceptance

**Passed:** `gradlew.bat check jar` on the installed U7P5 toolchain: 3,152 main,
392 native codec/control, 49 CanvasUI and 5 Tavern JUnit tests (3,598 total;
zero failures, errors or skipped tests). Retained Tavern feature checks also pass.
The final build log, test counts and package report are retained in
`evidence/hyarpg/U7P5-compatibility/`.

`gradlew.bat check jar` runs compilation, the main and isolated native JUnit
suites, CanvasUI/Tavern checks, CustomUI grammar validation, the spatial-grant
audit, the proc graph audit, and package ownership checks.

`NativeAffixU7P5AssetValidationTest` adds installed-codec coverage for:

- Legacy `Primary` failure and all **97** migrated complete ability items.
- All **2,040** managed carrier bodies and their durability/quality round trips.
- All **48** packaged projectile configs and **22** projectile models.
- All **215** generated item animation profiles, including native inheritance.
- All four existing ability activation/charging/channel roots.
- Optional inherited quality and explicit `QualityOverride` serialization.

`auditU7P5AssetCompatibility` checks every **4,556** packaged JSON asset against a
frozen semantic manifest from the installed R176 JAR. Only the reviewed 97-item
Core migration is allowed. It also verifies **40,440** common/named references,
translation keys, the U7P5 manifest version and unchanged plugin identity.

The deployment script reruns this package guard before replacing the R176 JAR.
The usual backup and staged/installed SHA-256 verification remain in place.

**Deployed:** R177-U7P5 to the existing `pre-release/Saves/RPG/mods/HyARPG.jar`.
Installed and built SHA-256 both equal
`363cd04bb61ea0a9302c8a21ef926f2b365e8e985cbca998ba49e9be405e9086`.
The original R176 JAR was backed up outside `Saves` at
`evidence/hyarpg/jar-deploy-20261001T161559360Z/HyARPG-before.jar`.
The deployment receipt is `evidence/hyarpg/U7P5-compatibility/deployment.json`.

Existing asset ID capitalization warnings are nonfatal. IDs are retained because
renaming saved carrier IDs would violate the save-preservation requirement.
Offline acceptance does not prove connected-client behavior; the user performs
that final check in the canonical RPG save.

## In-game QA

1. Join the existing **RPG** save on U7P5 and confirm the R177-U7P5 revision.
2. Press Tab; hover bag/equipment items and close with Escape. Equip, unequip,
   drop and pick up a normal item and a managed gear item.
3. Use the existing `/rpg gear affixqa spawn <fixtureId>` and `/rpg geartrace on`
   commands. Test weapon damage, Iron Sentinel inheritance and equipment stats.
4. Cast an existing instant skill and a held/charged skill; confirm normal RPG
   resource payment and cooldown behavior.

The official update reference is
[Hytale U7P5 patch notes](https://hytale.com/news/2026/9/pre-release-patch-notes-update-7#pre-release-u7p5).
The installed U7P5 server codecs and shipped assets are the exact compatibility
reference; native skill balance changes from the patch notes are not copied into
the RPG skill catalog.
