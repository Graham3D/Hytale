package com.inigmasgames.hytalerpg.execution.connection;
import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Approved RPG conversion data; no native projectile/model/interaction ID is invented. */
public final class OrbitConversionProfiles {
    private static final Gson JSON=new Gson();private OrbitConversionProfiles(){}
    public record Config(double durationSeconds,double magnitudeFactor,double radius,double originHeight,double degreesPerSecond,double sampleSeconds,double targetIntervalSeconds,int maxOrbs){
        public Config{for(double n:new double[]{durationSeconds,magnitudeFactor,radius,originHeight,degreesPerSecond,sampleSeconds,targetIntervalSeconds})if(!Double.isFinite(n)||n<=0)throw new IllegalArgumentException("INVALID_ORBIT_CONFIG");
            if(durationSeconds>10||magnitudeFactor>1||radius>8||originHeight>3||degreesPerSecond>360||sampleSeconds<.05||durationSeconds/sampleSeconds>256||targetIntervalSeconds<.75||maxOrbs<1||maxOrbs>3)throw new IllegalArgumentException("UNBOUNDED_ORBIT_CONFIG");}
    }
    public static final Config CONFIG=load();
    private static Config load(){try(var in=OrbitConversionProfiles.class.getResourceAsStream("/rpg/runtime/orbit-conversion.json")){if(in==null)throw new IllegalStateException("ORBIT_CONFIG_MISSING");return JSON.fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),Config.class);}catch(java.io.IOException e){throw new IllegalStateException(e);}}
    public static boolean eligible(Stage04SkillProfile p){return p.family()==Stage04SkillProfile.Family.PROJECTILE&&p.projectile()!=null
            ||p.connection()!=null&&Set.of(ConnectionProfile.Kind.ORB,ConnectionProfile.Kind.ORBIT).contains(p.connection().kind());}
    public static int count(Stage04SkillProfile p,CompiledSkillPlan plan){return plan.projectileModifiers().volley()?3:p.connection()!=null&&p.connection().kind()==ConnectionProfile.Kind.ORBIT?Math.min(CONFIG.maxOrbs(),p.connection().details().bladeCount()):p.projectile()!=null?Math.min(CONFIG.maxOrbs(),p.projectile().details().pattern().count()):1;}
    public static Stage04SkillProfile convert(Stage04SkillProfile p,CompiledSkillPlan plan){
        if(!eligible(p))throw new IllegalArgumentException("NO_ORBIT_CONVERSION_COMPONENT");
        var result=JSON.toJsonTree(p).getAsJsonObject();var cfg=CONFIG;
        double contact=p.projectile()!=null?p.projectile().radius():p.connection().radius();
        String element=p.connection()!=null?p.connection().element():plan.finalTags().contains("COLD")?"COLD":plan.finalTags().contains("FIRE")?"FIRE":plan.finalTags().contains("EARTH")?"EARTH":plan.finalTags().contains("POISON")?"POISON":plan.finalTags().contains("NATURE")?"NATURE":plan.finalTags().contains("VOID")?"VOID":plan.finalTags().contains("ARCANE")?"ARCANE":"PHYSICAL";
        var detail=new ConnectionProfile.Details(0,List.of(),count(p,plan),cfg.degreesPerSecond(),cfg.targetIntervalSeconds(),0,"",0);
        var connection=new ConnectionProfile(ConnectionProfile.Kind.ORBIT,cfg.radius(),0,Math.max(.10,contact*2),0,0,cfg.durationSeconds(),cfg.sampleSeconds(),p.damageCoefficient(),0,contact,cfg.originHeight(),element,detail);
        result.addProperty("family","ORBIT");result.add("connection",JSON.toJsonTree(connection));
        // Keep projectile payload metadata solely for ammo/status/knockback, never create its native carrier.
        return JSON.fromJson(result,Stage04SkillProfile.class);
    }
}
