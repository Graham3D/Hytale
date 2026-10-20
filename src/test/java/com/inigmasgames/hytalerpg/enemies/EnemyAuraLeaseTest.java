package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects.*;
import static org.junit.jupiter.api.Assertions.*;

class EnemyAuraLeaseTest {
    final UUID world=UUID.randomUUID(),leader=UUID.randomUUID(),member=UUID.randomUUID();
    final StatSource source=new StatSource(world,leader,4,SourceKind.MONSTER_AFFIX,"ME-023");
    StatEffect lease(UUID target,Stat stat,double value,double start){return new StatEffect(new StatKey(source,target,4,stat),value,start,start+.5);}
    @Test void replacementRemovesDepartedMembersAndExpiryDoesNotLeakAcrossWorldOrGeneration(){
        var owner=new FiniteSupportEffects();
        assertTrue(owner.replaceAuraStats(source,List.of(lease(leader,Stat.AURA_PHYSICAL_INCREASE,.2,0),lease(member,Stat.AURA_PHYSICAL_INCREASE,.2,0)),0,()->true));
        assertEquals(.2,owner.winningStat(world,member,4,Stat.AURA_PHYSICAL_INCREASE,.2));
        assertEquals(0,owner.winningStat(world,member,5,Stat.AURA_PHYSICAL_INCREASE,.2));
        assertEquals(0,owner.winningStat(UUID.randomUUID(),member,4,Stat.AURA_PHYSICAL_INCREASE,.2));
        owner.replaceAuraStats(source,List.of(lease(leader,Stat.AURA_PHYSICAL_INCREASE,.2,.25)),.25,()->true);
        assertEquals(0,owner.winningStat(world,member,4,Stat.AURA_PHYSICAL_INCREASE,.25));
        assertFalse(owner.cleanseStats(world,leader,4,()->true));
        assertEquals(.2,owner.winningStat(world,leader,4,Stat.AURA_PHYSICAL_INCREASE,.749));
        assertEquals(0,owner.winningStat(world,leader,4,Stat.AURA_PHYSICAL_INCREASE,.75));
    }
    @Test void invalidationDuringPublicationClearsBothOldAndPartiallyPublishedLeases(){
        var owner=new FiniteSupportEffects();
        owner.replaceAuraStats(source,List.of(lease(leader,Stat.AURA_PHYSICAL_INCREASE,.2,0)),0,()->true);
        var calls=new AtomicInteger();
        assertFalse(owner.replaceAuraStats(source,List.of(lease(leader,Stat.AURA_MOVEMENT_INCREASE,.2,.2),
                lease(member,Stat.AURA_MOVEMENT_INCREASE,.2,.2)),.2,()->calls.incrementAndGet()<3));
        assertTrue(owner.statEffects(world,leader,4,.2).isEmpty());assertTrue(owner.statEffects(world,member,4,.2).isEmpty());
    }
    @Test void invalidLeaseCannotPartiallyReplaceAValidSourceAndOtherPacksRemainIndependent(){
        var owner=new FiniteSupportEffects();
        owner.replaceAuraStats(source,List.of(lease(leader,Stat.AURA_PHYSICAL_INCREASE,.2,0)),0,()->true);
        var bad=new StatEffect(new StatKey(source,member,4,Stat.AURA_RESISTANCE_ADD),.3,.1,1);
        assertThrows(IllegalArgumentException.class,()->owner.replaceAuraStats(source,List.of(lease(member,Stat.AURA_PHYSICAL_INCREASE,.2,.1),bad),.1,()->true));
        assertEquals(.2,owner.winningStat(world,leader,4,Stat.AURA_PHYSICAL_INCREASE,.1));
        assertTrue(owner.statEffects(world,member,4,.1).isEmpty());
        var other=new StatSource(world,UUID.randomUUID(),4,SourceKind.MONSTER_AFFIX,"ME-023");
        owner.replaceAuraStats(other,List.of(new StatEffect(new StatKey(other,member,4,Stat.AURA_PHYSICAL_INCREASE),.3,.1,.6)),.1,()->true);
        owner.replaceAuraStats(source,List.of(),.2,()->false);
        assertEquals(.3,owner.winningStat(world,member,4,Stat.AURA_PHYSICAL_INCREASE,.2));
        owner.clearWorld(world);assertEquals(0,owner.size());
    }
    @Test void inspectionDoesNotExpireAStatLeaseOrChangeTheWinner(){
        var owner=new FiniteSupportEffects();
        owner.replaceAuraStats(source,List.of(lease(leader,Stat.AURA_PHYSICAL_INCREASE,.2,0)),0,()->true);
        assertEquals(.2,owner.peekWinningStat(world,leader,4,Stat.AURA_PHYSICAL_INCREASE,.25));
        assertEquals(0,owner.peekWinningStat(world,leader,4,Stat.AURA_PHYSICAL_INCREASE,.5));
        assertEquals(.2,owner.winningStat(world,leader,4,Stat.AURA_PHYSICAL_INCREASE,.25));
    }
    @Test void nativeDeathWithdrawsOnlyThatActorsAuraMembershipAndSource(){
        var owner=new FiniteSupportEffects();
        owner.replaceAuraStats(source,List.of(lease(leader,Stat.AURA_PHYSICAL_INCREASE,.2,0),
                lease(member,Stat.AURA_PHYSICAL_INCREASE,.2,0)),0,()->true);
        var otherLeader=UUID.randomUUID();
        var other=new StatSource(world,otherLeader,4,SourceKind.MONSTER_AFFIX,"ME-023");
        owner.replaceAuraStats(other,List.of(new StatEffect(new StatKey(other,member,4,Stat.AURA_PHYSICAL_INCREASE),.3,0,.5)),0,()->true);
        owner.withdrawAuraActor(world,leader,4);
        assertEquals(.3,owner.winningStat(world,member,4,Stat.AURA_PHYSICAL_INCREASE,.1));
        assertEquals(0,owner.winningStat(world,leader,4,Stat.AURA_PHYSICAL_INCREASE,.1));
        owner.withdrawAuraActor(world,member,4);
        assertEquals(0,owner.winningStat(world,member,4,Stat.AURA_PHYSICAL_INCREASE,.1));
    }
}
