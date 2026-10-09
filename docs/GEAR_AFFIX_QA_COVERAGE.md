# Gear affix QA coverage (R164)

The [machine manifest](../src/main/resources/rpg/gear/affix-qa-coverage-v1.json) assigns every one of the 160 authored affix IDs exactly once. The build test reconstructs the manifest from the current catalog, fixtures, and Iron Sentinel classifier and fails on omissions, duplicates, invalid tiers, eligibility, exclusion groups, requirements, or rarity budgets.

This manifest records *fixture coverage*, not production safety. Twenty affixes have mapped fixture carriers and effect adapters; of those, **19 are production-safe by the offline runtime audit**. WA-121 is the exception: its Rare battleaxe fixture and the production generator violate its authored Very Rare/Legendary gate. WA-003 is enabled but has no mapped focus base. The other 139 affixes have no active production effect adapter. See the [160-affix runtime audit](GEAR_AFFIX_RUNTIME_AUDIT.md) for each ID's status, owner, and missing boundary. A fixture's presence or `TEST` disposition does not establish an in-game effect, and WA-151's Magic Find deliberately excludes QA-provenance gear.

| Fixture | Base | Slot/family | Rarity | Qualified affixes |
| --- | --- | --- | --- | --- |
| `head-magic` | `gm.cloth_linen.head.n` | Head | Magic | WA-091, WA-009 |
| `chest-magic` | `gm.cloth_linen.chest.n` | Chest | Magic | WA-092, WA-012 |
| `hands-rare` | `gm.cloth_linen.hands.n` | Hands | Rare | GA-159, WA-085, WA-151 |
| `legs-rare` | `gm.cloth_linen.legs.n` | Legs | Rare | GA-160, WA-093, WA-086, WA-154 |
| `sword-rare` | `gm.sword_copper.n` | Sword | Rare | WA-001, WA-157 |
| `shortbow-rare` | `gm.shortbow_copper.n` | Shortbow | Rare | WA-002, WA-158, WA-088 |
| `battleaxe-rare` | `gm.battleaxe_adamantite.h` | Battleaxe | Rare | WA-121, WA-089 |
| `crossbow-magic` | `gm.crossbow_scout.nm` | Crossbow | Magic | WA-090 |
| `mace-magic` | `gm.mace_copper.n` | Mace | Magic | WA-087 |

The requested ten-affix items and Legendary legs cannot be produced honestly with the active legality and rarity budgets. These fixtures use the production budgets; no production pool or item/save format has been relaxed. The stable fixture/player identity prevents duplicate issuance. Use `/rpg gear affixqa list` and `/rpg gear affixqa spawn <fixtureId|armor|weapons|all>` with the gear-author permission. The group command stops at the first failed admission/save, with the fixture ID in the message. Clearing space does not reset a previously issued identity; use a different QA character for a fresh full-suite pass.

For connected QA, enable `/rpg geartrace on`, mark each experiment, and turn it off before leaving. Kill common, specialist, and elite enemies to inspect opportunity and world-drop records. Equip a weapon fixture, attack an enemy, and compare `ROOT_ATTACK_POWER` before/after values; non-attack affixes are explicitly marked unapplied to that leaf. Open Advanced Stats before/after equipping armor and inspect `ADVANCED_STAT` rows; a displayed string alone does not establish gameplay effect. For Iron Sentinel, use an eligible iron source item and inspect `IRON_SENTINEL_BIND`; a rejected or owner-only affix is not inherited. Connected drop, pickup, attack, stat, and Sentinel results remain unverified until the user's in-game QA trace is captured.
