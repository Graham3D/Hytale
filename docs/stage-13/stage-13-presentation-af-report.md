# R032-AF — Healing Beam appearance correction

**IMPLEMENTED / PACKAGED / DEPLOYED. Corrected connected appearance = UNVERIFIED.**

2026-09-12. Branch `RPG`. Baseline `bc1bd0f` / installed R032-AE. This is a presentation-only correction prompted by the owner's screenshot of oversized, crossed cyan ribbons.

## Connected evidence: the ECS boundary now works

Reviewed `2026-09-12_13-47-51_server.log` and the latest `skill-trace.jsonl`. In the 17:48 UTC session there are four roots with matching `NATIVE_BEAM_STARTED`, `NATIVE_BEAM_UPDATED`, and `NATIVE_BEAM_REMOVED` receipts, and **zero native Beam failures**. AE's creation/update/removal correction is therefore supported by connected server evidence. The screenshot independently confirms that something renders, but the appearance is unacceptable. This does not certify every branch, movement case, or client cleanup condition.

The current failure is no longer `Store is currently processing` and does not justify another input, ECS, healing or persistence redesign. The earlier AC/AD report inference that a custom texture failed to render was not supported while native creation itself was failing. AF does not carry that inference forward as proof that `RPG_Healing` is unusable.

Evidence: `evidence/stage-13/cohort-af/connected-ae-evidence.json`, preserving timestamps, root/correlation identifiers and the source trace hash without changing the live trace.

## Presentation diagnosis and bounded change

Installed 0.7.0-pre.2 `Basic` selects `Trails/Charged_Blue.png`, a 256x96 broad charged-attack trail with large opaque shapes. Inspection of the actual PNG confirms it is not a narrow healing strand. The six-piece elastic tether repeats that material at each piece; the screenshot shows the resulting crossed, broad ribbons.

