# R261-A Cyclone Armor durability contract

R261-A installs a durable `spirit_shield` authority behind an explicit `false`
gate in `Phase00Plugin`. R260's finite, ten-second shield remains the only live
Cyclone Armor implementation in the owner's RPG save. This document records
the protocol to be used when R261-B connects the new cast and presentation path.

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

The native damage bridge runs after native mitigation and existing finite
shields, before Managuard and native Health application. For an active,
reconciled generation it computes `min(post-finite damage, remaining HP)`,
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
owner must reserve the actual native Mana cost and obtain a durable cooldown
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
crash. Every new connection therefore quarantines a surviving ACTIVE barrier
until `RpgResourceService` re-debits its recorded amount from unreserved,
spendable native Mana. A full observed debit lifts quarantine; insufficient
payment durably depletes the barrier. This deliberately permits a second
Mana charge when the original native payment did persist. R261-B must present
and evaluate this conservative recovery policy before enabling the gate.
Death durably depletes the barrier through a native death hook, with support
tick as a fallback. An old connection's lease cannot detach a newer session.

## Integration boundary

R261-B must attach the coordinator to `SkillExecutionService`'s owned cast
instance, replace only Cyclone's finite effect with the durable pool, and
carry any required Reflective Ward and mastery snapshot into the durable
absorption path. Existing temporary shields, Managuard, encounter shields,
and other skills remain on their current paths. R261-A does not change the
Cyclone formula, cost, duration, HUD, or Mana Before Life behavior.

The measured forced-write sample is recorded in the focused JUnit XML.
It is a local storage measurement, not a connected-game latency guarantee.
