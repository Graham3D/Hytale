# R258 diagnostic command inventory

This register covers currently registered Hywind commands. Historical reports
retain their original syntax as evidence of earlier builds. The maintained
operator syntax is in [COMMANDS.md](COMMANDS.md). No gameplay command, recorder,
serializer, historical evidence file or save was removed.

| Command or owner | Decision | Reason and replacement |
| --- | --- | --- |
| `/rpg uitrace on|mark|off` and `RpgUiTraceCommand` | MIGRATE, RETIRE wrapper | The same `UiInteractionTrace` now serves `/rpg-trace ui start|mark|stop|status`; native CanvasUI and Inventory hooks remain. Scoped Inventory and Skill Tree sessions have independent files. |
| `/rpg geartrace on|mark|status|off` and `RpgGearTraceCommand` | MIGRATE, RETIRE wrapper | `GearQaTrace` remains installed under `/rpg-trace gear`. Its fixture-source and combat receipts are preserved. |
| `/rpg sentineltrace on|snapshot|off` and `RpgSentinelTraceCommand` | MIGRATE, RETIRE wrapper | The separate Sentinel `GearQaTrace` and `HytaleSummonSystem.qaSnapshot` remain under `/rpg-trace sentinel`. `/rpg iron-sentinel` remains a QA grant. |
| `/rpg spawntrace start|stop|status` and `RpgSpawnTraceCommand` | MIGRATE, RETIRE wrapper | The global `MonsterSpawnTrace` and density readout remain under `/rpg-trace spawn`. |
| `/rpg tabtrace` and `RpgTabTraceCommand` | MIGRATE, RETIRE wrapper | `TabTraceProbe` remains under `/rpg-trace inventory-tab`; explicit stop saves partial evidence. |
| `/rpg nativedroptrace` and `RpgNativeDropTraceCommand` | MIGRATE, RETIRE wrapper | `NativeInventoryDropTraceProbe` remains under `/rpg-trace inventory-drop`; explicit stop restores page substitution and saves partial evidence. |
| `/rpg-trace normal|detailed|performance` | MIGRATE | `/rpg-trace skill start [detailed|performance]` creates a bounded personal file. The pre-existing configured global skill telemetry remains separate and is reported honestly in status. |
| `/rpg geartrace light|lightprobe|lightcal` | MIGRATE | WA-155 readout, temporary native light projection and curve fitting are now `/rpg lightqa light|lightprobe|lightcal`, still gated by `inigmasgames.rpg.gear.author`. These are calibration fixtures, not observational trace actions. |
| `/rpg inventoryprobe` | GATED FIXTURE | Registered only with the operator probe marker. It owns Inventory entry redirection, isolated drag surfaces and migration preflight; these are not passive tracing and lack equivalent acceptance coverage in the unified recorder. |
| `/canvasui-input-probe` | KEEP | Opens a labeled CustomUI input capability page. The generic UI trace does not replace that interactive control and its historical development contract remains. |
| `/canvasui-cursor-probe`, `-nohud`, `-page`, `-drag-proof`, and `/canvasui-cursor-probe-close` | KEEP | The primary command writes display-size cursor calibration used by Skill Tree; the comparison contexts and two-node drag proof remain relevant to input calibration. Emergency close is retained. |
| `/rpg-heal-probe` | GATED FIXTURE | Explicit property/live-test build gate and healing-probe permission; some modes create temporary visual carriers or recipient effects. The channel observer is distinct from the normal skill trace. |
| `/rpg-native-spawn-audit` | GATED FIXTURE | Registered only under an explicit native audit property and refuses any world outside its isolated root or with connected players. It mutates an isolated test world and is not a live trace. |
| `/rpg spatialqa`, native Inventory migration commands | GATED FIXTURE | Existing copied-save markers and gear-author permission protect state-changing migration tests. Not moved to `/rpg-trace`. |
| `/rpg difficulty inspect`, `/rpg enemies inspect`, `/rpg stats`, `/rpg skilltree` | KEEP | Gameplay or authoritative inspections, not redundant trace wrappers. |

No experimental probe outside the retired trace wrappers had both a verified
replacement and no remaining calibration, fixture or test purpose. Their
registrations therefore remain. Native Inventory client-packet gaps and
connected Skill Tree behavior still require owner QA; offline tests do not
claim otherwise.
