package com.inigmasgames.hytalerpg.execution.area;

import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** World-thread finite-effect ownership and per-cast ledgers shared by spatial families. */
public final class AreaRuntime {
    public static final int OWNER_CAP = 8, GLOBAL_CAP = 128;
    private final com.inigmasgames.hytalerpg.execution.OwnedFieldBudget capacity;
    public AreaRuntime(){this(new com.inigmasgames.hytalerpg.execution.OwnedFieldBudget());}
    public AreaRuntime(com.inigmasgames.hytalerpg.execution.OwnedFieldBudget capacity){this.capacity=java.util.Objects.requireNonNull(capacity);}
    private final Map<String, Field> fields = new LinkedHashMap<>();
    private final Map<String,Map<String,Ledger>> rootLedgers=new HashMap<>();
    private final Map<String,Integer> rootSpawned=new HashMap<>();

    public synchronized String admission(UUID owner, String skill, boolean trap) {
        if(skill.equals("blizzard")&&fields.values().stream().anyMatch(f->!f.done&&f.context.request().actorId().equals(owner)&&f.context.profile().skillId().equals(skill)&&f.context.secondaryKind().isEmpty()))
            return "BLIZZARD_ALREADY_ACTIVE";
        String capacityVerdict=capacity.admission(owner);if(!capacityVerdict.equals("PASS"))return capacityVerdict;
        if (trap && fields.values().stream().anyMatch(f -> f.context.request().actorId().equals(owner)
                && f.context.profile().skillId().equals(skill))) return "TRAP_ALREADY_DEPLOYED";
        return "PASS";
    }

    /** Static placement stays committed; a Mobile Domain attaches to its live owner only at release. */
    public synchronized void start(SkillExecutionContext context, Vec3 point, Vec3 direction, double now,
                      double radiusFactor, AreaWorldPort port) {
        AreaSkillProfile profile = context.profile().area();
        if (profile == null || !Double.isFinite(now) || !Double.isFinite(radiusFactor) || radiusFactor <= 0)
            throw new IllegalArgumentException("Invalid area start");
        boolean mobile=context.compiledPlan().zones().mobileDomain();
        boolean cascade=context.compiledPlan().zones().cascade();
        boolean controlled=cascade||context.compiledPlan().zones().aftermath();
        if(cascade&&!com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.cascade(context.profile()))throw new IllegalStateException("CASCADE_AREA_COMPONENT_REQUIRED");
        if(context.compiledPlan().pulses().rapidPulse()&&!com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.periodicPulse(context.profile()))
            throw new IllegalStateException("PERIODIC_PULSE_COMPONENT_REQUIRED");
        if(mobile&&!com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.mobileZone(context.profile()))
            throw new IllegalStateException("MOBILE_FINITE_ZONE_COMPONENT_REQUIRED");
        Vec3 anchor=mobile?mobileOrigin(context,port).orElseThrow(()->new IllegalStateException("MOBILE_OWNER_ANCHOR_UNAVAILABLE")):point;
        Vec3 origin=mobile&&context.secondaryKind().isEmpty()?anchor:point;
        String admission = context.derivedRelease()&&context.profile().skillId().equals("blizzard")
                ?capacity.admission(context.request().actorId()):admission(context.request().actorId(), context.profile().skillId(), profile.trap());
        if (!admission.equals("PASS")) throw new IllegalStateException(admission);
        if (fields.containsKey(context.skillInstanceId())) throw new IllegalStateException("DUPLICATE_FIELD_INSTANCE");
        int spawnCost=1+(profile.stratified()&&!controlled?profile.impactCount():0);
        int spent=rootSpawned.getOrDefault(rootKey(context),0);
        if(spent+spawnCost>context.compiledPlan().safetyBudgets().maxSpawnedEffects()) throw new IllegalStateException("ROOT_SPAWN_EFFECT_BUDGET");
        Field field = new Field(context, profile.footprint(origin, direction, radiusFactor), now, radiusFactor);
        if(mobile)field.mobileOffset=origin.subtract(anchor);
        if(controlled&&context.derivedRelease()){
            String claim=context.effects().claim(context.skillInstanceId(),1,!context.secondaryKind().isEmpty());
            if(!claim.equals("PASS"))throw new IllegalStateException(claim);
        }
        capacity.reserve(context.request().actorId(),context.skillInstanceId());
        rootSpawned.put(rootKey(context),spent+spawnCost);
        fields.put(context.skillInstanceId(), field);
        try {
            port.trace(context, "AREA_STARTED", Map.of("origin", origin.toString(), "radius", field.geometry.radius(),
                    "height", field.geometry.height(), "lifetimeSeconds", profile.lifetimeSeconds(),"mobileDomain",mobile));
            tickField(field, now, port);
            if(cascade&&!context.derivedRelease()&&context.effects().once("CASCADE"))startCascade(field,now,port);
        }
        catch (RuntimeException error) { finish(field, "NATIVE_ADAPTER_FAILURE_" + error.getClass().getSimpleName(), port); throw error; }
        finally { if (field.done) removeField(field); }
    }

