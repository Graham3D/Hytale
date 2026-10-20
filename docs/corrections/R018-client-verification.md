# R018 — Connected HUD Verification

Fully stop and restart the RPG world before this check. Do not begin Stage 06.

1. Rejoin the RPG world. World entry must complete without `Failed to load CustomUI
   documents` or another CustomUI exception.
2. Confirm the bottom-center stack is, from bottom to top: native hotbar, graphical
   `Health | Mana | Stamina`, then the continuous XP bar. The three resource cells
   must be equally scaled and spaced, centered, and non-overlapping.
3. Damage/heal the player and spend/regenerate Mana and Stamina. Confirm each
   graphical fill and numeric value follows the native authoritative value.
4. Confirm the native lower-right Signature Move remains visible and functional.
   There must be no replacement Signature cell and no upper-right RPG diagnostic.
5. Confirm three RPG cells appear immediately to the left of the native ability
   area and are labelled `Ability2`, `Ability3`, and `Ability4`.
6. Run `/rpg loadout`. Equip or clear `skill01`, `skill02`, and `skill03`, and confirm
   the corresponding cells update live as `Ability2`, `Ability3`, and `Ability4`.
7. Activate each equipped Skill. Confirm ready, cooldown, and unavailable/empty
   presentation changes without obscuring the native Signature or hotbar.
8. Run these presentation-only XP checks:

   ```text
   /rpg dev xp-display 0
   /rpg dev xp-display 9.9
   /rpg dev xp-display 10
   /rpg dev xp-display 50
   /rpg dev xp-display 99.9
   /rpg dev xp-display 100
   /rpg dev xp-display clear
   ```

   Confirm the fill grows smoothly from the left: empty, about 9.9%, 10%, half,
   about 99.9%, full, then the authoritative XP projection is restored. The frame
   and background must remain fixed.
9. Inspect `ui-trace.jsonl`. Confirm initial records include `HUD_LAYOUT_READY`,
   `RESOURCE_HUD_REFRESH`, `ABILITY_HUD_REFRESH`, one `ABILITY_SLOT_CHANGED` per RPG
   cell, and `XP_HUD_REFRESH`. Change one slot and confirm only meaningful transition
   records appear; there must be no periodic `HUD_REFRESHED` spam.
10. Fully restart/rejoin once more. Confirm the same layout and saved three-slot
    loadout return and no CustomUI exception appears.

For a failure, retain the newest client/server logs and `ui-trace.jsonl`, and record
the exact step plus the earliest incorrect or missing trace event.

