# HyARPG: changing spell colors

Updated for R032-AF / installed Hytale 0.7.0-pre.2, 2026-09-12.

Yes: Hytale has an in-game **Asset Editor**, not just an asset viewer. It can create a writable asset pack containing overrides. Looking at an installed asset in a viewer does not change the RPG JAR or the repository. Hytale's [official asset-pack guide](https://pre-release.docs.hytale.com/creating-content/asset-packs/) describes creating a pack in the editor and overriding existing assets. Use a separate test world for visual experiments.

AE connected testing has now proved Beam creation/update/removal. AF uses the dedicated `RPG_Healing` Beam asset, referencing shipped `Trails/Void_Green.png`, with native width scale 0.025. **Do not edit shared `Basic`**: it is no longer the Healing Beam asset. The AF appearance still requires connected visual acceptance.

## Where things actually live

- Working repository: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale`.
- Source assets: `src\main\resources\Server` and `src\main\resources\Common` inside that repository.
- Compiled build: `build\libs\HyARPG.jar`.
- Installed build: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`.
- The folder named `InigmasGames_HytaleRPGPhase00Audit` contains persistent RPG state and logs. It is **not** the source asset folder. Do not rename/delete it when editing artwork.
- `art\Skills` plus `tools\Update-RpgIcons.ps1` is the skill-icon workflow only. Dropping particle JSON or Beam textures there does not install spell visuals.

The public documentation's example `UserData` paths are generic/older. Your actual pre-release QA world uses the `data\pre-release\Saves\RPG\mods` path above.

## Which asset controls which effect?

| Visible part | Active asset / source | Color editing boundary |
| --- | --- | --- |
| Healing Beam strand | `src/main/resources/Server/Entity/Beams/RPG_Healing.json` -> shipped `Common/Trails/Void_Green.png` | Texture pixels. The installed native Beam asset has a texture field, **no particle `Color` field**. |
| Blizzard falling-shard trail | `src/main/resources/Server/Particles/RPG/Blizzard/RPG_Blizzard_Trail.particlesystem` | Follow its three `SpawnerId` references to the sibling `.particlespawner` files. |
| Blizzard snow | Shipped `Server/Particles/Weather/Snow/Snow_Heavy.particlesystem` -> `Server/Particles/Weather/Snow/Spawners/Snow_Heavy.particlespawner` | `Particle.InitialAnimationFrame.Color` and any color animation keys in a writable override. |
| Blizzard ground impact | Shipped `Server/Particles/Combat/Impact/Misc/Ice/Impact_Ice.particlesystem` | Follow its spawners and edit their particle colors. |
| Falling solid shard | `src/main/resources/Server/Models/RPG/RPG_Blizzard_Shard.json` | Model texture, separate from trail/impact particle color. |

`Beam_Heal_Green` and the old sampled healing particles do **not** control the beam. `RPG_Healing.json` now does. Likewise, the retained `RPG_Blizzard_Impact` derivative is not the current ground-impact request: runtime requests shipped `Impact_Ice`.

## Easy particle-color experiment in the Asset Editor

1. Open the in-game Asset Editor and create/select a writable personal asset pack. Use the editor's pack creation workflow rather than modifying the installed `Assets.zip`.
2. Find the ParticleSystem from the table above. Inspect its `Spawners` list, then open the referenced ParticleSpawner. A system can contain several spawners; recoloring only one may leave other parts white/blue.
3. Save an editable override in your pack. For the current runtime to use it without a Java change, the overridden asset must retain the exact existing ID. Confirm in the editor that the selected writable pack is the source of the override.
4. In the spawner's `Particle` data, change `InitialAnimationFrame.Color`. Also change any `Color` entries under `Particle.Animation`; a keyframe can replace the initial tint later in the particle's life.
5. Preview it. Use a pale tint first. Existing texture colors, transparency, render mode, light influence and animation affect the final result; a hex color is not a guarantee of a particular screen color.
6. Keep lifespan, particle lifespan, spawn rate, collision, position, velocity, scale and gameplay assets unchanged for a color-only edit.
7. Enable the pack in the test world and restart/rejoin to confirm the actual spell. The preview alone is not connected skill verification. Back up your authored pack under the GitHub repository so subsequent game updates cannot lose it.