    private void startCascade(Field parent,double now,AreaWorldPort port){
        var context=parent.context;var direction=parent.geometry.direction();var right=new Vec3(direction.z(),0,-direction.x()).horizontalNormalized();
        double offset=1.2*com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.baseAreaRadius(context.profile().skillId());
        for(int ordinal=1;ordinal<=2;ordinal++){
            var child=context.cascadeCopy(ordinal);var point=parent.geometry.origin().add(right.multiply(ordinal==1?-offset:offset));
            try{
                var wanted=context.profile().area().footprint(point,direction,parent.radiusFactor*.6);
                var placement=port.prepareImpact(parent.geometry.origin(),wanted);
                if(placement.isEmpty()){port.trace(child,"AREA_QUERY_REJECTED",Map.of("reason","CASCADE_NO_LEGAL_TERRAIN","ordinal",ordinal));continue;}
                start(child,placement.get().origin(),direction,now,parent.radiusFactor*.6,port);
            }catch(RuntimeException failure){
                try{port.trace(child,"AREA_QUERY_REJECTED",Map.of("reason","CASCADE_CHILD_REJECTED","boundary",String.valueOf(failure.getMessage()),"paidRootRetained",true));}catch(RuntimeException ignored){}
            }
        }
    }

    public synchronized void tick(UUID owner, double now, AreaWorldPort port) {
        if (!Double.isFinite(now)) throw new IllegalArgumentException("Invalid clock");
        for (Field field : new ArrayList<>(fields.values())) {
            if (!field.context.request().actorId().equals(owner)) continue;
            try { tickField(field, now, port); }
            catch (RuntimeException error) { finish(field, "NATIVE_ADAPTER_FAILURE_" + error.getClass().getSimpleName(), port); }
            finally { if (field.done) removeField(field); }
        }
    }

    /** No retained native references; owner teardown is idempotent and drops every ledger. */
    public synchronized List<SkillExecutionContext> cancel(UUID owner) {
        List<SkillExecutionContext> removed = new ArrayList<>();
        fields.values().removeIf(f -> {
            if (!f.context.request().actorId().equals(owner)) return false;
            f.done=true;removed.add(f.context);capacity.release(owner,f.context.skillInstanceId());return true;
        });
        removed.forEach(c->cleanupRoot(rootKey(c)));
        return List.copyOf(removed);
    }
    public synchronized int size() { return fields.size(); }
    public synchronized double activeRemaining(UUID owner,String skill,double now){
        return fields.values().stream().filter(f->!f.done&&f.context.request().actorId().equals(owner)&&f.context.profile().skillId().equals(skill)&&f.context.secondaryKind().isEmpty())
                .mapToDouble(f->Math.max(0,f.started+f.context.profile().area().lifetimeSeconds()-now)).max().orElse(0);
    }

