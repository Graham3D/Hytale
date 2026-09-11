# Add your own skill and passive icons

You do not need Codex, Java, Gradle, or a JAR editor.

R032-V's optional new filenames are `SkillHealingbeam.png`,
`SkillBlessingofprotection.png` and `PassiveArc.png`. Use these only with a V-or-newer
build containing those catalog entries. The updater also supports the older
87-skill/66-passive builds, but intentionally rejects icons unknown to that build.

1. Save skill PNGs in **art/Skills**; save passive PNGs in **art/Passives**.
2. Use the exact filename in **[ICON-FILENAMES.csv](ICON-FILENAMES.csv)**.
   Open it in Excel or a text editor: every row names the actual skill/passive.
3. **Close Hytale completely** (and any Hytale server).
4. Double-click **Update RPG Icons.cmd** in the Hytale GitHub folder.
5. Wait for **SUCCESS** (or **Already up to date**), then launch Hytale and rejoin RPG.

| Content | File | Folder |
|---|---|---|
| Fire Bolt | SkillFirebolt.png | art/Skills |
| Fireball (a different skill) | SkillFireball.png | art/Skills |
| Quick Slash | SkillQuickslash.png | art/Skills |
| Whirlwind | SkillWhirlwind.png | art/Skills |
| Potency | PassivePotency.png | art/Passives |
| Shared Aegis | PassiveSharedaegis.png | art/Passives |

Names use `Skill` or `Passive`, then the content name with spaces/punctuation
removed: first letter capitalized, remaining letters lowercase. The CSV is
authoritative; you do not need to guess compound names.
**Fire Bolt and Fireball are different rows.** Make sure Windows is showing file
extensions so you do not accidentally save `SkillFirebolt.png.png`.

## Artwork

- PNG, preferably **128 x 128**, with a transparent background.
- Square images from 16 x 16 through 1024 x 1024 are accepted, up to 4 MiB each.
- Draw only your symbol. The existing slot background/frame remains behind/around it.
- The updater does not edit, tint, resize, crop, or remove backgrounds from PNGs.
  An opaque image will cover the normal background.
- Non-PNG working files such as PSDs are ignored. Unrecognized PNG filenames or
  invalid images stop the update before the installed JAR changes.

## What gets replaced?

Skills: the native skill-slot icon plus the Skill Tree library, details and
equipped-node preview. Passives: Skill Tree library, details and equipped-node
preview. Passive artwork does **not** replace a Chill/status-effect icon.
No damage, cost, cooldown, XP, resource bar, input, or save-data changes.

Missing artwork keeps the existing fallback. Only supplied PNGs are updated.
Deleting a source PNG does not remove an already installed icon. To undo the
last update, close Hytale and double-click **Undo Last Icon Update.cmd**.
Undo refuses to overwrite a newer mod build or other subsequent changes.

## Where the installed artwork lives

Hytale loads these images from its mod asset pack, not directly from this art
folder. The updater safely packages them inside the existing RPG JAR under
`Common/Icons/Items/RPG/` (skills) and
`Common/UI/Custom/Icons/RPG/` (skills and passives), then installs that JAR to:

`C:/Users/Zemio/AppData/Roaming/Hytale/data/pre-release/Saves/RPG/mods/HytaleRPG-0.0.25.jar`

Keep the original PNGs here. After installing a newer RPG build, run the updater
again to reapply your complete artwork collection. Do not put loose PNGs directly
in the mods folder and do not add a fourth mod.

The tool checks the embedded 87-skill/66-passive filename index, validates every
image, creates a staged JAR, verifies unrelated entries are unchanged, and makes
a hash-verified backup before replacing the installed JAR atomically.
Backups and before/after hashes live in **icon-backups** in the GitHub folder.
It never edits live saves. Restart/rejoin is required; this is not hot reload.

This workflow is locally validated. New artwork still needs your visual check in
`/rpg skilltree` and the native skill slots.
