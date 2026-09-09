# Stage 13 K — complete current ARPG/RPG testing build

2026-09-09, branch `RPG`, starting HEAD `eb42b1c8a437c2dbf0210b2b7b38be2e63d2ad28`.

**Requested testing build: COMPLETE / PACKAGED_FOR_TESTING.** This packages the entire current RPG implementation, not a reduced persistence demo. The bounded encounter-load correction is finished. No further optimization or architecture work is part of this handoff. Production release acceptance remains unverified where connected evidence is required; that does not prevent delivery of this owner-requested testing package.

## Download and exact identities

- [Three-mod testing ZIP](../../evidence/stage-13/cohort-k/Hytale-RPG-Stage13-K-test-build.zip)
- [RPG JAR separately](../../evidence/stage-13/cohort-k/artifacts/HytaleRPG-0.0.25.jar)
- [Machine-readable build result](../../evidence/stage-13/cohort-k/test-build.json)
- [Checksum manifest](../../evidence/stage-13/cohort-k/checkpoint-manifest.json)

| File | SHA256 |
|---|---|
| Hytale-RPG-Stage13-K-test-build.zip | `E96151A649ABCD49363047D96E16CC52321A2D15A75D27E51F8FD7EE9498E159` |
| HytaleRPG-0.0.25.jar | `9083F89CB224BAC55B5D1B1CB9F5F5BA90C104D23A55B447A8E8029B4156B06F` |
| CanvasUI-0.1.0.jar | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| HYTALEDEVLIB-0.5.0.jar | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |

The ZIP contains exactly those three JARs, verified by reading and hashing their decompressed entries. The existing code version remains R032 / 0.0.25; **cohort K and the hash distinguish this build** from older same-version JARs. Target: Hytale **0.7.0-pre.1**, build `e8b4d191fc98a977bf5546a951a7b25473d323e3`, Java 25.

## Bounded fix

Only two production files changed: `PersistentEncounterRuntime.java` and `FileEncounterStore.java` (14 added / four removed lines). Previously, a queued native encounter load called the public store reader when its worker eventually ran. That reader captured the then-current global submission tail, rather than the tail present when the load was admitted. A later unrelated write could therefore extend the load's dependency. Role-mismatch exclusion repeated the same late global wait.

Native attachment now captures an immutable WAL/checkpoint frontier at admission. Its existing worker waits for that frontier and uses a prepared reader under the existing storage lock. New same-context contributions remain behind the attachment's ordered tail. Role-mismatch tombstones use the prepared exclusion operation after that same prerequisite. The ordinary public synchronous store reader retains its original waiting semantics.

No WAL format, checkpoint protocol, lock lifetime, force mode, durability deadline, queue capacity, gameplay formula, reward formula, skill executor, HUD, native input adapter or shield escrow mechanism was redesigned. This does not make disk work instantaneous: the worker still takes required IO locks, but the owner thread does not wait for it and later unrelated receipts do not become new prerequisites.

Three new tests cover:

1. A queued reload completing while a later unrelated receipt remains deliberately held.
2. A queued role mismatch durably excluding its context without waiting on that later receipt.
3. An earlier accepted contribution remaining a required predecessor, with its contributor and anti-farm watermarks restored and preserved through the next hit/death capture.

An initial new-test assertion incorrectly assumed the contribution ledger independently deduplicates identical numeric damage observations. The established ledger does not identify events from those numbers; that assertion was replaced with direct recovered snapshot/credit/watermark equality. No production behavior or retained assertion was changed to satisfy it.

## Validation of the exact candidate

The focused eight-test subset passed before the final run. Then the complete retained suite ran **once**:

```powershell
.\gradlew.bat :test :nativeControlTest :canvas-ui:test --rerun build --console=plain
```

Result: **2,090 tests, zero failures, errors or skipped tests**. All 2,087 cohort J identities remain; the only additions are the three load-ordering cases. Native-control and CanvasUI tests actually executed. No production source changed after this full run.

