package com.inigmasgames.hytalerpg.progress;

import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.SkillDefinition;
import java.util.*;
import java.util.function.DoubleSupplier;

/** Explicit combat identities/aliases, not fuzzy names or difficulty labels. No shipped source is connected-verified yet. */
public final class LearningSources {
    public record Binding(String combatIdentity,String source,ProgressionMath.AcquisitionRarity rarity){
        public Binding {AcquisitionProgress.id(combatIdentity);AcquisitionProgress.id(source);Objects.requireNonNull(rarity);}
    }
    public record Opportunity(String skill,String source,ProgressionMath.AcquisitionRarity rarity,double effectiveWisdom){
        public Opportunity {AcquisitionProgress.id(skill);AcquisitionProgress.id(source);ProgressionMath.learnChance(rarity,effectiveWisdom);}
        public EarnedReward decide(EarnedReward defeat,RewardCheckpoint before,DoubleSupplier random){
            Objects.requireNonNull(before.acquisition());
            if(before.acquisition().learnedSkills().contains(skill))return defeat; // No RNG call or pity change.
            int failures=before.acquisition().progress().pity().getOrDefault(source,0);
            boolean learned=failures>=ProgressionMath.pityFailures(rarity);
            if(!learned){double roll=random.getAsDouble();if(!Double.isFinite(roll)||roll<0||roll>=1)throw new IllegalArgumentException("INVALID_LEARNING_ROLL");
                learned=roll<ProgressionMath.learnChance(rarity,effectiveWisdom);}
            if(defeat.progression()!=null)throw new IllegalArgumentException("DEFEAT_ALREADY_HAS_PROGRESSION");
            return new EarnedReward(defeat.eventId(),defeat.characterXp(),defeat.insight(),defeat.mastery(),defeat.reason(),defeat.rootCastId(),defeat.skillInstanceId(),defeat.correlationId(),
                    new ProgressionDelta(learned?ProgressionDelta.Kind.LEARNING_SUCCESS:ProgressionDelta.Kind.LEARNING_FAILURE,skill,source,0));
        }
    }
    private final Map<String,SkillDefinition> signatures;
    private final Map<String,Binding> bindings;
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
        var sources=new TreeMap<String,SkillDefinition>();
        for(var skill:catalog.skills()){
            String source=skill.sourceAcquisition().signatureEnemyId();
            if(source.startsWith("UNASSIGNED"))continue;
            if(sources.putIfAbsent(sourceKey(source),skill)!=null)throw new IllegalArgumentException("SOURCE_HAS_MULTIPLE_SIGNATURES");
        }
        if(authoredBindings.size()>2048)throw new IllegalArgumentException("LEARNING_BINDING_BUDGET");
        var identities=new TreeMap<String,Binding>();var rarities=new HashMap<String,ProgressionMath.AcquisitionRarity>();
        for(var binding:authoredBindings){
            if(!sources.containsKey(binding.source()))throw new IllegalArgumentException("UNKNOWN_SIGNATURE_SOURCE");
            if(identities.putIfAbsent(binding.combatIdentity(),binding)!=null)throw new IllegalArgumentException("DUPLICATE_COMBAT_IDENTITY");
            var old=rarities.putIfAbsent(binding.source(),binding.rarity());
            if(old!=null&&old!=binding.rarity())throw new IllegalArgumentException("ALIASES_MUST_SHARE_ACQUISITION_RARITY");
        }
        signatures=Map.copyOf(sources);bindings=Map.copyOf(identities);
    }
    public Optional<Opportunity> resolve(String combatIdentity,ProgressionMath.Rank rank,double effectiveWisdom){
        var binding=bindings.get(combatIdentity);if(binding==null)return Optional.empty();
        var skill=signatures.get(binding.source());
        if(!"VERIFIED_CONNECTED".equals(skill.sourceAcquisition().validationState())||tier(skill.tier())>rank.signatureTierCeiling)return Optional.empty();
        return Optional.of(new Opportunity(skill.id().value(),binding.source(),binding.rarity(),effectiveWisdom));
    }
    public int assignedSources(){return signatures.size();}
    public int verifiedBindings(){return (int)bindings.values().stream().filter(b->signatures.get(b.source()).sourceAcquisition().validationState().equals("VERIFIED_CONNECTED")).count();}
    public static String sourceKey(String canonicalName){
        if(canonicalName==null||canonicalName.startsWith("UNASSIGNED"))throw new IllegalArgumentException("SOURCE_UNASSIGNED");
        String key=canonicalName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","_").replaceAll("^_|_$","");
        AcquisitionProgress.id(key);return key;
    }
    private static int tier(String tier){return switch(tier){case "I"->1;case "II"->2;case "III"->3;case "IV"->4;case "V"->5;default->throw new IllegalArgumentException("UNKNOWN_SIGNATURE_TIER");};}
}
