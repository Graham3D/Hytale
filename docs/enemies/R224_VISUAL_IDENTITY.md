# R224-U7P5 — Master Enemies visual identity QA prototype

## Scope and source of truth

This is a presentation projection of the **frozen** `EnemyDescriptor` rarity and own affixes. `MonsterVisualProfile` selects at most one skin hue, one weapon owner, and one armor owner; it never feeds the affix planner, combat, equipment inventory, rewards, or encounter state. The source color values are Champion `#1d4dff`, Unique `#a000ff`, and Super Unique `#ff9100`. Ordinary monsters remain native. `EXTRA_STRONG`, `FRENZIED`, `ARMOR_BREAKER`, and other nonvisual operators do not gain a persistent equipment color.

The fixed weapon priority is Fire, Cold, Lightning, Poison, Wind, Earth, Void, Spectral. The fixed armor priority is Packbound, Bulwark, Stone Skin, Magic Resistant, Reflective. The priority is independent of affix ordering; no colors are blended. `/rpg enemies inspect [entityUuid]` now reports the selected profile. The command shows the *selected* visual owner, which can be unsupported by a particular model in this first prototype; the capability log records that separately.

## Installed Hytale owner audit

The installed `Trork_Warrior` model has cyan body texture `NPC/Intelligent/Trork/Models/Model_Textures/Cyan_Dark.png` and separate Warrior armor attachment textures. The held native battleaxe is an inventory item, `Weapon_Battleaxe_Stone_Trork`, with its own model and `Stone_Texture.png`. Native `EntityEffect` tint applies to the whole actor, so it cannot isolate body, armor and held weapon. The existing `HytaleEnemyPalette.texturesOnly` native `ModelComponent` copy can replace the body and armor attachment textures without touching geometry or the other materials. The native `EquipmentUpdate` packet can display an alternate battleaxe item ID to viewers while the server inventory retains the original item.

## Implemented first connected sample

- All three promoted Trork rarities use restrained body texture washes. The native sheet's cyan skin pixels receive the hue; the existing brown and white detail largely remains native. Alpha, shading and model dimensions are preserved. Source hue strengths are defined in `tools/Generate-TrorkVisualTextures.py` and the original native texture SHA-256 values are pinned there. If Hytale art changes, regeneration stops.
- Trork Stone Skin uses four separately generated desaturated/slate armor attachment textures. Other armor owners are resolved deterministically but not yet colored. Each unsupported capability is logged once.
- Trork Fire Enchanted displays a warmer battleaxe texture through a **viewer-only** cosmetic `EquipmentUpdate` and `RPG_ME_Trork_Battleaxe_FireVisual` item asset. The actual NPC item stack remains `Weapon_Battleaxe_Stone_Trork`; no item stat or attack changes are made. The update is queued after native equipment updates, and only for an already-sent tracked entity. Other weapon owners are selected but are not yet colored in this prototype; their capability is logged once.
- Presentation exceptions are logged and do not fail the enemy birth or alter combat.

The current sample intentionally does **not** tint other roles, all weapons, or all armor owners. For roles without certified separate regions, no whole-model tint is applied. This is the requested three-combination proof before expanding the art matrix.

## Offline verification and connected QA boundary

Compiled against installed pre-release `0.7.0-pre.5.1`; focused `MonsterVisualProfileTest` passed; `verifyHyArpgJar` passed. The package contains the eight generated PNGs and the cosmetic battleaxe item asset. Installed Trork art SHA-256 pins and native texture dimensions match. No standalone Hytale server or game client was launched. Deployment alone does not prove that the connected client renders the tint; the user will test in the active RPG save.

Connected QA commands:

```text
/rpg spawn Trork_Warrior champion normal
/rpg spawn Trork_Warrior unique hell fireenchanted stoneskin extrastrong
/rpg spawn Trork_Warrior unique hell fireenchanted spectralhit stoneskin
/rpg enemies inspect
```

The third command may be rejected by affix compatibility. If so, the offline resolver test covers Fire versus Spectral priority without weakening production combination rules.

## Deployment record

- Active save: `C:/Users/Zemio/AppData/Roaming/Hytale/data/pre-release/Saves/RPG`
- Active mod: `mods/HyARPG.jar` (the existing active filename; no duplicate Hywind JAR was introduced)
- R224 deployed SHA-256: `7CDACE0CFFB530E6DEB7C8E8E1F64E97185589A9973CA9CB692E767651BCE2A1`
- Previous active SHA-256: `893FA3A51BDF2CD0822E748D3BFDE4DE045B2FB191BC1D87D92A6158369AB25A`
- Recovery copy outside Saves: `C:/Users/Zemio/.codex/deployment-backups/R224-U7P5/HyARPG-before-R224-893FA3A51BDF2CD0822E748D3BFDE4DE045B2FB191BC1D87D92A6158369AB25A.jar`
- Connected visual behavior awaits owner QA. The offline tests cannot prove in-game tint rendering.
