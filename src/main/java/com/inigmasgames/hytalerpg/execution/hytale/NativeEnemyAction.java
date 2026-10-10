package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.*;
import com.hypixel.hytale.server.core.asset.type.projectile.config.Projectile;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.LaunchProjectileInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.hytalerpg.combat.damage.WeaponDamageExecution;
import com.inigmasgames.hytalerpg.combat.hytale.NativeDamageLeafInteraction;
import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytale.patch.NativeProjectileReceiptHook;
import java.util.*;
import java.util.function.*;

/** Acceptance-time adapter for certified native melee graphs. No attack, victim query or damage submission. */
public final class NativeEnemyAction {
    public record Strike(String authoredId,String producer,double procCoefficient,NativeEnemyDamageInteraction leaf,EnemyStatusEffects.Source statusSource){
        public Strike(String authoredId,String producer,double procCoefficient,NativeEnemyDamageInteraction leaf){this(authoredId,producer,procCoefficient,leaf,null);}
        public Strike{
            id(authoredId);id(producer);Objects.requireNonNull(leaf);
            if(!Double.isFinite(procCoefficient)||procCoefficient<0||procCoefficient>1)throw new IllegalArgumentException("ENEMY_ACTION_PROC_COEFFICIENT");
            leaf.acceptanceCalculator();
            // Nonzero native status sources need a merged native-owner bridge before this adapter can bind them.
            if(statusSource!=null&&statusSource.existingChance().values().stream().anyMatch(chance->chance!=0))
                throw new IllegalArgumentException("ENEMY_NATIVE_STATUS_MERGE_OWNER_NOT_BOUND");
        }
    }
    /** One existing native launch; its original holder and later native impact remain Hytale owned. */
    public record ProjectileStrike(String authoredId,String producer,double procCoefficient,
            LaunchProjectileInteraction leaf,EnemyStatusEffects.Source statusSource){
        public ProjectileStrike{
            id(authoredId);id(producer);Objects.requireNonNull(leaf);
            if(!Double.isFinite(procCoefficient)||procCoefficient<0||procCoefficient>1)
                throw new IllegalArgumentException("ENEMY_PROJECTILE_PROC_COEFFICIENT");
            if(statusSource!=null&&statusSource.existingChance().values().stream().anyMatch(chance->chance!=0))
                throw new IllegalArgumentException("ENEMY_NATIVE_STATUS_MERGE_OWNER_NOT_BOUND");
        }
    }
    /** Uses actual resolved assets, not an ID substring or a merely registered damage handler. */
    public record Binding(String revision,RootInteraction root,InteractionType type,List<Strike> strikes,
            List<ProjectileStrike> projectileStrikes,Map<String,Integer> contactOccurrences){
        public Binding(String revision,RootInteraction root,InteractionType type,List<Strike> strikes){
            this(revision,root,type,strikes,List.of(),Map.of());
        }
        public Binding(String revision,RootInteraction root,InteractionType type,List<Strike> strikes,
                List<ProjectileStrike> projectileStrikes){
            this(revision,root,type,strikes,projectileStrikes,Map.of());
        }
        public Binding{
            id(revision);Objects.requireNonNull(root);Objects.requireNonNull(type);
            strikes=List.copyOf(strikes);projectileStrikes=List.copyOf(projectileStrikes);
            contactOccurrences=Map.copyOf(contactOccurrences);
            if(strikes.size()+projectileStrikes.size()<1||strikes.size()+projectileStrikes.size()>32)
                throw new IllegalArgumentException("ENEMY_ACTION_STRIKE_BOUNDS");
            var ids=new HashSet<String>();var leaves=Collections.newSetFromMap(new IdentityHashMap<NativeEnemyDamageInteraction,Boolean>());
            for(var strike:strikes)if(!ids.add(strike.authoredId())||!leaves.add(strike.leaf()))throw new IllegalArgumentException("ENEMY_ACTION_DUPLICATE_STRIKE");
            var launches=Collections.newSetFromMap(new IdentityHashMap<LaunchProjectileInteraction,Boolean>());
            for(var strike:projectileStrikes)if(!ids.add(strike.authoredId())||!launches.add(strike.leaf()))
                throw new IllegalArgumentException("ENEMY_ACTION_DUPLICATE_PROJECTILE_STRIKE");
            if(!contactOccurrences.isEmpty()&&(strikes.size()<2||!projectileStrikes.isEmpty()
                    ||!contactOccurrences.keySet().equals(strikes.stream().map(Strike::authoredId).collect(java.util.stream.Collectors.toSet()))
                    ||contactOccurrences.values().stream().anyMatch(count->count==null||count<1||count>16)))
                throw new IllegalArgumentException("ENEMY_ACTION_NATIVE_CONTACT_CERTIFICATE");
        }
        public void validate(InteractionContext context){
            var graph=NativeBasicAttackPaths.resolve(context,root,type);var occurrences=graph.damageOccurrences();
            if(occurrences.size()!=strikes.size()||graph.projectileOccurrences().size()!=projectileStrikes.size())
                throw new IllegalStateException("ENEMY_ACTION_UNBOUND_STRIKE_ROUTE");
            for(var strike:strikes){
                int visits=occurrences.getOrDefault(strike.leaf(),0);
                int expected=contactOccurrences.getOrDefault(strike.authoredId(),1);
                if(contactOccurrences.isEmpty()
                        ?visits<1||visits!=1&&!graph.exclusiveChainingDamage(strike.leaf())
                        :visits!=expected)
                    throw new IllegalStateException("ENEMY_ACTION_REPEATED_OR_MISSING_DAMAGE_ROUTE");
                // A multi-variable native chain gives each authored leaf its own frozen identity.
                // Single-strike bindings still reject follow-up and charged alternatives.
                if(graph.classify(strike.leaf()).orElse(null)!=NativeBasicAttackPaths.Kind.NORMAL)
                    throw new IllegalStateException("ENEMY_ACTION_CONDITIONAL_DAMAGE_ROUTE");
                strike.leaf().acceptanceCalculator();
            }
            for(var strike:projectileStrikes){
                if(!Integer.valueOf(1).equals(graph.projectileOccurrences().get(strike.leaf()))
                        ||graph.classify(strike.leaf()).orElse(null)!=NativeBasicAttackPaths.Kind.NORMAL)
                    throw new IllegalStateException("ENEMY_ACTION_REPEATED_OR_CONDITIONAL_PROJECTILE_ROUTE");
                var projectile=Projectile.getAssetMap().getAsset(strike.leaf().getProjectileId());
                if(projectile==null||projectile.getDamage()<=0)
                    throw new IllegalStateException("ENEMY_ACTION_PROJECTILE_ASSET_UNSUPPORTED");
            }
        }
        /** Resolve the actual native Role Replace graph, rather than binding a named parent leaf by guess. */
        public static Binding certifySingle(String revision,NPCEntity npc,String rootId,InteractionType type,
                String strikeId,String producer,EnemyStatusEffects.Source statusSource){
            Objects.requireNonNull(npc);id(rootId);
            var role=npc.getRole();
            var root=RootInteraction.getAssetMap().getAsset(rootId);
            if(role==null||root==null)throw new IllegalStateException("ENEMY_ACTION_NATIVE_ROOT_MISSING");
            var context=InteractionContext.withoutEntity();
            context.setInteractionVarsGetter(ignored->role.getInteractionVars());
            return certifySingle(revision,root,type,context,strikeId,producer,statusSource);
        }
        /** Also used by the offline native graph fixture with an installed equivalent root. */
        public static Binding certifySingle(String revision,RootInteraction root,InteractionType type,InteractionContext context,
                String strikeId,String producer,EnemyStatusEffects.Source statusSource){
            var graph=NativeBasicAttackPaths.resolve(context,root,type);
            if(graph.damageOccurrences().size()!=1)throw new IllegalStateException("ENEMY_ACTION_NOT_SINGLE_STRIKE");
            var entry=graph.damageOccurrences().entrySet().iterator().next();
            if(!(entry.getKey() instanceof NativeEnemyDamageInteraction leaf)
                    ||entry.getValue()!=1&&!graph.exclusiveChainingDamage(leaf)
                    ||graph.classify(leaf).orElse(null)!=NativeBasicAttackPaths.Kind.NORMAL)
                throw new IllegalStateException("ENEMY_ACTION_NATIVE_LEAF_UNCERTIFIED");
            var binding=new Binding(revision,root,type,List.of(new Strike(strikeId,producer,1,leaf,statusSource)));
            binding.validate(context);return binding;
        }
        /** Certify one native chain containing several authored Replace-variable damage leaves. */
        public static Binding certifyNativeVariables(String revision,RootInteraction root,InteractionType type,
                InteractionContext context,Map<String,String> nativeVariables,List<String> strikeKeys,
                String producer,EnemyStatusEffects.Source statusSource){
            return certifyNativeVariables(revision,root,type,context,nativeVariables,strikeKeys,producer,statusSource,Map.of());
        }
        /** Existing repeated native contacts share one accepted action receipt per strike/victim.
         * The exact installed contact count is part of this binding's fail-closed certificate. */
        public static Binding certifyNativeVariables(String revision,RootInteraction root,InteractionType type,
                InteractionContext context,Map<String,String> nativeVariables,List<String> strikeKeys,
                String producer,EnemyStatusEffects.Source statusSource,Map<String,Integer> contactOccurrences){
            if(strikeKeys.size()<2||strikeKeys.size()>32||new HashSet<>(strikeKeys).size()!=strikeKeys.size())
                throw new IllegalArgumentException("ENEMY_ACTION_NATIVE_VARIABLE_KEYS");
            var graph=NativeBasicAttackPaths.resolve(context,root,type);
            if(graph.damageOccurrences().size()!=strikeKeys.size()||!graph.projectileOccurrences().isEmpty())
                throw new IllegalStateException("ENEMY_ACTION_NATIVE_VARIABLE_GRAPH_CHANGED");
            var strikes=new ArrayList<Strike>();
            for(var key:strikeKeys){
                var nativeRootId=nativeVariables.get(key);
                var nativeRoot=nativeRootId==null?null:RootInteraction.getAssetMap().getAsset(nativeRootId);
                if(nativeRoot==null)throw new IllegalStateException("ENEMY_ACTION_NATIVE_VARIABLE_MISSING:"+key);
                var variableGraph=NativeBasicAttackPaths.resolve(context,nativeRoot,type);
                if(variableGraph.damageOccurrences().size()!=1||!variableGraph.projectileOccurrences().isEmpty())
                    throw new IllegalStateException("ENEMY_ACTION_NATIVE_VARIABLE_NOT_SINGLE:"+key);
                var leaf=variableGraph.damageOccurrences().keySet().iterator().next();
                if(!Integer.valueOf(1).equals(variableGraph.damageOccurrences().get(leaf))
                        ||!(leaf instanceof NativeEnemyDamageInteraction routed)
                        ||!graph.damageOccurrences().containsKey(routed)
                        ||variableGraph.classify(routed).orElse(null)!=NativeBasicAttackPaths.Kind.NORMAL)
                    throw new IllegalStateException("ENEMY_ACTION_NATIVE_VARIABLE_UNCERTIFIED:"+key);
                strikes.add(new Strike(key,producer,1,routed,statusSource));
            }
            var binding=new Binding(revision,root,type,strikes,List.of(),contactOccurrences);
            binding.validate(context);return binding;
        }
        /** Two mutually exclusive native cooldown paths, each with its own original damage leaf. */
        public static Binding certifyConditionalLeaves(String revision,RootInteraction root,InteractionType type,
                InteractionContext context,List<String> strikeKeys,String producer,
                EnemyStatusEffects.Source statusSource){
            if(strikeKeys.size()!=2||new HashSet<>(strikeKeys).size()!=2)
                throw new IllegalArgumentException("ENEMY_CONDITIONAL_STRIKE_KEYS");
            var graph=NativeBasicAttackPaths.resolve(context,root,type);
            if(graph.damageOccurrences().size()!=2||graph.orderedDamage().size()!=2
                    ||!graph.projectileOccurrences().isEmpty())
                throw new IllegalStateException("ENEMY_CONDITIONAL_NATIVE_GRAPH_CHANGED");
            var strikes=new ArrayList<Strike>();
            for(int index=0;index<2;index++){
                var nativeLeaf=graph.orderedDamage().get(index);
                if(!(nativeLeaf instanceof NativeEnemyDamageInteraction leaf)
                        ||!Integer.valueOf(1).equals(graph.damageOccurrences().get(leaf))
                        ||graph.classify(leaf).orElse(null)!=NativeBasicAttackPaths.Kind.NORMAL)
                    throw new IllegalStateException("ENEMY_CONDITIONAL_NATIVE_LEAF_UNCERTIFIED");
                strikes.add(new Strike(strikeKeys.get(index),producer,1,leaf,statusSource));
            }
            var binding=new Binding(revision,root,type,strikes);
            binding.validate(context);return binding;
        }
        public static Binding certifyProjectileSingle(String revision,NPCEntity npc,String rootId,InteractionType type,
                String strikeId,String producer,EnemyStatusEffects.Source statusSource){
            Objects.requireNonNull(npc);id(rootId);
            var role=npc.getRole();var root=RootInteraction.getAssetMap().getAsset(rootId);
            if(role==null||root==null)throw new IllegalStateException("ENEMY_ACTION_NATIVE_ROOT_MISSING");
            var context=InteractionContext.withoutEntity();
            context.setInteractionVarsGetter(ignored->role.getInteractionVars());
            return certifyProjectileSingle(revision,root,type,context,strikeId,producer,statusSource);
        }
        public static Binding certifyProjectileSingle(String revision,RootInteraction root,InteractionType type,
                InteractionContext context,String strikeId,String producer,EnemyStatusEffects.Source statusSource){
            var graph=NativeBasicAttackPaths.resolve(context,root,type);
            if(!graph.damageOccurrences().isEmpty()||graph.projectileOccurrences().size()!=1)
                throw new IllegalStateException("ENEMY_ACTION_NOT_SINGLE_PROJECTILE");
            var entry=graph.projectileOccurrences().entrySet().iterator().next();
            if(entry.getValue()!=1||graph.classify(entry.getKey()).orElse(null)!=NativeBasicAttackPaths.Kind.NORMAL)
                throw new IllegalStateException("ENEMY_ACTION_NATIVE_LAUNCH_UNCERTIFIED");
            var binding=new Binding(revision,root,type,List.of(),List.of(new ProjectileStrike(
                    strikeId,producer,1,entry.getKey(),statusSource)));
            binding.validate(context);return binding;
        }
    }
    private final Ref<EntityStore> actor;
    private final InteractionChain root;
    private final Binding binding;
    private final Map<NativeEnemyDamageInteraction,EnemyOffenseSnapshot> snapshots;
    private record ProjectileSource(EnemyOffenseSnapshot offense,int nativeBaseDamage){}
    private final Map<LaunchProjectileInteraction,ProjectileSource> projectileSnapshots;
    private final BooleanSupplier current;
    private final com.inigmasgames.hytalerpg.execution.math.Vec3 forward;
    private final Set<ReceiptKey> receipts=new HashSet<>();
    private record ReceiptKey(String strike,UUID victim){}
    private NativeEnemyAction(Ref<EntityStore> actor,InteractionChain root,Binding binding,
            Map<NativeEnemyDamageInteraction,EnemyOffenseSnapshot> snapshots,
            Map<LaunchProjectileInteraction,ProjectileSource> projectileSnapshots,
            BooleanSupplier current,com.inigmasgames.hytalerpg.execution.math.Vec3 forward){
        this.actor=actor;this.root=root;this.binding=binding;
        this.snapshots=Collections.unmodifiableMap(new IdentityHashMap<>(snapshots));
        this.projectileSnapshots=Collections.unmodifiableMap(new IdentityHashMap<>(projectileSnapshots));
        this.current=current;this.forward=forward;
    }
    /** rootId is allocated by the encounter's replay owner before this call. No collision-time RNG or timestamp IDs. */
    public static NativeEnemyAction capture(Store<EntityStore> store,Ref<EntityStore> actor,InteractionChain chain,
            Binding binding,EnemyDescriptor descriptor,EnemyAffixSnapshot providers,String rootId,String encounterSeed,
            double difficultyFactor,double nativeSupportFactor,double directWeakening,NativeEnemyOutgoingEffects outgoing,BooleanSupplier current){
        Objects.requireNonNull(current);id(rootId);id(encounterSeed);
        if(!store.isInThread()||actor==null||!actor.isValid()||chain.getForkedChainId()!=null
                ||chain.getInitialRootInteraction()!=binding.root()||chain.getType()!=binding.type()
                ||chain.getContext().getEntity()!=actor||!descriptor.nativeBindingRevision().equals(binding.revision())||!current.getAsBoolean())
            throw new IllegalStateException("ENEMY_ACTION_ACCEPTANCE_BINDING");
        binding.validate(chain.getContext());
        var values=new IdentityHashMap<NativeEnemyDamageInteraction,EnemyOffenseSnapshot>();
        // Stable authored order prevents unordered maps from changing which native random sample belongs to a strike.
        for(var strike:binding.strikes().stream().sorted(Comparator.comparing(Strike::authoredId)).toList()){
            var vector=outgoing.snapshot(store,actor,strike.leaf().sampleAtAcceptance());
            var identity=new WeaponDamageExecution.Identity(descriptor.worldId(),descriptor.logicalActorId(),rootId,binding.root().getId(),strike.authoredId());
            values.put(strike.leaf(),EnemyOffenseSnapshot.freeze(descriptor,identity,vector,strike.producer(),difficultyFactor,nativeSupportFactor,
                    strike.procCoefficient(),providers,directWeakening,encounterSeed));
        }
        var launches=new IdentityHashMap<LaunchProjectileInteraction,ProjectileSource>();
        for(var strike:binding.projectileStrikes().stream().sorted(Comparator.comparing(ProjectileStrike::authoredId)).toList()){
            var projectile=Projectile.getAssetMap().getAsset(strike.leaf().getProjectileId());
            if(projectile==null||projectile.getDamage()<=0)throw new IllegalStateException("ENEMY_ACCEPTED_PROJECTILE_CHANGED");
            var vector=outgoing.snapshot(store,actor,Map.of(DamageCause.PROJECTILE,(float)projectile.getDamage()));
            var identity=new WeaponDamageExecution.Identity(descriptor.worldId(),descriptor.logicalActorId(),rootId,
                    binding.root().getId(),strike.authoredId());
            launches.put(strike.leaf(),new ProjectileSource(EnemyOffenseSnapshot.freeze(descriptor,identity,vector,
                    strike.producer(),difficultyFactor,nativeSupportFactor,strike.procCoefficient(),providers,
                    directWeakening,encounterSeed),projectile.getDamage()));
        }
        if(!current.getAsBoolean())throw new IllegalStateException("ENEMY_ACTION_ACCEPTANCE_GENERATION_CHANGED");
        com.inigmasgames.hytalerpg.execution.math.Vec3 forward=null;
        if(descriptor.own(EnemyAffixRegistry.Operator.KNOCKBACK).isPresent()&&binding.strikes().stream().anyMatch(s->!s.leaf().ownsNativeImpulse())){
            var head=store.getComponent(actor,com.hypixel.hytale.server.core.modules.entity.component.HeadRotation.getComponentType());
            var transform=store.getComponent(actor,com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
            if(head==null&&transform==null)throw new IllegalStateException("ENEMY_STRIKE_FORWARD_MISSING");
            var direction=head==null?transform.getRotation().transform(new org.joml.Vector3d(0,0,1)):head.getDirection();
            forward=new com.inigmasgames.hytalerpg.execution.math.Vec3(direction.x(),0,direction.z());
            if(forward.horizontalLength()<1e-9)throw new IllegalStateException("ENEMY_STRIKE_FORWARD_DEGENERATE");
            forward=forward.horizontalNormalized();
        }
        return new NativeEnemyAction(actor,chain,binding,values,launches,current,forward);
    }
    public InteractionChain root(){return root;}
    public EnemyOffenseSnapshot snapshot(NativeEnemyDamageInteraction leaf){
        var result=snapshots.get(leaf);if(result==null)throw new IllegalArgumentException("ENEMY_ACTION_UNKNOWN_LEAF");return result;
    }
    public EnemyOffenseSnapshot projectileSnapshot(LaunchProjectileInteraction leaf){
        var result=projectileSnapshots.get(leaf);
        if(result==null)throw new IllegalArgumentException("ENEMY_ACTION_UNKNOWN_PROJECTILE_LEAF");
        return result.offense();
    }
    public record ProjectileLaunch(String nativeProjectileAsset,int nativeBaseDamage,EnemyOffenseSnapshot offense){}
    /** Match the patched launch coordinates to this already accepted root, never to shooter/time proximity. */
    public Optional<ProjectileLaunch> projectileLaunch(NativeProjectileReceiptHook.Context context){
        if(!current.getAsBoolean()||!actor.isValid()||context.projectile()==null
                ||!binding.root().getId().equals(context.initialRoot()))return Optional.empty();
        var queue=new ArrayDeque<InteractionChain>();queue.add(root);
        var seen=Collections.newSetFromMap(new IdentityHashMap<InteractionChain,Boolean>());
        ProjectileLaunch found=null;
        while(!queue.isEmpty()){
            var chain=queue.removeFirst();if(!seen.add(chain))continue;
            if(seen.size()>256||queue.size()+chain.getForkedChains().size()+chain.getNewForks().size()>256)
                throw new IllegalStateException("ENEMY_NATIVE_FORK_BOUNDS");
            if(chain.getChainId()==context.chainId()&&forkPath(chain.getForkedChainId()).equals(context.forkPath())
                    &&chain.getRootInteraction().getId().equals(context.operationRoot())
                    &&chain.getOperationIndex()==context.operationIndex()){
                int operation=context.operationCounter();var operationRoot=chain.getRootInteraction();
                if(operation<0||operation>=operationRoot.getOperationMax())
                    throw new IllegalStateException("ENEMY_PROJECTILE_OPERATION_OUT_OF_BOUNDS");
                for(var strike:binding.projectileStrikes())if(operationRoot.getOperation(operation).getInnerOperation()==strike.leaf()){
                    if(found!=null)throw new IllegalStateException("ENEMY_PROJECTILE_AMBIGUOUS_ACCEPTED_ROOT");
                    var source=projectileSnapshots.get(strike.leaf());
                    found=new ProjectileLaunch(strike.leaf().getProjectileId(),source.nativeBaseDamage(),source.offense());
                }
            }
            queue.addAll(chain.getForkedChains().values());queue.addAll(chain.getNewForks());
        }
        return Optional.ofNullable(found);
    }
    static List<Integer> forkPath(com.hypixel.hytale.protocol.ForkedChainId fork){
        var parts=new ArrayList<Integer>();int depth=0;
        while(fork!=null){
            if(++depth>16)throw new IllegalStateException("ENEMY_PROJECTILE_FORK_DEPTH");
            parts.add(fork.entryIndex);parts.add(fork.subIndex);fork=fork.forkedId;
        }
        return List.copyOf(parts);
    }
    /** Exact native ancestry, active operation and actor; another live chain is insufficient. */
    public boolean owns(NativeDamageLeafInteraction.Invocation call){
        if(!snapshots.containsKey(call.leaf()))return false;
        var context=call.context();var chain=context.getChain();
        if(context.getEntity()!=actor||context.getOwningEntity()!=actor||chain==null||context.getEntry()==null
                ||context.getEntry().isUseSimulationState()||chain.getType()!=binding.type())return false;
        int operation=context.getOperationCounter();var operationRoot=chain.getRootInteraction();
        if(operation<0||operation>=operationRoot.getOperationMax()
                ||operationRoot.getOperation(operation).getInnerOperation()!=call.leaf())return false;
        return acceptedLineage(root,chain);
    }
    /** Native Selector hit forks may be detached from the parent's mutable fork map while
     *  they are still ticking. Hytale copies the server-issued chain ID into every fork;
     *  a non-root fork ID proves this is a descendant of that accepted attack. The
     *  caller has already checked actor, action type and the exact certified leaf. */
    static boolean acceptedLineage(InteractionChain accepted,InteractionChain executing){
        if(accepted==executing)return true;
        return accepted.getForkedChainId()==null&&executing.getForkedChainId()!=null
                &&accepted.getChainId()<0&&accepted.getChainId()==executing.getChainId();
    }
    public NativeEnemyDamageInteraction.EnemyScope scope(NativeDamageLeafInteraction.Invocation call,
            Consumer<EnemyAppliedHit> accepted,Consumer<Throwable> rejected){
        if(!owns(call))throw new IllegalStateException("ENEMY_ACTION_ACTIVE_LEAF_MISMATCH");
        if(!current.getAsBoolean()||!actor.isValid())throw new IllegalStateException("ENEMY_ACTION_STALE_GENERATION");
        var target=call.context().getTargetEntity();var buffer=call.context().getCommandBuffer();
        if(target==null||!target.isValid()||target==actor)throw new IllegalStateException("ENEMY_ACTION_INVALID_TARGET");
        var uuid=buffer.getComponent(target,UUIDComponent.getComponentType());
        var nativeActorId=buffer.getComponent(actor,UUIDComponent.getComponentType());
        if(uuid==null||nativeActorId==null)throw new IllegalStateException("ENEMY_ACTION_NATIVE_IDENTITY_MISSING");
        var leaf=(NativeEnemyDamageInteraction)call.leaf();var snapshot=snapshot(leaf);
        return new EnemyNativeStrikeScope(snapshot,nativeActorId.getUuid(),uuid.getUuid(),leaf.acceptanceCalculator(),
                ()->actor.isValid()&&current.getAsBoolean(),hit->{
                    // Native repeated contacts retain their damage; they cannot duplicate the ME opportunity.
                    var key=new ReceiptKey(snapshot.identity().authoredTickId(),hit.victim());
                    if(hit.actualHealthLoss()>0&&!receipts.contains(key)){
                        if(receipts.size()>=4096)throw new IllegalStateException("ENEMY_ACTION_RECEIPT_BOUNDS");
                        receipts.add(key);accepted.accept(hit);
                    }
                },rejected);
    }
    private static void id(String value){if(value==null||value.isBlank()||value.length()>512||value.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("ENEMY_ACTION_ID");}
    public EnemyStatusEffects.Source statusSource(NativeEnemyDamageInteraction leaf){return binding.strikes().stream().filter(s->s.leaf()==leaf).findFirst().orElseThrow().statusSource();}
    public com.inigmasgames.hytalerpg.execution.math.Vec3 forward(){return forward;}
}
