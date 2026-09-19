# R169 Profile Document Tabs

Revision: `R169-PROFILE-DOCUMENT-TABS`

Date: 2026-09-05

## Outcome

R169 refines the existing Profile Editor without changing profile authority,
persistence, generation, or save/discard behavior.

- The selected Profile section now displays the exact packaged gold selection
  marker already used by the Voice Recorder:
  `ImmersiveNpcInventory/NpcIconSelectSample.png`.
- The unsupported server-emitted `▶` character was removed. It was rendered by
  the client as `?`; section labels are now static UI content and selection is
  represented only by the image marker.
- Each rail button now acts as a document tab. It asks the mounted Profile form
  to scroll its corresponding direct child into view through Hytale 0.6.3's
  native `ScrollChildIndexIntoView` group property.
- Navigation sends a focused state/scroll update and does not clear, append, or
  rebuild the form, preserving unsaved field input.
- The Personality and Values section envelopes were enlarged to contain their
  declared children. This removes the observed `VALUES & BELIEFS`/`Dislikes`
  overlap and prevents the following Motivations heading from encroaching.

## Stable section mapping

The mounted form owns these direct children and rail targets:

| Index | Rail target | Form child |
| ---: | --- | --- |
| 0 | Basic Info | `SectionBasicInfo` |
| 1 | Background | `SectionBackground` |
| 2 | Personality | `SectionPersonality` |
| 3 | Values & Beliefs | `SectionValues` |
| 4 | Motivations | `SectionMotivations` |
| 5 | Relationships | `SectionRelationships` |
| 6 | Speech Style | `SectionSpeech` |
| 7 | Notes | `SectionNotes` |

The active marker is toggled independently for all eight buttons. A fixed icon
well remains in every row so selected and unselected labels retain identical
alignment.

## Regression coverage

The complete deterministic suite passed with live local-model tests skipped.
The new `R169ProfileDocumentTabsTest` additionally verifies:

- all eight buttons and selected-marker nodes exist;
- the Profile markers reuse the Recorder asset;
- no unsupported triangle glyph or runtime label rewrite remains;
- indices 0 through 7 remain explicit and stable;
- navigation emits the native integer scroll property;
- the navigation route does not clear/rebuild the mounted form;
- corrected Personality and Values section heights remain present.

Release-resource validation and compilation also passed. Existing warnings are
limited to pre-existing deprecated Hytale/Unsafe APIs.

## Deployment

- Active JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R169-PROFILE-DOCUMENT-TABS.jar`
- Active SHA-256: `F22E2CCD9C6B3B8442748BC17827D33855ACAC4F4BCDD915467E2FBB072CB57F`
- Active project JAR count: `1`
- Immediate rollback: `C:\HytaleRollback\ProfileDocumentTabs-R168-2026-09-05-R169\ImmersiveNPCs-0.6.3-R168-PROFILE-SECTION-EVENT-HOTFIX.jar`
- Rollback SHA-256: `FFF3D432D4D3C127112F1A13BAAC7541A926F47AEDD3CD67F83D2001DB7D307D`

## Connected validation checklist

1. Join the NPC save and confirm the HUD reports
   `R169-PROFILE-DOCUMENT-TABS`.
2. Open an NPC Profile Editor and click each of the eight rail buttons.
3. Confirm only the selected row shows the Recorder-style gold marker and that
   no `?` appears.
4. Confirm each click moves the corresponding section to the top of the form
   viewport (the final sections may stop at the natural scroll extent).
5. Type unsaved content, navigate between several sections, then return and
   confirm the draft input is retained.
6. Inspect Personality through Motivations and confirm section headings do not
   overlap Dislikes, helper text, or neighboring sections.
7. Save, close, and reopen to confirm existing Profile persistence behavior.

Stop after connected validation for approval.
