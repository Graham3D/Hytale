# R209-U7P5 — Monster target billboard projection

Date: 2026-10-07. Built from the current R208-U7P5 source and deployed to the active `RPG` save.

## Native capability check

The installed Hytale 0.7.0-pre.5.1 `EntityUIModule` publishes only `EntityStat` and `CombatText` entity UI types. The `EntityStat` packet has a stat index and hitbox offset, but no texture, label, or custom UI document field. `Nameplate` is a single text string. `Server/Entity/UI/Healthbar.json` supplies Health and a hitbox offset; the client owns its art. There is no server-side custom textured entity UI path in this SDK.

R209 therefore uses the brief's permitted viewer-local projection fallback for the existing three monster health PNGs. The selected actor's native `BoundingBox.max.y` and transform determine the head point; the viewing player's head rotation and eye height project that point into a compact centered CustomUI element. The projection recalculates on the existing 4 Hz target poll and on target-specific Health/display changes. Frame, fill, background, name, level/rarity, and actual affix tags share one projected stack; apparent size follows inverse depth. Native managed-enemy nameplate text is blanked to avoid duplicate identity. Non-managed nameplates retain their original path.

This is **not** an engine-attached world-space UI. The CustomUI fallback uses a fixed virtual focal length, and its 4 Hz updates can lag fast movement or differ from the client's camera FOV/UI scale. Connected QA must judge positioning, perspective, and smoothness. A true custom-textured native billboard would require a client/protocol capability absent from this installed SDK; R209 does not claim to add one.

## Scope and verification

- Preserved the R208 health fraction and left-edge depletion, all three original PNGs, target/look-at eligibility, affix DTO and minion inheritance, QA spawning, and encounter/combat code.
- Replaced the pinned top-center target panel with a compact projected root; no full-screen hitbox or background.
- Focused `R208EnemyTargetHudTest` passed (art layers, health fractions, head movement, inverse-distance scale, behind-camera rejection).
- `compileJava`, `validateCustomUi` (88 documents), `verifyHyArpgJar`, and package validation passed. No game or standalone server was launched.
- Deployed `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar` at SHA-256 `821CC5B67119C9BE1B89061DD4DB64534FE86D4B65EB7E42CBA5E1AB9A2329AE`.
- Previous R208 JAR backed up outside Saves at `C:\Users\Zemio\.codex\backups\HyARPG\R209-predeploy-20261007\HyARPG-R208-U7P5.jar`.

## Connected QA

Restart the RPG world. Aim at and damage a Trork; confirm the identity/bar/affixes appear together above the head, follow movement, resize with distance, and the fill retreats from the right. Look away and confirm the stack hides. Repeat with a Larva and Crystal Golem for different heights; inspect a Unique leader and its minion to confirm their distinct affix lines. Note any persistent offset, jitter, or duplicate name with the camera mode and distance.

Only documentation was committed/pushed separately because the working source tree already contains extensive uncommitted later RPG changes; the documentation commit does not represent a reproducible source snapshot.
