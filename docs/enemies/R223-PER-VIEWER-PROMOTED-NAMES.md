# R223-U7P5 — per-viewer promoted names

## Scope and implementation

- Replaced R222 shared glyph entities and nearest-viewer orientation with one logical promoted name per actor and packet-only glyph state per tracking viewer. The fake root is mounted to the actual NPC network entity; the glyphs are mounted to that root. Ordinary movement follows Hytale's client-side parent interpolation. Yaw refresh sends transform packets without resending mounts. Both body and look pitch/roll are zero. Monster head/body rotation is not the facing source.
- Admission uses the native entity viewer's `visible` and `sent` tracking state. There is no extra activation/drop distance. The packet state is removed on tracking loss, owner death/despawn, QA clear, world unload, viewer disconnect, and plugin shutdown. Tracking-loss logs include viewer distance when available.
- `MonsterPresentationLayout.Rows.nameY` remains the sole name-row height. Native affix anchor, native Healthbar, ordinary native Nameplates, and gameplay owners are unchanged. Promoted names remain blank on their original actor only after glyph preparation succeeds.
- Increased the single promoted-name model scale to `0.31`. Three clamped, hysteretic distance buckets provide up to 1.5× compensation while the owner remains natively tracked. Replaced the monospaced presentation atlas with proportional Lato Bold, generated from the checked-in OFL-licensed font. The font license is packaged as `META-INF/licenses/Lato-OFL.txt`. No Hytale client font was copied. The technique was independently implemented after studying public MysticNameTags as an architecture reference; no GPL source was copied.
- Changed only the displayed alias `Wind Ench.` to `Wind Enchanted`. ME identity, planner data, persistence, and affix execution were not changed.

## Verification and deployment

- `compileJava compileTestJava`: PASS.
- Focused `PromotedNameGlyphsTest`, `MonsterAffixLabelTest`, `MonsterPresentationLayoutTest`: PASS, including spawn mount topology, yaw-only refresh with no new mounts, upright orientation, glyph order, proportional widths, and distance buckets.
- `verifyHyArpgJar`: PASS. Manifest `InigmasGames:HyARPG@0.2.0-R223-U7P5`, 19,170 entries, 2,085 classes, no ImmersiveNPCs payload.
- `Validate-PromotedNameAssets.py`: PASS. 282 rarity models, 94 glyph models, exact atlas color pixels, metrics, packaged OFL license.
- `auditU7P5AssetCompatibility`: FAIL on its frozen R176 JSON asset-set allowlist. The failure lists the pre-existing R200–R222 Master Enemies/presentation assets and this revision's glyph metrics; it does not report a broken model reference. The historical audit baseline was not broadened or rewritten for this presentation task.
- Previous active JAR was backed up outside Saves at `C:/Users/Zemio/.codex/backups/HyARPG/R222-U7P5-20261007-232538/HyARPG-R222-U7P5.jar` (SHA-256 `F871EE0CB89A57986C084A4A7A5BAB06E111460891D4F8B10005A682B56B255D`). The matching HyARPG mod-data archive is in the same directory (SHA-256 `7E0A557D860B738CE18C594AD56D9D1DDB5CDD8865EC3DA74A58600EE7899EC0`). Save data was not modified.
- Deployed `C:/Users/Zemio/AppData/Roaming/Hytale/data/pre-release/Saves/RPG/mods/HyARPG.jar` SHA-256 `893FA3A51BDF2CD0822E748D3BFDE4DE045B2FB191BC1D87D92A6158369AB25A`. No Hytale server or game client was launched. Connected acceptance is pending owner QA.

## Connected QA

Restart the RPG world, then test `/rpg spawn Trork_Warrior unique hell` and `/rpg spawn Skeleton_Archer champion normal`. Check readable colored names at point blank and native tracking distance, upright 360° billboarding while camera pitch or monster facing changes, name → affix → Healthbar order, and cleanup after `/rpg spawn clear`. Native Healthbar behavior is unchanged from R222; the affix row remains persistent.
