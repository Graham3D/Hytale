package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.gear.GearRandom;
import java.util.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.*;

/** Birth-time grant policy only. Damage mitigation remains with the shared resistance owner. */
public final class EnemyImmunitySelection {
    public enum Reason { GRANTED,FAILED_CHANCE,ALREADY_IMMUNE,CAP_FULL,SAFETY_REJECTED,MODE_INELIGIBLE }
    public record Decision(String sourceId,String channel,Reason reason,String drawKey,Double draw){}
    public record Result(Set<String> preservedNative,List<EnemyDescriptor.ImmunityGrant> selected,
            Map<String,Double> affinityFloors,List<Decision> decisions,String rejection){
        public Result{preservedNative=Collections.unmodifiableSet(new TreeSet<>(preservedNative));selected=List.copyOf(selected);
            affinityFloors=Collections.unmodifiableMap(new TreeMap<>(affinityFloors));decisions=List.copyOf(decisions);}
        public boolean accepted(){return rejection==null;}
        public Set<String> active(){var values=new TreeSet<>(preservedNative);selected.forEach(g->values.add(g.channel()));return Collections.unmodifiableSet(values);}
    }
    private final EnemyBalance balance;private final EnemyAffinityRegistry affinities;
    public EnemyImmunitySelection(EnemyBalance balance,EnemyAffinityRegistry affinities){this.balance=Objects.requireNonNull(balance);this.affinities=Objects.requireNonNull(affinities);}
    public Result select(String canonicalRole,EnemyRarity rarity,DifficultyId mode,boolean nativeBossEncounter,
            Set<String> nativeChannels,Set<String> explicitTemplateChannels,List<EnemyAffixSelection.Choice> ownAffixes,
            boolean randomPromotion,boolean packboundLeader,boolean packboundGuard,boolean authoredAccessibilityProven,String birthSeed){
        require(CHANNELS.containsAll(nativeChannels)&&CHANNELS.containsAll(explicitTemplateChannels),"NONCANONICAL_IMMUNITY_INPUT");
        require(birthSeed!=null&&!birthSeed.isBlank(),"IMMUNITY_BIRTH_SEED_REQUIRED");
        var floors=new TreeMap<String,Double>();var affinity=affinities.forRole(canonicalRole,nativeBossEncounter);
        affinity.ifPresent(a->floors.put(a.channel(),a.floor(mode)));
        if(nativeBossEncounter||rarity==EnemyRarity.BOSS){
            require(explicitTemplateChannels.isEmpty(),"BOSS_IMMUNITY_REMAINS_AUTHORED_OWNER");
            return new Result(nativeChannels,List.of(),Map.of(),List.of(),null);
        }
        int cap=balance.immunityCap(rarity,mode);var active=new TreeSet<>(nativeChannels);
        boolean singleElementOnly=packboundLeader||ownAffixes.stream().anyMatch(a->Set.of("ME-016","ME-027","ME-024").contains(a.affixId()));
        if(randomPromotion&&(nativeChannels.contains("PHYSICAL")||elementCount(active)>cap))return new Result(nativeChannels,List.of(),floors,List.of(),"NATIVE_PROFILE_EXCEEDS_RANDOM_SAFETY_BUDGET");
        if(packboundGuard&&active.contains("PHYSICAL")||packboundLeader&&elementCount(active)>1)
            return new Result(nativeChannels,List.of(),floors,List.of(),"PACKBOUND_NATIVE_IMMUNITY_UNSAFE");
        if(singleElementOnly&&elementCount(active)>1)return new Result(nativeChannels,List.of(),floors,List.of(),"SUSTAIN_NATIVE_IMMUNITY_UNSAFE");
        var grants=new ArrayList<EnemyDescriptor.ImmunityGrant>();var decisions=new ArrayList<Decision>();
        for(String channel:CHANNELS)if(explicitTemplateChannels.contains(channel)&&!active.contains(channel)){
            if(!authoredAccessibilityProven||channel.equals("PHYSICAL")&&(randomPromotion||packboundGuard)
                    ||!channel.equals("PHYSICAL")&&(elementCount(active)>=cap||singleElementOnly&&elementCount(active)>=1))
                return new Result(nativeChannels,grants,floors,decisions,"AUTHORED_IMMUNITY_ACCESSIBILITY_OR_CAP");
            active.add(channel);grants.add(new EnemyDescriptor.ImmunityGrant(channel,EnemyDescriptor.ImmunitySource.TEMPLATE,"authored-template","template/"+channel));
        }
        var random=new GearRandom("master-enemies-v1.1/"+balance.revision()+"/"+affinities.revision()+"/"+birthSeed);
        if(affinity.isPresent()){
            var a=affinity.get();candidate(a.channel(),EnemyDescriptor.ImmunitySource.AFFINITY,a.canonicalRoleId(),"me.affinity-immunity/"+a.canonicalRoleId(),
                    balance.affinityChance(mode),cap,singleElementOnly,active,grants,decisions,random);
        }
        if(rarity==EnemyRarity.UNIQUE||rarity==EnemyRarity.SUPER_UNIQUE){
            for(var choice:ownAffixes.stream().sorted(Comparator.comparing(EnemyAffixSelection.Choice::affixId)).toList()){
                String channel=affixChannel(choice.affixId());if(channel==null)continue;
                candidate(channel,EnemyDescriptor.ImmunitySource.AFFIX,choice.affixId(),"me.affix-immunity/"+choice.affixId(),balance.affixChance(mode),
                        cap,singleElementOnly,active,grants,decisions,random);
            }
        }
        return new Result(nativeChannels,grants,floors,decisions,null);
    }
    private static void candidate(String channel,EnemyDescriptor.ImmunitySource source,String id,String key,double chance,int cap,boolean packbound,
            Set<String> active,List<EnemyDescriptor.ImmunityGrant> grants,List<Decision> decisions,GearRandom random){
        Reason rejection=active.contains(channel)?Reason.ALREADY_IMMUNE:chance<=0?Reason.MODE_INELIGIBLE:elementCount(active)>=cap?Reason.CAP_FULL:
                packbound&&elementCount(active)>=1?Reason.SAFETY_REJECTED:null;
        if(rejection!=null){decisions.add(new Decision(id,channel,rejection,key,null));return;}
        double draw=random.stream(key).nextDouble();boolean granted=draw<chance;
        decisions.add(new Decision(id,channel,granted?Reason.GRANTED:Reason.FAILED_CHANCE,key,draw));
        if(granted){active.add(channel);grants.add(new EnemyDescriptor.ImmunityGrant(channel,source,id,key));}
    }
    private static long elementCount(Set<String> channels){return channels.stream().filter(c->!c.equals("PHYSICAL")).count();}
    public static String affixChannel(String id){return switch(id){
        case "ME-005"->"FIRE";case "ME-006"->"WATER";case "ME-007"->"LIGHTNING";case "ME-008","ME-010"->"EARTH";
        case "ME-009"->"WIND";case "ME-011"->"VOID";default->null;
    };}
}
