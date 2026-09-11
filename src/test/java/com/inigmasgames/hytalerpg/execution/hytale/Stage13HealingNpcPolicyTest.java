package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Production policy tests, not connected NPC/healing evidence. */
class Stage13HealingNpcPolicyTest {
    @Test void damageImmunityDoesNotBlockHealingOfAffirmativeAllies(){
        for(var attitude:new Attitude[]{Attitude.FRIENDLY,Attitude.REVERED}){
            assertTrue(HytaleSupportSystem.permitsAllyAttitude(attitude,true,true));
            assertTrue(HytaleSupportSystem.permitsAllyAttitude(attitude,false,true));
        }
    }
    @Test void healingNeverConvertsNeutralHostileIgnoredOrMissingAttitudeIntoFriendship(){
        for(var attitude:new Attitude[]{Attitude.HOSTILE,Attitude.NEUTRAL,Attitude.IGNORE,null})
            for(boolean protectedNpc:new boolean[]{false,true})
                assertFalse(HytaleSupportSystem.permitsAllyAttitude(attitude,protectedNpc,true));
    }
    @Test void otherSupportSkillsRetainTheirProtectionPolicy(){
        for(var attitude:Attitude.values()){
            assertFalse(HytaleSupportSystem.permitsAllyAttitude(attitude,true,false));
            assertEquals(attitude==Attitude.FRIENDLY||attitude==Attitude.REVERED,
                    HytaleSupportSystem.permitsAllyAttitude(attitude,false,false));
        }
    }
    @Test void nativeTetherUsesSameHealingPolicyForAcquisitionRevalidationAndSecondaryInjury() throws Exception {
        var source=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.java"));
        var connection=source.substring(source.indexOf("public java.util.Optional<Target> resolveFriendly(String id)"),source.indexOf("public boolean payUpkeep(SkillExecutionContext context,int tick,double seconds)"));
        assertEquals(2,connection.split("HytaleSupportSystem.eligibleHealingAlly",-1).length-1);
        assertFalse(connection.contains("HytaleSupportSystem.eligibleAlly("));
        assertTrue(connection.contains("if(resolveFriendly(target.id()).isEmpty())return 0"));
        assertTrue(connection.contains("id.equals(playerRef.getUuid().toString())"));
        var support=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSupportSystem.java"));
        assertTrue(support.contains("target==null||!target.isValid()||!alive(store,target)"));
    }
}
