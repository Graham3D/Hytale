package com.inigmasgames.hytalerpg.spawning;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.spawning.world.component.WorldSpawnData;
import com.hypixel.hytale.server.spawning.world.system.WorldSpawningSystem;
import com.inigmasgames.hytalerpg.diagnostics.MonsterSpawnTrace;
import java.util.*;
import java.util.function.Supplier;

/** Reallocates native per-role expected member counts; native job selection and all caps remain owners. */
public final class NativePopulationBalance implements AutoCloseable {
    public record Policy(boolean enabled,double hostileShare,double wildlifeShare) {
        public Policy{
            if(!Double.isFinite(hostileShare)||!Double.isFinite(wildlifeShare)||hostileShare<=0
                    ||wildlifeShare<=0||Math.abs(hostileShare+wildlifeShare-1)>1e-9)
                throw new IllegalArgumentException("POPULATION_BALANCE_SHARES");
        }
    }
    public record EnvironmentSnapshot(int environment,int segments,double nativeExpected,int nativeActual,
                                      int hostileActual,int wildlifeActual,int avianActual,int otherActual,
                                      double baseHostile,double baseWildlife,double targetHostile,double targetWildlife,
                                      boolean adjusted,String reason,int unknownRoles) {}
    private final NativePopulationRoles roles=NativePopulationRoles.load();
    private final Supplier<Policy> policy;
    private final Map<World,Long> lastRun=Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<World,Long> lastRoleTrace=Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<World,Set<Integer>> modified=Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<UUID,Map<Integer,EnvironmentSnapshot>> snapshots=new java.util.concurrent.ConcurrentHashMap<>();
    private volatile boolean closed;
    public NativePopulationBalance(Supplier<Policy> policy){this.policy=Objects.requireNonNull(policy);}
    public Map<Integer,EnvironmentSnapshot> snapshot(UUID world){return snapshots.getOrDefault(world,Map.of());}
    public NativePopulationRoles roles(){return roles;}

