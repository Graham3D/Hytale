# Stage 13 M connected casting blocker — correction N

Date: 2026-09-09. Branch: `RPG`. Baseline: `e6ef501772464ae867d18ee92a8146d87a780689`.
Build identity stays **R032 / 0.0.25**. Identify this correction by **cohort N and SHA256**, not the unchanged filename.

Status: **IMPLEMENTED / RETAINED_VALIDATION_PASS / DEPLOYED_FOR_CONNECTED_RETEST**.
**N connected casting and native performance qualification: UNVERIFIED. Stage 13 is not marked PASS.**
No native input/projection redesign, HUD change, executor change, or persistence/escrow architecture change was made.

## 1. Connected evidence and exact boundary

Read the current skill/UI traces and the 2026-09-09 17:46:11 local server log. The latter starts at 21:46:11 UTC.
The latest session has 11 `NATIVE_ABILITY_INPUT_OBSERVED` and 11 `SKILL_ACTIVATION_REQUEST` records. Projection succeeds for both slots.
It contains four `SKILL_PREPARATION_FAILED` records: three Flame Longsword attempts and one Mithril Staff attempt.
Neither reached paid dispatch. Other rejection records are not counted as additional instances of those four failures.

- `Weapon_Longsword_Flame`: native Family `Longsword`, Type `Weapon`; Quick Slash's family check is valid, but the old 15-item registry omitted this exact ID. Its descriptor therefore lacked `WeaponPower`, producing `MISSING_AUDITED_WEAPON_POWER`.
- `Weapon_Staff_Mithril`: native Family `Staff`, Type `Weapon`; Fire Bolt's family check is valid, but the old registry omitted this ID and its descriptor lacked `MagicPower`, producing `MISSING_AUTHORED_MAGIC_POWER`.
- Earliest failing boundary is **equipment power resolution**, not ability input, targeting, resource commit, projectile execution, or damage dispatch.

Source hashes, full retained-file counts, latest-session counts, and the ten matching failure records across both September 9 M sessions are in [connected-failure-evidence.json](../../evidence/stage-13/cohort-n/connected-failure-evidence.json). The full retained file also contains older sessions; those are explicitly separated from the latest-session statistics. Raw server authentication logs were not copied into the report packet.

Installed reference, unchanged:

| Source | SHA256 |
| --- | --- |
| Hytale 0.7.0-pre.1 `Assets.zip` | `46F6AA12DECF4F900FCFDF28ECC67568403C37A3AD0A03A4E4CF7653A324AE39` |
| `HytaleServer.jar` | `EC57E9BD6E2CA3CB16CC5883D42B04A0C64D382DEE532C5BC1CFCF68421E1EE3` |

## 2. Authoritative expanded power policy

`tools/AuditVanillaWeaponPower.java` audits all native weapon-directory items and weapon-tagged items outside that directory. It follows authored item parents/tags, selects family-specific basic uncharged damage variables, and resolves referenced interaction/root/projectile assets. No filename/tier guessing, damage-summary min/max, charged/signature averaging, or combo summation is used. Multiple damage values without a unique selected basic source remain unresolved. Ambiguous duplicate native projectile-config IDs are excluded rather than selected by ZIP order.

The installed audit contains **261 candidates**:

- **197 resolved** across all 14 RPG-supported weapon kinds: 136 native-source bases and 61 explicitly authored magic/shield reference bases.
- **24 unresolved** supported-kind candidates, including four templates and one debug bow. These do not silently acquire inferred power.
- **40 outside supported RPG weapon-family authority**, including Club/Arrow/deployable families and untyped minigame weapons. This is not a claim that their native gameplay is broken; RPG's family requirements are not expanded to admit them.

Resolved counts: Sword 16, Longsword 20, Dagger 17, Battleaxe/Axe 24, Mace 11, Spear 18, Bow 15, Crossbow 3, Gun 3, Bomb 8, Shield 17, Staff 34, Wand 5, Spellbook 6.

