# Hywind split-boundary recovery (2026-10-04)

> Historical R141 investigation. Its "current" build statements below describe
> 2026-10-04, not the R250-U7P5B recovery starting point. Do not copy its
> `HywindPlugin`, manifest, Gradle configuration, or resources wholesale over
> the R250 source. See [the development baseline](DEVELOPMENT_BASELINE.md).

## Deployment revision

**R141 / `0.1.0-merge.91`** was the owner-QA build at the time of this report.
The then-active file was `RPG/mods/Hywind.jar`, deployed **2026-10-04 13:28:50 EDT**
(17:28:50 UTC), SHA-256
`18298D5F25582118DE92AA7B27585015C9245B06450B8C0A3C3D5256379091A8`.
R140 is backed up at
`evidence/hywind/jar-deploy-20261004T172850104Z/Hywind-before.jar`.
Connected-game acceptance is pending.

The 2026-10-04 13:07 client log recorded the R137 join failure:
`InventoryNativeWorkspace/Section702.ui` could not find
`Pages/ProfileInventory/GridCommon.ui`. That document had been supplied by the
ImmersiveNPCs resource bundle. R139 relocates the shared grid style and its
eight required textures under Hywind's `InventoryGrid` resource path and
updates the RPG inventory and both generated section families to import it.
The offline package validator checks the style, textures, and representative
generated sections, including Section702.

The same join log also recorded `ENCOUNTER_FILE_UNREADABLE` for a preserved
operator-QA gear loot receipt whose ten frozen affixes exceed the current
production Legendary budget. R140 accepts only bounded historical QA receipt
versions with `qaOnly=true` through the shared `GearInstance` reader. Normal
production rarity budgets and the saved receipt bytes remain unchanged.

The 2026-10-04 13:22 client log recorded an in-game Tab crash:
`Target element in CustomUI event binding has no compatible Dropped event.
Selector: #DropBackdrop`. That selector is a `Group`, which cannot own a
`Dropped` event. R141 removes that unsupported binding and its unreachable
handler. The native `ItemGrid` still owns `Dropped` and `DragCancelled`, and
the explicit Drop Selected action remains. The offline package validator now
rejects a reintroduced `Dropped` binding on the backdrop.

Reference: the user-provided, post-split `HyARPG.jar`, SHA-256
`12F2E0DA1E2DF5C8794741A8F0DCBC03C0F526A0B31A731D97DB548007B9EEDA`,
and matching `origin/codex/checkpoint-c-optional-bridge` source. The reference is
evidence, not the deployed build or a source-tree rollback.

## Compared boundaries

- Root plugin entrypoint and lifecycle at R141: reference `HyArpgPlugin`,
  R141 `HywindPlugin`; reference and R141 `TavernsPlugin`.
- Root and Tavern Gradle dependencies and JAR inclusions.
- Root manifest, optional dependencies, plugin description, and source metadata.
- Package class/resource inventories, language keys, Tavern assets, and NPC
  status/debug pages in reference, current, and standalone ImmersiveNPCs JARs.
- Optional bridge API/adapter and Tavern inventory-ownership adapter.

The reference includes Tavern gameplay, its command/listener/service registrations,
Tavern UI, and Tavern assets. It contains no `persistentnpcs` implementation
classes or NPC status/debug pages. Hywind therefore retains Tavern gameplay.
The source of the unwanted registrations was the later superclass chain
`HywindPlugin -> TavernsPlugin -> PersistentNpcsPlugin` plus the root JAR's
`persistent-npcs` project output. That chain activated ImmersiveNPCs, Orbis,
Nemotron, Chatterbox, and Moonshine providers from Hywind in addition to the
installed separate ImmersiveNPCs mod.

## Recovered ownership

- `TavernsPlugin` again extends Hytale `JavaPlugin`; no inherited
  `PersistentNpcsPlugin.setup/start/shutdown` executes in Hywind.
- Hywind's production dependencies and JAR assembly exclude `persistent-npcs`.
  The project remains available for its separate source and retained
  diagnostic tests; it is a test-only dependency of the RPG project.
- The optional `immersive-compat-api` handshake and RPG damage-event adapter
  are restored. Without the separate provider they resolve to a no-op. The
  standalone ImmersiveNPCs mod still owns its providers and registrations.
- Tavern's `SpatialPlayerItemPolicy` is restored as a Tavern-owned host adapter.
  A neutral ReadyPath diagnostic class is packaged via `hywind-runtime-common`
  because later RPG and Tavern code both use it; it performs no NPC registration.
- The root manifest again declares `ImmersiveNPCs` optional and no longer
  claims NPC source ownership. NPC-only package classes and status/debug pages
  are forbidden by `Test-HywindPackage.ps1`.
- Shared RPG language keys for the two NPC roles were retained because they
  are present in the authoritative split backup. Native RPG monster roles,
  Master Enemies, affixes, progression, Defense, resistance, and QA command
  content were retained.

Connected-game acceptance remains with the owner under `AGENTS.md`; this
recovery uses compile, focused bridge tests, bytecode/package inspection, and
offline asset validation only.

## R250 boundary review (2026-10-10)

The R250-U7P5B source still has `TavernsPlugin extends JavaPlugin`, an optional
ImmersiveNPCs dependency, and a package validator that forbids
`persistentnpcs` classes, NPC-owned resources, and ProfileInventory imports.
Read-only inspection of the active `Hywind.jar` found no `persistentnpcs`
classes. Its own `RpgInventory/GridCommon.ui` and eight textures replace the
older `InventoryGrid` path. The older eight texture files are byte-identical
to those already packaged at the R250 path. The neutral `ReadyPathProbe` source
is also identical, now compiled from the root project instead of a separate
`hywind-runtime-common` project.

The active R250 JAR retains the internal `HyARPG` identity and
`HyArpgPlugin` entrypoint. This recovery preserves that identity. A future
identity migration would need separate approval and save compatibility proof.
