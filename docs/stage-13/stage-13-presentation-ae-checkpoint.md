# R032-AE checkpoint

**IMPLEMENTED / PACKAGED / DEPLOYED. CONNECTED-VERIFIED = false.**

Public filename: `HyARPG.jar`. Internal mod/save identity is unchanged.

Installed SHA-256: `5629BDEA510ED1543DC43812A8E10EDE84744D5037ECEBDD7335369E380E0C77`.

[Report, rollback and minimal connected checklist](stage-13-presentation-ae-report.md).
[Spell-color editing instructions](../owner-spell-color-guide.md).

All 2,237 retained tests pass. The installed-byte isolated test reproduces AD's
Beam processing guard, then proves real create/update/remove and same-buffer
cancellation with unchanged `Basic`. The owner must still verify the Beam and HUD
visually in a connected client. Blizzard's existing 3-second lifetime/cooldown,
native input, resources and persistence are unchanged. Do not change Beam assets
until the connected ECS test succeeds. Owner-installed icons and all non-target
save files are preserved.
