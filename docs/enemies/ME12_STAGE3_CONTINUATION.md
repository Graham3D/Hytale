# Stage 3 continuation: implemented changes and remaining blockers

Starting source: Guardian-published `63fcb510e1a65ba6491b5564fdb20e901cb2360a`.
Branch: `codex/me12-stage3`. Candidate: `R262ME12S3B-CANDIDATE` / Hytale 0.7.1.
State: **IN_PROGRESS / INTEGRATION_BLOCKED**, not engineering-complete.
**Guarded Stage 4 connected QA cannot safely begin with this checkpoint.**
No deployment, main change, active save edit, server launch or Stage 4 activation.
Monster Affix remains OPEN, ownerAccepted=false; Elite/HRO remain QA_PENDING.

## Implemented source corrections

* The default native context provider now reads existing staged spawn owners on the
  world thread: exact job provenance, native role/action bindings, existing source
  classifier, actual roster and transform, world/environment/chunk headroom and
  frozen policies. Unavailable hooks/roles remain explicit dependencies. Non-Unique
  births retain the existing path. This implementation is not a complete production
  certification result; the required full native-owner integration fixture is absent.
* Async planning freezes pooled `SpawnJobData` fields before the synchronous native
  job ends. Finalization uses immutable inputs, rechecks generation/lifetime, source
  identities and native capacity, and materializes only selected additions through
  the existing flock owner. Source revalidation ignores only the later observation
  timestamp; all identity/combat fields remain exact. Selected Packbound rosters
  recheck live distance before the existing durable root/publication sequence.
* Firesteel has an exact hash-pinned incoming-only attachment contract. It gains no
  outgoing action permission. Passive-only actors remain in final staged publication
  revalidation without allocating attack roots. Original native attacks remain owned
  by Hytale. Native attachment/execution/recovery integration proof is still required.
* Owner JSON configuration uses the existing learning registry and player transaction
  owner. Every prepared opportunity and progression delta records its immutable
  assignment archive. Invalid edits keep the prior mapping; historical identity
  remapping is rejected. Missing archives hold affected delivery/recovery, including
  an archive lost after a durable player intent; unrelated encounter delivery can
  continue. Existing player recovery serialization prevents mutation past a held intent.
* HRO separates natural v2 publication from QA/v1 using provenance, schema and the
  native receipt. It records source eligibility/revision and native effect presence
  for Cursed/Armor Breaker. All 27 operators have explicit evidence requirements;
  selection never becomes execution acceptance. Empty resource effects are
  NOT_APPLICABLE, malformed/out-of-bounds readbacks are BLOCKED/FAIL, and dropped
  evidence prevents PASS. Diagnostics do not own combat or progression.

Tier probabilities, signatures, exact sampler, 2-8 budget, v1 arithmetic, XP/quantity
formulas, item quality/MF, Champion/authored encounters, density/pack limits and the
309-role classification matrix are unchanged. All 21 Stage 2 pinned inputs remain
unchanged. No unknown role became a policy exclusion.

## Native blocker groups and certification boundary

| Group | Offline status / next required implementation | Connected-only evidence |
| --- | --- | --- |
| Goblin Turret and Scarak Seeker modern projectiles | BLOCKED. Modern `ProjectileInteraction` calls `ProjectileModule.spawnProjectile`; the existing pre-queue hook is for `LaunchProjectileInteraction`. Add exact creation/owner snapshots, persisted projectile identity, native impact-vector receipt ownership and removal/recovery, preserving the mixed-route guard. | Actual collision, attribution, impact effects, no duplicate damage and cleanup. |
| Seeker impact effects | BLOCKED as part of modern ownership. Installed Poison 10 packet leads to Antidote `Match=None`, clear/reapply Poison_T1, then projectile removal. Preserve the whole chain; the legacy Poison 14 projectile is not an equivalent route. | Antidote and Poison execution, expiry and no duplicated application. |
| Turret impact effects | BLOCKED as part of modern ownership. Installed Physical 8 packet retains Stone_Death effect, native point knockback, visuals/audio, hit/miss removal. Do not widen outgoing permissions to satisfy the budget. | Real original impulse and one damage receipt, hit/miss cleanup. |
| Mixed native flock extension | BLOCKED. Captured mixed members have exact per-member role checks. Future randomly selected `FlockSpawnTypes` cannot be certified by assuming the leader role. Need an exact existing-owner forecast/reservation of selected concrete roles before plan compilation. | Actual selected roster, density accounting, rollback and reload. |
| Production capture/provider | PARTIAL. World-thread capture, immutable async job inputs and fail-closed revalidation implemented. Full fixture from actual native capture/classifier through reserve/archive/attach/publication/rollback is still missing; boundary tests do not substitute for it. | Natural births, persistence across native unload/reload and ordinary spawn preservation. |
| Firesteel incoming-only route | PARTIAL. Explicit attachment and per-stage identity checks added; source/Health/control tests remain. Full production attachment/recovery fixture remains offline work. | Real self-defense/control/reflection, unchanged native attacks. |
| Native role scope | Original 309-ID matrix retained: 84 configured, 5 authored exclusions, 179 uncertified/unmapped and 41 catalog-only (5 retired); 16 Normal immunity exclusions. Further known-role certificates and unresolved owner scope decisions remain distinct. | Encountered native roles and their intended eligible effects. |
| QA activation | BLOCKED. Global ENABLED guard remains intact; there is no candidate-only activation authority yet. Do not interpret SHADOW as connected v2 gameplay. | Only after the remaining offline gates and separate activation/deployment authorization. |

