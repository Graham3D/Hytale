# HRO connected-QA evidence correction

The owner-provided run `520d916f-6f53-4edb-86fa-71cfe7ebf19d` finalized
normally with 16,323 admitted observations and three drops. Two drops were
reported as queue contention; the third had no source in the original bundle.
The prior evaluator treated any loss as a veto over all 17 contract summaries
and all findings. This hid observed complete receipts: 22 combat reconciliation
results and eight equipped-gear results were counted as PASS before the global
loss veto. Skill projection and UI also had complete individual observations.
Those are scoped observations, not blanket gameplay acceptance. The original
bundle is preserved unchanged; its unattributed drop cannot be assigned to a
specific operation after the fact.

The fix records bounded loss provenance by contract and operation in
`integrity.json/lossScopes`. An affected operation and its contract remain
INCONCLUSIVE; unrelated complete operations and contract families retain their
observed outcomes. Contract-wide overflow remains conservative for that
contract, and missing attribution or an interrupted capture remains globally
conservative. Offline reanalysis and approved per-scenario comparisons apply
the same scope rules. Checksums still establish capture integrity independently
of evidence completeness. No gameplay authority or save data changes.

The run's producer p99 was reported as a 65.536 microsecond **logarithmic
bucket upper bound**. That alone cannot establish whether the actual p99
crossed the 50 microsecond target. The new performance report counts samples
at or above exactly 50 microseconds, states its threshold-count method, and
breaks slow samples down by contract. It retains the old histogram for
comparison and reuses the current world's alias in the hot path instead of
recomputing it for every event. The owner-tick observation p99 upper bound
was 65.536 microseconds, within its 500 microsecond budget.

The same run had no natural spawn admission or elite promotion observations.
That is unexercised coverage, not evidence of a failed detector or broken
rarity selection. Its first 1,024 paired native whole-world-tick samples
were approximately 77.6 ms p95 and 124.1 ms p99. There is no matched
trace-off run, so HRO causation and client FPS remain UNVERIFIED.

The owner workflow is unchanged: `/rpg-trace patch start`, ordinary gameplay,
`/rpg-trace patch stop`, then `/rpg-trace patch status` for the folder. The
next connected capture can verify that loss scopes and exact threshold counts
are useful in the live game. Natural elite coverage requires a separate
ordinary opportunity during recording; zero sightings alone are not a defect.
