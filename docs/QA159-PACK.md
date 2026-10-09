# QA159 connected gear pack — R195

17 distinct legal carriers; 159 unique functional affixes. WA-155 excluded. Six items have ten affixes; eleven have nine.

The approved 17-item adjustment preserves the thirteen mutually exclusive skill-rank affixes on thirteen held items plus four armor pieces.

Rolls use the upper legal endpoint of the first legal tier, with the native source window, requirements and selector restrictions retained. Legendary is a QA carrier quality, not a new random loot outcome.

## Fixtures

- `qa159-01 gm.plate_iron.head.h native=Armor_Iron_Head gm.plate_iron.head/HEAD 2x2 [WA-012, WA-075, WA-078, WA-079, WA-080, WA-090, WA-092, WA-098, WA-151, WA-154] Sentinel=ELIGIBLE (per-affix disposition in trace)`
  Requirements: Gate[level=54, attributes={STR=134, INT=10, WIS=10, LUCK=10}]
- `qa159-02 gm.robes_oracle.chest.h native=Armor_Cloth_Silk_Chest gm.robes_oracle.chest/CHEST 2x3 [GA-160, WA-073, WA-087, WA-093, WA-099, WA-101, WA-104, WA-105, WA-110, WA-114] Sentinel=ELIGIBLE (per-affix disposition in trace)`
  Requirements: Gate[level=62, attributes={DEX=10, WIS=137}]
- `qa159-03 gm.cloth_cinder.hands.h native=Armor_Cloth_Cindercloth_Hands gm.cloth_cinder.hands/HANDS 2x2 [WA-006, WA-007, WA-009, WA-013, WA-024, WA-074, WA-081, WA-088, WA-091, WA-153] Sentinel=ELIGIBLE (per-affix disposition in trace)`
  Requirements: Gate[level=68, attributes={WIS=10, INT=150, STR=10}]
- `qa159-04 gm.leather_raven.legs.h native=Armor_Leather_Raven_Legs gm.leather_raven.legs/LEGS 2x3 [GA-159, WA-072, WA-076, WA-077, WA-089, WA-100, WA-102, WA-111, WA-152, WA-156] Sentinel=ELIGIBLE (per-affix disposition in trace)`
  Requirements: Gate[level=62, attributes={WIS=10, LUCK=10, DEX=147}]
- `qa159-05 gm.sword_copper.h native=Weapon_Sword_Copper gm.sword_copper/HELD 1x4 [WA-001, WA-005, WA-025, WA-036, WA-052, WA-061, WA-066, WA-094, WA-124, WA-146] Sentinel=ELIGIBLE (per-affix disposition in trace)`
  Requirements: Gate[level=56, attributes={INT=10, DEX=170}]
- `qa159-06 gm.battleaxe_iron.h native=Weapon_Battleaxe_Iron gm.battleaxe_iron/HELD 2x4 [WA-002, WA-034, WA-037, WA-051, WA-059, WA-068, WA-071, WA-127, WA-135, WA-137] Sentinel=ELIGIBLE (per-affix disposition in trace)`
  Requirements: Gate[level=56, attributes={INT=10, STR=180}]
- `qa159-07 gm.mace_thorium.h native=Weapon_Mace_Thorium gm.mace_thorium/HELD 2x4 [WA-010, WA-014, WA-033, WA-040, WA-045, WA-056, WA-123, WA-136, WA-157] Sentinel=ELIGIBLE (per-affix disposition in trace)`
  Requirements: Gate[level=58, attributes={DEX=10, STR=195}]
- `qa159-08 gm.daggers_adamantite.h native=Weapon_Daggers_Adamantite gm.daggers_adamantite/HELD 1x2 [WA-018, WA-027, WA-029, WA-038, WA-041, WA-057, WA-129, WA-141, WA-145] Sentinel=ELIGIBLE (per-affix disposition in trace)`
  Requirements: Gate[level=68, attributes={INT=10, DEX=230}]
- `qa159-09 gm.daggers_cobalt.h native=Weapon_Daggers_Cobalt gm.daggers_cobalt/HELD 1x2 [WA-043, WA-055, WA-064, WA-082, WA-096, WA-097, WA-126, WA-138, WA-158] Sentinel=ELIGIBLE (per-affix disposition in trace)`
  Requirements: Gate[level=62, attributes={DEX=210, STR=10}]
