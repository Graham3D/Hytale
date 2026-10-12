# R261 connected-game acceptance checklist

This checklist is for the owner's test in the established
`Hytale/data/pre-release/Saves/RPG` save. With the verified R261QA1 JAR
installed, launch Hytale normally and enter that world. Durable Cyclone Armor
is enabled by default in the production entrypoint. No JVM property or startup
capture is required. Connected-game acceptance remains with the owner.

## Optional ReadyPath startup capture

ReadyPath/JFR is available only if startup profiling is useful. With Hytale
and its launcher closed, the optional capture command is:

```powershell
$capture = 'C:\Users\Zemio\.codex\worktrees\combat-damage-trace\Hytale-main-clean\tools\Capture-ReadyPathStartup.ps1'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $capture -EnableDurableCyclone
```

When it prints `ARMED`, launch Hytale normally and enter the existing RPG
save. Leave PowerShell open until capture completes. The flag requests a gate
confirmation in the matched integrated-server log; the normal build already
enables the gate without that flag. The script temporarily sets
`JAVA_TOOL_OPTIONS` for ReadyPath/JFR and explicitly requests
`-Dhywind.cyclone.durable=true` during this optional capture. It removes the
user-level setting after matching the integrated server or on failure. It
does not launch the game.
The captured `server.log` must contain exactly one
`RPG_CYCLONE_DURABLE_GATE enabled=true` line. The script prints
`CYCLONE GATE CONFIRMED` and records `cycloneGateConfirmed: true` in
`manifest.json`; a missing, false, or ambiguous marker fails the QA capture.
The recording and log snapshots are under
`Hytale/data/pre-release/StartupCaptures`, outside `Saves`. If PowerShell
closes unexpectedly before cleanup, rerun the script with `-Restore`.
Repeat the arming command for each separate profiling launch. Ordinary play
needs no arming. Connected gameplay acceptance still requires the checks below.

1. Use `/rpg skilllevel spirit_shield 1` on a learned Cyclone Armor. Cast
   with at least 100 spendable Mana. Confirm the entire unreserved Mana amount
   is spent once, the cooldown starts once, and the Barrier HUD begins at the
   frozen level 1 capacity. Check the persistent visual and no countdown.
2. Take several ordinary and large hits. Confirm Barrier HP falls by actual
   post-mitigation absorption, overflow continues through finite shields,
   Managuard/Mana Before Life, then native Health. With `GA-161`, verify the
   displayed Mana debit and Health protection agree. Deplete the barrier,
   then recast only when cooldown permits.
3. Set `/rpg skilllevel spirit_shield 20`; verify the level 20 base curve,
   effective WIS/INT and equipment modifiers, and that gear or Link Tree
   changes after casting do not resize an active generation.
4. Link Reflective Ward and cast. Have a hostile actor hit the barrier.
   Confirm reflection uses 20% of confirmed absorption, never a duplicate or
   environmental/non-hostile hit. Disconnect and reconnect with Barrier HP
   remaining, then remove/add the link and confirm the old cast keeps its
   frozen behavior. Repeat across an integrated-server restart.
5. For Shared Aegis, two players explicitly establish trust with
   `/rpg trusted request <playerUuid>` and `/rpg trusted accept <playerUuid>`.
   Cast near a trusted player and a Friendly/Revered NPC. The nearest eligible
   recipient within 8 m and clear line of sight receives one child with half
   the caster's frozen capacity. A wall, >8 m distance, neutral NPC, or player
   without mutual consent must not become a recipient. Verify the child cannot
   recursively grant another child.
6. Damage the recipient, inspect the Barrier HUD and overflow, then disconnect
   recipient first and caster first in separate runs. Repeat with the caster
   offline and after an integrated-server restart. Previously absorbed child
   HP must not return. Revoke trust with `/rpg trusted revoke <playerUuid>`;
   the old child must stop protecting and must not revive after renewed consent.
   Test caster death, recipient death, parent depletion, parent recast, and
   recipient world transition.
7. During repeated combat, watch for unusual server tick pauses and confirm
   the existing skills, passives, summons, inventory/equipment, and world
   difficulty still operate. Capture the normal skill and combat traces only
   if a discrepancy occurs; the acceptance decision remains with the owner.
