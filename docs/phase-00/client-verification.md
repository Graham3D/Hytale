# Stage 00 real-client verification

The temporary probe jar is named `HytaleRPG-Phase00-Audit-0.0.1.jar` and is
installed only in `UserData/Saves/RPG/mods`. Start the `RPG` save in the normal
release client, then perform these checks in order. Do not continue after the
first failure; preserve the client/server log and a screenshot.

| Check | Command/action | Expected evidence |
|---|---|---|
| Load | Open `RPG` | Server log contains `Enabled plugin InigmasGames:HytaleRPGPhase00Audit` and `Enabled plugin HytaleDevLib:HytaleDevLib`. |
| Exact enums | `/rpgp00-capabilities` | Chat lists Ability1–3, no Ability4, the current UI event types, HUD components, and page enum. |
| Native stats + HTDevLib | `/rpgp00-stats` | A native `Health/Mana/Stamina` line and an HTDevLib comparison line appear without changing values. |
| Character page | `/rpgp00-character` | A standalone Phase 00 panel renders; Escape dismisses it; reopening works. Capture open and closed states. |
| Link page | `/rpgp00-link` | Fixed canvas and node render. Each button updates the status label. Capture the page and an updated label. |
| Link pointer behavior | Try left/middle drag and Space+left drag | Record actual behavior. The implementation currently makes no drag/pan/pointer-capture claim. |
| Page-local key | Focus the Link page and press keys | Record whether `KeyDown` reaches the status label. This does not establish global C/K binding. |
| HUD replacement | `/rpgp00-hud` | Probe HUD appears and native Health/Mana/Stamina are hidden. Capture it. |
| HUD restoration | `/rpgp00-hud-clear` | Probe HUD disappears and the exact original native HUD set returns. Capture it. |
| C ownership | With no page open, press C; then repeat with each page open | Record camera and page behavior. Do not edit `Settings.json`. |
| K ownership | With no page open, press K | Record behavior. No global K handler is installed by this probe. |
| Ability inputs | Exercise current Ability1/2/3 bindings | Record emitted behavior/logs. Ability4 cannot be tested because the installed enum has no Ability4. |
| Tab Inventory | Press Tab before/during/after probes | Native Inventory must remain intact. No docking claim is made. |
| Reconnect | Disconnect/rejoin, then repeat page and HUD checks | No stale page/HUD state; commands continue to work. |

At the end, always run `/rpgp00-hud-clear`, exit the save, and run
`tools/Uninstall-Phase00Probe.ps1` if the probe is no longer needed.

