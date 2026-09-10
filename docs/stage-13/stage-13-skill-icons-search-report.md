# Stage 13 Correction R — skill artwork and Skill Tree search

Baseline: RPG branch, Q commit `86e82dd8b767b5d192f698f8364e17bcaea8d2bf`.
Correction badge: **R032-R**, RPG plugin version **0.0.25**.
Scope: the two supplied skill icons and the static Skill Tree search/presentation.
No combat, targeting, input projection, native resource ownership, persistence,
XP artwork or gameplay formulas were changed.

## Diagnosis and implementation

### Search input was discarded by the page event codec

`RpgSkillTreePage.bind()` emitted the dynamic search property as
`"@Value": "#SearchInput.Value"`, but `Data.CODEC` decoded `"Value"`.
The client-substituted query therefore did not populate `Data.value`. The
projection received its empty default. This is a concrete mismatched-key
defect, not evidence of a native input capability limit.

The codec now reads **@Value**. A test constructs the actual page binding
with the installed native UIEventBuilder, substitutes the text exactly at
that key and decodes through the actual page codec. It tests Firebolt,
Quick Slash, Potency and an empty query. This proves server-side
binding/decoding consistency, **not connected typing delivery**.

The production projection also accepts compact, spaced, hyphenated and
underscore spellings of catalog names, case-insensitively. Thus `firebolt`,
`Fire Bolt` and `fire_bolt` find Fire Bolt; likewise Quickslash. Existing
description/keyword search, weapon filters and ownership gates remain intact.
Search is read-only: it never changes a player's equipped content.

Both tabs now have an explicit label above the same input: **Search skills**
or **Search passives**. Switching tabs or selecting a tree node clears the
query so a skill query does not silently hide the Passive library. Ordinary
typing updates do not rewrite the input value. Persistent page controls
are bound once when the page is built, rather than rebound on every search
keystroke; replacement library/filter rows still receive their own bindings.
The library viewport is reduced by the label's 20 px to retain its panel bounds.

### Original icons, native background, no custom ability HUD

Source artwork:

- `art/Skills/SkillFirebolt.png`
- `art/Skills/SkillQuickslash.png`

Both are **128×128 PNGs with alpha**. They are copied byte-for-byte: no
redrawing, recoloring, cropping, background removal or compositing into the
source PNG. Their intrinsic gray/white artwork remains exactly as supplied.

The corresponding native bridge Item assets change **only their Icon field**:

- Fire Bolt: `Icons/Items/RPG/SkillFirebolt.png`
- Quick Slash: `Icons/Items/RPG/SkillQuickslash.png`

These resolve under packaged `Common/Icons/Items/RPG/`. Hytale continues
to draw its own native ability background, frame, key binding, error states
and cooldown visuals. There is no additional RPG-owned overlay HUD.
Cost=0, CostType=None, Cooldown=0 and the existing native Cast root remain
unchanged. Native ability gameplay authority is unchanged.

CustomUI uses identical copies under
`Common/UI/Custom/Icons/RPG/`, giving UI-relative paths without relying on
client-install filesystem access. `RpgSkillIcons` maps canonical skill IDs
to these paths. The library row previously carried an unused iconPath and
always displayed a Tornado placeholder. It now renders the projected icon.
Equipped skill nodes and the details panel use the same mapping; other
skills/passives retain their placeholder pending actual supplied artwork.
Empty skill slots hide their icon preview.

Each Skill Tree icon surface layers:

1. Shipped `Background_Ability_Ready.png`.
2. Transparent icon overlay, inset inside the slot.
3. Shipped `Frame_Ability_Ready.png`.

The background/frame were copied unchanged from this installed build's
`Client/Data/Game/Interface/InGame/Hud/Abilities/Assets/`. Its sibling
`Ability.ui` establishes this same background → icon → frame composition.
These are packaged in an RPG-specific CustomUI namespace, not overrides of
native HUD documents. Reusing native artwork is not claiming that the static
Skill Tree preview has live native cooldown/error state.

### Source SHA-256

