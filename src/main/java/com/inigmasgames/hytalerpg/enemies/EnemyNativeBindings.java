package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.*;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.difficulty.AuthoredEncounterCatalog;
import com.inigmasgames.hytalerpg.difficulty.MonsterResistanceProfile;
import com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyAction;
import com.inigmasgames.hytalerpg.execution.hytale.NativeNpcTiming;
import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import com.inigmasgames.hytalerpg.progress.RewardIntent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Pinned installed-role evidence, separate from the ME card catalog and native asset loader. */
public final class EnemyNativeBindings {
    private static final EnemyRewardRegistry ENCOUNTER_ROLES=EnemyRewardRegistry.load();
    private static final AuthoredEncounterCatalog AUTHORED_ROLES=AuthoredEncounterCatalog.load();
    public record Action(String nativeActionId,List<String> strikeIds,String producer,Map<String,String> channels,
            String recoveryProfileId,String positiveTestId,String negativeTestId,Set<String> supportedAffixIds,
            boolean conditionalNativeLeaves,String nativeVariantOwnerId,Map<String,Integer> contactOccurrences){
        public Action(String nativeActionId,List<String> strikeIds,String producer,Map<String,String> channels,
                String recoveryProfileId,String positiveTestId,String negativeTestId,Set<String> supportedAffixIds){
            this(nativeActionId,strikeIds,producer,channels,recoveryProfileId,positiveTestId,negativeTestId,
                    supportedAffixIds,false,null,Map.of());
        }
        public Action(String nativeActionId,List<String> strikeIds,String producer,Map<String,String> channels,
                String recoveryProfileId,String positiveTestId,String negativeTestId,Set<String> supportedAffixIds,
                boolean conditionalNativeLeaves){
            this(nativeActionId,strikeIds,producer,channels,recoveryProfileId,positiveTestId,negativeTestId,
                    supportedAffixIds,conditionalNativeLeaves,null,Map.of());
        }
        public Action(String nativeActionId,List<String> strikeIds,String producer,Map<String,String> channels,
                String recoveryProfileId,String positiveTestId,String negativeTestId,Set<String> supportedAffixIds,
                boolean conditionalNativeLeaves,String nativeVariantOwnerId){
            this(nativeActionId,strikeIds,producer,channels,recoveryProfileId,positiveTestId,negativeTestId,
                    supportedAffixIds,conditionalNativeLeaves,nativeVariantOwnerId,Map.of());
        }
        public Action{nonblank(nativeActionId);nonblank(producer);nonblank(positiveTestId);nonblank(negativeTestId);
            strikeIds=List.copyOf(strikeIds);if(strikeIds.isEmpty()||strikeIds.size()>32||new HashSet<>(strikeIds).size()!=strikeIds.size())
                throw new IllegalArgumentException("ENEMY_NATIVE_ACTION_STRIKE_KEYS");
            strikeIds.forEach(EnemyNativeBindings::nonblank);
            if(recoveryProfileId!=null)nonblank(recoveryProfileId);
            if(nativeVariantOwnerId!=null)nonblank(nativeVariantOwnerId);
            if(conditionalNativeLeaves&&nativeVariantOwnerId!=null)
                throw new IllegalArgumentException("ENEMY_NATIVE_AMBIGUOUS_CERTIFICATE");
            contactOccurrences=Map.copyOf(contactOccurrences);
            if(!contactOccurrences.isEmpty()&&(!contactOccurrences.keySet().equals(Set.copyOf(strikeIds))
                    ||contactOccurrences.values().stream().anyMatch(count->count==null||count<1||count>16)
                    ||conditionalNativeLeaves||nativeVariantOwnerId!=null))
                throw new IllegalArgumentException("ENEMY_NATIVE_CONTACT_CERTIFICATE");
            channels=Map.copyOf(channels);if(channels.isEmpty())throw new IllegalArgumentException("ENEMY_NATIVE_ACTION_CHANNELS");
            if(supportedAffixIds!=null){supportedAffixIds=Set.copyOf(supportedAffixIds);
                if(supportedAffixIds.isEmpty()||supportedAffixIds.stream().anyMatch(id->!id.matches("ME-0(0[1-9]|1[0-9]|2[0-7])")))
                    throw new IllegalArgumentException("ENEMY_NATIVE_ACTION_AFFIX_SET");}}
    }
    public record Role(String canonicalRoleId,Set<String> nativeRoleIds,Map<DifficultyId,String> profiles,
            Set<EnemyAffixRegistry.Capability> capabilities,String modelAssetId,List<String> textures,
            String controlProfileId,String sourceValidationId,String nativeLootSourceId,String sourceAssetPath,String sourceAssetSha256,
            Set<String> nativeImmunityChannels,EnemyStatusEffects.Source nativeStatusSource,
            boolean nativeStunStaggerImmune,boolean nativeSlowImmune,int maximumBaseEquipmentSlots,
            boolean distanceDisplacementDelivery,boolean productionPromotionEnabled,List<Action> actions,List<String> evidenceFiles){
        public Role{
            nonblank(canonicalRoleId);nonblank(modelAssetId);nonblank(controlProfileId);nonblank(sourceValidationId);nonblank(nativeLootSourceId);
            nonblank(sourceAssetPath);hash(sourceAssetSha256);
            nativeRoleIds=Set.copyOf(nativeRoleIds);profiles=Map.copyOf(profiles);capabilities=Set.copyOf(capabilities);
            textures=List.copyOf(textures);actions=List.copyOf(actions);evidenceFiles=List.copyOf(evidenceFiles);
            nativeImmunityChannels=Set.copyOf(nativeImmunityChannels);Objects.requireNonNull(nativeStatusSource);
            if(!EnemyAffixRegistry.CHANNELS.containsAll(nativeImmunityChannels)
                    ||maximumBaseEquipmentSlots<0||maximumBaseEquipmentSlots>com.inigmasgames.hytalerpg.gear.GearLootService.MAX_BASE_EQUIPMENT_SLOTS_PER_DEFEAT)
                throw new IllegalArgumentException("ENEMY_NATIVE_TRAITS_UNCERTIFIED");
            if(nativeRoleIds.isEmpty()||profiles.isEmpty()||textures.isEmpty()||evidenceFiles.isEmpty()
                    ||actions.isEmpty()==capabilities.contains(EnemyAffixRegistry.Capability.DIRECT_HIT))
                throw new IllegalArgumentException("ENEMY_NATIVE_BINDING_INCOMPLETE");
        }
        /** Re-resolves the actual native Role Replace graph at actor attachment time. */
        public List<NativeEnemyAction.Binding> certify(String revision,NPCEntity npc){
            if(npc==null||!nativeRoleIds.contains(npc.getRoleName()))throw new IllegalStateException("ENEMY_NATIVE_ROLE_UNSUPPORTED");
            var results=new ArrayList<NativeEnemyAction.Binding>();
            for(var action:actions){
                var root=com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction.getAssetMap()
                        .getAsset(action.nativeActionId());
                if(root==null||npc.getRole()==null)throw new IllegalStateException("ENEMY_ACTION_NATIVE_ROOT_MISSING");
                if(action.nativeVariantOwnerId()!=null){
                    var asset=com.hypixel.hytale.server.npc.config.balancing.BalanceAsset.getAssetMap()
                            .getAsset(action.nativeVariantOwnerId());
                    if(!(asset instanceof com.hypixel.hytale.builtin.npccombatactionevaluator.config.CombatBalanceAsset cae))
                        throw new IllegalStateException("ENEMY_NATIVE_VARIANT_OWNER_MISSING");
                    int certified=0;
                    for(var entry:new TreeMap<>(cae.getEvaluatorConfig().getActionSets()).entrySet()){
                        var basic=entry.getValue().getBasicAttacks();
                        if(basic==null||!Arrays.asList(basic.getAttacks()).contains(action.nativeActionId()))continue;
                        var vars=basic.getInteractionVars(com.hypixel.hytale.server.core.entity.InteractionContext.withoutEntity());
                        var variantContext=com.hypixel.hytale.server.core.entity.InteractionContext.withoutEntity();
                        variantContext.setInteractionVarsGetter(ignored->vars);
                        results.add(NativeEnemyAction.Binding.certifyNativeVariables(revision,root,InteractionType.Primary,
                                variantContext,vars,action.strikeIds(),action.producer(),nativeStatusSource));
                        certified++;
                    }
                    if(certified<2||certified>8)throw new IllegalStateException("ENEMY_NATIVE_VARIANT_SET_CHANGED");
                    continue;
                }
                var context=com.hypixel.hytale.server.core.entity.InteractionContext.withoutEntity();
                context.setInteractionVarsGetter(ignored->npc.getRole().getInteractionVars());
                var graph=com.inigmasgames.hytalerpg.execution.hytale.NativeBasicAttackPaths.resolve(context,root,InteractionType.Primary);
                if(graph.damageOccurrences().isEmpty()&&action.strikeIds().size()!=1)
                    throw new IllegalStateException("ENEMY_PROJECTILE_ONLY_SINGLE_STRIKE_CERTIFIED");
                var bound=graph.damageOccurrences().isEmpty()
                        ?NativeEnemyAction.Binding.certifyProjectileSingle(revision,root,InteractionType.Primary,context,
                                action.strikeIds().getFirst(),action.producer(),nativeStatusSource)
                        :action.conditionalNativeLeaves()
                                ?NativeEnemyAction.Binding.certifyConditionalLeaves(revision,root,InteractionType.Primary,
                                        context,action.strikeIds(),action.producer(),nativeStatusSource)
                        :action.strikeIds().size()==1
                                ?NativeEnemyAction.Binding.certifySingle(revision,root,InteractionType.Primary,context,
                                        action.strikeIds().getFirst(),action.producer(),nativeStatusSource)
                                :NativeEnemyAction.Binding.certifyNativeVariables(revision,root,InteractionType.Primary,context,
                                        npc.getRole().getInteractionVars(),action.strikeIds(),action.producer(),nativeStatusSource,
                                        action.contactOccurrences());
                if(!bound.projectileStrikes().isEmpty()&&action.supportedAffixIds()==null)
                    throw new IllegalStateException("ENEMY_PROJECTILE_AFFIX_CAPABILITIES_UNDECLARED");
                if(!bound.projectileStrikes().isEmpty()&&!action.channels().equals(Map.of("Projectile","PHYSICAL")))
                    throw new IllegalStateException("ENEMY_LEGACY_PROJECTILE_CHANNEL_UNCERTIFIED");
                boolean impulse=bound.strikes().stream().anyMatch(strike->strike.leaf().ownsNativeImpulse());
                if(impulse&&distanceDisplacementDelivery&&actions.size()==1)
                    throw new IllegalStateException("ENEMY_NATIVE_DISTANCE_BINDING_CONTRADICTS_IMPULSE");
                results.add(bound);
            }
            if(distanceDisplacementDelivery&&results.stream().allMatch(binding->binding.projectileStrikes().isEmpty()
                    &&binding.strikes().stream().allMatch(strike->strike.leaf().ownsNativeImpulse())))
                throw new IllegalStateException("ENEMY_NATIVE_DISTANCE_BINDING_HAS_NO_ROUTE");
            if(capabilities.contains(EnemyAffixRegistry.Capability.NATIVE_KNOCKBACK)
                    &&results.stream().noneMatch(binding->binding.strikes().stream()
                            .anyMatch(strike->strike.leaf().ownsNativeImpulse())))
                throw new IllegalStateException("ENEMY_NATIVE_KNOCKBACK_UNCERTIFIED");
            return List.copyOf(results);
        }
        public Optional<String> recoveryProfile(String actualRoot){
            return actions.stream().filter(action->action.nativeActionId().equals(actualRoot))
                    .map(Action::recoveryProfileId).filter(Objects::nonNull).findFirst();
        }
        public boolean requiresProjectileReceipt(){
            return actions.stream().anyMatch(action->action.channels().containsKey("Projectile"));
        }
        /** Copies the already authored native classifier result; no new level, role or reward source is inferred. */
        public EnemyBirthPlanner.Candidate baseline(EnemyRewardRegistry.Spawn spawn,UUID encounter,long generation,
                String spawnCycleId,String resolvedName,EnemyBalance balance,String bindingRevision){
            return baseline(spawn,encounter,generation,spawnCycleId,resolvedName,balance,bindingRevision,
                    EnemyRewardContext.Origin.NATURAL);
        }
        public EnemyBirthPlanner.Candidate qaBaseline(EnemyRewardRegistry.Spawn spawn,UUID encounter,long generation,
                String spawnCycleId,String resolvedName,EnemyBalance balance,String bindingRevision){
            return baseline(spawn,encounter,generation,spawnCycleId,resolvedName,balance,bindingRevision,
                    EnemyRewardContext.Origin.QA);
        }
        private EnemyBirthPlanner.Candidate baseline(EnemyRewardRegistry.Spawn spawn,UUID encounter,long generation,
                String spawnCycleId,String resolvedName,EnemyBalance balance,String bindingRevision,
                EnemyRewardContext.Origin origin){
            Objects.requireNonNull(spawn);Objects.requireNonNull(encounter);
            var authored=ENCOUNTER_ROLES.resolveRole(spawn.roleId()).orElse(null);
            if(spawn.combat()==null||!nativeRoleIds.contains(spawn.roleId())||authored==null
                    ||!canonicalRoleId.equals(authored.canonical().roleId())
                    ||!authored.canonical().combatIdentity().equals(spawn.combatIdentity())
                    ||spawn.enemyRewards()!=null||spawn.milestone()!=null
                    ||!("QA_CATALOG_NATIVE_PROFILE".equals(sourceValidationId)
                            &&origin==EnemyRewardContext.Origin.QA)
                            &&!profiles.getOrDefault(spawn.combat().difficulty(),"").equals(spawn.registryProfile())
                    ||origin!=EnemyRewardContext.Origin.QA
                            &&spawn.rank()==com.inigmasgames.hytalerpg.progress.ProgressionMath.Rank.BOSS)
                throw new IllegalStateException("ENEMY_NATIVE_UNAUTHORED_BASELINE");
            nonblank(spawnCycleId);nonblank(resolvedName);nonblank(bindingRevision);
            var nativeImmunities=new HashSet<>(nativeImmunityChannels);
            spawn.combat().resistance().immunities().forEach(channel->nativeImmunities.add(channel.canonical().name()));
            boolean improves=Arrays.stream(MonsterResistanceProfile.Channel.values())
                    .filter(channel->channel==channel.canonical()).anyMatch(channel->
                            !spawn.combat().resistance().immune(channel)
                            &&spawn.combat().resistance().effective(channel)<MonsterResistanceProfile.CAP);
            var seed=RewardIntent.digest("me.actor/"+spawn.world()+"/"+encounter+"/"+spawn.enemy());
            var fingerprint=RewardIntent.digest("me.native-baseline/"+sourceAssetSha256+"/"+spawn.registryProfile()+"/"
                    +spawn.combat().maxHealth()+"/"+spawn.combat().attackBasis()+"/"+spawn.level());
            var descriptor=new EnemyDescriptor(1,1,balance.revision(),bindingRevision,spawn.world(),encounter,generation,
                    spawn.enemy(),spawn.enemy(),spawnCycleId,origin,canonicalRoleId,spawn.roleId(),
                    spawn.level(),spawn.combat().difficulty(),spawn.rank(),EnemyRarity.NORMAL,EnemyDescriptor.PackRole.NONE,
                    null,null,spawn.registryProfile(),nativeLootSourceId,null,resolvedName,seed,null,List.of(),List.of(),
                    nativeImmunities,List.of(),controlProfileId,
                    balance.rewards(EnemyRarity.NORMAL,origin,false,0),fingerprint,null);
            var binding=affixBinding(bindingRevision,improves,0);
            return new EnemyBirthPlanner.Candidate(descriptor,binding,maximumBaseEquipmentSlots);
        }
        /** The same certified action pool feeds birth planning, template validation and the role matrix. */
        public EnemyAffixSelection.Binding affixBinding(String bindingRevision,boolean improvesElementalResistance,
                                                        int initialMinions){
            var supported=new HashSet<String>();for(var op:EnemyAffixRegistry.Operator.values())supported.add(op.id());
            if(actions.isEmpty())supported.retainAll(Set.of("ME-003","ME-004","ME-022","ME-023","ME-024","ME-026","ME-027"));
            for(var action:actions)if(action.supportedAffixIds()!=null)supported.retainAll(action.supportedAffixIds());
            return new EnemyAffixSelection.Binding(bindingRevision,capabilities,0,improvesElementalResistance,
                    nativeStunStaggerImmune,nativeSlowImmune,initialMinions,distanceDisplacementDelivery,supported);
        }
    }
    private final String revision,serverJarSha256,assetsSha256;
    private final Map<String,Role> roles;
    private final Map<String,Role> qaCatalogRoles=new HashMap<>();
    private EnemyNativeBindings(String revision,String serverJarSha256,String assetsSha256,Map<String,Role> roles){
        this.revision=revision;this.serverJarSha256=serverJarSha256;this.assetsSha256=assetsSha256;this.roles=Map.copyOf(roles);
    }
    public String revision(){return revision;}
    public String serverJarSha256(){return serverJarSha256;}
    public String assetsSha256(){return assetsSha256;}
    public Collection<Role> roles(){return roles.values();}
    public Optional<Role> role(String nativeRole){return Optional.ofNullable(roles.get(nativeRole));}
    public Optional<String> productionEligibilityRejection(String nativeRole){
        return productionEligibilityRejection(nativeRole,ENCOUNTER_ROLES,AUTHORED_ROLES);
    }
    /** Static certification only. A specific natural actor must still pass the hostile world-spawn classifier. */
    public Optional<String> productionEligibilityRejection(String nativeRole,EnemyRewardRegistry catalog,
                                                           AuthoredEncounterCatalog authored){
        var resolved=catalog.resolveRole(nativeRole).orElse(null);
        if(resolved==null)return Optional.of("NOT_IN_AUTHORED_ROLE_CATALOG");
        if(nativeRole.startsWith("Template_")||nativeRole.startsWith("Component_"))
            return Optional.of("ABSTRACT_OR_COMPONENT_ROLE");
        if(resolved.canonical().rank()==com.inigmasgames.hytalerpg.progress.ProgressionMath.Rank.BOSS)
            return Optional.of("NATIVE_BOSS_ENCOUNTER");
        if(authored.roles().stream().anyMatch(row->row.id().equals(nativeRole)&&!row.campaignRegion().isBlank()))
            return Optional.of("AUTHORED_CAMPAIGN_ENCOUNTER");
        var binding=role(nativeRole).orElse(null);
        if(binding==null)return Optional.of("NATIVE_BINDING_NOT_CERTIFIED");
        if(!binding.profiles().keySet().containsAll(EnumSet.allOf(DifficultyId.class)))
            return Optional.of("ERA_PROFILES_NOT_CERTIFIED");
        if("QA_CATALOG_NATIVE_PROFILE".equals(binding.sourceValidationId()))
            return Optional.of("QA_ONLY_NATIVE_BINDING");
        return Optional.empty();
    }
    /** QA-only passive binding derived from the authored role catalog. Production remains pinned to the manifest. */
    public synchronized Optional<Role> qaRole(String nativeRole){
        var pinned=role(nativeRole);
        if(pinned.isPresent())return pinned;
        var authored=ENCOUNTER_ROLES.resolveRole(nativeRole).orElse(null);
        if(authored==null)return Optional.empty();
        return Optional.of(qaCatalogRoles.computeIfAbsent(nativeRole,id->{
            var source=authored.alias()?ENCOUNTER_ROLES.aliases().stream()
                    .filter(row->row.roleId().equals(id)).findFirst().orElseThrow():null;
            var canonical=authored.canonical();
            return new Role(canonical.roleId(),Set.of(id),Map.of(DifficultyId.NORMAL,"QA_CATALOG_NATIVE_PROFILE"),
                    Set.of(EnemyAffixRegistry.Capability.DEFENSE_STAT,EnemyAffixRegistry.Capability.PACK_LEADER,
                            EnemyAffixRegistry.Capability.MINION_ROSTER,EnemyAffixRegistry.Capability.SHIELD_OWNER,
                            // Player-hit receipts are supplied by the shared victim-side damage owner for every QA NPC.
                            EnemyAffixRegistry.Capability.APPLIED_HIT_RECEIPTS),
                    id,List.of("NativeSpawningContext:"+id),"NativeRole:"+id,"QA_CATALOG_NATIVE_PROFILE",
                    "NativeRole:"+id,source==null?canonical.assetPath():source.assetPath(),
                    (source==null?canonical.assetSha256():source.assetSha256()).toLowerCase(Locale.ROOT),Set.of(),
                    EnemyStatusEffects.Source.noNativeStatus(0),false,false,0,false,false,List.of(),
                    List.of(source==null?canonical.assetPath():source.assetPath()));
        }));
    }
    /** Resolve the saved actor against its own pinned native binding, including mixed-role packs. */
    public Role requireActorRole(EnemyDescriptor actor){
        Objects.requireNonNull(actor);
        var role=actor.spawnOrigin()==EnemyRewardContext.Origin.QA
                ?qaRole(actor.nativeRoleId()).orElse(null):roles.get(actor.nativeRoleId());
        if(role==null||!revision.equals(actor.nativeBindingRevision())
                ||!role.canonicalRoleId().equals(actor.canonicalRoleId())
                ||!role.nativeRoleIds().contains(actor.nativeRoleId()))
            throw new IllegalStateException("ENEMY_ACTOR_NATIVE_ROLE_BINDING");
        return role;
    }
    public static EnemyNativeBindings load(){
        try(var stream=EnemyNativeBindings.class.getResourceAsStream("/rpg/enemies/native-bindings-v1.json")){
            if(stream==null)throw new IllegalStateException("ENEMY_NATIVE_BINDINGS_MISSING");
            return decode(JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject());
        }catch(IOException failure){throw new IllegalStateException("ENEMY_NATIVE_BINDINGS_UNREADABLE",failure);}
    }
    public static EnemyNativeBindings decode(JsonObject root){
        exact(root,"schemaVersion","revision","serverJarSha256","assetsSha256","bindings","derivedBindings");
        if(root.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("ENEMY_NATIVE_BINDING_SCHEMA");
        String revision=string(root,"revision"),server=string(root,"serverJarSha256"),assets=string(root,"assetsSha256");
        hash(server);hash(assets);
        var roles=new HashMap<String,Role>();var bindings=root.getAsJsonArray("bindings");
        if(bindings.isEmpty()||bindings.size()>2048)throw new IllegalArgumentException("ENEMY_NATIVE_BINDING_COUNT");
        for(var item:bindings){
            var row=item.getAsJsonObject();exact(row,"canonicalRoleId","nativeRoleIds","encounterProfileIdsByMode","modelAssetId",
                    "defaultTextureAssetIds","capabilities","defenseBinding","movementBinding","healthResourceBinding","actionBindings",
                    "controlProfileId","sourceValidationId","nativeLootSourceId","nativeImmunityChannels","nativeStatusSource","nativeStunStaggerImmune",
                    "nativeSlowImmune","maximumBaseEquipmentSlots","productionPromotionEnabled","sourceAssetPath","sourceAssetSha256",
                    "distanceDisplacementDelivery","evidenceFiles");
            var names=new LinkedHashSet<>(strings(row,"nativeRoleIds"));
            var modes=new EnumMap<DifficultyId,String>(DifficultyId.class);
            var profiles=row.getAsJsonObject("encounterProfileIdsByMode");
            for(var mode:profiles.entrySet()){
                var key=DifficultyId.valueOf(mode.getKey());
                if(modes.put(key,nonblank(mode.getValue().getAsString()))!=null)throw new IllegalArgumentException("ENEMY_NATIVE_PROFILE_DUPLICATE");
            }
            var capabilities=EnumSet.noneOf(EnemyAffixRegistry.Capability.class);
            for(var value:strings(row,"capabilities"))capabilities.add(EnemyAffixRegistry.Capability.valueOf(value));
            requiredBinding(row,"healthResourceBinding","ownerSymbol","receiptProducerSymbol");
            if(capabilities.contains(EnemyAffixRegistry.Capability.DEFENSE_STAT))
                requiredBinding(row,"defenseBinding","ownerSymbol","valueField","mitigationOwnerSymbol");
            else if(!row.get("defenseBinding").isJsonNull())
                throw new IllegalArgumentException("ENEMY_NATIVE_UNADVERTISED_DEFENSE");
            if(capabilities.contains(EnemyAffixRegistry.Capability.MOBILE))
                requiredBinding(row,"movementBinding","ownerSymbol","speedField","baselineUnit");
            else if(!row.get("movementBinding").isJsonNull())
                throw new IllegalArgumentException("ENEMY_NATIVE_UNADVERTISED_MOVEMENT");
            var actions=new ArrayList<Action>();
            for(var value:row.getAsJsonArray("actionBindings")){
                var action=value.getAsJsonObject();exact(action,action.has("supportedAffixIds")?Set.of("nativeActionId","sourceProducerSymbol","rootIdProducerSymbol","authoredStrikeKeys",
                        "nativeCauseToCanonicalChannelMap","procCoefficientPolicyId","offenseSnapshotBoundary",
                        "appliedHitReceiptConsumerSymbol","scalableRecoveryBinding","preservesAnimationAndHitCount",
                        "positiveNativeTestId","negativeNativeTestId","supportedAffixIds"):Set.of("nativeActionId","sourceProducerSymbol","rootIdProducerSymbol","authoredStrikeKeys",
                        "nativeCauseToCanonicalChannelMap","procCoefficientPolicyId","offenseSnapshotBoundary",
                        "appliedHitReceiptConsumerSymbol","scalableRecoveryBinding","preservesAnimationAndHitCount",
                        "positiveNativeTestId","negativeNativeTestId"));
                if(!action.get("preservesAnimationAndHitCount").getAsBoolean())throw new IllegalArgumentException("ENEMY_NATIVE_ANIMATION_CHANGED");
                for(var key:List.of("rootIdProducerSymbol","procCoefficientPolicyId","offenseSnapshotBoundary","appliedHitReceiptConsumerSymbol"))string(action,key);
                var strikeKeys=strings(action,"authoredStrikeKeys");
                if(strikeKeys.size()>32)throw new IllegalArgumentException("ENEMY_NATIVE_ACTION_STRIKE_KEYS");
                var channels=new TreeMap<String,String>();
                for(var channel:action.getAsJsonObject("nativeCauseToCanonicalChannelMap").entrySet()){
                    nonblank(channel.getKey());var target=nonblank(channel.getValue().getAsString());
                    if(!EnemyAffixRegistry.CHANNELS.contains(target))throw new IllegalArgumentException("ENEMY_NATIVE_NONCANONICAL_CHANNEL");
                    channels.put(channel.getKey(),target);
                }
                if(channels.containsKey("Projectile")&&!action.has("supportedAffixIds"))
                    throw new IllegalArgumentException("ENEMY_PROJECTILE_AFFIX_CAPABILITIES_UNDECLARED");
                String recoveryProfile=null;
                if(capabilities.contains(EnemyAffixRegistry.Capability.RECOVERY_TIMELINE)){
                    requiredBinding(action,"scalableRecoveryBinding","ownerSymbol","fieldOrProfileId");
                    var recovery=action.getAsJsonObject("scalableRecoveryBinding");
                    if(!"NativeNpcTiming.Attack".equals(string(recovery,"ownerSymbol")))
                        throw new IllegalArgumentException("ENEMY_NATIVE_RECOVERY_OWNER");
                    recoveryProfile=string(recovery,"fieldOrProfileId");
                    NativeNpcTiming.protectedSeconds(recoveryProfile);
                }else if(!action.get("scalableRecoveryBinding").isJsonNull())
                    throw new IllegalArgumentException("ENEMY_NATIVE_UNADVERTISED_RECOVERY");
                actions.add(new Action(string(action,"nativeActionId"),strikeKeys,string(action,"sourceProducerSymbol"),
                        channels,recoveryProfile,string(action,"positiveNativeTestId"),string(action,"negativeNativeTestId"),
                        action.has("supportedAffixIds")?Set.copyOf(strings(action,"supportedAffixIds")):null));
            }
            var nativeStatus=row.getAsJsonObject("nativeStatusSource");exact(nativeStatus,"existingChance","penetration");
            var nativeChances=new TreeMap<String,Double>();
            for(var chance:nativeStatus.getAsJsonObject("existingChance").entrySet())
                nativeChances.put(nonblank(chance.getKey()),chance.getValue().getAsDouble());
            var source=new EnemyStatusEffects.Source(nativeChances,nativeStatus.get("penetration").getAsDouble());
            var role=new Role(string(row,"canonicalRoleId"),names,modes,capabilities,string(row,"modelAssetId"),
                    strings(row,"defaultTextureAssetIds"),string(row,"controlProfileId"),string(row,"sourceValidationId"),string(row,"nativeLootSourceId"),
                    string(row,"sourceAssetPath"),string(row,"sourceAssetSha256"),Set.copyOf(stringsOrEmpty(row,"nativeImmunityChannels")),source,
                    row.get("nativeStunStaggerImmune").getAsBoolean(),row.get("nativeSlowImmune").getAsBoolean(),
                    row.get("maximumBaseEquipmentSlots").getAsInt(),row.get("distanceDisplacementDelivery").getAsBoolean(),
                    row.get("productionPromotionEnabled").getAsBoolean(),actions,strings(row,"evidenceFiles"));
            for(var name:names)if(roles.putIfAbsent(name,role)!=null)throw new IllegalArgumentException("ENEMY_NATIVE_ROLE_DUPLICATE");
        }
        var derived=root.getAsJsonObject("derivedBindings");
        exact(derived,"canonicalVariants","nativeArchetypes","installedCombatArchetypes");
        for(var value:derived.getAsJsonArray("nativeArchetypes")){
            var row=value.getAsJsonObject();exact(row,"id","parentRoleId","nativeActionId","memberRoleIds","supportedAffixIds");
            String archetype=string(row,"id"),parent=string(row,"parentRoleId"),rootId=string(row,"nativeActionId");
            boolean bite=archetype.equals("installed-predator-bite-physical-single-melee-v1");
            if(!parent.equals("Template_Predator")||!(bite?rootId.equals("member-native-action")
                    :rootId.equals("Root_NPC_Attack_Melee")))
                throw new IllegalArgumentException("ENEMY_ARCHETYPE_UNREVIEWED_SIGNATURE:"+archetype);
            var supported=Set.copyOf(strings(row,"supportedAffixIds"));
            var capabilities=Set.of(EnemyAffixRegistry.Capability.DIRECT_HIT,EnemyAffixRegistry.Capability.PHYSICAL_DIRECT_HIT,
                    EnemyAffixRegistry.Capability.NATIVE_KNOCKBACK,EnemyAffixRegistry.Capability.MOBILE,
                    EnemyAffixRegistry.Capability.DEFENSE_STAT,EnemyAffixRegistry.Capability.PACK_LEADER,
                    EnemyAffixRegistry.Capability.MINION_ROSTER,EnemyAffixRegistry.Capability.HOSTILE_STATUS_ADMISSION,
                    EnemyAffixRegistry.Capability.APPLIED_HIT_RECEIPTS);
            for(var element:row.getAsJsonArray("memberRoleIds")){
                var member=element.getAsJsonObject();exact(member,bite?
                        Set.of("roleId","modelAssetId","defaultTextureAssetIds","nativeLootSourceId","nativeActionId"):
                        Set.of("roleId","modelAssetId","defaultTextureAssetIds","nativeLootSourceId"));
                String id=string(member,"roleId");
                String memberRoot=bite?string(member,"nativeActionId"):rootId;
                if(bite&&!Set.of("Root_NPC_Wolf_Attack","Root_NPC_Snake_Attack","Root_NPC_Rat_Attack",
                        "Root_NPC_Fox_Attack","Root_NPC_Hyena_Attack").contains(memberRoot))
                    throw new IllegalArgumentException("ENEMY_ARCHETYPE_UNREVIEWED_ACTION:"+memberRoot);
                var resolved=ENCOUNTER_ROLES.resolveRole(id).orElseThrow(()->new IllegalArgumentException("ENEMY_ARCHETYPE_NOT_CATALOGUED:"+id));
                if(resolved.alias()||!resolved.canonical().roleId().equals(id))
                    throw new IllegalArgumentException("ENEMY_ARCHETYPE_NOT_CANONICAL:"+id);
                var source=resolved.canonical();
                var action=new Action(memberRoot,List.of("melee"),"ActionAttack.execute",Map.of("Physical","PHYSICAL"),null,
                        "ProductionEliteArchetypeAssetTest.certifiedNativeMeleeGraph",
                        "ProductionEliteArchetypeAssetTest.rejectsChangedNativeLeaf",supported);
                var binding=new Role(id,Set.of(id),profiles(id),capabilities,string(member,"modelAssetId"),
                        strings(member,"defaultTextureAssetIds"),parent,"EncounterProfileResolver.classifyAuthored",
                        string(member,"nativeLootSourceId"),source.assetPath(),source.assetSha256().toLowerCase(Locale.ROOT),
                        Set.of(),EnemyStatusEffects.Source.noNativeStatus(0),false,false,1,false,true,List.of(action),
                        List.of(source.assetPath(),"Server/NPC/Roles/_Core/Templates/Template_Predator.json",
                                bite?"installed-native-root:"+memberRoot:
                                        "Server/Item/RootInteractions/NPCs/Root_NPC_Attack_Melee.json"));
                if(roles.putIfAbsent(id,binding)!=null)throw new IllegalArgumentException("ENEMY_NATIVE_ROLE_DUPLICATE:"+id);
            }
        }
        // Further installed routes retain their own native roots and damage variables. The
        // pinned asset proof creates only role-local adapters; this parser never guesses an
        // attack or capability from a species name.
        for(var value:derived.getAsJsonArray("installedCombatArchetypes")){
            var row=value.getAsJsonObject();exact(row,"id","routeKind","supportedAffixIds","members");
            String archetype=string(row,"id"),kind=string(row,"routeKind");
            if(!Set.of("SINGLE_MELEE","CHAIN_SHARED_MELEE","CHAIN_VARIABLE_MELEE","CHAIN_REPEATED_MELEE","SINGLE_PROJECTILE",
                    "MIXED_NATIVE_ACTIONS","DUAL_NATIVE_ACTIONS","CONDITIONAL_MELEE","CAE_NATIVE_ACTIONS").contains(kind))
                throw new IllegalArgumentException("ENEMY_INSTALLED_ROUTE_KIND:"+archetype);
            var supported=Set.copyOf(strings(row,"supportedAffixIds"));
            for(var valueMember:row.getAsJsonArray("members")){
                var member=valueMember.getAsJsonObject();exact(member,"roleId","parentRoleId","modelAssetId",
                        "defaultTextureAssetIds","nativeLootSourceId","nativeImmunityChannels",
                        "nativeStunStaggerImmune","nativeSlowImmune","actions");
                String id=string(member,"roleId"),parent=string(member,"parentRoleId");
                var resolved=ENCOUNTER_ROLES.resolveRole(id).orElseThrow(()->new IllegalArgumentException("ENEMY_INSTALLED_ROLE_UNKNOWN:"+id));
                if(resolved.alias()||!resolved.canonical().roleId().equals(id))
                    throw new IllegalArgumentException("ENEMY_INSTALLED_ROLE_NOT_CANONICAL:"+id);
                var actions=new ArrayList<Action>();
                boolean projectile=false,melee=false;
                for(var item:member.getAsJsonArray("actions")){
                    var action=item.getAsJsonObject();
                    var actionKeys=new HashSet<>(Set.of("rootId","strikeKeys","channel"));
                    if(action.has("variantOwnerId"))actionKeys.add("variantOwnerId");
                    if(action.has("contactOccurrences"))actionKeys.add("contactOccurrences");
                    exact(action,actionKeys);
                    if(action.has("contactOccurrences")!=kind.equals("CHAIN_REPEATED_MELEE")
                            ||action.has("variantOwnerId")&&!kind.equals("CAE_NATIVE_ACTIONS"))
                        throw new IllegalArgumentException("ENEMY_INSTALLED_CONTACT_OR_VARIANT_KIND:"+id);
                    String channel=string(action,"channel");
                    if(!Set.of("Physical","Projectile").contains(channel))
                        throw new IllegalArgumentException("ENEMY_INSTALLED_CHANNEL_UNCERTIFIED:"+id);
                    projectile|=channel.equals("Projectile");melee|=channel.equals("Physical");
                    var strikes=strings(action,"strikeKeys");
                    if(channel.equals("Projectile")&&strikes.size()!=1)
                        throw new IllegalArgumentException("ENEMY_INSTALLED_PROJECTILE_STRIKE_COUNT:"+id);
                    actions.add(new Action(string(action,"rootId"),strikes,"ActionAttack.execute",
                            Map.of(channel,"PHYSICAL"),null,"ProductionEliteInstalledRouteTest.certifiedNativeRoute",
                            "ProductionEliteInstalledRouteTest.rejectsChangedNativeRoute",supported,
                            kind.equals("CONDITIONAL_MELEE"),
                            action.has("variantOwnerId")?string(action,"variantOwnerId"):null,
                            action.has("contactOccurrences")?readContactOccurrences(action.getAsJsonObject("contactOccurrences")):Map.of()));
                }
                if(actions.isEmpty()||actions.size()>16||kind.equals("SINGLE_PROJECTILE")!=projectile&&!kind.equals("MIXED_NATIVE_ACTIONS"))
                    throw new IllegalArgumentException("ENEMY_INSTALLED_ACTION_SET:"+id);
                var capabilities=EnumSet.of(EnemyAffixRegistry.Capability.DEFENSE_STAT,
                        EnemyAffixRegistry.Capability.APPLIED_HIT_RECEIPTS,
                        EnemyAffixRegistry.Capability.HOSTILE_STATUS_ADMISSION);
                if(melee||projectile)capabilities.add(EnemyAffixRegistry.Capability.DIRECT_HIT);
                if(melee||projectile)capabilities.add(EnemyAffixRegistry.Capability.PHYSICAL_DIRECT_HIT);
                if(melee){capabilities.add(EnemyAffixRegistry.Capability.MOBILE);
                    capabilities.add(EnemyAffixRegistry.Capability.PACK_LEADER);
                    capabilities.add(EnemyAffixRegistry.Capability.MINION_ROSTER);}
                var source=resolved.canonical();
                var binding=new Role(id,Set.of(id),profiles(id),capabilities,string(member,"modelAssetId"),
                        strings(member,"defaultTextureAssetIds"),parent,"EncounterProfileResolver.classifyAuthored",
                        string(member,"nativeLootSourceId"),source.assetPath(),source.assetSha256().toLowerCase(Locale.ROOT),
                        Set.copyOf(stringsOrEmpty(member,"nativeImmunityChannels")),EnemyStatusEffects.Source.noNativeStatus(0),
                        member.get("nativeStunStaggerImmune").getAsBoolean(),member.get("nativeSlowImmune").getAsBoolean(),
                        1,false,true,actions,List.of(source.assetPath(),"docs/enemies/R229_PRODUCTION_ELITE_COVERAGE.md"));
                if(roles.putIfAbsent(id,binding)!=null)throw new IllegalArgumentException("ENEMY_NATIVE_ROLE_DUPLICATE:"+id);
            }
        }
        for(var value:derived.getAsJsonArray("canonicalVariants")){
            var row=value.getAsJsonObject();exact(row,"canonicalRoleId","nativeRoleIds");
            String canonical=string(row,"canonicalRoleId");
            var base=roles.get(canonical);
            if(base==null||!base.productionPromotionEnabled()||!base.nativeRoleIds().contains(canonical))
                throw new IllegalArgumentException("ENEMY_VARIANT_BASE_NOT_CERTIFIED:"+canonical);
            for(var id:strings(row,"nativeRoleIds")){
                var resolved=ENCOUNTER_ROLES.resolveRole(id).orElseThrow(()->new IllegalArgumentException("ENEMY_VARIANT_NOT_CATALOGUED:"+id));
                if(!resolved.alias()||!resolved.canonical().roleId().equals(canonical))
                    throw new IllegalArgumentException("ENEMY_VARIANT_CANONICAL_MISMATCH:"+id);
                var alias=ENCOUNTER_ROLES.aliases().stream().filter(item->item.roleId().equals(id)).findFirst().orElseThrow();
                var inherited=new Role(canonical,Set.of(id),profiles(id),base.capabilities(),base.modelAssetId(),base.textures(),
                        base.controlProfileId(),base.sourceValidationId(),base.nativeLootSourceId(),alias.assetPath(),
                        alias.assetSha256().toLowerCase(Locale.ROOT),base.nativeImmunityChannels(),base.nativeStatusSource(),
                        base.nativeStunStaggerImmune(),base.nativeSlowImmune(),base.maximumBaseEquipmentSlots(),
                        base.distanceDisplacementDelivery(),true,base.actions(),List.of(alias.assetPath(),base.sourceAssetPath()));
                if(roles.putIfAbsent(id,inherited)!=null)throw new IllegalArgumentException("ENEMY_NATIVE_ROLE_DUPLICATE:"+id);
            }
        }
        return new EnemyNativeBindings(revision,server,assets,roles);
    }
    private static Map<DifficultyId,String> profiles(String roleId){
        var result=new EnumMap<DifficultyId,String>(DifficultyId.class);
        for(var mode:DifficultyId.values())result.put(mode,AUTHORED_ROLES.profileId()+"/"+roleId+"/"+mode);
        return result;
    }
    private static void requiredBinding(JsonObject row,String key,String... fields){
        if(row.get(key)==null||row.get(key).isJsonNull())throw new IllegalArgumentException("ENEMY_NATIVE_REQUIRED_BINDING:"+key);
        var value=row.getAsJsonObject(key);exact(value,fields);
        for(var field:fields)string(value,field);
    }
    private static Map<String,Integer> readContactOccurrences(JsonObject value){
        var result=new HashMap<String,Integer>();
        for(var entry:value.entrySet()){
            int count=entry.getValue().getAsInt();
            if(count<1||count>16||result.put(nonblank(entry.getKey()),count)!=null)
                throw new IllegalArgumentException("ENEMY_NATIVE_CONTACT_CERTIFICATE");
        }
        return Map.copyOf(result);
    }
    private static List<String> strings(JsonObject row,String key){
        var result=stringsOrEmpty(row,key);
        if(result.isEmpty())throw new IllegalArgumentException("ENEMY_NATIVE_STRING_LIST:"+key);
        return result;
    }
    private static List<String> stringsOrEmpty(JsonObject row,String key){
        var result=new ArrayList<String>();for(var value:row.getAsJsonArray(key))result.add(nonblank(value.getAsString()));
        if(result.size()>64||new HashSet<>(result).size()!=result.size())throw new IllegalArgumentException("ENEMY_NATIVE_STRING_LIST:"+key);
        return List.copyOf(result);
    }
    private static String string(JsonObject row,String key){return nonblank(row.get(key).getAsString());}
    private static String nonblank(String value){
        if(value==null||value.isBlank()||value.length()>512||value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("ENEMY_NATIVE_EMPTY_ID");return value;
    }
    private static void hash(String value){if(value==null||!value.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("ENEMY_NATIVE_PACKAGE_HASH");}
    private static void exact(JsonObject value,String... keys){
        if(!value.keySet().equals(Set.of(keys)))throw new IllegalArgumentException("ENEMY_NATIVE_BINDING_KEYS:"+value.keySet());
    }
    private static void exact(JsonObject value,Set<String> keys){
        if(!value.keySet().equals(keys))throw new IllegalArgumentException("ENEMY_NATIVE_BINDING_KEYS:"+value.keySet());
    }
}
