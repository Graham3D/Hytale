package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.domain.*;
import com.inigmasgames.hytalerpg.execution.summon.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage10DecoyTest {
    private Stage10SummonTest.Harness harness(){return new Stage10SummonTest.Harness("simulacrum");}
    @Test void canonicalCostCooldownHealthAndLifetime(){
        var h=harness();assertTrue(h.cast().committed());assertEquals(80,h.mana);
        var lease=h.leases.getFirst();assertEquals(25,lease.maximumHealth());assertEquals(8,lease.expires());
        assertEquals(0,lease.coefficient());assertEquals(4,h.context.profile().summon().range());
        assertEquals(18,h.context.profile().cooldownSeconds());assertEquals("RPG_Summon_Decoy",lease.roleId());
    }
    @Test void neverClaimsDamageIncludingAfterLongTickStall(){
        var h=harness();h.cast();var lease=h.leases.getFirst();h.summons.activate(lease,UUID.randomUUID(),0);
        for(double now:new double[]{0,1,2,7.999,8,99})assertEquals(0,h.summons.claimAttack(lease.token(),now));
    }
    @Test void secondDecoyRejectsBeforeAnotherChargeEvenWhenCooldownCleared(){
        var h=harness();h.cast();h.kernel.cooldowns().clear(h.actor);
        assertFalse(h.cast().committed());assertEquals(80,h.mana);assertEquals(1,h.summons.size());
        assertEquals("DECOY_ALREADY_ACTIVE",h.summons.admission(h.actor,1,true));
    }
    @Test void capIncludesPendingUnspawnedDecoy(){
        var h=harness();h.cast();assertThrows(IllegalStateException.class,()->h.summons.reserve(Stage10SummonTest.copy(h.context,h.actor,"next"),0));
    }
    @Test void distinctOwnersCanEachHaveOneDecoy(){
        var h=harness();h.cast();var owner=UUID.randomUUID();h.summons.reserve(Stage10SummonTest.copy(h.context,owner,"other"),0);
        assertEquals(2,h.summons.size());assertEquals(1,h.summons.cancel(owner).size());assertEquals(1,h.summons.size());
    }
    @Test void combatSummonDoesNotConsumeDecoySingletonSlot(){
        var h=harness();h.cast();assertEquals("PASS",h.summons.admission(h.actor,1,false));
    }
    @Test void deathPactAndSwarmCannotTurnDecoyIntoCombatSummon(){
        for(String passive:List.of("death_pact","swarm")){
            var h=harness();assertTrue(h.bundle.service().equipPassive(h.actor,PassiveSlot.PASSIVE01,new PassiveId(passive)).success());
            assertFalse(h.bundle.service().link(h.actor,LinkNodeId.PASSIVE01,LinkNodeId.SKILL01).success());
            assertTrue(h.cast().committed());assertEquals(1,h.leases.size());assertEquals(0,h.leases.getFirst().coefficient());
        }
    }
    @Test void empowermentChangesOnlyHpAndLifetimeNotDamageOrCount(){
        var h=harness();h.link("minion_empowerment",PassiveSlot.PASSIVE01);assertTrue(h.cast().committed());
        var lease=h.leases.getFirst();assertEquals(32.5,lease.maximumHealth());assertEquals(6,lease.expires());assertEquals(0,lease.coefficient());assertEquals(1,h.leases.size());
    }
    @Test void cannotSacrificeNoncombatDecoy(){
        var h=harness();h.cast();var lease=h.leases.getFirst();h.summons.activate(lease,UUID.randomUUID(),0);
        assertTrue(h.summons.consume(h.actor,lease.world(),lease.entity(),1,()->fail("Decoy must not grant combat benefit")).isEmpty());
    }
    @Test void cancellationBlocksQueuedNativeSpawn(){
        var h=harness();h.cast();var lease=h.leases.getFirst();h.summons.cancel(h.actor);assertFalse(h.summons.activate(lease,UUID.randomUUID(),1));
    }
    @Test void terminationNeverCreatesDeathPact(){
        for(var reason:SummonRegistry.EndReason.values()){
            var h=harness();h.cast();var l=h.leases.getFirst();h.summons.activate(l,UUID.randomUUID(),0);
            assertFalse(h.summons.end(l.token(),reason).orElseThrow().deathPact());assertEquals("PASS",h.summons.admission(h.actor,1,true));
        }
    }
    @Test void onlyExplicitEncounterAlreadyAttackingCasterCanBeAttracted(){
        assertTrue(DecoyAttractionPolicy.accepts("Wolf_Black",true,false,true,true));
        for(String role:List.of("Unknown","Skeleton_Elite","Test_Boss_Basic","Player","RPG_Summon_Wolf"))
            assertFalse(DecoyAttractionPolicy.accepts(role,true,false,true,true));
        assertFalse(DecoyAttractionPolicy.accepts("Wolf_Black",false,false,true,true));
        assertFalse(DecoyAttractionPolicy.accepts("Wolf_Black",true,true,true,true));
        assertFalse(DecoyAttractionPolicy.accepts("Wolf_Black",true,false,false,true));
        assertFalse(DecoyAttractionPolicy.accepts("Wolf_Black",true,false,true,false));
    }
    @Test void malformedDecoyCannotIntroduceCountOrDamage(){
        assertThrows(IllegalArgumentException.class,()->new SummonProfile("RPG_Summon_Decoy","ARCANE",4,2,.25,0,8,1,24,false,true));
        assertThrows(IllegalArgumentException.class,()->new SummonProfile("RPG_Summon_Decoy","ARCANE",4,1,.25,.1,8,1,24,false,true));
        assertThrows(IllegalArgumentException.class,()->new SummonProfile("RPG_Summon_Decoy","ARCANE",4,1,.25,0,8,1,24,true,true));
    }
}
