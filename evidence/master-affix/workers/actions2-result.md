# Action worker continuation — 2026-09-30

## Result and ID status

- **WA-008 remains incomplete.** Sword primary now has exact frozen 0.1 percentage-point timing and an on-demand native item/root/interaction/animation variant path. Other legal native attack families, fixed-warning audit, and connected predicted/server checks remain. The capability gate was not changed.
- **WA-014 remains incomplete.** Sword primary uses the same native selector with exact 0.01 m extension, LOS and block route, and a pre-chain carrier variant. Other melee families and connected wall/victim checks remain. The capability gate was not changed.
- **WA-141 remains incomplete.** A bounded dagger twin graph, server-gated second native selector, independently accepted offhand snapshot and 0.40-power damage calculation now exist. Plugin codec registration, NoProc metadata propagation, asset-load/contact validation and connected visual check remain. The capability gate was not changed.
- **Fully implemented IDs claimed here: none.** Do not count these three as FUNCTIONAL or report 160 complete.

## Changed files in this continuation

- `src/main/java/com/inigmasgames/hytalerpg/gear/NativeGearActionProfiles.java`: exact WA-008 tenth-percent profile IDs instead of 10/15/20% bins; WA-014 centimetres retained.
- `src/main/java/com/inigmasgames/hytalerpg/gear/NativeSwordActionAssets.java`: on-demand generated sword interaction, root, animation and carrier assets from the pinned sample; public `HytaleAssetStore.loadBuffersWithKeys` broadcasts updates through its packet generators. Generates only requested combinations, not a static item/rate/reach crossproduct.
- `src/main/java/com/inigmasgames/hytalerpg/gear/GearNativeItems.java`: validates variant IDs against the frozen GearInstance, maps their native base ID, rebuilds ItemStack with cloned metadata and identical quality/durability/quantity, and selects variants for accepted equipment. Persisted dynamic variants can be decoded before their assets are republished; unsupported items revert to their original carrier.
- `src/main/java/com/inigmasgames/hytalerpg/gear/HytaleGearEquipment.java`: uses the real valid-equipment snapshot while refreshing native inventory stacks via compare-and-swap; cancels a primary chain whose native root differs from the selected sword/twin profile. Failures and CAS retries are logged while owned items remain present.
- `tools/New-NativeGearActionProfiles.ps1`: exact tenth-percent input and sample regeneration.
- `tools/New-NativeTwinDaggers.ps1` and 28 JSON templates under `src/main/resources/rpg/gear/action-templates/twin-daggers/`: pinned native dagger primary graph with a server gate after the first selector, a second `Stab` selector with LOS/block handling and `SecondaryItem` trail, and an offhand managed damage leaf.
- `src/main/java/com/inigmasgames/hytalerpg/gear/NativeTwinDaggersAssets.java`: on-demand twin root/interactions and utility-compatible dagger carrier variant. It preserves the source model, texture, base interaction variables and durability; the variant enables native dual-wield rendering only on WA-141 eligible daggers.
- `src/main/java/com/inigmasgames/hytalerpg/gear/NativeTwinAssaultGate.java` and `NativeTwinAssaultEligibility.java`: server-authoritative V% decision at the native checkpoint, with `WaitForDataFrom.Server`; commits actor, root chain, main ID, exact offhand GearInstance and valid-equipment snapshot into the root native InteractionContext meta store.
- `src/main/java/com/inigmasgames/hytalerpg/gear/ManagedGearDamageInteraction.java`: `Offhand` codec field. The second native damage leaf reads only the gate's committed offhand, samples its own local Physical range, applies coefficient 0.40, disables crit, and tags the hit root `/WA141/NoProc`. It does not use a mainhand scalar.
- `src/test/java/com/inigmasgames/hytalerpg/gear/NativeGearActionProfilesTest.java` and `NativeTwinAssaultTest.java`: exact timing/reach asset checks, native graph/selector/utility carrier checks, and positive/control/negative controlled-snapshot checks.

## Public API findings and native wiring

