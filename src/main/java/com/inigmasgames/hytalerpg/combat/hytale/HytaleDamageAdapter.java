package com.inigmasgames.hytalerpg.combat.hytale;

import com.google.gson.Gson;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.damage.DamageCalculationService;

/** Only bridge allowed to narrow the kernel's double result and submit it to Hytale Damage. */
public final class HytaleDamageAdapter {
    static final Gson GSON = new Gson();
    public static final MetaKey<String> RPG_METADATA = Damage.META_REGISTRY.registerMetaObject(
            ignored -> "", false, "InigmasGames:HytaleRPGDamage", Codec.STRING);

    public void apply(Ref<EntityStore> target, ComponentAccessor<EntityStore> accessor,
                      Ref<EntityStore> source, DamageCause cause,
                      HytaleDamageMetadata metadata, DamageCalculationService.Result calculation) {
        EntityStatMap targetStats = accessor.getComponent(target, EntityStatMap.getComponentType());
        double before = targetStats == null || targetStats.get(DefaultEntityStatTypes.getHealth()) == null
                ? Double.NaN : targetStats.get(DefaultEntityStatTypes.getHealth()).get();
        HytaleDamageMetadata complete = new HytaleDamageMetadata(metadata.actorId(), metadata.rootCastId(),
                metadata.skillInstanceId(), metadata.correlationId(), calculation.preMitigationDamage(), before);
        Damage damage = new Damage(source == null ? Damage.NULL_SOURCE : new Damage.EntitySource(source),
                cause, calculation.toHytaleDamageFloat());
        damage.putMetaObject(RPG_METADATA, GSON.toJson(complete));
        DamageSystems.executeDamage(target, accessor, damage);
    }
    static HytaleDamageMetadata metadata(Damage damage) {
        String json = damage.getIfPresentMetaObject(RPG_METADATA);
        return json == null || json.isBlank() ? null : GSON.fromJson(json, HytaleDamageMetadata.class);
    }
}
