# R124 NPC Profile polish and inventory repair

## Scope

R124 preserves the R123 production NPC Profile architecture: the Custom UI renders the
profile, mesh preview, gear controls, voice state, and two inventory grids; a registered
`ContainerWindow` plus `InventoryUtils.moveItem(...)` remains the sole inventory mutation
authority. This revision does not return to simulated cursor movement or manual stack copying.

## Connected findings and diagnoses

### NPC inventory internal movement

Connected events reached the bridge with the correct NPC window section and source/destination
slot indices. The rejection was server-authored, not a client failure:

`OPERATION_NOT_ENABLED_INTERNAL_MOVE`

`CustomInventoryTransactionBridge` explicitly rejected every operation where the resolved source
and destination containers were the same object. This was a deliberately retained R118 rollout
gate. R124 removes only that gate. Same-slot drops remain no-ops, and all other existing checks
remain: active session/page, active registered window, allowed section IDs, exact container object
identity, slot bounds, left mouse button, authoritative full quantity, empty destination, duplicate
release suppression, and post-move state verification.

Result: a full stack may now move from one empty NPC inventory slot to another through
`InventoryUtils.moveItem(...)`. Occupied-slot swapping, merging, and partial-stack movement are
still intentionally outside the Custom UI bridge contract and will fail closed.

### Preview inheriting the viewer's held item

The preview intentionally targets the viewing player's network ID because
`CharacterPreviewComponent` reads that client-local entity. A Player inventory transaction can
therefore emit a viewer `EquipmentUpdate` after the NPC model/skin/equipment overlay was applied.
That packet replaced the preview's target equipment with the viewer's currently selected item.

R124 reasserts the captured NPC target `EquipmentUpdate` after every committed storage move. The
new trace marker is:

`NPC_PROFILE_PREVIEW_REASSERT_AFTER_INVENTORY_MOVE`

Gear edits continue to refresh the preview from the authoritative `NpcInventoryRepository.Session`
snapshot. Closing the page still restores the original viewer model, skin, and equipment through
the existing preview-session safeguards.

### Jonalith worked while Mara rejected transfers

The connected server log showed different authorities:

- Jonalith: `CUSTOM_BRIDGE_TO_LIVE_NPC_STORAGE`
- Mara: `NOT_SPAWNED inventoryMode=READ_ONLY_PROFILE_SNAPSHOT`

Mara's profile opened successfully, but because no Mara entity was active, the UI used a detached
authoring container and deliberately omitted the transaction bridge. The screen looked writable
while every transfer was effectively unavailable.

R124 retains live ECS Storage as the preferred authority. When an existing NPC profile is not
spawned, it now uses a mutable `NpcInventoryRepository.Session` as
`CUSTOM_BRIDGE_TO_PERSISTED_AUTHORING_STORAGE`. That container is registered in the page's real
`ContainerWindow`, uses the same validated bridge, and writes the same `npc-inventory.json`.
Spawning the NPC later hydrates its ECS inventory from that persisted state.

## UI changes

- Removed the redundant `Open Native Inventory` button and its event/codec/callback path.
- Removed the text labels for Head, Chest, Hands, Legs, Primary Weapon, Shield / Offhand, and
  Preferred Ammo.
- Retained the verified in-game-style armor slot images already packaged with the mod.
- Realigned the three loadout slots to the same 68-pixel row geometry as Head, Chest, and Hands.
- Positioned the Infinite Ammo control on the fourth row, aligned with Legs.
- Added a red Delete button between Cancel and Enter.
- Added a separate confirmation overlay containing `Delete <NPC name>?`, No, and Yes.

No verified weapon/offhand/ammunition empty-slot icons were present in the project's packaged
resources or discoverable cache-index strings. R124 leaves those native empty slots unlabelled
rather than inventing art. Suitable assets can be added later without changing transaction logic.

## Delete transaction

Delete is available only while updating an existing profile. No performs no mutation. Yes:

1. closes the inventory bridge so late events fail closed;
2. restores and closes the mesh preview;
3. flushes and closes the inventory persistence session;
4. removes any active world entity for the selected profile, or accepts that it is absent;
5. ends conversation/runtime state for the profile;
6. unregisters the profile and managed-role mappings;
7. recursively deletes exactly the validated direct child profile directory.

The deletion path is normalized and must be one direct child of the configured profiles root.
Traversal and broad-root deletion are rejected. The native NPC builder cache cannot be explicitly
evicted by the exposed API; its mapping is retired immediately and the cached role is replaced on
same-name recreation or cleared by restart.

## `/npc create <name>` scaffolding

The command now immediately creates:

- `profiles/<name>/<name>.json`, containing a valid editable template and stable identity;
- `profiles/<name>/npc-inventory.json`, containing the empty schema-v2 inventory state bound to
  that identity.

Later profile JSON selection preserves the stable identity allocated by the create command. The
full profile editor remains future work.

## Automated validation

`test.ps1 -SkipLive` passed the complete deterministic project suite. The new R124 regression gate
also verifies:

- the internal-move rejection no longer exists;
- preview reassertion is wired after committed inventory moves;
- unspawned profiles receive mutable persisted authoring storage;
- the redundant button and gear text labels remain absent;
- the delete confirmation controls remain packaged;
- template creation and recursive profile-directory deletion round-trip safely.

The packaged JAR audit confirmed the R124 manifest, NPC Profile UI, delete confirmation, and the
generated section-bound inventory documents through section 1024.

## Connected acceptance checklist

1. Open `/npc update Jonalith`; move Player -> Jonalith -> different empty Jonalith slot -> Player.
2. Repeat while holding a visible hotbar weapon; Jonalith's preview must not inherit it after any
   inventory move.
3. Open `/npc update Mara` while Mara is not spawned; Player -> Mara and reopen persistence must
   work.
4. Confirm the native-inventory button and gear text labels are gone and the four rows align.
5. Use Delete -> No and verify nothing changes.
6. Use `/npc create R124TemplateCheck` and verify its two generated JSON files before testing the
   destructive Delete -> Yes flow on that disposable profile.

## Deployment

- Active JAR: `ImmersiveNPCs-0.6.0-pre.13.1-R124-NPC-PROFILE-POLISH.jar`
- SHA-256: `BBAE9340409853EB8F5BD1661B7A7F43AEFA64E29714E241F9DF7EE417E3C830`
- Size: `2,417,989` bytes
- Rollback: R123 preserved separately with SHA-256
  `4D681FB945B7017B702F9E2285305DAD351C1386D8AD19243418267452037B09`
