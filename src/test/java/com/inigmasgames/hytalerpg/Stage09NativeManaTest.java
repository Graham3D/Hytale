package com.inigmasgames.hytalerpg;

import com.hypixel.hytale.protocol.EntityStatResetBehavior;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.RegeneratingValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.inigmasgames.hytalerpg.combat.hytale.NativeManaReservationProjection;
import com.inigmasgames.hytalerpg.combat.hytale.NativeManaRegenerationAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.event.EventBus;
import java.util.Map;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

/** Actual pinned native stat/regeneration methods over an isolated stat asset, NOT connected behavior. */
class Stage09NativeManaTest {
    static final EntityStatType.Regenerating REGEN=new EntityStatType.Regenerating(1,.015f,
            EntityStatType.Regenerating.RegenType.PERCENTAGE,null,null);
    static final EntityStatType MANA=new EntityStatType("Mana",100,0,100,false,new EntityStatType.Regenerating[]{REGEN},null,null,
            EntityStatResetBehavior.InitialValue);
    static HytaleAssetStore<String,EntityStatType,IndexedLookupTableAssetMap<String,EntityStatType>> store;
    static class FixtureMap extends IndexedLookupTableAssetMap<String,EntityStatType>{
        FixtureMap(){super(EntityStatType[]::new);}
        void seed(){putAll("Stage09Fixture",EntityStatType.CODEC,Map.of("Mana",MANA),Map.of(),Map.of());}
    }
    @BeforeAll static void registry() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var map=new FixtureMap();map.seed();
        var builder=HytaleAssetStore.builder(EntityStatType.class,(IndexedLookupTableAssetMap<String,EntityStatType>)map)
                .setPath("Entity/Stats").setCodec(EntityStatType.CODEC).setKeyFunction(EntityStatType::getId)
                .setReplaceOnRemove(EntityStatType::getUnknownFor);
        store=new HytaleAssetStore<>(builder){
            final EventBus events=new EventBus(false);
            @Override protected EventBus getEventBus(){return events;}
        };
        AssetRegistry.register(store);
    }
    @AfterAll static void release(){if(store!=null)AssetRegistry.unregister(store);}
    static class Stat extends EntityStatValue {
        final EntityStatType asset;
        Stat(){this(MANA);}
        Stat(EntityStatType asset){super(EntityStatType.getAssetMap().getIndex("Mana"),asset);this.asset=asset;set(20);}
        void modifier(String key,StaticModifier modifier){putModifier(key,modifier);computeModifiers(asset);}
        void clear(String key){removeModifier(key);computeModifiers(asset);}
        void current(float value){set(value);}
    }
    static StaticModifier additive(float value){return new StaticModifier(Modifier.ModifierTarget.MAX,StaticModifier.CalculationType.ADDITIVE,value);}
    @Test void nativeStaticReservationClampsAndUnreserveDoesNotMint(){
        var stat=new Stat();stat.current(90);
        stat.modifier(NativeManaReservationProjection.KEY,additive(-50));
        assertEquals(50,stat.get());assertEquals(50,stat.getMax());
        assertEquals(100,NativeManaReservationProjection.totalMaximum(stat));
        stat.clear(NativeManaReservationProjection.KEY);assertEquals(50,stat.get());assertEquals(100,stat.getMax());
    }
    @Test void nativeMultiplierIsSumOfFactorsNotIncreasedPercentage(){
        var stat=new Stat();
        stat.modifier("external",new StaticModifier(Modifier.ModifierTarget.MAX,StaticModifier.CalculationType.MULTIPLICATIVE,1.5f));
        stat.modifier(NativeManaReservationProjection.KEY,additive(-20));
        assertEquals(120,stat.getMax());assertEquals(150,NativeManaReservationProjection.totalMaximum(stat));
        assertEquals(1.5,NativeManaReservationProjection.maximumMultiplier(stat));
    }
    @Test void nativePositiveDeltaIsUnclampedUntilStatApplication(){
        var stat=new Stat();stat.current(99.9f);
        assertEquals(1.5,new RegeneratingValue(REGEN).regenerate(null,null,Instant.EPOCH,.1f,stat,0),1e-5);
    }
    @Test void auraUsesNativeTimerOnceAndTotalManaDespiteReservation(){
        var stat=new Stat();stat.modifier(NativeManaReservationProjection.KEY,additive(-80));
        stat.current(0);
        var entry=new NativeManaRegenerationAdapter.Entry(new RegeneratingValue(REGEN),()->.25);
        assertEquals(1.875,entry.regenerate(null,null,Instant.EPOCH,.1f,stat,0),1e-5);
        assertEquals(0,entry.regenerate(null,null,Instant.EPOCH,.1f,stat,0));
    }
    @Test void noAuraRestoresUnmodifiedOnePointFiveTotalManaRegen(){
        var stat=new Stat();stat.modifier(NativeManaReservationProjection.KEY,additive(-50));
        var entry=new NativeManaRegenerationAdapter.Entry(new RegeneratingValue(REGEN),()->0);
        assertEquals(1.5,entry.regenerate(null,null,Instant.EPOCH,.1f,stat,0),1e-5);
    }
    @Test void changingAuraMultiplierDoesNotResetNativeTimer(){
        var stat=new Stat();double[] bonus={.25};
        var entry=new NativeManaRegenerationAdapter.Entry(new RegeneratingValue(REGEN),()->bonus[0]);
        assertEquals(1.875,entry.regenerate(null,null,Instant.EPOCH,.1f,stat,0),1e-5);
        bonus[0]=0;
        assertEquals(0,entry.regenerate(null,null,Instant.EPOCH,.1f,stat,0));
        assertEquals(1.5,entry.regenerate(null,null,Instant.EPOCH,1,stat,0),1e-5);
    }
    @Test void reservationUsesClientSupportedStaticPacket(){
        assertNotNull(additive(-50).toPacket());
    }
}
