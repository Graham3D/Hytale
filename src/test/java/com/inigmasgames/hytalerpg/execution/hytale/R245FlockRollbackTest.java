package com.inigmasgames.hytalerpg.execution.hytale;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.flock.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class R245FlockRollbackTest {
    @Test void nativeDissolvedFlockCleanupIsIdempotentButNeverDeletesReplacement() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var registry=new ComponentRegistry<EntityStore>();
        var singleton=FlockPlugin.class.getDeclaredField("instance");singleton.setAccessible(true);var previous=singleton.get(null);
        try{
            var unsafe=Class.forName("sun.misc.Unsafe");var field=unsafe.getDeclaredField("theUnsafe");field.setAccessible(true);
            var plugin=unsafe.getMethod("allocateInstance",Class.class).invoke(field.get(null),FlockPlugin.class);
            var membership=registry.registerComponent(FlockMembership.class,FlockMembership::new);
            var slot=FlockPlugin.class.getDeclaredField("flockMembershipComponentType");slot.setAccessible(true);slot.set(plugin,membership);singleton.set(null,plugin);
            var tag=registry.registerComponent(Nameplate.class,()->new Nameplate("test"));
            var store=registry.addStore(null,new R244NamesTest.Resources());
            java.util.function.Supplier<Ref<EntityStore>> entity=()->{
                var h=registry.newHolder();h.addComponent(tag,new Nameplate("test"));return store.addEntity(h,AddReason.SPAWN);
            };
            var leader=entity.get();var flock=entity.get();var m=new FlockMembership();m.setFlockRef(flock);store.addComponent(leader,membership,m);
            // Native removal of the last added member dissolves the flock before our final cleanup.
            store.removeEntity(flock,RemoveReason.REMOVE);
            NativeEnemyFlockExtension.removeCreatedFlock(store,leader,flock);
            NativeEnemyFlockExtension.removeCreatedFlock(store,leader,flock);
            assertTrue(leader.isValid());assertNull(store.getComponent(leader,membership));
            var replacement=entity.get();var changed=new FlockMembership();changed.setFlockRef(replacement);store.addComponent(leader,membership,changed);
            assertThrows(IllegalStateException.class,()->NativeEnemyFlockExtension.removeCreatedFlock(store,leader,flock));
            assertTrue(replacement.isValid());assertSame(changed,store.getComponent(leader,membership));
            NativeEnemyFlockExtension.removeCreatedFlock(store,leader,replacement);assertFalse(replacement.isValid());assertTrue(leader.isValid());
            registry.removeStore(store);
        }finally{singleton.set(null,previous);registry.shutdown();}
    }
}
