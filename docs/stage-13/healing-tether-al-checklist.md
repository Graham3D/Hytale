# R032-AL — test the actual Healing Beam

1. Restart Hytale and enter your normal RPG world. Confirm **R032-AL** in the top-right badge. If it shows AI/AK, stop and report that before testing.
2. Use `/rpg skilltree` to equip Healing Beam. Hold a supported staff/spellbook, have Mana available, and aim at a nearby friendly living ally/NPC (not yourself).
3. Hold the equipped native ability key to channel Healing Beam normally. **No probe command is required to select the new renderer.** `/rpg-heal-probe beam none` is a retained standalone control, not the production skill test.
4. Observe at roughly 2, 6, 12 and 18 metres: stationary endpoints should have a narrow, straight, continuous green stream ending at the target, with flow toward the target.
5. Strafe, move forward/back, change elevation and reverse direction. Stop and check that temporary lag settles back to a straight line. Repeat with a moving ally and with both moving. The core uses the caster's authoritative anchor, not a proven exact animated staff tip.
6. Hold a long channel with enough Mana; check for disappearance/refresh flashes. Test a full-health ally too: recipient/staff effects should remain while channeling, but zero effective healing is valid.
7. Test Arc/Fork/Chain if equipped: each connection should follow its own endpoints, with no inherited bend from another branch.
8. Release the key, move out of range, lose the target or leave the world. Check that no green strand or staff/recipient effect remains. Staff cosmetics should not begin merely from holding the staff.

Optional existing trace capture: run `/rpg-heal-probe channel none`, then cast normally during its 30-second observation window. It observes; it does not enable another beam version, waive Mana or cast for you. Report the time and visible result. Active skill trace remains under `Saves/RPG/mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl`.

The build's local/native checks are not connected visual approval. Record what you actually see, including thickness, direction, continuity and cleanup.
