# R077 Lightning Spire hit-feedback correction

R077 corrects the connected-client failure captured after R076 and applies the requested Lightning Spire presentation changes.

## Trace diagnosis

The R076 trace showed four accepted player strikes and `LIGHTNING_SPIRE_CHARGE`, followed by `Invalid entity reference!` and native Health reaching zero. The transient Shock carrier attempted to start its animation before its buffered entity had entered the store. That presentation exception escaped the friendly-hit callback before the native damage event could be cancelled. Consequently the Spire took player damage, and the later gauge creation never ran.

## Corrections

- Friendly direct player strikes are cancelled independently of presentation success. Gauge, SFX, and Shock failures are isolated and traced without allowing player damage through.
- Transient model animation begins from a deferred command after the carrier entity is committed.
- Four distinct READY-state player melee strikes now trigger a shockwave. Gauge states are 25%, 50%, 75%, and 100%.
- The authored gauge is composited in background → fill bar → frame order and appears one block above the Spire top.
- Every accepted hit replays the emergence shake, plays `SFX_Metal_Break`, and displays the existing eight-frame, three-axis Shock carrier at 1.75 blocks above ground for its complete animation.
- Emergence now spawns Hytale's `Undead_Digging` particle system.
- The discharge now uses a packaged particle-system wrapper around Hytale's `Portal_Hedera_Spawn2_Shockwave2` spawner. Gameplay radius, damage, target impacts, and `Laser_Impact` remain unchanged.

Connected-client QA remains required for world rendering and audible SFX acceptance.
