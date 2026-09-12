# R032-AJ isolated Healing presentation probe

NOT DEPLOYED TO LIVE. This is a harness correction, not a Healing Beam rendering fix.

## Start a new flat fixture

Stop any older probe with `stop` in its server console. Do not reuse the failed AI world or its old launch command. In PowerShell:

```powershell
Set-Location -LiteralPath 'C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale'
.\tools\New-HealingProbeAI.ps1 -Start
```

The helper retains its familiar filename but now selects the **AJ** package. It prints a new `run/healing-probe-aj-<id>` directory and uses authenticated Direct Connect on **127.0.0.1:5591**. It does not install anything in Saves/RPG/mods or copy live gameplay data/credentials. The native flat world has a grass floor ending at Y=64 and an initial player spawn at **8.5,64,8.5**. Automatic ambient NPC/block spawns are disabled only in this fresh diagnostic fixture, not in production.

In the running **server console**, enter `auth login device`, complete the displayed browser device authorization, then `auth status`. Device codes come from the server console, not email. Keep credentials private. Join through Hytale Direct Connect only once authentication succeeds.

## Controls

Stay near the initial spawn. Look horizontally across the clear floor before starting a core control. Run **one command at a time and wait at least 12 seconds** before the next. Watch for **STARTED** or **FAILED** in chat; REQUESTED alone is not success.

| Command | What to watch |
|---|---|
| `/rpg-heal-probe world none` | Exact stock world particle; no NPC required. |
| `/rpg-heal-probe empty none` | Particle on the existing empty-model carrier; no NPC required. |
| `/rpg-heal-probe visible none` | Same particle on a visible carrier; distinguish the model from its particles. |
| `/rpg-heal-probe beam none` | Existing AF native Beam, geometry-only comparison; no NPC required. |
| `/rpg-heal-probe recipient-once native` | Health-pack effect applied once to a temporary native mannequin at **8.5,64,2.5**. |
| `/rpg-heal-probe recipient-overwrite native` | Same recipient effect refreshed using the existing overwrite cadence. |
| `/rpg-heal-probe staff-once none` | Hold an audited vanilla staff (e.g. Bronze); observe the staff effect applied once. |
| `/rpg-heal-probe staff-overwrite none` | Hold the same staff; compare refreshed effect behavior. |

For recipient controls, turn toward the mannequin six metres toward world -Z from the initial player spawn. For staff controls, obtain/hold a staff using the normal inventory/admin tools, then inspect both first and third person. There is no need to equip or cast Healing Beam: this probe changes no Health, Mana, cooldown or skill state.

Standalone core controls capture a world endpoint six metres along your **initial aim**; the carrier source follows your actor anchor on subsequent updates. This is not a proposed production tether. Stock particle controls still have the existing directional-emitter length limitation. The Beam comparison intentionally retains the existing material; it is not a new approved appearance.

The legacy `native` argument is also accepted by standalone modes but **does not spawn an NPC**. For recipient modes only, an existing runtime entity UUID may replace `native`; that borrowed entity is not removed. A profile ID is not a runtime UUID. New native fixtures require the flat launcher world and unchanged floor/clearance; an edited or old world is rejected explicitly, never silently treated as a rendering result.

Early stop: `/rpg-heal-probe stop none`. Entity-attached effects/carriers and owned recipient are removed through the deferred world mutation boundary. The standalone world particle has only its native finite lifetime/tail, not an audited instance-specific cancellation handle. Do not interpret that documented tail as an orphan entity.

## Record and return

- Each mode, approximate time, STARTED/FAILED, visibility, blinking, movement and residue after cleanup.
- A short video plus the matching client log if an effect remains absent.
- The printed disposable run path; server log and `mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl` (and rotated segments if any).
- AJ is identified by **R032-AJ command/startup messages** and trace `cohort=AJ`. The normal HUD badge was deliberately not changed by this probe-only correction.

Server creation, viewer queues and outbound observer receipts narrow the boundary but **do not prove client rendering**. A failed fixture setup is a harness failure. Do not deploy this diagnostic package into the live RPG save.

Only after the three-mod controls, optionally create another fresh fixture with `-NpcModPath 'C:\path\ImmersiveNPCs-<version>.jar'`. Repeat only recipient modes against that mod's visible runtime NPC UUID. Keep this four-mod comparison separate from the three-mod baseline.
