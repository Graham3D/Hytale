# Inventory weapon art and spatial footprints

The R193 art import reads `art/UI/items/weapons` from the artist's workspace.
Run `tools/Import-WeaponArt.py <source-directory>` to copy each recognized PNG
byte-for-byte into the mod. The importer records PNG dimensions, alpha bounds,
the source path, and SHA-256 in `rpg/inventory/weapon-art-v1.json`. It never
crops, resamples, recolors, or re-encodes item art. The inventory UI fits the
visible alpha bounds uniformly inside the item's grid rectangle.

Spatial dimensions come from a folder suffix such as `Bows2x4`, overridden
by a filename suffix such as `icon1x4.png`. Image canvas dimensions do not
determine occupancy; a transparent 256×256 bow remains a 2×4 item. Files
without either suffix retain their existing reviewed catalog footprint.
`tools/Apply-WeaponArtFootprints.py` updates only suffix-bearing installed
items and their managed gear carriers. The revision-3 catalog stores the prior
dimensions for revision-2 save migration.

On loading an older bag, every saved item is repacked at its newly audited
size only if the whole bag fits. Otherwise its previous rectangles remain
valid under revision 3; the bag, item identities, payloads, and receipts are
preserved. New items use revision-3 dimensions. No save is rewritten during
offline build validation.

Seven source files are intentionally not bound: the dual-dagger and offhand
alternate views, two alternate Iron longsword renders, an alternate Iron mace,
the second Wood shield render, and Shuriken (no installed native item ID).
All other 133 source PNGs are packaged and checked byte-for-byte. New 3×4 and
4×4 footprints receive a flat rarity tint where no matching gradient texture
exists; existing rarity textures remain in use for prior sizes.
