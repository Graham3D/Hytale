# R076 Lightning Spire strike-shake correction

R076 is a bounded correction to the existing Lightning Spire implementation. It does not change the Spire lifetime, health, charge threshold, shockwave balance, emergence rise, or persistence contract.

## Correction

- A valid direct player Primary physical strike against the deployed Spire is now routed to the friendly-construct hit owner even when Hytale has already cleared the native interaction-chain entry before its damage event.
- The fallback is Spire-only. It requires a direct `Damage.EntitySource`, a `PlayerRef`, `InteractionType.Primary`, no RPG-authored damage metadata, and a Physical damage cause. Projectile, NPC, RPG-authored, and non-physical damage do not enter it.
- An accepted strike remains zero-damage to the Spire, contributes one charge through the existing deduplication owner, and starts the same two-second rotational shake envelope used during emergence.
- Strike presentation changes rotation only. It does not replay or alter the emergence rise coordinate, native collision, authoritative ground position, or health.

## Verification

Automated tests cover the shared shake envelope and fail-closed Physical-cause classification.

- `gradlew clean test`: PASS
- `gradlew nativeControlTest verifyHywindJar`: PASS
- Isolated merged-server smoke: PASS
- Two post-deployment server restarts: PASS
- Deployed artifact: `Hywind.jar`, version `0.1.0-merge.31`, revision `R076`
- SHA-256: `CFE8B04558E4237B7C15ED9F9CADCFB08FAA673D14E98C2FACE4EBF7C0F48355`
- Rollback snapshot: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260922T014953Z`

Connected-client verification remains the acceptance boundary for visible shake and one-charge-per-strike behavior.
