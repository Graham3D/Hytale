# R160 Appearance Native Visual Catalog

Status: deployed connected-validation candidate; deterministic validation passed. R159 is the accepted immediate rollback.

R160 implements only Appearance recovery Checkpoint 3. It expands the connected-approved R159 immutable graphical-card contract to the complete installed Hytale 0.6.3 cosmetic registry without restoring the failed R153/R154 runtime image architecture. Profile, Voice, Inventory, Gear, Orbis, stable NPC identity, appearance authority, persistence, and player-restoration paths are unchanged.

## Result

- The installed registry is represented by **590 immutable canonical thumbnails**.
- The two connected-proven R159 Undertop assets remain at their original paths and hashes. The other **588** images are packaged below `ImmersiveNpcAppearance/Catalog/Thumbnails`.
- Every image is **92x149 RGBA**, matching the proven card-source dimensions. The packaged PNG payload is 4,516,115 bytes in total.
- A normal graphical card is image-dominant and retains the existing Hytale-style button hover/pressed treatment, tooltip/name plate, and independent gold selected underline.
- The `None` choice is not a cosmetic registry entry and intentionally retains the explicit generic empty-choice presentation.
- Cards use only stable packaged `AssetImage.FallbackTexturePath` references. There is no per-session copy, per-color copy, runtime PNG generation, runtime recoloring, runtime asset registration, or image upload packet.

## Thumbnail source and build pipeline

The installed cosmetic descriptors expose models, textures, variants, localization, and gradient sets, but no supported server-side thumbnail/icon field. R160 therefore uses the connected-proven fallback priority from R159: the deterministic offline category-rig output documented by R151/R152.

The production packaging command is:

```powershell
.\tools\package_appearance_thumbnail_catalog.ps1
```

It reads the retired offline provenance index, verifies every source hash, preserves the two R159 files rather than duplicating them, copies the remaining 588 images to the production catalog, and emits a deterministic 590-row `Catalog/index.tsv`. Normal runtime and plugin startup never invoke this script or the offline baker.

Release validation reads the exact installed `Assets.zip` paired with the build server JAR and requires the packaged `(category, cosmetic ID)` keys to equal the installed registry keys. It also verifies all 590 image hashes and rejects the retired dynamic color-source banks and upload/generation classes.

## Bounded rendering contract

Hytale 0.6.3's server Custom UI event surface exposes click/value/focus events but no supported scroll-position or viewport-change event. `UICommandBuilder` can replace rows, but without a trustworthy viewport signal it cannot safely drive recycling as the user scrolls. R160 therefore retains the design-approved fixed pagination fallback instead of claiming false virtualization.

- selected secondary category only;
- five columns;
- 20 card hosts per page;
- at most 20 simultaneously realized cosmetic cards, below the requested ceiling of 30;
- page/category/search changes clear and replace that bounded subtree;
- selection and color changes update state only and do not remount image resources;
- all 590 entries remain reachable through pages.

True vertical scrolling is deliberately not activated in this candidate. It remains gated on a supported client viewport/recycling contract.

## Search and colors

Search remains local to the selected registry category and is debounced by 180 ms. The pending query is separate from the committed query, preventing a typed-but-not-yet-applied search from racing clicks against the visible page. Search changes the same bounded 20-card subtree and does not schedule a character preview unless the selected cosmetic itself changes.

The installed server API contains no server Custom UI `ColorOptionGrid` class/contract. R160 retains the already connected-safe swatch controls and renders the complete valid palette together. Color authority remains the catalog descriptor:

1. explicit `Textures` choices when supplied by the cosmetic;
2. otherwise the cosmetic's installed `GradientSet` entries and actual `PlayerSkinPartTexture` base colors.

No arbitrary RGB values are synthesized. A color click changes the validated appearance draft and selected swatch, then enters the existing coalesced central preview path. Canonical card images never change color; the large NPC `CharacterPreviewComponent` remains the exact preview.

## Preview and diagnostics

The existing newest-generation-wins preview gate was not replaced. It retains session, active-editor, page/editor generation, draft identity/hash, and preview-generation guards before applying the encoded `PlayerSkinUpdate` to the private character preview.

Deterministic coalescing measurement: a burst of 101 requests realizes one active worker, replaces 100 pending requests, and applies only generation 101. A subsequent request cancelled during page cleanup does not apply. Runtime diagnostics now expose:

- `realizedCardCount`;
- `staticThumbnailReferencesThisCategory`;
- `totalThumbnailReferencesUsedThisSession`;
- `cardRebuildCount` and `gridRebuildCount`;
- `previewJobsScheduled`, `previewJobsCoalesced`, and `previewJobsApplied`;
- category/page/search `rebuildReason` with `durationMs`;
- `runtimeThumbnailCreates=0`;
- `runtimeThumbnailWrites=0`;
- `runtimeThumbnailRecolors=0`.

