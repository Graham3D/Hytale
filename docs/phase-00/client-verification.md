# Stage 00 real-client verification — R001

Target: Hytale `0.7.0-pre.1` / `e8b4d191fc98a977bf5546a951a7b25473d323e3`  
Probe source commit: `eaeb93d84565f3ac4677c47c4463d66fbc51fc51`  
Installed jar SHA-256: `6D928E821CDA28ACA8B9E31FB192F319FEC10CC9A1078CFA087AC3EED8E8752F`

The audit jar is installed only in the isolated pre-release `RPG` save. R001
must remain visible at the top of the game UI in every screenshot. Stop at the
first failure and keep the newest save log plus a screenshot; do not edit the
client settings file to manufacture a pass.

| # | Check | Command/action | Passing evidence | Result |
|---:|---|---|---|---|
| 1 | Load/revision | Open pre-release `RPG` | Both plugins enable; `R001` appears at top and persists after pages/HUD clear. | PENDING |
| 2 | Exact API | `/rpgp00-capabilities` | Chat lists `Ability1`, `Ability2`, `Ability3`, and `Ability4`. | PENDING |
| 3 | Native authority | `/rpgp00-stats` | Health/Mana/Stamina direct reads and HTDevLib reads agree without mutation. | PENDING |
| 4 | HTDevLib runtime | `/rpgp00-htdevlib` | `Effect_Heal` visibly plays and log/chat contains `PHASE00_HTDEVLIB_PASS`, with no helper warning/exception. | PENDING |
| 5 | Character lifecycle | `/rpgp00-character`; Escape; reopen; repeat 10 times | Standalone page shows player/native stats, dismisses cleanly, and log has balanced OPEN/DISMISS markers. | PENDING |
| 6 | Low-level mouse | `/rpgp00-mouse-probe`; hold left and move; release | PRESS/MOVE/RELEASE counters/logs advance, held continuity appears, node follows smoothly, and measured event rate is recorded. | PENDING |
| 7 | Canvas pan | On same page, hold middle and move; release | Canvas/node/connection pan together; no zoom operation exists; preview remains responsive at capped 10 Hz. | PENDING |
| 8 | Connection fallback | Drag then pan | Three-segment connection remains attached to ROOT and DRAG NODE. | PENDING |
| 9 | Position persistence | Release, Escape, reopen mouse probe | Node and pan coordinates restore from the audit layout file. | PENDING |
| 10 | Ability paths | Run `/rpgp00-ability-inputs` to reset; exercise current Ability1–4 bindings; rerun command | Non-zero independent counters/log markers for all four types, or an exact first failure is recorded. | PENDING |
| 11 | HUD replacement | `/rpgp00-hud` | Mana / Health / Stamina order, ten-segment XP sketch, four ability cells, and level notice render; native three stat bars hide. | PENDING |
| 12 | HUD restore | `/rpgp00-hud-clear` | Audit HUD disappears, exact native visibility snapshot returns, and R001 remains. | PENDING |
| 13 | C ownership/context | Inspect Settings → Controls; then test C in gameplay and each custom page | Exact default C owner and contexts are recorded. No automatic settings edit. | PENDING |
| 14 | K ownership/context | Inspect Settings → Controls; then test K in gameplay and each custom page | Exact default K owner/conflicts and contexts are recorded. | PENDING |
| 15 | Tab/native inventory | Press Tab before/during/after probes | Native Inventory remains intact; no unsupported docking is claimed. | PENDING |
| 16 | Reconnect/leaks | Disconnect/rejoin; repeat Character, mouse, and HUD tests | R001 returns, commands work, no stale custom HUD/page/session or exceptions. | PENDING |

Capture PNGs under `evidence/phase-00/client/R001-eaeb93d-0.7.0-pre.1/` with
descriptive names. Copy the newest pre-release `RPG/logs/*.log` into the same
directory. The report must state exact average/peak mouse event rate and any
observable press-to-preview latency; the packet client timestamp is retained in
the log but its time unit is not assumed.

At completion run `/rpgp00-hud-clear`, exit the save, and use
`tools/Uninstall-Phase00Probe.ps1` if the audit probe is no longer needed.
