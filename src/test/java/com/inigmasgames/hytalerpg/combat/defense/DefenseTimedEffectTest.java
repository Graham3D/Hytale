package com.inigmasgames.hytalerpg.combat.defense;

import com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects.*;

class DefenseTimedEffectTest {
    private final UUID world=UUID.randomUUID(),target=UUID.randomUUID();
    private StatKey source(UUID owner){return new StatKey(new StatSource(world,owner,1,SourceKind.MONSTER_AFFIX,"ME-025"),target,0,Stat.DEFENSE_BREAK);}
    @Test void strongestIndependentSourceExpiresWithoutRemovingWeakerSource(){
        var effects=new FiniteSupportEffects();var a=source(UUID.randomUUID());var b=source(UUID.randomUUID());
        assertTrue(effects.applyStat(new StatEffect(a,.25,0,4),0,()->true));
        assertTrue(effects.applyStat(new StatEffect(b,.15,2,6),2,()->true));
        assertEquals(.25,effects.winningStat(world,target,0,Stat.DEFENSE_BREAK,3));
        assertEquals(.15,effects.winningStat(world,target,0,Stat.DEFENSE_BREAK,4));
        assertEquals(0,effects.winningStat(world,target,0,Stat.DEFENSE_BREAK,6));
    }
    @Test void replayReplacesOneSourceAndCurrentBaselineRecomputes(){
        var effects=new FiniteSupportEffects();var key=source(UUID.randomUUID());var applied=new StatEffect(key,.25,0,4);
        effects.applyStat(applied,0,()->true);effects.applyStat(applied,0,()->true);assertEquals(1,effects.size());
        double winning=effects.winningStat(world,target,0,Stat.DEFENSE_BREAK,1);
        assertEquals(150,DefenseView.managed(10,0,new DefenseView.Contributions(0,200,0,winning)).effectiveRating());
        assertEquals(75,DefenseView.managed(10,0,new DefenseView.Contributions(0,100,0,winning)).effectiveRating());
        assertEquals(0,effects.winningStat(world,target,1,Stat.DEFENSE_BREAK,1));
        assertEquals(0,effects.winningStat(UUID.randomUUID(),target,0,Stat.DEFENSE_BREAK,1));
    }
    @Test void protectedTargetRejectsApplyAndCleanseAtMutationBoundary(){
        var effects=new FiniteSupportEffects();var applied=new StatEffect(source(UUID.randomUUID()),.25,0,4);
        assertFalse(effects.applyStat(applied,0,()->false));assertEquals(0,effects.size());
        assertTrue(effects.applyStat(applied,0,()->true));
        assertFalse(effects.cleanseStats(world,target,0,()->false));assertEquals(1,effects.size());
        assertTrue(effects.cleanseStats(world,target,0,()->true));assertEquals(0,effects.size());
    }
    @Test void acceptedDebuffOutlivesItsSourceButCannotLeakIntoAnotherTargetGeneration(){
        var effects=new FiniteSupportEffects();var owner=UUID.randomUUID();
        effects.applyStat(new StatEffect(source(owner),.25,0,4),0,()->true);
        effects.forget(owner);assertEquals(1,effects.size());
        assertEquals(.25,effects.winningStat(world,target,0,Stat.DEFENSE_BREAK,1));
        assertEquals(0,effects.winningStat(world,target,1,Stat.DEFENSE_BREAK,1));
        assertEquals(0,effects.winningStat(world,target,0,Stat.DEFENSE_BREAK,4));
        assertEquals(0,effects.size());
    }
    @Test void cursedUsesOneStrongestDirectReductionAndLeavesPeriodicSnapshotUntouched(){
        var effects=new FiniteSupportEffects();var a=UUID.randomUUID();var b=UUID.randomUUID();
        var keyA=new StatKey(new StatSource(world,a,1,SourceKind.MONSTER_AFFIX,"ME-014"),target,0,Stat.DIRECT_WEAKEN);
        var keyB=new StatKey(new StatSource(world,b,1,SourceKind.MONSTER_AFFIX,"ME-014"),target,0,Stat.DIRECT_WEAKEN);
        effects.applyStat(new StatEffect(keyA,.15,0,4),0,()->true);
        effects.applyStat(new StatEffect(keyB,.1,2,6),2,()->true);
        var base=com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets.NONE;
        assertEquals(.85,effects.directWeakening(world,target,0,base,3).factor());
        assertEquals(.85,effects.nativeDirectOutgoingFactor(world,target,0,3));
        assertEquals(1,effects.outgoingModifiers(world,target,base,3).factor());
        assertEquals(1,effects.nativeOutgoingFactor(world,target,3));
        effects.forget(a);assertEquals(.85,effects.directWeakening(world,target,0,base,3).factor());
        assertEquals(.9,effects.directWeakening(world,target,0,base,4).factor());
        assertEquals(1,effects.directWeakening(world,target,0,base,6).factor());
    }
}
