package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.*;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.*;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.progress.*;
import com.inigmasgames.hytalerpg.execution.summon.IronSentinelBinding;
import com.inigmasgames.hytalerpg.execution.summon.IronSentinelAffixes;
import com.inigmasgames.hytalerpg.execution.summon.IronSentinelSourceEligibility;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.ui.inventory.SpatialBagComponent;
import com.inigmasgames.hytalerpg.ui.inventory.SpatialBagAggregate;
import com.inigmasgames.hytalerpg.ui.inventory.FootprintCatalog;
import com.inigmasgames.hytalerpg.ui.inventory.SpatialInventoryTransferCoordinator;
import com.inigmasgames.hytalerpg.ui.inventory.InventoryProbePage;
import org.bson.BsonDocument;
import org.bson.BsonString;
import java.util.*;
import java.util.concurrent.*;

/** Candidate native handoff. Disk work stays on a bounded worker; all ECS work is on its world queue. */
public final class HytaleGearLoot implements AutoCloseable {
    public static final String SOURCE="RpgGearSourceEvent",PROJECTION="RpgGearWorldProjection";
    private final GearLootService loot;private final RpgLoadoutService players;private final HytaleGearEquipment equipment;
    private final java.nio.file.Path spatialQaMarker;
    private final SpatialInventoryTransferCoordinator spatial;
    private final ExecutorService io=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),r->Thread.ofPlatform().daemon().name("RPG-gear-receipts").unstarted(r));
    private volatile Map<String,GearLootService.Loot> records=Map.of();private volatile Set<UUID> consumed=Set.of();
    private volatile Map<UUID,GearInstance> usableItems=Map.of();
    private volatile Set<UUID> unavailableItems=Set.of();
    private volatile Map<UUID,GearLootService.Loot> pendingDrops=Map.of();
    private volatile Map<UUID,IronSentinelBinding> sentinels=Map.of();
    private final Set<UUID> pendingSalvage=ConcurrentHashMap.newKeySet();
    private final Map<UUID,Ref<EntityStore>> spatialOperationSession=new ConcurrentHashMap<>();
    private final Map<UUID,Ref<EntityStore>> equipmentOperationSession=new ConcurrentHashMap<>();
    private final Map<UUID,Ref<EntityStore>> stockOperationSession=new ConcurrentHashMap<>();
    private final Map<UUID,Long> stockDropGrace=new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicReference<String> copiedFault=new java.util.concurrent.atomic.AtomicReference<>();
    private final Map<UUID,Map<String,Ref<EntityStore>>> displays=new ConcurrentHashMap<>();
    private final Map<String,Long> projectionWarningAt=new ConcurrentHashMap<>();
    private final Map<UUID,Long> pickupNoticeAt=new ConcurrentHashMap<>();
    private final Map<UUID,Long> refreshAt=new ConcurrentHashMap<>(),pickupScanAt=new ConcurrentHashMap<>(),stockScanAt=new ConcurrentHashMap<>();private final Set<UUID> busy=ConcurrentHashMap.newKeySet();
    private final Map<UUID,Ref<EntityStore>> failedDropSession=new ConcurrentHashMap<>();
    private volatile String failure="";private volatile long cacheAt;
    private final CompletableFuture<Void> initialView;
    private volatile java.util.function.Consumer<GearLootService.Loot> projected=ignored->{};
    public HytaleGearLoot(GearLootService loot,RpgLoadoutService players,HytaleGearEquipment equipment,
                          java.nio.file.Path spatialQaMarker){
        this.loot=loot;this.players=players;this.equipment=equipment;this.spatialQaMarker=spatialQaMarker;
        this.spatial=new SpatialInventoryTransferCoordinator(loot,io,this::publish);
        initialView=CompletableFuture.runAsync(()->{
            refresh();
            for(var receipt:loot.spatialStockDropReceipts())
                if(receipt.stage().equals("PREPARED"))
                    stockDropGrace.put(receipt.source(),System.currentTimeMillis()+30_000);
        },io).whenComplete((ignored,error)->{if(error!=null)failure="Initial gear custody view: "+error;});
    }
    /** Shared custody preparation once per runtime; never a per-join global scan. */
    public CompletableFuture<Void> prepareInitialView(){return initialView;}
    public void armCopiedTransferFault(String boundary){
        if(!java.nio.file.Files.isRegularFile(spatialQaMarker))throw new IllegalStateException("Copied-save marker required");
        if(!Set.of("equipment-after-prepare","equipment-after-save","stock-after-prepare",
                "stock-after-save","pickup-after-prepare","pickup-after-save").contains(boundary))
            throw new IllegalArgumentException("Unknown copied-save fault boundary");
        if(boundary.startsWith("pickup-")){spatial.armCopiedFault(boundary);return;}
        if(!copiedFault.compareAndSet(null,boundary))throw new IllegalStateException("A copied-save fault is already armed");
    }
    private boolean consumeCopiedFault(String boundary){return copiedFault.compareAndSet(boundary,null);}
    public void configureProjectionNotification(java.util.function.Consumer<GearLootService.Loot> listener){projected=Objects.requireNonNull(listener);}
    public boolean usable(GearInstance item){return !unavailableItems.contains(item.identity())&&
            (item.qaOnly()||failure.isEmpty()&&!pendingSalvage.contains(item.identity())&&!consumed.contains(item.identity())&&item.equals(usableItems.get(item.identity())));}
    public Optional<IronSentinelBinding> sentinel(UUID owner){return Optional.ofNullable(sentinels.get(owner));}
    public CompletionStage<IronSentinelBinding> prepareSentinel(GroundSource source,UUID owner,UUID instance,
            UUID world,Vec3 at,int effectiveLevel,double nativeInterval,double powerFactor){
        var future=new CompletableFuture<IronSentinelBinding>();
        try{io.execute(()->{try{
            var row=loot.prepareSentinel(source.event(),owner,source.row().allocation().revision(),System.currentTimeMillis(),
                    source.row().result().item().identity(),instance,world,at,effectiveLevel,nativeInterval,powerFactor);
            refresh();future.complete(row);
        }catch(RuntimeException error){try{refresh();}catch(RuntimeException reload){error.addSuppressed(reload);}future.completeExceptionally(error);}});
        }catch(RuntimeException error){future.completeExceptionally(error);}
        return future;
    }
    public CompletionStage<IronSentinelBinding> prepareReplacingSentinel(GroundSource source,UUID owner,UUID instance,
            UUID world,Vec3 at,int effectiveLevel,double nativeInterval,double powerFactor){
        var future=new CompletableFuture<IronSentinelBinding>();
        try{io.execute(()->{try{
            var row=loot.prepareReplacingSentinel(source.event(),owner,source.row().allocation().revision(),System.currentTimeMillis(),
                    source.row().result().item().identity(),instance,world,at,effectiveLevel,nativeInterval,powerFactor);
            refresh();future.complete(row);
        }catch(RuntimeException error){try{refresh();}catch(RuntimeException reload){error.addSuppressed(reload);}future.completeExceptionally(error);}});
        }catch(RuntimeException error){future.completeExceptionally(error);}
        return future;
    }
    public void abandonSentinel(UUID owner,UUID instance){
        io.execute(()->{try{var row=loot.sentinel(owner).orElse(null);
            if(row!=null&&row.instanceId().equals(instance)&&row.state()==IronSentinelBinding.State.PREPARED){
                if(loot.replacementBackup(instance).isPresent())loot.abortPreparedReplacement(owner,instance,true);
                else loot.abortPreparedSentinel(owner,instance);
            }
            refresh();
        }catch(RuntimeException error){failure="Iron Sentinel rollback: "+error;}});
    }
    public void activateSentinel(UUID owner,UUID instance,String event,UUID item,UUID world,Vec3 at,double health){
        io.execute(()->{try{
            var existing=loot.sentinel(owner).orElse(null);
            if(existing==null||!existing.instanceId().equals(instance)||existing.state()==IronSentinelBinding.State.DEAD){refresh();return;}
            loot.acknowledgeForge(event,owner,item,instance);
            loot.sentinelState(owner,instance,IronSentinelBinding.State.PREPARED,IronSentinelBinding.State.ACTIVE,health,world,at);
            refresh();
        }catch(RuntimeException error){failure="Iron Sentinel activation: "+error;}});
    }
    public CompletionStage<Void> activateReplacingSentinel(UUID owner,UUID instance,String event,UUID item,UUID world,Vec3 at,double health){
        var result=new CompletableFuture<Void>();
        try{io.execute(()->{try{
            var current=loot.sentinel(owner).orElseThrow();
            if(!current.instanceId().equals(instance))throw new IllegalStateException("SENTINEL_REPLACEMENT_IDENTITY_CHANGED");
            loot.acknowledgeForge(event,owner,item,instance);
            if(current.state()==IronSentinelBinding.State.PREPARED)
                loot.sentinelState(owner,instance,IronSentinelBinding.State.PREPARED,IronSentinelBinding.State.ACTIVE,health,world,at);
            else if(current.state()!=IronSentinelBinding.State.ACTIVE)throw new IllegalStateException("SENTINEL_REPLACEMENT_STATE_CHANGED");
            refresh();result.complete(null);
        }catch(RuntimeException error){result.completeExceptionally(error);}});}
        catch(RuntimeException error){result.completeExceptionally(error);}
        return result;
    }
    public void terminateSentinel(UUID owner,UUID instance,boolean trueDeath,UUID world,Vec3 at,double health){
        // A replacement may already own the singleton record while the outgoing native actor
        // is still present. Its removal must never mutate or terminate the incoming binding.
        if(sentinel(owner).filter(row->!row.instanceId().equals(instance)).isPresent())return;
        if(trueDeath){
            IronSentinelBinding row;
            try{row=loot.endSentinel(owner,instance,true,health,world,at);}
            catch(IllegalStateException changed){
                if(loot.sentinel(owner).filter(current->!current.instanceId().equals(instance)).isPresent())return;
                throw changed;
            }
            var copy=new HashMap<>(sentinels);copy.put(owner,row);sentinels=Map.copyOf(copy);
            return;
        }
        try{io.execute(()->{try{if(loot.sentinel(owner).filter(row->row.instanceId().equals(instance)).isPresent())
                    loot.endSentinel(owner,instance,false,health,world,at);refresh();}
            catch(RuntimeException error){failure="Iron Sentinel lifecycle: "+error;}});}
        catch(RuntimeException rejected){failure="Iron Sentinel lifecycle queue: "+rejected;}
    }
    public CompletionStage<Optional<IronSentinelBinding>> claimSentinelRestore(UUID owner,UUID world,Vec3 at){
        var result=new CompletableFuture<Optional<IronSentinelBinding>>();
        try{io.execute(()->{try{var row=loot.claimSentinelRestore(owner,world,at);refresh();result.complete(row);}
            catch(RuntimeException error){result.completeExceptionally(error);}});}
        catch(RuntimeException error){result.completeExceptionally(error);}return result;
    }
    public CompletionStage<IronSentinelBinding> finishSentinelRestore(UUID owner,UUID instance,double health,UUID world,Vec3 at){
        var result=new CompletableFuture<IronSentinelBinding>();
        try{io.execute(()->{try{var row=loot.finishSentinelRestore(owner,instance,health,world,at);refresh();result.complete(row);}
            catch(RuntimeException error){result.completeExceptionally(error);}});}
        catch(RuntimeException error){result.completeExceptionally(error);}return result;
    }
    public void checkpointSentinel(UUID owner,UUID instance,double health,UUID world,Vec3 at){
        try{io.execute(()->{try{loot.checkpointSentinel(owner,instance,health,world,at);}
            catch(RuntimeException error){failure="Iron Sentinel health checkpoint: "+error;}});}
        catch(RuntimeException rejected){failure="Iron Sentinel health checkpoint queue: "+rejected;}
    }
    public record GroundSource(String event,GearLootService.Loot row,Ref<EntityStore> entity) {}
    /** Native item entities are presentation of these world-owned receipts, never an item ID supplied by a client. */
    public List<GroundSource> visibleGroundSources(UUID world,Store<EntityStore> store){
        var shown=displays.get(world);if(shown==null||!failure.isEmpty())return List.of();
        var result=new ArrayList<GroundSource>();long now=System.currentTimeMillis();
        for(var entry:shown.entrySet()){
            var row=records.get(entry.getKey());var ref=entry.getValue();
            if(row==null||!row.state().equals("WORLD")||row.source()==null||!row.source().world().equals(world)
                    ||row.allocation()==null||now>=row.allocation().expiresAt()||ref==null||!ref.isValid())continue;
            var component=store.getComponent(ref,ItemComponent.getComponentType());
            if(component==null||!sameIdentity(component.getItemStack(),row.result().item().identity())
                    ||store.getComponent(ref,PreventPickup.getComponentType())==null)continue;
            result.add(new GroundSource(entry.getKey(),row,ref));
        }
        return List.copyOf(result);
    }
    public void requireForgeEligibility(GearInstance item,UUID owner,Store<EntityStore> store,Ref<EntityStore> actor){
        IronSentinelSourceEligibility.requireLoaded(item);
        IronSentinelAffixes.requireAdapted(item);
        IronSentinelAffixes.resistances(item);
        com.inigmasgames.hytalerpg.execution.summon.IronSentinelStatProjection.project(1,item,2);
        // Source equip gates belong to the player equipment owner, not to a summoned creature.
    }
    private synchronized void refresh(){
        try (var readyPathSpan = com.inigmasgames.hywind.readypath.ReadyPathProbe.span("RPG_GEAR_GLOBAL_RECORD_SCAN", null)) {var snapshot=new LinkedHashMap<String,GearLootService.Loot>();for(var row:loot.records())if(row.source()!=null)snapshot.put(row.source().eventId(),row);
        var dead=new HashSet<UUID>();loot.salvageReservations().forEach(r->dead.add(r.item()));
        var usable=new HashMap<UUID,GearInstance>();snapshot.values().stream().filter(r->Set.of("INVENTORY","SPATIAL_BAG").contains(r.state())&&r.result()!=null&&r.result().item()!=null).forEach(r->usable.put(r.result().item().identity(),r.result().item()));
        var bound=new HashMap<UUID,IronSentinelBinding>();
        for(var row:loot.sentinels())bound.put(row.ownerId(),row);
        var unavailable=new HashSet<UUID>();var pending=new HashMap<UUID,GearLootService.Loot>();
        for(var row:snapshot.values())if(row.result()!=null&&row.result().item()!=null){
            if(custodyUnavailable(row))
                unavailable.add(row.result().item().identity());
            if(row.state().equals("DROP_PENDING_NATIVE_SAVE"))pending.put(row.allocation().assigned(),row);
        }
        for(var row:bound.values())if(row.state()!=IronSentinelBinding.State.ABORTED)unavailable.add(row.boundItem().identity());
        consumed=Set.copyOf(dead);usableItems=Map.copyOf(usable);unavailableItems=Set.copyOf(unavailable);
        pendingDrops=Map.copyOf(pending);records=Map.copyOf(snapshot);sentinels=Map.copyOf(bound);cacheAt=System.currentTimeMillis();
        }
    }
    private synchronized void publish(GearLootService.Loot row){var snapshot=new LinkedHashMap<>(records);snapshot.put(row.source().eventId(),row);records=Map.copyOf(snapshot);
        var usable=new HashMap<>(usableItems);if(Set.of("INVENTORY","SPATIAL_BAG").contains(row.state()))usable.put(row.result().item().identity(),row.result().item());
        else if(row.result()!=null&&row.result().item()!=null)usable.remove(row.result().item().identity());
        usableItems=Map.copyOf(usable);
        var unavailable=new HashSet<>(unavailableItems);UUID item=row.result().item().identity();
        if(custodyUnavailable(row))unavailable.add(item);
        else if(sentinels.values().stream().noneMatch(binding->binding.state()!=IronSentinelBinding.State.ABORTED
                &&binding.boundItem().identity().equals(item)))unavailable.remove(item);
        unavailableItems=Set.copyOf(unavailable);
        var pending=new HashMap<>(pendingDrops);if(row.state().equals("DROP_PENDING_NATIVE_SAVE"))pending.put(row.allocation().assigned(),row);
        else pending.values().removeIf(value->value.source().eventId().equals(row.source().eventId()));
        pendingDrops=Map.copyOf(pending);cacheAt=System.currentTimeMillis();}
    private static boolean custodyUnavailable(GearLootService.Loot row){
        return Set.of("DROP_PENDING_NATIVE_SAVE","FORGE_PENDING","SENTINEL_BOUND").contains(row.state())
                ||row.state().equals("WORLD")&&(row.reason().startsWith("PLAYER_IRON_DROP")
                ||row.reason().startsWith("PLAYER_SPATIAL_DROP"));
    }
    public void tick(World world){long now=System.currentTimeMillis();UUID id=world.getWorldConfig().getUuid();
        stockDropGrace.entrySet().removeIf(entry->entry.getValue()<=now);
        if(!pendingDrops.isEmpty())reconcileIronDrops(world);
        if(pickupScanAt.getOrDefault(id,0L)<=now){pickupScanAt.put(id,now+100);scanPickup(world,now);}
        if(java.nio.file.Files.isRegularFile(spatialQaMarker)&&stockScanAt.getOrDefault(id,0L)<=now){
            stockScanAt.put(id,now+250);scanStockPickup(world);
        }
        if(refreshAt.getOrDefault(id,0L)>now)return;refreshAt.put(id,now+1000);
        io.execute(()->{try{if(now-cacheAt>=1000)refresh();world.execute(()->project(world));}catch(RuntimeException error){failure=error.toString();}});
    }
    private void scanPickup(World world,long now){var shown=displays.get(world.getWorldConfig().getUuid());if(shown==null||shown.isEmpty())return;
        var store=world.getEntityStore().getStore();for(var entry:shown.entrySet()){
            var row=records.get(entry.getKey());if(row!=null&&row.state().equals("WORLD")&&now<row.allocation().expiresAt())autoPickup(world,store,row,entry.getValue(),now);
        }
    }
    private void scanStockPickup(World world){
        var store=world.getEntityStore().getStore();
        for(var player:world.getPlayerRefs()){
            UUID owner=player.getUuid();if(busy.contains(owner))continue;
            var actor=world.getEntityRef(owner);if(actor==null||!actor.isValid())continue;
            var bag=store.getComponent(actor,SpatialBagComponent.getComponentType());
            if(bag==null||bag.mode(owner)==SpatialBagComponent.OwnershipMode.NATIVE)continue;
            var transform=store.getComponent(actor,TransformComponent.getComponentType());if(transform==null)continue;
            var location=transform.getPosition();var nearby=new java.util.concurrent.atomic.AtomicBoolean();
            store.forEachChunk(ItemComponent.getComponentType(),(chunk,buffer)->{
                if(nearby.get())return;
                for(int index=0;index<chunk.size();index++){
                    var item=chunk.getComponent(index,ItemComponent.getComponentType());
                    if(item==null||ItemStack.isEmpty(item.getItemStack())||GearNativeItems.managed(item.getItemStack()))continue;
                    var ref=chunk.getReferenceTo(index);
                    var sourceId=store.getComponent(ref,UUIDComponent.getComponentType());
                    if(store.getComponent(ref,PreventPickup.getComponentType())==null
                            ||sourceId==null||System.currentTimeMillis()<stockDropGrace.getOrDefault(sourceId.getUuid(),0L))continue;
                    var sourcePosition=store.getComponent(ref,TransformComponent.getComponentType());
                    if(sourcePosition!=null&&location.distanceSquared(sourcePosition.getPosition())<=6.25){
                        nearby.set(true);return;
                    }
                }
            });
            if(nearby.get())takeNearbyStock(store,actor,player,world,message->{
                if(message.startsWith("Stock transfer rejected:")){
                    long now=System.currentTimeMillis();
                    if(now-pickupNoticeAt.getOrDefault(owner,0L)<10_000)return;
                    pickupNoticeAt.put(owner,now);
                }
                notice(player,message);
            });
        }
    }
    private void project(World world){var store=world.getEntityStore().getStore();long now=System.currentTimeMillis();UUID id=world.getWorldConfig().getUuid();
        var shown=displays.computeIfAbsent(id,ignored->new HashMap<>());var expected=new HashSet<String>();
        for(var entry:records.entrySet()){var row=entry.getValue();if(!row.state().equals("WORLD")||!row.source().world().equals(id)||now>=row.allocation().expiresAt())continue;
            if(expected.size()>=256)break;expected.add(entry.getKey());var current=shown.get(entry.getKey());if(current!=null&&current.isValid())continue;
            var p=row.position();var stack=GearNativeItems.create(row.result().item(),1,Map.of()).withMetadata(PROJECTION,new BsonString("true"));
            var holder=ItemComponent.generateItemDrop(store,stack,new org.joml.Vector3d(p.x(),p.y()+.5,p.z()),com.hypixel.hytale.math.vector.Rotation3f.ZERO,0,0,0);
            if(holder==null){warnProjection(entry.getKey(),row,now);continue;}
            holder.putComponent(PreventPickup.getComponentType(),PreventPickup.INSTANCE);
            holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
            var spawned=store.addEntity(holder,AddReason.SPAWN);
            if(spawned==null||!spawned.isValid()){warnProjection(entry.getKey(),row,now);continue;}
            shown.put(entry.getKey(),spawned);
            try{projected.accept(row);}catch(RuntimeException diagnostic){com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                    "RPG_GEAR_PROJECTION_TRACE_FAILED event=%s error=%s",entry.getKey(),diagnostic.toString());}
        }
        shown.entrySet().removeIf(e->{var ref=e.getValue();if(expected.contains(e.getKey())&&ref!=null&&ref.isValid())return false;
            if(ref!=null&&ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);return true;});
        projectionWarningAt.keySet().removeIf(event->!expected.contains(event));
        for(var event:expected){var row=records.get(event);if(row!=null)autoPickup(world,store,row,shown.get(event),now);}
    }
    private void warnProjection(String event,GearLootService.Loot row,long now){
        if(now-projectionWarningAt.getOrDefault(event,0L)<30_000)return;
        projectionWarningAt.put(event,now);
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                "RPG_GEAR_PROJECTION_REJECTED event=%s item=%s",event,row.result().item().identity());
    }
    private void autoPickup(World world,Store<EntityStore> store,GearLootService.Loot row,Ref<EntityStore> projection,long now){
        if((row.reason().startsWith("PLAYER_IRON_DROP")||row.reason().startsWith("PLAYER_SPATIAL_DROP"))
                &&now<row.allocation().exclusiveUntil())return;
        var drop=projection!=null&&projection.isValid()?store.getComponent(projection,TransformComponent.getComponentType()):null;
        var q=drop!=null?drop.getPosition():null;
        for(var player:world.getPlayerRefs()){
            UUID owner=player.getUuid();if(busy.contains(owner)||!row.allocation().denial(owner,now).isEmpty())continue;
            var actor=world.getEntityRef(owner);if(actor==null||!actor.isValid())continue;
            var transform=store.getComponent(actor,TransformComponent.getComponentType());if(transform==null)continue;
            var p=transform.getPosition();
            if(q!=null?p.distanceSquared(q)>6.25:p.distanceSquared(row.position().x(),row.position().y(),row.position().z())>6.25)continue;
            try{pickup(message->{try{player.sendMessage(Message.raw(message));}catch(RuntimeException disconnected){/* Receipt remains authoritative. */}},store,actor,player,world,row);}
            catch(RuntimeException error){
                if(now-pickupNoticeAt.getOrDefault(owner,0L)>=10_000){pickupNoticeAt.put(owner,now);
                    try{player.sendMessage(Message.raw("Gear pickup: "+error.getMessage()));}catch(RuntimeException disconnected){/* Receipt remains authoritative. */}}
            }
        }
    }
    /** Own supported managed equipment drops; other native drops keep Hytale's normal path. */
    public final class IronDropSystem extends EntityEventSystem<EntityStore,DropItemEvent.PlayerRequest> {
        public IronDropSystem(){super(DropItemEvent.PlayerRequest.class);}
        @Override public Query<EntityStore> getQuery(){return PlayerRef.getComponentType();}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                CommandBuffer<EntityStore> buffer,DropItemEvent.PlayerRequest event){
            if(event.isCancelled())return;
            var actor=chunk.getReferenceTo(index);var player=chunk.getComponent(index,PlayerRef.getComponentType());
            var entity=store.getComponent(actor,Player.getComponentType());
            if(entity!=null&&entity.getPageManager().getCustomPage() instanceof InventoryProbePage page
                    &&page.ownsSpatialAlias(event.getInventorySectionId())){
                event.setCancelled(true); // The paired native container is empty; Hywind owns this source.
                UUID entry=page.spatialEntryForDrop(event.getInventorySectionId(),event.getSlotId());
                if(entry!=null){
                    int requestedQuantity=page.consumeDropQuantity(event.getSlotId());
                    var component=store.getComponent(actor,SpatialBagComponent.getComponentType());
                    var saved=component==null?null:component.state(player.getUuid()).entry(entry).orElse(null);
                    if(saved!=null&&!GearNativeItems.managed(saved.payload()))
                        spatialStockDrop(store,actor,player,page,entry,requestedQuantity);
                    else spatialIronDrop(store,actor,player,page,entry);
                    return;
                }
                var nativeSource=page.nativeStorageForDrop(event.getInventorySectionId(),event.getSlotId());
                var storage=store.getComponent(actor,InventoryComponent.Storage.getComponentType());
                if(nativeSource==null||storage==null||nativeSource.slot()>=storage.getInventory().getCapacity()
                        ||!nativeSource.fingerprint().equals(storage.getInventory().getItemStack(nativeSource.slot()))){
                    notice(player,"Drop source changed. Refresh Inventory and try again.");return;
                }
                var stack=nativeSource.fingerprint();
                if(GearNativeItems.managed(stack)){
                    try{var gear=GearNativeItems.read(stack);
                        IronSentinelSourceEligibility.requireLoaded(gear);
                        playerIronDrop(store,actor,player,storage.getInventory(),nativeSource.slot(),stack,gear);
                    }catch(RuntimeException invalid){notice(player,"Managed gear drop denied: "+invalid.getMessage());}
                }else{
                    // Mirror the native DropItemStack post-event operation against
                    // real Storage, never against the empty cursor alias.
                    var removed=storage.getInventory().removeItemStackFromSlot(nativeSource.slot(),stack.getQuantity());
                    if(!ItemStack.isEmpty(removed.getOutput()))
                        com.hypixel.hytale.server.core.entity.ItemUtils.throwItem(actor,removed.getOutput(),6.0f,store);
                }
                page.refreshAfterSpatialDrop(actor,store);
                return;
            }
            var section=InventoryUtils.getSectionById(actor,event.getInventorySectionId(),store);
            if(section==null||event.getSlotId()<0||event.getSlotId()>=section.getCapacity())return;
            var stack=section.getItemStack(event.getSlotId());if(!GearNativeItems.managed(stack))return;
            GearInstance item;
            try{item=GearNativeItems.read(stack);}
            catch(RuntimeException invalid){event.setCancelled(true);notice(player,"Managed gear drop denied: "+invalid.getMessage());return;}
            if(item.category()!=GearCatalog.Category.HELD&&item.category()!=GearCatalog.Category.ARMOR)return;
            event.setCancelled(true);
            try{IronSentinelSourceEligibility.requireLoaded(item);}
            catch(RuntimeException invalid){notice(player,"Forgeable gear drop denied: "+invalid.getMessage());return;}
            playerIronDrop(store,actor,player,section,event.getSlotId(),stack,item);
        }
    }
    /** Native outside-grid gesture -> durable stock receipt -> bag save -> protected world source. */
    private void spatialStockDrop(Store<EntityStore> store, Ref<EntityStore> actor, PlayerRef player,
                                  InventoryProbePage page, UUID entryId, int requestedQuantity) {
        UUID owner=player.getUuid();
        if(!java.nio.file.Files.isRegularFile(spatialQaMarker)){
            notice(player,"Spatial ground drop requires the copied-save QA owner.");return;
        }
        if(!busy.add(owner)){notice(player,"An inventory transaction is already pending.");return;}
        try {
            var component=store.getComponent(actor,SpatialBagComponent.getComponentType());
            if(component==null||component.mode(owner)==SpatialBagComponent.OwnershipMode.NATIVE)
                throw new IllegalStateException("Spatial bag is not active");
            var before=component.state(owner);
            var entry=before.entry(entryId).orElseThrow(()->new IllegalArgumentException("Bag item changed"));
            var held=entry.payload();
            if(GearNativeItems.managed(held))throw new IllegalArgumentException("Managed gear uses its own drop custody");
            int quantity=requestedQuantity>0?requestedQuantity:held.getQuantity();
            if(quantity>held.getQuantity())throw new IllegalArgumentException("Drop quantity exceeds source stack");
            var stack=held.withQuantity(quantity);
            UUID operation=UUID.randomUUID(), source=UUID.randomUUID();
            var planned=before.withdrawQuantity(operation,before.revision(),entryId,entry.payloadJson(),quantity);
            if(!planned.accepted())throw new IllegalStateException("Bag withdrawal rejected");
            var world=store.getExternalData().getWorld();
            var transform=store.getComponent(actor,TransformComponent.getComponentType());
            if(transform==null)throw new IllegalStateException("Player position unavailable");
            var p=transform.getPosition();double yaw=transform.getRotation().yaw();
            var safe=com.inigmasgames.hytalerpg.difficulty.HytaleDifficultyTravel.findSafe(world,
                    p.x()-Math.sin(yaw)*3.5,p.y(),p.z()-Math.cos(yaw)*3.5);
            if(p.distanceSquared(safe)>25)throw new IllegalStateException("No safe drop point nearby");
            var receipt=new GearLootService.SpatialStockDropReceipt(source,operation,owner,
                    world.getWorldConfig().getUuid(),entryId,frozen(stack),before.revision(),
                    safe.x(),safe.y(),safe.z(),"PREPARED");
            io.execute(()->{
                try{loot.prepareSpatialStockDrop(receipt);}catch(RuntimeException error){
                    busy.remove(owner);world.execute(()->page.failSpatialDrop("Ground drop receipt unavailable; item remains in bag."));return;
                }
                world.execute(()->{
                    if(world.getEntityRef(owner)!=actor||!actor.isValid()||component.state(owner)!=before){
                        io.execute(()->{loot.finishSpatialStockDrop(source,operation,"ABORTED");busy.remove(owner);});return;
                    }
                    stockOperationSession.put(owner,actor);
                    try{
                        page.beginSpatialDrop();
                        component.publish(owner,before,planned.bag());
                        var entity=store.getComponent(actor,Player.getComponentType());
                        if(entity==null)throw new IllegalStateException("Player save owner unavailable");
                        entity.saveConfig(world,entity.toHolder(),true).whenComplete((ignored,error)->{
                            if(error!=null){world.execute(()->page.failSpatialDrop("Drop save uncertain. Reconnect for recovery."));return;}
                            world.execute(()->{
                                try{
                                    spawnStockDrop(store,world,receipt);
                                    io.execute(()->{try{loot.finishSpatialStockDrop(source,operation,"FINALIZED");
                                        stockOperationSession.remove(owner,actor);busy.remove(owner);
                                        world.execute(()->{page.refreshAfterSpatialDrop(actor,store);
                                            notice(player,"Item dropped. Walk near it to collect it again.");});
                                    }catch(RuntimeException uncertain){world.execute(()->page.failSpatialDrop(
                                            "Drop receipt uncertain. Reconnect for recovery."));}});
                                }catch(RuntimeException uncertain){page.failSpatialDrop("Drop source uncertain. Reconnect for recovery.");}
                            });
                        });
                    }catch(RuntimeException uncertain){page.failSpatialDrop("Drop state uncertain. Reconnect for recovery.");}
                });
            });
        }catch(RuntimeException rejected){busy.remove(owner);notice(player,"Ground drop rejected: "+rejected.getMessage());}
    }

    private void spawnStockDrop(Store<EntityStore> store,World world,
                                GearLootService.SpatialStockDropReceipt receipt){
        var existing=world.getEntityRef(receipt.source());
        if(existing!=null&&existing.isValid()){
            var item=store.getComponent(existing,ItemComponent.getComponentType());
            if(item==null||!sameFrozen(item.getItemStack(),receipt.payloadJson()))
                throw new IllegalStateException("Drop source identity collision");
            return;
        }
        var stack=ItemStack.CODEC.decode(BsonDocument.parse(receipt.payloadJson()),new ExtraInfo());
        var holder=ItemComponent.generateItemDrop(store,stack,
                new org.joml.Vector3d(receipt.x(),receipt.y()+.5,receipt.z()),
                com.hypixel.hytale.math.vector.Rotation3f.ZERO,0,0,0);
        if(holder==null)throw new IllegalStateException("Native item drop could not be constructed");
        holder.putComponent(UUIDComponent.getComponentType(),new UUIDComponent(receipt.source()));
        holder.putComponent(PreventPickup.getComponentType(),PreventPickup.INSTANCE);
        var spawned=store.addEntity(holder,AddReason.SPAWN);
        if(spawned==null||!spawned.isValid())throw new IllegalStateException("Native item drop could not spawn");
        stockDropGrace.put(receipt.source(),System.currentTimeMillis()+10_000);
    }

    private void recoverStockDropOnReady(Store<EntityStore> store,Ref<EntityStore> actor,
                                         PlayerRef player,World world,Runnable recovered){
        UUID owner=player.getUuid();
        io.execute(()->{
            List<GearLootService.SpatialStockDropReceipt> pending;
            try{pending=loot.spatialStockDropReceipts().stream().filter(r->r.player().equals(owner)
                    &&r.world().equals(world.getWorldConfig().getUuid())&&r.stage().equals("PREPARED")).toList();}
            catch(RuntimeException error){com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                    "RPG_STOCK_DROP_RECOVERY_READ_FAILED player=%s error=%s",owner,error);return;}
            world.execute(()->{
                if(world.getEntityRef(owner)!=actor||!actor.isValid())return;
                var component=store.getComponent(actor,SpatialBagComponent.getComponentType());
                if(component==null)return;
                var bag=component.state(owner);
                var decisions=new ArrayList<Map.Entry<GearLootService.SpatialStockDropReceipt,String>>();
                for(var receipt:pending){
                    var source=world.getEntityRef(receipt.source());
                    var item=source!=null&&source.isValid()?store.getComponent(source,ItemComponent.getComponentType()):null;
                    var original=bag.entry(receipt.entryId()).orElse(null);
                    boolean withdrawn=bag.receipts().stream().anyMatch(r->r.operationId().equals(receipt.operationId())
                            &&r.outcome()==SpatialBagAggregate.Outcome.ACCEPTED);
                    String decision;
                    if(!withdrawn&&original!=null){
                        if(item!=null&&sameFrozen(item.getItemStack(),receipt.payloadJson()))
                            store.removeEntity(source,RemoveReason.REMOVE);
                        decision=item==null||sameFrozen(item.getItemStack(),receipt.payloadJson())?"ABORTED":"QUARANTINED";
                    }else if(withdrawn){
                        try{
                            if(item==null)spawnStockDrop(store,world,receipt);
                            else if(!sameFrozen(item.getItemStack(),receipt.payloadJson()))
                                throw new IllegalStateException("Drop source payload mismatch");
                            decision="FINALIZED";
                        }catch(RuntimeException error){decision="QUARANTINED";}
                    }else decision="QUARANTINED";
                    decisions.add(Map.entry(receipt,decision));
                }
                io.execute(()->{
                    boolean safe=true;
                    for(var decision:decisions){
                        try{loot.finishSpatialStockDrop(decision.getKey().source(),
                                decision.getKey().operationId(),decision.getValue());
                            if(decision.getValue().equals("QUARANTINED"))safe=false;
                        }catch(RuntimeException uncertain){safe=false;}
                    }
                    stockOperationSession.remove(owner);
                    if(safe)world.execute(recovered);
                });
            });
        });
    }
    /** Native outside-grid gesture -> private-bag receipt -> player save -> protected world source. */
    private void spatialIronDrop(Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,
                                 InventoryProbePage page,UUID entryId){
        UUID owner=player.getUuid();
        if(!java.nio.file.Files.isRegularFile(spatialQaMarker)){
            notice(player,"Spatial ground drop requires the copied-save QA owner.");return;
        }
        if(!initialView.isDone()||!failure.isEmpty()||pendingDrops.containsKey(owner)){
            notice(player,"Ground drop custody is not ready; the item remains in your bag.");return;
        }
        if(!busy.add(owner)){notice(player,"An inventory transaction is already pending.");return;}
        GearLootService.Loot reserved=null;boolean published=false;
        try{
            var component=store.getComponent(actor,SpatialBagComponent.getComponentType());
            if(component==null||component.mode(owner)==SpatialBagComponent.OwnershipMode.NATIVE)
                throw new IllegalStateException("Spatial bag is not active");
            var before=component.state(owner);
            var entry=before.entry(entryId).orElseThrow(()->new IllegalArgumentException("Bag item changed"));
            var stack=entry.payload();
            var item=GearNativeItems.read(stack);
            if(item==null||stack.getQuantity()!=1)
                throw new IllegalArgumentException("Only exact managed gear is enabled for ground-drop QA");
            IronSentinelSourceEligibility.requireLoaded(item);
            UUID operation=UUID.randomUUID();
            var planned=before.withdraw(operation,before.revision(),entryId,entry.payloadJson());
            if(!planned.accepted())throw new IllegalStateException("Bag withdrawal rejected: "+planned.receipt().outcome());
            var world=store.getExternalData().getWorld();
            var transform=store.getComponent(actor,TransformComponent.getComponentType());
            if(transform==null)throw new IllegalStateException("Player position unavailable");
            double yaw=transform.getRotation().yaw();var p=transform.getPosition();
            var safe=com.inigmasgames.hytalerpg.difficulty.HytaleDifficultyTravel.findSafe(world,
                    p.x()-Math.sin(yaw)*3.5,p.y(),p.z()-Math.cos(yaw)*3.5);
            if(p.distanceSquared(safe)>25)throw new IllegalStateException("No safe ground-drop point nearby");
            String source=stack.getMetadata()!=null&&stack.getMetadata().containsKey(SOURCE)
                    ?stack.getMetadata().getString(SOURCE).getValue():null;
            reserved=loot.beginIronDrop(source,owner,world.getWorldConfig().getUuid(),
                    new Vec3(safe.x(),safe.y(),safe.z()),item,true);
            publish(reserved);
            page.beginSpatialDrop();
            component.publish(owner,before,planned.bag());published=true;
            var entity=store.getComponent(actor,Player.getComponentType());
            if(entity==null)throw new IllegalStateException("Player save owner unavailable");
            String event=reserved.source().eventId();UUID identity=item.identity();
            entity.saveConfig(world,entity.toHolder(),true).whenComplete((ignored,error)->{
                if(error!=null){failedDropSession.put(owner,actor);busy.remove(owner);
                    world.execute(()->page.failSpatialDrop("Ground drop save uncertain. Reconnect for recovery."));
                    notice(player,"Ground drop save uncertain. Reconnect before moving items.");return;}
                try{world.execute(()->{
                    var current=world.getEntityRef(owner);
                    if(current!=actor||!actor.isValid()||component.state(owner)!=planned.bag()
                            ||spatialContains(component.state(owner),identity)){
                        failedDropSession.put(owner,actor);busy.remove(owner);
                        page.failSpatialDrop("Ground drop receipt pending. Reconnect for recovery.");return;
                    }
                    io.execute(()->{try{
                        publish(loot.acknowledgeIronDrop(event,owner,identity));
                        world.execute(()->{try{project(world);page.refreshAfterSpatialDrop(actor,store);
                            notice(player,"Gear dropped to protected ground custody. Walk near it to collect it again.");}
                            finally{busy.remove(owner);}});
                    }catch(RuntimeException uncertain){failedDropSession.put(owner,actor);busy.remove(owner);
                        world.execute(()->page.failSpatialDrop("Ground drop receipt uncertain. Reconnect for recovery."));}});
                });}catch(RuntimeException closed){failedDropSession.put(owner,actor);busy.remove(owner);}
            });
        }catch(RuntimeException error){
            if(reserved!=null&&!published){
                try{publish(loot.releaseIronDrop(reserved.source().eventId(),owner,reserved.result().item().identity()));}
                catch(RuntimeException uncertain){failedDropSession.put(owner,actor);error.addSuppressed(uncertain);}
            }
            if(published){failedDropSession.put(owner,actor);
                page.failSpatialDrop("Ground drop state uncertain. Reconnect for recovery.");}
            busy.remove(owner);
            notice(player,"Ground drop rejected: "+error.getMessage());
        }
    }
    private void playerIronDrop(Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,
            com.hypixel.hytale.server.core.inventory.container.ItemContainer section,short slot,ItemStack stack,GearInstance item){
        UUID owner=player.getUuid();
        if(!initialView.isDone()||!failure.isEmpty()){notice(player,"Iron gear custody is not ready; item remains in inventory.");return;}
        if(pendingDrops.containsKey(owner)){notice(player,"An earlier iron drop is still in custody escrow; reconnect to resolve it first.");return;}
        if(!busy.add(owner)){notice(player,"Iron gear transaction already pending; item remains in inventory.");return;}
        GearLootService.Loot reserved=null;
        try{
            var world=store.getExternalData().getWorld();var transform=store.getComponent(actor,TransformComponent.getComponentType());
            if(transform==null)throw new IllegalStateException("Player position unavailable");
            double yaw=transform.getRotation().yaw();var p=transform.getPosition();
            var safe=com.inigmasgames.hytalerpg.difficulty.HytaleDifficultyTravel.findSafe(world,
                    p.x()-Math.sin(yaw)*3.5,p.y(),p.z()-Math.cos(yaw)*3.5);
            if(p.distanceSquared(safe)>25)throw new IllegalStateException("No safe iron drop point within forge range");
            var point=new Vec3(safe.x(),safe.y(),safe.z());
            String source=stack.getMetadata().containsKey(SOURCE)?stack.getMetadata().getString(SOURCE).getValue():null;
            if(source==null&&!item.qaOnly()){
                var matches=records.values().stream().filter(row->row.state().equals("INVENTORY")&&row.result()!=null
                        &&item.equals(row.result().item())).toList();
                if(matches.size()!=1)throw new IllegalStateException("Exact source receipt is missing or ambiguous");
                source=matches.getFirst().source().eventId();
            }
            reserved=loot.beginIronDrop(source,owner,world.getWorldConfig().getUuid(),point,item);
            publish(reserved);
            var removal=section.removeItemStackFromSlot(slot,stack,1,true,true);
            if(!removal.succeeded()||!sameIdentity(removal.getOutput(),item.identity())){
                var held=section.getItemStack(slot);
                if(sameIdentity(held,item.identity()))publish(loot.releaseIronDrop(reserved.source().eventId(),owner,item.identity()));
                else failedDropSession.put(owner,actor);
                notice(player,"Iron gear drop could not finish; check inventory and reconnect if the item is in escrow.");
                busy.remove(owner);return;
            }
            var nativePlayer=store.getComponent(actor,Player.getComponentType());
            if(nativePlayer==null)throw new IllegalStateException("Native player save unavailable");
            String event=reserved.source().eventId();
            saveNative(store,actor,world).whenComplete((ignored,error)->{
                if(error!=null){failedDropSession.put(owner,actor);busy.remove(owner);
                    nativeSaveFailed("iron-drop",owner,error);
                    notice(player,"Iron drop is in custody escrow after a native save failure. Reconnect to recover it; no replacement was minted.");return;}
                try{world.execute(()->{try{
                        var current=world.getEntityRef(owner);
                        if(current==null||!current.isValid()||!current.equals(actor)){failedDropSession.put(owner,actor);return;}
                        if(inventoryContains(store,current,item.identity())){failedDropSession.put(owner,actor);
                            notice(player,"Iron drop receipt is pending because the exact item is still in inventory. Reconnect to reconcile it.");return;}
                        publish(loot.acknowledgeIronDrop(event,owner,item.identity()));
                        project(world);
                notice(player,"Dropped forgeable gear: "+item.displayName()+". Aim at it to cast Iron Sentinel.");
                    }catch(RuntimeException failed){failedDropSession.put(owner,actor);
                        com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                                "RPG_IRON_DROP_ACK_FAILED player=%s item=%s error=%s",owner,item.identity(),failed.toString());
                        notice(player,"Iron drop receipt is pending. Reconnect to reconcile the exact item.");
                    }finally{busy.remove(owner);}});
                }catch(RuntimeException rejected){failedDropSession.put(owner,actor);busy.remove(owner);
                    notice(player,"Iron drop receipt is pending after shutdown. Reconnect to reconcile the exact item.");}
            });
        }catch(RuntimeException error){
            if(reserved!=null){
                try{var held=section.getItemStack(slot);
                    if(sameIdentity(held,item.identity()))publish(loot.releaseIronDrop(reserved.source().eventId(),owner,item.identity()));
                    else failedDropSession.put(owner,actor);
                }catch(RuntimeException uncertain){failedDropSession.put(owner,actor);error.addSuppressed(uncertain);}
            }
            busy.remove(owner);com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                    "RPG_IRON_DROP_FAILED player=%s item=%s error=%s",owner,item.identity(),error.toString());
            notice(player,"Iron gear drop failed: "+error.getMessage()+". Check inventory; reconnect if the item is in escrow.");
        }
    }
    /** Resolve an interrupted handoff from the reloaded native inventory, never from a guessed save result. */
    private void reconcileIronDrops(World world){
        var store=world.getEntityStore().getStore();
        for(var row:pendingDrops.values()){
            UUID owner=row.allocation().assigned();var actor=world.getEntityRef(owner);
            if(actor==null||!actor.isValid()||busy.contains(owner))continue;
            var failed=failedDropSession.get(owner);
            if(failed!=null&&failed.isValid()&&failed.equals(actor))continue;
            failedDropSession.remove(owner);if(!busy.add(owner))continue;
            try{
                boolean spatialSource=row.reason().startsWith("PLAYER_SPATIAL_DROP");
                boolean present=spatialSource?spatialContains(store,actor,owner,row.result().item().identity())
                        :inventoryContains(store,actor,row.result().item().identity());
                saveNative(store,actor,world).whenComplete((ignored,error)->{
                    if(error!=null){failedDropSession.put(owner,actor);busy.remove(owner);
                        nativeSaveFailed("iron-drop-recovery",owner,error);return;}
                    try{world.execute(()->{try{
                        var current=world.getEntityRef(owner);
                        if(current==null||!current.isValid()||!current.equals(actor))return;
                        boolean stillPresent=spatialSource?spatialContains(store,current,owner,row.result().item().identity())
                                :inventoryContains(store,current,row.result().item().identity());
                        if(present!=stillPresent)return;
                        var resolved=present?loot.releaseIronDrop(row.source().eventId(),owner,row.result().item().identity())
                                :loot.acknowledgeIronDrop(row.source().eventId(),owner,row.result().item().identity());
                        publish(resolved);
                        if(!present&&row.source().world().equals(world.getWorldConfig().getUuid()))project(world);
                        notice(store.getComponent(actor,PlayerRef.getComponentType()),present
                                ?"Interrupted iron drop restored from your saved inventory."
                                :"Interrupted iron drop restored on the ground in its original world for forging.");
                    }catch(RuntimeException recoveryError){failedDropSession.put(owner,actor);
                        com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                                "RPG_IRON_DROP_RECOVER_FAILED player=%s event=%s error=%s",owner,row.source().eventId(),recoveryError.toString());
                    }finally{busy.remove(owner);}});
                    }catch(RuntimeException closed){failedDropSession.put(owner,actor);busy.remove(owner);}
                });
            }catch(RuntimeException error){failedDropSession.put(owner,actor);busy.remove(owner);
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                    "RPG_IRON_DROP_RECOVER_FAILED player=%s event=%s error=%s",owner,row.source().eventId(),error.toString());}
        }
    }
    private static boolean inventoryContains(Store<EntityStore> store,Ref<EntityStore> actor,UUID item){
        var inventory=InventoryComponent.getCombined(store,actor,InventoryComponent.EVERYTHING);
        if(inventory==null)return false;
        for(short slot=0;slot<inventory.getCapacity();slot++)if(sameIdentity(inventory.getItemStack(slot),item))return true;
        return false;
    }
    private static boolean spatialContains(Store<EntityStore> store,Ref<EntityStore> actor,UUID owner,UUID item){
        var component=store.getComponent(actor,SpatialBagComponent.getComponentType());
        return component!=null&&spatialContains(component.state(owner),item);
    }
    private static boolean spatialContains(SpatialBagAggregate bag,UUID item){
        for(var entry:bag.entries())if(sameIdentity(entry.payload(),item))return true;
        return false;
    }
    private static void notice(PlayerRef player,String message){
        if(player==null)return;try{player.sendMessage(Message.raw(message));}catch(RuntimeException disconnected){/* Durable receipt remains authoritative. */}
    }
    private interface PlayerAction {void run(CommandContext context,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world);}
    public AbstractCommandCollection command(){var commands=new AbstractCommandCollection("loot","Candidate protected loot and economy transactions."){};
        commands.requirePermission(com.inigmasgames.hytalerpg.commands.RpgGearCommand.AUTHOR_PERMISSION);
        commands.addSubCommand(new AbstractPlayerCommand("sentinel-qa","Place protected supported ground equipment for Iron Sentinel QA."){
            final varArgument base=new varArgument(this,"base","Mapped gm.* weapon or armor base"),kind=new varArgument(this,"kind","common, speed20, damage30, crit5, or fire20");
            @Override protected void execute(CommandContext c,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world){try{
                String variant=c.get(kind.arg).toLowerCase(Locale.ROOT);if(!Set.of("common","speed20","damage30","crit5","fire20").contains(variant))
                    throw new IllegalArgumentException("Choose common, speed20, damage30, crit5, or fire20");
                var catalog=GearCatalog.load();var definition=catalog.base(c.get(base.arg));
                if(definition.category()!=GearCatalog.Category.HELD&&definition.category()!=GearCatalog.Category.ARMOR)
                    throw new IllegalArgumentException("TARGET_NOT_WEAPON_OR_ARMOR");
                var binding=new GearBindings().require(definition.id());
                if(!binding.mapped())throw new IllegalArgumentException("TARGET_UNSUPPORTED_GEAR: "+binding.reason());
                String family=switch(variant){case "speed20"->"WA-008";case "damage30"->"WA-005";
                    case "crit5"->"WA-010";case "fire20"->"WA-074";default->null;};
                if(Set.of("speed20","damage30","crit5").contains(variant)&&definition.category()!=GearCatalog.Category.HELD)
                    throw new IllegalArgumentException(variant+" requires a weapon");
                int level=definition.sourceWindow().getLast();
                var affix=family==null?null:catalog.affix(family);
                double value=switch(variant){case "damage30"->30;case "crit5"->5;default->20;};
                var rolls=affix==null?List.<GearInstance.AffixRoll>of():List.of(new GearInstance.AffixRoll(family,affix.side(),
                        affix.exclusionGroup(),1,value,new GearRequirements.Gate(1,Map.of()),
                        variant+" (operator QA roll)",affix.name()));
                var gear=GearInstance.authoredQa(definition,UUID.randomUUID(),level,1000,
                        family==null?GearRarity.COMMON:GearRarity.UNCOMMON,rolls,java.math.BigDecimal.ZERO);
                requireForgeEligibility(gear,player.getUuid(),store,actor);
                var position=store.getComponent(actor,TransformComponent.getComponentType());double yaw=position.getRotation().yaw();var p=position.getPosition();
                var safe=com.inigmasgames.hytalerpg.difficulty.HytaleDifficultyTravel.findSafe(world,
                        p.x()-Math.sin(yaw)*4,p.y(),p.z()-Math.cos(yaw)*4);
                var point=new Vec3(safe.x(),safe.y(),safe.z());UUID owner=player.getUuid();
                io.execute(()->{try{var row=loot.publishSentinelQa(owner,world.getWorldConfig().getUuid(),point,gear);refresh();
                    world.execute(()->project(world));c.sendMessage(Message.raw("Iron Sentinel QA ground item: "+gear.displayName()
                            +"; base="+gear.baseId()+"; identity="+gear.identity()+"; position="+point+"; event="+row.source().eventId()));
                }catch(RuntimeException error){c.sendMessage(Message.raw("Sentinel QA drop: "+error.getMessage()));}});
            }catch(RuntimeException error){c.sendMessage(Message.raw("Sentinel QA drop: "+error.getMessage()));}}
        });
        commands.addSubCommand(new AbstractPlayerCommand("assign","Frozen eligible leader assigns a nearby pending item."){
            final varArgument enemy=new varArgument(this,"enemy","Enemy UUID"), recipient=new varArgument(this,"recipient","Eligible recipient UUID"), revision=new varArgument(this,"revision","Ownership revision");
            @Override protected void execute(CommandContext c,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world){try{
                String event="enemy-death/"+world.getWorldConfig().getUuid()+"/"+UUID.fromString(c.get(enemy.arg));var row=records.get(event);if(row==null||!near(row,world,store,actor))throw new IllegalArgumentException("No nearby item");
                UUID to=UUID.fromString(c.get(recipient.arg)),owner=player.getUuid();long expected=Long.parseLong(c.get(revision.arg));
                io.execute(()->{try{var result=loot.assign(event,owner,to,expected,System.currentTimeMillis());refresh();c.sendMessage(Message.raw(result.allocation().display(System.currentTimeMillis())));}catch(RuntimeException error){c.sendMessage(Message.raw("Assignment: "+error.getMessage()));}});
            }catch(RuntimeException error){c.sendMessage(Message.raw(error.getMessage()));}}});
        commands.addSubCommand(new AbstractPlayerCommand("upgrade-preview","Show exact skill recipe and any higher-provenance substitution before committing."){
            final varArgument skill=new varArgument(this,"skill","Learned stable SkillId");
            @Override protected void execute(CommandContext c,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world){try{
                String id=c.get(skill.arg);var state=players.getPresentationView(player.getUuid()).state();if(!state.learnedSkills.contains(id))throw new IllegalArgumentException("Learned skill required");
                var recipe=GearEconomy.recipe(players.baseSkillRank(player.getUuid(),id)+1);var debit=GearEconomy.debit(state.gearEconomy.materials(),recipe);
                c.sendMessage(Message.raw("Rank "+recipe.targetRank()+": "+debit+"; reset to rank 1 chance "+recipe.resetPercent()+"%. Commit: /rpg loot upgrade "+id+" "+state.revision+" "+UUID.randomUUID()));
            }catch(RuntimeException error){c.sendMessage(Message.raw("Upgrade: "+error.getMessage()));}}});
        commands.addSubCommand(new AbstractPlayerCommand("upgrade","Commit the previewed recipe once; failure resets only the base rank."){
            final varArgument skill=new varArgument(this,"skill","Learned stable SkillId"),revision=new varArgument(this,"revision","Preview revision"),request=new varArgument(this,"request","Request UUID");
            @Override protected void execute(CommandContext c,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world){try{
                UUID owner=player.getUuid(),key=UUID.fromString(c.get(request.arg));String id=c.get(skill.arg);long expected=Long.parseLong(c.get(revision.arg));
                io.execute(()->{try{var result=players.upgradeSkill(owner,id,expected,key);c.sendMessage(Message.raw("Base rank "+result.beforeRank()+" → "+result.afterRank()+"; spent "+result.amounts()));}catch(RuntimeException error){c.sendMessage(Message.raw("Upgrade: "+error.getMessage()));}});
            }catch(RuntimeException error){c.sendMessage(Message.raw(error.getMessage()));}}});
        commands.addSubCommand(new AbstractPlayerCommand("recover","Resume this player's committed salvage; inspect uncertain pickup without rerolling."){
            @Override protected void execute(CommandContext c,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world){UUID owner=player.getUuid();if(!busy.add(owner)){c.sendMessage(Message.raw("Transaction pending"));return;}
                io.execute(()->{try{refresh();var pending=loot.salvageReservations().stream().filter(r->r.player().equals(owner)&&!r.credited()).toList();
                    world.execute(()->{var current=world.getEntityRef(owner);if(current==null||!current.isValid()){busy.remove(owner);return;}var inv=InventoryComponent.getCombined(store,current,InventoryComponent.EVERYTHING);
                        for(var reservation:pending)for(short slot=0;slot<inv.getCapacity();slot++){var stack=inv.getItemStack(slot);if(sameIdentity(stack,reservation.item()))inv.removeItemStackFromSlot(slot,stack,1,true,true);}
                        var acknowledgements=new ArrayList<GearLootService.Loot>();for(var row:records.values())if(row.state().equals("PICKUP_PENDING_NATIVE_SAVE")&&owner.equals(row.allocation().claimedBy())){
                            boolean found=false;for(short slot=0;slot<inv.getCapacity();slot++)if(sameIdentity(inv.getItemStack(slot),row.result().item().identity()))found=true;
                            if(found)acknowledgements.add(row);else c.sendMessage(Message.raw("Pickup remains in escrow for "+row.result().item().identity()+"; native save reconciliation required. No replacement minted."));
                        }
                        saveNative(store,current,world).whenComplete((ignored,error)->{if(error!=null){nativeSaveFailed("recover",owner,error);busy.remove(owner);c.sendMessage(Message.raw("Recovery native save failed; receipts remain in escrow. Check the server log."));return;}
                            io.execute(()->{try{for(var reservation:pending)loot.creditSalvage(reservation,players);for(var row:acknowledgements)loot.acknowledgePickup(row.source().eventId(),owner,row.result().item().identity());refresh();c.sendMessage(Message.raw("Recovery complete; existing receipts retained."));}finally{busy.remove(owner);}});});
                    });
                }catch(RuntimeException error){busy.remove(owner);c.sendMessage(Message.raw("Recovery: "+error.getMessage()));}});
            }});
        commands.addSubCommand(new AbstractPlayerCommand("recover-spatial","Reconcile protected spatial pickup after reconnect."){
            @Override protected void execute(CommandContext c,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world){
                recoverSpatial(message -> c.sendMessage(Message.raw(message)),store,actor,player,world);
            }});
        commands.addSubCommand(new AbstractPlayerCommand("nearby","Show frozen ownership, denial reasons and time before expiry."){
            @Override protected void execute(CommandContext c,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world){
                if(!failure.isEmpty()){c.sendMessage(Message.raw("Loot unavailable: "+failure));return;}
                int found=0;long now=System.currentTimeMillis();
                for(var row:records.values())if(near(row,world,store,actor)&&row.state().equals("WORLD")){
                    found++;var denial=row.allocation().denial(player.getUuid(),now);
                    c.sendMessage(Message.raw(row.source().enemy()+" · "+row.result().item().displayName()+" · revision "+row.allocation().revision()+" · "+row.allocation().display(now)
                            +(denial.isEmpty()?" · walk over to collect, or /rpg loot pickup "+row.source().enemy():" · "+denial)));
                }
                if(found==0)c.sendMessage(Message.raw("No unclaimed RPG gear within 6 blocks. Gear rolls are optional; check near the defeated enemy before its 180-second expiry."));
            }});
        commands.addSubCommand(new AbstractPlayerCommand("pickup","Pick up nearby gear through its ownership receipt."){
            final varArgument enemy=new varArgument(this,"enemy","Enemy UUID");
            @Override protected void execute(CommandContext c,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world){
                try{String event="enemy-death/"+world.getWorldConfig().getUuid()+"/"+UUID.fromString(c.get(enemy.arg));var row=records.get(event);
                    if(row==null||!near(row,world,store,actor))throw new IllegalArgumentException("No nearby item");pickup(c,store,actor,player,world,row);
                }catch(RuntimeException error){c.sendMessage(Message.raw("Loot: "+error.getMessage()));}
            }});
        commands.addSubCommand(new AbstractPlayerCommand("salvage","Consume held eligible source gear once for its stored provenance yield."){
            @Override protected void execute(CommandContext c,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world){
                try{var stack=InventoryComponent.getItemInHand(store,actor);var item=GearNativeItems.read(stack);if(item==null||stack.getMetadata()==null||!stack.getMetadata().containsKey(SOURCE))throw new IllegalArgumentException("Owned source gear required");
                    var payout=GearEconomy.salvage(item).orElseThrow(()->new IllegalArgumentException("Common, tools and QA gear have no RPG salvage"));
                    String event=stack.getMetadata().getString(SOURCE).getValue();UUID owner=player.getUuid();if(!busy.add(owner))throw new IllegalArgumentException("Inventory transaction pending");
                    pendingSalvage.add(item.identity()); // Stop gameplay synchronously, before the durable worker admission.
                    io.execute(()->{try{var reservation=loot.reserveSalvage(event,owner,item);refresh();world.execute(()->{
                        var current=world.getEntityRef(owner);if(current==null||!current.isValid()){busy.remove(owner);return;}
                        var inventory=InventoryComponent.getCombined(store,current,InventoryComponent.EVERYTHING);
                        for(short slot=0;slot<inventory.getCapacity();slot++){var held=inventory.getItemStack(slot);if(sameIdentity(held,item.identity()))inventory.removeItemStackFromSlot(slot,held,1,true,true);}
                        saveNative(store,current,world).whenComplete((ignored,error)->{if(error!=null){nativeSaveFailed("salvage",owner,error);busy.remove(owner);c.sendMessage(Message.raw("Salvage native save failed; receipt remains pending. Check the server log."));return;}
                            io.execute(()->{try{loot.creditSalvage(reservation,players);refresh();c.sendMessage(Message.raw("Salvaged: "+payout.quantity()+" "+payout.material().key()));}catch(RuntimeException bad){failure=bad.toString();}finally{busy.remove(owner);}});});
                    });}catch(RuntimeException bad){try{refresh();if(!consumed.contains(item.identity()))pendingSalvage.remove(item.identity());}catch(RuntimeException uncertain){failure=uncertain.toString();}busy.remove(owner);c.sendMessage(Message.raw("Salvage: "+bad.getMessage()));}});
                }catch(RuntimeException error){c.sendMessage(Message.raw("Salvage: "+error.getMessage()));}
            }});
        commands.addSubCommand(new AbstractPlayerCommand("materials","Show component balances by immutable provenance."){
            @Override protected void execute(CommandContext c,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world){c.sendMessage(Message.raw(players.getPresentationView(player.getUuid()).state().gearEconomy.materials().toString()));}});
        return commands;
    }
    /** Helper avoids duplicated parsing declaration; native command builder remains the owner. */
    private static final class varArgument {
        final com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg<String> arg;
        varArgument(com.hypixel.hytale.server.core.command.system.AbstractCommand command,String name,String help){arg=command.withRequiredArg(name,help,ArgTypes.STRING);}
    }
    private void pickup(CommandContext c,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world,GearLootService.Loot row){
        pickup(message->c.sendMessage(Message.raw(message)),store,actor,player,world,row);
    }
    private void pickup(java.util.function.Consumer<String> reply,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world,GearLootService.Loot row){
        UUID owner=player.getUuid();var view=equipment.view(actor,store);var stack=GearNativeItems.create(row.result().item(),view.level(),view.baseline()).withMetadata(SOURCE,new BsonString(row.source().eventId()));
        // A test player with an attached private bag uses one managed-source coordinator.
        // Production players without the component retain the established native path.
        var bagOwner=store.getComponent(actor,SpatialBagComponent.getComponentType());
        if(bagOwner!=null && Set.of(SpatialBagComponent.OwnershipMode.QA_PROOF,
                    SpatialBagComponent.OwnershipMode.MIGRATION_PROOF).contains(bagOwner.mode(owner))
                && !java.nio.file.Files.isRegularFile(spatialQaMarker))
            throw new IllegalStateException("Copied-save spatial QA marker is missing; private-bag pickup denied");
        if(bagOwner!=null && bagOwner.mode(owner)!=SpatialBagComponent.OwnershipMode.NATIVE){
            if(!busy.add(owner))throw new IllegalArgumentException("Inventory transaction pending");
            spatialOperationSession.put(owner,actor);
            spatial.acceptManagedWorldItem(row,stack,store,actor,player,world,reply,()->busy.remove(owner));
            return;
        }
        var inventory=InventoryComponent.getCombined(store,actor,InventoryComponent.HOTBAR_STORAGE_BACKPACK);
        if(!inventory.canAddItemStack(stack))throw new IllegalArgumentException("Inventory full; item remains on ground");if(!busy.add(owner))throw new IllegalArgumentException("Inventory transaction pending");
        long started=System.nanoTime();
        io.execute(()->{try{publish(loot.reservePickup(row.source().eventId(),owner,row.allocation().revision(),System.currentTimeMillis(),true));long reserved=System.nanoTime();world.execute(()->{
            long enteredWorld=System.nanoTime();
            var current=world.getEntityRef(owner);if(current==null||!current.isValid()){
                io.execute(()->{try{publish(loot.releaseFullPickup(row.source().eventId(),owner));}finally{busy.remove(owner);}});return;}
            var target=InventoryComponent.getCombined(store,current,InventoryComponent.HOTBAR_STORAGE_BACKPACK);boolean exists=false;
            for(short slot=0;slot<target.getCapacity();slot++)if(sameIdentity(target.getItemStack(slot),row.result().item().identity()))exists=true;
            if(!exists&&!target.addItemStack(stack,true,false,true).succeeded()){
                io.execute(()->{try{publish(loot.releaseFullPickup(row.source().eventId(),owner));reply.accept("Inventory full; item remains on ground");}finally{busy.remove(owner);}});return;}
            long added=System.nanoTime();
            saveNative(store,current,world).whenComplete((ignored,error)->{if(error!=null){nativeSaveFailed("pickup",owner,error);busy.remove(owner);reply.accept("Gear pickup native save failed; item remains in escrow. Run /rpg loot recover and check the server log.");return;}
                long saved=System.nanoTime();io.execute(()->{try{publish(loot.acknowledgePickup(row.source().eventId(),owner,row.result().item().identity()));
                    long acknowledged=System.nanoTime();com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                            "RPG_GEAR_PICKUP_TIMING event=%s player=%s reserveMs=%d worldQueueMs=%d nativeSaveMs=%d acknowledgeMs=%d totalMs=%d",
                            row.source().eventId(),owner,elapsedMs(started,reserved),elapsedMs(reserved,enteredWorld),elapsedMs(added,saved),elapsedMs(saved,acknowledged),elapsedMs(started,acknowledged));
                    reply.accept("Picked up "+row.result().item().displayName());}catch(RuntimeException bad){failure=bad.toString();}finally{busy.remove(owner);}});});
        });}catch(RuntimeException error){busy.remove(owner);reply.accept("Pickup: "+error.getMessage());}});
    }
    /** Reconcile unfinished source receipts on every player-ready, including after process restart. */
    public void recoverSpatialOnReady(Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world){
        if(store.getComponent(actor,SpatialBagComponent.getComponentType())==null)return;
        var previousPickup=spatialOperationSession.get(player.getUuid());
        if(previousPickup!=null&&previousPickup!=actor){busy.remove(player.getUuid());spatialOperationSession.remove(player.getUuid(),previousPickup);}
        var previousEquipment=equipmentOperationSession.get(player.getUuid());
        if(previousEquipment!=null&&previousEquipment!=actor)busy.remove(player.getUuid());
        var previousStock=stockOperationSession.get(player.getUuid());
        if(previousStock!=null&&previousStock!=actor)busy.remove(player.getUuid());
        recoverEquipmentOnReady(store,actor,player,world,()->recoverStockOnReady(store,actor,player,world,
                ()->recoverStockDropOnReady(store,actor,player,world,()->recoverSpatial(message ->
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                        "RPG_SPATIAL_AUTO_RECOVERY player=%s result=%s",player.getUuid(),message),
                store,actor,player,world))));
    }
    private record EquipmentSlot(String section,short index,ItemContainer container) {
        ItemStack read(){return container.getItemStack(index);}
    }
    private static EquipmentSlot equipmentSlot(String name,Store<EntityStore> store,Ref<EntityStore> actor){
        String key=name.toLowerCase(Locale.ROOT);
        if(key.equals("held")){
            var hotbar=store.getComponent(actor,InventoryComponent.Hotbar.getComponentType());
            if(hotbar==null)return null;
            return new EquipmentSlot("HOTBAR",hotbar.getActiveSlot(),hotbar.getInventory());
        }
        if(key.equals("offhand")){
            var utility=store.getComponent(actor,InventoryComponent.Utility.getComponentType());
            if(utility==null)return null;
            return new EquipmentSlot("UTILITY",utility.getActiveSlot(),utility.getInventory());
        }
        com.hypixel.hytale.protocol.ItemArmorSlot armorSlot=switch(key){
            case "head"->com.hypixel.hytale.protocol.ItemArmorSlot.Head;
            case "chest"->com.hypixel.hytale.protocol.ItemArmorSlot.Chest;
            case "hands"->com.hypixel.hytale.protocol.ItemArmorSlot.Hands;
            case "legs"->com.hypixel.hytale.protocol.ItemArmorSlot.Legs;
            default->null;};
        var armor=store.getComponent(actor,InventoryComponent.Armor.getComponentType());
        return armorSlot==null||armor==null?null:new EquipmentSlot("ARMOR",(short)armorSlot.getValue(),armor.getInventory());
    }
    private static String frozen(ItemStack stack){return ItemStack.isEmpty(stack)?null:
            ItemStack.CODEC.encode(stack,new ExtraInfo()).asDocument().toJson();}
    private static boolean sameFrozen(ItemStack stack,String json){
        if(ItemStack.isEmpty(stack))return json==null;
        if(json==null)return false;
        var expected=ItemStack.CODEC.decode(BsonDocument.parse(json),new ExtraInfo());
        return withoutPresentation(stack).equals(withoutPresentation(expected));
    }
    private static ItemStack withoutPresentation(ItemStack stack){
        var metadata=stack.getMetadata();
        if(metadata==null||!metadata.containsKey(com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata.KEY))return stack;
        var copy=metadata.clone();copy.remove(com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata.KEY);
        return stack.withMetadata(copy);
    }
    private static UUID displacementId(UUID operation,String section,short slot){
        return UUID.nameUUIDFromBytes((operation+":"+section+":"+slot).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static boolean twoHanded(ItemStack stack){
        var gear=GearNativeItems.read(stack);
        if(gear==null)return false;
        String family=GearCatalog.load().base(gear.baseId()).family();
        return List.of("gm.battleaxe_","gm.shortbow_","gm.longbow_","gm.crossbow_","gm.rifle_",
                "gm.blunderbuss_","gm.longsword_","gm.spear_","gm.staff_").stream().anyMatch(family::startsWith);
    }
    private static void validateTarget(ItemStack stack,String slot){
        if(ItemStack.isEmpty(stack)||stack.getQuantity()!=1||stack.isBroken())
            throw new IllegalArgumentException("Equipment requires one unbroken item");
        var item=stack.getItem();
        if(item==null)throw new IllegalArgumentException("Item asset unavailable");
        if(List.of("head","chest","hands","legs").contains(slot)){
            var armor=item.getArmor();
            if(armor==null||!armor.getArmorSlot().name().equalsIgnoreCase(slot))
                throw new IllegalArgumentException("Item does not fit armor slot");
        }else if(slot.equals("offhand")){
            if(item.getUtility()==null||!item.getUtility().isCompatible())
                throw new IllegalArgumentException("Item is not offhand compatible");
        }else if(!slot.equals("held"))throw new IllegalArgumentException("Unknown equipment slot");
        var gear=GearNativeItems.read(stack);
        if(gear!=null){
            var authored=GearCatalog.load().base(gear.baseId()).slot();
            if(!authored.name().equalsIgnoreCase(slot.equals("offhand")?"held":slot))
                throw new IllegalArgumentException("Managed gear slot mismatch");
        }
    }
    /** Copied-save QA entry: one prepared receipt, one player save, then finalization. */
    public void transferEquipment(Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,
                                  World world,UUID sourceId,String requestedSlot,boolean equip,
                                  java.util.function.Consumer<String> reply){
        UUID owner=player.getUuid();String slot=requestedSlot.toLowerCase(Locale.ROOT);
        if(!java.nio.file.Files.isRegularFile(spatialQaMarker))throw new IllegalStateException("Equipment QA requires copied-save marker");
        if(!busy.add(owner)){reply.accept("Inventory transaction pending");return;}
        try{
            var component=store.getComponent(actor,SpatialBagComponent.getComponentType());
            if(component==null||component.mode(owner)==SpatialBagComponent.OwnershipMode.NATIVE)
                throw new IllegalStateException("Attach the QA spatial bag first");
            var before=component.state(owner);var target=equipmentSlot(slot,store,actor);
            if(target==null)throw new IllegalArgumentException("Equipment slot unavailable: "+slot);
            var old=target.read();ItemStack incoming;
            UUID operation=UUID.randomUUID();var changes=new ArrayList<GearLootService.EquipmentSlotChange>();
            var displaced=new ArrayList<SpatialBagAggregate.OfferedItem>();
            if(equip){
                if(sourceId==null)throw new IllegalArgumentException("Bag entry required");
                var entry=before.entry(sourceId).orElseThrow(()->new IllegalArgumentException("Bag entry missing"));
                incoming=entry.payload();validateTarget(incoming,slot);
                if(GearNativeItems.managed(incoming)){
                    var gear=GearNativeItems.read(incoming);
                    var all=InventoryComponent.getCombined(store,actor,InventoryComponent.EVERYTHING);
                    if(all!=null)for(short i=0;i<all.getCapacity();i++){
                        var nativeStack=all.getItemStack(i);
                        if(GearNativeItems.managed(nativeStack)&&GearNativeItems.read(nativeStack).identity().equals(gear.identity()))
                            throw new IllegalStateException("Managed identity already exists in native inventory");
                    }
                }
                if(!ItemStack.isEmpty(old))displaced.add(new SpatialBagAggregate.OfferedItem(
                        displacementId(operation,target.section(),target.index()),old));
                changes.add(new GearLootService.EquipmentSlotChange(target.section(),target.index(),frozen(old),frozen(incoming)));
                if(slot.equals("held")&&twoHanded(incoming)){
                    var secondary=equipmentSlot("offhand",store,actor);
                    if(secondary!=null&&!ItemStack.isEmpty(secondary.read())){
                        displaced.add(new SpatialBagAggregate.OfferedItem(displacementId(operation,secondary.section(),secondary.index()),secondary.read()));
                        changes.add(new GearLootService.EquipmentSlotChange(secondary.section(),secondary.index(),frozen(secondary.read()),null));
                    }
                }
                if(slot.equals("offhand")){
                    var held=equipmentSlot("held",store,actor);
                    if(held!=null&&!ItemStack.isEmpty(held.read())&&twoHanded(held.read())){
                        displaced.add(new SpatialBagAggregate.OfferedItem(displacementId(operation,held.section(),held.index()),held.read()));
                        changes.add(new GearLootService.EquipmentSlotChange(held.section(),held.index(),frozen(held.read()),null));
                    }
                }
                if(GearNativeItems.managed(incoming)){
                    var view=equipment.view(actor,store);
                    if(view==null)throw new IllegalStateException("Character attributes not ready");
                    var gear=GearNativeItems.read(incoming);
                    var removed=new HashSet<UUID>();
                    for(var change:changes)if(change.beforeJson()!=null){
                        var prior=ItemStack.CODEC.decode(BsonDocument.parse(change.beforeJson()),new ExtraInfo());
                        if(GearNativeItems.managed(prior))removed.add(GearNativeItems.read(prior).identity());
                    }
                    var survivors=view.equipped().stream().filter(g->!removed.contains(g.identity())).toList();
                    var attrs=GearRequirements.resolve(view.level(),view.baseline(),survivors.stream()
                            .map(g->new GearRequirements.Equipped(g.identity(),g.requirements(),GearAffixRuntime.attributes(g))).toList()).permanentAttributes();
                    if(!gear.requirements().failures(view.level(),attrs,gear.category()==GearCatalog.Category.ARMOR).isEmpty())
                        throw new IllegalArgumentException("Equipment requirements not met");
                }
                var planned=before.exchange(operation,before.revision(),sourceId,entry.payloadJson(),displaced,FootprintCatalog.loadDefault());
                if(!planned.accepted())throw new IllegalStateException("Equipment exchange rejected: "+planned.receipt().outcome());
                commitEquipment(store,actor,player,world,component,before,planned.bag(),changes,operation,reply);
            }else{
                if(ItemStack.isEmpty(old))throw new IllegalArgumentException("Equipment slot empty");
                var planned=before.offer(operation,before.revision(),old,FootprintCatalog.loadDefault());
                if(!planned.accepted())throw new IllegalStateException("Unequip rejected: "+planned.receipt().outcome());
                changes.add(new GearLootService.EquipmentSlotChange(target.section(),target.index(),frozen(old),null));
                commitEquipment(store,actor,player,world,component,before,planned.bag(),changes,operation,reply);
            }
        }catch(RuntimeException failure){busy.remove(owner);reply.accept("Equipment transfer rejected: "+failure.getMessage());}
    }
    private void commitEquipment(Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world,
                                 SpatialBagComponent component,SpatialBagAggregate before,SpatialBagAggregate after,
                                 List<GearLootService.EquipmentSlotChange> changes,UUID operation,
                                 java.util.function.Consumer<String> reply){
        commitEquipment(store,actor,player,world,component,before,after,changes,operation,null,reply);
    }
    private void commitEquipment(Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world,
                                 SpatialBagComponent component,SpatialBagAggregate before,SpatialBagAggregate after,
                                 List<GearLootService.EquipmentSlotChange> changes,UUID operation,
                                 SpatialBagComponent.OwnershipMode targetMode,
                                 java.util.function.Consumer<String> reply){
        UUID owner=player.getUuid();
        var receipt=new GearLootService.SpatialEquipmentReceipt(operation,owner,before.revision(),
                before.toBson().toJson(),after.toBson().toJson(),changes,
                targetMode==null?null:component.mode(owner).name(),targetMode==null?null:targetMode.name(),"PREPARED");
        io.execute(()->{
            try{loot.prepareSpatialEquipment(receipt);}catch(RuntimeException failed){
                equipmentOperationSession.put(owner,actor);
                reply.accept("Equipment journal uncertain; reconnect for automatic recovery: "+failed.getMessage());return;}
            if(consumeCopiedFault("equipment-after-prepare")){
                equipmentOperationSession.put(owner,actor);
                reply.accept("QA fault after durable equipment prepare. Reconnect for automatic rollback.");return;
            }
            world.execute(()->{
                var current=world.getEntityRef(owner);
                if(current==null||!current.isValid()||current!=actor||player.getReference()!=actor
                        ||store.getComponent(actor,SpatialBagComponent.getComponentType())!=component
                        ||component.state(owner)!=before||!equipmentBeforeMatches(changes,store,actor)){
                    io.execute(()->{try{loot.finishSpatialEquipment(operation,"ABORTED");}
                        finally{busy.remove(owner);reply.accept("Equipment transfer cancelled before publication.");}});return;}
                try{
                    equipmentOperationSession.put(owner,actor);
                    for(var change:changes){
                        var nativeSlot=equipmentSlotByReceipt(change,store,actor);
                        var payload=change.afterJson()==null?ItemStack.EMPTY:ItemStack.CODEC.decode(BsonDocument.parse(change.afterJson()),new ExtraInfo());
                        var transaction=nativeSlot.container().setItemStackForSlot(nativeSlot.index(),payload);
                        if(!transaction.succeeded()||!sameFrozen(nativeSlot.read(),change.afterJson()))
                            throw new IllegalStateException("Native equipment write rejected");
                    }
                    if(targetMode==null)component.publish(owner,before,after);
                    else component.publishCopiedMigration(owner,before,after,targetMode);
                    var entity=store.getComponent(actor,Player.getComponentType());
                    if(entity==null)throw new IllegalStateException("Player save owner unavailable");
                    entity.saveConfig(world,entity.toHolder(),true).whenComplete((ignored,error)->{
                        if(error!=null){reply.accept("Equipment save uncertain; reconnect for automatic recovery.");return;}
                        if(consumeCopiedFault("equipment-after-save")){
                            reply.accept("QA fault after player save. Reconnect for automatic finalization.");return;
                        }
                        io.execute(()->{try{loot.finishSpatialEquipment(operation,"FINALIZED");
                            equipmentOperationSession.remove(owner,actor);busy.remove(owner);
                            reply.accept("Equipment transfer saved and finalized.");}
                        catch(RuntimeException uncertain){reply.accept("Equipment saved; receipt uncertain. Reconnect for recovery.");}});
                    });
                }catch(RuntimeException uncertain){reply.accept("Equipment state uncertain; reconnect for automatic recovery: "+uncertain.getMessage());}
            });
        });
    }
    private static EquipmentSlot equipmentSlotByReceipt(GearLootService.EquipmentSlotChange change,
                                                         Store<EntityStore> store,Ref<EntityStore> actor){
        var component=switch(change.section()){
            case "HOTBAR"->store.getComponent(actor,InventoryComponent.Hotbar.getComponentType()).getInventory();
            case "UTILITY"->store.getComponent(actor,InventoryComponent.Utility.getComponentType()).getInventory();
            case "ARMOR"->store.getComponent(actor,InventoryComponent.Armor.getComponentType()).getInventory();
            case "STORAGE"->store.getComponent(actor,InventoryComponent.Storage.getComponentType()).getInventory();
            default->throw new IllegalArgumentException("Invalid equipment section");};
        return new EquipmentSlot(change.section(),change.slot(),component);
    }
    private static boolean equipmentBeforeMatches(List<GearLootService.EquipmentSlotChange> changes,
                                                  Store<EntityStore> store,Ref<EntityStore> actor){
        for(var change:changes)if(!sameFrozen(equipmentSlotByReceipt(change,store,actor).read(),change.beforeJson()))return false;
        return true;
    }
    private static UUID migrationEntryId(UUID operation,short slot){
        return UUID.nameUUIDFromBytes((operation+":STORAGE:"+slot).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    /** Explicit copied-save dry run and reversible Storage cutover; no main-save migration entry exists. */
    public void copiedStorageMigration(Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,
                                       World world,boolean reverse,boolean preview,
                                       java.util.function.Consumer<String> reply){
        UUID owner=player.getUuid();
        if(!java.nio.file.Files.isRegularFile(spatialQaMarker))throw new IllegalStateException("Copied-save marker required");
        if(!busy.add(owner)){reply.accept("Inventory transaction pending");return;}
        if(reverse){
            io.execute(()->{
                GearLootService.SpatialEquipmentReceipt origin;
                try{origin=loot.spatialEquipmentReceipts().stream().filter(r->r.player().equals(owner)
                        &&r.stage().equals("FINALIZED")&&"QA_PROOF".equals(r.beforeMode())
                        &&"MIGRATION_PROOF".equals(r.afterMode()))
                        .max(Comparator.comparingLong(GearLootService.SpatialEquipmentReceipt::beforeRevision))
                        .orElseThrow(()->new IllegalStateException("No finalized migration origin"));}
                catch(RuntimeException failure){busy.remove(owner);reply.accept("Reverse migration rejected: "+failure.getMessage());return;}
                world.execute(()->planCopiedStorageMigration(store,actor,player,world,true,preview,origin,reply));
            });
        }else planCopiedStorageMigration(store,actor,player,world,false,preview,null,reply);
    }
    private void planCopiedStorageMigration(Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world,
                                            boolean reverse,boolean preview,
                                            GearLootService.SpatialEquipmentReceipt origin,
                                            java.util.function.Consumer<String> reply){
        UUID owner=player.getUuid();
        try{
            if(world.getEntityRef(owner)!=actor||!actor.isValid())throw new IllegalStateException("Player session changed");
            var component=store.getComponent(actor,SpatialBagComponent.getComponentType());
            if(component==null)throw new IllegalStateException("Attach an empty QA bag first");
            var mode=component.mode(owner);
            if(mode!=(reverse?SpatialBagComponent.OwnershipMode.MIGRATION_PROOF:SpatialBagComponent.OwnershipMode.QA_PROOF))
                throw new IllegalStateException("Wrong migration mode: "+mode);
            var before=component.state(owner);
            var storageComponent=store.getComponent(actor,InventoryComponent.Storage.getComponentType());
            if(storageComponent==null)throw new IllegalStateException("Native Storage unavailable");
            var storage=storageComponent.getInventory();
            UUID operation=UUID.randomUUID();
            var changes=new ArrayList<GearLootService.EquipmentSlotChange>();
            SpatialBagAggregate.Result planned;
            if(!reverse){
                var items=new ArrayList<SpatialBagAggregate.OfferedItem>();
                for(short slot=0;slot<storage.getCapacity();slot++){
                    var stack=storage.getItemStack(slot);
                    if(ItemStack.isEmpty(stack))continue;
                    items.add(new SpatialBagAggregate.OfferedItem(migrationEntryId(operation,slot),stack));
                    changes.add(new GearLootService.EquipmentSlotChange("STORAGE",slot,frozen(stack),null));
                }
                if(items.isEmpty())throw new IllegalStateException("Native Storage is empty");
                planned=before.offerAll(operation,before.revision(),items,FootprintCatalog.loadDefault());
            }else{
                if(before.entries().isEmpty())throw new IllegalStateException("Private bag is empty");
                for(short slot=0;slot<storage.getCapacity();slot++)
                    if(!ItemStack.isEmpty(storage.getItemStack(slot)))
                        throw new IllegalStateException("Unexpected native Storage item at slot "+slot);
                var entries=before.entries().stream().sorted(Comparator.comparing(e->e.id().toString())).toList();
                var targets=new HashMap<UUID,Short>();var used=new HashSet<Short>();
                for(var change:origin.slots())if(change.section().equals("STORAGE")){
                    var id=migrationEntryId(origin.operationId(),change.slot());
                    if(before.entry(id).isPresent()){targets.put(id,change.slot());used.add(change.slot());}
                }
                short cursor=0;
                for(var entry:entries)if(!targets.containsKey(entry.id())){
                    while(cursor<storage.getCapacity()&&used.contains(cursor))cursor++;
                    if(cursor>=storage.getCapacity())throw new IllegalStateException("Native Storage has insufficient slots");
                    targets.put(entry.id(),cursor);used.add(cursor++);
                }
                for(var entry:entries){
                    short slot=targets.get(entry.id());
                    if(!ItemStack.isEmpty(storage.getItemStack(slot)))
                        throw new IllegalStateException("Native Storage is not empty at export slot "+slot);
                    changes.add(new GearLootService.EquipmentSlotChange("STORAGE",slot,null,entry.payloadJson()));
                }
                var frozenEntries=new LinkedHashMap<UUID,String>();
                for(var entry:entries)frozenEntries.put(entry.id(),entry.payloadJson());
                planned=before.clearForExport(operation,before.revision(),frozenEntries);
            }
            if(!planned.accepted())throw new IllegalStateException("Migration preflight: "+planned.receipt().outcome());
            if(preview){busy.remove(owner);reply.accept((reverse?"Reverse export":"Storage import")+
                    " preflight PASS: "+changes.size()+" exact stacks, candidate revision "+planned.bag().revision()+". Nothing changed.");return;}
            commitEquipment(store,actor,player,world,component,before,planned.bag(),changes,operation,
                    reverse?SpatialBagComponent.OwnershipMode.QA_PROOF:SpatialBagComponent.OwnershipMode.MIGRATION_PROOF,reply);
        }catch(RuntimeException failure){busy.remove(owner);reply.accept("Migration rejected: "+failure.getMessage());}
    }
    private void recoverEquipmentOnReady(Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,
                                         World world,Runnable recovered){
        UUID owner=player.getUuid();
        var previous=equipmentOperationSession.get(owner);
        if(previous==actor&&actor.isValid())return;
        if(previous!=null)busy.remove(owner);
        if(!busy.add(owner))return;
        io.execute(()->{
            List<GearLootService.SpatialEquipmentReceipt> pending;
            try{pending=loot.spatialEquipmentReceipts().stream().filter(r->r.player().equals(owner)
                    &&Set.of("PREPARED","QUARANTINED").contains(r.stage())).toList();}
            catch(RuntimeException error){com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                    "RPG_SPATIAL_EQUIPMENT_RECOVERY_READ_FAILED player=%s error=%s",owner,error);return;}
            world.execute(()->{
                if(world.getEntityRef(owner)!=actor||!actor.isValid())return;
                var component=store.getComponent(actor,SpatialBagComponent.getComponentType());
                if(component==null)return;
                var snapshot=component.state(owner).toBson();
                var decisions=new ArrayList<Map.Entry<UUID,String>>();
                for(var receipt:pending){
                    if(receipt.stage().equals("QUARANTINED")){
                        decisions.add(Map.entry(receipt.operationId(),"QUARANTINED"));continue;
                    }
                    boolean before=snapshot.equals(BsonDocument.parse(receipt.beforeBagJson()))
                            &&(receipt.beforeMode()==null||component.mode(owner).name().equals(receipt.beforeMode()));
                    boolean after=snapshot.equals(BsonDocument.parse(receipt.afterBagJson()))
                            &&(receipt.afterMode()==null||component.mode(owner).name().equals(receipt.afterMode()));
                    try{for(var change:receipt.slots()){
                        var actual=equipmentSlotByReceipt(change,store,actor).read();
                        before &=sameFrozen(actual,change.beforeJson());
                        after &=sameFrozen(actual,change.afterJson());
                    }}catch(RuntimeException mismatch){before=false;after=false;}
                    decisions.add(Map.entry(receipt.operationId(),after?"FINALIZED":before?"ABORTED":"QUARANTINED"));
                }
                io.execute(()->{
                    boolean safe=true;
                    for(var decision:decisions)try{
                        loot.finishSpatialEquipment(decision.getKey(),decision.getValue());
                        if(decision.getValue().equals("QUARANTINED"))safe=false;
                        com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                                "RPG_SPATIAL_EQUIPMENT_RECOVERY player=%s operation=%s result=%s",owner,decision.getKey(),decision.getValue());
                    }catch(RuntimeException error){
                        safe=false;
                        com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                                "RPG_SPATIAL_EQUIPMENT_RECOVERY_FAILED player=%s operation=%s error=%s",owner,decision.getKey(),error);
                    }
                    equipmentOperationSession.remove(owner);
                    if(safe){busy.remove(owner);world.execute(recovered);}
                });
            });
        });
    }
    /** Explicit copied-save stock admission; the holder guard prevents native pickup first. */
    public void takeNearbyStock(Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world,
                                java.util.function.Consumer<String> reply){
        UUID owner=player.getUuid();
        if(!java.nio.file.Files.isRegularFile(spatialQaMarker))throw new IllegalStateException("Stock QA marker required");
        if(!busy.add(owner)){reply.accept("Inventory transaction pending");return;}
        try{
            var component=store.getComponent(actor,SpatialBagComponent.getComponentType());
            if(component==null||component.mode(owner)==SpatialBagComponent.OwnershipMode.NATIVE)
                throw new IllegalStateException("Attach the QA bag first");
            var playerPosition=store.getComponent(actor,TransformComponent.getComponentType()).getPosition();
            var nearest=new java.util.concurrent.atomic.AtomicReference<Ref<EntityStore>>();
            var distance=new double[]{36};
            store.forEachChunk(ItemComponent.getComponentType(),(chunk,buffer)->{
                for(int index=0;index<chunk.size();index++){
                    var candidate=chunk.getReferenceTo(index);
                    if(store.getComponent(candidate,PreventPickup.getComponentType())==null)continue;
                    var item=chunk.getComponent(index,ItemComponent.getComponentType());
                    if(item==null||ItemStack.isEmpty(item.getItemStack())||GearNativeItems.managed(item.getItemStack()))continue;
                    var uuid=store.getComponent(candidate,UUIDComponent.getComponentType());
                    var transform=store.getComponent(candidate,TransformComponent.getComponentType());
                    if(uuid==null||transform==null
                            ||System.currentTimeMillis()<stockDropGrace.getOrDefault(uuid.getUuid(),0L))continue;
                    double squared=playerPosition.distanceSquared(transform.getPosition());
                    if(squared<distance[0]){distance[0]=squared;nearest.set(candidate);}
                }
            });
            var source=nearest.get();
            if(source==null)throw new IllegalStateException("No protected stock item within six blocks; UUID and pickup fence required");
            var sourceUuid=store.getComponent(source,UUIDComponent.getComponentType()).getUuid();
            var stack=store.getComponent(source,ItemComponent.getComponentType()).getItemStack();
            var before=component.state(owner);UUID operation=UUID.randomUUID();
            var planned=before.offerStacking(operation,before.revision(),stack,FootprintCatalog.loadDefault());
            if(!planned.accepted())throw new IllegalStateException("Stock no-fit/unmapped: "+planned.receipt().outcome());
            var receipt=new GearLootService.SpatialStockReceipt(sourceUuid,operation,owner,
                    world.getWorldConfig().getUuid(),frozen(stack),before.revision(),"PREPARED");
            io.execute(()->{
                try{loot.prepareSpatialStock(receipt);}catch(RuntimeException uncertain){
                    stockOperationSession.put(owner,actor);
                    reply.accept("Stock receipt uncertain; reconnect for automatic recovery: "+uncertain.getMessage());return;}
                if(consumeCopiedFault("stock-after-prepare")){
                    stockOperationSession.put(owner,actor);
                    reply.accept("QA fault after durable stock prepare. Reconnect for automatic rollback.");return;
                }
                world.execute(()->{
                    var currentSource=world.getEntityRef(sourceUuid);
                    if(world.getEntityRef(owner)!=actor||!actor.isValid()||player.getReference()!=actor
                            ||store.getComponent(actor,SpatialBagComponent.getComponentType())!=component
                            ||component.state(owner)!=before||currentSource!=source||!source.isValid()
                            ||store.getComponent(source,PreventPickup.getComponentType())==null
                            ||!sameFrozen(store.getComponent(source,ItemComponent.getComponentType()).getItemStack(),receipt.payloadJson())){
                        io.execute(()->{try{loot.finishSpatialStock(sourceUuid,operation,"ABORTED");}
                            finally{busy.remove(owner);reply.accept("Stock transfer cancelled; source remains protected.");}});return;}
                    stockOperationSession.put(owner,actor);
                    try{
                        component.publish(owner,before,planned.bag());
                        var entity=store.getComponent(actor,Player.getComponentType());
                        if(entity==null)throw new IllegalStateException("Player save owner unavailable");
                        entity.saveConfig(world,entity.toHolder(),true).whenComplete((ignored,error)->{
                            if(error!=null){reply.accept("Stock save uncertain; reconnect for recovery.");return;}
                            if(consumeCopiedFault("stock-after-save")){
                                reply.accept("QA fault after stock player save. Reconnect for automatic finalization.");return;
                            }
                            world.execute(()->{
                                var protectedSource=world.getEntityRef(sourceUuid);
                                if(protectedSource!=null&&protectedSource.isValid()
                                        &&sameFrozen(store.getComponent(protectedSource,ItemComponent.getComponentType()).getItemStack(),receipt.payloadJson()))
                                    store.removeEntity(protectedSource,RemoveReason.REMOVE);
                                io.execute(()->{try{loot.finishSpatialStock(sourceUuid,operation,"FINALIZED");
                                    stockOperationSession.remove(owner,actor);busy.remove(owner);
                                    reply.accept("Protected stock item admitted to the private bag once.");}
                                catch(RuntimeException uncertain){reply.accept("Stock source/receipt uncertain; reconnect for recovery.");}});
                            });
                        });
                    }catch(RuntimeException uncertain){reply.accept("Stock publication uncertain; reconnect for recovery: "+uncertain.getMessage());}
                });
            });
        }catch(RuntimeException rejected){busy.remove(owner);reply.accept("Stock transfer rejected: "+rejected.getMessage());}
    }
    private void recoverStockOnReady(Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,
                                     World world,Runnable recovered){
        UUID owner=player.getUuid();
        var previous=stockOperationSession.get(owner);
        if(previous==actor&&actor.isValid())return;
        if(previous!=null)busy.remove(owner);
        if(!busy.add(owner))return;
        io.execute(()->{
            List<GearLootService.SpatialStockReceipt> receipts;
            try{receipts=loot.spatialStockReceipts().stream().filter(r->r.player().equals(owner)
                    &&r.world().equals(world.getWorldConfig().getUuid())
                    &&Set.of("PREPARED","FINALIZED","QUARANTINED").contains(r.stage())).toList();}
            catch(RuntimeException failure){com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                    "RPG_SPATIAL_STOCK_RECOVERY_READ_FAILED player=%s error=%s",owner,failure);return;}
            world.execute(()->{
                if(world.getEntityRef(owner)!=actor||!actor.isValid())return;
                var component=store.getComponent(actor,SpatialBagComponent.getComponentType());
                if(component==null)return;
                var bag=component.state(owner);
                var decisions=new ArrayList<Map.Entry<GearLootService.SpatialStockReceipt,String>>();
                for(var receipt:receipts){
                    var source=world.getEntityRef(receipt.source());
                    var entry=bag.entry(receipt.operationId()).orElse(null);
                    boolean sourcePresent=source!=null&&source.isValid();
                    boolean sourceMatches=sourcePresent&&store.getComponent(source,PreventPickup.getComponentType())!=null
                            &&store.getComponent(source,ItemComponent.getComponentType())!=null
                            &&sameFrozen(store.getComponent(source,ItemComponent.getComponentType()).getItemStack(),receipt.payloadJson());
                    String decision;
                    if(receipt.stage().equals("QUARANTINED"))decision="QUARANTINED";
                    else if(receipt.stage().equals("FINALIZED")){
                        if(sourceMatches)store.removeEntity(source,RemoveReason.REMOVE);
                        decision=!sourcePresent||sourceMatches?"FINALIZED":"QUARANTINED";
                    }
                    else if(entry!=null&&BsonDocument.parse(entry.payloadJson()).equals(BsonDocument.parse(receipt.payloadJson()))
                            &&(!sourcePresent||sourceMatches)){
                        if(sourcePresent)store.removeEntity(source,RemoveReason.REMOVE);
                        decision="FINALIZED";
                    }else if(entry==null&&sourceMatches&&receipt.stage().equals("PREPARED"))decision="ABORTED";
                    else decision="QUARANTINED";
                    decisions.add(Map.entry(receipt,decision));
                }
                io.execute(()->{
                    boolean safe=true;
                    for(var decision:decisions){
                        var receipt=decision.getKey();String stage=decision.getValue();
                        try{
                            if(stage.equals("QUARANTINED"))safe=false;
                            if(receipt.stage().equals("PREPARED"))loot.finishSpatialStock(receipt.source(),receipt.operationId(),stage);
                            com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                                    "RPG_SPATIAL_STOCK_RECOVERY player=%s source=%s result=%s",owner,receipt.source(),stage);
                        }catch(RuntimeException uncertain){safe=false;}
                    }
                    stockOperationSession.remove(owner);
                    if(safe){busy.remove(owner);world.execute(recovered);}
                });
            });
        });
    }
    private void recoverSpatial(java.util.function.Consumer<String> reply,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world){
        UUID owner=player.getUuid();
        if(spatialOperationSession.get(owner)==actor && actor.isValid()){
            reply.accept("Reconnect before spatial recovery so the saved player snapshot can be inspected.");return;
        }
        if(!busy.add(owner)){reply.accept("Inventory transaction pending");return;}
        try{io.execute(()->{try{
            var pending=loot.spatialPickupReceipts().stream().filter(r->r.player().equals(owner)
                    &&!Set.of("FINALIZED","ABORTED").contains(r.stage())).toList();
            world.execute(()->{
                var current=world.getEntityRef(owner);
                if(current==null||!current.isValid()||current!=player.getReference()){
                    busy.remove(owner);return;
                }
                var component=store.getComponent(current,SpatialBagComponent.getComponentType());
                var bag=component==null?null:component.state(owner);
                var nativeInventory=InventoryComponent.getCombined(store,current,InventoryComponent.EVERYTHING);
                var decisions=new ArrayList<java.util.Map.Entry<GearLootService.SpatialPickupReceipt,Boolean>>();
                for(var receipt:pending){
                    boolean nativeCopy=false;
                    if(nativeInventory!=null)for(short slot=0;slot<nativeInventory.getCapacity();slot++)
                        if(sameIdentity(nativeInventory.getItemStack(slot),receipt.item()))nativeCopy=true;
                    var entry=bag==null?null:bag.entry(receipt.operationId()).orElse(null);
                    if(nativeCopy || entry!=null&&!BsonDocument.parse(entry.payloadJson()).equals(BsonDocument.parse(receipt.payloadJson()))){
                        reply.accept("Spatial recovery quarantined "+receipt.operationId()+": conflicting payload/copy.");
                        continue;
                    }
                    decisions.add(Map.entry(receipt,entry!=null));
                }
                try{io.execute(()->{try{
                    int committed=0,released=0;
                    for(var decision:decisions){var receipt=decision.getKey();
                        try{var row=decision.getValue()
                                ?loot.acknowledgeSpatialPickup(receipt.event(),owner,receipt.operationId())
                                :loot.releaseUncommittedSpatialPickup(receipt.event(),owner,receipt.operationId());
                            publish(row);if(decision.getValue())committed++;else released++;
                        }catch(RuntimeException ambiguous){
                            reply.accept("Spatial recovery quarantined "+receipt.operationId()+": "+ambiguous.getMessage());
                        }
                    }
                    if(!pending.isEmpty())reply.accept("Spatial recovery: committed="+committed+" released="+released
                            +" unresolved="+(pending.size()-committed-released));
                    if(pending.size()==committed+released)busy.remove(owner);
                }catch(RuntimeException unresolved){reply.accept("Spatial recovery quarantined: "+unresolved.getMessage());}});}catch(RuntimeException rejected){
                    reply.accept("Spatial recovery queue unavailable.");}
            });
        }catch(RuntimeException error){reply.accept("Spatial recovery: "+error.getMessage());}});
        }catch(RuntimeException rejected){reply.accept("Spatial recovery queue unavailable.");}
    }
    private static long elapsedMs(long start,long end){return TimeUnit.NANOSECONDS.toMillis(end-start);}
    private static boolean sameIdentity(ItemStack stack,UUID id){try{return stack!=null&&stack.getMetadata()!=null&&stack.getMetadata().containsKey(GearNativeItems.KEY)&&GearInstance.fromJson(stack.getMetadata().getString(GearNativeItems.KEY).getValue()).identity().equals(id);}catch(RuntimeException invalid){return false;}}
    private static boolean near(GearLootService.Loot row,World world,Store<EntityStore> store,Ref<EntityStore> actor){if(row.source()==null||!row.source().world().equals(world.getWorldConfig().getUuid()))return false;var p=store.getComponent(actor,TransformComponent.getComponentType()).getPosition();var q=row.position();return p.distanceSquared(q.x(),q.y(),q.z())<=36;}
    private static CompletableFuture<Void> saveNative(Store<EntityStore> store,Ref<EntityStore> actor,World world){
        // Hytale's own player save uses a shallow holder. copyEntity clones every component,
        // including Entity, whose codec is absent in pre.4 and crashes the world task.
        try{var player=store.getComponent(actor,Player.getComponentType());
            return player.saveConfig(world,player.toHolder(),true);
        }catch(RuntimeException error){return CompletableFuture.failedFuture(error);}
    }
    private static void nativeSaveFailed(String operation,UUID owner,Throwable error){
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                "RPG_GEAR_NATIVE_SAVE_FAILED operation=%s player=%s error=%s",operation,owner,error.toString());
    }
    @Override public void close(){io.shutdown();try{if(!io.awaitTermination(10,TimeUnit.SECONDS))throw new IllegalStateException("Gear IO shutdown timeout");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}}
}
