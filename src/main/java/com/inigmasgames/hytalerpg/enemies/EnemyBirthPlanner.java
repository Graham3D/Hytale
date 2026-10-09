package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.gear.GearRandom;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.progress.ProgressionMath;
import com.inigmasgames.hytalerpg.progress.RewardIntent;
import java.util.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.*;

/** Birth-only composition of the existing selection, immunity, inheritance and reward owners.
 * Candidates are certified native spawn-plan slots, never nearby live actors. This owns no ECS or IO.
 */
public final class EnemyBirthPlanner {
    public record Candidate(EnemyDescriptor nativeBaseline,EnemyAffixSelection.Binding binding,int maximumEquipment){
        public Candidate{
            Objects.requireNonNull(nativeBaseline);Objects.requireNonNull(binding);
            require(nativeBaseline.enemyRarity()==EnemyRarity.NORMAL
                    &&(nativeBaseline.encounterRank()!=ProgressionMath.Rank.BOSS
                            ||nativeBaseline.spawnOrigin()==EnemyRewardContext.Origin.QA)
                    &&nativeBaseline.packRole()==EnemyDescriptor.PackRole.NONE&&nativeBaseline.ownAffixes().isEmpty()
                    &&nativeBaseline.inheritedAffixes().isEmpty()&&nativeBaseline.selectedImmunityGrants().isEmpty(),"BIRTH_REQUIRES_UNPROMOTED_BASELINE");
            require(nativeBaseline.spawnOrigin()==EnemyRewardContext.Origin.NATURAL||nativeBaseline.spawnOrigin()==EnemyRewardContext.Origin.QA,"BIRTH_ORIGIN_INELIGIBLE");
            require(nativeBaseline.nativeBindingRevision().equals(binding.revision()),"BIRTH_BINDING_REVISION");
            require(maximumEquipment>=0&&maximumEquipment<=16,"BIRTH_EQUIPMENT_MAXIMUM");
        }
    }
    public record Request(String seed,UUID packId,Vec3 anchor,String nativePlanReceipt,List<Candidate> originals,
            List<Candidate> additionalSlots,boolean compatibleNativeGroup,int additionalNativeCapacity,boolean specialPackCapacity){
        public Request{
            require(seed!=null&&!seed.isBlank()&&seed.length()<=512,"BIRTH_SEED");Objects.requireNonNull(packId);Objects.requireNonNull(anchor);
            require(nativePlanReceipt!=null&&!nativePlanReceipt.isBlank()&&nativePlanReceipt.length()<=512,"BIRTH_NATIVE_PLAN");
            originals=List.copyOf(originals);additionalSlots=List.copyOf(additionalSlots);
            require(!originals.isEmpty()&&originals.size()<=8&&originals.size()+additionalSlots.size()<=8&&additionalNativeCapacity>=0,"BIRTH_CANDIDATE_BOUNDS");
            var first=originals.getFirst().nativeBaseline();var logical=new HashSet<UUID>();var nativeIds=new HashSet<UUID>();
            for(var candidate:combined(originals,additionalSlots)){
                var actor=candidate.nativeBaseline();
                require(actor.worldId().equals(first.worldId())&&actor.encounterId().equals(first.encounterId())
                        &&actor.encounterGeneration()==first.encounterGeneration()&&actor.difficulty()==first.difficulty()
                        &&actor.balanceRevision().equals(first.balanceRevision())&&actor.spawnOrigin()==first.spawnOrigin()
                        &&actor.spawnCycleId().equals(first.spawnCycleId()),"BIRTH_CANDIDATE_CONTEXT");
                require(logical.add(actor.logicalActorId())&&nativeIds.add(actor.entityId()),"BIRTH_CANDIDATE_DUPLICATE");
                // An approved role mix is supplied by the incoming plan; added actors cannot introduce a new role.
                require(originals.stream().anyMatch(original->original.nativeBaseline().canonicalRoleId().equals(actor.canonicalRoleId())),"BIRTH_UNAPPROVED_ADDITIONAL_ROLE");
            }
        }
    }
    public record Result(EnemyBirthPlan plan,EnemyRarity rolledRarity,String fallbackReason){
        public boolean promoted(){return plan.pack()!=null;}
    }
    private record UniqueDemand(int baseMinions,EnemyAffixSelection.Binding binding,List<EnemyAffixSelection.Choice> choices,int members){}
    private final EnemyBalance balance;private final EnemyAffixRegistry registry;private final EnemyAffixSelection selection;
    private final EnemyImmunitySelection immunity;private final EnemyNamePools names;private final EnemyVisualVariants visuals;
    public EnemyBirthPlanner(EnemyBalance balance,EnemyAffixRegistry registry,EnemyAffinityRegistry affinities,EnemyNamePools names,EnemyVisualVariants visuals){
        this(balance,registry,affinities,names,visuals,new EnemyAffixSelection.WeightPolicy(Map.of(),Set.of()));
    }
    public EnemyBirthPlanner(EnemyBalance balance,EnemyAffixRegistry registry,EnemyAffinityRegistry affinities,EnemyNamePools names,
                             EnemyVisualVariants visuals,EnemyAffixSelection.WeightPolicy weightPolicy){
        this.balance=Objects.requireNonNull(balance);this.registry=Objects.requireNonNull(registry);
        selection=new EnemyAffixSelection(registry,weightPolicy);
        immunity=new EnemyImmunitySelection(balance,affinities);this.names=Objects.requireNonNull(names);this.visuals=Objects.requireNonNull(visuals);
    }
    public EnemyRarity rarity(String seed,DifficultyId difficulty){
        var weights=balance.promotion().weights(difficulty);long total=1000;
        long draw=random(seed).stream("me.rarity").nextLong(total);
        for(var rarity:List.of(EnemyRarity.NORMAL,EnemyRarity.CHAMPION,EnemyRarity.UNIQUE)){
            int weight=weights.get(rarity);if(draw<weight)return rarity;draw-=weight;
        }
        throw new IllegalStateException("BIRTH_RARITY_DRAW_RANGE");
    }
    /** Preflight only: native additional slots are created after a selected Unique demand, never speculatively. */
    public OptionalInt additionalUniqueMembers(Request request){
        require(request.originals().getFirst().nativeBaseline().balanceRevision().equals(balance.revision()),"BIRTH_BALANCE_REVISION");
        if(!request.compatibleNativeGroup()||!request.specialPackCapacity()
                ||rarity(request.seed(),request.originals().getFirst().nativeBaseline().difficulty())!=EnemyRarity.UNIQUE)return OptionalInt.empty();
        var demand=uniqueDemand(request).orElse(null);
        if(demand==null||demand.members()>balance.promotion().maximumMembers()||demand.members()<request.originals().size())
            return OptionalInt.empty();
        return OptionalInt.of(demand.members()-request.originals().size());
    }
    /** The Champion count is the same stream and bound used by final planning. */
    public OptionalInt additionalChampionMembers(Request request){
        require(request.originals().getFirst().nativeBaseline().balanceRevision().equals(balance.revision()),"BIRTH_BALANCE_REVISION");
        if(!request.compatibleNativeGroup()||!request.specialPackCapacity()
                ||rarity(request.seed(),request.originals().getFirst().nativeBaseline().difficulty())!=EnemyRarity.CHAMPION)
            return OptionalInt.empty();
        var promotion=balance.promotion();
        int selected=Math.max(request.originals().size(),random(request.seed()).stream("me.champion-count")
                .nextInt(promotion.championMinimum(),promotion.championMaximum()+1));
        if(selected>promotion.championMaximum()||selected>promotion.maximumMembers())return OptionalInt.empty();
        return OptionalInt.of(selected-request.originals().size());
    }
    private Optional<UniqueDemand> uniqueDemand(Request request){
        var promotion=balance.promotion();var rng=random(request.seed());
        int baseMinions=Math.max(Math.min(request.originals().size()-1,promotion.minionMaximum()),
                rng.stream("me.minion-count").nextInt(promotion.minionMinimum(),promotion.minionMaximum()+1));
        var leader=request.originals().getFirst().binding();
        var supported=new HashSet<>(leader.supportedAffixIds());
        // The native extension adds the first role's existing action, so only already-present
        // different-role minions can narrow the inherited package before the birth draw.
        for(var minion:request.originals().subList(1,request.originals().size()))
            for(var id:List.copyOf(supported)){
                var inheritance=registry.require(id).inheritance();
                if(inheritance!=Inheritance.NONE&&inheritance!=Inheritance.PACK_AURA_MEMBERSHIP_ONLY
                        &&!canInherit(minion.binding(),id,inheritance))supported.remove(id);
            }
        var binding=withMinions(new EnemyAffixSelection.Binding(leader.revision(),leader.capabilities(),
                leader.usableDefense(),leader.improvesElementalResistance(),leader.nativeStunStaggerImmune(),
                leader.nativeSlowImmune(),leader.initialMinions(),leader.distanceDisplacementDelivery(),supported),baseMinions);
        var choices=selection.select(new EnemyAffixSelection.Request(binding,
                request.originals().getFirst().nativeBaseline().difficulty(),EnemyRarity.UNIQUE,
                balance.rarity(EnemyRarity.UNIQUE,false).counts().get(request.originals().getFirst().nativeBaseline().difficulty().ordinal()),
                List.of(),true),request.seed()).orElse(null);
        if(choices==null)return Optional.empty();
        int members=1+baseMinions+(choices.stream().anyMatch(c->c.affixId().equals(Operator.HORDE.id()))?2:0);
        return Optional.of(new UniqueDemand(baseMinions,binding,choices,members));
    }
    public Result plan(Request request){
        require(request.originals().getFirst().nativeBaseline().balanceRevision().equals(balance.revision()),"BIRTH_BALANCE_REVISION");
        var rolled=rarity(request.seed(),request.originals().getFirst().nativeBaseline().difficulty());
        if(rolled==EnemyRarity.NORMAL)return normal(request,rolled,null);
        if(!request.compatibleNativeGroup())return normal(request,rolled,"INCOMPATIBLE_NATIVE_GROUP");
        if(!request.specialPackCapacity())return normal(request,rolled,"SPECIAL_PACK_CAPACITY");
        var p=balance.promotion();var rng=random(request.seed());
        int champions=rolled==EnemyRarity.CHAMPION?Math.max(request.originals().size(),rng.stream("me.champion-count").nextInt(p.championMinimum(),p.championMaximum()+1)):0;
        if(champions>p.championMaximum())return normal(request,rolled,"INCOMING_ROSTER_EXCEEDS_SELECTED_PACK");
        if(rolled==EnemyRarity.CHAMPION&&(champions-request.originals().size()>request.additionalNativeCapacity()
                ||champions>request.originals().size()+request.additionalSlots().size()))return normal(request,rolled,"NATIVE_SPAWN_CAPACITY");
        var unique=rolled==EnemyRarity.UNIQUE?uniqueDemand(request).orElse(null):null;
        if(rolled==EnemyRarity.UNIQUE&&unique==null)return normal(request,rolled,"NO_COMPLETE_LEGAL_AFFIX_SET");
        int baseMinions=unique==null?0:unique.baseMinions();
        var binding=rolled==EnemyRarity.CHAMPION?championBinding(combined(request.originals(),request.additionalSlots().subList(0,champions-request.originals().size())))
                :unique.binding();
        var mode=request.originals().getFirst().nativeBaseline().difficulty();
        var chosen=rolled==EnemyRarity.UNIQUE?Optional.of(unique.choices()):selection.select(
                new EnemyAffixSelection.Request(binding,mode,rolled,balance.rarity(rolled,false).counts().get(mode.ordinal()),List.of(),true),request.seed());
        if(chosen.isEmpty())return normal(request,rolled,"NO_COMPLETE_LEGAL_AFFIX_SET");
        var choices=chosen.get();
        int members=rolled==EnemyRarity.CHAMPION?champions:unique.members();
        if(members>p.maximumMembers()||rolled==EnemyRarity.CHAMPION&&members>p.championMaximum()||members<request.originals().size())
            return normal(request,rolled,"INCOMING_ROSTER_EXCEEDS_SELECTED_PACK");
        int extra=members-request.originals().size();
        if(extra>request.additionalNativeCapacity()||extra>request.additionalSlots().size())return normal(request,rolled,"NATIVE_SPAWN_CAPACITY");
        var roster=combined(request.originals(),request.additionalSlots().subList(0,extra));
        var leader=roster.getFirst().nativeBaseline();var leaderId=rolled==EnemyRarity.UNIQUE?leader.logicalActorId():null;
        var own=choices.stream().map(choice->ownInstance(choice,mode,binding)).toList();
        var inherited=leaderId==null?List.<EnemyDescriptor.AffixInstance>of():own.stream()
                .map(affix->EnemyDescriptor.inheritedInstance(registry.require(affix.affixId()),affix,leaderId,"me.inherit/"+affix.affixId()))
                .flatMap(Optional::stream).toList();
        boolean packbound=choices.stream().anyMatch(choice->choice.affixId().equals(Operator.PACKBOUND.id()));
        var actors=new ArrayList<EnemyDescriptor>();var floors=new TreeMap<UUID,Map<String,Double>>();
        for(int index=0;index<roster.size();index++){
            var candidate=roster.get(index);var source=candidate.nativeBaseline();boolean minion=leaderId!=null&&index>0;
            var rarity=minion?EnemyRarity.NORMAL:rolled;var actorOwn=minion?List.<EnemyDescriptor.AffixInstance>of():own;
            var actorInherited=minion?inherited:List.<EnemyDescriptor.AffixInstance>of();
            if(minion&&!supportsInheritance(candidate.binding(),actorInherited))return normal(request,rolled,"MINION_INHERITANCE_BINDING");
            var grants=immunity.select(source.canonicalRoleId(),rarity,mode,false,source.nativeImmunityChannels(),Set.of(),minion?List.of():choices,
                    true,packbound&&!minion,packbound&&minion,false,actorSeed(request,source));
            if(!grants.accepted())return normal(request,rolled,grants.rejection());
            var origin=source.spawnOrigin()==EnemyRewardContext.Origin.QA?source.spawnOrigin():minion?EnemyRewardContext.Origin.INITIAL_PACK_MINION:source.spawnOrigin();
            var rewards=balance.rewards(rarity,origin,minion,actorOwn.size());
            if(Math.ceil(candidate.maximumEquipment()*rewards.quantityFactor())>16)return normal(request,rolled,"ENEMY_EQUIPMENT_QUANTITY_EXCEEDS_CAP");
            rewards.validateEquipmentMaximum(candidate.maximumEquipment());
            var role=leaderId==null?EnemyDescriptor.PackRole.MEMBER:minion?EnemyDescriptor.PackRole.MINION:EnemyDescriptor.PackRole.LEADER;
            actors.add(descriptor(request,source,rarity,role,leaderId,actorOwn,actorInherited,grants,rewards));floors.put(source.logicalActorId(),grants.affinityFloors());
        }
        var membersRecord=actors.stream().map(actor->new EnemyPackRecord.Member(actor.logicalActorId(),actor.entityId(),actor.canonicalRoleId(),EnemyPackRecord.Role.valueOf(actor.packRole().name()))).toList();
        var guards=new TreeSet<UUID>();if(packbound)actors.stream().skip(1).forEach(actor->guards.add(actor.logicalActorId()));
        var pack=new EnemyPackRecord(1,request.packId(),leader.worldId(),leader.encounterId(),leader.encounterGeneration(),EnemyPackRecord.State.RESERVED,null,
                request.anchor(),membersRecord,leaderId,guards,Map.of(),false,false,request.nativePlanReceipt(),null);
        return new Result(birth(request,actors,pack,floors),rolled,null);
    }
    /** Explicit operator birth uses the same descriptor, immunity, inheritance and pack owners as natural birth. */
    public EnemyBirthPlan planQa(Request request,EnemyQaSpawnRequest qa){
        require(request.originals().getFirst().nativeBaseline().spawnOrigin()==EnemyRewardContext.Origin.QA
                &&request.originals().stream().allMatch(c->c.nativeBaseline().spawnOrigin()==EnemyRewardContext.Origin.QA)
                &&request.additionalSlots().isEmpty(),"QA_BIRTH_PROVENANCE");
        var rarity=qa.rarity();var offered=request.originals();var leader=offered.getFirst().nativeBaseline();
        require(leader.nativeRoleId().equals(qa.nativeRoleId()),"QA_BIRTH_ROLE");
        require(leader.difficulty()==qa.era()&&offered.stream().allMatch(candidate->
                candidate.nativeBaseline().difficulty()==qa.era()),"QA_BIRTH_ERA_CHANGED");
        boolean champion=rarity==EnemyRarity.CHAMPION;
        int offeredCount=qa.offeredMembers();
        require(offered.size()==offeredCount,"QA_BIRTH_ROSTER_SIZE");
        var binding=withMinions(offered.getFirst().binding(),qa.baseMinions());
        qa.validateExplicitCount(balance);
        int expectedAffixes=qa.requiredAffixes(balance);
        var fixed=qa.authoredSuperUnique().map(SuperUniqueTemplates.Template::fixedAffixes).orElse(List.of());
        if(!qa.affixIds().isEmpty())for(var required:fixed)
            require(qa.affixIds().contains(required.affixId()),"QA_SUPER_UNIQUE_FIXED_AFFIX_REQUIRED:"+required.affixId());
        var choices=new ArrayList<EnemyAffixSelection.Choice>();
        if(qa.affixIds().isEmpty()){
            var normal=new EnemyAffixSelection.Request(binding,leader.difficulty(),rarity,expectedAffixes,fixed,false);
            choices.addAll(selection.select(normal,request.seed()).orElseThrow(()->new IllegalArgumentException("QA_NO_SUPPORTED_RANDOM_AFFIX_SET")));
        }else {
            var incompatible=new ArrayList<String>();
            for(String id:qa.affixIds()){
            EnemyAffixSelection.Choice choice;
            if(id.equals(Operator.AURA_ENCHANTED.id())){
                var allowed=Arrays.stream(Selector.values()).map(selector->new EnemyAffixSelection.Choice(id,selector))
                        .filter(candidate->selection.eligibleQa(binding,rarity,candidate)).toList();
                if(allowed.isEmpty()){incompatible.add(id);continue;}
                choice=allowed.get(random(request.seed()).stream("me.qa-aura").nextInt(allowed.size()));
            }else choice=fixed.stream().filter(f->f.affixId().equals(id)).findFirst()
                    .orElse(new EnemyAffixSelection.Choice(id,null));
            if(!selection.eligibleQa(binding,rarity,choice))incompatible.add(id);
            else choices.add(choice);
            }
            if(!incompatible.isEmpty())
                throw new IllegalArgumentException("QA_AFFIX_CAPABILITY_UNSUPPORTED:"+String.join(",",incompatible)+":"+qa.nativeRoleId());
        }
        int expected=1+qa.baseMinions()+(choices.stream()
                .anyMatch(c->c.affixId().equals(Operator.HORDE.id()))?2:0);
        var roster=offered.subList(0,expected);
        var selectedRequest=expected==offered.size()?request:new Request(request.seed(),request.packId(),request.anchor(),
                request.nativePlanReceipt(),roster,List.of(),true,0,true);
        var own=choices.stream().map(choice->ownInstance(choice,leader.difficulty(),binding)).toList();
        UUID leaderId=champion?null:leader.logicalActorId();
        var inherited=champion?List.<EnemyDescriptor.AffixInstance>of():own.stream()
                .map(affix->EnemyDescriptor.inheritedInstance(registry.require(affix.affixId()),affix,leaderId,"me.inherit/"+affix.affixId()))
                .flatMap(Optional::stream).toList();
        boolean packbound=choices.stream().anyMatch(choice->choice.affixId().equals(Operator.PACKBOUND.id()));
        var actors=new ArrayList<EnemyDescriptor>();var floors=new TreeMap<UUID,Map<String,Double>>();
        for(int index=0;index<roster.size();index++){
            var candidate=roster.get(index);var source=candidate.nativeBaseline();boolean minion=!champion&&index>0;
            if(minion&&!supportsInheritance(candidate.binding(),inherited))
                throw new IllegalArgumentException("QA_MINION_INHERITANCE_UNSUPPORTED:"+source.nativeRoleId());
            var actorChoices=minion?List.<EnemyAffixSelection.Choice>of():choices;
            var grants=immunity.select(source.canonicalRoleId(),minion?EnemyRarity.NORMAL:rarity,
                    source.difficulty(),source.encounterRank()==ProgressionMath.Rank.BOSS,
                    source.nativeImmunityChannels(),Set.of(),actorChoices,true,
                    packbound&&!minion,packbound&&minion,false,actorSeed(selectedRequest,source));
            if(!grants.accepted())throw new IllegalArgumentException("QA_IMMUNITY_UNSUPPORTED:"+grants.rejection());
            var role=champion?EnemyDescriptor.PackRole.MEMBER:minion?EnemyDescriptor.PackRole.MINION:EnemyDescriptor.PackRole.LEADER;
            var reward=balance.rewards(minion?EnemyRarity.NORMAL:rarity,EnemyRewardContext.Origin.QA,minion,minion?0:own.size());
            actors.add(descriptor(selectedRequest,source,minion?EnemyRarity.NORMAL:rarity,role,leaderId,
                    minion?List.of():own,minion?inherited:List.of(),grants,reward));
            floors.put(source.logicalActorId(),grants.affinityFloors());
        }
        var members=actors.stream().map(actor->new EnemyPackRecord.Member(actor.logicalActorId(),actor.entityId(),
                actor.canonicalRoleId(),EnemyPackRecord.Role.valueOf(actor.packRole().name()))).toList();
        var guards=new TreeSet<UUID>();if(packbound)actors.stream().skip(1).forEach(actor->guards.add(actor.logicalActorId()));
        var pack=new EnemyPackRecord(1,selectedRequest.packId(),leader.worldId(),leader.encounterId(),leader.encounterGeneration(),
                EnemyPackRecord.State.RESERVED,null,selectedRequest.anchor(),members,leaderId,guards,Map.of(),false,false,
                selectedRequest.nativePlanReceipt(),null);
        return birth(selectedRequest,actors,pack,floors);
    }
    private Result normal(Request request,EnemyRarity rolled,String reason){
        var actors=new ArrayList<EnemyDescriptor>();var floors=new TreeMap<UUID,Map<String,Double>>();
        for(var candidate:request.originals()){
            var source=candidate.nativeBaseline();var grants=immunity.select(source.canonicalRoleId(),EnemyRarity.NORMAL,source.difficulty(),false,
                    source.nativeImmunityChannels(),Set.of(),List.of(),false,false,false,false,actorSeed(request,source));
            require(grants.accepted(),"NORMAL_NATIVE_IMMUNITY_REJECTED");
            actors.add(descriptor(request,source,EnemyRarity.NORMAL,EnemyDescriptor.PackRole.NONE,null,List.of(),List.of(),grants,
                    balance.rewards(EnemyRarity.NORMAL,source.spawnOrigin(),false,0)));floors.put(source.logicalActorId(),grants.affinityFloors());
        }
        return new Result(birth(request,actors,null,floors),rolled,reason);
    }
    private EnemyBirthPlan birth(Request request,List<EnemyDescriptor> actors,EnemyPackRecord pack,Map<UUID,Map<String,Double>> floors){
        var first=actors.getFirst();return new EnemyBirthPlan(1,first.worldId(),first.encounterId(),first.encounterGeneration(),request.seed(),
                request.originals().stream().map(c->c.nativeBaseline().entityId()).toList(),actors,pack,floors);
    }
    private EnemyDescriptor descriptor(Request request,EnemyDescriptor s,EnemyRarity rarity,EnemyDescriptor.PackRole role,UUID leader,
            List<EnemyDescriptor.AffixInstance> own,List<EnemyDescriptor.AffixInstance> inherited,EnemyImmunitySelection.Result grants,EnemyRewardContext rewards){
        String seed=actorSeed(request,s);String name=rarity==EnemyRarity.UNIQUE||rarity==EnemyRarity.SUPER_UNIQUE
                ?names.uniqueName(seed):s.nameToken();
        String palette=visuals.select(s.canonicalRoleId(),rarity,null,seed).map(EnemyVisualVariants.Variant::id).orElse(null);
        EnemyDescriptor.TemplateBirth template=rarity==EnemyRarity.SUPER_UNIQUE&&s.spawnOrigin()==EnemyRewardContext.Origin.QA
                ?new EnemyDescriptor.TemplateBirth("qa-ad-hoc",1,balance.rarity(rarity,false).maxHealth(),
                        balance.rarity(rarity,false).directDamage()):null;
        return new EnemyDescriptor(1,s.descriptorRevision(),balance.revision(),s.nativeBindingRevision(),s.worldId(),s.encounterId(),s.encounterGeneration(),
                s.logicalActorId(),s.entityId(),s.spawnCycleId(),rewards.origin(),s.canonicalRoleId(),s.nativeRoleId(),s.combatLevel(),s.difficulty(),s.encounterRank(),
                rarity,role,role==EnemyDescriptor.PackRole.NONE?null:request.packId(),leader,s.sourceValidationId(),s.lootSourceId(),s.acquisitionSourceId(),name,seed,palette,
                own,inherited,grants.preservedNative(),grants.selected(),s.controlProfileId(),rewards,s.nativeBaselineFingerprint(),template);
    }
    private EnemyAffixSelection.Binding championBinding(List<Candidate> roster){
        var capabilities=EnumSet.allOf(Capability.class);roster.forEach(c->capabilities.retainAll(c.binding().capabilities()));
        var supported=new HashSet<String>(roster.getFirst().binding().supportedAffixIds());
        roster.forEach(c->supported.retainAll(c.binding().supportedAffixIds()));
        return new EnemyAffixSelection.Binding(registry.revision()+"/shared-champion",capabilities,
                roster.stream().mapToDouble(c->c.binding().usableDefense()).min().orElse(0),roster.stream().allMatch(c->c.binding().improvesElementalResistance()),
                roster.stream().anyMatch(c->c.binding().nativeStunStaggerImmune()),roster.stream().anyMatch(c->c.binding().nativeSlowImmune()),0,
                roster.stream().allMatch(c->c.binding().distanceDisplacementDelivery()),supported);
    }
    private static EnemyAffixSelection.Binding withMinions(EnemyAffixSelection.Binding b,int count){return new EnemyAffixSelection.Binding(b.revision(),b.capabilities(),b.usableDefense(),b.improvesElementalResistance(),b.nativeStunStaggerImmune(),b.nativeSlowImmune(),count,b.distanceDisplacementDelivery(),b.supportedAffixIds());}
    /** ME-001 explicitly freezes a zero recovery bonus for a movement-only native role. */
    private EnemyDescriptor.AffixInstance ownInstance(EnemyAffixSelection.Choice choice,com.inigmasgames.hytalerpg.difficulty.DifficultyId mode,
            EnemyAffixSelection.Binding binding){
        var instance=EnemyDescriptor.ownInstance(registry.require(choice.affixId()),choice,mode,"me.affix-set/"+choice.affixId());
        String unsupported=choice.affixId().equals(Operator.EXTRA_FAST.id())&&!binding.capabilities().contains(Capability.RECOVERY_TIMELINE)
                ?"recoveryRateIncrease":choice.affixId().equals(Operator.AVENGER.id())&&!binding.capabilities().contains(Capability.MOBILE)
                ?"perDefeatedMinionMovementIncrease":null;
        if(unsupported==null)return instance;
        var parameters=new TreeMap<>(instance.parameters());parameters.put(unsupported,0.0);
        return new EnemyDescriptor.AffixInstance(instance.affixId(),instance.definitionRevision(),parameters,instance.selector(),
                instance.origin(),instance.sourceLeaderId(),instance.contributesToRewardCount(),instance.persistedDrawKey());
    }
    private boolean supportsInheritance(EnemyAffixSelection.Binding binding,List<EnemyDescriptor.AffixInstance> inherited){
        for(var affix:inherited)if(!canInherit(binding,affix.affixId(),registry.require(affix.affixId()).inheritance()))return false;
        return true;
    }
    private static boolean canInherit(EnemyAffixSelection.Binding binding,String id,Inheritance inheritance){
        var needed=switch(inheritance){
            case MOVEMENT_ONLY->Set.of(Capability.MOBILE);
            case PHYSICAL_INCREASE->Set.of(Capability.PHYSICAL_DIRECT_HIT);
            case EXTRA_ELEMENTAL_POWER_ONLY->Set.of(Capability.DIRECT_HIT);
            case POISON_PACKAGE->Set.of(Capability.DIRECT_HIT,Capability.HOSTILE_STATUS_ADMISSION,Capability.APPLIED_HIT_RECEIPTS);
            case EMPOWERED_MINION_SNAPSHOT->Set.of(Capability.DIRECT_HIT,Capability.MOBILE);
            default->Set.<Capability>of();
        };
        return binding.supportedAffixIds().contains(id)&&binding.capabilities().containsAll(needed);
    }
    private GearRandom random(String seed){return new GearRandom("master-enemies-v1.1/"+balance.revision()+"/"+seed);}
    private static String actorSeed(Request request,EnemyDescriptor actor){return RewardIntent.digest("me.actor/"+request.seed()+"/"+actor.logicalActorId());}
    private static List<Candidate> combined(List<Candidate> first,List<Candidate> second){var all=new ArrayList<>(first);all.addAll(second);return List.copyOf(all);}
}
