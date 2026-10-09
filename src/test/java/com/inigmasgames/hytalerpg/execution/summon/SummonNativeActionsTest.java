package com.inigmasgames.hytalerpg.execution.summon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SummonNativeActionsTest {
    private static JsonObject asset(String path) {
        try(var stream=SummonNativeActionsTest.class.getResourceAsStream(path)) {
            assertNotNull(stream,path);
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(java.io.IOException ex){throw new java.io.UncheckedIOException(ex);}
    }

    @Test void everyAcceptedArcherRollHasOneNativeArrowAndMatchingRoleAnimationTimeline(){
        assertEquals(SummonNativeActions.ARCHER,SummonNativeActions.roleId(SummonNativeActions.ARCHER,0));
        assertEquals(1,SummonNativeActions.attackPeriod(SummonNativeActions.ARCHER,1,0),1e-9);
        for(int tenth=32;tenth<=240;tenth++){
            double roll=tenth/10d,factor=1+roll/100;
            String suffix=String.format(java.util.Locale.ROOT,"%03d",tenth);
            String action="RPG_Summon_Archer_Command_"+suffix;
            String animation=action+"_Bow";
            String role=SummonNativeActions.roleId(SummonNativeActions.ARCHER,roll);
            assertEquals("RPG_Summon_Skeleton_Archer_Command_"+suffix,role);
            var roleAsset=asset("/Server/NPC/Roles/RPG/"+role+".json");
            assertEquals(action,roleAsset.getAsJsonObject("Modify").get("Attack").getAsString());
            var root=asset("/Server/Item/RootInteractions/RPG/Summon/"+action+".json");
            assertEquals(1,root.getAsJsonArray("Interactions").size());
            assertEquals(action,root.getAsJsonArray("Interactions").get(0).getAsString());
            var timeline=asset("/Server/Item/Interactions/RPG/Summon/"+action+".json");
            assertEquals("Simple",timeline.get("Type").getAsString());
            assertEquals(animation,timeline.getAsJsonObject("Effects").get("ItemPlayerAnimationsId").getAsString());
            var release=timeline.getAsJsonObject("Next");
            assertEquals("LaunchProjectile",release.get("Type").getAsString());
            assertEquals("Skeleton_Archer_Arrow",release.get("ProjectileId").getAsString());
            assertFalse(release.has("Next"));
            double period=timeline.get("RunTime").getAsDouble()+release.get("RunTime").getAsDouble();
            assertEquals(SummonNativeActions.attackPeriod(SummonNativeActions.ARCHER,1,roll),period,2e-6);
            var bow=asset("/Server/Item/Animations/RPG/Summon/"+animation+".json");
            assertEquals("Skeleton_Bow",bow.get("Parent").getAsString());
            assertEquals(factor,bow.getAsJsonObject("Animations").getAsJsonObject("Shoot").get("Speed").getAsDouble(),1e-6);
        }
        assertThrows(IllegalArgumentException.class,()->SummonNativeActions.roleId(SummonNativeActions.ARCHER,24.1));
    }

    @Test void nativeArmorDeltaPreservesEngineBaselineAndUnarmoredControl(){
        assertEquals(0,HytaleSummonSystem.ordinaryDefenseContribution(0,50,.2));
        assertEquals(0,HytaleSummonSystem.ordinaryDefenseContribution(.3,50,0));
        double delta=HytaleSummonSystem.ordinaryDefenseContribution(.3,50,.2);
        double combined=1-(1-.3)*(1-delta);
        assertEquals(.36/1.06,combined,1e-9);
        assertTrue(delta>0);
        var chassis=HytaleSummonSystem.sentinelDefenseView(.3,50,.2);
        assertEquals(combined,chassis.managedProtection(),1e-9);
        assertEquals(.3,HytaleSummonSystem.sentinelDefenseView(.3,50,0).managedProtection(),1e-9);
    }
    @Test void sentinelSwingPresentationUsesExactFrozenRate(){
        var authored=JsonParser.parseString(NativeSentinelActionAssets.render(1.12)).getAsJsonObject();
        assertEquals("Default",authored.get("Parent").getAsString());
        assertEquals(1.68,authored.getAsJsonObject("Animations").getAsJsonObject("SwingLeft")
                .get("Speed").getAsDouble(),1e-9);
        assertThrows(IllegalArgumentException.class,()->NativeSentinelActionAssets.render(0));
    }
}
