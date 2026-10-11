# Cyclone Armor durable barrier contract

R261-B connects the R261-A/A.1 durable `spirit_shield` authority to live casts
and post-mitigation damage behind an opt-in gate. The gate defaults to false;
the owner's active RPG save still uses the deployed R260 JAR and ten-second
finite Cyclone. The R261-B JAR is a protected-test candidate, not accepted
gameplay. Set JVM property `-Dhywind.cyclone.durable=true` on an isolated
test launch to enable it; remove the property and restart to restore R260
behavior. The gate is read once at plugin startup, not toggled during a cast.

## Storage and damage order

Each player has two alternating journal segments under the plugin data
directory's `cyclone-barriers/` folder. Schema 2 records the player, stable
skill ID, generation and activation UUIDs, revision, capacity, remaining HP,
phase, native Mana and cooldown receipt IDs, and the native Mana debit amount.
Each line carries a SHA-256 checksum. A checkpoint contains the complete
state plus current-generation hit receipts and lifetime activation IDs.
Replay chooses the valid segment with the highest revision. A torn trailing
line is truncated; a complete malformed line, conflicting equal revisions,
or an invalid transition fails closed. A missing sidecar is an empty R260
character, never an invented barrier.

The native damage bridge runs after native mitigation and before existing
finite shields, Managuard, and native Health application. For an active,
reconciled generation it computes `min(post-mitigation damage, remaining HP)`,
appends the debit receipt, calls `FileChannel.force(true)`, then returns the
confirmed absorption and overflow. Only then may the native `Damage` amount
be reduced. A duplicate receipt returns zero new absorption. Per-player
monitors serialize concurrent hits; unrelated players do not share a lock.
The final double-to-native-float conversion rounds any residual damage up,
so numeric narrowing cannot grant an extra unrecorded fraction of protection.
Storage failure poisons that session and leaves the unconfirmed portion of
the native hit in place. A crash after a forced debit but before Health
application may forfeit some barrier HP; it cannot restore consumed HP.

Segments rotate at 16 MB, forcing a self-contained checkpoint into the
inactive segment before subsequent debits use it. The old segment remains
available during an interrupted checkpoint. A hard 100,000-receipt ceiling
durably depletes the current generation rather than allowing unreceipted
absorption or trapping the player in an active barrier. A 100,000-activation
history ceiling rejects further casts explicitly; it requires a future
format migration before that extreme limit is reached. These are safety
bounds, not normal gameplay limits.

## Activation and recovery

The Cyclone-specific coordinator admits one pending generation after
eligibility, active-barrier, spendable-Mana and cooldown checks. R261-B's cast
owner reserves the actual native Mana cost and obtains a durable cooldown
spend from the existing services. It commits native Mana, verifies native
readback, forces the Mana receipt and amount, accepts the persisted cooldown
spend, forces its receipt, and finally forces the ACTIVE barrier. Only after
that final force may it publish presentation and a successful cast trace.
The typed coordinator methods call `RpgResourceService` and
`RpgCooldownService`; other skills keep their current transaction path.

For an ordinary pre-publication failure, compensation refunds cooldown and
then native Mana using the original authority tokens. The callback must be
idempotent. A failed compensation records RECOVERY_PENDING and can be retried;
on restart it is conservatively settled as DEPLETED. If an ACTIVE write was
attempted but its force result is uncertain, no refund is issued until replay
has established whether protection was published. All incomplete PENDING
states replay to DEPLETED. This can forfeit a paid cost or cooldown after a
crash, but never grants protection on a guessed payment. The journal does not
pretend native Mana and plugin storage form one atomic transaction.

Native Mana persistence can itself roll back after a disconnect or process
crash. Hytale 0.7.0-pre.5.1 stores `EntityStatMap` values, but its native
`setStatValue` API has no durable transaction identity shared with this
journal. The recorded cost token proves only the original in-memory debit
and readback; it cannot distinguish a saved debit from a rolled-back one.
Every new connection therefore quarantines a surviving ACTIVE barrier and
forces a DEPLETED record before marking that connection ready. Recovery does
not call the native resource authority, charge Mana, or refund a possibly
saved payment. The existing cooldown spend is left with its owner and is
not automatically refunded. A failed invalidation force leaves the session
uncertain and denies protection and ready completion. A later replay may
retry; it never infers a payment from the current Mana balance. This policy
may forfeit paid protection after reconnect; it remains a known R261-B
gameplay limitation during protected testing.
Death durably depletes the barrier through a native death hook, with support
tick as a fallback. An old connection's lease cannot detach a newer session.

## Integration boundary

The coordinator is called by `SkillExecutionService`'s owned cast instance.
At the final commit it requotes spendable native Mana, requires at least 100,
then spends the whole unreserved amount. Cost reduction and Health conversion
cannot reduce this Mana payment. A forced Mana receipt and persisted cooldown
receipt precede the forced ACTIVE journal state. Successful durable casts do
not call the finite support shield path. The old finite implementation is
available only while the gate is disabled.

Base durable capacity is 100 plus one HP per effective WIS and INT above the
canonical starting effective value. The level curve is
`1 + 0.60 * (level - 1) / 19` for levels 1 through 20; the result is rounded
down to whole HP. Existing barrier-strength modifiers, including Reflective
Ward's 20% capacity reduction, are then applied and rounded down once more.
Capacity is frozen in the journal for that generation. No duration, passive
regeneration, or native Health/Mana coupling exists. The native infinite
`RPG_Cyclone_Armor_Durable` effect displays the existing Cyclone particle
and skill icon without a countdown and is removed after depletion, death,
disconnect, or failed reconnect recovery.