    private void tickField(Field field, double now, AreaWorldPort port) {
        if (field.done || now < field.lastTick) return;
        if(field.context.compiledPlan().zones().mobileDomain()){
            var origin=mobileOrigin(field.context,port);
            if(origin.isEmpty()){finish(field,"MOBILE_OWNER_ANCHOR_UNAVAILABLE",port);return;}
            // One current footprint, never a swept damage trail or a restarted duration/ledger.
            field.geometry=field.geometry.at(origin.get().add(field.mobileOffset),field.geometry.radius());
        }
        double gap = now - field.lastTick;
        field.lastTick = now;
        AreaSkillProfile profile = field.context.profile().area();
        double elapsed = now - field.started;
        if (profile.trap()) {
            // Expiry takes priority: no detonation when a lagged tick arrives after lifetime.
            if (elapsed >= profile.lifetimeSeconds()) { finish(field, "TRAP_EXPIRED", port); return; }
            if (now < field.nextScan) return;
            field.nextScan = now + .25;
            port.present(field.context, field.geometry, elapsed < profile.armingSeconds() ? "ARMING" : "ARMED", .3);
            if (elapsed < profile.armingSeconds()) return;
            List<AreaWorldPort.Target> targets = targets(field, field.geometry, port);
            if (targets == null || targets.isEmpty()) return;
            AreaGeometry blast = profile.impactRadius() > 0
                    ? field.geometry.at(field.geometry.origin(), profile.impactRadius() * field.radiusFactor) : field.geometry;
            List<AreaWorldPort.Target> selected = profile.firstTargetOnly() ? targets : targets(field, blast, port);
            if (selected == null) return;
            int applied = hit(field, blast, selected, 0, now, port);
            if (applied > 0) { port.present(field.context, blast, "IMPACT", .3); finish(field, "TRAP_TRIGGERED", port); }
            return;
        }
        if ((profile.periodic() || profile.impactCount() > 1) && gap > 1 + 1e-9) {
            // Current entity positions cannot reconstruct an arbitrarily long missed simulation interval.
            finish(field, "SIMULATION_GAP_EXCEEDS_ONE_SECOND", port); return;
        }
        if(profile.stratified()&&profile.overheadHeight()>0){tickFallingShards(field,now,port);return;}
        if (profile.periodic()) { tickPeriodic(field, now, port); return; }
        if (profile.impactCount() > 1||field.context.secondaryKind().equals("aftermath")&&profile.impactCount()==1) { tickImpacts(field, now, port); return; }
        if (profile.overheadHeight() > 0) {
            if (!port.overheadClear(field.geometry, profile.overheadHeight())) { finish(field, "OVERHEAD_ROOF_BLOCKED", port); return; }
            if (elapsed >= profile.warningSeconds() - profile.descentSeconds() && elapsed < profile.warningSeconds()
                    && now >= field.nextDescent) {
                field.nextDescent = now + .05;
                double altitude = profile.overheadHeight() * Math.clamp((profile.warningSeconds() - elapsed) / profile.descentSeconds(), 0, 1);
                port.descendingVisual(field.context, field.geometry.origin().add(new Vec3(0, altitude, 0)), .08);
            }
        }
        if (elapsed < profile.warningSeconds()) {
            if (now >= field.nextScan) {
                field.nextScan = now + .25;
                presentField(field, "WARNING", Math.min(.3, profile.warningSeconds() - elapsed), port);
            }
            return;
        }
        if (profile.overheadHeight() > 0 && !sameSurface(field.geometry, field.geometry.origin(), port)) {
            finish(field, "WARNED_SURFACE_CHANGED", port); return;
        }
        List<AreaWorldPort.Target> targets = targets(field, field.geometry, port);
        if (targets == null) { finish(field, "CANDIDATE_BUDGET_REJECTED", port); return; }
        hit(field, field.geometry, targets, 0, now, port);
        presentField(field, "IMPACT", .3, port);
        finish(field, "AREA_COMPLETE", port);
    }

    private void tickPeriodic(Field field, double now, AreaWorldPort port) {
        AreaSkillProfile profile = field.context.profile().area();
        double elapsed = now - field.started;
        if (now >= field.nextScan && elapsed < profile.lifetimeSeconds()) {
            field.nextScan = now + .25; presentField(field, "ACTIVE", .3, port);
        }
        double end = Math.min(elapsed, profile.lifetimeSeconds());
        int index = field.nextImpact;
        while (field.integrated < end - 1e-9) {
            if((field.context.compiledPlan().pulses().rapidPulse()||field.context.secondaryKind().equals("aftermath"))&&field.integrated+profile.intervalSeconds()>profile.lifetimeSeconds()+1e-9)break;
            double next = Math.min(field.integrated + profile.intervalSeconds(), profile.lifetimeSeconds());
            if (elapsed < next - 1e-9) break;
            List<AreaWorldPort.Target> targets = targets(field, field.geometry, port);
            if (targets == null) { finish(field, "CANDIDATE_BUDGET_REJECTED", port); return; }
            double seconds = next - field.integrated;
            hit(field, field.geometry, targets, index++, field.started + next, port, seconds, true);
            field.integrated = next;
        }
        field.nextImpact = index;
        if (elapsed >= profile.lifetimeSeconds()-1e-9) finish(field, "AREA_EXPIRED", port);
    }

