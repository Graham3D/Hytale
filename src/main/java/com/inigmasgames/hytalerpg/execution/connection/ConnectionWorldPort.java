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
    record TetherVisualSegment(String id,ConnectionShape shape,String recipient) {
        public TetherVisualSegment(String id,ConnectionShape shape){this(id,shape,null);}
        public TetherVisualSegment {
            if(id==null||id.isBlank())throw new IllegalArgumentException("Tether visual id required");
            Objects.requireNonNull(shape);
        }
    }
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
    /** Friendly access must enforce alive/loaded/alliance/self policy, not the hostile query. */
    default Query queryFriendly(ConnectionShape shape,int cap){return new Query(List.of(),false);}
    default Optional<Target> resolveFriendly(String id){return Optional.empty();}
    default boolean injured(Target target){return false;}
    default boolean held(SkillExecutionContext context){return false;}
    default double heal(SkillExecutionContext context,Target target,int tick,double coefficient){throw new IllegalStateException("HEAL_TETHER_ADAPTER_UNAVAILABLE");}
    boolean lineOfSight(Vec3 origin,Target target);
    /** Uses the real resource service; false prevents this tick's hit. */
    boolean payUpkeep(SkillExecutionContext context,int tick,double seconds);
    double damage(SkillExecutionContext context,Target target,int tick,double coefficient,boolean periodic);
    default double damage(SkillExecutionContext context,Target target,int tick,double coefficient,boolean periodic,Vec3 effectCenter){return damage(context,target,tick,coefficient,periodic);}
    default void healFromDamage(SkillExecutionContext context,int tick,double actualHealthLost){throw new IllegalStateException("DRAIN_HEAL_ADAPTER_UNAVAILABLE");}
    void present(SkillExecutionContext context,ConnectionShape shape,String phase,double seconds);
    /** One coherent visual frame lets a persistent renderer update live segments and remove stale branches. */
    default void presentTether(SkillExecutionContext context,List<TetherVisualSegment> segments){
        for(var segment:segments)present(context,segment.shape(),segment.id().startsWith("PRIMARY:")?"ACTIVE":"TETHER_SECONDARY",.1);
    }
    void ended(SkillExecutionContext context,String reason);
    void trace(SkillExecutionContext context,String event,Map<String,?> details);
}
