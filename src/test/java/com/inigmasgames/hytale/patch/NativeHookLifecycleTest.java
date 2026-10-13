package com.inigmasgames.hytale.patch;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Neutral hook behavior on the exact release API; native call-site validation is in the patcher. */
class NativeHookLifecycleTest {
    @BeforeAll static void options() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
    }

    @Test void mutationAdmitsWithoutProviderAndDeniesOnlyThroughInstalledPolicy() throws Exception {
        assertFalse(NativeMutationHook.installed());
        assertTrue(NativeMutationHook.allowClear(null, null, "effect"));
        assertTrue(NativeMutationHook.allowStat(null, null, null, null, null));
        var context=(InteractionContext)unsafe().allocateInstance(InteractionContext.class);
        Ref<EntityStore> target=new Ref<>(null);
        var seen=new ArrayList<NativeMutationHook.Kind>();
        var registration=NativeMutationHook.install(mutation->{seen.add(mutation.kind());return false;});
        try {
            assertTrue(NativeMutationHook.installed());
            assertFalse(NativeMutationHook.allowClear(context,target,"effect"));
            assertFalse(NativeMutationHook.allowStat(context,target,new Int2FloatOpenHashMap(),null,null));
            assertFalse(NativeMutationHook.allowStatWithModifier(context,target,new Int2FloatOpenHashMap(),null,null));
            assertEquals(List.of(NativeMutationHook.Kind.CLEAR_ENTITY_EFFECT,
                    NativeMutationHook.Kind.CHANGE_STAT,NativeMutationHook.Kind.CHANGE_STAT_WITH_MODIFIER),seen);
            assertThrows(IllegalStateException.class,()->NativeMutationHook.install(mutation->true));
        } finally {registration.close();}
        assertFalse(NativeMutationHook.installed());
        var admitted=NativeMutationHook.install(mutation->true);
        try {assertTrue(NativeMutationHook.allowClear(context,target,"effect"));}
        finally {admitted.close();}
    }

    @Test void damageAndProjectileObservationHaveNoProviderParityAndSingleOwnership() throws Exception {
        assertFalse(NativeDamageReceiptHook.installed());
        NativeDamageReceiptHook.stamp(null,null,null,0);
        assertNull(NativeDamageReceiptHook.receipt(null));
        var damageCalls=new AtomicInteger();
        var damage=NativeDamageReceiptHook.install(context->{damageCalls.incrementAndGet();return null;});
        try {
            assertTrue(NativeDamageReceiptHook.installed());
            assertThrows(IllegalStateException.class,()->NativeDamageReceiptHook.install(context->null));
            assertEquals(0,damageCalls.get());
        } finally {damage.close();}
        assertFalse(NativeDamageReceiptHook.installed());
        var damageAgain=NativeDamageReceiptHook.install(context->null);
        damageAgain.close();

        assertFalse(NativeProjectileReceiptHook.installed());
        NativeProjectileReceiptHook.beforeQueue(null,null);
        var projectileCalls=new AtomicInteger();
        var projectile=NativeProjectileReceiptHook.install((context,holder)->projectileCalls.incrementAndGet());
        try {
            assertTrue(NativeProjectileReceiptHook.installed());
            assertThrows(IllegalStateException.class,()->NativeProjectileReceiptHook.install((context,holder)->{}));
            assertEquals(0,projectileCalls.get());
        } finally {projectile.close();}
        assertFalse(NativeProjectileReceiptHook.installed());
        var projectileAgain=NativeProjectileReceiptHook.install((context,holder)->{});
        projectileAgain.close();
    }

    @Test void populationCallbackHasNoProviderParityAndCleanTeardown() throws Exception {
        NativePopulationProjectionHook.beforeSelection(null);
        var calls=new AtomicInteger();
        var projection=NativePopulationProjectionHook.install(store->calls.incrementAndGet());
        try {
            NativePopulationProjectionHook.beforeSelection(null);
            assertEquals(1,calls.get());
            assertThrows(IllegalStateException.class,()->NativePopulationProjectionHook.install(store->{}));
        } finally {projection.close();}
        NativePopulationProjectionHook.beforeSelection(null);
        assertEquals(1,calls.get());
        var again=NativePopulationProjectionHook.install(store->{});
        again.close();
    }

    private static sun.misc.Unsafe unsafe() throws ReflectiveOperationException {
        var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe)field.get(null);
    }
}
