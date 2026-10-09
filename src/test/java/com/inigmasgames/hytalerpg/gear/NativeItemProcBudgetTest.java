package com.inigmasgames.hytalerpg.gear;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NativeItemProcBudgetTest {
    private static final UUID WORLD=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_WORLD=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ACTOR=UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ITEM=UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static UUID victim(int n){return new UUID(0,100+n);}
    private final NativeItemProcBudget budget=new NativeItemProcBudget();
    private double credit(UUID world,String root,UUID item,Object strike,int victim,String contact,
                          String channel,double coefficient,boolean admitted){
        return budget.nativeCredit(world,ACTOR,root,item,"revision",strike,victim(victim),contact,
                channel,coefficient,admitted);
    }
    @Test void controlDuplicateChannelsAndCancelledFirstComponent(){
        Object strike=new Object();
        assertEquals(0,credit(WORLD,"root",ITEM,strike,1,"contact","PHYSICAL",1,false));
        assertEquals(1,credit(WORLD,"root",ITEM,strike,1,"contact","PHYSICAL",1,true));
        assertEquals(1,credit(WORLD,"root",ITEM,strike,1,"contact","FIRE",1,true));
        assertEquals(0,credit(WORLD,"root",ITEM,strike,1,"contact","FIRE",1,true));
        assertEquals(0,credit(WORLD,"root",ITEM,new Object(),2,"second","PHYSICAL",1,true));
        assertEquals(1,budget.size());
    }
    @Test void crowdsAndPelletsShareOneRootAllowance(){
        double total=0;
        for(int pellet=0;pellet<7;pellet++){
            Object strike=new Object();
            for(int victim=0;victim<30;victim++)
                total+=credit(WORLD,"volley",ITEM,strike,victim,"shared/"+victim,
                        "PHYSICAL",1d/3,true);
        }
        assertEquals(1,total,1e-12);
    }
    @Test void wrongWorldSourceChangeAndCleanup(){
        Object strike=new Object();
        assertEquals(.5,credit(WORLD,"root",ITEM,strike,1,"one","PHYSICAL",.5,true));
        assertEquals(.5,credit(OTHER_WORLD,"root",ITEM,strike,1,"one","PHYSICAL",.5,true));
        assertThrows(IllegalStateException.class,()->credit(WORLD,"root",UUID.randomUUID(),
                new Object(),2,"two","PHYSICAL",.5,true));
        assertEquals(.5,credit(WORLD,"root",ITEM,new Object(),1,
                "one","FIRE",.5,true));
        budget.clearWorld(WORLD);
        assertEquals(1,budget.size());
        budget.clearActor(ACTOR);
        assertEquals(0,budget.size());
    }
    @Test void paidWindowsSharePaidDeltaAndUnpaidTicksHaveNoCredit(){
        Object strike=new Object();
        assertEquals(0,budget.paidWindowCredit(WORLD,ACTOR,"channel","unpaid",ITEM,"revision",
                strike,victim(1),"tick","PHYSICAL",0,false));
        double total=0;
        for(int tick=0;tick<20;tick++)for(int target=0;target<10;target++)
            total+=budget.paidWindowCredit(WORLD,ACTOR,"channel","window-1",ITEM,"revision",
                    strike,victim(target),"tick/"+tick+"/"+target,"PHYSICAL",.4,true);
        assertEquals(.4,total,1e-12);
        assertEquals(.2,budget.paidWindowCredit(WORLD,ACTOR,"channel","window-2",ITEM,"revision",
                new Object(),victim(1),"tick","PHYSICAL",.2,true));
    }
    @Test void declaredAreaSharesEquallyRegardlessOfDamageOrder(){
        var victims=java.util.List.of(victim(3),victim(1),victim(2));
        assertFalse(budget.areaDeclared(WORLD,ACTOR,"area",ITEM,"revision","cast/strike/0/"));
        budget.declareArea(WORLD,ACTOR,"area",ITEM,"revision","cast/strike/0/",victims,1);
        assertTrue(budget.areaDeclared(WORLD,ACTOR,"area",ITEM,"revision","cast/strike/0/"));
        double total=0;
        for(int n:java.util.List.of(3,1,2)){
            Object strike=new Object();String contact="cast/strike/0/"+victim(n);
            assertEquals(1d/3,credit(WORLD,"area",ITEM,strike,n,contact,"PHYSICAL",1,true),1e-12);
            assertEquals(1d/3,credit(WORLD,"area",ITEM,strike,n,contact,"FIRE",1,true),1e-12);
            total+=1d/3;
            assertEquals(0,credit(WORLD,"area",ITEM,new Object(),n,contact+"/retry","PHYSICAL",1,true));
        }
        assertEquals(1,total,1e-12);
        assertEquals(0,credit(WORLD,"area",ITEM,new Object(),4,"cast/strike/0/"+victim(4),"PHYSICAL",1,true));
    }
    @Test void areaReservesOnlyRemainingRootCreditAndCancelledVictimDoesNotReallocate(){
        assertEquals(.4,credit(WORLD,"mixed",ITEM,new Object(),9,"primary","PHYSICAL",.4,true));
        budget.declareArea(WORLD,ACTOR,"mixed",ITEM,"revision","area/",java.util.List.of(victim(2),victim(1)),1);
        assertEquals(0,credit(WORLD,"mixed",ITEM,new Object(),1,"area/one","PHYSICAL",1,false));
        assertEquals(.3,credit(WORLD,"mixed",ITEM,new Object(),2,"area/two","PHYSICAL",1,true),1e-12);
        assertEquals(0,credit(WORLD,"mixed",ITEM,new Object(),8,"later","PHYSICAL",1,true));
        assertThrows(IllegalStateException.class,()->budget.declareArea(WORLD,ACTOR,"mixed",ITEM,
                "revision","area/",java.util.List.of(victim(3)),1));
    }
    @Test void authoredSequentialStrikesKeepDistinctOrdinalsWithinStableCastRoot(){
        for(int ordinal=0;ordinal<2;ordinal++){
            String prefix="cast/strike/"+ordinal+"/";
            budget.declareSequentialStrikeArea(WORLD,ACTOR,"cast",ITEM,"revision",prefix,
                    java.util.List.of(victim(2),victim(1)),1);
            assertEquals(.5,credit(WORLD,"cast",ITEM,new Object(),1,prefix+victim(1),"PHYSICAL",1,true));
            assertEquals(.5,credit(WORLD,"cast",ITEM,new Object(),2,prefix+victim(2),"PHYSICAL",1,true));
        }
        assertEquals(1,budget.size());
    }
    @Test void declaredPaidIntervalUsesActualDeltaAndStableEqualShares(){
        budget.declarePaidWindowArea(WORLD,ACTOR,"channel","tick-7",ITEM,"revision","paid/7/",
                java.util.List.of(victim(3),victim(1),victim(2)),.4);
        for(int n:java.util.List.of(3,1,2))
            assertEquals(.4/3,budget.paidWindowCredit(WORLD,ACTOR,"channel","tick-7",ITEM,
                    "revision",new Object(),victim(n),"paid/7/"+victim(n),"PHYSICAL",.4,true),1e-12);
        assertEquals(0,budget.paidWindowCredit(WORLD,ACTOR,"channel","tick-7",ITEM,
                "revision",new Object(),victim(4),"paid/7/"+victim(4),"PHYSICAL",.4,true));
        assertEquals(0,budget.paidWindowCredit(WORLD,ACTOR,"channel","unpaid",ITEM,
                "revision",new Object(),victim(1),"paid/8/"+victim(1),"PHYSICAL",0,false));
    }
}
