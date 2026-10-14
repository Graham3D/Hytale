# Hostile mob manifest — installed Hytale 0.7.1

This is a **source audit**, not a gameplay change. It inventories 204 directly spawnable native roles whose role behavior has a Combat/Attack branch and either an explicit hostile player attitude or a Chapter I Goblin combat role. Another 53 combat roles have no direct production `Server/NPC/Spawn` selector and are listed separately as scripted/conditional encounters. 40 listed roles belong to the Goblin/Feastmaster families. The inspected package is the installed release `Assets.zip` (3,942,663,049 bytes; SHA-256 `B9164EB07D7C42E697D208BA3D35078A9B4A0125BEF6261847A2F91E293E53B4`). The mod comparison is QA-pending `codex/elite-natural-hookup` source commit `1d7e1ab4a3205d27705885da359af20d01e078d9`. This is an asset-defined roster, not an in-game observation of every possible spawn.

**How to read the status.** “Configured” means the native role appears in Hywind's `enemy-registry.json`, so ordinary encounter progression and additional equipment-loot handling have an authored route when admission and player contribution succeed. Native Hytale drops remain native. “No catalog role” means this mod has no authored encounter/reward route for that exact role. “Configured*” means the 0.7.0 production Elite matrix enables both Champion and Unique for that exact role; it is **not a claim of working promotion on 0.7.1**. The latest client log reports all four natural-Elite native hooks missing after the update, so **no listed role is currently verified to promote naturally on 0.7.1**. The old role/asset bindings also require a separate 0.7.1 compatibility review. This table makes the configured coverage gap visible without silently calling new Goblins eligible.

Locations are native production spawn-selector environments or marker families; they indicate where Hytale *can* place a role, not a guaranteed sighting. Test, debug, and deprecated selectors are excluded. Names come from installed `en-US/server.lang` when a role has a translation key. The audit reads `Server/NPC/Roles`, `Server/NPC/Spawn`, and `Server/Languages/en-US/server.lang` in `Assets.zip`; mod status comes from `rpg/progression/enemy-registry.json`, `rpg/spawning/native-population-roles-v1.json`, and `docs/enemies/ELITE_ROLE_MATRIX.md`. A hostile attitude alone is insufficient: passive critters such as frogs are excluded because their behavior flees rather than attacks. Neutral animals that retaliate only after provocation are excluded. Fox and Cactee inherit combat instructions but the earlier Hywind population audit classified them as wildlife; they remain **behavior-review candidates**, not confirmed attackers. Patrol, wander, dungeon, and Coffer variants are separate rows because Hywind binds exact native role IDs.

## Directly spawnable attackers (204)

