# R032-AR — Mantle of Flame connected-QA polish

## Scope and evidence discipline

**IMPLEMENTED · PACKAGED · DEPLOYED. CONNECTED-VERIFIED: NO (AR visuals await owner QA).**

Release state: **IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION**.

This is a cumulative local RPG build over the working R032-AQ candidate, not a replacement of its weapon-Fire architecture. Git HEAD at task start: `25b85cb2819351a0d727e33132b2f57013cda024`; AQ and AR changes are local working-tree changes. No push is authorized or performed.

The owner confirmed AQ's functional connected behavior. The previous connected session (`2026-09-13_14-08-10_server.log`) and archived skill trace contain 31 `MANTLE_PULSE_RESOLVED` events, all with positive damage: 93 affected-recipient results and 678 total authoritative Health lost. The first recorded two-recipient conversion spent 0.5 Mana under AQ. No `MANTLE_PRESENTATION_FAILURE` was found in the extracted AQ records. Successful server effects therefore did not establish visible client particles.

`Mantle1.mp4` was found at `C:/Users/Zemio/OneDrive/Desktop/Mantle1.mp4` and sampled for visual inspection. It shows the large red ground pattern and the existing fiery proc spiral in third person. The first-person defect is the owner's observation, not something inferred from the third-person video. Input video SHA-256: `F8301DE0867C1F292442AD702E107DD03B77D8C5049B9E75D9F3F24A4ED5D824`.

Evidence directory: `evidence/stage-13/cohort-ar/`. `before-audit.json` records native asset hashes; `aq-connected-mantle-records.json` preserves relevant AQ records. The source archive and live traces were read, not edited.

## 1. First-person Aura ownership

### Diagnosed boundary

AQ defined only `ApplicationEffects.Particles` on `RPG_Mantle_Aura`, targeting `Entity`. It supplied no `FirstPersonParticles`. The exact installed 0.7.0-pre.2 `ApplicationEffects` codec and network packet expose separate `particles` and `firstPersonParticles` arrays. Shipped `Server/Entity/Effects/Status/Poison.json` supplies the same body effect to both arrays, using the default `EntityPart.Self`; shipped poison imbue and Wind buff also explicitly supply both camera arrays.

The absent first-person definition is a confirmed configuration defect matching the connected symptom. We cannot inspect the closed client's actual camera culling decisions from a server test, so client-visible success remains a connected gate.

### Narrow correction: native camera variants (task option B)

One existing finite `RPG_Mantle_Aura` EntityEffect still owns the presentation. It now supplies exactly one particle definition in each native camera array, both targeting `Self`, with no model bone, held-item, screen effect, or positional offset. Both use `Scale=0.5` and `ClearParticlesOnRemove=true`.

There is no additional carrier entity, world-packet emission loop, first-person HUD overlay, or camera polling. The native camera-specific field is used rather than broadcasting a second general-world emitter. Native camera selection is responsible for activating the appropriate local variant; the ordinary variant remains available to observers. This is the same field-level separation used by shipped effects, but absence of duplicates during camera transitions still needs connected verification.

`DetachedFromModel` was audited: its codec documentation describes whether emitted particles follow the model or spawn in world space. It is not an independent, camera-observable owner API. The one-shot world particle packet has a position/duration but no following-entity identity. Introducing another carrier/refresh system was unnecessary when the explicit native first-person effect field is available.

Both camera definitions share the same 0.75-second renewable lease and existing toggle/death/logout/world-change Aura termination owner. Cleanup removes the Aura effect and current proc effect as before. No gameplay state restarts on camera changes. A failed cleanup does not retain an unbounded diagnostic root record; the finite native lease remains a backstop.

New bounded `MANTLE_PRESENTATION_OWNER` telemetry is emitted on first successful presentation and cleanup, not every Aura refresh. Fields include requested native camera-variant mode, shared effect owner, active owner count, configured variant counts, scale and cleanup. It explicitly reports `cameraTransitionOwner=CLIENT_NATIVE_NOT_SERVER_OBSERVED`. It does **not** manufacture first/third-person transition observations or claim that configured variants are simultaneously rendering.

## 2. Half-size ambient visual; gameplay/spiral unchanged

Changed only Aura **ModelParticle.Scale**, from the implicit `1.0` to `0.5`, in both camera arrays. No particle texture, spawner, rate, lifespan, color, or animation field was edited. The original ambient system itself is byte-identical to AQ.

The runtime profile remains radius `9.0` and conversion coefficient `0.25`. Existing compiled radius modifiers remain authoritative. A retained production runtime test checks base radius 9, Expanded Radius 11.25, and Concentration 6.3.

