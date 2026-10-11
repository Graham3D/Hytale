# R261-C development contract

This is an offline development milestone on `codex/r261c-progression-mana-hud`.
The active RPG save retains R260. R261-C is not a connected-game acceptance build.

## Player resources

Natural maximum Health at character level `L` (1–99) is
`100 + floor(288 × (L − 1) / 98)`. Representative values are 100, 126,
170, 244, 317, and 388 at levels 1, 10, 25, 50, 75, and 99.
This changes only the derived player maximum. Earned levels, allocated
attributes, monster Health, and save payloads are unchanged.

Effective STR and INT use the existing five-segment diminishing-return
curve and the canonical starting effective attribute of 10. Derived maximum
Health adds `2 × max(0, effective STR − 10)`; derived maximum Mana adds
`2 × max(0, effective INT − 10)`. Gear's flat capacity remains additive.
The native EntityStatMap holds current values. Recalculation preserves
current Health and Mana on capacity increases and clamps on decreases;
it does not heal, revive, or refill a character. Managuard reservation
projection remains the owner of reserved Mana capacity.

## Mana Before Life

Gear Master v1's 160 affix identities remain frozen. `affixes-v2.json`
adds armor-only `GA-161`, **of Warding Mana**, as a suffix on Head, Chest,
Hands, and Legs. It has ordinary Q tiers and a top tier of 12–18%; lower
tiers follow the established Q multipliers and 0.1% roll grid. Production
generation and equipped-gear admission use the existing Gear Master owner.
Values from admitted equipped armor add, then the combined fraction is
capped at 60%. Inventory gear and held weapons do not contribute.

The native post-mitigation damage path is durable Cyclone, other established
barriers and support defenses, Mana Before Life, then native Health.
The conversion applies once to any uncancelled positive damage still headed
to player Health, including periodic, environmental, reflected, and secondary
hits. It consumes at most the eligible current spendable, unreserved native
Mana at one Mana per damage. Native readback determines the confirmed spend
and the Health reduction. If a write fails before changing Mana, Health
damage stays intact. If it throws after a readable native debit, only that
observed debit protects Health. If readback is unavailable, full Health
damage remains; no speculative refund or retry is issued. This conservative
boundary can produce a double cost if native Mana changed but became
unreadable, so it must be audited in R261 Final.
The before/write/readback interval holds the existing resource-owner lock so
concurrent hits cannot claim one another's Mana debit. That lock is shared
across players and is part of the performance review.

The protected QA command already requires `inigmasgames.rpg.gear.author`.
Its `ab-ga-161-control` and `ab-ga-161-affixed` fixtures use the existing
saved item-delivery path. These IDs do not introduce an unrestricted grant.

## Presentation and outstanding work

Hytale continues to own native Health and Mana bars. The existing Hywind HUD
shows their native current/maximum values and a distinct Cyclone Barrier meter.
The meter reads the in-memory durable ledger state through the live session,
has no duration countdown, and disappears on depletion or invalidation.
The normal HUD poll suppresses unchanged messages and keeps a session revision
floor to reject older projections, including after a fail-closed clear.
Connected UI packet
ordering, layout at supported scales, and damage-to-screen latency still
need final acceptance.

R261-B's reconnect forfeiture, Shared Aegis restriction, and same-player
forced-write contention remain open. The new Mana conversion adds resource
work on the native damage path; offline timing cannot prove live tick safety.
R261 Final must resolve or explicitly accept these boundaries before merging
to `main` or deploying.

## Offline damage bridge timing

The focused `R261CCombatLatencyTest` measures the Cyclone ledger and Mana
resource bridge, excluding ECS scheduling, native ApplyDamage, UI traffic,
and connected-game disk contention. The first local run measured milliseconds
as follows (p50 / p95 / p99 / worst): barrier only 2.419 / 3.443 / 4.937 /
4.937; Mana conversion only 0.005 / 0.031 / 2.111 / 2.111; combined
2.181 / 2.367 / 3.652 / 3.652; Health overflow 0.003 / 0.009 / 0.013 /
0.013; eight concurrent hits on one player 0.003 / 0.202 / 0.735 /
0.802; eight players 0.002 / 0.110 / 3.465 / 3.579. The bridge serializes
native Mana read/write/readback through `RpgResourceService` across players;
the durable Cyclone ledger still forces storage writes before absorption.
These timings are exploratory, not live tick guarantees; the R261-B recorded
160.398 ms worst case remains a separate unresolved risk.