The active manifest is [native-item-power-vanilla-0.7-pre1.json](../../src/main/resources/rpg/runtime/native-item-power-vanilla-0.7-pre1.json). Every row includes exact item ID, RPG kind, expected native Family, source asset/property, selection policy, and numeric base. Full per-item classification/provenance is in [vanilla-weapon-audit.json](../../evidence/stage-13/cohort-n/vanilla-weapon-audit.json).

### The two reported weapons

**Flame Longsword = WeaponPower 31.** Source:

```text
Server/Item/Items/Weapon/Longsword/Weapon_Longsword_Flame.json
InteractionVars/Longsword_Swing_Left_Damage/Interactions/0/DamageCalculator/BaseDamage/Fire
```

This is its authored basic uncharged left swing, not its 39-power charged stab. The value supplies RPG base magnitude; this correction does not change Quick Slash's damage element/formula to match the source item's native attack.

**Mithril Staff = authored MagicPower 20.** This distinction is important: its `Staff_Primary` has an uncharged spear-melee branch and a charged spell branch. Neither supplies an appropriate uncharged spell base for Fire Bolt. Taking melee damage or charged Skeleton Mage projectile damage would misrepresent the source. N extends the **existing explicit RPG reference base 20** policy already used for Ice Staff, Wooden Wand, Demon Spellbook and Iron Shield to audited variants of those kinds when no selected native uncharged spell/shield base exists. It does not invent a tier curve or claim Mithril's native spell deals 20. The Flame Crystal Staff retains its existing native spell base 10.

All original 15 audited items retain their exact previous kind and numeric base. The original `native-item-power-r032.json` and its tests remain a historical source fixture. `HytaleEquipmentAdapter` now explicitly uses `NativeItemPowerRegistry.loadProduction()`, not that historical subset.

Runtime authorization remains exact installed ID **and** matching native Type/Family. Missing Family is accepted only where the audited native asset genuinely has none, using its authored template/animation profile for the offline classification. It is not a runtime wildcard. Unknown IDs and mismatching/ambiguous tags still fail closed without power. No broad name-based fallback was added.

### Genuinely unresolved supported candidates

No basic base is authored in the relevant template/override chain, or the prototype supplies only a charged/special route with no selected uncharged source:

```text
Debug_Bow_Two_Handed
Template_Weapon_Battleaxe
Template_Weapon_Daggers
Template_Weapon_Mace
Template_Weapon_Sword
Weapon_Battleaxe_Scarab
Weapon_Battleaxe_Tribal
Weapon_Battleaxe_Wood_Fence
Weapon_Blowgun_Tribal
Weapon_Daggers_Void_Crystal
Weapon_Mace_Prisma
Weapon_Shortbow_Bomb
Weapon_Shortbow_Combat
Weapon_Shortbow_Pull
Weapon_Shortbow_Ricochet
Weapon_Shortbow_Vampire
Weapon_Sword_Cutlass
Weapon_Sword_Frost
Weapon_Sword_Nexus
Weapon_Sword_Runic
Weapon_Sword_Silversteel
Weapon_Sword_Steel
Weapon_Sword_Steel_Incandescent
Weapon_Sword_Wood
```

For example, Steel Sword inherits a template with no authored basic damage override. Tribal Battleaxe's override is signature damage, not an ordinary base. Prototype Combat Bow's charging graph is not the production zero-strength basic-damage variable. N does not substitute ItemLevel or another tier's damage for those missing sources. These limitations remain explicit instead of promising every placeholder/debug weapon works.

## 3. Real trace levels

The latest M session contains 1,850 raw native tick records using **1,136,913 of 1,198,300 bytes (94.877%)**. The preexisting `level` configuration did not filter those emissions.

`SkillTraceRouter` now routes records **before JSON serialization**:

| Mode | Native tick records | Successful compile stages | Casting/combat/progression/failures |
| --- | --- | --- | --- |
| NORMAL (default) | Per-world 10-second aggregates | Suppressed | Retained |
| DETAILED / DEBUG | Same aggregates | Retained | Retained |
| PERFORMANCE | Every raw `NATIVE_RPG_TICK_SAMPLE`, unchanged | Suppressed | Retained |

