# Owner deployment policy

Updated 2026-09-12: the owner explicitly instructed, **“Always deploy a new build!”**

For completed RPG build/change tasks, deploy the newest validated cumulative test
artifact to the actual connected QA save's mods folder unless the owner explicitly
prohibits deployment for that task. Do not confuse packaging with installation.
Do not overwrite newer accepted work with an older standalone correction.

Before replacement, confirm the game/server is stopped and back up/hash-verify the
installed RPG JAR and matching save/mod-data state. Preserve other mods and saves.
Verify the installed SHA-256 against the exact candidate. Retain rollback.
If deployment cannot safely proceed, clearly report **NOT DEPLOYED** and the blocker;
do not stop processes or claim installation without evidence.

Reports must distinguish IMPLEMENTED, PACKAGED, DEPLOYED and CONNECTED-VERIFIED.
Installed bytes and isolated server smoke do not establish live startup, rendering,
input delivery or connected gameplay success. Record outstanding gates honestly.
