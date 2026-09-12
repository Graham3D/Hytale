# R032-AJ-LIVE — normal single-player diagnostic deployment

Follow-up 2026-09-12: the owner subsequently reported seeing all eight controls. The
[AK report](stage-13-healing-observer-ak-report.md) records the matching logs, archive
integrity and the distinction between client-only controls and native production delivery.
The status below records the original AJ-LIVE deployment handoff, before that observation.

**IMPLEMENTED / PACKAGED / DEPLOYED. CONNECTED-VERIFIED: NO.**

Deployed 2026-09-12 at 21:45:12 UTC, following the owner's explicit instruction to stop the disposable Direct Connect workflow and install a live-test variant. This supersedes AJ's previous no-live-deployment restriction for this diagnostic build only. Production Healing Beam and the separate encounter/persistence defect were not changed.

## Build and authorization boundary

Starting branch/HEAD: `RPG`, `9cb3b677bd1938b83d451c08cbfb92c16f196188`. The cumulative AJ code is built with `-PhealingProbeLiveTest=true`. This adds a checked `healing-probe-live-test.txt` resource containing `R032-AJ-LIVE`. Plugin registration detects that packaged marker and exposes the existing probe command/tick/observer without requiring JVM flags or a disposable path. The live variant skips the old real-path restriction. Administrative permission `inigmasgames.rpg.healingprobe`, bounded run counts and finite durations remain enforced.

A normal build without this Gradle property removes the marker and retains the original opt-in/isolation behavior. Historical AJ/AI packages and evidence were not overwritten. The normal HUD was not changed; use the probe command messages/startup marker to identify this variant:

```text
RPG_HEAL_PROBE revision=R032-AJ enabled=true permission=inigmasgames.rpg.healingprobe liveTest=true disposableWorldRequired=false connectedProof=false
```

The packaged JAR registered that marker automatically during the isolated native startup validation without `rpg.healingPresentationProbe` flags. The live world's next startup remains owner QA; installing a matching file is not proof it has already executed in that world.

## Non-persistent operation in the real world

Six standalone controls retain their AJ geometry/particle/staff purposes. Live recipient modes accept `self` or an existing runtime entity UUID, not a newly spawned NPC. `native` recipient creation is rejected explicitly. No fixed flat-world fixture, terrain mutation, NPC creation, NPC behavior change, teleport or saved entity edit is used in the live-test path.

An installed-native API audit found that `EffectControllerComponent.CODEC` serializes its active effects and `ActiveEntityEffect` has no per-effect non-persistence option. Therefore merely exposing AJ's native-controller attachment in a real save would not satisfy the owner's non-persistence requirement: autosave could retain a diagnostic effect during its finite lifetime.

For this live-test variant only, recipient and staff controls instead send the existing diagnostic effect's native `EntityEffectsUpdate` Add/Remove representation directly to the invoking client. Once uses a finite 12-second duration; overwrite uses the existing 0.3-second refresh at the bounded update cadence. Cleanup sends Remove for only the owned diagnostic effect ID/network entity ID. No existing effects are globally cleared or overwritten, and no native EffectController is modified. NPC/player save components, Health, Mana, cooldowns and progression remain untouched by these controls.

This is an explicit **client-presentation control**, not evidence of native effect-controller execution, queue production or second-viewer replication. Traces label `liveTest=true`, backend `CLIENT_ONLY_NO_NATIVE_CONTROLLER_WRITE`, and stage `CLIENT_EFFECT_SUBMITTED`. It must not be compared to an isolated `EFFECT_ATTACHED` result as though they exercised the same server ownership boundary.

Core model/Beam controls retain their nonserialized transient entities and original assets. The existing ten-second run limit, 20 Hz updates, bounded trace receipts, ownership/generation checks, deferred ECS mutations, endpoint death/liveness checks, owner detach/shutdown cleanup and two-second observation tail remain. Client-only effects cannot be written into NPC/player saves; disconnected delivery remains unverified and client world reset clears client-only world state. The standalone world particle retains its documented finite native duration/tail rather than a fabricated per-instance early-cancel mechanism.

