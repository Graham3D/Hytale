package com.inigmasgames.hytalerpg.execution.summon;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import java.lang.reflect.Field;

/** Isolated native component type registry for EffectControllerComponent.addEffect without a world. */
final class NativeSummonComponentFixture implements AutoCloseable {
    private final Object priorEntity;
    private final Object priorStats;

    NativeSummonComponentFixture() throws Exception {
        var unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        var unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        priorEntity=field(EntityModule.class,"instance").get(null);
        priorStats=field(EntityStatsModule.class,"instance").get(null);
        var entity=unsafe.allocateInstance(EntityModule.class);
        field(EntityModule.class,"effectControllerComponentType").set(entity,new ComponentType<>());
        field(EntityModule.class,"transformComponentType").set(entity,new ComponentType<>());
        field(EntityModule.class,"instance").set(null,entity);
        var stats=unsafe.allocateInstance(EntityStatsModule.class);
        field(EntityStatsModule.class,"entityStatMapComponentType").set(stats,new ComponentType<>());
        field(EntityStatsModule.class,"instance").set(null,stats);
    }
    private static Field field(Class<?> owner,String name) throws ReflectiveOperationException {
        var field=owner.getDeclaredField(name);field.setAccessible(true);return field;
    }
    @Override public void close() {
        try {
            field(EntityModule.class,"instance").set(null,priorEntity);
            field(EntityStatsModule.class,"instance").set(null,priorStats);
        } catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
}