| In-game name / native role | Native location selector(s) | Hywind combat/loot | Champion & Unique |
| --- | --- | --- | --- |
| Grizzly Bear (`Bear_Grizzly`) | Portal Goblin Surface; Zone1 Autumn; Zone1 Azure; Zone1 Forests; Zone1 Mountains; Zone3 Forests; marker: Bear; marker: Welcome Animal | Configured | Configured* |
| Polar Bear (`Bear_Polar`) | Zone3 Glacial; Zone3 Shores | Configured | Configured* |
| Voidtaken Grizzly (`Bear_Voidtaken`) | Beacons/Bear Voidtaken Beacon | No catalog role | No |
| Undead Chicken (`Chicken_Undead`) | Zone4 Forests | No catalog role | No |
| Undead Cow (`Cow_Undead`) | Zone4 Wastes | Configured | Configured* |
| Void Crawler (`Crawler_Void`) | Beacons/Portals/Portal Void Event Minor; Portal Goblin Cave Void; Zone1 Autumn; Zone1 Azure; Zone1 Forests; Zone1 Mountains; Zone1 Plains; Zone1 Swamps; Zone2 Deserts; Zone2 Oasis; Zone2 Plateaus; Zone2 Savanna; Zone2 Scrub; Zone3 Forests; Zone3 Glacial; Zone3 Mountains; Zone3 Tundra; marker: Void | Configured | Configured* |
| Void Crawler (`Crawler_Void_Anomaly`) | marker: Void | Configured | No |
| Void Crawler (`Crawler_Void_Surge`) | Portal Goblin Cave; Portal Goblin Cave Deep; Portal Goblin Cave Void; Portal Goblin Surface | Configured | No |
| Crocodile (`Crocodile`) | Portals Hedera; Portals Oasis; Zone2 Oasis; marker: Crocodile | Configured | Configured* |
| Feastmaster (`Dungeon_Feastmaster_Boss`) | marker: Dungeon/Goblin | No catalog role | No |
| Goblin Burner (`Dungeon_Goblin_Burner`) | marker: Dungeon/Goblin | No catalog role | No |
| Goblin Burner (`Dungeon_Goblin_Burner_Patrol`) | marker: Dungeon/Goblin | No catalog role | No |
| Goblin Glutton (`Dungeon_Goblin_Glutton`) | marker: Dungeon/Goblin | No catalog role | No |
| Goblin Guardian (`Dungeon_Goblin_Guardian`) | marker: Dungeon/Goblin | No catalog role | No |
| Goblin Lobber (`Dungeon_Goblin_Lobber`) | marker: Dungeon/Goblin | No catalog role | No |
| Goblin Lobber (`Dungeon_Goblin_Lobber_Patrol`) | marker: Dungeon/Goblin | No catalog role | No |
| Goblin Lobber (`Dungeon_Goblin_Lobber_Turret`) | marker: Dungeon/Goblin | No catalog role | No |
| Goblin Guardian Mace (`Dungeon_Goblin_Mace`) | marker: Dungeon/Goblin | No catalog role | No |
| Goblin Miner (`Dungeon_Goblin_Miner`) | marker: Dungeon/Goblin | No catalog role | No |
| Goblin Miner (`Dungeon_Goblin_Miner_Patrol`) | marker: Dungeon/Goblin | No catalog role | No |
| Goblin Scrapper (`Dungeon_Goblin_Scrapper`) | marker: Dungeon/Goblin | No catalog role | No |
| Goblin Scrapper (`Dungeon_Goblin_Scrapper_Enrage`) | marker: Dungeon/Goblin | No catalog role | No |
| Goblin Scrapper (`Dungeon_Goblin_Scrapper_Patrol`) | marker: Dungeon/Goblin | No catalog role | No |
| Goblin Thief (`Dungeon_Goblin_Thief`) | marker: Dungeon/Goblin | No catalog role | No |
| Goblin Thief (`Dungeon_Goblin_Thief_Patrol`) | marker: Dungeon/Goblin | No catalog role | No |
| Goblin Scrapper (`Dungeon_Goblin_Turret`) | marker: Dungeon/Goblin | No catalog role | No |
| Scarak Fighter (`Dungeon_Scarak_Fighter`) | marker: Dungeon Scarak Fighter | Configured | No |
| Scarak Fighter (`Dungeon_Scarak_Fighter_Patrol`) | marker: Dungeon Scarak Fighter Patrol | Configured | No |
| Scarak Seeker (`Dungeon_Scarak_Seeker`) | marker: Dungeon Scarak Seeker | Configured | No |
| Scarak Seeker (`Dungeon_Scarak_Seeker_Patrol`) | marker: Dungeon Scarak Seeker Patrol | Configured | No |
| Sandswept Skeleton Archer (`Dungeon_Skeleton_Sand_Archer`) | marker: Dungeon Skeleton Sand Archer | Configured | No |
| Sandswept Skeleton Assassin (`Dungeon_Skeleton_Sand_Assassin`) | marker: Dungeon Skeleton Sand Assassin | Configured | No |
| Sandswept Skeleton Warlock (`Dungeon_Skeleton_Sand_Mage`) | marker: Dungeon Skeleton Sand Mage | Configured | No |
| Sandswept Skeleton Soldier (`Dungeon_Skeleton_Sand_Soldier`) | marker: Dungeon Skeleton Sand Soldier | Configured | No |
| Emberwulf (`Emberwulf`) | Zone4 Volcanoes | Configured | Configured* |
| Void Eye (`Eye_Void`) | Beacons/Portals/Portal Void Event Major; Zone1 Autumn; Zone1 Azure; Zone1 Forests; Zone1 Mountains; Zone1 Plains; Zone1 Swamps; Zone2 Deserts; Zone2 Oasis; Zone2 Plateaus; Zone2 Savanna; Zone2 Scrub; Zone3 Forests; Zone3 Glacial; Zone3 Mountains; Zone3 Tundra; marker: Void | Configured | Configured* |
| Void Eye (`Eye_Void_Surge`) | Portal Goblin Cave; Portal Goblin Cave Deep; Portal Goblin Cave Void; Portal Goblin Surface | Configured | No |
| Fen Stalker (`Fen_Stalker`) | Zone1 Swamps | Configured | Configured* |
| Goblin Burner (`Goblin_Burner`) | Zone1 Caves Volcanic T1; Zone1 Caves Volcanic T2; Zone1 Caves Volcanic T3; Zone2 Caves Volcanic T1; Zone2 Caves Volcanic T2; Zone2 Caves Volcanic T3; marker: Intelligent/Goblin | Configured | No |
| Goblin Glutton (`Goblin_Glutton`) | marker: Intelligent/Goblin | No catalog role | No |
| Goblin Guardian (`Goblin_Guardian`) | marker: Intelligent/Goblin | Configured | No |
| Goblin Lobber (`Goblin_Lobber`) | Beacons/Goblin Duke Phase 2; Beacons/Goblin Duke Phase 3; Zone1 Caves Volcanic T1; Zone1 Caves Volcanic T2; Zone1 Caves Volcanic T3; Zone2 Caves Volcanic T1; Zone2 Caves Volcanic T2; Zone2 Caves Volcanic T3; marker: Intelligent/Goblin | Configured | No |
| Goblin Lobber (`Goblin_Lobber_Turret`) | marker: Intelligent/Goblin | Configured | No |
| Goblin Miner (`Goblin_Miner`) | Zone1 Caves Forests; Zone1 Caves Plains; Zone1 Caves Swamps; Zone1 Caves Volcanic T1; Zone1 Caves Volcanic T2; Zone1 Caves Volcanic T3; Zone2 Caves Volcanic T1; Zone2 Caves Volcanic T2; Zone2 Caves Volcanic T3; marker: Intelligent/Goblin | Configured | No |
| Goblin Scrapper (`Goblin_Scrapper`) | Beacons/Goblin Duke Phase 1; Beacons/Goblin Duke Phase 2; Beacons/Goblin Duke Phase 3; Portal Goblin Cave; Zone1 Caves Forests; Zone1 Caves Goblins; Zone1 Caves Plains; Zone1 Caves Swamps; Zone1 Caves Volcanic T1; Zone1 Caves Volcanic T2; Zone1 Caves Volcanic T3; Zone2 Caves Deserts; Zone2 Caves Scrub; Zone2 Caves Volcanic T1; Zone2 Caves Volcanic T2; Zone2 Caves Volcanic T3; Zone3 Caves Forests; Zone3 Caves Glacial; Zone3 Caves Tundra; marker: Intelligent/Goblin | Configured | Configured* |
| Goblin Scrapper (`Goblin_Turret`) | Portal Goblin Cave; Zone1 Caves Goblins; marker: Intelligent/Goblin | Configured | No |
| Earthen Golem (`Golem_Crystal_Earth`) | marker: Elemental | Configured | No |
| Ember Golem (`Golem_Crystal_Flame`) | marker: Elemental | Configured | No |
| Frost Golem (`Golem_Crystal_Frost`) | marker: Elemental | Configured | No |
| Sandswept Golem (`Golem_Crystal_Sand`) | marker: Elemental | Configured | No |
| Firesteel Golem (`Golem_Firesteel`) | Zone1 Caves Volcanic T2; Zone1 Caves Volcanic T3; Zone2 Caves Volcanic T2; Zone2 Caves Volcanic T3; Zone4 Caves Volcanic; marker: Elemental | Configured | Configured* |
| The Hedera (`Hedera`) | marker: Marker Hedera | No catalog role | No |
| Bleached Hound (`Hound_Bleached`) | Zone4 Caves; Zone4 Forests | Configured | Configured* |
| Hyena (`Hyena`) | Zone2 Plateaus; Zone2 Savanna; Zone2 Scrub; marker: Hyena | Configured | Configured* |
| Silk Larva (`Larva_Silk`) | Zone1 Caves Forests; Zone1 Caves Spiders; Zone1 Caves Swamps; Zone1 Mineshafts | Configured | No |
| Void Larva (`Larva_Void`) | Beacons/Portals/Portal Void Event Critter; Portal Goblin Cave Void; Zone1 Autumn; Zone1 Azure; Zone1 Forests; Zone1 Mountains; Zone1 Plains; Zone1 Swamps; Zone2 Deserts; Zone2 Oasis; Zone2 Plateaus; Zone2 Savanna; Zone2 Scrub; Zone3 Forests; Zone3 Glacial; Zone3 Mountains; Zone3 Tundra; marker: Void | Configured | Configured* |
| Void Larva (`Larva_Void_Surge`) | Portal Goblin Cave; Portal Goblin Cave Deep; Portal Goblin Cave Void; Portal Goblin Surface | Configured | No |
| Snow Leopard (`Leopard_Snow`) | Zone3 Glacial | Configured | Configured* |
| Molerat (`Molerat`) | Zone1 Caves Forests; Zone1 Caves Swamps; Zone1 Mineshafts | Configured | No |
| Outlander Initiate (`Outlander_Cultist`) | marker: Intelligent/Outlander | Configured | No |
| Outlander Hunter (`Outlander_Hunter`) | Zone3 Outlander; marker: Intelligent/Outlander | Configured | Configured* |
| Outlander Priest (`Outlander_Priest`) | marker: Intelligent/Outlander | Configured | No |
| Outlander Sorcerer (`Outlander_Sorcerer`) | marker: Intelligent/Outlander | Configured | No |
| Outlander Stalker (`Outlander_Stalker`) | marker: Intelligent/Outlander | Configured | No |
| Undead Pig (`Pig_Undead`) | Zone4 Forests | Configured | Configured* |
| Piranha (`Piranha`) | Zone1 Azure; Zone1 Swamps | Configured | Configured* |
| Black Piranha (`Piranha_Black`) | Portals Oasis; Zone1 Azure; Zone1 Swamps; Zone2 Deserts; Zone2 Oasis; Zone3 Caves Volcanic T1; Zone3 Caves Volcanic T2; Zone3 Caves Volcanic T3; marker: Piranha Black | Configured | Configured* |
| Cave Raptor (`Raptor_Cave`) | Zone4 Jungles; marker: Rex Cave | Configured | Configured* |
| Rat (`Rat`) | Portal Goblin Cave; Portal Goblin Cave Deep; Zone1 Caves Forests; Zone1 Caves Goblins; Zone1 Caves Plains; Zone1 Caves Rats; Zone1 Caves Swamps; Zone1 Mineshafts; Zone2 Caves Deserts; Zone2 Caves Rats; Zone2 Caves Savanna; Zone2 Caves Scrub; Zone3 Caves Forests; Zone3 Caves Glacial; Zone3 Caves Tundra; Zone4 Caves; Zone4 Sewers; marker: Dungeon; marker: Rat; marker: Rat Manual | Configured | Configured* |
| Cave Rex (`Rex_Cave`) | Zone4 Jungles; marker: Rex Cave | Configured | Configured* |
| Scarak Fighter (`Scarak_Fighter`) | Zone2 Dungeons; marker: Scarak | Configured | Configured* |
| Scarak Fighter (`Scarak_Fighter_Patrol`) | marker: Scarak Patrol | Configured | No |
| Scarak Seeker (`Scarak_Seeker`) | Zone2 Deserts; Zone2 Plateaus; Zone2 Scrub; marker: Scarak Seeker | Configured | No |
| Scarak Seeker (`Scarak_Seeker_Patrol`) | marker: Scarak Seeker Patrol | Configured | No |
| Scorpion (`Scorpion`) | Zone2 Caves Deserts; Zone2 Caves Scrub; Zone2 Plateaus; Zone2 Scrub; marker: Scorpion | Configured | Configured* |
| Skeleton Archer (`Skeleton_Archer`) | marker: Undead; marker: Undead/Skeleton | Configured | Configured* |
| Skeleton Archer (`Skeleton_Archer_Patrol`) | marker: Undead/Skeleton | Configured | No |
| Skeleton Archer (`Skeleton_Archer_Turret`) | marker: Undead/Skeleton | Configured | No |
| Skeleton Archmage (`Skeleton_Archmage`) | marker: Undead; marker: Undead/Skeleton | Configured | No |
| Skeleton Archmage (`Skeleton_Archmage_Patrol`) | marker: Undead/Skeleton | Configured | No |
| Burnt Skeleton Alchemist (`Skeleton_Burnt_Alchemist`) | Zone4 Sewers; marker: Undead; marker: Undead/Skeleton Burnt | Configured | No |
| Burnt Skeleton Alchemist (`Skeleton_Burnt_Alchemist_Patrol`) | marker: Undead/Skeleton Burnt | Configured | No |
| Burnt Skeleton Archer (`Skeleton_Burnt_Archer`) | marker: Undead; marker: Undead/Skeleton Burnt | Configured | Configured* |
| Burnt Skeleton Archer (`Skeleton_Burnt_Archer_Patrol`) | marker: Undead/Skeleton Burnt | Configured | No |
| Burnt Skeleton Gunner (`Skeleton_Burnt_Gunner`) | Zone4 Volcanoes; marker: Undead; marker: Undead/Skeleton Burnt | Configured | Configured* |
| Burnt Skeleton Gunner (`Skeleton_Burnt_Gunner_Patrol`) | marker: Undead/Skeleton Burnt | Configured | No |
| Burnt Skeleton Knight (`Skeleton_Burnt_Knight`) | marker: Undead; marker: Undead/Skeleton Burnt | Configured | No |
| Burnt Skeleton Knight (`Skeleton_Burnt_Knight_Patrol`) | marker: Undead/Skeleton Burnt | Configured | No |
| Burnt Skeleton Lancer (`Skeleton_Burnt_Lancer`) | Zone4 Wastes; marker: Undead; marker: Undead/Skeleton Burnt | Configured | Configured* |
| Burnt Skeleton Lancer (`Skeleton_Burnt_Lancer_Patrol`) | marker: Undead/Skeleton Burnt | Configured | No |
| Burnt Skeleton Lancer (`Skeleton_Burnt_Lancer_Wander`) | Zone4 Forests; Zone4 Volcanoes | Configured | Configured* |
| Burnt Skeleton Praetorian (`Skeleton_Burnt_Praetorian`) | Zone1 Azure; Zone1 Swamps; marker: Undead; marker: Undead/Skeleton Burnt | Configured | Configured* |
| Burnt Skeleton Praetorian (`Skeleton_Burnt_Praetorian_Patrol`) | marker: Undead/Skeleton Burnt | Configured | No |
| Burnt Skeleton Praetorian (`Skeleton_Burnt_Praetorian_Wander`) | Zone1 Autumn; Zone1 Forests; Zone1 Mountains; Zone4 Shores; Zone4 Wastes | Configured | Configured* |
| Burnt Skeleton Soldier (`Skeleton_Burnt_Soldier`) | Zone4 Sewers; marker: Undead; marker: Undead/Skeleton Burnt | Configured | Configured* |
| Burnt Skeleton Soldier (`Skeleton_Burnt_Soldier_Patrol`) | marker: Undead/Skeleton Burnt | Configured | No |
| Burnt Skeleton Soldier (`Skeleton_Burnt_Soldier_Wander`) | Zone4 Shores; Zone4 Wastes | Configured | Configured* |
| Burnt Skeleton Wizard (`Skeleton_Burnt_Wizard`) | marker: Undead; marker: Undead/Skeleton Burnt | Configured | No |
| Burnt Skeleton Wizard (`Skeleton_Burnt_Wizard_Patrol`) | marker: Undead/Skeleton Burnt | Configured | No |
| Revenant Guardian (`Skeleton_Elite`) | marker: Undead | Configured | No |
| Skeleton Fighter (`Skeleton_Fighter`) | Zone1 Autumn; Zone1 Azure; Zone1 Forests; Zone1 Mineshafts; Zone1 Mountains; Zone1 Plains; Zone1 Swamps; Zone3 Forests; Zone3 Mountains; marker: Undead; marker: Undead/Skeleton | Configured | Configured* |
| Skeleton Fighter (`Skeleton_Fighter_Patrol`) | marker: Undead/Skeleton | Configured | No |
| Skeleton Fighter (`Skeleton_Fighter_Wander`) | Zone1 Autumn; Zone1 Azure; Zone1 Forests; Zone1 Mountains; Zone1 Plains; Zone1 Swamps; Zone3 Forests; Zone3 Mountains | Configured | Configured* |
| Frost Skeleton Archer (`Skeleton_Frost_Archer`) | marker: Undead; marker: Undead/Skeleton Frost | Configured | Configured* |
| Frost Skeleton Archer (`Skeleton_Frost_Archer_Patrol`) | marker: Undead/Skeleton Frost | Configured | Configured* |
| Frost Skeleton Archmage (`Skeleton_Frost_Archmage`) | marker: Undead; marker: Undead/Skeleton Frost | Configured | No |
| Frost Skeleton Archmage (`Skeleton_Frost_Archmage_Patrol`) | marker: Undead/Skeleton Frost | Configured | No |
| Frost Skeleton Fighter (`Skeleton_Frost_Fighter`) | Zone3 Forests; Zone3 Glacial Henges; Zone3 Mountains; marker: Undead; marker: Undead/Skeleton Frost | Configured | Configured* |
| Frost Skeleton Fighter (`Skeleton_Frost_Fighter_Patrol`) | marker: Undead/Skeleton Frost | Configured | No |
| Frost Skeleton Fighter (`Skeleton_Frost_Fighter_Wander`) | Zone3 Forests; Zone3 Mountains | Configured | Configured* |
| Frost Skeleton Knight (`Skeleton_Frost_Knight`) | Zone3 Glacial; marker: Undead; marker: Undead/Skeleton Frost | Configured | Configured* |
| Frost Skeleton Knight (`Skeleton_Frost_Knight_Patrol`) | marker: Undead/Skeleton Frost | Configured | No |
| Frost Skeleton Knight (`Skeleton_Frost_Knight_Wander`) | Zone3 Glacial | Configured | Configured* |
| Frost Skeleton Mage (`Skeleton_Frost_Mage`) | Zone3 Glacial; marker: Undead; marker: Undead/Skeleton Frost | Configured | Configured* |
| Frost Skeleton Mage (`Skeleton_Frost_Mage_Patrol`) | marker: Undead/Skeleton Frost | Configured | No |
| Frost Skeleton Mage (`Skeleton_Frost_Mage_Wander`) | Zone3 Glacial | Configured | Configured* |
| Frost Skeleton Ranger (`Skeleton_Frost_Ranger`) | Zone3 Glacial Henges; marker: Undead; marker: Undead/Skeleton Frost | Configured | Configured* |
| Frost Skeleton Ranger (`Skeleton_Frost_Ranger_Patrol`) | marker: Undead/Skeleton Frost | Configured | Configured* |
| Frost Skeleton Scout (`Skeleton_Frost_Scout`) | marker: Undead; marker: Undead/Skeleton Frost | Configured | No |
| Frost Skeleton Scout (`Skeleton_Frost_Scout_Patrol`) | marker: Undead/Skeleton Frost | Configured | No |
| Frost Skeleton Soldier (`Skeleton_Frost_Soldier`) | Zone3 Caves Volcanic T2; Zone3 Caves Volcanic T3; Zone3 Glacial; Zone3 Glacial Henges; marker: Undead; marker: Undead/Skeleton Frost | Configured | Configured* |
| Frost Skeleton Soldier (`Skeleton_Frost_Soldier_Patrol`) | marker: Undead/Skeleton Frost | Configured | No |
| Frost Skeleton Soldier (`Skeleton_Frost_Soldier_Wander`) | Zone3 Glacial | Configured | Configured* |
| Incandescent Skeleton Fighter (`Skeleton_Incandescent_Fighter`) | marker: Undead; marker: Undead/Skeleton Incandescent | Configured | No |
| Incandescent Skeleton Fighter (`Skeleton_Incandescent_Fighter_Patrol`) | marker: Undead/Skeleton Incandescent | Configured | No |
| Incandescent Skeleton Footman (`Skeleton_Incandescent_Footman`) | marker: Undead; marker: Undead/Skeleton Incandescent | Configured | Configured* |
| Incandescent Skeleton Footman (`Skeleton_Incandescent_Footman_Patrol`) | marker: Undead/Skeleton Incandescent | Configured | No |
| Incandescent Skeleton Footman (`Skeleton_Incandescent_Footman_Wander`) | Zone4 Forests; Zone4 Volcanoes | Configured | Configured* |
| Incandescent Skeleton Head (`Skeleton_Incandescent_Head`) | Zone4 Forests; marker: Undead/Skeleton Incandescent | No catalog role | No |
| Incandescent Skeleton Mage (`Skeleton_Incandescent_Mage`) | marker: Undead; marker: Undead/Skeleton Incandescent | Configured | Configured* |
| Incandescent Skeleton Mage (`Skeleton_Incandescent_Mage_Patrol`) | marker: Undead/Skeleton Incandescent | Configured | No |
| Incandescent Skeleton Mage (`Skeleton_Incandescent_Mage_Wander`) | Zone4 Forests; Zone4 Volcanoes | Configured | Configured* |
| Skeleton Knight (`Skeleton_Knight`) | marker: Undead; marker: Undead/Skeleton | Configured | No |
| Skeleton Knight (`Skeleton_Knight_Patrol`) | marker: Undead/Skeleton | Configured | No |
| Skeleton Mage (`Skeleton_Mage`) | Zone1 Azure; Zone1 Mineshafts; Zone1 Swamps; marker: Undead; marker: Undead/Skeleton | Configured | Configured* |
| Skeleton Mage (`Skeleton_Mage_Patrol`) | marker: Undead/Skeleton | Configured | No |
| Skeleton Mage (`Skeleton_Mage_Wander`) | Zone1 Azure; Zone1 Swamps | Configured | Configured* |
| Skeleton Pirate Captain (`Skeleton_Pirate_Captain`) | marker: Undead; marker: Undead/Skeleton Pirate | Configured | No |
| Skeleton Pirate Captain (`Skeleton_Pirate_Captain_Patrol`) | marker: Undead/Skeleton Pirate | Configured | No |
| Skeleton Pirate Gunner (`Skeleton_Pirate_Gunner`) | marker: Undead; marker: Undead/Skeleton Pirate | Configured | No |
| Skeleton Pirate Gunner (`Skeleton_Pirate_Gunner_Patrol`) | marker: Undead/Skeleton Pirate | Configured | No |
| Skeleton Pirate Striker (`Skeleton_Pirate_Striker`) | marker: Undead; marker: Undead/Skeleton Pirate | Configured | No |
| Skeleton Pirate Striker (`Skeleton_Pirate_Striker_Patrol`) | marker: Undead/Skeleton Pirate | Configured | No |
| Skeleton Ranger (`Skeleton_Ranger`) | marker: Undead; marker: Undead/Skeleton | Configured | No |
| Skeleton Ranger (`Skeleton_Ranger_Patrol`) | marker: Undead/Skeleton | Configured | No |
| Sandswept Skeleton Archer (`Skeleton_Sand_Archer`) | marker: Undead; marker: Undead/Skeleton Sand | Configured | No |
| Sandswept Skeleton Archer (`Skeleton_Sand_Archer_Patrol`) | marker: Undead/Skeleton Sand | Configured | No |
| Sandswept Skeleton Archmage (`Skeleton_Sand_Archmage`) | marker: Undead; marker: Undead/Skeleton Sand | Configured | No |
| Sandswept Skeleton Archmage (`Skeleton_Sand_Archmage_Patrol`) | marker: Undead/Skeleton Sand | Configured | No |
| Sandswept Skeleton Assassin (`Skeleton_Sand_Assassin`) | Zone2 Deserts; Zone2 Oasis; Zone2 Plateaus; Zone2 Savanna; Zone2 Scrub; marker: Undead; marker: Undead/Skeleton Sand | Configured | Configured* |
| Sandswept Skeleton Assassin (`Skeleton_Sand_Assassin_Patrol`) | marker: Undead/Skeleton Sand | Configured | No |
| Sandswept Skeleton Assassin (`Skeleton_Sand_Assassin_Wander`) | Zone2 Deserts; Zone2 Oasis; Zone2 Plateaus; Zone2 Savanna; Zone2 Scrub | Configured | Configured* |
| Sandswept Skeleton Guard (`Skeleton_Sand_Guard`) | Zone2 Deserts; Zone2 Oasis; Zone2 Savanna; marker: Undead; marker: Undead/Skeleton Sand | Configured | Configured* |
| Sandswept Skeleton Guard (`Skeleton_Sand_Guard_Patrol`) | marker: Undead/Skeleton Sand | Configured | No |
| Sandswept Skeleton Guard (`Skeleton_Sand_Guard_Wander`) | Zone2 Deserts; Zone2 Oasis; Zone2 Plateaus; Zone2 Savanna; Zone2 Scrub | Configured | Configured* |
| Sandswept Skeleton Mage (`Skeleton_Sand_Mage`) | Zone2 Deserts; Zone2 Oasis; marker: Undead; marker: Undead/Skeleton Sand | Configured | Configured* |
| Sandswept Skeleton Mage (`Skeleton_Sand_Mage_Patrol`) | marker: Undead/Skeleton Sand | Configured | No |
| Sandswept Skeleton Mage (`Skeleton_Sand_Mage_Wander`) | Zone2 Deserts; Zone2 Oasis | Configured | Configured* |
| Sandswept Skeleton Ranger (`Skeleton_Sand_Ranger`) | marker: Undead; marker: Undead/Skeleton Sand | Configured | No |
| Sandswept Skeleton Ranger (`Skeleton_Sand_Ranger_Patrol`) | marker: Undead/Skeleton Sand | Configured | No |
| Sandswept Skeleton Scout (`Skeleton_Sand_Scout`) | marker: Undead; marker: Undead/Skeleton Sand | Configured | No |
| Sandswept Skeleton Scout (`Skeleton_Sand_Scout_Patrol`) | marker: Undead/Skeleton Sand | Configured | No |
| Sandswept Skeleton Soldier (`Skeleton_Sand_Soldier`) | marker: Undead; marker: Undead/Skeleton Sand | Configured | No |
| Sandswept Skeleton Soldier (`Skeleton_Sand_Soldier_Patrol`) | marker: Undead/Skeleton Sand | Configured | No |
| Skeleton Scout (`Skeleton_Scout`) | Zone3 Tundra; marker: Undead; marker: Undead/Skeleton | Configured | Configured* |
| Skeleton Scout (`Skeleton_Scout_Patrol`) | marker: Undead/Skeleton | Configured | Configured* |
| Skeleton Scout (`Skeleton_Scout_Wander`) | Zone3 Tundra | Configured | Configured* |
| Skeleton Soldier (`Skeleton_Soldier`) | Zone1 Mineshafts; marker: Undead; marker: Undead/Skeleton | Configured | Configured* |
| Skeleton Soldier (`Skeleton_Soldier_Patrol`) | marker: Undead/Skeleton | Configured | No |
| Magma Slug (`Slug_Magma`) | Zone4 Volcanoes; marker: Slug Magma | Configured | Configured* |
| Cobra (`Snake_Cobra`) | Zone2 Caves Deserts; Zone2 Caves Savanna; Zone2 Caves Scrub; Zone3 Forests; Zone3 Tundra; marker: Snakes | Configured | Configured* |
| Marsh Snake (`Snake_Marsh`) | Portals Hedera; Zone1 Swamps | Configured | Configured* |
| Rattlesnake (`Snake_Rattle`) | Zone2 Scrub; marker: Snake Rattle; marker: Snakes | Configured | Configured* |
| Snapdragon (`Snapdragon`) | Zone4 Jungles | Configured | Configured* |
| Spider (`Spider`) | Zone1 Autumn; Zone1 Azure; Zone1 Forests | Configured | Configured* |
| Cave Spider (`Spider_Cave`) | Zone1 Caves Forests; Zone1 Caves Spiders; Zone1 Caves Swamps; Zone1 Mineshafts; Zone3 Caves Forests; Zone3 Caves Glacial; marker: Instance/Dungeon Goblin; marker: Spider Cave; marker: Spider Cave Manual | Configured | No |
| Cave Spider (`Spider_Cave_Ambush`) | marker: Spider Cave Ambush; marker: Spider Cave Ambush Manual | Configured | No |
| Cave Spider (`Spider_Cave_Spiderling`) | marker: Spider Cave Spiderling | No catalog role | No |
| Frost Spirit (`Spirit_Frost`) | Beacons/Zone3/Zone3 Elemental Circle Tier1; Beacons/Zone3/Zone3 Elemental Circle Tier2; Beacons/Zone3/Zone3 Elemental Circle Tier3 | Configured | No |
| Root Spirit (`Spirit_Root`) | Beacons/Zone1/Zone1 Elemental Circle Tier1; Beacons/Zone1/Zone1 Elemental Circle Tier2; Beacons/Zone1/Zone1 Elemental Circle Tier3; marker: Spirit Root; marker: Spirit Root Manual | Configured | No |
| Thunder Spirit (`Spirit_Thunder`) | Beacons/Zone2/Zone2 Elemental Circle Tier1; Beacons/Zone2/Zone2 Elemental Circle Tier2; Beacons/Zone2/Zone2 Elemental Circle Tier3 | Configured | No |
| Sabertooth Tiger (`Tiger_Sabertooth`) | Portals Oasis; Zone2 Deserts; Zone2 Oasis; Zone2 Savanna | Configured | Configured* |
| Rhino Toad (`Toad_Rhino`) | Zone4 Wastes; marker: Toad Rhino | Configured | Configured* |
| Magma Rhino Toad (`Toad_Rhino_Magma`) | Zone1 Caves Volcanic T1; Zone1 Caves Volcanic T2; Zone1 Caves Volcanic T3; Zone2 Caves Volcanic T1; Zone2 Caves Volcanic T2; Zone2 Caves Volcanic T3; Zone4 Caves Volcanic; Zone4 Volcanoes; marker: Toad Rhino Magma | Configured | Configured* |
| Trork Elder (`Trork_Doctor_Witch`) | marker: Intelligent/Trork | Configured | No |
| Trork Guard (`Trork_Guard`) | marker: Intelligent/Trork | Configured | No |
| Trork Hunter (`Trork_Hunter`) | marker: Intelligent/Trork | Configured | No |
| Trork Sentry (`Trork_Sentry`) | marker: Intelligent/Trork | Configured | No |
| Trork Elder (`Trork_Shaman`) | marker: Intelligent/Trork | Configured | No |
| Void Fiend (`Void_Spawn_Entombed`) | marker: Void | Configured | No |
| Void Fiend (`Void_Spawn_Entombed_Anomaly`) | marker: Void | Configured | No |
| Void Fiend (`Void_Spawn_Surge`) | Portal Goblin Cave; Portal Goblin Cave Deep; Portal Goblin Cave Void; Portal Goblin Surface | Configured | No |
| Void Spectre (`Void_Spectre`) | Zone1 Azure; Zone1 Swamps; Zone2 Deserts; Zone2 Oasis; Zone3 Glacial | Configured | Configured* |
| Void Spectre (`Void_Spectre_Static`) | marker: Void | No catalog role | No |
| Voidtaken Goblin (`Voidtaken_Goblin_Surge`) | Portal Goblin Cave; Portal Goblin Cave Deep; Portal Goblin Cave Void; Portal Goblin Surface | Configured | No |
| Black Wolf (`Wolf_Black`) | Zone1 Plains; marker: Wolf Black | Configured | Configured* |
| White Wolf (`Wolf_White`) | Zone3 Glacial; Zone3 Mountains; marker: Wolf White | Configured | Configured* |
| Wraith (`Wraith`) | Zone4 Wastes; marker: Undead | Configured | Configured* |
| Wraith's Lantern (`Wraith_Lantern`) | marker: Undead | Configured | No |
| Yeti (`Yeti`) | marker: Yeti | Configured | No |
| Zombie (`Zombie`) | Zone4 Sewers; marker: Undead | Configured | No |
| Burnt Zombie (`Zombie_Burnt`) | Zone4 Caves; Zone4 Volcanoes | Configured | Configured* |
| Frost Zombie (`Zombie_Frost`) | marker: Undead | Configured | No |
| Sandswept Zombie (`Zombie_Sand`) | marker: Undead | Configured | No |

