package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.SkillId;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Gear Master v1.2 §17.1 presentation only; mechanics and persisted rolls never use these strings. */
public final class GearAffixDisplay {
    private static final Pattern VALUE=Pattern.compile("(?<![A-Za-z])V(?![A-Za-z])");
    private static final Pattern BRACKET=Pattern.compile("\\[[^]]*]");
    private GearAffixDisplay() {}
    private static final class GearDefinitions {
        private static final GearCatalog GEAR=GearCatalog.load();
    }
    private static final class SkillDefinitions {
        private static final RpgCatalog SKILLS=RpgCatalog.loadCanonical();
    }
    /** An affix with [Skill] needs a frozen selected SkillId before its gated family may enter loot. */
    public static String format(GearInstance.AffixRoll roll){
        Objects.requireNonNull(roll);
        return format(roll,roll.selector()==null?null:new SkillId(roll.selector()));
    }
    public static String format(GearInstance.AffixRoll roll,SkillId selectedSkill){
        Objects.requireNonNull(roll);
        GearCatalog.Affix definition;
        try{definition=GearDefinitions.GEAR.affix(roll.familyId());}
        catch(IllegalArgumentException unknown){return "Legacy modifier unavailable";}
        String template=definition.playerTooltipTemplate();
        if(template.contains("[Skill]")&&selectedSkill==null)return "Skill modifier unavailable";
        String name=selectedSkill==null?null:SkillDefinitions.SKILLS.skill(selectedSkill)
                .orElseThrow(()->new IllegalArgumentException("Unknown selected skill")).name();
        return resolve(template,roll.value(),name);
    }
    public static String resolve(String template,double persistedValue,String playerSkillName){
        validateTemplate(template);
        if(!Double.isFinite(persistedValue))throw new IllegalArgumentException("Invalid persisted affix roll");
        String result=VALUE.matcher(template).replaceAll(Matcher.quoteReplacement(GearTooltip.number(persistedValue)));
        if(result.contains("[Skill]")){
            if(playerSkillName==null||playerSkillName.isBlank())throw new IllegalArgumentException("Player-facing skill name required");
            result=result.replace("[Skill]",playerSkillName);
        }
        return result;
    }
    static void validateTemplate(String template){
        if(template==null||template.isBlank()||template.length()>160||template.indexOf('\n')>=0||template.indexOf('\r')>=0)
            throw new IllegalArgumentException("Invalid player tooltip template");
        var brackets=BRACKET.matcher(template);
        while(brackets.find())if(!brackets.group().equals("[Skill]"))
            throw new IllegalArgumentException("Unknown player tooltip placeholder: "+brackets.group());
        if(template.replace("[Skill]","").contains("[")||template.replace("[Skill]","").contains("]"))
            throw new IllegalArgumentException("Malformed player tooltip placeholder");
    }
}