    private void apply(World world){
        if(closed||!world.getWorldConfig().isSpawningNPC())return;
        long now=System.nanoTime();
        synchronized(lastRun){
            if(now-lastRun.getOrDefault(world,0L)<1_000_000_000L)return;
            lastRun.put(world,now);
        }
        var entityStore=world.getEntityStore().getStore();
        var nativeData=entityStore.getResource(WorldSpawnData.getResourceType());
        var time=entityStore.getResource(WorldTimeResource.getResourceType());
        if(nativeData==null||time==null)return;
        var selected=policy.get();
        boolean traceRoles=false;
        if(MonsterSpawnTrace.enabled()&&now-lastRoleTrace.getOrDefault(world,0L)>=15_000_000_000L){
            lastRoleTrace.put(world,now);traceRoles=true;
        }
        var modifiedWorld=modified.computeIfAbsent(world,ignored->new HashSet<>());
        var observed=new TreeMap<Integer,EnvironmentSnapshot>();
        for(int index:nativeData.getWorldEnvironmentSpawnDataIndexes()){
            var environment=nativeData.getWorldEnvironmentSpawnData(index);
            if(environment==null||!environment.hasNPCs())continue;
            var rows=new ArrayList<PopulationWeightPlan.Row>();
            int unknown=0;
            for(var stat:environment.getNpcStatMap().values()){
                String id=NPCPlugin.get().getName(stat.getRoleIndex());
                if(id==null||!roles.audited(id))unknown++;
                var category=roles.category(id);
                boolean eligible=false;
                try{
                    eligible=stat.getSpawnParams()!=null&&stat.getSpawnWrapper()!=null
                            &&!stat.isUnspawnable()&&stat.getWeight(time.getMoonPhase())>0
                            &&stat.getSpawnWrapper().spawnParametersMatch(entityStore);
                }catch(RuntimeException unavailable){/* Native eligibility remains authoritative. */}
                int minFlock=1;
                if(stat.getSpawnParams()!=null){
                    var flock=stat.getSpawnParams().getFlockDefinition();
                    if(flock!=null)minFlock=Math.max(1,flock.getMinFlockSize());
                }
                rows.add(new PopulationWeightPlan.Row(stat.getRoleIndex(),category,
                        Math.max(0,stat.getWeight(time.getMoonPhase())),Math.max(0,stat.getActual()),minFlock,eligible));
            }
            var result=PopulationWeightPlan.calculate(environment.getExpectedNPCs(),rows,
                    selected.hostileShare(),selected.wildlifeShare());
            boolean adjust=selected.enabled()&&result.adjusted();
            if(adjust||modifiedWorld.contains(index)){
                for(var stat:environment.getNpcStatMap().values()){
                    double target=result.expected().getOrDefault(stat.getRoleIndex(),stat.getExpected());
                    if(Math.abs(stat.getExpected()-target)>1e-8)stat.setExpected(target);
                }
                if(adjust)modifiedWorld.add(index);else modifiedWorld.remove(index);
            }
            String reason=selected.enabled()?result.reason():"DISABLED_NATIVE_WEIGHTS";
            observed.put(index,new EnvironmentSnapshot(index,environment.getSegmentCount(),environment.getExpectedNPCs(),
                    environment.getActualNPCs(),result.actualHostile(),result.actualWildlife(),result.actualAvian(),
                    result.actualOther(),result.baseHostile(),result.baseWildlife(),adjust?result.targetHostile():result.baseHostile(),
                    adjust?result.targetWildlife():result.baseWildlife(),adjust,reason,unknown));
            if(MonsterSpawnTrace.enabled())MonsterSpawnTrace.event("POPULATION_WEIGHT",world.getWorldConfig().getUuid(),index,"all",
                    "segments="+environment.getSegmentCount()+" expected="+environment.getExpectedNPCs()
                    +" actual="+environment.getActualNPCs()+" hostileActual="+result.actualHostile()
                    +" wildlifeActual="+result.actualWildlife()+" avianActual="+result.actualAvian()
                    +" otherActual="+result.actualOther()+" hostileExpected="+(adjust?result.targetHostile():result.baseHostile())
                    +" wildlifeExpected="+(adjust?result.targetWildlife():result.baseWildlife())
                    +" headroom="+Math.max(0,environment.getExpectedNPCs()-environment.getActualNPCs())
                    +" nativeJobs="+nativeData.getActiveSpawnJobs()+"/"+nativeData.getTotalSpawnJobsCompleted()
                    +" budgetUsed="+nativeData.getTotalSpawnJobBudgetUsed()
                    +" adjusted="+adjust+" reason="+reason+" unknownRoles="+unknown);
            double totalNativeWeight=rows.stream().mapToDouble(PopulationWeightPlan.Row::nativeWeight).sum();
            if(traceRoles)for(var row:rows)if(row.category()==NativePopulationRoles.Category.HOSTILE
                    ||row.category()==NativePopulationRoles.Category.WILDLIFE)
                MonsterSpawnTrace.event("POPULATION_ROLE_WEIGHT",world.getWorldConfig().getUuid(),index,
                        NPCPlugin.get().getName(row.roleIndex()),"category="+row.category()+" nativeWeight="+row.nativeWeight()
                        +" baseExpected="+(totalNativeWeight>0?environment.getExpectedNPCs()*row.nativeWeight()/totalNativeWeight:0)
                        +" adjustedExpected="+(adjust?result.expected().get(row.roleIndex()):"native")
                        +" actual="+row.actual()+" minFlock="+row.minimumFlock()+" eligible="+row.eligible());
        }
        snapshots.put(world.getWorldConfig().getUuid(),Map.copyOf(observed));
    }
    @Override public void close(){closed=true;lastRun.clear();lastRoleTrace.clear();modified.clear();snapshots.clear();}
    public final class Tick extends TickingSystem<ChunkStore> {
        @Override public Set<Dependency<ChunkStore>> getDependencies(){return Set.of(
                new SystemDependency<>(Order.AFTER,NativeWorldSpawnDensity.Tick.class),
                new SystemDependency<>(Order.BEFORE,WorldSpawningSystem.class));}
        @Override public void tick(float dt,int index,Store<ChunkStore> store){
            try{apply(store.getExternalData().getWorld());}
            catch(RuntimeException failure){
                // Population tuning must never take down the native spawning tick.
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(failure)
                        .log("RPG_POPULATION_WEIGHT_UNAVAILABLE nativeSpawningContinues=true");
            }
        }
    }
}
