# R165 Offline NPC Equipment / Spawn Hydration Repair

Revision: `R165-OFFLINE-EQUIPMENT-HYDRATION`
Date: 2026-09-05
Scope: offline equipment authoring, native spawn hydration, live profile reopen, and equipment-stat synchronization only.

## Outcome

R165 repairs the general absent-NPC → equipment authoring → spawn → live-profile lifecycle. It does not contain a Mara-specific data patch and does not change Appearance Editor or Profile Editor behavior.

The installed Hytale 0.6.3 `InventoryComponent.Armor`, `Hotbar`, `Utility`, and `Storage` default constructors can expose non-null containers with capacity `0`. The prior code tested for a missing component/container but treated a present zero-capacity container as usable. It could therefore read or restore slot `0` before the managed-NPC equipment schema existed, producing `Slot is outside capacity! 0 >= 0`.

The other contributing defect was spawn hydration replacing all four native inventory components with new objects. That could detach later UI/stat consumers from the component/container identities originally attached to the entity.

## Authority and lifecycle

### NPC absent

- `npc-inventory.json` remains the durable equipment authority.
- The authoring session uses its own fixed-capacity Armor (4), Hotbar (8), Utility (1), and Storage (40) containers.
- Equipment changes persist atomically through `NpcInventoryRepository`.
- No live `EntityStatMap` or `StatModifiersManager` synchronization is attempted.
- Profile stat cards label unspawned values as `SAVED`; Defense is calculated from durable offline armor rather than presented as a live runtime value.

### Spawn or world-restored live NPC

1. Resolve the entity's existing native inventory components.
2. Audit their pre-initialization identities and capacities.
3. Create a component only when it or its container is absent.
4. Expand zero/undersized native containers in place with `InventoryComponent.ensureCapacity`.
5. Retain every already-valid native component/container identity.
6. Hydrate the persisted equipment once into those retained native containers.
7. Mark equipment components outdated and select native Hotbar/Utility slot 0.
8. Invoke the existing native equipment/stat synchronization.
9. Validate the hydrated state before exposing live inventory windows.

The native armor-stat synchronizer remains idempotent: its stable source keys replace prior values instead of accumulating duplicate modifiers. A later lifecycle synchronization is therefore safe and does not double the effective armor contribution.

## Hard capacity boundary

R165 establishes these minimum managed-NPC capacities before any hydration, slot read/write, equipment publication, or stat synchronization:

| Domain | Minimum capacity |
| --- | ---: |
| Armor | 4 |
| Hotbar | 8 |
| Utility/offhand | 1 |
| Storage | 40 |

Hydration slot clears/restores now reject any slot outside the container's current capacity with an explicit bounded error. Loadout snapshots skip unavailable source slots and never read slot `0` from a capacity-0 container. Live UI authority validation also fails closed when any required domain remains undersized.

## Diagnostics

`NPC_INVENTORY_CONTAINER_AUDIT` is emitted at:

- `PROFILE_OPEN_ABSENT`
- `PERSISTED_EQUIPMENT_MUTATION`
- `SPAWN_COMPONENT_CREATION_BEFORE`
- `SPAWN_COMPONENT_CREATION`
- `SPAWN_HYDRATION_INITIALIZATION_BEFORE`
- `SPAWN_HYDRATION_INITIALIZATION`
- `SPAWN_HYDRATION_HYDRATED`
- `PROFILE_OPEN_LIVE_INITIALIZATION_BEFORE`
- `PROFILE_OPEN_LIVE_INITIALIZATION`
- `PROFILE_OPEN_LIVE_HYDRATED`
- `STAT_MODIFIERS_SYNC_*`

Each record includes NPC name, stage, equipment/storage domain, component object identity, container object identity, capacity, slot count, occupied slots, owning component, persisted source path, live/offline state, and whether a missing component was initialized. The paired `*_BEFORE` and post-initialization records show whether a capacity-0 native container was expanded and whether its owning component identity was retained.

Hydration also retains the existing begin, validation, runtime-conflict preservation, and rollback markers. A failed live-domain resolution is returned to the command as a bounded chat error; the profile/durable equipment is preserved and a later `/npc update` can retry.

## Deterministic validation

`test.ps1 -SkipLive` passed in full after the final R165 build. The new `R165OfflineEquipmentHydrationTest` verifies:

- all four installed native default inventory components begin at capacity 0;
- schema capacities are established before slot access;
- valid native Armor and Storage container identities are retained;
- capacity-0 loadout reads are skipped;
- capacity-0 hydration writes are explicitly rejected;
- offline authoring, spawn hydration, stat-sync, and diagnostics wiring are ordered;
- spawn hydration no longer constructs replacement `SimpleItemContainer` objects.

All prior deterministic R092–R164 inventory, persistence, stat, profile, appearance, voice, cognition, and rollback gates also passed. The pre-existing JDK/Hytale deprecation warnings remain warnings only.

## Deployment and rollback

- Active JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R165-OFFLINE-EQUIPMENT-HYDRATION.jar`
- SHA-256: `07060AC3CCCF874C28FDB34B373291104C062A8908425C919BBFE685701CA22F`
- Active project JAR count after deployment: `1`
- R164 rollback: `C:\HytaleRollback\OfflineEquipment-R164-2026-09-05-R165\ImmersiveNPCs-0.6.3-R164-PROFILE-EDITOR-POLISH.jar`
- Accepted R163 rollback preserved: `C:\HytaleRollback\ProfileEditor-R163-2026-09-05-R164\ImmersiveNPCs-0.6.3-R163-NPC-AUTHORITATIVE-ARMOR-STATS.jar`

## Connected validation checklist

1. Start the NPC save and confirm the top-right revision reads `R165-OFFLINE-EQUIPMENT-HYDRATION`.
2. With Mara absent, run `/npc update Mara`.
3. Equip armor, verify the Defense card is calculated and its tooltip begins `SAVED`, close the Studio, and reopen it once while Mara remains absent.
4. Run `/npc spawn Mara`, then `/npc update Mara`.
5. Confirm the Studio opens without `Slot is outside capacity`, the authored armor remains equipped, the Defense tooltip begins `LIVE`, and the armor contribution is present exactly once.
6. Close and reopen Mara's profile again while she remains spawned.
7. Repeat one armor change while live; verify Defense updates once and remains correct after another reopen.
8. Repeat the absent→spawn→reopen path with Hoit, and confirm Jonalith's existing working armor behavior remains unchanged.
9. Restart the server, reopen Mara, Hoit, and Jonalith, and verify their equipment and Defense values persist.
10. If any failure occurs, capture the contiguous `NPC_INVENTORY_CONTAINER_AUDIT`, `NPC_INVENTORY_HYDRATION_*`, and `NPC_EQUIPMENT_STATS_SYNC` records for that NPC.

Stop after connected R165 approval. No Appearance Editor or Profile Editor work is included.
