# R154 — bounded client-atlas repair

2026-09-05. Baseline: R153 / `dd68cac5fb50ba38a0d57d2caf63b64168f3dd18`.
Status: **implemented, deterministic PASS, deployed; connected approval pending**.
R153 connected validation **FAILED**. No Profile Editor polish or backend rewrite.

## Observed failure

Both supplied screenshots were reviewed. The Hair screenshot shows white/blue
strips replacing the frame/header, category icons, color swatches and action
buttons while cosmetic pictures still render. The Pants screenshot has intact
chrome and color-responsive purple cards. This is not simply a wrong garment
gradient: shared UI texture resources were being dropped.

Client log `2026-09-05_10-29-33_client.log`, lines 2917 and 3333, at local
10:30:08.0239 and 10:30:13.0065:

> Texture atlas needs 4096x32768 but the maximum is 4096x16384; dropping 194 of 1093 images

Server log `2026-09-05_10-29-39_server.log` confirms R153, 16 card batches with
975 card references, up to 226 retained live names, and repeated whole-category
updates with unchanged Black selection. The client logged 19 asset-update
completions; their reported durations totaled 98ms with a 19ms maximum. These are
not comprehensive frame-time measurements, but repeated rebuilds are a plausible
lag contributor. No `NPC_APPEARANCE_PRIVATE_CARDS_FAILED` server entries appeared.
The run ended with orderly shutdown, not a demonstrated server crash.

R153 bounded server PNG cache memory and name count but did not adequately budget
the client's shared UI atlas. Its two retained banks and 2x-resolution references
were excessive, and publish unconditionally requested a rebuild even when every
asset hash was unchanged. Deterministic server cache tests missed that constraint.

## Focused repair

- Keep the fixed **184×298 geometry/material bake** and all 685 source buffers.
  Recolor at that size using the verified native exact-gray gradient rule. Then
  reduce to **92×149**, the existing displayed card dimensions, for client upload.
  Runtime reduction averages 2×2 pixels with alpha weighting; reference reduction
  uses Pillow BOX. The 590 packaged reference cards are also 92×149. Source
  provenance records bake and client dimensions separately. This deliberately
  trades 2x client texel density for atlas headroom, not a UI size/layout change.
- Replace two live banks with **one stable slot bank**. Remove names no longer
  needed by the new category/filter result. At most 128 names, only 112 for the
  largest current category, instead of retaining previous categories/two banks.
- Compare content hashes before sending. Identical batches emit **zero asset
  packets and zero rebuilds**. A single changed card emits one native asset
  triplet and one rebuild; removals and uploads share one final rebuild.
- Coalesce rapid selections for **180ms** before starting render work. This is an
  input debounce, **not** a guessed client-ready delay. Latest-generation checks,
  bounded pending work and Back/dismiss cancellation remain in place.
- Validate PNG signature and 92×149 header dimensions before any private send.
  Build validation rejects oversized packaged references, even if hashes match.
- Log `uploaded`, `removed`, `atlasRebuild`, `liveTexels`, `clientCard` and
  `coalesceMs` in the existing private-card diagnostic for connected review.

Maximum raw card texels fall from **46,387,872** (590+256 at 184×298) to
**9,842,344** (590+128 at 92×149): approximately **79% smaller**. Packaged reference
cards account for 8,087,720 texels; maximum private cards 1,754,624. All **681**
project-owned UI PNGs total **9,894,304** raw texels, or **11,648,928** including
maximum private cards. Native UI, other mods, atlas padding/packing and client
allocation remain outside this measured project budget; this is not a claim to
predict the final full-client atlas dimensions exactly.

Unchanged: layout/chrome, 590-option catalog, category rigs/crops, native gradients,
variants, search/scrolling, palettes, selection, randomize, large live preview,
draft/save/discard and persistence, stable NPC identity, R149 stats, inventory/gear,
voice/profile editing/cognition and player restoration. No client executable or
global input changes. Private assets still go only to the owning player; no
global registration or broadcast, no authoritative player/NPC mutation.

