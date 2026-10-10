# R249 Monster presentation and Frost Skeleton coverage

Status: **connected QA candidate; Monster Affix remains OPEN**. This source change does not establish owner acceptance.

## Repaired paths

- R248 prepared promoted names but `presentPromotedAnchors` discarded the colored glyph state and packets on every publication. The single `NativeHostileNames` compositor now retains the logical name and suppresses its white actor plate only while a matching colored glyph name is prepared. Champion uses blue, Unique gold, Super Unique orange, and Boss red. Ordinary and minion names remain native. Glyph delivery failure tears down the packet row and requests restoration of the native plate; that actor does not repeatedly retry a broken glyph path.
- R248 placed the native affix Nameplate carrier at the desired visible row. Hytale lifts Nameplate text above its carrier, which detached the affix row from the model. `MonsterPresentationLayout` now accounts for that lift and keeps name, affix and healthbar rows centered on the same hitbox-derived X/Z. This is an offline geometry correction; exact client spacing, especially in crowded packs, needs connected QA.
- The QA presentation log previously called a combined name-and-affix condition `affixAnchor`. It now reports primary-name owner, affix-anchor presence, and colored-glyph availability separately.
- The pinned Frost Ranger bow root is a single native projectile route. Frost Archer Wander/Patrol and Frost Ranger Wander/Patrol alter only native movement/flock fields while retaining the certified parent combat graph. The pinned archetype generator now admits those exact roles, preserving its projectile affix subset and all existing negative capability checks. Frost Archmage/Scout remain uncertified.
- The existing dormant target-card formatter now retains rarity/level and all ordered defense, immunity and protection tags. The connected presentation still uses native entity UI; the old projected target poll remains disabled.

## Polar Bear boundary

The installed `Bear_Polar` model references `NPC/Beast/Bear_Polar/Models/Texture.png`, inherits the Bear model, and declares no gradient or light override. Hywind's visual variant registry has no entries and runtime tint is disabled; the only authored texture substitution is the Trork region. The Polar Bear path applies visual scale while retaining texture, gradient, attachments and collision geometry. The screenshot alone cannot distinguish native bright-white rendering from an affix/client lighting interaction. No Polar Bear material was changed. Same-light ordinary/Elite comparison in day and low light remains **BLOCKED on connected QA**.

## Evidence and boundaries

- Before: R248 connected screenshots show white generated names, detached affix rows and the pale Polar Bear; the R248 trace has one `Skeleton_Frost_Ranger` and two `Skeleton_Frost_Archer_Wander` `bindingUnavailable` rejections. R248 source removed colored name packets at publication and its native binding manifest omitted these roles.
- After: offline tests cover the exact pinned native route/alias graph, Hell profile registration, projectile affix limits, glyph atlas colors and outline, shared layout geometry, and Polar Bear scale-only packet fields. The focused tests and full verification result are recorded in the deployment receipt.
- The installed Nameplate is plain text and exposes no verified rarity color/bold field. The existing entity-mounted glyph packet path supplies color for promoted names; client rendering, glyph packet visibility, small/large-model spacing, and overload in crowded packs are **NOT_RUN** until owner QA.
- Existing source-owned combat, affix, rewards, persistence, active-pack lease, native population and world configuration paths are unchanged. No Hytale server was launched for validation.

## Connected owner QA

Restart the active RPG world with R249, then use existing operator commands:

1. `/rpg spawn Skeleton_Frost_Archer champion hell` — confirm blue Champion identity, no ordinary minions, and native bow attack.
2. `/rpg spawn Trork_Warrior unique hell` — confirm gold generated name, affix row immediately below it, damage-triggered native healthbar, and no duplicate white name. Check near/far and while moving among its minions.
3. `/rpg spawn Bear_Polar unique hell` and a naturally occurring ordinary Polar Bear — compare fur detail in the same light by day and in low light; report active affixes.
4. `/rpg spawn Skeleton_Frost_Ranger unique hell` and `/rpg spawn Skeleton_Frost_Archer_Wander champion hell` — confirm Hell level/Health and normal native projectile behavior.
5. Observe one naturally promoted pack, leave until it unloads, return, and check its name/affixes remain attached. Use `/rpg spawn clear` after QA spawns.

Connected combat, color, placement, bear rendering and unload/reload evidence remain open in `MONSTER_AFFIX_RELEASE_GATE.json` until the owner reports results.
