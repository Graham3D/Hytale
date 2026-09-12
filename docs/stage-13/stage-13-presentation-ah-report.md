# R032-AH — Healing stream loading, channel-only staff glow, native Mana replication

Date: 2026-09-12. Branch: RPG. Starting pushed HEAD: `b6b461c10a7095c99c762bffe55b4e1112fa9c08` (AG).

## Delivery status

- **IMPLEMENTED:** the three bounded corrections below.
- **PACKAGED:** exact cumulative HyARPG JAR and three-mod archive.
- **DEPLOYED:** 2026-09-12 19:07:57 UTC to `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`.
- **CONNECTED-VERIFIED: NO.** Isolated native execution/update-queue checks are not client rendering or packet-delivery proof. Owner rejoin/visual QA remains required.

## Connected evidence that motivated the changes

Reviewed the actual 0.7.0-pre.2 client log `Logs/2026-09-12_14-45-03_client.log`, RPG save server log `logs/2026-09-12_14-45-09_server.log`, and skill trace under the unchanged internal mod-data directory `mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl`.

In the 18:45–18:47 UTC session:

- Five Healing Beam channels each produced `HEAL_PARTICLE_STARTED`, `HEAL_PARTICLE_UPDATED`, and `HEAL_PARTICLE_REMOVED` without presentation failure.
- The client nevertheless logged **Failed to load entity texture: NPC/MISC/Empty.png** on those five channels: 14:45:32.3991, 14:45:40.9624, 14:45:47.9300, 14:46:31.6508, and 14:46:49.2839 local log time.
- Five Blizzard commits recorded cost **28 Mana**, cooldown **3 seconds**, at 18:45:59.479754, 18:46:05.4137658, 18:46:12.7137235, 18:46:20.8467102, and 18:46:47.3133184 UTC.
- All 620 `CHANNEL_UPKEEP` entries reported `nativeWriteObserved=true`. That proves the server-side repeated Mana writes were observed, not that the client displayed each write.
- The owner's screenshot reports AG and no visible stream. Their report that Healing Beam catches up the Mana bar is connected evidence of stale presentation, not authorization to change Blizzard's cost.

## 1. Missing stream: unresolved model texture, not failed ECS creation

AG's `RPG_Healing_Stream.json` used `NPC/MISC/Empty.blockymodel` without an explicit texture. The installed asset archive contains that model but **does not contain** `Common/NPC/MISC/Empty.png`. The client attempted this inferred path and failed. AG's native audit checked persistent model construction but did not check this loading dependency.

AH adds the explicit shipped `Items/Projectiles/Projectile_default.png` texture. This is the same empty-model/explicit-texture pair used by native `Server/Models/Projectiles/Abilities/Ground_Slam.json`. It does not add visible geometry or redesign the stream: the empty model still has no mesh and its sole particle remains the requested **Beam_Heal_Green2**, with the same scale, direction, emitter count, rates and lifetimes.

The new regression verifies both referenced files exist and contain bytes in the exact installed archive; it also retains the negative control that the old inferred texture is absent. Startup and the production-path native carrier audit check the explicit texture survives into the actual model packet. Carrier identity, deferred ECS mutation, update/remove, finite root/segment bounds, and same-buffer cancel protection remain intact.

This removes the exact logged asset failure. It does **not** establish that a connected client will now render the stream with the desired length/orientation/continuity. Those remain explicit QA checks.

## 2. Staff_Bronze is channel-owned, not an always-on item decoration

AG incorrectly put the requested sparkle into each staff's permanent `Particles` array. AH removes that exact particle from all 26 overrides. Other native particles and every non-particle item field are preserved and compared with the pinned native assets. Native Bronze/Bone's older Staff_Bronze entries remain removed too, so those items cannot restore it merely by being held.

The existing audited item-to-node manifest remains `rpg/presentation/staff-heads-ag.json`. Its name identifies the original audit, not an always-on behavior requirement. Four new finite, cosmetic EntityEffect assets cover its four unique node names:

