package com.inigmasgames.hytalerpg.execution.hytale;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class R244NamesTest {
    @org.junit.jupiter.api.BeforeAll static void options() throws Exception{com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});}
    static class Resources implements IResourceStorage {
        public <T extends Resource<E>,E> CompletableFuture<T> load(Store<E> s,ComponentRegistry.Data<E> d,ResourceType<E,T> t){return CompletableFuture.completedFuture(d.createResource(t));}
        public <T extends Resource<E>,E> CompletableFuture<Void> save(Store<E> s,ComponentRegistry.Data<E> d,ResourceType<E,T> t,T r){return CompletableFuture.completedFuture(null);}
        public <T extends Resource<E>,E> CompletableFuture<Void> remove(Store<E> s,ComponentRegistry.Data<E> d,ResourceType<E,T> t){return CompletableFuture.completedFuture(null);}
    }
    public static final class Ready implements Component<EntityStore>{public Ready clone(){return new Ready();}}
    @Test void actualEcsStructuralWriteWaitsForReadinessAndPreservesNativeDirtyFlag(){
        var registry=new ComponentRegistry<EntityStore>();
        try{
            var names=registry.registerComponent(Nameplate.class,()->new Nameplate(""));
            var ready=registry.registerComponent(Ready.class,Ready::new);
            var requests=new ArrayList<Ref<EntityStore>>();
            registry.registerSystem(new RefSystem<EntityStore>(){
                public Query<EntityStore> getQuery(){return Query.any();}
                public void onEntityAdded(Ref<EntityStore> r,AddReason reason,Store<EntityStore> s,CommandBuffer<EntityStore> b){
                    assertTrue(s.isProcessing());assertThrows(IllegalStateException.class,()->NativeHostileNames.write(s,r,names,"Bear"));requests.add(r);
                }
                public void onEntityRemove(Ref<EntityStore> r,RemoveReason reason,Store<EntityStore> s,CommandBuffer<EntityStore> b){}
            });
            var store=registry.addStore(null,new Resources());var actor=store.addEntity(registry.newHolder(),AddReason.SPAWN);
            assertNull(store.getComponent(actor,names));
            assertEquals(List.of(actor),requests);
            store.addComponent(actor,ready,new Ready());assertEquals(List.of(actor),requests);assertNull(store.getComponent(actor,names));
            NativeHostileNames.write(store,actor,names,"Bear Grizzly");
            assertEquals("Bear Grizzly",store.getComponent(actor,names).getText());
            // Only this test consumes the flag to emulate the native tracker, never diagnostics/runtime.
            assertTrue(store.getComponent(actor,names).consumeNetworkOutdated());
            store.getComponent(actor,names).setText("");NativeHostileNames.write(store,actor,names,"Bear Grizzly");
            assertEquals("Bear Grizzly",store.getComponent(actor,names).getText());
            assertTrue(store.getComponent(actor,names).consumeNetworkOutdated());
        }finally{registry.shutdown();}
    }
    @Test void sharedStatePreservesPersonalNamesAndRecomputesAfterDetachOrRoleChange(){
        var state=new NativeHostileNames.State();
        assertEquals("Dreadtusk",state.choose("Trork","","Trork Warrior","Dreadtusk"));state.owned="Dreadtusk";
        assertEquals("Trork Warrior",state.choose("Trork","Dreadtusk","Trork Warrior",null));state.owned="Trork Warrior";
        assertEquals("Bear Polar",state.choose("Bear_Polar","Trork Warrior","Bear Polar",null));state.owned="Bear Polar";
        assertEquals("Authored Guardian",state.choose("Bear_Polar","Authored Guardian","Bear Polar","Generated"));
        assertEquals("Authored Guardian",state.choose("Bear_Polar","","Bear Polar",null));
    }
}
