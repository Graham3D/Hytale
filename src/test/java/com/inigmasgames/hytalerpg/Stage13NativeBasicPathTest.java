package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.hytale.NativeBasicAttackPaths;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChargingInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.StatsConditionInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.data.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual installed interaction classes; nativeControlTest supplies the required logger JVM. */
class Stage13NativeBasicPathTest {
    @Test void nativeChargingZeroBranchSupersedesOuterHeldThreshold(){
        var collector=new NativeBasicAttackPaths();collector.start();collector.into(null,null);
        var outer=new ChargingInteraction();collector.collect(CollectorTag.ROOT,null,outer);collector.into(null,outer);
        var inner=new ChargingInteraction();collector.collect(ChargingInteraction.ChargingTag.of(.2f),null,inner);collector.into(null,inner);
        var ordinary=new DamageEntityInteraction();collector.collect(ChargingInteraction.ChargingTag.of(0),null,ordinary);
        var charged=new DamageEntityInteraction();collector.collect(ChargingInteraction.ChargingTag.of(.65f),null,charged);
        assertEquals(NativeBasicAttackPaths.Kind.NORMAL,collector.classify(ordinary).orElseThrow());assertEquals(NativeBasicAttackPaths.Kind.CHARGED,collector.classify(charged).orElseThrow());
        collector.outof();collector.outof();collector.outof();collector.finished();
    }
    @Test void nativeStaminaFailureBranchCannotBecomeChargedRecovery(){
        var collector=new NativeBasicAttackPaths();collector.start();collector.into(null,null);
        var condition=new StatsConditionInteraction();collector.collect(ChargingInteraction.ChargingTag.of(.2f),null,condition);collector.into(null,condition);
        var damage=new DamageEntityInteraction();collector.collect(StringTag.of("Failed"),null,damage);
        assertEquals(NativeBasicAttackPaths.Kind.NORMAL,collector.classify(damage).orElseThrow());
    }
    @Test void ambiguousSharedDamagePathAndOnHitDamageBranchesCannotMintBasicReceipts(){
        var collector=new NativeBasicAttackPaths();collector.start();collector.into(null,null);var same=new DamageEntityInteraction();
        collector.collect(ChargingInteraction.ChargingTag.of(0),null,same);collector.collect(ChargingInteraction.ChargingTag.of(.2f),null,same);
        assertTrue(collector.classify(same).isEmpty());collector.into(null,same);
        var child=new DamageEntityInteraction();collector.collect(StringTag.of("Next"),null,child);assertTrue(collector.classify(child).isEmpty());
    }
}
