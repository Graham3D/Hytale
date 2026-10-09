package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Player-facing text only. Affix mechanics and frozen rolls stay in GearInstance. */
public final class GearTooltip {
    private GearTooltip() {}
    public static final String NEUTRAL_COLOR = "#c9d2dd";
    public static final String DAMAGE_COLOR = "#ffffff";
    public static final String MUTED_COLOR = "#a6b5c5";
    public static final String ERROR_COLOR = "#ff6666";
    public static final String DIVIDER_COLOR = "#425568";
    public enum Style { NAME, NORMAL, DIVIDER, HEADING, DAMAGE, AFFIX, ERROR, FLAVOR }
    public record Line(String text,Style style,String color,boolean breakBefore) {
        public Line(String text,Style style,String color){this(text,style,color,false);}
    }
    public static List<Line> describe(GearInstance gear,int actorLevel,Map<RpgAttribute,Integer> requirementAttributes) {
        return describe(gear,actorLevel,requirementAttributes,null);
    }
    /** Optional authored flavor only; this view never generates flavor or rerolls an item. */
    public static List<Line> describe(GearInstance gear,int actorLevel,Map<RpgAttribute,Integer> requirementAttributes,String flavor) {
        var lines=new ArrayList<Line>();
        lines.add(new Line(gear.displayName(),Style.NAME,gear.rarity().color));
        lines.add(new Line(GearBasePresentation.classification(gear.baseId()),Style.NORMAL,MUTED_COLOR));
        lines.add(divider());
        var stats=gear.intrinsicStats();
        if(stats.containsKey("physicalMin")&&stats.containsKey("physicalMax")) {
            var resolved=GearCombatEffects.physical(gear);
            lines.add(new Line("Damage",Style.HEADING,"#bca57a"));
            lines.add(new Line(String.format(Locale.ROOT,"%.1f - %.1f",resolved.minimum(),resolved.maximum()),Style.DAMAGE,DAMAGE_COLOR));
            // Native primary actions have combo/charged timing, not a verified
            // sustained APS scalar. Do not substitute a cooldown reciprocal.
        }
        for(String key:List.of("protectionPoints","shieldDefense","magicPower","healingPower","health","mana"))
            if(stats.containsKey(key))lines.add(new Line(statLabel(key)+": "+(key.equals("health")||key.equals("mana")?"+":"")+number(stats.get(key)),Style.NORMAL,NEUTRAL_COLOR));
        stats.entrySet().stream().filter(e->!Set.of("physicalMin","physicalMax","protectionPoints","shieldDefense","magicPower","healingPower","health","mana").contains(e.getKey()))
            .sorted(Map.Entry.comparingByKey()).forEach(e->lines.add(new Line(statLabel(e.getKey())+": "+number(e.getValue()),Style.NORMAL,NEUTRAL_COLOR)));
        lines.add(divider());
        for(var affix:gear.affixes()) {
            lines.add(new Line(GearAffixDisplay.format(affix),Style.AFFIX,GearRarity.AFFIX_COLOR));
        }
        if(!gear.affixes().isEmpty()) lines.add(divider());
        var gate=gear.requirements();
        boolean levelFailed=gate.failures(actorLevel,requirementAttributes,gear.category()==GearCatalog.Category.ARMOR)
                .contains("Requires Character Level "+gate.level());
        var missing=gate.missingAttributes(requirementAttributes);
        lines.add(new Line("Requires Level "+gate.level(),levelFailed?Style.ERROR:Style.NORMAL,levelFailed?ERROR_COLOR:MUTED_COLOR));
        for(var stat:RpgAttribute.values()) {
            int floor=gate.attributes().getOrDefault(stat,0);
            if(floor>0) lines.add(new Line("Requires "+floor+" "+attributeName(stat),missing.contains(stat)?Style.ERROR:Style.NORMAL,
                    missing.contains(stat)?ERROR_COLOR:MUTED_COLOR));
        }
        if(!GearAffixRuntime.supported(gear))lines.add(new Line("Legacy modifier unavailable",Style.ERROR,ERROR_COLOR,true));
        if(flavor!=null&&!flavor.isBlank()){
            lines.add(divider());
            lines.add(new Line(flavor.strip(),Style.FLAVOR,MUTED_COLOR));
        }
        return List.copyOf(lines);
    }
    // A semantic section boundary. The Hywind inventory renders a real Group;
    // native fallback uses spacing and never sends decorative characters.
    private static Line divider(){return new Line("",Style.DIVIDER,DIVIDER_COLOR);}
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
