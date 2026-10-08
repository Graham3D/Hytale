# R228-U7P5 — Production elite role coverage

R228 extends the R227 production binding set using the existing `EnemyNativeBindings`, `EnemyAffixSelection`, and native melee receipt owner. It does not change the 27 affix implementations, rarity roll, reward calculation, presentation, promotion pipeline, QA encounter architecture, or campaign exclusions.

## Coverage

The installed `Assets.zip` has 156 distinct world-spawn `NPCs[].Id` values. Of those, 82 are in the 275-ID authored monster catalog; 66 resolve to a statically Hostile player attitude. R227 certified two natural IDs (`Larva_Void`, `Skeleton_Scout`). R228 certifies 21 natural IDs: the two existing roles, `Skeleton_Scout_Wander`, and 18 additional ordinary hostiles. `Skeleton_Scout_Patrol` also inherits Scout certification but has no current native world-spawn entry. There are 61 unbound catalogued natural IDs, including 45 with statically Hostile attitude. The [coverage report](R228_PRODUCTION_ELITE_COVERAGE.md) gives an individual installed-asset reason for each denial.

The Scout variants reference `Skeleton_Scout` and change only wandering/patrol/separation properties. They reuse the exact Scout action, status, immunity, and capability binding while retaining their own role ID and authored profile. Three native melee archetypes group the 18 new roles by installed parent, root/selector, and one Physical damage leaf. Their packaged role assets only select the existing `RPG_EnemyDamage` leaf adapter on the original native hit; original damage values, animations, targeting, impulse, movement, loot, and status data are preserved. Native action certification still runs at actor attachment. Supported affix IDs remain restricted by each archetype and the existing selector; Extra Fast, Frenzied, and ME distance-Knockback are not granted by these archetypes.

Remaining high-population groups such as Skeleton combat roots, caster/projectile routes, conditional statuses, and protected authored actors remain unbound or excluded. Random Super Unique remains disabled. No new attacks, capabilities, or combat infrastructure were invented to enlarge the denominator.

## Verification and deployment

- Generated adapters were rechecked against the SHA-256 pinned installed `Assets.zip`. Focused JUnit tests passed for canonical variants, native adapter deltas, role matrix and legal affix sets, native routing, group preparation, and negative capability selection.
- `verifyHyArpgJar` passed. Compared with deployed R227, the archive adds exactly 18 native role overrides, removes no entries, and changes only the native binding manifest among gameplay assets. No affix implementation class changed.
- Built and deployed `InigmasGames:HyARPG@0.2.0-R228-U7P5` to the active `RPG/mods/Hywind.jar`; build and deployment SHA-256: `5D24ABFFE40018ADC62179F720D708B58C58C7FD047166F1403C39A428C08387`.
- Previous R227 `Hywind.jar` was copied outside Saves to `C:/Users/Zemio/.codex/deployment-backups/R228-U7P5/Hywind-before-R228-F7959972.jar`, SHA-256 `F795997279D52EE4EA65C4FA39F3D2517B7A53C92810BE0C0F3A43635ED33F63`.
- No standalone server or game was launched. Connected client behavior remains for owner QA.

Suggested QA after restarting the RPG world: use `/rpg spawn Wolf_Black unique normal` and `/rpg spawn Skeleton_Scout_Wander champion normal` to exercise the newly certified route and inherited variant, then observe natural hostile spawns over multiple births. The normal production rarity roll remains probabilistic.
