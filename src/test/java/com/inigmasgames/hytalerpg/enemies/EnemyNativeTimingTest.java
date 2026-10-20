package com.inigmasgames.hytalerpg.enemies;

import com.hypixel.hytale.server.npc.asset.builder.*;
import com.hypixel.hytale.server.npc.role.support.CombatSupport;
import com.inigmasgames.hytalerpg.execution.hytale.NativeNpcTiming;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real native pause countdown; full action/animation and connected navigation acceptance remain separate gates. */
class EnemyNativeTimingTest {
    private static CombatSupport nativeCombat(){
        return new CombatSupport(new SupportConfigBuilder<Object>(){
            @Override public Object build(BuilderSupport support){throw new AssertionError("No server role creation in this fixture");}
            @Override public Class<Object> category(){return Object.class;}
            @Override public String getShortDescription(){return "Native recovery contract fixture";}
            @Override public String getLongDescription(){return getShortDescription();}
            @Override public BuilderDescriptorState getBuilderDescriptorState(){return BuilderDescriptorState.Stable;}
            @Override public boolean isEnabled(com.hypixel.hytale.server.npc.util.expression.ExecutionContext context){return true;}
            @Override public boolean excludeFromRegularBuilding(){return false;}
            @Override public boolean isDisableDamageFlock(){return true;}
            @Override public int[] getDisableDamageGroups(BuilderSupport support){return new int[0];}
        },null);
    }
    @Test void nativeCountdownPreservesTheWholeProtectedActionThenScalesOnlyRecovery(){
        var projection=new NativeNpcTiming.Recovery("Larva_Void_Bite","fixture",
                NativeNpcTiming.protectedSeconds("Larva_Void_Bite/installed-0.7.0-pre.5.1"),1.2);
        double pause=projection.pause(projection.rootId(),2);
        assertEquals(.5+1.5/1.2,pause,1e-12);
        var combat=nativeCombat();combat.setExecutingAttack(null,false,pause);
        combat.tick(.217);assertTrue(combat.isExecutingAttack()); // Native bite preparation.
        combat.tick(.117);assertTrue(combat.isExecutingAttack()); // Native selector and hit window.
        combat.tick(.166);assertTrue(combat.isExecutingAttack()); // Native padding and animation end.
        combat.tick(1.2);assertTrue(combat.isExecutingAttack());
        combat.tick(.051);assertFalse(combat.isExecutingAttack());
    }
    @Test void identityAndShortPausesStayExactlyNativeAndOverridesCannotBorrowAnotherTimeline(){
        for(double pause:new double[]{0,.6,1,1.00000001,1.8,2}){
            assertEquals(Double.doubleToLongBits(pause),Double.doubleToLongBits(new NativeNpcTiming.Recovery("root","rev",1,1).pause("root",pause)));
            if(pause<=1)assertEquals(pause,new NativeNpcTiming.Recovery("root","rev",1,1.5).pause("root",pause));
        }
        var frozen=new NativeNpcTiming.Recovery("root","rev",1,1.2);
        assertThrows(IllegalStateException.class,()->frozen.pause("native-override",2));
        assertThrows(IllegalArgumentException.class,()->new NativeNpcTiming.Recovery("root","rev",1,1.6));
        assertThrows(IllegalArgumentException.class,()->frozen.pause("root",Double.NaN));
        assertThrows(IllegalArgumentException.class,()->NativeNpcTiming.protectedSeconds("unknown/native-action"));
    }
}
