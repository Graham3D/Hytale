# R023 — One native Rune control, not an RPG casting fix

Restart the RPG world and rejoin on R023. Leave Quick Slash and Fire Bolt equipped
in the RPG loadout. Use the configured native Ability2/Ability3 keys (E/R if unchanged).

1. Stand somewhere safe and aim at empty ground away from players, NPCs and builds.
   The control uses a real vanilla Fireball, including its damage, 25 Mana cost and
   12-second native cooldown. Have at least 25 available Mana for each press.
2. Run `/rpg dev rune-control start`. It must confirm shipped Fireball in **both**
   native slots. It refuses foreign primary runes, support runes, or a pending recovery.
3. Press Ability2 **once**. Note whether a vanilla fireball actually appears.
4. Wait at least **13 seconds**, and for at least **25 Mana**, then press Ability3
   **once**. Again note whether a vanilla fireball actually appears.
5. Run `/rpg dev rune-control stop` and confirm the original RPG native slots are restored.
   Send the two visual outcomes and ask for the latest logs/trace to be reviewed.

Do not edit the loadout, inventory or rune slots during the trial. The trial times
out after 120 seconds. `/rpg dev rune-control status` prints and logs the current
counters if needed. Default command permissions apply; the saved owner has the native Admin group.

RPG execution is deliberately suppressed for the trial and for 15 seconds after
restoration. **Both controls should test vanilla Fireball**, not Quick Slash or
the RPG Fire Bolt. RPG skill activation/executor events during this interval are
a failed safety gate, not evidence of a successful correction.

Logs are automatic:

- `Saves/RPG/mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl`
- latest `Saves/RPG/logs/*_server.log`

The new `NATIVE_RUNE_CONTROL_START`, `..._PACKET`, `..._SUMMARY` and `..._RESTORE`
events share a trial correlation ID. Existing `NATIVE_ABILITY_INPUT_OBSERVED`
events retain their per-chain ID and say `NATIVE_CONTROL_RPG_SUPPRESSED` when mapped.

If interrupted, R023 keeps a durable slot journal and restores on the next world
ready/tick. If restoration reports BLOCKED, stop testing and retain that journal;
it will not overwrite a slot changed by another actor. Do not roll back the plugin
until restoration succeeds and no native-control journal remains.

## Interpretation — do not jump from silence to a fix

| Observed control result | Next boundary |
|---|---|
| Vanilla casts; watcher receives initial Ability2/3 | Watcher is viable for this control. Compare the shipped ItemAbility/root path against RPG; do not blindly copy its gameplay branches. |
| Vanilla casts; raw Ability2/3 arrives but initial/mapping gate rejects it | The watcher sees traffic; the adapter's assumptions are wrong. Investigate observed type/state/root before changing integration. |
| Vanilla casts; watcher sees other packets but no Ability2/3 | Current packet architecture is unsuitable for this native path. Investigate AbilitiesPlugin/InteractionManager execution-side integration; do not change executors. |
| No vanilla cast, or no watcher traffic at all | Inconclusive. Verify binding, Mana, native cooldown, slot acceptance and instrumentation before choosing either explanation. |

For every outcome, Stage 04/05 executors and Stage 06 remain out of scope.
