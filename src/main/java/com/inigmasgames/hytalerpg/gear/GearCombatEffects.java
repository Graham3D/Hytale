package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets;
import java.util.*;

/** Frozen outgoing gear inputs used by native weapon leaves and explicit skill execution calls. */
public final class GearCombatEffects {
    private GearCombatEffects() {}
    public enum Channel { PHYSICAL, WIND, WATER, FIRE, EARTH, LIGHTNING, VOID }
    private static final String[] FLAT={"WA-017","WA-018","WA-019","WA-020","WA-021","WA-022"};
    private static final String[] INCREASED={"WA-023","WA-024","WA-025","WA-026","WA-027","WA-028"};
    private static final String[] PENETRATION={"WA-029","WA-030","WA-031","WA-032","WA-033","WA-034"};
    private static final String[] CONVERSION={"WA-035","WA-036","WA-037","WA-038","WA-039","WA-040"};
    private static final String[] RESISTANCE={"WA-072","WA-073","WA-074","WA-075","WA-076","WA-077"};
    private static final GearBindings BINDINGS=new GearBindings();
    public static String nativeCause(Channel channel){return switch(channel){
        case PHYSICAL->"Physical";case WIND->"Wind";case WATER->"Ice";case FIRE->"Fire";
        case EARTH->"RPG_Nature";case LIGHTNING->"Lightning";case VOID->"RPG_Void";};}
    public static Channel nativeChannel(String cause){return switch(cause){
        case "Physical","Projectile"->Channel.PHYSICAL;case "Wind"->Channel.WIND;case "Ice"->Channel.WATER;
        case "Fire"->Channel.FIRE;case "RPG_Nature"->Channel.EARTH;case "Lightning"->Channel.LIGHTNING;
        case "RPG_Void"->Channel.VOID;default->null;};}
    private static int index(Channel channel){return channel.ordinal()-1;}
    /** Preserve the existing native-basic observer path for legacy local-Physical-only equipment. */
    public static boolean needsNativeEnvelope(Hit hit){
        for(var item:hit.snapshot().items())for(var roll:item.affixes()){
            String id=roll.familyId();
            boolean global=id.matches("WA-00[467]|WA-01[013]|WA-0(?:2[3-9]|3[0-4]|6[5-8]|9[4-8])");
            boolean local=item.identity().equals(hit.itemId())
                    &&id.matches("WA-005|WA-0(?:1[7-9]|2[0-2]|3[5-9]|[4-5][0-9]|6[0-4])|WA-13[4-9]|WA-14[0-6]");
            if(global||local)return true;
        }
        return false;
    }
    private static double tenth(double value){return Math.round(value*10.0)/10.0;}
    /** The §4.1 range, with final 0.1-unit endpoint rounding. */
    public static GearAffixRuntime.Range physical(GearInstance item){
        return physicalRange(item.intrinsicStats().getOrDefault("physicalMin",0d),item.intrinsicStats().getOrDefault("physicalMax",0d),
                GearAffixRuntime.value(item,"WA-001"),GearAffixRuntime.value(item,"WA-157"),
                GearAffixRuntime.value(item,"WA-158"),GearAffixRuntime.value(item,"WA-002"));
    }
    public static GearAffixRuntime.Range physicalRange(double intrinsicMin,double intrinsicMax,double both,double minimum,double maximum,double enhanced){
        if(!Double.isFinite(intrinsicMin)||!Double.isFinite(intrinsicMax)||!Double.isFinite(both)
                ||!Double.isFinite(minimum)||!Double.isFinite(maximum)||!Double.isFinite(enhanced))
            throw new IllegalArgumentException("Invalid Physical range");
        double factor=1+enhanced/100;
        double min=intrinsicMin+both+minimum;
        double max=intrinsicMax+both+maximum;
        double resolvedMin=tenth(Math.max(.1,min*factor));
        return new GearAffixRuntime.Range(resolvedMin,tenth(Math.max(resolvedMin,Math.max(min,max)*factor)));
    }
    public record Hit(UUID itemId,String revision,String rootId,Map<Channel,Double> amounts,
                      Map<Channel,Double> penetration,Map<Channel,Double> increased,
                      boolean critical,double criticalMultiplier,
                      GearEffectSnapshot snapshot,com.inigmasgames.hytalerpg.execution.math.Vec3 origin) {
        public Hit {
            Objects.requireNonNull(revision);Objects.requireNonNull(rootId);
            Objects.requireNonNull(snapshot);amounts=Map.copyOf(amounts);penetration=Map.copyOf(penetration);
            increased=Map.copyOf(increased);
        }
        public double amount(Channel channel){return amounts.getOrDefault(channel,0d);}
        public double penetration(Channel channel){return penetration.getOrDefault(channel,0d);}
        public double increased(Channel channel){return increased.getOrDefault(channel,0d);}
    }
    /** Accept once per authored strike. supplied physical is the already sampled, locally enhanced source power. */
    public static Hit attack(GearEffectSnapshot equipped,UUID itemId,String rootId,double physical,double coefficient,
                             boolean attack,boolean spell,double skillConvertedFraction,Channel skillDestination,
                             double baselineCritChance,double baselineCritMultiplier,boolean canCrit){
        return attack(equipped,itemId,rootId,physical,coefficient,attack,spell,skillConvertedFraction,skillDestination,
                baselineCritChance,baselineCritMultiplier,canCrit,null);
    }
    public static Hit attack(GearEffectSnapshot equipped,UUID itemId,String rootId,double physical,double coefficient,
                             boolean attack,boolean spell,double skillConvertedFraction,Channel skillDestination,
                             double baselineCritChance,double baselineCritMultiplier,boolean canCrit,
                             com.inigmasgames.hytalerpg.execution.math.Vec3 origin){
        return attack(equipped,itemId,rootId,physical,coefficient,attack,spell,skillConvertedFraction,skillDestination,
                baselineCritChance,baselineCritMultiplier,canCrit,origin,
                new com.inigmasgames.hytalerpg.combat.damage.CriticalRoller(
                        ()->new java.util.SplittableRandom(stableHash(rootId)).nextDouble()));
    }
    public static Hit attack(GearEffectSnapshot equipped,UUID itemId,String rootId,double physical,double coefficient,
                             boolean attack,boolean spell,double skillConvertedFraction,Channel skillDestination,
                             double baselineCritChance,double baselineCritMultiplier,boolean canCrit,
                             com.inigmasgames.hytalerpg.execution.math.Vec3 origin,
                             com.inigmasgames.hytalerpg.combat.damage.CriticalRoller criticalRoller){
        Objects.requireNonNull(equipped);Objects.requireNonNull(rootId);
        if(!Double.isFinite(physical)||physical<0||!Double.isFinite(coefficient)||coefficient<0
                ||!Double.isFinite(skillConvertedFraction)||skillConvertedFraction<0||skillConvertedFraction>1
                ||!Double.isFinite(baselineCritChance)||baselineCritChance<0||baselineCritChance>1
                ||!Double.isFinite(baselineCritMultiplier)||baselineCritMultiplier<1
                ||skillConvertedFraction>0&&(skillDestination==null||skillDestination==Channel.PHYSICAL))
            throw new IllegalArgumentException("Invalid gear combat input");
        var local=itemId==null?GearEffectSnapshot.EMPTY:equipped.forItem(itemId);
        if(itemId!=null&&local.empty())throw new IllegalArgumentException("Contributing item absent from valid equipment");
        var values=new EnumMap<Channel,Double>(Channel.class);
        for(var channel:Channel.values())values.put(channel,0d);
        double remaining=physical;
        if(skillConvertedFraction>0){double moved=physical*skillConvertedFraction;remaining-=moved;values.merge(skillDestination,moved,Double::sum);}
        for(var channel:Channel.values())if(channel!=Channel.PHYSICAL){
            int i=index(channel);double moved=Math.min(remaining,physical*local.percent(CONVERSION[i]));
            remaining-=moved;values.merge(channel,moved+local.value(FLAT[i]),Double::sum);
        }
        values.put(Channel.PHYSICAL,remaining);
        double chance=Math.min(.75,baselineCritChance+equipped.percent(GearEffectSnapshot.Operator.CRIT_CHANCE));
        boolean critical=criticalRoller.roll(chance,canCrit);
        double multiplier=baselineCritMultiplier+equipped.percent(GearEffectSnapshot.Operator.CRIT_MULTIPLIER);
        var result=new EnumMap<Channel,Double>(Channel.class);
        var penetration=new EnumMap<Channel,Double>(Channel.class);
        var increasedByChannel=new EnumMap<Channel,Double>(Channel.class);
        for(var channel:Channel.values()){
            double increased=(attack?local.percent(GearEffectSnapshot.Operator.ATTACK_DAMAGE):0)
                    +(spell?equipped.percent(GearEffectSnapshot.Operator.SPELL_DAMAGE):0)
                    +(channel==Channel.PHYSICAL?equipped.percent(GearEffectSnapshot.Operator.PHYSICAL_DAMAGE_GLOBAL):
                        equipped.percent(GearEffectSnapshot.Operator.ELEMENTAL_DAMAGE)+equipped.percent(INCREASED[index(channel)]));
            var buckets=ModifierBuckets.NONE.withIncreased(increased);
            increasedByChannel.put(channel,increased);
            double amount=values.get(channel)*coefficient*buckets.factor()*(critical?multiplier:1);
            if(amount>0)result.put(channel,amount);
            if(channel!=Channel.PHYSICAL)penetration.put(channel,equipped.percent(PENETRATION[index(channel)]));
        }
        return new Hit(itemId,equipped.revision(),rootId,result,penetration,increasedByChannel,
                critical,multiplier,equipped,origin);
    }
    private static long stableHash(String text){long hash=0xcbf29ce484222325L;
        for(int i=0;i<text.length();i++){hash^=text.charAt(i);hash*=0x100000001b3L;}return hash;}
    /** Resolve a committed native carrier only when it names one valid frozen instance. */
    public static boolean carrierMatches(GearInstance item,String carrierId){
        if(carrierId==null)return false;
        var binding=BINDINGS.require(item.baseId());
        if(!binding.mapped())return false;
        String carrier=binding.carrier(item.rarity());
        if (carrier.equals(carrierId)) return true;
        // A syntactically valid suffix alone cannot authenticate a frozen item profile.
        try {
            var snapshot = new GearEffectSnapshot(List.of(item));
            if (NativeSwordActionAssets.variantOf(carrierId, carrier))
                return carrierId.equals(NativeSwordActionAssets.variantId(carrier,
                        NativeGearActionProfiles.swordPrimary(snapshot, item.identity())));
            if (NativePrimaryActionAssets.variantOf(carrierId, carrier))
                return carrierId.equals(NativePrimaryActionAssets.variantId(carrier,
                        NativeGearActionProfiles.primary(snapshot, item.identity())));
            return item.baseId().startsWith("gm.daggers_") &&
                    (NativeTwinUtilityAssets.variantOf(carrierId, carrier)
                    || NativeTwinDaggersAssets.variantOf(carrierId, carrier)
                        && item.affixes().stream().anyMatch(a -> a.familyId().equals("WA-141")));
        } catch (IllegalArgumentException invalidProfile) {
            return false;
        }
    }
    public static UUID contributingItem(GearEffectSnapshot equipped,String carrierId){
        if(carrierId==null||carrierId.isBlank())return null;
        UUID found=null;
        for(var item:equipped.items()){
            if(!carrierMatches(item,carrierId))continue;
            if(found!=null)throw new IllegalStateException("AMBIGUOUS_COMMITTED_GEAR_SOURCE");
            found=item.identity();
        }
        return found;
    }
    /** Only direct offensive affixes need vector submission; other gear keeps the accepted scalar skill path. */
    public static boolean needsSkillEnvelope(GearEffectSnapshot equipped){
        for(var item:equipped.items())for(var roll:item.affixes()){
            String id=roll.familyId();
            if(id.matches("WA-00[4-7]|WA-01[013]|WA-0(?:1[7-9]|2[0-9]|3[0-9]|4[0-9]|5[0-9]|6[0-8]|9[4-8])|WA-13[4-9]|WA-14[0-6]"))return true;
        }
        return false;
    }
    /** Existing skill scaling/More/Less and its one crit roll are already in result and buckets. */
    public static Hit skill(GearEffectSnapshot equipped,UUID itemId,String rootId,Channel sourceChannel,
                            com.inigmasgames.hytalerpg.combat.damage.DamageCalculationService.Result result,
                            ModifierBuckets buckets,double coefficient,boolean attack,boolean spell,
                            double skillConvertedFraction,Channel skillDestination,double criticalMultiplier,
                            com.inigmasgames.hytalerpg.execution.math.Vec3 origin){
        return skill(equipped,itemId,rootId,sourceChannel,result,buckets,coefficient,attack,spell,
                skillConvertedFraction,skillDestination,criticalMultiplier,origin,true);
    }
    /** A multi-component authored light hit contributes its local flat element once. */
    public static Hit skill(GearEffectSnapshot equipped,UUID itemId,String rootId,Channel sourceChannel,
                            com.inigmasgames.hytalerpg.combat.damage.DamageCalculationService.Result result,
                            ModifierBuckets buckets,double coefficient,boolean attack,boolean spell,
                            double skillConvertedFraction,Channel skillDestination,double criticalMultiplier,
                            com.inigmasgames.hytalerpg.execution.math.Vec3 origin,boolean includeLocalFlats){
        Objects.requireNonNull(equipped);Objects.requireNonNull(rootId);Objects.requireNonNull(sourceChannel);
        Objects.requireNonNull(result);Objects.requireNonNull(buckets);
        if(!Double.isFinite(coefficient)||coefficient<0||!Double.isFinite(criticalMultiplier)||criticalMultiplier<1
                ||!Double.isFinite(skillConvertedFraction)||skillConvertedFraction<0||skillConvertedFraction>1
                ||skillConvertedFraction>0&&(skillDestination==null||skillDestination==Channel.PHYSICAL))
            throw new IllegalArgumentException("Invalid skill gear input");
        var local=itemId==null?GearEffectSnapshot.EMPTY:equipped.forItem(itemId);
        if(itemId!=null&&local.empty())throw new IllegalArgumentException("Contributing item absent from valid equipment");
        var values=new EnumMap<Channel,Double>(Channel.class);
        for(var channel:Channel.values())values.put(channel,0d);
        double source=result.skillRawDamage();
        double remaining=source;
        if(sourceChannel==Channel.PHYSICAL){
            if(skillConvertedFraction>0){double moved=source*skillConvertedFraction;remaining-=moved;values.merge(skillDestination,moved,Double::sum);}
            for(var channel:Channel.values())if(channel!=Channel.PHYSICAL){
                double moved=Math.min(remaining,source*local.percent(CONVERSION[index(channel)]));
                remaining-=moved;values.merge(channel,moved,Double::sum);
            }
        }
        values.merge(sourceChannel,remaining,Double::sum);
        // A local flat rides the same authored attribute/coefficient and existing More/Less.
        if(includeLocalFlats&&(attack||spell))for(var channel:Channel.values())if(channel!=Channel.PHYSICAL)
            values.merge(channel,local.value(FLAT[index(channel)])*result.attributeMultiplier()*coefficient,Double::sum);
        var amounts=new EnumMap<Channel,Double>(Channel.class);
        var penetration=new EnumMap<Channel,Double>(Channel.class);
        var increases=new EnumMap<Channel,Double>(Channel.class);
        for(var channel:Channel.values()){
            double increased=(attack?local.percent(GearEffectSnapshot.Operator.ATTACK_DAMAGE):0)
                    +(spell?equipped.percent(GearEffectSnapshot.Operator.SPELL_DAMAGE):0)
                    +(channel==Channel.PHYSICAL?equipped.percent(GearEffectSnapshot.Operator.PHYSICAL_DAMAGE_GLOBAL):
                        equipped.percent(GearEffectSnapshot.Operator.ELEMENTAL_DAMAGE)+equipped.percent(INCREASED[index(channel)]));
            increases.put(channel,increased+buckets.increased().stream().mapToDouble(Double::doubleValue).sum()
                    -buckets.reduced().stream().mapToDouble(Double::doubleValue).sum());
            double amount=values.get(channel)*buckets.withIncreased(increased).factor()
                    *(result.critical()?criticalMultiplier:1);
            if(amount>0)amounts.put(channel,amount);
            if(channel!=Channel.PHYSICAL)penetration.put(channel,equipped.percent(PENETRATION[index(channel)]));
        }
        return new Hit(itemId,equipped.revision(),rootId,amounts,penetration,increases,
                result.critical(),criticalMultiplier,equipped,origin);
    }
    public static double resistance(GearEffectSnapshot defense,Channel channel,double nonGear){
        if(channel==Channel.PHYSICAL)return 0;
        return Math.clamp(nonGear+defense.percent(RESISTANCE[index(channel)])+defense.percent("WA-078"),0,.75);
    }
    public static double resisted(double amount,GearEffectSnapshot defense,Channel channel,double nonGear,double penetration){
        if(channel==Channel.PHYSICAL)return amount;
        return amount*(1-Math.max(0,resistance(defense,channel,nonGear)-penetration));
    }
}
