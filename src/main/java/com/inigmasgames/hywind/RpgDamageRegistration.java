package com.inigmasgames.hywind;

import com.hypixel.hytale.component.system.ISystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.resource.RpgResourceService;
import com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems;
import java.util.Objects;
import java.util.function.Consumer;

/** Registers the late damage stages using the same resource authority as the combat kernel. */
final class RpgDamageRegistration {
    private RpgDamageRegistration() { }

    static void registerLateDamageSystems(Consumer<ISystem<EntityStore>> register,
                                          RpgResourceService resources) {
        Objects.requireNonNull(register);
        register.accept(new SupportDamageSystems.ManaBeforeLife(resources));
        register.accept(new SupportDamageSystems.BeforeApply());
    }
}
