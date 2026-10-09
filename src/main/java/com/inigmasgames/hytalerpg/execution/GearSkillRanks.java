package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot;
import com.inigmasgames.hytalerpg.gear.GearInstance;
import com.inigmasgames.hytalerpg.gear.GearRarity;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One rank selector for execution and UI. Caller supplies the validated learned skill/profile. */
public final class GearSkillRanks {
    private GearSkillRanks() { }
    private static final com.inigmasgames.hytalerpg.gear.GearCatalog GEAR_CATALOG=
            com.inigmasgames.hytalerpg.gear.GearCatalog.load();
    private static final Map<String,Set<String>> SKILL_TAGS=RpgCatalog.prepared().catalog().skills().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(s->s.id().value(),s->s.tags()));
    private static final java.util.concurrent.ConcurrentHashMap<String,java.util.List<String>> MATCHING=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String,String> SCHOOL=Map.of(
            "WA-123","WIND","WA-124","WATER","WA-125","FIRE","WA-126","EARTH",
            "WA-127","LIGHTNING","WA-128","VOID");
    // Canonical catalog still calls this school COLD. These exact authored skill IDs are the
    // WATER school until the catalog owner publishes an explicit schoolElement field.
    private static final Set<String> WATER_SKILLS=Set.of("frost_bolt","cold_blast","frost_nova",
            "cold_wave","blizzard","comet","avalanche","chilling_aura");
    // An explicit skill roster: a PHYSICAL damage tag or incidental staff requirement does not
    // establish the martial family. Keep this roster aligned with the authored weapon profiles.
    private static final Set<String> MARTIAL_SKILLS=Set.of("quick_slash","heavy_swing","spear_thrust",
            "shield_bash","guard","dagger_flurry","maul_swing","jump_strike","charge",
            "finishing_strike","whirlwind","execution_strike","riposte","backstab","frenzy",
            "axe_toss","spear_toss","quick_shot","crossbow_bolt","blunderbuss_shot","snipe",
            "powder_mine","explosive_flask","bomb_toss","ground_slam","lightning_arrow",
            "hunter_s_mark","flame_weapon");

    public static int bonus(GearEffectSnapshot gear,Stage04SkillProfile profile){
        if(gear==null||profile==null)throw new IllegalArgumentException("Missing rank context");
        Set<String> tags=SKILL_TAGS.get(profile.skillId());
        if(tags==null)throw new IllegalArgumentException("Unknown learned skill");
        if(gear.sources(GearEffectSnapshot.Operator.SKILLER).isEmpty())return 0;
        Map<UUID,GearInstance> items=gear.items().stream().collect(java.util.stream.Collectors.toMap(GearInstance::identity,i->i));
        int result=0;
        for(var source:gear.sources(GearEffectSnapshot.Operator.SKILLER)){
            GearInstance item=items.get(source.itemId());
            if(item==null)throw new IllegalStateException("Rank source without valid equipment");
            int value=(int)source.value();
            if(value!=source.value()||value<1)throw new IllegalStateException("Invalid frozen rank roll");
            String id=source.affixId();
            if(id.equals("WA-122")&&source.roll().selector()==null)
                throw new IllegalStateException("Named skill selector absent from frozen gear schema");
            if(id.equals("WA-122")&&!MATCHING.computeIfAbsent(item.baseId(),baseId->
                    com.inigmasgames.hytalerpg.gear.GearDropGenerator.matchingSkillIds(GEAR_CATALOG.base(baseId)))
                    .contains(source.roll().selector()))
                throw new IllegalStateException("Named skill selector does not match its frozen carrier");
            if(!legal(item,id,value))continue;
            boolean matches=id.equals("WA-121")
                    ||id.equals("WA-122")&&source.roll().selector().equals(profile.skillId())
                    ||SCHOOL.containsKey(id)&&(id.equals("WA-124")?WATER_SKILLS.contains(profile.skillId()):tags.contains(SCHOOL.get(id)))
                    ||id.equals("WA-129")&&MARTIAL_SKILLS.contains(profile.skillId())
                    ||id.equals("WA-130")&&(profile.support()!=null&&profile.support().kind()==com.inigmasgames.hytalerpg.execution.support.SupportProfile.Kind.HEAL
                            ||profile.connection()!=null&&profile.connection().friendlyTether())
                    ||id.equals("WA-131")&&profile.summon()!=null
                    ||id.equals("WA-132")&&profile.strike()!=null&&profile.family()==Stage04SkillProfile.Family.STRIKE
                    ||id.equals("WA-133")&&profile.projectile()!=null&&profile.family()==Stage04SkillProfile.Family.PROJECTILE;
            if(matches)result=Math.addExact(result,value);
        }
        return result;
    }

    /** UI and execution share the same selector; only persisted learned ranks enter this projection. */
    public static Map<String,RankProjection> projectLearned(GearEffectSnapshot gear,
            Map<String,Integer> learnedBaseRanks,Stage04SkillProfiles profiles){
        if(gear==null||learnedBaseRanks==null||profiles==null)throw new IllegalArgumentException("Missing rank projection context");
        var result=new java.util.LinkedHashMap<String,RankProjection>();
        for(var entry:learnedBaseRanks.entrySet()){
            if(entry.getValue()==null||entry.getValue()<1)continue;
            int base=entry.getValue();
            var profile=profiles.require(entry.getKey());
            result.put(entry.getKey(),new RankProjection(base,EffectiveSkillLevel.resolveBase(base,bonus(gear,profile))));
        }
        return Map.copyOf(result);
    }
    public record RankProjection(int base,int effective){}

    private static boolean legal(GearInstance item,String id,int rank){
        int level=item.itemLevel();GearRarity rarity=item.rarity();
        if(id.equals("WA-121"))return rank==1&&level>=78&&(rarity==GearRarity.VERY_RARE||rarity==GearRarity.LEGENDARY)
                ||rank==2&&level>=92&&rarity==GearRarity.LEGENDARY;
        if(id.equals("WA-122")){
            if(rank==1)return level>=35&&rarity!=GearRarity.COMMON&&rarity!=GearRarity.NORMAL;
            if(rank==2)return level>=60&&(rarity==GearRarity.RARE||rarity==GearRarity.VERY_RARE||rarity==GearRarity.LEGENDARY);
            if(rank==3)return level>=82&&(rarity==GearRarity.RARE||rarity==GearRarity.VERY_RARE||rarity==GearRarity.LEGENDARY);
            return rank==4&&level>=94&&rarity==GearRarity.RARE;
        }
        if(rank==1)return level>=55&&(rarity==GearRarity.RARE||rarity==GearRarity.VERY_RARE||rarity==GearRarity.LEGENDARY);
        if(rank==2)return level>=80&&(rarity==GearRarity.RARE||rarity==GearRarity.VERY_RARE||rarity==GearRarity.LEGENDARY);
        return rank==3&&level>=94&&rarity==GearRarity.RARE;
    }
}
