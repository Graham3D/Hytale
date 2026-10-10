package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.JsonParser;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Offline proof for the pinned R229 native action families; connected combat remains owner QA. */
class ProductionEliteInstalledRouteTest {
    @Test void everyNewInstalledRoleHasACompleteProductionPlanAndExactActionRestrictions() throws Exception {
        var document=JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/rpg/enemies/native-bindings-v1.json"))).getAsJsonObject();
        var groups=document.getAsJsonObject("derivedBindings").getAsJsonArray("installedCombatArchetypes");
        var expected=Map.of("SINGLE_MELEE",3,"CHAIN_SHARED_MELEE",13,"CHAIN_VARIABLE_MELEE",6,
                "CHAIN_REPEATED_MELEE",1,
                "SINGLE_PROJECTILE",10,"MIXED_NATIVE_ACTIONS",2,"DUAL_NATIVE_ACTIONS",1,
                "CONDITIONAL_MELEE",2,"CAE_NATIVE_ACTIONS",1);
        assertEquals(expected.size(),groups.size());
        var bindings=EnemyNativeBindings.load();
        var selector=new EnemyAffixSelection(EnemyAffixRegistry.canonical());
        var seen=new HashSet<String>();
        for(var element:groups){
            var group=element.getAsJsonObject();
            String kind=group.get("routeKind").getAsString();
            assertEquals(expected.get(kind).intValue(),group.getAsJsonArray("members").size(),kind);
            for(var item:group.getAsJsonArray("members")){
                var member=item.getAsJsonObject();
                String id=member.get("roleId").getAsString();
                assertTrue(seen.add(id),id);
                var role=bindings.role(id).orElseThrow();
                assertTrue(role.productionPromotionEnabled(),id);
                assertTrue(bindings.productionEligibilityRejection(id).isEmpty(),id);
                assertEquals(member.getAsJsonArray("actions").size(),role.actions().size(),id);
                var affixes=role.affixBinding(bindings.revision(),true,0);
                for(var era:DifficultyId.values()){
                    var champion=new EnemyAffixSelection.Request(affixes,era,EnemyRarity.CHAMPION,1,List.of(),true);
                    assertTrue(selector.select(champion,"r229/"+id+"/"+era+"/champion").isPresent(),id+"/"+era);
                    var unique=new EnemyAffixSelection.Request(affixes,era,EnemyRarity.UNIQUE,
                            era==DifficultyId.NORMAL?2:3,List.of(),true);
                    assertTrue(selector.select(unique,"r229/"+id+"/"+era+"/unique").isPresent(),id+"/"+era);
                }
                for(var forbidden:List.of(EnemyAffixRegistry.Operator.EXTRA_FAST,
                        EnemyAffixRegistry.Operator.KNOCKBACK,EnemyAffixRegistry.Operator.FRENZIED)){
                    assertTrue(selector.rejectionReason(affixes,EnemyRarity.UNIQUE,
                            EnemyAffixSelection.Choice.of(forbidden)).isPresent(),id+"/"+forbidden);
                }
                assertEquals(role.actions().stream().anyMatch(action->action.channels().containsKey("Projectile")),
                        role.requiresProjectileReceipt(),id);
                if(kind.equals("CAE_NATIVE_ACTIONS"))
                    assertEquals("CAE_Skeleton_Praetorian",role.actions().getFirst().nativeVariantOwnerId());
                if(kind.equals("CHAIN_REPEATED_MELEE"))
                    assertEquals(Map.of("Club_Swing_Left_Right_Damage",6,
                            "Club_Swing_Left_Right_Down_Damage",3),role.actions().getFirst().contactOccurrences());
            }
        }
        assertEquals(39,seen.size());
        assertTrue(bindings.productionEligibilityRejection("Goblin_Turret").isPresent());
        assertTrue(bindings.productionEligibilityRejection("Scarak_Seeker").isPresent());
    }
}
