package com.inigmasgames.hytalerpg.execution.support;

import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SupportWorldPort {
    NativeResourcePort resources();
    String valid(SkillExecutionContext context);
    /** Owner plus positively established allies, alive/loaded/in range/LOS; >64 rejects the entire query. */
    List<UUID> allies(SkillExecutionContext context,double radius);
    default List<UUID> enemies(SkillExecutionContext context,double radius){throw new IllegalStateException("AURA_HOSTILE_QUERY_UNAVAILABLE");}
    default boolean upkeep(SkillExecutionContext context,double seconds,int quantum){throw new IllegalStateException("AURA_UPKEEP_UNAVAILABLE");}
    default void auraPulse(SkillExecutionContext context,List<UUID> targets,int tick,boolean chill){throw new IllegalStateException("AURA_PAYLOAD_UNAVAILABLE");}
    default void auraMembership(SkillExecutionContext context,List<UUID> allies,List<UUID> enemies){}
    default void auraEnded(SkillExecutionContext context){}
    double heal(SkillExecutionContext context,UUID target,double requested);
    default void finiteEffect(SkillExecutionContext context,FiniteSupportEffects effects,double now){throw new IllegalStateException("FINITE_SUPPORT_UNAVAILABLE");}
    default double masteryMultiplier(SkillExecutionContext context){return 1;}
    /** Must mean authenticated native root/contact attribution, not a Primary-type packet or visual trail. */
    default boolean rootWeaponContactAvailable(){return false;}
    void present(SkillExecutionContext context,double radius,double duration);
    void trace(SkillExecutionContext context,String event,Map<String,?> details);
}
