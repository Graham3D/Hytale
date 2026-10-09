package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.damage.CriticalRoller;
import com.inigmasgames.hytalerpg.combat.damage.DamageCalculationService;
import com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets;
import com.inigmasgames.hytalerpg.combat.damage.SkillScalingService;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata;
import com.inigmasgames.hytalerpg.gear.*;
import com.inigmasgames.hytalerpg.execution.hytale.GearSignatureBindings;
import com.inigmasgames.hytalerpg.execution.hytale.NativeGearSignatureProcs;
import com.inigmasgames.hytalerpg.execution.hytale.NativeGearSignatureDefenseFilter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Frozen admitted equipment -> authoritative receipt -> next-tick native Damage -> controlled recipient. */
class SignatureConsumerProofTest {
    private static NativeAssetTestFixtures assets;
    private static final GearCatalog CATALOG=GearCatalog.load();
    private static final GearAffixQaSuite QA=new GearAffixQaSuite(CATALOG);
    private static final UUID WORLD=UUID.randomUUID(),OWNER=UUID.randomUUID(),TARGET=UUID.randomUUID(),OTHER=UUID.randomUUID();
    private static final Map<RpgAttribute,Integer> STATS=Map.of(RpgAttribute.STR,500,RpgAttribute.DEX,500,
            RpgAttribute.INT,500,RpgAttribute.WIS,500,RpgAttribute.LUCK,500);
    @BeforeAll static void open() throws Exception {assets=NativeAssetTestFixtures.open();assets.loadDamageCauses();}
    @AfterAll static void close(){if(assets!=null)assets.close();}

