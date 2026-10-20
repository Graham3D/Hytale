package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import java.util.*;

/** Gear Master section 19. Types and provenance never convert into one another. */
public final class GearEconomy {
    private GearEconomy() {}
    public enum Component { PLAIN_SCRAP, ARCANE_DUST, RESONANT_SHARD, SINGULAR_CORE }
    public enum Grade { CAMPAIGN, TEMPERED, ASCENDANT;
        public static Grade from(DifficultyId era) { return switch(era){case NORMAL->CAMPAIGN;case NIGHTMARE->TEMPERED;case HELL->ASCENDANT;}; }
    }
    public record Material(Component type,Grade grade) {
        public Material { Objects.requireNonNull(type);Objects.requireNonNull(grade); }
        public String key(){return type.name()+"/"+grade.name();}
        public static Material parse(String key){var parts=key.split("/");if(parts.length!=2)throw new IllegalArgumentException("Invalid material");return new Material(Component.valueOf(parts[0]),Grade.valueOf(parts[1]));}
    }
    public record Salvage(Material material,int quantity) {}
    public record Recipe(int targetRank,Component type,Grade minimumGrade,int cost,int resetPercent) {}
    private static final int[] COST={4,6,8,10,8,12,16,20,24,24,32,40,48,56,40,56,72,88,112};
    private static final int[] RESET={0,0,0,0,10,11,12,13,14,15,16,18,19,20,21,23,25,28,30};
    public static int yield(int level){if(level<1||level>99)throw new IllegalArgumentException("Item level");return new int[]{1,2,3,4,8,12,20,28,40,56}[level/10];}
    public static Optional<Component> componentFor(GearQuality quality){return switch(quality){
        case NORMAL->Optional.empty();case MAGIC->Optional.of(Component.PLAIN_SCRAP);
        case RARE->Optional.of(Component.ARCANE_DUST);case SET->Optional.of(Component.RESONANT_SHARD);
        case UNIQUE->Optional.of(Component.SINGULAR_CORE);
    };}
    public static Optional<Salvage> salvage(GearInstance item){
        if(item.qaOnly()||item.category()==GearCatalog.Category.TOOL||item.quality()==GearQuality.NORMAL)return Optional.empty();
        // Legacy enum values remain on frozen items, preserving previously earned high-band salvage.
        var type=switch(item.rarity()){case MAGIC,UNCOMMON,RARE->componentFor(item.quality()).orElseThrow();
            case VERY_RARE->Component.RESONANT_SHARD;case LEGENDARY->Component.SINGULAR_CORE;
            default->throw new IllegalStateException();};
        return Optional.of(new Salvage(new Material(type,Grade.from(item.sourceEra())),GearEconomy.yield(item.itemLevel())));
    }
    public static Recipe recipe(int target){if(target<2||target>20)throw new IllegalArgumentException("Base rank 2..20 required");
        return new Recipe(target,target<=5?Component.PLAIN_SCRAP:target<=10?Component.ARCANE_DUST:target<=15?Component.RESONANT_SHARD:Component.SINGULAR_CORE,
                target<=10?Grade.CAMPAIGN:target<=15?Grade.TEMPERED:Grade.ASCENDANT,COST[target-2],RESET[target-2]);}
    /** Returned debit is also the substitution preview. Insufficient funds mutate nothing. */
    public static Map<String,Long> debit(Map<String,Long> wallet,Recipe recipe){
        var result=new LinkedHashMap<String,Long>();long remaining=recipe.cost();
        for(var grade:Grade.values())if(grade.ordinal()>=recipe.minimumGrade().ordinal()){
            String key=new Material(recipe.type(),grade).key();long count=Math.min(remaining,wallet.getOrDefault(key,0L));
            if(count>0){result.put(key,count);remaining-=count;}
        }
        if(remaining!=0)throw new IllegalArgumentException("Insufficient components of required provenance");return Collections.unmodifiableMap(result);
    }
}
