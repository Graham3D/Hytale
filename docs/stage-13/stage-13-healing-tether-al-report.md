# R032-AL — production endpoint-controlled Healing Beam

Date: 2026-09-12. Branch: `RPG`. Starting HEAD: `99aaab876fdd704a21960aab174cf859d94bf5fb`.

## Delivery status

**IMPLEMENTED · PACKAGED · DEPLOYED** to the normal RPG single-player installation at 2026-09-12T22:47:53Z.
CONNECTED-VERIFIED: **NO** for the AL rendering candidate. No local test or isolated server result is a claim of client rendering, smoothness, width, flow visibility or native packet delivery to a connected client.

This is an actual production renderer replacement, not another diagnostic-only cohort. Normal Healing Beam activation selects the new owner. The existing optional observer and eight legacy controls remain available, but no new probe commands, disposable-world workflow or gameplay bypass were added.

## Why this repair follows from AK

The owner confirmed AJ's eight standalone controls were visible, then confirmed that AK's real channel still looked like the old implementation. The normal RPG server sessions at `2026-09-12_18-12-25` and `18-15-34` loaded AK. Their channel observer saw production core model packets using `NPC/MISC/Empty.blockymodel` with `Beam_Heal_Green2`, plus native recipient/staff effect additions and removals. The first observer window captured one production channel and the last captured two. No core presentation exceptions were observed in those captures. The last window ended at disconnect rather than reaching its full observation deadline.

This separates delivery from geometry: the old model particle carrier was being delivered. Rotating a fixed particle effect at one anchor does not give its particles an authoritative second endpoint or a length contract. The owner-visible result, not a local asset audit, rejected that production appearance. Another control-only cohort would not repair this boundary.

The stale top-right AI label was a separate hardcoded badge oversight. AL now obtains the badge from the production renderer's `REVISION`, and startup/optional observer marker identify AL consistently.

## Installed native API and material audit

Pinned installed Hytale: **0.7.0-pre.2**, revision `b41721d651ef241809e402f6c3371781b2ea5f84`.

- HytaleServer.jar SHA-256: `9CDC1E77DADFDAD0B1D849977CDAD5C2752207F538272FB3D76D4E915546B25E`.
- Assets.zip SHA-256: `0E35DF2D4E4A803F7752CB300FA7C9FE7ACDF62B4311978DA95C7AFF7ABBC126`.
- `Beam`/protocol `BeamConfig` expose a texture path, not arbitrary shader, tint, UV-scroll or spline properties.
- `AttachedBeam.toPosition` supports source offset, exact target position and endpoint scale. `BeamComponent` supports a persistent list of up to 64 attachments. Native spawn supplies the non-serialized visual entity contract.
- The shipped Basic/Charged_Blue appearance was already rejected connected. Inspected Void_Green/Acid strips have jagged ribbon shapes; Rope is a rope material, not the requested soft healing stream. None was selected as the final appearance.
- `Beam_Heal_Green2` remains the color/appearance reference, not the production core transport. Its glow uses green `#72ff1f` fading toward `#0f7000`; its sparks include positive forward speed 4.

Two repository-owned, code-native SVG gradient strips provide transparent green edges and a bright pale-green center, without the old ribbon triangles. They rasterize at 128x16 RGBA, constant along X so repeated longitudinal texture sampling has no seam. `HealingCore.svg` and `HealingFlow.svg` are under `art/Generated`; `tools/Render-HealingMaterials.cjs` regenerates packaged PNGs using a development-only Sharp installation. No runtime library dependency was added.

The material is an adaptation of the selected green intent, **not a claim of pixel-identical Beam_Heal_Green2 appearance**. Client approval remains required.

## Production implementation

### Ownership and ECS boundary

`HytaleSkillExecutionSystem` now owns `HealingTetherPresentation`. It composes the native core owner and the existing cosmetic attachment owner in effects-only mode. That mode never creates the empty-model stream carrier. Legacy controls still use the old renderer explicitly for comparison.

Core and cosmetic submission have independent failure reporting and cleanup calls. A core submission failure does not prevent recipient/staff submission. The existing finite native recipient/staff leases remain unchanged; this repair does not replace them with persistent or serialized effects. Recipient shared-root cleanup and channel-only staff behavior remain covered by retained native checks.

