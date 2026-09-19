# R155 — Appearance Editor static-resource safety repair

2026-09-05. Baseline: R154 / `eca3b6a1b499a286b1ffcde304539aaeb1aa8252`.
Status: **implemented, deterministic PASS, deployed; connected approval pending**.

R155 is a critical client-resource containment build. It removes both previous
cosmetic-card image paths from the production JAR: the 590 pre-baked reference
cards and the R154 per-viewer color-responsive texture generation. The editor
now presents a bounded 5 x 4 page of static category glyphs and readable
cosmetic names while retaining the single authoritative 3D character preview.
No Profile Editor polish or unrelated backend work is included.

## Connected failure evidence

The supplied screenshots show shared UI resources being replaced by white/blue
texture strips: header chrome, category buttons, color swatches and action
buttons were corrupted at the same time. That is materially different from an
incorrectly rendered cosmetic card and is consistent with shared atlas pressure
or invalidation.

Local client evidence confirms an atlas-capacity failure in
`2026-09-05_10-29-33_client.log`:

```text
Texture atlas needs 4096x32768 but the maximum is 4096x16384; dropping 194 of 1093 images
```

The message appeared twice. Direct3D 11's documented maximum 2D texture
dimension is 16,384, matching the client diagnostic limit:
<https://learn.microsoft.com/en-us/windows/win32/direct3d11/overviews-direct3d-11-devices-downlevel-intro>.

The newest inspected local log,
`2026-09-05_10-53-09_client.log`, also records 112 R154 private Hair-card
textures resident for one page UUID before an `AssetUpdate` removal of slots
1-112. A remove request does not demonstrate that the client atlas was
immediately compacted or reclaimed. No GPU-temperature, fan-speed or power
telemetry was captured, so R155 makes no claim that atlas pressure caused the
reported fan behavior.

## Production architecture

- `NpcAppearanceCatalogService.PAGE_SIZE` is 20 and every catalog response is
  bounded to 20 descriptors.
- The markup owns exactly 20 fixed card hosts in a 5 x 4 viewport.
- Each populated card uses a packaged static category glyph plus the cosmetic's
  readable display name. Empty hosts are hidden.
- Previous/next controls perform real server-side pagination; all catalog
  entries remain reachable without mounting all entries at once.
- Color changes still update the authoritative draft and the central live 3D
  preview. They do not create, recolor, upload or replace card textures.
- `AppearanceColorCards`, `AppearanceCardJobs`, and
  `PrivateAppearanceCardAssets` were removed from production code.
- The 590 baked cards, their dynamic color material sources, and their runtime
  UI include are absent from the production JAR.
- `AppearanceUiAssetBudget.PRODUCTION` enforces 20 visible cards and zero
  dynamic images, pixels, or bytes.

The retired R151-R154 resources remain source-controlled under
`tools/retired-appearance-r154/` for offline investigation only. They are not on
the runtime resource/class path and cannot be sent to a client by this build.

## Bounded updates and preview work

`AppearanceUiState` maintains stable hashes for the visible catalog page,
selected cosmetic, selected color, draft skin, and preview. Unchanged `Set`
commands are removed before an incremental update is sent. Category, search,
selection, color, variant and page-boundary no-ops are suppressed.

The fixed card hosts remain mounted during normal interaction. Full page
rebuilds are reserved for initial editor construction; selection and color
changes update only changed properties. Palette controls are rebuilt only when
the palette identity changes.

`AppearancePreviewGate` permits at most one queued preview job per editor
session. A newer request replaces the pending callback, stale callbacks are
cancelled, and a preview whose stable hash already matches the applied state is
suppressed. Closing, saving, resetting or cancelling the editor cancels pending
preview work.

## Resource audit

| Resource class | Identity / mapping | Dimensions and volume | Lifecycle evidence | R155 disposition |
| --- | --- | --- | --- | --- |
| R151 packaged reference cards | `Thumbnails/<stable-hash>.png`; index mapped cosmetic IDs to hashes | 590 cards at 184 x 298 source / 92 x 149 display; 4,780,599 bytes including metadata | Files dated 10:38:16-10:38:44; packaged with the JAR; per-card client atlas admission and last-use time were not exposed | Quarantined under `tools/retired-appearance-r154/Thumbnails`; absent from JAR |
| R154 color source/mask images | Stable source hashes and `index.json`; base/mask pair where present | 1,370 PNGs plus metadata; 8,894,449 bytes | Files dated 10:38:27-10:39:04; server-side renderer inputs, not intended as direct Custom UI images | Quarantined under `tools/retired-appearance-r154/appearance-color-sources`; absent from JAR |
| R154 private color cards | `UI/Custom/Pages/ImmersiveNpcAppearance/Live/<page-uuid>/<slot>.png` | 92 x 149; exact encoded byte counts not surfaced by the server contract | Newest log records Hair/Black generation 8, 112 resident names, and later removal of slots 1-112 for page UUID `c75af104-42f1-455e-bc71-92254ef2289e`; exact cosmetic-to-slot mapping and atlas reclamation are unavailable | Generator, packet construction and resource namespace removed |
| R155 card visuals | Static category icon already packaged in Appearance metadata plus server-set text | No new image and no per-card image packet | Reused for all cards in its category; normal Custom UI property update only | Production path |
| R155 runtime-generated images | None | 0 images, 0 pixels, 0 bytes | Creation is rejected by the production budget | Production invariant |

