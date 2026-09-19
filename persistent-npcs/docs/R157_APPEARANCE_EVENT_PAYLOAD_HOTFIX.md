# R157 — Appearance event payload hotfix (Checkpoint 1 repair)

## Scope and gate status

R157 is a narrow repair to the R156 Appearance Editor interaction envelope. It
does not begin the two-thumbnail Checkpoint 2, restore catalog thumbnails, alter
appearance authority, or change the editor layout. Checkpoint 2 remains gated
until this build passes connected validation.

## Connected failure evidence

The connected R156 server log at
`UserData/Saves/NPC/logs/2026-09-05_13-56-13_server.log` proves that Hytale sent
ordinary-key selector strings literally:

- option events supplied `#AppearanceCatalogHash.Text` and
  `#AppearanceOptionN #Id.Text`, then failed `STALE_CATALOG_HASH`;
- a category event failed with
  `No enum constant ...Category.#APPEARANCECATEGORY3 #ID.TEXT`;
- telemetry reported `payloadSelectorsResolved=false` for the affected option
  events.

This explains both the placeholder-only screen and the red validation message:
R156's interaction lifecycle was released correctly, but the client/server
payload contract for dynamic values was wrong.

## Hytale 0.6.3 contract confirmation

Installed `HytaleServer.jar` bytecode was inspected before implementation.
Native pages use `@`-prefixed `EventData`/`KeyedCodec` names when a value must be
read from a client UI property—for example `@BrowserSearch` bound to an input's
`.Value`. Native buttons embed server-owned IDs directly.

R157 follows that contract:

- search uses `@AppearanceSearch` with
  `#AppearanceSearchInput.Value` on both the search button and the text field's
  `ValueChanged` event;
- category names, catalog page hashes, cosmetic IDs, color IDs, and variant IDs
  are embedded as immutable server-owned values in each visible control's event
  binding;
- no ordinary event-data key contains an Appearance UI selector;
- all browsing interactions remain non-locking, while Save/Cancel/Back retain
  R156's transition locking and acknowledgment behavior.

Because bindings are regenerated from the current server page, a replaced page
cannot convert an old identity into a current one. Existing catalog-hash,
authoring-session, page-generation, and editor-generation admission checks remain
unchanged.

## Files changed

- `NpcProfilePage.java` — corrected dynamic Appearance event payloads and native
  search-value binding.
- `R156AppearanceInteractionLifecycleTest.java` — retains the R156 lifecycle and
  lock-policy gate without asserting the disproven selector convention.
- `R157AppearanceEventPayloadHotfixTest.java` — verifies concrete payloads for
  categories, cards, pages, variants, and colors; verifies native `@` search
  capture; rejects reintroduction of ordinary-key selector interpolation.
- `test.ps1` — adds the R157 regression gate.
- release identity/build/install/manifest files — monotonic R157 identity.

## Deterministic validation

`test.ps1 -SkipLive` passed in full on 2026-09-05, including:

- all historical persistence, inventory, gear, stats, Profile, voice, cognition,
  and Appearance gates;
- R155 static-resource safety;
- R156 lifecycle/acknowledgment behavior;
- R157 concrete event-payload and native search-binding coverage.

No runtime image generation, thumbnail catalog, or local-model work was enabled.

## Deployment and rollback

- Candidate:
  `ImmersiveNPCs-0.6.3-R157-NPC-APPEARANCE-EVENT-PAYLOAD-HOTFIX.jar`
- Candidate SHA-256:
  `A6AD833401CD464D4351F65C454DBFE8FAD5ACB8AFA7F3916C24FEE3C97B524A`
- Active project JAR after deployment:
  `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R157-NPC-APPEARANCE-EVENT-PAYLOAD-HOTFIX.jar`
- Immediate rollback:
  `C:\HytaleRollback\NpcAuthoringStudio-Appearance-R156-2026-09-05-R157PayloadHotfix\ImmersiveNPCs-0.6.3-R156-NPC-APPEARANCE-EVENT-LIFECYCLE.jar`
- Immediate rollback SHA-256:
  `3EC90BF7057E3368344542D8EB433E595362282B7502FCC68223A0CE24B27595`
- Exactly one project JAR is active in the save's `mods` directory.
- The earlier R155 checkpoint rollback remains preserved separately.

## Connected validation checklist

1. Restart the `NPC` world/local server and confirm the HUD/revision reports
   `R157-NPC-APPEARANCE-EVENT-PAYLOAD-HOTFIX`.
2. Open `/npc update Hoit`, then Appearance.
3. Select every primary rail and several secondary categories. Confirm no red
   `No enum constant` message appears.
4. Select cards on page 1 and page 2. Confirm the central NPC preview changes and
   no `STALE_CATALOG_HASH` rejection occurs.
5. Select several colors and variants; confirm the authoritative draft preview
   updates.
6. Search for a known cosmetic, clear search, and verify pagination still works.
7. Save one change, close/reopen, and confirm persistence.
8. Discard a separate change and confirm restoration.
9. Confirm Back, Profile, Voice Recorder, coupled inventory, and player
   appearance/equipment remain unchanged.
10. Review the new server log for concrete `suppliedCatalogHash`/`optionId`,
    `payloadSelectorsResolved=true`, and no selector-valued enum/hash failures.

**STOP:** R157 awaits connected Checkpoint 1 approval. Do not begin Checkpoint 2
until this checklist passes.