The full run includes retained recovery/rollback tests, the 48 retained plus seven escrow/handoff real process-halt cases, and the unchanged 60 × 64 real-storage contribution workload. Process halts are not physical power-loss simulations. The retained actual archived Stage I reader and coordinated player/reward/encounter rollback fixtures passed; copied native-world reopening remains a connected test, not a local assertion.

Final exact-JAR isolated server smoke: **PASS**, exactly three mods, plugin/asset registrations successful, native ability roots resolved, server boot successful, clean shutdown and process exit zero. This was an offline loopback server, not a connected client session. Packaging checks retain 87 skills, 66 passives, 87 runtime profiles/zero-native-cost ability triggers, 5,742 skill/passive cells, 2,145 passive pairs and 1,000 graph cases. All protected content/UI bytes remain unchanged. Static CustomUI validation passed for 32 source documents and 10 packaged RPG documents.

Storage diagnostic p50/p95/p99: **4.5710 / 8.0007 / 18.9611 ms** for a sample ending after all 64 durable acknowledgements. The historical nominal 4/8 comparison remains false. This is not a native tick measurement, and no extra optimization was attempted. Actual native tick work still requires the existing **4 ms p95 / 8 ms p99** connected qualification.

## Included functionality and known testing limitations

The package includes the current catalog, loadout/Link Tree/compiler, attributes and resources, cooldowns, strikes/projectiles/areas/connections, support and pre-durable shields, summons/conversion, progression/mastery/earned rewards, persistence/recovery, native HUD integration and diagnostics already developed through Stage 13. It does **not** imply every catalog skill has passed connected acceptance.

Remaining known issues/gates, deliberately recorded rather than expanded into new work:

- Connected native casting, animation, damage, visual behavior, rejoin and sustained multiplayer performance are not certified by the local suite or smoke. A playable testing package is delivered; universal client functionality is not claimed as observed.
- Existing explicit adapter gates remain for native bow maximum range (Snipe), selective enemy-only Bone Cage collision, native Guard held-item release routing, and per-actor basic-attack cadence. Affected content may display an unavailable reason; these gates were not bypassed.
- The earlier full free-drag/pan CanvasUI interaction work remains capability-gated/deferred; this build does not introduce a NoesisGUI implementation.
- The four-player native performance run, intended deployment scaling, remaining connected master fault/visual matrix, and actual copied-world rollback remain outstanding. Development entitlements/configuration remain suitable for testing, not a production-public-server release declaration.

See [machine-readable release readiness](../../evidence/stage-13/cohort-k/release-readiness.json). Its intentional `BLOCKED` result is **production qualification status**, separate from `PACKAGED_FOR_TESTING`; no gate was silently changed to PASS.

## Testing and rollback precautions

Test on a separate world/server copy. Stop its writers before copying saves or changing JARs. Preserve the matching **world + players + earned-rewards + encounters** checkpoint. Extract the ZIP's three JARs into that test world's/server's `mods` directory. Keep exactly one version of each mod; do not leave an older RPG or separate CanvasUI-Demo JAR alongside this package. No manual JSON/schema edits are required to build or install it.

Once joined, use `/rpg skilltree` to inspect/equip and `/rpg dev ability-status` to inspect projection; test the native configured ability inputs. Trace evidence remains under the test server's `mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl`. Report client-observed failures alongside that trace rather than inferring success from a command returning.

Immediate previous J JAR is retained under `cohort-k/rollback/stage13-j/`, SHA256 `529F7601D69F9DBDA54ABAD87FBF2C674A31C3944E641602AE1BC79225A696AC`. The Stage I and earlier rollback archives are also preserved. Never rewind only a player or reward file; restore the coordinated checkpoint with its matching binary. Player schema 9, compiled-plan schema 41, and WAL/checkpoint v2 remain unchanged.

No live JAR was installed and no live save was modified. The exact three live JARs and three previously inventoried RPG-owned data files were checked unchanged; this is not a full native-world byte comparison. All output is in the GitHub checkout, and owner `art/` remains untouched. This handoff completes the requested build and stops here.
