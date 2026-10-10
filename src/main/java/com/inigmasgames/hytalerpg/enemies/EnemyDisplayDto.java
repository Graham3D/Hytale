package com.inigmasgames.hytalerpg.enemies;

import java.util.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.require;

/** Read-only projection shared by the target card, nameplate compositor and inspect output. */
public record EnemyDisplayDto(UUID worldId,UUID logicalActorId,long generation,long descriptorRevision,long stateRevision,
        String name,String baseRoleDisplayName,int combatLevel,String rarityLabel,String packRoleLabel,
        List<EnemyTag> ownAffixTags,List<EnemyTag> inheritedEffectTags,List<EnemyTag> activeDefenseTags,List<EnemyTag> stateTags,
        Integer remainingGuardCount,String paletteVariantId,ResourceRef currentHealthRef,ResourceRef shieldRef) {
    public enum SourceKind { AFFIX,INHERITED_AFFIX,DAMAGE_IMMUNITY,STATUS_IMMUNITY,PROTECTION,STATE }
    public enum Style { AFFIX,INHERITED,IMMUNITY,PROTECTION,STATE }
    public record ResourceRef(UUID worldId,UUID logicalActorId,UUID nativeEntityId,long generation,String resource){
        public ResourceRef {Objects.requireNonNull(worldId);Objects.requireNonNull(logicalActorId);Objects.requireNonNull(nativeEntityId);
            require(generation>=0&&Set.of("HEALTH","INTRINSIC_SHIELD").contains(resource),"DISPLAY_RESOURCE_REFERENCE");}
    }
    public record EnemyTag(String stableTagId,SourceKind sourceKind,String sourceId,String labelKey,Map<String,String> labelParameters,
            String fallbackText,Style semanticStyle,int sortPriority,String mechanicDetailKey){
        public EnemyTag {for(String value:List.of(stableTagId,sourceId,labelKey,fallbackText,mechanicDetailKey))text(value,512);
            Objects.requireNonNull(sourceKind);Objects.requireNonNull(semanticStyle);labelParameters=Collections.unmodifiableMap(new TreeMap<>(labelParameters));}
    }
    public EnemyDisplayDto {
        Objects.requireNonNull(worldId);Objects.requireNonNull(logicalActorId);Objects.requireNonNull(currentHealthRef);
        require(generation>=0&&descriptorRevision>=1&&stateRevision>=0&&combatLevel>=1&&combatLevel<=99,"DISPLAY_IDENTITY");
        text(name,256);text(baseRoleDisplayName,256);text(rarityLabel,64);text(packRoleLabel,64);
        ownAffixTags=List.copyOf(ownAffixTags);inheritedEffectTags=List.copyOf(inheritedEffectTags);
        activeDefenseTags=List.copyOf(activeDefenseTags);stateTags=List.copyOf(stateTags);
        require(ownAffixTags.size()<=27&&inheritedEffectTags.size()<=27,"DISPLAY_AFFIX_COUNT");
        require(remainingGuardCount==null||remainingGuardCount>=0&&remainingGuardCount<=5,"DISPLAY_GUARD_COUNT");
        var all=new ArrayList<EnemyTag>();all.addAll(ownAffixTags);all.addAll(inheritedEffectTags);all.addAll(activeDefenseTags);all.addAll(stateTags);
        var ids=new HashSet<String>();for(var tag:all)require(ids.add(tag.stableTagId()),"DUPLICATE_DISPLAY_TAG");
        for(var ref:shieldRef==null?List.of(currentHealthRef):List.of(currentHealthRef,shieldRef))
            require(ref.worldId().equals(worldId)&&ref.logicalActorId().equals(logicalActorId)&&ref.generation()==generation,"FOREIGN_DISPLAY_RESOURCE");
    }
    /** Full tags, with Packbound protection adjacent. No overflow count, tooltip-only immunity or truncation. */
    public List<EnemyTag> orderedTags(){
        var result=new ArrayList<EnemyTag>();var protection=activeDefenseTags.stream().filter(t->t.stableTagId().equals("protection/invulnerable")).findFirst();
        boolean adjacent=false;
        for(var tag:ownAffixTags){result.add(tag);if(tag.sourceId().equals("ME-024")&&protection.isPresent()){result.add(protection.get());adjacent=true;}}
        result.addAll(inheritedEffectTags);for(var tag:activeDefenseTags)if(!adjacent||!tag.stableTagId().equals("protection/invulnerable"))result.add(tag);
        result.addAll(stateTags);return List.copyOf(result);
    }
    public String overheadText(){
        if(rarityLabel.equals("Champion")&&name.startsWith("Champion "))return "Lv "+combatLevel+" · "+name;
        return "Lv "+combatLevel+" · "+(packRoleLabel.equals("Minion")?packRoleLabel:rarityLabel)+" · "+name;
    }
    static String primaryName(EnemyRarity rarity,String savedName,String nativeName){
        Objects.requireNonNull(rarity);text(savedName,40);text(nativeName,256);
        if(rarity!=EnemyRarity.CHAMPION)return savedName;
        String value="Champion "+nativeName;
        text(value,256);
        return value;
    }
    public static EnemyDisplayDto project(EnemyDescriptor descriptor,EnemyAffixRegistry definitions,EnemyAffixSnapshot stats,
            EnemyPackRecord pack,long stateRevision,String resolvedName,String roleDisplayName,
            Set<String> activeNativeStatusImmunities,boolean nativeInvulnerable,double intrinsicShieldRemaining){
        Objects.requireNonNull(stats);Objects.requireNonNull(activeNativeStatusImmunities);
        require(Double.isFinite(intrinsicShieldRemaining)&&intrinsicShieldRemaining>=0,"DISPLAY_SHIELD_VALUE");
        if(descriptor.packId()!=null)require(pack!=null&&pack.packId().equals(descriptor.packId())&&pack.worldId().equals(descriptor.worldId())
                &&pack.generation()==descriptor.encounterGeneration()&&pack.contains(descriptor.logicalActorId()),"DISPLAY_PACK_BINDING");
        require(stats.blocksExternalMutation()==(pack!=null&&pack.blocksExternalMutation(descriptor.logicalActorId())),"DISPLAY_PROTECTION_SNAPSHOT_STALE");
        var own=new ArrayList<EnemyTag>();var inherited=new ArrayList<EnemyTag>();var defenses=new ArrayList<EnemyTag>();var states=new ArrayList<EnemyTag>();
        boolean packbound=descriptor.own(EnemyAffixRegistry.Operator.PACKBOUND).isPresent();
        require(!packbound||pack!=null&&!pack.guardIds().isEmpty()&&descriptor.logicalActorId().equals(pack.leaderId()),"DISPLAY_PACKBOUND_ROSTER_REQUIRED");
        Integer remaining=packbound?pack.livingGuards():null;
        for(var instance:descriptor.ownAffixes())own.add(affix(instance,definitions,stats,pack,own.size(),false));
        for(var instance:descriptor.inheritedAffixes())inherited.add(affix(instance,definitions,stats,pack,100+inherited.size(),true));
        if(descriptor.packRole()==EnemyDescriptor.PackRole.MINION&&stats.auraMember())
            inherited.add(tag("inherited/ME-023",SourceKind.INHERITED_AFFIX,"ME-023",
                    label(stats.auraSelector().name()),Style.INHERITED,100+inherited.size(),
                    Map.of("selectorLabel",label(stats.auraSelector().name()))));
        if(nativeInvulnerable||stats.blocksExternalMutation())defenses.add(tag("protection/invulnerable",SourceKind.PROTECTION,
                packbound&&!pack.packboundReleased()?"ME-024":"NATIVE_OR_ENCOUNTER_PROTECTION","Invulnerable",Style.PROTECTION,200,Map.of()));
        for(String channel:EnemyAffixRegistry.CHANNELS)if(descriptor.activeImmuneChannels().contains(channel))
            defenses.add(tag("damage-immunity/"+channel,SourceKind.DAMAGE_IMMUNITY,channel,label(channel)+" Immune",Style.IMMUNITY,210+defenses.size(),Map.of()));
        var statusImmunities=new TreeSet<>(activeNativeStatusImmunities);
        if(stats.stunStaggerImmune())statusImmunities.add("STUN_STAGGER");
        if(stats.slowImmune())statusImmunities.add("SLOW_MOVEMENT");
        for(String status:statusImmunities){text(status,64);
            String display=switch(status){case "STUN_STAGGER"->"Stun/Stagger Immune";case "SLOW_MOVEMENT"->"Slow Immune";default->label(status)+" Immune";};
            defenses.add(tag("status-immunity/"+status,SourceKind.STATUS_IMMUNITY,status,display,Style.IMMUNITY,230+defenses.size(),Map.of()));
        }
        if(stats.enraged())states.add(tag("state/enraged",SourceKind.STATE,"ME-019","Enraged",Style.STATE,300,Map.of()));
        if(intrinsicShieldRemaining>0)states.add(tag("state/shielded",SourceKind.STATE,"ME-027","Shielded",Style.STATE,310,Map.of()));
        if(pack!=null&&pack.state()==EnemyPackRecord.State.SUSPENDED)states.add(tag("state/recovering",SourceKind.STATE,"PACK_RECOVERY","Recovering",Style.STATE,320,Map.of()));
        var health=new ResourceRef(descriptor.worldId(),descriptor.logicalActorId(),descriptor.entityId(),descriptor.encounterGeneration(),"HEALTH");
        var shield=descriptor.own(EnemyAffixRegistry.Operator.BULWARK).isEmpty()?null:new ResourceRef(descriptor.worldId(),descriptor.logicalActorId(),descriptor.entityId(),descriptor.encounterGeneration(),"INTRINSIC_SHIELD");
        return new EnemyDisplayDto(descriptor.worldId(),descriptor.logicalActorId(),descriptor.encounterGeneration(),descriptor.descriptorRevision(),stateRevision,
                primaryName(descriptor.enemyRarity(),resolvedName,roleDisplayName),roleDisplayName,descriptor.combatLevel(),label(descriptor.enemyRarity().name()),label(descriptor.packRole().name()),
                own,inherited,defenses,states,remaining,descriptor.paletteId(),health,shield);
    }
    private static EnemyTag affix(EnemyDescriptor.AffixInstance instance,EnemyAffixRegistry definitions,EnemyAffixSnapshot stats,EnemyPackRecord pack,int priority,boolean inherited){
        var definition=definitions.require(instance.affixId());require(definition.revision().equals(instance.definitionRevision()),"DISPLAY_DEFINITION_REVISION_MISSING");
        var parameters=new TreeMap<String,String>();
        if(instance.affixId().equals("ME-020"))parameters.put("stacks",Integer.toString(stats.avengerStacks()));
        if(instance.selector()!=null)parameters.put("selectorLabel",label(instance.selector().name()));
        if(instance.affixId().equals("ME-024"))parameters.put("guardLabel",pack.packboundReleased()?"Broken":Integer.toString(pack.livingGuards()));
        String fallback=definition.tagTemplate();for(var entry:parameters.entrySet())fallback=fallback.replace("{"+entry.getKey()+"}",entry.getValue());
        require(!fallback.contains("{")&&!fallback.contains("}"),"UNRESOLVED_DISPLAY_TAG_PARAMETER");
        if(inherited)fallback+=" (Inherited)";
        return tag((inherited?"inherited/":"affix/")+instance.affixId(),inherited?SourceKind.INHERITED_AFFIX:SourceKind.AFFIX,
                instance.affixId(),fallback,inherited?Style.INHERITED:Style.AFFIX,priority,parameters);
    }
    private static EnemyTag tag(String id,SourceKind kind,String source,String text,Style style,int priority,Map<String,String> params){
        return new EnemyTag(id,kind,source,"enemies.tags."+id.replace('/','.'),params,text,style,priority,"enemies.details."+source);
    }
    private static String label(String token){return Arrays.stream(token.toLowerCase(Locale.ROOT).split("_")).map(s->Character.toUpperCase(s.charAt(0))+s.substring(1)).collect(java.util.stream.Collectors.joining(" "));}
    private static void text(String value,int maximum){require(value!=null&&!value.isBlank()&&value.codePointCount(0,value.length())<=maximum&&value.chars().noneMatch(Character::isISOControl),"INVALID_DISPLAY_TEXT");}
}
