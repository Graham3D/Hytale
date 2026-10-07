# R208-U7P5 — QA Frost binding and target HUD

Date: 2026-10-07. Current source is the R207-U7P5 worktree plus the changes below; no older HyARPG revision was restored.

## Changes

- Added a QA-only `Golem_Crystal_Frost` native binding. Its original chained root and seven authored damage variables are certified through the existing action/receipt owner. The role overlay changes only those seven leaf types to the existing `RPG_EnemyDamage` adapter. The binding admits ME-006 Cold Enchanted, ME-003 Magic Resistant, and ME-027 Bulwark; it does not enable natural production promotion. The earlier Earth Crystal and Firesteel Golem bindings remain passive-only.
- The QA birth decision now checks the staged native role's hostile player attitude. Abstract, passive, unbound, or otherwise unsupported roles still reject. `classifyStagedQa` and its authored-profile fallback were not changed.
- Replaced the upper-left target card with a transparent, compact, top-center target HUD. It shows the targeted actor's name, native Health bar, own/inherited affix text, and concise rarity/level. The three supplied PNGs are packaged in the JAR. Health fill uses the EXP bar's left-anchored width mechanism; damage sends a target-specific refresh, and the existing viewer-local target poll catches target changes and healing. The configured target HUD supersedes transient world-space health-bar reveals.
- Preserved the existing Trork action binding and leader/minion affix projection.

## Focused verification

- `:compileJava`: passed.
- `:test` focused on `EnemyNativeBindingsTest`, `FrostGolemNativeBindingAssetTest`, `EnemyQaBirthPlannerTest`, `EnemyNativeOutgoingTest`, `EnemyNativeDamageLeafTest`, `R208EnemyTargetHudTest`, and `R020HudCorrectionTest`: passed.
- `validateCustomUi`: 88 documents passed.
- `:jar`: passed. Archive inspection found the Frost role overlay, binding manifest, all three health PNGs, target UI, and embedded `rpg.revision=R208-U7P5`.
- No standalone server or game client was launched. Live action-graph attachment and visual appearance remain connected-client QA checks.

## Deployment

- Live JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`
- Live/package SHA-256: `70C15B9961452F36624D26DF7F6A74956CBC0A6BAEE7875DF9FBC33C2B7C8A42`
- Previous R207-U7P5 SHA-256: `2ADF7BBE5D9A0C29E9E9A14E04D519BCCB4E574D7CE609278507F34036A4C77C`
- Previous JAR backup outside Saves: `C:\Users\Zemio\.codex\backups\HyARPG\R208-predeploy-20261007-113400\HyARPG-R207-U7P5.jar`
- No save data or other mod JAR was edited.

## Connected QA still needed

1. Restart the RPG world, run `/rpg spawn Golem_Crystal_Frost superunique coldenchanted magicresistant bulwark`, and confirm acceptance, attached native melee, and no `ENEMY_ACTION_*` failure.
2. Confirm a Frost leader displays its three affixes; aim at a minion to confirm only its own/inherited effects are shown.
3. Damage and heal a targeted monster; confirm the top-center background/fill/frame layers track native Health with a fixed left edge. Change and lose the target to confirm the HUD updates and hides.
4. Run `/rpg spawn Trork_Warrior unique extrastrong frenzied armorbreaker` to check the earlier Trork route remains functional.

Offline checks prove packaging and static owner wiring; they do not certify connected rendering or native Golem action attachment. If the latter rejects, retain the log's precise `ENEMY_ACTION_*` boundary for a focused repair.

Git visibility: this report, `INTEGRATION_MAP.md`, and `COMMANDS.md` are pushed on `codex/checkpoint-c-optional-bridge`. The deployed source remains in the local R207/R208 worktree, which already contained a large set of unrelated uncommitted changes; this documentation commit does not claim to publish a reproducible source snapshot.
