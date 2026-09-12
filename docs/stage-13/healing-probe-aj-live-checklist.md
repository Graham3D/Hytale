# R032-AJ-LIVE: test in your normal RPG single-player world

This live-test JAR automatically registers the diagnostic commands. **No disposable server, Direct Connect, JVM flags or repeated server authentication is required.** Open Hytale normally and load the existing RPG world. Administrative permission `inigmasgames.rpg.healingprobe` is still required; the mod does not rewrite permission grants.

The normal gameplay HUD is unchanged. Identify this build by the live-test startup marker:

```text
RPG_HEAL_PROBE revision=R032-AJ enabled=true ... liveTest=true disposableWorldRequired=false
```

## Run one control at a time

Stand in a clear area and look horizontally forward. Wait **at least 12 seconds between commands**. Each control runs for ten seconds; chat reports STARTED/FAILED/ENDED.

```text
/rpg-heal-probe world none
/rpg-heal-probe empty none
/rpg-heal-probe visible none
/rpg-heal-probe beam none
```

These compare the existing stock world particle, empty carrier, visible carrier and existing native Beam geometry. The visible carrier's model is separate from its particles. No NPC is needed. The existing geometry material is not a proposed new Healing Beam appearance.

Switch to third person to see the health-pack effect on yourself:

```text
/rpg-heal-probe recipient-once self
/rpg-heal-probe recipient-overwrite self
```

To inspect an existing NPC instead, replace `self` with its **current runtime entity UUID**, while it is nearby and visible. This borrows only the entity identity/anchor; it does not spawn, move, damage, heal, edit or save that NPC. `native` recipient creation is deliberately rejected in the live-test build; no flat-world or NPC-creation workflow is required.

Hold an audited vanilla staff, e.g. the Bronze staff, then run:

```text
/rpg-heal-probe staff-once none
/rpg-heal-probe staff-overwrite none
```

Observe staff effects in first and third person. No need to equip/cast Healing Beam. Stop the current control early with:

```text
/rpg-heal-probe stop none
```

## Important interpretation

Recipient and staff effects in this **live-test** variant are finite, cosmetic packets sent only to the invoking client. They do not enter the native saved EffectController. This is necessary to avoid saving diagnostic effects with a player/NPC, even if autosave happens during a control. It tests client presentation, **not** native effect-controller execution or replication to a second viewer. Trace stage is `CLIENT_EFFECT_SUBMITTED`, not `EFFECT_ATTACHED`; records identify `liveTest=true` and the client-only backend. Other native effects are not cleared or replaced.

Model/Beam controls use transient nonserialized entities. Cleanup removes owned entities/effects on stop, completion, invalid endpoint or owner departure. A standalone world particle has its finite native lifetime/tail; the audited API has no instance-specific early-cancel handle. Failed/disconnected client delivery cannot be asserted successful from a server packet receipt; a world unload/client reconnect resets client-only effects. No diagnostic effect is stored in player/NPC save components.

Record each mode, start time, whether it appears, blinking, movement and residue. Return the normal RPG server log and `mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl`, plus client video/log if a visual is missing. `OUTBOUND_OBSERVED` means observed before transport, not client-rendered proof.

Production Healing Beam, Blizzard, Health/Mana, cooldowns, progression and persistence architecture are unchanged. These commands are cosmetic controls, not a completed Healing Beam repair.
