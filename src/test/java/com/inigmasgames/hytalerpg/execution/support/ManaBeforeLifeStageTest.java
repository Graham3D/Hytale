package com.inigmasgames.hytalerpg.execution.support;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.resource.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ManaBeforeLifeStageTest {
    private static final UUID PLAYER=UUID.randomUUID();
    private static final class Port implements NativeResourcePort {
        double mana,maximum;boolean failBefore,failAfter,failReadBefore,failReadback,written;
        Port(double mana,double maximum){this.mana=mana;this.maximum=maximum;}
        public double current(ResourceType type){if(failReadBefore||failReadback&&written)throw new IllegalStateException("readback");return mana;}
        public double maximum(ResourceType type){return maximum;}
        public void setCurrent(ResourceType type,double value){
            if(failBefore)throw new IllegalStateException("prewrite");
            mana=value;written=true;if(failAfter)throw new IllegalStateException("postwrite");
        }
    }
    private static RpgResourceService owner(ReservationService reservations){
        return new RpgResourceService(CombatBalanceProfile.loadCanonical(),reservations);
    }
    @Test void capAndPartialManaForwardEveryUnpaidPointToHealth(){
        var resources=owner(new ReservationService());
        var ample=new Port(100,100);
        var capped=ManaBeforeLifeStage.apply(100,.65,PLAYER,resources,ample);
        assertEquals(60,capped.manaSpent());assertEquals(40,capped.healthDamage());
        var partial=new Port(10,100);
        var result=ManaBeforeLifeStage.apply(60,.5,PLAYER,resources,partial);
        assertEquals(10,result.manaSpent());assertEquals(50,result.healthDamage());
        assertEquals(0,partial.mana);
    }
    @Test void reservationsAndZeroManaDoNotForgiveDamage(){
        var reservations=new ReservationService();var port=new Port(100,100);
        reservations.addFixed(PLAYER,"managuard",80,port);
        var result=ManaBeforeLifeStage.apply(100,.6,PLAYER,owner(reservations),port);
        assertEquals(20,result.manaSpent());assertEquals(80,result.healthDamage());
        assertEquals(100,ManaBeforeLifeStage.apply(100,.6,PLAYER,owner(new ReservationService()),new Port(0,100)).healthDamage());
    }
    @Test void prewriteFailureKeepsHealthDamageAndPostwriteFailureUsesReadback(){
        var resources=owner(new ReservationService());
        var before=new Port(100,100);before.failBefore=true;
        var rejected=ManaBeforeLifeStage.apply(100,.5,PLAYER,resources,before);
        assertTrue(rejected.uncertain());assertEquals(0,rejected.manaSpent());assertEquals(100,rejected.healthDamage());
        var after=new Port(100,100);after.failAfter=true;
        var confirmed=ManaBeforeLifeStage.apply(100,.5,PLAYER,resources,after);
        assertTrue(confirmed.uncertain());assertEquals(50,confirmed.manaSpent());assertEquals(50,confirmed.healthDamage());
    }
    @Test void unreadablePostDebitNeverPublishesUnconfirmedProtection(){
        var port=new Port(100,100);port.failReadback=true;
        var result=ManaBeforeLifeStage.apply(100,.5,PLAYER,owner(new ReservationService()),port);
        assertEquals(50,port.mana);assertTrue(result.uncertain());assertEquals(0,result.manaSpent());assertEquals(100,result.healthDamage());
    }
    @Test void unavailableInitialManaAndZeroAffixLeaveDamageIntact(){
        var owner=owner(new ReservationService());var port=new Port(100,100);
        assertEquals(35,ManaBeforeLifeStage.apply(35,0,PLAYER,owner,port).healthDamage());
        port.failReadBefore=true;
        var result=ManaBeforeLifeStage.apply(35,.4,PLAYER,owner,port);
        assertTrue(result.uncertain());assertEquals(35,result.healthDamage());assertEquals(100,port.mana);
        assertThrows(IllegalArgumentException.class,()->ManaBeforeLifeStage.apply(-1,.4,PLAYER,owner,port));
        assertThrows(IllegalArgumentException.class,()->ManaBeforeLifeStage.apply(10,Double.NaN,PLAYER,owner,port));
    }
    @Test void concurrentHitsCannotClaimTheSameNativeManaDebit() throws Exception {
        var resources=owner(new ReservationService());var port=new Port(100,100);
        var pool=Executors.newFixedThreadPool(8);var ready=new CountDownLatch(8);var start=new CountDownLatch(1);
        try{
            var jobs=new java.util.ArrayList<Future<ManaBeforeLifeStage.Resolution>>();
            for(int i=0;i<8;i++)jobs.add(pool.submit(()->{ready.countDown();start.await();
                return ManaBeforeLifeStage.apply(10,.5,PLAYER,resources,port);}));
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();
            double mana=0,health=0;
            for(var job:jobs){var result=job.get(10,TimeUnit.SECONDS);mana+=result.manaSpent();health+=result.healthDamage();}
            assertEquals(40,mana);assertEquals(40,health);assertEquals(60,port.mana);
        }finally{start.countDown();pool.shutdownNow();}
    }
}