`NATIVE_OWNER_CAPTURE` means the provider used native owner inputs; it is not a claim
of connected acceptance or a replacement for exact installed native action validation.
`CERTIFIED_STATIC_FIXTURE` is still rejected at the production selection seam;
`UNCERTIFIED_PRODUCTION` is rejected by compilation/finalization.

## Acquisition authoring and persistence evidence

See [learning schema and workflow](ME12_LEARNING_AUTHORING.md) and `docs/COMMANDS.md`.
`/rpg-learning validate`, `/rpg-learning reload`, `/rpg-learning status` require
`inigmasgames.rpg.learning.author`. The eventual file is
`<save>/mods/InigmasGames_HytaleRPGPhase00Audit/learning-sources.json`.
No actual-save file was created by engineering. Default enabled count: zero.

Synthetic Skill and first-copy Passive tests cover atomic validation, readiness,
aliases, duplicate identities, preservation across disable/reload/restart, replay,
every existing player reward write boundary, missing/corrupt archives, and a missing
archive after intent creation. Existing acquisition tests retain Wisdom, rarity,
separate +3 percentage points, ordinary 95% cap, pity and known/owned protections.
The native command fixture checks exact names and author permissions.

The owner's deferred authoring is recorded as **PROPOSED_RELEASE_SCOPE_AMENDMENT**.
ME12-R08 remains BLOCKED until real owner-selected sources exist and Skill/Passive
acquisition is connected-tested and accepted. No skill redesign or TBD implementation.

## Diagnostic coverage of all 27 operators

| IDs | Current evidence in the combined report | Still required for full execution proof |
| --- | --- | --- |
| 001 Extra Fast | Selected identity plus existing raw timing/movement facts | Native movement and recovery changes |
| 002 Extra Strong | Grouped accepted native hit | Isolated physical contribution and completed Health receipt |
| 003 Magic Resistant; 004 Stone Skin | Selected identity, raw defense/mitigation | Actual elemental/physical mitigation and caps |
| 005 Fire; 006 Cold; 007 Lightning; 008 Poison; 009 Wind; 010 Earth; 011 Void | Grouped accepted vector, raw status facts | Correct own offensive and defensive contributions; Poison native chain |
| 012 Spectral Hit | Frozen action/vector and grouped receipt | Selected channel, no collision reroll and actual component receipt |
| 013 Mana Burn; 016 Vampiric | Dedicated requested/applied resource readback | Opportunity locks, aggregate caps, no recursion and lifecycle |
| 014 Cursed; 025 Armor Breaker | Dedicated native effect presence readback | Chance/admission, correct reduction and expiry |
| 015 Knockback | Original action/grouped receipt | Original impulse plus exactly one modifier |
| 017 Unwavering; 018 Unstoppable | Raw protection facts | Actual rejected mutations with legal unrelated components unaffected |
| 019 Frenzied; 020 Avenger | Raw pack/action facts | Phase/stacks, bounded movement/direct/recovery changes |
| 021 Empowered Minions; 022 Horde | Frozen roster and native publication facts | Minion snapshot, two additions, accounting and rollback |
| 023 Aura Enchanted; 024 Packbound | Raw membership/protection facts | Radius, selector, expiry, guards and blocked original mutations |
| 026 Reflective; 027 Bulwark | Raw damage/shield/recovery facts | Later reflection receipt/no loop; absorption/no reload regeneration |

Four partial dedicated readbacks do not certify four complete operators. The other
23 operators lack complete dedicated semantic readback predicates. All full operator
acceptance rows remain BLOCKED. The report keeps PASS/FAIL/BLOCKED/NOT_OBSERVED/
NOT_APPLICABLE distinct; a selected name or server HUD publication proves neither
combat execution nor client rendering.

## Verification and four-axis ledger

The exact source/build/test/publication result is in the post-commit
`build/reports/me12/stage3-continuation-manifest.json`; Guardian receipt:
`build/guardian/verification.json`. Earlier 4,375-test evidence is retained separately.
Required cumulative gate: `check jar testReleaseNativePatch guardianSpotBugs
-PguardianSpotBugs=true --continue`, plus the 16 Python tests and independent RNG oracle.
No passing totals are predeclared by this document.

| Scope | Implemented | Automated | Native connected | Owner accepted |
| --- | --- | --- | --- | --- |
| JSON authoring and archived acquisition | Yes, empty public mapping | Focused tests; final exact-HEAD receipt required | No | No |
| Immutable native job/source revalidation | Yes, bounded capture change | Native job and boundary tests; full provider fixture missing | No | No |
| Firesteel production attachment | Partial integration | Source/defender and contract checks; full attachment fixture missing | No | No |
| Turret/Seeker and mixed extensions | No complete route | Existing rejection/asset evidence only | No | No |
| All-27 diagnostic execution acceptance | Partial; explicit requirements and four readbacks | Report classification tests | No | No |
| Full AD1 / Stage 3 completion | **No** | **Incomplete scope even if regressions pass** | No | No |

Next engineering step is the shared modern projectile receipt lifecycle and complete
native-owner integration fixtures, then mixed-flock certification and guarded activation.
Passing the regression suite or Guardian publication must not be called Stage 3 completion.
