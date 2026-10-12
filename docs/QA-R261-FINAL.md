# R261 connected-game acceptance checklist

This checklist is for the owner's later test after a separately authorized
deployment to the established `Hytale/data/pre-release/Saves/RPG` save. The
offline release candidate does not replace the active R260 `Hywind.jar` and
does not establish connected-game acceptance. The durable path requires the
documented `-Dhywind.cyclone.durable=true` startup property.

## First connected launch with startup capture

The verified R261 mainline artifact is
`C:\Users\Zemio\.codex\worktrees\combat-damage-trace\Hytale-main-clean\build\release\Hywind.jar`,
built from source commit `e42b903291409bde99fde985e8e07174cbb92401` at
revision `R261-U7P5B`. Its SHA-256 is
`766E38665C3DAC4133B8F8606A7A4E8BC17894B91D56DD9D859E4D222499CF6A`.
The active RPG save still carries the R260 JAR. Deployment requires approval:
with Hytale closed, back up that JAR outside `Saves`, replace only
`Saves/RPG/mods/Hywind.jar` with this artifact, and verify the destination
checksum and embedded source commit before the first launch. Do not edit
player-save data or launch Hytale as part of deployment.

After the separately approved R261 `Hywind.jar` deployment, close Hytale and
its launcher, then run this in PowerShell:

```powershell
$capture = 'C:\Users\Zemio\.codex\worktrees\combat-damage-trace\Hytale-main-clean\tools\Capture-ReadyPathStartup.ps1'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $capture -CheckOnly -EnableDurableCyclone
powershell.exe -NoProfile -ExecutionPolicy Bypass -File $capture -EnableDurableCyclone
```

When the second command prints `ARMED`, launch Hytale normally and enter the
existing RPG save. Leave PowerShell open until capture completes. The script
sets `JAVA_TOOL_OPTIONS` temporarily with both the ReadyPath JFR options and
`-Dhywind.cyclone.durable=true`; it removes the user-level setting after it
matches the integrated server, or on failure. It does not launch the game.
The captured `server.log` must contain exactly one
`RPG_CYCLONE_DURABLE_GATE enabled=true` line. The script prints
`CYCLONE GATE CONFIRMED` and records `cycloneGateConfirmed: true` in
`manifest.json`; a missing, false, or ambiguous marker fails the QA capture.
The recording and log snapshots are under
`Hytale/data/pre-release/StartupCaptures`, outside `Saves`. If PowerShell
closes unexpectedly before cleanup, rerun the script with `-Restore`.
Connected gameplay acceptance still requires the checks below.

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
