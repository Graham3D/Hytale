package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Player-facing text only. Affix mechanics and frozen rolls stay in GearInstance. */
public final class GearTooltip {
    private GearTooltip() {}
    public enum Style { NAME, NORMAL, AFFIX, ERROR }
    public record Line(String text,Style style,String color,boolean breakBefore) {
        public Line(String text,Style style,String color){this(text,style,color,false);}
    }
    public static List<Line> describe(GearInstance gear,int actorLevel,Map<RpgAttribute,Integer> requirementAttributes) {
        var lines=new ArrayList<Line>();
        lines.add(new Line(gear.displayName(),Style.NAME,gear.rarity().color));
        var stats=gear.intrinsicStats();
        if(stats.containsKey("physicalMin")&&stats.containsKey("physicalMax"))
            lines.add(new Line("Physical Damage: "+number(stats.get("physicalMin"))+"–"+number(stats.get("physicalMax")),Style.NORMAL,null));
        for(String key:List.of("protectionPoints","shieldDefense","magicPower","healingPower","health","mana"))
            if(stats.containsKey(key))lines.add(new Line(statLabel(key)+": "+(key.equals("health")||key.equals("mana")?"+":"")+number(stats.get(key)),Style.NORMAL,null));
        stats.entrySet().stream().filter(e->!Set.of("physicalMin","physicalMax","protectionPoints","shieldDefense","magicPower","healingPower","health","mana").contains(e.getKey()))
            .sorted(Map.Entry.comparingByKey()).forEach(e->lines.add(new Line(statLabel(e.getKey())+": "+number(e.getValue()),Style.NORMAL,null)));
        lines.add(new Line("Required Level: "+gear.requirements().level(),actorLevel<gear.requirements().level()?Style.ERROR:Style.NORMAL,null,true));
        for(var stat:RpgAttribute.values()) {
            int floor=gear.requirements().attributes().getOrDefault(stat,0);
            if(floor>0) lines.add(new Line("Required "+attributeName(stat)+": "+floor,requirementAttributes.getOrDefault(stat,0)<floor?Style.ERROR:Style.NORMAL,null));
        }
        boolean firstAffix=true;
        for(var affix:gear.affixes()) {
            lines.add(new Line(GearAffixDisplay.format(affix),Style.AFFIX,GearRarity.AFFIX_COLOR,firstAffix));
            firstAffix=false;
        }
        if(gear.qaOnly()) lines.add(new Line("Cannot salvage",Style.NORMAL,null,true));
        if(!GearAffixRuntime.supported(gear))lines.add(new Line("Legacy modifier unavailable",Style.ERROR,null,true));
        return List.copyOf(lines);
    }
    private static String statLabel(String key){return switch(key){
        case "protectionPoints"->"Armor";case "shieldDefense"->"Shield Defense";
        case "magicPower"->"Magic Power";case "healingPower"->"Healing Power";
        case "health"->"Health";case "mana"->"Mana";
        default->key.replaceAll("([a-z])([A-Z])","$1 $2").replace('_',' ');
    };}
    private static String attributeName(RpgAttribute stat){return switch(stat){
        case STR->"Strength";case DEX->"Dexterity";case INT->"Intelligence";
        case WIS->"Wisdom";case LUCK->"Luck";
    };}
    static String number(double value){return BigDecimal.valueOf(value).setScale(2,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();}
}
