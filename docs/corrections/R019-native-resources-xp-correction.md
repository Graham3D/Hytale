# R019 — Native Resources and XP Correction

R019 supersedes R018's broken HUD presentation. This remains a HUD-only correction;
Stage 06 has not begun.

## Connected R018 failure

The owner-connected R018 screenshot showed red missing-texture placeholders for the
custom HUD art and an XP group stretched across the left half of the screen. The
failure image and log review are retained under `evidence/corrections/R019/`.

There were two independent root causes:

1. R018 requested custom textures with a leading `Common/UI/Custom/` path. The
   connected client log proves transferred assets are mounted under `UI/Custom/`;
   relative document paths are required here.
2. R018 treated `Horizontal: 0` as a center coordinate. In Hytale's anchor grammar it
   supplies horizontal margins/stretching. Native fixed-width HUD documents center
   themselves by omitting Left, Right, and Horizontal.

## Native resource ownership restored

All R018 custom Health and Stamina controls, fill calculations, and copied resource
textures were removed. R019 does not redraw or reposition them. The visibility lease
now hides only `HudComponent.Mana`; it explicitly leaves native Health, Stamina,
Abilities, and the hotbar visible and restores the exact original snapshot on
teardown.

The installed release documents place native Health and Stamina at:

```text
Bottom = InventoryClosedContainerMargin (36)
       + HotbarHeight (78)
       + 6
       = 120
```

R019 adds only one centered Mana overlay. Its 142-by-4 background/fill and Mana icon
are byte-identical copies from the owner-specified release Inventory directory:

| Asset | SHA-256 |
|---|---|
| `CharacterPanelStatIconMana@2x.png` | `A43E68F5F29D4D6AA7CAC4CE1179EBCA9704AD899D99617533297DA4DBDB1718` |
| `ProgressBar@2x.png` | `2274A622744FAF9320E3190D1E54DFC8B64A9B6E428765A5B40EF209EFFB5547` |
| `ProgressBarFill@2x.png` | `AFCB6063F66B493D17AC91B80FCF98AA5B5123FAB95F4E0809B6F5726E3AE4D8` |

The Mana fill remains an authoritative `EntityStatMap` projection, clamps to its
maximum, anchors at the left, and expands to 142 pixels at 100%. No RPG-owned Health
or Stamina presentation remains in the JAR.

## XP replacement

The R018 XP group was replaced structurally while preserving byte-identical copies
of the three files in `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale\art`:

| Asset | Dimensions | SHA-256 |
|---|---:|---|
| `ExperienceFrame.png` | 931 x 28 | `F173DE47BAB0EAAA5D0F1DB79EB701D03D4EBC94266CF7D05F479FCB69B26CAE` |
| `ExperienceBackground.png` | 925 x 28 | `79200B6BE97B648F4B642067A266C0C9C64DE81B02C315BC7136131203E7D6AF` |
| `ExperienceBar.png` | 1 x 22 | `B8DCBDE4295DDC2810AB043B075202D1C337D9444A867B4DF5D927794F2C3D2B` |

The fixed 931-pixel group is implicitly centered over the hotbar at `Bottom: 138`.
Native resource bars occupy bottom 120 through 132, so the XP group begins after the
same six-pixel vertical gap that separates the resource bars from the hotbar.

The exact sibling order is:

```text
ExperienceBackground
ExperienceFill
ExperienceFrame
```

The fill is inset three pixels from the frame's left edge, matching the 925-pixel
background. Its height always remains 22 pixels. Its width is
`round(925 * clampedProgress)`, producing 0 pixels at 0% and 925 pixels at 100%.
The extra R018 text overlay was removed so the requested three-layer composition is
not obscured. `CharacterXpProjectionService` and presentation-only diagnostic
fixtures are unchanged.

## Texture path correction

Every packaged HUD texture now uses a path relative to `RpgHud.ui`, including the
existing lower-right ability frames. Tests reject the incorrect
`Common/UI/Custom/...` prefix. This correction addresses all red placeholders visible
in the supplied screenshot, not only the XP textures.

## Verification and deployment

| Gate | Result |
|---|---|
| Branch | `RPG` |
| Hytale target | `0.7.0-pre.1` |
| Revision/version | `R019` / `0.0.12` |
| Player schema | `3` (unchanged) |
| Implementation commit | `b2d73e994ef6cc468d1b279b2363abc3c4be6cf4` |
| Aggregate tests | PASS — 132 tests, 0 failures/errors/skips |
| Source CustomUI validation | PASS — 17 documents |
| Packaged RPG CustomUI validation | PASS — 10 documents |
| Native Health/Stamina custom controls absent | PASS |
| Retired R018 resource assets absent | PASS |
| Mana Inventory assets byte-identical | PASS |
| Owner XP assets byte-identical | PASS |
| Relative texture-path gate | PASS |
| CanvasUI source | unchanged |
| Isolated three-mod server smoke | PASS — ready and clean shutdown |
| RPG JAR SHA-256 | `C3C9B957A7D09524958B69B4E71B77E015FD0E631A59A661651A7E73468FAC96` |

The RPG save now contains exactly:

| Mod | SHA-256 |
|---|---|
| `CanvasUI-0.1.0.jar` | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| `HYTALEDEVLIB-0.5.0.jar` | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| `HytaleRPG-0.0.12.jar` | `C3C9B957A7D09524958B69B4E71B77E015FD0E631A59A661651A7E73468FAC96` |

Machine-readable verification, layout/asset audit, smoke, and deployment records are
under `evidence/corrections/R019/`. R018 is retained there as
`rollback/HytaleRPG-0.0.11.jar`.

Static and isolated-server evidence cannot prove final client rendering. Complete
`docs/corrections/R019-client-verification.md` after a full restart/rejoin.

```ini
R019 = DEPLOYED_AWAITING_CONNECTED_HUD_QA
Stage06Started = false
```

