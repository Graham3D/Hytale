# R197 inventory presentation correction

## Changes

- Character Stats shows `<balance> points remaining` on the level row, including zero. XP remains visible on the next line. Attribute `+` controls are hidden and disabled when there is no authoritative allocation balance/service. Existing allocation, refund, revision and persistence rules are unchanged.
- Both tooltip hosts now overlay one centered `TooltipWorkspace`, matching the inventory workspace's 1164-unit width and 16-unit outer inset. R196 incorrectly put two full-width children directly in `LayoutMode: Center`, causing them to participate in horizontal layout and shifting the primary tooltip offscreen. Existing full-footprint item bounds, placement/clamping, text, colors and counterpart renderer remain unchanged.
- The native outside-drop ItemGrid retains its section, slot identity and activatable state. Its existing fully transparent PNG is now also its `MaskTexturePath`, suppressing the native cell's hover painting without replacing native drop handling. The property is part of Hypixel's [ItemGrid contract](https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/elements/itemgrid).
- Compare button, binding and toggle state removed. Paired tooltip renderer retained. Alt-hover is **not implemented**: installed CustomUI exposes no held-Alt state or KeyUp callback; native MouseButtonEvent/MouseMotionEvent also carry no keyboard modifiers. No guessed property, global keyboard hook or click substitute was introduced. The server-side custom tooltip cannot truthfully determine whether Alt is held from these events.
- Right-click equip/unequip, stock stack quantities, airborne native drops and existing inventory transfers are preserved.

## Why this character currently has zero points

The active RPG server log `2026-10-02_10-26-12_server.log` records `/rpg dev reset` at 14:33:55 UTC, then `/rpg level reset` at 14:34:06 UTC. The archived R195 command invokes `resetDevelopmentAttributes`, which overwrote every raw attribute with 10 without refunding the removed allocation. R196 removed that command.

The immediate pre-R196-reset player `.json.bak` (10:34:06 local) has level 99, all five attributes 10, and zero unspent points. The latest log records the R196 `/rpg level reset` at 16:05:49 UTC; its saved result is level 1, XP 0, attributes 10, zero unspent. There were no above-baseline allocations left for that reset to refund. Current reset refunds actual allocated points and preserves existing unspent points; it cannot reconstruct allocations erased by the old development reset.

No saved balances were guessed or player data overwritten. Older schema backups predate subsequent QA attribute changes and are not a safe exact refund amount. Existing regression coverage proves the current reset refunds allocated points, preserves unspent points across reopening, and does not refund twice. `/rpg dev points grant <positiveInteger>` remains available for an explicit QA point grant; reset itself does not invent a level-derived balance.

## Offline checks

Passed in one final source state: full `:test :nativeControlTest :jar` (3,175 unit tests and 404 native-control tests; zero failures/errors/skips), package validation and U7P5 compatibility validation (40,440 references, zero failures). Logs: `build/r197-final-checks.log`, `build/r197-package-check.log`, `build/r197-u7p5-check.log`. Added regression coverage for zero/refunded balances and hidden controls, the single overlay coordinate space, and a transparent mask retaining an active native drop slot. No game/server/authentication process is launched. Rendering still needs connected QA.

## Connected QA

1. Open Inventory: verify level 1 and `0 points remaining`, with no `+` buttons. For allocation QA, run `/rpg dev points grant 5`; reopen, allocate all five and confirm the count reaches zero and the buttons disappear. `/rpg level reset` should refund those five exactly once.
2. Hover a multi-cell item near either edge, then a base-game item: the panel should sit above the full item footprint, centered unless clamping is needed. Exit the item and press Escape to check cleanup.
3. Move across the blurred background: no screen-sized hover flash. Drop a stock stack and gear outside the windows to verify the target remains functional.
4. Confirm Compare button is absent and right-click equip/unequip still works. Alt comparison remains blocked by the input boundary described above.

## Deployment

Deployed `0.2.0-R197-U7P5` to the only active RPG save, `Saves/RPG/mods/HyARPG.jar`. Previous R196 JAR backed up to `evidence/hyarpg/jar-deploy-20261002T162742538Z/HyARPG-before.jar`. Installed SHA256 verified: `3A0E1A5F43D696CD74AC8B139355A05031EAB757D932933077636ADE357E27F0`. Save/player data unchanged. Connected rendering and drop acceptance remain user QA, not asserted by the offline tests.
