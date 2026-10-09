package com.inigmasgames.hytalerpg.enemies;

import java.util.*;
import org.junit.jupiter.api.Test;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.Operator.*;
import static org.junit.jupiter.api.Assertions.*;

class EnemyDisplayDtoTest {
    final EnemyAffixSnapshotTest fixture=new EnemyAffixSnapshotTest();
    @Test void packboundTagAndAdmissionReleaseFromTheSameDurableGuardState(){
        UUID second=UUID.randomUUID();var base=fixture.pack();
        var roster=new ArrayList<>(base.birthRoster());roster.add(new EnemyPackRecord.Member(second,second,"Trork_Warrior",EnemyPackRecord.Role.MINION));
        var pack=new EnemyPackRecord(1,base.packId(),base.worldId(),base.encounterId(),1,EnemyPackRecord.State.RESERVED,null,base.anchor(),roster,
                fixture.leader,Set.of(fixture.minion,second),Map.of(),false,false,"guard-birth",null).staged().publish();
        var descriptor=fixture.actor(false,List.of(fixture.affix(PACKBOUND),fixture.affix(FIRE_ENCHANTED),fixture.affix(EXTRA_FAST)),List.of(),null);
        var before=project(descriptor,pack,0);assertEquals(2,before.remainingGuardCount());
        assertEquals(List.of("Packbound: 2","Invulnerable","Fire Enchanted","Extra Fast"),before.orderedTags().stream().map(EnemyDisplayDto.EnemyTag::fallbackText).toList());
        var suspended=project(descriptor,pack.suspend(),1);assertEquals("Packbound: 2",suspended.ownAffixTags().getFirst().fallbackText());
        assertTrue(suspended.orderedTags().stream().anyMatch(t->t.fallbackText().equals("Recovering")));
        pack=pack.terminalDefeat(fixture.minion,"enemy-death/"+fixture.world+"/"+fixture.minion);
        var one=project(descriptor,pack,2);assertEquals("Packbound: 1",one.ownAffixTags().getFirst().fallbackText());
        var oldStats=EnemyAffixSnapshot.resolve(descriptor,fixture.balance,pack,0,true,false);
        pack=pack.terminalDefeat(second,"enemy-death/"+fixture.world+"/"+second);
        var released=project(descriptor,pack,3);assertEquals(0,released.remainingGuardCount());
        assertEquals("Packbound: Broken",released.ownAffixTags().getFirst().fallbackText());
        assertFalse(released.activeDefenseTags().stream().anyMatch(t->t.fallbackText().equals("Invulnerable")));
        assertEquals(before.ownAffixTags().getFirst().stableTagId(),released.ownAffixTags().getFirst().stableTagId());
        var finalPack=pack;
        assertThrows(IllegalArgumentException.class,()->EnemyDisplayDto.project(descriptor,fixture.registry,oldStats,finalPack,3,"Name","Trork Warrior",Set.of(),false,0));
    }
    @Test void resistanceDoesNotInventImmunityAndInheritedTagsStayOutsideRewardAffixCount(){
        var leader=fixture.actor(false,List.of(fixture.affix(MAGIC_RESISTANT),fixture.affix(UNSTOPPABLE),fixture.affix(UNWAVERING)),List.of(),null);
        var dto=project(leader,fixture.pack(),0);
        assertTrue(dto.activeDefenseTags().stream().noneMatch(t->t.sourceKind()==EnemyDisplayDto.SourceKind.DAMAGE_IMMUNITY));
        assertEquals(Set.of("Slow Immune","Stun/Stagger Immune"),dto.activeDefenseTags().stream().map(EnemyDisplayDto.EnemyTag::fallbackText).collect(java.util.stream.Collectors.toSet()));
        var fire=fixture.affix(FIRE_ENCHANTED);var inherited=EnemyDescriptor.inheritedInstance(fixture.registry.require(FIRE_ENCHANTED),fire,fixture.leader,"inherit").orElseThrow();
        var minion=fixture.actor(true,List.of(),List.of(inherited),null);var minionDto=project(minion,fixture.pack(),0);
        assertTrue(minionDto.ownAffixTags().isEmpty());assertEquals("Fire Enchanted (Inherited)",minionDto.inheritedEffectTags().getFirst().fallbackText());
        assertEquals(0,minion.immutableRewardContext().ownAffixCount());assertTrue(minionDto.overheadText().contains("Minion"));
    }
    @Test void largeCardRetainsAllAffixAndImmunityTagsAndUsesNativeResourceReferences(){
        var d=fixture.actor(false,List.of(fixture.affix(EXTRA_FAST),fixture.affix(EXTRA_STRONG),fixture.affix(FIRE_ENCHANTED),fixture.affix(UNSTOPPABLE)),List.of(),
                new EnemyDescriptor.TemplateBirth("qa",1,4,1.2));
        var descriptor=new EnemyDescriptor(d.schemaVersion(),d.descriptorRevision(),d.balanceRevision(),d.nativeBindingRevision(),d.worldId(),d.encounterId(),d.encounterGeneration(),d.logicalActorId(),
                d.entityId(),d.spawnCycleId(),d.spawnOrigin(),d.canonicalRoleId(),d.nativeRoleId(),d.combatLevel(),d.difficulty(),d.encounterRank(),d.enemyRarity(),d.packRole(),d.packId(),d.leaderId(),
                d.sourceValidationId(),d.lootSourceId(),d.acquisitionSourceId(),d.nameToken(),d.nameSeed(),d.paletteId(),d.ownAffixes(),d.inheritedAffixes(),Set.of("FIRE","WATER"),List.of(),
                d.controlProfileId(),d.immutableRewardContext(),d.nativeBaselineFingerprint(),d.templateBirth());
        var dto=project(descriptor,fixture.pack(),0);assertEquals(4,dto.ownAffixTags().size());assertEquals(7,dto.orderedTags().size());
        assertTrue(dto.orderedTags().stream().anyMatch(t->t.fallbackText().equals("Fire Immune")));
        assertTrue(dto.orderedTags().stream().anyMatch(t->t.fallbackText().equals("Water Immune")));
        assertEquals(descriptor.entityId(),dto.currentHealthRef().nativeEntityId());assertNull(dto.shieldRef());
        assertFalse(dto.orderedTags().stream().anyMatch(t->t.fallbackText().contains("+3")));
    }
    private EnemyDisplayDto project(EnemyDescriptor descriptor,EnemyPackRecord pack,long revision){
        return EnemyDisplayDto.project(descriptor,fixture.registry,EnemyAffixSnapshot.resolve(descriptor,fixture.balance,pack,0,true,false),pack,revision,
                "Grimgor the Ashen","Trork Warrior",Set.of(),false,0);
    }
}
