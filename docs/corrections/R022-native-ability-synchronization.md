# R022 — Native Ability Synchronization Correction

R022 is a bounded correction of the R021 native Ability2/Ability3 synchronization
boundary. Stage 06 has not begun. Stage 04/05 skill behavior, execution, resources,
cooldowns, projectiles, damage, native ability-slot projection, HUD ownership, XP
presentation, and the Ability4 policy were not changed.

## Status

```ini
R022 = FAILED_CONNECTED_NATIVE_ABILITY_QA
Stage03 = IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION
Stage04 = IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION
Stage05 = IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION
Stage06Started = false
```

The source, packaged-asset, and isolated-server gates passed. The subsequent
2026-09-07 connected session FAILED: four successful projection transitions, zero
accepted initial ability observations and zero RPG activation/executor events.
The FirstClick synchronization hypothesis was insufficient. It is not an established
root cause or fix. See `R023-native-rune-control.md` and its retained R022 evidence.

## R021 connected failure evidence

The connected R021 session on 2026-09-07 proved that RPG loadout compilation and
native projection succeeded:

- Quick Slash was committed to `skill01` and projected as
  `RPG_Ability_Quick_Slash` at native Primary index 0 / Ability2;
- Fire Bolt was committed to `skill02` and projected as
  `RPG_Ability_Fire_Bolt` at native Primary index 3 / Ability3; and
- `/rpg dev ability-status` was run after both assignments.

R021 trace counts contained four `NATIVE_ABILITY_SLOT_PROJECTED` transitions but
zero `NATIVE_ABILITY_INPUT_OBSERVED`, zero `SKILL_ACTIVATION_REQUEST`, and therefore
no downstream validation, resource, cooldown, executor, projectile, or damage
event caused by those key presses. This places the earliest failure between the
rendered native item control and the server-side RPG input observer.

Retained external evidence at diagnosis time:

| Evidence | SHA-256 |
|---|---|
| R021 `skill-trace.jsonl` through `2026-09-07T16:27:43Z` | `BC7BE482678702F915652F4D0D68299930E46898C5193BA3F6A2519BDA6ED012` |
| R021 `2026-09-07_12-23-43_server.log` | `826547CB067F4ED967C2B5BC9031F6EA6D2AB2D4005819BA6FF424B72D039FE4` |

## Historical R022 hypothesis — not confirmed as the connected root cause

R021 used this bridge root:

```json
{"Interactions":[{"Type":"Simple","RunTime":0.01}],"RequireNewClick":true}
```

Inspection of the exact installed `0.7.0-pre.1` server implementation established:

- `SimpleInteraction.getWaitForDataFrom()` returns `WaitForDataFrom.None`;
- a branchless `SimpleInteraction.needsRemoteSync()` returns false; and
- the root consequently had no remote-synchronized operation.

The hypothesis was that this local operation did not induce the traffic consumed
by `HytaleAbilitySkillInputAdapter`. R022 changed the remote-sync properties but
did not recover casting. These static properties alone therefore do not establish
the connected failure's cause. The old observer also only logged accepted initial
ability chains: absence of that event must not be described as a raw packet count.

## Correction

The bridge now contains exactly:

```json
{
  "Interactions": [
    {
      "Type": "FirstClick"
    }
  ],
  "RequireNewClick": true
}
```

The exact installed `FirstClickInteraction` implementation was inspected before
the change:

- `getWaitForDataFrom()` returns `WaitForDataFrom.Client`;
- `needsRemoteSync()` returns true;
- `Click` and `Held` are optional; and
- when both are absent, compilation adds only the `FirstClickInteraction` operation.

No Click/Held gameplay branch was added. The operation is solely a client/server
synchronization primitive and contains no damage, stat, inventory, resource,
cooldown, projectile, status, effect, or launch behavior.

All 12 projected ItemAbility assets remain unchanged at:

```text
Cost = 0
CostType = None
Cooldown = 0
Cast = Root_RPG_Ability_Bridge
```

RPG remains the only gameplay authority after the synchronized native interaction
is observed.

## Packaged asset resolution proof

