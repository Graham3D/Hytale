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
    boolean lineOfSight(Vec3 origin,Target target);
    /** Uses the real resource service; false prevents this tick's hit. */
    boolean payUpkeep(SkillExecutionContext context,int tick,double seconds);
    double damage(SkillExecutionContext context,Target target,int tick,double coefficient,boolean periodic);
    void present(SkillExecutionContext context,ConnectionShape shape,String phase,double seconds);
    void ended(SkillExecutionContext context,String reason);
    void trace(SkillExecutionContext context,String event,Map<String,?> details);
}
