package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.FileEncounterStore;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class EnemySuperUniqueTest {
    private SuperUniqueAnchor anchor(){return new SuperUniqueAnchor(1,UUID.randomUUID(),UUID.randomUUID(),DifficultyId.HELL,new Vec3(1,2,3),
            "grimgor_the_ashen",1,"verified-placement",SuperUniqueTemplates.canonical().requireTemplate("grimgor_the_ashen").respawn(),1,0,
            SuperUniqueAnchor.State.IDLE,null,0,null);}
    @Test void grimgorPreservesSourceFixedAffixesAndFourGuards(){
        var template=SuperUniqueTemplates.canonical().requireTemplate("grimgor_the_ashen");
        assertEquals("Trork_Warrior",template.canonicalRoleId());assertEquals(4,template.minionCount());
        assertEquals(List.of("ME-005","ME-024"),template.fixedAffixes().stream().map(EnemyAffixSelection.Choice::affixId).toList());
        assertEquals(Set.of("FIRE"),template.explicitImmuneChannelsByMode().get(DifficultyId.HELL));
        assertEquals(4,template.affixCount(DifficultyId.HELL));
        var binding=new EnemyAffixSelection.Binding("test",EnumSet.allOf(EnemyAffixRegistry.Capability.class),100,true,false,false,4,true);
        var selection=new EnemyAffixSelection(EnemyAffixRegistry.canonical());
        template.validateBindings(selection,role->binding,(t,m)->true,id->false,id->false);
        assertThrows(IllegalArgumentException.class,()->template.validateBindings(selection,role->binding,(t,m)->false,id->false,id->false));
    }
    @Test void rejectsRewardSkillUnknownFieldAndUnsafeStatOverrides(){
        for(String key:List.of("teachesSkill","xpMultiplier","unknown")){
            var document=EnemyAffixRegistry.resource("super-uniques-v1.json");document.getAsJsonArray("templates").get(0).getAsJsonObject().addProperty(key,"bad");
            assertThrows(IllegalArgumentException.class,()->new SuperUniqueTemplates(document));
        }
        var document=EnemyAffixRegistry.resource("super-uniques-v1.json");
        document.getAsJsonArray("templates").get(0).getAsJsonObject().addProperty("directDamageFactor",3);
        assertThrows(IllegalArgumentException.class,()->new SuperUniqueTemplates(document));
    }
    @Test void cooldownReloadAndMissedCyclesNeverCreateCatchupSpawns(@TempDir Path directory){
        var first=anchor();SuperUniqueAnchor reserved;
        try(var store=new FileEncounterStore(directory)){
            store.placeEnemyAnchor(first);reserved=store.reserveEnemyAnchor(first.worldId(),first.anchorId(),1000);
            assertEquals(reserved,store.reserveEnemyAnchor(first.worldId(),first.anchorId(),1000));
        }
        try(var store=new FileEncounterStore(directory)){
            assertEquals(reserved,store.enemyAnchor(first.worldId(),first.anchorId()).orElseThrow());
            assertEquals(List.of(reserved),store.enemyAnchors(first.worldId()));
            assertTrue(store.enemyAnchors(UUID.randomUUID()).isEmpty());
            var actor=UUID.randomUUID();var pack=new EnemyPackRecord(1,UUID.randomUUID(),first.worldId(),reserved.encounterId(),reserved.cycle(),
                    EnemyPackRecord.State.RESERVED,null,first.position(),List.of(new EnemyPackRecord.Member(actor,actor,"Trork_Warrior",EnemyPackRecord.Role.LEADER)),actor,
                    Set.of(),Map.of(),false,false,"spawn-plan",null);
            assertNotEquals(pack.packId(),pack.encounterId());
            store.reserveEnemyPack(pack);store.transitionEnemyPack(first.worldId(),pack.packId(),EnemyPackRecord::staged);
            store.transitionEnemyPack(first.worldId(),pack.packId(),EnemyPackRecord::publish);
            var live=store.publishEnemyAnchor(first.worldId(),first.anchorId(),pack.packId());assertEquals(SuperUniqueAnchor.State.LIVE,live.state());
            assertThrows(IllegalStateException.class,()->store.settleEnemyAnchor(first.worldId(),first.anchorId(),pack.packId(),2000));
            store.transitionEnemyPack(first.worldId(),pack.packId(),p->p.abort("QA_ADMIN_ABORT"));
            var cooldown=store.settleEnemyAnchor(first.worldId(),first.anchorId(),pack.packId(),2000);
            assertEquals(902000,cooldown.nextEligibleMillis());assertFalse(cooldown.due(901999));
            assertEquals(cooldown,store.settleEnemyAnchor(first.worldId(),first.anchorId(),pack.packId(),99999));
            var next=store.reserveEnemyAnchor(first.worldId(),first.anchorId(),90_000_000);
            assertEquals(2,next.cycle());assertNotEquals(reserved.encounterId(),next.encounterId());
            assertEquals(SuperUniqueAnchor.State.REMOVED,store.removeEnemyAnchor(first.worldId(),first.anchorId(),null).state());
            assertEquals(SuperUniqueAnchor.State.REMOVED,store.enemyAnchors(first.worldId()).getFirst().state());
        }
    }
}