The Hytale 0.6.3 `PlayerSkinPart` server-visible contract exposes identity,
model, textures, variants and tags but no verified thumbnail/icon property.
Consequently R155 uses the safe text/category-glyph priority rather than
guessing at an undocumented client-owned part-preview binding.

## Telemetry and degradation

Appearance telemetry now records page/session-scoped events for:

- `APPEARANCE_UI_ASSET_CREATED` (always zero under the production policy);
- `APPEARANCE_UI_ASSET_REUSED` and `APPEARANCE_UI_ASSET_RELEASE_REQUESTED`;
- `APPEARANCE_UI_PAGE_RENDERED` and `APPEARANCE_UI_PAGE_UNLOADED`;
- preview scheduled, coalesced and applied counts;
- suppressed full rebuilds.

Events include visible-card count, dynamic image/pixel/byte totals, page and
category, reuse/discard counts, active/pending/cancelled preview work, duration,
and degraded state. Static asset telemetry explicitly reports
`sentNewAsset=false`.

If a server-visible status reports atlas/capacity corruption, the editor latches
a degraded state, preserves the draft, stops optional appearance UI work, and
reports that the client must restart before another attempt. Hytale does not
expose remote client logs to this server plugin, so production code cannot
reliably detect the atlas string for another player's client. For local
development only, `tools/check_hytale_appearance_atlas.ps1` scans the newest
client log and exits nonzero when a known atlas-capacity signature is present.

## Deterministic verification

`test.ps1 -SkipLive` passed in full, including:

- the 8,100-case inventory/gear matrix;
- all lifecycle, persistence, appearance, voice and R148/R149 stat suites;
- R151 catalog reachability with 20-entry page bounds;
- R153/R154 regression replacements proving dynamic card machinery and
  resources are absent;
- R155 stable-hash diff suppression, preview coalescing/cancellation, zero
  dynamic-asset budget, fixed static card selectors, and release-resource
  validation.

Candidate artifact:

```text
dist/ImmersiveNPCs-0.6.3-R155-NPC-APPEARANCE-STATIC-SAFETY.jar
SHA-256 0DB4FB007164A97347C518BA65C68D3C0992791A99B0A8E203213000DE11E422
size 3,536,315 bytes
entries 3,316
packaged PNGs 91
baked cosmetic cards 0
dynamic color materials 0
dynamic card classes 0
```

Deployment verification:

- exactly one active project JAR is present in the world `mods` directory;
- its SHA-256 matches the tested candidate above;
- the deployed archive contains 3,316 entries, 91 PNGs, and zero unsafe card
  resources/classes;
- all 76 profile files retained the same aggregate SHA-256 fingerprint before
  and after deployment (`005766FED106970477ED657E187BD451247CF0EC501AF8A31CC4A6620C584C58`);
- R154 was moved intact to
  `C:/HytaleRollback/NpcAuthoringStudio-Appearance-R154-2026-09-05/`
  (SHA-256 `AEE75003EF5D0332D62C89EC1237DE037AA8154CE29A2F851D8BB4774378CB9E`).

## Connected validation checklist

Start from a fully closed Hytale client so the already-corrupted atlas is not
carried into the R155 test.

1. Start Hytale and enter the test server. Confirm the startup notification says
   `R155-NPC-APPEARANCE-STATIC-SAFETY`.
2. Run `/npc update Hoit`, open Appearance, and verify exactly 20 or fewer cards
   are visible in a 5 x 4 page with category icons and readable names.
3. Traverse previous/next pages and every category. Confirm all cosmetics remain
   reachable and no unrelated UI chrome, swatches or buttons become striped or
   replaced.
4. Change colors rapidly, change cosmetics, search, clear search, paginate, and
   randomize. Confirm cards remain static while the central character preview
   follows the latest draft without visible stale-preview churn.
5. Save, discard, back out, close/reopen, and restart. Confirm the existing
   authoritative appearance behavior and player restoration are unchanged.
6. Exercise Inventory, Profile Editor, Voice Recorder, hotbar, chat, and another
   Custom UI page after heavy Appearance use. Confirm no shared UI corruption or
   unusual accumulating lag.
7. Inspect server telemetry for zero dynamic image/pixel/byte totals, bounded
   visible cards, stable page load/unload pairs, and coalesced preview counts.
8. Inspect the fresh client log for `Texture atlas needs`, `dropping ... images`,
   invalid Custom UI texture, or atlas-capacity errors. Any such line is a
   connected failure; stop Appearance work and restart the client before a
   further attempt.

R155 is not a connected PASS until this checklist is completed by the tester.
