package com.inigmasgames.hytalerpg.difficulty;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.combat.defense.DefenseView;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Versioned, precomputed hostile encounter baseline. No spawn or hit path reads files. */
public final class MonsterProgression {
    private record Anchor(int level,double health,double physicalProtection,double elementalFloor){}
    private record Config(String revision,double damagePerLevel,Map<String,Integer> referenceLevelOverrides,List<Anchor> anchors){}
    public record Entry(double healthGrowth,double damageGrowth,double defenseRating,double elementalFloor){}
    private static final MonsterProgression CURRENT=load();
    private final String revision;
    private final Map<String,Integer> referenceLevelOverrides;
    private final Entry[] levels=new Entry[100];

    private MonsterProgression(Config config){
        if(config==null||!"monster-progression-v1-test-baseline".equals(config.revision())
                ||!Double.isFinite(config.damagePerLevel())||config.damagePerLevel()<0
                ||config.referenceLevelOverrides()==null||config.anchors()==null||config.anchors().size()<2
                ||config.anchors().getFirst().level()!=1||config.anchors().getLast().level()!=99)
            throw new IllegalArgumentException("MONSTER_PROGRESSION_CONFIG");
        revision=config.revision();
        config.referenceLevelOverrides().forEach((role,level)->{
            if(role==null||role.isBlank()||level==null||level<1||level>99)
                throw new IllegalArgumentException("MONSTER_REFERENCE_OVERRIDE");
        });
        referenceLevelOverrides=Map.copyOf(config.referenceLevelOverrides());
        int previous=0;
        for(var a:config.anchors()){
            if(a.level()<=previous||a.level()>99||!Double.isFinite(a.health())||a.health()<=0
                    ||!Double.isFinite(a.physicalProtection())||a.physicalProtection()<0||a.physicalProtection()>=1
                    ||!Double.isFinite(a.elementalFloor())||a.elementalFloor()<0||a.elementalFloor()>.75)
                throw new IllegalArgumentException("MONSTER_PROGRESSION_ANCHOR");
            previous=a.level();
        }
        for(int level=1;level<=99;level++){
            int upper=1;while(config.anchors().get(upper).level()<level)upper++;
            var a=config.anchors().get(upper-1);var b=config.anchors().get(upper);
            double t=(double)(level-a.level())/(b.level()-a.level());
            double health=blend(a.health(),b.health(),t);
            double protection=blend(a.physicalProtection(),b.physicalProtection(),t);
            double floor=blend(a.elementalFloor(),b.elementalFloor(),t);
            levels[level]=new Entry(health,1+config.damagePerLevel()*(level-1),
                    DefenseView.rating(level,protection),floor);
        }
    }
    private static double blend(double a,double b,double t){return a+t*(b-a);}
    private static MonsterProgression load(){
        try(var stream=MonsterProgression.class.getResourceAsStream("/rpg/progression/monster-progression-v1.json")){
            if(stream==null)throw new IllegalStateException("MONSTER_PROGRESSION_MISSING");
            return new MonsterProgression(new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),Config.class));
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    public static MonsterProgression current(){return CURRENT;}
    public String revision(){return revision;}
    public boolean explicitlyOptsIn(String role){return referenceLevelOverrides.containsKey(role);}
    /** Zero selects global chassis scaling; a positive value opts this role into an explicit reference ratio. */
    public int referenceLevel(String role){return referenceLevelOverrides.getOrDefault(role,0);}
    public Entry at(int level){if(level<1||level>99)throw new IllegalArgumentException("MONSTER_PROGRESSION_LEVEL");return levels[level];}
    public double healthRatio(int level,int referenceLevel){return at(level).healthGrowth()/at(referenceLevel).healthGrowth();}
    public double damageRatio(int level,int referenceLevel){return at(level).damageGrowth()/at(referenceLevel).damageGrowth();}
}