Immutable frame jobs coalesce per cast. Structural ECS changes run through the command buffer after Store processing, not directly during the processing phase. Removal invalidates pending work before it can create an orphan. Each logical connection has **one persistent non-serialized Beam entity**, updated in place with native BeamComponent/TransformComponent changes. There are no new gameplay entities, persistence records or long-lived particle carrier models.

### Endpoint and motion contract

At rest the tether is the straight line between fresh authoritative anchors. Six core spans approximate a flexible strand while moving. Interior state uses an analytic critically damped response (`omega=16`), a 12-unit/s velocity clamp and a tapered displacement envelope `min(0.5, 0.08 * currentLength) * sin(pi*t)`. Endpoints are pinned on every update; there is no gravity term or permanent sag.

Clock reversal, gaps over 0.25 s, endpoint jumps over 4 units, and near-zero extent reset to a straight bounded state instead of running catch-up simulation. Coincident endpoints produce a zero-width native attachment, not stale geometry. Branch identity includes source/destination and each branch owns its motion history. A changed recipient under the same visual key replaces that segment's state.

All core pieces and moving highlights are attachments on the same logical entity. Core source offsets are relative to an unrotated, model-free native origin; target positions are explicit world positions. Each connection is bounded to 30 attachments (below native maximum 64), and each channel is bounded to six logical connections. Existing root/pending capacity bounds remain 512.

The primary and secondary visual anchors are refreshed every visual frame. Secondary membership and coefficients remain those selected by the authoritative pulse; visual refresh does not select extra heal recipients, pay extra pulses or change LOS/healing decisions. Missing secondary entities are omitted from presentation rather than drawing at stale positions.

### Continuity and directional flow

The core persists for the channel and does not periodically expire and respawn. Source-to-recipient flow is represented by two short brighter spans moving at 4 units/s along the current polyline's arc length. They wrap with bounded split intervals and never replace the continuous core. This uses supported endpoint geometry rather than inventing native UV animation fields or adding overlapping short-lived emitters.

The highlights' visual strength, apparent speed and 20 Hz update smoothness must still be assessed connected. No assertion here certifies that a particular client/GPU will render the intended continuous appearance without seams.

### Staff anchoring limitation

Native recipient `Effect_Health_Pack` and staff-head cosmetic leases remain in place. The core starts at the authoritative caster anchor, **not an exact animated staff-tip transform**. The installed Beam source-node interface does not establish attachment to the player's animated PrimaryItem staff-head node. No invented bone name or server-side simulated item-tip transform was introduced. This remaining limitation must not be confused with a solved exact staff-tip attachment.

## Scope preservation

No skill profile, coefficient, healing formula, equipment rule, target acquisition, Mana upkeep, cooldown, LOS grace, range validation, Arc/Fork/Chain selection, support credit, escrow, reward, progression, persistence or Blizzard change was made. The only ConnectionRuntime edit refreshes presentation-only secondary anchors. Native resource bars and ability slots are untouched; the sole HUD edit corrects the revision badge.

The JAR packager compares entries to the exact AK archive and rejects changes outside the explicit renderer, visual-anchor, renderer-asset, audit, revision-marker and badge allowlist. The native support asset guard now requires both exact AL texture paths; it was not disabled to permit a pass.

## Validation chronology and evidence limits

Focused implementation tests exposed two test-construction mistakes: use of a JOML interface field instead of its `x()` accessor, and retaining an alias of BeamComponent's mutable list when comparing before/after frames. Both were corrected in the new tests; no production contract was weakened.

The first isolated smoke stopped at `SUPPORT_NATIVE_BEAM_TEXTURE_MISMATCH` because the startup audit still required the rejected Void_Green texture. That failure is retained under `evidence/stage-13/cohort-al/attempts/01-stale-material-guard.txt`. The guard now validates the selected core and flow paths. The next native smoke passed the real production owner, persistent entity update/removal, native source offsets at 2/6/12/18/25.2 units, non-serialization and cancellation checks.

New regressions cover exact extent/continuous geometry, bounded forward flow, short/long movement envelopes, invalid/degenerate inputs, transparent seamless material dimensions, production wiring/revision, installed native attachment construction, and moving secondary anchors without additional healing. Retained gameplay, durability, escrow, exact-once, resource, cooldown, UI, tracing and CanvasUI regressions are preserved.

