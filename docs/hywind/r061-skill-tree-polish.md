# R061 Skill Tree polish

Status: **IMPLEMENTED / PACKAGED / DEPLOYED / CONNECTED VERIFICATION PENDING**

R061 applies the requested native-menu polish without changing graph authority or persistence semantics:

- the `SKILL TREE` heading is larger and uses the installed Hytale Inventory gradient plus the selected-tab gold line/up-pointer overlay;
- Skill, Passive, Reset, Save, Exit, Yes, and No labels receive native-style hover highlighting;
- node moves and newly drafted links remain unsaved until `SAVE`; attempting to exit a dirty draft opens the centered `Exit without saving?` Yes/No confirmation;
- the focused search page now overlays the authored search bar exactly, and the HUD placeholder is hidden while that page is active, removing the displaced duplicate text;
- the center ornament uses a bordered texture composition across the full inner Skill Tree frame so its corners reach the frame without non-uniform distortion;
- the Library exposes sixteen rows;
- Reset confirmation is centered in the full screen; and
- successful Save displays a large centered green `SAVED` acknowledgement which fades through bounded HUD patches.

Two exact native Hytale UI assets are pinned into the package and hash-gated: `TabSelectedOverlay@2x.png` and `TextGradient.png`. No game-install asset is referenced at runtime.

Validation completed:

- `gradlew.bat clean check` — PASS;
- 59 CustomUI documents — PASS;
- 46 CanvasUI tests — PASS;
- unified package audit — PASS (5,861 entries, 2,207 classes, 2,090 UI documents);
- candidate/install hash equality — PASS; and
- two deployed startup/restart cycles — PASS, with Hywind and Taverns started and no legacy project plugin discovered.

Artifact: `Hywind.jar` (`0.1.0-merge.16`, `R061`)

SHA-256: `61C1887E7D9A2418BE96C2CCCEE947B22375C9DD2D9C8C2782525745DDDB473E`

Implementation commit: `abd9b4f47f63afa3efaed64080f571bc3017c17e`

Rollback: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T150038Z`

Connected-client verification remains pending for the title treatment, hover states, sixteen-row Library, focused search alignment, full-frame ornament placement, centered Reset/dirty-exit confirmations, draft discard behavior, and fading Save acknowledgement.
