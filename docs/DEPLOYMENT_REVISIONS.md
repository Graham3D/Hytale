# Hywind deployment revisions

This is the historical deployment ledger recovered from the primary R141
working tree. It is not the development baseline record; see
[`DEVELOPMENT_BASELINE.md`](DEVELOPMENT_BASELINE.md). A copied or compiled JAR
does not establish connected-game acceptance.

Record the build revision, version, deployed artifact checksum, and purpose
after each owner-QA deployment. The manifest and `gradle.properties` must
carry the same revision.

| Revision | Version | Deployed (EDT) | SHA-256 | Purpose |
| --- | --- | --- | --- | --- |
| R141 | `0.1.0-merge.91` | 2026-10-04 13:28:50 | `18298D5F25582118DE92AA7B27585015C9245B06450B8C0A3C3D5256379091A8` | Remove invalid `Dropped` CustomUI event binding from the inventory Group backdrop; retain native ItemGrid and explicit Drop Selected handling. |
| R140 | `0.1.0-merge.90` | 2026-10-04 13:21:34 | `CDC979D893FF36DD0DE934C58F58FB3392A3BD807377381412576C5839F6837F` | R139 CustomUI repair plus bounded read compatibility for preserved operator-QA loot receipts that exceed production rarity affix budgets. |
| R139 | `0.1.0-merge.89` | 2026-10-04 13:16:58 | `DD5D557CE91CC58A9079DD8A0BA0BA65756A1D094E8A70DAD9D35772B4C1D004` | Repair Hywind inventory CustomUI import after split; package the shared grid style and textures under Hywind ownership. |
| R138 | `0.1.0-merge.88` | 2026-10-04 13:09:43 | `5CF221BA501845112FB4E865DD56D41FFEE5F34156DA8B4C91740FF5F45738AA` | Restore post-split Hywind/ImmersiveNPCs boundary while retaining Master Enemies and later RPG work. |

R140 is superseded by R141 after the connected client log exposed the
incompatible inventory backdrop event binding. R139, R138, and R137 were intermediate
deployments during the split recovery.
The original post-split `HyARPG.jar` is archived at
`evidence/hywind/split-reference/HyARPG-post-split-reference.jar`; it is not
in the active save's mods directory.

## Later R249/R250 evidence

The R249-U7P5B deployment receipt and offline verification are preserved at
[`R249B_DEPLOYMENT.md`](enemies/R249B_DEPLOYMENT.md) and
[`R249B_VERIFICATION.json`](enemies/R249B_VERIFICATION.json). The receipt
records SHA-256 `A0EA6EEB80577AA30AA787475E7B7750417F5DE7530A57EC7DF72DE7D8E13101`
and source commit `6c0ab9fd6a8ddaf647ba075d831bab1128f495c1`. Its
connected-game and owner acceptance gates remained open.

On 2026-10-10, a read-only inspection found `RPG/mods/Hywind.jar` at SHA-256
`F56BB5120BFA764088A30EBFB4ED62C067C1A4899E90A50B0A7B5DA5CBE14C30`.
It was byte-identical to the R250-U7P5B build in the
`codex/teleport-r250-latest` worktree. Its embedded source commit is
`44184d936d750e0a6729a8f797e7f6a4fe30356f`; its internal plugin name
and entrypoint remain `HyARPG` and `HyArpgPlugin`. This observation does not
establish the deployment time, connected-game acceptance, or a new revision.
