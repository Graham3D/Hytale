# R021 — Connected Native Ability Verification

Fully stop and restart the RPG world, then rejoin.

1. Confirm there is no CustomUI load error. Confirm the old three RPG-fabricated
   ability cells are gone, Hytale's native ability HUD is intact, Health/Mana/Stamina
   remain vanilla, and the R020 XP bar is unchanged.
2. Run:

   ```text
   /rpg equip skill01 quick_slash
   /rpg equip skill02 fire_bolt
   /rpg dev ability-status
   ```

   Expect `HudComponent.Abilities visible=true`, capacity 6, primary indices `0,3`,
   `skill01 -> PROJECTED ...RPG_Ability_Quick_Slash`, and
   `skill02 -> PROJECTED ...RPG_Ability_Fire_Bolt`. If a result is
   `NATIVE_SLOT_OCCUPIED`, use Hytale's Ability Bench to move that existing Rune to
   the Rune Bag, then rerun the command. Do not discard the Rune.
3. Confirm the native Ability2 and Ability3 cells show the two skill icons and native
   binding glyphs. Confirm the weapon Signature Move still works unchanged.
4. Equip a compatible sword, aim at a valid target, press the player's configured
   native Ability2 control once, and confirm Quick Slash executes once. Confirm one
   Stamina cost, one RPG cooldown, and no duplicated damage.
5. Equip a compatible Staff or Wand, aim at a valid target, press native Ability3
   once, and confirm one Fire Bolt projectile. Confirm one Mana cost, one RPG
   cooldown, one projectile, and one damage path.
6. Run:

   ```text
   /rpg unequip skill02
   /rpg dev ability-status
   ```

   Confirm native Ability3 clears while Ability2 remains projected. Re-equip Fire
   Bolt afterward if desired.
7. Equip any executable skill into `skill03`, then run the status command. Confirm
   the loadout retains it but reports `NATIVE_ABILITY4_UNAVAILABLE`; there must be no
   custom third cell or invented key binding.
8. Fully restart and rejoin once more. Confirm `skill01`/`skill02` project back into
   the native cells and repeat one activation.

Retain the newest server/client logs and
`mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl`. Connected
acceptance requires a single correlated sequence from
`NATIVE_ABILITY_INPUT_OBSERVED` through `SKILL_ACTIVATION_REQUEST`, validation,
commit, executor dispatch, and the appropriate strike/projectile/damage events.

Until this checklist passes, R021 remains
`DEPLOYED_AWAITING_CONNECTED_NATIVE_ABILITY_QA`; Stage 06 must not begin.
