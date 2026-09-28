# Checkpoint C: Optional HyARPG / ImmersiveNPCs Bridge

Checkpoint C keeps both products independently loadable while allowing HyARPG
to publish a deliberately small set of authoritative gameplay facts when
ImmersiveNPCs is installed.

## Contract

- API version: `1`
- Discovery: Hytale `PluginManager` and manifest `OptionalDependencies`
- Producer capability: `COMBAT_EVENTS`
- Consumer capability: `NPC_EVENT_OBSERVATION`
- Current event: post-apply, non-cancelled `ENTITY_DAMAGED`

The neutral API contains immutable data records only. It contains no HyARPG,
Tavern, Orbis, provider, voice, persistence, or cognition implementation.
HyARPG does not package the API; it resolves the contract from ImmersiveNPCs
through Hytale's optional-dependency classloader only when that plugin exists.

## Failure behavior

- HyARPG only: logs `status=NO_OP`; gameplay continues.
- ImmersiveNPCs only: no HyARPG lookup or dependency is required.
- Compatible pair: API v1/capabilities are negotiated and observations enter
  the existing ImmersiveNPCs event bus.
- Incompatible provider: one `status=DISABLED` diagnostic is emitted and only
  the bridge becomes a no-op.

Bridge publication catches provider failures and disables subsequent delivery;
it cannot cancel or roll back authoritative ARPG combat.

## Ownership

HyARPG remains authoritative for combat and damage. ImmersiveNPCs may observe
the resulting facts for NPC cognition, but cannot execute ARPG gameplay through
this contract. No AI runtime or ImmersiveNPC data is packaged in HyARPG.
