# R168 Profile Section Event Hotfix

Revision: `R168-PROFILE-SECTION-EVENT-HOTFIX`

Date: 2026-09-05

Scope: connected Profile Editor section-button event payload only.

## Connected failure

Selecting any button in the Profile Sections rail disconnected the client with:

`Failed to gather CustomUI event binding data`

The failure occurred client-side before the section navigation event reached the server.

## Root cause

R167 encoded each server-owned section constant (`BASIC_INFO`, `BACKGROUND`, and peers) with the output-binding key `@ProfileSection`. In Hytale Custom UI, the `@` key contract is for values gathered from a live UI selector/property. The section value is not client state—it is a server-owned constant embedded when the button event is built. The client therefore attempted to gather a nonexistent dynamic value and disconnected before dispatch.

This is the same static-versus-output binding boundary previously proven by the R157 Appearance event repair:

- Static/server-owned constant: ordinary event key, such as `ProfileSection = BACKGROUND`.
- Live client field capture: output key plus selector, such as `@ProfileBiography = #ProfileBiographyInput.Value`.

## Repair

- Changed all eight section-button payloads from `@ProfileSection` to static `ProfileSection`.
- Changed the matching `PageData` codec key to `ProfileSection`.
- Preserved the dedicated `PROFILE_SECTION` action and navigation-only handler.
- Preserved all `@Profile...` Save bindings because those intentionally gather current mounted input values.
- Preserved the R167 stable identity migration, one-form scrolling model, draft authority, validation, generation, and atomic persistence unchanged.

## Deterministic validation

`test.ps1 -SkipLive` passed in full.

The new `R168ProfileSectionEventHotfixTest` constructs the real Profile Editor event bindings and verifies:

- exactly eight `PROFILE_SECTION` events exist;
- every section payload embeds a concrete static `ProfileSection` value;
- no section event contains `@ProfileSection`;
- Biography and the other Save fields retain typed client output binding.

All prior deterministic inventory, equipment, stats, profile, appearance, voice, persistence, and cognition gates also passed. Existing Hytale/JDK deprecation warnings remain warnings only.

## Deployment and rollback

- Active JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R168-PROFILE-SECTION-EVENT-HOTFIX.jar`
- Active SHA-256: `FFF3D432D4D3C127112F1A13BAAC7541A926F47AEDD3CD67F83D2001DB7D307D`
- Active project JAR count: `1`
- Immediate R167 rollback: `C:\HytaleRollback\ProfileSectionEvent-R167-2026-09-05-R168\ImmersiveNPCs-0.6.3-R167-IDENTITY-PROFILE-EDITOR-REPAIR.jar`
- Rollback SHA-256: `CAF798D68942354578592BFBC25F77002520DB6E02DF25D12609A5625B29F8D8`

## Connected validation

1. Start the NPC save and confirm the HUD reports `R168-PROFILE-SECTION-EVENT-HOTFIX`.
2. Run `/npc update Mara` and open Profile Editor.
3. Click every Profile Sections rail button once.
4. Confirm no disconnect and no `Failed to gather CustomUI event binding data` or `Unknown authoring action` error.
5. Enter a unique value in Biography and another field, select Save Profile, reopen, and confirm both exact values.
6. Mouse-wheel through all eight mounted sections and confirm draft values remain intact.
