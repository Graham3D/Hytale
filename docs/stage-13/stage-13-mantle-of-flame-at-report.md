# R032-AT — additive Mantle of Flame

## Scope and status

**IMPLEMENTED / PACKAGED / DEPLOYED. CONNECTED-VERIFIED: NO.** Receipts are below. This is a bounded correction over the cumulative RPG working tree, not a stage redesign. Existing uncommitted AS/AQ/AR work and user artwork were preserved. No save/progression/NPC migration is needed.

## Previous behavior and correction

`WeaponFireDecision.directAmount` previously returned zero for eligible Fire components when its result was `CONVERTED`, including when the Aura found no recipients. NativeWeaponFireProducer called the Aura router from inside the native damage calculator; Quick Slash also made its decision before direct damage and skipped suppressed components. Consequently original Fire was replaced, not supplemented.

The decision now owns **only the additive echo**. Its success status is `ECHO_COMMITTED`; `directAmount` validates the component and numeric amount but always preserves the original scalar, independent of whether an echo decision is pending, failed, closed, unaffordable or successful. Quick Slash no longer has a suppression return. There is no compensating Health write, restoration of suppressed damage, refund/replay of an already claimed pulse, or removal of the direct Fire channel.

The authoritative `WeaponDamageExecution`, immutable components/provenance and execution ledger are retained. Native `NORMALIZED_FIRE_V1` source production still samples the existing authored distribution once per supported execution; it is not replaced with per-victim damage reconstruction. Source attribute normalization and unrelated native damage channels remain intact. Changing Mantle to additive does not broaden producer coverage or invent eligibility for unknown Fire damage.

Native execution now collects the echo callback during calculation and releases it only after the native damage leaf returns successfully. The support adapter queues its decision after the leaf's queued native work, without retaining the callback's CommandBuffer. Quick Slash completes its direct victim/component loop before requesting the echo. Echo admission failures are traced as `ECHO_FAILED_DIRECT_PRESERVED`; the existing uncertain resource-decision guard prevents another debit/pulse attempt on the same decision. No echo failure can suppress the already submitted original attack.

## Preserved contracts

- Each accepted Aura recipient receives base magnitude `sourceFire * 0.25`, with the existing compiled magnitude/victim modifiers. The source is evaluated once, not multiplied by direct victim count.
- One decision, resource receipt and pulse claim per authoritative execution; independent Quick Slash hits retain independent execution ownership. Derived Aura damage remains ineligible as a new weapon source.
- Existing caster-centered 9 m query, hostility/protection/range/LOS checks and dispatch-time checks are retained.
- Existing proportional Mana formula is unchanged: base 0.75% of total maximum Mana per accepted recipient, scaled by existing magnitude and resource factors. Empty recipient sets cost zero; insufficient Mana still terminates the Aura. No idle drain is added.
- VFX, mastery/reward authority, Link compatibility and modifier calculations are unchanged. Legacy compatibility tags are intentionally retained to avoid silently changing Link behavior.
- Native input/HUD, Healing Beam, Blizzard, resource/cooldown services, persistence, escrow and exact-once owners are outside scope.

The catalog and native item tooltip now describe an additive echo. The native tooltip's stale 0.25% cost text was corrected to the already implemented 0.75%; this is not a balance change.

## Regression coverage

Updated only tests whose expected direct-Fire suppression was intentionally replaced by the new contract. The same tests continue checking one source/query/receipt/pulse for multiple victims, resource/magnitude anchors, insufficient Mana, no recipients, component provenance, delivery exclusions, ledger limits, world/actor/root separation and uncertainty behavior. The Quick Slash five-victim test now requires original Fire plus unchanged Physical damage for both independently owned hits. Added an explicit test proving original 100 Fire plus a paid 25 Fire echo, one debit, no second pulse, and unchanged direct damage after decision cleanup.

Focused RPG/Mantle and native-control tests passed. An initial unqualified Gradle `test --tests` command also selected CanvasUI, whose tests do not match the RPG filter; the command was corrected to `:test` without changing any assertion. Final full retained suite, native smoke, ZIP checks and rollback receipts will be recorded on completion.

## Connected checklist

1. Restart Hytale and join RPG; confirm **R032-AT** in the revision badge.
2. Equip the Flame Longsword and Mantle of Flame. Compare ordinary Primary attacks with Mantle off/on against healthy hostile targets. Original weapon damage must remain; nearby visible hostiles should additionally take the echo when Mana is available.
3. Test Quick Slash with Mantle enabled. Each base hit retains its original Physical/Fire components and can own one additional pulse; multiple direct victims must not multiply pulses for that execution.
4. Check Mana cost with one and multiple nearby recipients, and with insufficient Mana. An unaffordable echo must not remove weapon Fire or partially dispatch its recipient set.
5. Test a wall between caster and an otherwise nearby hostile, an out-of-radius hostile, and an attack with no Aura recipients. No unauthorized echo hits or empty-set Mana charge should occur.
6. Inspect `WEAPON_FIRE_SOURCE_ROUTED` for `ECHO_COMMITTED`, sourceFire, recipient count and Mana cost; correlate `MANTLE_PULSE_RESOLVED` by weaponRoot/execution. Look for absence of old `SUPPRESSED_EMPTY`/`CONVERTED` routing. Test existing Links and visuals normally.

