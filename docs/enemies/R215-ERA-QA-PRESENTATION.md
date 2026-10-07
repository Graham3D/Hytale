# R215-U7P5 — era QA and promoted monster presentation

## Implementation

- `/rpg spawn <concreteRole|random> <champion|unique|superunique> <normal|nightmare|hell> [affixes...]` resolves an existing authored profile for the requested era and freezes its level, Health, damage, Defense, resistance, and difficulty in each QA actor. The saved world binding is read but never changed. The same birth planner and affix owners remain in use. Explicit affixes must match the selected era's count; an empty list uses the existing compatibility selector. `RPG_ENEMY_QA_PLAN` logs the actual selected IDs and level.
- Champion counts are 1/1/1 and Unique counts 1/2/3 from `master-enemies-v1.json`. An authored Super Unique role uses its template's fixed affixes plus its authored era addition and minion count. Roles without a Super Unique template retain the established `qa-ad-hoc` three-card base; Nightmare/Hell additions stop at the existing four-card cap. This is a QA fallback, not a production template or promotion change.
- Connected R214 logs showed `Combat.DisplayHealthBars=true`, `UIComponentList` still containing native `Healthbar`, and native Health changing from 358.4 to 226.4 to 208.4 to 7.4 on a damaged Trork. The Trork role assets have no `HiddenUIComponents` declaration. The server cannot read a player's private client display setting. The owner reported no visible native bar, so R215 uses the existing viewer-local projected target stack and native Health values. `RPG_ENEMY_QA_PRESENTATION_FINAL` records the final server state and fallback mode.
- The compact stack is projected from the native bounding-box top and hidden when the target is no longer eligible. It uses the existing background/fill/frame art in that order, with a left-anchored fill clamped to current/max native Health. Promoted names show no level, rarity word, QA marker, or minion text; Champion/Unique/Super Unique names use `#1d4dff`/`#a000ff`/`#ff9100` and bold text. Affix labels are regular-weight under the bar. Minions retain a reduced name-only target presentation and native identity. Boss identity formatting remains outside this pass.

## Verification and connected QA

Focused offline tests cover the three authored era profiles without a world rebinding, exact affix counts, template fixed cards/minions, QA birth, transient encounter state, projected perspective/fill/art, native healthbar diagnostics, and command parsing. CustomUI asset validation and JAR verification run before deployment. No standalone Hytale server was launched. Connected rendering and combat scaling still require owner play.

The verified `R215-U7P5` package is deployed as `Hytale/data/pre-release/Saves/RPG/mods/HyARPG.jar`, SHA-256 `BB0652F78ECDC8772AEDAA4AA83533C8C2E40A29CCCC72133B61C1A09ED93503`. The prior R214 package is retained outside the save at `C:/Users/Zemio/.codex/backups/HyARPG/HyARPG-R214-U7P5-20261007-193555.jar`.

Restart the active RPG world, then test:

1. `/rpg spawn Skeleton_Fighter unique normal`
2. `/rpg spawn Skeleton_Fighter unique nightmare`
3. `/rpg spawn Skeleton_Fighter unique hell`
4. `/rpg spawn Trork_Warrior unique hell extrastrong frenzied armorbreaker`

Inspect the named leader while looking at it, then damage/heal it and move the camera/actor. Check that the name is bold, affixes are regular, the bar follows its head and changes with native Health, and minions do not receive the full stack. The selected affixes and frozen level appear in `RPG_ENEMY_QA_PLAN`; `RPG_ENEMY_QA_PRESENTATION_FINAL` reports server-side presentation state. Deployment does not count as connected acceptance.
