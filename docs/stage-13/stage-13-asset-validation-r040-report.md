# Stage 13 R040 — Single-Player Asset Validation Correction

Date: 2026-09-15  
Branch: `RPG`  
Baseline HEAD: `25b85cb2819351a0d727e33132b2f57013cda024`  
Revision: `R040`

## Delivery status

- IMPLEMENTED: YES
- PACKAGED: YES
- DEPLOYED: YES
- CONNECTED-VERIFIED: NO — owner must restart and join the RPG save

## Connected failure

The 2026-09-15 08:32 connected client log records two failed single-player startup attempts. The earliest fatal boundary is:

```text
[AssetStore|Interaction] Failed to validate asset:
**Projectile_Config_Hookshot_Interactions_ProjectileSpawn_Interactions_0
Key: BeamConfig
FAIL: Asset 'Rope' of type com.hypixel.hytale.builtin.beam.asset.Beam doesn't exist!
...
[HytaleServer] Asset validation FAILED with 1 reason(s)
Assets Hytale:Hytale failed to load.
```

The numerous missing RPG localization-key messages in the same log are warnings and are not the reason the server shuts down. The fatal validation count is one and points specifically to the shipped Hookshot's `BeamConfig: Rope` dependency.

## Diagnosis

The installed `0.7.0-pre.2` asset package was inspected directly. It contains:

- `Server/Entity/Beams/Basic.json`
- `Server/Entity/Beams/Fire.json`
- `Server/Entity/Beams/Rope.json`

The native `Rope.json` descriptor is:

```json
{
  "TexturePath": "Trails/Rope.png"
}
```

R038 and R039 contain byte-identical RPG Healing Beam descriptors, so the crash was not caused by a change to `RPG_Healing` or `RPG_Healing_Flow`. A fresh four-mod server using the exact live JAR set also booted successfully with R039, which means the failure is specific to the connected single-player/save asset-merge path rather than a deterministic malformed R039 Beam JSON.

The connected single-player path nevertheless produced a registry in which the native Hookshot interaction could not resolve the native `Rope` Beam. Because Hytale treats that missing built-in dependency as globally fatal, relying on the base-pack merge alone is unsafe for this save configuration.

## Correction

R040 packages an exact fallback copy of the installed pre.2 native descriptor at:

```text
Server/Entity/Beams/Rope.json
```

This does not redesign, replace, or modify the Rope texture, Hookshot interaction, Healing Beam implementation, Fire Bolt, or Fireball. It ensures the merged Beam asset store has the same `Rope -> Trails/Rope.png` mapping even when the single-player merge omits the base-pack record.

An automated regression loads the packaged resource and requires:

- exactly one descriptor property;
- property `TexturePath`;
- exact value `Trails/Rope.png`.

The smoke runner was advanced to numeric revision `R040`; all retained gates remain enabled.

## Validation

### Focused tests

The R040 asset-validation regression and retained R039 fire-presentation tests passed.

### Complete retained suite

Command:

```powershell
.\gradlew.bat clean check --console=plain
```

Result: PASS

- RPG/native JVM tests: 2,425 passed; 0 failures; 0 errors; 0 skipped.
- CanvasUI tests: 21 passed.
- Combined retained total: 2,446 passed.
- CustomUI validation: PASS.

### Exact three-mod smoke

Command:

```powershell
.\tools\Run-Stage13CohortSmoke.ps1 -Cohort r040 -NativeProjectileSpawnAudit
```

Result: PASS

- exactly three mods;
- R040 plugin setup and enablement observed;
- native asset validation completed;
- server booted and shut down cleanly;
- retained native projectile construction audit passed;
- no missing Rope or global asset-validation failure.

Evidence: `evidence/stage-13/cohort-r040/`

### Live-pack compatibility smoke

A separate isolated fresh universe was started with the same four JARs present in the owner's normal save:

- CanvasUI
- HyARPG R040
- HYTALEDEVLIB
- ImmersiveNPCs R170

Result: PASS

- four JARs discovered;
- HyARPG enabled;
- server booted;
- no missing `Rope`;
- no asset-validation failure;
- clean process exit.

Evidence log: `run/r040-live-pack-smoke-345d0702e51b40d7b0b87acbffe5aa89/server-smoke.txt`

## Packaging and deployment

RPG artifact:

- `evidence/stage-13/revision-r040/artifacts/HyARPG.jar`
- size: 3,287,534 bytes
- SHA-256: `54F7EE11BAD20A83CD039D592E9DAA1B0B34EC34786B4E95F7D22D3C7E7BF3E1`

Three-mod archive:

- `evidence/stage-13/revision-r040/HyARPG-R040-three-mods.zip`
- SHA-256: `5219026CCEAF30C411CE37AC18FF67870DA243E23A946453D3619206161E1486`
- exactly three JARs.

Live deployment:

- `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`
- SHA-256: `54F7EE11BAD20A83CD039D592E9DAA1B0B34EC34786B4E95F7D22D3C7E7BF3E1`
- packaged/deployed hashes: MATCH

Rollback captured before replacement:

- `evidence/stage-13/revision-r040/rollback/live-before-r040/HyARPG-R039.jar`
- prior SHA-256: `CD8D80603BFAE91E3B160294EEC4ACE6FF0192E147B0DDB753D484AF41E6D795`
- matching RPG mod-data backup: present, 37,458,225 bytes.

No live world or save data was modified during this correction.

## Connected acceptance

1. Fully restart Hytale.
2. Enter the normal RPG save.
3. Confirm the top-right revision badge reads `R040`.
4. Confirm startup no longer reports `Asset validation failed`.
5. Retest the R039 Fire Bolt and Fireball presentation checklist.

If connection still fails, preserve the new client log. The first comparison should be whether `Asset 'Rope' ... doesn't exist` is absent or whether a different earliest asset boundary has surfaced.

