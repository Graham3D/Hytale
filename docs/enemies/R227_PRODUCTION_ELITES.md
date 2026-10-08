# R227-U7P5 — Production Elites, Tasks 1 and 2

This is the first owner QA build for the two-task Production Elites change. It is based on the R226-U7P5 source and preserves the existing 27 affix implementations, durable encounter/reward owners, transient QA spawning, native combat, and monster progression. Connected behavior remains for the owner to verify in the main game; no standalone server was launched.

## Production eligibility

- The generated [role matrix](ELITE_ROLE_MATRIX.md) evaluated 275 concrete catalog IDs. Four have complete certified Champion and Unique affix plans in Normal, Nightmare, and Hell: `Larva_Void`, `Skeleton_Scout`, `Trork_Warrior`, and `Golem_Firesteel`. All four have their production manifest flag enabled. Five authored campaign actors remain excluded; 266 IDs lack a production-certified native binding. QA's generic binding remains QA-only.
- Installed world-spawn assets contain `Larva_Void` (4 files) and `Skeleton_Scout` (1 file). No installed world-spawn entry names `Trork_Warrior` or `Golem_Firesteel`, so enabling their bindings does not create a natural spawn. It allows promotion only if an existing native world job actually produces a qualifying actor. No spawn asset was invented.
- A natural actor still must pass the native hostile world-spawn provenance and authored profile checks. The existing deterministic 92% Normal / 2% Champion / 6% Unique roll, legal affix selection, whole-pack publication, rollback, and production rewards are unchanged. Random Super Unique remains disabled; Crystal Golem campaign ownership remains authored.

## Visual identity and SDK boundary

- Rarity visual root scale is Normal ×1.00, Champion ×1.15, Unique ×1.30, Super Unique ×1.45. The original native model scale is recorded once on the saved actor identity and reused on rebind/reload. Only the model packet scale changes. The native model box, physics values, attachments, animation data, and combat stats are copied unchanged. Normal minions retain their native scale.
- The shared `MonsterPresentationLayout` now raises the native name/affix presentation rows using the ratio of current model scale to the frozen native baseline. The existing native Healthbar asset and viewer-local damage window are unchanged. Connected QA must verify that Hytale's native Healthbar position remains clear of enlarged models; the server API exposes no per-actor `Healthbar` hitbox offset without changing the shared asset or collision box.
- The accepted Trork rarity color path uses three prepared Trork texture substitutions, not a general runtime tint. The installed native `Model`/packet has texture and gradient identifiers but no per-actor tint/color field or certified generic tint adapter. Generic runtime rarity tint successes: **0**. `Trork_Warrior` retains its existing rarity-colored texture treatment; `Larva_Void`, `Skeleton_Scout`, and `Golem_Firesteel` report `PRESENTATION_TINT_UNSUPPORTED` and retain their native detailed textures, colored Elite name, and Elite scale. No per-species recolored textures, shader, or client projection system was added.

## Offline verification and QA

- Compiled main and test Java. Focused tests cover identity codec/clone baseline persistence, packet-only scale and unchanged native geometry, shared scaled layout, generated role-matrix parity, native bindings/assets, legal affix planning, native group preparation, and Scout projectile restrictions.
- `verifyHyArpgJar` passed for the R227 JAR; `validateCustomUi`, native proc asset audit, and spatial-grant audit passed. A direct R226-to-R227 archive comparison found zero added/removed assets and exactly one changed gameplay asset, `rpg/enemies/native-bindings-v1.json`. No substantive affix implementation class changed. The legacy `auditU7P5AssetCompatibility` gate fails because it still demands the R176/R200/R201 asset set and rejects the already shipped R226 Master Enemies assets; its report contains no missing-reference finding. This historical baseline mismatch was not widened or used to change gameplay assets.
- Suggested connected QA: restart the RPG world; find natural Larva in Void night spawns and Skeleton Scout in Zone 3, verify Champion/Unique incidence and rewards over multiple spawns; use `/rpg spawn Trork_Warrior unique hell` and `/rpg spawn Skeleton_Scout champion normal` to compare rarity scale, colored names, affix row, and damage-triggered native Healthbar. Natural promotion is probabilistic, so a small sample may contain only Normal actors.

## Deployment

- Active save: `C:/Users/Zemio/AppData/Roaming/Hytale/data/pre-release/Saves/RPG` only.
- Active mod: `mods/Hywind.jar`, manifest `InigmasGames:HyARPG@0.2.0-R227-U7P5`, SHA-256 `F795997279D52EE4EA65C4FA39F3D2517B7A53C92810BE0C0F3A43635ED33F63`. The built and deployed checksums match.
- Previous R226 `HyARPG.jar` moved outside Saves to `C:/Users/Zemio/.codex/deployment-backups/R227-U7P5/HyARPG-before-R227-334F1329.jar`, SHA-256 `334F1329DB79F6BB808A539CD0570A286468944BF2BA04926D0A1E1341364457`. Only one HyARPG plugin JAR remains active.
- No game/server was started for validation. Owner connected QA is pending.
