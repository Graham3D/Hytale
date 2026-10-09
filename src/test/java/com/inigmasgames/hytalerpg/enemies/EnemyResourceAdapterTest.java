package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.resource.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyResourceAdapterTest {
    static final class Port implements NativeResourcePort{
        double health=90,mana=100;boolean manaPool=true;int writes;
        public boolean hasResource(ResourceType type){return type==ResourceType.HEALTH||type==ResourceType.MANA&&manaPool;}
        public double current(ResourceType type){return type==ResourceType.HEALTH?health:mana;}
        public double maximum(ResourceType type){return 100;}
        public void setCurrent(ResourceType type,double value){writes++;if(type==ResourceType.HEALTH)health=value;else mana=value;}
    }
    @Test void manaDrainPreservesCapacityReservationsAndPendingCosts(){
        var reservations=new ReservationService();var resources=new RpgResourceService(CombatBalanceProfile.loadCanonical(),reservations);
        var actor=UUID.randomUUID();var port=new Port();reservations.addPercentage(actor,"existing-aura",.5,port);
        var held=resources.reserveCost(actor,new ResourceCost(ResourceType.MANA,30),port);
        assertEquals(20,resources.drainSpendableMana(actor,90,port,()->true));assertEquals(30,port.mana);assertEquals(90,port.health);
        assertEquals(50,reservations.reserved(actor,100));assertEquals(0,resources.drainSpendableMana(actor,90,port,()->true));
        assertTrue(resources.commitCost(held,port));assertEquals(0,port.mana);assertEquals(50,reservations.reserved(actor,100));
    }
    @Test void protectedNoPoolAndDeadRecipientsCannotBeMutated(){
        var resources=new RpgResourceService(CombatBalanceProfile.loadCanonical(),new ReservationService());var port=new Port();var actor=UUID.randomUUID();
        assertEquals(0,resources.drainSpendableMana(actor,50,port,()->false));assertEquals(0,resources.creditLivingHealth(50,port,()->false));assertEquals(0,port.writes);
        port.manaPool=false;assertEquals(0,resources.drainSpendableMana(actor,50,port,()->true));
        assertEquals(10,resources.creditLivingHealth(50,port,()->true));port.health=0;assertEquals(0,resources.creditLivingHealth(50,port,()->true));assertEquals(1,port.writes);
    }
    @Test void nativeFloatRoundingCannotExceedDebitOrCreditAllowance(){
        for(float current:new float[]{.001f,1,100,1000000})for(double amount:new double[]{.00000001,.0001,.1,1,100}){
            float after=RpgResourceService.nativeDebitTarget(current,amount,0);
            assertTrue(after<=current);assertTrue((double)current-after<=amount);
            float healed=RpgResourceService.nativeCreditTarget(current,amount,Float.MAX_VALUE);
            assertTrue(healed>=current);assertTrue((double)healed-current<=amount);
        }
    }
}
