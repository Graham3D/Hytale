# Packbound mutation, damage receipt and projectile holder patch, 0.7.0-pre.5.1

`PatchNativeMutations.java` is the executable bytecode diff. `Build-PackboundNative.ps1` compiles and applies it only to the exact installed server SHA-256 `35a34a32175cd92ce5e2a51310953db3a4a64cac89d995cbd4830c52a9b1b904`. The pre-R244 output SHA-256 was `6f4233203e804d6b0071a6416d26dd867f5cfb0c60d3e78b69a6a91415c1a59e`. See the R244 extension below for the current pin.

The patch inserts the following instruction sequence immediately before each existing native component writer in `firstRun`; all original writer instructions and their arguments remain intact:

```text
ClearEntityEffectInteraction:
    allowClear(context, resolvedTarget, entityEffectId)
    if false: throw native ChainCancelledException(InteractionState.Failed)
    EffectControllerComponent.removeEffect(...)

ChangeStatInteraction:
    allowStat(context, context.getEntity(), resolvedStatValues, valueType, changeStatBehaviour)
    if false: throw native ChainCancelledException(InteractionState.Failed)
    EntityStatMap.processStatChanges(...)

ChangeStatWithModifierInteraction:
    allowStatWithModifier(context, context.getEntity(), fullyResolvedModifiedStatValues,
                          valueType, changeStatBehaviour)
    if false: throw native ChainCancelledException(InteractionState.Failed)
    EntityStatMap.processStatChanges(...)
```

The original class-entry SHA-256 pins are embedded in the patcher. It rejects a changed whole JAR, missing class, changed class entry or unexpected writer shape. Its output verifier rejects any removed entry, any addition beyond the four neutral `NativeMutationHook` files, three neutral `NativeDamageReceiptHook` files and three neutral `NativeProjectileReceiptHook` files, or any change to a native entry besides these three mutation leaves, the damage leaf and the projectile launch method below. The mutation hook contains no Packbound policy. `PackboundNativeMutationBridge` installs the existing Hywind protection decision after verifying the complete patched output hash.

The separately authorized damage identity insertion is in `DamageEntityInteraction.attemptEntityDamage0`, immediately before its single `CommandBuffer.invoke(target, damage)` for each calculated component. Its original class-entry SHA-256 is `6be3b2c00e3364373cb22b622e07677e14e31dcfa2cf1df9b5e7185340836e73`.

```text
NativeDamageReceiptHook.stamp(interactionContext, target, damage, componentIndex)
CommandBuffer.invoke(target, damage)  // original native call and arguments unchanged
```

With no receipt provider installed, `stamp` returns before reading or changing the event; the metadata key is registered only when a provider installs. With a provider, it freezes world, executor/owner/source/target identities, chain and fork coordinates, root/operation IDs, component index and cause, then attaches only a provider-supplied receipt string to the existing `Damage` metadata. It never manufactures a receipt, performs damage, changes ordering or selects a gameplay policy. Hywind still must connect this seam to an accepted durable root and later Gather reflection consumer before ME-026 can be enabled. The offline hook test proves the no-provider event remains unchanged and install/teardown is single-owner. The patcher removes the five injected instructions from the patched method's instruction signature and proves every original opcode and operand still matches the pinned native method. Connected ordinary-damage parity remains to be tested in the final game build.

The separately authorized projectile identity insertion is in `LaunchProjectileInteraction.firstRun`, after the existing native `ProjectileComponent.shoot` and immediately before its one `CommandBuffer.addEntity(holder, SPAWN)` call. The original class-entry SHA-256 is `973c5e280b171f59c575b9e3cd4b4d0741e538eb40c8699867d16360eb13938e`.

```text
NativeProjectileReceiptHook.beforeQueue(interactionContext, originalHolder)
CommandBuffer.addEntity(originalHolder, SPAWN)  // original native call and arguments unchanged
```

With no provider installed, `beforeQueue` returns before reading the interaction or holder. With a provider, it freezes the world, shooter/owner, native projectile UUID and available chain/operation coordinates and passes the **same original holder** to Hywind for attaching an accepted-root receipt. Missing native coordinates are passed as absent; Hywind can leave an ordinary launch alone or fail closed for a bound ME action. It checks that the native projectile UUID was not changed. It does not create, shoot, enqueue, move or damage a projectile. The patcher removes its three injected instructions from the patched method's instruction signature and proves all original opcodes and operands, including durability and post-queue penalty logic, match the pinned native method. Hywind launch-to-impact correlation, reload behavior and connected ordinary-projectile parity remain GAP-007 work; this hook alone does not certify the Scout or ME-015.

Native `SimpleInstantInteraction.tick0` continues to the `Next` branch after a leaf's `firstRun` returns. Thus a denied mutation must raise the native cancellation exception rather than return normally. The native interaction manager catches that exception and terminates the chain with `Failed`; the leaf's `Next` and `Failed` branches cannot execute. Admitted calls take their original path.

The build keeps the original recovery copy at `build/native-patch/original/HytaleServer.jar`, outside `Saves`. If the installed server later has exactly the pinned patched hash, the builder uses that hash-checked original copy as its input; any third hash fails closed. Neither this patcher nor compilation installs the patched JAR into the game.


## R244: final population projection seam

The R244 coherent-population requirement needs a final callback **inside** `WorldSpawningSystem.tick`, after native time/chunk target reconstruction and before native job selection. A separate earlier ticking system cannot establish that ordering. The neutral `NativePopulationProjectionHook.beforeSelection(Store<ChunkStore>)` is inserted immediately before the first of the pinned method's two `getActiveSpawnJobs` calls. Only `ALOAD 3` plus this static call is added. Original targets, budgets, eligibility, failure state, native loops and selection remain native. Hywind's existing `NativePopulationBalance` supplies the final projection; no provider is a no-op. Install/close permits exactly one provider.

- Original complete server: `35a34a32175cd92ce5e2a51310953db3a4a64cac89d995cbd4830c52a9b1b904`.
- Original `WorldSpawningSystem.class`: `c4392e0e579032f3b6f3596d065f010879cedd8d72e01defa4cf82acfff869de`.
- R244 patched complete server: `a032a64e03390ca0aeb6c03c3b1600eaa892c8bc833d1bf3ac39307ccdf3cb20`.
- Total changed native classes: six (the previous five plus WorldSpawningSystem). One additional neutral hook class is admitted. The executable verifier strips the insertion and compares original instruction signatures, checks class hashes, and rejects unrelated archive changes.
- Builder accepts the exact original, prior approved patched server, or this output as installed input evidence. It always patches the hash-checked original recovery copy, never chains transformations or fuzzy-matches a new SDK.
- Deployment backs up the prior installed server outside Saves, installs the new pinned server with Hywind R244, and verifies both hashes. The unchanged original recovery JAR is also retained outside Saves. Restore the prior server **and its matching Hywind JAR together** to revert; a mismatched pair fails the existing hook gate.
- Offline fixtures exercise native stat setters/getters, reconstruction/reprojection, enable-disable-enable, unchanged actual/unspawnable state, and unused-hook/install/teardown semantics. Connected population selection remains owner QA.
