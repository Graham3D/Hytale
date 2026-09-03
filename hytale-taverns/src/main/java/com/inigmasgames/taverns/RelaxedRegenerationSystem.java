package com.inigmasgames.taverns;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsSystems;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

/** Adds 25% of the regeneration amount already accepted by Hytale's native loop. */
final class RelaxedRegenerationSystem extends EntityTickingSystem<EntityStore>
        implements EntityStatsSystems.StatModifyingSystem {
    static final float HEALTH_AND_MANA_REGENERATION_BONUS = 0.25f;
    static final float STAMINA_REGENERATION_BONUS = 2.0f;

    private final Query<EntityStore> query = Archetype.of(
            Player.getComponentType(),
            EntityStatMap.getComponentType(),
            EffectControllerComponent.getComponentType());
    private final Set<Dependency<EntityStore>> dependencies = Set.of(
            new SystemDependency<>(
                    Order.AFTER,
                    EntityStatsModule.PlayerRegenerateStatsSystem.class));
    private final Consumer<Throwable> error;
    private final Field nativeRegenerationValues;
    private boolean reportedAccessFailure;

    RelaxedRegenerationSystem(Consumer<Throwable> error) {
        this.error = error;
        this.nativeRegenerationValues = resolveNativeRegenerationValues();
    }

    @Override
    public void tick(
            float delta,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (nativeRegenerationValues == null) {
            return;
        }
        EffectControllerComponent effects = chunk.getComponent(
                index, EffectControllerComponent.getComponentType());
        int relaxedIndex = EntityEffect.getAssetMap().getIndex(
                ComfortManager.RELAXED_EFFECT_ID);
        if (effects == null || relaxedIndex < 0 || !effects.hasEffect(relaxedIndex)) {
            return;
        }
        EntityStatMap stats = chunk.getComponent(index, EntityStatMap.getComponentType());
        if (stats == null) {
            return;
        }

        try {
            float[] nativeAmounts = (float[]) nativeRegenerationValues.get(stats);
            addBonus(
                    stats,
                    nativeAmounts,
                    DefaultEntityStatTypes.getHealth(),
                    HEALTH_AND_MANA_REGENERATION_BONUS);
            addBonus(
                    stats,
                    nativeAmounts,
                    DefaultEntityStatTypes.getStamina(),
                    STAMINA_REGENERATION_BONUS);
            addBonus(
                    stats,
                    nativeAmounts,
                    DefaultEntityStatTypes.getMana(),
                    HEALTH_AND_MANA_REGENERATION_BONUS);
        } catch (IllegalAccessException | RuntimeException exception) {
            if (!reportedAccessFailure) {
                reportedAccessFailure = true;
                error.accept(new IllegalStateException(
                        "Could not read Hytale's native regeneration results", exception));
            }
        }
    }

    private static void addBonus(
            EntityStatMap stats,
            float[] nativeAmounts,
            int statIndex,
            float bonus) {
        if (nativeAmounts == null
                || statIndex < 0
                || statIndex >= nativeAmounts.length) {
            return;
        }
        float nativeAmount = nativeAmounts[statIndex];
        if (nativeAmount > 0.0f) {
            stats.addStatValue(statIndex, nativeAmount * bonus);
        }
    }

    private static Field resolveNativeRegenerationValues() {
        try {
            Field field = EntityStatMap.class.getDeclaredField("tempRegenerationValues");
            return field.trySetAccessible() ? field : null;
        } catch (NoSuchFieldException | RuntimeException exception) {
            return null;
        }
    }

    boolean canAccessNativeRegeneration() {
        return nativeRegenerationValues != null;
    }

    static boolean nativeRegenerationHookAvailable() {
        return resolveNativeRegenerationValues() != null;
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }
}
