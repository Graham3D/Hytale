package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.progress.ProgressionMath;
import java.util.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.require;

/** Frozen birth contract. Native Health and mutable resources remain in their existing entity store. */
public record EnemyDescriptor(int schemaVersion,long descriptorRevision,String balanceRevision,String nativeBindingRevision,
        UUID worldId,UUID encounterId,long encounterGeneration,UUID logicalActorId,UUID entityId,String spawnCycleId,
        EnemyRewardContext.Origin spawnOrigin,String canonicalRoleId,String nativeRoleId,int combatLevel,DifficultyId difficulty,
        ProgressionMath.Rank encounterRank,EnemyRarity enemyRarity,PackRole packRole,UUID packId,UUID leaderId,
        String sourceValidationId,String lootSourceId,String acquisitionSourceId,String nameToken,String nameSeed,String paletteId,
        List<AffixInstance> ownAffixes,List<AffixInstance> inheritedAffixes,Set<String> nativeImmunityChannels,
        List<ImmunityGrant> selectedImmunityGrants,String controlProfileId,EnemyRewardContext immutableRewardContext,
        String nativeBaselineFingerprint,TemplateBirth templateBirth) {
    public enum PackRole { NONE,MEMBER,LEADER,MINION }
    public enum AffixOrigin { OWN,INHERITED }
    public enum ImmunitySource { NATIVE,AFFINITY,AFFIX,TEMPLATE,SCRIPT }
    public record TemplateBirth(String templateId,int revision,double maxHealthFactor,double directDamageFactor){
        public TemplateBirth{
            id(templateId);require(revision>=1&&Double.isFinite(maxHealthFactor)&&maxHealthFactor>=.25&&maxHealthFactor<=10
                    &&Double.isFinite(directDamageFactor)&&directDamageFactor>=.5&&directDamageFactor<=2,"TEMPLATE_BIRTH_FACTORS");
        }
        public static TemplateBirth freeze(SuperUniqueTemplates.Template template){
            return new TemplateBirth(template.id(),template.revision(),template.maxHealthFactor(),template.directDamageFactor());
        }
    }
    public record ImmunityGrant(String channel,ImmunitySource source,String sourceId,String persistedDrawKey) {
        public ImmunityGrant {EnemyDescriptor.channel(channel);Objects.requireNonNull(source);id(sourceId);id(persistedDrawKey);}
    }
    public record AffixInstance(String affixId,String definitionRevision,Map<String,Double> parameters,
            EnemyAffixRegistry.Selector selector,AffixOrigin origin,UUID sourceLeaderId,
            boolean contributesToRewardCount,String persistedDrawKey) {
        public AffixInstance {
            id(affixId);require(affixId.matches("ME-0(0[1-9]|1[0-9]|2[0-7])"),"UNKNOWN_AFFIX_INSTANCE");
            id(definitionRevision);Objects.requireNonNull(origin);id(persistedDrawKey);
            require((affixId.equals("ME-023"))==(selector!=null),"AFFIX_SELECTOR_MISMATCH");
            require((origin==AffixOrigin.OWN)==contributesToRewardCount,"AFFIX_REWARD_COUNT_MISMATCH");
            require((origin==AffixOrigin.INHERITED)==(sourceLeaderId!=null),"AFFIX_LEADER_SOURCE_MISMATCH");
            var frozen=new TreeMap<String,Double>();parameters.forEach((key,value)->{
                id(key);require(value!=null&&Double.isFinite(value)&&value>=0,"INVALID_RESOLVED_AFFIX_PARAMETER");frozen.put(key,value);
            });parameters=Collections.unmodifiableMap(frozen);
        }
        public double value(String key){var value=parameters.get(key);if(value==null)throw new IllegalArgumentException("MISSING_RESOLVED_PARAMETER:"+key);return value;}
    }
    public EnemyDescriptor {
        require(schemaVersion==1&&descriptorRevision>=1&&encounterGeneration>=0,"ENEMY_DESCRIPTOR_VERSION");
        for(var uuid:List.of(worldId,encounterId,logicalActorId,entityId))Objects.requireNonNull(uuid);
        for(var value:List.of(balanceRevision,nativeBindingRevision,spawnCycleId,canonicalRoleId,nativeRoleId,
                sourceValidationId,lootSourceId,nameToken,nameSeed,controlProfileId))id(value);
        if(acquisitionSourceId!=null)id(acquisitionSourceId);if(paletteId!=null)id(paletteId);
        require(combatLevel>=1&&combatLevel<=99,"ENEMY_COMBAT_LEVEL");
        Objects.requireNonNull(spawnOrigin);Objects.requireNonNull(difficulty);Objects.requireNonNull(encounterRank);
        Objects.requireNonNull(enemyRarity);Objects.requireNonNull(packRole);Objects.requireNonNull(immutableRewardContext);
        require((enemyRarity==EnemyRarity.SUPER_UNIQUE)==(templateBirth!=null),"SUPER_UNIQUE_TEMPLATE_BIRTH_REQUIRED");
        ownAffixes=List.copyOf(ownAffixes);inheritedAffixes=List.copyOf(inheritedAffixes);
        int affixCap=spawnOrigin==EnemyRewardContext.Origin.QA?27:4;
        require(ownAffixes.size()<=affixCap&&inheritedAffixes.size()<=affixCap,"ENEMY_AFFIX_INSTANCE_CAP");
        var ids=new HashSet<String>();for(var affix:ownAffixes){require(affix.origin()==AffixOrigin.OWN&&ids.add(affix.affixId()),"OWN_AFFIX_DUPLICATE_OR_ORIGIN");}
        ids.clear();for(var affix:inheritedAffixes){require(affix.origin()==AffixOrigin.INHERITED&&ids.add(affix.affixId())
                &&Objects.equals(leaderId,affix.sourceLeaderId()),"INHERITED_AFFIX_DUPLICATE_OR_SOURCE");}
        require((packRole==PackRole.NONE)==(packId==null),"PACK_ID_RELATION_MISMATCH");
        require((packRole==PackRole.MINION||packRole==PackRole.LEADER)==(leaderId!=null),"PACK_LEADER_RELATION_MISMATCH");
        require(packRole!=PackRole.LEADER||logicalActorId.equals(leaderId),"PACK_LEADER_IDENTITY_MISMATCH");
        require(packRole!=PackRole.MINION||!logicalActorId.equals(leaderId)&&enemyRarity==EnemyRarity.NORMAL&&ownAffixes.isEmpty(),"INVALID_MINION_DESCRIPTOR");
        require(packRole==PackRole.MINION||inheritedAffixes.isEmpty(),"INHERITANCE_REQUIRES_INITIAL_MINION");
        require(spawnOrigin!=EnemyRewardContext.Origin.INITIAL_PACK_MINION||packRole==PackRole.MINION,"MINION_PROVENANCE_REQUIRES_SEALED_RELATION");
        require(enemyRarity!=EnemyRarity.NORMAL||ownAffixes.isEmpty(),"NORMAL_CANNOT_OWN_AFFIX");
        require(enemyRarity!=EnemyRarity.CHAMPION||packRole==PackRole.MEMBER
                &&(spawnOrigin==EnemyRewardContext.Origin.QA||ownAffixes.size()==1),"CHAMPION_MEMBER_CONTRACT");
        require(enemyRarity!=EnemyRarity.BOSS||encounterRank==ProgressionMath.Rank.BOSS,"BOSS_REQUIRES_AUTHORED_RANK");
        require(immutableRewardContext.rarity()==enemyRarity&&immutableRewardContext.origin()==spawnOrigin
                &&immutableRewardContext.minion()==(packRole==PackRole.MINION)&&immutableRewardContext.ownAffixCount()==ownAffixes.size()
                &&immutableRewardContext.balanceRevision().equals(balanceRevision),"FROZEN_REWARD_DESCRIPTOR_MISMATCH");
        var nativeChannels=new TreeSet<String>();for(var channel:nativeImmunityChannels){channel(channel);nativeChannels.add(channel);}
        nativeImmunityChannels=Collections.unmodifiableSet(nativeChannels);selectedImmunityGrants=List.copyOf(selectedImmunityGrants);
        var channels=new HashSet<>(nativeImmunityChannels);for(var grant:selectedImmunityGrants)require(channels.add(grant.channel()),"DUPLICATE_IMMUNITY_GRANT");
        require(nativeBaselineFingerprint!=null&&nativeBaselineFingerprint.matches("[a-f0-9]{64}"),"NATIVE_BASELINE_FINGERPRINT_REQUIRED");
    }
    public Set<String> activeImmuneChannels(){var result=new TreeSet<>(nativeImmunityChannels);selectedImmunityGrants.forEach(g->result.add(g.channel()));return Collections.unmodifiableSet(result);}
    public Optional<AffixInstance> own(EnemyAffixRegistry.Operator operator){return ownAffixes.stream().filter(a->a.affixId().equals(operator.id())).findFirst();}
    public static AffixInstance ownInstance(EnemyAffixRegistry.Definition definition,EnemyAffixSelection.Choice choice,DifficultyId mode,String drawKey){
        require(definition.id().equals(choice.affixId()),"AFFIX_DEFINITION_MISMATCH");
        return new AffixInstance(definition.id(),definition.revision(),definition.resolvedNumbers(mode),choice.selector(),AffixOrigin.OWN,null,true,drawKey);
    }
    public static Optional<AffixInstance> inheritedInstance(EnemyAffixRegistry.Definition definition,AffixInstance own,UUID leader,String drawKey){
        require(own.origin()==AffixOrigin.OWN&&definition.id().equals(own.affixId())&&definition.revision().equals(own.definitionRevision()),"INHERITANCE_DEFINITION_MISMATCH");
        var values=new TreeMap<String,Double>();
        switch(definition.inheritance()){
            case NONE,PACK_AURA_MEMBERSHIP_ONLY->{return Optional.empty();}
            case MOVEMENT_ONLY->values.put("movementIncrease",own.value("movementIncrease")*definition.inherited("parameterScale"));
            case PHYSICAL_INCREASE->values.put("physicalIncrease",own.value("physicalIncrease")*definition.inherited("parameterScale"));
            case EXTRA_ELEMENTAL_POWER_ONLY->values.put("extraPowerFraction",own.value("extraPowerFraction")*definition.inherited("parameterScale"));
            case POISON_PACKAGE->{
                values.put("statusChance",definition.inherited("statusChance"));values.put("poisonPowerFraction",own.value("poisonPowerFraction")*definition.inherited("potencyScale"));
                values.put("opportunityLockMs",definition.inherited("opportunityLockMs"));values.put("stacksPerAcceptedPackage",own.value("stacksPerAcceptedPackage"));
            }
            case EMPOWERED_MINION_SNAPSHOT->values.putAll(own.parameters());
        }
        return Optional.of(new AffixInstance(own.affixId(),own.definitionRevision(),values,null,AffixOrigin.INHERITED,leader,false,drawKey));
    }
    private static void channel(String value){require(EnemyAffixRegistry.CHANNELS.contains(value),"NONCANONICAL_IMMUNITY_CHANNEL");}
    private static void id(String value){require(value!=null&&!value.isBlank()&&value.length()<=512&&value.chars().noneMatch(Character::isISOControl),"INVALID_DESCRIPTOR_ID");}
}
