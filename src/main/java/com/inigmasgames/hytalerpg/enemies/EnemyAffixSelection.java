package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.gear.GearRandom;
import java.util.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.*;

/** Birth-only legal-set enumeration and exact integer weighting (ME section 12.1). */
public final class EnemyAffixSelection {
    public record Binding(String revision, Set<Capability> capabilities, double usableDefense,
                          boolean improvesElementalResistance, boolean nativeStunStaggerImmune,
                          boolean nativeSlowImmune, int initialMinions,boolean distanceDisplacementDelivery,
                          Set<String> supportedAffixIds) {
        private static final Set<String> ALL_AFFIX_IDS=Arrays.stream(Operator.values())
                .map(Operator::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
        public Binding {
            Objects.requireNonNull(revision); capabilities=Set.copyOf(capabilities);
            supportedAffixIds=Set.copyOf(supportedAffixIds);
            require(!revision.isBlank()&&Double.isFinite(usableDefense)&&usableDefense>=0&&initialMinions>=0&&initialMinions<=7,"INVALID_AFFIX_BINDING");
            require(supportedAffixIds.stream().allMatch(id->id.matches("ME-0(0[1-9]|1[0-9]|2[0-7])")),"UNKNOWN_SUPPORTED_AFFIX");
        }
        public Binding(String revision, Set<Capability> capabilities, double usableDefense,
                       boolean improvesElementalResistance, boolean nativeStunStaggerImmune,
                       boolean nativeSlowImmune, int initialMinions,boolean distanceDisplacementDelivery) {
            this(revision,capabilities,usableDefense,improvesElementalResistance,nativeStunStaggerImmune,
                    nativeSlowImmune,initialMinions,distanceDisplacementDelivery,ALL_AFFIX_IDS);
        }
    }
    public record Choice(String affixId, Selector selector) {
        public Choice { Objects.requireNonNull(affixId);
            require(affixId.matches("ME-0(0[1-9]|1[0-9]|2[0-7])"),"UNKNOWN_AFFIX_CHOICE");
            require((affixId.equals(Operator.AURA_ENCHANTED.id()))==(selector!=null),"AURA_SELECTOR_REQUIRED_ONLY_FOR_AURA"); }
        public static Choice of(Operator op){return new Choice(op.id(),null);}
        String key(){return affixId+(selector==null?"":"/"+selector);}
    }
    public record Request(Binding binding, DifficultyId difficulty, EnemyRarity rarity, int count,
                          List<Choice> fixed, boolean randomActor) {
        public Request {
            Objects.requireNonNull(binding);Objects.requireNonNull(difficulty);Objects.requireNonNull(rarity);
            fixed=List.copyOf(fixed);require(count>=0&&count<=4&&fixed.size()<=count,"AFFIX_COUNT");
            if(rarity==EnemyRarity.NORMAL)require(count==0,"NORMAL_OWN_AFFIXES");
            if(rarity==EnemyRarity.CHAMPION)require(count==1,"CHAMPION_OWN_AFFIXES");
        }
    }
    private record Key(Binding binding,DifficultyId difficulty,EnemyRarity rarity){}
    private final EnemyAffixRegistry registry;
    private final Map<Key,List<Choice>> candidates=new LinkedHashMap<>();
    record WeightedSet(List<Choice> choices,long weight){WeightedSet{choices=List.copyOf(choices);}}
    private record Distribution(List<WeightedSet> sets,long total){}
    private final Map<Request,Distribution> distributions=new LinkedHashMap<>(16,.75f,true);
    private int cachedSets;
    public EnemyAffixSelection(EnemyAffixRegistry registry){this.registry=Objects.requireNonNull(registry);}

    public Optional<List<Choice>> select(Request request,String birthSeed) {
        if(!legal(request,request.fixed()))throw new IllegalArgumentException("INVALID_FIXED_AFFIX_SET");
        var distribution=distribution(request);
        if(distribution.sets.isEmpty())return Optional.empty();
        var random=new GearRandom("master-enemies-v1.1/"+registry.revision()+"/"+birthSeed);
        long draw=random.stream("me.affix-set").nextLong(distribution.total);
        for(var candidate:distribution.sets){if(draw<candidate.weight)return Optional.of(candidate.choices);draw-=candidate.weight;}
        throw new IllegalStateException("AFFIX_WEIGHT_DRAW_OUT_OF_RANGE");
    }
    synchronized List<WeightedSet> weightedSets(Request request){return distribution(request).sets;}
    private synchronized Distribution distribution(Request request){
        var cached=distributions.get(request);if(cached!=null)return cached;
        if(!legal(request,request.fixed()))throw new IllegalArgumentException("INVALID_FIXED_AFFIX_SET");
        var sets=new ArrayList<WeightedSet>();
        enumerate(request,new ArrayList<>(request.fixed()),pool(request),0,1,sets);
        long total=0;for(var set:sets)total=Math.addExact(total,set.weight);
        var result=new Distribution(List.copyOf(sets),total);
        while(!distributions.isEmpty()&&(distributions.size()>=128||cachedSets+sets.size()>200000)){
            var key=distributions.keySet().iterator().next();cachedSets-=distributions.remove(key).sets.size();
        }
        distributions.put(request,result);cachedSets+=sets.size();return result;
    }
    private void enumerate(Request request,List<Choice> selected,List<Choice> pool,int start,long weight,List<WeightedSet> sets){
        if(selected.size()==request.count()){sets.add(new WeightedSet(selected,weight));return;}
        for(int index=start;index<pool.size();index++){
            var choice=pool.get(index);
            if(selected.stream().anyMatch(s->s.affixId.equals(choice.affixId)))continue;
            var test=new ArrayList<>(selected);test.add(choice);if(!legal(request,test))continue;
            var definition=registry.require(choice.affixId);
            long memberWeight=definition.weight(request.difficulty());
            if(choice.selector!=null)memberWeight=Math.multiplyExact(memberWeight,(long)definition.selectorWeight(choice.selector));
            if(memberWeight<=0)continue;
            selected.add(choice);enumerate(request,selected,pool,index+1,Math.multiplyExact(weight,memberWeight),sets);selected.removeLast();
        }
    }
    private synchronized List<Choice> pool(Request request){
        var key=new Key(request.binding(),request.difficulty(),request.rarity());
        var cached=candidates.get(key);if(cached!=null)return cached;
        var choices=new ArrayList<Choice>();
        for(var d:registry.definitions())if(d.weight(request.difficulty())>0) {
            if(d.operator()==Operator.AURA_ENCHANTED) {
                for(var selector:Selector.values()){var choice=new Choice(d.id(),selector);if(eligible(request,choice)&&d.selectorWeight(selector)>0)choices.add(choice);}
            } else {var choice=new Choice(d.id(),null);if(eligible(request,choice))choices.add(choice);}
        }
        choices.sort(Comparator.comparing(Choice::key));
        if(candidates.size()>=512)candidates.remove(candidates.keySet().iterator().next());
        var result=List.copyOf(choices);candidates.put(key,result);return result;
    }
    public boolean legal(Request request,List<Choice> choices){
        if(choices.size()>4)return false;
        var ids=EnumSet.noneOf(Operator.class);var groups=new EnumMap<Group,Integer>(Group.class);
        for(var choice:choices){
            if(!eligible(request,choice))return false;
            var d=registry.require(choice.affixId);if(!ids.add(d.operator()))return false;
            for(var group:semanticGroups(d.operator(),choice.selector))groups.merge(group,1,Integer::sum);
        }
        for(var group:List.of(Group.ELEMENTAL,Group.LEADER,Group.SURVIVAL,Group.CONTROL))if(groups.getOrDefault(group,0)>1)return false;
        if(groups.getOrDefault(Group.OFFENSE,0)>2)return false;
        if(request.randomActor&&groups.getOrDefault(Group.DENIAL,0)>1)return false;
        if(ids.contains(Operator.FRENZIED)&&ids.contains(Operator.AVENGER))return false;
        return !ids.contains(Operator.REFLECTIVE)||(!ids.contains(Operator.VAMPIRIC)&&!ids.contains(Operator.PACKBOUND));
    }
    public boolean eligible(Request request,Choice choice){
        return registry.require(choice.affixId).rarities().contains(request.rarity())
                &&eligibleCompatible(request.binding(),request.rarity(),choice);
    }
    /** Explicit operator selection bypasses production rarity/group/count rules, never native capability checks. */
    public boolean eligibleQa(Binding binding,EnemyRarity rarity,Choice choice){
        if(rarity!=EnemyRarity.CHAMPION&&rarity!=EnemyRarity.UNIQUE&&rarity!=EnemyRarity.SUPER_UNIQUE)
            throw new IllegalArgumentException("QA_RARITY");
        return eligibleCompatible(binding,rarity,choice);
    }
    private boolean eligibleCompatible(Binding binding,EnemyRarity rarity,Choice choice){
        var d=registry.require(choice.affixId);var op=d.operator();
        if(!binding.capabilities().containsAll(d.capabilities())
                ||!binding.supportedAffixIds().contains(choice.affixId()))return false;
        // These safety predicates cannot be disabled by editing definition group labels.
        if(semanticGroups(op,choice.selector).contains(Group.LEADER)&&
                (rarity==EnemyRarity.CHAMPION||!binding.capabilities().containsAll(Set.of(Capability.PACK_LEADER,Capability.MINION_ROSTER))||binding.initialMinions()==0))return false;
        // Owner correction: Stone Skin supplies additive rating; DEFENSE_STAT means a supported D01 path, including zero baseline.
        if(op==Operator.EXTRA_STRONG&&!binding.capabilities().contains(Capability.PHYSICAL_DIRECT_HIT))return false;
        if(op==Operator.MAGIC_RESISTANT&&!binding.improvesElementalResistance())return false;
        if(op==Operator.UNWAVERING&&binding.nativeStunStaggerImmune())return false;
        if(op==Operator.UNSTOPPABLE&&binding.nativeSlowImmune())return false;
        if(op==Operator.PACKBOUND&&(binding.initialMinions()<2||binding.initialMinions()>5))return false;
        if(choice.selector==Selector.MIGHT&&!binding.capabilities().contains(Capability.PHYSICAL_DIRECT_HIT))return false;
        if(choice.selector==Selector.WARD&&!binding.improvesElementalResistance())return false;
        return choice.selector!=Selector.HASTE||binding.capabilities().contains(Capability.MOBILE);
    }
    public static Set<Group> semanticGroups(Operator op,Selector selector){
        var groups=EnumSet.noneOf(Group.class);
        if(op.ordinal()>=4&&op.ordinal()<=11){groups.add(Group.ELEMENTAL);groups.add(Group.OFFENSE);}
        if(Set.of(Operator.AVENGER,Operator.EMPOWERED_MINIONS,Operator.HORDE,Operator.AURA_ENCHANTED,Operator.PACKBOUND).contains(op))groups.add(Group.LEADER);
        if(Set.of(Operator.MAGIC_RESISTANT,Operator.STONE_SKIN,Operator.VAMPIRIC,Operator.BULWARK,Operator.PACKBOUND).contains(op)||selector==Selector.WARD)groups.add(Group.SURVIVAL);
        if(op==Operator.COLD_ENCHANTED||op==Operator.KNOCKBACK)groups.add(Group.CONTROL);
        if(Set.of(Operator.EXTRA_STRONG,Operator.FRENZIED,Operator.AVENGER).contains(op)||selector==Selector.MIGHT)groups.add(Group.OFFENSE);
        if(op==Operator.MANA_BURN||op==Operator.CURSED)groups.add(Group.DENIAL);
        return Collections.unmodifiableSet(groups);
    }
}
