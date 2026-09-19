# Stage 13 R039 — Fire Bolt / Fireball Presentation Corrections

Date: 2026-09-15  
Branch: `RPG`  
Baseline HEAD: `25b85cb2819351a0d727e33132b2f57013cda024`  
Revision: `R039`

## Delivery status

- IMPLEMENTED: YES
- PACKAGED: YES
- DEPLOYED: YES
- CONNECTED-VERIFIED: NO — owner connected-client QA remains required

This correction is presentation-scoped. It does not intentionally change Fire Bolt or Fireball damage, resource cost, cooldown, charge timing, ballistic solution, collision dimensions, continuation coefficients, mastery, persistence, or combat authority.

## Requested contract and result

### Fire Bolt

- Launch now presents native `SFX_Staff_Fire_Shoot` once for the authoritative root projectile.
- Enemy and terminal terrain impact now present native `SFX_Staff_Flame_Flamethrower_Impact`.
- Fork/Chain/Volley descendants do not replay the launch sound.
- Impact sound ownership is bounded by a deterministic impact key so the same terminal impact cannot emit repeatedly.
- The moving projectile retains the existing `Fire_Projectile` visual intent through the RPG-owned `RPG_Fire_Projectile_No_Line` carrier asset.
- The generic sampled line-trail renderer is suppressed only for Fire Bolt and Fireball. Other projectile families retain their existing readability trail behavior.

### Fireball

- Release now presents native `SFX_Staff_Flame_Fireball_Launch` once for the authoritative root projectile.
- Authoritative explosion resolution presents native `SFX_Staff_Flame_Fireball_Impact` once per claimed root explosion.
- No RPG-authored charge countdown or charge-stage sound remains in the Fireball charge source or assets.
- The ballistic preview now uses neutral white sampled markers and a white landing-ring marker. It continues to consume the same `FireballTrajectory` solver output used by release.
- Preview markers are removed through the existing charge-session cleanup path on release, cancellation, interruption, or terminal state.
- The released projectile no longer receives the generic white/fire sampled line trail.
- Charge stage maps to four visual tiers:

  | Charge stage | Tier | Native config | Visual scale |
  | --- | --- | --- | ---: |
  | 0 or 1 | Small | `Projectile_Config_RPG_Fireball_Small` | 1.0x |
  | 2 | Medium | `Projectile_Config_RPG_Fireball_Medium` | 1.5x |
  | 3 | Large | `Projectile_Config_RPG_Fireball_Large` | 2.1x |
  | 4 | Extra Large | `Projectile_Config_RPG_Fireball_Extra_Large` | 2.8x |

- All four model assets preserve the same mechanical hit box (`-0.45..+0.45` on each axis). Only model visual scale changes.
- All four projectile configs preserve speed `16`, gravity `12`, physics behavior, and empty native gameplay interactions.
- Fork/Chain/Volley descendants inherit the root's selected projectile config, so a continued projectile keeps its original charge-stage size.

## Installed 0.7.0-pre.2 native asset audit

The implementation was checked against the installed pinned asset package rather than inferred from names.

Verified native SoundEvents:

- `SFX_Staff_Fire_Shoot`
- `SFX_Staff_Flame_Flamethrower_Impact`
- `SFX_Staff_Flame_Fireball_Launch`
- `SFX_Staff_Flame_Fireball_Impact`

The shipped charge particle families `Fire_Charge1` and `Fire_Charged1..4` were inspected. Their child systems use baked delays (approximately 0.2, 2.2, 4.2, 6.0, and 8.3 seconds), making them unsuitable as the moving projectile's four release-size tiers. R039 therefore reuses the existing RPG orb presentation and varies only model scale.

`Fireball_Charge_To_4` contains staged particle spawners but no authored sound field. `Server/Item/Animations/Fire_Stick.json` maps `Soak_4` to the native third-person, moving, and first-person clips with speed `0.45`; it contains no audio metadata. A byte-level audit of all three referenced `Soak_4` `.blockyanim` files found no sound/SFX/audio/event reference. This supports retaining the native charge animation while removing RPG-authored charge-stage audio.

The pinned Beam implementation (`BeamComponent` / `AttachedBeam`) is a straight endpoint beam and does not expose a curved/polyline ballistic guide. R039 therefore uses the existing bounded sampled-preview fallback, but with an RPG-owned stationary white marker asset instead of fire-colored guide particles.

## Implementation details

### Central presentation policy

`FireProjectilePresentation` centralizes:

- charge-stage-to-tier mapping;
- config ID and visual scale;
- exact launch and impact SoundEvent IDs;
- root-only launch ownership;
- bounded impact ownership keys;
- fire-projectile generic-trail suppression.

This prevents presentation decisions from being duplicated across spawn, impact, and continuation code.

### Runtime integration

`HytaleSkillExecutionSystem` now:

1. selects the Fireball projectile config from the authoritative charge stage before native spawn;
2. emits launch audio only after the carrier has been successfully spawned;
3. skips launch audio for continuation descendants;
4. emits Fire Bolt impact audio at enemy or terminal terrain resolution;
5. emits Fireball impact audio only after the root explosion ownership claim succeeds;
6. suppresses the generic sampled projectile trail only for Fire Bolt and Fireball;
7. traces selected Fireball stage, tier, scale, and native config;
8. records bounded SoundEvent resolution/presentation failures without changing gameplay outcome.

### Native asset validation

`NativeProjectileAssetAudit` now resolves all four Fireball configs through the actual Hytale projectile asset path and verifies:

