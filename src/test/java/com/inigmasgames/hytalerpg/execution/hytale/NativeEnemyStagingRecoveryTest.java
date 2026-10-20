package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.ProgressionMath;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeEnemyStagingRecoveryTest {
    private static UUID id(String value){return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));}
    private static EnemyBirthPlan promotedBirth(){
        var balance=EnemyBalance.canonical();
        var planner=new EnemyBirthPlanner(balance,EnemyAffixRegistry.canonical(),EnemyAffinityRegistry.canonical(),
                EnemyNamePools.canonical(),EnemyVisualVariants.canonical());
        var candidates=new ArrayList<EnemyBirthPlanner.Candidate>();
        for(int index=0;index<8;index++){
            var nativeId=id("staging-native/"+index);
            var descriptor=new EnemyDescriptor(1,1,balance.revision(),"recovery-fixture",id("staging-world"),
                    id("staging-encounter"),3,nativeId,nativeId,"cycle",EnemyRewardContext.Origin.NATURAL,
                    "Trork_Warrior","Trork_Warrior",50,DifficultyId.HELL,ProgressionMath.Rank.COMMON,
                    EnemyRarity.NORMAL,EnemyDescriptor.PackRole.NONE,null,null,"source-proof","native-loot",
                    "learning","Trork Warrior","baseline-name",null,List.of(),List.of(),Set.of(),List.of(),
                    "native-control",balance.rewards(EnemyRarity.NORMAL,EnemyRewardContext.Origin.NATURAL,false,0),
                    "a".repeat(64),null);
            candidates.add(new EnemyBirthPlanner.Candidate(descriptor,
                    new EnemyAffixSelection.Binding("recovery-fixture",EnumSet.allOf(EnemyAffixRegistry.Capability.class),
                            0,true,false,false,0,true),1));
        }
        for(int attempt=0;attempt<10000;attempt++){
            var request=new EnemyBirthPlanner.Request("recovery-birth/"+attempt,id("staging-pack"),Vec3.ZERO,
                    "native-job/recovery",candidates.subList(0,2),candidates.subList(2,8),true,6,true);
            var result=planner.plan(request);
            if(result.promoted()&&result.plan().actors().size()>2)return result.plan();
        }
        throw new AssertionError("No promoted additional-member fixture");
    }
    @Test void onlyTheExactCompensatedBirthCanReleaseSavedOriginalsOrDiscardExtras(){
        var birth=promotedBirth();
        var compensation=new EnemyBirthCompensation(birth.world(),birth.encounter(),birth.generation(),birth.seed());
        var original=birth.actors().getFirst();
        var extra=birth.actors().stream().filter(actor->!birth.originalNativeEntities().contains(actor.entityId()))
                .findFirst().orElseThrow();
        var originalState=new EnemyStaging.State(birth.world(),birth.encounter(),original.entityId(),birth.generation(),7);
        var extraState=new EnemyStaging.State(birth.world(),birth.encounter(),extra.entityId(),birth.generation(),15);
        assertTrue(NativeEnemyStagingRecovery.recoveryMatches(originalState,birth,compensation,
                EnemyActorIdentity.State.of(original)));
        assertTrue(NativeEnemyStagingRecovery.recoveryMatches(extraState,birth,compensation,
                EnemyActorIdentity.State.of(extra)));
        assertFalse(NativeEnemyStagingRecovery.recoveryMatches(originalState,birth,null,null));
        assertFalse(NativeEnemyStagingRecovery.recoveryMatches(extraState,birth,
                new EnemyBirthCompensation(birth.world(),birth.encounter(),birth.generation(),"other-seed"),null));
        assertFalse(NativeEnemyStagingRecovery.recoveryMatches(new EnemyStaging.State(birth.world(),birth.encounter(),
                extra.entityId(),birth.generation(),7),birth,compensation,null));
        assertFalse(NativeEnemyStagingRecovery.recoveryMatches(originalState,birth,compensation,
                EnemyActorIdentity.State.of(extra)));
        assertTrue(NativeEnemyStagingRecovery.recoveryMatches(originalState,null,null,null));
        assertTrue(NativeEnemyStagingRecovery.recoveryMatches(extraState,null,null,null));
        assertFalse(NativeEnemyStagingRecovery.recoveryMatches(originalState,null,null,
                EnemyActorIdentity.State.of(original)));
    }
    @Test void publishedExtraRestagesWithoutAnExtraBitButPreIdentityExtraMustRetainIt(){
        var birth=promotedBirth();
        var extra=birth.actors().stream().filter(actor->!birth.originalNativeEntities().contains(actor.entityId()))
                .findFirst().orElseThrow();
        var postPublicationLoad=new EnemyStaging.State(birth.world(),birth.encounter(),extra.entityId(),birth.generation(),7);
        var interruptedPreIdentity=new EnemyStaging.State(birth.world(),birth.encounter(),extra.entityId(),birth.generation(),15);
        assertTrue(NativeEnemyWholeBirthRecovery.validStagingProvenance(birth,extra,postPublicationLoad,
                EnemyActorIdentity.State.of(extra)));
        assertFalse(NativeEnemyWholeBirthRecovery.validStagingProvenance(birth,extra,postPublicationLoad,null));
        assertTrue(NativeEnemyWholeBirthRecovery.validStagingProvenance(birth,extra,interruptedPreIdentity,null));
        assertFalse(NativeEnemyWholeBirthRecovery.validStagingProvenance(birth,extra,interruptedPreIdentity,
                EnemyActorIdentity.State.of(birth.actors().getFirst())));
    }
}
