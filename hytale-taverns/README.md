# Taverns

`Taverns` is a Hytale Java mod with a bundled asset pack. Tavern, Kitchen, and Bedroom Cores use one persistent zoning/editor framework.

## Implemented in 0.1.0

- A new placeable `Core_Tavern` item/block using the official Green Crystal block visuals.
- Placeable `Core_Kitchen` and `Core_Bedroom` items using the official Cyan and Blue Crystal block visuals.
- Kitchen volumes begin at 13 × 10 × 5; Bedroom volumes begin at 7 × 5 × 5. Both must stay fully inside their parent Tavern, and specialized volumes cannot overlap.
- Multiple Bedroom Cores are stored as independent UUID-backed room records so they do not overwrite one another.
- The Core drops itself as one whole item. It never uses the Green Crystal shard drop table.
- A Farming Bench recipe: one Green Crystal block plus one Concentrated Life Essence. The design's “Greater Life Crystal” does not currently exist as an installed Hytale asset, so Concentrated Life Essence is the isolated phase-one substitute.
- One Tavern per owner.
- A persistent Tavern UUID, owner UUID, world UUID, Core coordinates, inclusive cuboid bounds, purchased expansion units, and intersected chunk indexes.
- Initial bounds of exactly 21 × 21 × 5 = 2,205 blocks. The Core is centered horizontally and the volume extends upward from the placed Core.
- Owner-only Core configuration and breaking.
- Rejection of Core placement or resizing that overlaps another Tavern.
- Zoning Editor uses Hytale's existing Selection Tool volume UI for every implemented Core type.
- Entering it hides only the hotbar HUD and reports that the player's items are temporarily hidden; no server-side item stack is moved, renamed, replaced, or deleted.
- The client receives the vanilla Selection Tool's dotted-cube hand appearance and selection interactions while the editor is active. The server keeps the player's real Adventure game mode and grants only selection-bound updates.
- All six selection faces can resize the saved cuboid. Left-click toggles dragging for the selected face; right-click confirms the resize and leaves face-drag mode. Ordinary held items cannot place, attack, or perform their normal interactions until the editor closes.
- An empty active hotbar slot uses a hidden, client-only Selection Tool placeholder so the editor indicator and face controls still work without creating a real item.
- Cancelling the selection restores the Core's saved volume. Interacting with the Core again closes Zoning Editor, restores the prior HUD and game-mode presentation, and leaves the inventory unchanged.
- Adventure-mode expansion costs one Green Crystal Shard (`Ingredient_Crystal_Green`) per five blocks beyond the initial volume. Shrinking refunds the exact purchased five-block units. Creative mode bypasses the material transfer.
- Resizing is previewed before it is committed: the moved face is outlined green when affordable and red when invalid or short on shards.
- The first left click starts moving a face; the second left click confirms it. Green Crystal Shards are transferred and bounds are saved only on that confirming click.
- A Core cannot be contracted below its definition's starting width, depth, or height (21 x 21 x 5 for the Tavern Core). Contracting paid expansion refunds the corresponding vanilla Green Crystal Shards.
- Current checks cover world height, Core containment, overlap, and a temporary one-million-block safety ceiling.
- Atomic persistent-data writes in the plugin data directory.
- Vanilla-first progression: use Hytale's existing obtainable items, interactions, and rendering systems when they fit instead of creating duplicate mod-only equivalents.

## Not yet implemented

- Integration with third-party claim/protection mods when resizing. Ordinary Hytale block placement/break protection still applies.
- Terrain subtraction, registered-object bonuses, management menus, tenancy/renting, kitchen automation, Bar Core, and the rest of the larger Tavern simulation.
- A recovery/admin command for a Core record whose physical block was removed by an unrelated admin/editor operation that bypassed normal block events.

## Build

Requirements are already present in this workspace: the local Temurin Java 25 JDK and your installed Hytale 0.5.9 server JAR.

From PowerShell in this folder:

```powershell
.\build.ps1
```

Output:

```text
dist\Taverns-0.1.0.jar
```

## Install into a singleplayer save

Leave the target world before replacing a running mod. You do not normally need to close the full Hytale client.

```powershell
.\install.ps1 -SaveName "New"
```

Replace `New` with the target save name. The script copies the JAR into:

```text
%APPDATA%\Hytale\UserData\Saves\<save>\mods\Taverns-0.1.0.jar
```

Open that world's settings, confirm `Taverns` is enabled in Mod Management, then enter the world.

## First in-game test

