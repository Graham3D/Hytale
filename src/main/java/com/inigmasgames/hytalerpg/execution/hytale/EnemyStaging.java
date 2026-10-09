package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Saved transaction marker over native pause/protection/collision flags; not an ailment or a role. */
public final class EnemyStaging implements Component<EntityStore> {
    public record State(UUID world,UUID encounter,UUID entity,long generation,int addedFlags){
        public State{
            Objects.requireNonNull(world);Objects.requireNonNull(encounter);Objects.requireNonNull(entity);
            if(generation<0||addedFlags<0||addedFlags>15)throw new IllegalArgumentException("ENEMY_STAGING_STATE");
        }
        /** The fourth bit records native extra-minion provenance; lower bits still own flags. */
        public boolean additional(){return (addedFlags&8)!=0;}
    }
    private static ComponentType<EntityStore,EnemyStaging> type;
    private UUID world,encounter,entity;private long generation;private int addedFlags;
    public static final BuilderCodec<EnemyStaging> CODEC=BuilderCodec.builder(EnemyStaging.class,EnemyStaging::new)
            .append(new KeyedCodec<>("World",Codec.UUID_BINARY),(p,v)->p.world=v,p->p.world).add()
            .append(new KeyedCodec<>("Encounter",Codec.UUID_BINARY),(p,v)->p.encounter=v,p->p.encounter).add()
            .append(new KeyedCodec<>("Entity",Codec.UUID_BINARY),(p,v)->p.entity=v,p->p.entity).add()
            .append(new KeyedCodec<>("Generation",Codec.LONG),(p,v)->p.generation=v,p->p.generation).add()
            .append(new KeyedCodec<>("AddedFlags",Codec.INTEGER),(p,v)->p.addedFlags=v,p->p.addedFlags).add()
            .afterDecode(EnemyStaging::validateDecoded).build();
    public EnemyStaging(){}
    public EnemyStaging(State value){world=value.world();encounter=value.encounter();entity=value.entity();generation=value.generation();addedFlags=value.addedFlags();}
    private void validateDecoded(){
        if(world==null&&encounter==null&&entity==null&&generation==0&&addedFlags==0)return;
        state();
    }
    public State state(){return new State(world,encounter,entity,generation,addedFlags);}
    public static void bind(ComponentType<EntityStore,EnemyStaging> value){type=Objects.requireNonNull(value);}
    public static ComponentType<EntityStore,EnemyStaging> getComponentType(){return type;}
    @Override public EnemyStaging clone(){return new EnemyStaging(state());}