- `RPG_Healing_Staff_Origin_Projectile`
- `RPG_Healing_Staff_TopPommel`
- `RPG_Healing_Staff_Block5`
- `RPG_Healing_Staff_Knob`

Each has only a 0.3-second duration, overwrite behavior, non-debuff designation, and application particles. The particle is Staff_Bronze, attached to **PrimaryItem** and the exact audited node, with clear-on-remove enabled. No stats, damage, modifiers, resource changes, HUD icons, or gameplay effects are added.

`HealingParticleVisuals` acquires/renews the owner's effect only while handling an active Healing Beam presentation frame. It releases the previous effect on a changed attachment and releases the current effect when the root is removed or cancelled. Shared ownership does not clear another root's identical staff effect. The finite duration is a fallback for abandoned ownership. Unknown items receive no guessed attachment.

The isolated native audit verifies no effect before the frame, effect presence during the channel, and no remaining effect after release. Existing full-health recipient presentation and shared-recipient cleanup tests remain. The target's Effect_Health_Pack does not depend on actual healing being greater than zero.

## 3. Mana synchronization: participate in native stat-modification ordering

The shared resource adapter already uses the live native EntityStatMap and `setStatValue`; Blizzard is not using a separate RPG-only Mana counter. The defect found in the integration contract was that `HytaleSkillExecutionSystem` had only an AFTER-physics dependency and did **not** implement native `EntityStatsSystems.StatModifyingSystem`.

Inspection of the installed server implementation establishes:

1. Native `Changes` runs after systems of that type and before `EntityTrackerUpdate`.
2. The tracker consumes dirty flags and queues EntityStatsUpdate for the self viewer.
3. `ClearChanges` runs after the tracker and clears the pending update lists.
4. A resource mutation between tracker consumption and clear can therefore be erased from that tick's update list. Repeated writes can mask a missing one-shot update.

AH adds the native marker interface to the existing RPG execution system. It does not replace the resource port, force packets, add a polling HUD, repeat a charge, or change persistence handoff. The actual sorted native registry now proves:

`execution=561 -> Changes=562 -> EntityTrackerUpdate=563 -> ClearChanges=568`.

The new opt-in isolated `NativeManaReplicationAudit` uses the real RpgResourceService and EntityStatResourcePort, native Mana asset/stat map, native viewer state, and installed EntityTrackerUpdate/ClearChanges methods. A single 28-Mana debit, followed by an attempted duplicate commit of the same token, produces exactly one non-predictable native Set update at the correct remaining value. The queued protocol update survives clearing of the stat's mutable update lists.

This proves the repaired ordering and native queue boundary. The old connected log did not record the old sorted system indices or outbound stat packets; consequently the specific old drop interleaving is a diagnosed integration hazard consistent with the symptom, **not a captured packet-level fact**. If AH still shows stale Mana, the next boundary is actual self-viewer delivery/client prediction, not another change to costs or an assumed successful fix.

## Scope and unchanged behavior

No gameplay formulas, Blizzard duration/cooldown/cost, Healing Beam math/targeting/upkeep/LOS/range, Arc/Fork/Chain coefficients, support credit, skills/passives, native ability projection, XP HUD, native resource-bar ownership, trace storage, or Stage 13 durability/escrow/exact-once code changed. The sole HUD change is the revision badge suffix AH.

The prior limitation remains: the directed stream starts at the authoritative spatial anchor, **not the animated staff tip**. The staff sparkle is genuinely item-node attached; these are different presentation paths. The selected shipped particle's static travel characteristics and lack of a recipient endpoint remain unchanged. No new claim of exact endpoint binding or spring behavior is made.

## Validation

Focused tests: PresentationAG, PresentationAH and SupportTether passed. AG's staff assertion was updated to verify the owner-requested channel-only location, retaining native gameplay-field equality, other-particle equality, exact node uniqueness and full 26-item coverage.

One complete retained invocation:

```powershell
.\gradlew.bat :test :nativeControlTest :canvas-ui:test :jar --rerun-tasks --continue --console=plain
```

