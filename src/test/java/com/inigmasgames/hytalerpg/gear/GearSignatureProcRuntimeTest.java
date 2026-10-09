package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.execution.hytale.NativeGearSignatureProcs;
import com.inigmasgames.hytalerpg.execution.hytale.NativeGearSignatureDefenseFilter;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GearSignatureProcRuntimeTest {
    private static com.inigmasgames.hytalerpg.gear.NativeAssetTestFixtures nativeAssets;
    @org.junit.jupiter.api.BeforeAll static void installNativeAssets() throws Exception {
        nativeAssets = com.inigmasgames.hytalerpg.gear.NativeAssetTestFixtures.open();
    }
    @org.junit.jupiter.api.AfterAll static void closeNativeAssets() {
        if (nativeAssets != null) nativeAssets.close();
    }

    private static final UUID WORLD=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OWNER=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TARGET=UUID.fromString("00000000-0000-0000-0000-000000000003");
    private final GearAffixQaSuite suite=new GearAffixQaSuite(GearCatalog.load());
    private GearInstance item(String id){
        var fixture=suite.fixtures().stream().filter(f->f.fixtureId().equals("ab-"+id.toLowerCase()+"-affixed")).findFirst().orElseThrow();
        return suite.preview(fixture);
    }
    private GearSignatureProcRuntime.Contact contact(GearInstance gear,String root,String id,double remaining,
                                                      double loss,GearSignatureProcRuntime.Kind kind,boolean melee,
                                                      boolean protectedTarget,boolean kill){
        return new GearSignatureProcRuntime.Contact(WORLD,OWNER,gear.identity(),TARGET,root,id,
                new GearEffectSnapshot(List.of(gear)),true,melee,true,false,false,false,
                loss,remaining,1000,kind,100,Map.of(GearCombatEffects.Channel.PHYSICAL,100d,
                GearCombatEffects.Channel.FIRE,20d),1,protectedTarget,false,kill);
    }
    private static final class Port implements GearSignatureProcRuntime.Port {
        final List<GearSignatureProcRuntime.Child> children=new ArrayList<>();
        final List<GearSignatureProcRuntime.Contact> executes=new ArrayList<>();
        List<UUID> nearby=List.of();
        boolean executeAccept=true;
        @Override public void enqueue(GearSignatureProcRuntime.Child child){children.add(child);}
        @Override public boolean execute(GearSignatureProcRuntime.Contact contact){executes.add(contact);return executeAccept;}
        @Override public List<UUID> burstTargets(GearSignatureProcRuntime.Contact c,double radius,int maximum){
            assertEquals(2,radius,1e-9);assertEquals(64,maximum);return nearby;
        }
    }
    @Test void deadlyUsesLocalValidItemAndOnlyNoncriticalPhysical(){
        var gear=item("WA-136");var snapshot=new GearEffectSnapshot(List.of(gear));
        var purposes=new ArrayList<String>();
        var runtime=new GearSignatureProcRuntime((GearSignatureProcRuntime.Roll)key->{purposes.add(key);return 0;});
        assertEquals(200,runtime.deadly(snapshot,gear.identity(),"strike",100,false,true,false,1),1e-9);
        assertEquals(List.of("strike/WA-136"),purposes);
        assertEquals(100,runtime.deadly(snapshot,gear.identity(),"strike",100,true,true,false,1),1e-9);
        assertEquals(100,runtime.deadly(snapshot,gear.identity(),"strike",100,false,true,true,1),1e-9);
        assertEquals(100,runtime.deadly(snapshot,UUID.randomUUID(),"strike",100,false,true,false,1),1e-9);
        assertEquals(100,new GearSignatureProcRuntime(()->.999).deadly(snapshot,gear.identity(),"strike",100,false,true,false,1),1e-9);
        assertEquals(100,runtime.deadly(snapshot,gear.identity(),"zero",100,false,true,false,0),1e-9);
    }
    @Test void crushingUsesSurvivingHealthCapIcdAndNativeEnvelope(){
        var gear=item("WA-135");var runtime=new GearSignatureProcRuntime(()->0);var port=new Port();
        runtime.applied(contact(gear,"root", "one",1000,20,GearSignatureProcRuntime.Kind.COMMON,true,false,false),1,port);
        assertEquals(40,port.children.getFirst().channels().get(GearCombatEffects.Channel.PHYSICAL).doubleValue(),1e-9);
        assertTrue(port.children.getFirst().noProc());assertTrue(port.children.getFirst().noCrit());assertTrue(port.children.getFirst().noLeech());
        var nativeDamage=NativeGearSignatureProcs.nativeDamage(port.children.getFirst(),GearCombatEffects.Channel.PHYSICAL,Damage.NULL_SOURCE,1000);
        assertEquals(40,nativeDamage.getAmount(),1e-6);
        assertEquals("Physical",nativeDamage.getCause().getId());
        var metadata=HytaleDamageAdapter.metadata(nativeDamage);assertFalse(metadata.canProc());
        assertEquals(HytaleDamageMetadata.Origin.TRIGGERED,metadata.origin());
        assertEquals(1000,metadata.targetHealthBefore(),1e-9);
        assertEquals("root",metadata.rootCastId());
        assertTrue(metadata.correlationId().contains("/one/"));
        var nativeQueue=new NativeGearSignatureProcs(ignored->{fail("No execution for Crushing");return false;});
        nativeQueue.enqueue(port.children.getFirst(),1);assertEquals(1,nativeQueue.pending(WORLD));
        var submitted=new ArrayList<Damage>();
        NativeGearSignatureProcs.NativeSubmit accepted=new NativeGearSignatureProcs.NativeSubmit(){
            @Override public boolean eligible(GearSignatureProcRuntime.Child child){return true;}
            @Override public Damage.Source source(GearSignatureProcRuntime.Child child){return Damage.NULL_SOURCE;}
            @Override public double targetHealthBefore(GearSignatureProcRuntime.Child child){return 1000;}
            @Override public void submit(GearSignatureProcRuntime.Child child,GearCombatEffects.Channel channel,Damage damage){submitted.add(damage);}
        };
        assertEquals(0,nativeQueue.drain(WORLD,1,accepted));assertEquals(1,nativeQueue.pending(WORLD));
        assertEquals(1,nativeQueue.drain(WORLD,2,accepted));assertEquals(1,submitted.size());
        assertEquals(0,nativeQueue.pending(WORLD));
        nativeQueue.enqueue(port.children.getFirst(),2);
        assertEquals(0,nativeQueue.drain(WORLD,3,new NativeGearSignatureProcs.NativeSubmit(){
            @Override public boolean eligible(GearSignatureProcRuntime.Child child){return false;}
            @Override public Damage.Source source(GearSignatureProcRuntime.Child child){fail("Rejected child must not acquire a source");return Damage.NULL_SOURCE;}
            @Override public double targetHealthBefore(GearSignatureProcRuntime.Child child){fail("Rejected child must not read HP");return 0;}
            @Override public void submit(GearSignatureProcRuntime.Child child,GearCombatEffects.Channel channel,Damage damage){fail("Rejected child must not submit");}
        }));
        assertEquals(1,submitted.size());
        runtime.applied(contact(gear,"root2","two",1000,20,GearSignatureProcRuntime.Kind.COMMON,true,false,false),1.5,port);
        assertEquals(1,port.children.size());
        runtime.applied(contact(gear,"root3","three",100000,20,GearSignatureProcRuntime.Kind.COMMON,true,false,false),2.1,port);
        assertEquals(200,port.children.getLast().channels().get(GearCombatEffects.Channel.PHYSICAL).doubleValue(),1e-9);
        runtime.applied(contact(gear,"root4","four",1000,20,GearSignatureProcRuntime.Kind.BOSS,true,false,false),4,port);
        assertEquals(2,port.children.size());
        runtime.applied(contact(gear,"root5","five",1000,0,GearSignatureProcRuntime.Kind.COMMON,true,false,false),5,port);
        assertEquals(2,port.children.size());
        var failedChance=new Port();new GearSignatureProcRuntime(()->.999).applied(
                contact(gear,"failed","failed",1000,20,GearSignatureProcRuntime.Kind.COMMON,true,false,false),1,failedChance);
        assertTrue(failedChance.children.isEmpty());
    }
    @Test void barbedConsumesOldestChargeWithoutCreationHitAndExpires(){
        var gear=item("WA-137");var runtime=new GearSignatureProcRuntime(()->0);var port=new Port();
        runtime.applied(contact(gear,"r1","c1",500,10,GearSignatureProcRuntime.Kind.COMMON,true,false,false),1,port);
        assertTrue(port.children.isEmpty());
        for(int n=2;n<=4;n++)runtime.applied(contact(gear,"r"+n,"c"+n,500,10,GearSignatureProcRuntime.Kind.COMMON,true,false,false),n,port);
        assertEquals(3,port.children.size());
        assertTrue(port.children.stream().allMatch(c->c.channels().get(GearCombatEffects.Channel.PHYSICAL)==8));
        runtime.applied(contact(gear,"r5","c5",500,10,GearSignatureProcRuntime.Kind.COMMON,true,false,false),12,port);
        assertEquals(3,port.children.size());
        runtime.applied(contact(gear,"r6","c6",500,0,GearSignatureProcRuntime.Kind.COMMON,true,false,false),13,port);
        assertEquals(3,port.children.size());
    }
    @Test void rivetingAndFortifyingAreTimedAndDoNotStackOrRefresh(){
        var armor=item("WA-138");var protection=item("WA-140");var runtime=new GearSignatureProcRuntime(()->0);var port=new Port();
        runtime.applied(contact(armor,"a1","a1",500,10,GearSignatureProcRuntime.Kind.COMMON,true,false,false),1,port);
        assertEquals(.85,runtime.armorRatingFactor(TARGET,2));
        assertEquals(200d/185d,NativeGearSignatureDefenseFilter.physicalDefenseFactor(
                new NativeGearSignatureDefenseFilter.Rating(100,100),.85),1e-9);
        assertEquals(1,NativeGearSignatureDefenseFilter.physicalDefenseFactor(
                new NativeGearSignatureDefenseFilter.Rating(1000,100),.85),1e-9);
        var nativeArmorHit=new Damage(Damage.NULL_SOURCE,DamageCause.PHYSICAL,50);
        NativeGearSignatureDefenseFilter.applyFactor(nativeArmorHit,NativeGearSignatureDefenseFilter.physicalDefenseFactor(
                new NativeGearSignatureDefenseFilter.Rating(100,100),runtime.armorRatingFactor(TARGET,2)));
        assertEquals(100d/1.85,nativeArmorHit.getAmount(),1e-5);
        runtime.applied(contact(armor,"a2","a2",500,10,GearSignatureProcRuntime.Kind.COMMON,true,false,false),2,port);
        assertEquals(1,runtime.armorRatingFactor(TARGET,4.1));
        runtime.applied(contact(armor,"a3","a3",500,10,GearSignatureProcRuntime.Kind.BOSS,true,false,false),6.1,port);
        assertEquals(1,runtime.armorRatingFactor(TARGET,7));
        runtime.applied(contact(protection,"p1","p1",500,10,GearSignatureProcRuntime.Kind.COMMON,true,false,false),1,port);
        assertEquals(.95,runtime.incomingHitFactor(OWNER,2,true,false));
        var nativeFortifiedHit=new Damage(Damage.NULL_SOURCE,DamageCause.PHYSICAL,100);
        NativeGearSignatureDefenseFilter.applyFactor(nativeFortifiedHit,runtime.incomingHitFactor(OWNER,2,true,false));
        assertEquals(95,nativeFortifiedHit.getAmount(),1e-6);
        var periodicControl=new Damage(Damage.NULL_SOURCE,DamageCause.PHYSICAL,10);
        NativeGearSignatureDefenseFilter.applyFactor(periodicControl,runtime.incomingHitFactor(OWNER,2,false,true));
        assertEquals(10,periodicControl.getAmount(),1e-6);
        assertEquals(1,runtime.incomingHitFactor(OWNER,2,false,true));
        runtime.applied(contact(protection,"p2","p2",500,10,GearSignatureProcRuntime.Kind.COMMON,true,false,false),2,port);
        assertEquals(1,runtime.incomingHitFactor(OWNER,4.1,true,false));
        runtime.applied(contact(protection,"p3","p3",500,10,GearSignatureProcRuntime.Kind.COMMON,true,false,false),6.1,port);
        assertEquals(.95,runtime.incomingHitFactor(OWNER,7,true,false));
        runtime.clearWorld(WORLD);
        assertEquals(1,runtime.incomingHitFactor(OWNER,7,true,false));
        assertEquals(1,runtime.armorRatingFactor(TARGET,7));
        var rangedRuntime=new GearSignatureProcRuntime(()->0);
        rangedRuntime.applied(contact(protection,"range","range",500,10,GearSignatureProcRuntime.Kind.COMMON,false,false,false),1,port);
        assertEquals(1,rangedRuntime.incomingHitFactor(OWNER,2,true,false));
    }
    @Test void cullUsesRealSurvivorThresholdAndProtectedVeto(){
        var gear=item("WA-139");var runtime=new GearSignatureProcRuntime(()->0);var port=new Port();
        runtime.applied(contact(gear,"one","one",50,10,GearSignatureProcRuntime.Kind.COMMON,true,false,false),1,port);
        assertEquals(1,port.executes.size());
        runtime.applied(contact(gear,"two","two",51,10,GearSignatureProcRuntime.Kind.COMMON,true,false,false),2,port);
        runtime.applied(contact(gear,"three","three",50,10,GearSignatureProcRuntime.Kind.BOSS,true,false,false),3,port);
        runtime.applied(contact(gear,"four","four",50,10,GearSignatureProcRuntime.Kind.COMMON,true,true,false),4,port);
        runtime.applied(contact(gear,"five","five",50,0,GearSignatureProcRuntime.Kind.COMMON,true,false,false),5,port);
        assertEquals(1,port.executes.size());
        var vetoRuntime=new GearSignatureProcRuntime(()->0);var vetoPort=new Port();vetoPort.executeAccept=false;
        vetoRuntime.applied(contact(gear,"veto","veto",50,10,GearSignatureProcRuntime.Kind.COMMON,true,false,false),1,vetoPort);
        vetoPort.executeAccept=true;
        vetoRuntime.applied(contact(gear,"later","later",50,10,GearSignatureProcRuntime.Kind.COMMON,true,false,false),2,vetoPort);
        assertEquals(2,vetoPort.executes.size());
    }
    @Test void retributionRequiresActualHostileMeleeAndPairLock(){
        var shield=item("WA-142");var snapshot=new GearEffectSnapshot(List.of(shield));
        var runtime=new GearSignatureProcRuntime(()->0);var port=new Port();
        runtime.received(WORLD,OWNER,TARGET,"root","one",snapshot,true,true,false,false,10,false,1,port);
        assertEquals(shield.affixes().getFirst().value(),port.children.getFirst().channels().get(GearCombatEffects.Channel.PHYSICAL).doubleValue(),1e-9);
        runtime.received(WORLD,OWNER,TARGET,"root2","two",snapshot,true,true,false,false,10,false,1.2,port);
        runtime.received(WORLD,OWNER,TARGET,"root3","three",snapshot,false,true,false,false,10,false,2,port);
        runtime.received(WORLD,OWNER,TARGET,"root4","four",snapshot,true,true,true,false,10,false,2,port);
        runtime.received(WORLD,OWNER,TARGET,"root5","five",snapshot,true,true,false,false,0,false,3,port);
        assertEquals(1,port.children.size());
        var metadata=HytaleDamageAdapter.metadata(NativeGearSignatureProcs.nativeDamage(port.children.getFirst(),GearCombatEffects.Channel.PHYSICAL,Damage.NULL_SOURCE,100));
        assertEquals(HytaleDamageMetadata.Origin.REFLECTED,metadata.origin());assertTrue(metadata.noLeech());
    }
    @Test void killBurstPreservesChannelsAndCannotRepeatFromChildOrReplay(){
        var gear=item("WA-143");var runtime=new GearSignatureProcRuntime(()->0);var port=new Port();
        var other=UUID.randomUUID();port.nearby=List.of(TARGET,other);
        var kill=contact(gear,"kill","one",0,10,GearSignatureProcRuntime.Kind.COMMON,true,false,true);
        runtime.applied(kill,1,port);assertTrue(port.children.isEmpty());
        runtime.creditedKill(kill,1,port);runtime.creditedKill(kill,1,port);
        assertEquals(1,port.children.size());
        assertEquals(other,port.children.getFirst().target());
        assertEquals(35,port.children.getFirst().channels().get(GearCombatEffects.Channel.PHYSICAL).doubleValue(),1e-9);
        assertEquals(7,port.children.getFirst().channels().get(GearCombatEffects.Channel.FIRE).doubleValue(),1e-9);
        var nativeFire=NativeGearSignatureProcs.nativeDamage(port.children.getFirst(),GearCombatEffects.Channel.FIRE,Damage.NULL_SOURCE,100);
        assertEquals("Fire",nativeFire.getCause().getId());assertEquals(7,nativeFire.getAmount(),1e-6);
        runtime.creditedKill(contact(gear,"kill2","two",0,10,GearSignatureProcRuntime.Kind.COMMON,true,false,true),1.2,port);
        assertEquals(1,port.children.size());
        runtime.creditedKill(contact(gear,"kill3","three",0,0,GearSignatureProcRuntime.Kind.COMMON,true,false,true),3,port);
        assertEquals(1,port.children.size());
        var failedChance=new Port();failedChance.nearby=List.of(other);
        new GearSignatureProcRuntime(()->.999).creditedKill(kill,1,failedChance);
        assertTrue(failedChance.children.isEmpty());
    }
}
