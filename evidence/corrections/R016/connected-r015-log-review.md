# Connected R015 log review used by R016

Reviewed artifacts:

| Artifact | SHA-256 |
|---|---|
| `2026-09-06_20-03-27_server.log` | `4EB0A54043C222A8F6A1751B6D06D38DA0A98A4584ADA78E8FFF0FB23E9F7277` |
| `skill-trace.jsonl` | `47E2362A54A9973D52114E0A8CDA2A834E0EF7FA3FDDED39569B3ACE7BF6F843` |
| `ui-trace.jsonl` | `306F1A5C35D9B9065337339ABA518FCB335AFA4CE0D555052ABC9B390C22FD18` |

The server advertised R015 with schema 2 and the obsolete
`abilityInput=Ability1..Ability4` mapping. The session executed or exposed:

- Adventure/Creative gamemode changes;
- `/rpg loadout` and `/rpg stats`;
- Skill equip/unequip operations for `skill01` and `skill02`;
- `/rpg dev points grant 5` and `/rpg character`;
- `/rpg dev xp-display 0`, `9.9`, `10`, `50`, `99.9`, `100`, and `clear`;
- a Polar Bear spawn, time changes/pause, and an orderly server stop.

Skill trace events in the reviewed connected session:

| Event | Count |
|---|---:|
| `ATTRIBUTE_SNAPSHOT` | 2 |
| `COMPILE_BEGIN` | 28 |
| `COMPILE_FAILURE` | 2 |
| `COMPILE_STAGE` | 405 |
| `COMPILE_SUCCESS` | 26 |
| `DERIVED_STATS` | 2 |
| `EQUIP_SKILL_REQUEST` | 6 |
| `EQUIP_SKILL_COMMITTED` | 4 |
| `UNEQUIP_SKILL_REQUEST` | 4 |
| `UNEQUIP_SKILL_COMMITTED` | 4 |
| `LOAD` | 1 |
| `SAVE` | 14 |

There were zero activation, executor, strike, projectile, or damage events. The
session therefore did not test the connected Stage 04/05 activation boundary and
cannot establish success or failure for those systems.

UI trace evidence:

| Event | Count |
|---|---:|
| `HUD_REFRESHED` | 207 |
| `SKILLBAR_REFRESH` | 8 |
| `XP_PROJECTED` | 7 |
| `ATTRIBUTE_ALLOCATE_REQUEST` | 5 |
| `ATTRIBUTE_ALLOCATE_COMMITTED` | 5 |
| `CHARACTER_REFRESHED` | 5 |

The XP projections were numerically correct: 0 was empty; 9.9 produced 0.99 of the
first pip; 10 produced one full pip; 50 produced five full pips; 99.9 produced nine
full plus 0.99; 100 produced ten full; clear restored zero for the current fixture
state. The owner reported that none of those fills were visible in-client.

Character allocation moved five pending/unspent points to zero (one Strength and four
Dexterity allocations), with derived projections updating and the notification
disappearing. The four-cell R015 custom skill strip was visible behind/under the
native hotbar. The normal server log also repeated literal unexpanded
`RPG_UI_TRACE revision={0}...` placeholders.

Conclusion: R015 connected QA successfully covered loadout/compiler, XP command
projection, and Character allocation. It exposed mapping, HUD placement, XP rendering,
and log-formatting defects, while leaving actual skill activation untested.