`NATIVE_RPG_TICK_SUMMARY` includes count, first/last native tick and timestamp, RPG wall total/mean/max, counts over 4/8 ms, paired whole-world counts/total/max, per-phase totals/max, and rejected-world metrics. Aggregation holds at most 64 world windows and a fixed phase set; excess/malformed samples remain visible as raw records. Windows flush on their next sample after 10 seconds, on mode switch, or orderly close. Partial windows can be lost on abnormal process termination; normal summaries are **not** a formal timing dataset.

`COMPILE_BEGIN`, final `COMPILE_SUCCESS`, and `COMPILE_FAILURE` remain visible. The producer avoids building successful detailed stage records unless requested. Explicit failed stage records, if emitted, remain visible in every mode. Compiler execution itself is unchanged.

The existing `BoundedTraceWriter` is byte-identical: queue limits, rotation (configured 8 MiB / four retained files), `TRACE_GAP`, accepted/written/dropped/failed metrics and failure notification are preserved. PERFORMANCE does not bypass bounded admission. Any gap/drop/write failure makes a purported complete performance capture incomplete; no silent sampling is substituted.

Session admin commands:

```text
/rpg-trace status
/rpg-trace normal
/rpg-trace detailed
/rpg-trace performance
```

Switches emit `TRACE_LEVEL_CHANGED`, flush prior aggregates, and do not edit save data. Mode defaults back to NORMAL on restart. An explicitly managed server may set `-Drpg.skillTrace.level=PERFORMANCE` at startup. DEBUG is an alias for DETAILED. PERFORMANCE, not NORMAL or DETAILED, is required for the unchanged **4 ms p95 / 8 ms p99 native-tick gate**.

Offline replay of the actual latest retained session through the **packaged production router**:

- PERFORMANCE: 1,940 records, all 1,850 raw ticks retained, 1,208,016 serialized bytes.
- NORMAL: 97 records, seven summaries representing all 1,850 ticks, all 90 event-driven records retained, 71,970 serialized bytes.
- Reduction: approximately **94.0%** under the same Gson serialization. Replay normalizes deserialized numeric representations, so these byte totals are not identical to the original file's bytes.
- This is an offline volume/retention check, **not connected casting proof or native timing qualification**. See [trace-level-replay.json](../../evidence/stage-13/cohort-n/trace-level-replay.json).

## 4. Validation and scope preservation

Focused production-path tests were followed by **one coherent complete retained run**:

```text
gradlew.bat :test :nativeControlTest :canvas-ui:test build --rerun-tasks --console=plain
2,067 root + 31 native-control + 21 CanvasUI = 2,119 tests
0 failures / 0 errors / 0 skips
```

New cases cover exact Flame Longsword/Quick Slash and Mithril Staff/Fire Bolt descriptions, targetless commit/dispatch, one payment/cooldown, projectile expiry with no refund/damage/mastery, wrong equipment/resources, every manifest source leaf, every ID's actual inherited installed tags, preservation of the old 15 bases, tag mismatch and unknown-ID rejection, aggregation arithmetic/bounds, mode changes, compile-stage filtering and event retention.

One M fixture was deliberately migrated: `nativeMithrilReproducesExactMissingMagicPowerThenRejectsBeforeCommit` becomes `unauditedStaffReproducesExactMissingMagicPowerThenRejectsBeforeCommit`. The owner now requires Mithril support. **All missing-power/no-payment/diagnostic assertions remain**, using `Unaudited_Staff_Test` with native Staff tags, and a separate positive test now proves real Mithril. The packaging gate explicitly records that one migration; it does not broadly ignore missing tests. No persistence/escrow/exact-once/archived-reader tests were changed.

Retained fault/recovery tests, 87 skills/66 passives, targetless contracts, resources/cooldowns, bounded traces and CustomUI validation pass. The unchanged real-storage 60 × 64-update durability diagnostic measured p50 **4.4345 ms**, p95 **7.8401 ms**, p99 **16.0393 ms**; recovery contributor counts passed. These are durable acknowledgement timings, not native tick timings. N does not promote them to a release-gate pass or change the previously authorized nonblocking handoff interpretation.

