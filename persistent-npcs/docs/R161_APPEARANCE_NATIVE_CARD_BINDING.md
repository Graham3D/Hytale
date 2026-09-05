# R161 Appearance Native Card Binding

Status: deployed connected-validation candidate on 2026-09-05. Connected visual approval is pending.

## Scope and outcome

R161 is the bounded completion repair for the R160 Appearance Editor visual catalog. It does not redesign the editor, alter appearance authority, generate runtime images, recolor thumbnails, or change the draft/save/persistence pipeline.

The connected R160 symptom—590 valid catalog entries rendering as blank/text-only cards—was traced to the Custom UI image binding contract. R160 appended an `AssetImage` dynamically and populated only `FallbackTexturePath`. R161 declares one stable `AssetImage #Thumbnail` inside every realized card document and binds the current immutable packaged image with:

```text
#AppearanceOptionN #Choice #Thumbnail.AssetPath
```

The bound value uses the native Common-relative form:

```text
UI/Custom/Pages/ImmersiveNpcAppearance/Catalog/Thumbnails/<hash>.png
```

Only the current page's maximum 20 cards are visible and bound. Empty slots and the semantic `None` choice do not retain an image reference.

## Installed Hytale 0.6.3 evidence

The installed client documents under `Common/UI/Custom/Pages/Memories` declare stable `AssetImage` nodes with a fallback. Decompilation of the matching installed server `MemoriesPage` shows Hytale assigning the actual image dynamically through `UICommandBuilder.set("#MemoryIcon.AssetPath", iconPath)` and clearing it through `setNull`. R161 follows this supported native pattern exactly.

The previous R160 path (`ImmersiveNpcAppearance/...`) was valid only as a local inline fallback reference. Runtime `AssetPath` requires the full Common-relative `UI/Custom/Pages/...` form. The packaging index, probe references, catalog validator, and release gate now enforce that form and the invariant:

```text
packagedAssetPath == "Common/" + uiTexturePath
```

## Preserved appearance authority

The semantic pipeline remains unchanged:

```text
CosmeticRegistry / installed cosmetic assets
  -> cosmetic ID and variant
  -> texture variant or GradientSet / gradient ID
  -> NpcAppearanceDraft
  -> Hytale PlayerSkin
  -> CosmeticsModule.validateSkin
  -> CharacterPreviewComponent
  -> PlayerSkin-compatible persisted selections
```

R161 does not bake a combined NPC texture, invent an atlas or UV contract, create a proprietary appearance format, mutate player appearance/equipment authority, or change preview restoration.

The installed 0.6.3 server surface contains no supported server Custom UI `ColorOptionGrid` class. R161 therefore retains the complete static swatch controls from R160. Swatches still select native descriptor colors/gradients and update the authoritative PlayerSkin preview; card thumbnails remain immutable representative artwork and are not recolored.

## Catalog and runtime bounds

- 590 installed registry cosmetics map to exactly 590 index rows.
- Every index key and UI path is unique.
- Every row resolves to exactly one packaged PNG with its pinned SHA-256.
- 588 catalog PNGs plus the two connected-approved R159 probe PNGs are packaged.
- The grid realizes no more than 20 card documents at a time.
- Page/category/search rebuilds replace those 20 bindings; hidden slots are cleared.
- Runtime thumbnail creates, writes, recolors, uploads, and session IDs remain zero.
- R153/R154 generators, color-card jobs, and runtime asset packet paths remain absent.

## Temporary connected diagnostics

Each realized successful binding emits:

```text
APPEARANCE_THUMBNAIL_BOUND cosmeticId=<id> thumbnailAssetPath=<UI path> cardIndex=<0-19> selector=<exact selector> packagedAssetPresent=<true|false>
```

An unexpected real-cosmetic lookup failure emits:

```text
APPEARANCE_THUMBNAIL_MISSING cosmeticId=<id> thumbnailAssetPath=NONE cardIndex=<0-19> selector=<exact selector> packagedAssetPresent=false
```

The semantic `None` option is intentionally not reported as missing.

These events prove the server emitted the exact client selector/path command and that the resource exists in the running artifact. Final proof that the client renders the image remains the connected validation below.

## Deterministic validation

`test.ps1 -SkipLive` passed in full. The new R161 gate verifies:

- all 590 mappings and packaged resources;
- Common-relative runtime paths;
- stable `AssetImage #Thumbnail` declaration;
- exact `.AssetPath` set and clear operations;
- required bound/missing diagnostic fields;
- selected-overlay/card structure retention;
- zero runtime image generation, writes, or recoloring.

Retained R156–R160 interaction, payload, preview, two-card, full-catalog, pagination, search-debounce, persistence, PlayerSkin, and runtime safety gates also passed.

## Deployment

Active candidate (the only active project JAR):

```text
C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R161-NPC-APPEARANCE-NATIVE-CARD-BINDING.jar
SHA-256: 2B698271C33E4E08D9FB96960C0F5636AA2A69373CADE49E757B6F5D4BC29E8D
```

Immediate rollback preserved before deployment:

```text
C:\HytaleRollback\NpcAuthoringStudio-Appearance-R160-2026-09-05-R161NativeCardBinding\ImmersiveNPCs-0.6.3-R160-NPC-APPEARANCE-NATIVE-VISUAL-CATALOG.jar
SHA-256: 96CFB4D6BC1E57E53C8AB8A1046F2283A7EA533550063E2FB1D6974D7BB1876C
```

## Connected validation checklist

Fully exit Hytale before testing so the client reloads packaged UI resources.

1. Start the `NPC` save and confirm the revision display says `R161-NPC-APPEARANCE-NATIVE-CARD-BINDING`.
2. Run `/npc update hoit`, open Appearance, and confirm the first page shows graphical cosmetic cards rather than text-only placeholders.
3. Change pages in the same category. Confirm all visible cards update, no earlier-page image remains, and no more than 20 `APPEARANCE_THUMBNAIL_BOUND` entries occur for a page rebuild.
4. Traverse every primary and secondary category. Confirm representative cards render and the selected gold marker remains correct.
5. Search for a known cosmetic and clear the search. Confirm the result card and restored page both show the correct artwork.
6. Select several cosmetics and colors. Confirm the central CharacterPreview updates immediately, while the immutable card artwork itself does not recolor.
7. Save, close, reopen, and restart the world. Confirm the PlayerSkin-compatible appearance persists exactly.
8. Discard a different draft and confirm the persisted appearance returns.
9. Confirm the player avatar/equipment is unchanged after leaving the editor.
10. Review logs: every visible real cosmetic should report `APPEARANCE_THUMBNAIL_BOUND ... packagedAssetPresent=true`; stop and retain the first `APPEARANCE_THUMBNAIL_MISSING`, `packagedAssetPresent=false`, Custom UI error, blank-card category/page, or client-resource warning.

R161 stops here for connected approval. No Profile Editor polish or unrelated system work is included.
