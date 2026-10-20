# Managed carrier continuation — 2026-09-30

## Production changes

- Added `ManagedCarrierDamageInteraction` (`RPG_CarrierDamage`) and `ManagedCarrierProjectile` (`RPG_CarrierProjectile`). Their native leaves use `GearNativeItems`' valid equipment snapshot, the exact frozen item identity, `GearCombatEffects.attack` channel composition, native `DamageEntity` contact, and a nonserialized launch component for projectile impacts. Focus contact and charged projectile attacks consume actual item `magicPower + WA-003`; bomb throws sample the durable item's resolved Physical range once at launch. Missing/invalid gear is denied. The new hit ledger is bounded and `HytaleDamageAdapter.ManagedGearGather` now claims its native events for the shared gear Gather/Filter path.
- Added `GearShieldBashPower.resolve(validSnapshot, activeShieldId, combatLevel)`: the skill owner can read final `GearDefenseEffects.totalRating()` once, including valid managed armor, shield flat/increased Defense and global Defense. The stock guard shove remains zero damage; the shield's unarmed primary remains one power.
- Completed 405 previously staged focus/shield assets and authored 45 durable bomb assets. Focus quick damage variables and charged launch variables now point to the new leaves. Shield guard bash and unarmed primary use managed leaves. The durable bomb retains the installed model, icon, physics and AoE circle; its private throw graph has no `ModifyInventory` and its impact has no `Explode_Generic` or fixed `EntityDamage`. `GearInteractionAudit` walks the new managed projectile configs and accepts only their managed damage leaves.
- `GearDropGenerator.eligible` admits the six C-star local elemental flat families on staff/wand/book. Focus timing WA-008 and dual-wield WA-141 stars remain closed. `GearAffixRuntime.ENABLED` was not edited.
- `Complete-NativeGearCarriers.ps1` regenerates and verifies the private graphs and all five rarity carriers; `Test-ManagedCarrierGraphs.ps1` checks all 450 assets, 90 pinned stock SHA-256 hashes, managed damage paths, and no ordinary item removal or fixed explosion. The stage-one generator now refuses to overwrite managed assets.

## Registration and promotion boundary for main

The 90 new bindings remain `BLOCKED_WITH_REASON`, with their original source provenance. They cannot enter natural drops or `GearNativeItems.read` until main registers the native codecs/systems and runs its asset-loader and stack roundtrip gate. The exact registrations beside existing `ManagedGearProjectile` setup are:

```java
getCodecRegistry(Interaction.CODEC).register(ManagedCarrierDamageInteraction.TYPE,
        ManagedCarrierDamageInteraction.class, ManagedCarrierDamageInteraction.CODEC);
getCodecRegistry(Interaction.CODEC).register(ManagedCarrierProjectile.TYPE,
        ManagedCarrierProjectile.class, ManagedCarrierProjectile.CODEC);
ManagedCarrierProjectile.bind(getEntityStoreRegistry().registerComponent(
        ManagedCarrierProjectile.Snapshot.class, ManagedCarrierProjectile.Snapshot::new));
getEntityStoreRegistry().registerSystem(new ManagedCarrierProjectile.Launch());
getEntityStoreRegistry().registerSystem(new ManagedCarrierProjectile.Impact());
```

`ManagedCarrierProjectile.Impact` has a `BEFORE HytaleGearEquipment.Use` dependency. Main's shared equipment owner must recognize `ManagedCarrierProjectile.hasSnapshot(proxy, accessor)` at the same two existing checks for `ManagedGearProjectile.hasSnapshot`, so already launched hits retain their committed source across equipment changes. The skill equipment/power owner must call `GearShieldBashPower.resolve(acceptedGearSnapshot, activeShieldIdentity, authoritativeCombatLevel)` for `shield_bash`/Smite's `OFFHAND_WEAPON` base power. These are the two shared-owner edits explicitly withheld by the task. `HytaleDamageAdapter` Gather is already connected here.

After the registrations, shared equipment checks, native asset-loader test, and five-rarity `GearNativeItems.create/read` stack test pass, main can run `./tools/Complete-NativeGearCarriers.ps1 -Promote` to map the 90 rows. Do not run the stage-one generator. The capability gate must remain until the main suite passes.

## Verification and exact limits

- `javac` directly compiled the six edited/new Java owners and the focused test against the installed public server JAR and existing local classes. No Gradle was run.
- `ManagedCarrierProductionTest` passed five methods: positive/control/negative focus `magicPower` and elemental local contact for staff, wand and book; frozen bomb Physical and local flat positive/control/invalid source; shield final Total Defense positive/control/invalid source; five rarity frozen payload roundtrips for representative focus/shield/bomb bases; and C-star legality boundaries.
- `Test-NativeGearCarriers.ps1` passed: 450 managed JSON carriers, 90 exact source hashes, private managed impact paths and no ammunition removal/fixed native explosion. `Complete-NativeGearCarriers.ps1 -VerifyOnly` passed. `git diff --check` passed on touched tracked files.
- Native `ItemStack` create/read cannot run in the standalone offline JVM because `Item.getAssetStore()` is unset without the native asset loader. The five-rarity test proves frozen payload and carrier ID mapping only; main must execute the actual native stack roundtrip after asset loading. No native connected action or owner gameplay QA is asserted. No affix ID was promoted or newly enabled by this worker. The 160-affix objective remains incomplete.
