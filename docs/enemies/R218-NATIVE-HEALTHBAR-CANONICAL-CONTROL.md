# R218-U7P5 — canonical Healthbar QA control

## Connected R217 evidence

The Hell Unique Trork had a native Nameplate, valid native Health, and `gameplayNativeBars=true`. Actual positive HP loss repeatedly queued and requeued viewer-specific `UIComponentsUpdate` packets, yet the client showed no native Healthbar. The final actor `UIComponentList` lacked Healthbar (`listContainsNativeBar=false`). R217 therefore did not prove that a viewer-only packet can establish renderable EntityUI state when the actor's canonical list excludes that component.

## Bounded R218 change

- During existing QA birth publication, a QA-origin Champion, Unique, or Super Unique actor now receives native `Healthbar` in its actual `UIComponentList`, before staged release. Normal minions and all production actors retain the R217 behavior. Other UI component IDs are preserved.
- The QA control leaves the shared Healthbar present until actor death/despawn/clear. Its damage path records native Health and exits before the viewer-local admission packet, follow-up, or three-second expiry. No projected CustomUI path is enabled.
- The existing final QA log now includes all canonical component IDs, `listContainsNativeBar`, native Healthbar's EntityStat index, and current/max Health. A read-only system observes the native tracker queue after `UIComponentSystems.Update` and before `EntityTrackerSystems.SendPackets`. `RPG_ENEMY_QA_CANONICAL_UI_UPDATE nativeQueueObserved=true` means the queued `UIComponentsUpdate` matched the actor's canonical IDs for a viewer. The audit cannot prove transport delivery or client rendering. If no matching update is observed within two seconds, it logs `false` with the number of tracking viewers.
- No affix implementation, rarity, era, flock, combat, Nameplate, reward, or production encounter owner was changed. This is a connected comparison control; it does not select a permanent healthbar policy.

## Verification and deployment

- Focused JUnit: `EnemyNativeHealthbarQaTest` and `R208EnemyTargetHudTest` passed.
- Offline `verifyHyArpgJar` passed, including the CustomUI and spatial-grant checks. Manifest: `InigmasGames:HyARPG@0.2.0-R218-U7P5`.
- Active deployment: `C:/Users/Zemio/AppData/Roaming/Hytale/data/pre-release/Saves/RPG/mods/HyARPG.jar`.
- Deployed SHA-256: `8BBCCA3E859E3BB69F08DEDDDC4FE696AA4159A28EE9FB7B48F4668956372081`.
- Prior R217 backup outside Saves: `C:/Users/Zemio/.codex/backups/HyARPG/HyARPG-R217-U7P5-20261007-205205.jar`, SHA-256 `13AC5421C35D4A87CD71D4DE8B6A9EE5EE425C6A7D066899B5EE202CBCEB399F`.

Restart the RPG world; run `/rpg spawn Trork_Warrior unique hell`; hit the leader once. If the native bar renders, canonical ownership works and R217's viewer-specific injection did not. If it does not render, the failure lies below viewer-local admission. Stop after recording the visual result and relevant final/queue logs; do not infer a client result from the offline build.
