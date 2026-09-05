# R153 — private, on-demand appearance color cards

Date: 2026-09-05. Baseline: clean main / origin main at
`8d602a4a3313783cecb69c99c4309b2b2dd584dc` (R152).
Status: **implemented, full deterministic PASS, deployed; connected approval pending**.
**Connected private asset refresh is not yet approved.** No Profile Editor polish.

## Diagnosis and verified native evidence

R151/R152 cards were static PNG references. Color selection updated the authored
draft / large preview, but could not change those PNGs. Their baker also treated
every texel's red channel as a gradient index, including already-colored trim.
That incorrectly recolored leather, metal and embroidery along with fabric.

Installed native `MainMenu/MyAvatar/MyAvatarPage.ui` defines the 92×149 card host,
5-column grid, 10px gaps and #2f3a4f background. Individual native part previews
are client-populated, not server-created NPC entities. The installed NativeAOT
client contains `PartPreviewComponent` (byte offset 29881915) and
`UpdatePartPreviewVisibilities` (30104258). No verified public server Custom UI
binding for arbitrary native part/model/gradient targets was found. This is not a
claim to have decompiled the complete native MyAvatar controller/call graph.

Exact embedded shader text at byte offset **30217644** verifies the material rule:

```
bool isGreyscale = (texel.r == texel.g) && (texel.g == texel.b);
if (isGreyscale && gradientId > 0) {
    ivec2 coord = ivec2(texel.r * 255, gradientId - 1);
    color.rgb = texelFetch(uGradientAtlasTexture, coord, 0).rgb;
}
color.rgb *= tintColor;
```

Client SHA-256:
`4947649CCFF38F37847F78250FC56226B4634FB9D1C4D362E14FCD18F93C55A5`.
Evidence source: installed 0.6.3 `Client/HytaleClient.exe`, not web speculation.
The supplied native Purple/Green screenshots demonstrate immediate card color
changes. Inference: the native client reuses part geometry with the current
gradient selection. The exact-gray shader rule itself is directly verified.

The operator explicitly chose **private on-demand colored thumbnails with a
bounded cache and connected live-refresh testing** over a large packaged atlas
or waiting for an undocumented native binding. R153 implements that fallback;
it does not pretend static PNGs are native client-owned 3D parts.

## Implementation and scope

1. `tools/bake_appearance_color_sources.py` exports **685** compact material sources
   for **590** cosmetics, including real variants and direct texture choices.
   Each source is a base RGBA image plus an RGBA mask encoding grayscale index,
   lighting and whether that visible pixel belongs to tintable chosen geometry.
   Neutral context and fixed trim are excluded. Winning depth-buffer pixels
   determine the mask, so hidden/occluded geometry cannot bleed through.
2. Native 256-column gradient LUTs are stored once in `palettes.json`. There is no
   cosmetic × every-gradient-color expansion (which would mean roughly 11,026
   first-variant combinations alone). Sources are server resources outside
   `Common/`, not automatically registered client UI assets.
3. `AppearanceColorCards` loads only closed packaged keys, verifies material and
   palette hashes, and reconstructs 184×298 PNGs in memory. Only grayscale fabric
   changes, with byte-quantized existing lighting (at most one RGB level error).
   There is no RGB approximation, whole-image tint, selected NPC portrait read,
   player avatar read, external render service, Python runtime requirement, file
   persistence or ImageIO disk cache. The R152 category cameras/context stay fixed.
4. `NpcProfilePage` snapshots current category, filtered option IDs, selected
   color and selected option's variant. All legal compatible cards in the current
   category get that same native color ID; unsupported colors retain their real
   reference and say so in their tooltip. Unselected cards use their default
   variant. Unknown mod options/variants retain the existing fallback. No
   fabricated assets/IDs/colors and no authority/data-format changes.
5. `AppearanceCardJobs` runs rendering off the world thread. One running job and
   one latest pending request per viewer; generation checks reject stale work on
   color/category/search/variant/randomize/rebuild/Back/close. Queued world delivery
   is coalesced; stale batches cannot touch newer card selectors.