- `qa159-10 gm.shortbow_cobalt.h native=Weapon_Shortbow_Cobalt gm.shortbow_cobalt/HELD 2x4 [WA-008, WA-017, WA-023, WA-031, WA-035, WA-049, WA-065, WA-122, WA-134] Sentinel=ELIGIBLE (per-affix disposition in trace)`
  Requirements: Gate[level=62, attributes={INT=10, DEX=210}]
- `qa159-11 gm.crossbow_heavy.h native=Weapon_Crossbow_Ancient_Steel gm.crossbow_heavy/HELD 2x2 [WA-015, WA-019, WA-026, WA-050, WA-062, WA-085, WA-095, WA-133, WA-139] Sentinel=ELIGIBLE (per-affix disposition in trace)`
  Requirements: Gate[level=68, attributes={INT=10, DEX=230, STR=58}]
- `qa159-12 gm.longsword_adamantite.h native=Weapon_Longsword_Adamantite gm.longsword_adamantite/HELD 2x4 [WA-021, WA-030, WA-039, WA-046, WA-053, WA-125, WA-140, WA-143, WA-144] Sentinel=ELIGIBLE (per-affix disposition in trace)`
  Requirements: Gate[level=68, attributes={STR=230}]
- `qa159-13 gm.shield_mithril.h native=Weapon_Shield_Mithril gm.shield_mithril/HELD 4x4 [WA-047, WA-069, WA-070, WA-083, WA-084, WA-132, WA-142, WA-147, WA-149] Sentinel=ELIGIBLE (per-affix disposition in trace)`
  Requirements: Gate[level=74, attributes={WIS=68, STR=213}]
- `qa159-14 gm.staff_flame.h native=Weapon_Staff_Crystal_Red gm.staff_flame/HELD 1x4 [WA-004, WA-048, WA-058, WA-107, WA-109, WA-112, WA-116, WA-117, WA-128] Sentinel=ELIGIBLE (per-affix disposition in trace)`
  Requirements: Gate[level=56, attributes={INT=180, WIS=10}]
- `qa159-15 gm.staff_healing.h native=Weapon_Staff_Wizard gm.staff_healing/HELD 2x4 [WA-022, WA-060, WA-086, WA-103, WA-118, WA-119, WA-120, WA-131, WA-148] Sentinel=ELIGIBLE (per-affix disposition in trace)`
  Requirements: Gate[level=72, attributes={WIS=160}]
- `qa159-16 gm.book_binder.h native=Weapon_Spellbook_Grimoire_Purple gm.book_binder/HELD 1x2 [WA-011, WA-020, WA-028, WA-044, WA-054, WA-108, WA-113, WA-121, WA-150] Sentinel=ELIGIBLE (per-affix disposition in trace)`
  Requirements: Gate[level=76, attributes={WIS=71, INT=210, DEX=10}]
- `qa159-17 gm.wand_crystal.h native=Weapon_Wand_Wood_Rotten gm.wand_crystal/HELD 2x4 [WA-003, WA-016, WA-032, WA-042, WA-063, WA-067, WA-106, WA-115, WA-130] Sentinel=ELIGIBLE (per-affix disposition in trace)`
  Requirements: Gate[level=58, attributes={WIS=44, INT=195, DEX=10}]

## Per-affix test matrix

EXPECTED means a check is applicable, not that connected QA has passed. NOT_APPLICABLE is not a failure. OWNER_ONLY/INAPPLICABLE source affixes must remain excluded from bound-item inheritance; their rejection is itself observable in the Sentinel snapshot. Conditional, block, status, cleanse, corpse and minion effects need their real trigger; a tooltip is not proof.

