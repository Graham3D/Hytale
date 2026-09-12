package com.inigmasgames.hytalerpg;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets;
import com.inigmasgames.hytalerpg.combat.power.BasePowerSource;
import com.inigmasgames.hytalerpg.combat.resource.ResourceCost;
import com.inigmasgames.hytalerpg.combat.snapshot.CombatSnapshot;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.SkillExecutionRequest;
import com.inigmasgames.hytalerpg.execution.Stage04SkillProfile;
import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.area.AreaRuntime;
import com.inigmasgames.hytalerpg.execution.area.AreaWorldPort;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage06AreaRuntimeTest {
    @Test void groundSlamUsesOneAuthoritativeBurstAndLinearAuthoredFalloff() {
        var context = context("ground_slam"); var runtime = new AreaRuntime(); var port = new FakePort();
        port.targets = List.of(target("centre", 0, 0), target("edge", 5, 0), target("above", 0, 3.1));
        runtime.start(context, Vec3.ZERO, Vec3.FORWARD, 0, 1, port);
        assertEquals(0, runtime.size()); assertEquals(2, port.payloads.size());
        assertEquals(1.55, port.payloads.get(0).coefficient(), 1e-12);
        assertEquals(1.55 * .7, port.payloads.get(1).coefficient(), 1e-12);
        assertEquals(2, port.payloads.getFirst().displacement());
        runtime.tick(context.request().actorId(), 2, port); assertEquals(2, port.payloads.size());
        assertSame(context, port.contexts.getFirst());
    }
    @Test void frostNovaQueriesBoundsAndRequestsExactlyTwoChillWithoutExpandingArrivalHits() {
        var runtime = new AreaRuntime(); var port = new FakePort(); var context = context("frost_nova");
        port.targets = List.of(new AreaWorldPort.Target("edge-bounds",
                new AreaGeometry.Bounds(new Vec3(5.4, 0, 0), new Vec3(6.4, 2, 1)), false));
        runtime.start(context, Vec3.ZERO, Vec3.FORWARD, 1, 1, port);
        assertEquals(1, port.payloads.size()); assertEquals(2, port.payloads.getFirst().chillStacks());
        assertEquals("CHILL", port.payloads.getFirst().status()); assertEquals(5.5, port.presented.getFirst().radius());
        runtime.tick(context.request().actorId(), 1.3, port); assertEquals(1, port.payloads.size());
    }
    @Test void trapArmsThenHitsOnlyFirstSusceptibleTargetAndTerminates() {
        var runtime = new AreaRuntime(); var port = new FakePort(); var context = context("root_snare");
        port.targets = List.of(target("far", 2, 0), target("near", 1, 0));
        runtime.start(context, Vec3.ZERO, Vec3.FORWARD, 0, 1, port);
        runtime.tick(context.request().actorId(), .49, port); assertTrue(port.payloads.isEmpty());
        runtime.tick(context.request().actorId(), .75, port);
        assertEquals(List.of("near"), port.hitIds); assertEquals("ROOT", port.payloads.getFirst().status());
        assertEquals(3, port.payloads.getFirst().statusSeconds()); assertEquals(0, runtime.size());
    }
    @Test void trapExpiryCannotDetonateAndCleanupDropsLedgers() {
        var runtime = new AreaRuntime(); var port = new FakePort(); var context = context("root_snare");
        runtime.start(context, Vec3.ZERO, Vec3.FORWARD, 0, 1, port);
        port.targets = List.of(target("late", 0, 0)); runtime.tick(context.request().actorId(), 15, port);
        assertTrue(port.payloads.isEmpty()); assertEquals(0, runtime.size());
        runtime.start(context, Vec3.ZERO, Vec3.FORWARD, 20, 1, port);
        assertEquals("TRAP_ALREADY_DEPLOYED", runtime.admission(context.request().actorId(), "root_snare", true));
        assertEquals(List.of(context), runtime.cancel(context.request().actorId()));
        assertTrue(runtime.cancel(context.request().actorId()).isEmpty()); assertEquals(0, runtime.size());
    }
    @Test void overflowAndLosRejectWithoutPartialDamage() {
        var runtime = new AreaRuntime(); var port = new FakePort(); var context = context("frost_nova");
        port.targets = List.of(target("blocked", 1, 0)); port.los = false;
        runtime.start(context, Vec3.ZERO, Vec3.FORWARD, 0, 1, port); assertTrue(port.payloads.isEmpty());
        port.los = true; port.overflow = true;
        runtime.start(context, Vec3.ZERO, Vec3.FORWARD, 1, 1, port); assertTrue(port.payloads.isEmpty());
        assertTrue(port.events.contains("AREA_QUERY_REJECTED"));
    }
    @Test void expandedRadiusChangesDeclaredFootprintNotHeightOrConeAngle() {
        var profile = profile("frost_nova"); var expanded = profile.area().footprint(Vec3.ZERO, Vec3.FORWARD, 1.25);
        assertEquals(6.875, expanded.radius()); assertEquals(3, expanded.height());
    }

    static Stage04SkillProfile profile(String id) {
        return com.inigmasgames.hytalerpg.execution.Stage04SkillProfiles.loadCanonical(
                com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical()).require(id);
    }
    static SkillExecutionContext context(String id) {
        UUID owner = UUID.randomUUID(); var bundle = Stage01BTestSupport.bundle();
        assertTrue(bundle.service().equipSkill(owner, SkillSlot.SKILL01, new SkillId(id)).success());
        var plan = bundle.service().getPresentationView(owner).plans().get(SkillSlot.SKILL01);
        String instance = "area-" + UUID.randomUUID();
        var snapshot = new CombatSnapshot("area-root", instance, owner, Map.of(), Map.of(), null,
                "fixture", null, BasePowerSource.INNATE, 20, plan.planHash(), profile(id).damageCoefficient(),
                0, 1.5, ModifierBuckets.NONE, ResourceCost.NONE, 0, Map.of());
        return new SkillExecutionContext(new SkillExecutionRequest(owner, SkillSlot.SKILL01, "fixture", 6,
                "area-correlation", Vec3.FORWARD), "area-root", instance, profile(id), plan, snapshot, null);
    }
    static AreaWorldPort.Target target(String id, double x, double y) {
        return new AreaWorldPort.Target(id, new AreaGeometry.Bounds(new Vec3(x, y, 0), new Vec3(x, y + 1, 0)), false);
    }
    record Data(int schemaVersion, List<Stage04SkillProfile> skills) { }
    static final class FakePort implements AreaWorldPort {
        List<Target> targets = List.of(); boolean overflow, los = true;
        final List<Payload> payloads = new ArrayList<>(); final List<String> hitIds = new ArrayList<>();
        final List<SkillExecutionContext> contexts = new ArrayList<>();
        final List<AreaGeometry> presented = new ArrayList<>(); final List<String> events = new ArrayList<>();
        boolean ground = true, roof;
        public java.util.Optional<Vec3> sweepShard(Vec3 from,Vec3 to,double radius){
            return ground&&from.y()>0&&to.y()<=radius?java.util.Optional.of(new Vec3(to.x(),0,to.z())):java.util.Optional.empty();
        }
        final List<Vec3> descending = new ArrayList<>();
        public boolean overheadClear(AreaGeometry shape,double height) { return !roof; }
        public void descendingVisual(SkillExecutionContext context,Vec3 position,double seconds) { descending.add(position); }
        public Query query(AreaGeometry shape, int budget) { return new Query(targets, overflow); }
        public boolean lineOfSight(Vec3 origin, Target target) { return los; }
        public java.util.Optional<AreaGeometry> prepareImpact(Vec3 parent, AreaGeometry shape) {
            return ground ? java.util.Optional.of(shape) : java.util.Optional.empty();
        }
        public boolean apply(SkillExecutionContext context, Target target, Payload payload) {
            contexts.add(context); hitIds.add(target.id()); payloads.add(payload); return true;
        }
        public void present(SkillExecutionContext context, AreaGeometry shape, String phase, double seconds) { presented.add(shape); }
        public void trace(SkillExecutionContext context, String event, Map<String, ?> details) { events.add(event); }
    }
}
