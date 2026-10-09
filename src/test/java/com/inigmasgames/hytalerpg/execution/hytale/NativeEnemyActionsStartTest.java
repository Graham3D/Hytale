package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.protocol.InteractionChainData;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ForkedChainId;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.event.InteractionChainStartEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeEnemyActionsStartTest {
    @Test void nativeRootCanBeCapturedAtStartBeforeItsFirstTick(){
        var root=new RootInteraction("FixtureRoot","FixtureLeaf");
        var chain=new InteractionChain(InteractionType.Primary,InteractionContext.withoutEntity(),
                new InteractionChainData(),root,null,false);
        assertEquals(InteractionState.Finished,chain.getFinalState());
        var event=new InteractionChainStartEvent(chain);
        assertTrue(NativeEnemyActions.acceptsRootStart(event));
        event.setCancelled(true);
        assertFalse(NativeEnemyActions.acceptsRootStart(event));
    }
    @Test void onlyTheTrorkGenericSparRootIsUncertified(){
        assertTrue(NativeEnemyActions.uncertifiedTrorkSpar("Trork_Warrior","Root_NPC_Attack_Melee"));
        assertFalse(NativeEnemyActions.uncertifiedTrorkSpar("Trork_Warrior","Trork_Warrior_Battleaxe_Swing_Left"));
        assertFalse(NativeEnemyActions.uncertifiedTrorkSpar("Larva_Void","Root_NPC_Attack_Melee"));
    }
    @Test void detachedNativeSelectorHitKeepsTheAcceptedAttackChainIdentity(){
        var root=new RootInteraction("TrorkSwing","Selector");
        var accepted=new InteractionChain(InteractionType.Primary,InteractionContext.withoutEntity(),
                new InteractionChainData(),root,null,false);
        accepted.setChainId(-41);
        var hit=new InteractionChain(new ForkedChainId(0,0,null),new ForkedChainId(0,0,null),
                InteractionType.Primary,InteractionContext.withoutEntity(),new InteractionChainData(),
                new RootInteraction("GeneratedHit","Damage"),null,true);
        hit.setChainId(-41);
        assertTrue(accepted.getForkedChains().isEmpty());
        assertTrue(NativeEnemyAction.acceptedLineage(accepted,hit));
        assertTrue(NativeEnemyAction.acceptedLineage(accepted,accepted));
        hit.setChainId(-42);
        assertFalse(NativeEnemyAction.acceptedLineage(accepted,hit));
        hit.setChainId(-41);
        accepted.setChainId(0);
        hit.setChainId(0);
        assertFalse(NativeEnemyAction.acceptedLineage(accepted,hit));
        var unrelated=new InteractionChain(InteractionType.Primary,InteractionContext.withoutEntity(),
                new InteractionChainData(),root,null,false);
        unrelated.setChainId(-41);
        assertFalse(NativeEnemyAction.acceptedLineage(accepted,unrelated));
    }
}
