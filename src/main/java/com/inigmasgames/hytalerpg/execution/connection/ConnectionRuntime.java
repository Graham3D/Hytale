package com.inigmasgames.hytalerpg.execution.connection;

import com.inigmasgames.hytalerpg.execution.OwnedFieldBudget;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;

/** One finite scheduler and hit-ledger owner for line fronts, channel slices and moving pulse origins. */
public final class ConnectionRuntime {
    private final OwnedFieldBudget capacity;
    private final Map<String,Field> fields=new LinkedHashMap<>();
    public ConnectionRuntime(){this(new OwnedFieldBudget());}
    public ConnectionRuntime(OwnedFieldBudget capacity){this.capacity=Objects.requireNonNull(capacity);}
    public synchronized String admission(UUID owner,boolean channel) {
        if(channel&&fields.values().stream().anyMatch(f->f.owner().equals(owner)&&f.profile().channel()))return "CHANNEL_ALREADY_ACTIVE";
        return capacity.admission(owner);
    }
    public synchronized String admission(UUID owner,com.inigmasgames.hytalerpg.execution.Stage04SkillProfile profile,com.inigmasgames.hytalerpg.domain.CompiledSkillPlan plan){
        String verdict=admission(owner,profile.connection().channel());if(!verdict.equals("PASS"))return verdict;
        if(plan.orbit()){
            int active=fields.values().stream().filter(f->f.owner().equals(owner)&&f.context.compiledPlan().orbit()).mapToInt(f->f.profile().details().bladeCount()).sum();
            if(active+profile.connection().details().bladeCount()>OrbitConversionProfiles.CONFIG.maxOrbs())return "CONVERTED_ORBIT_LIVE_CAP";
        }
        return "PASS";
    }
    public synchronized void start(SkillExecutionContext context,double now,ConnectionWorldPort port) {
        startAt(context,now,port,null,null);
    }
    private void startAt(SkillExecutionContext context,double now,ConnectionWorldPort port,Vec3 inheritedOrigin,Vec3 inheritedDirection){
        if(context.profile().connection()==null||!Double.isFinite(now))throw new IllegalArgumentException("Invalid connection start");
        var profile=context.profile().connection();var frame=port.frame();String verdict=port.validate(context,frame.world());
        if(!verdict.equals("PASS"))throw new IllegalStateException(verdict);
        verdict=admission(context.request().actorId(),context.profile(),context.compiledPlan());if(!verdict.equals("PASS"))throw new IllegalStateException(verdict);
        var origin=inheritedOrigin==null?frame.feet().add(new Vec3(0,profile.originHeight(),0)):inheritedOrigin;
        Vec3 direction=inheritedDirection!=null?inheritedDirection:context.target()==null?frame.aim():context.target().point().subtract(origin).normalized();
        if(profile.kind()==ConnectionProfile.Kind.WAVE)direction=direction.horizontalNormalized();
        var field=new Field(context,frame.world(),origin,direction,now);
        if(context.compiledPlan().orbit()&&!context.secondaryKind().equals("aftermath")){
            int count=profile.details().bladeCount();
            for(int i=0;i<count;i++)if(context.derivedRelease()||i>0){
                String claim=context.effects().claim(context.skillInstanceId()+"/orbit-"+i,1,false);
                if(!claim.equals("PASS"))throw new IllegalStateException(claim);
            }
        }
        if(context.secondaryKind().equals("aftermath")){
            String claim=context.effects().claim(context.skillInstanceId(),1,true);if(!claim.equals("PASS"))throw new IllegalStateException(claim);
            if(context.compiledPlan().orbit())for(int i=1;i<profile.details().bladeCount();i++){
                claim=context.effects().claim(context.skillInstanceId()+"/orbit-"+i,2,false);if(!claim.equals("PASS"))throw new IllegalStateException(claim);
            }
            // Leave an expiring Orb's pulse at its final point; this is not another flight/reach grant.
            field.stopped=profile.kind()==ConnectionProfile.Kind.ORB;
        }
        if(profile.requiresTarget()) {
            if(context.target()==null||context.target().entityId()==null)throw new IllegalStateException("COMMITTED_ENTITY_TARGET_MISSING");
            field.targetId=context.target().entityId().toString();
            if(boundTarget(field,origin,port)==null)throw new IllegalStateException("COMMITTED_ENTITY_TARGET_INVALID");
        }
        capacity.reserve(field.owner(),context.skillInstanceId());fields.put(context.skillInstanceId(),field);
        try {port.trace(context,"CONNECTION_STARTED",Map.of("kind",profile.kind(),"origin",origin.toString(),"lifetimeSeconds",profile.lifetimeSeconds()));tickField(field,now,port);}
        catch(RuntimeException error){finish(field,"NATIVE_ADAPTER_FAILURE_"+error.getClass().getSimpleName(),port);throw error;}
    }
    public synchronized void tick(UUID owner,double now,ConnectionWorldPort port) {
        if(!Double.isFinite(now))throw new IllegalArgumentException("Invalid connection clock");
        for(var field:new ArrayList<>(fields.values()))if(field.owner().equals(owner)) {
            try{tickField(field,now,port);}catch(RuntimeException error){finish(field,"NATIVE_ADAPTER_FAILURE_"+error.getClass().getSimpleName(),port);}
        }
    }
    public synchronized List<SkillExecutionContext> cancel(UUID owner,boolean channelsOnly) {
        var removed=new ArrayList<SkillExecutionContext>();
        fields.values().removeIf(field->{
            if(!field.owner().equals(owner)||channelsOnly&&!field.profile().channel())return false;
            field.done=true;capacity.release(owner,field.context.skillInstanceId());removed.add(field.context);return true;
        });return List.copyOf(removed);
    }
    public synchronized int size(){return fields.size();}
    public synchronized boolean channelActive(UUID owner){return fields.values().stream().anyMatch(f->f.owner().equals(owner)&&f.profile().channel());}
    private void tickField(Field field,double now,ConnectionWorldPort port) {
        if(field.done||now<field.lastTick)return;
        String valid=port.validate(field.context,field.world);if(!valid.equals("PASS")){finish(field,valid,port);return;}
        if(now-field.lastTick>1+1e-9){finish(field,"SIMULATION_GAP_EXCEEDS_ONE_SECOND",port);return;}
        field.lastTick=now;var profile=field.profile();double elapsed=Math.min(now-field.started,profile.lifetimeSeconds());
        if(profile.kind()==ConnectionProfile.Kind.WAVE){wave(field,elapsed,now,port);return;}
        if(profile.kind()==ConnectionProfile.Kind.LINE||profile.kind()==ConnectionProfile.Kind.TETHER){instant(field,port);return;}
        if(profile.kind()==ConnectionProfile.Kind.CHAIN){chain(field,elapsed,port);return;}
        if(profile.kind()==ConnectionProfile.Kind.ORBIT){orbit(field,elapsed,port);return;}
        while(!field.done&&(field.tick+1)*profile.intervalSeconds()<=elapsed+1e-9) {
            int tick=field.tick+1;double at=tick*profile.intervalSeconds();ConnectionShape shape;
            ConnectionWorldPort.Target tether=null;
            if(profile.kind()==ConnectionProfile.Kind.DRAIN) {
                var origin=port.frame().feet().add(new Vec3(0,profile.originHeight(),0));tether=boundTarget(field,origin,port);
                if(tether==null){finish(field,"TETHER_TARGET_INVALID",port);return;}
                shape=ConnectionShape.line(origin,tether.bounds().centre(),profile.width(),profile.width());
            } else if(profile.channel()) {
                // Current native aim is explicitly sampled for each authored channel slice.
                var frame=port.frame();Vec3 origin=frame.feet().add(new Vec3(0,profile.originHeight(),0));
                Vec3 end=port.unobstructedEndpoint(origin,origin.add(frame.aim().normalized().multiply(profile.range())));
                shape=ConnectionShape.line(origin,end,profile.width(),profile.height());
            } else {
                moveOrb(field,at,port);shape=ConnectionShape.cylinder(field.position,profile.radius()*field.context.compiledPlan().executionModifiers().radiusFactor(),profile.height());
            }
            var targets=tether==null?targets(field,shape,port):List.of(tether);if(targets==null)return;
            if(profile.channel()&&!port.payUpkeep(field.context,tick,profile.intervalSeconds())){finish(field,"INSUFFICIENT_UPKEEP",port);return;}
            field.tick=tick;
            hit(field,targets,tick,profile.coefficient()*(profile.channel()?profile.intervalSeconds():1),profile.channel(),shape.start().add(shape.end()).multiply(.5),port);
            if(field.done)return;
            port.present(field.context,shape,"IMPACT",profile.channel()?.1:.15);
        }
        if(field.done)return;
        if(elapsed>=profile.lifetimeSeconds()-1e-9){
            // Rapid Pulse suppresses the final partial damage pulse, not the elapsed channel upkeep.
            double tail=profile.lifetimeSeconds()-field.tick*profile.intervalSeconds();
            if(profile.channel()&&field.context.compiledPlan().pulses().rapidPulse()&&tail>1e-9
                    &&!port.payUpkeep(field.context,field.tick+1,tail)){finish(field,"INSUFFICIENT_UPKEEP",port);return;}
            finish(field,"CONNECTION_EXPIRED",port);return;
        }
        if(now>=field.nextVisual) {
            field.nextVisual=now+.05;ConnectionShape shape;
            if(profile.channel()) {
                var frame=port.frame();var start=frame.feet().add(new Vec3(0,profile.originHeight(),0));
                if(profile.kind()==ConnectionProfile.Kind.DRAIN){var target=boundTarget(field,start,port);if(target==null){finish(field,"TETHER_TARGET_INVALID",port);return;}
                    shape=ConnectionShape.line(start,target.bounds().centre(),profile.width(),profile.width());}
                else shape=ConnectionShape.line(start,port.unobstructedEndpoint(start,start.add(frame.aim().normalized().multiply(profile.range()))),profile.width(),profile.height());
            } else {moveOrb(field,elapsed,port);shape=ConnectionShape.cylinder(field.position,profile.radius()*field.context.compiledPlan().executionModifiers().radiusFactor(),profile.height());}
            port.present(field.context,shape,"ACTIVE",.08);
        }
    }
    private ConnectionWorldPort.Target boundTarget(Field field,Vec3 origin,ConnectionWorldPort port){
        var target=port.resolveTarget(field.targetId).orElse(null);
        return target!=null&&ConnectionShape.pointDistanceSquared(origin,target.bounds())<=field.profile().range()*field.profile().range()+1e-9
                &&port.lineOfSight(origin,target)?target:null;
    }
    private void instant(Field field,ConnectionWorldPort port){
        var p=field.profile();ConnectionShape shape;List<ConnectionWorldPort.Target> targets;
        if(p.kind()==ConnectionProfile.Kind.TETHER){var target=boundTarget(field,field.origin,port);if(target==null){finish(field,"TETHER_TARGET_INVALID",port);return;}
            shape=ConnectionShape.line(field.origin,target.bounds().centre(),p.width(),p.height());targets=List.of(target);}
        else {shape=ConnectionShape.line(field.origin,port.unobstructedEndpoint(field.origin,field.origin.add(field.direction.multiply(p.range()))),p.width(),p.height());targets=targets(field,shape,port);}
        if(targets==null)return;hit(field,targets,0,p.coefficient(),false,shape.start().add(shape.end()).multiply(.5),port);
        if(!field.done){port.present(field.context,shape,"IMPACT",p.kind()==ConnectionProfile.Kind.TETHER?.25:.15);finish(field,"LINE_COMPLETE",port);}
    }
    private void chain(Field field,double elapsed,ConnectionWorldPort port){
        var p=field.profile();var coefficients=p.details().jumpCoefficients();
        if(field.lastHit.isEmpty()){
            var target=boundTarget(field,field.origin,port);if(target==null){finish(field,"CHAIN_TARGET_INVALID",port);return;}
            chainHit(field,target,field.origin,0,coefficients.getFirst(),port);
        }
        while(!field.done&&(field.tick+1)*p.intervalSeconds()<=elapsed+1e-9&&field.tick+1<coefficients.size()){
            // Death/despawn after a hit keeps its last actual impact point; a live prior target is refreshed.
            var from=port.resolveTarget(field.targetId).map(t->t.bounds().centre()).orElse(field.position);
            double radius=p.details().jumpRadius()*field.context.compiledPlan().executionModifiers().radiusFactor();
            var found=targets(field,ConnectionShape.cylinder(from,radius,radius*2),port);if(found==null)return;
            var target=found.stream().filter(t->!field.lastHit.containsKey(t.id())&&ConnectionShape.pointDistanceSquared(from,t.bounds())<=radius*radius+1e-9)
                    .sorted(Comparator.comparingDouble((ConnectionWorldPort.Target t)->ConnectionShape.pointDistanceSquared(from,t.bounds())).thenComparing(ConnectionWorldPort.Target::id)).findFirst();
            if(target.isEmpty()){finish(field,"CHAIN_NO_VALID_JUMP",port);return;}
            int tick=field.tick+1;chainHit(field,target.get(),from,tick,coefficients.get(tick),port);
        }
        if(!field.done&&field.tick+1>=coefficients.size())finish(field,"CHAIN_COMPLETE",port);
    }
    private void chainHit(Field field,ConnectionWorldPort.Target target,Vec3 origin,int tick,double coefficient,ConnectionWorldPort port){
        field.tick=tick;field.targetId=target.id();field.position=target.bounds().centre();hit(field,List.of(target),tick,coefficient,false,field.position,port);
        if(!field.done)port.present(field.context,ConnectionShape.line(origin,field.position,field.profile().width(),field.profile().width()),"IMPACT",.15);
    }
    private void orbit(Field field,double elapsed,ConnectionWorldPort port){
        var p=field.profile();double interval=p.intervalSeconds(),radius=p.range()*field.context.compiledPlan().executionModifiers().radiusFactor();
        while(!field.done&&(field.tick+1)*interval<=elapsed+1e-9&&(field.tick+1)*interval<p.lifetimeSeconds()-1e-9){
            int tick=field.tick+1;double at=tick*interval;var center=port.frame().feet().add(new Vec3(0,p.originHeight(),0));
            var oldCenter=field.tick<0?center:field.position;var shapes=new ArrayList<ConnectionShape>();
            for(int blade=0;blade<p.details().bladeCount();blade++){
                double angle=Math.toRadians(p.details().degreesPerSecond()*at+360d*blade/p.details().bladeCount());
                double prior=Math.toRadians(p.details().degreesPerSecond()*Math.max(0,at-interval)+360d*blade/p.details().bladeCount());
                var start=oldCenter.add(new Vec3(Math.cos(prior)*radius,0,Math.sin(prior)*radius));
                var end=center.add(new Vec3(Math.cos(angle)*radius,0,Math.sin(angle)*radius));
                shapes.add(ConnectionShape.capsule(start,end,p.radius()));
            }
            var query=port.query(shapes,64);
            if(query.overflow()||query.targets().size()>64){finish(field,"CANDIDATE_BUDGET_REJECTED",port);return;}
            var unique=new TreeMap<String,ConnectionWorldPort.Target>();
            for(var target:query.targets())if(shapes.stream().anyMatch(s->s.intersects(target.bounds()))&&port.lineOfSight(center,target))unique.putIfAbsent(target.id(),target);
            long additions=unique.keySet().stream().filter(id->!field.lastHit.containsKey(id)).count();
            if(field.lastHit.size()+additions>256){finish(field,"TARGET_LEDGER_BUDGET",port);return;}
            var eligible=unique.values().stream().filter(t->(tick-field.lastHit.getOrDefault(t.id(),-1000000))*interval>=p.details().contactCooldown()-1e-9).toList();
            if(field.context.compiledPlan().orbit())eligible=eligible.stream().filter(t->field.context.effects().claimOrbitContact(t.id(),field.started+at).equals("PASS")).toList();
            field.tick=tick;field.position=center;hit(field,eligible,tick,p.coefficient(),false,center,port);
            if(field.done)return;for(var shape:shapes)port.present(field.context,shape,"BLADE_CONTACT_PROXY",.08);
        }
        if(!field.done&&elapsed>=p.lifetimeSeconds()-1e-9)finish(field,"ORBIT_EXPIRED",port);
    }
    private void wave(Field field,double elapsed,double now,ConnectionWorldPort port) {
        var profile=field.profile();
        Vec3 end=port.unobstructedEndpoint(field.origin,field.origin.add(field.direction.multiply(profile.range())));
        double limit=Math.clamp(ConnectionShape.dot(end.subtract(field.origin),field.direction),0,profile.range());
        double distance=Math.min(limit,profile.speed()*elapsed);
        double from=Math.max(0,field.travelled-profile.depth()/2),to=Math.min(limit,distance+profile.depth()/2);
        if(to<from||limit<=1e-6){finish(field,"WAVE_BLOCKED",port);return;}
        var shape=ConnectionShape.line(field.origin.add(field.direction.multiply(from)),field.origin.add(field.direction.multiply(to)),profile.width(),profile.height());
        var targets=targets(field,shape,port);if(targets==null)return;
        hit(field,targets,0,profile.coefficient(),false,shape.start().add(shape.end()).multiply(.5),port);field.travelled=distance;
        if(field.done)return;
        if(now>=field.nextVisual){field.nextVisual=now+.05;port.present(field.context,shape,"FRONT_SWEEP",.08);}
        if(elapsed>=profile.lifetimeSeconds()-1e-9||distance>=limit-1e-9)finish(field,limit<profile.range()?"WAVE_BLOCKED":"WAVE_COMPLETE",port);
    }
    private void moveOrb(Field field,double elapsed,ConnectionWorldPort port) {
        if(field.stopped)return;var profile=field.profile();double wanted=Math.min(profile.range(),profile.speed()*elapsed);
        double step=Math.max(0,wanted-field.travelled);if(step<=1e-9)return;
        var proposed=field.position.add(field.direction.multiply(step));var actual=port.unobstructedEndpoint(field.position,proposed);
        double moved=Math.max(0,ConnectionShape.dot(actual.subtract(field.position),field.direction));
        if(moved>step+1e-6)throw new IllegalStateException("NATIVE_ENDPOINT_EXCEEDS_SEGMENT");
        field.position=actual;field.travelled+=moved;
        if(actual.distanceSquared(proposed)>1e-10||field.travelled>=profile.range()-1e-9)field.stopped=true;
    }
    private List<ConnectionWorldPort.Target> targets(Field field,ConnectionShape shape,ConnectionWorldPort port) {
        var found=port.query(shape,64);
        if(found.overflow()||found.targets().size()>64){finish(field,"CANDIDATE_BUDGET_REJECTED",port);return null;}
        var unique=new LinkedHashMap<String,ConnectionWorldPort.Target>();
        for(var target:found.targets())if(target.id()!=null&&!target.id().isBlank()&&shape.intersects(target.bounds())&&port.lineOfSight(shape.start(),target))unique.putIfAbsent(target.id(),target);
        long additions=unique.keySet().stream().filter(id->!field.lastHit.containsKey(id)).count();
        if(field.lastHit.size()+additions>256){finish(field,"TARGET_LEDGER_BUDGET",port);return null;}
        return unique.values().stream().sorted(Comparator.comparingDouble((ConnectionWorldPort.Target t)->shape.entryDistance(t.bounds())).thenComparing(ConnectionWorldPort.Target::id)).toList();
    }
    private void hit(Field field,List<ConnectionWorldPort.Target> targets,int tick,double coefficient,boolean periodic,Vec3 center,ConnectionWorldPort port) {
        int attempts=0;double healthLost=0;
        for(var target:targets) {
            if(field.done)break;if(field.lastHit.getOrDefault(target.id(),-1)==tick)continue;
            field.lastHit.put(target.id(),tick);attempts++;
            double lost=port.damage(field.pulseContext,target,tick,coefficient,periodic,center);if(Double.isFinite(lost)&&lost>0)healthLost+=lost;
            if(!field.done&&field.profile().kind()==ConnectionProfile.Kind.DRAIN&&Double.isFinite(lost)&&lost>0)port.healFromDamage(field.pulseContext,tick,lost);
        }
        port.trace(field.context,"CONNECTION_TICK",Map.of("tick",tick,"coefficient",coefficient,"targetAttempts",attempts,"actualHealthLoss",healthLost,"periodic",periodic));
    }
    private void finish(Field field,String reason,ConnectionWorldPort port) {
        if(field.done)return;field.done=true;fields.remove(field.context.skillInstanceId(),field);capacity.release(field.owner(),field.context.skillInstanceId());
        try {port.trace(field.context,"CONNECTION_TERMINATED",Map.of("reason",reason,"ticks",field.tick,"travelled",field.travelled));}
        finally {port.ended(field.context,reason);}
        if(field.context.compiledPlan().zones().aftermath()&&!field.context.derivedRelease()
                &&Set.of("ORBIT_EXPIRED","CONNECTION_EXPIRED").contains(reason)
                &&com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.aftermath(field.context.profile())
                &&field.context.effects().once("AFTERMATH")){
            try{startAt(field.context.aftermathCopy(),field.lastTick,port,field.position,field.direction);}
            catch(RuntimeException failure){try{port.trace(field.context,"CONNECTION_TERMINATED",Map.of("reason","AFTERMATH_CHILD_REJECTED","boundary",String.valueOf(failure.getMessage())));}catch(RuntimeException ignored){}}
        }
    }
    private static final class Field {
        final SkillExecutionContext context,pulseContext;final UUID world;final Vec3 origin,direction;final double started;
        final Map<String,Integer> lastHit=new HashMap<>();Vec3 position;double lastTick,nextVisual,travelled;int tick;boolean stopped,done;String targetId;
        Field(SkillExecutionContext context,UUID world,Vec3 origin,Vec3 direction,double now){
            this.context=context;this.world=world;this.origin=origin;this.direction=direction;this.position=origin;this.started=now;this.lastTick=now;this.nextVisual=now;
            if(context.compiledPlan().pulses().rapidPulse()&&!com.inigmasgames.hytalerpg.execution.ProfileComponentPolicy.periodicPulse(context.profile()))throw new IllegalStateException("PERIODIC_PULSE_COMPONENT_REQUIRED");
            pulseContext=context.compiledPlan().pulses().payload(context);
            if(context.profile().connection().kind()==ConnectionProfile.Kind.ORBIT)tick=-1;
        }
        UUID owner(){return context.request().actorId();}ConnectionProfile profile(){return context.profile().connection();}
    }
}