    private void tickImpacts(Field field, double now, AreaWorldPort port) {
        AreaSkillProfile profile = field.context.profile().area();
        // Instant COMMIT starts warning preparation. Authored impact offsets are relative to the active epoch.
        // This supplies the first offset-zero Blizzard impact its mandatory warning without free pre-commit damage.
        double epoch = field.started + (profile.stratified() ? Math.max(0, profile.warningSeconds() - profile.firstImpactSeconds()) : 0);
        if (now >= field.nextScan && now < epoch + profile.lifetimeSeconds()) {
            field.nextScan = now + .25; presentField(field, "ACTIVE", .3, port);
        }
        for (int i = 0; i < profile.impactCount(); i++) {
            if (field.impacted[i]) continue;
            double impactAt = epoch + profile.firstImpactSeconds() + i * profile.intervalSeconds();
            if (now < impactAt - profile.warningSeconds() - 1e-9) continue;
            AreaGeometry footprint = profile.stratified()
                    ? field.geometry.at(field.geometry.origin().add(field.offsets.get(i)), profile.impactRadius() * field.radiusFactor)
                    : field.geometry;
            if (profile.stratified()) {
                if (!field.prepared[i]) {
                    field.prepared[i] = true;
                    if(field.context.compiledPlan().zones().cascade()||field.context.compiledPlan().zones().aftermath()){
                        String budget=field.context.effects().claim(field.context.skillInstanceId()+"/area-impact-"+i,field.context.derivedRelease()?2:1,false);
                        if(!budget.equals("PASS")){
                            field.impacted[i]=true;field.nextImpact++;port.trace(field.context,"AREA_QUERY_REJECTED",Map.of("reason",budget,"impactIndex",i));continue;
                        }
                    }
                    field.impactGeometry[i] = port.prepareImpact(field.geometry.origin(), footprint).orElse(null);
                }
                footprint = field.impactGeometry[i];
                if (footprint == null) {
                    field.impacted[i] = true; field.nextImpact++;
                    port.trace(field.context, "AREA_QUERY_REJECTED", Map.of("reason", "NO_LEGAL_IMPACT_SURFACE", "impactIndex", i));
                    continue;
                }
            }
            if (profile.stratified() && Double.isNaN(field.warnedAt[i]) && now >= impactAt - profile.warningSeconds() - 1e-9) {
                field.warnedAt[i] = now; port.present(field.context, footprint, "WARNING", profile.warningSeconds());
            }
            if (now < impactAt - 1e-9 || profile.stratified()
                    && (Double.isNaN(field.warnedAt[i]) || now - field.warnedAt[i] < profile.warningSeconds() - 1e-9)) continue;
            if (profile.stratified()) {
                if (!sameSurface(footprint, field.geometry.origin(), port)) {
                    field.impacted[i] = true; field.nextImpact++;
                    port.trace(field.context, "AREA_QUERY_REJECTED", Map.of("reason", "WARNED_SURFACE_CHANGED", "impactIndex", i));
                    continue;
                }
            }
            List<AreaWorldPort.Target> targets = targets(field, footprint, port);
            if (targets == null) { finish(field, "CANDIDATE_BUDGET_REJECTED", port); return; }
            hit(field, footprint, targets, i, now, port);
            port.present(field.context, footprint, "IMPACT", .3); field.impacted[i] = true; field.nextImpact++;
        }
        if (now >= epoch + profile.lifetimeSeconds()) {
            if (profile.finalCoefficient() > 0 && !field.finalHit) {
                field.finalHit = true;
                var selected = targets(field, field.geometry, port);
                if (selected == null) { finish(field, "FINAL_CANDIDATE_BUDGET_REJECTED", port); return; }
                hit(field, field.geometry, selected, profile.impactCount(), now, port, 1, false, true);
                presentField(field, "IMPACT", .3, port);
            }
            finish(field, "AREA_COMPLETE", port);
        }
    }

