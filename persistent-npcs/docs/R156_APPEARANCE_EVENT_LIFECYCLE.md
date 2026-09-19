# R156 — Appearance interaction lifecycle recovery (Checkpoint 1)

## Scope and disposition

R156 implements **Checkpoint 1 only** of the bounded Appearance recovery brief.
It repairs Custom UI interaction completion without changing the R155 visual
layout, cosmetic cards, colors, packaged assets, appearance authority, skin
format, or preview packet sequence. Checkpoints 2 and 3 remain gated on a
connected R156 PASS.

## Root cause addressed

The installed Hytale 0.6.3 server bytecode confirms that
`UIEventBuilder.addEventBinding(type, selector, data)` delegates to the
four-argument overload with `locksInterface=true`. R155 used that implicit
locking overload for ordinary Appearance browsing actions. Several valid
handler paths then suppressed a rebuild and returned without any
`sendUpdate(...)` or page transition, including repeated selection, unchanged
search/category state, page boundaries, stale current-page catalog events, and
an all-empty command diff. A locked event on one of those paths could therefore
leave the client dimmed behind `Loading...` indefinitely.

The installed `CustomUIPage` also exposes the minimal no-command
`sendUpdate()` path. R156 uses that only as an interaction acknowledgment; it
does not rebuild the page or send image resources.

## Interaction contract

- Primary/category selection, search, catalog paging, cosmetic selection,
  color selection, variant selection/paging, Randomize, and Reset now bind with
  explicit `locksInterface=false`.
- Save and Cancel/Back remain explicit locking transitions. Each completes via
  a UI update, dirty-confirmation update, or valid transition back to Studio.
- An unchanged/no-op current-page action receives a minimal empty update.
- A rejected stale catalog/current-selection action from the still-open page is
  acknowledged without mutating the draft.
- An event from a dismissed or replaced page is rejected by session/page/editor
  generation validation and is not acknowledged into the newer page.
- Command-diff suppression remains active. A completely empty diff now sends a
  minimal acknowledgment instead of silently returning.
- Mesh preview work remains coalesced, newest-generation-wins, and asynchronous.
  The event response is not delayed until the preview applies.
- The last valid preview remains visible on preview failure; the draft is
  retained and the existing in-page error route is used.

## Bounded diagnostics

The following markers are emitted without profile contents:

- `APPEARANCE_EVENT_RECEIVED`
- `APPEARANCE_EVENT_NOOP`
- `APPEARANCE_UPDATE_SENT`
- `APPEARANCE_PREVIEW_REQUESTED`
- `APPEARANCE_PREVIEW_APPLIED`
- `APPEARANCE_EVENT_REJECTED`

Each marker includes event type, authoritative session/page/editor generation,
supplied generation values, lock policy, update-sent state, catalog hashes, and
selected option ID. `APPEARANCE_EVENT_RECEIVED` also records whether selector
payload values were resolved rather than received as literal `#...` selector
expressions, so the installed-client behavior can be confirmed during the
connected run.

## Files changed

- `src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java`
  — explicit event lock policy, terminal acknowledgment paths, stale-page
  isolation, async preview markers, and bounded event diagnostics.
- `src/test/java/com/inigmasgames/persistentnpcs/R156AppearanceInteractionLifecycleTest.java`
  — production binding inspection plus actual-handler tests for no-op,
  current-page rejection, and replaced-page rejection.
- `src/test/java/com/inigmasgames/persistentnpcs/R155AppearanceStaticSafetyTest.java`
  — retained R155 preview-gate assertion adjusted for the traced call signature.
- `src/test/java/com/inigmasgames/persistentnpcs/R153AppearanceColorCardsTest.java`
  — production archive inspection follows the current R156 artifact.
- `test.ps1` — adds the R156 deterministic gate.
- `build.ps1`, `install.ps1`, `src/main/resources/manifest.json`, and
  `src/main/java/com/inigmasgames/persistentnpcs/PersistentNpcsPlugin.java`
  — monotonic R156 release identity.
- `docs/R156_APPEARANCE_EVENT_LIFECYCLE.md` — this report.

No `.ui` file, cosmetic asset, appearance persistence schema, profile data, or
unrelated subsystem was changed.

## Deterministic validation

Command:

```powershell
.\test.ps1 -SkipLive -ServerJar 'C:\Users\Zemio\AppData\Roaming\Hytale\install\release\package\game\latest\Server\HytaleServer.jar'
```

Result: **PASS**. The complete deterministic Persistent NPC suite passed; live
local-model tests were intentionally skipped. The new R156 test invokes the
actual `NpcProfilePage.handleDataEvent(...)` implementation and proves:

- ordinary Appearance bindings are explicitly non-locking;
- Save/Cancel bindings remain explicitly locking;
- binding payloads retain catalog/current-option selector expressions;
- repeated current selection emits NOOP and UPDATE_SENT;
- stale current-page catalog input emits REJECTED and UPDATE_SENT;
- a replaced-page generation emits REJECTED and no update into the newer page;
- R155 zero-runtime-image/resource-quarantine invariants remain intact.

Final log: `build/r156-deterministic.log`.

## Deployment and rollback

- Active candidate:
  `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R156-NPC-APPEARANCE-EVENT-LIFECYCLE.jar`
- R156 SHA-256:
  `3EC90BF7057E3368344542D8EB433E595362282B7502FCC68223A0CE24B27595`
- Active project JAR count: `1`
- Immediate R155 rollback:
  `C:\HytaleRollback\NpcAuthoringStudio-Appearance-R155-2026-09-05-Checkpoint1\ImmersiveNPCs-0.6.3-R155-NPC-APPEARANCE-STATIC-SAFETY.jar`
- R155 SHA-256:
  `0DB4FB007164A97347C518BA65C68D3C0992791A99B0A8E203213000DE11E422`
- Profile files before/after deployment: `76`
- Deployment-time aggregate profile fingerprint before/after:
  `9C47A5D7BAD12FE186D0A794C803F3BFAB4DFD511D2E83D73619D5F77FF1FACE`

Hytale/Java was stopped for the swap. The server was not launched by the build
process.

## Connected validation required

Test from a clean client/server start at both 1920×1080 and 2560×1440:

1. Open `/npc update <name>` and enter Appearance.
2. Select the already-selected cosmetic repeatedly.
3. Select different cosmetics rapidly and confirm the preview converges to the
   last choice.
4. Rapidly change primary/category selections.
5. Rapidly change colors and variants.
6. Apply and clear search/filter input, including an unchanged search.
7. Exercise catalog and variant boundaries.
8. Use Back, reopen Appearance, and repeat.
9. Save one change, reopen, and verify persistence; discard another and verify
   restoration.
10. Confirm Profile, Voice, Inventory, Gear, and normal NPC preview restoration
    remain functional.
11. Review server/client logs for the six R156 markers and confirm selector
    payloads report `payloadSelectorsResolved=true`.

Acceptance requires zero permanent Loading overlays, zero stuck dimmed screens,
no page reopen needed to recover, a functional live preview, and no profile or
skin persistence regression.

**STOP:** R156 is awaiting connected Checkpoint 1 approval. Do not begin
Checkpoint 2 until that approval is explicit.
