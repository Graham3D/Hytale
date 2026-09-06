# Stage 01B R009 connected-client verification

This checklist closes the one proof gap that cannot be exercised by the
headless server: commands executed by a real player followed by a complete
world restart and rejoin.

## Preconditions

- Stop the RPG world before changing JARs.
- Confirm the save contains exactly these three files in `Saves/RPG/mods`:
  `HYTALEDEVLIB-0.5.0.jar`, `CanvasUI-0.1.0.jar`, and
  `HytaleRPG-0.0.2.jar`.
- The R009 RPG JAR SHA-256 must be
  `98E1DFC00620A3C15D6BA5DC08294F99CB0DCEF3A98F1BCF6522F45E54446BD5`.
- Start the RPG world and join it normally.

## Required command sequence

Run these commands in order and retain screenshots or the matching server log:

```text
/rpg equip skill02 firebolt
/rpg equip passive06 fork
/rpg link passive06 skill02
/rpg loadout
/rpg compile
```

Expected result:

- all three mutations report success;
- `skill02` resolves to Fire Bolt;
- `passive06` resolves to Fork and routes to `skill02`;
- compile reports `PASS`;
- the compiled continuation contains
  `FORK(children=2,angles=-20/+20,depth=1)`.

Then run the required rejection proof:

```text
/rpg equip skill01 quickslash
/rpg link passive06 skill01
/rpg loadout
```

Expected result:

- the link command is rejected with a typed compatibility failure because
  Quick Slash is not a projectile/fork-capable skill;
- the last valid route, `passive06 -> skill02`, remains present;
- no partial state is persisted.

## Restart and persistence proof

1. Exit the world and stop it completely.
2. Start the same RPG world again and rejoin.
3. Run `/rpg loadout` and `/rpg compile`.

Expected result: Fire Bolt, Fork, the `passive06 -> skill02` route, and the
same compiled Fork semantics survive the restart. The rejected Quick Slash
route must remain absent.

## Evidence locations

- Server log: `Saves/RPG/logs/<timestamp>_server.log`
- Structured trace:
  `Saves/RPG/mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl`
- Player state:
  `Saves/RPG/mods/InigmasGames_HytaleRPGPhase00Audit/players/<player-uuid>.json`

The trace should contain correlated equip, link, compile, save/load, and
rejected-link events. Diagnostics are asynchronous and fail-safe; lack of a
trace write must not change the gameplay transaction result.

## Result record

- Connected-client command sequence: **PENDING**
- Rejected-link rollback: **PENDING**
- Full world restart/rejoin persistence: **PENDING**
- Client crash or CustomUI dependency: not expected; these commands do not
  open or depend on CanvasUI.
