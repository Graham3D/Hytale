# R032-AB owner correction

2026-09-12, superseding only the listed AA presentation/cooldown settings.

- Blizzard base duration remains 3 seconds; its authored cooldown changes from
  18 to 3 seconds and starts at paid commit/dispatch, not on expiry. A manual
  recast is rejected while its primary storm is active. Existing legal derived
  releases and modifier/durability contracts remain. Compiled lifetime and
  cooldown modifiers still apply; active lifetime is the minimum recast gate.
- Remove Blizzard's warning/debug geometry only. Preserve first-solid sweep,
  actual-contact impact position, 0.38 coefficient, 2 m impact, 6 m zone,
  0.75-second per-target root interval and Chill 1.
- Use shipped `Impact_Ice` at actual contact; preserve per-impact
  `SFX_Ice_Ball_Death`, no audio loop. Bound localized Snow_Heavy emissions by
  the active root lifetime. Existing native one-shot impact tails are not damage.
- AA connected QA disproved the +Z beam-flow assumption. Reverse presentation
  only, using native -Z flow and tangent-oriented samples of a sagging quadratic
  Bezier. Healing source, recipient, range, LOS and amounts remain unchanged.
- Owner now explicitly requests a read-only radial cooldown overlay on the two
  native E/R skill icons. This is a narrow presentation exception, not permission
  to replace icons, key bindings, Signature, AbilitySlots or native resource HUD.
- Deploy the cumulative test build after verified backup, per owner deployment
  policy. Local tests/native smoke never establish connected visual success.
