# R032-AI isolated visual checklist

**NOT DEPLOYED.** AH remains installed. These controls diagnose the missing visuals; they are not a repaired production beam. The repair brief requires an isolated world and forbids an automatic live deployment.

## Prepare and start only when ready to record

In PowerShell:

```powershell
Set-Location -LiteralPath 'C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale'
.\tools\New-HealingProbeAI.ps1 -Start
```

This creates a fresh `run\healing-probe-ai-<unique-id>` directory and starts a local test server at **127.0.0.1:5591**, pinned to the audited native build. Join that address using Direct Connect. It does not replace anything in `Saves\RPG\mods`. It copies the local permissions file only, not live character/mod state. Omit `-Start` to prepare only. If needed, `/op self` is enabled in this isolated server; the diagnostic permission is `inigmasgames.rpg.healingprobe`. Confirm the **R032-AI** badge.

### Authenticate the test server before Direct Connect

The launcher now explicitly uses `--auth-mode authenticated`. Its original `offline` setting was wrong for Direct Connect: installed pre.2 rejects it as singleplayer-only. Listening on a port alone does not prove join readiness.

Stop the old offline probe with `stop` in **its server console**, then rerun the updated launcher above. After boot, type these commands into the **running server console**, not a separate PowerShell prompt or the client's chat:

```text
auth login device
```

Open the URL displayed by Hytale, complete the device sign-in with your Hytale account, and follow any profile-selection instructions. Then:

```text
auth status
```

Wait for the server to confirm successful authentication before connecting to **127.0.0.1:5591**. If it still says no server tokens are configured, sign-in has not completed. Client/launcher sign-in is not a substitute for this dedicated server's own session. Do not use `insecure`, a singleplayer flag or token copying from the live save as a workaround. Keep login codes/tokens private.

Each helper invocation creates a fresh disposable directory; it does not copy auth credentials or change auth persistence settings. Consequently a newly created probe may require device sign-in again. A prepared-only command printed by the **old** helper still contains `offline`; discard that command and generate a new one with the corrected helper.

Stop the server normally with `stop` in its PowerShell server console. Do not force-kill it as a visual cleanup test. A stopped disposable directory can be retained as evidence; no automatic recursive deletion is performed.

## One control at a time

Face toward world **-Z** and stand on clear level terrain. Each `native` run creates a visible temporary native mannequin six metres in that direction. It is removed after the ten-second test. Wait **12 seconds** between runs so cleanup receipts can be recorded. No healing, Mana payment or skill cooldown is involved.

```text
/rpg-heal-probe world native
/rpg-heal-probe empty native
/rpg-heal-probe visible native
/rpg-heal-probe recipient-once native
/rpg-heal-probe recipient-overwrite native
```

Record each separately: visible or invisible, direction, startup delay, whether it blinks, and what remains after the test. The visible-model carrier may itself show a mannequin at the source; distinguish its model from its attached particles. All core controls except `beam` reproduce the stock directional emitter's existing length limitation.

Equip an audited vanilla staff, such as Bronze, then run:

```text
/rpg-heal-probe staff-once native
/rpg-heal-probe staff-overwrite native
```

Confirm Staff_Bronze is absent before/after each run. Inspect first person and third person independently. This tests a staff effect, not a proven staff-tip core endpoint.

Optional explicit geometry-only comparison:

```text
/rpg-heal-probe beam native
```

This intentionally uses the **existing AF material**, not an approved new Healing Beam look. Record only native geometry visibility and update/removal. Do not treat its appearance as the proposed repair.

Early cleanup command, valid for all modes:

```text
/rpg-heal-probe stop native
```

For `world`, early stop cannot cancel one exact emitter instance with the audited packet API. Wait for its finite native lifetime/tail. No broad effect-cancel packet is used. Entity-attached controls should remove their owned effects/carriers immediately when the deferred world operation executes; record any residue.

## Moving target and second viewer

Use a loaded, visible native NPC's **runtime entity UUID** in place of `native` to keep the same recipient between comparisons. Do not use an NPC profile identifier. The probe does not move or despawn a borrowed NPC. Use the normal native NPC tools/behavior to move it, and record how the recipient-only effect follows it.

Have a second client join or enter visibility several seconds after an attachment starts. Record whether that viewer sees the effect and compare its viewer-specific queue/outbound receipts. No second-client result has yet been obtained.

Only after the native control passes, repeat in a new disposable world with ImmersiveNPCs explicitly supplied:

```powershell
.\tools\New-HealingProbeAI.ps1 -Start -NpcModPath 'C:\path\to\ImmersiveNPCs-<version>.jar'
```

That opt-in comparison has four mods. The validated baseline archive/smoke has exactly three. Spawn the NPC using the mod's normal tools, obtain its current runtime entity UUID, then repeat the recipient-once/overwrite pair using that UUID. Keep the primary target at full Health for this visual-only comparison. No damage or injured-healing test is required here.

## Evidence to return

- Mode, AI badge, root/generation, approximate timestamps, native versus Immersive recipient, local versus second viewer.
- A short video showing start, stationary view, movement, stop and any remaining particles.
- The matching client log, not only server log.
- Disposable server log and `mods\InigmasGames_HytaleRPGPhase00Audit\logs\rpg\skill-trace.jsonl` (plus its rotated archives/manifests if present).

Interpretation: REQUESTED → ENTITY_CREATED/EFFECT_ATTACHED → VIEWER_TRACKED → UPDATE_QUEUED → OUTBOUND_OBSERVED narrows the server side. **OUTBOUND_OBSERVED is before transport; it does not prove packet delivery or client rendering.** Report the first missing transition instead of declaring the visual fixed from server receipts. Keep all unrelated live QA sessions separate.
