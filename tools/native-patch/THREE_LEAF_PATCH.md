# Packbound mutation, damage receipt and projectile holder patch, 0.7.0-pre.5.1

`PatchNativeMutations.java` is the executable bytecode diff. `Build-PackboundNative.ps1` compiles and applies it only to the exact installed server SHA-256 `35a34a32175cd92ce5e2a51310953db3a4a64cac89d995cbd4830c52a9b1b904`. The generated output SHA-256 is `6f4233203e804d6b0071a6416d26dd867f5cfb0c60d3e78b69a6a91415c1a59e`.

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
