package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.cooldown.*;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.progress.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Stage09CooldownPersistenceTest {
    @TempDir Path temp;
    @Test void startPersistsBeforeReturningAndDisconnectIsNotReset(){
        var h=new Harness();h.start();assertEquals(10,h.saved.get("skill").remainingWork());h.now=2_000_000_000L;h.cd.detach(h.actor);
        assertEquals(8,h.saved.get("skill").remainingWork());h.now=100_000_000_000L;assertEquals(8,h.cd.remaining(h.actor,"skill"));
    }
    @Test void auraRateExpiresAtDisconnectAndIsNeverSerialized(){
        var h=new Harness();h.cd.setAuraRate(h.actor,.15,1,.25);h.start();h.now=200_000_000L;h.cd.detach(h.actor);h.now=10_000_000_000L;
        assertEquals(9.77,h.cd.remaining(h.actor,"skill"),1e-9);assertEquals(Set.of("remainingWork","baseRecovery","queued"),new com.google.gson.Gson().toJsonTree(h.saved.get("skill")).getAsJsonObject().keySet());
    }
    @Test void failedStartSaveDoesNotPublishAnUnrecordedCooldown(){
        var h=new Harness();h.fail=true;assertThrows(IllegalStateException.class,h::start);assertEquals(0,h.cd.remaining(h.actor,"skill"));assertTrue(h.saved.isEmpty());
    }
    @Test void failedDisconnectSaveKeepsLiveWorkRatherThanClearingIt(){
        var h=new Harness();h.start();h.fail=true;h.now=2_000_000_000L;assertThrows(IllegalStateException.class,()->h.cd.detach(h.actor));assertEquals(8,h.cd.remaining(h.actor,"skill"));
    }
    @Test void checkpointCadenceIsBoundedAndCrashCanOnlyRetainConservativeOlderWork(){
        var h=new Harness();h.start();for(int i=1;i<=9;i++){h.now=i*100_000_000L;h.cd.checkpoint(h.actor);}assertEquals(1,h.saves);
        h.now=1_000_000_000L;h.cd.checkpoint(h.actor);assertEquals(2,h.saves);assertEquals(9,h.saved.get("skill").remainingWork());
    }
    @Test void explicitClearPersistsAndDoesNotClearAnotherSkill(){
        var h=new Harness();h.start();h.cd.startCooldown(h.actor,"second",5,1,0,CompiledSkillPlan.KernelModifiers.NONE);h.cd.clear(h.actor,"skill");
        assertEquals(Set.of("second"),h.saved.keySet());h.cd.detach(h.actor);assertEquals(0,h.cd.remaining(h.actor,"skill"));assertEquals(5,h.cd.remaining(h.actor,"second"));
    }
    @Test void savedCooldownMapRejectsUnboundedOrMalformedState(){
        assertThrows(IllegalArgumentException.class,()->new SavedCooldown(Double.NaN,0));assertThrows(IllegalArgumentException.class,()->new SavedCooldown(1,-1));
        var values=new HashMap<String,SavedCooldown>();for(int i=0;i<90;i++)values.put("s"+i,new SavedCooldown(1,0));
        assertThrows(IllegalArgumentException.class,()->SavedCooldown.validate(values));
    }
    @Test void failedCheckpointRetriesAreBoundedToOnePerSecond(){
        var h=new Harness();h.start();h.fail=true;h.now=1_000_000_000L;
        assertThrows(IllegalStateException.class,()->h.cd.checkpoint(h.actor));
        for(int i=1;i<10;i++){h.now=1_000_000_000L+i*100_000_000L;assertFalse(h.cd.checkpoint(h.actor));}
    }
    @Test void playerRepositoryPersistsWorkWithDeficitAndLoadoutUnchanged(){
        var state=RpgPlayerState.create(UUID.randomUUID());state.cooldowns.put("reaping_storm",new SavedCooldown(31,.1));
        state.support=state.support.guard(new com.inigmasgames.hytalerpg.combat.barrier.ManaguardLedger(7,50,50,9));state.equippedSkills[0]="reaping_storm";
        var repo=new FileRpgPlayerStateRepository(temp);repo.save(state);var loaded=repo.load(state.playerUuid()).state();
        assertEquals(state.cooldowns,loaded.cooldowns);assertEquals(state.support,loaded.support);assertEquals("reaping_storm",loaded.equippedSkills[0]);
    }
    @Test void cooldownSaveCannotClobberSupportRevisionOrRecompileTheLoadout(){
        var b=Stage01BTestSupport.bundle();var id=UUID.randomUUID();var old=b.service().getPresentationView(id).state();
        b.service().mutateSupport(id,0,s->s.guard(new com.inigmasgames.hytalerpg.combat.barrier.ManaguardLedger(10,50,50,8)));
        b.service().saveCooldowns(id,Map.of("minor_heal",new SavedCooldown(3,0)));
        var current=b.service().getPresentationView(id).state();assertEquals(1,current.support.revision());assertEquals(10,current.support.managuard().deficit());assertEquals(old.revision,current.revision);
    }
    static class Harness implements RpgCooldownService.Persistence {
        long now;boolean fail;int saves;final UUID actor=UUID.randomUUID();Map<String,SavedCooldown> saved=Map.of();
        final RpgCooldownService cd=new RpgCooldownService(CombatBalanceProfile.loadCanonical(),()->now);
        Harness(){cd.bindPersistence(this);}
        void start(){cd.startCooldown(actor,"skill",10,1,0,CompiledSkillPlan.KernelModifiers.NONE);}
        public Map<String,SavedCooldown> load(UUID actor){return saved;}
        public void save(UUID actor,Map<String,SavedCooldown> next){if(fail)throw new IllegalStateException("disk failure");saved=Map.copyOf(next);saves++;}
    }
}
