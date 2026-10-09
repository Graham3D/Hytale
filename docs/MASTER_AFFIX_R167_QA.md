# R167 Master Affix QA build

R167 repairs the remaining startup dependency-registration failures in the R165–R166 Master Affix builds and includes its runtime pass against the existing skill, combat, status, resource, summon, equipment and reward owners. Base skill definitions remain authoritative: Simulacrum costs 20 Mana before eligible affix reductions, and no Ameliorate or new cleanse skill was added. WA-112 consumes the canonical successful-cleanse receipt after actual removal.

The owner's explicit light-calibration decision supersedes the original 160-functional release target for WA-155. This build qualifies **159 FUNCTIONAL, 1 GATED, 0 UNSUPPORTED**. WA-155 retains metres, has an offline native light adapter, and cannot roll or spawn as affixed gear until the owner measures the renderer conversion. No guessed conversion is installed.

## Evidence and bounds

- All 160 IDs have simulator/carrier/admission coverage; no omitted or duplicate IDs. Positive, unrolled/control, invalid-equipment, persistence and owner-result checks qualify the 159 enabled IDs. WA-155's rendered-distance acceptance is deliberately pending.
- The R167 RPG suite passes 3,128 tests, including the startup dependency-order regression; the isolated native adapter suite passes 376. CanvasUI has 49 passing tests and Taverns has five, with zero failures/errors/skips across these suites. The Gradle `check` and package validator passed on R167.
- The evidence generator's 21 contract tests enforce source-linked assertions, fresh JUnit results, real runtime consumers and supplemental owner measurements. Missing or stale evidence gates an affix; package validation requires 159 qualified rows and the single disabled WA-155 exception.
- Advanced Stats comparisons cover all 64 represented affixes. Source-local and conditional effects are not invented as global sheet values; skill ranks remain projected by the existing skill owner.
- Iron Sentinel policy covers every ID: 101 inherited, 56 owner-only and three explicitly inapplicable. The policy tests cover actual bound-source effects or exclusion as appropriate. Unaffixed Sentinel metadata and existing base skill behavior are preserved.
- The QA suite contains 358 named fixtures: 160 A/B pairs, nine original gear fixtures and 29 combined fixtures. **356 are spawnable**; the WA-155 pair is reserved for calibration. The exact IDs and carriers are in `evidence/master-affix/qa-fixtures-r165.json`; R167 preserves those IDs.
- Offline asset checks cover 2,040 native carriers across 18 families, 4,181 damage nodes, 686 files and 47 selectors; native volley accounting retains three projectiles at one-third budget each. All 81 CustomUI documents pass validation.
- No game, standalone server or authentication flow is launched. Native codec/component tests run isolated in test JVMs. Connected attack contact, animation, renderer and client acceptance remain the owner's QA; deployment does not establish them.

## Runtime composition

Equipment mutation resolves immutable admitted snapshots through `GearEquipmentResolution` and `HytaleGearEquipment`. Hit owners consume those snapshots without re-parsing gear per victim. Native damage acceptance and metadata carry source identity, channel, committed coefficients and proc budgets to existing damage/status owners. Signature children drain through the existing native damage submission path with NoProc, attribution and bounded duplicate/expiry rules.

`GearStatusRuntime`, `PeriodicStatusRuntime`, `StatusService`, the existing resource ports, support/healing owners and summon registry consume affix modifiers. Shared adapters expose accepted-damage recovery receipts, native protection/resistance, hostile displacement, regeneration suppression and native summon health/movement projection. Item-granted skill/aura paths reuse canonical dispatch and support owners; they do not alter learned ownership. Kill rewards use the existing encounter shares and saved reward ledger, including the approved shared Gold pot. Magic Find and Gold are frozen at credited death and cannot be changed by later equipment removal.

The exact per-affix owner, intended effect, eligible gear, missing boundary and qualification notes are in [GEAR_AFFIX_RUNTIME_AUDIT.md](GEAR_AFFIX_RUNTIME_AUDIT.md) and its CSV companion. The packaged JSON also records exact test methods, Sentinel policy, legal carriers and supplemental evidence.

## In-game pass

Use the single main **RPG** save and confirm **R167**. Gear authoring and trace commands require `inigmasgames.rpg.gear.author`; spawning gear changes inventory/save and still respects space and equipment requirements.

1. Run `/rpg geartrace on`, then `/rpg gear affixqa list`. Existing fixture IDs remain usable. Group spawns (`armor`, `weapons`, `support`, `summons`) issue up to eight saved items per call and resume when repeated.
2. Compare `/rpg gear affixqa spawn ab-wa-001-control` with `/rpg gear affixqa spawn ab-wa-001-affixed`. Equip one at a time, mark each phase with `/rpg geartrace mark baseline` or `equipped`, and attack comparable enemies.
3. Use the `ab-wa-074-control` and `ab-wa-074-affixed` pair for Fire Resistance. Compare the inventory resistance and Advanced Stats before equip, after equip and after removal. Multiple valid gear sources should contribute to the displayed total.
4. Spawn a separate weapon fixture for Iron Sentinel, mark the phase, and bind it using the existing summon workflow. Compare inherited affixes against the control; owner-only/inapplicable policies are explicit in the audit. Binding consumes the selected item under existing rules.
5. Run `/rpg geartrace off` to flush the trace. Save/rejoin and inspect the same gear and persisted values.

Protected QA provenance excludes Magic Find and Gold bonuses from production reward farming. Their exact death-freeze, contribution split and ledger behavior are covered by deterministic offline tests.

For WA-155, `/rpg geartrace lightprobe <0..127>` temporarily projects a native actor light for at most 30 seconds; `0` restores it early. Measure three native-radius/metre pairs and the renderer ceiling, then use `/rpg geartrace lightcal <r1:m1,r2:m2,r3:m3,ceiling,toleranceMetres>`. This only checks the fit and prints values; it does not install a conversion. `/rpg geartrace light` shows calibration/projection status. Full syntax and effects are maintained in [COMMANDS.md](COMMANDS.md).

## Build and deployment

Product: `HyARPG.jar`, version `0.2.0-R167`, HUD revision `R167`, pinned Hytale `0.7.0-pre.4`. This is the current merged product, preserving the existing save's plugin identity. The normal deployment script backs up the prior JAR outside `Saves`, atomically replaces only `RPG/mods/HyARPG.jar`, and verifies SHA-256. It does not migrate or replace save data. The deployment receipt records the exact installed checksum and backup path.

R165 failed during plugin setup because `ManagedCarrierProjectile.Impact` referenced `HytaleGearEquipment.Use` before that system type was registered. R166 reached a later failure where `ManagedGearGather` referenced `HytaleDamageLifecycleSystems.Gather` before registration. The offline audit also found the resistance filter's shield dependency. Hytale validates even `Order.BEFORE` references at registration. R167 registers the affected targets first; declared dependency edges still control execution order.

Deployment completed at `2026-10-01T01:03:14Z`, replacing R166. Installed SHA-256: `EF3089D591DD56AADFDF2BEC8593D53D7C05DE864B98883165AE385ED55CB336`. [Deployment receipt and backup location](../evidence/hyarpg/jar-deploy-20261001T010314477Z/deployment.json). Full Gradle `check` passed in 3m 36s; the expanded registration-order test and package validation passed again afterward. Connected acceptance is pending.
