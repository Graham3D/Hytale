# R072 Lightning Spire

## Outcome

R072 replaces Lightning Coil's player-facing implementation with Lightning Spire while retaining the stable `lightning_coil` ID and `Lightning Coil` migration alias. The deployed artifact is `Hywind 0.1.0-merge.27`.

## Implemented contract

- Added a native damageable deployable with a 0.9-second invulnerable emergence and a five-second READY lifetime.
- Enforced one active Spire per caster, level-scaled health, repeated exact ten-hit allied-melee charge cycles, and zero friendly damage/resource rewards.
- Added 6 m repeated Lightning waves at the specified level-scaled 1.80 Magic Power coefficient, +2 Electrified, target conditions, displacement passives, and root-cast recovery budgets.
- Added authored emergence, sway, top Shock animation, expanding Shockwave, exact 10% charge-gauge states, destruction/expiry explosion, and deterministic cleanup.
- Preserved offensive/passive snapshots while keeping native construct health damageable after READY.
- Classified the skill as a timed deployable construct rather than Projectile, Strike, Summon, Periodic, or Aura, with the requested accepted and rejected passive matrix.
- Added an owner-asset build pipeline and moved all runtime model resources into Hytale's validated `Common/VFX` namespace.

## Automated validation

- `gradlew clean check`: PASS.
- JAR verification: PASS; 5,922 entries, 2,225 classes, 2,090 CustomUI documents, and all authored icon hashes verified.
- Isolated native server smoke: PASS; unified Hywind discovery/setup/start/shutdown, NPCs, Taverns, plugin manager, and full server boot.
- Final deployed restart validation: PASS twice with no legacy first-party plugin discovery or scoped failures.
- Persistent roots retained: `ImmersiveNPCs`, `InigmasGames_CanvasUI`, `InigmasGames_HytaleRPGPhase00Audit`, and `InigmasGames_Taverns`.

## Deployment

- Installed JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\Hywind.jar`
- SHA-256: `D28E25DBB4BB2F6F12D2A83833DF3E087D68D8221FC86A3125C5F78E570FD60F`
- Rollback snapshot: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260921T233755Z`

## Connected-client verification remaining

Verify authored model scale/placement, emergence and invulnerability, directional sway, animated Shock and Shockwave presentation, first-hit gauge appearance and all ten states, repeated waves, friendly melee interception, hostile damage/destruction, passive variants, expiry, and cleanup. This is the only remaining acceptance boundary.