    /** Called from the native incoming spawn holder boundary, before first publication. */
    public static State prepare(Holder<EntityStore> holder,UUID world,UUID encounter,long generation){
        return prepare(holder,world,encounter,generation,null);
    }
    public static State prepareAdditional(Holder<EntityStore> holder,UUID world,UUID encounter,long generation){
        return prepare(holder,world,encounter,generation,true);
    }
    private static State prepare(Holder<EntityStore> holder,UUID world,UUID encounter,long generation,Boolean additional){
        var id=holder.getComponent(UUIDComponent.getComponentType());
        if(id==null)throw new IllegalStateException("ENEMY_STAGING_NATIVE_ID_REQUIRED");
        var previous=holder.getComponent(type);
        if(previous!=null){
            var state=previous.state();
            if(!state.world().equals(world)||!state.encounter().equals(encounter)||!state.entity().equals(id.getUuid())||state.generation()!=generation)
                throw new IllegalStateException("ENEMY_STAGING_OWNERSHIP_CHANGED");
            if(additional!=null&&state.additional()!=additional)
                throw new IllegalStateException("ENEMY_STAGING_PROVENANCE_CHANGED");
            holder.ensureComponent(Frozen.getComponentType());
            holder.ensureComponent(Invulnerable.getComponentType());
            holder.ensureComponent(Intangible.getComponentType());
            return state;
        }
        int flags=(holder.getComponent(Frozen.getComponentType())==null?1:0)
                |(holder.getComponent(Invulnerable.getComponentType())==null?2:0)
                |(holder.getComponent(Intangible.getComponentType())==null?4:0)
                |(Boolean.TRUE.equals(additional)?8:0);
        var state=new State(world,encounter,id.getUuid(),generation,flags);
        holder.ensureComponent(Frozen.getComponentType());holder.ensureComponent(Invulnerable.getComponentType());holder.ensureComponent(Intangible.getComponentType());
        holder.addComponent(type,new EnemyStaging(state));return state;
    }
    /** Pause an already-published saved ME actor after an uncertain runtime result. */
    public static State prepareExisting(Store<EntityStore> store,Ref<EntityStore> ref,
            UUID world,UUID encounter,long generation,boolean additional){
        if(!store.isInThread()||ref==null||!ref.isValid()||ref.getStore()!=store
                ||!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(world))
            throw new IllegalStateException("ENEMY_STAGING_EXISTING_WORLD_THREAD");
        var id=store.getComponent(ref,UUIDComponent.getComponentType());
        if(id==null)throw new IllegalStateException("ENEMY_STAGING_EXISTING_UUID");
        var old=store.getComponent(ref,type);
        if(old!=null){
            var saved=old.state();
            if(!saved.world().equals(world)||!saved.encounter().equals(encounter)
                    ||!saved.entity().equals(id.getUuid())||saved.generation()!=generation
                    ||saved.additional()!=additional)
                throw new IllegalStateException("ENEMY_STAGING_EXISTING_CONFLICT");
            // An interrupted release may have removed one native flag while the saved marker
            // remains. Reassert protection before rebind instead of exposing that actor.
            if(store.getComponent(ref,Frozen.getComponentType())==null)
                store.addComponent(ref,Frozen.getComponentType(),Frozen.get());
            if(store.getComponent(ref,Invulnerable.getComponentType())==null)
                store.addComponent(ref,Invulnerable.getComponentType(),Invulnerable.INSTANCE);
            if(store.getComponent(ref,Intangible.getComponentType())==null)
                store.addComponent(ref,Intangible.getComponentType(),Intangible.INSTANCE);
            return saved;
        }
        int flags=(store.getComponent(ref,Frozen.getComponentType())==null?1:0)
                |(store.getComponent(ref,Invulnerable.getComponentType())==null?2:0)
                |(store.getComponent(ref,Intangible.getComponentType())==null?4:0)
                |(additional?8:0);
        var saved=new State(world,encounter,id.getUuid(),generation,flags);
        try{
            if((flags&1)!=0)store.addComponent(ref,Frozen.getComponentType(),Frozen.get());
            if((flags&2)!=0)store.addComponent(ref,Invulnerable.getComponentType(),Invulnerable.INSTANCE);
            if((flags&4)!=0)store.addComponent(ref,Intangible.getComponentType(),Intangible.INSTANCE);
            store.addComponent(ref,type,new EnemyStaging(saved));
            return saved;
        }catch(RuntimeException failure){
            if(ref.isValid())try{
                if((flags&4)!=0&&store.getComponent(ref,Intangible.getComponentType())!=null)
                    store.removeComponent(ref,Intangible.getComponentType());
                if((flags&2)!=0&&store.getComponent(ref,Invulnerable.getComponentType())!=null)
                    store.removeComponent(ref,Invulnerable.getComponentType());
                if((flags&1)!=0&&store.getComponent(ref,Frozen.getComponentType())!=null)
                    store.removeComponent(ref,Frozen.getComponentType());
            }catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            throw failure;
        }
    }
    /** Birth/recovery owner must validate the whole roster and durable publication before releasing any member. */
    public static void release(Store<EntityStore> store,Ref<EntityStore> ref,State expected){
        validateRelease(store,ref,expected);
        if((expected.addedFlags()&1)!=0)store.removeComponent(ref,Frozen.getComponentType());
        if((expected.addedFlags()&2)!=0)store.removeComponent(ref,Invulnerable.getComponentType());
        if((expected.addedFlags()&4)!=0)store.removeComponent(ref,Intangible.getComponentType());
        store.removeComponent(ref,type);
    }
    /** One world-thread publication boundary: reject an incomplete roster before exposing its first member. */
    public static void releaseGroup(Store<EntityStore> store,List<State> members){
        if(members==null||members.isEmpty()||members.size()>8)throw new IllegalStateException("ENEMY_STAGING_GROUP_SIZE");
        var first=members.getFirst();var unique=new HashSet<UUID>();var refs=new ArrayList<Ref<EntityStore>>(members.size());
        for(var member:members){
            if(!member.world().equals(first.world())||!member.encounter().equals(first.encounter())
                    ||member.generation()!=first.generation()||!unique.add(member.entity()))
                throw new IllegalStateException("ENEMY_STAGING_GROUP_IDENTITY");
            var ref=store.getExternalData().getRefFromUUID(member.entity());
            validateRelease(store,ref,member);refs.add(ref);
        }
        for(int i=0;i<members.size();i++)release(store,refs.get(i),members.get(i));
    }
    private static void validateRelease(Store<EntityStore> store,Ref<EntityStore> ref,State expected){
        if(!store.isInThread()||ref==null||!ref.isValid()||ref.getStore()!=store
                ||!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(expected.world()))
            throw new IllegalStateException("ENEMY_STAGING_RELEASE_BINDING");
        var marker=store.getComponent(ref,type);var id=store.getComponent(ref,UUIDComponent.getComponentType());
        if(marker==null||!marker.state().equals(expected)||id==null||!id.getUuid().equals(expected.entity()))
            throw new IllegalStateException("ENEMY_STAGING_RELEASE_OWNERSHIP");
        if(store.getComponent(ref,Frozen.getComponentType())==null||store.getComponent(ref,Invulnerable.getComponentType())==null
                ||store.getComponent(ref,Intangible.getComponentType())==null)throw new IllegalStateException("ENEMY_STAGING_NATIVE_FLAG_LOST");
    }
    /** Same filter surface as native HideFromPlayer, applied to every viewer including creative players. */
    public static final class Visibility extends EntityTickingSystem<EntityStore>{
        @Override public Query<EntityStore> getQuery(){return EntityTrackerSystems.EntityViewer.getComponentType();}
        @Override public SystemGroup<EntityStore> getGroup(){return EntityTrackerSystems.FIND_VISIBLE_ENTITIES_GROUP;}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemDependency<>(Order.AFTER,EntityTrackerSystems.CollectVisible.class),
                new SystemDependency<>(Order.BEFORE,EntityTrackerSystems.AddToVisible.class));}
        @Override public boolean isParallel(int size,int tasks){return false;}
        @Override public void tick(float dt,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
            try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.LIFECYCLE)){
            var viewer=chunk.getComponent(index,EntityTrackerSystems.EntityViewer.getComponentType());
            viewer.visible.removeIf(ref->ref.isValid()&&buffer.getComponent(ref,type)!=null);
        
            }}
    }
}
