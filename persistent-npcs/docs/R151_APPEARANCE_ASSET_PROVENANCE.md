# R151 Appearance native assets and thumbnail provenance

Date: 2026-09-05. Baseline: R150 `b122dbca1e321a215c7bdf9202e3fecfc7085518`.

## Native UI artwork

These 12 additional files (20022 bytes) are byte-identical copies from the installed Hytale 0.6.3 client Interface directory. The category icons and palette artwork already packaged in R150 remain unchanged. Hytale/Hypixel artwork remains owned by its original rights holders; “project-owned” here means the packaged resource location, not a claim of copyright ownership.

Destination: `src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcAppearance/`.

| File | Source relative to client Interface | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| CategoryIconBackground@2x.png | MainMenu/MyAvatar/CategoryIconBackground@2x.png | 304 | `ADC803B6958726C7BB81038179F2FC5D16D5C36ED771221CA039FAC305039F2B` |
| ContainerTitleArrow@2x.png | Common/ContainerTitleArrow@2x.png | 360 | `4426366AAAEB57BDB83AE259F7B40CD10E3924EE70201E6C5426858D5041263D` |
| ContainerVerticalSeparator@2x.png | Common/ContainerVerticalSeparator@2x.png | 2447 | `3BF2BBEAD00ABCF4CE464672CFF7F60A45D18F795009A876A88F1AEC5A8B83B2` |
| EmptyPartIcon@2x.png | MainMenu/MyAvatar/EmptyPartIcon@2x.png | 7146 | `85AC2031B5B6840F88B680A1BD5661A205F1566A0071FC952EAE767B51D0EE2E` |
| PartFrame.png | MainMenu/MyAvatar/PartFrame.png | 306 | `15151A3DC887CE72F9D44EAC34A690BD422F671EE4C1D24793C530CEACA57087` |
| PartFrame@2x.png | MainMenu/MyAvatar/PartFrame@2x.png | 577 | `B0A11359E5D0F03CBFA477DB170FD05E83260AA6BEFA22846606A33BCCBD4201` |
| PartMask.png | MainMenu/MyAvatar/PartMask.png | 600 | `39C49E1D62F02687E5AA8189868E5D05A97F69FE2B0B29C6EC1FC1C7357C8848` |
| PartMask@2x.png | MainMenu/MyAvatar/PartMask@2x.png | 368 | `2E41C8853A3443A90FD959E9EA64FED1C8FA087ABE1F444538B7A1898ADCDE99` |
| RandomizeIcon@2x.png | MainMenu/MyAvatar/RandomizeIcon@2x.png | 3388 | `9DD9E7CF2827F63A8AB4EB77581207F78A87EFAF7A283C0870C3CC3CF28998B0` |
| ResetSkinIcon@2x.png | MainMenu/MyAvatar/ResetSkinIcon@2x.png | 3755 | `88D369D94E460D184C3BDDD9C5C8FA5FC1F2C87D971DBB0A4ABB3E310502BF0D` |
| SearchFieldIcon@2x.png | MainMenu/MyAvatar/SearchFieldIcon@2x.png | 580 | `AB4D566C1DDD439A555716D6B18410439ED0CCBB9E8992409F3AD2193F6A8D3C` |
| SearchFieldPatch@2x.png | MainMenu/MyAvatar/SearchFieldPatch@2x.png | 191 | `DCA714221F1FF14A448571C74E0703C7514170617754885F873989C1C1957B1C` |

## Graphical cards

Native MyAvatar declares a scrolling parts host and card style parameters. The client executable contains `PartPreviewComponent` and programmatic part-preview population. No verified server Custom UI contract for that component, and no installed static cosmetic-thumbnail library, was found. We therefore do **not** inject an undocumented component or borrow/mutate the viewer's appearance to render cards.

The authorized fallback is `tools/bake_appearance_thumbnails.py`: a deterministic CPU orthographic rasterizer reading the actual installed cosmetic blockymodel geometry, UV layouts, textures and gradient assets. The output is 590 distinct catalog entries at 184×298 RGBA pixels, displayed at native 92×149 card size. It uses a canonical neutral mannequin, reference colors and representative first variants, never player or persisted NPC data.

These are **reference cards**, not Hytale's native animated renderer or exact live-draft/color portraits. The existing live NPC preview remains the preview for the selected appearance. Models use approximate static attachment/lighting presentation; native animation, entitlement thumbnails and shader effects are not emulated. Tooltips identify reference-color cards. Future unknown catalog IDs remain selectable with an explicit unavailable-thumbnail placeholder, never a misleading generic cosmetic image.

- `Thumbnails/index.tsv`: exact category + cosmetic ID → packaged filename + PNG SHA-256.
- `Thumbnails/provenance.json`: every consumed registry/model/texture/gradient SHA-256, renderer hash and library versions; zero unrendered pinned cosmetics.
- `ImmersiveNpcAppearanceThumbnails.ui`: closed, generated PatchStyle references; no untrusted IDs are interpolated into markup.
- [Renderer contact sheet](R151_APPEARANCE_THUMBNAIL_CONTACT_SHEET.png): representative assets across categories, **not a connected UI screenshot**.
- Release validation rejects changed installed thumbnail source hashes, incomplete coverage and corrupt/missing packaged PNGs.

## Reproduction

Python with Pillow 12.3.0 and numpy 2.3.5 (existing bundled runtime; no system packages installed):

```powershell
& '<python>' tools/bake_appearance_thumbnails.py '<Hytale game latest>/Assets.zip' src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcAppearance/Thumbnails --ui-index src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcAppearanceThumbnails.ui
```

The archive path is an **offline build input only**. No runtime UI or Java thumbnail path depends on an absolute installation location. Reproduction to a separate build directory is checked by comparing the full index, including all 590 PNG hashes. Normal plugin builds consume committed images and do not need Python.