    /** One root ledger; moving transforms are execution children, not extra SkillInstances. */
    private void tickFallingShards(Field f,double now,AreaWorldPort port){
        var p=f.context.profile().area();double elapsed=now-f.started;
        if(elapsed>=p.lifetimeSeconds()-1e-9){finish(f,"AREA_COMPLETE",port);return;}
        try{port.stormVisual(f.context,f.geometry,p.lifetimeSeconds()-elapsed);}catch(RuntimeException ignored){}
        for(int i=0;i<p.impactCount();i++){
            if(f.impacted[i])continue;
            double spawnAt=f.started+Math.max(0,p.firstImpactSeconds()-p.descentSeconds())+i*p.intervalSeconds();
            if(now<spawnAt-1e-9)continue;
            if(!f.prepared[i]){
                f.prepared[i]=true;
                if(now+p.descentSeconds()>f.started+p.lifetimeSeconds()+1e-9){f.impacted[i]=true;continue;}
                if(f.context.compiledPlan().zones().cascade()||f.context.compiledPlan().zones().aftermath()){
                    var budget=f.context.effects().claim(f.context.skillInstanceId()+"/area-impact-"+i,f.context.derivedRelease()?2:1,false);
                    if(!budget.equals("PASS")){f.impacted[i]=true;port.trace(f.context,"AREA_QUERY_REJECTED",Map.of("reason",budget,"impactIndex",i));continue;}
                }
                var wanted=f.geometry.at(f.geometry.origin().add(f.offsets.get(i)),p.impactRadius()*f.radiusFactor);
                f.impactGeometry[i]=port.prepareImpact(f.geometry.origin(),wanted).orElse(null);
                if(f.impactGeometry[i]==null){f.impacted[i]=true;continue;}
                f.warnedAt[i]=now;
                f.shardPositions[i]=f.impactGeometry[i].origin().add(new Vec3(0,p.overheadHeight(),0));
                try{port.present(f.context,f.impactGeometry[i],"WARNING",p.warningSeconds());}catch(RuntimeException ignored){}
            }
            var start=f.impactGeometry[i].origin().add(new Vec3(0,p.overheadHeight(),0));
            var next=start.add(new Vec3(0,-p.overheadHeight()*Math.min(1,(now-f.warnedAt[i])/p.descentSeconds()),0));
            var contact=port.sweepShard(f.shardPositions[i],next,.1);
            if(contact.isPresent()){
                f.impacted[i]=true;
                var actual=f.impactGeometry[i].at(contact.get(),f.impactGeometry[i].radius());
                try{port.shardVisual(f.context,i,contact.get(),true);}catch(RuntimeException ignored){}
                var selected=targets(f,actual,port);
                if(selected==null){finish(f,"CANDIDATE_BUDGET_REJECTED",port);return;}
                hit(f,actual,selected,i,now,port);
                try{port.present(f.context,actual,"IMPACT",.3);}catch(RuntimeException ignored){}
            }else if(now-f.warnedAt[i]>=p.descentSeconds()-1e-9){
                f.impacted[i]=true;
                try{port.shardVisual(f.context,i,next,true);}catch(RuntimeException ignored){}
            }else{
                f.shardPositions[i]=next;
                try{port.shardVisual(f.context,i,next,false);}catch(RuntimeException ignored){}
            }
        }
    }

    private boolean sameSurface(AreaGeometry footprint, Vec3 parent, AreaWorldPort port) {
        var fresh = port.prepareImpact(parent, footprint);
        return fresh.isPresent() && fresh.get().origin().distanceSquared(footprint.origin()) <= .0001;
    }
    private void presentField(Field field, String phase, double seconds, AreaWorldPort port) {
        port.present(field.context, field.geometry, phase, seconds);
        var profile=field.context.profile().area();
        double core=profile.visualCoreRadius() > 0 ? profile.visualCoreRadius() : profile.innerRadius();
        if(core > 0) port.present(field.context,field.geometry.at(field.geometry.origin(),core*field.radiusFactor),phase+"_CORE",seconds);
    }

    private List<AreaWorldPort.Target> targets(Field field, AreaGeometry geometry, AreaWorldPort port) {
        int budget = field.context.profile().area().candidateBudget();
        AreaWorldPort.Query query = port.query(geometry, budget);
        if (query.overflow() || query.targets().size() > budget) {
            port.trace(field.context, "AREA_QUERY_REJECTED", Map.of("reason", "CANDIDATE_BUDGET", "budget", budget));
            return null;
        }
        return query.targets().stream().filter(t -> geometry.intersects(t.bounds()))
                .filter(t -> port.lineOfSight(geometry.origin(), t))
                .sorted(Comparator.comparingDouble((AreaWorldPort.Target t) -> geometry.horizontalDistance(t.bounds()))
                        .thenComparing(AreaWorldPort.Target::id)).toList();
    }