The unload record explicitly reports `clientAtlasReleaseClaim=false`; clearing server-side selectors is not represented as proof that the client released atlas storage.

## Deterministic validation

`test.ps1 -SkipLive` passed the full deterministic suite on 2026-09-05.

New/tightened validation proves:

- exact 590-entry installed-registry coverage with no duplicate IDs or image paths;
- 588 catalog images plus the two unchanged R159 probe images;
- exact SHA-256 and 92x149 dimensions for every PNG;
- all assets are present in the built JAR;
- 20-card hard realization ceiling and complete fixed-page reachability;
- no runtime asset creation/write/recolor/upload implementation;
- 180 ms debounced search and no preview scheduling from search alone;
- complete existing registry-derived swatches;
- coalesced newest-preview behavior;
- retained R156 event lifecycle, R157 payload identity, R158 focused preview, and R159 hashes;
- all earlier NPC profile, persistence, inventory, gear, stats, voice, appearance, and player-isolation regressions.

Compilation produced only the pre-existing Hytale deprecation and deterministic-test `Unsafe` warnings; there were no build or test failures.

## Files changed

- `src/main/java/com/inigmasgames/persistentnpcs/ui/AppearanceThumbnailCatalog.java`
- `src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java`
- `src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcAppearanceCard.ui`
- `src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcAppearance/Catalog/index.tsv`
- `src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcAppearance/Catalog/Thumbnails/*.png` (588)
- `tools/package_appearance_thumbnail_catalog.ps1`
- `validate-release-resources.ps1`
- `src/test/java/com/inigmasgames/persistentnpcs/R160AppearanceNativeVisualCatalogTest.java`
- retained R151/R153/R155/R159 regression gates and `test.ps1`
- release revision metadata, build/install scripts, R159 approval record, and this report.

## Deployment

Exactly one project JAR is active:

```text
C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R160-NPC-APPEARANCE-NATIVE-VISUAL-CATALOG.jar
SHA-256 96CFB4D6BC1E57E53C8AB8A1046F2283A7EA533550063E2FB1D6974D7BB1876C
```

Immediate accepted rollback:

```text
C:\HytaleRollback\NpcAuthoringStudio-Appearance-R159-2026-09-05-R160NativeVisualCatalog\ImmersiveNPCs-0.6.3-R159-NPC-APPEARANCE-TWO-CARD-PROBE.jar
SHA-256 0ADD94A4FDA4DA0A26D77FECE5E598805D35AF353FDFBDC01C33F17BD22C3700
```

## Connected validation checklist

Fully exit Hytale before testing so its client UI resources are rebuilt from R160.

1. Confirm the revision HUD/notification says `R160-NPC-APPEARANCE-NATIVE-VISUAL-CATALOG`.
2. Open Hoit with `/npc update hoit`, then open Appearance.
3. Visit every primary and secondary category. Use Prev/Next through every populated category; this candidate intentionally uses pages, not scroll virtualization.
4. Confirm graphical cards are distinct, correctly framed, image-dominant, and have a gold selected underline plus hover/tooltip feedback.
5. Select at least 50 different cosmetics across the session, including rapid alternation. Confirm the large preview converges to the final selection.
6. Exercise every displayed color palette and rapidly switch colors. Confirm all valid swatches are visible together, the large preview changes, and card artwork remains canonical/unchanged.
7. Search/filter repeatedly and rapidly switch categories. Confirm no stuck Loading/dim state and no preview change from search alone.
8. Back/reopen Appearance at least ten times, then switch between at least two NPCs.
9. Save and reopen to verify the exact appearance. Then make another change, Discard, and verify the prior appearance.
10. Repeat at 1920x1080 and 2560x1440.
11. Open NPC Profile, both inventories, Voice Recorder, and the Orbis HUD afterward; confirm there is no unrelated UI corruption and the logged-in player's appearance/equipment restores exactly.
12. Inspect client/server logs for `Texture atlas needs`, `dropping images`, Custom UI document/binding failures, growing preview queues, or progressive interaction lag.

Required connected result: zero Loading regression, stuck dim screen, atlas overflow, dropped images, unrelated UI corruption, progressive lag, growing preview queue, runtime thumbnail creation, stale preview, save/discard error, or player mutation.

Connected atlas/client-log evidence for R160 is intentionally pending this restarted-client run. Deterministic validation cannot establish client atlas retention or visual rendering. If the complete static catalog causes growth or corruption, stop on R159 and record the exact category/session threshold, warning, persistence after category replacement, and smallest reproduction; do not reintroduce a dynamic image system.
