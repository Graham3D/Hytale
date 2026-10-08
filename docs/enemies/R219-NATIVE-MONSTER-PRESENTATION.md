# R219-U7P5 — native monster presentation QA

## Presentation ownership

- The monster keeps Hytale's own tracked `Nameplate` for its resolved display name. Promoted names do not include rarity, QA provenance, level, or a UUID. Native `NameplateUpdate` has only a text field; the installed client protocol exposes no supported rarity color or bold property. R219 leaves that text unstyled rather than using markup of unknown semantics.
- R218's permanent shared-list QA Healthbar attachment is disabled. R217's post-ApplyDamage positive-Health-loss receipt admits the native Healthbar only for the damaging player, refreshes a roughly three-second window, and removes it on expiry/death/despawn. The connected client must have **Show Entity Health Bars** enabled. The R218 shared-list method remains unused diagnostic code.
- The asset-pack override `Server/Entity/UI/Healthbar.json` keeps native `EntityStat=Health` and changes only `HitboxOffset.Y` from `-30` to `-36`. This is a supported server asset in the base `Assets.zip`; it does not replace the client `HealthBar.ui` or Health data. This offset affects native Healthbar layout wherever that shared asset is used.
- Promoted actors get one nonserialized `Invisible_Projectile` carrier with zero bounding volume and Hytale's native `Nameplate` component. The actor's frozen `EnemyDisplayDto.ownAffixTags` supplies the text. The canonical carrier Nameplate stays empty. Only a damaging viewer receives its affix text through `NameplateUpdate`; expiry sends an empty update. The same hit/expiry window owns both bar and affix admission.
- `MonsterPresentationLayout` uses the actor's hitbox top and horizontal center for row positions. Its base margin, affix gap, and name gap are named in one place. `AnchorFollow` updates the carrier transform on the native entity tick, not a screen-space camera projection or the old 4 Hz HUD poll. Carrier removal follows death, encounter detach, entity removal, QA clear, and world unload. The client owns visual interpolation; exact apparent spacing and smoothness require connected QA.
- Display-only aliases are applied after the frozen snapshot. No affix ID, planner, mechanic, save record, or compatibility rule changed.

## Connected acceptance and limits

Restart the active RPG world and run `/rpg spawn Trork_Warrior unique hell`. Before hitting the leader, check that its name appears and the bar/affix row do not. After causing positive HP loss, check the order `Name → Affixes → Healthbar → Monster`, horizontal centering, native movement, and the approximately three-second timeout. Hit again to check refresh; clear or kill the actor to check cleanup. The installed native nameplate and EntityStat renderers have separate client placement rules, so compilation cannot establish final pixel alignment. In particular, rarity color and bold styling are unavailable through the installed native Nameplate packet.

## Offline verification and deployment

Revision: **R219-U7P5**. Focused tests: `MonsterPresentationLayoutTest`, `MonsterAffixLabelTest`, `EnemyNativeHealthbarQaTest`, and `EnemyNameplateTextTest`.

- Focused `:test` passed for the four classes above. `compileJava` and `verifyHyArpgJar` passed. The package contains 18,774 entries, 2,077 classes, 88 validated CustomUI documents, no ImmersiveNPCs payload, and manifest `InigmasGames:HyARPG@0.2.0-R219-U7P5`.
- Built and active RPG-save JAR: `C:/Users/Zemio/AppData/Roaming/Hytale/data/pre-release/Saves/RPG/mods/HyARPG.jar`, SHA-256 `999793FFBC743AC7E8F424CF158D647A6FAB91BD22E3120120C8E14EBE494291`.
- The former R218 JAR was copied outside Saves to `C:/Users/Zemio/.codex/backups/HyARPG/HyARPG-R218-U7P5-20261007-215021.jar`, SHA-256 `8BBCCA3E859E3BB69F08DEDDDC4FE696AA4159A28EE9FB7B48F4668956372081`.
- No native/local server was launched. Deployment is ready for owner-connected visual QA and is not itself connected acceptance.
