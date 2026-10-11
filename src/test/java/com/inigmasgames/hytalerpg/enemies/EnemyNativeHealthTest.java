package com.inigmasgames.hytalerpg.enemies;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.protocol.EntityStatResetBehavior;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.*;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.inigmasgames.hytalerpg.difficulty.*;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleDifficultyCombat;
import com.inigmasgames.hytalerpg.execution.hytale.CycloneNativePaymentMarker;
import com.inigmasgames.hytalerpg.execution.support.CycloneActivationTransaction;
import com.inigmasgames.hytalerpg.execution.support.CycloneBarrierLedger;
import com.inigmasgames.hytalerpg.combat.hytale.DerivedStatEntityAdapter;
import com.inigmasgames.hytalerpg.combat.attribute.DerivedStatService;
import com.inigmasgames.hytalerpg.combat.attribute.EffectiveAttributeService;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the same native stat map and difficulty modifier used at publication. No server startup. */
class EnemyNativeHealthTest {
    static HytaleAssetStore<String,EntityStatType,IndexedLookupTableAssetMap<String,EntityStatType>> assets;
    static Object previousAssetStore;
    static Map<String,Integer> previousDefaultIndexes;
    private static final List<String> DEFAULT_INDEX_FIELDS=List.of(
            "HEALTH","OXYGEN","STAMINA","MANA","SIGNATURE_ENERGY","AMMO");
    static class Stats extends IndexedLookupTableAssetMap<String,EntityStatType>{
        Stats(){super(EntityStatType[]::new);}
        void seed(){
            var entries=new HashMap<String,EntityStatType>();
            for(String name:List.of("Health","Oxygen","Stamina","Mana","SignatureEnergy","Ammo"))
                entries.put(name,new EntityStatType(name,100,0,100,false,null,null,null,EntityStatResetBehavior.InitialValue));
            putAll("EnemyHealthFixture",EntityStatType.CODEC,entries,Map.of(),Map.of());
        }
    }
    @BeforeAll static void setup() throws Exception {
        var storeField=EntityStatType.class.getDeclaredField("ASSET_STORE");storeField.setAccessible(true);
        previousAssetStore=storeField.get(null);
        previousDefaultIndexes=new HashMap<>();
        for(var name:DEFAULT_INDEX_FIELDS){
            var field=DefaultEntityStatTypes.class.getDeclaredField(name);field.setAccessible(true);
            previousDefaultIndexes.put(name,field.getInt(null));
        }
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});var map=new Stats();map.seed();
        var builder=HytaleAssetStore.builder(EntityStatType.class,(IndexedLookupTableAssetMap<String,EntityStatType>)map)
                .setPath("Entity/Stats").setCodec(EntityStatType.CODEC).setKeyFunction(EntityStatType::getId).setReplaceOnRemove(EntityStatType::getUnknownFor);
        assets=new HytaleAssetStore<>(builder){final EventBus events=new EventBus(false);@Override protected EventBus getEventBus(){return events;}};
        AssetRegistry.register(assets);storeField.set(null,assets);DefaultEntityStatTypes.update();
        assertTrue(DefaultEntityStatTypes.getHealth()>=0,"Fixture Health stat must resolve through its own SDK asset map");
    }
    @AfterAll static void cleanup() throws Exception {
        try{if(assets!=null)AssetRegistry.unregister(assets);}
        finally{
            var storeField=EntityStatType.class.getDeclaredField("ASSET_STORE");storeField.setAccessible(true);
            storeField.set(null,previousAssetStore);
            if(previousDefaultIndexes!=null)for(var entry:previousDefaultIndexes.entrySet()){
                var field=DefaultEntityStatTypes.class.getDeclaredField(entry.getKey());field.setAccessible(true);
                field.setInt(null,entry.getValue());
            }
        }
    }
    private static EncounterProfileResolver.Resolved profile(){return new EncounterProfileResolver.Resolved(UUID.randomUUID(),UUID.randomUUID(),
            DifficultyId.NIGHTMARE,"fixture","world","Trork_Warrior","biome",50,200,23,2,1,
            new MonsterResistanceProfile(Map.of(),Set.of()),EncounterProfileResolver.Evidence.FIXTURE_ONLY);}
    private static EnemyAffixSnapshot providers(double rarity,double empowered){return new EnemyAffixSnapshot(rarity,1,empowered,1,1,0,0,0,Map.of(),0,Map.of(),false,false,false,null,0,false,false);}
    private static EntityStatMap stats(){var stats=new EntityStatMap();stats.update();return stats;}
    @Test void nativeManaValueAndZeroPaymentMarkerRoundTripTogether(){
        com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.CODEC.register(
                "Static",StaticModifier.class,StaticModifier.ENTITY_CODEC);
        var map=stats();int mana=DefaultEntityStatTypes.getMana();
        String marker="hytalerpg:cyclone-payment/"+UUID.randomUUID();
        map.setStatValue(mana,0);
        map.putModifier(mana,marker,new StaticModifier(
                com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.ModifierTarget.MAX,
                StaticModifier.CalculationType.ADDITIVE,0));
        map.update();
        var encoded=EntityStatMap.CODEC.encode(map,new com.hypixel.hytale.codec.ExtraInfo());
        var decoded=EntityStatMap.CODEC.decode(encoded,new com.hypixel.hytale.codec.ExtraInfo());
        assertEquals(0,decoded.get(mana).get(),.001);
        assertNotNull(decoded.get(mana).getModifier(marker));
    }
    @Test void cycloneMarkerProvesOnlyItsPaidGenerationAndPreservesMana(){
        com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.CODEC.register(
                "Static",StaticModifier.class,StaticModifier.ENTITY_CODEC);
        int mana=DefaultEntityStatTypes.getMana();
        var nativeStats=stats();nativeStats.setStatValue(mana,37);
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        assertFalse(CycloneNativePaymentMarker.confirms(nativeStats,first));
        CycloneNativePaymentMarker.mark(nativeStats,first,false);
        assertEquals(37,nativeStats.get(mana).get(),.001);
        assertEquals(100,nativeStats.get(mana).getMax(),.001);
        assertTrue(CycloneNativePaymentMarker.confirms(nativeStats,first));
        var balance=CombatBalanceProfile.loadCanonical();
        new DerivedStatEntityAdapter().apply(nativeStats,
                new DerivedStatService(balance,new EffectiveAttributeService(balance)).derive(Map.of(),99));
        assertTrue(CycloneNativePaymentMarker.confirms(nativeStats,first));
        var persisted=EntityStatMap.CODEC.decode(EntityStatMap.CODEC.encode(nativeStats,
                new com.hypixel.hytale.codec.ExtraInfo()),new com.hypixel.hytale.codec.ExtraInfo());
        assertTrue(CycloneNativePaymentMarker.confirms(persisted,first));
        assertEquals(37,persisted.get(mana).get(),.001);
        assertFalse(CycloneNativePaymentMarker.confirms(persisted,second));
        CycloneNativePaymentMarker.mark(persisted,second,true);
        assertFalse(CycloneNativePaymentMarker.confirms(persisted,first));
        assertFalse(CycloneNativePaymentMarker.confirms(persisted,second));
        assertEquals(CycloneNativePaymentMarker.Kind.REFLECTIVE,CycloneNativePaymentMarker.kind(persisted,second));
        var reflectiveReload=EntityStatMap.CODEC.decode(EntityStatMap.CODEC.encode(persisted,
                new com.hypixel.hytale.codec.ExtraInfo()),new com.hypixel.hytale.codec.ExtraInfo());
        assertEquals(CycloneNativePaymentMarker.Kind.REFLECTIVE,
                CycloneNativePaymentMarker.kind(reflectiveReload,second));
        assertEquals(37,persisted.get(mana).get(),.001);
    }
    @Test void recastRetainsOnlyPaymentMarkersNeededBySurvivingSharedChildren(){
        com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.CODEC.register(
                "Static",StaticModifier.class,StaticModifier.ENTITY_CODEC);
        var nativeStats=stats();nativeStats.setStatValue(DefaultEntityStatTypes.getMana(),15);
        UUID retained=UUID.randomUUID(),expired=UUID.randomUUID(),current=UUID.randomUUID();
        CycloneNativePaymentMarker.mark(nativeStats,retained,false);
        CycloneNativePaymentMarker.mark(nativeStats,expired,true,generation->generation.equals(retained));
        CycloneNativePaymentMarker.mark(nativeStats,current,false,generation->generation.equals(retained));
        var loaded=EntityStatMap.CODEC.decode(EntityStatMap.CODEC.encode(nativeStats,
                new com.hypixel.hytale.codec.ExtraInfo()),new com.hypixel.hytale.codec.ExtraInfo());
        assertEquals(CycloneNativePaymentMarker.Kind.PLAIN,CycloneNativePaymentMarker.kind(loaded,retained));
        assertEquals(CycloneNativePaymentMarker.Kind.NONE,CycloneNativePaymentMarker.kind(loaded,expired));
        assertEquals(CycloneNativePaymentMarker.Kind.PLAIN,CycloneNativePaymentMarker.kind(loaded,current));
        assertEquals(15,loaded.get(DefaultEntityStatTypes.getMana()).get(),.001);
    }
    @Test void nativeEquivalentSavedStatFixtureRequiresMatchingPaidGeneration(@TempDir java.nio.file.Path directory) throws Exception {
        com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.CODEC.register(
                "Static",StaticModifier.class,StaticModifier.ENTITY_CODEC);
        UUID paid=UUID.randomUUID(),unpaid=UUID.randomUUID();
        var original=stats();original.setStatValue(DefaultEntityStatTypes.getMana(),0);
        CycloneNativePaymentMarker.mark(original,paid,false);
        var snapshot=EntityStatMap.CODEC.encode(original,new com.hypixel.hytale.codec.ExtraInfo());
        var file=directory.resolve("native-entity-stats.json");
        java.nio.file.Files.writeString(file,snapshot.asDocument().toJson());
        var loaded=EntityStatMap.CODEC.decode(
                org.bson.BsonDocument.parse(java.nio.file.Files.readString(file)),
                new com.hypixel.hytale.codec.ExtraInfo());
        assertEquals(0,loaded.get(DefaultEntityStatTypes.getMana()).get(),.001);
        assertEquals(CycloneNativePaymentMarker.Kind.PLAIN,CycloneNativePaymentMarker.kind(loaded,paid));
        assertEquals(CycloneNativePaymentMarker.Kind.NONE,CycloneNativePaymentMarker.kind(loaded,unpaid));
    }
    @Test void recoveredNativePaymentPreservesOnlyProvenPlainBarrier(@TempDir java.nio.file.Path root){
        com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.CODEC.register(
                "Static",StaticModifier.class,StaticModifier.ENTITY_CODEC);
        for(int mode=0;mode<5;mode++){
            var player=UUID.randomUUID();var generation=UUID.randomUUID();var activation=UUID.randomUUID();
            var path=root.resolve("case-"+mode);
            var journal=new CycloneBarrierLedger(path);var first=journal.attach(player);
            var tx=new CycloneActivationTransaction(journal);
            tx.begin(first,activation,generation,100,()->true,()->true,()->true);
            tx.manaCommitted(first,activation,"native-confirmed",40);
            tx.cooldownCommitted(first,activation,"cooldown-confirmed");tx.publish(first,activation);
            assertEquals(25,journal.debit(first,generation,"first-hit",25).absorbed());
            var nativeStats=stats();nativeStats.setStatValue(DefaultEntityStatTypes.getMana(),60);
            nativeStats.setStatValue(DefaultEntityStatTypes.getHealth(),mode==3?0:100);
            if(mode!=0)CycloneNativePaymentMarker.mark(nativeStats,
                    mode==4?UUID.randomUUID():generation,mode==2);
            var loaded=EntityStatMap.CODEC.decode(EntityStatMap.CODEC.encode(nativeStats,
                    new com.hypixel.hytale.codec.ExtraInfo()),new com.hypixel.hytale.codec.ExtraInfo());
            var restarted=new CycloneBarrierLedger(path);var reconnect=restarted.attach(player);
            assertEquals(0,restarted.debit(reconnect,generation,"held-hit",10).absorbed());
            var recovered=CycloneNativePaymentMarker.recover(restarted,reconnect,loaded);
            assertEquals(mode==1?CycloneBarrierLedger.Phase.ACTIVE:CycloneBarrierLedger.Phase.DEPLETED,
                    recovered.phase());
            assertEquals(mode==1?75:0,recovered.remaining());
            assertEquals(60,loaded.get(DefaultEntityStatTypes.getMana()).get(),.001);
            assertEquals(mode==1?10:0,restarted.debit(reconnect,generation,"after-recovery",10).absorbed());
        }
    }
    @Test void reflectiveRecoveryRequiresBothLoadedNativePaymentAndFrozenBehavior(@TempDir java.nio.file.Path root){
        com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.CODEC.register(
                "Static",StaticModifier.class,StaticModifier.ENTITY_CODEC);
        for(int mode=0;mode<5;mode++){
            UUID player=UUID.randomUUID(),generation=UUID.randomUUID(),activation=UUID.randomUUID();
            var path=root.resolve("reflective-"+mode);
            var ledger=new CycloneBarrierLedger(path);var lease=ledger.attach(player);
            var tx=new CycloneActivationTransaction(ledger);
            tx.begin(lease,activation,generation,80,()->true,()->true,()->true);
            tx.manaCommitted(lease,activation,"native-paid",100);
            tx.cooldownCommitted(lease,activation,"cooldown-paid");
            var frozen=new com.inigmasgames.hytalerpg.execution.support.CycloneReflectionJournal.Behavior(
                    1,player,mode==2?UUID.randomUUID():generation,activation,"root","instance","corr",.2,80);
            var reflections=new com.inigmasgames.hytalerpg.execution.support.CycloneReflectionJournal(path);
            if(mode!=1)reflections.install(frozen);
            tx.publish(lease,activation);
            assertEquals(25,ledger.debit(lease,generation,"absorbed",25).absorbed());
            var nativeStats=stats();nativeStats.setStatValue(DefaultEntityStatTypes.getMana(),0);
            if(mode!=3)CycloneNativePaymentMarker.mark(nativeStats,generation,true);
            if(mode==4)nativeStats.setStatValue(DefaultEntityStatTypes.getHealth(),0);
            var loaded=EntityStatMap.CODEC.decode(EntityStatMap.CODEC.encode(nativeStats,
                    new com.hypixel.hytale.codec.ExtraInfo()),new com.hypixel.hytale.codec.ExtraInfo());
            var restarted=new CycloneBarrierLedger(path);var connected=restarted.attach(player);
            var frozenReload=new com.inigmasgames.hytalerpg.execution.support.CycloneReflectionJournal(path);
            frozenReload.preload(player);
            var result=CycloneNativePaymentMarker.recover(restarted,connected,loaded,frozenReload);
            assertEquals(mode==0?CycloneBarrierLedger.Phase.ACTIVE:CycloneBarrierLedger.Phase.DEPLETED,result.phase());
            assertEquals(mode==0?55:0,result.remaining());
            assertEquals(0,loaded.get(DefaultEntityStatTypes.getMana()).get(),.001);
            assertEquals(mode==0?10:0,restarted.debit(connected,generation,"new-hit",10).absorbed());
        }
    }
    @Test void playerCapacityRecalculationPreservesNativeCurrentAndClampsOnReduction(){
        var map=stats();var projection=new DerivedStatEntityAdapter();
        var derived=new DerivedStatService(CombatBalanceProfile.loadCanonical(),new EffectiveAttributeService(CombatBalanceProfile.loadCanonical()));
        int health=DefaultEntityStatTypes.getHealth(),mana=DefaultEntityStatTypes.getMana();
        map.setStatValue(health,45);map.setStatValue(mana,25);
        var level99=derived.derive(Map.of(),99);
        for(int i=0;i<3;i++)projection.apply(map,level99);
        assertEquals(388,map.get(health).getMax(),.001);
        assertEquals(45,map.get(health).get(),.001);
        assertEquals(25,map.get(mana).get(),.001);
        projection.apply(map,derived.derive(Map.of(),1));
        assertEquals(100,map.get(health).getMax(),.001);
        assertEquals(45,map.get(health).get(),.001);
        map.setStatValue(health,0);
        projection.apply(map,level99);
        assertEquals(0,map.get(health).get(),.001);
    }
    @Test void rarityAndEmpoweredHealthUseExactlyOneNativeModifierWithoutRefilling(){
        var stats=stats();var profile=profile();int health=DefaultEntityStatTypes.getHealth();
        var providers=providers(1.15,.45);
        HytaleDifficultyCombat.projectHealth(stats,profile,providers,true);
        assertEquals(333.5,stats.get(health).getMax(),.001);assertEquals(333.5,stats.get(health).get(),.001);
        var modifier=(StaticModifier)stats.getModifier(health,HytaleDifficultyCombat.HEALTH_KEY);assertEquals(233.5,modifier.getAmount(),.001);
        stats.subtractStatValue(health,63);
        for(int i=0;i<10;i++)HytaleDifficultyCombat.projectHealth(stats,profile,providers,true);
        assertEquals(270.5,stats.get(health).get(),.001);assertEquals(333.5,stats.get(health).getMax(),.001);
    }
    @Test void injuredBirthAndReloadCannotTurnIntoFullHealth(){
        var stats=stats();var profile=profile();int health=DefaultEntityStatTypes.getHealth();stats.setStatValue(health,72);
        HytaleDifficultyCombat.projectHealth(stats,profile,providers(2.4,0),true);
        assertEquals(480,stats.get(health).getMax(),.001);assertEquals(72,stats.get(health).get(),.001);
        var clone=stats.clone();HytaleDifficultyCombat.projectHealth(clone,profile,providers(2.4,0),false);
        assertEquals(72,clone.get(health).get(),.001);assertEquals(480,clone.get(health).getMax(),.001);
    }
    @Test void resolvedNativeVariantBaselineUsesFrozenTargetWithoutRelaxingForeignModifierGuard(){
        int health=DefaultEntityStatTypes.getHealth();
        for(double[] pair:List.of(new double[]{36,74},new double[]{124,103})){
            var stats=stats();
            stats.putModifier(health,"NPC_Max",new StaticModifier(com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.ModifierTarget.MAX,
                    StaticModifier.CalculationType.ADDITIVE,(float)(pair[0]-100)));
            stats.update();stats.maximizeStatValue(health);
            var base=profile();var target=new EncounterProfileResolver.Resolved(base.worldId(),base.enemyId(),base.difficulty(),base.profileId(),
                    base.worldProfileId(),base.roleId(),base.biomeKey(),base.sourceCombatLevel(),pair[1]*2,base.attackBasis(),2,1,
                    base.resistance(),base.evidence());
            assertThrows(IllegalStateException.class,()->HytaleDifficultyCombat.projectHealth(stats,target,providers(2.4,0),true));
            HytaleDifficultyCombat.projectHealth(stats,target,providers(2.4,0),true,pair[0]);
            assertEquals(pair[1]*4.8,stats.get(health).getMax(),.001);
            stats.subtractStatValue(health,13);float wounded=stats.get(health).get();
            for(int replay=0;replay<3;replay++)HytaleDifficultyCombat.projectHealth(stats,target,providers(2.4,0),false,pair[0]);
            assertEquals(wounded,stats.get(health).get(),.001);
            stats.putModifier(health,"foreign",new StaticModifier(com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.ModifierTarget.MAX,
                    StaticModifier.CalculationType.ADDITIVE,17));stats.update();
            assertThrows(IllegalStateException.class,()->HytaleDifficultyCombat.projectHealth(stats,target,providers(2.4,0),false,pair[0]));
        }
    }
    @Test void unrelatedBaselineIsRejectedAndOrdinaryDifficultyRetainsItsProjection(){
        var stats=stats();var profile=profile();int health=DefaultEntityStatTypes.getHealth();
        HytaleDifficultyCombat.projectHealth(stats,profile,true);assertEquals(200,stats.get(health).get(),.001);
        assertEquals(100,((StaticModifier)stats.getModifier(health,HytaleDifficultyCombat.HEALTH_KEY)).getAmount(),.001);
        stats.removeModifier(health,HytaleDifficultyCombat.HEALTH_KEY);stats.update();
        stats.putModifier(health,"foreign",new StaticModifier(com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.ModifierTarget.MAX,
                StaticModifier.CalculationType.ADDITIVE,17));stats.update();
        assertThrows(IllegalStateException.class,()->HytaleDifficultyCombat.projectHealth(stats,profile,providers(2.4,0),true));
        assertEquals(117,stats.get(health).getMax(),.001);
    }
}
