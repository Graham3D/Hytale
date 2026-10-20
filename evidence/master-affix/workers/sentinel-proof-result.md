# Bounded Sentinel simulator proof handoff

Scope: new tests only in six `MasterAffixSentinel*Test.java` files. Read `docs/MASTER_AFFIX_SCOPE_CORRECTION.md`. No runtime, gate, proof-map, base-skill, or existing-test edits.

## Focused result

Direct `javac` of all six files succeeded against the current `build/classes/java/main`, `build/classes/java/test`, resources, Hytale server jar, and local JUnit jars. JUnit Platform launcher: **117 found, 115 passed, 2 failed**. The two failures are production recovery gaps below. No Gradle, game, server, auth, or deploy run.

## Passing methods and exact IDs

| Test method / parameterized display name | IDs | Bound consumer observed |
| --- | --- | --- |
| `ownerOnlyBoundSourceDoesNotChangeSentinelRuntime[{0}]` | WA-003, WA-004, WA-009, WA-012, WA-085, WA-086, WA-087, WA-088, WA-089, WA-090, WA-092, WA-093, WA-095, WA-097, WA-098, WA-099, WA-100, WA-101, WA-102, WA-103, WA-104, WA-105, WA-106, WA-107, WA-108, WA-109, WA-110, WA-112, WA-113, WA-114, WA-115, WA-116, WA-117, WA-118, WA-119, WA-120, WA-121, WA-122, WA-123, WA-124, WA-125, WA-126, WA-127, WA-128, WA-129, WA-130, WA-131, WA-132, WA-133, WA-144, WA-151, WA-152, WA-153, WA-154, WA-155, WA-156 | All 56 bound sources remain in the lease snapshot; actual Sentinel physical/health/protection/cadence, outgoing attack envelope, incoming resistance, reach, and received healing equal a plain bound source. No minion recursion or owner/global copy. |
| `boundLocalPhysicalChangesSentinelProjectionAndAttack[{0}]` | WA-001, WA-002, WA-157, WA-158 | Bound source range changes Sentinel projected physical endpoint and its accepted attack amount. |
| `boundGlobalAttackModifierChangesActualHit[{0}]` | WA-005, WA-006, WA-007 | Existing attack envelope increases the matching physical or elemental channel over plain bound source. |
| `boundChassisCapacityOrCadenceChangesOnlyIntendedStat[{0}]` | WA-008, WA-091 | Sentinel attack interval or Health capacity changes; unrelated stat stays controlled. |
| `boundCriticalChangesExistingRollOrMultiplier[{0}]` | WA-010, WA-011 | Existing critical roller/attack envelope receives chance or multiplier. |
| `boundElementalAffixChangesActualAttackChannel[{0}]` | WA-017, WA-018, WA-019, WA-020, WA-021, WA-022, WA-023, WA-024, WA-025, WA-026, WA-027, WA-028, WA-029, WA-030, WA-031, WA-032, WA-033, WA-034, WA-035, WA-036, WA-037, WA-038, WA-039, WA-040 | Bound native attack hit changes matching flat/increased/penetration/conversion channel against control and retains source identity/revision. |
| `boundStatusQualityChangesExistingPeriodicOwner[{0}]` | WA-013, WA-065, WA-066, WA-067, WA-068 | Source-scoped bound item changes real periodic owner tick or remaining duration, with wrong-status control for WA-065. |
| `boundStatusPenetrationChangesActualAdmission_WA064` | WA-064 | Existing status admission admits the bound physical hit at a draw where control misses. |
| `boundDefenseChangesNativeSentinelProtection[{0}]` | WA-069, WA-070, WA-071, GA-159, GA-160 | Shield/armor bound source increases native Sentinel total rating and physical incoming protection. |
| `boundResistanceChangesNativeSentinelIncomingDamage[{0}]` | WA-072, WA-073, WA-074, WA-075, WA-076, WA-077, WA-078 | Native incoming elemental resistance increases only for the matching cause(s), with Physical control. |
| `boundRendingAdmitsBleedAndSuppressesVictimRegeneration_WA134` | WA-134 | Bound accepted physical hit enters canonical Bleed admission and victim regeneration suppression; plain and NoProc controls fail. |
| `boundNpcAcceptedHitExecutesCanonicalRankOneChild[{0}]` | WA-145, WA-146 | Accepted bound NPC hit alone enqueues; next world-step queue calls existing canonical rank-one `sentinelItemContext` for Fire/Frost Bolt with exact item, actor, zero Mana and no linked passives. Owner actor and NoProc child cannot enqueue. |
| `boundItemAuraActivatesAndChangesRecipient[{0}]` | WA-148, WA-149, WA-150 | `NativeSentinelItemAuras.boundContext` enters actual `SupportRuntime.activateItemAura`; recipient gains Mana regeneration, Thorns, or cooldown recovery, loses it after membership removal, regains it on return, and loses it on teardown. Free NPC sustain makes no resource call. |

Existing `SentinelSourceConsumerTest` owns WA-014–016, WA-041–063, WA-079–082, WA-084, WA-111; `SentinelSignatureConsumerTest` owns WA-135–140, WA-142–143. Main's `SentinelConditionalActionTest` owns current-chassis negatives WA-083, WA-141, WA-147.

## Failing methods and remaining gaps

| Test method / ID | Actual focused result | Gap |
| --- | --- | --- |
| `boundNpcAcceptedHitPaysOnlyNpcHealth[WA-094]` | Expected 3 Health, got 0. | `GearRecoveryRuntime()` uses `GearAffixRuntime.ENABLED::contains`; WA-094 is absent from that set. Bound hit/source and NPC-only receipt are in the test. |
| `boundNpcAcceptedHitPaysOnlyNpcHealth[WA-096]` | Expected 4 Health (20% of 20 actual victim Health lost), got 0. | Same production capability gate excludes WA-096. |

These two failures are intentional regression evidence for the recovery owner; the test does not bypass the production gate. WA-145/146 prove canonical child context and queued execution callback in the simulator; native projectile spawn requires a live world and remains outside offline coverage. No successful block, offhand, or two-handed action was fabricated.