The proc system and its 0.4-second effect lease are unchanged. Baseline SHA-256:

- `RPG_Mantle_Pulse.particlesystem`: `A685E8B5E0D57546212481F4913CC3CCFED0EDF281920C9428139DA29F4E0E85`
- `RPG_Mantle_Pulse.json`: `3206A5674FADCBF63561936D75D2DF2C5C827BB33A866C86AD561EA6D15F3007`
- `RPG_Mantle_Flash.json`: `B6CB0A448A973C75A6043B2AB3B4741AD544DE53173AE59D672E560149323753`

Package validation compares these and the source/profile assets and out-of-scope owners against the actual archived AQ JAR, not only current source files.

## 3. Recipient Impact_Fire repair

Exact native asset: `Server/Particles/Combat/Impact/Misc/Fire/Impact_Fire.particlesystem`, runtime ID `Impact_Fire`. Native asset SHA-256: `31818BBCEEA65866E422F1AC9A3B9B0ADE3C0912A464646D2AC016948B24E3F2`.

AQ already placed its effect call after uncancelled, positive authoritative Health loss. The damage boundary was not moved. Inspection instead found presentation defects:

- The native fire and smoke child groups offset Y by **-0.5 m** (sparks -0.4), but AQ supplied no compensating body offset. Its entity-origin placement could put the effect below the recipient's feet/surface.
- The AQ effect lasted only **0.35 seconds** with forced particle clearing. Stock fire particles live 0.5–0.667 seconds; smoke lives 1–1.667 seconds; child spawner windows reach 6.667 seconds. Thus AQ forcibly truncated the authored effect.
- An already-active effect overwrite updates its duration; it is not an explicit remove/recreate cycle for a later authored pulse.

AR repairs this same EntityEffect owner. It adds `PositionOffset.Y=1.25` (native fire/smoke origin becomes +0.75 m relative to the entity origin) and sets a finite **7-second** lease. Scale remains the native default 1.0. The exact stock system and sprites remain unchanged; no parallel world-particle fallback was added. Forced cleanup is retained to bound lifetime and despawn residue. The body offset is a constant relative anchor, not a bone-specific or per-species center calculation.

For a later successful pulse, `MantlePresentation.impact` explicitly removes the previous `RPG_Mantle_Impact` owner if present, then adds it again. Pinned `EffectControllerComponent.addChange` appends ordered operations; the native smoke asserts `Remove` then `Add` and still exactly one active impact owner. This permits a new pulse to restart the effect without accumulating indefinite emitters. It may truncate the previous pulse's remaining visual tail; gameplay is unaffected.

These are proven configuration/lifecycle defects, not proof of which individual client rendering factor dominated the old symptom. The visual result must still be judged connected.

### Authoritative trigger/dedup remains unchanged

Complete resource preflight/commit → immutable claimed pulse → revalidate recipient (alive, permitted, range/LOS) → native damage adapter → `!hit.cancelled() && max(0, healthBefore-healthAfter)>0` → impact plus unchanged red flash.

`WeaponFireDecision.claimPulse()` is still single-use; its immutable recipient list rejects duplicate UUIDs. Together, authored weapon-root/execution identity and recipient UUID provide the existing pulse/target deduplication. No new timer cache, damage callback authority, or per-particle damage exists. Zero/cancelled/late-rejected hits never reach the presentation block; insufficient Mana has an empty pulse and dispatches nothing.

`MANTLE_IMPACT_APPLIED` records weapon root, authored execution, target UUID, actual Health lost, exact particle/owner and one instance, only after the native effect call succeeds. One successfully damaging pulse continues to produce one caster spiral and one impact/flash result per damaged recipient. A server receipt is not a rendering receipt.

## 4. Revised event-paid Mana

Only the two equivalent cost constants in `WeaponFireDecision` and the catalog description changed. No activation fee, reservation, idle drain, regeneration, recovery, Stamina, cooldown, or non-Mantle cost was changed.

Old: `MaxMana * .005 * (sum(M_i)/(.50*S)) * R`, equivalent to `MaxMana*.0025*sum(K_i)*R`.

New: `MaxMana * .015 * (sum(M_i)/(.50*S)) * R`, implemented as:

```text
eventCost              = MaxMana * .03   * (sum(M_i)/S) * R
proportionalEventCost  = MaxMana * .0075 * sum(K_i)     * R
M_i                    = S * .25 * K_i
```