No production Healing Beam class/asset, Blizzard implementation, resource/cooldown math, targeting, progression, NPC mod data or persistence architecture was changed. Packaging compares every JAR entry with AJ: only diagnostic classes, plugin probe registration and the live-test marker may differ. Internal identity stays `InigmasGames:HytaleRPGPhase00Audit`, distribution filename `HyARPG.jar`.

## Verification

- Focused retained probe tests and three new live tests passed: recipient target policy forbids live NPC spawning; finite effect packets contain only the exact cosmetic component and matching cleanup ID; invalid/unbounded packet parameters are rejected.
- One full retained run passed: **2,182 RPG + 55 native-control + 21 CanvasUI = 2,258 tests**, zero failures/errors/skips. Existing assertions were retained. The 34-document CustomUI validation also passed.
- Exact three-mod native startup/construction smoke passed, including automatic live-test registration with `disposableWorldRequired=false`, retained native projectile/Blizzard/particle/staff/Mana audits and clean shutdown. This is automated validation, not a new owner Direct Connect workflow or connected rendering claim.
- Packaged archive contains exactly the established three supporting mods. Every archive entry hash was verified, and isolated binary rollback AJ → AJ-LIVE → AJ passed.
- Existing single-player setup contains a fourth mod, ImmersiveNPCs; deployment preserves it rather than removing it to match the archive's baseline composition.

Full command:

```powershell
.\gradlew.bat -PhealingProbeLiveTest=true :test :nativeControlTest :canvas-ui:test :jar --rerun-tasks --continue --console=plain
```

Evidence: [package validation](../../evidence/stage-13/cohort-aj-live/package-validation.json), [deployment validation](../../evidence/stage-13/cohort-aj-live/deployment-validation.json), and the retained transcripts/XML in the same cohort directory. No connected effect visibility, long-channel continuity or native production Healing Beam fix is claimed.

## Deployment and rollback

Installed file:

```text
C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar
```

SHA-256 of both built/packaged and installed JAR:

```text
774BD5844BEE9D00C0941A827A4D4F3FF1E87E39C8F1F25D2BCA3AB75200C79E
```

Three-mod archive `evidence/stage-13/cohort-aj-live/HyARPG-R032-AJ-LIVE-three-mods.zip`:

```text
300DBDC6513E7C832360424B62367875D807C58018C45D8157A7AA3BFEC2BA43
```

The owner confirmed Hytale was closed; deployment independently checked processes before backup and replacement. The full stopped RPG save/mod state was copied and all **477 files / 290,841,136 bytes** hash-verified before replacing the JAR. Backup, kept locally outside version control:

```text
C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale\evidence\stage-13\cohort-aj-live\before\save\20260912T214508Z\RPG
```

The old AH JAR is also retained beside that backup under `retired-live-name/HyARPG.jar`, hash `272E9F8365FE877152B5890D9100E123AE1127D8DC881A88D93993D3C51FE692`. Deployment verified every non-target live file remained byte-identical and the live file count remained unchanged. CanvasUI, HytaleDevLib, ImmersiveNPCs, NPC data, progression data and world configuration were not replaced. Do not restore the entire save over subsequent testing progress merely to roll back a diagnostic binary; with the game stopped, restore only the old JAR unless a separate data rollback is explicitly required.

## Owner testing

Open Hytale normally and load RPG. Follow the [exact in-game checklist](healing-probe-aj-live-checklist.md). No separate server authentication is required. Wait twelve seconds between controls. For recipient-on-self and staff tests, compare third-person visibility. Existing NPC UUIDs can be substituted for self, without modifying those NPCs. Return the normal RPG server/skill trace and client observations.

Status is deliberately split: source implemented, candidate packaged, actual live file deployed and hash-matched, **connected visibility/live-world startup still unverified**. The separately diagnosed encounter observation mismatch remains deferred and unmodified.
