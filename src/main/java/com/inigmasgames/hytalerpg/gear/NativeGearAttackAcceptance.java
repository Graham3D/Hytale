package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.modules.interaction.event.InteractionChainStartEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.SelectInteraction;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Accepted native strike facts. The damage leaf records this at calculation time, never at impact. */
public final class NativeGearAttackAcceptance {
    public static final String PROC_SELECTOR="RpgProcSelector",PROC_COEFFICIENT="RpgProcCoefficient";
    /** HytaleSkillExecutionSystem's Iron Sentinel callback submits one target per authored attack ordinal. */
    public static final double SENTINEL_SINGLE_ROOT_PROC_COEFFICIENT=1;
    private static final java.util.Set<String> ATTACK_SIGNATURES=java.util.Set.of(
            "WA-135","WA-136","WA-137","WA-138","WA-139","WA-140","WA-143");
    public static boolean hasAttackSignature(GearInstance item){
        return item!=null&&item.affixes().stream().anyMatch(a->ATTACK_SIGNATURES.contains(a.familyId()));
    }
    public record Chain(UUID world,UUID actor,UUID item,GearEffectSnapshot snapshot,
                        double baselineCritChance,double baselineCritMultiplier,String selector,
                        com.inigmasgames.hytalerpg.execution.math.Vec3 origin) {
        public Chain(UUID world,UUID actor,UUID item,GearEffectSnapshot snapshot,
                     double baselineCritChance,double baselineCritMultiplier,String selector) {
            this(world,actor,item,snapshot,baselineCritChance,baselineCritMultiplier,selector,null);
        }
        public Chain {
            Objects.requireNonNull(world);Objects.requireNonNull(actor);Objects.requireNonNull(item);
            Objects.requireNonNull(snapshot);Objects.requireNonNull(selector);
            if(snapshot.forItem(item).empty()||selector.isBlank()||!Double.isFinite(baselineCritChance)
                    ||baselineCritChance<0||baselineCritChance>1||!Double.isFinite(baselineCritMultiplier)
                    ||baselineCritMultiplier<1)throw new IllegalArgumentException("INVALID_NATIVE_GEAR_CHAIN");
        }
    }
    public record Accepted(UUID world,UUID actor,GearCombatEffects.Hit hit,double noncriticalPhysical,
                           double procCoefficient,boolean melee,boolean noProc,String authoredSelector) {
        public Accepted {
            Objects.requireNonNull(world);Objects.requireNonNull(actor);Objects.requireNonNull(hit);
            Objects.requireNonNull(authoredSelector);
            if(hit.itemId()==null||hit.snapshot().forItem(hit.itemId()).empty()
                    ||hit.rootId().isBlank()||authoredSelector.isBlank()
                    ||!Double.isFinite(noncriticalPhysical)||noncriticalPhysical<0
                    ||!Double.isFinite(procCoefficient)||procCoefficient<0||procCoefficient>1)
                throw new IllegalArgumentException("INVALID_ACCEPTED_GEAR_STRIKE");
        }
    }
    private record Key(UUID world,UUID actor,String root) {}
    private record Entry(Accepted accepted,long expiresAt) {}
    /** The root is the proc budget, while each committed strike has its own immutable envelope. */
    private static final class StrikeKey {
        final Key root;
        final GearCombatEffects.Hit hit;
        StrikeKey(UUID world,UUID actor,GearCombatEffects.Hit hit) {
            root=new Key(world,actor,hit.rootId());this.hit=hit;
        }
        @Override public int hashCode(){return 31*root.hashCode()+System.identityHashCode(hit);}
        @Override public boolean equals(Object other){return other instanceof StrikeKey k&&root.equals(k.root)&&hit==k.hit;}
    }
    private record RootSource(UUID item,GearEffectSnapshot snapshot,long expiresAt) {}
    private record ChainEntry(Chain chain,long expiresAt) {}
    private static final Map<StrikeKey,Entry> ACCEPTED=new HashMap<>();
    private static final Map<Key,RootSource> SOURCES=new HashMap<>();
    private static final Map<Key,ChainEntry> CHAINS=new HashMap<>();
    private static final int MAX=4096;
    private static final long LIFETIME=60_000_000_000L;
    private static final NativeItemProcBudget ITEM_PROC_BUDGET=new NativeItemProcBudget();
    public static NativeItemProcBudget itemProcBudget(){return ITEM_PROC_BUDGET;}
    private static volatile GearSignatureProcRuntime installed;
    private NativeGearAttackAcceptance() {}