6. `PrivateAppearanceCardAssets` sends only to the owning player's packet handler.
   AssetInitialize → AssetPart → AssetFinalize → RequestCommonAssetsRebuild precede
   the matching Custom UI PatchStyle update. These are the installed SDK's native
   common-asset packet sequence (verified in CommonAssetModule bytecode), without
   its WorldLoadProgress messages. No global CommonAssetRegistry registration,
   broadcasting or mutation of player/NPC skin, equipment, ECS or containers.
7. Two banks of fixed slots under an unpredictable session namespace avoid an
   unbounded list of live texture names. Back invalidates work immediately and
   queues private RemoveAssets after the replacement page removes card nodes.
   Reopening can repopulate the same bounded namespace. Dismiss/session cleanup
   closes jobs and releases owned assets idempotently, including partial sends.

Server bounds: **32** decoded base/mask pairs (~14 MiB), **128** cached PNGs and
**12 MiB** PNG bytes process-wide; **128** cards per batch, **128 KiB** maximum
PNG, **4 MiB** delivery batch; at most **256** resident private asset names per
authoring session. Larger mod categories keep normal fallback cards past the
private-card budget rather than truncating the actual catalog or UI.

Private means **not broadcast**, not confidential: rendered cosmetic PNGs reach
the owning client and Hytale may retain its own content-addressed disk cache.
RemoveAssets bounds live references; no claim is made that it erases the client's
disk cache. No NPC identity, authored JSON, voice or player equipment is encoded
in these generic cosmetic images. The server's bounded shared cache contains only
generic cosmetic/material combinations.

All 590 packaged reference cards were also rebaked with the correct trim rule.
They appear while private results load and remain the fallback on renderer failure.
Their provenance intentionally continues to identify the fixed R152 rig contract;
the renderer source hash changes to bind the R153 material correction.

Unchanged: layout/chrome, 184×298→92×149 card pipeline, search/scroll, option and
palette selection, randomize, draft/save/discard, cosmetics validation, appearance
materialization/persistence, stable NPC identity, preview/restoration, R149 stats,
inventory/gear, profile editor, voice/cognition and existing NPC data.

## Explicit live-refresh limitation / connected gate

Public packet and PatchStyle codecs compile and deterministic packet ordering is
tested. **No per-asset client-ready acknowledgment or verified hot-refresh timing
contract was found.** A rebuild request is not proof the client has rebuilt the
texture before the subsequent UI set. No arbitrary delay or false readiness flag
is used. Path resolution and texture replacement while an open Custom UI consumes
new private assets must be connected-tested on 0.6.3.

If connected testing shows missing/stale images or a Custom UI disconnect, stop
promotion at R153 and capture the client/server log plus `NPC_APPEARANCE_PRIVATE_CARDS`
generation/count. Do not silently label a representative-color card as the selected
color and do not claim this is a supported native PartPreview binding. R152 rollback
is retained. No renderer/backend rewrite or client executable modification occurs.

## Verification and reproduction

```
python tools/bake_appearance_color_sources.py <Assets.zip> src/main/resources/appearance-color-sources
python tools/test_appearance_color_sources.py --assets <Assets.zip>
python tools/test_appearance_category_rigs.py
.\test.ps1 -SkipLive -ServerJar <installed-release-Server/HytaleServer.jar>
```

The material test checks all 685 source dimensions/hashes/rigs; exact grayscale
predicate; trim/context/alpha preservation; and compares six reconstructed
Purple/Green Overtops with separately rendered tinted source textures (≤1 RGB
level). The Java regression renders the 590-entry catalog, exercises cache limits,
unknown/invalid choices, private packet ordering and cross-viewer isolation,
500 updates with bounded names, release/reopen, and blocked-render rapid changes
with stale category/Back/close rejection. Existing 1080p/1440p layout budgets stay
unchanged; native connected rendering is not claimed by those arithmetic tests.