The confirmed durable absorption uses the frozen cast's Reflective Ward
selection and returns 20% of actual absorbed damage through the existing
secondary native damage path. Duplicate receipts return zero absorption and
cannot reflect twice. Reflection uses the established 16 secondary-hit
budget and REFLECTED metadata to prevent recursion. Existing finite effects,
monster shields, and Managuard remain in their respective downstream paths.
Shared Aegis's derived ally shield is not created by the durable Cyclone path;
casts with that linked passive reject before payment. It remains available
on R260's finite path and needs a separate durable multi-recipient design
before use with this opt-in candidate.

The forced debit runs synchronously on the native damage-processing call
stack. `debit` locks only its player's holder, then appends and calls
`FileChannel.force(true)` before returning any absorbed damage. There is no
global Java journal lock, although filesystem and device contention can
affect unrelated players. The focused test records p50/p95/p99 and worst
wall-clock debit latency for repeated hits and concurrent same-player and
multi-player hits. These local measurements are not connected-game latency
guarantees. Moving the force to an asynchronous queue would violate the
current no-acknowledgment-before-durability rule; R261-B should evaluate
the measured pause budget before production acceptance. R261-B's offline
`CycloneDamageStageTest` profiles the exact journal debit and native float
bridge invoked by the handler. It excludes ECS traversal and connected-game
storage contention; the disabled-by-default gate and owner-only protected
test are essential. See [R261-B QA notes](QA-R261B-CYCLONE.md).

## R261-A invariant coverage

| Invariant or failure boundary | Automated coverage |
| --- | --- |
| Native Mana and cooldown receipts precede ACTIVE; admission is singular | `nativeManaAndCooldownAuthorityReceiptsGateActivation`, `cooldownProofAndManaProofAreRequiredBeforeProtection`, `simultaneousCastRequestsProduceOnePendingGeneration` |
| Pending stages, failed compensation, and cooldown persistence failures never publish protection | `interruptedActivationAtEveryProofStageCannotGrantFreeBarrier`, `uncertainCompensationSettlesConservativelyAndRetryCanProceed`, `compensationCanSettleAfterOneFailureWithoutDoubleRefund`, `cooldownPersistenceFailureLeavesNoBarrierAndCanCompensateMana` |
| Reconnect cannot charge Mana again or publish uncertain protection; cooldown debt and activation history survive; stale leases cannot affect the next session | `nativeManaRollbackAndPersistedManaBothForfeitWithoutAnotherCharge`, `restartWithUncertainNativeManaDepletesInsteadOfChargingAgain`, `recoveredCooldownDebtSurvivesUncertainBarrierInvalidation`, `delayedOldSessionCannotInvalidateNewBarrierOrSession`, `preloadedReconnectDoesNotRefillAndOldLeaseCannotClearNewSession` |
| Recovery storage failure is fail closed | `uncertainRecoveryWriteFailureNeverPublishesProtection`, `nativeDamageCanOnlyBeReducedAfterConfirmedForcedWrite` |
| Durable debit, duplicate receipt, exact overflow, numeric narrowing, and death depletion | `forcedDebitSurvivesRestartWithoutRefill`, `duplicateAndStaleReceiptsCannotDebitTwice`, `concurrentHitsAreSerializedAndOverflowIsExact`, `tinyUnrepresentableDamageNeverGetsFreeAbsorption`, `nativeFloatNarrowingCannotGrantUnrecordedAbsorption`, `oldSaveWithoutSidecarIsEmptyAndDeathPersists` |
| Torn write, malformed transition, equal-revision conflict, checkpoint rotation, and activation replay fail closed | `tornTailIsIgnoredButCompletedCorruptionFailsClosed`, `duplicateOrOutOfOrderDurableRevisionFailsClosed`, `conflictingEqualRevisionSegmentsFailClosed`, `twoSlotCheckpointPreservesDebitsAndReceiptHistory`, `tornInactiveCheckpointCannotRestoreAbsorbedDamage`, `oldActivationIdCannotBeReplayedAfterAnotherGeneration` |
| Receipt and activation ceilings, session and preload budgets fail closed without dropping damage or evicting state | `receiptCeilingForfeitsPoolWithoutUnreceiptedAbsorptionAndActivationCeilingRejects`, `sessionAndPreloadBudgetsRejectExcessWithoutEviction` (small package-private record bound exercises the production ceiling path) |
| Forced-write latency and same-player versus unrelated-player contention remain visible | `multiPlayerForcedDebitLatencyProfile`, `repeatedHitBenchmarkRecordsBoundedPerPlayerSyncCost`, `simultaneousHitBenchmarkRecordsLockContention` |

Native player-save flush timing and connected-client damage latency remain
unverified. The gate remains disabled by default in R261-B.

R261-C adds player progression and Mana Before Life downstream of confirmed
Cyclone absorption. Its native resource conversion cannot establish a durable
payment identity across a crash; see [R261-C development contract](R261C_ENGINEERING.md).
The R261-B reconnect forfeiture and Shared Aegis boundaries remain open.
