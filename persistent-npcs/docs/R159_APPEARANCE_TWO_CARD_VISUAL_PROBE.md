# R159 Appearance Two-Card Visual Probe

Status: deployed connected-validation candidate; not yet approved.

R159 implements only the documented Appearance recovery Checkpoint 2. The version is R159 rather than the brief's original R157 filename because R157 and R158 were already consumed by the event-payload and focused-preview repairs. No full-catalog thumbnail restoration has begun.

## Result

- Exactly two `UNDERTOP` placeholder cards now display real cosmetic artwork:
  - `UNDERTOP:FarmerTop`
  - `UNDERTOP:FlowerShirt`
- The other catalog entries retain the safe R158 icon/name placeholder presentation.
- The card remains the existing interactive `Button`, with an immutable packaged `AssetImage`, readable name plate/tooltip, and the existing gold selected-state overlay.
- The thumbnails do not change when a color is selected. The authoritative draft and central live NPC preview remain responsible for color.
- Selection, color, preview coalescing, save, discard, generation validation, and persistence paths were not replaced.

## Descriptor inspection and source decision

The installed release `Assets.zip` entries for `FarmerTop` and `FlowerShirt` expose:

- `Id`
- localization `Name`
- `Model`
- `GreyscaleTexture`
- `GradientSet`

They expose no supported icon or thumbnail field. R159 therefore uses priority 2 from the Authoring Studio design: a project-packaged fallback derived from the retired offline R154 output.

Source files:

| Cosmetic | Retired R154 source | Dimensions | SHA-256 |
| --- | --- | ---: | --- |
| Farmer Top | `tools/retired-appearance-r154/Thumbnails/a8104e29c7632e96599fe54d.png` | 92x149 | `059DC8C47AFE08AAC235EF33EFF22F216751B046103CC208200CB3E3523CC219` |
| Flower Shirt | `tools/retired-appearance-r154/Thumbnails/2438e1bc4fdae0c2494b3150.png` | 92x149 | `FB59F44840BE4DE56C2BDFE82221889E18308669A409F54D3C009D27A02DF559` |

Packaged paths:

- `Common/UI/Custom/Pages/ImmersiveNpcAppearance/Probe/UNDERTOP-FarmerTop.png`
- `Common/UI/Custom/Pages/ImmersiveNpcAppearance/Probe/UNDERTOP-FlowerShirt.png`

The packaged files retain the source dimensions and hashes. Release validation fails if either file changes, disappears, or if a third probe file is added.

## Standalone versus contact sheet

R159 uses two standalone images. A contact sheet was not selected because Hytale 0.6.3's `PatchStyle.Area` source-crop behavior is not connected-proven for this Custom UI path, while two direct `AssetImage` references have no crop contract and decode the same total source pixel count as a two-cell sheet. The standalone route is the smaller-risk atlas probe and does not introduce an additional sheet resource.

## Runtime resource boundary

The probe allow-list is compile-time immutable and contains exactly two entries.

```text
dynamicThumbnailCreates=0
runtimeThumbnailWrites=0
```

There is no `AssetInitialize`, `AssetPart`, `AssetFinalize`, `AssetUpdate`, `RemoveAssets`, runtime PNG writer, per-color bitmap, session asset ID, or thumbnail generation queue in the production path. Repeated cosmetic and color selections only update normal UI state and the central preview.

Bounded diagnostics added:

- `APPEARANCE_THUMBNAIL_REFERENCE`
- `APPEARANCE_THUMBNAIL_CARD_BUILT`

Each record includes cosmetic ID, stable packaged path, 92x149 dimensions, `packagedStatic=true`, the per-page card build count, and both zero runtime-resource counters. Reference/card logging is de-duplicated within the page instance, so color clicks do not create or count new image resources.

## Deterministic validation

The full deterministic suite passed twice with `test.ps1 -SkipLive`.

New and tightened gates verify:

- exactly two allow-listed cosmetics in one category;
- exact packaged paths, PNG dimensions, and SHA-256 provenance;
- exactly two probe images in the built JAR;
- no retired full `Thumbnails` tree in the built JAR;
- no dynamic image packet or runtime generator classes;
- zero dynamic creates and zero runtime writes;
- retained R156 event lifecycle, R157 payload, and R158 focused-preview behavior;
- all earlier NPC persistence, inventory, gear, stats, profile, voice, and appearance regressions.

Build warnings are unchanged deprecation warnings from the installed Hytale API and existing deterministic Unsafe-based test seams; there were no compilation or test failures.

## Files changed

- `src/main/java/com/inigmasgames/persistentnpcs/ui/AppearanceThumbnailProbe.java`
- `src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java`
- `src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcAppearanceCard.ui`
- `src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcAppearance/Probe/UNDERTOP-FarmerTop.png`
- `src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcAppearance/Probe/UNDERTOP-FlowerShirt.png`
- `src/test/java/com/inigmasgames/persistentnpcs/R159AppearanceTwoCardVisualProbeTest.java`
- retained R151/R153/R155 safety tests, `test.ps1`, release validation, version metadata, build/install scripts, and this report.

## Deployment

Active JAR (exactly one project JAR in the NPC save):

```text
C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R159-NPC-APPEARANCE-TWO-CARD-PROBE.jar
SHA-256 0ADD94A4FDA4DA0A26D77FECE5E598805D35AF353FDFBDC01C33F17BD22C3700
```

Immediate rollback:

```text
C:\HytaleRollback\NpcAuthoringStudio-Appearance-R158-2026-09-05-R159TwoCardProbe\ImmersiveNPCs-0.6.3-R158-NPC-APPEARANCE-FOCUSED-PREVIEW.jar
SHA-256 883B563DACE1C12A3ED293EE686438248876D446EAB63CE0BC7FF2D7DE0468B3
```

Earlier R157/R156/R155 rollback checkpoints remain preserved.

## Connected validation checklist

Fully exit Hytale before starting this test so the client resource atlas is rebuilt from R159.

1. Confirm the revision HUD/notification reports `R159-NPC-APPEARANCE-TWO-CARD-PROBE`.
2. Run `/npc update hoit`, open Appearance, then choose Clothing -> Undertop.
3. On page 1, confirm Farmer Top and Flower Shirt show their distinct real torso artwork; all other cards should remain placeholders.
4. Alternate Farmer Top and Flower Shirt rapidly. Confirm the gold selected state follows the final click and the large NPC preview converges to it.
5. Click every available color repeatedly. Confirm the two card PNGs remain unchanged while the large NPC preview changes color.
6. Leave Undertop and return. Confirm both real cards still render and no loading state remains.
7. Back out and reopen Appearance at least five times.
8. Verify Save and Discard, then close/reopen the NPC Profile.
9. Open unrelated Profile, inventory, Voice Recorder, and HUD UI and verify their chrome is unchanged.
10. Check the client/server logs for atlas overflow, `dropping images`, Custom UI document/binding errors, or progressive resource growth.
11. Repeat the visual check at 1920x1080 and 2560x1440 if both resolutions are available.

Required connected result: zero permanent loading, atlas overflow, dropped images, unrelated UI corruption, progressive lag, new runtime image resources, stale preview, or player appearance mutation. Both thumbnails must remain stable and the central preview must remain authoritative.

Client atlas/log evidence for R159 is intentionally pending this connected run; the deterministic suite cannot truthfully establish client rendering or atlas behavior. Stop after reporting the result. Do not restore additional thumbnails until R159 is explicitly approved.
