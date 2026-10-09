# R180-U7P5 native gear tooltip presentation

This revision changes only tooltip presentation. It retains the R179 client-safe
ItemGrid projection, the native item tooltip, ItemQuality frame and label, and
the authoritative gear, affix, requirement, and durability owners.

The GearTooltip source now emits a content-driven, blue-gray divider after the
catalog-family classification and after the base-stat block. It adds a third
divider around a present affix block, with no separator between individual
affixes. Optional authored flavor gets a final divider only when present. The
resolved physical range is assigned white display emphasis in ItemDisplay
metadata. Affixes remain assigned Hywind blue; failed requirements remain red,
independently of met requirements. The QA-only `Cannot salvage` line came from
`GearTooltip.describe`, not the game client, and is removed from normal display.
Native ItemQuality assets already map Magic to the `server.gear.quality.Magic`
label and #1d4dff; they are unchanged. Native durability remains in the
right-aligned footer, so no duplicate or guessed value is inserted into the
description. APS remains absent.

## U7P5 client presentation boundary

`ItemGridSlot` exposes a plain-string `Name` and `Description`. Its server
`ItemStack.CODEC` emits private BSON `Metadata` and `QualityOverride`, which
the U7P5 CustomUI client rejects. R179 safely projects title and description
to strings and removes that server metadata from the outgoing UI command.
Consequently this native CustomUI view **cannot display per-line Message
colors, weight, italic, or font size** even though those tokens are present in
the authoritative `ItemDisplayMetadata`. The separators are dynamically
inserted text lines, not native rule widgets. `ItemTooltip.ui` has a client-owned
`#Id` control and a client-owned durability footer; the per-item slot API has
no supported visibility or prefix field for either. This revision does not
reintroduce the metadata that disconnected the client in R178 or replace the
native hover system. Connected QA is required to confirm the visible result.

## Offline and connected checks

Presentation tests verify semantic style tokens, divider order/count, Magic
quality localization/color, no internal ID in authored description, no
`Cannot salvage`, canonical affix wording, and resolved damage. Native codec
tests cover the U7P5-safe outgoing slot contract. Package and asset checks run
before deployment. No native server or client is launched by this workflow.

For connected QA, open the RPG save, press Tab, and hover a Common, Magic, and
Rare gear item. Check title and rarity, divider placement, base damage, affix
wording, requirements, and footer durability. Move the mouse away and close
with Escape; no tooltip should remain or disconnect occur.

`gradlew check jar` passed: 3,154 main, 394 isolated native, 49 CanvasUI,
and 5 Tavern JUnit tests (3,602 total; no failures or skips). The deployment
script passed offline package and U7P5 asset/reference validation, backed up
R179 outside `Saves`, and installed R180-U7P5 to the sole `RPG` save. The
installed SHA-256 is
`A995CABD171440F1967F98886E630CFFE7CCF38C8E0E04192142286C7436BC34`;
the backup and receipt are in
`evidence/hyarpg/jar-deploy-20261001T185507313Z/`. Connected acceptance
remains pending user QA.
