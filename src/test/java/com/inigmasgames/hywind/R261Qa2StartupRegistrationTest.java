package com.inigmasgames.hywind;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.combat.resource.RpgResourceService;
import com.inigmasgames.hytalerpg.combat.resource.ReservationService;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleSupportSystem;
import com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the native registerSystem dependency validation that rejected QA1 at boot. */
class R261Qa2StartupRegistrationTest {
    @Test @ResourceLock("hytale-native-singletons")
    void productionLateDamageRegistrationFollowsBarriersAndPrecedesNativeHealth() throws Exception {
        try (RegistryFixture fixture = new RegistryFixture()) {
            var registry = fixture.registry;
            var missing = assertThrows(IllegalArgumentException.class,
                    () -> registry.registerSystem(new SupportDamageSystems.BeforeApply()));
            assertTrue(missing.getMessage().contains(SupportDamageSystems.ManaBeforeLife.class.getName()));

            var resources = new RpgResourceService(CombatBalanceProfile.loadCanonical(), new ReservationService());
            RpgDamageRegistration.registerLateDamageSystems(registry::registerSystem, resources);
            assertTrue(registry.hasSystemClass(SupportDamageSystems.ManaBeforeLife.class));
            assertTrue(registry.hasSystemClass(SupportDamageSystems.BeforeApply.class));

            ISystem<?>[] order = (ISystem<?>[]) field(ComponentRegistry.class, "sortedSystems").get(registry);
            assertTrue(index(order, SupportDamageSystems.ManaBeforeLife.class)
                    < index(order, SupportDamageSystems.BeforeApply.class));
            assertTrue(index(order, SupportDamageSystems.BeforeApply.class)
                    < index(order, DamageSystems.ApplyDamage.class));
        }
    }

    private static int index(ISystem<?>[] systems, Class<?> type) {
        for (int i = 0; i < systems.length; i++) if (systems[i] != null && systems[i].getClass() == type) return i;
        throw new AssertionError("System missing from native order: " + type);
    }

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class RegistryFixture implements AutoCloseable {
        final ComponentRegistry<EntityStore> registry;
        final Object oldUniverse, oldStats, oldDamage;

        @SuppressWarnings("unchecked") RegistryFixture() throws Exception {
            com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
            registry = new ComponentRegistry<>();
            oldUniverse = field(Universe.class, "instance").get(null);
            oldStats = field(EntityStatsModule.class, "instance").get(null);
            oldDamage = field(DamageModule.class, "instance").get(null);
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) unsafeField.get(null);

            var universe = (Universe) unsafe.allocateInstance(Universe.class);
            field(Universe.class, "playerRefComponentType").set(universe,
                    registry.registerComponent(PlayerRef.class, () -> null));
            field(Universe.class, "instance").set(null, universe);
            var stats = (EntityStatsModule) unsafe.allocateInstance(EntityStatsModule.class);
            field(EntityStatsModule.class, "entityStatMapComponentType").set(stats,
                    registry.registerComponent(EntityStatMap.class, EntityStatMap::new));
            field(EntityStatsModule.class, "instance").set(null, stats);
            var damage = (DamageModule) unsafe.allocateInstance(DamageModule.class);
            field(DamageModule.class, "gatherDamageGroup").set(damage, registry.registerSystemGroup());
            field(DamageModule.class, "filterDamageGroup").set(damage, registry.registerSystemGroup());
            field(DamageModule.class, "inspectDamageGroup").set(damage, registry.registerSystemGroup());
            field(DamageModule.class, "instance").set(null, damage);

            registry.registerSystem(new DamageSystems.ApplyDamage());
            // These two production barriers registered before the failed QA1 line. The
            // fixture supplies their class identities so native validation reaches it.
            var classes = (Map<Class<?>, Integer>) field(ComponentRegistry.class, "systemClasses").get(registry);
            classes.put(HytaleSupportSystem.Absorb.class, -1);
            classes.put(SupportDamageSystems.Shield.class, -1);
        }

        @Override public void close() {
            try {
                registry.shutdown();
                field(Universe.class, "instance").set(null, oldUniverse);
                field(EntityStatsModule.class, "instance").set(null, oldStats);
                field(DamageModule.class, "instance").set(null, oldDamage);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
    }
}
