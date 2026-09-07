# R022 — Connected Client Verification

Fully stop and restart the RPG world, then rejoin.

1. Run:

   ```text
   /rpg equip skill01 quick_slash
   /rpg equip skill02 fire_bolt
   /rpg dev ability-status
   ```

   Confirm both report `PROJECTED` at native Primary indices 0 and 3.
2. Equip a compatible sword, face a valid nearby target, and press the configured
   native Ability2 control exactly once. Confirm Quick Slash visibly executes once.
3. Equip a compatible Staff or Wand, aim into clear space toward a valid target,
   and press native Ability3 exactly once. Confirm exactly one Fire Bolt projectile.
4. Do not press either ability again during this capture. Leave the world normally
   and retain the newest server/client logs and `skill-trace.jsonl`.

The trace must prove:

```text
NATIVE_ABILITY_INPUT_OBSERVED
-> SKILL_ACTIVATION_REQUEST
-> SKILL_VALIDATION_PASS
-> SKILL_COMMITTED
-> EXECUTOR_DISPATCH
```

Fire Bolt must additionally contain `PROJECTILE_SPAWN_REQUEST` and
`PROJECTILE_SPAWNED`. Each activation must show exactly one resource commit and one
cooldown start, with one executor dispatch and no duplicate native damage or
projectile.

If either native press still produces no `NATIVE_ABILITY_INPUT_OBSERVED`, stop the
test. Do not use a development command to bypass native input and do not test or
alter Stage 04/05 executors.
