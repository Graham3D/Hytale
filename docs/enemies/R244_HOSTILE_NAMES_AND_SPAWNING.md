# R244: hostile names and natural spawn admission

## Source and evidence

Baseline: R243 `bdc8f513a7dc694e77d14673bc4a4f1f57d8466e`. Final runtime commit, verification counts and hashes are recorded in `R244_DEPLOYMENT.json`. The JAR embeds its runtime commit in `rpg-build.properties`; spawn trace summaries include it. Connected acceptance is **pending owner QA**. No server/game was launched.

Evidence: owner-supplied R244 brief/R243 review and `monster-spawn-1791548489094.jsonl`, SHA-256 `573e237baa1459b21a42b21f5c3cd7f65f7051d1da1b0f2f0cddab091d39367d`. Its 57 authored-profile declines precede rarity; 37 groups reached the roll and four published. This does not establish incorrect rarity probabilities or a global hostile decline.

## Implemented owners

### Primary hostile names

`NativeHostileNames` is the single Hywind primary hostile Nameplate writer. Encounter and combat presentation submit reconciliation requests instead of independently writing/restoring text. NPC/UUID/Transform tracking, WorldSupport changes, publication/detach, initial loaded-actor enumeration and bounded reconciliation feed that owner. Native RefSystem entity-add callbacks do not rerun merely because a readiness component is added; the explicit change hook and retry queue cover that distinction.

At most 32,768 actors are tracked; 128 entries per world tick are examined with a 500 ms per-entry interval. One deferred batch contains UUIDs and lifetime tokens, reacquires current world/reference/projection and writes outside Store processing. No repeating task per NPC. Native Nameplate setters retain tracker dirty-state ownership; diagnostics never consume the send flag.

Stable native player attitude determines hostile naming independently of reward catalog, profile, producer and population category. Summon/conversion projections retain their policies; no blanket nonserialized exclusion is used. Native localization/PersistentDisplayName precedes readable-role fallback. Explicit personal/external names are preserved. Only accepted snapshots supply level. Living detach recomposes an ordinary name instead of restoring blank. Naming neither enrolls rewards nor changes Health.

The native primary name remains nonblank with optional anchors. The duplicate custom promoted-name segment is suppressed; native Healthbar, affix anchor, tint and scale remain. Primary native names are white in this release, with no invented markup/projection. Client rendering remains UNKNOWN until connected QA; late viewers use normal native tracking.

### Production profile closure

`Build-CampaignBiomeCoverage.py` joins exact installed Default `Tile.*` and `Custom.*` biome keys to each zone's existing unambiguous campaign band. The versioned manifest contains **237 supported keys**, 166 more than the old 71-key list, with source asset hashes. Build verification rejects stale coverage; runtime performs no asset enumeration.

`AuthoredEncounterCatalog` compiles those profiles. `EncounterProfileResolver.classifyNatural` is shared by ordinary and staged natural actors, with typed world/generator/role/biome/region/profile/baseline/provenance results and exact world/era/runtime-role/canonical-role/environment/biome context. Native environment and campaign biome remain separate. Immutable source snapshots continue through the existing planner. A missing translation cannot reject a combat profile.

Coverage: **270 ordinary concrete registry roles x 237 exact biome keys x 3 eras = 191,970 real production resolver contexts**, including all 15 observed rejected roles. The fixture does not use QA fallback. Campaign Golem authored-final ownership, rarity weights and purpose-separated RNG remain unchanged.

Intentional exclusion: 32 installed Ocean Tile/Custom assets have no approved RPG campaign band. The manifest records `REGION_UNMAPPED_NO_AUTHORED_BAND`; no region/level/reward profile is invented. Those actors retain native spawning and plain hostile names. Unknown/custom generators remain explicitly unsupported for RPG profiles.

### Coherent native population projection

`NativeWorldSpawnDensity` invokes the existing population owner after density/chunk reconstruction. A minimal pinned callback also executes inside `WorldSpawningSystem.tick`, after native target reconstruction and before selection. An independently throttled earlier tick could not provide this ordering.

`NativePopulationBalance` publishes actual native per-role expected values without a correctness throttle; diagnostic sampling remains bounded. `PopulationWeightPlan` explicitly restores native weights when disabled. Native actuals, failure/unspawnable flags, total accounting, exempt targets and flock accounting remain intact. Inspected native setDensity/updateExpectedNPCs do not reset suppression flags. Expected values also influence native despawn thresholds; this remains an explicit effect of the stable configured policy, never a temporary zero-target cooldown.

The patch adds one neutral callback to the existing framework, not a scheduler. Its verifier strips the insertion and compares all original instructions, pins original/output hashes and rejects unrelated archive changes. See `tools/native-patch/THREE_LEAF_PATCH.md`.

### Aquatic admission

`NativeAquaticHabitat` replaces the whole-column any-fluid guard at the existing `NativeEnemySpawnGroups` job boundary. Applicability derives from native model/spawn parameters, water breathing and Dive-only motion; no fish-name whitelist. Amphibious/multi-mode actors remain native.

- Inspect only loaded runs for this job's environment and required fluid tag. Convert the native cursor's inclusive maximum explicitly. Unrelated fluid/height and missing unrelated sections do not determine this habitat.
- Use native SpawningContext column/span selection, block/fluid admission, light and canSpawn predicates for candidate clearance. No surface-ice heuristic or placement bypass.
- SUITABLE_CANDIDATE continues normal placement; complete KNOWN_UNSUITABLE avoids native position work; missing data/work exhaustion is permissive UNKNOWN. Assessment bounds: 16,384 work units, 16 native candidate probes.
- Rejection terminates the existing job; native endProbing/removal/accounting still runs once. No NPC deletion or population-count manipulation.
- A 1,024-entry table retains world/chunk/environment/role-requirement retry state. Three completed native failures can produce a two-second backoff only with a completed assessment and valid terrain/fluid packet-version witnesses. Missing/changed witnesses disable that backoff. Expiry permits another probe; habitat changes/success/world cleanup reset it.
- Dry signatures are revalidated on subsequent jobs. `cacheHit` means a retained matching signature, not unverified terrain reuse. Packet-cache witnesses are read only: no serialization or dirty-set consumption. No synchronous disk/network IO in callbacks.

