package com.inigmasgames.hytalerpg.progress;

import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.SkillDefinition;
import java.util.*;
import java.util.function.DoubleSupplier;

/** Explicit combat identities/aliases, not fuzzy names or difficulty labels. No shipped source is connected-verified yet. */
public final class LearningSources {
    public enum AbilityKind { SKILL, FIRST_COPY_PASSIVE }
    /** Accepted authored source assignment; shipped bindings remain empty until source acceptance. */
    public record Assignment(AbilityKind kind,String ability,String validationState,String provenance,ProgressionMath.Rank minimumRank){
        public Assignment{Objects.requireNonNull(kind);AcquisitionProgress.id(ability);Objects.requireNonNull(validationState);
            Objects.requireNonNull(minimumRank,"ACQUISITION_SOURCE_RANK_REQUIRED");
            if(provenance==null||provenance.isBlank())throw new IllegalArgumentException("ACQUISITION_PROVENANCE_REQUIRED");}
        public boolean accepted(){return validationState.equals("VERIFIED_CONNECTED");}
    }
    public record Binding(String combatIdentity,String source,ProgressionMath.AcquisitionRarity rarity,Assignment assignment){
        public Binding(String combatIdentity,String source,ProgressionMath.AcquisitionRarity rarity){this(combatIdentity,source,rarity,null);}
        public Binding {AcquisitionProgress.id(combatIdentity);AcquisitionProgress.id(source);Objects.requireNonNull(rarity);}
    }
    public record Opportunity(String skill,String source,ProgressionMath.AcquisitionRarity rarity,double effectiveWisdom,
            com.inigmasgames.hytalerpg.enemies.EnemyRewardContext enemyRewards,AbilityKind kind){
        public Opportunity(String skill,String source,ProgressionMath.AcquisitionRarity rarity,double effectiveWisdom,
                           com.inigmasgames.hytalerpg.enemies.EnemyRewardContext enemyRewards){this(skill,source,rarity,effectiveWisdom,enemyRewards,null);}
        public Opportunity(String skill,String source,ProgressionMath.AcquisitionRarity rarity,double effectiveWisdom){this(skill,source,rarity,effectiveWisdom,null);}
        public Opportunity {AcquisitionProgress.id(skill);AcquisitionProgress.id(source);ProgressionMath.learnChance(rarity,effectiveWisdom);}
        public Opportunity withEnemyRewards(com.inigmasgames.hytalerpg.enemies.EnemyRewardContext context){
            return new Opportunity(skill,source,rarity,effectiveWisdom,context,kind);
        }
        public AbilityKind abilityKind(){return kind==null?AbilityKind.SKILL:kind;}
        public double chance(){double base=ProgressionMath.learnChance(rarity,effectiveWisdom);
            return enemyRewards==null?base:enemyRewards.learningChance(base);}
        public EarnedReward decide(EarnedReward defeat,RewardCheckpoint before,DoubleSupplier random){
            Objects.requireNonNull(before.acquisition());
            if(enemyRewards!=null&&!enemyRewards.economic())return defeat;
            if(chance()<=0)return defeat;
            if(abilityKind()==AbilityKind.SKILL?before.acquisition().learnedSkills().contains(skill):before.acquisition().ownedPassives().containsKey(skill))return defeat; // Zero-copy entries retain acquisition provenance.
            int failures=before.acquisition().progress().pity().getOrDefault(source,0);
            boolean learned=failures>=ProgressionMath.pityFailures(rarity);
            if(!learned){double roll=random.getAsDouble();if(!Double.isFinite(roll)||roll<0||roll>=1)throw new IllegalArgumentException("INVALID_LEARNING_ROLL");
                learned=roll<chance();}
            if(defeat.progression()!=null)throw new IllegalArgumentException("DEFEAT_ALREADY_HAS_PROGRESSION");
            return new EarnedReward(defeat.eventId(),defeat.characterXp(),defeat.insight(),defeat.mastery(),defeat.reason(),defeat.rootCastId(),defeat.skillInstanceId(),defeat.correlationId(),
                    new ProgressionDelta(abilityKind()==AbilityKind.SKILL?(learned?ProgressionDelta.Kind.LEARNING_SUCCESS:ProgressionDelta.Kind.LEARNING_FAILURE):
                            (learned?ProgressionDelta.Kind.PASSIVE_LEARNING_SUCCESS:ProgressionDelta.Kind.PASSIVE_LEARNING_FAILURE),skill,source,0),defeat.milestone(),defeat.goldPot());
        }
        /** Only v2 changes RNG ownership. DeathPlan already freezes Wisdom/source/context before the player transaction. */
        public EarnedReward decide(EarnedReward defeat,RewardCheckpoint before,UUID player,DoubleSupplier legacyRandom){
            if(enemyRewards==null||enemyRewards.v2()==null)return decide(defeat,before,legacyRandom);
            String seed=RewardIntent.digest(defeat.eventId()+"\n"+player+"\n"+abilityKind()+"\n"+skill+"\n"+source+"\n"+enemyRewards.v2().policyDigest());
            return decide(defeat,before,()->new com.inigmasgames.hytalerpg.enemies.Me12Random(com.inigmasgames.hytalerpg.enemies.Me12Random.LEARN,seed)
                    .uniformBelow(java.math.BigInteger.ONE.shiftLeft(53)).doubleValue()/0x1.0p53);
        }
    }
    private final Map<String,List<SkillDefinition>> signatures;
    private final Map<String,Binding> bindings;
    private final Map<String,Integer> assignmentTiers;
    private record Document(int schemaVersion,List<Binding> bindings,String reason){}
    public static LearningSources load(RpgCatalog catalog){
        try(var stream=LearningSources.class.getResourceAsStream("/rpg/progression/learning-sources.json")){
            if(stream==null)throw new IllegalStateException("MISSING_LEARNING_SOURCES");
            var document=new com.google.gson.Gson().fromJson(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8),Document.class);
            if(document.schemaVersion()!=1||document.reason()==null||document.reason().isBlank())throw new IllegalArgumentException("INVALID_LEARNING_SOURCE_DOCUMENT");
            return new LearningSources(catalog,document.bindings());
        }catch(java.io.IOException error){throw new IllegalStateException("LEARNING_SOURCES_UNREADABLE",error);}
    }
    public LearningSources(RpgCatalog catalog,List<Binding> authoredBindings){
        var sources=new TreeMap<String,List<SkillDefinition>>();
        for(var skill:catalog.skills()){
            String source=skill.sourceAcquisition().signatureEnemyId();
            if(source.startsWith("UNASSIGNED"))continue;
            sources.computeIfAbsent(sourceKey(source),ignored->new ArrayList<>()).add(skill);
        }
        for(var group:sources.values())
            if(group.stream().filter(skill->"VERIFIED_CONNECTED".equals(skill.sourceAcquisition().validationState())).count()>1)
                throw new IllegalArgumentException("SOURCE_HAS_MULTIPLE_VERIFIED_SIGNATURES");
        if(authoredBindings.size()>2048)throw new IllegalArgumentException("LEARNING_BINDING_BUDGET");
        var identities=new TreeMap<String,Binding>();var rarities=new HashMap<String,ProgressionMath.AcquisitionRarity>();
        var assignments=new HashMap<String,Assignment>();var tiers=new HashMap<String,Integer>();
        for(var binding:authoredBindings){
            var assignment=binding.assignment();
            if(assignment==null){if(!sources.containsKey(binding.source()))throw new IllegalArgumentException("UNKNOWN_SIGNATURE_SOURCE");}
            else {
                int abilityTier;
                if(assignment.kind()==AbilityKind.SKILL){
                    var skill=catalog.skill(new com.inigmasgames.hytalerpg.domain.SkillId(assignment.ability())).orElseThrow(()->new IllegalArgumentException("UNKNOWN_SKILL_ASSIGNMENT"));
                    if(!sourceKey(skill.sourceAcquisition().signatureEnemyId()).equals(binding.source())||assignment.accepted()&&!"VERIFIED_CONNECTED".equals(skill.sourceAcquisition().validationState()))throw new IllegalArgumentException("SKILL_ASSIGNMENT_SOURCE_MISMATCH");
                    abilityTier=tier(skill.tier());
                }else{
                    var passive=catalog.passive(new com.inigmasgames.hytalerpg.domain.PassiveId(assignment.ability())).orElseThrow(()->new IllegalArgumentException("UNKNOWN_PASSIVE_ASSIGNMENT"));
                    if(sources.getOrDefault(binding.source(),List.of()).stream().anyMatch(s->"VERIFIED_CONNECTED".equals(s.sourceAcquisition().validationState())))throw new IllegalArgumentException("SOURCE_HAS_MULTIPLE_VERIFIED_SIGNATURES");
                    // Passive purchase-cost tiers are not Skill combat tiers. The source owner must author this gate explicitly.
                    abilityTier=assignment.minimumRank().signatureTierCeiling;
                }
                abilityTier=Math.max(abilityTier,assignment.minimumRank().signatureTierCeiling);
                var prior=assignments.putIfAbsent(binding.source(),assignment);
                if(prior!=null&&!prior.equals(assignment))throw new IllegalArgumentException("ALIASES_MUST_SHARE_CANONICAL_ABILITY");
                tiers.put(binding.source(),abilityTier);
            }
            if(identities.putIfAbsent(binding.combatIdentity(),binding)!=null)throw new IllegalArgumentException("DUPLICATE_COMBAT_IDENTITY");
            var old=rarities.putIfAbsent(binding.source(),binding.rarity());
            if(old!=null&&old!=binding.rarity())throw new IllegalArgumentException("ALIASES_MUST_SHARE_ACQUISITION_RARITY");
        }
        var frozen=new TreeMap<String,List<SkillDefinition>>();
        sources.forEach((source,skills)->frozen.put(source,List.copyOf(skills)));
        for(var binding:authoredBindings)if(assignments.containsKey(binding.source())&&!Objects.equals(assignments.get(binding.source()),binding.assignment()))throw new IllegalArgumentException("ALIASES_MUST_SHARE_CANONICAL_ABILITY");
        signatures=Map.copyOf(frozen);bindings=Map.copyOf(identities);assignmentTiers=Map.copyOf(tiers);
    }
    public Optional<Opportunity> resolve(String combatIdentity,ProgressionMath.Rank rank,double effectiveWisdom){
        var binding=bindings.get(combatIdentity);if(binding==null)return Optional.empty();
        if(binding.assignment()!=null){var assignment=binding.assignment();
            if(!assignment.accepted()||assignmentTiers.get(binding.source())>rank.signatureTierCeiling)return Optional.empty();
            return Optional.of(new Opportunity(assignment.ability(),binding.source(),binding.rarity(),effectiveWisdom,null,assignment.kind()));
        }
        var skill=signatures.get(binding.source()).stream()
                .filter(candidate->"VERIFIED_CONNECTED".equals(candidate.sourceAcquisition().validationState()))
                .findFirst().orElse(null);
        if(skill==null||tier(skill.tier())>rank.signatureTierCeiling)return Optional.empty();
        return Optional.of(new Opportunity(skill.id().value(),binding.source(),binding.rarity(),effectiveWisdom));
    }
    public int assignedSources(){return signatures.size();}
    public int verifiedBindings(){return (int)bindings.values().stream().filter(b->resolve(b.combatIdentity(),ProgressionMath.Rank.BOSS,0).isPresent()).count();}
    public boolean accepts(AbilityKind kind,String ability,String source){return bindings.values().stream()
            .filter(b->b.source().equals(source)).map(b->resolve(b.combatIdentity(),ProgressionMath.Rank.BOSS,0))
            .flatMap(Optional::stream).anyMatch(o->o.abilityKind()==kind&&o.skill().equals(ability));}
    public static String sourceKey(String canonicalName){
        if(canonicalName==null||canonicalName.startsWith("UNASSIGNED"))throw new IllegalArgumentException("SOURCE_UNASSIGNED");
        String key=canonicalName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","_").replaceAll("^_|_$","");
        AcquisitionProgress.id(key);return key;
    }
    private static int tier(String tier){return switch(tier){case "I"->1;case "II"->2;case "III"->3;case "IV"->4;case "V"->5;default->throw new IllegalArgumentException("UNKNOWN_SIGNATURE_TIER");};}
}
