package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.*;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
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
    public record Action(String nativeActionId,String strikeId,String producer,Map<String,String> channels,
            String recoveryProfileId,String positiveTestId,String negativeTestId,Set<String> supportedAffixIds){
        public Action{nonblank(nativeActionId);nonblank(strikeId);nonblank(producer);nonblank(positiveTestId);nonblank(negativeTestId);
            if(recoveryProfileId!=null)nonblank(recoveryProfileId);
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
                var context=com.hypixel.hytale.server.core.entity.InteractionContext.withoutEntity();
                context.setInteractionVarsGetter(ignored->npc.getRole().getInteractionVars());
                var graph=com.inigmasgames.hytalerpg.execution.hytale.NativeBasicAttackPaths.resolve(context,root,InteractionType.Primary);
                var bound=graph.damageOccurrences().isEmpty()
                        ?NativeEnemyAction.Binding.certifyProjectileSingle(revision,root,InteractionType.Primary,context,
                                action.strikeId(),action.producer(),nativeStatusSource)
                        :NativeEnemyAction.Binding.certifySingle(revision,root,InteractionType.Primary,context,
                                action.strikeId(),action.producer(),nativeStatusSource);
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
                    ||!profiles.getOrDefault(spawn.combat().difficulty(),"").equals(spawn.registryProfile())
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
            var supported=new HashSet<String>();for(var op:EnemyAffixRegistry.Operator.values())supported.add(op.id());
            if(actions.isEmpty())supported.retainAll(Set.of("ME-003","ME-004","ME-022","ME-023","ME-024","ME-026","ME-027"));
            for(var action:actions)if(action.supportedAffixIds()!=null)supported.retainAll(action.supportedAffixIds());
            var binding=new EnemyAffixSelection.Binding(bindingRevision,capabilities,0,improves,
                    nativeStunStaggerImmune,nativeSlowImmune,0,distanceDisplacementDelivery,supported);
            return new EnemyBirthPlanner.Candidate(descriptor,binding,maximumBaseEquipmentSlots);
        }
    }
    private final String revision,serverJarSha256,assetsSha256;
    private final Map<String,Role> roles;
    private EnemyNativeBindings(String revision,String serverJarSha256,String assetsSha256,Map<String,Role> roles){
        this.revision=revision;this.serverJarSha256=serverJarSha256;this.assetsSha256=assetsSha256;this.roles=Map.copyOf(roles);
    }
    public String revision(){return revision;}
    public String serverJarSha256(){return serverJarSha256;}
    public String assetsSha256(){return assetsSha256;}
    public Collection<Role> roles(){return roles.values();}
    public Optional<Role> role(String nativeRole){return Optional.ofNullable(roles.get(nativeRole));}
    /** Resolve the saved actor against its own pinned native binding, including mixed-role packs. */
    public Role requireActorRole(EnemyDescriptor actor){
        Objects.requireNonNull(actor);
        var role=roles.get(actor.nativeRoleId());
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
        exact(root,"schemaVersion","revision","serverJarSha256","assetsSha256","bindings");
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
                if(strikeKeys.size()!=1)throw new IllegalArgumentException("ENEMY_NATIVE_ONLY_SINGLE_STRIKE_CERTIFIED");
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
                actions.add(new Action(string(action,"nativeActionId"),strikeKeys.getFirst(),string(action,"sourceProducerSymbol"),
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
        return new EnemyNativeBindings(revision,server,assets,roles);
    }
    private static void requiredBinding(JsonObject row,String key,String... fields){
        if(row.get(key)==null||row.get(key).isJsonNull())throw new IllegalArgumentException("ENEMY_NATIVE_REQUIRED_BINDING:"+key);
        var value=row.getAsJsonObject(key);exact(value,fields);
        for(var field:fields)string(value,field);
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