R022 adds a one-shot listener to Hytale's actual `LoadedAssetsEvent` for
`RootInteraction`. It inspects the decoded and compiled packaged asset, not a
second hand-authored model. When the bridge appears in the loaded-assets event,
the listener rejects it if it contains more than one operation, is not `FirstClickInteraction`, does not wait for
the client, does not require remote synchronization at both operation and root
levels, or is not effect-free by its single branchless operation topology. Correction:
the listener returns when this event does not contain the root; it is not a standalone
absence gate. The smoke script separately requires the successful audit log.

The isolated three-mod server emitted:

```text
RPG_NATIVE_BRIDGE_AUDIT revision=R022 root=Root_RPG_Ability_Bridge
exists=true operations=1 operation=FirstClickInteraction waitFor=Client
operationRemote=true rootRemote=true effectFree=true result=PASS
```

This proves Hytale accepted, resolved, and compiled the bridge through its installed
asset path. It does not substitute for connected client input proof.

## Protected surfaces

The automated change-boundary check found no modifications to:

- Stage 04 executors;
- Stage 05 `ProjectileFamilyExecutor`;
- `SkillExecutionService`;
- combat/resource/cooldown calculations;
- `NativeAbilityProjectionService` or its tick system;
- any ItemAbility projection asset;
- native HUD ownership or custom HUD code;
- R020 Health/Mana/Stamina ownership;
- R020 XP UI or graphics; or
- Ability4 behavior.

The owner-updated files currently present in the separate `art` folder were not
incorporated because R022 is synchronization-only.

## Verification and deployment

| Gate | Result |
|---|---|
| Branch | `RPG` |
| Implementation commit | `ef36fbf0d33e3a53771382197edb74ebc415b2b6` |
| Revision/version | `R022` / `0.0.15` |
| Hytale target | `0.7.0-pre.1` |
| Aggregate retained tests | PASS — 136, 0 failures/errors/skips |
| Source CustomUI validation | PASS — 16 documents |
| Packaged RPG CustomUI validation | PASS — 9 documents |
| Single branchless FirstClick source bridge | PASS |
| Installed FirstClick waits for client | PASS |
| Installed FirstClick needs remote sync | PASS |
| Packaged root decoded/compiled by server | PASS |
| Runtime root/operation remote sync | PASS |
| Gameplay-mutating bridge operation | absent |
| ItemAbility native cost/cooldown authority | zero/none — unchanged |
| Protected mechanics/HUD files changed | no |
| Isolated three-mod server smoke | PASS — ready, plugin enabled, clean shutdown |
| Connected client activation | FAILED — session 2026-09-07 17:13–17:16 UTC |
| RPG JAR SHA-256 | `27012D3095D450895D690EB00DD820BB79A971F52A0944156B6C4D8F5BF9B7D9` |

Deployment contains exactly:

| Mod | SHA-256 |
|---|---|
| `CanvasUI-0.1.0.jar` | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| `HYTALEDEVLIB-0.5.0.jar` | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| `HytaleRPG-0.0.15.jar` | `27012D3095D450895D690EB00DD820BB79A971F52A0944156B6C4D8F5BF9B7D9` |

Machine-readable verification, installed implementation audit, server smoke, and
installation records are retained in `evidence/corrections/R022/`.

## Rollback

The exact deployed R021 `HytaleRPG-0.0.14.jar` is retained at
`evidence/corrections/R022/rollback/HytaleRPG-0.0.14.jar`. To roll back, fully stop
the RPG world, remove `HytaleRPG-0.0.15.jar`, and restore that JAR. No schema changed.

## Connected acceptance boundary

Use `docs/corrections/R022-client-verification.md`. Acceptance requires one
correlated Ability2 sequence through executor dispatch and one Ability3 sequence
through projectile spawn, with exactly one RPG resource commit and one RPG cooldown
per activation and no duplicated native gameplay.

If `FirstClick` still produces no `NATIVE_ABILITY_INPUT_OBSERVED`, R022 remains
blocked at native interaction synchronization. Do not modify Stage 04/05 executors;
instead compare this root with a known-working shipped `0.7.0-pre.1` Primary Rune
root and identify the next native prerequisite.
