# R062 Skill Tree search and layout correction

Status: **IMPLEMENTED / PACKAGED / DEPLOYED / CONNECTED VERIFICATION PENDING**

R062 applies the connected-QA corrections without changing the authoritative graph, loadout, progression, or persistence contracts:

- inspector rows now reserve enough height for wrapped Effect, Cost, and constraint content, preventing text from crossing section dividers;
- the existing native `CanvasGraphSearchPage` remains the keyboard-input owner, but visually overlays only the Library search field with an active cyan frame and visible `X` control;
- Enter, `X`, safe outside clicks, and result selection finish Search Mode while preserving the query, active tab, scroll position, Inspector selection, node positions, and graph topology;
- selecting a filtered result carries it into the Inspector and immediately restores graph ownership; first Escape dismisses Search Mode back to the graph through the native page lifecycle;
- the native Inventory selected-tab ornament is rendered at its natural logical size and aligned flush with the header instead of being non-uniformly stretched;
- Skill ports are fixed to the bottom center, Joint ports are fixed across the triangle's straight edge, and only Passive ports retain destination-facing orbit behavior during an active drag;
- the owner-authored `skilltree_port.png` replaces the gold triangle connector marker and is hash-gated in the packaged resources; and
- confirmed Reset still clears only equipped Skill/Passive content and links, but now also removes stale port-anchor metadata and restores the canonical two-Joint, three-Skill, six-Passive presentation layout.

Validation completed:

- `gradlew.bat clean check` — PASS;
- 2,370 RPG JUnit tests and 47 CanvasUI tests — PASS;
- 59 CustomUI documents — PASS;
- 34 Skill Tree asset hashes — PASS;
- unified package audit — PASS (5,862 entries, 2,207 classes, 2,090 UI documents);
- isolated unified-plugin smoke — PASS;
- deployment dry run and candidate/install hash equality — PASS; and
- two deployed startup/restart cycles — PASS, with Hywind and Taverns started, clean shutdown, no legacy project plugin discovered, and all persistent data roots retained.

Artifact: `Hywind.jar` (`0.1.0-merge.17`, `R062`)

SHA-256: `1FFD58CB854AE36EDC4EFD11A1304B3A99DDB338F49FC034EEC9A23BE3A49A1C`

Implementation commit: `3160d525b529827cd5700199d3d3e5be436b1d9d`

Rollback: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T160051Z`

Connected-client verification remains pending for final rendering, search focus/exit input behavior, connector placement, canonical Reset layout, and regression coverage of the existing graph interactions.