Local unit/codec tests and isolated server startup do not prove connected native hit ordering, damage, Mana feedback, rendering or mastery. These remain owner QA requirements. Previously unresolved Stage 13 storage/native-tick performance gates are not changed or claimed fixed by this correction.

## Final validation and deployment receipts

- Full retained command: `gradlew.bat -PhealingProbeLiveTest=true :check :canvas-ui:check :jar --rerun-tasks --console=plain`. BUILD SUCCESSFUL in 1m36s. **2,404 tests passed**: 2,317 RPG + 66 native-control + 21 CanvasUI; zero failures/errors/skips. Complete receipts under `evidence/stage-13/cohort-at/validation/` and `full-validation-final.txt`.
- Retained compatibility and crash/recovery evidence was copied from `build/stage11-matrix` and `build/stage13-hardening`. Existing storage qualification remains unresolved: real 64-update storage sample p50 4.7404 ms, p95 8.6516 ms, p99 58.267 ms against unchanged 4/8 ms targets. This benchmark is not connected native-tick proof. No threshold/assertion was changed.
- Applied the existing icon updater to the built candidate before smoke/archive. Seven supplied icons were recognized; three JAR entries changed. All icon bytes now match current `art` files, including Mantle. The final live `-CheckOnly` confirms **zero entries would change**. This was an artwork synchronization, not a gameplay rebuild after validation.
- Exact final candidate booted in the isolated pinned three-mod native smoke with revision R032-AT, native managed Fire bindings, retained Light Attack inventory and presentation/projectile/Healing audit gates passing. Exit 0, clean shutdown; `server-smoke-summary.json` reports the same hash as the installed JAR. Live connected startup/casting still requires the owner to rejoin.
- Archive and entry hashes validated; no removed cumulative AS entries. Protected input, Healing/projectile owners, resource/cooldown and support runtime, selected persistence/escrow owners, weapon override assets, Mantle presentation assets and runtime formula files were byte-identical to AS. Native Fire producer/leaf, decision and shared execution/support adapters intentionally changed.
- Binary rollback rehearsal **AS -> AT -> AS PASS** outside the live world. Backups and archive were retained.

Deployed path:

`C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`

Built, archived and deployed JAR SHA-256:

`DDB5C13AEFA8A88EADD6CE73C3146264794E486A63D348DAB7C825CB8EF9A4B6`

Three-mod archive: `evidence/stage-13/cohort-at/HyARPG-R032-AT-three-mods.zip`

Archive SHA-256: `BAF3E5FBEE997E1D5DF77DE1CEEBBF0EE5AAFEED054108AA096DD017BC04279E`

Before replacement Hytale/server processes were checked stopped. The entire matching RPG save/mod state was copied to:

`evidence/stage-13/cohort-at/before/save/20260913T211628Z/RPG`

All **547 files** were hash-verified. Atomic replacement changed only `mods/HyARPG.jar`; independent confirmation found zero new/deleted save files and exactly one active RPG mod identity. Supporting mods, NPC data, progression and active traces were not modified. `package-validation.json` and `post-deployment-validation.json` record hashes and inventories.

For rollback, close Hytale, then restore the pre-replacement `HyARPG.jar` from that matching backup (preserves any prior icon updates). The immutable AS release artifact is also retained at `evidence/stage-13/cohort-as/artifacts/HyARPG.jar`, SHA-256 `114AFD95E29A4DC49DBAE688EA8D0038680FF56AE1855A1F3AC649185A033E25`. Do not restore an old full save over subsequent progress unless intentionally reverting save state.

## Changed files in this correction

- `combat/damage/WeaponFireDecision.java`: echo status, unconditional direct scalar preservation.
- `combat/hytale/ManagedWeaponFireInteraction.java`, `NativeWeaponFireProducer.java`: post-native-leaf echo dispatch and preserved normalized direct Fire.
- `execution/hytale/HytaleSupportSystem.java`: safe post-native handoff, additive trace routing, failure isolation.
- `execution/hytale/HytaleSkillExecutionSystem.java`: remove Quick Slash suppression and request echo after its direct-hit loop.
- Catalog description and native language tooltip; revision in `gradle.properties`.
- `WeaponFireDecisionTest`, `QuickSlashLightProfileTest`: explicit new additive expectations and retained ownership/provenance/uncertainty assertions.
- AT smoke support, `Package-MantleAT.ps1`, `Confirm-MantleAT.ps1`, and this report. No commit or push was performed; files are in the GitHub working folder, not claimed published remotely.
