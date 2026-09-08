package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import com.inigmasgames.hytalerpg.combat.resource.ReservationService;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class Stage09ReservationTest {
    final UUID actor=UUID.randomUUID();
    final ReservationService service=new ReservationService();
    final Mana mana=new Mana();
    @Test void activationDebitsEvenWhenBelowNewCap() {
        mana.current=60;
        service.addPercentage(actor,"a",.2,mana);
        assertEquals(40,mana.current); assertEquals(80,service.spendableMaximum(actor,100));
    }
    @Test void insufficientCurrentRejectsWithoutPublishing() {
        mana.current=19;
        assertThrows(IllegalStateException.class,()->service.addPercentage(actor,"a",.2,mana));
        assertEquals(19,mana.current); assertTrue(service.reservations(actor).isEmpty());
    }
    @Test void ignoredNativeWriteRejectsWithoutPublishing() {
        mana.ignore=true;
        assertThrows(IllegalStateException.class,()->service.addPercentage(actor,"a",.2,mana));
        assertEquals(100,mana.current); assertTrue(service.reservations(actor).isEmpty());
    }
    @Test void exceptionAfterNativeWriteRollsBackWithoutPublishing() {
        mana.failOnce=true;
        assertThrows(IllegalStateException.class,()->service.addPercentage(actor,"a",.2,mana));
        assertEquals(100,mana.current); assertTrue(service.reservations(actor).isEmpty());
    }
    @Test void replacementChargesOnlyPositiveDelta() {
        service.addPercentage(actor,"a",.2,mana);
        service.addPercentage(actor,"a",.3,mana); assertEquals(70,mana.current);
        service.addPercentage(actor,"a",.3,mana); assertEquals(70,mana.current);
        service.addPercentage(actor,"a",.1,mana); assertEquals(70,mana.current);
    }
    @Test void failedReplacementPreservesOldAllocationAndMana() {
        service.addPercentage(actor,"a",.2,mana); mana.current=5;
        assertThrows(IllegalStateException.class,()->service.addPercentage(actor,"a",.3,mana));
        assertEquals(.2,service.reservations(actor).get("a").value()); assertEquals(5,mana.current);
    }
    @Test void percentagesUseTotalNotSuccessiveSpendable() {
        service.addPercentage(actor,"a",.5,mana); service.addPercentage(actor,"b",.1,mana);
        assertEquals(60,service.reserved(actor,100)); assertEquals(40,mana.current);
    }
    @Test void removeAndRemoveAllNeverRefundMana() {
        service.addPercentage(actor,"a",.5,mana); service.addFixed(actor,"b",10,mana);
        service.remove(actor,"a"); service.removeAll(actor);
        assertEquals(40,mana.current); assertEquals(100,service.spendableMaximum(actor,100));
    }
    @Test void oversubscriptionDoesNotChargeOrPublish() {
        service.addPercentage(actor,"a",.8,mana);
        assertThrows(IllegalStateException.class,()->service.addFixed(actor,"b",21,mana));
        assertEquals(20,mana.current); assertEquals(List.of("a"),List.copyOf(service.reservations(actor).keySet()));
    }
    @Test void maximumReductionCancelsNewestFirst() {
        service.addFixed(actor,"old",30,mana); service.addFixed(actor,"new",30,mana);
        mana.max=40;
        assertEquals(List.of("new"),service.reconcileMaximum(actor,mana));
        assertEquals(10,mana.current); assertEquals(List.of("old"),List.copyOf(service.reservations(actor).keySet()));
    }
    @Test void maximumIncreaseNeverRefills() {
        service.addPercentage(actor,"a",.5,mana); mana.max=200;
        assertEquals(List.of(),service.reconcileMaximum(actor,mana));
        assertEquals(50,mana.current); assertEquals(100,service.reserved(actor,200));
    }
    @Test void resizeKeepsOriginalActivationOrder() {
        service.addFixed(actor,"old",20,mana); service.addFixed(actor,"new",20,mana);
        service.addFixed(actor,"old",30,mana); mana.max=40;
        assertEquals(List.of("new"),service.reconcileMaximum(actor,mana));
    }
    @Test void fractionalDebitsDoNotUseUpfrontIntegerRounding() {
        mana.max=101; mana.current=101;
        service.addPercentage(actor,"a",.01,mana); assertEquals(99.99,mana.current,1e-9);
    }
    @Test void invalidInputsNeverPublish() {
        assertThrows(IllegalArgumentException.class,()->service.addPercentage(actor,"a",Double.NaN,mana));
        assertThrows(IllegalArgumentException.class,()->service.addFixed(actor,"a",Double.POSITIVE_INFINITY,mana));
        assertTrue(service.reservations(actor).isEmpty());
    }
    static class Mana implements NativeResourcePort {
        double current=100,max=100; boolean ignore,failOnce;
        public double current(ResourceType type){return current;}
        public double maximum(ResourceType type){return max;}
        public void setCurrent(ResourceType type,double value){
            if(ignore)return;
            current=Math.max(0,Math.min(max,value));
            if(failOnce){failOnce=false;throw new IllegalStateException("fixture native write failed after mutation");}
        }
    }
}