## Scripted or conditional combat roles without a direct native spawn selector (53)

These roles may be created by encounters, transitions, or code. Their role assets contain combat behavior, but this audit did not find a direct `Server/NPC/Spawn` entry; their location is therefore intentionally left unasserted.

| In-game name / native role | Native location selector(s) | Hywind combat/loot | Champion & Unique |
| --- | --- | --- | --- |
| Goblin Burner (`Coffer_Goblin_Burner`) | No direct native spawn selector found | Configured | No |
| Goblin Glutton (`Coffer_Goblin_Glutton`) | No direct native spawn selector found | No catalog role | No |
| Goblin Guardian (`Coffer_Goblin_Guardian`) | No direct native spawn selector found | Configured | No |
| Goblin Lobber (`Coffer_Goblin_Lobber`) | No direct native spawn selector found | Configured | No |
| Goblin Miner (`Coffer_Goblin_Miner`) | No direct native spawn selector found | Configured | No |
| Goblin Scrapper (`Coffer_Goblin_Scrapper`) | No direct native spawn selector found | Configured | No |
| Goblin Scrapper (`Coffer_Goblin_Turret`) | No direct native spawn selector found | Configured | No |
| Goblin Scrapper (`Dungeon_Feastmaster_Scrapper`) | No direct native spawn selector found | No catalog role | No |
| Ghoul (`Ghoul`) | No direct native spawn selector found | Configured | No |
| Gobliterator MkIIII (`Goblin_Gobliterator_Phase1`) | No direct native spawn selector found | No catalog role | No |
| Gobliterator MkIIII Final (`Goblin_Gobliterator_Phase1_HM`) | No direct native spawn selector found | No catalog role | No |
| Gobliterator MkIIII (`Goblin_Gobliterator_Phase2`) | No direct native spawn selector found | No catalog role | No |
| Gobliterator MkIIII Final (`Goblin_Gobliterator_Phase2_HM`) | No direct native spawn selector found | No catalog role | No |
| Gobliterator MkIIII (`Goblin_Gobliterator_Phase3`) | No direct native spawn selector found | No catalog role | No |
| Gobliterator MkIIII Final (`Goblin_Gobliterator_Phase3_HM`) | No direct native spawn selector found | No catalog role | No |
| Thunder Golem (`Golem_Crystal_Thunder`) | No direct native spawn selector found | Configured | No |
| Scarak Imperial Guard (`Scarak_Fighter_Royal_Guard`) | No direct native spawn selector found | Configured | No |
| Shadow Knight (`Shadow_Knight`) | No direct native spawn selector found | Configured | No |
| Skeleton (`Skeleton`) | No direct native spawn selector found | No catalog role | No |
| Skeleton Archer (`Skeleton_Archer_Wander`) | No direct native spawn selector found | Configured | Configured* |
| Skeleton Archmage (`Skeleton_Archmage_Wander`) | No direct native spawn selector found | Configured | No |
| Burnt Skeleton Alchemist (`Skeleton_Burnt_Alchemist_Wander`) | No direct native spawn selector found | Configured | No |
| Burnt Skeleton Archer (`Skeleton_Burnt_Archer_Wander`) | No direct native spawn selector found | Configured | Configured* |
| Burnt Skeleton Gunner (`Skeleton_Burnt_Gunner_Wander`) | No direct native spawn selector found | Configured | No |
| Burnt Skeleton Knight (`Skeleton_Burnt_Knight_Wander`) | No direct native spawn selector found | Configured | No |
| Burnt Skeleton Wizard (`Skeleton_Burnt_Wizard_Wander`) | No direct native spawn selector found | Configured | No |
| Frost Skeleton Archer (`Skeleton_Frost_Archer_Wander`) | No direct native spawn selector found | Configured | Configured* |
| Frost Skeleton Archmage (`Skeleton_Frost_Archmage_Wander`) | No direct native spawn selector found | Configured | No |
| Frost Skeleton Ranger (`Skeleton_Frost_Ranger_Wander`) | No direct native spawn selector found | Configured | Configured* |
| Frost Skeleton Scout (`Skeleton_Frost_Scout_Wander`) | No direct native spawn selector found | Configured | No |
| Incandescent Skeleton Fighter (`Skeleton_Incandescent_Fighter_Wander`) | No direct native spawn selector found | Configured | No |
| Skeleton Knight (`Skeleton_Knight_Wander`) | No direct native spawn selector found | Configured | No |
| Skeleton Pirate Captain (`Skeleton_Pirate_Captain_Wander`) | No direct native spawn selector found | Configured | No |
| Skeleton Pirate Gunner (`Skeleton_Pirate_Gunner_Wander`) | No direct native spawn selector found | Configured | No |
| Skeleton Pirate Striker (`Skeleton_Pirate_Striker_Wander`) | No direct native spawn selector found | Configured | No |
| Skeleton Ranger (`Skeleton_Ranger_Wander`) | No direct native spawn selector found | Configured | No |
| Sandswept Skeleton Archer (`Skeleton_Sand_Archer_Wander`) | No direct native spawn selector found | Configured | No |
| Sandswept Skeleton Archmage (`Skeleton_Sand_Archmage_Wander`) | No direct native spawn selector found | Configured | No |
| Sandswept Skeleton Ranger (`Skeleton_Sand_Ranger_Wander`) | No direct native spawn selector found | Configured | No |
| Sandswept Skeleton Scout (`Skeleton_Sand_Scout_Wander`) | No direct native spawn selector found | Configured | No |
| Sandswept Skeleton Soldier (`Skeleton_Sand_Soldier_Wander`) | No direct native spawn selector found | Configured | No |
| Skeleton Soldier (`Skeleton_Soldier_Wander`) | No direct native spawn selector found | Configured | Configured* |
| Ember Spirit (`Spirit_Ember`) | No direct native spawn selector found | Configured | No |
| Trork Sentry (`Trork_Sentry_Patrol`) | No direct native spawn selector found | Configured | No |
| Void Fiend (`Void_Spawn`) | No direct native spawn selector found | No catalog role | No |
| Werewolf (`Werewolf`) | No direct native spawn selector found | Configured | No |
| Hunting Wolf (`Wolf_Outlander_Priest`) | No direct native spawn selector found | Configured | No |
| Hunting Wolf (`Wolf_Outlander_Sorcerer`) | No direct native spawn selector found | Configured | No |
| Hunting Wolf (`Wolf_Trork_Hunter`) | No direct native spawn selector found | Configured | No |
| Hunting Wolf (`Wolf_Trork_Shaman`) | No direct native spawn selector found | Configured | No |
| Aberrant Zombie (`Zombie_Aberrant`) | No direct native spawn selector found | Configured | No |
| Large Aberrant Zombie (`Zombie_Aberrant_Big`) | No direct native spawn selector found | Configured | No |
| Small Aberrant Zombie (`Zombie_Aberrant_Small`) | No direct native spawn selector found | Configured | No |

## Audit findings

- 24 directly spawnable attackers have no exact Hywind encounter-catalog role. New Chapter I Goblin variants are prominent in this gap. A native drop list in the game is not evidence of Hywind equipment loot.
- The Champion/Unique column is authored **0.7.0** eligibility only. Current 0.7.1 connected logs show `RPG_NATURAL_ELITE_HOOKS_MISSING`; promotion needs renewed native-hook and binding verification before QA can establish actual eligibility.
- The generated rows are exact native roles, so names sharing a player-facing translation may appear several times. This is intentional: their spawn context and mod coverage can differ.
- Fox and Cactee are omitted from the confirmed list because inherited combat instructions alone do not establish attack-on-sight behavior; the earlier population audit classifies them as wildlife. Additional neutral or unspecified-attitude combat roles may be reactive or scripted. Connected QA would be needed to resolve ambiguous behavior. This manifest is exhaustive for the stated asset classification rule, not proof that every installed NPC has been witnessed in-game.
