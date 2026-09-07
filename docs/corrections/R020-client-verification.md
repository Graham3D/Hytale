# R020 — Connected HUD Verification

Fully stop and restart the RPG world before testing.

1. Rejoin successfully. Confirm there is no CustomUI load error or missing-texture
   placeholder.
2. Confirm Health, Mana, and Stamina are entirely vanilla: no duplicates, overlays,
   repositioning, resizing, or restyling. Spend Stamina and confirm its native
   temporary visibility behavior remains intact; spend Mana and confirm its native
   behavior remains intact.
3. Confirm the XP frame is centered from the left edge of hotbar slot 1 to the right
   edge of slot 9 and remains above the native resource area.
4. Run:

   ```text
   /rpg dev xp-display 0
   /rpg dev xp-display 9.9
   /rpg dev xp-display 10
   /rpg dev xp-display 50
   /rpg dev xp-display 99.9
   /rpg dev xp-display 100
   /rpg dev xp-display clear
   ```

   Confirm the frame/background stay fixed, the fill remains 22 pixels high and
   left-anchored, and 100% aligns exactly with the background's right edge.
5. Confirm the native Signature and RPG Ability2–4 cells remain present and usable.
6. Fully restart/rejoin once more. Confirm the HUD and saved loadout return, then
   retain the newest client/server logs and `ui-trace.jsonl`.

Until this checklist passes, R020 remains `DEPLOYED_AWAITING_CONNECTED_HUD_QA`.
