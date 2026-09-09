package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.server.core.entity.*;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.interaction.event.InteractionChainStartEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.diagnostics.CombatTrace;
import com.inigmasgames.hytalerpg.combat.hytale.*;
import com.inigmasgames.hytalerpg.combat.power.NativeItemPowerRegistry;
import com.inigmasgames.hytalerpg.combat.resource.RootWeaponHit;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import java.util.*;

/** Pinned execution-side witness, never a packet/button/timestamp heuristic. Native damage remains untouched. */
public final class NativeBasicAttackObserver {
    private record Root(InteractionChain chain,NativeBasicAttackPaths paths,RootWeaponHit receipt,String item){}
    private record Witness(Root root,boolean charged,double healthBefore,Ref<EntityStore> source,String victim){}
    private record CacheKey(String item,RootInteraction root,Map<String,String> variables){}
    private static final MetaKey<Witness> WITNESS=Damage.META_REGISTRY.registerMetaObject(ignored->null,false,"InigmasGames:NativeBasicHitWitness",null);
    private final Map<UUID,List<Root>> roots=new HashMap<>();
    private final LinkedHashMap<CacheKey,NativeBasicAttackPaths> paths=new LinkedHashMap<>();
    private final NativeItemPowerRegistry items=NativeItemPowerRegistry.loadCanonical();
    private final RpgCombatKernel kernel;
    private final CombatTrace trace;
    private final java.util.function.BiConsumer<UUID,Double> onHit;
    public NativeBasicAttackObserver(RpgCombatKernel kernel,CombatTrace trace,java.util.function.BiConsumer<UUID,Double> onHit){
        this.kernel=kernel;this.trace=trace;this.onHit=onHit;
    }
    public synchronized void forget(UUID actor){roots.remove(actor);}
    private synchronized void start(UUID actor,Ref<EntityStore> actorRef,InteractionChainStartEvent event){
        var chain=event.getChain();var context=event.getContext();var item=context.getOriginalItemType();
        if(event.getType()!=InteractionType.Primary||chain.getForkedChainId()!=null||context.getEntity()!=actorRef||item==null||item.getWeapon()==null)return;
        var entry=items.find(item.getId()).orElse(null);
        if(entry==null||!Set.of("SWORD","LONGSWORD","DAGGER","BATTLEAXE","MACE","SPEAR").contains(entry.kind()))return;
        if(!Objects.equals(item.getInteractions().get(InteractionType.Primary),event.getRootInteractionId()))return;
        var variables=context.getInteractionVars();if(variables!=null&&variables.size()>256)throw new IllegalStateException("NATIVE_BASIC_ITEM_VARIABLE_LIMIT");
        var key=new CacheKey(item.getId(),chain.getInitialRootInteraction(),variables==null?Map.of():Map.copyOf(variables));
        var resolved=paths.get(key);
        if(resolved==null){
            resolved=NativeBasicAttackPaths.resolve(context,chain.getInitialRootInteraction());
            if(paths.size()>=128)paths.remove(paths.keySet().iterator().next()); // Asset cache only; never a hit/dedup ledger.
            paths.put(key,resolved);
        }
        var owned=roots.get(actor);
        if(owned==null){if(roots.size()>=1024)throw new IllegalStateException("NATIVE_BASIC_OWNER_LIMIT");owned=new ArrayList<>();roots.put(actor,owned);}
        owned.removeIf(root->root.chain().getFinalState()!=InteractionState.NotFinished&&root.chain().getContext().getEntry()==null);
        if(owned.stream().anyMatch(root->root.chain()==chain))return;
        if(owned.size()>=32)throw new IllegalStateException("NATIVE_BASIC_ROOT_LIMIT");
        owned.add(new Root(chain,resolved,new RootWeaponHit(actor),item.getId()));
    }
    private synchronized Witness find(UUID actor,Ref<EntityStore> source,Ref<EntityStore> target,double before,String victim){
        var owned=roots.get(actor);if(owned==null)return null;
        Witness matched=null;int visited=0;
        var seen=Collections.newSetFromMap(new IdentityHashMap<InteractionChain,Boolean>());
        for(var root:owned){
            var queue=new ArrayDeque<InteractionChain>();queue.add(root.chain());
            while(!queue.isEmpty()){
                var chain=queue.removeFirst();if(!seen.add(chain))continue;
                if(++visited>256)throw new IllegalStateException("NATIVE_BASIC_ACTIVE_CHAIN_LIMIT");
                var c=chain.getContext();var active=c.getChain();var entry=c.getEntry();
                // Hytale sets these only inside tick and clears them in deinitEntry. Never select a merely live chain.
                if(active==chain&&entry!=null&&!entry.isUseSimulationState()&&c.getEntity()==source
                        &&chain.getType()==InteractionType.Primary&&c.getTargetEntity()==target
                        &&c.getHeldItem()!=null&&root.item().equals(c.getHeldItem().getItemId())){
                    var operationRoot=chain.getRootInteraction();int operation=c.getOperationCounter();
                    if(operation>=0&&operation<operationRoot.getOperationMax()){
                        var kind=root.paths().classify(operationRoot.getOperation(operation));
                        if(kind.isPresent()){
                            if(matched!=null)throw new IllegalStateException("NATIVE_BASIC_AMBIGUOUS_ACTIVE_DAMAGE");
                            matched=new Witness(root,kind.get()==NativeBasicAttackPaths.Kind.CHARGED,before,source,victim);
                        }
                    }
                }
                queue.addAll(chain.getForkedChains().values());queue.addAll(chain.getNewForks());
                if(queue.size()>256)throw new IllegalStateException("NATIVE_BASIC_FORK_LIMIT");
            }
        }
        return matched;
    }
    private void failure(UUID actor,String boundary){
        trace.emit(actor,RpgTraceEventType.NATIVE_BASIC_HIT_REJECTED,new CombatTrace.Context("","",""),Map.of("boundary",String.valueOf(boundary)));
    }
    public static final class Start extends EntityEventSystem<EntityStore,InteractionChainStartEvent>{
        private final NativeBasicAttackObserver owner;
        public Start(NativeBasicAttackObserver owner){super(InteractionChainStartEvent.class);this.owner=owner;}
        @Override public Query<EntityStore> getQuery(){return PlayerRef.getComponentType();}
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,InteractionChainStartEvent event){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.DAMAGE)){
            var actor=chunk.getComponent(i,PlayerRef.getComponentType()).getUuid();
            try{owner.start(actor,chunk.getReferenceTo(i),event);}catch(RuntimeException boundary){owner.failure(actor,boundary.getMessage());}

        }
    }
    }
    public static final class Before extends DamageEventSystem {
        private final NativeBasicAttackObserver owner;
        public Before(NativeBasicAttackObserver owner){this.owner=owner;}
        @Override public Query<EntityStore> getQuery(){return Query.and(EntityStatMap.getComponentType(),UUIDComponent.getComponentType());}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemGroupDependency<>(Order.AFTER,DamageModule.get().getFilterDamageGroup()),new SystemDependency<>(Order.BEFORE,DamageSystems.ApplyDamage.class));}
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.DAMAGE)){
            if(damage.isCancelled()||damage.getAmount()<=0||HytaleDamageAdapter.metadata(damage)!=null
                    ||damage.getSource()==null||damage.getSource().getClass()!=Damage.EntitySource.class||damage.getIfPresentMetaObject(Damage.INTERACTION_TYPE)!=InteractionType.Primary)return;
            var source=((Damage.EntitySource)damage.getSource()).getRef();var target=chunk.getReferenceTo(i);
            if(source==target||!source.isValid())return;
            var player=buffer.getComponent(source,PlayerRef.getComponentType());if(player==null)return;
            try{
                if(!HytaleAreaQueries.hostile(store,target,source))return;
                var hp=chunk.getComponent(i,EntityStatMap.getComponentType()).get(DefaultEntityStatTypes.getHealth());if(hp==null||hp.get()<=0)return;
                var witness=owner.find(player.getUuid(),source,target,hp.get(),chunk.getComponent(i,UUIDComponent.getComponentType()).getUuid().toString());
                if(witness!=null)damage.putMetaObject(WITNESS,witness);
            }catch(RuntimeException boundary){owner.failure(player.getUuid(),boundary.getMessage());}

        }
    }
    }
    public static final class After extends DamageEventSystem {
        private final NativeBasicAttackObserver owner;
        public After(NativeBasicAttackObserver owner){this.owner=owner;}
        @Override public Query<EntityStore> getQuery(){return EntityStatMap.getComponentType();}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.AFTER,DamageSystems.ApplyDamage.class),new SystemGroupDependency<>(Order.BEFORE,DamageModule.get().getInspectDamageGroup()));}
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.DAMAGE)){
            var witness=damage.getIfPresentMetaObject(WITNESS);if(witness==null)return;
            damage.putMetaObject(WITNESS,null);
            var hp=chunk.getComponent(i,EntityStatMap.getComponentType()).get(DefaultEntityStatTypes.getHealth());if(hp==null)return;
            var receipt=witness.root().receipt();
            if(!receipt.observe(witness.healthBefore(),hp.get(),damage.isCancelled(),true,witness.charged()))return;
            var details=new LinkedHashMap<String,Object>();details.put("item",witness.root().item());details.put("nativeRoot",witness.root().chain().getInitialRootInteraction().getId());
            details.put("target",witness.victim());details.put("charged",receipt.charged());details.put("healthBefore",witness.healthBefore());details.put("healthAfter",(double)hp.get());details.put("actualHealthLost",witness.healthBefore()-hp.get());
            try{
                var stats=buffer.getComponent(witness.source(),EntityStatMap.getComponentType());if(stats==null)throw new IllegalStateException("NATIVE_RESOURCE_MAP_MISSING");
                var recovery=owner.kernel.resources().recoverHostileWeaponHit(receipt,new EntityStatResourcePort(stats));
                details.put("recoveryApplied",recovery.applied());details.put("manaRecovered",recovery.manaRecovered());details.put("staminaRecovered",recovery.staminaRecovered());
            }catch(RuntimeException boundary){details.put("recoveryFailure",boundary.getMessage());}
            try{owner.onHit.accept(receipt.actor(),System.nanoTime()/1e9);}
            catch(RuntimeException boundary){details.put("comboFailure",String.valueOf(boundary.getMessage()));}
            owner.trace.emit(receipt.actor(),RpgTraceEventType.NATIVE_BASIC_HIT_OBSERVED,new CombatTrace.Context(receipt.id(),receipt.id(),receipt.id()),details);

        }
    }
    }
}