| Base recipients | % Total Max Mana | MaxMana 100 | MaxMana 200 |
|---:|---:|---:|---:|
| 1 | 0.75% | 0.75 | 1.5 |
| 2 | 1.5% | 1.5 | 3 |
| 4 | 3% | 3 | 6 |
| 8 | 6% | 6 | 12 |
| 12 | 9% | 9 | 18 |
| 20 | 15% | 15 | 30 |
| 30 | 22.5% | 22.5 | 45 |
| 64 | 48% | 48 | 96 |

Magnitude, Potency, mastery, Concentration, Expanded Radius and Overcharge magnitude remain in K. Efficiency/Overcharge's explicit resource factor R applies afterward, exactly once. The generated pre-mitigation basis remains independent of target resistance and overkill. Zero source bypasses the query/division, zero recipients spend zero, and fractional precision is retained.

The new native-route regression uses 5.99 available Mana for an eight-target pulse costing 6: zero debit, zero claimed recipients, one Aura deactivation and unchanged direct Fire/Physical. Exact fractional affordability and one aggregate debit are tested. The normal basic-hit recovery and 1.5%/second regeneration can change the observed net HUD delta; the charge trace is the precise cost evidence.

## Validation and release record

Focused tests: 336 passing, including Mantle runtime, resource/source ownership, Stage 09, mastery and presentation. Native codec tests register reference-key fixtures for particle/sound asset validation; those fixtures do not claim real stock rendering. Actual asset resolution and effect operations are separately enforced in the isolated exact-JAR native smoke.

Full validation command: `./gradlew.bat -PhealingProbeLiveTest=true :check :canvas-ui:check :jar --rerun-tasks --console=plain`. One coherent-candidate full run completed in 1m42s: **2,363 tests, zero failures/errors/skips** (RPG 2,276; native 66; CanvasUI 21). Matrix: **90 × 67 = 6,030** skill/passive cases; **2,211** passive pairs; **1,000** generated valid six-link graphs (seed 110033). All retained assertions remained active. Two added codec tests initially exposed missing fixture asset-store registrations; those fixture dependencies were supplied without changing production validation.

`Run-Stage13CohortSmoke.ps1 -Cohort ar -NativeProjectileSpawnAudit` booted/shut down the exact candidate with exactly the three established supporting test mods. It passed the retained native gates, managed weapon binding, and new `RPG_MANTLE_AR_PRESENTATION` audit: both camera arrays decode through the actual asset loader, one shared Aura effect owner, 0.5 scale, one impact owner with ordered Remove/Add on retrigger, and successful native cleanup. No connected player rendering is implied. Existing missing localization-key warnings for some unrelated skill items remain; they did not reject the native ability assets.

`Package-MantleAR.ps1 -Deploy` passed archive entry/hash checks and an isolated binary rollback **AQ → AR → AQ**. Only 15 packaged entries differ from AQ: `WeaponFireDecision`, `RpgTraceEventType`, `HytaleSupportSystem` and its nested-class recompilation metadata, `MantlePresentation`, build/manifest identity, the catalog description, and the Aura/Impact EntityEffect assets. No entries were removed. Weapon source owners, resource/cooldown service classes, support runtime/profile, source bindings, native input, Healing Beam, projectile logic, selected persistence/escrow owners, ambient particle system and spiral/flash assets passed byte-identity checks.

### Package and deployment receipts

- New JAR: `C:/Users/Zemio/OneDrive/Documents/GitHub/Hytale/evidence/stage-13/cohort-ar/artifacts/HyARPG.jar`
- New JAR SHA-256: **`D424F051F55F9B61F89A84B2C8858192C42A55992A72419E513D79E4C263659D`**
- Three-mod archive: `C:/Users/Zemio/OneDrive/Documents/GitHub/Hytale/evidence/stage-13/cohort-ar/HyARPG-R032-AR-three-mods.zip`
- Archive SHA-256: `8204412C196C38B054E42E3210FC3E69FFE2492140A811D495AEE8B95DB455DD`
- AQ rollback JAR: `C:/Users/Zemio/OneDrive/Documents/GitHub/Hytale/evidence/stage-13/cohort-aq/artifacts/HyARPG.jar`
- AQ rollback SHA-256: `43DA05C1031226EE34F0F8B34EAC2FEA255ACDFA34CCD072DEE703D854B9EC09`
- Installed: **`C:/Users/Zemio/AppData/Roaming/Hytale/data/pre-release/Saves/RPG/mods/HyARPG.jar`**, hash exactly matches the new artifact.
- Full pre-replacement save/mod-data backup: `C:/Users/Zemio/OneDrive/Documents/GitHub/Hytale/evidence/stage-13/cohort-ar/before/save/20260913T190035Z/RPG`. All **536 files** were hash-verified; the replaced live AQ binary is additionally preserved beside that backup as `replaced-live.jar`.

