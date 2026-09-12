# R032-AK — capture the real Healing Beam

Use your normal RPG single-player world. No separate server or authentication. You do not need to repeat the eight successful controls.

1. Equip Healing Beam and a supported staff. Stand within range of the same permitted NPC you have been healing; full Health is fine. Ensure sufficient Mana.
2. Enter:

   ```text
   /rpg-heal-probe channel none
   ```

3. Wait for **R032-AK ARMED for 30s**. The command itself spawns nothing and casts nothing.
4. Immediately use the normal Healing Beam hotkey on the NPC. Hold for about five seconds, strafe briefly, then release. Note separately whether you see the stream, recipient crosses and staff sparkle. Record a short video if possible.
5. Cast once more within the same window if Mana permits. Release and wait until the 30-second observer ends, plus two seconds for its summary.
6. Tell Codex the visible result. It will compare the latest normal RPG server/client logs and `mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl`, including rotated archives.

Optional early stop:

```text
/rpg-heal-probe stop none
```

Stopping observation does not end an active Healing Beam. Release the hotkey normally. If you see no ARMED message, report the command response; do not interpret a command/setup failure as a rendering result.

This build instruments the actual failing path; it does not yet replace or fix the production beam. Normal Mana costs and healing rules apply during these real casts. Server receipts alone will not be treated as proof you saw the visuals.
