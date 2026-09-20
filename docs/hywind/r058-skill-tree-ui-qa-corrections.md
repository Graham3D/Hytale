# R058 Skill Tree UI QA Corrections

Status: **IMPLEMENTED / PACKAGED / DEPLOYED / CONNECTED VERIFICATION PENDING**

R058 is a bounded correction pass over the existing R052/R053 Skill Tree graph, persistence, and interaction architecture.

## Corrections

- The native primary-frame border now encloses the header, Library, Skill Tree, Details, instructions, and footer controls as one dominant window. The title uses Hytale's native container title style and is centered against the full frame.
- The center ornament is rendered at a fixed, uniform aspect ratio over a separately framed dark workspace rather than being stretched with non-uniform borders.
- Right-click link removal once again freezes the exact edge identity behind a `Break Link?` Yes/No confirmation. Removing a link that would detach an otherwise valid presentation branch removes only the selected visible spline while preserving the remaining dormant edge identities and port bindings.
- `RESET` is centered under the Library. Its `Reset Skill Tree?` confirmation atomically clears equipped Skill slots, Passive slots, inactive equipped passives, and links only. Learned Skills, owned Passives, mastery, character progression, resources, and unrelated RPG state are retained.
- Connector arrows use eight exact rotated derivatives of the existing Hytale arrow texture and face outward from their owning node.
- While a connected node is actively dragged, its connected port anchors orbit the circle/perimeter toward their destinations and the spline follows continuously. The resulting anchors persist on drop and remain stable during stationary redraws and restart.
- Escape is handled by a transparent dismissible CustomUI lease and closes through the same exact-once service cleanup path as `EXIT`, including normal HUD/input restoration.

## Validation and deployment

- `gradlew.bat clean check`: PASS.
- RPG JUnit: 2,370; native-control JUnit: 67; CanvasUI JUnit: 44; Tavern JUnit: 5; zero failures, errors, or skips.
- CustomUI validation: 60 source documents; package verification: 2,091 UI documents, 5,861 entries, 2,208 classes.
- Skill Tree pinned-asset hashes: 31; owner-authored RPG icon hashes: 17.
- R058 isolated unified-plugin smoke: PASS.
- Deployment dry run: PASS.
- Deployed startup/shutdown and restart cycles: 2/2 PASS; Hywind and Taverns initialized, no superseded first-party plugin was discovered, and all four persistent data roots remained present.

Artifact: `Hywind.jar` (`0.1.0-merge.13`, `R058`)

SHA-256: `931071DF264A2BA2C7EFBBEFE2EEA8167A89AA57EAA627760ED8D9FA69FC075F`

Implementation commit: `56d368cec533eca28fce67c7041010a8357e4ca8`

Installed path: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\Hywind.jar`

Rollback: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260920T131311Z`

Connected-client verification remains pending for visual composition, exact link selection/removal, Reset Yes/No scope, eight-direction arrows, active-drag port orbit/persistence, Escape cleanup, and all retained editor interactions.
