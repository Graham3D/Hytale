package com.inigmasgames.hytalerpg.execution;
import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.support.SupportProfile;
import java.util.UUID;
/** Component-local handoff to the existing finite Fear authority, not another skill activation. */
public final class HitProcProfiles {
    private HitProcProfiles(){}
    public static SkillExecutionContext fear(SkillExecutionContext child,UUID victim,UUID world,Vec3 feet,Vec3 position,double seconds){
        if(!child.secondaryKind().equals("terror"))throw new IllegalArgumentException("NOT_TERROR_CHILD");
        var gson=new Gson();var data=gson.toJsonTree(child.profile()).getAsJsonObject();
        data.add("support",gson.toJsonTree(new SupportProfile(SupportProfile.Kind.FEAR,feet.subtract(position).length()+1,0,0,0,0,seconds,0)));
        var profile=gson.fromJson(data,Stage04SkillProfile.class);
        var target=new CommittedTarget(world,feet,position,position.subtract(feet).horizontalNormalized(),victim);
        return new SkillExecutionContext(child.request(),child.rootCastId(),child.skillInstanceId(),profile,child.compiledPlan(),child.snapshot(),child.equipment(),target,false,0,child.leechBudget(),0,child.effects(),"terror");
    }
}
