package com.inigmasgames.hytalerpg.execution.summon;

import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.SkillExecutionResult;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem;
import com.inigmasgames.hytalerpg.gear.*;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.ApplicationEffects;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.protocol.EntityStatResetBehavior;
import com.inigmasgames.hytalerpg.combat.damage.*;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata;
import com.inigmasgames.hytalerpg.gear.NativeAssetTestFixtures;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.component.ComponentAccessor;
import org.bson.BsonDocument;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Reuses the canonical Wolf cast harness across package boundaries, then observes the lease owner. */
final class MasterAffixSummonCompletionTest {
    private static final GearCatalog CATALOG=GearCatalog.load();
    private static final GearAffixQaSuite QA=new GearAffixQaSuite(CATALOG);
    private static SkillExecutionContext castWolf(){return castSummon("wolf_summon");}
    private static SkillExecutionContext castSummon(String skill){
        try{
            var type=Class.forName("com.inigmasgames.hytalerpg.Stage10SummonTest$Harness");
            var constructor=type.getDeclaredConstructor(String.class);constructor.setAccessible(true);
            var harness=constructor.newInstance(skill);
            var cast=type.getDeclaredMethod("cast");cast.setAccessible(true);
            assertTrue(((SkillExecutionResult)cast.invoke(harness)).committed());
            var context=type.getSuperclass().getDeclaredField("context");context.setAccessible(true);
            return (SkillExecutionContext)context.get(harness);
        }catch(ReflectiveOperationException error){throw new AssertionError("Canonical summon harness inaccessible",error);}
    }
    private static GearInstance item(String id){
        return QA.preview(QA.fixtures().stream().filter(f->f.fixtureId().equals("ab-"+id.toLowerCase(Locale.ROOT)+"-affixed"))
                .findFirst().orElseThrow());
    }
    private static GearEffectSnapshot admitted(GearInstance source,boolean intact){
        try{
            var resolve=GearEquipmentResolution.class.getDeclaredMethod("resolve",int.class,Map.class,Collection.class,Set.class);
            resolve.setAccessible(true);
            var attributes=new EnumMap<com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute,Integer>(com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.class);
            for(var attribute:com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.values())attributes.put(attribute,500);
            var result=(GearEquipmentResolution.Result)resolve.invoke(null,99,attributes,
                    List.of(new GearEquipmentResolution.Candidate(source,true,intact,true)),Set.of(source.affixes().getFirst().familyId()));
            if(intact)assertEquals(List.of(source),result.validItems());
            else {assertEquals("BROKEN",result.rejected().get(source.identity()));assertTrue(result.validItems().isEmpty());}
            return result.effects().snapshot();
        }catch(ReflectiveOperationException error){throw new AssertionError("Candidate summon equipment admission unavailable",error);}
    }
    private static SummonRegistry.Lease lease(SkillExecutionContext cast,GearInstance source){
        var effects=source==null?GearEffectSnapshot.EMPTY:admitted(source,true);
        return new SummonRegistry().reserve(cast,0,null,effects).getFirst();
    }
    private static SummonRegistry.Lease broken(SkillExecutionContext cast,GearInstance source){
        return new SummonRegistry().reserve(cast,0,null,admitted(source,false)).getFirst();
    }
    @Test void wa113ChangesActualSummonAttackCoefficient() throws Exception {
        var cast=castWolf();var source=item("WA-113");var control=lease(cast,null);var treated=lease(cast,source);
        try(var fixture=NativeAssetTestFixtures.open()){
            var cause=fixture.damageCause("Physical");
            float plain=submittedWolfHit(cast,control,cause);
            float rolled=submittedWolfHit(cast,treated,cause);
            float invalid=submittedWolfHit(cast,broken(cast,source),cause);
            assertTrue(rolled>plain);
            assertEquals(plain,invalid,1e-6);
            var unrelated=lease(cast,null);
            assertEquals(plain,submittedWolfHit(cast,unrelated,cause),1e-6);
            assertNotEquals(treated.token(),unrelated.token());
            var decoyCast=castSummon("simulacrum");
            var decoyRegistry=new SummonRegistry();
            var decoy=decoyRegistry.reserve(decoyCast,0,null,admitted(source,true)).getFirst();
            assertEquals(0,decoy.coefficient(),1e-9);
            assertTrue(decoyRegistry.activate(decoy,UUID.randomUUID(),0));
            assertEquals(0,decoyRegistry.claimAttack(decoy.token(),1));
        }
        assertEquals(control.maximumHealth(),treated.maximumHealth(),1e-9);
        assertEquals(control.coefficient(),broken(cast,source).coefficient(),1e-9);
    }
    private static float submittedWolfHit(SkillExecutionContext cast,SummonRegistry.Lease lease,DamageCause cause){
        var damage=new DamageCalculationService(new SkillScalingService(
                com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile.loadCanonical()),new CriticalRoller(()->1d));
        var result=damage.calculate(new DamageCalculationService.Request(100,0,lease.coefficient(),
                ModifierBuckets.NONE,false,0,1));
        String root=cast.rootCastId();
        var hit=GearCombatEffects.skill(GearEffectSnapshot.EMPTY,null,root,GearCombatEffects.Channel.PHYSICAL,
                result,ModifierBuckets.NONE,lease.coefficient(),true,false,0,null,1,null);
        var metadata=new HytaleDamageMetadata(cast.request().actorId(),root,cast.skillInstanceId(),
                cast.request().correlationId()+"/offline-contact",hit.amount(GearCombatEffects.Channel.PHYSICAL),Double.NaN,
                "offline-contact",true,HytaleDamageMetadata.Origin.DIRECT);
        return HytaleDamageAdapter.prepareGearDamage(null,cause,metadata,hit,GearCombatEffects.Channel.PHYSICAL,
                null,cast).getAmount();
    }
    @Test void wa114ChangesActualSummonMaximumHealth() throws Exception {
        var cast=castWolf();var source=item("WA-114");var control=lease(cast,null);var treated=lease(cast,source);
        assertTrue(treated.maximumHealth()>control.maximumHealth());
        assertEquals(control.coefficient(),treated.coefficient(),1e-9);
        assertEquals(control.maximumHealth(),broken(cast,source).maximumHealth(),1e-9);
        int priorHealth=DefaultEntityStatTypes.getHealth();
        try(var fixture=NativeAssetTestFixtures.open()){
            fixture.seedActionReferences("{\"EntityStatId\":\"Health\"}");
            DefaultEntityStatTypes.update();
            var plain=nativeHealth(100,83);var rolled=nativeHealth(100,83);var invalid=nativeHealth(100,83);
            HytaleSummonSystem.projectNativeHealth(plain,control,null);
            HytaleSummonSystem.projectNativeHealth(rolled,treated,null);
            HytaleSummonSystem.projectNativeHealth(invalid,broken(cast,source),null);
            int health=DefaultEntityStatTypes.getHealth();
            assertEquals(control.maximumHealth(),plain.get(health).getMax(),.001);
            assertEquals(treated.maximumHealth(),rolled.get(health).getMax(),.001);
            assertEquals(control.maximumHealth(),invalid.get(health).getMax(),.001);
            for(var stats:List.of(plain,rolled,invalid))
                assertEquals(17,stats.get(health).getMax()-stats.get(health).get(),.001);
        }finally{
            var field=DefaultEntityStatTypes.class.getDeclaredField("HEALTH");field.setAccessible(true);
            field.setInt(null,priorHealth);
        }
    }
    private static EntityStatMap nativeHealth(float maximum,float current) throws Exception {
        var type=new EntityStatType("Health",(int)maximum,0,(int)maximum,false,null,null,null,EntityStatResetBehavior.InitialValue);
        int index=DefaultEntityStatTypes.getHealth();
        var map=new EntityStatMap();
        var field=EntityStatMap.class.getDeclaredField("values");field.setAccessible(true);
        var values=new EntityStatValue[index+1];values[index]=new EntityStatValue(index,type);
        field.set(map,values);map.setStatValue(index,current);
        return map;
    }
    @Test void wa115ChangesActualPhysicalDamageGuardFromCapturedNativeArmor() throws Exception {
        var cast=castWolf();var source=item("WA-115");
        var controlRegistry=new SummonRegistry();var treatedRegistry=new SummonRegistry();var brokenRegistry=new SummonRegistry();
        var control=controlRegistry.reserve(cast,0).getFirst();
        var treated=treatedRegistry.reserve(cast,0,null,admitted(source,true)).getFirst();
        control.captureNativeProtection(Map.of("Physical",.3));
        treated.captureNativeProtection(Map.of("Physical",.3));
        assertTrue(HytaleSummonSystem.incomingResistance(treated,"Physical")>
                HytaleSummonSystem.incomingResistance(control,"Physical"));
        assertEquals(0,HytaleSummonSystem.incomingResistance(treated,"Ice"),1e-9);
        var rejected=brokenRegistry.reserve(cast,0,null,admitted(source,false)).getFirst();
        rejected.captureNativeProtection(Map.of("Physical",.3));
        assertEquals(HytaleSummonSystem.incomingResistance(control,"Physical"),
                HytaleSummonSystem.incomingResistance(rejected,"Physical"),1e-9);
        try(var fixture=NativeAssetTestFixtures.open()){
            assertNativeReduction(treatedRegistry,treated,controlRegistry,control,brokenRegistry,rejected,
                    fixture.damageCause("Physical"),fixture.damageCause("Ice"));
        }
    }
    @Test void wa116ChangesActualElementalDamageGuardOnly() throws Exception {
        var cast=castWolf();var source=item("WA-116");
        var controlRegistry=new SummonRegistry();var treatedRegistry=new SummonRegistry();var brokenRegistry=new SummonRegistry();
        var control=controlRegistry.reserve(cast,0).getFirst();
        var treated=treatedRegistry.reserve(cast,0,null,admitted(source,true)).getFirst();
        var rejected=brokenRegistry.reserve(cast,0,null,admitted(source,false)).getFirst();
        assertTrue(HytaleSummonSystem.incomingResistance(treated,"Ice")>
                HytaleSummonSystem.incomingResistance(control,"Ice"));
        assertEquals(HytaleSummonSystem.incomingResistance(control,"Physical"),
                HytaleSummonSystem.incomingResistance(treated,"Physical"),1e-9);
        assertEquals(HytaleSummonSystem.incomingResistance(control,"Ice"),
                HytaleSummonSystem.incomingResistance(rejected,"Ice"),1e-9);
        try(var fixture=NativeAssetTestFixtures.open()){
            assertNativeReduction(treatedRegistry,treated,controlRegistry,control,brokenRegistry,rejected,
                    fixture.damageCause("Ice"),fixture.damageCause("Physical"));
        }
    }
    private static void assertNativeReduction(SummonRegistry treatedRegistry,SummonRegistry.Lease treated,
                                              SummonRegistry controlRegistry,SummonRegistry.Lease control,
                                              SummonRegistry brokenRegistry,SummonRegistry.Lease broken,
                                              DamageCause cause,DamageCause wrongCause){
        var source=new Damage.EntitySource(null); // The live filter has already checked attacker hostility.
        var active=new Damage(source,cause,100);
        var plain=new Damage(source,cause,100);
        var invalid=new Damage(source,cause,100);
        HytaleSummonSystem.filterIncoming(treatedRegistry,treated.token(),true,active);
        HytaleSummonSystem.filterIncoming(controlRegistry,control.token(),true,plain);
        HytaleSummonSystem.filterIncoming(brokenRegistry,broken.token(),true,invalid);
        assertTrue(active.getAmount()<plain.getAmount());
        assertEquals(plain.getAmount(),invalid.getAmount(),.001);
        var unrelated=new Damage(source,wrongCause,100);
        HytaleSummonSystem.filterIncoming(treatedRegistry,treated.token(),true,unrelated);
        assertEquals(100,unrelated.getAmount(),.001);
        var wrongOrigin=new Damage(Damage.NULL_SOURCE,cause,100);
        HytaleSummonSystem.filterIncoming(treatedRegistry,treated.token(),true,wrongOrigin);
        assertEquals(100,wrongOrigin.getAmount(),.001);
        var cancelled=new Damage(source,cause,100);cancelled.setCancelled(true);
        HytaleSummonSystem.filterIncoming(treatedRegistry,treated.token(),true,cancelled);
        assertEquals(100,cancelled.getAmount(),.001);
        var foreign=new Damage(source,cause,100);
        HytaleSummonSystem.filterIncoming(treatedRegistry,UUID.randomUUID(),true,foreign);
        assertEquals(100,foreign.getAmount(),.001);
    }
    @Test void wa117ChangesActualClaimedAttackCadence(){
        var cast=castWolf();var controlRegistry=new SummonRegistry();var treatedRegistry=new SummonRegistry();
        var control=controlRegistry.reserve(cast,0).getFirst();
        var source=item("WA-117");
        var treated=treatedRegistry.reserve(cast,0,null,admitted(source,true)).getFirst();
        assertTrue(treated.interval()<control.interval());
        assertEquals(control.interval(),broken(cast,source).interval(),1e-9);
        assertTrue(controlRegistry.activate(control,UUID.randomUUID(),0));
        assertTrue(treatedRegistry.activate(treated,UUID.randomUUID(),0));
        assertEquals(1,treatedRegistry.claimAttack(treated.token(),treated.interval()));
        assertEquals(0,controlRegistry.claimAttack(control.token(),treated.interval()));
        assertEquals(1,controlRegistry.claimAttack(control.token(),control.interval()));
    }
    @Test void wa119ExtendsActualNaturalExpiryAndAttackWindow(){
        var cast=castWolf();var controlRegistry=new SummonRegistry();var treatedRegistry=new SummonRegistry();
        var control=controlRegistry.reserve(cast,0).getFirst();
        var source=item("WA-119");
        var treated=treatedRegistry.reserve(cast,0,null,admitted(source,true)).getFirst();
        assertTrue(treated.expires()>control.expires());
        assertEquals(control.expires(),broken(cast,source).expires(),1e-9);
        assertTrue(controlRegistry.activate(control,UUID.randomUUID(),0));
        assertTrue(treatedRegistry.activate(treated,UUID.randomUUID(),0));
        assertEquals(0,controlRegistry.claimAttack(control.token(),control.expires()));
        assertTrue(treatedRegistry.claimAttack(treated.token(),control.expires())>0);
        assertEquals(0,treatedRegistry.claimAttack(treated.token(),treated.expires()));
    }
    @Test void wa118InstallsPackagedNativeMovementPayload() throws Exception {
        var cast=castWolf();var source=item("WA-118");
        var treated=lease(cast,source);var control=lease(cast,null);var removed=broken(cast,source);
        double points=treated.ownerEffects().value("WA-118");
        assertTrue(points>0);
        try(var fixture=NativeAssetTestFixtures.open();var nativeComponents=new NativeSummonComponentFixture()){
            var storeField=NativeAssetTestFixtures.class.getDeclaredField("effectStore");storeField.setAccessible(true);
            @SuppressWarnings("unchecked")
            var store=(HytaleAssetStore<String,EntityEffect,?>)storeField.get(fixture);
            String id=SummonNativeMovement.assetId(points);
            var file=Path.of("src/main/resources/Server/Entity/Effects/RPG/Summon",id+".json");
            var decoded=store.decode("SummonPursuitFixture",id,BsonDocument.parse(Files.readString(file)));
            assertNotNull(decoded);
            var map=EntityEffect.getAssetMap();
            var seed=map.getClass().getDeclaredMethod("seed",Map.class);seed.setAccessible(true);
            seed.invoke(map,Map.of(id,decoded));
            var controller=new EffectControllerComponent();
            @SuppressWarnings("unchecked")
            var accessor=(ComponentAccessor<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>)
                    java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ComponentAccessor.class},
                            (proxy,method,args)->null);
            var accepted=SummonNativeMovement.synchronize(treated,controller,null,accessor,map::getAsset).orElseThrow();
            assertEquals(id,accepted.effectId());
            assertEquals((float)(1+points/100),accepted.horizontalSpeedMultiplier(),1e-6);
            assertEquals(.25f,controller.getActiveEffects().get(map.getIndex(id)).getRemainingDuration(),1e-6);
            assertTrue(controller.hasEffect(decoded));
            assertTrue(SummonNativeMovement.apply(control,key->fail("unrolled lookup"),effect->fail("unrolled add"))
                    .isEmpty());
            assertTrue(SummonNativeMovement.apply(removed,key->fail("broken lookup"),effect->fail("broken add"))
                    .isEmpty());
            assertTrue(SummonNativeMovement.synchronize(removed,controller,null,accessor,map::getAsset).isEmpty());
            assertFalse(controller.hasEffect(decoded));
            assertThrows(IllegalStateException.class,()->SummonNativeMovement.apply(treated,key->decoded,effect->false));
            assertThrows(IllegalStateException.class,()->SummonNativeMovement.apply(treated,
                    key->nativeEffect(key,1f),effect->fail("mismatched speed reached controller")));
        }
    }
    private static EntityEffect nativeEffect(String id,float speed){
        return new EntityEffect(id){
            private final ApplicationEffects payload=new ApplicationEffects(){
                @Override public float getHorizontalSpeedMultiplier(){return speed;}
            };
            @Override public ApplicationEffects getApplicationEffects(){return payload;}
        };
    }
}
