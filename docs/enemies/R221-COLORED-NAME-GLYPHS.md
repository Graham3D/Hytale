# R221-U7P5 — promoted monster colored names

R221 replaces the promoted monster **name row only** with world-space glyph models. Champion names use `#1d4dff`, Unique `#a000ff`, and Super Unique `#ff9100`. The glyph atlases are drawn in IBM Plex Mono Bold. The authoritative `EnemyDisplayDto.name()` supplies the text; no level, QA marker, rarity word, UUID, or combat information is added. Ordinary monsters keep their direct native Nameplate. The R220 native affix Nameplate anchor and Hytale Health EntityStat bar are unchanged.

`PromotedNameGlyphs` creates transient, nonserialized, intangible, zero-hitbox model children only for promoted owners. `EnemyHealthBarPresentation` owns their lifecycle and reuses `MonsterPresentationLayout.Rows.nameAnchorPosition()` for X/Z and nameY. Its existing entity-tick follow system moves glyphs with the actor and orients the row from the nearest tracking player's **world position**; no screen projection, camera/FOV sampling, or 4 Hz poll is used. Creation is atomic. The actor's direct native white Nameplate is blanked only after every requested glyph model exists; on failure it remains the fallback. The affix row is independent of name-construction success. Death, removal, QA clear, world unload, and plugin shutdown release the children.

The installed SDK's plain `Nameplate` has no color field. The [MIT-licensed TextUtils source](https://github.com/Flo12344/TextUtils) provided the glyph-quad/atlas reference. R221 does not depend on TextUtils at runtime. Its own generated assets are reproducible via `tools/Generate-MonsterNameGlyphs.py`. IBM Plex Mono Bold comes from [Google Fonts](https://github.com/google/fonts/tree/main/ofl/ibmplexmono) under OFL 1.1. Licenses ship under `META-INF/licenses` in the JAR; the source font and OFL text are retained under `tools/fonts/hywind-name`.

## Offline verification and deployment

- Focused tests: `PromotedNameGlyphsTest`, `MonsterPresentationLayoutTest`, `MonsterAffixLabelTest`, `EnemyNativeHealthbarQaTest`.
- Generated assets: 94 basic-ASCII glyph models, 282 rarity-specific server model assets, three 1024×512 transparent color atlases. Opaque/antialiased pixels retain the exact requested RGB values.
- `verifyHyArpgJar`: PASS, 19,163 entries, 2,080 classes, manifest `InigmasGames:HyARPG@0.2.0-R221-U7P5`, no ImmersiveNPCs payload. The packaged and deployed SHA-256 is `CAE663E209549E69012663CC817B542DF92AFA587D5B664C005027B86676C00F`.
- Active mod: `C:/Users/Zemio/AppData/Roaming/Hytale/data/pre-release/Saves/RPG/mods/HyARPG.jar`. The prior R220 JAR was backed up outside Saves at `C:/Users/Zemio/.codex/backups/HyARPG/HyARPG-R220-U7P5-20261007-224221.jar`, SHA-256 `14700F6643CA3C4D145A820532D544F7A0B0D79EE4D8742B88CF8EF06E5CF89E`.
- This is a **connected QA** build. Offline checks cannot confirm client-side model orientation, apparent size, spacing relative to the native affix row, or the client setting that governs native Healthbar display.
- Nearest-player facing is shared entity orientation. Multiple players on opposite sides will not each receive a separate private billboard in R221. The first connected QA is singleplayer.

## Owner QA

Restart the active RPG world, then run:

```text
/rpg spawn Skeleton_Archer champion normal
/rpg spawn Trork_Warrior unique hell
```

Check blue/purple name color before combat, affixes beneath the name, and the native Healthbar after a damaging hit. Inspect movement, rapid camera motion, centering, normal-monster names, and removal after death or `/rpg spawn clear`. A Super Unique can be tested separately for orange names.
