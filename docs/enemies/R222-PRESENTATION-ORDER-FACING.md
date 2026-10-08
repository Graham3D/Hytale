# R222-U7P5 — promoted-name facing and row order

Connected R221 QA found that the colored glyph name showed the back of its quads and that the native affix Nameplate appeared above the name. This pass changes only presentation.

`PromotedNameGlyphs.frontFacingYaw` rotates glyph models by 180° relative to their previous viewer-facing yaw. Glyph **positions** retain the original viewer-right basis, so letters stay left to right. The same correction applies at creation and during entity-tick follow. A focused test checks readable-front alignment and glyph order at four viewing angles.

`MonsterPresentationLayout` remains the only owner of world-space row coordinates. The colored renderer still receives `Rows.nameAnchorPosition()`, the affix anchor still receives `Rows.affixAnchorPosition()`, and the native Healthbar remains the lowest row. The native affix Nameplate renders its text above its zero-volume entity anchor, while glyphs render at their entity Y. The shared name gap is now `1.00 m` (from `0.36 m`) to clear that native lift. This is a connected-QA spacing correction; the installed client does not expose a numeric Nameplate text-offset field to the server, so visual acceptance remains with owner QA. Rarity colors, font assets, ordinary monster names, native Healthbar behavior, and all combat/affix systems are unchanged.

## Verification and deployment

- Focused `PromotedNameGlyphsTest` and `MonsterPresentationLayoutTest`: PASS.
- `verifyHyArpgJar`: PASS. Manifest `InigmasGames:HyARPG@0.2.0-R222-U7P5`; 19,163 entries, 2,080 classes, no ImmersiveNPCs payload.
- Active `C:/Users/Zemio/AppData/Roaming/Hytale/data/pre-release/Saves/RPG/mods/HyARPG.jar`: SHA-256 `F871EE0CB89A57986C084A4A7A5BAB06E111460891D4F8B10005A682B56B255D`.
- Prior R221 backup outside Saves: `C:/Users/Zemio/.codex/backups/HyARPG/HyARPG-R221-U7P5-20261007-225212.jar`, SHA-256 `CAE663E209549E69012663CC817B542DF92AFA587D5B664C005027B86676C00F`.
- No standalone server or game client was launched.

Restart the RPG world and run `/rpg spawn Trork_Warrior unique hell`. Inspect readable purple name above persistent affixes, native Healthbar after damage, horizontal centering, movement, and camera rotation. Connected visual acceptance is pending.
