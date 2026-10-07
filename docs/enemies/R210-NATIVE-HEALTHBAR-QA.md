# R210-U7P5 — QA native monster healthbar

Deployed `HyARPG.jar` to the active RPG save on 2026-10-07. SHA-256: `7870C18E91F20C9B3C458FA1073A2B4D4B4CEB209F8836E89795FEA4DFA12DB5`. The previous R209-U7P5 JAR is backed up outside Saves at `C:\Users\Zemio\.codex\backups\HyARPG\HyARPG-before-R210-U7P5-821CC5B67119.jar`.

For published `/rpg spawn` actors with `Origin.QA`, the existing enemy UI owner adds Hytale's `Healthbar` EntityStat component to `UIComponentList`. It checks that the asset targets the native Health stat, retains all other component IDs, and suppresses the R209 projected target card for those QA actors. Native QA nameplates retain enemy identity. Natural actors keep R209 presentation. The QA component is removed on death and encounter detach; native entity removal handles despawn and `/rpg spawn clear`. Clearing removes loaded QA actors only through the existing native removal path.

Offline verification: `compileJava`, `EnemyNativeHealthbarQaTest`, `R208EnemyTargetHudTest`, `RpgSpawnCommandSyntaxTest`, CustomUI validation, and `verifyHyArpgJar` passed. No local Hytale server was started. Native visibility, follow motion, perspective, and Health updates await owner connected QA.

Owner step: restart the world, then run `/rpg spawn Trork_Warrior unique extrastrong frenzied armorbreaker`. Observe the native bar at full and damaged Health, while moving and rotating the camera, and after combat. Use `/rpg spawn clear` when finished.