1. Use Creative inventory search for `Tavern Core`, or run `/give Core_Tavern` if your player has the built-in self-give permission.
2. Place it on the ground at Y 315 or lower. The chat confirmation should report `21 x 21 x 5 (2,205 blocks)`.
3. Press F to interact with the placed Core. Chat should say "Zoning Editor active" and that the hotbar items are temporarily hidden; the hotbar HUD should disappear.
4. Confirm the vanilla Selection Tool's dotted-cube effect appears around the player's hand in both Adventure and Creative modes.
5. Switch among occupied and empty hotbar slots and drag each of the six faces. Only Core faces should respond; held items must not place blocks, attack, or perform their normal interactions.
6. Try cancelling the selection. The box should return because a Core volume cannot be cancelled.
7. Press F on the placed Core again to close Zoning Editor. The hotbar HUD, real game-mode presentation, item IDs, slots, quantities, and metadata should all be restored unchanged.
8. In Adventure mode, keep `Ingredient_Crystal_Green` in either the hotbar or backpack and expand a face. Only that moved face should outline green; right-click and verify the vanilla shards are consumed only when the resize is confirmed.
9. Remove or reduce the shard stack and expand beyond the affordable size. Only that moved face should outline red, the proposed bounds should remain adjustable, and no shards or saved Core volume should change.
10. Contract saved bounds and right-click to confirm. Verify the exact purchased expansion units are refunded and that no dimension can be dragged below 21 x 21 x 5.
11. Break the Core as its owner. It should return one `Core_Tavern`, never ordinary crystal shards.
12. Place it again, resize it, leave the world, and re-enter. The saved Core record and bounds should still be present.
13. Craft or give `Core_Kitchen` and `Core_Bedroom`, place them fully inside the Tavern volume, and verify each opens its own Zoning Editor volume.
14. Place multiple non-overlapping Bedroom Cores and verify they retain separate volumes after re-entering the world.

Also test these rejection cases:

- attempt to place a second Core as the same player;
- attempt to resize a Core so it no longer contains its physical block;
- attempt to overlap a different player's Tavern;
- attempt to configure or break another player's Core;
- attempt to move the top face above Y 319;
- switch hotbar slots while Zoning Editor is active (the mode and selection should remain active until F is pressed on the Core again).

Avoid deleting a held item by dropping it outside the Creative inventory UI during this Hytale build. The client can encode that action as an invalid zero-quantity stack and crash before a mod receives control.

## Updating during development

### Safest workflow

For every Java or mixed Java/asset update:

1. Leave the world (this stops the local singleplayer server).
2. Run `.\build.ps1`.
3. Run `.\install.ps1 -SaveName "<save>"`.
4. Re-enter the world.

The full Hytale client normally stays open. Restart the full game only if the client keeps a stale icon/model/UI asset or behaves incorrectly after reconnecting.

### Live developer reload

Hytale 0.5.9 has plugin unload/load/reload commands. For this mod's bundled JAR on Windows, the reliable live sequence is:

1. Exit Zoning Editor first.
2. Run `/plugin unload InigmasGames:Taverns` in game or `plugin unload InigmasGames:Taverns` in the server console.
3. Rebuild and replace the JAR with `build.ps1` and `install.ps1`.
4. Run `/plugin load InigmasGames:Taverns`.

`/plugin reload InigmasGames:Taverns` is useful when the JAR has already been replaced successfully, but Windows may keep a loaded JAR locked. A world/server restart remains the baseline when testing persistence, lifecycle code, manifest changes, dependencies, or after a reload failure.

For a dedicated server, stop/restart only the server process and reconnect; the client does not need a separate mod install because Hytale distributes server-provided assets.

## Logs and persistent data

Singleplayer logs are under:

```text
%APPDATA%\Hytale\UserData\Saves\<save>\logs
```

Search the newest server log for `Taverns`, `Core_Tavern`, `SEVERE`, or `Failed` when reporting a problem.

The persistent file is named `taverns.properties` under Hytale's data directory for `InigmasGames:Taverns`. Do not edit it while the world/server is running.

## Validation performed

- Compiled with Temurin Java 25 against the installed Hytale 0.5.9 `HytaleServer.jar`.
- Packaged Java classes, `manifest.json`, server assets, localization, interactions, and drop list into one JAR.
- Booted an isolated offline Hytale server using the built JAR.
- Confirmed discovery of `InigmasGames:Taverns`, asset-pack loading, successful plugin enable, world initialization, and clean plugin shutdown.
