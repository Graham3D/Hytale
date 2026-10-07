# R216-U7P5 — QA feedback and monster target visibility

## Connected R215 finding

The 2026-10-07 19:38:03 RPG server log recorded a successful Hell Unique Trork birth with `ME-002`, `ME-019`, and `ME-025`, native Health registered at 4337.1/4337.1, and `PROJECTED_FALLBACK` with no native `Healthbar` in the final UI component list. The leader's native nameplate was intentionally blanked in R215 to avoid a second title; minions kept their small native names. The screenshot therefore reflects a missing projected stack, not a failed affix or color registration.

The projection used the positive Z axis as camera forward. The installed Hytale `TargetUtil.getLook` obtains the player's eye pose and head rotation; its `Transform.getDirection(pitch, yaw)` points along negative Z at zero rotation. The projected stack calculated negative depth for a monster directly in front of the player and returned no frame. That silently hid the name, bar, and affix text. R216 now uses `TargetUtil.getLook` and its direction for the same pose as the target ray. A focused test checks the native zero-rotation direction and a monster in front of it.

R215 also only refreshed a bar after damage if that viewer already had the target selected. R216 uses the existing post-ApplyDamage actual Health-loss callback to keep a player-hit target eligible for three seconds, reusing the same compact projected stack and native Health value. The ordinary look-at path remains. No separate Health ledger, damage rule, affix owner, or native UI asset was added.

The same log shows `Skeleton_Fighter unique hell` failed with `QA_NO_SUPPORTED_RANDOM_AFFIX_SET`. Its generic QA binding had only Survival and Leader affix groups, so the existing three-card legal-set selector could not choose three. The shared player-hit receipt owner is victim-side and applies to this QA role; R216 declares its existing `APPLIED_HIT_RECEIPTS` capability in the QA-only generic binding, allowing Reflective to supply a third compatible group where selected. The substantive Reflective and other affix implementations are unchanged, as is the production binding.

## Player feedback

`/rpg spawn` now reports the actual affix names from the published birth receipt, such as `Trork Warrior spawned with Extra Strong, Frenzied, and Armor Breaker.` An explicitly incompatible set names every incompatible affix: `Extra Fast and Mana Burn are incompatible with Golem Crystal Earth.` Routine rejection text no longer prints internal affix IDs, encounter UUIDs, or exception codes to the player. Those remain in server diagnostics. Permission, syntax, era counts, transient QA state, and zero rewards are unchanged.

## Verification and deployment

- Focused JUnit: `R208EnemyTargetHudTest`, `QaSpawnFeedbackTest`, `EnemyNativeBindingsTest` passed.
- Offline `verifyHyArpgJar` passed: 88 CustomUI documents validated, spatial grant audit passed, packaged manifest `InigmasGames:HyARPG@0.2.0-R216-U7P5`, no ImmersiveNPCs payload.
- Active deployment: `C:/Users/Zemio/AppData/Roaming/Hytale/data/pre-release/Saves/RPG/mods/HyARPG.jar`.
- Deployed SHA-256: `C8F822C72D0F6AF6107EEC3949A46023CC3B8E8184515CDB344CA1F75E986A6C`.
- Prior R215 backup outside Saves: `C:/Users/Zemio/.codex/backups/HyARPG/HyARPG-R215-U7P5-20261007-195755.jar`.

Connected-client appearance is pending owner QA. Restart the active RPG world. Test `/rpg spawn Trork_Warrior unique hell extrastrong frenzied armorbreaker`, look directly at the leader, hit it, and inspect the name, Health bar, and affix line. Test `/rpg spawn Skeleton_Fighter unique hell` for three automatically compatible affixes. Test one explicitly incompatible affix set to verify the plain-language rejection.