- config existence;
- speed and gravity;
- absence of native gameplay interactions;
- invariant mechanical bounds;
- expected model scale;
- neutral-white guide asset existence;
- all four native SoundEvent IDs.

Startup evidence reports the resolved fire-presentation contract in `RPG_STAGE13_PROJECTILE_ASSETS`.

## Principal affected files

- `src/main/java/com/inigmasgames/hytalerpg/execution/projectile/FireProjectilePresentation.java`
- `src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.java`
- `src/main/java/com/inigmasgames/hytalerpg/execution/hytale/NativeProjectileAssetAudit.java`
- `src/main/resources/Server/Particles/RPG/RPG_Fireball_Aim_White.particlesystem`
- `src/main/resources/Server/Particles/RPG/Spawners/RPG_Fireball_Aim_White_Marker.particlespawner`
- `src/main/resources/Server/Models/Projectiles/RPG_Fireball_{Small,Medium,Large,Extra_Large}.json`
- `src/main/resources/Server/ProjectileConfigs/RPG/Projectile_Config_RPG_Fireball_{Small,Medium,Large,Extra_Large}.json`
- `src/main/resources/rpg/runtime/stage-13-projectiles-cohort-c.json`
- `src/test/java/com/inigmasgames/hytalerpg/Stage13FireProjectilePresentationR039Test.java`
- `src/test/java/com/inigmasgames/hytalerpg/Stage13AuthoredProjectileTest.java`
- `tools/Run-Stage13CohortSmoke.ps1`
- `gradle.properties`

## Verification

### Focused regression

The R039 presentation and retained authored-projectile tests passed. Coverage includes:

- exact four-tier stage mapping;
- visual-only scale changes with invariant collision bounds;
- root config inheritance by continuation descendants;
- root-only launch audio and bounded impact audio;
- absence of authored charge-stage SFX;
- white preview assets;
- selective Fire Bolt/Fireball line-trail suppression;
- runtime hook ordering around successful native spawn and authoritative impact ownership.

### Complete retained validation

Command:

```powershell
.\gradlew.bat clean check --console=plain
```

Result: PASS

- Main/native JVM tests: 2,424 passed; 0 failures; 0 errors; 0 skipped.
- CanvasUI retained tests: 21 passed; 0 failures.
- Combined retained total: 2,445 passed.
- CustomUI packaging validation: PASS.

### Exact three-mod isolated smoke

Command:

```powershell
.\tools\Run-Stage13CohortSmoke.ps1 -Cohort r039 -NativeProjectileSpawnAudit
```

Result: PASS

- exactly three mods staged;
- R039 startup revision observed;
- all four Fireball configs resolved through the native asset registry;
- speed, gravity, invariant `0.45` mechanical radius, visual scales, guide asset, and SoundEvents audited;
- smoke artifact SHA-256 matched the packaged candidate.

Smoke evidence: `evidence/stage-13/cohort-r039/`

## Packaging and deployment

RPG artifact:

- file: `evidence/stage-13/revision-r039/artifacts/HyARPG.jar`
- size: 3,287,362 bytes
- SHA-256: `CD8D80603BFAE91E3B160294EEC4ACE6FF0192E147B0DDB753D484AF41E6D795`

Three-mod archive:

- file: `evidence/stage-13/revision-r039/HyARPG-R039-three-mods.zip`
- SHA-256: `166FF3F0DA4DE0FC8118DDB362C5F4D664BCFDAFD1CEB03F05FF287C2258F7BE`
- contents: CanvasUI, HyARPG, and HYTALEDEVLIB only.

Live deployment:

- destination: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`
- deployed SHA-256: `CD8D80603BFAE91E3B160294EEC4ACE6FF0192E147B0DDB753D484AF41E6D795`
- packaged/deployed hash comparison: MATCH

Rollback was captured before replacement:

- directory: `evidence/stage-13/revision-r039/rollback/live-before-r039/`
- prior JAR: `HyARPG-R038.jar`
- prior JAR SHA-256: `9450A6585EEA75B38027A7D23FCB989E2F35D7256F94348D563AC7815423B858`
- matching RPG mod-data directory was copied into the rollback set.

No live save or world data was launched or mutated during packaging/deployment.

## Connected-client QA still required

Local tests and isolated smoke establish structure and server-side asset resolution; they do not prove client rendering or audio.

1. Restart Hytale, join the RPG save, and confirm the top-right revision reads `R039`.
2. Cast Fire Bolt into empty space and at terrain: confirm one launch sound, no long line trail, retained moving fire visual, and one terminal impact sound.
3. Hit an entity with Fire Bolt: confirm one impact sound and unchanged gameplay result.
4. Hold Fireball through each charge threshold: confirm there is no countdown/stage audio while holding.
5. During Fireball charge, confirm the ballistic path and approximately 3.5-meter landing ring are neutral white and move with aim.
6. Release at stages 0/1, 2, 3, and 4: confirm visibly Small, Medium, Large, and Extra-Large projectiles; one launch sound; no post-release line trail; and one impact sound.
7. Confirm the guide disappears immediately on release/cancel and leaves no residue.
8. Repeat a charged Fireball with Fork, Chain, and Volley where available: descendants must retain the root projectile size and must not replay launch audio.
9. Confirm gameplay values—damage, Mana, cooldown, trajectory, explosion radius, and collision behavior—remain unchanged.

If the client still emits a charge-stage sound, capture the connected client log and skill trace around a single hold/release. The installed native asset audit found no audio hook in the selected `Fire_Stick/Soak_4` animation, so remaining audio would be localized to a client-native charging behavior not exposed by the audited asset metadata.

