# R032-AA — Healing Beam presentation / Blizzard contract reconciliation

Authority: owner-supplied `Hytale RPG - Skill Bug List.docx.md`, 2026-09-12,
and the owner's subsequent audio clarification. Baseline RPG `eea0fa1`.

This scoped amendment supersedes Master v1.3 SK-069's 8-second Blizzard
duration with **3.0 seconds**. Catalog and executable profile must agree.
Base radius stays 6 m, placement 24 m, local impact radius 2 m, coefficient
0.38 and per-target root interval 0.75 s. Each damaging impact applies one
Chill under the existing rules. Falling shards start 7.5 m above their sampled
surface, descend in 0.45 s, and spawn every 0.25 s from 0 through 2.5 s.
Effective compiled duration/radius/cadence remain authoritative for modifiers.
First solid swept contact terminates a shard; expiry never manufactures a hit.

The owner's clarification supersedes BL-004's loop requirement: use only
`SFX_Ice_Ball_Death` for each shard; there is **no storm audio loop**.
Healing Beam gameplay and all existing persistence contracts remain unchanged.
Native feedback capability gaps must be reported, not approximated as proven.

This cohort is package-only: **NOT DEPLOYED**. Connected rendering, audio,
collision appearance and cleanup remain owner QA requirements.
