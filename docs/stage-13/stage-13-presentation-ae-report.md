# R032-AE — Healing Beam ECS boundary and cooldown readability

**IMPLEMENTED / PACKAGED / DEPLOYED. CONNECTED-VERIFIED = false.**

Date: 2026-09-12. Branch: `RPG`. Starting HEAD: `e2220886f4254a5dd076378727ed6b8c96c0eac0` (AD).

Installed test JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`.

Scope: correct the native Beam ECS mutation boundary; add readable RPG cooldown and low-resource feedback without changing native input, native resource presentation or Blizzard timing; rename the distributable; preserve the owner's installed icons; document spell coloring. No gameplay, targeting, resource/cooldown formula, persistence, escrow, reward or exact-once redesign was performed.

## 1. Evidence and exact runtime

The installed package is **0.7.0-pre.2**, native revision `b41721d651ef241809e402f6c3371781b2ea5f84`.

| Installed input | SHA-256 |
| --- | --- |
| `Server/HytaleServer.jar` | `9CDC1E77DADFDAD0B1D849977CDAD5C2752207F538272FB3D76D4E915546B25E` |
| `Assets.zip` | `0E35DF2D4E4A803F7752CB300FA7C9FE7ACDF62B4311978DA95C7AFF7ABBC126` |

Reviewed the latest RPG server logs (`2026-09-12_12-42-10_server.log`, `2026-09-12_13-01-55_server.log`) and the active skill/UI traces. The old trace header still said pre.1 because AD embedded compile-time version metadata. It was not proof that the current server was pre.1. AE builds against the installed server and updates manifest/build metadata to pre.2.

The skill trace contains **85 native Beam failures**, all at allocation/update with `IllegalStateException` and:

```text
Store is currently processing! Ensure you aren't calling a store method from a system.
```

One example is 16:42:47.605 UTC, root `input-9-59be6c2d`, correlation `59be6c2d-5734-4a41-b32b-5edb658ee846`. Healing gameplay reached its existing channel path; Beam asset resolution was not the failing boundary. The previous inference that an unresolved/inappropriate texture explained invisibility was premature: native entity creation was being rejected before a Beam could be rendered.

Blizzard's UI trace already reports duration 3.0 and transitions from roughly 2.90 seconds remaining to READY/0. Examples include the 17:03:29–17:03:32 and 17:06:03–17:06:06 casts. Subsequent skill rejections include `failureCode=INSUFFICIENT_RESOURCE`. **No Blizzard cooldown or lifetime change is included in AE.**

Evidence excerpts and source-trace hashes are in `evidence/stage-13/cohort-ae/connected-ad-evidence.json`. Neither active trace was cleared, truncated, rotated manually, or edited.

## 2. Healing Beam: fix the mutation boundary, not the asset

The installed `BeamComponent.spawn(Store, ...)` directly inserts an entity into the Store. AD called it from the skill execution system while Hytale was processing ECS work. Correct asset index resolution did not make this legal.

`NativeHealingBeamVisuals.present` now captures a bounded immutable list of presentation segments and schedules actual Store mutation through the current `CommandBuffer.run(...)`. Hytale consumes that callback after releasing the Store processing guard. Cleanup uses the same boundary. For callers without a matching command buffer, mutation runs directly only on the owning thread outside processing; otherwise it is scheduled through the owning world's executor.

Specific lifecycle safeguards:

- Pending frames are bounded by the existing 512-root limit and six logical segments per root.
- Multiple queued frames for the same root/store coalesce to the most recent anchors/time; a growing frame queue is not introduced.
- Release/cancel invalidates pending creation immediately. The delayed callback checks identity before acting, so create-then-release in the same buffer cannot resurrect the visual.
- Existing native entities are updated in place; movement does not create a new particle row or replace the Beam entities every refresh.
- Removal waits only for the nearest safe ECS mutation boundary, not persistence. Native visual entities remain `NonSerialized`.
- The existing independent elastic motion state, exact endpoint anchors, branch reconciliation and gameplay channel lifecycle remain intact.

**`ASSET_ID = Basic`, `TexturePath = Trails/Charged_Blue.png`, and scale 0.25 are unchanged.** The unused custom Beam asset was not substituted or recolored.

`HEAL_PRESENTATION` now emits `NATIVE_BEAM_STARTED` after actual native creation, one `NATIVE_BEAM_UPDATED` after an actual subsequent update, and `NATIVE_BEAM_REMOVED` after actual removal. Failure receipts retain bounded error class/message. These are server mutation receipts, never claims that a connected client rendered the object. They do not add per-frame success tracing. The trace stage bucket is `COMMAND_BUFFER_CONSUMED`; the no-buffer fallback uses the same receipt path after its safe world-thread mutation.

### Actual native processing-phase regression

The opt-in isolated `rpg-native-spawn-audit` now exercises the production Beam owner inside the installed Store's real processing phase:

1. Reproduce the original direct-spawn processing guard exception.
2. Queue creation and verify nothing is inserted while processing.
3. After consumption, require valid native Beam entities with nonserialized components.
4. Update in another processing pass and require the same entity references and Transform instance with the new authoritative anchor.
5. Remove in another pass and require every reference invalidated plus the removal receipt.
6. Queue create then cancel in one buffer and require no recreation or false start receipt.

The final packaged and subsequently installed JAR passed:

```text
RPG_BEAM_NATIVE_INTEGRATION result=PASS asset=Basic oldProcessingGuard=true
create=true update=true remove=true sameBufferCancel=true persistentRefs=true connectedProof=false
```

This command is enabled only for the explicitly configured isolated audit world. It is not an instruction to run invasive probes in the owner's save.

## 3. HUD audit and correction

Re-read the installed pre.2 `AbilitiesHud`/`Ability.ui`. Native layout still uses Right 50 / Bottom 40, width 329, with right-flow 66px skill slots and 28px spacing. The RPG read-only 58px inset controls remain at Right 176 / 82, Bottom 72. No native icon, key, frame, Signature ability, Ability4 policy or resource-bar control is replaced.

AD's 250x250 circular mask was transparent over roughly 90% of its pixels, including the center. It was a ring-shaped mask, not a full skill-face overlay. AE packages a deterministic 58x58 opaque white mask with small transparent corner chamfers, so the native circular control has an actual full face to tint. `tools/GenerateCooldownMask.java` reproduces it; it is not AI-generated art.

The overlay root now explicitly anchors to all four screen edges. Both circular controls keep red at 50% opacity and the clamped **remaining / duration** fraction, with visibility only while an equipped skill has remaining cooldown. Readable, centered, 24px countdown labels with a dark shadow are layered over the icon. Countdown uses ceiling seconds, then positive tenths below one second, and disappears at zero. It never shows `0` while a positive cooldown remains.

Low resources are a separate display state, not an artificial cooldown. `RpgUiProjectionService` calls the existing real resource service's activation-cost evaluation and `canAfford`, with a read-only view of the same native resource snapshots and current attunement stacks. It does not spend resources, persist anything or change cooldowns. A cyan `LOW MANA` notice is displayed above the affected slot; other resource failures remain `LOW STAMINA`/`LOW HEALTH`. During cooldown, the countdown and low-resource warning can both be shown. When cooldown expires, the radial/timer disappear even if Mana is still insufficient.

The HUD readiness check represents the evaluated upfront activation cost, not a promise that every other equipment/target/channel prerequisite passes. Existing validation remains authoritative. `COOLDOWN_HUD_STATE` now includes countdown and resource feedback on state/reason transitions without per-frame trace spam.

**Client rendering, overlay compositing, actual clockwise direction and alignment at the owner's UI scale remain connected requirements.** Native asset inspection, packet construction and server boot cannot prove these visual properties.

## 4. Public filename and preservation of owner icons

Gradle now outputs **`build/libs/HyARPG.jar`**. The installed filename is also `HyARPG.jar`. The internal `InigmasGames:HytaleRPGPhase00Audit` identity remains stable, preserving dependencies, world configuration and the persistent mod-data directory. This is an artifact rename, not a save migration.

The deployment guard found that the live AD JAR was newer than its original archive: the owner had installed four icons at 16:41 UTC. Actual pre-deployment SHA-256 was `CE44364A24A42B3460523342AF32614CB0312057EE4B98CD2E0BBE71C442F38C`, not the archived AD hash. Deployment stopped before any live mutation while this difference was inspected.

The only extra live changes were icon mappings and paired HUD/skilltree PNGs for Blizzard, Healing Beam, Snipe and Whirlwind. Those exact eight PNG entries were preserved into source, and the four item `Icon` fields were updated to the same paths. All other item JSON properties remain equal to the owner-installed assets. No icon was redesigned. Final packaging verifies PNG bytes and normalized JSON against that live baseline. The icon updater now discovers `HyARPG.jar` and still supports one unambiguous legacy filename. After deployment its read-only check reported six supplied icons and **zero entries would change**.

## 5. Verification

Focused tests covered the new production HUD projection, countdown boundaries, mask interior, retained presentation contracts, icon updater, and native Beam construction.

The complete retained suite ran once after the code candidate was coherent:

```powershell
.\gradlew.bat :test :nativeControlTest :canvas-ui:test :jar --rerun-tasks --continue --console=plain
```

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| RPG | 2,162 | 0 | 0 | 0 |
| Native controls | 54 | 0 | 0 | 0 |
| CanvasUI | 21 | 0 | 0 | 0 |
| Total | **2,237** | **0** | **0** | **0** |

After discovering the owner icon changes, only those assets/mappings were restored, followed by a resource rebuild, targeted native skilltree/icon regression and another final-artifact smoke. No runtime class changed after the complete suite. Complete-suite XML was retained separately before the focused run replaced Gradle's native-control output.

The previously failing trace fixture compression assertion also passes on this session's supplied traces, with the assertion and writer unchanged: skill 1,645,158 -> 58,579 bytes, UI 22,486 -> 2,617 bytes. Both reconstructed byte-for-byte and preserved 1,848/53 events. This reflects the current fixtures; it is not a trace-code fix or a guarantee that arbitrarily tiny files compress below 15%.

Additional checks passed:

- 34 packaged/source CustomUI documents pass the retained static validator.
- Exact-three-mod isolated startup, all retained native registrations/audits, real Beam mutation test and clean exit 0.
- Same smoke rerun using the **installed** `mods/HyARPG.jar` as input, with matching hash.
- Scoped JAR entry differential: no unrelated classes/assets removed or changed; owner icon exception checked explicitly.
- Three-mod archive contains exactly three JARs and every entry hash equals the corresponding artifact.
- AD -> AE -> AD isolated binary rollback simulation.
- Full stopped live-save backup and individual SHA-256 verification before replacement.
- After replacement, every non-target live file hash and total file count remain unchanged.

No assertion, test threshold, trace level, durability gate or native performance threshold was relaxed. Connected tick-latency qualification and previous unverified client/gameplay gates are not promoted to PASS by this presentation correction.

## 6. Package and deployment receipt

| Artifact | SHA-256 |
| --- | --- |
| `HyARPG.jar` / installed RPG JAR | `5629BDEA510ED1543DC43812A8E10EDE84744D5037ECEBDD7335369E380E0C77` |
| `HyARPG-R032-AE-three-mods.zip` | `3E83CA97A9ED45E82ECBC11FCD616343B896E8497C0C77EF6C99AB49783D6917` |
| `CanvasUI-0.1.0.jar` | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| `HYTALEDEVLIB-0.5.0.jar` | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |

Deployed **2026-09-12 17:41:30 UTC**. Backup: `evidence/stage-13/cohort-ae/before/save/20260912T174127Z/RPG` (local Git-ignored rollback copy), **469 files / 281,553,477 bytes**, all hashes verified. The legacy live JAR was moved recoverably to the adjacent `retired-live-name` rollback folder, then `HyARPG.jar` installed. There is exactly one RPG JAR in the live mods directory. CanvasUI, HytaleDevLib and the owner's ImmersiveNPCs JAR remain installed and unchanged; the distribution archive itself contains only the established three RPG distribution mods.

The installed-byte isolated smoke finished at 17:42:25 UTC and logged both `hytale=0.7.0-pre.2` and `RPG_SUPPORT_TETHER_ASSETS cohort=AE ... beam=Basic`. **The actual RPG save has not been started by the agent; live startup and connected rendering are still pending the owner.**

For rollback, close Hytale/server first; move `mods/HyARPG.jar` out of the mod discovery folder and restore the backed-up `HytaleRPG-0.0.25.jar`. Do not leave both installed. No data-schema change occurred. The full save backup is available if needed, but do not overwrite newer test progress with it merely to roll back presentation.

## 7. Minimal connected checklist

1. Launch/rejoin RPG. Confirm top-right **R032-AE** and server startup `cohort=AE`, `beam=Basic`, pre.2. Do not change the Beam asset during this test.
2. Equip Healing Beam using `/rpg skilltree`; channel an injured friendly NPC with an accepted weapon. Confirm actual healing and a visible continuous Beam. In `skill-trace.jsonl`, require `HEAL_PRESENTATION` results STARTED -> UPDATED -> REMOVED (full values `NATIVE_BEAM_*`) with no processing exception. Those receipts prove ECS mutation only; separately record whether you see it.
3. Move caster and recipient, stop, release, invalidate the target and cross the existing range/LOS termination boundary. Check exact attachments, straight stationary beam, elastic movement and no residue. Repeat Arc/Fork/Chain where equipped. Preserve all gameplay expectations.
4. With enough Mana, cast Blizzard in E, then test R. Require a red half-transparent radial cover and readable countdown immediately, clearing/hiding as the existing three-second effect/cooldown ends. Try early recast: no duplicate skill/payment.
5. With inadequate Mana after cooldown ends, require **no cooldown sweep/countdown** and a distinct **LOW MANA** notice. Replenish Mana normally and confirm the notice clears without extending cooldown. Swap slots and test empty slots.
6. Record visible sweep direction and alignment at your UI scale. If countdown and sweep are both absent, investigate CustomHud rendering/layering, not Blizzard timing. If text appears but the sweep is absent, isolate the native circular control/mask. If Beam mutation succeeds but remains invisible, then—and only then—advance to client Beam rendering/asset diagnosis.
7. Rejoin once and verify loadout persistence. Keep the current skill/UI traces and server/client logs for review.

Color instructions: [owner-spell-color-guide.md](../owner-spell-color-guide.md). This guide distinguishes particle spawner colors from the native Beam texture and explains writable Asset Editor packs, global-override risks and manual rebuild/install steps. No spell colors were changed in AE.

Implementation/evidence paths: `NativeHealingBeamVisuals`, `HytaleSkillExecutionSystem`, opt-in `NativeProjectileSpawnAuditCommand`, `NativeSupportTetherAudit`, `Phase00Plugin`, `RpgUiProjectionService`, `SkillSlotView`, `RpgHud`, `RpgHudCoordinator`, `CooldownSweep`, `RpgCooldownSweep.ui`, mask, artifact metadata, owner icon resources and bounded package/deploy tooling. Detailed outputs and tests are under `evidence/stage-13/cohort-ae/`.