Native overrides are shared: overriding `Snow_Heavy` changes other consumers of `Snow_Heavy`, including weather; overriding `Impact_Ice` affects other ice impacts. For **spell-only** colors, duplicate the system and all relevant spawners under unique names, then point the RPG presentation references to those unique IDs. That last wiring step is a source/configuration change; creating a differently named asset alone does not redirect the spell. Avoid two packs overriding the same ID with ambiguous load order.

The three active RPG trail spawners are:

- `RPG_Blizzard_Trail_Boulder_Trail_Mist.particlespawner`
- `RPG_Blizzard_Trail_Boulder_Trail_Snow.particlespawner`
- `RPG_Blizzard_Trail_IceBall_Trail_Snowflake.particlespawner`

All are beside `RPG_Blizzard_Trail.particlesystem` in `src/main/resources/Server/Particles/RPG/Blizzard`. For example, `RPG_Blizzard_Trail_Boulder_Trail_Snow` currently has `#ebebf5` in both the initial frame and animation frame `0`. Edit both for a consistent tint. These are actual [ParticleSpawner color/animation fields](https://pre-release.docs.hytale.com/assets/particles_particlespawner/), not Java healing/damage parameters.

## Healing Beam recoloring

AF uses a continuous native Beam, not a particle emitter. The exact installed `Beam` codec exposes `TexturePath`; there is no generic Beam tint field to turn green in the particle editor.

For spell-only recoloring, add a recolored RGBA texture under `src/main/resources/Common/Trails`, for example `Healing_Custom.png`, then change `RPG_Healing.json` to `"TexturePath": "Trails/Healing_Custom.png"`. Preserve alpha and the texture's seamless long-axis pattern. Use an image editor for the texture pixels; the Asset Editor can manage the texture reference/asset, but a particle color control cannot recolor this Beam. Do not replace shared `Void_Green.png` or `Charged_Blue.png` globally unless you intentionally want every native consumer to change. Rebuild/reinstall as described below. A custom path must also replace the exact texture check in `NativeSupportTetherAudit` (currently `Trails/Void_Green.png`) before a source build will accept it; this guard prevents accidental wrong-asset deployment. An editor-only override must satisfy the same runtime guard, so the current build is not an unrestricted texture hot-loader.

Test AF's default first and preserve a backup. The 0.025 endpoint width is configured in `NativeHealingBeamVisuals.WIDTH_SCALE`, not the particle editor. A width change requires a Java rebuild and matching appearance tests. It is a native scale multiplier, not a documented metres measurement.

## If you edit repository assets instead of a personal pack

Source edits must be rebuilt and installed; they are not watched live. From PowerShell, start in the correct directory:

```powershell
Set-Location 'C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale'
.\gradlew.bat :jar --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Build failed: do not install' }
```

Close Hytale and its server completely. Back up the installed JAR before replacing it; do not change the save/mod-data folder. A manual color-only replacement can then use:

```powershell
$installedJar = 'C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar'
$colorBackup = Join-Path (Get-Location) ('icon-backups\before-spell-colors-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $colorBackup | Out-Null
Copy-Item -LiteralPath $installedJar -Destination $colorBackup
Copy-Item -LiteralPath '.\build\libs\HyARPG.jar' -Destination $installedJar -Force
Get-FileHash -LiteralPath '.\build\libs\HyARPG.jar', $installedJar -Algorithm SHA256
```

Both hashes must match. Launch/rejoin and test. If you subsequently added icons using the icon updater rather than source, rerun `tools\Update-RpgIcons.ps1` after the rebuild to reapply your current `art\Skills`/passive icons. Its explicit backup protects the previous state. AE already preserves the four owner-installed Blizzard, Healing Beam, Snipe and Whirlwind icons in source.

Rebuilding from an older repository checkout can downgrade a newer installed build. Always use the current `RPG` branch and keep the pre-edit backup. To undo a color-only JAR replacement, close the game and copy your saved `HyARPG.jar` back; remove only your own experimental override pack if it is also active.
