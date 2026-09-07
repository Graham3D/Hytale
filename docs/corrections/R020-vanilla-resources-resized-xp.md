# R020 — Vanilla Resources and Resized XP

R020 supersedes R019's custom Mana presentation. This is a HUD-only correction;
Stage 06 has not begun, and resource gameplay/balance code was not changed.

## Inputs reviewed before implementation

The pass began from clean branch `RPG` at R019. The R019 correction report and its
machine evidence were reviewed before source changes. Both installed Hytale tracks
were then audited:

| Package | Last write (UTC) | Server SHA-256 |
|---|---|---|
| release/latest | 2026-09-07 14:13:20 | `7EBE0259793BAC8D23426E28FFA7F728C911A157C6039969FE3EED268052B787` |
| pre-release/latest | 2026-09-06 03:08:51 | `EC57E9BD6E2CA3CB16CC5883D42B04A0C64D382DEE532C5BC1CFCF68421E1EE3` |

The latest release's native Health, Mana, Stamina, and Hotbar documents were
inspected. Their hashes are recorded in
`evidence/corrections/R020/latest-hytale-build-audit.json`. The established runtime
target remains `0.7.0-pre.1`; changing it during a narrowly scoped HUD correction
would combine compatibility migration with presentation correction.

## Native resource ownership

Hytale is once again the sole presenter of Health, Mana, and Stamina. R020 removes:

- the complete custom `#ManaHud` document subtree;
- custom Mana fill calculations and update commands;
- the Mana-only native visibility lease and every associated
  `getVisibleHudComponents` / `setVisibleHudComponents` call;
- the obsolete Phase 00 replacement-HUD commands and document, which could still
  hide all three native bars when manually invoked;
- RPG resource-presentation polling traces; and
- all remaining RPG-packaged Health, Mana, and Stamina art.

Consequently, the RPG HUD does not hide, overlay, move, size, style, or duplicate any
native resource control. Native Stamina keeps its own temporary visibility, drain,
and error behavior because the RPG no longer changes the native HUD-component set.

This did not alter the resource model or gameplay. Martial skills still use Stamina,
Dexterity still scales Stamina, magical skills still use Mana, and Intelligence still
scales Mana. Costs, recovery, attributes, and abilities are outside this correction.

## XP replacement

The new owner-supplied files from `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale\art`
were copied without modification:

| Asset | Dimensions | SHA-256 |
|---|---:|---|
| `ExperienceFrame.png` | 702 x 28 | `E0F96FBF1032EE2CEA47831E09B1AD87C68CF41D19DA90378D5FCA5FA6842C40` |
| `ExperienceBackground.png` | 696 x 28 | `361282840D94680116E2B7A231AACA45E67A3AFAC0E14F7E724D82B025C8F456` |
| `ExperienceBar.png` | 1 x 22 | `B8DCBDE4295DDC2810AB043B075202D1C337D9444A867B4DF5D927794F2C3D2B` |

The fixed-width 702-pixel group remains implicitly centered at `Bottom: 138`. This
matches the native nine-slot hotbar width (`9 × 74 + 9 × 4 = 702`) and preserves the
existing six-pixel spacing above the native resource row. Artwork is rendered at its
actual dimensions; there is no artwork redesign or rescaling.

Sibling/layer order remains:

```text
ExperienceBackground
ExperienceFill
ExperienceFrame
```

The 22-pixel-high fill stays at `Left: 3, Top: 3`. Runtime width is
`round(696 × clampedProgress)`, so 0% produces 0 pixels and 100% aligns exactly with
the 696-pixel usable background width. No XP text or extra HUD element was added.

## Verification and deployment

| Gate | Result |
|---|---|
| Branch | `RPG` |
| Revision/version | `R020` / `0.0.13` |
| Hytale target | `0.7.0-pre.1` (unchanged) |
| Player schema | `3` (unchanged) |
| Primary implementation commit | `359fef86ae89feea4730bcbe503b04281507f468` |
| Legacy probe retirement commit | `01dfd176b34d2a26c22d7a9fd3baf3b2efdfbe9d` |
| Verified source/gate commit | `4a57696cb774e32fbcb7b2bd31a0fe98b2f85302` |
| Initial evidence/deployment commit | `63cbb89711726a660c240b4ce98096484bd67b2b` |
| Aggregate tests | PASS — 130 tests, 0 failures/errors/skips |
| Source CustomUI validation | PASS — 16 documents |
| Packaged RPG CustomUI validation | PASS — 9 documents |
| RPG resource controls/assets absent from every packaged HUD | PASS |
| RPG native-visibility mutation absent | PASS |
| Owner XP files byte-identical | PASS |
| XP runtime/source dimensions | PASS — 702×28, 696×28, 1×22 |
| XP centering/layer/left-fill geometry | PASS |
| CanvasUI source | unchanged |
| Isolated three-mod server smoke | PASS — ready and clean shutdown |
| RPG JAR SHA-256 | `FBC49B7625161934FDA4139FE4EA55607ECFB1FC7A5298930D2A6987B0A9A01C` |

The RPG save contains exactly:

| Mod | SHA-256 |
|---|---|
| `CanvasUI-0.1.0.jar` | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| `HYTALEDEVLIB-0.5.0.jar` | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| `HytaleRPG-0.0.13.jar` | `FBC49B7625161934FDA4139FE4EA55607ECFB1FC7A5298930D2A6987B0A9A01C` |

Rollback is retained at
`evidence/corrections/R020/rollback/HytaleRPG-0.0.12.jar`. Static tests and a bare
server smoke cannot prove client-side pixel placement or native transient Stamina
behavior, so connected-client QA remains required.

```ini
R020 = DEPLOYED_AWAITING_CONNECTED_HUD_QA
Stage06Started = false
```