- Installed `ItemStack` exposes no per-instance interaction/animation override. Public `AssetStore.loadBuffersWithKeys` accepts in-memory `RawAsset`s. The installed `HytaleAssetStore.handleRemoveOrUpdate` generates update packets and broadcasts them to active clients; `InteractionModule` registers packet generators for interactions and roots. The item and animation stores have corresponding packet generators. The actual client receipt order is still unverified in a connected test.
- Native `InteractionContext` has a public root meta store; `NativeTwinAssaultGate` commits offhand identity/snapshot there and the damage leaf retrieves it, avoiding a late current-Utility substitution.
- The installed dagger template has `Utility.Compatible=false`, and `RenderDualWielded` is a visual property of a composite dagger item. The WA-141 carrier variant changes Utility compatibility for eligible dagger items only. The extra child uses a second native selector; the visual must still be checked against an independently equipped same-base Utility dagger.
- `GearNativeItems.create` and equipment refresh publish the twin carrier only when the existing `GearAffixRuntime.supported` gate admits WA-141. Thus the unregistered custom interaction is not loaded while WA-141 remains gated.

## Exact cross-owner wiring required before enabling WA-141

1. **Main, `HyArpgPlugin.java` near existing `ManagedGearDamageInteraction` registration (about line 525):** register `NativeTwinAssaultGate.TYPE`, `.class`, `.CODEC` on `Interaction.CODEC` before a supported WA-141 carrier can be created or its dynamic graph loaded. Do not enable the affix before this registration and a native asset-load test pass.
2. **Combat owner, `HytaleDamageAdapter.ManagedGearGather`:** after `claim`, detect `ManagedGearDamageInteraction.noProcChild(hit)`. Build its metadata with `canProc=false` and `Origin.TRIGGERED`; keep `gearHit` attached and source item UUID from the offhand Hit. Ordinary main hits keep their existing metadata. This makes `GearSignatureBindings` and `NativeItemAffixBindings` reject child proc opportunities while retaining the native damage receipt.
3. **Recovery owner, `HytaleDamageMetadata.noLeech()` / `GearAppliedHitRecovery`:** treat the WA-141 triggered child as `NoLeech`. `Origin.TRIGGERED` currently does not return true from `noLeech()`, so tagging only `canProc=false` is insufficient as a general guarantee. Preserve kill credit and one root payment; do not tag it REFLECTED/REDIRECTED merely to suppress leech.
4. **Combat skill source owner, `GearCombatEffects.contributingItem`:** normalize or validate action variant carrier IDs so a committed sword/twin variant retains its GearInstance source in weapon-scaling skills; the current direct `binding.carrier(...)` equality does not recognize variants. Verify with the actual held stack and accepted snapshot, not a UUID inferred from an asset ID alone.
5. **Main:** retain `GearAffixRuntime.ENABLED` unchanged until all positive/control/negative native consumer tests pass, including offhand NoProc, and then enable only supported legal carrier classes. Registration and metadata changes are necessary but do not by themselves complete WA-141.

## Verification performed, limits, and remaining work

- Compiled the edited Java files directly with `javac` against the installed public server JAR and existing compiled project classes. No Gradle invocation.
- Ran both focused JUnit classes with the cached JUnit launcher: **8 found, 8 passed**. They parse the actual packaged native templates and installed `Assets.zip`; they do not exercise a live asset store or native contact.
- Ran both generators on the pinned installed `Assets.zip`; the sword sample regenerated and Twin Daggers generated 28 parseable templates. `git diff --check` passed for the tracked edited Java files.
- **Still missing for WA-008:** action timeline/animation variants for all legal melee/ranged/bomb attack profiles and any focus base with a real weapon-attack profile; accepted client/server timing traces; fixed telegraph and NPC timing audit. Only sword primary is implemented here.
- **Still missing for WA-014:** other legal melee action geometries, native victim ledger proof, wall obstruction, and visual trail QA. Only sword primary is implemented here.
- **Still missing for WA-141:** plugin codec registration and NoProc/NoLeech metadata propagation listed above; native asset decoder/startup proof; actual Utility equip transaction, predicted/server gate and offhand animation, LOS/terrain/victim/one-contact tests, asset reload/reconnect and source swap tests. A controlled snapshot constructor is not equipment validity or connected runtime proof.
- No game, server, auth, deployment, Gradle, commit or push was run. All unrelated dirty files were left intact.
