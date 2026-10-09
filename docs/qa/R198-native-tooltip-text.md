# R198 native item description formatting

## Implemented fix

The installed U7P5 `Assets.zip` contains this Heavy Hide description in `Common/Languages/en-US/server.lang`:

`Can be processed into <item is="Ingredient_Leather_Heavy"/> at a <item is="Bench_Tannery"/>.`

The names resolve to `Heavy Leather` and `Tanning Rack`. The owned tooltip was putting the localized string straight into a plain Label, so the native rich-text references appeared as literal tags.

`NativeTooltipText` now resolves item references using native item translation metadata and the player's language. It resolves embedded `<msg key="..."/>` descriptions, preserves paragraphs and visible text while removing native color/bold/italic/strikethrough tags, and bounds recursion. Missing references or unavailable assets do not break hover. Only the native fallback description path is changed; gear affix formatting, tooltip placement/frame, inventory transfers and allocation are unchanged.

Regression tests cover the exact Heavy Hide description, localized names, regex-special characters, formatting/paragraphs, nested portal descriptions, missing assets, cyclic messages and ordinary comparison symbols.

## Requested inputs not implemented

- **Alt-hover comparison:** no held-Alt field in installed CustomUI hover events or native mouse packets. Existing paired-tooltip rendering remains available, but there is no supported modifier signal to activate/deactivate it.
- **Ctrl-left-click +5:** the authoritative allocation service supports bounded five-point requests, but installed button activation does not report Control state. No invented modifier field was bound.
- **Accelerating held-left-click:** TextButton exposes Activating, DoubleClicking, RightClicking, MouseEntered and MouseExited. It does not expose a usable mouse-down/mouse-up pair or held-button value. `MouseButtonReleased` exists in the protocol enum but is implemented by slider controls, not TextButton. Starting a repeat timer from a completed click would not establish that the button remains held.

Evidence: installed `HytaleServer.jar` CustomUIEventBindingType and mouse packet types; installed client Button metadata; Hypixel's [TextButton contract](https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/elements/textbutton) and [Button contract](https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation/elements/button). Client-side Noesis keyboard symbols are not a server CustomUI event bridge. No global OS keyboard hook, client modification, substitute comparison toggle or automatic spending loop was introduced.

These requests remain blocked on a supported client input bridge, not marked complete. Single-click stat allocation retains its current behavior.

## Validation and deployment

Passed in one final source state: `:test :nativeControlTest :jar --offline` (3,182 unit tests + 404 native-control tests, zero failures/errors/skips), package validation and U7P5 validation (40,440 references; zero failures). Logs: `build/r198-final-checks.log`, `build/r198-package-check.log`, `build/r198-u7p5-check.log`. No client/server/authentication flow launched. No player data edits.

## Connected QA

Open the Hywind inventory and hover Heavy Hide, another hide, a bucket and a portal key if available. Check readable item/station names, clean formatting, and preserved paragraphs. Hover managed gear to confirm its affix styling and centered placement are unchanged. The three requested modifier/hold gestures are not part of this build's acceptance claim.

Deployed R198 to `Saves/RPG/mods/HyARPG.jar`; previous R197 backed up under `evidence/hyarpg/jar-deploy-20261002T164545982Z/HyARPG-before.jar`. Installed SHA256 verified: `B8E58184D0A2D8E36B6ACC6AA405764366D4888753D70DEE972F74AE741B7276`. Connected acceptance remains user QA.
