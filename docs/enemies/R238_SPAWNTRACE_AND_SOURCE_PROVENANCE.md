# R238 natural spawn trace and source provenance

## Connected trace, 2026-10-08

The RPG save's `monster-spawn-1791504073874.jsonl` recorded 576,211 aggregate events over the owner's two-minute trace. Detailed events stopped at the 10,000-event limit. The active `world-config.json` has an 8.0 native environment spawn multiplier and Normal probabilities of 1.6% Champion plus 6.4% Unique.

| Trace stage | Count |
| --- | ---: |
| Native jobs created | 2,209 |
| NPCs added by native jobs | 1,906 |
| Eligible native groups intercepted | 419 |
| Rarity decisions | 273 |
| Elite plans completed | 2 |
| Pack reservations attempted | 2 |
| Original groups restored after declining modification | 419 |

All 25 detailed `NATIVE_GROUP_INTERCEPTED` events captured before detail saturation report `activePackReservations=12`. `default` recovered 14 birth/pack records at startup, of which 12 nonterminal packs occupy the configured `maxActiveSpecialPacksPerLoadedWorld=12`. `EnemyPackCapacity.reserve` rejects a new pack at that limit. The trace's two `PACK_RESERVATION` events occurred after the detailed limit, so their exact `gate` fields are unavailable; the capacity index and earlier events strongly indicate world-limit rejection. The owner can raise this supported setting in `world-config.json` and use `/rpg worldconfig reload` for subsequent births. Existing packs are not rerolled.

The first 10,000 detailed events include three non-Normal rarity draws that fell back with `NATIVE_EXTENSION_UNAVAILABLE`. Native flock extension checks world, environment, and nearby chunk headroom and the original flock's identity; this trace does not identify which subcheck failed. No capacity or extension safety guard was bypassed. The very large `NATIVE_REJECTION_NO_POSITION` and `NATIVE_REJECTION_INVALID_SPAWN_BLOCK` totals count native placement attempts, not lost actors. The 1,906 additions prove natural spawning continued.

The trace therefore disproves a disabled Normal rarity roll or a globally stopped native spawner. It proves pack-cap saturation and shows an additional native extension gate. A follow-up trace after increasing the cap would distinguish those gates without changing rarity or spawn rules.

## Deployed build provenance

- The originally deployed R237 JAR was built from uncommitted workspace source. **No pre-existing Git commit exactly identified that build.** Its SHA-256, preserved as the pre-R238 backup, is `43AD493E6F9B8D503B9209593D47DD4F1318F17CD654C9DC3FC4F28336B05928`.
- `codex/r237-source-reconstruction` commit `dfb2fe0886a48181c62e20c956bf253c38295e73` reconstructs the R237 source state from the R238 delta. A clean offline build has the same 19,299 archive entries and identical executable class bytes as the deployed R237 JAR. The 13,728 text entries with byte differences become identical when CRLF is normalized to LF. Its whole-JAR checksum is therefore different; this is a verified content reconstruction, not a claim that the historical build had a source commit.
- The complete current R238 implementation source, tests, assets, tools, and documents is committed and pushed on `codex/checkpoint-c-optional-bridge` at `0ed109dc9083eebbbfecfee155b67382018d675e`. The deployed R238 JAR SHA-256 is `6A7FF41FEF07733636E10A7B34DA011309CB5315E0D86A3504544E9361B55D49`.
- A clean R238 checkout compiled offline. Its classes match the deployed R238 JAR, while 3,865 text assets differ only by Windows line endings and eight empty directory entries differ. The source is preserved on Git; byte-identical archive reproduction additionally requires preserving the original workspace asset line endings and empty directories.
- The native patch build also requires the hash-pinned original Hytale server JAR (`35A34A32175CD92CE5E2A51310953DB3A4A64CAC89D995CBD4830C52A9B1B904`). A verified recovery copy now lives outside worktrees at `C:\Users\Zemio\.codex\sdk-recovery\HytaleServer-original-35A34A32175CD92CE5E2A51310953DB3A4A64CAC89D995CBD4830C52A9B1B904.jar`. The source patch and hash checks are in `tools/native-patch/`.

No Hytale server was launched for this investigation and no JAR or gameplay configuration was changed.
