# Native Healthbar feasibility proof (R209 unchanged)

Date: 2026-10-07. Installed SDK: Hytale 0.7.0-pre.5.1. The active `HyARPG.jar` remains R209-U7P5; no production source, active save, command, or deployment was changed.

## Observed native contract

- `Assets.zip!/Server/Entity/UI/Healthbar.json`: `Type=EntityStat`, `EntityStat=Health`, `HitboxOffset={X:0,Y:-30}`.
- `EntityStatUIComponent.generatePacket()` emits `EntityUIType.EntityStat` and the resolved `Health` stat index. The entity UI protocol has no custom texture, label, or visibility-duration property.
- `UIComponentList` defaults to all registered components when `Components` is omitted. A list constructed with explicit IDs resolves them to asset indexes and marks itself network-outdated. `UIComponentSystems.Setup` adds the default list to living entities; `UIComponentSystems.Update` sends `UIComponentsUpdate` to new/current tracking viewers when the list changes. `UIComponentSystems.Remove` queues a native `UIComponents` component removal when the list component is removed.
- `EntityStatsSystems.EntityTrackerUpdate` sends initial `EntityStatsUpdate` and subsequent changed stats to tracking viewers. No Hywind Health copy is required for the native bar.
- The installed stock `Trork_Warrior` role and its `Template_Trork_Melee` do not declare `HiddenUIComponents`. Update 7 Part 4 says omitting the key keeps components visible; `Larva_Void` is an example that explicitly hides `Healthbar`.
- R209 `EnemyHealthBarPresentation.hideByDefault()` deliberately strips `Healthbar` from managed actors' shared lists, and its configured damage callback returns to the projected target UI before the earlier viewer-local native reveal route. A native Trork bar therefore needs a narrowly scoped opt-in after QA birth. The existing R209 projected presentation remains untouched.
- Installed client resource `Client/Data/Game/Interface/InGame/EntityUI/HealthBar.ui` defines a 164×12 container and 158×8 fill using `HealthBarContainer.png` and `HealthBarFill.png`. The server asset/protocol exposes no path to substitute Hywind's three custom PNGs for one actor. `HitboxOffset`, role `HiddenUIComponents`, entity `UIComponentList`, per-viewer `UIComponentsUpdate`, and global `GameplayConfig.Combat.DisplayHealthBars` are the evidenced controls.

## Isolated implementation prototype

`tools/probes/NativeTrorkHealthbarProbe.java` compiles against the installed server JAR and current R209 classes. It is deliberately outside `src/main` and is neither registered nor packaged. Its `attach` method requires the world writer thread, a real `Trork_Warrior` NPC with a published Master Enemies `Origin.QA` descriptor, native `EntityStatMap.Health`, and the registered `Healthbar` asset. It appends only `Healthbar` to that actor's `UIComponentList`, preserving other IDs. `restore` reinstates the exact prior list only if no other owner changed it. The prototype does not touch Health, damage, attacks, projected UI, or other actors.

Offline compile command: `javac --release 25 -cp "build/classes/java/main;<installed HytaleServer.jar>" -d build/native-healthbar-probe tools/probes/NativeTrorkHealthbarProbe.java` — passed. This proves the API types and additive list mutation compile, not that the client rendered anything.

## Visibility questions and decision

| State | Server/native evidence | Connected result |
|---|---|---|
| Full Health | With `Healthbar` attached, the UI ID and initial Health stat are sent to tracking viewers. Neither server asset nor `HealthBar.ui` specifies a full-Health hide rule. | Unknown: client rendering gate is not exposed in inspected sources. |
| After damage | `EntityStatMap` sends changed Health to tracking viewers; the native bar is bound to that stat. `DamageSystems.EntityUIEvents` handles combat text, not UI-list admission. | Actual appearance and animation need connected observation. |
| After combat ends | No inspected server UI-list system removes `Healthbar` on combat exit. The list stays attached until changed, despawn, or viewer loss. | Unknown whether client hides it on an internal timer or at full Health. |

The native component is structurally capable of replacing R209's **bar and Health update path**, with native entity tracking and no world-to-screen CustomUI projection. Native movement, camera-facing behavior, perspective scale, and the three visibility states still need connected observation on a one-actor QA probe. Preserving R209's target/look-at or recent-damage rule would require a viewer-local `UIComponentsUpdate` admission window or equivalent; the raw shared `UIComponentList` prototype alone would expose the bar to every viewer tracking that Trork. Exact pixel art replacement is unsupported by the evidenced server API.

No in-game claim is made. Per repository QA rules, no standalone server or client was launched. A connected proof would require a small opt-in QA build that calls this probe on one QA-spawned Trork, then tests full Health, damage, combat exit, movement, viewing angle, near/far perspective, and restoration. Do not remove R209 on the basis of this offline evidence alone.

Official references: [Update 7 pre-release notes, Part 4](https://hytale.com/news/2026/9/pre-release-patch-notes-update-7); [pre-release GameplayConfigs reference](https://pre-release.docs.hytale.com/assets/gameplayconfigs/).
