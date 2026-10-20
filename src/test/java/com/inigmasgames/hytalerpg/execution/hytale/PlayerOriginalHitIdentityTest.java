package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.progress.FileEncounterStore;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PlayerOriginalHitIdentityTest {
    @TempDir Path directory;

    @Test void sameAcceptedSkillActionReplaysToTheSameHitReceiptAfterReopen(){
        UUID world=UUID.randomUUID(),player=UUID.randomUUID(),victim=UUID.randomUUID();
        String root,first,second;
        try(var store=new FileEncounterStore(directory)){
            root=store.bindPlayerActionRoot(world,player,"cast-accepted-1");
            assertEquals(root,store.bindPlayerActionRoot(world,player,"cast-accepted-1"));
            first=PlayerOriginalHitIdentity.skill(root,"instance-1","direct",0,victim);
            second=PlayerOriginalHitIdentity.skill(root,"instance-1","direct",1,victim);
            assertNotEquals(first,second);
        }
        try(var reloaded=new FileEncounterStore(directory)){
            String replayRoot=reloaded.bindPlayerActionRoot(world,player,"cast-accepted-1");
            assertEquals(root,replayRoot);
            assertEquals(first,PlayerOriginalHitIdentity.skill(replayRoot,"instance-1","direct",0,victim));
            assertNotEquals(root,reloaded.bindPlayerActionRoot(world,player,"cast-accepted-2"));
        }
    }

    @Test void overlappingProjectilesKeepSeparateExactOriginalReceipts(){
        UUID world=UUID.randomUUID(),player=UUID.randomUUID(),victim=UUID.randomUUID();
        try(var store=new FileEncounterStore(directory)){
            var roots=store.reservePlayerHitRoots(world,player,2);
            String a=PlayerOriginalHitIdentity.projectile(roots.id(roots.first()),"arrow-A",victim);
            String b=PlayerOriginalHitIdentity.projectile(roots.id(roots.last()),"arrow-B",victim);
            assertNotEquals(a,b);
            assertEquals(a,PlayerOriginalHitIdentity.projectile(roots.id(roots.first()),"arrow-A",victim));
            assertNotEquals(a,PlayerOriginalHitIdentity.projectile(roots.id(roots.first()),"arrow-A",UUID.randomUUID()));
        }
    }
}
