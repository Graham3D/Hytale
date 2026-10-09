package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.protocol.ColorLight;
import com.hypixel.hytale.server.core.modules.entity.component.DynamicLight;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsConfig;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.math.shape.Box;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NativeAffixSpatialAdaptersTest {
    private final GearCatalog catalog=GearCatalog.load();
    private GearInstance item(String base,String id,double value) {
        var definition=catalog.affix(id);
        var roll=new GearInstance.AffixRoll(id,definition.side(),definition.exclusionGroup(),1,value,
                new GearRequirements.Gate(1,Map.of()),definition.name(),definition.name());
        return GearInstance.authoredQa(catalog.base(base),UUID.randomUUID(),99,1000,
                GearRarity.UNCOMMON,List.of(roll),BigDecimal.ZERO);
    }
    @Test void flightAndHorizonChangeNativeSpawnQuoteAndPathLedger() {
        var flight=item("gm.shortbow_mithril.h","WA-015",25);
        var horizon=item("gm.shortbow_mithril.h","WA-016",25);
        var a=new GearEffectSnapshot(List.of(flight));
        var speed=NativeAffixProjectileTravel.launch(a,flight.identity(),10,20,2,false);
        assertEquals(1.25f,speed.speedMultiplier());assertEquals(20,speed.maximumTravel());
        assertTrue(speed.safetyLifetimeSeconds()>=1.6f);
        var b=new GearEffectSnapshot(List.of(horizon));
        var range=NativeAffixProjectileTravel.launch(b,horizon.identity(),10,20,2,false);
        assertEquals(1f,range.speedMultiplier());assertEquals(25,range.maximumTravel());
        var both=new GearEffectSnapshot(List.of(flight,horizon));
        assertEquals(20,NativeAffixProjectileTravel.launch(both,flight.identity(),10,20,2,false).maximumTravel());
        assertEquals(1f,NativeAffixProjectileTravel.launch(both,horizon.identity(),10,20,2,false).speedMultiplier());
        var path=new NativeAffixProjectileTravel.Path(range,new Vector3d());
        assertFalse(path.afterNativeMove(new Vector3d(8,0,0))); // A wall may stop the native entity here.
        assertFalse(path.afterNativeMove(new Vector3d(20,0,0)));
        assertTrue(path.afterNativeMove(new Vector3d(25,0,0)));
        assertEquals(25,path.traveled());
    }
    @Test void projectileControlsAndNegativeSourcesStayNative() {
        var flight=item("gm.shortbow_mithril.h","WA-015",25);
        var effects=new GearEffectSnapshot(List.of(flight));
        assertNull(NativeAffixProjectileTravel.launch(GearEffectSnapshot.EMPTY,flight.identity(),10,20,2,false));
        assertNull(NativeAffixProjectileTravel.launch(effects,UUID.randomUUID(),10,20,2,false));
        assertNull(NativeAffixProjectileTravel.launch(effects,flight.identity(),10,20,2,true));
        var quote=NativeAffixProjectileTravel.launch(effects,flight.identity(),10,20,2,false);
        var path=new NativeAffixProjectileTravel.Path(quote,new Vector3d());
        assertFalse(path.afterNativeMove(new Vector3d(12,0,0)));
        var nativeVelocity=new Velocity(new Vector3d(10,0,0));
        path.limitStraightStep(nativeVelocity,1f);
        assertEquals(8,nativeVelocity.getSpeed(),1e-6);
    }
    @Test void launchEventChangesTheInstalledStandardPhysicsVelocityOnce() {
        var flight=item("gm.shortbow_mithril.h","WA-015",25);
        var quote=NativeAffixProjectileTravel.launch(new GearEffectSnapshot(List.of(flight)),
                flight.identity(),10,20,2,false);
        var provider=new StandardPhysicsProvider(new BoundingBox(new Box(-.1,-.1,-.1,.1,.1,.1)),
                UUID.randomUUID(),new StandardPhysicsConfig(),new Vector3d(10,0,0),true);
        provider.getVelocity().set(10,0,0);
        provider.getForceProviderStandardState().nextTickVelocity.set(10,0,0);
        var velocity=new Velocity(new Vector3d(10,0,0));
        NativeAffixProjectileTravel.applyNativeSpeed(quote,velocity,provider);
        assertEquals(12.5,provider.getVelocity().length(),1e-6);
        assertEquals(12.5,provider.getForceProviderStandardState().nextTickVelocity.length(),1e-6);
        assertEquals(12.5,velocity.getSpeed(),1e-6);
        var path=new NativeAffixProjectileTravel.Path(quote,new Vector3d());
        assertFalse(path.afterNativeMove(new Vector3d(12.5,0,0)));
        path.limitNativeStep(velocity,provider,1f);
        assertEquals(7.5,provider.getVelocity().length(),1e-6);
        assertEquals(7.5,velocity.getSpeed(),1e-6);
        var impacts=new AtomicInteger();
        var guarded=NativeAffixProjectileTravel.guardImpact(path,(ref,point,block,entity,detail,buffer)->impacts.incrementAndGet());
        guarded.onImpact(null,new Vector3d(19.9,0,0),null,null,null,null);
        assertEquals(1,impacts.get(),"in-range impact retains the native consumer");
        assertFalse(path.contactWithinBudget(new Vector3d(20.1,0,0)),
                "an overrange native contact is rejected before impact submission");
    }
    @Test void lightComposesHeldNativeComponentAndRestoresIt() {
        var lantern=item("gm.plate_mithril.head.h","WA-155",1);
        var original=new ColorLight((byte)4,(byte)12,(byte)8,(byte)3);
        var light=new DynamicLight(new ColorLight(original));
        var calibrated=new NativeAffixLightProjection.Calibration(1,18);
        var result=NativeAffixLightProjection.project(light,original,new GearEffectSnapshot(List.of(lantern)),calibrated);
        assertEquals(5,result.targetMetres());assertEquals(5,result.effectiveNativeRadius());
        assertEquals(12,Byte.toUnsignedInt(light.getColorLight().red));
        assertEquals(8,Byte.toUnsignedInt(light.getColorLight().green));
        assertEquals(3,Byte.toUnsignedInt(light.getColorLight().blue));
        var torch=new ColorLight((byte)7,(byte)9,(byte)4,(byte)1);
        NativeAffixLightProjection.project(light,torch,new GearEffectSnapshot(List.of(lantern)),calibrated);
        assertEquals(8,Byte.toUnsignedInt(light.getColorLight().radius));
        NativeAffixLightProjection.project(light,torch,GearEffectSnapshot.EMPTY,calibrated);
        assertEquals(torch,light.getColorLight());
        var tiny=item("gm.plate_mithril.head.h","WA-155",.01);
        assertEquals(5,NativeAffixLightProjection.project(light,original,new GearEffectSnapshot(List.of(tiny)),calibrated).effectiveNativeRadius());
    }
    @Test void playerLightSystemOwnerCreatesComposesAndCleansUpOneActorComponent() {
        var lantern=item("gm.plate_mithril.head.h","WA-155",1);
        var effects=new GearEffectSnapshot(List.of(lantern));
        var calibration=new NativeAffixLightProjection.Calibration(1,18);
        var bare=NativeAffixLightSystem.update(null,null,null,GearEffectSnapshot.EMPTY,calibration);
        assertNull(bare.light());assertNull(bare.state());
        var equipped=NativeAffixLightSystem.update(null,null,null,effects,calibration);
        assertEquals(1,Byte.toUnsignedInt(equipped.light().getColorLight().radius));
        assertEquals(1,equipped.state().effectiveNativeRadius());
        var torch=new ColorLight((byte)4,(byte)7,(byte)8,(byte)9);
        var combined=NativeAffixLightSystem.update(equipped.light(),equipped.state(),torch,effects,calibration);
        assertSame(equipped.light(),combined.light());
        assertEquals(5,Byte.toUnsignedInt(combined.light().getColorLight().radius));
        assertEquals(7,Byte.toUnsignedInt(combined.light().getColorLight().red));
        var removed=NativeAffixLightSystem.update(combined.light(),combined.state(),torch,
                GearEffectSnapshot.EMPTY,calibration);
        assertFalse(removed.removeLight());assertNotNull(removed.state());
        assertEquals(torch,removed.light().getColorLight());
        var putAway=NativeAffixLightSystem.update(removed.light(),removed.state(),null,
                GearEffectSnapshot.EMPTY,calibration);
        assertTrue(putAway.removeLight());assertNull(putAway.state());
        var normal=new DynamicLight(new ColorLight(torch));
        var withNormal=NativeAffixLightSystem.update(normal,null,torch,effects,calibration);
        var restored=NativeAffixLightSystem.update(normal,withNormal.state(),torch,
                GearEffectSnapshot.EMPTY,calibration);
        assertFalse(restored.removeLight());assertEquals(torch,normal.getColorLight());
        var external=new DynamicLight(new ColorLight(torch));
        var owned=NativeAffixLightSystem.update(external,null,torch,effects,calibration);
        var otherWriter=new ColorLight((byte)6,(byte)3,(byte)2,(byte)1);
        external.setColorLight(otherWriter);
        var clean=NativeAffixLightSystem.update(external,owned.state(),null,GearEffectSnapshot.EMPTY,calibration);
        assertFalse(clean.removeLight());assertEquals(otherWriter,external.getColorLight());
    }
    @Test void nativeLightCalibrationRequiresMeasuredLinearUnitsAndExplicitCeiling() {
        var fitted=NativeAffixLightProjection.fit(
                new NativeAffixLightProjection.Observation(1,1),
                new NativeAffixLightProjection.Observation(4,4),
                new NativeAffixLightProjection.Observation(8,8),18,.01);
        assertEquals(1,fitted.metresPerUnit());assertEquals(18,fitted.maximumNativeRadius());
        assertThrows(IllegalArgumentException.class,()->NativeAffixLightProjection.fit(
                new NativeAffixLightProjection.Observation(1,1),
                new NativeAffixLightProjection.Observation(4,4),
                new NativeAffixLightProjection.Observation(8,6),18,.01));
        assertThrows(IllegalArgumentException.class,()->new NativeAffixLightProjection.Calibration(1,255));
        var item=item("gm.plate_mithril.head.h","WA-155",1);
        var light=new DynamicLight(new ColorLight((byte)18,(byte)1,(byte)2,(byte)3));
        var result=NativeAffixLightProjection.project(light,light.getColorLight(),
                new GearEffectSnapshot(List.of(item)),fitted);
        assertEquals(19,result.targetMetres());assertEquals(18,result.effectiveNativeRadius());
        var capped=NativeAffixLightProjection.project(light,new ColorLight((byte)18,(byte)1,(byte)2,(byte)3),
                new GearEffectSnapshot(List.of(item)),new NativeAffixLightProjection.Calibration(1,15));
        assertEquals(16,capped.targetMetres());assertEquals(15,capped.effectiveNativeRadius());
        assertEquals(18,Byte.toUnsignedInt(light.getColorLight().radius));
    }
    @Test void finalNativeSegmentClipsVisibleEndpointAndReturnStartsFreshBudget() {
        var flight=item("gm.shortbow_mithril.h","WA-015",25);
        var quote=NativeAffixProjectileTravel.launch(new GearEffectSnapshot(List.of(flight)),
                flight.identity(),10,20,2,false);
        var path=new NativeAffixProjectileTravel.Path(quote,new org.joml.Vector3d(0,0,0));
        assertFalse(path.afterNativeMove(new org.joml.Vector3d(19,0,0)));
        assertEquals(20,path.terminalPoint(new org.joml.Vector3d(23,0,0)).x,1e-9);
        assertTrue(path.afterNativeMove(new org.joml.Vector3d(23,0,0)));
        path.restart(new org.joml.Vector3d(20,0,0),5);
        assertEquals(5,path.remaining(),1e-9);
        assertFalse(path.afterNativeMove(new org.joml.Vector3d(17,0,0)));
    }
    @Test void gatheringAdmitsOnlyProtectedClaimedMaterialThroughExistingReceiptOwner() {
        var legs=item("gm.plate_mithril.legs.h","WA-156",.75);
        var effects=new GearEffectSnapshot(List.of(legs));
        var material=new NativeAffixMaterialPickup.ItemKind("Ingredient_Bar_Gold",100,false,false,true);
        assertEquals(2.5,NativeAffixMaterialPickup.reach(effects,1.75));
        assertEquals(2.5,NativeAffixMaterialPickup.reach(GearEffectSnapshot.EMPTY,2.5));
        assertTrue(NativeAffixMaterialPickup.admit(effects,1.75,new NativeAffixMaterialPickup.Candidate(material,2.4,true,true,true,true)));
        assertFalse(NativeAffixMaterialPickup.admit(effects,1.75,new NativeAffixMaterialPickup.Candidate(material,2.6,true,true,true,true)));
        assertFalse(NativeAffixMaterialPickup.admit(GearEffectSnapshot.EMPTY,1.75,new NativeAffixMaterialPickup.Candidate(material,2.4,true,true,true,true)));
        assertFalse(NativeAffixMaterialPickup.admit(effects,1.75,new NativeAffixMaterialPickup.Candidate(
                new NativeAffixMaterialPickup.ItemKind("RPG_Gear_Helmet",1,false,true,false),2,true,true,true,true)));
        assertTrue(NativeAffixMaterialPickup.admit(effects,1.75,new NativeAffixMaterialPickup.Candidate(
                new NativeAffixMaterialPickup.ItemKind("Rock_Aqua",50,false,false,true),2.4,true,true,true,true)));
        assertFalse(NativeAffixMaterialPickup.admit(effects,1.75,new NativeAffixMaterialPickup.Candidate(
                new NativeAffixMaterialPickup.ItemKind("Furniture_Crude_Table",50,false,false,true),2.4,true,true,true,true)));
        assertFalse(NativeAffixMaterialPickup.admit(effects,1.75,new NativeAffixMaterialPickup.Candidate(material,2.4,true,true,false,true)));
        assertFalse(NativeAffixMaterialPickup.admit(effects,1.75,new NativeAffixMaterialPickup.Candidate(material,2.4,true,true,true,false)));
        assertFalse(NativeAffixMaterialPickup.admit(effects,1.75,new NativeAffixMaterialPickup.Candidate(material,2.4,false,true,true,true)));
    }
}
