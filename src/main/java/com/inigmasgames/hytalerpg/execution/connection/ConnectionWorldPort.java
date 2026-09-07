package com.inigmasgames.hytalerpg.execution.connection;

import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;

/** Fresh native access per owning world tick. Runtime retains UUIDs/immutable geometry, not ECS references. */
public interface ConnectionWorldPort {
    record Frame(UUID world,Vec3 feet,Vec3 aim) { }
    record Target(String id,AreaGeometry.Bounds bounds) { }
    record Query(List<Target> targets,boolean overflow){public Query{targets=List.copyOf(targets);}}
    Frame frame();
    String validate(SkillExecutionContext context,UUID world);
    Vec3 unobstructedEndpoint(Vec3 origin,Vec3 destination);
    Query query(ConnectionShape shape,int cap);
    /** A compound contact query shares one candidate budget across all blade proxies. */
    default Query query(java.util.List<ConnectionShape> shapes,int cap) {
        var targets=new LinkedHashMap<String,Target>();
        for(var shape:shapes){var found=query(shape,cap);if(found.overflow())return new Query(List.of(),true);
            for(var target:found.targets())if(shape.intersects(target.bounds()))targets.putIfAbsent(target.id(),target);
            if(targets.size()>cap)return new Query(List.of(),true);}
        return new Query(List.copyOf(targets.values()),false);
    }
    default Optional<Target> resolveTarget(String id){return Optional.empty();}
    boolean lineOfSight(Vec3 origin,Target target);
    /** Uses the real resource service; false prevents this tick's hit. */
    boolean payUpkeep(SkillExecutionContext context,int tick,double seconds);
    double damage(SkillExecutionContext context,Target target,int tick,double coefficient,boolean periodic);
    default void healFromDamage(SkillExecutionContext context,int tick,double actualHealthLost){throw new IllegalStateException("DRAIN_HEAL_ADAPTER_UNAVAILABLE");}
    void present(SkillExecutionContext context,ConnectionShape shape,String phase,double seconds);
    void ended(SkillExecutionContext context,String reason);
    void trace(SkillExecutionContext context,String event,Map<String,?> details);
}