`Confirm-MantleAR.ps1` independently inspected all active JAR manifests: **exactly one RPG mod identity**, in `HyARPG.jar`. CanvasUI, HytaleDevLib and ImmersiveNPCs are unchanged. Comparing the whole live save against its backup after deployment found exactly one modified file (`mods/HyARPG.jar`) and zero new files. No progression, NPC state, worlds or active traces were modified/deleted.

The installed client was stopped for backup/replacement. The new binary was executed in the isolated smoke, not the live single-player client. Live startup/connected verification is deliberately pending. Nothing was pushed.

### Affected source/tool files for review

- `src/main/java/com/inigmasgames/hytalerpg/combat/damage/WeaponFireDecision.java`: two 3× balance constants only.
- `src/main/java/com/inigmasgames/hytalerpg/execution/hytale/MantlePresentation.java`: impact retrigger, native asset contract and lifecycle audit.
- `src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSupportSystem.java`: presentation calls and bounded owner/impact telemetry; existing damage and recipient validation retained.
- `src/main/java/com/inigmasgames/hytalerpg/diagnostics/RpgTraceEventType.java`: two event types.
- `src/main/resources/Server/Entity/Effects/RPG/RPG_Mantle_Aura.json`, `RPG_Mantle_Impact.json`: native presentation definitions.
- `src/main/resources/rpg/catalog/skills.json`: Mantle cost description; `gradle.properties`: R032-AR identity.
- Tests: `WeaponFireDecisionTest`, `MantleNativeSourceContractTest`, new `MantlePresentationContractTest`; retained `MantleRuntimeTest` unchanged.
- Tools: new `Audit-MantleAR.ps1`, `Package-MantleAR.ps1`, `Confirm-MantleAR.ps1`; retained smoke runner extended with AR without dropping historical gates.

### Remaining known issues outside this correction

The prior AQ session logs `ENCOUNTER_PERSISTENCE_UNCERTAIN_RESTART_REQUIRED` during plugin shutdown in `DurableEncounterEffects.close` / `PersistentEncounterRuntime.attachPrepared`. This remains an unresolved persistence warning, not a Mantle visual diagnosis or a reason to reset the player's state. AR does not change that subsystem, suppress the error or edit save data. If it recurs during testing, preserve the log/trace for a separate investigation.

Existing Stage 13 reference-storage latency thresholds remain unchanged and must not be inferred passed from a green correctness suite. Rendering, camera transitions, emitter visibility and the appearance of the red flash require connected QA.

This run's retained 64-update real-storage benchmark measured p95 **9.0251 ms**, p99 **20.5412 ms**, above the unchanged 4/8 ms thresholds. The separate sparse v2 fixture measured p95 2.0553 ms and p99 148.5933 ms. These are unresolved measured performance limits, not a newly claimed release qualification. Evidence and retained crash matrices are copied into this cohort's `stage13-hardening` directory. No optimization or persistence redesign was attempted.

## Short connected checklist

1. Start the normal RPG world and confirm the top-right revision is **R032-AR**. Equip Mantle in `/rpg skilltree`; keep the same supported Flame Longsword setup used for successful AQ testing.
2. Toggle Mantle on in third person, switch to first person and back several times without recasting. Check the half-size ambient pattern, unchanged spiral, no duplicate patterns and no gameplay restart. Look toward the ground around the character so the compact footprint is in view.
3. Hit once with one visible hostile target, then repeat with a pack (five targets if practical). Every actually damaged target should show one finite Impact_Fire and the same brief red flash. Hit again to test retriggering. Block one target with solid terrain and confirm no damage/impact for it.
4. With no magnitude/cost Links, compare trace Mana charges with 0.75% maximum Mana per recipient. With eight recipients this is 6 Mana at MaxMana 100; regen/basic recovery affect the net HUD change. At insufficient Mana, expect Aura off, no AoE/spiral/impact, and the normal direct weapon Fire fallback.
5. Toggle off in first person. Check immediate ambient cleanup, then re-enable and test death/reconnect cleanup. The proc remains its unchanged short effect; target impacts are finite and must not remain stuck after their window/despawn.

No diagnostic-only command or disposable Direct Connect server is needed. Share the connected observations and latest skill/server logs; the automated gates cannot certify this checklist's visual outcomes.
