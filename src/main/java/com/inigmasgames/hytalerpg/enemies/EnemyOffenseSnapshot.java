package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.combat.damage.*;
import com.inigmasgames.hytalerpg.gear.GearRandom;
import java.util.*;

/** One accepted authored strike, frozen before victims/defenses. Native delivery and crit remain their owners. */
public record EnemyOffenseSnapshot(WeaponDamageExecution.Identity identity,long generation,String nativeBindingRevision,
        String channelMappingRevision,String balanceRevision,double procCoefficient,Map<String,Double> sourceVector,
        double sourcePower,Map<String,Double> affixedVector,String spectralChannel,double directWeakening) {
    public EnemyOffenseSnapshot {
        Objects.requireNonNull(identity);
        if(generation<0||nativeBindingRevision==null||nativeBindingRevision.isBlank()||channelMappingRevision==null||channelMappingRevision.isBlank()
                ||balanceRevision==null||balanceRevision.isBlank())throw new IllegalArgumentException("OFFENSE_SNAPSHOT_IDENTITY");
        fraction(procCoefficient);fraction(directWeakening);sourceVector=vector(sourceVector);affixedVector=vector(affixedVector);
        double sum=sourceVector.values().stream().mapToDouble(Double::doubleValue).sum();
        if(!Double.isFinite(sourcePower)||sourcePower<=0||Double.compare(sum,sourcePower)!=0)throw new IllegalArgumentException("OFFENSE_SOURCE_POWER_MISMATCH");
        if(spectralChannel!=null&&!EnemyAffixRegistry.CHANNELS.subList(1,7).contains(spectralChannel))throw new IllegalArgumentException("SPECTRAL_CHANNEL");
    }
    /** nativeResolved is the calculator's real complete vector, never asset base values or victim Damage.amount. */
    public static EnemyOffenseSnapshot freeze(EnemyDescriptor descriptor,WeaponDamageExecution.Identity identity,
            Map<String,Double> nativeResolved,String authoredProducer,double difficultyFactor,double existingNativeSourceFactor,
            double procCoefficient,EnemyAffixSnapshot providers,double directWeakening,String frozenEncounterSeed){
        if(!identity.worldId().equals(descriptor.worldId())||!identity.actorId().equals(descriptor.logicalActorId()))
            throw new IllegalArgumentException("OFFENSE_ACTOR_MISMATCH");
        for(double factor:new double[]{difficultyFactor,existingNativeSourceFactor,providers.rarityDirectFactor()})
            if(!Double.isFinite(factor)||factor<=0)throw new IllegalArgumentException("OFFENSE_SOURCE_FACTOR");
        fraction(directWeakening);var mappings=DamageChannels.canonical();
        var source=new TreeMap<String,Double>();var channels=new HashMap<String,DamageChannels.Channel>();
        for(var entry:vector(nativeResolved).entrySet()){
            var mapping=mappings.resolve(entry.getKey(),authoredProducer);
            if(mapping.state()!=DamageChannels.State.RESOLVED)throw new IllegalArgumentException("UNSUPPORTED_ENEMY_ACTION_CHANNEL:"+entry.getKey()+":"+authoredProducer);
            String cause=mappings.nativeCause(entry.getKey(),authoredProducer);
            double amount=entry.getValue()*difficultyFactor*providers.rarityDirectFactor()*existingNativeSourceFactor;
            source.merge(cause,amount,Double::sum);channels.put(cause,mapping.channel());
        }
        source=new TreeMap<>(vector(source));double power=source.values().stream().mapToDouble(Double::doubleValue).sum();
        if(power<=0)throw new IllegalArgumentException("NO_POSITIVE_NATIVE_DIRECT_POWER");
        var result=new TreeMap<>(source);String spectral=null;
        var extra=new TreeMap<>(providers.extraPowerFractions());
        if(providers.spectralFraction()>0){
            String seed=new com.google.gson.Gson().toJson(List.of(frozenEncounterSeed,descriptor.balanceRevision(),descriptor.encounterGeneration(),
                    descriptor.logicalActorId(),identity.rootId(),identity.executionId(),identity.authoredTickId()));
            spectral=EnemyAffixRegistry.CHANNELS.subList(1,7).get(new GearRandom(seed).stream("me.spectral/strike").nextInt(6));
            extra.merge(spectral,providers.spectralFraction(),Double::sum);
        }
        for(var entry:extra.entrySet()){
            var channel=DamageChannels.Channel.valueOf(entry.getKey());String cause=nativeCause(channel);
            result.merge(cause,power*entry.getValue(),Double::sum);channels.put(cause,channel);
        }
        for(var cause:List.copyOf(result.keySet())){
            var modifiers=providers.directModifiers(channels.get(cause)==DamageChannels.Channel.PHYSICAL);
            // Cursed's strongest source reduction remains direct-only; it never changes the frozen B used by Poison.
            var less=new ModifierBuckets(modifiers.increased(),modifiers.reduced(),modifiers.more(),List.of(directWeakening));
            result.put(cause,result.get(cause)*less.factor());
        }
        return new EnemyOffenseSnapshot(identity,descriptor.encounterGeneration(),descriptor.nativeBindingRevision(),mappings.revision(),
                descriptor.balanceRevision(),procCoefficient,source,power,result,spectral,directWeakening);
    }
    public static String nativeCause(DamageChannels.Channel channel){return switch(channel){
        case PHYSICAL->"Physical";case WIND->"Wind";case WATER->"Water";case FIRE->"Fire";case EARTH->"Earth";case LIGHTNING->"Lightning";case VOID->"RPG_Void";
    };}
    private static Map<String,Double> vector(Map<String,Double> input){
        if(input==null||input.isEmpty()||input.size()>WeaponDamageExecution.MAX_COMPONENTS+1)throw new IllegalArgumentException("ENEMY_VECTOR_BUDGET");
        var result=new TreeMap<String,Double>();double total=0;
        for(var entry:input.entrySet()){
            String cause=entry.getKey();Double amount=entry.getValue();
            if(cause==null||cause.isBlank()||amount==null||!Double.isFinite(amount)||amount<0||amount>Float.MAX_VALUE)throw new IllegalArgumentException("INVALID_ENEMY_VECTOR_COMPONENT");
            result.put(cause,amount);total+=amount;
        }
        if(!Double.isFinite(total)||total>Float.MAX_VALUE)throw new IllegalArgumentException("ENEMY_VECTOR_OVERFLOW");
        return Collections.unmodifiableMap(result);
    }
    private static void fraction(double value){if(!Double.isFinite(value)||value<0||value>1)throw new IllegalArgumentException("OFFENSE_FRACTION");}
}
