package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyEliteRoleMatrixTest {
    @Test void everyCatalogIdentityHasDeterministicCertificationAndGeneratedDocument()throws Exception{
        var generator=new EnemyEliteRoleMatrix();var rows=generator.rows();
        var catalog=EnemyRewardRegistry.load();
        assertEquals(catalog.roles().size()+catalog.aliases().size(),rows.size());
        assertEquals(rows.size(),rows.stream().map(EnemyEliteRoleMatrix.Row::roleId).distinct().count());
        for(var row:rows){
            assertNotNull(row.canonicalRoleId());
            if(!row.certified())assertFalse(row.rejection().isBlank(),row.roleId());
            else for(var era:DifficultyId.values()){
                assertNotNull(row.champion().get(era),row.roleId()+"/"+era);
                assertNotNull(row.unique().get(era),row.roleId()+"/"+era);
            }
        }
        assertEquals(generator.render(),Files.readString(Path.of("docs/enemies/ELITE_ROLE_MATRIX.md")));
    }

    @Test void certifiedRolesUseOnlyCompleteLegalSetsAndNaturalSuperUniqueNeverRolls(){
        var rows=new EnemyEliteRoleMatrix().rows();var bindings=EnemyNativeBindings.load();
        var registry=EnemyAffixRegistry.canonical();var balance=EnemyBalance.canonical();
        var selection=new EnemyAffixSelection(registry);
        for(var row:rows)if(row.certified())for(var era:DifficultyId.values())
            for(var rarity:List.of(EnemyRarity.CHAMPION,EnemyRarity.UNIQUE)){
                var role=bindings.role(row.roleId()).orElseThrow();
                var binding=role.affixBinding(bindings.revision(),true,
                        rarity==EnemyRarity.CHAMPION?0:balance.promotion().minionMinimum());
                int count=balance.rarity(rarity,false).counts().get(era.ordinal());
                var request=new EnemyAffixSelection.Request(binding,era,rarity,count,List.of(),true);
                var selected=selection.select(request,"matrix-test/"+row.roleId()+"/"+era+"/"+rarity);
                boolean expected=rarity==EnemyRarity.CHAMPION?row.champion().get(era):row.unique().get(era);
                assertEquals(expected,selected.isPresent(),row.roleId()+"/"+era+"/"+rarity);
                selected.ifPresent(choices->{
                    assertEquals(count,choices.size());assertTrue(selection.legal(request,choices));
                    for(var choice:choices){
                        assertTrue(selection.rejectionReason(binding,rarity,choice).isEmpty());
                        if(rarity==EnemyRarity.CHAMPION)
                            assertFalse(EnemyAffixSelection.semanticGroups(registry.require(choice.affixId()).operator(),
                                    choice.selector()).contains(EnemyAffixRegistry.Group.LEADER));
                    }
                });
            }
        assertTrue(bindings.productionEligibilityRejection("Skeleton_Fighter").isEmpty());
        assertTrue(bindings.productionEligibilityRejection("Golem_Crystal_Frost").isPresent());
        assertTrue(bindings.productionEligibilityRejection("Trork_Warrior").isEmpty());
        for(var role:List.of("Larva_Void","Trork_Warrior","Skeleton_Scout","Golem_Firesteel"))
            assertTrue(bindings.role(role).orElseThrow().productionPromotionEnabled(),role);
        for(var role:List.of("Golem_Crystal_Earth","Golem_Crystal_Frost"))
            assertFalse(bindings.role(role).orElseThrow().productionPromotionEnabled(),role);
        assertTrue(rows.stream().filter(row->row.roleId().equals("Trork_Warrior")).findFirst()
                .orElseThrow().authoredSuperUniqueTemplates().contains("grimgor_the_ashen"));
        var planner=new EnemyBirthPlanner(balance,registry,EnemyAffinityRegistry.canonical(),
                EnemyNamePools.canonical(),EnemyVisualVariants.canonical());
        for(var era:DifficultyId.values())for(int i=0;i<300;i++)
            assertNotEquals(EnemyRarity.SUPER_UNIQUE,planner.rarity("elite-roll/"+i,era));
    }

    @Test void authoredSuperUniqueRejectsUncertifiedFixedKnockbackWithReason(){
        var bindings=EnemyNativeBindings.load();
        var original=SuperUniqueTemplates.canonical().requireTemplate("grimgor_the_ashen");
        var impossible=new SuperUniqueTemplates.Template("scout_knockback_probe",1,"Scout Probe",
                "Skeleton_Scout",Set.of(DifficultyId.NORMAL),1,1,
                List.of(EnemyAffixSelection.Choice.of(EnemyAffixRegistry.Operator.KNOCKBACK)),
                List.of(0,0,0),List.of(),Map.of(DifficultyId.NORMAL,Set.of(),
                        DifficultyId.NIGHTMARE,Set.of(),DifficultyId.HELL,Set.of()),
                null,null,original.placement(),original.respawn());
        var scout=bindings.role("Skeleton_Scout").orElseThrow().affixBinding(bindings.revision(),true,0);
        var rejected=assertThrows(IllegalArgumentException.class,()->impossible.validateBindings(
                new EnemyAffixSelection(EnemyAffixRegistry.canonical()),ignored->scout,
                (template,era)->true,ignored->true,ignored->true));
        assertTrue(rejected.getMessage().contains("TEMPLATE_AFFIX_UNSUPPORTED:ME-015:NATIVE_KNOCKBACK_NOT_CERTIFIED"));
    }

    @Test void nativeKnockbackAndOtherNegativePredicatesCannotBeBypassed(){
        var bindings=EnemyNativeBindings.load();var selector=new EnemyAffixSelection(EnemyAffixRegistry.canonical());
        var scout=bindings.role("Skeleton_Scout").orElseThrow()
                .affixBinding(bindings.revision(),true,3);
        assertEquals("NATIVE_KNOCKBACK_NOT_CERTIFIED",selector.rejectionReason(scout,EnemyRarity.UNIQUE,
                EnemyAffixSelection.Choice.of(EnemyAffixRegistry.Operator.KNOCKBACK)).orElseThrow());
        var noPhysical=new EnemyAffixSelection.Binding("test",Set.of(EnemyAffixRegistry.Capability.DIRECT_HIT),
                0,true,false,false,0,false);
        assertTrue(selector.rejectionReason(noPhysical,EnemyRarity.UNIQUE,
                EnemyAffixSelection.Choice.of(EnemyAffixRegistry.Operator.EXTRA_STRONG)).isPresent());
        var immune=new EnemyAffixSelection.Binding("test",EnumSet.allOf(EnemyAffixRegistry.Capability.class),
                0,true,true,true,3,false);
        assertEquals("NATIVE_STUN_STAGGER_IMMUNE",selector.rejectionReason(immune,EnemyRarity.UNIQUE,
                EnemyAffixSelection.Choice.of(EnemyAffixRegistry.Operator.UNWAVERING)).orElseThrow());
        assertEquals("NATIVE_SLOW_IMMUNE",selector.rejectionReason(immune,EnemyRarity.UNIQUE,
                EnemyAffixSelection.Choice.of(EnemyAffixRegistry.Operator.UNSTOPPABLE)).orElseThrow());
    }
}
