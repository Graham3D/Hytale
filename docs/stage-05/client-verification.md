# Stage 05 R014 — Connected Client Verification

## Setup

1. Fully restart the `RPG` world and rejoin. Confirm the HUD badge is `R014`, then run
   `/rpg loadout` and `/rpg stats`.
2. Keep one ordinary damageable NPC available. Record its Health before each hit.
3. Watch the newest records in
   `Saves/RPG/mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl`.
   One activation must retain one `rootCastId`, `skillInstanceId`, and `correlationId`.

Use `/rpg equip skill01 "Fire Bolt"` or `/rpg equip skill01 "Snipe"`; confirm each
change with `/rpg loadout`, then activate skill01 with Ability1.

## Required checks

| # | Action | Required evidence |
|---|---|---|
| 1 | Hold a Staff or Wand, equip Fire Bolt, face a valid NPC within 24 m, and press Ability1 once. | One projectile at about 24 m/s; 8 Mana and one cooldown; exactly one target hit; actual Health loss; `PROJECTILE_SPAWNED` -> `PROJECTILE_HIT` -> native Gather/Filter/Apply/Inspect with continuous IDs. |
| 2 | Repeat Fire Bolt into solid terrain, then once into open space with no target. | Terrain contact records `PROJECTILE_TERRAIN_IMPACT`; open flight records `PROJECTILE_EXPIRED` at about 24 m/1.0 s. Neither path damages a target or leaves a carrier. |
| 3 | Review the valid Fire Bolt hit for four seconds. | Burn applies only after the damaging hit and emits exactly four `BURN_TICK` events about 1 s apart. Each tick uses coefficient 0.10, cannot crit, follows native Gather/Filter/Apply/Inspect, reduces Health, and retains the activation IDs. No separate stock-Burn damage appears. |
| 4 | Equip Snipe, hold a Bow, count crude arrows, and press Ability1 once at a valid NPC within 48 m. | Immediate fully charged-looking release at about 45 m/s; exactly 12 Stamina and one crude arrow consumed; exactly one 2.00x uncharged Weapon-Power hit and actual Health loss; one full native damage chain with continuous IDs. |
| 5 | Fire Snipe without an eligible arrow, then fire with one arrow into terrain/open space. | Missing ammo rejects before Stamina/cooldown/world effect. Terrain/open attempts consume exactly one arrow and one cost, terminate distinctly, and do not create an extra hit or retained carrier. |
| 6 | Search the Snipe root ID in the trace and compare resources before/after. | No `RESOURCE_RECOVERY` is attributed to Snipe; its fully charged metadata does not grant the 12% charged-basic recovery. Native passive regeneration may continue independently. |
| 7 | Try Fire Bolt with a non-Staff/Wand and Snipe with a non-Bow; immediately retry a successful cast during cooldown. | Each invalid attempt rejects before resource, cooldown, ammo, projectile, or damage. Cooldown retry consumes nothing more. |
| 8 | Recheck all four HUD slots, disconnect/rejoin, fully restart, rejoin again, and run `/rpg loadout` plus `/rpg stats`. | No RPG-scoped exception; no owned projectile/Burn leak; HUD remains healthy; Stage 01B loadout/graph and Stage 03 attributes/presentation persist. |

Retain the server/client logs, `skill-trace.jsonl`, before/after target Health and player
resources, arrow counts, `/rpg loadout`, `/rpg stats`, and screenshots/video of both
projectiles and terminal paths. Record the earliest failed event boundary exactly. Do
not mark Stage 05 `PASS` until every row succeeds.
