# R196 inventory drop and interaction QA

## Evidence and root causes

Read `ui-trace-20261002T143512174Z-8cd30bb6.jsonl` and `2026-10-02_10-26-12_server.log` from the active RPG save. At 14:35:14 UTC, source cell 6 was `Food_Wildmeat_Raw`, quantity 23, entry `cd19236c-a0fa-465d-b3c2-80523269f2f9`; its outside drop at 14:35:15 requested 25. Other drops requested 100. The UI enabled `AllowMaxStackDraggableItems`, allowing Creative maximum-stack gestures against a finite inventory. R196 disables that option; the authoritative over-quantity rejection remains intact. Quantities are not silently clamped and no replacement items are minted.

All three player ground-drop paths (spatial stock, spatial managed gear, and native-held managed gear) used `HytaleDifficultyTravel.findSafe`, a grounded standing/teleport validation. Airborne players have no acceptable grounded position and hit `APPROVED_SPAWN_OBSTRUCTED`. Those player paths now use the installed native `ItemUtils.throwItem` origin formula (position + model eye height + head look direction), native launch speed 6, and `ItemComponent.generateItemDrop` physics. Existing receipts still store their established half-block-adjusted projection origin. No save schema or custody ownership changes were introduced.

Native `ItemComponent.PICKUP_DELAY_DROPPED` prevents immediate re-admission of a freshly thrown item. Because the existing custody fence excludes these entities from `PlayerItemEntityPickupSystem`, the existing admission scan advances the native component timer using monotonic elapsed time; it does not add a second pickup system. Unrelated mob drops and existing pickup transactions are unchanged. Recovery still resolves the same source UUID/receipt.

## Commands

`/rpg level reset` previously called attribute respec and never changed XP or level. It now saves XP 0, level 1, raw attributes 10, allocated-point refund into existing unspent points, and pending level-up indicator 0 in one mutation. Repeating reset does not refund twice. Skills, gear and reward history remain. The command still requires `inigmasgames.rpg.level`. `/rpg dev reset` is removed; see `docs/COMMANDS.md`.

## Inventory controls

- Right-click bag weapon/armor: quick equip/swap through the same custody transfer, requirements and displacement checks used by manual equipment placement. Shields target offhand. Right-click an equipped weapon/armor item: place it in the first fitting bag position through the existing unequip transaction.
- Compare toggle: while enabled, hovering bag gear displays the hovered item on the left and its currently equipped counterpart on the right. Both use the existing custom tooltip template, scoped separately, and disappear together. No equipped counterpart means one tooltip.
- Exact Alt-held comparison and Shift-left-click remapping are not implemented: the installed page event contract does not expose held modifier state. The user approved a Compare toggle fallback. Native ItemGrid quantity selection remains responsible for stack splitting; the existing partial-quantity placement path commits the selected amount at the chosen bag destination. No synthetic key events or fabricated CustomUI properties were added. The native quantity gesture needs connected verification with the user's bindings.

## Connected QA (user-run only)

1. In Creative, drag a non-full wildmeat stack (for example 23/25) outside the UI; verify exactly 23 leave the bag and can be picked back up. Repeat with a native stack whose maximum is 100.
2. Fly above ground and repeat with stock and affixed gear. Verify native falling motion and pickup after the short native delay. Reopen/reconnect to confirm custody remains singular.
3. Right-click gear in the bag with its matching slot occupied; verify the equipped item returns to the bag. Right-click equipped gear to unequip. Test insufficient requirements and a full bag.
4. Enable Compare and hover different weapons/armor; check both panels, empty corresponding slots, pointer exit and Escape.
5. Use the native stack quantity chooser, choose a partial amount, and place it in an empty cell. Verify the source remainder, placed amount, merge and cancellation.
6. Use `/rpg level reset`; reopen inventory and verify level 1, base attributes 10 and refunded unspent points. Repeat once to verify no extra refund; reconnect to check persistence. Confirm `/rpg dev reset` is unavailable.

Deployment is not connected acceptance. No game client, server or authentication flow was launched.

## Final offline validation and deployment

One final source state passed `:test :nativeControlTest :jar --offline`: 3,175 unit tests and 401 native-control tests; zero failures, errors, or skips. The package validator passed and U7P5 compatibility validation checked 40,440 references with no failures. Logs: `build/r196-final-checks.log`, `build/r196-package-check.log`, `build/r196-u7p5-check.log`.

Deployed `0.2.0-R196-U7P5` to the active `Hytale/data/pre-release/Saves/RPG/mods/HyARPG.jar`. SHA256: `7FDE367A38DFCFB317F6746B77C01996E418295E02F0E76F1C521DAFC14AF735`. Previous R195 JAR backed up and checksum verified under `evidence/hyarpg/jar-deploy-20261002T151935320Z/HyARPG-before.jar`. Save/player data was not edited. Connected acceptance remains pending user QA.
