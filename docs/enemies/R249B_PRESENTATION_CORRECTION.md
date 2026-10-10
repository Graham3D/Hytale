# R249-U7P5B Elite name and affix presentation correction

Status: **connected QA candidate**. Monster Affix remains **OPEN** and owner acceptance remains false.

- The shared hitbox layout raises the visible affix row by 0.18 world units. Its native Nameplate carrier still compensates for Hytale's 1.00-unit text lift. The colored name row stays at its previous height: `baseMargin + affixGap + nameGap` remains `0.80` world units above the hitbox top. The visible name-to-affix gap is now `0.20` rather than `0.38`.
- The overhead affix row uses the full frozen display labels. It no longer shortens Enchanted, Magic Resistant, Mana Burn, or similar names. ME-023 reads `Aura Enchanted: <element>`; ME-024 reads `Packbound`, with its live guard count retained in encounter state and detailed display data.
- Combat, affix mechanics, native name owner, colored glyph assets, normal NPC names, spawning, rewards, difficulty, persistence and world configuration are unchanged.
- Focused offline tests cover full overhead labels, Packbound state spelling, Skeleton/Bear/Trork-sized hitboxes, visual scaling, translation and deterministic rebind geometry. Connected placement and readability remain **NOT_RUN** until owner QA.

Restart the active RPG world and compare `/rpg spawn Trork_Warrior unique hell` at close and medium range. Confirm its colored name stays at the same height, the full affix names sit directly beneath it without overlap, and Packbound shows no number. Also check `/rpg spawn Skeleton_Frost_Archer champion hell` and `/rpg spawn Bear_Polar unique hell` while they move. Use `/rpg spawn clear` when finished.