    private static GearInstance item(String id,boolean rolled){
        var definition=CATALOG.affix(id);
        var base=CATALOG.bases().stream().filter(b->new GearBindings().require(b.id()).mapped()
                &&GearDropGenerator.eligible(definition,b)&&b.id().endsWith(".nm")).findFirst().orElseThrow();
        var fixture=QA.fixtures().stream().filter(f->f.fixtureId().equals("ab-"+id.toLowerCase()+"-affixed"))
                .findFirst().orElseThrow();
        var authored=QA.preview(fixture).affixes().getFirst();
        var roll=new GearInstance.AffixRoll(id,definition.side(),definition.exclusionGroup(),authored.tier(),
                authored.value(),new GearRequirements.Gate(1,Map.of()),definition.name(),definition.name());
        return GearInstance.authoredQa(base,UUID.randomUUID(),base.sourceWindow().getLast(),1000,
                GearRarity.UNCOMMON,rolled?List.of(roll):List.of(),BigDecimal.ZERO);
    }
    private static GearEffectSnapshot equipment(GearInstance item,boolean intact,boolean slot){
        var result=GearEquipmentResolution.resolve(99,STATS,List.of(
                new GearEquipmentResolution.Candidate(item,true,intact,slot)));
        assertEquals(intact&&slot?List.of(item):List.of(),result.validItems());
        return result.effects().snapshot();
    }
    private static GearSignatureProcRuntime.Contact contact(GearInstance item,GearEffectSnapshot snapshot,
            String root,UUID target,double hp,double loss,GearSignatureProcRuntime.Kind kind,boolean noProc,
            boolean protectedTarget,boolean credited){
        return new GearSignatureProcRuntime.Contact(WORLD,OWNER,item.identity(),target,root,root,snapshot,
                true,true,true,noProc,false,false,loss,hp,1000,kind,100,
                Map.of(GearCombatEffects.Channel.PHYSICAL,100d,GearCombatEffects.Channel.FIRE,20d),1,
                protectedTarget,false,credited);
    }
    private static final class Recipient implements NativeGearSignatureProcs.NativeSubmit {
        final Map<UUID,Double> health=new HashMap<>();
        final List<Damage> submitted=new ArrayList<>();
        final Map<UUID,Integer> deaths=new HashMap<>();
        final DamageCalculationService calculation=new DamageCalculationService(
                new SkillScalingService(CombatBalanceProfile.loadCanonical()),new CriticalRoller(()->1));
        Recipient(UUID target,double hp){health.put(target,hp);}
        Recipient add(UUID target,double hp){health.put(target,hp);return this;}
        @Override public boolean eligible(GearSignatureProcRuntime.Child child){return health.getOrDefault(child.target(),0d)>0;}
        @Override public Damage.Source source(GearSignatureProcRuntime.Child child){return Damage.NULL_SOURCE;}
        @Override public double targetHealthBefore(GearSignatureProcRuntime.Child child){return health.get(child.target());}
        @Override public void submit(GearSignatureProcRuntime.Child child,GearCombatEffects.Channel channel,Damage damage){
            assertFalse(damage.isCancelled());
            var meta=HytaleDamageAdapter.metadata(damage);
            assertFalse(meta.canProc());assertEquals(child.root(),meta.rootCastId());
            assertEquals(child.kind()==GearSignatureProcRuntime.ChildKind.RETRIBUTION
                    ?HytaleDamageMetadata.Origin.REFLECTED:HytaleDamageMetadata.Origin.TRIGGERED,meta.origin());
            assertEquals(health.get(child.target()),meta.targetHealthBefore(),1e-8);
            accept(child.target(),damage);
        }
        void accept(UUID target,Damage damage){
            var resolved=calculation.calculate(new DamageCalculationService.Request(damage.getAmount(),0,1,
                    ModifierBuckets.NONE,false,0,1));
            double before=health.get(target);
            double after=Math.max(0,before-resolved.toHytaleDamageFloat());
            health.put(target,after);submitted.add(damage);
            if(before>0&&after==0)deaths.merge(target,1,Integer::sum);
        }
        double hp(UUID target){return health.get(target);}
    }
    private static final class Port implements GearSignatureProcRuntime.Port {
        final NativeGearSignatureProcs queue=new NativeGearSignatureProcs(ignored->false);
        List<UUID> nearby=List.of();int executions;
        @Override public void enqueue(GearSignatureProcRuntime.Child child){queue.enqueue(child,1);}
        @Override public boolean execute(GearSignatureProcRuntime.Contact contact){
            var child=GearSignatureBindings.cullChild(contact);
            child.ifPresent(this::enqueue);
            if(child.isPresent())executions++;
            return child.isPresent();
        }
        @Override public List<UUID> burstTargets(GearSignatureProcRuntime.Contact contact,double radius,int maximum){
            assertEquals(2,radius);assertEquals(64,maximum);return nearby;
        }
        int drain(Recipient recipient){assertEquals(0,queue.drain(WORLD,1,recipient));return queue.drain(WORLD,2,recipient);}
    }
    @Test void crushingAndBarbedReachRecipientWithIcdExpiryAndAdmissionControls(){
        var crushing=item("WA-135",true);var snap=equipment(crushing,true,true);
        var p=new Port();var r=new GearSignatureProcRuntime(()->0);var victim=new Recipient(TARGET,1000);
        r.applied(contact(crushing,snap,"c1",TARGET,1000,20,GearSignatureProcRuntime.Kind.COMMON,false,false,false),1,p);
        r.applied(contact(crushing,snap,"c1",TARGET,1000,20,GearSignatureProcRuntime.Kind.COMMON,false,false,false),1,p);
        r.applied(contact(crushing,snap,"c2",TARGET,1000,20,GearSignatureProcRuntime.Kind.COMMON,false,false,false),1.2,p);
        assertEquals(1,p.drain(victim));assertEquals(960,victim.hp(TARGET),1e-5);
        assertEquals(0,p.queue.pending(WORLD));
        r.applied(contact(crushing,snap,"c3",TARGET,960,20,GearSignatureProcRuntime.Kind.COMMON,false,false,false),2.1,p);
        assertEquals(1,p.queue.drain(WORLD,3,victim));assertTrue(victim.hp(TARGET)<960);
        for(var excluded:List.of(equipment(crushing,false,true),equipment(crushing,true,false),
                equipment(item("WA-135",false),true,true))){
            var control=new Port();new GearSignatureProcRuntime(()->0).applied(
                    contact(crushing,excluded,"excluded",TARGET,1000,20,GearSignatureProcRuntime.Kind.COMMON,false,false,false),1,control);
            assertEquals(0,control.drain(new Recipient(TARGET,1000)));
        }
        var denied=new Port();r.applied(contact(crushing,snap,"noproc",TARGET,900,20,
                GearSignatureProcRuntime.Kind.COMMON,true,false,false),4,denied);
        assertEquals(0,denied.drain(new Recipient(TARGET,1000)));

        var barbed=item("WA-137",true);var fragments=new GearSignatureProcRuntime(()->0);var q=new Port();
        var recipient=new Recipient(TARGET,500).add(OTHER,500);var equipped=equipment(barbed,true,true);
        fragments.applied(contact(barbed,equipped,"b1",TARGET,500,10,GearSignatureProcRuntime.Kind.COMMON,false,false,false),1,q);
        assertEquals(0,q.drain(recipient));
        fragments.applied(contact(barbed,equipped,"wrong",OTHER,500,10,GearSignatureProcRuntime.Kind.COMMON,false,false,false),2,q);
        fragments.applied(contact(barbed,equipped,"b2",TARGET,500,10,GearSignatureProcRuntime.Kind.COMMON,false,false,false),2,q);
        assertEquals(1,q.queue.drain(WORLD,2,recipient));assertEquals(492,recipient.hp(TARGET),1e-5);
        assertEquals(500,recipient.hp(OTHER),1e-5);
        fragments.applied(contact(barbed,equipped,"late",TARGET,492,10,GearSignatureProcRuntime.Kind.COMMON,false,false,false),8.1,q);
        assertEquals(0,q.queue.drain(WORLD,3,recipient));
        for(var control:List.of(equipment(item("WA-137",false),true,true),equipment(barbed,false,true))){
            var plain=new GearSignatureProcRuntime(()->0);var deniedPort=new Port();
            plain.applied(contact(barbed,control,"first",TARGET,500,10,GearSignatureProcRuntime.Kind.COMMON,false,false,false),1,deniedPort);
            plain.applied(contact(barbed,control,"second",TARGET,500,10,GearSignatureProcRuntime.Kind.COMMON,false,false,false),2,deniedPort);
            assertEquals(0,deniedPort.drain(new Recipient(TARGET,500)));
        }
        var noProcPort=new Port();fragments.applied(contact(barbed,equipped,"noProc",TARGET,492,10,
                GearSignatureProcRuntime.Kind.COMMON,true,false,false),9,noProcPort);
        assertEquals(0,noProcPort.drain(new Recipient(TARGET,500)));
    }
    @Test void cullDrainsToDeathOnceAndRejectsThresholdBossProtectedAndDuplicate(){
        var gear=item("WA-139",true);var snapshot=equipment(gear,true,true);var p=new Port();
        var runtime=new GearSignatureProcRuntime(()->0);var survivor=new Recipient(TARGET,50);
        var contact=contact(gear,snapshot,"execute",TARGET,50,10,GearSignatureProcRuntime.Kind.COMMON,false,false,false);
        runtime.applied(contact,1,p);runtime.applied(contact,1,p);
        assertEquals(1,p.executions);assertEquals(1,p.drain(survivor));
        assertEquals(0,survivor.hp(TARGET));assertEquals(1,survivor.deaths.get(TARGET));
        runtime.applied(contact(gear,snapshot,"later",TARGET,50,10,GearSignatureProcRuntime.Kind.COMMON,false,false,false),2,p);
        assertEquals(0,p.queue.drain(WORLD,3,survivor));
        for(var rejected:List.of(
                contact(gear,snapshot,"above",OTHER,51,10,GearSignatureProcRuntime.Kind.COMMON,false,false,false),
                contact(gear,snapshot,"boss",OTHER,50,10,GearSignatureProcRuntime.Kind.BOSS,false,false,false),
                contact(gear,snapshot,"protected",OTHER,50,10,GearSignatureProcRuntime.Kind.COMMON,false,true,false),
                contact(gear,snapshot,"noProc",OTHER,50,10,GearSignatureProcRuntime.Kind.COMMON,true,false,false),
                contact(gear,equipment(gear,false,true),"broken",OTHER,50,10,GearSignatureProcRuntime.Kind.COMMON,false,false,false),
                contact(gear,equipment(item("WA-139",false),true,true),"plain",OTHER,50,10,
                        GearSignatureProcRuntime.Kind.COMMON,false,false,false))){
            var control=new Port();new GearSignatureProcRuntime(()->0).applied(rejected,1,control);
            assertEquals(0,control.drain(new Recipient(OTHER,50)));
        }
    }
    @Test void armorBreakAndFortifyMutateSubmittedNativeHitsAndExpire(){
        var armor=item("WA-138",true);var protect=item("WA-140",true);
        var ordinary=new Damage(Damage.NULL_SOURCE,assets.damageCause("Physical"),50);
        var broken=new Damage(Damage.NULL_SOURCE,assets.damageCause("Physical"),50);
        var r=new GearSignatureProcRuntime(()->0);var p=new Port();
        r.applied(contact(armor,equipment(armor,true,true),"armor",TARGET,500,10,
                GearSignatureProcRuntime.Kind.COMMON,false,false,false),1,p);
        NativeGearSignatureDefenseFilter.applyFactor(broken,NativeGearSignatureDefenseFilter.physicalDefenseFactor(
                new NativeGearSignatureDefenseFilter.Rating(100,100),r.armorRatingFactor(TARGET,2)));
        assertTrue(broken.getAmount()>ordinary.getAmount());
        var normalRecipient=new Recipient(TARGET,500);var brokenRecipient=new Recipient(TARGET,500);
        normalRecipient.accept(TARGET,ordinary);brokenRecipient.accept(TARGET,broken);
        assertTrue(brokenRecipient.hp(TARGET)<normalRecipient.hp(TARGET));
        var expired=new Damage(Damage.NULL_SOURCE,assets.damageCause("Physical"),50);
        NativeGearSignatureDefenseFilter.applyFactor(expired,NativeGearSignatureDefenseFilter.physicalDefenseFactor(
                new NativeGearSignatureDefenseFilter.Rating(100,100),r.armorRatingFactor(TARGET,4.1)));
        assertEquals(ordinary.getAmount(),expired.getAmount());
        var unrolledArmor=new GearSignatureProcRuntime(()->0);
        unrolledArmor.applied(contact(armor,equipment(item("WA-138",false),true,true),"plain",TARGET,500,10,
                GearSignatureProcRuntime.Kind.COMMON,false,false,false),1,new Port());
        assertEquals(1,unrolledArmor.armorRatingFactor(TARGET,2));
        var noProcArmor=new GearSignatureProcRuntime(()->0);
        noProcArmor.applied(contact(armor,equipment(armor,true,true),"noProc",TARGET,500,10,
                GearSignatureProcRuntime.Kind.COMMON,true,false,false),1,new Port());
        assertEquals(1,noProcArmor.armorRatingFactor(TARGET,2));
        var f=new GearSignatureProcRuntime(()->0);
        f.applied(contact(protect,equipment(protect,true,true),"fortify",TARGET,500,10,
                GearSignatureProcRuntime.Kind.COMMON,false,false,false),1,p);
        var incoming=new Damage(Damage.NULL_SOURCE,assets.damageCause("Physical"),100);
        NativeGearSignatureDefenseFilter.applyFactor(incoming,f.incomingHitFactor(OWNER,2,true,false));
        assertEquals(95,incoming.getAmount());
        var fortifiedRecipient=new Recipient(OWNER,500);
        fortifiedRecipient.accept(OWNER,incoming);
        assertEquals(405,fortifiedRecipient.hp(OWNER));
        assertEquals(1,f.incomingHitFactor(TARGET,2,true,false));
        assertEquals(1,f.incomingHitFactor(OWNER,2,false,true));
        assertEquals(1,f.incomingHitFactor(OWNER,4.1,true,false));
        var noProcFortify=new GearSignatureProcRuntime(()->0);
        noProcFortify.applied(contact(protect,equipment(protect,true,true),"noProc",TARGET,500,10,
                GearSignatureProcRuntime.Kind.COMMON,true,false,false),1,new Port());
        assertEquals(1,noProcFortify.incomingHitFactor(OWNER,2,true,false));
        for(var control:List.of(equipment(protect,false,true),equipment(item("WA-140",false),true,true))){
            var noFortify=new GearSignatureProcRuntime(()->0);
            noFortify.applied(contact(protect,control,"control",TARGET,500,10,
                    GearSignatureProcRuntime.Kind.COMMON,false,false,false),1,new Port());
            assertEquals(1,noFortify.incomingHitFactor(OWNER,2,true,false));
        }
    }
    @Test void retributionAndKillBurstDeliverNoProcReflectedAndCreditedRecipients(){
        var shield=item("WA-142",true);var r=new GearSignatureProcRuntime(()->0);var p=new Port();
        var recipient=new Recipient(TARGET,500);
        r.received(WORLD,OWNER,TARGET,"reflect","hit",equipment(shield,true,true),true,true,false,false,10,false,1,p);
        r.received(WORLD,OWNER,TARGET,"reflect","hit",equipment(shield,true,true),true,true,false,false,10,false,1,p);
        assertEquals(1,p.drain(recipient));
        assertEquals(500-shield.affixes().getFirst().value(),recipient.hp(TARGET),1e-5);
        assertEquals(HytaleDamageMetadata.Origin.REFLECTED,HytaleDamageAdapter.metadata(recipient.submitted.getFirst()).origin());
        r.received(WORLD,OWNER,TARGET,"locked","hit",equipment(shield,true,true),true,true,false,false,10,false,1.2,p);
        assertEquals(0,p.queue.drain(WORLD,3,recipient));
        r.received(WORLD,OWNER,TARGET,"recursive","hit",equipment(shield,true,true),true,true,true,false,10,false,2,p);
        assertEquals(0,p.queue.drain(WORLD,3,recipient));
        var control=new Port();new GearSignatureProcRuntime(()->0).received(WORLD,OWNER,TARGET,"control","hit",
                equipment(item("WA-142",false),true,true),true,true,false,false,10,false,1,control);
        assertEquals(0,control.drain(new Recipient(TARGET,500)));

        var burst=item("WA-143",true);var b=new GearSignatureProcRuntime(()->0);var targets=new Port();
        targets.nearby=List.of(TARGET,OTHER);var deaths=new Recipient(OTHER,100);
        var kill=contact(burst,equipment(burst,true,true),"kill",TARGET,0,10,
                GearSignatureProcRuntime.Kind.COMMON,false,false,true);
        b.creditedKill(kill,1,targets);b.creditedKill(kill,1,targets);
        assertEquals(2,targets.drain(deaths));assertEquals(58,deaths.hp(OTHER),1e-5);
        assertEquals(0,deaths.deaths.getOrDefault(OTHER,0));
        var noCredit=new Port();new GearSignatureProcRuntime(()->0).creditedKill(
                contact(burst,equipment(burst,true,true),"uncredited",TARGET,0,10,
                        GearSignatureProcRuntime.Kind.COMMON,false,false,false),1,noCredit);
        assertEquals(0,noCredit.drain(new Recipient(OTHER,100)));
        var noRoll=new Port();new GearSignatureProcRuntime(()->0).creditedKill(
                contact(burst,equipment(item("WA-143",false),true,true),"unrolled",TARGET,0,10,
                        GearSignatureProcRuntime.Kind.COMMON,false,false,true),1,noRoll);
        assertEquals(0,noRoll.drain(new Recipient(OTHER,100)));
        targets.nearby=List.of(OTHER);
        b.creditedKill(contact(burst,equipment(burst,true,true),"icd",TARGET,0,10,
                GearSignatureProcRuntime.Kind.COMMON,false,false,true),1.2,targets);
        assertEquals(0,targets.queue.drain(WORLD,3,deaths));
    }
}