The exact candidate passed isolated normal three-mod boot/setup/native asset audits/shutdown. Startup reports `RPG_STAGE13_N_POWER_REGISTRY entries=197 ... traceLevel=NORMAL connectedProof=false`. JAR differential: **14 changed/added entries**, restricted to power/trace classes, command registration/diagnostic startup log, successful compile-trace routing, and the new manifest. Every other entry is identical to M, including native ability bridge/input/HUD, SkillExecutionService, family executors, durability/escrow/ordering, XP and other assets. Atomic rollback/roll-forward drill and archived-reader retained checks passed.

Evidence: [full-validation.txt](../../evidence/stage-13/cohort-n/full-validation.txt), [test-results.json](../../evidence/stage-13/cohort-n/test-results.json), [server-smoke-summary.json](../../evidence/stage-13/cohort-n/server-smoke-summary.json), [jar-differential.json](../../evidence/stage-13/cohort-n/jar-differential.json), [durability-load.json](../../evidence/stage-13/cohort-n/durability-load.json).

## 5. Package, deployment and rollback

Deployed only the corrected RPG JAR into:

```text
C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods
```

The native server was stopped. Deployment staged and hash-checked the JAR, retained M, and atomically replaced the existing RPG JAR. There are still exactly three installed mods. All **17 non-JAR mod data files** hashed identically before/after; no world/player save or owner artwork was edited. Supporting mod bytes are unchanged.

| Artifact | SHA256 |
| --- | --- |
| `HytaleRPG-0.0.25.jar` N | `669B230DAC394A2CE1246125028C51DF446BBC5024FCBEED83059F65F8BDDF33` |
| `CanvasUI-0.1.0.jar` | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| `HYTALEDEVLIB-0.5.0.jar` | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| Three-mod ZIP | `BF74D173C7CAE53394ECE0C5AA1467F254EC13AEEFC1F64FF53E64CABCCA296F` |
| Retained M rollback RPG JAR | `AABF71F03C31014A092C471C45965013DBD3FE491D50E17AB817148ACBA1D410` |

[Three-mod archive](../../evidence/stage-13/cohort-n/Hytale-RPG-Stage13-N-power-trace-correction.zip), [deployment manifest](../../evidence/stage-13/cohort-n/power-trace-correction.json), [M rollback](../../evidence/stage-13/cohort-n/rollback/HytaleRPG-0.0.25.jar). Roll back only with the server stopped, replacing the RPG JAR with that verified M copy. Do not replace save data. No Google Drive writes.

## 6. Minimal connected retest — still required

1. Restart/rejoin RPG. Run `/rpg-trace status`; expect NORMAL. Confirm the N startup marker above in the server log.
2. In `/rpg skilltree`, equip Quick Slash into skill01 and Fire Bolt into skill02. `/rpg dev ability-status` should confirm native Ability2/Ability3 projection.
3. Hold **Flame Longsword** in the active hand, aim into empty space, and press Ability2/E once. Require native input → activation → validation pass → commit → dispatch, one Stamina payment/cooldown, and no fabricated hit. Visually verify the swing separately.
4. Switch the active held weapon to **Mithril Staff**, aim into empty space, and press Ability3/R once. Require the same pipeline plus projectile spawn request/spawn, one Mana payment/cooldown. Let the projectile expire; there must be no refund, fake damage or mastery.
5. Repeat against a valid target after cooldown. Verify actual native hit/Health loss and no duplicate execution/payment. Test wrong held weapon, insufficient resource and active cooldown rejection separately.
6. Leave NORMAL active during ordinary testing: expect periodic summaries, not per-tick samples. For a formal native performance capture, run `/rpg-trace performance` **before** the prescribed connected workload, retain raw samples plus mode boundaries/gap metrics, then restore `/rpg-trace normal`. Do not calculate qualification percentiles from summaries.
7. Restart/rejoin and verify persisted loadout/character state. Trace mode returning to NORMAL is intentional and is not a loadout persistence failure.

The two specific power failures are fixed locally, but connected animation/carrier/hit behavior and formal performance remain unverified until this retest. The unresolved items above and preexisting Stage 13 capability/QA limitations remain documented; this correction does not close unrelated gates or begin another stage.