## Verification

- Full `test.ps1 -SkipLive` **PASS**, including 8,100 conversation scenarios,
  zero stale commits/malformed actions/leaks, R149 stats, appearance/voice/gear
  regressions and the R153 private delivery/cleanup gates.
- New `R154AppearanceAtlasBudgetTest` **PASS**: all 590 client sizes, old/new texel
  budgets, 100 identical batches with zero packets, single-card upload, stable
  paths, 112→2 category eviction, empty results, malformed/oversized dimensions,
  rapid-request coalescing and cancellation before render.
- `tools/test_appearance_category_rigs.py`: **4 tests PASS**. Geometry remains
  184×298; packaged client textures are 92×149. R153 high-resolution contact sheets
  remain valid composition references; R154 raw bake sheets are build-only QA.
- `tools/test_appearance_color_sources.py`: **PASS** for all 590 cosmetics / 685
  sources, hashes, native material masks, trim/context/alpha preservation. Only
  the geometry-baker fingerprint changes in the material index; source PNGs remain
  byte-unchanged. Regenerated fallback reference hashes/provenance are committed.
- All HUD/build/installer/manifest counters identify R154. `git diff --check` PASS.

## Deployment / rollback

- Active: `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R154-NPC-APPEARANCE-ATLAS-REPAIR.jar`
- **16,428,537 bytes**, SHA-256
  `AEE75003EF5D0332D62C89EC1237DE037AA8154CE29A2F851D8BB4774378CB9E`.
- Exactly one active project JAR. Client/server stopped during staged replacement.
- Failed R153 retained for recovery/diagnostics:
  `C:\HytaleRollback\NpcAuthoringStudio-Appearance-R153-2026-09-05\ImmersiveNPCs-0.6.3-R153-NPC-APPEARANCE-LIVE-COLOR-CARDS.jar`,
  SHA-256 `1B2BEF774DA98DD65046A2A7A078393FA563775B9B436691C9DB77F3DC457B54`.
- Pre-live-refresh R152 and accepted R146 rollback remain intact; no older
  rollback, SkinSwap or HYTALEDEVLIB replacement was performed.
- Runtime profiles before/after **76 files / 50,335,199 bytes**, identical aggregate
  `93DC68D4FEB07DA5EED0036FCA09B685C53672E974A253031DBE683EFF4DF58F`.
- No installer/config reset, NPC/audio deletion, migration, or world edit.

## Connected validation — start with a full client restart

1. Fully quit Hytale, relaunch, and verify **R154** in the HUD. This avoids carrying
   an already-corrupted R153 atlas into the test; no manual cache deletion needed.
2. `/npc update Hoit` → Appearance → Hair. Switch hair and colors several times,
   including Black. All frame pieces, icons, swatches and buttons must remain
   correct; no white/blue substitute strips. Scroll through the last hair card.
3. Switch Hair → Eyes → Pants → Overtop → Hair. Select Purple/Green and rapidly
   click several colors. Cards should settle to the last selection after a brief
   debounce, with unchanged crop and correct trim. Repeated same-color option
   choices should log `uploaded=0 atlasRebuild=false` when card content is identical.
4. Back/reopen during updates, then close/reopen the Profile. No stale pictures,
   disconnect, accumulating textures or player appearance/equipment mutation.
5. Check 1920×1080 and 2560×1440 for acceptable card sharpness and clipping. Save,
   Discard, randomize, reopen/restart persistence remain functional spot checks.
6. Review the new client log for **zero** `Texture atlas needs ... dropping ...`
   warnings. Compare lag during fast changes; server logs now expose actual upload
   and rebuild counts rather than only requested-card counts.

Live refresh and shared-atlas rebuild stability remain connected gates, not an
automated approval. If this candidate still corrupts chrome or lags, **stop and
review alternatives with the operator** rather than widening the approach.
