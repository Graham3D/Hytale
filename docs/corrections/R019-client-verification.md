# R019 — Connected HUD Verification

Fully stop and restart the RPG world before testing.

1. Rejoin successfully. Confirm there are no red missing-texture X placeholders and
   no `Failed to load CustomUI documents` error.
2. Confirm Health and Stamina look and behave exactly like Hytale's original native
   bars above the hotbar. They must not be duplicated or restyled.
3. Confirm one Mana bar appears centered between the native Health and Stamina bars.
   Spend and regenerate Mana; its fill must move left-to-right with the authoritative
   value.
4. Confirm the 931-pixel XP frame is centered over the hotbar, six pixels above the
   resource row. Its background must sit inside the frame.
5. Run:

   ```text
   /rpg dev xp-display 0
   /rpg dev xp-display 9.9
   /rpg dev xp-display 10
   /rpg dev xp-display 50
   /rpg dev xp-display 99.9
   /rpg dev xp-display 100
   /rpg dev xp-display clear
   ```

   Confirm the 22-pixel-high fill remains anchored to the background's left edge and
   expands smoothly to its exact full width, while the background and frame stay
   fixed.
6. Confirm the lower-right native Signature and RPG Ability2–4 cells have real
   graphics rather than red placeholders.
7. Fully restart/rejoin once more and confirm the layout and saved loadout return.

Retain the newest client/server logs and `ui-trace.jsonl`. For a failure, record the
exact step and the earliest incorrect trace event. Do not begin Stage 06.

