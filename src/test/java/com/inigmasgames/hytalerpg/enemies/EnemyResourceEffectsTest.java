package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.damage.WeaponDamageExecution;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.NativeResult;
import com.inigmasgames.hytalerpg.combat.resource.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.Operator.*;
import static org.junit.jupiter.api.Assertions.*;

class EnemyResourceEffectsTest {
    final ReservationService reservations=new ReservationService();
    final RpgResourceService resources=new RpgResourceService(CombatBalanceProfile.loadCanonical(),reservations);
    final EnemyResourceEffects effects=new EnemyResourceEffects(resources);
    final UUID victim=UUID.randomUUID();
    EnemyDescriptor actor(EnemyAffixRegistry.Operator operator){
        var fixture=new EnemyAffixSnapshotTest();return fixture.actor(false,List.of(fixture.affix(operator)),List.of(),null);
    }
    EnemyAppliedHit hit(EnemyDescriptor actor,String strike,double health,double damage){
        var offense=new EnemyOffenseSnapshot(new WeaponDamageExecution.Identity(actor.worldId(),actor.logicalActorId(),"root","action",strike),
                actor.encounterGeneration(),actor.nativeBindingRevision(),"canonical",actor.balanceRevision(),1,
                Map.of("Physical",damage),damage,Map.of("Physical",damage),null,0);
        return EnemyAppliedHit.completed(offense,victim,Map.of("Physical",new NativeResult(false,damage,health,health-damage,damage)));
    }
    @Test void manaBurnUsesOnlyThisHitAndSpendableMana(){
        var actor=actor(MANA_BURN);var port=new EnemyResourceAdapterTest.Port();port.mana=40;
        assertEquals(4,effects.manaBurn(actor,hit(actor,"one",100,20),port,0,()->true).actual());
        assertEquals(4,effects.manaBurn(actor,hit(actor,"two",100,20),port,1,()->true).actual());
        assertEquals(32,port.mana);
        var held=resources.reserveCost(victim,new ResourceCost(ResourceType.MANA,30),port);
        assertEquals(2,effects.manaBurn(actor,hit(actor,"three",100,20),port,2,()->true).actual());
        assertEquals(30,port.mana);assertTrue(resources.refundIfUncommitted(held));
        assertTrue(effects.snapshot(3).budgets().isEmpty());
    }
    @Test void vampiricUsesActualNonOverkillLossAndNativeHealthClamp(){
        var actor=actor(VAMPIRIC);var port=new EnemyResourceAdapterTest.Port();port.health=99;
        assertEquals(.5,effects.vampiric(actor,hit(actor,"overkill",5,100),port,0,()->true).actual());
        assertEquals(.5,effects.vampiric(actor,hit(actor,"second",40,40),port,1,()->true).actual());
        port.health=80;
        assertEquals(4,effects.vampiric(actor,hit(actor,"third",40,40),port,2,()->true).actual());
        port.health=0;assertEquals(0,effects.vampiric(actor,hit(actor,"dead",40,40),port,3,()->true).actual());
    }
    @Test void protectionAndAbsentPoolsPerformNoWrites(){
        var actor=actor(MANA_BURN);var port=new EnemyResourceAdapterTest.Port();
        assertEquals(0,effects.manaBurn(actor,hit(actor,"protected",100,20),port,0,()->false).actual());
        port.manaPool=false;assertEquals(0,effects.manaBurn(actor,hit(actor,"no-pool",100,20),port,1,()->true).actual());
        assertEquals(0,port.writes);
    }
}