| File | SHA-256 |
|---|---|
| SkillFirebolt.png | 8B4E285602E6A13A37A594F9E23B4C53E8A3AF0A0BC403D0083553910D6A7C7C |
| SkillQuickslash.png | D3DBBBF03497A5CE845A48FE6F816955934271C668A6461F0D44CD1AEC077EC1 |
| Background_Ability_Ready.png | BD6479423B1AC5AFEFA70DB19A37BE9B0A9C1CC0500E87E0B8AAFB17CE2CD556 |
| Frame_Ability_Ready.png | E58BD509B7CB138C5875348747DEAC3251EDEF44BD6D0095D6D2257AAAF076FE |

## Validation

Eight new regressions cover real event binding/codec decoding, compact skill
search, passive search/clear, equipped/library icon projection, real page
command generation, no persistent-control rebinding during incremental
render, native Item icon paths and zero gameplay costs, PNG dimensions/alpha/
source hashes, and background/overlay/frame ordering.

Two existing assertions are updated for explicitly requested presentation:
the formerly universal placeholder now permits the two actual icons while
retaining placeholders for other entries; the badge expectation advances
from Q to R. No retained test case is deleted or renamed.

Implementation checks caught and corrected use of `set` instead of
`setObject` for native PatchStyle serialization. The new real-UI tests also
required Hytale's logger to initialize first. They run in the existing
`nativeControlTest` JVM with its HytaleLogManager setting; they are not
skipped. All eight pass there. This is test-runtime setup, not a change to
production logging or trace levels.

Final full-suite, exact-JAR smoke, packaging/deployment results and hashes
are recorded below. Connected UI QA remains required.

The first isolated server rejected both native Item assets before startup:
`Common Asset 'Icons/Skills/RPG/...' must be within the root:
[Icons/ItemsGenerated/, Icons/Items/]`. The source PNG existence test alone
did not detect this native semantic constraint. The corrected Item paths are
`Icons/Items/RPG/...`, with unchanged source bytes and CustomUI paths.
The rejected candidate was never deployed; its exact hash and server failure
are retained under `evidence/stage-13/cohort-r/initial-asset-validation/`.
The corrected candidate must pass the real native loader, not just the local
path check. The full retained suite is rerun for that final candidate.

## Connected checklist

1. Restart/rejoin RPG with R032-R in the top-right corner.
2. Open `/rpg skilltree`. Under Skills, type `firebolt`; confirm the list
   narrows to Fire Bolt and shows the supplied artwork.
3. Clear the field, then type `quickslash`. Confirm Quick Slash appears.
   Also try `Fire Bolt` and `QUICK_SLASH`. A nonsense query should show
   zero entries; clearing it restores the library.
4. Click each entry: verify the details icon changes. With the skill equipped,
   verify its icon also appears above its matching tree node.
5. Switch to Passives: confirm **Search passives**, an empty search input,
   and restored passive entries. Search `potency`, then clear it.
6. Close the page. Confirm equipped Quick Slash/Fire Bolt use the new icons
   within normal native skill-slot backgrounds; cooldown/error visuals still
   belong to Hytale. Do not interpret preview rendering as casting proof.
7. Reopen the page and repeat typing/clearing/tab changes. Check for lost
   focus, duplicated actions, missing textures or CustomUI disconnects.
   Rejoin once more and verify assignments persist unchanged.

Capture `SKILLTREE_INTERACTION` events in `ui-trace.jsonl` for actual typed
queries and screenshots for artwork/layering. Server-side tests and isolated
smoke cannot certify client rendering, focus behavior or input delivery.

## Remaining issues and disposition

### Affected implementation files

- `ui/skilltree/RpgSkillTreePage.java`: dynamic value codec, static binding
  lifetime, search label/query reset, actual icon commands.
- `ui/skilltree/RpgSkillTreeProjectionService.java`: separator-insensitive
  name matching and canonical ID-based icon projection.
- `ui/skilltree/RpgSkillIcons.java`: the two explicit presentation mappings.
- `ui/skilltree/StaticSkillTreeViewModel.java`: equipped-node icon path.
- `Common/UI/Custom/RpgSkillTree.ui` and `RpgSkillTreeLibraryRow.ui`:
  search label, native-art icon layers and equipped previews.
