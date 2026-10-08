# R232-U7P5 — native Elite attack adapter repair

Date: 2026-10-08. Deployed to the active RPG save as `mods/Hywind.jar`.

## Connected log findings

The active save log `2026-10-08_16-25-34_server.log` (R230 connected session) records nine `Simulation and server tick are not in sync (operation position)` failures, including `Spider_Bite_Damage`, `Bear_Grizzly_Swipe_Right_Damage`, and a converted Skeleton strike. It also records two `ENEMY_PROJECTILE_LAUNCH_DIRECTION_MISSING` failures for `Skeleton_Scout` launches. Hytale removed the affected interaction chains after those exceptions.

## Repair

- The installed SDK's `LaunchProjectileInteraction.firstRun` calls native `ProjectileComponent.shoot()` before the receipt hook. `shoot()` writes the actual launch vector to `ProjectileComponent.getSimplePhysicsProvider().getVelocity()`. The holder's separate `Velocity` component is still at its default at that point. The receipt adapter now reads the original projectile physics vector. For a perfectly vertical native shot, it uses the same holder's authored launch yaw to provide the receipt's required horizontal direction; it does not change projectile flight.
- The converted enemy melee leaf had an empty `simulateTick0` override. Native `DamageEntityInteraction.simulateTick0` delegates to the normal native leaf and maintains the simulation operation path. R232 restores that native method. The Master Enemies scope provider returns no accepted-hit scope during the simulation-only invocation; the authoritative server invocation still requires its accepted-root snapshot and follows the existing damage/receipt owner.
- Native attacks, animations, collision, damage delivery, projectiles, all 27 affixes, Elite eligibility, spawn architecture, and rewards remain intact. The pinned native server patch is unchanged.

## Offline verification and owner QA

Focused projectile and converted-damage adapter tests passed. Full offline `gradlew check` passed: **3,413 tests in 342 suites**, zero failures/errors, plus package/asset validation. No standalone native server was started. Connected attack behavior remains for owner verification.

Restart the RPG world, then allow a certified projectile Elite (for example `Skeleton_Scout`) and converted melee Elites (Bear, Spider, Wolf) to attack. Confirm their native projectile/melee attacks execute, actors remain present, and the new server log has neither of the two exception strings above.

- Final active JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\Hywind.jar`
- R232 SHA-256: `5F97EDE07B3B58C42A83008E1459F264ABE5AAF5A9BCF06436C995A8609FA48B`
- Backed-up prior R231 JAR: `C:\Users\Zemio\.codex\backups\Hytale\Hywind-R231-U7P5-EC57A87B-20261008.jar` (SHA-256 `EC57A87B72AFBF881C5AEC5A3D0964E31D708FB31CB38DF748CBAB57B426AB35`)