| Fixture | Affix | Name | Roll | UI stats | Gameplay | Sentinel inheritance | Disposition | Stat field / runtime owner |
|---|---|---|---|---|---|---|---|---|
| qa159-01 | WA-012 | of Readiness | 3.2 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | cooldown / src/main/java/com/inigmasgames/hytalerpg/execution/SkillExecutionService.java |
| qa159-01 | WA-075 | of Stone Warding | 9.6 | EXPECTED | EXPECTED | EXPECTED | INHERITED | resist by channel / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-01 | WA-078 | of Prismatic Warding | 2.4 | EXPECTED | EXPECTED | EXPECTED | INHERITED | resist by channel / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-01 | WA-079 | of Resolve | 2.4 | EXPECTED | EXPECTED | EXPECTED | INHERITED | statusResistance / src/main/java/com/inigmasgames/hytalerpg/combat/status/GearStatusRuntime.java |
| qa159-01 | WA-080 | of Recovery | 3.6 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/status/StatusService.java |
| qa159-01 | WA-090 | of Balance | 1.0 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | heavy / src/main/java/com/inigmasgames/hytalerpg/gear/GearEquipmentResolution.java |
| qa159-01 | WA-092 | Reservoir | 8.4 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | mana / src/main/java/com/inigmasgames/hytalerpg/gear/GearEquipmentResolution.java |
| qa159-01 | WA-098 | of Triumph | 0.4 | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/resource/GearRecoveryRuntime.java |
| qa159-01 | WA-151 | of Discovery | 7.2 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | find / src/main/java/com/inigmasgames/hytalerpg/gear/GearLootService.java |
| qa159-01 | WA-154 | of Ease | 7.2 | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearEquipmentResolution.java |
| qa159-02 | GA-160 | Fortified | 12.0 | EXPECTED | EXPECTED | EXPECTED | INHERITED | defense / src/main/java/com/inigmasgames/hytalerpg/gear/HytaleGearEquipment.java |
| qa159-02 | WA-073 | of Tides Warding | 9.6 | EXPECTED | EXPECTED | EXPECTED | INHERITED | resist by channel / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-02 | WA-087 | of Insight | 4.0 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | magic / src/main/java/com/inigmasgames/hytalerpg/gear/GearEquipmentResolution.java |
| qa159-02 | WA-093 | Enduring | 9.0 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | stamina / src/main/java/com/inigmasgames/hytalerpg/gear/GearEquipmentResolution.java |
| qa159-02 | WA-099 | of Flow | 6.0 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | manaRegen / src/main/java/com/inigmasgames/hytalerpg/combat/resource/RpgResourceService.java |
| qa159-02 | WA-101 | of Economy | 2.4 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | manaCost / src/main/java/com/inigmasgames/hytalerpg/combat/resource/RpgResourceService.java |
| qa159-02 | WA-104 | Benevolent | 6.0 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | healingDone / src/main/java/com/inigmasgames/hytalerpg/execution/support/SupportRuntime.java |
| qa159-02 | WA-105 | Sheltering | 6.0 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | barrierStrength / src/main/java/com/inigmasgames/hytalerpg/execution/support/FiniteSupportEffects.java |
| qa159-02 | WA-110 | of Continuance | 5.0 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | supportDuration / src/main/java/com/inigmasgames/hytalerpg/execution/support/FiniteSupportEffects.java |
| qa159-02 | WA-114 | Stalwart | 7.2 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | minionHealth / src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSummonSystem.java |
| qa159-03 | WA-006 | Relentless | 6.0 | EXPECTED | EXPECTED | EXPECTED | INHERITED | physicalDamage / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-03 | WA-007 | Prismatic | 5.6 | EXPECTED | EXPECTED | EXPECTED | INHERITED | damage by channel / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-03 | WA-009 | of Invocation | 3.6 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | cast / src/main/java/com/inigmasgames/hytalerpg/execution/SkillExecutionService.java |
| qa159-03 | WA-013 | Malignant | 7.2 | EXPECTED | EXPECTED | EXPECTED | INHERITED | dotDamage / src/main/java/com/inigmasgames/hytalerpg/combat/status/GearStatusRuntime.java |
| qa159-03 | WA-024 | Glacial | 7.2 | EXPECTED | EXPECTED | EXPECTED | INHERITED | damageChannel / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-03 | WA-074 | of Embers Warding | 9.6 | EXPECTED | EXPECTED | EXPECTED | INHERITED | resist by channel / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-03 | WA-081 | of Surefooting | 5.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/status/StatusService.java |
| qa159-03 | WA-088 | of Wisdom | 4.0 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | healing / src/main/java/com/inigmasgames/hytalerpg/gear/GearEquipmentResolution.java |
| qa159-03 | WA-091 | Vital | 16.8 | EXPECTED | EXPECTED | EXPECTED | INHERITED | health / src/main/java/com/inigmasgames/hytalerpg/gear/GearEquipmentResolution.java |
| qa159-03 | WA-153 | of Lasting Craft | 12.0 | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/NativeAffixDurabilitySystems.java |
| qa159-04 | GA-159 | Laminated | 0.9 | EXPECTED | EXPECTED | EXPECTED | INHERITED | defense / src/main/java/com/inigmasgames/hytalerpg/gear/HytaleGearEquipment.java |
| qa159-04 | WA-072 | of Gales Warding | 9.6 | EXPECTED | EXPECTED | EXPECTED | INHERITED | resist by channel / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-04 | WA-076 | of Storms Warding | 9.6 | EXPECTED | EXPECTED | EXPECTED | INHERITED | resist by channel / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-04 | WA-077 | of the Void Warding | 9.6 | EXPECTED | EXPECTED | EXPECTED | INHERITED | resist by channel / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-04 | WA-089 | of Fortune | 4.0 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | critical / src/main/java/com/inigmasgames/hytalerpg/gear/GearEquipmentResolution.java |
| qa159-04 | WA-100 | of Endurance | 6.0 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | staminaRegen / src/main/java/com/inigmasgames/hytalerpg/combat/resource/RpgResourceService.java |
| qa159-04 | WA-102 | of Conservation | 2.4 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | staminaCost / src/main/java/com/inigmasgames/hytalerpg/combat/resource/RpgResourceService.java |
| qa159-04 | WA-111 | of Receptivity | 3.2 | EXPECTED | EXPECTED | EXPECTED | INHERITED | healingReceived / src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSummonSystem.java |
| qa159-04 | WA-152 | of Prosperity | 12.0 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | currencyQuantity / src/main/java/com/inigmasgames/hytalerpg/progress/EncounterContributions.java |
| qa159-04 | WA-156 | of Gathering | 0.4 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | pickupReachBonus / src/main/java/com/inigmasgames/hytalerpg/gear/NativeAffixMaterialPickup.java |
| qa159-05 | WA-001 | Honed | 4.8 | EXPECTED | EXPECTED | EXPECTED | INHERITED | weapon/critical presentation / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-05 | WA-005 | Soldier’s | 14.4 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-05 | WA-025 | Ember | 14.4 | EXPECTED | EXPECTED | EXPECTED | INHERITED | damageChannel / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-05 | WA-036 | Glacial-Forged | 12.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-05 | WA-052 | Giant-Hunting | 9.6 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/hytale/HytaleConditionalDamage.java |
| qa159-05 | WA-061 | of Dimming | 4.8 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/status/GearStatusRuntime.java |
| qa159-05 | WA-066 | Toxic | 14.4 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/status/GearStatusRuntime.java |
| qa159-05 | WA-094 | of Reaping | 1.6 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/resource/GearRecoveryRuntime.java |
| qa159-05 | WA-124 | Glacial Sage’s | 1.0 | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/execution/GearSkillRanks.java |
| qa159-05 | WA-146 | Rimecalling | 4.8 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/ItemSkillTriggerRuntime.java |
| qa159-06 | WA-002 | Brutal | 36.0 | EXPECTED | EXPECTED | EXPECTED | INHERITED | weapon/critical presentation / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-06 | WA-034 | of Piercing the Void | 4.8 | EXPECTED | EXPECTED | EXPECTED | INHERITED | penetrationChannel / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-06 | WA-037 | Ember-Forged | 12.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-06 | WA-051 | Blood-Seeking | 11.2 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/hytale/HytaleConditionalDamage.java |
| qa159-06 | WA-059 | of Concussion | 4.8 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/status/GearStatusRuntime.java |
| qa159-06 | WA-068 | of Persistence | 12.0 | EXPECTED | EXPECTED | EXPECTED | INHERITED | statusDuration / src/main/java/com/inigmasgames/hytalerpg/combat/status/GearStatusRuntime.java |
| qa159-06 | WA-071 | of Guarding | 7.2 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/HytaleGearEquipment.java |
| qa159-06 | WA-127 | Voltaic Sage’s | 1.0 | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/execution/GearSkillRanks.java |
| qa159-06 | WA-135 | Crushing | 4.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearSignatureProcRuntime.java |
| qa159-06 | WA-137 | Barbed | 10.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearSignatureProcRuntime.java |
| qa159-07 | WA-010 | of Precision | 2.0 | EXPECTED | EXPECTED | EXPECTED | INHERITED | critical / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-07 | WA-014 | of Extension | 0.2 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/NativePrimaryActionAssets.java |
| qa159-07 | WA-033 | of Piercing Storms | 4.8 | EXPECTED | EXPECTED | EXPECTED | INHERITED | penetrationChannel / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-07 | WA-040 | Umbral-Forged | 12.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-07 | WA-045 | Opening | 14.4 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/hytale/HytaleConditionalDamage.java |
| qa159-07 | WA-056 | of Rime | 10.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/status/GearStatusRuntime.java |
| qa159-07 | WA-123 | Zephyr Sage’s | 1.0 | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/execution/GearSkillRanks.java |
| qa159-07 | WA-136 | Deadly | 4.8 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearSignatureProcRuntime.java |
| qa159-07 | WA-157 | of Measure | 4.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/ManagedGearDamageInteraction.java |
| qa159-08 | WA-018 | Glacial-Edged | 4.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-08 | WA-027 | Voltaic | 14.4 | EXPECTED | EXPECTED | EXPECTED | INHERITED | damageChannel / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-08 | WA-029 | of Piercing Gales | 4.8 | EXPECTED | EXPECTED | EXPECTED | INHERITED | penetrationChannel / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-08 | WA-038 | Earthen-Forged | 12.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-08 | WA-041 | Brawling | 12.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/hytale/HytaleConditionalDamage.java |
| qa159-08 | WA-057 | of Static | 10.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/status/GearStatusRuntime.java |
| qa159-08 | WA-129 | Warmaster’s | 1.0 | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/execution/GearSkillRanks.java |
| qa159-08 | WA-141 | of Twin Assault | 4.8 | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | INAPPLICABLE | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/ManagedGearDamageInteraction.java |
| qa159-08 | WA-145 | Embercalling | 4.8 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/ItemSkillTriggerRuntime.java |
| qa159-09 | WA-043 | Ambushing | 14.4 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/hytale/HytaleConditionalDamage.java |
| qa159-09 | WA-055 | of Venom | 10.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/status/GearStatusRuntime.java |
| qa159-09 | WA-064 | of Intrusion | 4.8 | EXPECTED | EXPECTED | EXPECTED | INHERITED | statusPenetration / src/main/java/com/inigmasgames/hytalerpg/combat/status/GearStatusRuntime.java |
| qa159-09 | WA-082 | of Anchoring | 12.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/execution/hytale/NativeAffixHostileDisplacement.java |
| qa159-09 | WA-096 | Vampiric | 1.6 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/resource/GearRecoveryRuntime.java |
| qa159-09 | WA-097 | Siphoning | 0.8 | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/resource/GearRecoveryRuntime.java |
| qa159-09 | WA-126 | Earthen Sage’s | 1.0 | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/execution/GearSkillRanks.java |
| qa159-09 | WA-138 | Riveting | 10.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearSignatureProcRuntime.java |
| qa159-09 | WA-158 | of Slaughter | 6.4 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/ManagedGearDamageInteraction.java |
| qa159-10 | WA-008 | of Alacrity | 7.2 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/NativePrimaryActionAssets.java |
| qa159-10 | WA-017 | Zephyr-Edged | 4.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-10 | WA-023 | Zephyr | 14.4 | EXPECTED | EXPECTED | EXPECTED | INHERITED | damageChannel / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-10 | WA-031 | of Piercing Embers | 4.8 | EXPECTED | EXPECTED | EXPECTED | INHERITED | penetrationChannel / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-10 | WA-035 | Zephyr-Forged | 12.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-10 | WA-049 | Storm-Seeking | 11.2 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/hytale/HytaleConditionalDamage.java |
| qa159-10 | WA-065 | Searing | 14.4 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/status/GearStatusRuntime.java |
| qa159-10 | WA-122 | of [Skill] Mastery | 1.0 / hunter_s_mark | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/execution/GearSkillRanks.java |
| qa159-10 | WA-134 | Rending | 8.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/status/GearStatusRuntime.java |
| qa159-11 | WA-015 | of Flight | 12.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/NativeAffixProjectileTravel.java |
| qa159-11 | WA-019 | Ember-Edged | 4.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-11 | WA-026 | Earthen | 14.4 | EXPECTED | EXPECTED | EXPECTED | INHERITED | damageChannel / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-11 | WA-050 | Venom-Seeking | 11.2 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/hytale/HytaleConditionalDamage.java |
| qa159-11 | WA-062 | of Dread | 4.8 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/status/GearStatusRuntime.java |
| qa159-11 | WA-085 | of Might | 9.0 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | heavy / src/main/java/com/inigmasgames/hytalerpg/gear/GearEquipmentResolution.java |
| qa159-11 | WA-095 | of Clarity | 0.8 | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/resource/GearRecoveryRuntime.java |
| qa159-11 | WA-133 | Fletcher-Mage’s | 1.0 | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/execution/GearSkillRanks.java |
| qa159-11 | WA-139 | of Finality | 5.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearSignatureProcRuntime.java |
| qa159-12 | WA-021 | Voltaic-Edged | 4.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-12 | WA-030 | of Piercing Tides | 4.8 | EXPECTED | EXPECTED | EXPECTED | INHERITED | penetrationChannel / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-12 | WA-039 | Voltaic-Forged | 12.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-12 | WA-046 | Opportunistic | 12.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/hytale/HytaleConditionalDamage.java |
| qa159-12 | WA-053 | of Serration | 10.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/status/GearStatusRuntime.java |
| qa159-12 | WA-125 | Ember Sage’s | 1.0 | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/execution/GearSkillRanks.java |
| qa159-12 | WA-140 | Resolute | 10.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearSignatureProcRuntime.java |
| qa159-12 | WA-143 | of Rupture | 6.4 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearSignatureProcRuntime.java |
| qa159-12 | WA-144 | of Borrowed Arts | 1.0 / execution_strike | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/execution/SkillExecutionService.java |
| qa159-13 | WA-047 | Scorch-Seeking | 11.2 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/hytale/HytaleConditionalDamage.java |
| qa159-13 | WA-069 | Plated | 24.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/HytaleGearEquipment.java |
| qa159-13 | WA-070 | Reinforced | 24.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/HytaleGearEquipment.java |
| qa159-13 | WA-083 | of Efficient Guarding | 7.2 | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | INAPPLICABLE | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/NativeAffixBlockCostSystem.java |
| qa159-13 | WA-084 | of Composure | 12.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearDefenseEffects.java |
| qa159-13 | WA-132 | Blademaster’s | 1.0 | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/execution/GearSkillRanks.java |
| qa159-13 | WA-142 | of Retribution | 4.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearSignatureProcRuntime.java |
| qa159-13 | WA-147 | of Succor | 4.8 | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | INAPPLICABLE | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/ItemSkillTriggerRuntime.java |
| qa159-13 | WA-149 | of the Thorn Standard | 1.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/execution/support/SupportRuntime.java |
| qa159-14 | WA-004 | Sorcerous | 14.4 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | spellDamage / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-14 | WA-048 | Frost-Seeking | 11.2 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/hytale/HytaleConditionalDamage.java |
| qa159-14 | WA-058 | of Hobbling | 10.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/status/GearStatusRuntime.java |
| qa159-14 | WA-107 | of Sustenance | 7.2 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | channelCost / src/main/java/com/inigmasgames/hytalerpg/combat/resource/RpgResourceService.java |
| qa159-14 | WA-109 | of Steadiness | 7.2 | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/status/StatusService.java |
| qa159-14 | WA-112 | of Respite | 2.0 | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/execution/support/FiniteSupportEffects.java |
| qa159-14 | WA-116 | of the Shelterer | 4.8 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | minionResistance / src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSummonSystem.java |
| qa159-14 | WA-117 | of Command | 4.8 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | minionAttackSpeed / src/main/java/com/inigmasgames/hytalerpg/execution/summon/SummonRegistry.java |
| qa159-14 | WA-128 | Umbral Sage’s | 1.0 | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/execution/GearSkillRanks.java |
| qa159-15 | WA-022 | Umbral-Edged | 4.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-15 | WA-060 | of Hushing | 4.8 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/status/GearStatusRuntime.java |
| qa159-15 | WA-086 | of Dexterity | 9.0 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | light / src/main/java/com/inigmasgames/hytalerpg/gear/GearEquipmentResolution.java |
| qa159-15 | WA-103 | Restorative | 4.0 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | healingPower / src/main/java/com/inigmasgames/hytalerpg/execution/support/SupportRuntime.java |
| qa159-15 | WA-118 | of Pursuit | 7.2 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | minionMovement / src/main/java/com/inigmasgames/hytalerpg/execution/summon/SummonNativeMovement.java |
| qa159-15 | WA-119 | of Binding Pacts | 12.0 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | minionDuration / src/main/java/com/inigmasgames/hytalerpg/execution/summon/SummonRegistry.java |
| qa159-15 | WA-120 | of Muster | 6.4 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | summonCost / src/main/java/com/inigmasgames/hytalerpg/combat/resource/RpgResourceService.java |
| qa159-15 | WA-131 | Binder’s | 1.0 | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/execution/GearSkillRanks.java |
| qa159-15 | WA-148 | of the Wellspring | 1.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/execution/support/SupportRuntime.java |
| qa159-16 | WA-011 | of Lethality | 10.0 | EXPECTED | EXPECTED | EXPECTED | INHERITED | criticalDamage / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-16 | WA-020 | Earthen-Edged | 4.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-16 | WA-028 | Umbral | 14.4 | EXPECTED | EXPECTED | EXPECTED | INHERITED | damageChannel / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-16 | WA-044 | Finishing | 14.4 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/hytale/HytaleConditionalDamage.java |
| qa159-16 | WA-054 | of Ignition | 10.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/status/GearStatusRuntime.java |
| qa159-16 | WA-108 | Patient | 2.4 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | channelRamp / src/main/java/com/inigmasgames/hytalerpg/execution/GearSupportModifiers.java |
| qa159-16 | WA-113 | Commanding | 12.0 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | minionDamage / src/main/java/com/inigmasgames/hytalerpg/combat/hytale/HytaleDamageAdapter.java |
| qa159-16 | WA-121 | Paragon’s | 1.0 | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/execution/GearSkillRanks.java |
| qa159-16 | WA-150 | of Measured Time | 1.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/execution/support/SupportRuntime.java |
| qa159-17 | WA-003 | Attuned | 4.0 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | weapon/critical presentation / src/main/java/com/inigmasgames/hytalerpg/gear/ManagedCarrierDamageInteraction.java |
| qa159-17 | WA-016 | of the Horizon | 12.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/gear/NativeAffixProjectileTravel.java |
| qa159-17 | WA-032 | of Piercing Stone | 4.8 | EXPECTED | EXPECTED | EXPECTED | INHERITED | penetrationChannel / src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java |
| qa159-17 | WA-042 | Farseeing | 12.0 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/hytale/HytaleConditionalDamage.java |
| qa159-17 | WA-063 | of Binding | 4.8 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/status/GearStatusRuntime.java |
| qa159-17 | WA-067 | Lacerating | 14.4 | NOT_APPLICABLE | EXPECTED | EXPECTED | INHERITED | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/combat/status/GearStatusRuntime.java |
| qa159-17 | WA-106 | of Connection | 12.0 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | tetherReach / src/main/java/com/inigmasgames/hytalerpg/execution/connection/ConnectionRuntime.java |
| qa159-17 | WA-115 | Bulwarked | 12.0 | EXPECTED | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | minionDefense / src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSummonSystem.java |
| qa159-17 | WA-130 | Healer’s | 1.0 | NOT_APPLICABLE | EXPECTED | NOT_APPLICABLE | OWNER_ONLY | NOT_APPLICABLE / src/main/java/com/inigmasgames/hytalerpg/execution/GearSkillRanks.java |