- Two `RPG_Ability_*.json` Item assets: Icon-only changes.
- `RpgHud.java` and `Phase00RevisionHud.ui`: Q → R badge only.
- Six packaged PNG copies: two native Item icons, two identical CustomUI
  overlays, and two native background/frame textures.
- `Stage13SkillTreeIconsSearchTest`, two retained presentation assertions,
  `build.gradle` test-JVM routing, and cohort packaging/smoke/differential tools.

Q's reported post-lethal execution exceptions and encounter Inspect/persistence
uncertainty are **not fixed by this presentation correction**. No uncertain
state was cleared, reward gate bypassed or live save edited. Review Q's report
and new bounded exception telemetry separately. Formal connected performance
and other Stage 13 gates remain unresolved; **Stage 13 is not PASS**.

Owner art remains in the GitHub checkout, unchanged. No Google Drive writes.

## Final validation and deployed artifact

Completed September 9 local / September 10 UTC. **2,145 tests passed**:
2,079 RPG tests + 45 native-control tests + 21 retained CanvasUI tests;
zero failures/errors/skips and every Q baseline test case retained.
The coherent final candidate used:

```powershell
.\gradlew.bat :test :nativeControlTest :canvas-ui:test build --rerun-tasks --console=plain
.\tools\Run-Stage13CohortSmoke.ps1 -Cohort r -NativeProjectileSpawnAudit
.\tools\Publish-Stage13StartupHotfix.ps1 -Cohort r
.\tools\Verify-Stage13CastingPackage.ps1 -Cohort r
.\tools\Publish-Stage13StartupHotfix.ps1 -Cohort r -Deploy
```

The final native smoke loaded the exact JAR, accepted the Item assets,
booted networking, validated retained family assets, spawned the actual native
Fire Bolt carrier, verified pending rollback and stationary lifetime expiry,
then shut down with exit 0. The subsequent complete rebuild produced the
**same SHA-256** as that smoke-tested JAR. This remains isolated native proof,
not connected-client UI/casting proof.

The packaged ten CustomUI documents pass the retained static syntax audit.
This audit does not execute the client parser. The JAR differential contains
only the allowlisted icons/search/badge files and directory entries. Every
other JAR entry is byte-identical to Q. Both Item JSON documents are compared
to Q after removing their Icon field; all remaining fields match. Both sets
of packaged skill PNGs match the owner source files by SHA-256.

The three-mod archive's entry hashes passed. Isolated atomic rollback to Q
and roll-forward to R passed. Retained persistence, escrow, crash/fault,
archived-reader and 64-update durability-load tests remain passing; formal
connected p95/p99 qualification is not inferred from them.

Deployment atomically replaced **only** `HytaleRPG-0.0.25.jar` in
`C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods`.
The server-stopped check passed. CanvasUI and HytaleDevLib hashes are unchanged,
there are still exactly three mods, and all **18 non-JAR mod-data files**
matched before/after deployment. No live save edits. Q is retained in
`evidence/stage-13/cohort-r/rollback/`.

| Artifact | SHA-256 |
|---|---|
| HytaleRPG-0.0.25.jar | 8D0A2B286839B433D38DF436C0ADE0B307E1FB38B2653F251D387928BF1488F0 |
| CanvasUI-0.1.0.jar | 218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6 |
| HYTALEDEVLIB-0.5.0.jar | DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230 |
| Hytale-RPG-Stage13-R-skill-icons-search.zip | B42ABEEFE9BD9C3AA7C89104BAA3D275F1231A3ED5A062852790ED610C1A23FC |
| Q rollback RPG JAR | D811E68C7D8E2DE1421EC1E893F597B5DE0B979D5DEDE4470DB430768D0DED73 |

Evidence: [deployment manifest](../../evidence/stage-13/cohort-r/skill-icons-search.json),
[retained test identities](../../evidence/stage-13/cohort-r/test-results.json),
[full validation](../../evidence/stage-13/cohort-r/full-validation.txt),
[JAR differential and rollback](../../evidence/stage-13/cohort-r/jar-differential.json),
[native smoke](../../evidence/stage-13/cohort-r/server-smoke-summary.json),
[native spawn regression](../../evidence/stage-13/cohort-r/native-spawn-integration.json).
