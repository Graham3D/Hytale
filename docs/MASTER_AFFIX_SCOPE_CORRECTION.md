# Owner scope correction — affix runtime only

The owner's explicit 2026-09-30 correction overrides stale skill-specific assumptions in Master Affix and Gear Master.

- Existing implemented base skills and values are authoritative. Do not create, redesign, rebalance, remap acquisition for, or otherwise modify base skills to fit an affix.
- Generic affix modifier/receipt hooks in existing skill, combat, status, resource and summon owners are allowed. Base cost, damage, targeting, cooldown, duration, acquisition and mechanics remain unchanged.
- Do not create Ameliorate or assign a new cleanse skill to an enemy. WA-112 consumes a canonical successful-cleanse runtime/event from any existing or future cleanse source, proven through the simulator. It never performs an additional cleanse.
- Simulacrum retains its existing finite 20-Mana cost. WA-120 discounts that existing cost normally. The earlier instruction to drain all current Mana is superseded.
- Complete all 160 affixes within this boundary. Stale references to particular absent skills do not authorize creating them.

Offline compilation, unit tests and asset validation only. No game/server/auth launch. Main owns deployment and final capability qualification.

## WA-155 renderer calibration decision

The owner chose to keep metres and perform renderer calibration during connected QA. Do not substitute native light units or guess a metre conversion. The offline native component/packet adapter and its measured-input calculation are testable now; the rendered-distance acceptance remains pending. Keep the uncalibrated production roll gated. Include the bounded `geartrace lightprobe` and read-only `geartrace lightcal` commands in the QA build, then install only an owner-measured calibration in a later build.