Build validation additionally pins the material baker, geometry baker, native
source hashes, category rig hashes, palette and every base/mask PNG. Reports:
[40 grouped category sheets](R153_CATEGORY_CONTACT_SHEETS/README.md),
[Purple/Green comparison](R153_COLOR_COMPARISON/README.md).

## Exact connected checklist (1080p and 1440p)

1. Confirm the HUD identifies **R153**, join normally, `/npc update Hoit`, Appearance.
   No Custom UI load or set-property disconnect should occur.
2. Clothing → Overtop: select a colorable shirt. Switch Purple → Green → Purple.
   All compatible visible and scrolled cards must change without changing crop;
   the large draft preview must agree. Black leather, gold buckle, embroidery,
   skin and neutral underwear must not receive fabric tint.
3. Check Hair, Eyes, Undertop, Pants, Shoes, Gloves, Head/Face/Ear accessories and
   Cape. Fixed-color/unsupported choices must remain valid references, not guessed
   colors. Select a real variant and verify the selected card follows it.
4. Rapidly click colors, search, clear search and switch categories; no late old
   colors, wrong-option pictures, empty cards, clipping or disconnects. Scroll to
   the last option after a color change. Test an unavailable/degraded saved option.
5. Back during rendering → Studio, immediately reopen → correct fresh cards.
   Close the whole Profile during rendering; reopen another NPC. No stale result
   or player appearance/equipment change. If a second viewer is available, use
   different colors simultaneously and confirm independent cards.
6. Randomize, Discard, Save, close/reopen and restart. NPC appearance identity and
   persistence must behave as before. Spot-check stats, gear, voice playback and
   existing samples without deleting established NPC audio.

## Final verification and deployment evidence

- Full deterministic `test.ps1 -SkipLive`: **PASS** after fixing an ImageIO
  double-close caught by the new regression. Includes the 8,100-case conversation
  matrix, R149 persistent stats, appearance authority, inventory/gear, voice and
  new R153 private card gates. Only existing deprecated SDK/Unsafe warnings.
- Offline material integrity/reconstruction test: **PASS**; fixed category rig
  regression: **4 tests PASS**. Purple/Green Overtop sheet visually inspected.
- Source package: 1,372 files / **8,894,449 bytes** (685 base/mask pairs plus index
  and palettes). They are outside Common UI and not globally sent as live assets.
- Deployed JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R153-NPC-APPEARANCE-LIVE-COLOR-CARDS.jar`
  — **16,675,973 bytes**, SHA-256
  `1B2BEF774DA98DD65046A2A7A078393FA563775B9B436691C9DB77F3DC457B54`.
- Deployed ZIP checked: 590 reference PNGs, 1,372 server-only material files,
  zero prepackaged private Live assets. Exactly **one** active project JAR.
- R152 rollback: `C:\HytaleRollback\NpcAuthoringStudio-Appearance-R152-2026-09-05\ImmersiveNPCs-0.6.3-R152-NPC-APPEARANCE-CATEGORY-RIGS.jar`
  — **8,451,441 bytes**, SHA-256
  `DB6B87CFD9B49A813AC7E25C392AB9C13666B1990FF81C4616EDC3D93946F3F3`.
- Accepted R146 plus retained R149/R150/R151 verified unchanged, as were SkinSwap
  and HYTALEDEVLIB. Hytale client/server were stopped during replacement. No
  broad installer, config reset, runtime migration, audio deletion or world edit.
- Runtime profiles before/after: **76 files / 50,335,199 bytes**, identical sorted
  relative-path/content-hash aggregate
  `93DC68D4FEB07DA5EED0036FCA09B685C53672E974A253031DBE683EFF4DF58F`.
- Build, installer, manifest and visible HUD counters all identify R153.
- Reports and implementation are committed to main with the deployment record;
  remote equality is checked after push. Native private refresh/1080p/1440p
  connected behavior remains the operator's next gate, not an automated PASS.

**STOP for connected approval. No Profile Editor polish.**