    /** Register after equipment Use: the leaf and released projectile read this immutable acceptance. */
    public static final class Prechain extends EntityEventSystem<EntityStore,InteractionChainStartEvent> {
        public Prechain(){super(InteractionChainStartEvent.class);}
        @Override public Query<EntityStore> getQuery(){return PlayerRef.getComponentType();}
        @Override public java.util.Set<Dependency<EntityStore>> getDependencies(){return java.util.Set.of(
                new SystemDependency<>(Order.AFTER,HytaleGearEquipment.Use.class));}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer,InteractionChainStartEvent event){
            if(event.isCancelled()||!GearNativeItems.managed(event.getContext().getHeldItem()))return;
            if(ManagedGearProjectile.hasSnapshot(event.getContext().getEntity(),buffer)
                    ||ManagedCarrierProjectile.hasSnapshot(event.getContext().getEntity(),buffer))return;
            var actor=chunk.getReferenceTo(index);
            if(!GearNativeItems.canUse(event.getContext().getHeldItem(),actor,buffer))return;
            var gear=GearNativeItems.read(event.getContext().getHeldItem());
            var effects=GearNativeItems.effects(actor,buffer).snapshot();
            if(effects.forItem(gear.identity()).empty())return;
            var derived=GearNativeItems.attributeDerived(actor,buffer);
            var player=chunk.getComponent(index,PlayerRef.getComponentType());
            var transform=buffer.getComponent(actor,com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
            var at=transform==null?null:transform.getPosition();
            var origin=at==null?null:new com.inigmasgames.hytalerpg.execution.math.Vec3(at.x,at.y,at.z);
            var accepted=new Chain(store.getExternalData().getWorld().getWorldConfig().getUuid(),
                    player.getUuid(),gear.identity(),effects,derived.criticalChance(),
                    derived.criticalMultiplier(),event.getRootInteractionId(),origin);
            captureChain(Integer.toString(event.getChain().getChainId()),accepted);
        }
    }
    public static synchronized void captureChain(String chainId,Chain chain){
        if(chainId==null||chainId.isBlank())throw new IllegalArgumentException("INVALID_NATIVE_CHAIN_ID");
        long now=System.nanoTime();CHAINS.entrySet().removeIf(row->row.getValue().expiresAt()<now);
        var key=new Key(chain.world(),chain.actor(),chainId);
        var old=CHAINS.get(key);
        if(old!=null&&!old.chain().equals(chain))throw new IllegalStateException("NATIVE_CHAIN_SOURCE_DRIFT");
        if(old==null&&CHAINS.size()>=MAX)throw new IllegalStateException("NATIVE_CHAIN_CAPACITY");
        CHAINS.putIfAbsent(key,new ChainEntry(chain,now+LIFETIME));
    }
    public static synchronized Chain chain(UUID world,UUID actor,String chainId){
        var key=new Key(world,actor,chainId);var row=CHAINS.get(key);
        if(row==null)return null;
        if(row.expiresAt()<System.nanoTime()){CHAINS.remove(key);return null;}
        return row.chain();
    }

