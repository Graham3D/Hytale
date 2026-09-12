# HyARPG: changing spell colors

Updated for R032-AG / installed Hytale 0.7.0-pre.2, 2026-09-12.

Yes: Hytale has an in-game **Asset Editor**, not just an asset viewer. It can create a writable asset pack containing overrides. Looking at an installed asset in a viewer does not change the RPG JAR or the repository. Hytale's [official asset-pack guide](https://pre-release.docs.hytale.com/creating-content/asset-packs/) describes creating a pack in the editor and overriding existing assets. Use a separate test world for visual experiments.

AG replaces the rejected native ribbon appearance with the explicitly requested `Beam_Heal_Green2` particle system. It also uses `Effect_Health_Pack` on recipients and `Staff_Bronze` on staff model nodes. Connected appearance remains unverified. `Basic` and `RPG_Healing` no longer control the active Healing Beam presentation.

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
| Healing Beam stream | `src/main/resources/Server/Models/RPG/RPG_Healing_Stream.json` -> shipped `Beam_Heal_Green2` | Its `_Sparks`, `_Glow`, and `_Plus` spawners under `Server/Particles/_Test/HealBeams/Spawners`. Edit their particle color keys. |
| Healing recipient | `src/main/resources/Server/Entity/Effects/RPG/RPG_Healing_Recipient.json` -> `Effect_Health_Pack` | `Health_Pack_Crosses` and `Health_Pack_Rays` under `Server/Particles/Status_Effect/Heal/Spawners`. |
| Staff heads | `src/main/resources/Server/Item/Items/Weapon/Staff/*.json` -> `Staff_Bronze` | `Staff_Bronze_Air` and `Staff_Bronze_Sparks` under `Server/Particles/Weapon/Staff/Spawners`. |
| Blizzard falling-shard trail | `src/main/resources/Server/Particles/RPG/Blizzard/RPG_Blizzard_Trail.particlesystem` | Follow its three `SpawnerId` references to the sibling `.particlespawner` files. |
| Blizzard snow | Shipped `Server/Particles/Weather/Snow/Snow_Heavy.particlesystem` -> `Server/Particles/Weather/Snow/Spawners/Snow_Heavy.particlespawner` | `Particle.InitialAnimationFrame.Color` and any color animation keys in a writable override. |
| Blizzard ground impact | Shipped `Server/Particles/Combat/Impact/Misc/Ice/Impact_Ice.particlesystem` | Follow its spawners and edit their particle colors. |
| Falling solid shard | `src/main/resources/Server/Models/RPG/RPG_Blizzard_Shard.json` | Model texture, separate from trail/impact particle color. |

Use `Beam_Heal_Green2` (including the final **2**), not `Beam_Heal_Green` or the similarly named unused `Beam_Heal_Green2.particlespawner`. The particle SYSTEM references three other spawners. The retained `RPG_Blizzard_Impact` derivative is not the current ground-impact request: runtime requests shipped `Impact_Ice`.

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

AG uses the stock system unchanged. Follow the particle-color workflow above for `Beam_Heal_Green2_Sparks`, `Beam_Heal_Green2_Glow`, and `Beam_Heal_Green2_Plus`. Their textures are `Ball3.png`, `Circle_Glow.png`, and `Health_Regen_Plus.png`. Editing the unused similarly named spawner will not change the active system.

For spell-only changes, use uniquely named copies of the system and its three referenced spawners, then update `RPG_Healing_Stream.json`, the exact asset audit, and appearance tests together. A global override of any shipped spawner also affects other native consumers. Do not change particle velocity/lifespan to make a color edit. The old `NativeHealingBeamVisuals.WIDTH_SCALE` and `RPG_Healing.json` are retained regression fixtures, not controls for AG's live stream.

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
