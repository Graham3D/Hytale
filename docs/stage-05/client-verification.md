# Stage 05 R015 — Connected Client Verification

## Setup

1. Fully restart the `RPG` world and rejoin. Confirm the HUD badge is `R015`.
2. Run `/rpg loadout` and `/rpg stats`. Keep one ordinary damageable, non-boss NPC
   available and record its Health before each test.
3. Preserve the newest server/client logs and
   `Saves/RPG/mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl`.
   Every activation must keep one `rootCastId`, `skillInstanceId`, and
   `correlationId`; every projectile adds one stable `projectileInstanceId`.
4. Equip a test skill with `/rpg equip skill01 "Skill Name"`, confirm with
   `/rpg loadout`, then activate it with Ability1.

## Required checks

| # | Action | Required proof |
|---|---|---|
| 1 | Hold a Staff or Wand. Equip `Fire Bolt`; hit the NPC within 24 m. | Visible travel; exactly 8 Mana and one 1.4 s cooldown; one 0.95x Magic hit; actual Health loss; spawn -> native Gather/Filter/Apply/Inspect -> entity hit -> termination with continuous IDs. |
| 2 | Observe the same Fire Bolt hit for 4 s. | Burn applies only after Health loss; exactly four roughly 1 s `BURN_TICK` submissions at 0.10 snapshot power; no stock duplicate damage. |
| 3 | Equip `Frost Bolt` with Staff/Wand; hit a fresh valid NPC once. | Visible 22 m/s travel; 8 Mana; 1.5 s cooldown; one 0.85x Magic hit; exactly one Chill stack request/result. |
| 4 | Equip `Arcane Bolt` with Wand/Spellbook; hit once. | Visible 26 m/s travel to at most 26 m; 7 Mana; 1.2 s cooldown; one plain 0.90x Magic hit; no status/control payload. |
| 5 | Equip `Stone Bolt` with Staff/Wand; hit once. | Visible 17 m/s travel to at most 20 m; 8 Mana; 2.2 s cooldown; one 1.20x Magic hit; 1.5 m knockback requested/applied and visibly authoritative on an eligible target. |
| 6 | Count crude arrows. Equip `Quick Shot`, hold a Bow, and hit once; repeat with a Crossbow after cooldown. | Exactly 4 Stamina and one real `Weapon_Arrow_Crude` each cast; native 0.075 arrow bounds/behavior; Bow about 30 m/s, Crossbow about 40 m/s; one 0.80x Light hit each; no duplicate native damage. |
| 7 | Try Quick Shot with no crude arrow, then with the wrong weapon. Try each magic skill and Axe Toss with a wrong weapon. | `PROJECTILE_SPAWN_REJECTED` before resource, cooldown, ammo, or world effect; inventory is never minted; immediate valid retry works. |
| 8 | Equip `Axe Toss`, hold a Battleaxe, and hit once. | Visible 18 m/s axe representation to at most 20 m; exactly 8 Stamina and one 5 s cooldown; one 1.20x Heavy hit; equipped Battleaxe remains in inventory. |
| 9 | Fire Fire Bolt or Arcane Bolt into solid terrain, then across open space. | Terrain emits `PROJECTILE_TERRAIN_HIT` then `PROJECTILE_TERMINATED`; open flight emits `PROJECTILE_MAX_RANGE` or `PROJECTILE_EXPIRED` at its bound. No target damage and no carrier remains. |
| 10 | For a valid non-critical hit and a valid critical hit, inspect the trace and target Health. | Each has one Stage 02 Gather -> Filter -> Apply -> Inspect chain, records pre-mitigation and actual Health loss, and uses only the Stage 02 crit decision. |
| 11 | Attempt repeated contact if practical, disconnect during flight, and rejoin. | No target receives a duplicate hit from one projectile; owner teardown emits cancel/terminate; no orphan projectile or registry state affects the next cast. |
| 12 | Link Fork to Fire Bolt with `/rpg link passive06 skill01`, verify `/rpg loadout`, and cast once. | Compile remains PASS and Link metadata is preserved, but only one generation-zero projectile spawns. No Fork child appears before Stage 07. |
| 13 | Watch Stage 03 HUD while casting, then disconnect/rejoin and fully restart/rejoin. Run `/rpg loadout` and `/rpg stats` again. | Resource/cooldown HUD updates match commits; all four slots and Link graph persist; no CustomUI/RPG exception; no projectile/status leak after either restart. |

For each failure, retain the relevant trace slice and record the earliest missing or
incorrect event. Do not mark Stage 05 `PASS` until every row succeeds. Do not begin
Stage 06 from this checklist.