    public static synchronized void bind(GearSignatureProcRuntime runtime){
        if(installed!=null&&installed!=runtime)throw new IllegalStateException("SIGNATURE_RUNTIME_ALREADY_BOUND");
        installed=Objects.requireNonNull(runtime);
    }
    public static synchronized void unbind(GearSignatureProcRuntime runtime){
        if(installed==runtime){installed=null;ACCEPTED.clear();SOURCES.clear();CHAINS.clear();ITEM_PROC_BUDGET.clear();}
    }
    public static GearCombatEffects.Hit commit(UUID world,UUID actor,GearCombatEffects.Hit hit,
            double procCoefficient,boolean melee,String authoredSelector){
        var runtime=installed;
        if(runtime==null)throw new IllegalStateException("SIGNATURE_RUNTIME_NOT_INSTALLED");
        return commit(world,actor,hit,procCoefficient,melee,authoredSelector,runtime);
    }
    /** Native selector variables are the authority; every pellet/strike must carry its own entry. */
    public static GearCombatEffects.Hit commit(UUID world,UUID actor,GearCombatEffects.Hit hit,
            boolean melee,String authoredSelector,Map<String,String> nativeVariables){
        if(!GearCombatEffects.needsNativeEnvelope(hit))return hit;
        return commit(world,actor,hit,coefficient(authoredSelector,nativeVariables),melee,authoredSelector);
    }
    public static GearCombatEffects.Hit commit(Chain chain,GearCombatEffects.Hit hit,boolean melee,
            String authoredSelector,Map<String,String> nativeVariables){
        if(chain==null||hit==null||!chain.item().equals(hit.itemId())||chain.snapshot()!=hit.snapshot())
            throw new IllegalArgumentException("NATIVE_GEAR_ACCEPTANCE_DRIFT");
        return commit(chain.world(),chain.actor(),hit,melee,authoredSelector,nativeVariables);
    }
    public static double coefficient(String authoredSelector,Map<String,String> nativeVariables){
        Objects.requireNonNull(nativeVariables);
        if(!Objects.equals(authoredSelector,nativeVariables.get(PROC_SELECTOR)))
            throw new IllegalArgumentException("NATIVE_PROC_SELECTOR_MISMATCH");
        String encoded=nativeVariables.get(PROC_COEFFICIENT);
        if(encoded==null)throw new IllegalArgumentException("NATIVE_PROC_COEFFICIENT_MISSING");
        final double value;
        try {value=Double.parseDouble(encoded);}catch(NumberFormatException invalid){
            throw new IllegalArgumentException("NATIVE_PROC_COEFFICIENT_INVALID",invalid);
        }
        if(!Double.isFinite(value)||value<0||value>1)
            throw new IllegalArgumentException("NATIVE_PROC_COEFFICIENT_INVALID");
        return value;
    }

    /** Native Selector has completed selection before it forks any damage child. RPG selectors have
     * no reservoir cap or alternate hit rules, so its public hit set is the selected population.
     * Apply the selector's own invulnerability gate and the receipt's hostile/protection gates. */
    public static double selectedAreaCoefficient(InteractionContext context,UUID world,UUID actor,
            GearCombatEffects.Hit hit,double authored){
        var parent=context.getMetaStore().getMetaObject(SelectInteraction.SELECT_META_STORE);
        if(parent==null)return authored;
        var selected=parent.getMetaObject(SelectInteraction.HIT_ENTITIES);
        if(selected==null)return 0;
        var target=context.getMetaStore().getMetaObject(Interaction.TARGET_ENTITY);
        var buffer=context.getCommandBuffer();
        if(target==null||!target.isValid()||buffer==null)return 0;
        var targetId=buffer.getComponent(target,UUIDComponent.getComponentType());
        if(targetId==null)return 0;
        String prefix=hit.rootId()+"/";
        double existing=ITEM_PROC_BUDGET.areaShare(world,actor,hit.rootId(),hit.itemId(),
                hit.snapshot().revision(),prefix,targetId.getUuid());
        if(existing>=0)return Math.min(authored,existing);
        if(selected.size()>4096)throw new IllegalStateException("NATIVE_SELECTOR_PROC_CAPACITY");
        var victims=new java.util.ArrayList<UUID>();
        buffer.getStore().forEachChunk(Query.and(NetworkId.getComponentType(),UUIDComponent.getComponentType()),
                (chunk,ignored)->{
                    for(int index=0;index<chunk.size();index++){
                        var network=chunk.getComponent(index,NetworkId.getComponentType());
                        if(!selected.contains(network.getId()))continue;
                        var ref=chunk.getReferenceTo(index);
                        if(!ref.isValid()||SelectorAccess.invulnerable(buffer,context.getEntity(),ref)
                                ||!com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageLifecycleSystems
                                        .eligibleItemProcTarget(buffer,ref,context.getOwningEntity()))continue;
                        victims.add(chunk.getComponent(index,UUIDComponent.getComponentType()).getUuid());
                    }
                });
        ITEM_PROC_BUDGET.declareArea(world,actor,hit.rootId(),hit.itemId(),hit.snapshot().revision(),
                prefix,victims,authored);
        return victims.isEmpty()?0:Math.min(authored,ITEM_PROC_BUDGET.areaShare(world,actor,
                hit.rootId(),hit.itemId(),hit.snapshot().revision(),prefix,targetId.getUuid()));
    }
    private static final class SelectorAccess extends SelectInteraction {
        static boolean invulnerable(CommandBuffer<EntityStore> buffer,
                Ref<EntityStore> source,Ref<EntityStore> target){
            return isTargetInvulnerable(buffer,source,target);
        }
    }