**PASS: 2,170 RPG + 55 native controls + 21 CanvasUI = 2,246 tests; zero failures/errors/skips.** Exit 0, 1m41s. All 34 packaged CustomUI documents passed the retained validator. Native access/deprecation warnings remain. No gameplay/persistence assertions or performance thresholds were relaxed. The smoke process was stopped before this full invocation.

Isolated native three-mod gates passed, including:

- persistent Healing particle carrier construction/update/remove and explicit model texture;
- channel-only staff effect attachment/removal;
- shared recipient effect cleanup and same-buffer cancellation;
- native Mana ordering/one-charge/viewer-update queue proof;
- retained native projectile allocation, queued rollback and expiry;
- retained Blizzard presentation and all existing startup/asset/registration gates.

An initial isolated audit attempt failed because the **new test fixture** populated the self-viewer map without adding the entity to the viewer's visible set. Native EntityViewer correctly rejected `Entity is not visible!`. The fixture was corrected to model both sides of native visibility; no runtime guard was weakened. That initial log is retained in `evidence/stage-13/cohort-ah/diagnostic-attempts/01-viewer-fixture-not-visible.txt`. Two preliminary compile errors were wildcard-import ambiguity in the new audit, corrected using explicit imports before passing focused/native/full validation.

## Artifacts, deployment and rollback

- HyARPG.jar SHA-256: **272E9F8365FE877152B5890D9100E123AE1127D8DC881A88D93993D3C51FE692**
- Three-mod ZIP SHA-256: **2054939A77F0A569C1DC01D85BBFA656341F8F9F22C95A50C1115715E97FCAEB**
- CanvasUI SHA-256: `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6`
- HytaleDevLib SHA-256: `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230`

Artifacts and technical receipts are under `evidence/stage-13/cohort-ah`. The archive contains exactly those three JARs with each entry hash verified. A scoped JAR comparison against AG rejects unrelated binary changes and unexpected removed entries. Binary rollback AG -> AH -> AG was validated in an isolated directory.

Before deployment, Hytale was confirmed stopped. The complete current RPG save was copied to:

`evidence/stage-13/cohort-ah/before/save/20260912T190754Z/RPG`

All **475 files / 289,252,298 bytes** were individually hash-verified. The old live AG JAR was moved to rollback storage; AH was staged/hash-checked and installed. Every other live file was verified unchanged. CanvasUI, HytaleDevLib, ImmersiveNPCs and all world/mod data were preserved. The internal mod identity/data-directory name remains unchanged. Backups are local/ignored; do not push private save data.

The final installed-byte isolated check passed at **19:08:47 UTC** using the JAR copied from the actual live mods path, not an assumed matching source checkout. Native staff/Mana markers were recorded at 19:08:41 UTC with the same SHA-256. This is still not live-save startup or connected-client verification.

## Minimal connected checklist

1. Start Hytale, join RPG, and confirm **R032-AH** at top right.
2. Hold a supported staff without casting. Staff_Bronze must not play. Other shipped staff particles may still exist.
3. Channel Healing Beam at the NPC at full Health and then while injured: verify the green stream, recipient health effect and staff-head sparkle. Release: both owned effects/stream must disappear. Repeat with movement and changing targets; no residue should remain.
4. With enough Mana, cast Blizzard alone. The native Mana bar must drop immediately when the skill commits, without subsequently casting Healing Beam. Baseline unmodified cost remains 28; passive cost modifiers still apply normally.
5. Wait for the three-second Blizzard lifetime/cooldown to end and cast again. Then start Healing Beam: Mana should drain by its normal upkeep, with no large catch-up jump attributable to earlier Blizzard casts.
6. If either issue remains, retain the new client log and skill trace. For the beam, check whether the missing-texture warning disappeared; for Mana, compare commit/upkeep timestamps with the actual bar behavior. Do not infer visible success from the new isolated PASS marker.

Stop point: AH implemented, validated, packaged and deployed for this connected checklist. No new stage or unrelated optimization begun.
