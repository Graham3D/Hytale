package com.inigmasgames.hytalerpg.enemies;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.combat.DamageCalculator;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.combat.Knockback;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.combat.DirectionalKnockback;
import com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyDamageInteraction;
import com.inigmasgames.hytalerpg.gear.ManagedGearDamageInteraction;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.event.EventBus;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Native codec regression for the extracted scaffold, with no server or fabricated Skill execution. */
class EnemyNativeDamageLeafTest {
    static HytaleAssetStore<String,DamageCause,IndexedLookupTableAssetMap<String,DamageCause>> assets;
    static final class Causes extends IndexedLookupTableAssetMap<String,DamageCause> {
        Causes(){super(DamageCause[]::new);}
        void seed(){putAll("EnemyLeafFixture",DamageCause.CODEC,
                Map.of("Fire",new DamageCause("Fire"),"Physical",new DamageCause("Physical")),Map.of(),Map.of());}
    }
    @BeforeAll static void setup() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        // InteractionModule registers this first: native melee assets omit Type and use its default.
        Knockback.CODEC.register("Directional",DirectionalKnockback.class,DirectionalKnockback.CODEC);
        var map=new Causes();map.seed();
        var builder=HytaleAssetStore.builder(DamageCause.class,(IndexedLookupTableAssetMap<String,DamageCause>)map)
                .setPath("Entity/Damage").setCodec(DamageCause.CODEC).setKeyFunction(DamageCause::getId).setReplaceOnRemove(DamageCause::new);
        assets=new HytaleAssetStore<>(builder){final EventBus events=new EventBus(false);@Override protected EventBus getEventBus(){return events;}};
        AssetRegistry.register(assets);NativeFixtureAssetCache.clear(DamageCause.class);
    }
    @AfterAll static void cleanup(){if(assets!=null)AssetRegistry.unregister(assets);NativeFixtureAssetCache.clear(DamageCause.class);}
    @Test void enemyWrapperPreservesNativeCalculatorDataAndAllNativeLeafBranches(){
        var input=BsonDocument.parse("""
            {"DamageCalculator":{"Type":"Absolute","BaseDamage":{"Physical":23,"Fire":5},"RandomPercentageModifier":0.1},
             "AngledDamage":[{"Angle":90,"AngleDistance":45,"DamageCalculator":{"Type":"Absolute","BaseDamage":{"Physical":12}}}],
             "TargetedDamage":{"Head":{"DamageCalculator":{"Type":"Absolute","BaseDamage":{"Physical":30}}}}}
            """);
        var nativeLeaf=DamageEntityInteraction.CODEC.decode(input,new ExtraInfo());
        var wrapper=NativeEnemyDamageInteraction.CODEC.decode(input,new ExtraInfo());
        assertInstanceOf(NativeEnemyDamageInteraction.Calculator.class,wrapper.getDamageCalculator());
        assertInstanceOf(NativeEnemyDamageInteraction.Calculator.class,wrapper.getAngledDamage()[0].getDamageCalculator());
        assertInstanceOf(NativeEnemyDamageInteraction.Calculator.class,wrapper.getTargetedDamage().get("Head").getDamageCalculator());
        assertEquals(DamageEntityInteraction.CODEC.encode(nativeLeaf,new ExtraInfo()),DamageEntityInteraction.CODEC.encode(wrapper,new ExtraInfo()));
    }
    @Test void gearKeepsItsNativeCoefficientAndRejectsMixedSourceCalculators(){
        var input=BsonDocument.parse("{\"DamageCalculator\":{\"Type\":\"Absolute\",\"BaseDamage\":{\"Physical\":23}}}");
        var gear=ManagedGearDamageInteraction.CODEC.decode(input,new ExtraInfo());
        assertInstanceOf(ManagedGearDamageInteraction.Calculator.class,gear.getDamageCalculator());
        var nativeLeaf=DamageEntityInteraction.CODEC.decode(input,new ExtraInfo());
        assertEquals(DamageCalculator.CODEC.encode(nativeLeaf.getDamageCalculator(),new ExtraInfo()),DamageCalculator.CODEC.encode(gear.getDamageCalculator(),new ExtraInfo()));
        var mixed=BsonDocument.parse("{\"DamageCalculator\":{\"Type\":\"Absolute\",\"BaseDamage\":{\"Physical\":23,\"Fire\":5}}}");
        assertThrows(RuntimeException.class,()->ManagedGearDamageInteraction.CODEC.decode(mixed,new ExtraInfo()));
    }
    @Test void nativePacketArrayUsesOneFrozenVectorAndAggregatesOnceAfterAllComponents(){
        var input=BsonDocument.parse("{\"DamageCalculator\":{\"Type\":\"Absolute\",\"BaseDamage\":{\"Physical\":100,\"Fire\":25}}}");
        var calculator=NativeEnemyDamageInteraction.CODEC.decode(input,new ExtraInfo()).getDamageCalculator();
        var accepted=new java.util.ArrayList<EnemyAppliedHit>();var rejected=new java.util.ArrayList<Throwable>();
        var scope=new com.inigmasgames.hytalerpg.execution.hytale.EnemyNativeStrikeScope(EnemyAppliedHitTest.offense(),java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),
                calculator,()->true,accepted::add,rejected::add);
        var vector=scope.calculate(calculator,0,()->{throw new AssertionError("Collision must not reroll the accepted source");});
        var packets=com.hypixel.hytale.server.core.modules.entity.damage.DamageCalculatorSystems.queueDamageCalculator(
                null,vector,null,null,com.hypixel.hytale.server.core.modules.entity.damage.Damage.NULL_SOURCE,null);
        packets[0].putMetaObject(com.hypixel.hytale.server.core.modules.entity.damage.DamageCalculatorSystems.DAMAGE_SEQUENCE,
                new com.hypixel.hytale.server.core.modules.entity.damage.DamageCalculatorSystems.DamageSequence(
                        new com.hypixel.hytale.server.core.modules.entity.damage.DamageCalculatorSystems.Sequence(),calculator));
        double health=80;
        for(var packet:packets){
            double next=Math.max(0,health-packet.getAmount());
            scope.component(packet).accept(new com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.NativeResult(false,packet.getAmount(),health,next));
            health=next;
        }
        assertTrue(accepted.isEmpty());scope.completed(null);scope.completed(null);
        assertEquals(1,accepted.size());assertEquals(80,accepted.getFirst().actualHealthLoss());assertTrue(rejected.isEmpty());
    }
    @Test void ownerCorrectedKnockbackPreservesTheNativeImpulseIncludingVerticalLaunch(){
        var input=BsonDocument.parse("""
                {"DamageCalculator":{"Type":"Absolute","BaseDamage":{"Physical":23}},
                 "DamageEffects":{"Knockback":{"Force":1,"RelativeX":0,"RelativeZ":-1,"VelocityY":3,"VelocityType":"Set"}}}
                """);
        var nativeLeaf=DamageEntityInteraction.CODEC.decode(input,new ExtraInfo());
        var adapted=NativeEnemyDamageInteraction.CODEC.decode(input,new ExtraInfo());
        assertTrue(adapted.ownsNativeImpulse());
        var before=DamageEntityInteraction.CODEC.encode(adapted,new ExtraInfo());
        var request=EnemyDisplacementMerge.resolve(adapted.ownsNativeImpulse(),0,.8);
        assertTrue(request.preserveNativeImpulse());assertEquals(0,request.horizontalMeters());
        assertEquals(DamageEntityInteraction.CODEC.encode(nativeLeaf,new ExtraInfo()),before);
        assertEquals(before,DamageEntityInteraction.CODEC.encode(adapted,new ExtraInfo()));
        assertFalse(NativeEnemyDamageInteraction.CODEC.decode(BsonDocument.parse("{\"DamageCalculator\":{\"BaseDamage\":{\"Physical\":23}}}"),new ExtraInfo()).ownsNativeImpulse());
    }
    @Test void angledOnlyNativeLeafBindsWithoutInventingAMainCalculator(){
        var input=BsonDocument.parse("{\"AngledDamage\":[{\"Angle\":90,\"AngleDistance\":45,\"DamageCalculator\":{\"Type\":\"Absolute\",\"BaseDamage\":{\"Physical\":12}}}]}");
        var leaf=NativeEnemyDamageInteraction.CODEC.decode(input,new ExtraInfo());
        assertNull(leaf.getDamageCalculator());assertInstanceOf(NativeEnemyDamageInteraction.Calculator.class,leaf.getAngledDamage()[0].getDamageCalculator());
    }
    @Test void acceptanceUsesActualRuntimeAndRejectsVictimDependentOrSequentialCalculators(){
        var absolute=NativeEnemyDamageInteraction.CODEC.decode(BsonDocument.parse("""
                {"RunTime":0.25,"DamageCalculator":{"Type":"Absolute","BaseDamage":{"Physical":100},"RandomPercentageModifier":0}}
                """),new ExtraInfo());
        assertEquals(100,absolute.sampleAtAcceptance().getFloat(DamageCause.getAssetMap().getAsset("Physical")));
        var dps=NativeEnemyDamageInteraction.CODEC.decode(BsonDocument.parse("""
                {"RunTime":0.25,"DamageCalculator":{"Type":"Dps","BaseDamage":{"Physical":100},"RandomPercentageModifier":0}}
                """),new ExtraInfo());
        assertEquals(25,dps.sampleAtAcceptance().getFloat(DamageCause.getAssetMap().getAsset("Physical")));
        for(String extra:java.util.List.of(
                "\"AngledDamage\":[{\"Angle\":90,\"AngleDistance\":45,\"DamageCalculator\":{\"Type\":\"Absolute\",\"BaseDamage\":{\"Physical\":12}}}]",
                "\"TargetedDamage\":{\"Head\":{\"DamageCalculator\":{\"Type\":\"Absolute\",\"BaseDamage\":{\"Physical\":30}}}}",
                "\"DamageAttribute\":\"Damage\"")){
            var leaf=NativeEnemyDamageInteraction.CODEC.decode(BsonDocument.parse("{\"DamageCalculator\":{\"Type\":\"Absolute\",\"BaseDamage\":{\"Physical\":100}},"+extra+"}"),new ExtraInfo());
            assertThrows(IllegalStateException.class,leaf::sampleAtAcceptance);
        }
        var sequence=NativeEnemyDamageInteraction.CODEC.decode(BsonDocument.parse("""
                {"DamageCalculator":{"Type":"Absolute","BaseDamage":{"Physical":100},"SequentialModifierStep":-0.1}}
                """),new ExtraInfo());
        assertThrows(IllegalStateException.class,sequence::sampleAtAcceptance);
    }
}