    /** Leaf hook immediately after GearCombatEffects.attack and before native component assembly. */
    public static GearCombatEffects.Hit commit(UUID world,UUID actor,GearCombatEffects.Hit hit,
            double procCoefficient,boolean melee,String authoredSelector,GearSignatureProcRuntime runtime){
        Objects.requireNonNull(runtime);
        double physical=hit.amount(GearCombatEffects.Channel.PHYSICAL);
        double ordinary=hit.critical()?physical/hit.criticalMultiplier():physical;
        double deadly=runtime.deadly(hit.snapshot(),hit.itemId(),hit.rootId(),physical,hit.critical(),
                true,false,procCoefficient);
        if(deadly!=physical){
            var amounts=new java.util.EnumMap<GearCombatEffects.Channel,Double>(GearCombatEffects.Channel.class);
            amounts.putAll(hit.amounts());amounts.put(GearCombatEffects.Channel.PHYSICAL,deadly);
            hit=new GearCombatEffects.Hit(hit.itemId(),hit.revision(),hit.rootId(),amounts,
                    hit.penetration(),hit.increased(),hit.critical(),hit.criticalMultiplier(),
                    hit.snapshot(),hit.origin());
        }
        capture(new Accepted(world,actor,hit,ordinary,procCoefficient,melee,false,authoredSelector));
        return hit;
    }

    /** Each authored strike or pellet supplies its own selector and coefficient. Never default a pellet to one. */
    public static synchronized void capture(Accepted accepted){
        long now=System.nanoTime();
        ACCEPTED.entrySet().removeIf(row->row.getValue().expiresAt()<now);
        SOURCES.entrySet().removeIf(row->row.getValue().expiresAt()<now);
        var key=new StrikeKey(accepted.world(),accepted.actor(),accepted.hit());
        var source=SOURCES.get(key.root);
        if(source!=null&&(!source.item().equals(accepted.hit().itemId())
                ||!source.snapshot().revision().equals(accepted.hit().snapshot().revision())))
            throw new IllegalStateException("GEAR_ROOT_SOURCE_DRIFT");
        var prior=ACCEPTED.get(key);
        if(prior!=null&&!prior.accepted().equals(accepted))
            throw new IllegalStateException("GEAR_STRIKE_SOURCE_DRIFT");
        if(prior==null&&ACCEPTED.size()>=MAX)throw new IllegalStateException("GEAR_STRIKE_CAPACITY");
        ACCEPTED.putIfAbsent(key,new Entry(accepted,now+LIFETIME));
        SOURCES.putIfAbsent(key.root,new RootSource(accepted.hit().itemId(),accepted.hit().snapshot(),now+LIFETIME));
    }
    public static synchronized Accepted find(UUID world,UUID actor,GearCombatEffects.Hit hit){
        var key=new StrikeKey(world,actor,hit);
        var row=ACCEPTED.get(key);
        if(row==null){
            var source=SOURCES.get(key.root);
            if(source!=null&&source.expiresAt()>=System.nanoTime())
                throw new IllegalStateException("GEAR_STRIKE_SNAPSHOT_DRIFT");
            return null;
        }
        if(row.expiresAt()<System.nanoTime()){
            ACCEPTED.remove(key);return null;
        }
        if(row.accepted().hit()!=hit)throw new IllegalStateException("GEAR_STRIKE_SNAPSHOT_DRIFT");
        return row.accepted();
    }
    public static synchronized void clearActor(UUID actor){
        ITEM_PROC_BUDGET.clearActor(actor);
        ACCEPTED.keySet().removeIf(key->key.root.actor().equals(actor));
        SOURCES.keySet().removeIf(key->key.actor().equals(actor));
        CHAINS.keySet().removeIf(key->key.actor().equals(actor));
    }
    public static synchronized void clearWorld(UUID world){
        ITEM_PROC_BUDGET.clearWorld(world);
        ACCEPTED.keySet().removeIf(key->key.root.world().equals(world));
        SOURCES.keySet().removeIf(key->key.world().equals(world));
        CHAINS.keySet().removeIf(key->key.world().equals(world));
    }
}
