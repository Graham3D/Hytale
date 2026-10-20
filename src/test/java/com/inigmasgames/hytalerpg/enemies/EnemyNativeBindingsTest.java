package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.difficulty.*;
import com.inigmasgames.hytalerpg.progress.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyNativeBindingsTest {
    private JsonObject source(){
        try(var reader=new java.io.InputStreamReader(getClass().getResourceAsStream("/rpg/enemies/native-bindings-v1.json"),
                java.nio.charset.StandardCharsets.UTF_8)){
            return JsonParser.parseReader(reader).getAsJsonObject();
        }catch(java.io.IOException error){throw new IllegalStateException(error);}
    }
    @Test void currentNativeBindingsAreStrictlyDecodedWithOnlyLarvaEnabled(){
        var manifest=EnemyNativeBindings.load();var role=manifest.role("Larva_Void").orElseThrow();
        assertTrue(role.productionPromotionEnabled());
        assertFalse(role.distanceDisplacementDelivery());
        assertTrue(role.nativeImmunityChannels().isEmpty());
        assertEquals(EnemyStatusEffects.Source.noNativeStatus(0),role.nativeStatusSource());
        assertFalse(role.nativeStunStaggerImmune());assertFalse(role.nativeSlowImmune());
        assertTrue(role.capabilities().contains(EnemyAffixRegistry.Capability.MOBILE));
        assertTrue(role.capabilities().contains(EnemyAffixRegistry.Capability.RECOVERY_TIMELINE));
        assertEquals("Larva_Void_Bite/installed-0.7.0-pre.5.1",
                role.recoveryProfile("Larva_Void_Bite").orElseThrow());
        assertTrue(role.recoveryProfile("unrelated").isEmpty());
        assertEquals(1,role.actions().size());
        assertEquals("Larva_Void_Bite",role.actions().getFirst().nativeActionId());
        var trork=manifest.role("Trork_Warrior").orElseThrow();
        assertFalse(trork.productionPromotionEnabled());
        assertEquals(5,trork.actions().size());
        assertTrue(trork.capabilities().contains(EnemyAffixRegistry.Capability.MOBILE));
        assertFalse(trork.capabilities().contains(EnemyAffixRegistry.Capability.RECOVERY_TIMELINE));
        var scout=manifest.role("Skeleton_Scout").orElseThrow();
        assertFalse(scout.productionPromotionEnabled());
        assertTrue(scout.distanceDisplacementDelivery());
        assertEquals(Set.of("ME-002","ME-003","ME-004","ME-015","ME-017"),
                scout.actions().getFirst().supportedAffixIds());
        assertFalse(scout.capabilities().contains(EnemyAffixRegistry.Capability.MOBILE));
        assertTrue(scout.requiresProjectileReceipt());
        assertFalse(role.requiresProjectileReceipt());
        assertFalse(trork.requiresProjectileReceipt());
        var authored=AuthoredEncounterCatalog.load().profiles();
        for(var mode:DifficultyId.values())assertTrue(authored.stream().anyMatch(profile->
                profile.id().equals(scout.profiles().get(mode))&&profile.roleId().equals("Skeleton_Scout")),mode.name());
    }
    @Test void ownerQaCandidateAdmitsAllTwentySevenLarvaCards(){
        var roles=EnemyNativeBindings.load();
        var larva=roles.role("Larva_Void").orElseThrow();
        var scout=roles.role("Skeleton_Scout").orElseThrow();
        assertTrue(larva.capabilities().containsAll(Set.of(EnemyAffixRegistry.Capability.PACK_LEADER,
                EnemyAffixRegistry.Capability.MINION_ROSTER,EnemyAffixRegistry.Capability.SHIELD_OWNER)));
        assertFalse(scout.capabilities().contains(EnemyAffixRegistry.Capability.PACK_LEADER));
        var registry=EnemyAffixRegistry.canonical();
        var selection=new EnemyAffixSelection(registry);
        var allowed=larva.actions().getFirst().supportedAffixIds();
        assertNotNull(allowed);
        var larvaBinding=new EnemyAffixSelection.Binding(roles.revision(),larva.capabilities(),0,true,
                larva.nativeStunStaggerImmune(),larva.nativeSlowImmune(),4,
                larva.distanceDisplacementDelivery(),allowed);
        var scoutBinding=new EnemyAffixSelection.Binding(roles.revision(),scout.capabilities(),0,true,
                scout.nativeStunStaggerImmune(),scout.nativeSlowImmune(),0,
                scout.distanceDisplacementDelivery(),scout.actions().getFirst().supportedAffixIds());
        for(var id:allowed){
            var operator=registry.require(id).operator();
            var choice=operator==EnemyAffixRegistry.Operator.AURA_ENCHANTED
                    ?new EnemyAffixSelection.Choice(operator.id(),EnemyAffixRegistry.Selector.MIGHT)
                    :EnemyAffixSelection.Choice.of(operator);
            var request=new EnemyAffixSelection.Request(larvaBinding,DifficultyId.NORMAL,EnemyRarity.UNIQUE,1,
                    List.of(choice),false);
            assertEquals(List.of(choice),selection.select(request,"manifest-coverage/"+operator.id()).orElseThrow(),
                    operator.name());
        }
        for(var admitted:List.of("ME-006","ME-007","ME-008","ME-013","ME-014","ME-015","ME-016","ME-025","ME-026")){
            assertTrue(allowed.contains(admitted));
            assertTrue(selection.eligible(new EnemyAffixSelection.Request(larvaBinding,DifficultyId.NORMAL,
                    EnemyRarity.UNIQUE,1,List.of(),true),
                    EnemyAffixSelection.Choice.of(registry.require(admitted).operator())),admitted);
        }
        assertTrue(selection.eligible(new EnemyAffixSelection.Request(scoutBinding,DifficultyId.NORMAL,
                EnemyRarity.UNIQUE,1,List.of(),true),
                EnemyAffixSelection.Choice.of(EnemyAffixRegistry.Operator.KNOCKBACK)));
        var noRoster=new EnemyAffixSelection.Binding(roles.revision(),larva.capabilities(),0,true,false,false,
                0,false);
        assertFalse(selection.eligible(new EnemyAffixSelection.Request(noRoster,DifficultyId.NORMAL,
                EnemyRarity.UNIQUE,1,List.of(),true),
                EnemyAffixSelection.Choice.of(EnemyAffixRegistry.Operator.PACKBOUND)));
        assertFalse(selection.eligible(new EnemyAffixSelection.Request(larvaBinding,DifficultyId.NORMAL,
                EnemyRarity.CHAMPION,1,List.of(),true),
                EnemyAffixSelection.Choice.of(EnemyAffixRegistry.Operator.PACKBOUND)));
        assertTrue(larva.productionPromotionEnabled());
        assertFalse(scout.productionPromotionEnabled());
    }
    @Test void qaGolemsHaveOnlyPassiveBindingsAndAuthoredProfiles(){
        var manifest=EnemyNativeBindings.load();var authored=AuthoredEncounterCatalog.load().profiles();
        for(var id:List.of("Golem_Crystal_Earth","Golem_Firesteel")){
            var role=manifest.role(id).orElseThrow();
            assertFalse(role.productionPromotionEnabled());assertTrue(role.actions().isEmpty());
            assertFalse(role.capabilities().contains(EnemyAffixRegistry.Capability.DIRECT_HIT));
            assertTrue(role.capabilities().contains(EnemyAffixRegistry.Capability.DEFENSE_STAT));
            for(var mode:DifficultyId.values())assertTrue(authored.stream().anyMatch(profile->
                    profile.id().equals(role.profiles().get(mode))&&profile.roleId().equals(id)),id+"/"+mode);
        }
    }
    @Test void unknownFieldsMissingOwnersAndNoncanonicalChannelsFailClosed(){
        var unknown=source();unknown.addProperty("inventedAbility",true);
        assertThrows(IllegalArgumentException.class,()->EnemyNativeBindings.decode(unknown));
        var missing=source();missing.getAsJsonArray("bindings").get(0).getAsJsonObject().add("defenseBinding",JsonNull.INSTANCE);
        assertThrows(IllegalArgumentException.class,()->EnemyNativeBindings.decode(missing));
        var channel=source();channel.getAsJsonArray("bindings").get(0).getAsJsonObject().getAsJsonArray("actionBindings")
                .get(0).getAsJsonObject().getAsJsonObject("nativeCauseToCanonicalChannelMap").addProperty("Physical","ARCANE");
        assertThrows(IllegalArgumentException.class,()->EnemyNativeBindings.decode(channel));
        var immunity=source();immunity.getAsJsonArray("bindings").get(0).getAsJsonObject()
                .getAsJsonArray("nativeImmunityChannels").add("ARCANE");
        assertThrows(IllegalArgumentException.class,()->EnemyNativeBindings.decode(immunity));
        var status=source();status.getAsJsonArray("bindings").get(0).getAsJsonObject()
                .getAsJsonObject("nativeStatusSource").getAsJsonObject("existingChance").remove("CHILL");
        assertThrows(IllegalArgumentException.class,()->EnemyNativeBindings.decode(status));
        var recovery=source();recovery.getAsJsonArray("bindings").get(0).getAsJsonObject()
                .getAsJsonArray("actionBindings").get(0).getAsJsonObject()
                .getAsJsonObject("scalableRecoveryBinding").addProperty("fieldOrProfileId","unknown/native-timeline");
        assertThrows(IllegalArgumentException.class,()->EnemyNativeBindings.decode(recovery));
        var missingScoutCapabilities=source();
        missingScoutCapabilities.getAsJsonArray("bindings").get(2).getAsJsonObject()
                .getAsJsonArray("actionBindings").get(0).getAsJsonObject().remove("supportedAffixIds");
        assertThrows(IllegalArgumentException.class,()->EnemyNativeBindings.decode(missingScoutCapabilities));
    }
    @Test void candidateCopiesAnExistingAuthoredCombatProfileWithoutChangingItsLevelOrLootIdentity(){
        var manifest=EnemyNativeBindings.load();var binding=manifest.role("Larva_Void").orElseThrow();
        var profile=AuthoredEncounterCatalog.load().profiles().stream().filter(p->p.roleId().equals("Larva_Void")
                &&p.difficulty()==DifficultyId.NORMAL).findFirst().orElseThrow();
        var world=UUID.randomUUID();var enemy=UUID.randomUUID();var encounter=UUID.randomUUID();
        var combat=new EncounterProfileResolver.Resolved(world,enemy,profile.difficulty(),profile.id(),profile.worldProfileId(),
                profile.roleId(),profile.biomeKey(),profile.combatLevel(),profile.roleHealth(),profile.roleAttackBasis(),
                1,1,profile.resistance(),profile.evidence());
        var spawn=new EnemyRewardRegistry.Spawn(world,enemy,"Larva_Void","larva_void",profile.biomeKey(),profile.combatLevel(),
                ProgressionMath.Rank.COMMON,ProgressionMath.Rarity.ORDINARY,profile.id(),1000,null,combat);
        var candidate=binding.baseline(spawn,encounter,1,"native-cycle/1","Larva Void",
                EnemyBalance.canonical(),manifest.revision());
        assertEquals(profile.combatLevel(),candidate.nativeBaseline().combatLevel());
        assertEquals(profile.id(),candidate.nativeBaseline().sourceValidationId());
        assertEquals(binding.nativeLootSourceId(),candidate.nativeBaseline().lootSourceId());
        assertEquals(com.inigmasgames.hytalerpg.gear.GearLootService.MAX_BASE_EQUIPMENT_SLOTS_PER_DEFEAT,
                candidate.maximumEquipment());
        assertEquals(enemy,candidate.nativeBaseline().logicalActorId());
        assertFalse(candidate.binding().distanceDisplacementDelivery());
        var foreignCombat=new EncounterProfileResolver.Resolved(world,enemy,profile.difficulty(),"foreign/Trork_Warrior/NORMAL",
                profile.worldProfileId(),"Trork_Warrior",profile.biomeKey(),profile.combatLevel(),profile.roleHealth(),
                profile.roleAttackBasis(),1,1,profile.resistance(),profile.evidence());
        var foreignSpawn=new EnemyRewardRegistry.Spawn(world,enemy,"Trork_Warrior","trork_warrior",profile.biomeKey(),
                profile.combatLevel(),ProgressionMath.Rank.COMMON,ProgressionMath.Rarity.ORDINARY,
                foreignCombat.profileId(),1000,null,foreignCombat);
        assertThrows(IllegalStateException.class,()->binding.baseline(foreignSpawn,encounter,1,"native-cycle/1","Trork",
                EnemyBalance.canonical(),manifest.revision()));
        var wrongCanonical=new EnemyRewardRegistry.Spawn(world,enemy,"Larva_Void","trork_warrior",profile.biomeKey(),
                profile.combatLevel(),ProgressionMath.Rank.COMMON,ProgressionMath.Rarity.ORDINARY,
                profile.id(),1000,null,combat);
        assertThrows(IllegalStateException.class,()->binding.baseline(wrongCanonical,encounter,1,"native-cycle/1","Larva Void",
                EnemyBalance.canonical(),manifest.revision()));
        var alreadySpecial=new EnemyRewardRegistry.Spawn(world,enemy,"Larva_Void","larva_void",profile.biomeKey(),
                profile.combatLevel(),ProgressionMath.Rank.COMMON,ProgressionMath.Rarity.ORDINARY,
                profile.id(),1000,null,combat,candidate.nativeBaseline().immutableRewardContext());
        assertThrows(IllegalStateException.class,()->binding.baseline(alreadySpecial,encounter,1,"native-cycle/1","Larva Void",
                EnemyBalance.canonical(),manifest.revision()));

        // The attachment path must resolve each saved actor, even when a pack contains two roles.
        var mixed=source();var alternate=mixed.getAsJsonArray("bindings").get(0).getAsJsonObject().deepCopy();
        var alternateNames=new JsonArray();alternateNames.add("Larva_Void_Alternate");
        alternate.add("nativeRoleIds",alternateNames);alternate.addProperty("nativeSlowImmune",true);
        mixed.getAsJsonArray("bindings").add(alternate);
        var mixedBindings=EnemyNativeBindings.decode(mixed);
        var original=candidate.nativeBaseline();
        var alternateActor=new EnemyDescriptor(original.schemaVersion(),original.descriptorRevision(),original.balanceRevision(),
                original.nativeBindingRevision(),original.worldId(),original.encounterId(),original.encounterGeneration(),
                original.logicalActorId(),original.entityId(),original.spawnCycleId(),original.spawnOrigin(),
                original.canonicalRoleId(),"Larva_Void_Alternate",original.combatLevel(),original.difficulty(),
                original.encounterRank(),original.enemyRarity(),original.packRole(),original.packId(),original.leaderId(),
                original.sourceValidationId(),original.lootSourceId(),original.acquisitionSourceId(),original.nameToken(),
                original.nameSeed(),original.paletteId(),original.ownAffixes(),original.inheritedAffixes(),
                original.nativeImmunityChannels(),original.selectedImmunityGrants(),original.controlProfileId(),
                original.immutableRewardContext(),original.nativeBaselineFingerprint(),original.templateBirth());
        assertFalse(mixedBindings.requireActorRole(original).nativeSlowImmune());
        assertTrue(mixedBindings.requireActorRole(alternateActor).nativeSlowImmune());
        assertThrows(IllegalStateException.class,()->manifest.requireActorRole(alternateActor));
    }
}