Final result and exact artifact receipts are appended below. Native audit lines explicitly say `connectedProof=false`.

### Final retained validation and deployment

One complete retained invocation ran all 2,269 tests (2,192 RPG, 56 native-control, 21 CanvasUI). It produced one failure: `Stage13PlayerFeedbackCorrectionTest.revisionBadgeIsTopRightWithoutNativeResourceControls` asserted that HUD source contained `BuildIdentity.REVISION`, the stale reference replaced by the approved AL badge correction. All other tests passed. The affected test now requires the renderer revision reference **and** its exact `R032-AL` value; positioning/no-resource-control assertions remain. The entire affected class passed a targeted rerun. No production code/JAR changed for that test correction.

The original failing full-run XML/log remains in `full-run-results` / `full-validation.txt`. The packager reconciles only that same named class, verifies identical test names/counts and a clean rerun, and rejects any remaining failure, error, skip or missing suite. Final reconciled XML under `validation` contains 2,269 passing tests. This is **one complete run plus one affected-class rerun**, not a claim that the initial invocation was wholly green.

34 CustomUI documents passed validation. The exact final JAR passed the isolated three-mod smoke and actual native production-owner checks. Binary rollback AK → AL → AK and every three-mod archive entry hash passed. The strict JAR diff preserves every entry outside the explicit repair allowlist. Fixture trace archive tests remain included; no connected trace qualification was fabricated.

| Artifact | SHA-256 |
| --- | --- |
| Built, archived and deployed `HyARPG.jar` | `899C80A31F7165456909EC33DD7BCBC0ECD1EE6843DF0120F2822B037BBAF87D` |
| `HyARPG-R032-AL-three-mods.zip` | `E21A990B10BD3B70539A3DD7FBAFAD2049251684D61282535D1316DEA9116809` |
| CanvasUI-0.1.0.jar (unchanged) | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| HYTALEDEVLIB-0.5.0.jar (unchanged) | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| Previous AK RPG JAR retained for rollback | `3F0A570703F4BEE8C86773E09D1F5FC985A7FAFB6DD42AD0584DBC4AAF0A64AA` |

Installed path: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`.

With Hytale/server processes stopped, the deployment script backed up **485 files / 291,994,004 bytes**, checked every backup hash, staged the new JAR, retired the old one and verified the installed hash. Backup: `evidence/stage-13/cohort-al/before/save/20260912T224749Z/RPG`; sibling `retired-live-name/HyARPG.jar` also retains AK. Private save backups are local-only/ignored by Git. Every non-target live file remained byte-identical, including NPC/mod data, progression, traces and the other three live JARs. Internal plugin/data identity remains `InigmasGames:HytaleRPGPhase00Audit`; only the public distribution filename is `HyARPG.jar`.

Evidence: `package-validation.json`, `deployment-validation.json`, `server-smoke-summary.json`, `server-smoke.txt`, `full-validation.txt`, `badge-targeted-validation.txt` and reconciled XML under `evidence/stage-13/cohort-al`. The archive contains exactly three framework/RPG mods; the existing ImmersiveNPCs JAR remains installed live but is not bundled into the archive.

**Live startup and AL connected rendering remain unverified.** The candidate did execute successfully in the isolated native server, but the owner's normal single-player world has not yet been restarted on AL. This task did not start the live world or modify it for a test.

## Connected acceptance still required

Use the actual equipped Healing Beam, not a legacy standalone beam control. See [AL connected checklist](healing-tether-al-checklist.md). Verify straight/continuous at rest; caster and target lateral, forward/back and vertical movement; simultaneous movement; reversal; smooth settling; a long channel without blinking; Arc/Fork/Chain independence; and no residue after release, target loss, death, range failure, world change or disconnect. Full-health targets must retain presentation while accepted healing may be zero.

Open limitations: exact animated staff-tip attachment; client-visible thickness/material/flow/segmentation; client cleanup appearance; finite cosmetic lease appearance; all connected AL visual acceptance. No unrelated Stage 13 performance/release gate is upgraded by this renderer repair.
