# R055 Skill Tree polish

R055 corrects the connected R054 composition without changing RPG gameplay or graph authority.

Implemented:

- Native Inventory container frames for Library, Tree, and Details.
- Symmetric unstretched ornamental lines and a larger centered title.
- Thirteen visible library rows with tabs/search aligned to the top.
- A single inline keyboard-focus search layer with no duplicate tabs, shade, or buttons.
- Divided Details sections with bounded wrapping and no footer overlap.
- Player-facing E/R/UNBOUND labels, inset ports, and center-window node confinement.
- Removal of Hywind RPG, revision, subtitle, Layout Saved, Current Link, and validation-footer presentation.

Validation:

- `clean check`: PASS (2,367 RPG, 67 native-control, 43 CanvasUI, 5 Tavern JUnit tests; retained Tavern/NPC gates included).
- CustomUI validation: 59 documents PASS.
- Package audit: PASS (5,851 entries, 2,206 classes, 2,090 UI documents).
- Isolated unified-plugin startup/shutdown smoke: PASS.
- Two deployed startup/restart cycles: PASS.
- Connected client visual/interaction verification: PENDING.

Deployment:

- Artifact: `Hywind.jar` (`0.1.0-merge.10`, `R055`).
- SHA-256: `55F571F58F26199A0B5058F5AAF06EE5C1BD85215856FDCDBAEEDE6F5B41FEC0`.
- Installed path: `C:/Users/Zemio/AppData/Roaming/Hytale/data/pre-release/Saves/RPG/mods/Hywind.jar`.
- Rollback: `C:/Users/Zemio/OneDrive/Documents/GitHub/Hytale-rollback/hywind-deploy-20260920T021817Z`.
