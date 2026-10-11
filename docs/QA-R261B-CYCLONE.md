# R261-B Cyclone Armor protected test notes

R261-B is an opt-in candidate. The active RPG save keeps its R260 `Hywind.jar`.
Test the candidate only in a separate protected environment. Do not copy
the candidate into the active RPG save before a separate deployment decision.

## Gate and startup

The gate is off unless the integrated server JVM receives
`-Dhywind.cyclone.durable=true`. Set that JVM property for the protected
test launch, install the candidate JAR there, and restart the game. The
server log must contain `RPG_CYCLONE_DURABLE_GATE enabled=true` before
testing. Remove the JVM property and restart to return to R260 Cyclone
behavior. A cast cannot switch modes at runtime.

On Windows, a launch environment can supply the argument with
`$env:JAVA_TOOL_OPTIONS='-Dhywind.cyclone.durable=true'` before starting
the protected Hytale client; verify the integrated server log because a
launcher may filter environment variables. Clear it afterward with
`Remove-Item Env:JAVA_TOOL_OPTIONS` in that PowerShell session.

## Behavior to check

1. With less than 100 spendable Mana, cast Cyclone. It must reject without
   spending Mana or starting cooldown. Repeat with exactly 100 and then with
   more than 100; a successful cast must spend the full unreserved balance.
2. Confirm the permanent Cyclone particle and status icon appear with no
   duration countdown. No finite ten-second shield should coexist.
3. Take mitigated damage below and above remaining barrier HP. Barrier
   absorbs first; overflow reaches existing defenses and then native Health.
   Repeating a damage receipt must not absorb or reflect twice.
4. With Reflective Ward linked, verify 20% less initial barrier capacity and
   reflection of 20% of confirmed absorption only. A depleted hit reflects
   only the absorbed portion; reflected damage cannot reflect again.
5. Attempt to recast while active, then after depletion and cooldown.
   The active cast must reject; the depleted barrier can be replaced by
   exactly one new generation after Mana and cooldown are eligible.
6. Die with an active barrier. The barrier and status indication must
   disappear. Reconnect or restart with an active barrier: it must be
   forfeited before the character is ready, without another Mana charge,
   automatic refund, or fabricated replacement barrier. Cooldown debt may
   remain.
7. Change worlds and return. The old session cannot duplicate protection.
   Unrelated temporary shields, monster shields, Managuard, and other skills
   should keep their established behavior.

## Known limits and performance

Native Mana persistence has no durable identity shared with the Cyclone
journal. A reconnect or restart forfeits surviving protection, even if the
original Mana payment was saved. This is intentional until reliable native
payment reconciliation exists. Shared Aegis's derived ally shield is not
created by the opt-in durable Cyclone path; a cast linked with it rejects
before payment. The full barrier/Health/Mana HUD redesign remains
R261-C work; the infinite native effect supplies only an active-status icon.

The debit bridge synchronously forces a per-player journal record on the
native damage-processing call stack. Offline measurements include same
player and separate-player contention, but cannot prove integrated-server
frame time. Watch for pauses under several rapid hits to one player in the
protected environment; report any observed pause.
The feature gate stays off for the active RPG save and production until
connected-game acceptance and a separate deployment decision.