The installed native `AttachedBeam`/tracker code and [official API](https://pre-release.docs.hytale.com/api/com/hypixel/hytale/builtin/beam/AttachedBeam) distinguish source/target width multipliers from endpoint coordinates. Both AE width multipliers were 0.25. The native demonstration command and shipped Hookshot use that default, but copying a demonstration value does not establish suitable Healing Beam appearance. The shipped [Beam asset contract](https://pre-release.docs.hytale.com/assets/entity/beams/) tiles a texture along the beam.

AF makes only these presentation changes:

- Select the existing dedicated **`RPG_Healing`** asset. It already references shipped **`Trails/Void_Green.png`** (64x32). No texture was generated, recolored, or overwritten.
- Reduce both native endpoint width multipliers **0.25 -> 0.025**, with equal source/target values to avoid an authored taper. This is a tenfold reduction in the configured multiplier, **not a claimed measurement in metres or proof of final screen width**.
- Use one shared native attachment factory for initial allocation and subsequent updates so asset, offsets and width cannot diverge between those paths.
- Audit the exact resolved texture at startup, verify the actual spawned components' asset and endpoint widths in the isolated native test, and display badge `R032-AF`.

The green trail was inspected before selection; it has no completely transparent image columns, so the source image does not deliberately insert a fully empty slice along its long axis. This is an asset property, not connected proof of a seamless animation. Actual material scale, crossed-plane appearance, tiling and seams are still client acceptance criteria.

The working AE command-buffer lifecycle, persistent/nonserialized native entities, six elastic pieces, exact endpoints, independent continuation motion histories and cleanup are retained. No per-frame particle recreation was introduced. Gameplay healing, resource/cooldown calculation, Mana upkeep, LOS/range rules, targeting, native ability input/projection, support credit, persistence/escrow, Blizzard and the radial/countdown HUD are unchanged.

## Tests and package scope

Focused presentation tests and native attachment tests passed. The first focused invocation selected a nonexistent test-name pattern; after correcting the invocation, the old `Basic` appearance assertion correctly failed. It was updated to require the newly requested dedicated green appearance and width, retaining the no-sampled-particle and production presenter assertions. No gameplay or durability assertions were changed or removed.

One complete retained run then passed:

```powershell
.\gradlew.bat :test :nativeControlTest :canvas-ui:test :jar --rerun-tasks --continue --console=plain
```

| Suite | Tests | Failures / errors / skipped |
| --- | ---: | --- |
| RPG | 2,162 | 0 / 0 / 0 |
| Native controls | 55 | 0 / 0 / 0 |
| CanvasUI | 21 | 0 / 0 / 0 |
| Total | **2,238** | **0 / 0 / 0** |

The native integration test uses the actual installed Store/BeamComponent and requires:

```text
RPG_BEAM_NATIVE_INTEGRATION result=PASS asset=RPG_Healing widthScale=0.025
oldProcessingGuard=true create=true update=true remove=true
sameBufferCancel=true persistentRefs=true connectedProof=false
```

It passed together with the retained exact-three-mod server smoke, native asset/executor registrations, projectile/Blizzard audits and clean shutdown. The smoke harness initially rejected the new AF cohort name at an older AA–AE marker check; the accepted cohort list was extended, and the entire smoke reran successfully. Product gates were not relaxed.

The final JAR differs from AE in only seven class entries: `NativeHealingBeamVisuals` and its nested classes, `NativeSupportTetherAudit`, the badge in `RpgHud`, and `HytaleSkillExecutionSystem$Port$1`. Disassembly confirms the latter changes only the inlined trace asset string `Basic -> RPG_Healing`; its source/gameplay code was not edited. **All packaged resources, including the owner's icons, and all unrelated class bytes are unchanged.** Archive entry hashes and isolated AE -> AF -> AE binary rollback passed.

## Deployment and rollback

Deployed at **2026-09-12 17:59:54 UTC** to:

`C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`

| Artifact | SHA-256 |
| --- | --- |
| `HyARPG.jar` / installed JAR | `1C485513F33E760EA3DE1AC8C52CD597D9CC54EBD2EF7B3C0C35C6922DA34134` |
| `HyARPG-R032-AF-three-mods.zip` | `EEDD591FF54763F6B0259AF33A2D440C875A58ED0EEB2B667D925BD115EF2183` |
| CanvasUI | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| HytaleDevLib | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |

Before replacement, the stopped RPG save was copied to `evidence/stage-13/cohort-af/before/save/20260912T175950Z/RPG`: **471 files / 282,863,810 bytes**, individually hash-verified. The old AE JAR is recoverable from that backup and the adjacent `retired-live-name` folder. Only the RPG JAR changed; every non-target live file hash and the file count remained unchanged. The owner's ImmersiveNPCs mod remains installed alongside the three distribution mods. Internal plugin/save identity is unchanged.

The exact installed JAR was then used as input to a fresh isolated three-mod smoke, finishing at **18:01:05 UTC**, with matching SHA-256, all native lifecycle gates and clean exit 0. The actual owner save was not started by the agent; corrected live rendering remains pending.

To roll back, close Hytale/server and restore the backed-up AE `HyARPG.jar` over the AF file. Do not put backup JARs beside the active JAR. No save migration is required, and there is no reason to overwrite newer player progress for a presentation rollback.

## Connected retest

1. Restart/rejoin and confirm **R032-AF**. Hold Healing Beam on a valid friendly target. It should be a much narrower green magical tether, not broad blue wings.
2. Look from first and third person; view the beam from the side and along its length. Check for excessive width, gaps, seams, crossed ribbons and endpoint placement.
3. Keep both endpoints stationary, then strafe/jump and stop. Check straight settling, bounded elastic lag and continuity.
4. Release and repeat with Arc/Fork/Chain where available. Confirm no leftover visual and independent branches. Existing healing/resource behavior should be unchanged.
5. If still malformed, provide a screenshot/video showing the new badge and camera angle. The next boundary is native client material/geometry presentation, not the already-proven input or ECS lifecycle. Do not call AF visually verified based on the isolated test.

See the updated [spell-color guide](../owner-spell-color-guide.md): `RPG_Healing.json` is now the active Beam asset; `Basic` and the old `Beam_Heal_Green` particle set are not. All evidence and the test archive are under `evidence/stage-13/cohort-af/`. Remaining connected/performance release gates from the larger project are not upgraded by this correction.
