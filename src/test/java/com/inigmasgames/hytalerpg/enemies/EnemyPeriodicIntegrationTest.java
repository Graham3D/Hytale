package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.damage.MonsterAffixSource;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata;
import com.inigmasgames.hytalerpg.combat.status.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyPeriodicIntegrationTest {
    final UUID world=UUID.randomUUID(),actor=UUID.randomUUID(),victim=UUID.randomUUID();
    MonsterAffixSource source(long generation){return new MonsterAffixSource(world,actor,actor,generation,"ME-008","balance","root","strike");}
    static final class Port implements PeriodicStatusRuntime.Port<Object,String>{
        final List<Double> amounts=new ArrayList<>();final List<String> ended=new ArrayList<>();
        public boolean tick(PeriodicStatusRuntime.Source source,Object context,String target,int tick,double coefficient,double seconds){
            if(context instanceof PeriodicContext.Monster monster){if(!monster.bindingCurrent().getAsBoolean())return false;amounts.add(monster.tickDamage(coefficient));}
            return true;
        }
        public void changed(PeriodicStatusRuntime.Source source,String target,PeriodicStatusRuntime.View view){}
        public void terminated(PeriodicStatusRuntime.Source source,Object context,String target,String reason){ended.add(reason);}
    }
    @Test void acceptedPoisonUsesFrozenPowerOnceAndTheExistingSixSecondScheduler(){
        var balance=CombatBalanceProfile.loadCanonical();var owner=new PeriodicStatusRuntime<Object,String>();var port=new Port();
        var context=new PeriodicContext.Monster(source(1),10,()->true);var key=PeriodicStatusRuntime.Source.monster(context.source(),victim);
        owner.apply(key,context,"target",balance.poisonBaselineCoefficientPerSecond,context.tickDamage(balance.poisonBaselineCoefficientPerSecond),
                balance.poisonDurationSeconds,1,PeriodicStatusRuntime.BASE_POISON_SOURCE_CAP,0,port);
        for(int i=1;i<=24;i++)owner.tick(actor,i*.25,port);
        assertEquals(6,port.amounts.size());port.amounts.forEach(amount->assertEquals(.6,amount,1e-12));
        assertEquals(3.6,port.amounts.stream().mapToDouble(Double::doubleValue).sum(),1e-12);assertEquals(0,owner.size());
        assertThrows(IllegalStateException.class,key::skill);assertEquals("ME-008",key.definitionId());
        var metadata=HytaleDamageMetadata.monster(context.source(),"tick",HytaleDamageMetadata.Origin.PERIODIC,.6);
        assertEquals("",metadata.skillInstanceId());assertFalse(metadata.canProc());assertTrue(metadata.noLeech());assertTrue(metadata.noRetaliation());
    }
    @Test void playerAndMonsterPackagesCompeteInOneGlobalPoisonCap(){
        var owner=new PeriodicStatusRuntime<Object,String>();var port=new Port();
        for(int i=0;i<4;i++)owner.apply(new PeriodicStatusRuntime.Source(new UUID(0,i+1),"poison_cloud",victim,PeriodicStatusRuntime.Kind.POISON),
                "existing player snapshot","target",.06,1+i,6,3,3,0,port);
        var monster=new PeriodicContext.Monster(source(1),100,()->true);var key=PeriodicStatusRuntime.Source.monster(monster.source(),victim);
        owner.apply(key,monster,"target",.06,6,6,3,3,0,port);
        assertEquals(12,owner.view(victim,PeriodicStatusRuntime.Kind.POISON,0).stacks());assertEquals(4,owner.size());
        assertFalse(owner.hasOwner(new UUID(0,1)));assertEquals(3,owner.sourceView(key,0).orElseThrow().stacks());
        owner.cancel(actor,.1,port);assertEquals(9,owner.view(victim,PeriodicStatusRuntime.Kind.POISON,.1).stacks());
        assertEquals(3,owner.size());
    }
    @Test void logicalSourceCapSurvivesNativeIdsAndLateRemovalCannotCancelTheNewBinding(){
        var owner=new PeriodicStatusRuntime<Object,String>();var port=new Port();
        var nativeOld=UUID.randomUUID();var nativeNew=UUID.randomUUID();
        var old=new MonsterAffixSource(world,actor,nativeOld,1,"ME-008","balance","old-root","one");
        var rebound=new MonsterAffixSource(world,actor,nativeNew,1,"ME-008","balance","new-root","two");
        var first=new PeriodicContext.Monster(old,10,()->true);var next=new PeriodicContext.Monster(rebound,10,()->true);
        var key=PeriodicStatusRuntime.Source.monster(old,victim);
        assertEquals(key,PeriodicStatusRuntime.Source.monster(rebound,victim));assertEquals(actor,key.owner());
        owner.apply(key,first,"old-target",.06,.6,6,3,3,0,port);
        assertEquals("MONSTER_PERIODIC_REBIND_REQUIRED",owner.admission(key,next));
        owner.cancelMonster(old,.1,port);
        owner.apply(key,next,"new-target",.06,.6,6,1,3,.1,port);
        owner.cancelMonster(old,.2,port);assertEquals(1,owner.size());
        assertFalse(owner.tickMonster(old,1.1,port));assertTrue(port.amounts.isEmpty());
        assertTrue(owner.tickMonster(rebound,1.1,port));assertEquals(java.util.List.of(.6),port.amounts);
        var newer=new MonsterAffixSource(world,actor,nativeNew,2,"ME-008","balance","newer-root","three");
        owner.cancelMonster(newer,1.2,port);assertEquals(1,owner.size());
    }
    @Test void invalidGenerationCannotReplayTicksOrMasqueradeAsAnotherSource(){
        var live=new java.util.concurrent.atomic.AtomicBoolean(true);var owner=new PeriodicStatusRuntime<Object,String>();var port=new Port();
        var context=new PeriodicContext.Monster(source(1),10,live::get);var key=PeriodicStatusRuntime.Source.monster(context.source(),victim);
        assertNotEquals(key,PeriodicStatusRuntime.Source.monster(source(2),victim));
        assertThrows(IllegalArgumentException.class,()->owner.apply(PeriodicStatusRuntime.Source.monster(source(2),victim),context,"target",.06,.6,6,1,3,0,port));
        owner.apply(key,context,"target",.06,.6,6,1,3,0,port);live.set(false);
        owner.tick(actor,1,port);owner.tick(actor,2,port);
        assertEquals(0,owner.size());assertTrue(port.amounts.isEmpty());assertEquals(1,port.ended.size());
    }
}
