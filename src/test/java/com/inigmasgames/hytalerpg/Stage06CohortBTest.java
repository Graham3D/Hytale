package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.area.AreaOutline;
import com.inigmasgames.hytalerpg.execution.area.AreaRuntime;
import com.inigmasgames.hytalerpg.execution.area.AreaWorldPort;
import com.inigmasgames.hytalerpg.execution.area.StratifiedAreaPattern;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.inigmasgames.hytalerpg.Stage06AreaRuntimeTest.*;
import static org.junit.jupiter.api.Assertions.*;

class Stage06CohortBTest {
    @Test void everyAreaNativeItemIsTriggerOnlyAndPeriodicCatalogDoesNotAdvertiseCrit() throws Exception {
        for(String id:List.of("ground_slam","frost_nova","root_snare","powder_mine","cold_wave","venom_spray","blizzard","wall_of_fire","poison_cloud",
                "vortex","earthquake","meteor","comet","avalanche","void_cataclysm")) {
            String item="RPG_Ability_"+java.util.Arrays.stream(id.split("_")).map(s->Character.toUpperCase(s.charAt(0))+s.substring(1))
                    .collect(java.util.stream.Collectors.joining("_"));
            try(var input=getClass().getResourceAsStream("/Server/Item/Items/RPG/Abilities/"+item+".json")) {
                assertNotNull(input,item);
                var ability=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("Ability");
                assertEquals(0,ability.get("Cost").getAsDouble());assertEquals(0,ability.get("Cooldown").getAsDouble());
                assertEquals("None",ability.get("CostType").getAsString());assertEquals("Primary",ability.get("Slot").getAsString());
                assertEquals("Root_RPG_Ability_Bridge",ability.get("Cast").getAsString());
            }
        }
        try(var input=getClass().getResourceAsStream("/rpg/catalog/skills.json")) {
            var catalog=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8));
            var records=catalog.isJsonArray()?catalog.getAsJsonArray():catalog.getAsJsonObject().getAsJsonArray("skills");
            assertEquals(89,records.size());
            for(var record:records) {
                var skill=record.getAsJsonObject();String id=skill.get("id").getAsString();
                if(id.equals("wall_of_fire")||id.equals("poison_cloud")||id.equals("vortex")) assertFalse(skill.get("canCrit").getAsBoolean());
                if(id.equals("venom_spray")) {
                    assertEquals("INNATE",skill.get("basePowerSource").getAsString());assertEquals(20,skill.get("innateBasePower").getAsDouble());
                }
            }
        }
    }
    @Test void nativeBombFamilyAndAuditedPowerDoNotRequireAWeaponNameGuess() {
        assertEquals("BOMB",com.inigmasgames.hytalerpg.execution.hytale.HytaleEquipmentAdapter.kind("opaque-item-id",
                java.util.Map.of("Family",new String[]{"Bomb"})));
        assertEquals("BOW",com.inigmasgames.hytalerpg.execution.hytale.HytaleEquipmentAdapter.kind("Weapon_Shortbow_Bomb",java.util.Map.of()));
        var registry=com.inigmasgames.hytalerpg.combat.power.ItemPowerRegistry.loadCanonical();
        assertEquals(20,registry.find("Weapon_Bomb").orElseThrow().weaponPower());
        assertEquals(20,registry.find("Weapon_Bomb_Fire").orElseThrow().weaponPower());
        assertTrue(registry.find("Weapon_Bomb_Stun").isEmpty()); // Never invent power for an unaudited variant.
    }
    @Test void powderMineUsesSeparateTriggerAndExplosionAndNeverExpiresWithDamage() {
        var runtime=new AreaRuntime(); var port=new FakePort(); var context=context("powder_mine");
        port.targets=List.of(target("outside-trigger",3.5,0)); runtime.start(context,Vec3.ZERO,Vec3.FORWARD,0,1,port);
        runtime.tick(context.request().actorId(),.5,port); assertTrue(port.payloads.isEmpty());
        port.targets=List.of(target("trigger",1,0),target("outside-trigger",3.5,0));
        runtime.tick(context.request().actorId(),.75,port); assertEquals(2,port.payloads.size());
        assertEquals(4,port.presented.getLast().radius()); assertEquals(0,runtime.size());
        assertTrue(port.payloads.stream().allMatch(p->p.coefficient()==1.7));
    }
    @Test void coneHasFullAuthoredAngleAndDisjointInnerChillTier() {
        var runtime=new AreaRuntime();var port=new FakePort();var context=context("cold_wave");
        port.targets=List.of(at("near",0,6),at("far",0,7),at("behind",0,-2),at("outside",8,1));
        runtime.start(context,Vec3.ZERO,Vec3.FORWARD,0,1,port);
        assertEquals(List.of("near","far"),port.hitIds);
        assertEquals(List.of(3,2),port.payloads.stream().map(AreaWorldPort.Payload::chillStacks).toList());
        assertEquals(70,port.presented.getFirst().angleDegrees());
    }
    @Test void periodicZonesIntegrateEightSecondsAtAnySupportedTickRateAndThrottleStatus() {
        for(String id:List.of("wall_of_fire","poison_cloud")) for(double dt:new double[]{.05,.25,.5}) {
            var runtime=new AreaRuntime();var port=new FakePort();var context=context(id);port.targets=List.of(at("inside",0,0));
            runtime.start(context,Vec3.ZERO,Vec3.FORWARD,0,1,port);
            for(int i=1;i<=Math.ceil(8/dt);i++)runtime.tick(context.request().actorId(),Math.min(8,i*dt),port);
            assertEquals(32,port.payloads.size());assertEquals(0,runtime.size());
            assertEquals(profile(id).area().coefficient()*8,port.payloads.stream().mapToDouble(AreaWorldPort.Payload::coefficient).sum(),1e-10);
            assertTrue(port.payloads.stream().allMatch(AreaWorldPort.Payload::periodic));
            assertEquals(8,port.payloads.stream().filter(p->!p.status().isBlank()).count());
        }
    }
    @Test void blizzardWarnsBeforeFirstImpactAndDoesNotHaveHiddenFullZoneDamage() {
        var runtime=new AreaRuntime();var port=new FakePort();var context=context("blizzard");
        port.targets=List.of(at("zone-corner-miss",6,0));runtime.start(context,Vec3.ZERO,Vec3.FORWARD,0,1,port);
        assertTrue(port.payloads.isEmpty()); assertTrue(port.presented.stream().anyMatch(s->s.radius()==2));
        for(int i=1;i<=33;i++)runtime.tick(context.request().actorId(),i*.25,port);
        assertEquals(0,runtime.size());assertTrue(port.payloads.isEmpty());
    }
    @Test void stratificationIsDeterministicBoundedAndRootSeeded() {
        var a=StratifiedAreaPattern.offsets("root-a",16,6,2);
        assertEquals(a,StratifiedAreaPattern.offsets("root-a",16,6,2));
        assertNotEquals(a,StratifiedAreaPattern.offsets("root-b",16,6,2));
        assertEquals(16,a.stream().distinct().count());assertTrue(a.stream().allMatch(p->p.horizontalLength()+2<=6));
    }
    @Test void blizzardCannotDamageBeforeWarningOrRetargetAfterWarnedTerrainDisappears() {
        var runtime=new AreaRuntime();var port=new FakePort();var context=context("blizzard");
        port.targets=List.of(new AreaWorldPort.Target("huge-target",new AreaGeometry.Bounds(new Vec3(-8,0,-8),new Vec3(8,2,8)),false));
        runtime.start(context,Vec3.ZERO,Vec3.FORWARD,0,1,port);
        runtime.tick(context.request().actorId(),.24,port);assertTrue(port.payloads.isEmpty());
        port.ground=false;runtime.tick(context.request().actorId(),.25,port);assertTrue(port.payloads.isEmpty());
        runtime.tick(context.request().actorId(),.5,port);assertTrue(port.payloads.isEmpty()); // Missing surface at actual descent completion.
        port.ground=true;
        for(int i=3;i<=33;i++)runtime.tick(context.request().actorId(),i*.25,port);
        assertEquals(0,runtime.size());assertFalse(port.payloads.isEmpty());
        assertTrue(port.payloads.stream().noneMatch(p->p.impactIndex()==0));
        assertTrue(port.payloads.size()<=8); // Per-root .75 s ICD, not every half-second sub-impact.
    }
    @Test void lateWorldTickCancelsAreaRatherThanFabricatingHistoricalContacts() {
        var runtime=new AreaRuntime();var port=new FakePort();var context=context("poison_cloud");port.targets=List.of(at("inside",0,0));
        runtime.start(context,Vec3.ZERO,Vec3.FORWARD,0,1,port);runtime.tick(context.request().actorId(),2,port);
        assertTrue(port.payloads.isEmpty());assertEquals(0,runtime.size());
    }
    @Test void proceduralConeAndWallUseExactGeometryAndBoundedOutlineSegments() {
        var cone=profile("cold_wave").area().footprint(Vec3.ZERO,Vec3.FORWARD,1);
        var lines=AreaOutline.segments(cone);assertTrue(lines.size()<16);
        assertTrue(lines.stream().allMatch(l->l.from().horizontalLength()<=12+1e-9));
        var wall=profile("wall_of_fire").area().footprint(Vec3.ZERO,Vec3.FORWARD,1);
        var wallLines=AreaOutline.segments(wall);assertEquals(12,wallLines.size());
        assertEquals(3.10,wallLines.stream().mapToDouble(l->l.to().y()).max().orElseThrow(),1e-12);
        assertEquals(5,wallLines.stream().mapToDouble(l->Math.abs(l.to().z())).max().orElseThrow());
        assertEquals(1,wallLines.stream().mapToDouble(l->Math.abs(l.to().x())).max().orElseThrow());
    }
    private static AreaWorldPort.Target at(String id,double x,double z) {
        return new AreaWorldPort.Target(id,new AreaGeometry.Bounds(new Vec3(x,0,z),new Vec3(x,1,z)),false);
    }
}
