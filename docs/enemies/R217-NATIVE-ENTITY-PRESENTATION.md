# R217-U7P5 — native monster presentation comparison

## Installed Hytale path

- The ordinary smooth NPC title is the tracked `Nameplate` component. `NameplateSystems.EntityTrackerUpdate` sends `NameplateUpdate` per visible actor; its packet contains text only. R217 uses this path for managed Champion, Unique, and Super Unique names again instead of blanking the promoted native title.
- `UIComponentList` is tracked separately. The installed `Server/Entity/UI/Healthbar.json` is `EntityStat` bound to native `Health`, with `HitboxOffset` Y = -30. The client has `Client/Data/Game/Interface/InGame/EntityUI/HealthBar.ui` (164 × 12 with a native progress fill). `EntityStatsSystems.EntityTrackerUpdate` replicates native Health to tracking viewers. The active RPG worlds use the `Default` GameplayConfig; R216 connected logs reported `gameplayNativeBars=true`.
- `UIComponentSystems.Update` sends the global component list to newly tracking viewers and on list changes. `EntityViewer.queueUpdate(actor, UIComponentsUpdate)` permits a distinct list for one viewer. R217 keeps Healthbar off the shared actor list, then admits the native Healthbar for the player who caused measured positive Health loss in the existing post-ApplyDamage Inspect path. Admission refreshes on another hit and expires after approximately three seconds; the native tracker and existing death/despawn cleanup remove it earlier. The existing one-time follow-up packet protects against a same-tick shared-list update superseding admission.
- `HiddenUIComponents` on native roles filters the role's shared UI list. R217 retains every other component and adds Healthbar only in the admitted viewer packet. It does not modify role assets globally.

## Live/diagnostic boundary

The live build does not register the R216 target-damage callback or enemy-display observer into the CustomUI target stack, and it does not poll that stack. The projected implementation and its UI asset remain as inactive diagnostic code. Native Nameplate and Healthbar positioning, camera motion, and perspective are client-owned. This changes presentation only; the 27 affix owners, combat calculations, promotion, QA encounter backend, and rewards are untouched.

The installed `EntityUIType` enum has only `EntityStat` and transient `CombatText`. `UIComponentsUpdate` carries component IDs only. There is no persistent arbitrary text component for an affix row. `NameplateUpdate` has no color, font weight, vertical offset, or relative-order fields, and the installed client assets have no editable Nameplate UI document; that renderer is internal to the client. Consequently R217 cannot deliver the specified Champion `#1d4dff`, Unique `#a000ff`, or Super Unique `#ff9100` through a supported native nameplate property, nor place a native affix row below Healthbar. No undocumented markup, simulated nameplate, or new projection was added. Affixes remain inspectable through the existing command/QA birth feedback while this native comparison runs. Client-side appearance and native bar visibility remain connected-QA questions; compilation and packets alone do not prove rendering.

## Verification and deployment

- Focused JUnit: `EnemyNativeHealthbarQaTest`, `EnemyNameplateTextTest`, `R208EnemyTargetHudTest` passed.
- Offline `verifyHyArpgJar` passed, including 88 CustomUI documents, spatial-grant audit, and manifest `InigmasGames:HyARPG@0.2.0-R217-U7P5`; no ImmersiveNPCs payload.
- Built JAR SHA-256: `13AC5421C35D4A87CD71D4DE8B6A9EE5EE425C6A7D066899B5EE202CBCEB399F`.
- Deployed JAR: `C:/Users/Zemio/AppData/Roaming/Hytale/data/pre-release/Saves/RPG/mods/HyARPG.jar`, verified at the same SHA-256.
- Prior R216 recovery JAR outside Saves: `C:/Users/Zemio/.codex/backups/HyARPG/HyARPG-R216-U7P5-20261007-203005.jar`, SHA-256 `C8F822C72D0F6AF6107EEC3949A46023CC3B8E8184515CDB344CA1F75E986A6C`.
- Connected test: restart the active RPG world, spawn a Unique Trork, observe its native name before hitting it, deal real HP damage, and compare native bar visibility/motion near and far for about three seconds. Check that a second hit refreshes the bar and that other players do not receive it. Record whether the client draws the native bar; the server path cannot establish that visually.