    private int hit(Field field, AreaGeometry footprint, List<AreaWorldPort.Target> targets,
                    int impactIndex, double now, AreaWorldPort port) {
        return hit(field, footprint, targets, impactIndex, now, port, 1, false);
    }
    private int hit(Field field, AreaGeometry footprint, List<AreaWorldPort.Target> targets,
                    int impactIndex, double now, AreaWorldPort port, double seconds, boolean periodic) {
        return hit(field, footprint, targets, impactIndex, now, port, seconds, periodic, false);
    }
    private int hit(Field field, AreaGeometry footprint, List<AreaWorldPort.Target> targets,
                    int impactIndex, double now, AreaWorldPort port, double seconds, boolean periodic, boolean finalBlast) {
        AreaSkillProfile profile = field.context.profile().area();
        int applied = 0;
        Map<String,Ledger> ledger=profile.stratified()&&!finalBlast
                ?rootLedgers.computeIfAbsent(rootKey(field.context),ignored->new HashMap<>()) :field.ledger;
        String impactKey=field.context.skillInstanceId()+"/"+impactIndex;
        for (AreaWorldPort.Target target : targets) {
            Ledger previous = ledger.get(target.id());
            if (!finalBlast && previous != null && (previous.hits >= profile.perTargetHitCap()
                    || now - previous.lastHit < profile.targetIntervalSeconds() - 1e-9
                    || previous.lastImpact.equals(impactKey))) continue;
            double distance = footprint.horizontalDistance(target.bounds());
            boolean inner = profile.innerRadius() > 0 && distance <= profile.innerRadius() * field.radiusFactor;
            double coefficient = inner && profile.innerCoefficient() > 0 ? profile.innerCoefficient() : profile.coefficient();
            if (finalBlast) coefficient = profile.finalCoefficient();
            coefficient *= 1 - profile.edgeFalloff() * Math.clamp(distance / Math.max(.001, footprint.radius()), 0, 1);
            double duration = profile.statusInnerRadius() > 0 && distance <= profile.statusInnerRadius() * field.radiusFactor
                    ? profile.statusInnerSeconds() : profile.statusSeconds();
            int chill = inner && profile.innerChillStacks() > 0 ? profile.innerChillStacks() : profile.chillStacks();
            String status = finalBlast ? "" : profile.status();
            String element = profile.element();
            if (profile.alternatingIceStone()) {
                boolean ice = impactIndex % 2 == 0;
                status = ice ? "CHILL" : "STAGGER"; element = ice ? "COLD" : "EARTH"; chill = ice ? 1 : 0;
            }
            boolean statusReady = now - field.statusLastHit.getOrDefault(target.id(), Double.NEGATIVE_INFINITY)
                    >= profile.statusIntervalSeconds() - 1e-9;
            String rootStatusKey=target.id()+"/"+status;
            if(field.context.compiledPlan().zones().cascade()||field.context.compiledPlan().zones().aftermath())statusReady&=field.context.effects().statusReady(rootStatusKey,now,Math.max(profile.statusIntervalSeconds(),profile.targetIntervalSeconds()));
            if(statusReady&&status.equals("CHILL")&&field.context.compiledPlan().pulses().rapidPulse()){
                chill=field.chill.grant(target.id(),impactIndex,chill);
                if(chill==0)status="";
            }
            AreaWorldPort.Payload payload = new AreaWorldPort.Payload(impactIndex, coefficient * seconds,
                    statusReady ? status : "", duration, chill, profile.displacement() * seconds, periodic, element, footprint.origin(),
                    finalBlast ? profile.finalPull() : profile.pullSpeed() * seconds,
                    finalBlast ? 0 : profile.pullCoreRadius() * field.radiusFactor, finalBlast);
            if (!port.apply(finalBlast?field.context:field.pulseContext, target, payload)) continue;
            if (statusReady && !status.isBlank()) {field.statusLastHit.put(target.id(), now);
                if(field.context.compiledPlan().zones().cascade()||field.context.compiledPlan().zones().aftermath())field.context.effects().statusApplied(rootStatusKey,now);
            }
            var accepted=new Ledger(previous == null ? 1 : previous.hits + 1, now, impactKey);
            ledger.put(target.id(),accepted);field.ledger.put(target.id(),accepted);
            applied++;
            port.trace(field.context, "AREA_HIT", Map.of("target", target.id(), "impactIndex", impactIndex,
                    "coefficient", coefficient, "distance", distance));
            if (profile.firstTargetOnly()) break;
        }
        return applied;
    }
    private void finish(Field field, String reason, AreaWorldPort port) {
        if(field.done)return;
        field.done = true;
        try{port.endVisuals(field.context);}catch(RuntimeException ignored){}
        // A failed diagnostic sink must not prevent cleanup or the next owner's field tick.
        try { port.trace(field.context, "AREA_TERMINATED", Map.of("reason", reason, "hitTargets", field.ledger.size())); }
        catch (RuntimeException ignored) { }
        if(field.context.compiledPlan().zones().aftermath()&&!field.context.derivedRelease()
                &&java.util.Set.of("AREA_EXPIRED","AREA_COMPLETE","TRAP_EXPIRED").contains(reason)
                &&field.context.effects().once("AFTERMATH")){
            removeField(field); // Release the expired field's lease before admitting its replacement.
            try{
                var child=field.context.aftermathCopy();
                var shape=child.profile().area().footprint(field.geometry.origin(),field.geometry.direction(),field.radiusFactor);
                var placed=port.prepareImpact(field.geometry.origin(),shape);
                if(placed.isEmpty())throw new IllegalStateException("AFTERMATH_NO_LEGAL_TERRAIN");
                start(child,placed.get().origin(),field.geometry.direction(),field.lastTick,field.radiusFactor,port);
            }catch(RuntimeException failure){try{port.trace(field.context,"AREA_QUERY_REJECTED",Map.of("reason","AFTERMATH_CHILD_REJECTED","boundary",String.valueOf(failure.getMessage())));}catch(RuntimeException ignored){}}
        }
    }
    private void removeField(Field field) {
        fields.remove(field.context.skillInstanceId());capacity.release(field.context.request().actorId(),field.context.skillInstanceId());cleanupRoot(rootKey(field.context));
    }
    private void cleanupRoot(String root) {
        if(fields.values().stream().noneMatch(f->rootKey(f.context).equals(root))) {
            rootLedgers.remove(root);rootSpawned.remove(root);
        }
    }
    public synchronized int retainedRootCount() { return rootSpawned.size(); }
    private static java.util.Optional<Vec3> mobileOrigin(SkillExecutionContext context,AreaWorldPort port){
        if(context.target()==null)return java.util.Optional.empty();
        return port.ownerAnchor(context).filter(a->a.actorId().equals(context.request().actorId())
                &&a.worldId().equals(context.target().worldId())).map(AreaWorldPort.OwnerAnchor::position);
    }
    private static String rootKey(SkillExecutionContext context) { return context.request().actorId()+"/"+context.rootCastId(); }
    private record Ledger(int hits, double lastHit, String lastImpact) { }
    private static final class Field {
        final SkillExecutionContext context,pulseContext; AreaGeometry geometry; final double started, radiusFactor;
        Vec3 mobileOffset=Vec3.ZERO;
        final com.inigmasgames.hytalerpg.execution.ChillPulseLedger chill=new com.inigmasgames.hytalerpg.execution.ChillPulseLedger();
        final Map<String, Ledger> ledger = new HashMap<>();
        final Map<String, Double> statusLastHit = new HashMap<>();
        final List<Vec3> offsets; final double[] warnedAt;
        final boolean[] prepared, impacted; final AreaGeometry[] impactGeometry;
        final Vec3[] shardPositions;
        double nextScan, lastTick, integrated, nextDescent; int nextImpact; boolean done, finalHit;
        Field(SkillExecutionContext context, AreaGeometry geometry, double started, double radiusFactor) {
            this.context = context; this.geometry = geometry; this.started = started; this.radiusFactor = radiusFactor;
            pulseContext=context.compiledPlan().pulses().payload(context);
            nextScan = started; lastTick = started;
            var profile = context.profile().area();
            offsets = profile.stratified() ? StratifiedAreaPattern.offsets(context.rootCastId() + "/" + context.profile().skillId(),
                    profile.impactCount(), geometry.radius(), profile.impactRadius() * radiusFactor) : List.of();
            warnedAt = new double[profile.impactCount()]; java.util.Arrays.fill(warnedAt, Double.NaN);
            prepared = new boolean[profile.impactCount()]; impacted = new boolean[profile.impactCount()];
            impactGeometry = new AreaGeometry[profile.impactCount()];
            shardPositions = new Vec3[profile.impactCount()];
        }
    }
}
