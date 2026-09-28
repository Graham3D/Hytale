package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import java.util.*;

/** Command-facing vocabulary. Base identity and eligibility still come from GearCatalog. */
public record GearQaRequest(String type, GearRarity rarity, DifficultyId era, Integer itemLevel, boolean max, String seed) {
    private static final Map<String,String> TYPES=types();
    private static Map<String,String> types(){
        var names=new LinkedHashMap<String,String>();
        aliases(names,"head","helmet","helm","head");aliases(names,"chest","chest","armor","body");
        aliases(names,"hands","gloves","hands","gauntlets");aliases(names,"legs","legs","pants","leggings");
        aliases(names,"sword","sword");aliases(names,"daggers","daggers","dagger");
        aliases(names,"shortbow","shortbow","bow");aliases(names,"longbow","longbow");
        aliases(names,"crossbow","crossbow");aliases(names,"battleaxe","battleaxe","axe");
        aliases(names,"mace","mace");aliases(names,"longsword","longsword","greatsword");
        aliases(names,"spear","spear");aliases(names,"shield","shield");
        aliases(names,"staff","staff");aliases(names,"wand","wand");
        aliases(names,"book","spellbook","book");aliases(names,"rifle","rifle");
        aliases(names,"blunderbuss","blunderbuss");aliases(names,"bomb","bomb");
        return Collections.unmodifiableMap(names);
    }
    private static void aliases(Map<String,String> names,String canonical,String... tokens){for(String token:tokens)names.put(token,canonical);}
    public static Set<String> typeTokens(){return TYPES.keySet();}
    public static GearQaRequest parse(String type,String rarity,String era,String option,String seed){
        String canonical=TYPES.get(type.toLowerCase(Locale.ROOT));
        if(canonical==null)throw new IllegalArgumentException("Unknown type \""+type+"\". Valid types: "+String.join(", ",TYPES.keySet()));
        GearRarity quality=switch(rarity.toLowerCase(Locale.ROOT).replace("_","").replace("-","")){
            case "normal"->GearRarity.NORMAL;case "magic"->GearRarity.MAGIC;case "rare"->GearRarity.RARE;
            default->throw new IllegalArgumentException("Unknown quality \""+rarity+"\". Valid qualities: normal, magic, rare.");
        };
        DifficultyId difficulty;try{difficulty=DifficultyId.valueOf(era.toUpperCase(Locale.ROOT));}
        catch(IllegalArgumentException invalid){throw new IllegalArgumentException("Unknown era \""+era+"\". Valid eras: normal, nightmare, hell.");}
        Integer level=null;boolean max=false;
        if(option!=null&&option.startsWith("seed:")){
            if(seed!=null)throw new IllegalArgumentException("Supply a seed only once.");
            seed=option.substring(5);
        }else if(option!=null){if(option.equalsIgnoreCase("max"))max=true;else try{level=Integer.valueOf(option);}
            catch(NumberFormatException invalid){throw new IllegalArgumentException("Fourth argument must be an item level from 1 to 99 or max.");}}
        if(level!=null&&(level<1||level>99))throw new IllegalArgumentException("Item level must be 1 through 99.");
        if(seed!=null&&(seed.isBlank()||seed.length()>80||!seed.matches("[A-Za-z0-9_.-]+")))
            throw new IllegalArgumentException("Seed must contain 1–80 letters, digits, dots, underscores or hyphens.");
        return new GearQaRequest(canonical,quality,difficulty,level,max,seed==null?UUID.randomUUID().toString():seed);
    }
    public boolean matches(GearCatalog.Base base){
        if(base.category()==GearCatalog.Category.TOOL)return false;
        return switch(type){
            case "head"->base.category()==GearCatalog.Category.ARMOR&&base.slot()==GearCatalog.Slot.HEAD;
            case "chest"->base.category()==GearCatalog.Category.ARMOR&&base.slot()==GearCatalog.Slot.CHEST;
            case "hands"->base.category()==GearCatalog.Category.ARMOR&&base.slot()==GearCatalog.Slot.HANDS;
            case "legs"->base.category()==GearCatalog.Category.ARMOR&&base.slot()==GearCatalog.Slot.LEGS;
            default->base.category()==GearCatalog.Category.HELD&&base.family().substring(3).split("_")[0].equals(type);
        };
    }
}
