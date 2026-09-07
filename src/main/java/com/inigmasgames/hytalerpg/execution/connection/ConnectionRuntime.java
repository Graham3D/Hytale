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
    public synchronized void start(SkillExecutionContext context,double now,ConnectionWorldPort port) {
        if(context.profile().connection()==null||!Double.isFinite(now))throw new IllegalArgumentException("Invalid connection start");
        var profile=context.profile().connection();var frame=port.frame();String verdict=port.validate(context,frame.world());
        if(!verdict.equals("PASS"))throw new IllegalStateException(verdict);
        verdict=admission(context.request().actorId(),profile.channel());if(!verdict.equals("PASS"))throw new IllegalStateException(verdict);
        var origin=frame.feet().add(new Vec3(0,profile.originHeight(),0));
        Vec3 direction=context.target()==null?frame.aim():context.target().point().subtract(origin).normalized();
        if(profile.kind()==ConnectionProfile.Kind.WAVE)direction=direction.horizontalNormalized();
        var field=new Field(context,frame.world(),origin,direction,now);
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
        while(!field.done&&(field.tick+1)*profile.intervalSeconds()<=elapsed+1e-9) {
            int tick=field.tick+1;double at=tick*profile.intervalSeconds();ConnectionShape shape;
            if(profile.channel()) {
                // Current native aim is explicitly sampled for each authored channel slice.
                var frame=port.frame();Vec3 origin=frame.feet().add(new Vec3(0,profile.originHeight(),0));
                Vec3 end=port.unobstructedEndpoint(origin,origin.add(frame.aim().normalized().multiply(profile.range())));
                shape=ConnectionShape.line(origin,end,profile.width(),profile.height());
            } else {
                moveOrb(field,at,port);shape=ConnectionShape.cylinder(field.position,profile.radius()*field.context.compiledPlan().executionModifiers().radiusFactor(),profile.height());
            }
            var targets=targets(field,shape,port);if(targets==null)return;
            if(profile.channel()&&!port.payUpkeep(field.context,tick,profile.intervalSeconds())){finish(field,"INSUFFICIENT_UPKEEP",port);return;}
            field.tick=tick;
            hit(field,targets,tick,profile.coefficient()*(profile.channel()?profile.intervalSeconds():1),profile.channel(),port);
            if(field.done)return;
            port.present(field.context,shape,"IMPACT",profile.channel()?.1:.15);
        }
        if(field.done)return;
        if(elapsed>=profile.lifetimeSeconds()-1e-9){finish(field,"CONNECTION_EXPIRED",port);return;}
        if(now>=field.nextVisual) {
            field.nextVisual=now+.05;ConnectionShape shape;
            if(profile.channel()) {
                var frame=port.frame();var start=frame.feet().add(new Vec3(0,profile.originHeight(),0));
                shape=ConnectionShape.line(start,port.unobstructedEndpoint(start,start.add(frame.aim().normalized().multiply(profile.range()))),profile.width(),profile.height());
            } else {moveOrb(field,elapsed,port);shape=ConnectionShape.cylinder(field.position,profile.radius()*field.context.compiledPlan().executionModifiers().radiusFactor(),profile.height());}
            port.present(field.context,shape,"ACTIVE",.08);
        }
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
        hit(field,targets,0,profile.coefficient(),false,port);field.travelled=distance;
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
    private void hit(Field field,List<ConnectionWorldPort.Target> targets,int tick,double coefficient,boolean periodic,ConnectionWorldPort port) {
        int attempts=0;double healthLost=0;
        for(var target:targets) {
            if(field.done)break;if(field.lastHit.getOrDefault(target.id(),-1)==tick)continue;
            field.lastHit.put(target.id(),tick);attempts++;
            double lost=port.damage(field.context,target,tick,coefficient,periodic);if(Double.isFinite(lost)&&lost>0)healthLost+=lost;
        }
        port.trace(field.context,"CONNECTION_TICK",Map.of("tick",tick,"coefficient",coefficient,"targetAttempts",attempts,"actualHealthLoss",healthLost,"periodic",periodic));
    }
    private void finish(Field field,String reason,ConnectionWorldPort port) {
        if(field.done)return;field.done=true;fields.remove(field.context.skillInstanceId(),field);capacity.release(field.owner(),field.context.skillInstanceId());
        try {port.trace(field.context,"CONNECTION_TERMINATED",Map.of("reason",reason,"ticks",field.tick,"travelled",field.travelled));}
        finally {port.ended(field.context,reason);}
    }
    private static final class Field {
        final SkillExecutionContext context;final UUID world;final Vec3 origin,direction;final double started;
        final Map<String,Integer> lastHit=new HashMap<>();Vec3 position;double lastTick,nextVisual,travelled;int tick;boolean stopped,done;
        Field(SkillExecutionContext context,UUID world,Vec3 origin,Vec3 direction,double now){
            this.context=context;this.world=world;this.origin=origin;this.direction=direction;this.position=origin;this.started=now;this.lastTick=now;this.nextVisual=now;
        }
        UUID owner(){return context.request().actorId();}ConnectionProfile profile(){return context.profile().connection();}
    }
}
