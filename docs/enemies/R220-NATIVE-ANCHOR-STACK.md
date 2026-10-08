# R220-U7P5 — native promoted-monster text anchors

## Connected R219 correction

R219 admitted the affix text only inside the damage-triggered Healthbar window. R220 makes identity text persistent: a promoted actor now owns two nonserialized, zero-volume invisible native Nameplate carriers. The first carries its frozen authoritative generated name; the second carries its frozen own-affix labels. Both are visible to ordinary tracking viewers before combat. The actor's original promoted Nameplate is blanked only after both carriers exist; if a carrier fails, the actor retains its direct native name.

`MonsterPresentationLayout` uses `TransformComponent.position + BoundingBox.max.y` as visual top, hitbox X/Z midpoints as shared horizontal center, and one set of named base margin, affix gap, and name gap constants. The two anchors take the calculated `nameY` and `affixY` positions. The existing native entity tick updates their transforms only when the calculated owner-relative position changes. Death, encounter detach, QA clear, world unload, and plugin shutdown release the carriers. Normal monsters continue to use their direct native Nameplate.

The native Healthbar remains Hytale's `EntityStat=Health` UI component on the actual monster. Only positive actual player HP loss triggers its viewer-local reveal; further hits refresh the approximately three-second expiry. Text anchors have no dependency on that window. The packaged server `Healthbar.json` retains Health binding and uses `HitboxOffset.Y=-48` to lift the native bar. No CustomUI projection is active.

## Installed SDK limits

`NATIVE_NAMEPLATE_RARITY_STYLE_UNAVAILABLE`: the installed `Nameplate`/`NameplateUpdate` contract exposes only plain text. No native color/bold token or field was found in the narrow protocol inspection, so names remain unstyled.

`EntityStatUIComponent.HitboxOffset` is an asset-level offset from the hitbox center. The native `UIComponentsUpdate` packet contains component IDs only. A separate asset per hitbox height would enter the default `UIComponentList` for unrelated entities because the installed `UIComponentList.update()` includes every registered entity UI asset when a list is unspecified. R220 therefore does not create per-actor Healthbar variants or alter collision hitboxes. The layout resolver calculates the desired world-space offset from each actual hitbox for diagnostics, while the single native Healthbar asset retains one shared client-pixel offset. Connected QA must establish whether the resulting bar clears both Trork and Skeleton models; the server API cannot guarantee exact per-actor placement on this SDK.

## Connected QA

Restart the active RPG world. Test `/rpg spawn Trork_Warrior unique hell` and `/rpg spawn Skeleton_Archer champion normal`. Before damage, inspect native name and affixes; after positive HP damage, inspect the native bar beneath them and its roughly three-second refresh/expiry. Check point-blank and ranged views, lateral movement, camera rotation, horizontal centering, and cleanup on `/rpg spawn clear`. R220 package verification is offline; owner-connected observation remains the visual acceptance gate.

## Verification and deployment

Revision: **R220-U7P5**. Focused `MonsterPresentationLayoutTest`, `MonsterAffixLabelTest`, `EnemyNativeHealthbarQaTest`, and `EnemyNameplateTextTest` passed. Package and deployment evidence follows below.

- `compileJava` and `verifyHyArpgJar` passed. Offline package: 18,774 entries, 2,077 classes, manifest `InigmasGames:HyARPG@0.2.0-R220-U7P5`, 88 validated CustomUI documents, and no ImmersiveNPCs payload.
- Active deployment: `C:/Users/Zemio/AppData/Roaming/Hytale/data/pre-release/Saves/RPG/mods/HyARPG.jar`.
- Built and deployed SHA-256: `14700F6643CA3C4D145A820532D544F7A0B0D79EE4D8742B88CF8EF06E5CF89E`.
- Prior R219 recovery copy outside Saves: `C:/Users/Zemio/.codex/backups/HyARPG/HyARPG-R219-U7P5-20261007-220824.jar`, SHA-256 `999793FFBC743AC7E8F424CF158D647A6FAB91BD22E3120120C8E14EBE494291`.
- No standalone Hytale server or game client was launched. Native movement, perspective, and spacing remain owner-connected QA observations.
