# R170 Creative Full-Profile Generation

Revision: `R170-CREATIVE-FULL-PROFILE-GENERATION`

Date: 2026-09-05

## Previous behavior and root cause

The Profile Editor's Generate event hard-coded the `BIOGRAPHY` scope. The
generation service then allowlisted only `BIOGRAPHY`, capped structured output
at eight changes, and automatically accepted only that one field. Likes,
dislikes, fears, knowledge domains, workplace, and self-identity were also
marked non-generatable. Consequently the authoring action behaved as a
Biography helper even though the underlying design called for a complete
character-authoring pass.

## New authoring contract

The visible action is now `Generate Profile` and the server—not client input—
selects `FILL_MISSING_ALLOWED_FIELDS`.

The boundary is:

`Basic Info + bounded approved lore -> one creative Nemotron request -> validated GeneratedProfilePatch -> NpcProfileDraft -> creator review/edit -> Save Profile -> authored canon`

Generation has no `ProfileRepository` dependency and no save path. It changes
only the revision-bound in-memory draft. The existing atomic `Save Profile`
operation remains the sole canon-promotion boundary and continues to preserve
unknown profile extensions.

The authoring request uses a dedicated creativity value of `0.72` and a 2,200
token budget. This setting is confined to `PROFILE_AUTHORING`; normal NPC
conversation sampling is unchanged.

## Fixed creator canon and eligible fields

Name, Role, Species, Age, Home, and creator Summary are presented to the model
as fixed canon and cannot appear in the output allowlist. Creator Notes are
excluded from both context and output.

The complete generation allowlist is:

- `selfIdentity`
- `workplace`
- `personality`
- `personalityTraits[]`
- `biography`
- `values[]`
- `likes[]`
- `dislikes[]`
- `fears[]`
- `purpose`
- `goals[]`
- `speakingStyle`
- `knowledgeDomains[]`

Inventory, gear, health/stats, capabilities, tasks, schedules, relationships,
stable identity, file paths, provider settings, and current world state are not
representable in `GeneratedProfilePatch`.

For the fill-missing scope, only an empty field or an exact untouched template
scaffold is eligible. Any manually dirty field wins. Any real persisted authored
content is retained. This includes the template edge where its placeholder
Personality is mirrored into `personalityTraits[]` by schema validation.

## Approved lore retrieval and creative fallback

`NpcProfileAuthoringLore` builds a read-only packet from authored profiles in
the registry. Candidates qualify only through:

- the same authored location;
- overlapping role context; or
- an existing authored relationship that resolves to a real stable NPC ID (or
  an existing target name which is then resolved to its stable ID).

The packet is capped at four NPC entries and 2,400 characters. Each entry
contains only stable identity plus bounded authored role, home, workplace,
summary, and background. It never includes inventory, live state, memory,
private player context, broad world state, or unrelated NPC records.

When no relevant authored NPC lore exists, `approvedLore` is empty and the
prompt explicitly directs Nemotron to create coherent character-level detail
from Basic Info. Sparse lore is not treated as an error.

## Structured output and validation

The typed `GeneratedProfilePatch` carries:

- request ID;
- stable NPC ID;
- base profile revision;
- source draft hash;
- allowlisted field/value changes;
- bounded warnings;
- provider/model/timestamp metadata; and
- patch schema version.

The strict JSON schema requests exactly one non-empty value for every eligible
field in a single response. Before the patch reaches the draft, validation
enforces:

- exact root and change-object schemas;
- known field enum values;
- full allowlist coverage with no duplicates;
- per-field character budgets;
- a maximum of 12 items for list-backed fields;
- no UI selector literals, control characters, system/prompt leakage,
  credentials, or filesystem paths;
- no forbidden runtime-state fields.

Any malformed, partial, over-budget, duplicated, prohibited, or leaking output
fails the complete patch. No partial draft application occurs.

## Relationship handling

Direct relationship-array mutation remains disabled. Existing relationships
may make a real registered NPC relevant to the lore packet, but generation
cannot output `relationships[]` or fabricate a target UUID. Invented incidental
people may be mentioned as biography-level lore only; they cannot become
relationship-domain records through this action.

## Lifecycle and stale-result rejection

The existing low-priority Orbis scheduler, 30-second timeout, one-active-request
handle, cancellation, and editor cleanup remain intact. A newer Generate closes
the prior handle.

A completed patch is accepted only while all of these still match:

- active handle/request ID;
- session and Profile editor;
- page generation;
- editor generation;
- draft ID;
- stable NPC ID;
- base profile revision;
- source draft hash; and
- patch schema version.

Failure or rejection updates Generate status only. The mounted editor and
manual editing remain usable.

## Deterministic verification

The complete deterministic suite passed with live local-model tests skipped.
The new `R170CreativeProfileGenerationTest` proves:

- Generate uses `FILL_MISSING_ALLOWED_FIELDS`;
- a minimal Basic Info draft yields a multi-section allowlist;
- exact template scaffolding is treated as missing;
- manually dirty fields and all Basic Info remain unchanged;
- substantial same-settlement lore is selected while unrelated profiles are
  excluded;
- no-lore context activates creative fallback;
- strict schema size equals the eligible field set;
- prohibited/malformed output leaves the draft hash unchanged;
- stale patches are atomically rejected;
- generated content is visible in draft values but the profile file remains
  byte-for-byte unchanged before Save;
- Save persists the generated fields; and
- a fresh registry load retains that authored canon.

All earlier Appearance, Voice Recorder, inventory, equipment, stats, Profile,
and cognition gates also passed. Existing warnings are limited to pre-existing
deprecated Hytale/Unsafe APIs.

## Deployment

- Active JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R170-CREATIVE-FULL-PROFILE-GENERATION.jar`
- Active SHA-256: `0E231487E8BAE985E32488A082E5B6E592B897F6DA7E74D82356EFBC22C15814`
- Active project JAR count: `1`
- Immediate rollback: `C:\HytaleRollback\CreativeFullProfile-R169-2026-09-05-R170\ImmersiveNPCs-0.6.3-R169-PROFILE-DOCUMENT-TABS.jar`
- Rollback SHA-256: `F22E2CCD9C6B3B8442748BC17827D33855ACAC4F4BCDD915467E2FBB072CB57F`

## Connected validation checklist

1. Join and confirm the HUD reports
   `R170-CREATIVE-FULL-PROFILE-GENERATION`.
2. Create or open an NPC with only Name, Role, Species, Age, Home, and Summary
   meaningfully authored.
3. Select `Generate Profile` and wait for the low-priority request.
4. Confirm Biography, Personality, Traits, Values, Likes/Dislikes, Fears, Goals,
   Purpose, Knowledge Domains, and Speaking Style populate together.
5. Confirm Basic Info and any manually written non-Basic field are unchanged.
6. Navigate across the rail and edit generated text before saving.
7. Cancel once and confirm generated draft content is not canonical.
8. Generate again, select `Save Profile`, close/reopen, then restart and confirm
   values persist and are available to Orbis only after Save.
9. Repeat once in a lore-rich location such as Sandsdeep and once with a novel
   location/role that has little or no supporting authored lore.

Stop after connected validation for approval.
