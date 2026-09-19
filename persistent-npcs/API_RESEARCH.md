# Hytale API Research — through R016

Research date: 2026-08-27.

## R016 incompatible-runtime crash containment

The R015 connected-client log again booted release `0.5.9`. Spawn completed, but
the first intelligence tick linked Update 6's `PositionCache.get(Ref,
ComponentAccessor)` and crashed the world thread. R016 detects the required Update
6 NPC signatures during setup. On an older runtime it does not register NPC tick,
chat, or interaction hooks and blocks AI commands with a launcher-channel
diagnostic. This also protects worlds containing an entity saved by the failed
spawn. It does not claim that release 0.5.9 supports the Update 6 voice milestone.

## R015 spawn compatibility finding

The connected-client failure log booted Hytale `0.5.9` from the launcher's
`release` channel even though Update 6 pre-release was installed. Release 0.5.9
exports `Rotation3f.lookAt(Vector3d)`, while Update 6 exports
`Rotation3f.lookAt(Vector3dc)`. That return-compatible source change is a binary
ABI break and caused the legacy test spawn path to throw `NoSuchMethodError`. R015 calculates
the equivalent pitch/yaw and calls the stable `Rotation3f(float,float,float)`
constructor. The regression test executes against both locally installed JARs.
The plugin manifest deliberately continues to require Update 6 because the
official entity-voice and player-interceptor APIs are not a release 0.5.9 feature.

## R014 Update 6 pre-release voice baseline

R014 compiles directly against `HytaleServer v0.6.0-pre.13.1`. The installed
public API was verified with `javap`: `VoiceModule.openEntityVoice(Ref)`,
`addPlayerVoiceInterceptor`, `VoiceSpeaker.play(List<byte[]>)`, and
`ClipPlayback.completion()` are present without reflection. `PlayerVoiceFrame`
is callback-scoped, so the plugin copies only its Opus bytes and identity before
returning. Normal proximity routing is not changed.

`VoiceSpeaker.play` requires immutable 48 kHz mono Opus frames of at most 512
bytes and paces them at 20 ms. R014 retains each immutable frame list until the
completion stage fires and serializes clips per NPC. The entity speaker is opened
on the world tick when social listening starts, before synthesis completes.

Update 6 moved `PositionCache` from `Role` to an ECS component and expanded
`Role.setMarkedTarget` with the owning entity/accessor. R014 uses those new direct
signatures. The removed `Role.isFriendly` surface has no verified replacement in
this pass, so perception now reports no invented hostile classification.

Primary references:

- [Update 6 pre-release notes](https://hytale.com/news/2026/5/pre-release-patch-notes-update-6)
- [Pre-release VoiceModule](https://pre-release.docs.hytale.com/api/com/hypixel/hytale/server/core/modules/voice/VoiceModule)
- [Pre-release VoiceSpeaker](https://pre-release.docs.hytale.com/api/com/hypixel/hytale/server/core/modules/voice/VoiceSpeaker)
- [Pre-release PlayerVoiceFrame](https://pre-release.docs.hytale.com/api/com/hypixel/hytale/server/core/modules/voice/PlayerVoiceFrame)
- [Resemble AI Chatterbox](https://github.com/resemble-ai/chatterbox)

## Exact installed baseline

The implementation was compiled and tested against the locally installed
release artifact:

```text
HytaleServer.jar Implementation-Version: 0.5.9
Implementation-Revision-Id: 214c57c5a63e6e5d51ed8be4c8a508dfcc177d16
SHA-256: C3807061935FCE64026CADB0111E1C50B1A9C85890744687AE6F309D465EC831
Java: 25.0.4
```

Signatures were verified with `jar`/`javap` against that artifact and checked
against the current official Hytale Javadocs. The official Hytale modding
status also says Java server plugins are the programmatic extension mechanism,
while NPCs are data-driven assets; it warns that current documentation and
exposure are incomplete. Sources:

- [Hytale modding strategy and status](https://hytale.com/news/2025/11/hytale-modding-strategy-and-status)
- [Hytale Server API Javadocs](https://docs.hytale.com/)
- [Hytale server manual](https://support.hytale.com/hc/en-us/articles/45326769420827-Hytale-Server-Manual)

## Verified APIs used

### R003 intelligence surfaces

The installed Role exposes setMarkedTarget, getHeadSteering, and
getPositionCache. PositionCache.hasLineOfSight is the public NPC visibility
check. HeadRotation and Steering expose yaw/pitch setters.

NPCEntity.getPathManager, setLeashPoint, and a BodyMotion Seek role instruction
with UsePathfinder provide native pathfinding.

InventoryComponent.getItemInHand, Hotbar, Storage, CombinedItemContainer,
ItemStack, transactional moveItemStackFromSlot, ItemComponent, and
ItemUtils.dropItem are public item surfaces.

CraftingRecipe.getAssetMap, CraftingManager input/output helpers,
BlockType.getBench, and BenchRequirement expose real loaded recipes and
stations. WorldTimeResource exposes game time for persisted appointments.
Only already-loaded chunks are scanned during tick-adjacent work.

### Plugin lifecycle and data directory

`JavaPlugin` extends `PluginBase`; the local built-in plugins use `setup()`,
`start()`, and `shutdown()`. `PluginBase` exposes `getDataDirectory()`,
`getCommandRegistry()`, and `getEventRegistry()`. The framework uses only these
public surfaces. [Official `PluginBase` documentation](https://docs.hytale.com/com/hypixel/hytale/server/core/plugin/PluginBase)

### Commands and world-thread work

`AbstractPlayerCommand` runs its `execute(...)` body on the sending player's
world thread and supplies `Store<EntityStore>`, player entity `Ref`,
`PlayerRef`, and `World`. The native NPC spawn integration uses it only for the short Hytale
entity mutation. No HTTP or persistence wait happens there.
[Official `AbstractPlayerCommand` documentation](https://docs.hytale.com/com/hypixel/hytale/server/core/command/system/basecommands/AbstractPlayerCommand)

### NPC assets, spawn, and identity

`NPCPlugin.spawnNPC(Store<EntityStore>, String npcType, String groupType,
Vector3dc, Rotation3fc)` is present and returns the spawned entity ref plus
`INonPlayerCharacter`. The test role is a packaged JSON NPC role and is spawned
through this API. `UUIDComponent` supplies the Hytale entity UUID. Separately,
the framework profile owns a stable UUID used for memory and relationships.
[Official `NPCPlugin` documentation](https://docs.hytale.com/com/hypixel/hytale/server/npc/NPCPlugin)

Update 5 changed persisted entity naming from `DisplayNameComponent` to
`PersistentDisplayName`; the adapter uses `PersistentDisplayName` plus the
runtime `Nameplate` component.
[Official Update 5 patch notes](https://hytale.com/news/2026/5/update-5-patch-notes)

### Chat input

`PlayerChatEvent` is an async, cancellable event with sender, targets, content,
and formatter. A focused player's event is cancelled immediately, then an HTTP
future is started without holding the event pipeline. Unfocused chat is not
changed. [Official `PlayerChatEvent` documentation](https://docs.hytale.com/com/hypixel/hytale/server/core/event/events/player/PlayerChatEvent)

### Player/NPC interaction

The installed artifact exposes `PlayerInteractEvent` with action type, target
entity, and target ref, but the current official Javadocs mark the entire class
deprecated. The milestone uses only `Use`/`Secondary` for Mara and normal nearby
chat as the supported fallback.
[Official `PlayerInteractEvent` documentation](https://docs.hytale.com/com/hypixel/hytale/server/core/event/events/player/PlayerInteractEvent)

This is a documented limitation, not a claim that a stable replacement exists.
The next milestone should validate a custom `UseEntityInteraction` asset/config
path against the exact then-current server API before migration.

### Async HTTP and returning to the game thread

No Hytale-specific outbound HTTP abstraction is required or documented for
this use. Hytale requires Java 25, so the provider uses the JDK's
`HttpClient.sendAsync`. `World` implements `Executor` and exposes
`execute(Runnable)`; completion delivery is queued there.
[Official `World` documentation](https://docs.hytale.com/com/hypixel/hytale/server/core/universe/world/World)

### Persistence

The public plugin API provides a plugin-specific data directory. It does not
require a particular persistence database for plugin-owned data. Milestone 1
therefore uses small atomic JSON files in that directory. Hytale remains
authoritative for world/entity data; this storage contains only framework
profiles, relationships, and memory summaries.

## Unsupported assumptions deliberately avoided

- No client mod, custom client input, Noesis GUI, voice capture, or speech API.
- No claim that Hytale automatically respawns this test entity after restart.
- No unregistered model function can invoke Hytale code, and no function gets
  arbitrary command access.
- No model-generated entity name or UUID is trusted.
- No global chat history, full world state, or another player's memory is sent.
- No semantic/vector memory capability is claimed.
- No request is awaited from a Hytale world/server tick.
- No simulated crafting success when a recipe, station, or ingredient check
  fails.
- No invented semantic resolver for phrases such as “behind the house.”
- No simulated unloaded-entity movement; tasks remain persisted until loaded.

## R012 cognition, performance, grounding, and appearance audit

- `HeadRotation` plus NPC role head steering is the installed, working look API.
  R012 temporarily clears the social marked target, steers through up to three
  authoritative semantic POIs, and restores the focused player without blocking a tick.
- `AnimationUtils.playAnimation(..., AnimationSlot.Emote, ...)` accepts the installed
  non-looping `Characters/Animations/Emote/Shrug.blockyanim`; R012 uses it only for an
  uncertainty appraisal. No unsupported free-form animation name is generated by the LLM.
- `WorldChunk.getHeight`, loaded block access, `TransformComponent.teleportPosition`,
  `Velocity`, and `MovementStatesComponent` provide the grounding/recovery path. The
  resolver never loads a new chunk and never copies player flying state.
- The Mara export contains both `SS_SKIN_Mara.json` (`PlayerSkin` cosmetic IDs) and a
  generated `SS_MODEL_Mara.json`. R012 parses and validates the skin snapshot with
  `CosmeticsModule`, then installs its exact generated `Model` into Mara's `ModelComponent`.
  This preserves the NPC role's walk controller and head tracking. Fields absent from the
  exported skin snapshot (facial hair, gloves, cape, and unused over-layers) remain unset;
  the mod does not invent replacements.

## R013 Chatterbox and Hytale playback audit

- The official original Chatterbox Python API accepts an optional
  `audio_prompt_path` and exposes `exaggeration`, `cfg_weight`, and `temperature`.
  Its built-in conditionals support a temporary voice when no reference is supplied.
  R013 uses that only as an explicitly reported fallback; it does not ship or clone a
  copyrighted character recording.
- Installed Hytale 0.5.9 exposes player-owned `VoiceData` input and
  `RelayedVoiceData` output using Opus. `VoiceRouter.routeVoiceFromCache` takes a
  `PlayerRef` speaker; no public listener registration or NPC audio source API exists.
- `PlaySoundEventEntity` can spatialize an existing `soundEventIndex` on an entity,
  and `UpdateSoundEvents` distributes asset definitions. Neither accepts runtime WAV,
  PCM, or a filesystem path. Dynamically generated speech therefore cannot be loaded
  through the supported asset-backed sound-event path.
- Directly forging `RelayedVoiceData` packets would couple the mod to player transport
  internals and is not treated as supported NPC playback. R013 stops at verified WAV
  synthesis until Hytale exposes a legitimate runtime audio or NPC voice source hook.

## Risks

1. `PlayerInteractEvent` may be removed before 0.6.0 despite the manifest range;
   normal nearby chat remains available, but proper interaction needs migration.
2. Hytale's early-access server API is changing quickly; every release needs a
   compile plus in-game smoke test, not only a version-range change.
3. OpenAI-compatible servers differ at their edges. R003 handles conventional
   message content, SSE content deltas, fragmented tool-call deltas, and JSON
   fallback; other provider-specific shapes still require an adapter.
4. File persistence is synchronized within one plugin process, not designed for
   shared multi-server writes.
5. Normal chat is intentionally captured while focused; moderation/logging
   integration and explicit chat-channel UX are future work.
6. Entity interaction, role validation, appearance, and actual response display
   still require a connected-client smoke test. An isolated real-server start
   did load the JAR and asset pack, complete NPC validation, enable the plugin,
   reach the fully booted state, and shut down cleanly, but it could not drive a
   player interaction.

## R032 installed Update 6 voice audit (0.6.2)

- `VoiceModule.openEntityVoice(Ref<EntityStore>)` and
  `openPositionalVoice(World, Vector3d)` are the supported spatial generated-audio
  sources. `VoiceSpeaker` exposes `pushOpus`, `play`, `isOpen`, and `close`;
  `ClipPlayback` exposes only `cancel`, `isDone`, and `completion`.
- The installed API has no per-`VoiceSpeaker` distance or gain setter. Hearing and
  reference distance are module-global values. R032 keeps the already working entity
  speaker and records the configured NPC speech radius without changing the global
  value per utterance.
- `PlayerVoiceFrame` supplies one authoritative speaker, world, position, timestamp,
  and Opus frame to an interceptor. Its routing methods operate on that captured player
  frame; they are not an NPC-output API. R032 copies the frame once and performs one STT
  pass before constructing its immutable multi-listener event.
- `VoiceRouter` exposes cached player routing and configuration delivery but no public
  generated-speaker audience/range control. Direct voice would remove spatial direction,
  so it is not used for NPC call-backs.
# R006 API audit addendum (installed API, 2026-08-26)

Detailed action-by-action findings and blockers are in `docs/ACTION_SUPPORT_MATRIX.md`; voice, vision, appearance and reliability findings are in `docs/FRAMEWORK_STATUS_R006.md`. Those documents distinguish classes found in the installed server JAR from behavior that was actually implemented and tested.
