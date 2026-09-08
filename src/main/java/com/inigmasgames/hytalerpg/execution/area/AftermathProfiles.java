package com.inigmasgames.hytalerpg.execution.area;
import com.google.gson.*;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.connection.ConnectionProfile;

/** A derived immutable finite profile; never a second pass through the Link compiler/resolver. */
public final class AftermathProfiles {
    private static final Gson JSON=new Gson();private AftermathProfiles(){}
    public static Stage04SkillProfile resolve(Stage04SkillProfile parent){
        if(!ProfileComponentPolicy.aftermath(parent))throw new IllegalArgumentException("NO_AFTERMATH_COMPONENT");
        var result=JSON.toJsonTree(parent).getAsJsonObject();
        if(parent.area()!=null){
            var p=parent.area();var area=result.getAsJsonObject("area");double lifetime=p.lifetimeSeconds()*.4;
            area.addProperty("lifetimeSeconds",lifetime);
            scale(area,.6,"radius","length","width","impactRadius","innerRadius","statusInnerRadius","pullCoreRadius","visualCoreRadius");
            if(p.impactCount()>1){
                int count=Math.max(0,(int)Math.ceil((lifetime-p.firstImpactSeconds())/p.intervalSeconds()-1e-9));
                if(count<1)throw new IllegalArgumentException("AFTERMATH_NO_COMPLETE_IMPACT");
                area.addProperty("impactCount",count);area.addProperty("perTargetHitCap",Math.min(p.perTargetHitCap(),count));
            }else if(p.periodic()){
                int ticks=(int)Math.floor(lifetime/p.intervalSeconds()+1e-9);
                if(ticks<1)throw new IllegalArgumentException("AFTERMATH_NO_COMPLETE_PULSE");
                area.addProperty("perTargetHitCap",Math.min(p.perTargetHitCap(),ticks));
            }
        }else{
            var p=parent.connection();var connection=result.getAsJsonObject("connection");
            connection.addProperty("lifetimeSeconds",p.lifetimeSeconds()*.4);
            scale(connection,.6,"radius","width");
            if(p.kind()==ConnectionProfile.Kind.ORBIT)scale(connection,.6,"range");
        }
        return JSON.fromJson(result,Stage04SkillProfile.class);
    }
    private static void scale(JsonObject value,double factor,String...fields){for(String key:fields)if(value.has(key))value.addProperty(key,value.get(key).getAsDouble()*factor);}
}