The historical trace did not retain the runtime index-12-to-asset mapping. The five cave environments in the installed fish table are known; choosing one as index 12 would be guessing. No index-specific rule was written. New rows emit the actual `job.getEnvironment().getId()` with its index.

## Diagnostics

The same 10,000 ordinary/2,000 priority row caps and twelve ten-second buckets remain. Summary manifest includes build/source, config revision/content hash, native asset hash and binding/biome revisions. Published packs and player-local samples receive priority retention. Buckets include naming/habitat outcomes. Target inspection shows actual Nameplate text, owner/lifetime, accepted profile or UNPROFILED, staging and rendering UNKNOWN without consuming tracker flags.

Local samples use the native spatial index: every ten seconds while tracing, at most 16 players, 64 m radius, at most 2,048 inspected results. Position/biome/sampled speed/game mode/view radius are reported; simulation distance is explicitly UNEXPOSED. No whole-world entity scan per tick.

## Verification scope

| Fixture | Automated proof | Connected acceptance remaining |
| --- | --- | --- |
| R244ProductionProfileTest | 191,970 actual production contexts; explicit unsupported cases | Actual route contexts |
| R244NaturalBirthIntegrationTest | Real classifier, frost mixed flock/single bear identities, deterministic planning/extension join, durable root/publication/reload, lease suspend/rebind | Native placement/publication in the save |
| R244NamesTest | Actual SDK Store processing rejects structural write; deferred native component and dirty-state checks; late readiness without re-add; personal-name/detach/role precedence | Actual Role/WorldSupport initialization and viewer rendering |
| R244PopulationProjectionTest | Real native expected setters/getters, enabled-disabled-enabled, reconstruction, unchanged actuals/unspawnable state; hook install/no-provider/teardown | Loaded-world selector/despawn behavior |
| R244HabitatTest | Real FluidSections: dry/relevant/unrelated/incomplete/fill/drain; candidate result preserved; finite backoff/reset | Native fish success under actual body/ice/light constraints and throughput |
| Existing affected suites | R240 active/suspended/grandfathered leases, staging/rollback, rarity, persistence and receipts | Same-save traversal/reload |

The habitat fixture supplies the candidate predicate result; it does not claim a fish spawned or that a full native world ran. The birth fixture represents native actor allocation by captured identities. These are offline fixtures, not connected ecological/rendering acceptance.

Initial verification repaired native test JVM logger setup, an incorrect fixture expectation that readiness reruns RefSystem, the new tick's missing existing metrics wrapper, and a test reading the old installed SDK hash rather than the built patch. Gameplay assertions were not weakened. Final counts/hashes are in the deployment receipt.

## Preservation, deployment and QA

No substantive affix implementation, rarity planner, reward owner, R240 lease implementation, production rollback transaction or native combat leaf changed. No persisted encounters or wounded actors are regenerated/restatted for naming. Standalone module ownership remains package-validated.

Only active root `world-config.json` density changes from **8.0 to 4.0**, using an outside-Saves backup and atomic replacement. Full JSON equality with only that substitution and byte preservation elsewhere are verified. Both `/rpg spawns` and `/rpg worldconfig` already consume HywindWorldConfiguration. Existing 65/35 policy, rarity rates, limits and rewards remain unchanged. The matching patched native server and Hywind JAR are backed up/deployed with hashes while the game is closed.

1. Restart the active RPG world; confirm R244. Run `/rpg worldconfig status` and `/rpg spawns status`: both should show 4x and retain existing policies.
2. Run `/rpg spawntrace start`. Spend two minutes around mixed frost skeletons/bears, traverse natural areas, then return. Forced QA spawns alone do not test natural admission.
3. Names should remain readable after targeting/range exit/reentry. Target a failure and use `/rpg enemies inspect`; operator UUID syntax is `/rpg enemies inspect --entityUuid=<uuid>` for a loaded current-world actor.
4. Observe real pools/ice/dry caves and retain the full trace plus same-session log. Compare buckets, exact profile results, habitat work, publication and leases. Do not require a fixed Elite count or exact local population ratio in a short random sample.
5. Rejoin/reload existing packs: identities/affixes/wounds remain, no duplicate names/rewards, and suspended encounters do not retain active capacity.


## Final offline results

- `:test`: **3,445 tests, 0 failures/errors/skips** (350 classes).
- Focused `:nativeControlTest`: **6 tests, 0 failures/errors/skips** (Nameplate, real native population stats/hook lifecycle, native staging).
- `:verifyHyArpgJar`, spatial-grant audit, 88 CustomUI documents and native proc-asset audit: PASS. Native patch bytecode/archive verifier: PASS.
- Package comparison against deployed R243: every existing non-class gameplay/role/UI asset is byte-identical. Differences are only mod/Canvas build metadata and the new campaign-biome manifest.
- Diff review: no changes under substantive `hytalerpg/enemies` or `hytalerpg/progress` runtime packages. R240 leases, birth planner, affixes and rewards remain unchanged.
- Connected rendering, native aquatic spawn success, route throughput and same-save behavior remain pending owner QA, not automated PASS claims.
